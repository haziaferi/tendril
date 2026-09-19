package com.tendril.app.sync

import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * audit §5.2 — whole-page last-write-wins no longer discards the loser *silently*.
 *
 * §9.4 accepts LWW for v1 deliberately and this does not change it: the winner still wins
 * outright. What changes is that the losing copy is written beside the winner instead of
 * vanishing, the way Syncthing itself keeps the file that lost a race — so a concurrent edit
 * made on another device is recoverable by hand.
 *
 * Two hazards this has to avoid, and most of these tests are about them rather than about the
 * preservation itself:
 *
 *  - a preserved loser must never be merged back, or it either resurrects the old version or,
 *    on losing again, writes a fresh copy of itself every single sync;
 *  - an identical copy is not a loss, or the folder fills with duplicates of itself.
 */
class LostPagePreservationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val ftsDao = FakePageFtsDao(store)
        val purgedDao = FakePurgedRecordDao()
        val registry = PurgeRegistry(
            purgedDao, pageDao, FakeEntryDao(), FakeHabitDao(), FakePropertyDao(store),
            RecordingEntryScheduleCoordinator(),
        )
        val engine = PagesSyncEngine(
            pageDao = pageDao,
            blockDao = blockDao,
            labelDao = FakeLabelDao(store),
            pageDatabaseDao = FakePageDatabaseDao(store),
            propertyDao = FakePropertyDao(store),
            propertyValueDao = FakePropertyValueDao(store),
            pageDatabaseViewDao = FakePageDatabaseViewDao(store),
            pageCanvasDao = FakePageCanvasDao(store),
            canvasNodeDao = FakeCanvasNodeDao(store),
            canvasEdgeDao = FakeCanvasEdgeDao(store),
            pageRelationDao = FakePageRelationDao(store),
            purgeRegistry = registry,
            pageContentRepository = PageContentRepository(pageDao, blockDao, ftsDao),
            pageHistory = PageHistory(pageDao, blockDao, FakePageRevisionDao()),
        )
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = FakeEntryDao(),
            habitDao = FakeHabitDao(),
            pageDao = pageDao,
            pagesSyncEngine = engine,
            purgeRegistry = registry,
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private fun pageRecord(uid: String, title: String, updatedAt: Long) = PageSnapshotRecord(
        uid = uid,
        title = title,
        kind = PageKind.PAGE.name,
        parentUid = null,
        databaseUid = null,
        createdAt = 1_000,
        updatedAt = updatedAt,
    )

    private companion object {
        const val UID = "11111111-1111-4111-8111-111111111111"
        const val LOST = ".tendril-lost-"
    }

    private fun InMemorySyncFileStore.lostFiles() =
        runBlocking { listPages() }.filter { LOST in it }

    /** A folder holding one page snapshot at [updatedAt]. */
    private fun folderWith(uid: String, title: String, updatedAt: Long) =
        InMemorySyncFileStore().apply {
            putPage("$uid.json", json.encodeToString(PageSnapshotRecord.serializer(), pageRecord(uid, title, updatedAt)))
        }

    // ------------------------------------------------------------ the preservation

    @Test
    fun `a losing remote page is kept beside the winner`() = runBlocking {
        val d = Device()
        // Local holds the newer version...
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, newer", 9_000)))
        // ...and an older, different copy arrives from the folder.
        val store = folderWith(UID, "Theirs, older", 5_000)

        d.orchestrator.readAndMerge(store)

        assertEquals("the newer local title must still win", "Mine, newer", d.pageDao.getByUid(UID)!!.title)
        assertEquals("the loser should have been kept", 1, store.lostFiles().size)
        assertTrue(store.lostFiles().single().startsWith("$UID$LOST"))
    }

    @Test
    fun `a local page overwritten by a newer remote is kept beside the winner`() = runBlocking {
        val d = Device()
        // Local holds an older, different version...
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, older", 5_000)))
        // ...and a newer copy arrives from the folder, which legitimately wins.
        val store = folderWith(UID, "Theirs, newer", 9_000)

        d.orchestrator.readAndMerge(store)

        assertEquals("the newer remote title must win", "Theirs, newer", d.pageDao.getByUid(UID)!!.title)
        assertEquals("the overwritten local version should have been kept", 1, store.lostFiles().size)
        assertTrue(store.lostFiles().single().startsWith("$UID$LOST"))
    }

    @Test
    fun `preserving the same losing version twice does not accumulate copies`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, newer", 9_000)))
        val store = folderWith(UID, "Theirs, older", 5_000)

        d.orchestrator.readAndMerge(store)
        d.orchestrator.readAndMerge(store)
        d.orchestrator.readAndMerge(store)

        // The name carries the losing updatedAt, so the same version overwrites one file.
        assertEquals(1, store.lostFiles().size)
    }

    // ------------------------------------------------------------ what is not a loss

    @Test
    fun `an identical copy is not preserved`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Same", 9_000)))
        // Older timestamp, identical content -- a stale copy of a version we already hold.
        val store = InMemorySyncFileStore().apply {
            putPage("$UID.json", json.encodeToString(PageSnapshotRecord.serializer(), pageRecord(UID, "Same", 9_000)))
        }

        d.orchestrator.readAndMerge(store)

        assertTrue("nothing differed, so nothing was lost", store.lostFiles().isEmpty())
    }

    @Test
    fun `the winner is not preserved, only the copy it replaced`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, older", 1_000)))
        val store = folderWith(UID, "Theirs, newer", 9_000)

        d.orchestrator.readAndMerge(store)

        assertEquals("the newer remote should have won", "Theirs, newer", d.pageDao.getByUid(UID)!!.title)
        // A loss file is named for the losing version's own timestamp, so this asserts *which*
        // side was kept: 1_000, the local copy that was overwritten -- never 9_000, which is
        // the live version and would be a copy of the winner.
        assertEquals(listOf("$UID${LOST}1000.json"), store.lostFiles())
    }

    @Test
    fun `a local page replaced by an identical newer remote is not preserved`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Same", 1_000)))
        // Newer, so it wins -- but it carries exactly the content already held, which is the
        // ordinary case of a peer's routine re-export catching up. Nothing was lost.
        val store = folderWith(UID, "Same", 9_000)

        d.orchestrator.readAndMerge(store)

        assertTrue("only the timestamp moved, so nothing was lost", store.lostFiles().isEmpty())
    }

    @Test
    fun `a page this device has never seen is not a loss`() = runBlocking {
        val d = Device()
        val store = folderWith(UID, "Brand new", 5_000)

        d.orchestrator.readAndMerge(store)

        assertTrue(store.lostFiles().isEmpty())
    }

    // ------------------------------------------------- a preserved loser is never an input

    @Test
    fun `a preserved loser is not merged back on a later pass`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, newer", 9_000)))
        val store = folderWith(UID, "Theirs, older", 5_000)
        d.orchestrator.readAndMerge(store)

        // The loser is sitting in the pages directory now. It must not be read as a page.
        d.orchestrator.readAndMerge(store)

        assertEquals("the loser must not resurrect", "Mine, newer", d.pageDao.getByUid(UID)!!.title)
        assertEquals("nor breed copies of itself", 1, store.lostFiles().size)
    }

    @Test
    fun `a preserved loser cannot resurrect a page after a restore from backup`() = runBlocking {
        // The hazard the marker exclusion actually guards, and it needs local to end up *older*
        // than the preserved copy -- which a §9.4.1 restore-from-backup does.
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, newer", 9_000)))

        // A different, older copy arrives and is preserved at t=5000.
        val store = folderWith(UID, "Lost copy", 5_000)
        d.orchestrator.readAndMerge(store)
        assertEquals(1, store.lostFiles().size)

        // The folder's own snapshot is then replaced by one OLDER than the preserved copy...
        store.putPage(
            "$UID.json",
            json.encodeToString(PageSnapshotRecord.serializer(), pageRecord(UID, "Folder copy", 2_000)),
        )
        // ...and this device is restored from a backup that predates all of it.
        d.pageDao.deleteForever(d.pageDao.getByUid(UID)!!.id)

        d.orchestrator.readAndMerge(store)

        // Merged as a page, the preserved copy (5_000) would outrank the folder's own (2_000)
        // and take the page over. It is evidence, not an input.
        assertEquals(
            "a preserved loser must never win a merge it was never entered into",
            "Folder copy",
            d.pageDao.getByUid(UID)!!.title,
        )
    }

    // ------------------------------------------------------------ delete forever means forever

    @Test
    fun `purging a page clears its preserved losers too`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID, "Mine, newer", 9_000)))
        val store = folderWith(UID, "Theirs, older", 5_000)
        d.orchestrator.readAndMerge(store)
        assertEquals(1, store.lostFiles().size)

        // "Delete forever" that leaves copies of the page behind is not delete forever.
        d.registry.purgePage(d.pageDao.getByUid(UID)!!.id)
        d.orchestrator.writeSnapshots(store)

        assertTrue("the preserved copy must go with the page", store.lostFiles().isEmpty())
    }
}
