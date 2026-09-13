package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The page-merge path — [PagesSyncEngine.mergePages] and the [PurgeRegistry] it consults —
 * which had no coverage at all: [SnapshotSyncConflictTest] mocks the engine out because it is
 * asserting about something else.
 *
 * The purge half is the reason this file exists. Everything else in §9.4's merge only ever
 * *adds*; a tombstone is the one thing in the codebase that deletes a row during a merge, and
 * it does so on a device that never saw the deletion happen. Two rules carry that weight and
 * are pinned here one assertion each:
 *
 *  - **A purge is a timestamped fact, not a veto.** Tombstone and record both carry a time,
 *    and the later one wins — so a purge deletes everywhere, *unless* some device edited the
 *    record afterwards without having seen the purge, in which case the edit resurrects it and
 *    the tombstone is dropped rather than left to fight the same record forever.
 *  - **Ordering is load-bearing.** Tombstones merge and apply before any record file is read.
 *    Reverse the two and a record and the tombstone that kills it cross in one pass, and the
 *    record survives by accident.
 *
 * The rest pins the merge's own shape: last-write-wins per page, the two-pass tree fixup, and
 * the upsert-by-uid on Property that keeps a re-merge from cascade-deleting every row's cell
 * values.
 */
class PageMergeTest {

    // Valid UUIDs, because the merge refuses any uid that isn't one — a page uid is reflected
    // back out as a filename, so `../..` used to escape the sync folder entirely.
    private companion object {
        const val UID_A = "11111111-1111-4111-8111-111111111111"
        const val UID_B = "22222222-2222-4222-8222-222222222222"
        const val UID_DB = "33333333-3333-4333-8333-333333333333"
        const val UID_ROW = "44444444-4444-4444-8444-444444444444"
        const val UID_PROP = "55555555-5555-4555-8555-555555555555"
        const val UID_PROP2 = "66666666-6666-4666-8666-666666666666"
        const val UID_BLOCK = "77777777-7777-4777-8777-777777777777"
        const val UID_NODE = "88888888-8888-4888-8888-888888888888"
        const val UID_NODE2 = "99999999-9999-4999-8999-999999999999"
        const val UID_ENTRY = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    }

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val blockDao = FakeBlockDao(store)
    private val labelDao = FakeLabelDao(store)
    private val pageDatabaseDao = FakePageDatabaseDao(store)
    private val propertyDao = FakePropertyDao(store)
    private val propertyValueDao = FakePropertyValueDao(store)
    private val viewDao = FakePageDatabaseViewDao(store)
    private val canvasDao = FakePageCanvasDao(store)
    private val nodeDao = FakeCanvasNodeDao(store)
    private val edgeDao = FakeCanvasEdgeDao(store)
    private val relationDao = FakePageRelationDao(store)
    private val ftsDao = FakePageFtsDao(store)

    private val purgedDao = FakePurgedRecordDao()
    private val entryDao = FakeEntryDao()
    private val coordinator = RecordingEntryScheduleCoordinator()

    private val purgeRegistry = PurgeRegistry(purgedDao, pageDao, entryDao, FakeHabitDao(), propertyDao, coordinator)
    private val contentRepository = PageContentRepository(pageDao, blockDao, ftsDao)

    private val engine = PagesSyncEngine(
        pageDao = pageDao,
        blockDao = blockDao,
        labelDao = labelDao,
        pageDatabaseDao = pageDatabaseDao,
        propertyDao = propertyDao,
        propertyValueDao = propertyValueDao,
        pageDatabaseViewDao = viewDao,
        pageCanvasDao = canvasDao,
        canvasNodeDao = nodeDao,
        canvasEdgeDao = edgeDao,
        pageRelationDao = relationDao,
        purgeRegistry = purgeRegistry,
        pageContentRepository = contentRepository,
    )

    private fun at(millis: Long): Instant = Instant.ofEpochMilli(millis)

    private fun pageRecord(
        uid: String,
        title: String,
        updatedAt: Long,
        kind: String = PageKind.PAGE.name,
        parentUid: String? = null,
        databaseUid: String? = null,
        labels: List<String> = emptyList(),
        blocks: List<BlockSnapshotRecord> = emptyList(),
        propertyValues: List<PropertyValueSnapshotRecord> = emptyList(),
        database: PageDatabaseSnapshotRecord? = null,
        canvas: CanvasSnapshotRecord? = null,
    ) = PageSnapshotRecord(
        uid = uid, title = title, kind = kind, parentUid = parentUid, databaseUid = databaseUid,
        createdAt = updatedAt, updatedAt = updatedAt, labels = labels, blocks = blocks,
        propertyValues = propertyValues, database = database, canvas = canvas,
    )

    private fun blockRecord(uid: String, content: String, order: Int = 0, at: Long = 1_000L) =
        BlockSnapshotRecord(uid = uid, type = "PARAGRAPH", order = order, content = content, createdAt = at, updatedAt = at)

    private fun localPage(uid: String, title: String, updatedAt: Long, kind: PageKind = PageKind.PAGE): Long =
        store.seedPage(Page(uid = uid, title = title, kind = kind, createdAt = at(updatedAt), updatedAt = at(updatedAt)))

    private suspend fun localEntry(uid: String, title: String, updatedAt: Long): Entry {
        val id = entryDao.insert(
            Entry(
                uid = uid, title = title, kind = EntryKind.TASK,
                startDate = null, startTime = null, endDate = null, endTime = null,
                recurrenceRule = null, createdAt = at(updatedAt), updatedAt = at(updatedAt),
            )
        )
        return entryDao.getById(id)!!
    }

    // ------------------------------------------------------- a purge is a fact, not a veto

    @Test
    fun `a purged page is refused when its own snapshot file comes back`() = runBlocking {
        purgeRegistry.purgePage(localPage(UID_A, "Deleted forever", 1_000L), now = at(2_000L))

        // The file is still sitting in the folder; without the tombstone this re-inserts it.
        engine.mergePages(listOf(pageRecord(UID_A, "Deleted forever", updatedAt = 1_000L)))

        assertNull("the purged page must not come back from its own snapshot file", pageDao.getByUid(UID_A))
    }

    @Test
    fun `a record edited after the purge resurrects it and supersedes the tombstone`() = runBlocking {
        purgeRegistry.purgePage(localPage(UID_A, "Purged here", 1_000L), now = at(2_000L))

        // Another device edited this page at t=3000, never having seen the purge.
        engine.mergePages(listOf(pageRecord(UID_A, "Edited elsewhere after the purge", updatedAt = 3_000L)))

        assertEquals(
            "an edit newer than the purge wins, or a stale delete would destroy live work",
            "Edited elsewhere after the purge",
            pageDao.getByUid(UID_A)?.title,
        )
        assertTrue(
            "the superseded tombstone is dropped, so it stops fighting the same record every pass",
            purgedDao.getForKind(PurgedKind.PAGE).isEmpty(),
        )
    }

    @Test
    fun `a purge from another device deletes the local page`() = runBlocking {
        localPage(UID_A, "Still here on this device", 1_000L)
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L))))

        purgeRegistry.applyToLocalRecords()

        assertNull("a tombstone arriving from another device deletes this device's row", pageDao.getByUid(UID_A))
    }

    @Test
    fun `a local edit after the purge supersedes an arriving tombstone`() = runBlocking {
        localPage(UID_A, "Edited here at t=3000", 3_000L)
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L))))

        purgeRegistry.applyToLocalRecords()

        assertNotNull("a row edited after the purge survives it", pageDao.getByUid(UID_A))
        assertTrue("and the tombstone is dropped as superseded", purgedDao.getAll().isEmpty())
    }

    /** The divergence case: A purges at t=2000 while B, never having seen it, edits at t=3000.
     * Both halves have to reach the same answer or the two devices flip the page forever. */
    @Test
    fun `both sides converge when one device purges and the other edits later`() = runBlocking {
        // Device A's view: it purged, then B's newer record arrives.
        purgeRegistry.purgePage(localPage(UID_A, "Original", 1_000L), now = at(2_000L))
        engine.mergePages(listOf(pageRecord(UID_A, "B's newer edit", updatedAt = 3_000L)))

        // Device B's view: it holds the newer row, then A's tombstone arrives.
        val b = FakePageStore()
        val bPageDao = FakePageDao(b)
        val bPurgedDao = FakePurgedRecordDao()
        val bRegistry = PurgeRegistry(bPurgedDao, bPageDao, FakeEntryDao(), FakeHabitDao(), FakePropertyDao(b), RecordingEntryScheduleCoordinator())
        b.seedPage(Page(uid = UID_A, title = "B's newer edit", createdAt = at(3_000L), updatedAt = at(3_000L)))
        bRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L))))
        bRegistry.applyToLocalRecords()

        assertEquals("A keeps the newer edit", "B's newer edit", pageDao.getByUid(UID_A)?.title)
        assertEquals("and B agrees", "B's newer edit", bPageDao.getByUid(UID_A)?.title)
        assertTrue("neither device is left holding a live tombstone", purgedDao.getAll().isEmpty() && bPurgedDao.getAll().isEmpty())
    }

    @Test
    fun `purging a page records the tombstone and drops the row in one operation`() = runBlocking {
        val id = localPage(UID_A, "Goodbye", 1_000L)

        purgeRegistry.purgePage(id, now = at(2_000L))

        assertNull(pageDao.getById(id))
        assertEquals(
            listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L))),
            purgedDao.getForKind(PurgedKind.PAGE),
        )
    }

    @Test
    fun `the second device to purge the same record does not move the timestamp`() = runBlocking {
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L))))
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(5_000L))))

        assertEquals(
            "two devices purging the same record is not a conflict; the earlier timestamp wins",
            listOf(at(2_000L)),
            purgedDao.getForKind(PurgedKind.PAGE).map { it.purgedAt },
        )
    }

    /** The same two tombstones in the other order. Keeping whichever arrived *first* would
     * leave this device on 5000 and the other on 2000, and a third device's edit at, say, 3000
     * would then supersede the purge on one and lose to it on the other — the two disagreeing
     * about whether the page exists, for good. */
    @Test
    fun `an earlier tombstone arriving second still wins, so devices cannot diverge`() = runBlocking {
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(5_000L))))
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L))))

        assertEquals(
            "adoption has to be order-independent or two devices settle on different answers",
            listOf(at(2_000L)),
            purgedDao.getForKind(PurgedKind.PAGE).map { it.purgedAt },
        )
    }

    @Test
    fun `one file carrying two tombstones for the same record keeps the earlier`() = runBlocking {
        purgeRegistry.adopt(
            listOf(
                PurgedRecord(PurgedKind.PAGE, UID_A, at(5_000L)),
                PurgedRecord(PurgedKind.PAGE, UID_A, at(2_000L)),
            )
        )

        assertEquals(listOf(at(2_000L)), purgedDao.getForKind(PurgedKind.PAGE).map { it.purgedAt })
    }

    @Test
    fun `purgedPageFileNames names the snapshot file the write pass should drop`() = runBlocking {
        purgeRegistry.purgePage(localPage(UID_A, "Goodbye", 1_000L), now = at(2_000L))

        assertEquals(setOf("$UID_A.json"), engine.purgedPageFileNames())
    }

    // -------------------------------------------------------------- entries, and their alarms

    @Test
    fun `a purge arriving from another device takes an entry's alarms down before its row`() = runBlocking {
        val entry = localEntry(UID_ENTRY, "Standup", 1_000L)
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.ENTRY, UID_ENTRY, at(2_000L))))

        purgeRegistry.applyToLocalRecords()

        assertNull("the entry row is gone", entryDao.getByUid(UID_ENTRY))
        assertEquals(
            "an alarm outliving its row is a wakeup for nothing, and this device never saw the purge coming",
            listOf(entry.uid),
            coordinator.removed.map { it.uid },
        )
    }

    @Test
    fun `an entry edited after the purge survives and keeps its alarms`() = runBlocking {
        localEntry(UID_ENTRY, "Standup, moved", 3_000L)
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.ENTRY, UID_ENTRY, at(2_000L))))

        purgeRegistry.applyToLocalRecords()

        assertNotNull(entryDao.getByUid(UID_ENTRY))
        assertTrue("nothing was torn down for a row that survived", coordinator.removed.isEmpty())
    }

    // ------------------------------------------------------------------ last-write-wins

    @Test
    fun `a new page is inserted with its blocks and labels`() = runBlocking {
        engine.mergePages(
            listOf(
                pageRecord(
                    UID_A, "Arrived", updatedAt = 1_000L,
                    labels = listOf("work", "urgent"),
                    blocks = listOf(blockRecord(UID_BLOCK, "hello world")),
                )
            )
        )

        val page = requireNotNull(pageDao.getByUid(UID_A)) { "the page should have been inserted" }
        assertEquals(listOf("hello world"), blockDao.getForPage(page.id).map { it.content })
        assertEquals(setOf("work", "urgent"), labelDao.getForPage(page.id).map { it.name }.toSet())
        assertEquals("the FTS index is rebuilt as part of the same write, title first (§3.1.1)", "Arrived hello world", store.fts[page.id])
    }

    @Test
    fun `a newer remote record replaces the local one`() = runBlocking {
        val id = localPage(UID_A, "Local title", 1_000L)

        engine.mergePages(listOf(pageRecord(UID_A, "Remote title", updatedAt = 2_000L)))

        assertEquals("Remote title", pageDao.getById(id)?.title)
    }

    @Test
    fun `an older remote record loses and leaves local content alone`() = runBlocking {
        val id = localPage(UID_A, "Local title", 5_000L)
        engine.mergePages(listOf(pageRecord(UID_A, "Newer here", updatedAt = 6_000L, blocks = listOf(blockRecord(UID_BLOCK, "kept")))))

        engine.mergePages(listOf(pageRecord(UID_A, "Stale remote", updatedAt = 2_000L)))

        assertEquals("Newer here", pageDao.getById(id)?.title)
        assertEquals("a loser must not wipe the winner's blocks", listOf("kept"), blockDao.getForPage(id).map { it.content })
    }

    @Test
    fun `a block reference travels by uid, verbatim, and needs no source to merge`() = runBlocking {
        // §0.6.12 — the source block lives on a page this device has never seen: the record
        // merges anyway (a block uid is never resolved), and the card falls back to its cache.
        val id = localPage(UID_A, "Notes", 1_000L)
        val reference = BlockSnapshotRecord(
            uid = UID_BLOCK, type = "BLOCK_REFERENCE", order = 0, content = "cached words",
            referencedBlockUid = "block-on-a-page-not-here", createdAt = 2_000L, updatedAt = 2_000L,
        )
        engine.mergePages(listOf(pageRecord(UID_A, "Notes", updatedAt = 2_000L, blocks = listOf(reference))))

        val merged = blockDao.getForPage(id).single()
        assertEquals(BlockType.BLOCK_REFERENCE, merged.type)
        assertEquals("block-on-a-page-not-here", merged.referencedBlockUid)
        assertEquals("cached words", merged.content)
        assertEquals("and goes back out unchanged", "block-on-a-page-not-here", engine.exportPages().single().blocks.single().referencedBlockUid)
    }

    @Test
    fun `a local image reference survives a merge that rebuilds its block`() = runBlocking {
        val id = localPage(UID_A, "Trip", 1_000L)
        engine.mergePages(listOf(pageRecord(UID_A, "Trip", updatedAt = 2_000L, blocks = listOf(blockRecord(UID_BLOCK, "caption")))))
        // Attached after the block exists. `Block.imagePath` points into app-private storage and
        // is deliberately excluded from the snapshot, so no peer can ever send it back.
        //
        // S4 did not change this, which is worth stating because it easily could have. The
        // snapshot now carries `imageName` — *which* image — but still never `imagePath`, where
        // this device keeps it. So the rule below holds exactly as written, and the uid re-attach
        // is still the only thing carrying a local path across a rebuild.
        blockDao.getForPage(id).single().let { blockDao.update(it.copy(imagePath = "/data/app/img-1.png")) }

        // The peer retitles the caption. Its record wins, and Pass 5 rebuilds every block here.
        engine.mergePages(listOf(pageRecord(UID_A, "Trip", updatedAt = 3_000L, blocks = listOf(blockRecord(UID_BLOCK, "new caption")))))

        val merged = blockDao.getForPage(id).single()
        assertEquals("the winner's content still applies", "new caption", merged.content)
        assertEquals(
            "the merge is the only thing that can carry imagePath across, since the snapshot holds " +
                "the image's name but never this device's path to it",
            "/data/app/img-1.png",
            merged.imagePath,
        )
    }

    @Test
    fun `a page's parent resolves even when the parent arrives later in the batch`() = runBlocking {
        // Child first: pass 1 gives every page an id, so pass 2 can resolve the FK for real.
        engine.mergePages(
            listOf(
                pageRecord(UID_B, "Child", updatedAt = 1_000L, parentUid = UID_A),
                pageRecord(UID_A, "Parent", updatedAt = 1_000L),
            )
        )

        val parent = requireNotNull(pageDao.getByUid(UID_A)) { "the parent should have been inserted" }
        assertEquals(parent.id, pageDao.getByUid(UID_B)?.parentId)
    }

    @Test
    fun `a uid that is not a UUID is refused before anything reads it`() = runBlocking {
        engine.mergePages(
            listOf(
                pageRecord("../../etc/passwd", "Traversal", updatedAt = 1_000L),
                pageRecord(UID_A, "Legitimate", updatedAt = 1_000L),
            )
        )

        assertEquals(
            "a page uid becomes a filename on the next write pass, so only real UUIDs are let through",
            listOf("Legitimate"),
            pageDao.getAll().map { it.title },
        )
    }

    // --------------------------------------------------------------- databases and rows

    private fun databaseRecord(updatedAt: Long, properties: List<PropertySnapshotRecord>) = pageRecord(
        UID_DB, "Tasks", updatedAt = updatedAt, kind = PageKind.DATABASE.name,
        database = PageDatabaseSnapshotRecord(properties = properties),
    )

    private fun databaseBatch(updatedAt: Long, properties: List<PropertySnapshotRecord>) = listOf(
        databaseRecord(updatedAt, properties),
        pageRecord(
            UID_ROW, "A row", updatedAt = updatedAt, databaseUid = UID_DB,
            propertyValues = properties.map { PropertyValueSnapshotRecord(it.uid, "cell for ${it.name}") },
        ),
    )

    @Test
    fun `a row's property values resolve against the database schema in the same batch`() = runBlocking {
        engine.mergePages(databaseBatch(1_000L, listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0))))

        val row = requireNotNull(pageDao.getByUid(UID_ROW)) { "the row should have been inserted" }
        assertEquals(listOf("cell for Status"), propertyValueDao.getForRow(row.id).map { it.value })
    }

    /** The second pass deliberately carries the *database page only*. That is what makes this
     * bite: if the row came too, Pass 5 would re-insert its cell values from the record and a
     * cascade-delete underneath would leave no trace. Someone editing a database's schema on
     * one device without touching its rows is the ordinary case, and the one that loses data. */
    @Test
    fun `re-merging a database on its own keeps every row's cell values`() = runBlocking {
        val properties = listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0))
        engine.mergePages(databaseBatch(1_000L, properties))
        val rowId = pageDao.getByUid(UID_ROW)!!.id
        val propertyIdBefore = propertyDao.getAll().single { it.uid == UID_PROP }.id

        engine.mergePages(listOf(databaseRecord(2_000L, properties)))

        assertEquals(
            "the Property keeps its local id, so PropertyValue's cascade never fires",
            propertyIdBefore,
            propertyDao.getAll().single { it.uid == UID_PROP }.id,
        )
        assertEquals(
            "the row was never in this batch — its cells have to survive on their own",
            listOf("cell for Status"),
            propertyValueDao.getForRow(rowId).map { it.value },
        )
    }

    @Test
    fun `renaming a property in place does not disturb the values under it`() = runBlocking {
        engine.mergePages(databaseBatch(1_000L, listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0))))
        val rowId = pageDao.getByUid(UID_ROW)!!.id

        engine.mergePages(listOf(databaseRecord(2_000L, listOf(PropertySnapshotRecord(UID_PROP, "State", "TEXT", order = 0)))))

        assertEquals(listOf("State"), propertyDao.getAll().map { it.name })
        assertEquals(listOf("cell for Status"), propertyValueDao.getForRow(rowId).map { it.value })
    }

    @Test
    fun `a purged property is deleted, and a peer's schema cannot bring it back`() = runBlocking {
        val both = listOf(
            PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0),
            PropertySnapshotRecord(UID_PROP2, "Notes", "TEXT", order = 1),
        )
        engine.mergePages(databaseBatch(1_000L, both))

        // Another device deleted "Notes" for good, and its tombstone arrives.
        purgeRegistry.adopt(listOf(PurgedRecord(PurgedKind.PROPERTY, UID_PROP2, at(2_000L))))
        purgeRegistry.applyToLocalRecords()

        assertEquals(listOf("Status"), propertyDao.getAll().map { it.name })

        // That device's peer has not seen the deletion yet and re-exports the old schema. Unlike
        // a Page, a Property carries no timestamp of its own to supersede a tombstone with, and
        // it needs none: `Property.uid` is minted fresh, so re-adding a deleted column produces a
        // *different* property that no tombstone names. A uid that is tombstoned is gone for good.
        engine.mergePages(listOf(databaseRecord(3_000L, both)))

        assertEquals("a tombstoned column must not be re-inserted", listOf("Status"), propertyDao.getAll().map { it.name })
    }

    @Test
    fun `a property missing from a remote schema is kept, not deleted`() = runBlocking {
        val both = listOf(
            PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0),
            PropertySnapshotRecord(UID_PROP2, "Notes", "TEXT", order = 1),
        )
        engine.mergePages(databaseBatch(1_000L, both))
        val rowId = pageDao.getByUid(UID_ROW)!!.id
        assertEquals(2, propertyValueDao.getForRow(rowId).size)

        // A device that has not yet seen "Notes" re-exports the schema without it. Absence is
        // "hasn't arrived", never "was deleted" -- the rule SnapshotSyncOrchestrator.readAndMerge
        // states for every other record, and which a real deletion signals with a tombstone.
        engine.mergePages(listOf(databaseRecord(2_000L, both.take(1))))

        assertEquals(setOf("Status", "Notes"), propertyDao.getAll().map { it.name }.toSet())
        assertEquals("the column's cells must survive with it", 2, propertyValueDao.getForRow(rowId).size)
    }

    // ------------------------------------------------------------------------- canvas

    @Test
    fun `canvas nodes and edges are rebuilt for a winning record`() = runBlocking {
        val canvas = CanvasSnapshotRecord(
            nodes = listOf(
                CanvasNodeSnapshotRecord(UID_NODE, "TEXT", 0f, 0f, 180f, 90f, text = "from", createdAt = 1_000L, updatedAt = 1_000L),
                CanvasNodeSnapshotRecord(UID_NODE2, "TEXT", 10f, 10f, 180f, 90f, text = "to", createdAt = 1_000L, updatedAt = 1_000L),
            ),
            edges = listOf(CanvasEdgeSnapshotRecord(UID_NODE, UID_NODE2, "ONE_WAY", label = "leads to")),
        )

        engine.mergePages(listOf(pageRecord(UID_A, "Board", updatedAt = 1_000L, kind = PageKind.CANVAS.name, canvas = canvas)))

        val canvasId = canvasDao.getByPageId(pageDao.getByUid(UID_A)!!.id)!!.id
        assertEquals(setOf("from", "to"), nodeDao.getForCanvas(canvasId).mapNotNull { it.text }.toSet())
        assertEquals(listOf("leads to"), edgeDao.getForCanvas(canvasId).mapNotNull { it.label })
    }

    @Test
    fun `an edge whose endpoint is missing is dropped rather than dangling`() = runBlocking {
        val canvas = CanvasSnapshotRecord(
            nodes = listOf(
                CanvasNodeSnapshotRecord(UID_NODE, "TEXT", 0f, 0f, 180f, 90f, text = "from", createdAt = 1_000L, updatedAt = 1_000L),
            ),
            edges = listOf(CanvasEdgeSnapshotRecord(UID_NODE, UID_NODE2, "ONE_WAY")),
        )

        engine.mergePages(listOf(pageRecord(UID_A, "Board", updatedAt = 1_000L, kind = PageKind.CANVAS.name, canvas = canvas)))

        val canvasId = canvasDao.getByPageId(pageDao.getByUid(UID_A)!!.id)!!.id
        assertTrue(edgeDao.getForCanvas(canvasId).isEmpty())
    }

    // ---------------------------------------------------------------------- relations

    @Test
    fun `relations merge once and skip self-relations`() = runBlocking {
        engine.mergePages(listOf(pageRecord(UID_A, "A", updatedAt = 1_000L), pageRecord(UID_B, "B", updatedAt = 1_000L)))

        engine.mergeRelations(
            listOf(
                PageRelationSnapshotRecord(UID_A, UID_B, 1_000L),
                PageRelationSnapshotRecord(UID_B, UID_A, 1_000L),
                PageRelationSnapshotRecord(UID_A, UID_A, 1_000L),
            )
        )

        assertEquals("the reverse pair is the same edge, and a self-relation is not one", 1, relationDao.getAll().size)
    }

    @Test
    fun `a relation to a page that has not merged in yet is dropped, not faked`() = runBlocking {
        engine.mergePages(listOf(pageRecord(UID_A, "A", updatedAt = 1_000L)))

        engine.mergeRelations(listOf(PageRelationSnapshotRecord(UID_A, UID_B, 1_000L)))

        assertTrue(relationDao.getAll().isEmpty())
    }
}
