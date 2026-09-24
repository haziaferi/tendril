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
import com.tendril.app.data.prefs.MapKeyValueStore
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.RollupAggregation
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.data.pagedatabase.parseRelationConfig
import com.tendril.app.data.pagedatabase.parseRelationValue
import com.tendril.app.data.pagedatabase.setValue
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.LabelMembership
import com.tendril.app.domain.formula.FormulaCheckResult
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.ui.canvas.CanvasViewModel
import com.tendril.app.ui.pages.PageDatabaseViewModel
import com.tendril.app.ui.pages.PageDetailViewModel
import com.tendril.app.ui.pages.PagesViewModel
import com.tendril.app.ui.pages.TableRow
import com.tendril.app.ui.roadmap.RoadMapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import com.tendril.app.domain.ai.OutlineRow
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assert.assertFalse
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
        val labelDao = FakeLabelDao(store)
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
        val habitDao = FakeHabitDao()
        val habitCompletionDao = FakeHabitCompletionDao()

        val contentRepository = PageContentRepository(pageDao, blockDao, ftsDao, FakeBlockFtsDao(store))
        val resolveEntryUseCase = ResolveEntryUseCase(entryDao, completionDao, coordinator)
        val templateManager = TemplateManager(pageDao, blockDao, pageDatabaseDao, propertyDao, canvasDao, nodeDao, edgeDao)
        val databaseSyncManager =
            DatabaseSyncManager(pageDao, pageDatabaseDao, propertyValueDao, entryDao, completionDao, resolveEntryUseCase, coordinator)
        val viewLockState = ViewLockState()
        val checkboxOnlyState = CheckboxOnlyState()
        val labelMembership = LabelMembership(pageDao, pageDatabaseDao, labelDao, entryDao, databaseSyncManager, resolveEntryUseCase)

        val purgedDao = FakePurgedRecordDao()
        val purgeRegistry = PurgeRegistry(purgedDao, pageDao, entryDao, habitDao, propertyDao, coordinator)

        val engine = PagesSyncEngine(
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
            pageHistory = PageHistory(pageDao, blockDao, FakePageRevisionDao()),
        )

        fun detail(pageId: Long) = PageDetailViewModel(
            pageId, pageDao, blockDao, labelDao, propertyDao, propertyValueDao, pageDatabaseDao, entryDao,
            resolveEntryUseCase, coordinator, contentRepository, templateManager, viewLockState, checkboxOnlyState, InMemoryLocalImageStore(), labelMembership,
            habitDao, CheckInHabitUseCase(habitDao, habitCompletionDao), PageHistory(pageDao, blockDao, FakePageRevisionDao()),
            FakeAiKeyStore(), MapKeyValueStore(), FakeCheckInDao(),
        )

        fun database(pageId: Long) = PageDatabaseViewModel(
            pageId, pageDao, pageDatabaseDao, propertyDao, propertyValueDao, entryDao, viewDao, blockDao,
            databaseSyncManager, resolveEntryUseCase, coordinator, templateManager, purgeRegistry, viewLockState, labelDao, labelMembership, contentRepository,
        )

        fun canvas(pageId: Long) = CanvasViewModel(pageId, pageDao, canvasDao, nodeDao, edgeDao, viewLockState, contentRepository, templateManager)

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
            pageDao, pageDatabaseDao, propertyDao, ftsDao, labelDao, purgeRegistry, databaseSyncManager,
            templateManager, viewLockState, contentRepository, entryDao, resolveEntryUseCase,
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
        fun roadmap() = RoadMapViewModel(pageDao, relationDao, contentRepository, viewLockState, labelDao, MapKeyValueStore())

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

    /** §0.6.15's fourth verb (2026-09-22) — the reply's rows land as a tree after the selection's
     * block, the root flagged as a mind map, and travel as ordinary blocks; one undo removes them. */
    @Test
    fun `a generated outline inserted on one device reaches the other as a tree, and one undo removes it`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Garden")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 0, content = "compost notes", createdAt = t0, updatedAt = t0))
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 1, content = "after", createdAt = t0, updatedAt = t0))
        val first = a.blockDao.getForPage(page.id).first { it.content == "compost notes" }
        val vm = a.detail(page.id)

        vm.insertOutline(first, listOf(OutlineRow(0, "Compost"), OutlineRow(1, "Inputs"), OutlineRow(2, "Scraps"), OutlineRow(1, "Care")), asMindMap = true)

        val rows = a.blockDao.getForPage(page.id).sortedBy { it.order }
        assertEquals(listOf("compost notes", "Compost", "Inputs", "Scraps", "Care", "after"), rows.map { it.content })
        val byText = rows.associateBy { it.content }
        assertEquals(null, byText["Compost"]!!.parentBlockId)
        assertEquals(byText["Compost"]!!.id, byText["Inputs"]!!.parentBlockId)
        assertEquals(byText["Inputs"]!!.id, byText["Scraps"]!!.parentBlockId)
        assertEquals(byText["Compost"]!!.id, byText["Care"]!!.parentBlockId)
        assertTrue(byText["Compost"]!!.mindMap); assertFalse(byText["Inputs"]!!.mindMap)
        assertTrue(rows.filter { it.parentBlockId != null || it.content == "Compost" }.all { it.type == BlockType.BULLETED_LIST_ITEM })

        syncAtoB()
        val onB = b.blockDao.getForPage(b.pageIdOf(page.uid)).sortedBy { it.order }
        assertEquals(rows.map { it.content }, onB.map { it.content })
        assertTrue(onB.first { it.content == "Compost" }.mindMap)

        vm.undo()
        assertEquals(listOf("compost notes", "after"), a.blockDao.getForPage(page.id).sortedBy { it.order }.map { it.content })
    }

    /** Audit 2026-09-24: `undo()` moved the stack before `launchAndReindex` refused the write, so
     * Ctrl+Z under View-Only spent the newest step without applying it — after unlocking, the next
     * undo reverted the edit *before* it, and the newest could no longer be undone at all. */
    @Test
    fun `an undo refused under View-Only does not spend the step`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 0, content = "one", createdAt = t0, updatedAt = t0))
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.PARAGRAPH, order = 1, content = "two", createdAt = t0, updatedAt = t0))
        val vm = a.detail(page.id)
        vm.deleteBlocks(setOf(a.blockDao.getForPage(page.id).first { it.content == "one" }.id))
        vm.deleteBlocks(setOf(a.blockDao.getForPage(page.id).first { it.content == "two" }.id))

        a.viewLockState.setViewOnly(true)
        vm.undo()
        assertEquals("refused under the lock", emptyList<String>(), a.blockDao.getForPage(page.id).map { it.content })
        a.viewLockState.setViewOnly(false)
        vm.undo()

        assertEquals(
            "the first undo after unlocking reverses the newest edit — the one pressed under the lock",
            listOf("two"),
            a.blockDao.getForPage(page.id).map { it.content },
        )
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

    /** P1 — the code block language picker in `BlockActionSheet`, wired up now that B3 no longer
     * blocks Stage 5. `setCodeLanguage` is a plain `launchAndReindex` mutation, so this is really
     * exercising the same write path as `changeType`/`setToggleExpanded` above, not new plumbing. */
    @Test
    fun `a code block's language picked on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Snippets")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.CODE, order = 0, content = "print(1)", createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.detail(page.id).setCodeLanguage(a.blockDao.getForPage(page.id).single(), "python")
        syncAtoB()

        assertEquals(listOf("python"), b.blockDao.getForPage(b.pageIdOf(page.uid)).map { it.codeLanguage })
    }

    /** P3 — the callout color swatch, same shape as the language picker above. */
    @Test
    fun `a callout's color picked on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Reminders")
        a.blockDao.insert(Block(pageId = page.id, type = BlockType.CALLOUT, order = 0, content = "don't forget", createdAt = t0, updatedAt = t0))
        syncAtoB()

        a.detail(page.id).setCalloutColor(a.blockDao.getForPage(page.id).single(), "#BFDBFE")
        syncAtoB()

        assertEquals(listOf("#BFDBFE"), b.blockDao.getForPage(b.pageIdOf(page.uid)).map { it.calloutColor })
    }

    /** Tags are not blocks and travel in their own field of the snapshot, but they are gated by
     * the same one timestamp as everything else hanging off the page. */
    @Test
    fun `a label added on one device reaches the other`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Notes")
        syncAtoB()

        a.detail(page.id).addLabel("urgent")
        syncAtoB()

        assertEquals(listOf("urgent"), b.labelDao.getForPage(b.pageIdOf(page.uid)).map { it.name })
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

    /** F6 (PR C, 2026-09-18 — Notion's, measured): a new view never opens empty. The seeded
     * database has a TEXT "Status" and no Select, so a Board gets a Select *Status* made for it
     * and grouped by; a Calendar gets a *Date*; a second Calendar binds the Date that now exists. */
    @Test
    fun `a new Board or Calendar view gets the property it plots by`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val vm = a.database(seeded.databasePage.id)
        val dbId = seeded.property.databaseId

        vm.addView("Board", ViewType.BOARD)
        val status = a.propertyDao.getForDatabase(dbId).single { it.type == PropertyType.SELECT }
        assertEquals("Status", status.name)
        assertEquals("Not started,In progress,Done", status.config)
        assertEquals(status.id, a.viewDao.getForDatabase(dbId).single { it.viewType == ViewType.BOARD }.groupByPropertyId)

        vm.addView("Calendar", ViewType.CALENDAR)
        val date = a.propertyDao.getForDatabase(dbId).single { it.type == PropertyType.DATE }
        assertEquals("Date", date.name)
        assertEquals(date.id, a.viewDao.getForDatabase(dbId).single { it.viewType == ViewType.CALENDAR }.datePropertyId)

        vm.addView("Plan", ViewType.TIMELINE)
        assertEquals("the existing Date is bound, not a second one made", 1, a.propertyDao.getForDatabase(dbId).count { it.type == PropertyType.DATE })
        assertEquals(date.id, a.viewDao.getForDatabase(dbId).single { it.viewType == ViewType.TIMELINE }.datePropertyId)
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

    // ----------------------------------------------------------------------- rollup (DB2)

    /** §5.4/DB2 — a rollup created on the *reverse* side of a relation (the auto-created "Tasks"
     * property on Projects) can aggregate a property that lives on the relation's target
     * database, proving `relationCandidateProperties` resolves through a reverse property
     * correctly, not only a forward one — the direction every real rollup is actually created
     * from, since the reverse is what a database gets automatically. */
    @Test
    fun `a SUM rollup totals the related rows' numeric property, recomputed fresh each read`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val projectsPage = a.pageDao.getById(a.pageDatabaseDao.getById(projectsDbId)!!.pageId)!!
        val pointsPropertyId = a.propertyDao.insert(Property(databaseId = tasksDbId, name = "Points", type = PropertyType.NUMBER, order = 0))
        val task1 = a.pageDao.getById(a.store.seedPage(Page(title = "Task 1", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val task2 = a.pageDao.getById(a.store.seedPage(Page(title = "Task 2", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsPropertyId, task1.id, "3")
        a.propertyValueDao.setValue(pointsPropertyId, task2.id, "5")

        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single { it.name == "Project" }
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single { it.name == "Tasks" }
        a.database(tasksPage.id).setRelationValue(forward, task1, setOf(projectRow.uid))
        a.database(tasksPage.id).setRelationValue(forward, task2, setOf(projectRow.uid))

        a.database(projectsPage.id).addRollupProperty("Total points", reverse.id, pointsPropertyId, RollupAggregation.SUM)
        val rollupProperty = a.propertyDao.getForDatabase(projectsDbId).single { it.type == PropertyType.COMPUTED }

        val reverseValue = a.propertyValueDao.getForPropertyAndRow(reverse.id, projectRow.id)?.value
        val projectTableRow = TableRow(
            projectRow,
            mapOf(reverse.id to PropertyValue(propertyId = reverse.id, rowPageId = projectRow.id, value = reverseValue)),
            linkedEntry = null,
        )

        assertEquals("8", a.database(projectsPage.id).computeRollupValue(rollupProperty, projectTableRow))

        // Never stored: raising Task 2's points changes the total with no edit to the rollup
        // cell, the row, or the relation at all — the entire point of "compute on read".
        a.propertyValueDao.setValue(pointsPropertyId, task2.id, "10")
        assertEquals("13", a.database(projectsPage.id).computeRollupValue(rollupProperty, projectTableRow))
    }

    /** The acceptance shape named for DB2 in the build plan: a rollup column shows the sum of a
     * related database's number column *and updates when the source changes* — proven here
     * across the real two-device sync harness. Both the rollup's definition (a schema item,
     * synced with the database's own properties) and its computed value (never stored, so there
     * is nothing to sync — only the ingredients: the relation cell and the target values) reach
     * device B. */
    @Test
    fun `a rollup's definition and its computed value both reach the other device`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val projectsPage = a.pageDao.getById(a.pageDatabaseDao.getById(projectsDbId)!!.pageId)!!
        val pointsPropertyId = a.propertyDao.insert(Property(databaseId = tasksDbId, name = "Points", type = PropertyType.NUMBER, order = 0))
        val task = a.pageDao.getById(a.store.seedPage(Page(title = "Task", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsPropertyId, task.id, "3")

        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single { it.name == "Project" }
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single { it.name == "Tasks" }
        a.database(tasksPage.id).setRelationValue(forward, task, setOf(projectRow.uid))
        a.database(projectsPage.id).addRollupProperty("Total points", reverse.id, pointsPropertyId, RollupAggregation.SUM)
        syncAtoB()

        val bProjectsDbId = b.pageDatabaseDao.getByPageId(b.pageIdOf(projectsPage.uid))!!.id
        val bRollupProperty = b.propertyDao.getForDatabase(bProjectsDbId).single { it.type == PropertyType.COMPUTED }
        val bReverseProperty = b.propertyDao.getForDatabase(bProjectsDbId).single { it.name == "Tasks" }
        val bProjectRowId = b.pageIdOf(projectRow.uid)
        val bProjectRow = b.pageDao.getById(bProjectRowId)!!
        val bReverseValue = b.propertyValueDao.getForPropertyAndRow(bReverseProperty.id, bProjectRowId)?.value
        val bTableRow = TableRow(
            bProjectRow,
            mapOf(bReverseProperty.id to PropertyValue(propertyId = bReverseProperty.id, rowPageId = bProjectRowId, value = bReverseValue)),
            linkedEntry = null,
        )

        assertEquals(
            "the rollup's definition (relation + target property + aggregation) travelled, and computes the same total on B",
            "3",
            b.database(b.pageIdOf(projectsPage.uid)).computeRollupValue(bRollupProperty, bTableRow),
        )
    }

    // ------------------------------------------------------------------------- formula (DB3 wiring)

    /** §5.4/DB3 wiring — the plain-property half: `prop("Points") * 2` evaluates against the
     * row's own stored value, with no relation or rollup involved at all. */
    @Test
    fun `a formula referencing a plain number property computes correctly`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        a.propertyValueDao.setValue(pointsId, seeded.row.id, "5")

        var result: FormulaCheckResult? = null
        a.database(seeded.databasePage.id).addFormulaProperty("Doubled", "prop(\"Points\") * 2") { result = it }
        assertTrue("a valid formula over a plain property should check clean", result?.errors.orEmpty().isEmpty())

        val doubled = a.propertyDao.getForDatabase(dbId).single { it.name == "Doubled" }
        val pointsValue = a.propertyValueDao.getForPropertyAndRow(pointsId, seeded.row.id)?.value
        val tableRow = TableRow(seeded.row, mapOf(pointsId to PropertyValue(propertyId = pointsId, rowPageId = seeded.row.id, value = pointsValue)), linkedEntry = null)

        assertEquals("10", a.database(seeded.databasePage.id).computeComputedValue(doubled, tableRow))
    }

    /** A formula-authored `COMPUTED` property can itself be referenced by name from another one
     * — [checkAllFormulas] and [computeComputedValue]'s own resolver both check every other
     * formula-authored property on the database together, per that function's own doc comment,
     * so a two-deep chain has to resolve through the middle property rather than only ever
     * seeing one level. */
    @Test
    fun `a formula referencing another formula resolves through the chain`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        a.propertyValueDao.setValue(pointsId, seeded.row.id, "5")

        a.database(seeded.databasePage.id).addFormulaProperty("Doubled", "prop(\"Points\") * 2") {}
        var result: FormulaCheckResult? = null
        a.database(seeded.databasePage.id).addFormulaProperty("Quadrupled", "prop(\"Doubled\") * 2") { result = it }
        assertTrue("a chained formula reference should check clean", result?.errors.orEmpty().isEmpty())

        val quadrupled = a.propertyDao.getForDatabase(dbId).single { it.name == "Quadrupled" }
        val pointsValue = a.propertyValueDao.getForPropertyAndRow(pointsId, seeded.row.id)?.value
        val tableRow = TableRow(seeded.row, mapOf(pointsId to PropertyValue(propertyId = pointsId, rowPageId = seeded.row.id, value = pointsValue)), linkedEntry = null)

        assertEquals("20", a.database(seeded.databasePage.id).computeComputedValue(quadrupled, tableRow))
    }

    /** §5.4/DB3 — "errors are caught when the formula is written." A formula naming itself is
     * the one cycle reachable through today's write path at all: there is no formula-*editing*
     * API yet (matching [addRollupProperty]/[addRelationProperty]'s own precedent — none of
     * DB1/DB2/DB3's `COMPUTED` config is editable after creation, only delete-and-recreate), so a
     * genuine multi-property cycle would need one formula changed out from under another after
     * both already exist, which this slice cannot do. Self-reference needs only one call. */
    @Test
    fun `a formula referencing itself is rejected as a cycle, not inserted`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id

        var result: FormulaCheckResult? = null
        a.database(seeded.databasePage.id).addFormulaProperty("Loopy", "prop(\"Loopy\") + 1") { result = it }

        assertTrue("a self-reference is a cycle of length one and must be rejected", result?.errors.orEmpty().isNotEmpty())
        assertTrue(
            "the rejected formula must not have been inserted as a property",
            a.propertyDao.getForDatabase(dbId).none { it.name == "Loopy" },
        )
    }

    /** A formula cannot traverse a relation yet (see [PropertyType.COMPUTED]'s own note) — naming
     * one is a real write-time error, exactly the shape [FormulaPropertyKind.Relation] already
     * documents for a bare reference, not a silent no-op or a value that only fails once a cell
     * tries to render it. */
    @Test
    fun `a formula referencing a relation property is rejected, not inserted`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)

        var result: FormulaCheckResult? = null
        a.database(tasksPage.id).addFormulaProperty("Bad", "prop(\"Project\")") { result = it }

        assertTrue("referencing a relation by name must fail type-checking", result?.errors.orEmpty().isNotEmpty())
        assertTrue(a.propertyDao.getForDatabase(tasksDbId).none { it.name == "Bad" })
    }

    /** The formula's *definition* is an ordinary [Property] row and travels as part of the
     * database's schema like any other; its computed value is never stored, so there is nothing
     * else to sync — only the ingredient ([Property.NUMBER] cell value) it reads on each device,
     * the exact shape the rollup sync test above already proves for the relation-and-rollup case. */
    @Test
    fun `a formula's definition reaches the other device and computes the same result there`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        a.propertyValueDao.setValue(pointsId, seeded.row.id, "5")
        a.database(seeded.databasePage.id).addFormulaProperty("Doubled", "prop(\"Points\") * 2") {}
        syncAtoB()

        val bDbId = b.pageDatabaseDao.getByPageId(b.pageIdOf(seeded.databasePage.uid))!!.id
        val bDoubled = b.propertyDao.getForDatabase(bDbId).single { it.name == "Doubled" }
        val bPointsId = b.propertyDao.getForDatabase(bDbId).single { it.name == "Points" }.id
        val bRowId = b.pageIdOf(seeded.row.uid)
        val bRow = b.pageDao.getById(bRowId)!!
        val bPointsValue = b.propertyValueDao.getForPropertyAndRow(bPointsId, bRowId)?.value
        val bTableRow = TableRow(bRow, mapOf(bPointsId to PropertyValue(propertyId = bPointsId, rowPageId = bRowId, value = bPointsValue)), linkedEntry = null)

        assertEquals("10", b.database(b.pageIdOf(seeded.databasePage.uid)).computeComputedValue(bDoubled, bTableRow))
    }

    // --------------------------------------------------------------------------- DB4 (summary + explain)

    @Test
    fun `a NUMBER column's footer summary is the sum and average of its currently displayed rows`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        val row2 = a.pageDao.getById(a.store.seedPage(Page(title = "Row 2", databaseId = dbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsId, seeded.row.id, "3")
        a.propertyValueDao.setValue(pointsId, row2.id, "7")
        val pointsProperty = a.propertyDao.getById(pointsId)!!

        val rows = listOf(seeded.row, row2).map { page ->
            val value = a.propertyValueDao.getForPropertyAndRow(pointsId, page.id)?.value
            TableRow(page, mapOf(pointsId to PropertyValue(propertyId = pointsId, rowPageId = page.id, value = value)), linkedEntry = null)
        }

        assertEquals("Σ 10 · ⌀ 5", a.database(seeded.databasePage.id).computeColumnSummary(pointsProperty, rows))
    }

    @Test
    fun `a non-numeric column's footer summary is empty`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val rows = listOf(TableRow(seeded.row, mapOf(seeded.property.id to PropertyValue(propertyId = seeded.property.id, rowPageId = seeded.row.id, value = "before")), linkedEntry = null))

        assertNull(a.database(seeded.databasePage.id).computeColumnSummary(seeded.property, rows))
    }

    @Test
    fun `a formula column's footer summary sums the computed values across rows`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        val row2 = a.pageDao.getById(a.store.seedPage(Page(title = "Row 2", databaseId = dbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsId, seeded.row.id, "3")
        a.propertyValueDao.setValue(pointsId, row2.id, "7")
        a.database(seeded.databasePage.id).addFormulaProperty("Doubled", "prop(\"Points\") * 2") {}
        val doubled = a.propertyDao.getForDatabase(dbId).single { it.name == "Doubled" }

        val rows = listOf(seeded.row, row2).map { page ->
            val value = a.propertyValueDao.getForPropertyAndRow(pointsId, page.id)?.value
            TableRow(page, mapOf(pointsId to PropertyValue(propertyId = pointsId, rowPageId = page.id, value = value)), linkedEntry = null)
        }

        assertEquals("Σ 20 · ⌀ 10", a.database(seeded.databasePage.id).computeColumnSummary(doubled, rows))
    }

    @Test
    fun `explaining a formula cell lists its property references and the result`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        a.propertyValueDao.setValue(pointsId, seeded.row.id, "5")
        a.database(seeded.databasePage.id).addFormulaProperty("Doubled", "prop(\"Points\") * 2") {}
        val doubled = a.propertyDao.getForDatabase(dbId).single { it.name == "Doubled" }
        val pointsValue = a.propertyValueDao.getForPropertyAndRow(pointsId, seeded.row.id)?.value
        val tableRow = TableRow(seeded.row, mapOf(pointsId to PropertyValue(propertyId = pointsId, rowPageId = seeded.row.id, value = pointsValue)), linkedEntry = null)

        val explanation = a.database(seeded.databasePage.id).explainComputedValue(doubled, tableRow) as PageDatabaseViewModel.ComputedExplanation.Formula

        assertEquals("prop(\"Points\") * 2", explanation.expression)
        assertEquals(listOf("Points" to "5"), explanation.inputs)
        assertEquals("10", explanation.result)
    }

    @Test
    fun `explaining a rollup cell lists the related rows and the value read from each`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val projectsPage = a.pageDao.getById(a.pageDatabaseDao.getById(projectsDbId)!!.pageId)!!
        val pointsPropertyId = a.propertyDao.insert(Property(databaseId = tasksDbId, name = "Points", type = PropertyType.NUMBER, order = 0))
        val task = a.pageDao.getById(a.store.seedPage(Page(title = "Task", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsPropertyId, task.id, "3")
        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single { it.name == "Project" }
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single { it.name == "Tasks" }
        a.database(tasksPage.id).setRelationValue(forward, task, setOf(projectRow.uid))
        a.database(projectsPage.id).addRollupProperty("Total points", reverse.id, pointsPropertyId, RollupAggregation.SUM)
        val rollupProperty = a.propertyDao.getForDatabase(projectsDbId).single { it.type == PropertyType.COMPUTED }
        val reverseValue = a.propertyValueDao.getForPropertyAndRow(reverse.id, projectRow.id)?.value
        val projectTableRow = TableRow(projectRow, mapOf(reverse.id to PropertyValue(propertyId = reverse.id, rowPageId = projectRow.id, value = reverseValue)), linkedEntry = null)

        val explanation = a.database(projectsPage.id).explainComputedValue(rollupProperty, projectTableRow) as PageDatabaseViewModel.ComputedExplanation.Rollup

        assertEquals("Tasks", explanation.relationName)
        assertEquals(RollupAggregation.SUM, explanation.aggregation)
        assertEquals("Points", explanation.targetName)
        assertEquals(listOf("Task" to "3"), explanation.relatedRows)
        assertEquals("3", explanation.result)
    }

    // --------------------------------------------------------------------------- DB5 (formula board grouping)

    @Test
    fun `a Board grouped by a formula property partitions rows by the computed result`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val pointsId = a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        val lowRow = seeded.row
        val highRow = a.pageDao.getById(a.store.seedPage(Page(title = "High row", databaseId = dbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsId, lowRow.id, "3")
        a.propertyValueDao.setValue(pointsId, highRow.id, "8")
        val vm = a.database(seeded.databasePage.id)
        vm.addFormulaProperty("Tier", "if(prop(\"Points\") >= 5, \"High\", \"Low\")") {}
        val tier = a.propertyDao.getForDatabase(dbId).single { it.name == "Tier" }
        val boardViewId = a.viewDao.insert(PageDatabaseView(databaseId = dbId, name = "Board", viewType = ViewType.BOARD, order = 1, groupByPropertyId = tier.id))
        vm.selectView(boardViewId)

        val columns = vm.boardColumns.first()

        assertEquals(setOf("High", "Low"), columns.map { it.label }.toSet())
        assertEquals(listOf(highRow.title), columns.single { it.label == "High" }.rows.map { it.page.title })
        assertEquals(listOf(lowRow.title), columns.single { it.label == "Low" }.rows.map { it.page.title })
    }

    /** PR C (2026-09-18): a blank Select cell is unset, not a mismatch — its rows sit in a named
     * first column whose key is "", so a card moved out of it takes an option and one moved in is cleared. */
    @Test
    fun `rows with a blank Select value sit in a named first column of the Board`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val vm = a.database(seeded.databasePage.id)
        vm.addView("Board", ViewType.BOARD)
        val columns = vm.boardColumns.first()
        assertEquals(listOf("No Status", "Not started", "In progress", "Done"), columns.map { it.label })
        assertEquals("", columns.first().key)
        assertEquals(listOf("A row"), columns.first().rows.map { it.page.title })
    }

    @Test
    fun `a row whose grouping formula evaluates to empty has no Board column`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        a.propertyDao.insert(Property(databaseId = dbId, name = "Points", type = PropertyType.NUMBER, order = 1))
        // seeded.row never gets a "Points" value, so prop("Points") resolves to Empty and the
        // whole if(...) — not just the comparison — degrades to Empty per §5.4's "a formula
        // referencing a blank cell should read as blank" rule (FormulaEvaluator.kt's own note).
        val vm = a.database(seeded.databasePage.id)
        vm.addFormulaProperty("Tier", "if(prop(\"Points\") >= 5, \"High\", \"Low\")") {}
        val tier = a.propertyDao.getForDatabase(dbId).single { it.name == "Tier" }
        val boardViewId = a.viewDao.insert(PageDatabaseView(databaseId = dbId, name = "Board", viewType = ViewType.BOARD, order = 1, groupByPropertyId = tier.id))
        vm.selectView(boardViewId)

        val columns = vm.boardColumns.first()

        assertTrue("a row with no computed value should not appear in any column", columns.isEmpty())
    }

    @Test
    fun `grouping by a rollup-authored COMPUTED property degrades to no columns rather than crashing`() = runTest(mainDispatcher) {
        val (tasksPage, projectsDbId) = seedTwoDatabasesOnA()
        val tasksDbId = a.pageDatabaseDao.getByPageId(tasksPage.id)!!.id
        val projectsPage = a.pageDao.getById(a.pageDatabaseDao.getById(projectsDbId)!!.pageId)!!
        val pointsPropertyId = a.propertyDao.insert(Property(databaseId = tasksDbId, name = "Points", type = PropertyType.NUMBER, order = 0))
        val task = a.pageDao.getById(a.store.seedPage(Page(title = "Task", databaseId = tasksDbId, createdAt = t0, updatedAt = t0)))!!
        val projectRow = a.pageDao.getById(a.store.seedPage(Page(title = "Launch", databaseId = projectsDbId, createdAt = t0, updatedAt = t0)))!!
        a.propertyValueDao.setValue(pointsPropertyId, task.id, "3")
        a.database(tasksPage.id).addRelationProperty("Project", projectsDbId)
        val forward = a.propertyDao.getForDatabase(tasksDbId).single { it.name == "Project" }
        val reverse = a.propertyDao.getForDatabase(projectsDbId).single { it.name == "Tasks" }
        a.database(tasksPage.id).setRelationValue(forward, task, setOf(projectRow.uid))
        val vm = a.database(projectsPage.id)
        vm.addRollupProperty("Total points", reverse.id, pointsPropertyId, RollupAggregation.SUM)
        val rollupProperty = a.propertyDao.getForDatabase(projectsDbId).single { it.type == PropertyType.COMPUTED }
        val boardViewId = a.viewDao.insert(PageDatabaseView(databaseId = projectsDbId, name = "Board", viewType = ViewType.BOARD, order = 1, groupByPropertyId = rollupProperty.id))
        vm.selectView(boardViewId)

        val columns = vm.boardColumns.first()

        assertTrue(columns.isEmpty())
    }

    // --------------------------------------------------------------------------- DB8 (column chooser)

    @Test
    fun `an untouched view's chooser shows every property, matching every view that predates it`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val notesId = a.propertyDao.insert(Property(databaseId = dbId, name = "Notes", type = PropertyType.TEXT, order = 1))
        val tableViewId = a.viewDao.insert(PageDatabaseView(databaseId = dbId, name = "Table", viewType = ViewType.TABLE, order = 0))
        val vm = a.database(seeded.databasePage.id)
        vm.selectView(tableViewId)

        assertEquals(setOf(seeded.property.id, notesId), vm.visibleProperties.first().map { it.id }.toSet())
    }

    @Test
    fun `hiding a column narrows visibleProperties, and it survives a restart`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        val notesId = a.propertyDao.insert(Property(databaseId = dbId, name = "Notes", type = PropertyType.TEXT, order = 1))
        val tableViewId = a.viewDao.insert(PageDatabaseView(databaseId = dbId, name = "Table", viewType = ViewType.TABLE, order = 0))
        val vm = a.database(seeded.databasePage.id)
        vm.selectView(tableViewId)
        val tableView = a.viewDao.getForDatabase(dbId).single { it.id == tableViewId }

        vm.updateView(tableView.copy(visiblePropertyIds = listOf(seeded.property.id)))

        assertEquals(listOf(seeded.property.id), vm.visibleProperties.first().map { it.id })
        // "Survives a restart" — a fresh ViewModel re-reads the same persisted view row rather
        // than carrying any in-memory state forward.
        val reopened = a.database(seeded.databasePage.id)
        reopened.selectView(tableViewId)
        assertEquals(
            "Notes stays hidden",
            listOf(seeded.property.id),
            reopened.visibleProperties.first().map { it.id },
        )
    }

    @Test
    fun `a hidden column reaches the other device`() = runTest(mainDispatcher) {
        val seeded = seedDatabaseOnA()
        val dbId = a.pageDatabaseDao.getByPageId(seeded.databasePage.id)!!.id
        a.propertyDao.insert(Property(databaseId = dbId, name = "Notes", type = PropertyType.TEXT, order = 1))
        val tableViewId = a.viewDao.insert(PageDatabaseView(databaseId = dbId, name = "Table", viewType = ViewType.TABLE, order = 0))
        syncAtoB()

        val tableView = a.viewDao.getForDatabase(dbId).single { it.id == tableViewId }
        a.database(seeded.databasePage.id).updateView(tableView.copy(visiblePropertyIds = listOf(seeded.property.id)))
        syncAtoB()

        val bDbId = b.pageDatabaseDao.getByPageId(b.pageIdOf(seeded.databasePage.uid))!!.id
        val bStatusId = b.propertyDao.getForDatabase(bDbId).single { it.name == "Status" }.id
        val bView = b.viewDao.getForDatabase(bDbId).single { it.name == "Table" }
        assertEquals(listOf(bStatusId), bView.visiblePropertyIds)
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
// FakeEntryCompletionDao moved to SyncTestDoubles.kt when S2 gave EntryCompletionDao its
// snapshot reads: two doubles for one DAO in one package is a redeclaration, and the doubles
// file is where the others already live.
