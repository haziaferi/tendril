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
        fun at(m: Long): Instant = Instant.ofEpochMilli(m)
    }

    /** One device: its own database, its own private image storage, one shared folder. */
    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val propertyDao = FakePropertyDao(store)
        val localImages = InMemoryLocalImageStore()

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
            purgeRegistry = PurgeRegistry(
                FakePurgedRecordDao(), pageDao, FakeEntryDao(), FakeHabitDao(), propertyDao,
                RecordingEntryScheduleCoordinator(),
            ),
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
}
