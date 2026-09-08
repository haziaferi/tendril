package com.tendril.app.sync

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.parseRelationConfig
import com.tendril.app.data.pagedatabase.parseRelationValue
import com.tendril.app.data.pagedatabase.setValue
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.ui.canvas.CanvasViewModel
import com.tendril.app.ui.pages.PageDatabaseViewModel
import com.tendril.app.ui.pages.PageDetailViewModel
import com.tendril.app.ui.pages.PagesViewModel
import com.tendril.app.ui.roadmap.RoadMapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * §9.4 — that an edit made through the app's own write path actually *reaches the other device*.
 *
 * [PageMergeTest] covers the merge itself, and covers it well, but every record it merges is
 * hand-built by `pageRecord(uid, title, updatedAt = N)`. That is the one thing it structurally
 * cannot check: whether the app, having changed a page, produces a record whose `updatedAt` says
 * so. It did not. The merge's LWW gate reads `pages.updatedAt` and nothing else, while a cell
 * edit wrote only `property_values` and a block edit only `blocks.updatedAt` — so the exported
 * snapshot carried new content under an unchanged timestamp, lost on the peer for not being
 * newer, and was skipped by the passes that apply schema, canvas, blocks and cells. It was not
 * even recorded as a conflict: an *equal* `updatedAt` is the same version by definition, so the
 * record never reached `candidateLosers` and no `.tendril-lost-*.json` was written either. The
 * edit was gone with nothing anywhere to say it had existed.
 *
 * So these tests deliberately refuse to build a snapshot record. They mutate through the real
 * ViewModel, export, and merge into a second store — the only arrangement in which the defect is
 * visible at all. On the editing device the change is right there in the database, looking
 * perfectly saved; that is why it survived this long.
 *
 * Every test installs `Dispatchers.Main`, because `viewModelScope` dispatches on it and a JVM
 * unit test has no such thing until it says so.
 */
class WritePathSyncTest {

    /** Older than any `Instant.now()` a write path can produce, so a moved timestamp is
     * unambiguously a bump rather than a rounding accident. */
    private val t0: Instant = Instant.ofEpochMilli(1_000L)

    /** Unconfined rather than Standard: every mutation under test is `viewModelScope.launch { }`
     * over in-memory fakes that never really suspend, so running them eagerly on the calling
     * thread lets each test read as the sequence of user actions it describes. */
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun installMainDispatcher() = Dispatchers.setMain(mainDispatcher)

    @After
    fun restoreMainDispatcher() = Dispatchers.resetMain()

    /**
     * One device: its whole local store, the engine that exports from and merges into it, and the
     * real ViewModels wired to the same DAOs the app wires them to.
     *
     * Shares [FakePageStore] with [PageMergeTest] rather than growing a second set of doubles —
     * the `ON DELETE CASCADE` behaviour that store models is as load-bearing here as it is there.
     */
    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val tagDao = FakeTagDao(store)
        val pageDatabaseDao = FakePageDatabaseDao(store)
        val propertyDao = FakePropertyDao(store)
        val propertyValueDao = FakePropertyValueDao(store)
        val viewDao = FakePageDatabaseViewDao(store)
        val canvasDao = FakePageCanvasDao(store)
        val nodeDao = FakeCanvasNodeDao(store)
        val edgeDao = FakeCanvasEdgeDao(store)
        val relationDao = FakePageRelationDao(store)
        val ftsDao = FakePageFtsDao(store)

        val entryDao = FakeEntryDao()
        val completionDao = FakeEntryCompletionDao()
        val coordinator = RecordingEntryScheduleCoordinator()

        val contentRepository = PageContentRepository(blockDao, ftsDao)
        val resolveEntryUseCase = ResolveEntryUseCase(entryDao, completionDao, coordinator)
        val templateManager = TemplateManager(pageDao, blockDao, pageDatabaseDao, propertyDao)
        val databaseSyncManager =
            DatabaseSyncManager(pageDao, pageDatabaseDao, propertyValueDao, entryDao, completionDao, resolveEntryUseCase)
        val viewLockState = ViewLockState()
        val checkboxOnlyState = CheckboxOnlyState()

        val purgedDao = FakePurgedRecordDao()
        val purgeRegistry = PurgeRegistry(purgedDao, pageDao, entryDao, FakeHabitDao(), propertyDao, coordinator)

        val engine = PagesSyncEngine(
            pageDao = pageDao,
            blockDao = blockDao,
            tagDao = tagDao,
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

        fun detail(pageId: Long) = PageDetailViewModel(
            pageId, pageDao, blockDao, tagDao, propertyDao, propertyValueDao, pageDatabaseDao, entryDao,
            resolveEntryUseCase, coordinator, contentRepository, templateManager, viewLockState, checkboxOnlyState,
        )

        fun database(pageId: Long) = PageDatabaseViewModel(
            pageId, pageDao, pageDatabaseDao, propertyDao, propertyValueDao, entryDao, viewDao, blockDao,
            databaseSyncManager, resolveEntryUseCase, coordinator, templateManager, purgeRegistry, viewLockState,
        )

        fun canvas(pageId: Long) = CanvasViewModel(pageId, pageDao, canvasDao, nodeDao, edgeDao, viewLockState)

        /**
         * The Pages list itself (§3.1) — the only surface that *creates* pages, canvases and
         * whole databases, and the only one that empties the Trash via [PurgeRegistry].
         *
         * Takes no `pageId`, unlike the three factories above: it is scoped to the tree, not to
         * a page. That is also why it matters for §3.1.2's View-Only lock — a create is a write
         * even though there is no page open to gate it against, and a "Delete forever" is the
         * one write in the app that leaves a tombstone rather than a record.
         */
        fun pages() = PagesViewModel(
            pageDao, pageDatabaseDao, propertyDao, ftsDao, tagDao, purgeRegistry, databaseSyncManager,
            templateManager, viewLockState,
        )

        /**
         * §3.4's Road Map. Also unscoped — the map is over every page, and [RoadMapViewModel]'s
         * own `setFocus` narrows it *after* construction rather than through the constructor,
         * so this factory deliberately takes no page id (see `RoadMapViewModel.kt:50-54`).
         *
         * Its one mutation, `relate`, writes a [com.tendril.app.data.page.PageRelation] through
         * the same [relationDao] the sync engine's relation pass reads, so a relation made here
         * is a relation the export carries.
         *
         * Note it constructs eagerly: `init { refresh() }` launches on `viewModelScope`, which
         * is why every caller must already be inside `runTest(mainDispatcher)`.
         */
        fun roadmap() = RoadMapViewModel(pageDao, relationDao, contentRepository, viewLockState)

        /** Local ids are each device's own; a page is the same page across devices only by `uid`. */
        suspend fun pageIdOf(uid: String): Long =
            requireNotNull(pageDao.getByUid(uid)) { "no page with uid $uid on this device" }.id
    }

    private val a = Device()
    private val b = Device()

    /** One sync pass in one direction, exactly as the folder sync runs it: A writes its snapshot
     * out, B reads it in. */
    private suspend fun syncAtoB() {
        // Tombstones first, then records -- the order SnapshotSyncOrchestrator uses, so a record
        // and the tombstone that kills it cannot cross in the same pass.
        b.purgeRegistry.adopt(a.purgedDao.getAll())
        b.purgeRegistry.applyToLocalRecords()
        b.engine.mergePages(a.engine.exportPages())
    }

    private suspend fun seedPageOnA(title: String, kind: PageKind = PageKind.PAGE): Page {
        val id = a.store.seedPage(Page(title = title, kind = kind, createdAt = t0, updatedAt = t0))
        return a.pageDao.getById(id)!!
    }

    /** A database page, one TEXT property, and one row holding a value under it. */
    private data class SeededDatabase(val databasePage: Page, val row: Page, val property: Property)

    private suspend fun seedDatabaseOnA(): SeededDatabase {
        val databasePage = seedPageOnA("Tasks", PageKind.DATABASE)
        val databaseId = a.pageDatabaseDao.insert(PageDatabase(pageId = databasePage.id, createdAt = t0, updatedAt = t0))
        val propertyId = a.propertyDao.insert(Property(databaseId = databaseId, name = "Status", type = PropertyType.TEXT, order = 0))
        val rowId = a.store.seedPage(Page(title = "A row", databaseId = databaseId, createdAt = t0, updatedAt = t0))
        a.propertyValueDao.setValue(propertyId, rowId, "before")
        return SeededDatabase(databasePage, a.pageDao.getById(rowId)!!, a.propertyDao.getById(propertyId)!!)
    }

    // ------------------------------------------------------------------------ page content

    @Test
    fun `a block edited on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 0, content = "before", createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.detail(page.id).updateBlockContent(a.blockDao.getForPage(page.id).single(), "after")
        syncAtoB()

        assertEquals(
            "the exported block carries the edit, but the merge only applies it if the page row says the page changed",
            listOf("after"),
            b.blockDao.getForPage(b.pageIdOf(page.uid)).map { it.content },
        )
    }

    @Test
    fun `a block added on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 0, content = "first", createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.detail(page.id).addBlock(BlockType.PARAGRAPH, afterOrder = 0, content = "second")
        syncAtoB()

        assertEquals(listOf("first", "second"), b.blockDao.getForPage(b.pageIdOf(page.uid)).map { it.content })
    }

    /** `setChecked` is the one block mutation deliberately outside `launchAndReindex` — §3.1.2's
     * checkbox-only mode keeps a to-do tappable while the rest of the page is locked — so it
     * needs its own bump, and its own test. */
    @Test
    fun `a checkbox ticked on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Groceries")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.TODO, order = 0, content = "milk", checked = false, createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.detail(page.id).setChecked(a.blockDao.getForPage(page.id).single(), true)
        syncAtoB()

        assertEquals(listOf(true), b.blockDao.getForPage(b.pageIdOf(page.uid)).map { it.checked })
    }

    /** Tags are not blocks and travel in their own field of the snapshot, but they are gated by
     * the same one timestamp as everything else hanging off the page. */
    @Test
    fun `a tag added on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        syncAtoB()

        a.detail(page.id).addTag("urgent")
        syncAtoB()

        assertEquals(listOf("urgent"), b.tagDao.getForPage(b.pageIdOf(page.uid)).map { it.name })
    }

    // --------------------------------------------------------------- database cells and schema

    @Test
    fun `a cell edited from the table reaches the other device`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        syncAtoB()

        a.database(seeded.databasePage.id).setCellValue(seeded.property, seeded.row, "after")
        syncAtoB()

        assertEquals(
            "a cell hangs off the ROW's page, so the row is the page whose timestamp has to move",
            listOf("after"),
            b.propertyValueDao.getForRow(b.pageIdOf(seeded.row.uid)).map { it.value },
        )
    }

    /** The same cell edited from the row's own page instead of the table: different ViewModel,
     * different function, identical exposure. */
    @Test
    fun `a cell edited from the row's own page reaches the other device`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        syncAtoB()

        a.detail(seeded.row.id).setRowPropertyValue(seeded.property, "after")
        syncAtoB()

        assertEquals(listOf("after"), b.propertyValueDao.getForRow(b.pageIdOf(seeded.row.uid)).map { it.value })
    }

    /** Schema rather than content: it merges in Pass 3, off the *database* page's record, so it
     * needs the database page bumped and no row would do. */
    @Test
    fun `a column's type changed on one device reaches the other`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        syncAtoB()

        a.database(seeded.databasePage.id).confirmChangeType(seeded.property, PropertyType.NUMBER)
        syncAtoB()

        assertEquals(listOf(PropertyType.NUMBER), b.propertyDao.getAll().map { it.type })
    }

    @Test
    fun `a column deleted on one device is deleted on the other`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        syncAtoB()
        assertEquals(1, b.propertyDao.getAll().size)

        a.database(seeded.databasePage.id).apply {
            requestDeleteProperty(seeded.property)
            confirmDeleteProperty()
        }
        syncAtoB()

        assertEquals(
            "absence no longer deletes, so a real deletion has to travel as its own tombstone",
            emptyList<String>(),
            b.propertyDao.getAll().map { it.name },
        )
    }

    // -------------------------------------------------------------------- relation property (DB1)

    /** Two empty databases on A, "Tasks" and "Projects" — the tasks page (with its database id
     * already resolved) and the projects database's own id, which is all [addRelationProperty]
     * needs as a target. */
    private suspend fun seedTwoDatabasesOnA(): Pair<Page, Long> {
        val tasksPage = seedPageOnA("Tasks", PageKind.DATABASE)
        val tasksDbId = a.pageDatabaseDao.insert(PageDatabase(pageId = tasksPage.id, createdAt = t0, updatedAt = t0))
        val projectsPage = seedPageOnA("Projects", PageKind.DATABASE)
        val projectsDbId = a.pageDatabaseDao.insert(PageDatabase(pageId = projectsPage.id, createdAt = t0, updatedAt = t0))
        return a.pageDao.getById(tasksPage.id)!!.copy(databaseId = tasksDbId) to projectsDbId
    }

    /** §5.4/DB1 — "plus auto-creation of the reverse property so the link is genuinely
     * two-way." Both properties exist the moment the forward one is created, paired by uid
     * rather than only by database, so two relation properties between the same pair of
     * databases would never be confused with each other. */
    @Test
    fun `creating a relation property also creates its reverse in the target database`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id

        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)

        val forward = a.propertyDao.getForDatabase(tasksDbId).single()
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single()

        assertEquals("Project", forward.name)
        assertEquals(PropertyType.RELATION, forward.type)
        assertEquals(PropertyType.RELATION, reverse.type)
        assertEquals(
            "the reverse is named after the source database, a starting value the person can rename",
            "Tasks",
            reverse.name,
        )
        assertEquals(
            "the reverse property's config points back at the forward one by its own uid, not just at the database",
            forward.uid,
            parseRelationConfig(reverse.config)?.reversePropertyUid,
        )
        assertEquals(
            "and the forward property's config points at the reverse one, symmetrically",
            reverse.uid,
            parseRelationConfig(forward.config)?.reversePropertyUid,
        )
    }

    /** Relating never one-way, even before anything syncs: the whole point of pairing the
     * properties at creation is that editing either row's cell updates the other row's cell on
     * the *same device*, in the same write. */
    @Test
    fun `relating two rows writes the reverse cell locally, before any sync`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val taskRow = a.pageDao.getById(a.store.seedPage(Page(title = "Ship it", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!

        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single()
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single()

        a.database(tasksPage.id).setRelationValue(forward, taskRow, setOf(projectRow.uid))

        assertEquals(
            "this row's own cell holds the related row's uid",
            setOf(projectRow.uid),
            parseRelationValue(a.propertyValueDao.getForPropertyAndRow(forward.id, taskRow.id)?.value),
        )
        assertEquals(
            "and the OTHER row's reverse cell was written in the same call, not left for a sync pass to fill in",
            setOf(taskRow.uid),
            parseRelationValue(a.propertyValueDao.getForPropertyAndRow(reverse.id, projectRow.id)?.value),
        )
    }

    /** Removing a related row from one side removes the edge from both, the same as adding —
     * the diff in [PageDatabaseViewModel.setRelationValue] has to reach the removed set too, not
     * only the added one. */
    @Test
    fun `un-relating a row clears the reverse cell locally too`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val taskRow = a.pageDao.getById(a.store.seedPage(Page(title = "Ship it", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!

        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single()
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single()
        a.database(tasksPage.id).setRelationValue(forward, taskRow, setOf(projectRow.uid))

        a.database(tasksPage.id).setRelationValue(forward, taskRow, emptySet())

        assertEquals(emptySet<String>(), parseRelationValue(a.propertyValueDao.getForPropertyAndRow(forward.id, taskRow.id)?.value))
        assertEquals(
            "the reverse cell has to lose the edge too, or the two rows disagree about being related",
            emptySet<String>(),
            parseRelationValue(a.propertyValueDao.getForPropertyAndRow(reverse.id, projectRow.id)?.value),
        )
    }

    /** The acceptance test named for DB1 in the build plan, verbatim: "Relate row X to row Y; Y
     * shows X in its reverse property; both sync." Both databases, both rows and the paired
     * properties are created on A and synced to B *before* the relation is made, so this proves
     * the relation edit itself travels — not merely that two databases created together do. */
    @Test
    fun `a relation made on one device is visible from both sides on the other`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val taskRow = a.pageDao.getById(a.store.seedPage(Page(title = "Ship it", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!
        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single()
        syncAtoB()

        a.database(tasksPage.id).setRelationValue(forward, taskRow, setOf(projectRow.uid))
        syncAtoB()

        val bTaskRowId = b.pageIdOf(taskRow.uid)
        val bProjectRowId = b.pageIdOf(projectRow.uid)
        val bForward = b.propertyDao.getAll().single { it.name == "Project" }
        val bReverse = b.propertyDao.getAll().single { it.name == "Tasks" }

        assertEquals(
            "X's own side: the task row relates to the project row",
            setOf(projectRow.uid),
            parseRelationValue(b.propertyValueDao.getForPropertyAndRow(bForward.id, bTaskRowId)?.value),
        )
        assertEquals(
            "Y shows X in its reverse property, per the acceptance test's own wording",
            setOf(taskRow.uid),
            parseRelationValue(b.propertyValueDao.getForPropertyAndRow(bReverse.id, bProjectRowId)?.value),
        )
    }

    // ------------------------------------------------------------------------------ canvas

    @Test
    fun `a canvas node's text edited on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Board", PageKind.CANVAS)
        val canvasId = a.canvasDao.insert(PageCanvas(pageId = page.id, createdAt = t0, updatedAt = t0))
        a.nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = 0f, y = 0f, text = "before", createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.canvas(page.id).setNodeText(a.nodeDao.getForCanvas(canvasId).single(), "after")
        syncAtoB()

        assertEquals(listOf("after"), b.nodesOfCanvasPage(page.uid).map { it.text })
    }

    @Test
    fun `a canvas node moved on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Board", PageKind.CANVAS)
        val canvasId = a.canvasDao.insert(PageCanvas(pageId = page.id, createdAt = t0, updatedAt = t0))
        a.nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = 0f, y = 0f, text = "node", createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.canvas(page.id).moveNode(a.nodeDao.getForCanvas(canvasId).single(), x = 120f, y = 240f)
        syncAtoB()

        assertEquals(listOf(120f to 240f), b.nodesOfCanvasPage(page.uid).map { it.x to it.y })
    }

    private suspend fun Device.nodesOfCanvasPage(uid: String): List<CanvasNode> {
        val canvas = requireNotNull(canvasDao.getByPageId(pageIdOf(uid))) { "the canvas should have merged" }
        return nodeDao.getForCanvas(canvas.id)
    }

    // ------------------------------------------------------------------- and LWW is still LWW

    /**
     * The fix moves a timestamp; it must not move one that should have stayed put. Opening a page
     * is not an edit, and a device that bumped on open would win every race against the device
     * that actually changed something.
     */
    @Test
    fun `opening a page does not move its timestamp`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 0, content = "untouched", createdAt = t0, updatedAt = t0))

        a.detail(page.id)
        a.database(page.id)
        a.canvas(page.id)

        assertEquals(t0, a.pageDao.getById(page.id)?.updatedAt)
    }

    /** The other half of the same guarantee, end to end: B's later edit still beats A's copy. */
    @Test
    fun `a newer edit from the other device still wins`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 0, content = "from A", createdAt = t0, updatedAt = t0))
        syncAtoB()

        val remoteId = b.pageIdOf(page.uid)
        b.detail(remoteId).updateBlockContent(b.blockDao.getForPage(remoteId).single(), "from B, later")

        a.engine.mergePages(b.engine.exportPages())

        assertEquals(listOf("from B, later"), a.blockDao.getForPage(page.id).map { it.content })
        assertTrue("and A's row moved with it", a.pageDao.getById(page.id)!!.updatedAt.isAfter(t0))
    }
}

/**
 * Reached only because [ResolveEntryUseCase] is a constructor argument of the ViewModels under
 * test; nothing here asserts on completions. Hand-written to match the rest of this package, and
 * because two members is less code than the `every { }` stanzas would be.
 */
private class FakeEntryCompletionDao : EntryCompletionDao {
    private val rows = mutableListOf<EntryCompletion>()

    override suspend fun insert(completion: EntryCompletion): Long {
        rows += completion
        return rows.size.toLong()
    }

    override fun observeForEntry(entryId: Long): Flow<List<EntryCompletion>> =
        flowOf(rows.filter { it.entryId == entryId })
}
