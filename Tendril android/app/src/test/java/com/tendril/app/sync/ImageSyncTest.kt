package com.tendril.app.sync

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * §9.4 / S4 — an image reaches the other device.
 *
 * The two fields this turns on are deliberately different things, and the tests are written to
 * keep them apart. `BlockSnapshotRecord.imageName` says *which* image and travels; `Block.imagePath`
 * says where **this** device keeps its copy and never leaves. [aPeersPathIsNeverAdopted] is the one
 * guarding the seam — a merge that copied a peer's absolute path into local state would look like
 * it worked on a single device and break on every real pair.
 */
class ImageSyncTest {

    private companion object {
        const val UID_PAGE = "11111111-1111-4111-8111-111111111111"
        const val UID_BLOCK = "77777777-7777-4777-8777-777777777777"
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        const val PASS = "correct horse battery staple"
        const val OTHER = "a different passphrase entirely"
        fun at(m: Long): Instant = Instant.ofEpochMilli(m)
    }

    /** One device: its own database, its own private image storage, one shared folder. */
    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val propertyDao = FakePropertyDao(store)
        val localImages = InMemoryLocalImageStore()
        val purgeRegistry = PurgeRegistry(
            FakePurgedRecordDao(), pageDao, FakeEntryDao(), FakeHabitDao(), propertyDao,
            RecordingEntryScheduleCoordinator(),
        )

        val engine = PagesSyncEngine(
            pageDao = pageDao,
            blockDao = blockDao,
            tagDao = FakeTagDao(store),
            pageDatabaseDao = FakePageDatabaseDao(store),
            propertyDao = propertyDao,
            propertyValueDao = FakePropertyValueDao(store),
            pageDatabaseViewDao = FakePageDatabaseViewDao(store),
            pageCanvasDao = FakePageCanvasDao(store),
            canvasNodeDao = FakeCanvasNodeDao(store),
            canvasEdgeDao = FakeCanvasEdgeDao(store),
            pageRelationDao = FakePageRelationDao(store),
            purgeRegistry = purgeRegistry,
            pageContentRepository = PageContentRepository(blockDao, FakePageFtsDao(store)),
        )

        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = FakeEntryDao(),
            habitDao = FakeHabitDao(),
            pageDao = mockk(relaxed = true),
            pagesSyncEngine = engine,
            purgeRegistry = mockk(relaxed = true),
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            localImages = localImages,
        )

        /** A page with one IMAGE block, optionally already holding a local file. */
        fun seed(localPath: String? = null): Long {
            val pageId = store.seedPage(
                Page(uid = UID_PAGE, title = "Trip", kind = PageKind.PAGE, createdAt = at(1_000), updatedAt = at(1_000))
            )
            // Key and `id` must be the same value: `FakeBlockDao` keys the map by `id`, so seeding
            // them apart leaves a row that reads back but cannot be updated in place.
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
            return pageId
        }
    }

    @Test
    fun `an image this device holds is published into the folder`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:photo.png")
        a.localImages.written["photo.png"] = PNG

        a.orchestrator.writeSnapshots(folder)

        // Named after the block, so any device can work out what it is called without being told.
        assertEquals(setOf("$UID_BLOCK.png"), folder.imageNames())
        assertArrayEquals(PNG, folder.imageBytes("$UID_BLOCK.png"))
    }

    @Test
    fun `an image in the folder is fetched into a device that has the block but not the file`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putImage("$UID_BLOCK.png", PNG)

        val b = Device()
        b.seed(localPath = null)
        b.orchestrator.readAndMerge(folder)

        val block = b.blockDao.getByUid(UID_BLOCK)!!
        // The block now points at a file *this* device wrote, under a path of its own choosing.
        assertEquals("local:$UID_BLOCK.png", block.imagePath)
        assertArrayEquals(PNG, b.localImages.written["$UID_BLOCK.png"])
    }

    @Test
    fun aPeersPathIsNeverAdopted() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putImage("$UID_BLOCK.png", PNG)

        val b = Device()
        b.seed(localPath = null)
        b.orchestrator.readAndMerge(folder)

        // Device A's path was `local:photo.png` in the test above. Nothing in the folder carries a
        // path at all, so there is nothing for B to adopt even by accident -- B's path is derived
        // from the *name*, by B, which is the property that makes this work across real devices.
        val path = b.blockDao.getByUid(UID_BLOCK)!!.imagePath!!
        assertTrue("a local path must be this device's own", path.startsWith("local:"))
        assertTrue("and must be derived from the folder name", path.endsWith("$UID_BLOCK.png"))
    }

    @Test
    fun `a folder image whose block is not here is left alone`() = runBlocking {
        val folder = InMemorySyncFileStore()
        folder.putImage("99999999-9999-4999-8999-999999999999.png", PNG)

        val b = Device() // no blocks at all
        b.orchestrator.readAndMerge(folder)

        // Not fetched, and not deleted either: the page may simply not have merged here yet, and
        // removing another device's file on that basis is how a sync engine destroys data.
        assertTrue(b.localImages.written.isEmpty())
        assertEquals(setOf("99999999-9999-4999-8999-999999999999.png"), folder.imageNames())
    }

    @Test
    fun `an image already in the folder is not rewritten on every pass`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:photo.png")
        a.localImages.written["photo.png"] = PNG

        a.orchestrator.writeSnapshots(folder)
        // A different payload under the same name would only appear if the second pass rewrote it.
        folder.putImage("$UID_BLOCK.png", byteArrayOf(9, 9, 9))
        a.orchestrator.writeSnapshots(folder)

        // Syncthing replicates every change, so republishing identical bytes each pass is pure
        // churn across every device on the folder.
        assertArrayEquals(byteArrayOf(9, 9, 9), folder.imageBytes("$UID_BLOCK.png"))
    }

    @Test
    fun `a block with no image publishes nothing`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = null)

        a.orchestrator.writeSnapshots(folder)

        assertTrue(folder.imageNames().isEmpty())
        assertNull(a.blockDao.getByUid(UID_BLOCK)!!.imagePath)
    }

    // ------------------------------------------------------------------ S4 stage 5: purge

    @Test
    fun `a local image no block points at is collected on the next write`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:$UID_BLOCK.png")
        a.localImages.written["$UID_BLOCK.png"] = PNG
        // The shape my own replace-with-a-different-type bug leaves behind: the block moved on to
        // a new name, and the previous file is unreachable from any row.
        a.localImages.written["$UID_BLOCK"] = byteArrayOf(1, 2, 3)

        a.orchestrator.writeSnapshots(folder)

        assertEquals(setOf("$UID_BLOCK.png"), a.localImages.written.keys)
    }

    @Test
    fun `an image a block still points at is never collected`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:$UID_BLOCK.png")
        a.localImages.written["$UID_BLOCK.png"] = PNG

        repeat(3) { a.orchestrator.writeSnapshots(folder) }

        assertArrayEquals(PNG, a.localImages.written["$UID_BLOCK.png"])
    }

    @Test
    fun `purging a page forever takes its images out of the folder`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        val pageId = a.seed(localPath = "local:$UID_BLOCK.png")
        a.localImages.written["$UID_BLOCK.png"] = PNG
        a.orchestrator.writeSnapshots(folder)
        assertEquals(setOf("$UID_BLOCK.png"), folder.imageNames())

        // "Delete forever", the one deletion §5.5.1.1 lets travel.
        a.purgeRegistry.purgePage(pageId)
        a.orchestrator.writeSnapshots(folder)

        // The page file goes, and so does the picture it owned -- read out of the page record
        // before that file was removed, which is the only positive evidence available.
        assertTrue("the page's snapshot should be gone", folder.pageNames().isEmpty())
        assertTrue("and its image with it", folder.imageNames().isEmpty())
    }

    @Test
    fun `a folder image whose page was not purged is left where it is`() = runBlocking {
        val folder = InMemorySyncFileStore()
        // Belongs to a page this device has never seen -- which is indistinguishable from a page
        // that simply has not merged here yet. §9.4's rule is that absence never implies deletion,
        // and this is exactly the case it protects.
        folder.putImage("99999999-9999-4999-8999-999999999999.png", PNG)

        val b = Device()
        b.orchestrator.writeSnapshots(folder)

        assertEquals(setOf("99999999-9999-4999-8999-999999999999.png"), folder.imageNames())
    }

    // ---------------------------------------------------- §9.4.2: the image channel, encrypted

    /** A device holding one picture, having published it into [folder] under [passphrase]. */
    private suspend fun publisher(folder: InMemorySyncFileStore, passphrase: String?): Device =
        Device().also {
            it.seed(localPath = "local:holiday.png")
            it.localImages.written["holiday.png"] = PNG
            it.orchestrator.writeSnapshots(folder, passphrase)
        }

    @Test
    fun `an encrypted folder holds the picture sealed, under a name that does not say what it is`() = runBlocking {
        val folder = InMemorySyncFileStore()
        publisher(folder, PASS)

        // The uid survives, because it is what lets a fetch be driven by the folder listing. The
        // extension does not: `.png` here would announce the type of every picture in the folder
        // to anyone who can read the directory, which is most of the point of encrypting them.
        assertEquals(setOf("$UID_BLOCK.tdrlimg"), folder.imageNames())
        val stored = folder.imageBytes("$UID_BLOCK.tdrlimg")!!
        assertTrue("must be sealed", SnapshotEncryption.isEncrypted(stored))
        assertEquals(
            "and the plaintext must not be sitting inside it",
            -1,
            stored.toList().windowed(PNG.size).indexOf(PNG.toList()),
        )
    }

    @Test
    fun `a device with the passphrase gets the picture back under its real name`() = runBlocking {
        val folder = InMemorySyncFileStore()
        publisher(folder, PASS)

        val b = Device()
        b.orchestrator.readAndMerge(folder, PASS)

        // `.png`, not `.tdrlimg`: the real name travelled inside the envelope, so this device
        // writes a file that is what it claims to be. P2's hardware run already showed what an
        // extensionless image costs, and the opaque folder name would reintroduce exactly that
        // if the name were taken from the folder rather than from inside the ciphertext.
        assertArrayEquals(PNG, b.localImages.written["$UID_BLOCK.png"])
        assertEquals("local:$UID_BLOCK.png", b.blockDao.getByUid(UID_BLOCK)!!.imagePath)
    }

    @Test
    fun `a device with the wrong passphrase gets no picture at all`() = runBlocking {
        val folder = InMemorySyncFileStore()
        publisher(folder, PASS)

        val b = Device()
        val result = b.orchestrator.readAndMerge(folder, OTHER)

        assertTrue("nothing may be written from a file this device cannot open", b.localImages.written.isEmpty())
        assertTrue("and the person has to be told why", result.passphraseMismatch)
    }

    @Test
    fun `a plaintext picture in an encrypted folder is refused, not adopted`() = runBlocking {
        val folder = InMemorySyncFileStore()
        publisher(folder, PASS)
        // Injected the way someone with write access to the synced folder would: named correctly
        // for a block that exists, simply not encrypted. This was the one channel where that still
        // worked -- and image bytes go to a platform decoder, a far less forgiving parser than the
        // JSON one `decryptText` has guarded since audit 4.1.
        folder.deleteImage("$UID_BLOCK.tdrlimg")
        folder.putImage("$UID_BLOCK.png", byteArrayOf(6, 6, 6))

        val b = Device()
        val result = b.orchestrator.readAndMerge(folder, PASS)

        assertTrue("an unencrypted image in an encrypted folder must not be taken", b.localImages.written.isEmpty())
        assertTrue("and it counts as unreadable rather than absent", result.undecryptableFiles > 0)
        // Refused, never deleted -- the same treatment an undecryptable snapshot gets, so the file
        // stays on disk to be inspected instead of being destroyed unread.
        assertTrue("$UID_BLOCK.png" in folder.imageNames())
    }

    @Test
    fun `a re-key rewrites the picture instead of leaving it under the dead key`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = publisher(folder, PASS)
        val before = folder.imageBytes("$UID_BLOCK.tdrlimg")!!.copyOf()

        a.orchestrator.rekey(folder, PASS, OTHER)

        // Snapshots re-encrypt themselves because they are rewritten every pass. An image is
        // written once and skipped forever after, which is right for churn and exactly wrong
        // here: without the re-key forcing a republish it would still be sealed under a key
        // nobody holds, and nothing else in the pass would fail to say so.
        val after = folder.imageBytes("$UID_BLOCK.tdrlimg")!!
        assertFalse("the stored bytes must change", before.contentEquals(after))

        val b = Device()
        b.orchestrator.readAndMerge(folder, OTHER)
        assertArrayEquals(PNG, b.localImages.written["$UID_BLOCK.png"])
    }

    @Test
    fun `turning encryption on clears the plaintext copy it replaces`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = publisher(folder, null)
        assertEquals(setOf("$UID_BLOCK.png"), folder.imageNames())

        a.orchestrator.writeSnapshots(folder, PASS)

        // The omission that would have made the whole change pointless: a picture nobody deleted
        // is a picture still readable to anyone with the folder, however well the sealed copy
        // beside it is written.
        assertEquals(setOf("$UID_BLOCK.tdrlimg"), folder.imageNames())
        assertTrue("$UID_BLOCK.png" in folder.deletedImageNames)
        assertNotEquals(0, folder.deletedImageNames.size)
    }

    @Test
    fun `the switch to encryption happens in one pass through the real entry point`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG

        // [syncNow] rather than [writeSnapshots], which is what every other test here calls: the
        // button a person presses runs a merge *and then* a write, and the sibling test covering
        // this transition exercises only the second half. A hardware run raised the question of
        // whether the merge could leave the publish with nothing to do -- it cannot, and this is
        // where that stays answered.
        a.orchestrator.syncNow(folder, null)
        assertEquals(setOf("$UID_BLOCK.png"), folder.imageNames())

        a.orchestrator.syncNow(folder, PASS)

        // One pass, not two. A picture left in the clear until some later sync would be a window
        // with nothing bounding it -- the person has been told encryption is on.
        assertEquals(setOf("$UID_BLOCK.tdrlimg"), folder.imageNames())
    }

    // -------------------------------------------- §9.4: a pass writes only what actually changed

    @Test
    fun `a second pass with nothing changed rewrites no page files`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG

        a.orchestrator.writeSnapshots(folder)
        assertEquals(listOf("$UID_PAGE.json"), folder.writtenPageNames)
        folder.writtenPageNames.clear()

        a.orchestrator.writeSnapshots(folder)

        // Page files are the one thing here that scales with the person's data, and each write is
        // a temp-file/rename/delete through SAF that Syncthing then replicates. A thousand
        // unchanged pages must cost a thousand reads and no writes at all.
        assertTrue("an unchanged page must not be republished", folder.writtenPageNames.isEmpty())
    }

    @Test
    fun `an encrypted folder skips the same unchanged page, despite the ciphertext differing`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG

        a.orchestrator.writeSnapshots(folder, PASS)
        folder.writtenPageNames.clear()

        a.orchestrator.writeSnapshots(folder, PASS)

        // The load-bearing half: AES-GCM uses a fresh IV per encryption, so the same page encrypts
        // to different bytes every pass. A comparison made on the stored bytes would never match
        // and this optimisation would silently do nothing at all under §9.4.2 — which is exactly
        // the configuration where write churn is most expensive.
        assertTrue("comparison must be on plaintext, not ciphertext", folder.writtenPageNames.isEmpty())
    }

    @Test
    fun `an edited page is still republished`() = runBlocking {
        val folder = InMemorySyncFileStore()
        val a = Device()
        a.seed(localPath = "local:holiday.png")
        a.localImages.written["holiday.png"] = PNG
        a.orchestrator.writeSnapshots(folder)
        folder.writtenPageNames.clear()

        val page = a.pageDao.getByUid(UID_PAGE)!!
        a.pageDao.update(page.copy(title = "Trip, renamed", updatedAt = at(9_000)))
        a.orchestrator.writeSnapshots(folder)

        // The direction that must never break: any difference at all still writes.
        assertEquals(listOf("$UID_PAGE.json"), folder.writtenPageNames)
    }
}
