package com.tendril.app.sync

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * §9.4 / S4 stage 4 — a picture survives the `.tendril` round trip.
 *
 * The folder half is [ImageSyncTest]'s; this is the same content through the other container. The
 * property both must hold is the one [ImageSyncTest.aPeersPathIsNeverAdopted] names: what travels
 * is `BlockSnapshotRecord.imageName`, and each device resolves it to a local path of its own. An
 * archive is a file that can cross machines *and* time, so it is the easier place to get that
 * wrong — the exporting device's `imagePath` is right there in the same object.
 */
class ArchiveImageTest {

    @get:Rule val temp = TemporaryFolder()

    private companion object {
        const val UID_PAGE = "22222222-2222-4222-8222-222222222222"
        const val UID_BLOCK = "88888888-8888-4888-8888-888888888888"
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 7, 7, 7)
        fun at(m: Long): Instant = Instant.ofEpochMilli(m)
    }

    /** One device: its own database, its own private image storage, its own file provider. */
    private inner class Device(passphrase: String? = null) {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val propertyDao = FakePropertyDao(store)
        val localImages = InMemoryLocalImageStore()
        val backing = FakeContentResolverBacking()
        val purgeRegistry = PurgeRegistry(
            FakePurgedRecordDao(), pageDao, FakeEntryDao(), FakeHabitDao(), propertyDao,
            RecordingEntryScheduleCoordinator(),
        )

        val engine = PagesSyncEngine(
            pageDao = pageDao,
            blockDao = blockDao,
            labelDao = FakeLabelDao(store),
            pageDatabaseDao = FakePageDatabaseDao(store),
            propertyDao = propertyDao,
            propertyValueDao = FakePropertyValueDao(store),
            pageDatabaseViewDao = FakePageDatabaseViewDao(store),
            pageCanvasDao = FakePageCanvasDao(store),
            canvasNodeDao = FakeCanvasNodeDao(store),
            canvasEdgeDao = FakeCanvasEdgeDao(store),
            pageRelationDao = FakePageRelationDao(store),
            purgeRegistry = purgeRegistry,
            pageContentRepository = PageContentRepository(pageDao, blockDao, FakePageFtsDao(store), FakeBlockFtsDao(store)),
            pageHistory = PageHistory(pageDao, blockDao, FakePageRevisionDao()),
        )

        val archive = PortableArchive(
            context = fakeContext(backing, temp.newFolder(), temp.newFolder()),
            entryDao = FakeEntryDao(),
            habitDao = FakeHabitDao(),
            pageDao = pageDao,
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            purgeRegistry = purgeRegistry,
            pagesSyncEngine = engine,
            localImages = localImages,
            passphrase = { passphrase },
            rearmAlarms = { _ -> },
        )

        /** A page with one IMAGE block, optionally already holding a local file. */
        fun seed(localPath: String? = null) {
            val pageId = store.seedPage(
                Page(uid = UID_PAGE, title = "Album", kind = PageKind.PAGE, createdAt = at(1_000), updatedAt = at(1_000))
            )
            // Key and `id` must agree — see the same note in [ImageSyncTest.Device.seed].
            val blockId = store.nextId()
            store.blocks[blockId] = Block(
                id = blockId,
                uid = UID_BLOCK,
                pageId = pageId,
                type = BlockType.IMAGE,
                order = 0,
                imagePath = localPath,
                content = "",
                createdAt = at(1_000),
                updatedAt = at(1_000),
            )
        }

        /** Exports and hands back the zip's entries. */
        fun exportedEntries(): Map<String, ByteArray> {
            val uri = backing.writableFile()
            runBlocking { archive.export(uri) }
            return entriesOf(backing.bytesWrittenTo(uri))
        }
    }

    @Test
    fun `an export packages the picture beside the record that names it`() = runBlocking {
        val a = Device()
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG

        val entries = a.exportedEntries()

        // Named for the block, not for the file it happens to be called on this device — the same
        // rule the sync folder uses, so one archive and one folder are interchangeable sources.
        assertArrayEquals(PNG, entries["images/$UID_BLOCK.png"])
        assertNotNull("the page record must travel too", entries["pages/$UID_PAGE.json"])
    }

    @Test
    fun `a picture makes it from one device to another through a file`() = runBlocking {
        val a = Device()
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG
        val uri = a.backing.writableFile()
        a.archive.export(uri)
        val archiveBytes = a.backing.bytesWrittenTo(uri)

        // B has never seen this page: both the block and its picture arrive in the same file.
        val b = Device()
        val result = b.archive.importAdditive(b.backing.givenFile(archiveBytes))

        val block = b.blockDao.getByUid(UID_BLOCK)!!
        assertEquals(1, result.imagesRestored)
        assertArrayEquals(PNG, b.localImages.written["$UID_BLOCK.png"])
        // B's own path, derived by B from the name — never A's `local:holiday.png`, which names a
        // file inside another install's private storage and would be a dead link here.
        assertEquals("local:$UID_BLOCK.png", block.imagePath)
    }

    @Test
    fun `a picture for a block this device does not have is not stored`() = runBlocking {
        val b = Device() // no pages, no blocks
        val stray = zipOf("images/99999999-9999-4999-8999-999999999999.png" to PNG)

        val result = b.archive.importAdditive(b.backing.givenFile(stray))

        // The same rule the folder fetch follows: a name matching no local block belongs to a page
        // that did not come across, and writing it would leave bytes nothing can reach.
        assertEquals(0, result.imagesRestored)
        assertTrue(b.localImages.written.isEmpty())
    }

    @Test
    fun `an import never replaces a picture this device already has`() = runBlocking {
        val b = Device()
        b.seed(localPath = "local:$UID_BLOCK.png")
        b.localImages.written["$UID_BLOCK.png"] = PNG
        val older = zipOf("images/$UID_BLOCK.png" to byteArrayOf(1, 1, 1))

        val result = b.archive.importAdditive(b.backing.givenFile(older))

        // §9.4.1 — import is additive. Overwriting this device's copy with whatever a file happens
        // to carry is the "one import silently overwrites the other person's data" failure, and an
        // image is the case where it would be silent: the block looks identical either way.
        assertEquals(0, result.imagesRestored)
        assertArrayEquals(PNG, b.localImages.written["$UID_BLOCK.png"])
    }

    @Test
    fun `an encrypted export encrypts the picture too`() = runBlocking {
        val a = Device(passphrase = "correct horse")
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG

        val packaged = a.exportedEntries()["images/$UID_BLOCK.png"]!!

        // §9.4.2 reaches the image channel here even though it does not yet reach the sync folder's
        // — this class encrypts per entry, so bytes are covered the moment they are written as one.
        assertTrue("the packaged image must be ciphertext", SnapshotEncryption.isEncrypted(packaged))
        assertEquals(
            "and must not merely be the plaintext with a prefix",
            -1,
            packaged.toList().windowed(PNG.size).indexOf(PNG.toList()),
        )
    }

    @Test
    fun `an encrypted picture comes back with the right passphrase and not without it`() = runBlocking {
        val a = Device(passphrase = "correct horse")
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG
        val uri = a.backing.writableFile()
        a.archive.export(uri)
        val archiveBytes = a.backing.bytesWrittenTo(uri)

        val wrong = Device(passphrase = "battery staple")
        // Refused whole, not silently partial: the existing `readable()` guard covers image entries
        // because they are counted in the same `undecryptable` tally as the JSON ones.
        runCatching { wrong.archive.importAdditive(wrong.backing.givenFile(archiveBytes)) }
            .onSuccess { error("an archive under another passphrase must not import") }
        assertNull(wrong.blockDao.getByUid(UID_BLOCK))
        assertTrue(wrong.localImages.written.isEmpty())

        val right = Device(passphrase = "correct horse")
        val result = right.archive.importAdditive(right.backing.givenFile(archiveBytes))

        assertEquals(1, result.imagesRestored)
        assertArrayEquals(PNG, right.localImages.written["$UID_BLOCK.png"])
    }
}
