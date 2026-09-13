package com.tendril.app.sync

import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * "Delete forever" across two devices, driven end-to-end through the real snapshot files
 * rather than against the merge functions directly — [PageMergeTest] does that.
 *
 * What only shows up at this level is the *loop*: device A's purge has to reach
 * `purged_records.json`, survive a round trip through JSON, delete the row on B, and clear
 * `pages/<uid>.json` so the record has nothing left to come back from. Each half of that is
 * individually correct in [PageMergeTest]; the failure this file is here to catch is the two
 * halves being run in the wrong order, where the record and the tombstone that kills it cross
 * in a single pass and the record survives by accident.
 */
class PurgePropagationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private companion object {
        const val UID = "11111111-1111-4111-8111-111111111111"
        const val SURVIVOR = "22222222-2222-4222-8222-222222222222"
    }

    /** One device: its own Room tables, its own registry, sharing whatever folder it's handed. */
    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val ftsDao = FakePageFtsDao(store)
        val purgedDao = FakePurgedRecordDao()
        val entryDao = FakeEntryDao()
        val habitDao = FakeHabitDao()
        val registry = PurgeRegistry(purgedDao, pageDao, entryDao, habitDao, FakePropertyDao(store), RecordingEntryScheduleCoordinator())

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
            entryDao = entryDao,
            habitDao = habitDao,
            pageDao = pageDao,
            pagesSyncEngine = engine,
            purgeRegistry = registry,
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
            habitCompletionDao = FakeHabitCompletionDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )

        fun seed(uid: String, title: String, updatedAt: Long): Long = store.seedPage(
            Page(
                uid = uid, title = title, kind = PageKind.PAGE,
                createdAt = Instant.ofEpochMilli(updatedAt), updatedAt = Instant.ofEpochMilli(updatedAt),
            )
        )
    }

    private fun pageJson(uid: String, title: String, updatedAt: Long) = json.encodeToString(
        PageSnapshotRecord(uid = uid, title = title, kind = PageKind.PAGE.name, createdAt = updatedAt, updatedAt = updatedAt)
    )

    private fun purgedJson(vararg records: PurgedRecordSnapshot) = json.encodeToString(records.toList())

    // -------------------------------------------------------------------- the whole loop

    @Test
    fun `a purge on one device deletes the page on the other and clears its snapshot file`() = runBlocking {
        val folder = InMemorySyncFileStore()

        // Device A holds the page, and publishes it.
        val a = Device()
        val pageId = a.seed(UID, "Shared note", 1_000L)
        a.orchestrator.writeSnapshots(folder)
        assertTrue("A published the page's own snapshot file", "$UID.json" in folder.pageNames())

        // Device B picks it up.
        val b = Device()
        b.orchestrator.readAndMerge(folder)
        assertNotNull("B has the page before the purge", b.pageDao.getByUid(UID))

        // A deletes it forever, then syncs: the tombstone is published and the page file goes.
        a.registry.purgePage(pageId, now = Instant.ofEpochMilli(2_000L))
        a.orchestrator.writeSnapshots(folder)

        assertFalse("the purged page's snapshot file is cleared from the folder", "$UID.json" in folder.pageNames())
        assertTrue("and the tombstone is published in its place", "purged_records.json" in folder.rootNames())

        // B syncs and loses the row it still holds.
        b.orchestrator.readAndMerge(folder)

        assertNull("the purge reached the other device", b.pageDao.getByUid(UID))
        assertEquals(
            "B adopted the tombstone, so it keeps propagating rather than stopping here",
            listOf(UID),
            b.purgedDao.getForKind(PurgedKind.PAGE).map { it.uid },
        )
    }

    /** The crossing case the merge order exists for: the tombstone and the record it kills are
     * both in the folder on the same pass. Read the pages directory first and the record is
     * inserted before anything knows it was purged. */
    @Test
    fun `a tombstone in the same pass beats the page file it kills`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putPage("$UID.json", pageJson(UID, "Purged elsewhere", 1_000L))
        folder.putRoot("purged_records.json", purgedJson(PurgedRecordSnapshot(PurgedKind.PAGE.name, UID, 2_000L)))

        val device = Device()
        device.orchestrator.readAndMerge(folder)

        assertNull("the record must not be inserted ahead of the tombstone that kills it", device.pageDao.getByUid(UID))
    }

    @Test
    fun `a page file newer than the tombstone is restored and the tombstone retired`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putPage("$UID.json", pageJson(UID, "Edited after the purge", 3_000L))
        folder.putRoot("purged_records.json", purgedJson(PurgedRecordSnapshot(PurgedKind.PAGE.name, UID, 2_000L)))

        val device = Device()
        device.orchestrator.readAndMerge(folder)

        assertEquals("Edited after the purge", device.pageDao.getByUid(UID)?.title)
        assertTrue(
            "a retired tombstone must not be republished, or it fights the same record forever",
            device.purgedDao.getAll().isEmpty(),
        )
    }

    @Test
    fun `a tombstone only takes the page it names`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putPage("$UID.json", pageJson(UID, "Doomed", 1_000L))
        folder.putPage("$SURVIVOR.json", pageJson(SURVIVOR, "Untouched", 1_000L))
        folder.putRoot("purged_records.json", purgedJson(PurgedRecordSnapshot(PurgedKind.PAGE.name, UID, 2_000L)))

        val device = Device()
        device.orchestrator.readAndMerge(folder)

        assertEquals(listOf("Untouched"), device.pageDao.getAll().map { it.title })
        assertEquals(
            "absence still never implies deletion — only the named tombstone removes anything",
            setOf("$UID.json", "$SURVIVOR.json"),
            folder.pageNames(),
        )
    }

    @Test
    fun `an unreadable tombstone file leaves local pages alone`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putRoot("purged_records.json", "{ this is not the array it should be")

        val device = Device()
        device.seed(UID, "Still here", 1_000L)
        device.orchestrator.readAndMerge(folder)

        assertNotNull("a corrupt tombstone file must not be read as 'delete everything'", device.pageDao.getByUid(UID))
    }

    @Test
    fun `a tombstone naming a kind this build does not know is skipped, not fatal`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putRoot(
            "purged_records.json",
            purgedJson(
                PurgedRecordSnapshot("WIDGET", "not-even-a-uuid", 2_000L),
                PurgedRecordSnapshot(PurgedKind.PAGE.name, UID, 2_000L),
            ),
        )

        val device = Device()
        device.seed(UID, "Doomed", 1_000L)
        device.orchestrator.readAndMerge(folder)

        assertNull("the rest of the file is still good", device.pageDao.getByUid(UID))
        assertEquals(
            "a newer build may purge things this one has no concept of",
            listOf(UID),
            device.purgedDao.getAll().map { it.uid },
        )
    }
}
