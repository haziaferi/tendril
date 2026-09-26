package com.tendril.app.sync

import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.domain.canvas.FRAME_DEFAULT_W
import com.tendril.app.domain.canvas.FRAME_DEFAULT_H
import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.prefs.MapKeyValueStore
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.LabelMembership
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.ui.canvas.CanvasViewModel
import com.tendril.app.ui.pages.PageDatabaseViewModel
import com.tendril.app.ui.roadmap.RoadMapViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * §3.1.2 — View-Only is "one global toggle across every page under Pages", and the enforcement
 * pattern the codebase settled on is one `locked()` gate per ViewModel, called at the top of
 * every mutating function (see `PageDetailViewModel.viewOnlyLocked` and
 * `PageDatabaseViewModel.locked`). Three write surfaces had never adopted it, and a grep for
 * `viewOnly` found nothing in `CanvasScreen.kt` or `RoadMapScreen.kt` either — so the lock was
 * absent at *both* layers on those screens, not merely un-enforced below a UI that hid the
 * affordance. That is the defect this class was written against; the fix is described below.
 *
 * That matters for §9.4, not just for the toggle's own promise. Every write these surfaces make
 * travels: a canvas node rides inside its page's snapshot, a manual relation is its own synced
 * `page_relations` row, and an import or a restore rewrites the whole store. A write made while
 * the person believed the app was read-only is a write they never chose, exported to every other
 * device on the next pass, where nothing distinguishes it from one they did.
 *
 * The two tests at the bottom assert the *opposite* — the exemptions the user decided are
 * deliberate. They are here so an over-zealous fix that gates everything it can find breaks a
 * test rather than the app.
 *
 * ## The shape of the fix
 * All three classes under test now take a [ViewLockState] as a constructor argument, which is the
 * intended shape rather than an accident of the test — the lock reaches the code that does the
 * work, so it cannot be routed around by a future call site:
 *  - `CanvasViewModel`, whose gate sits inside `launchAndTouch`, so every present and future
 *    mutation funnelled through it inherits the refusal,
 *  - `RoadMapViewModel`, gating `relate` — the only function on it that writes anything durable,
 *  - `PortableArchive` (see [archive] — the Settings call sites live in Compose, which is not
 *    unit-testable here, so the refusal is asserted at the class that actually does the work).
 */
class ViewOnlySurfacesGuardTest {

    @get:Rule val temp = TemporaryFolder()

    /** Older than any `Instant.now()` a write path can produce, so "the page row did not move"
     * is unambiguous rather than a rounding accident. */
    private val t0: Instant = Instant.ofEpochMilli(1_000L)

    /** Unconfined for the same reason [WritePathSyncTest] uses it: every mutation under test is
     * a `viewModelScope.launch { }` over in-memory fakes that never really suspend, so running
     * them eagerly on the calling thread lets each test read as the user action it describes —
     * and, here, means a *missing* write is a missing write rather than a pending one. */
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun installMainDispatcher() = Dispatchers.setMain(mainDispatcher)

    @After
    fun restoreMainDispatcher() = Dispatchers.resetMain()

    // ------------------------------------------------------------------ one device's store

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

    private val entryDao = FakeEntryDao()
    private val completionDao = RecordingEntryCompletionDao()
    private val coordinator = RecordingEntryScheduleCoordinator()

    private val contentRepository = PageContentRepository(pageDao, blockDao, ftsDao, FakeBlockFtsDao(store))
    private val resolveEntryUseCase = ResolveEntryUseCase(entryDao, completionDao, coordinator)
    private val templateManager = TemplateManager(pageDao, blockDao, pageDatabaseDao, propertyDao, canvasDao, nodeDao, edgeDao)
    private val databaseSyncManager =
        DatabaseSyncManager(pageDao, pageDatabaseDao, propertyValueDao, entryDao, completionDao, resolveEntryUseCase, coordinator)
    private val purgeRegistry =
        PurgeRegistry(FakePurgedRecordDao(), pageDao, entryDao, FakeHabitDao(), propertyDao, coordinator)

    /** The one global toggle (§3.1.2). [lockEverything] is what each test flips; a test that
     * wants the unlocked mirror simply never calls it. */
    private val viewLockState = ViewLockState()

    private fun lockEverything() = viewLockState.setViewOnly(true)

    // ------------------------------------------------------------------------- ViewModels

    /**
     * `CanvasViewModel.canvas` is `stateIn(..., WhileSubscribed(5_000), null)`, so it stays null
     * until something collects it — and `addTextNode`/`addPageEmbedNode`/`addEdge` all read
     * `canvas.value ?: return`. Without a collector those tests would "pass" by asserting that a
     * function which early-returned wrote nothing, which is no assertion at all. The collector
     * here is what makes the add path genuinely reachable, and every add test asserts
     * `canvas.value` is non-null before acting so a regression in this harness fails loudly
     * instead of turning into a vacuous green.
     */
    private fun TestScope.canvasViewModel(pageId: Long): CanvasViewModel {
        val viewModel = CanvasViewModel(
            pageId = pageId,
            pageDao = pageDao,
            pageCanvasDao = canvasDao,
            canvasNodeDao = nodeDao,
            canvasEdgeDao = edgeDao,
            viewLockState = viewLockState,
            pageContentRepository = contentRepository,
            templateManager = templateManager,
        )
        backgroundScope.launch { viewModel.canvas.collect { } }
        backgroundScope.launch { viewModel.nodes.collect { } }
        backgroundScope.launch { viewModel.page.collect { } }   // `saveAsTemplate` reads `page.value`, WhileSubscribed too
        testScheduler.advanceUntilIdle()
        return viewModel
    }

    private fun roadMapViewModel() = RoadMapViewModel(
        pageDao = pageDao,
        relationDao = relationDao,
        contentRepository = contentRepository,
        viewLockState = viewLockState,
        labelDao = labelDao,
        keyValueStore = MapKeyValueStore(),
    )

    /** Same `WhileSubscribed` problem as [canvasViewModel]: `ensureDefaultView` reads
     * `database.value ?: return`. Positional, matching today's constructor — this ViewModel
     * already takes the lock and needs no signature change. */
    private fun TestScope.pageDatabaseViewModel(pageId: Long): PageDatabaseViewModel {
        val viewModel = PageDatabaseViewModel(
            pageId, pageDao, pageDatabaseDao, propertyDao, propertyValueDao, entryDao, viewDao, blockDao,
            databaseSyncManager, resolveEntryUseCase, coordinator, templateManager, purgeRegistry, viewLockState,
            labelDao, LabelMembership(pageDao, pageDatabaseDao, labelDao, entryDao, databaseSyncManager, resolveEntryUseCase), contentRepository,
        )
        backgroundScope.launch { viewModel.database.collect { } }
        testScheduler.advanceUntilIdle()
        return viewModel
    }

    // ------------------------------------------------------------------------- seeding

    private suspend fun seedPage(title: String, kind: PageKind = PageKind.PAGE): Page {
        val id = store.seedPage(Page(title = title, kind = kind, createdAt = t0, updatedAt = t0))
        return pageDao.getById(id)!!
    }

    private data class SeededCanvas(val page: Page, val canvasId: Long)

    private suspend fun seedCanvas(): SeededCanvas {
        val page = seedPage("Board", PageKind.CANVAS)
        return SeededCanvas(page, canvasDao.insert(PageCanvas(pageId = page.id, createdAt = t0, updatedAt = t0)))
    }

    private suspend fun seedFrame(canvasId: Long, x: Float, y: Float): CanvasNode {
        val id = nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.FRAME, x = x, y = y, width = FRAME_DEFAULT_W, height = FRAME_DEFAULT_H, text = "Beds", createdAt = t0, updatedAt = t0))
        return nodeDao.getForCanvas(canvasId).first { it.id == id }
    }

    private suspend fun seedNode(canvasId: Long, text: String, x: Float = 0f, y: Float = 0f): CanvasNode {
        val id = nodeDao.insert(
            CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = x, y = y, text = text, createdAt = t0, updatedAt = t0)
        )
        return nodeDao.getForCanvas(canvasId).first { it.id == id }
    }

    /** The page row is what §9.4's merge gates every canvas change on, so "the write did not
     * happen" and "the page did not claim authorship" are two halves of one assertion. */
    private suspend fun assertPageUntouched(page: Page) {
        assertEquals(
            "a write refused under View-Only must not bump the page row either — a bump is a claim of " +
                "authorship that would outrank a real edit made on another device (§9.4)",
            t0,
            pageDao.getById(page.id)!!.updatedAt,
        )
    }

    // ============================================================== 1. Canvas (§3.1.2, §10)

    @Test
    fun `a canvas node cannot be added while View-Only is on`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val viewModel = canvasViewModel(seeded.page.id)
        assertNotNull("harness: the canvas must be observed or the add path early-returns", viewModel.canvas.value)
        lockEverything()

        viewModel.addTextNode(x = 10f, y = 20f)

        // Counted rather than compared against `emptyList<String>()`: `addTextNode` starts a node
        // with empty text, so a one-node list renders as `[]` too and the failure would read
        // "expected:<[]> but was:<[]>" — a true RED that looks like a broken assertion.
        assertEquals(
            "CanvasViewModel takes no ViewLockState, so View-Only never reaches the board and a " +
                "node is created on a screen the person was told is read-only",
            0,
            nodeDao.getForCanvas(seeded.canvasId).size,
        )
        assertPageUntouched(seeded.page)
    }

    @Test
    fun `a frame cannot be added, moved or resized while View-Only is on`() = runTest(mainDispatcher) {
        // §0.10 item 15 (2026-09-18) — the three frame writes ride `launchAndTouch` like every other.
        val seeded = seedCanvas()
        val frame = seedFrame(seeded.canvasId, x = 0f, y = 0f)
        val viewModel = canvasViewModel(seeded.page.id)
        assertNotNull(viewModel.canvas.value)
        lockEverything()

        viewModel.addFrame(300f, 300f)
        viewModel.moveFrame(frame, 50f, 60f)
        viewModel.resizeFrame(frame, 600f, 600f)
        var saved = false
        viewModel.saveAsTemplate { saved = true }

        val after = nodeDao.getForCanvas(seeded.canvasId)
        assertEquals("a frame added under the lock", 1, after.size)
        assertEquals("a frame moved under the lock", 0f, after.single().x)
        assertEquals("a frame resized under the lock", FRAME_DEFAULT_W, after.single().width)
        assertEquals("a template saved under the lock", false, saved)
        assertPageUntouched(seeded.page)
    }

    @Test
    fun `a frame carries what lies wholly inside it and leaves the rest`() = runTest(mainDispatcher) {
        // Geometry, no parent (Obsidian's group, measured on both platforms): the card inside moves
        // by the frame's delta, the card straddling the edge and the card outside stay.
        val seeded = seedCanvas()
        val frame = seedFrame(seeded.canvasId, x = 0f, y = 0f)              // 388 × 212
        val inside = seedNode(seeded.canvasId, "in", x = 20f, y = 20f)       // 20…80 × 20…68
        val straddling = seedNode(seeded.canvasId, "edge", x = 350f, y = 20f) // "edge" is 60 wide: 350…410, past the frame's 388
        val outside = seedNode(seeded.canvasId, "out", x = 600f, y = 600f)
        val viewModel = canvasViewModel(seeded.page.id)
        testScheduler.advanceUntilIdle()

        viewModel.moveFrame(frame, 100f, 50f)
        testScheduler.advanceUntilIdle()

        val after = nodeDao.getForCanvas(seeded.canvasId).associateBy { it.id }
        assertEquals(100f to 50f, after.getValue(frame.id).let { it.x to it.y })
        assertEquals(120f to 70f, after.getValue(inside.id).let { it.x to it.y })
        assertEquals(350f to 20f, after.getValue(straddling.id).let { it.x to it.y })
        assertEquals(600f to 600f, after.getValue(outside.id).let { it.x to it.y })
    }

    @Test
    fun `a canvas saved as a template keeps its frames, cards and arrows with the arrows rejoined`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val frame = seedFrame(seeded.canvasId, x = 0f, y = 0f)
        val a = seedNode(seeded.canvasId, "a", x = 20f, y = 20f)
        val b = seedNode(seeded.canvasId, "b", x = 200f, y = 20f)
        edgeDao.insert(CanvasEdge(canvasId = seeded.canvasId, fromNodeId = a.id, toNodeId = b.id, label = "then"))
        val viewModel = canvasViewModel(seeded.page.id)
        testScheduler.advanceUntilIdle()
        var templateId: Long? = null

        viewModel.saveAsTemplate { templateId = it }
        testScheduler.advanceUntilIdle()

        val template = pageDao.getById(templateId!!)!!
        assertEquals(true, template.isTemplate)
        assertEquals(PageKind.CANVAS, template.kind)
        val clonedCanvas = canvasDao.getByPageId(template.id)!!
        val clonedNodes = nodeDao.getForCanvas(clonedCanvas.id)
        assertEquals(3, clonedNodes.size)
        assertEquals(1, clonedNodes.count { it.type == CanvasNodeType.FRAME })
        assertEquals(setOf("a", "b", frame.text), clonedNodes.map { it.text }.toSet())
        val clonedEdge = edgeDao.getForCanvas(clonedCanvas.id).single()
        assertEquals("then", clonedEdge.label)
        assertTrue("the arrow joins the clones, not the originals", clonedNodes.any { it.id == clonedEdge.fromNodeId } && clonedNodes.any { it.id == clonedEdge.toNodeId })
        assertEquals("the source board is untouched", 3, nodeDao.getForCanvas(seeded.canvasId).size)
    }

    @Test
    fun `a canvas page cannot be moved to Trash while View-Only is on`() = runTest(mainDispatcher) {
        // The phone's fix PR (P5, 2026-09-18): the canvas page's own `···` gained *Move to Trash*.
        val seeded = seedCanvas()
        val viewModel = canvasViewModel(seeded.page.id)
        lockEverything()
        var done = false

        viewModel.trashPage { done = true }

        assertEquals("the lock must refuse the trash write", false, done)
        assertNull("a canvas trashed under View-Only", pageDao.getById(seeded.page.id)?.deletedAt)
        assertPageUntouched(seeded.page)
    }

    @Test
    fun `a canvas node cannot be moved while View-Only is on`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val node = seedNode(seeded.canvasId, "pinned", x = 5f, y = 6f)
        val viewModel = canvasViewModel(seeded.page.id)
        lockEverything()

        viewModel.moveNode(node, x = 120f, y = 240f)

        assertEquals(
            "a drag under View-Only rewrote the node's position and exported it to every other device",
            listOf(5f to 6f),
            nodeDao.getForCanvas(seeded.canvasId).map { it.x to it.y },
        )
        assertPageUntouched(seeded.page)
    }

    @Test
    fun `a canvas node cannot be deleted while View-Only is on`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val node = seedNode(seeded.canvasId, "keep me")
        val viewModel = canvasViewModel(seeded.page.id)
        lockEverything()

        viewModel.deleteNode(node)

        assertEquals(
            "View-Only has to stop a destructive canvas action first of all",
            listOf("keep me"),
            nodeDao.getForCanvas(seeded.canvasId).map { it.text },
        )
        assertPageUntouched(seeded.page)
    }

    @Test
    fun `a canvas edge cannot be added while View-Only is on`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val from = seedNode(seeded.canvasId, "from")
        val to = seedNode(seeded.canvasId, "to")
        val viewModel = canvasViewModel(seeded.page.id)
        assertNotNull("harness: the canvas must be observed or the add path early-returns", viewModel.canvas.value)
        lockEverything()

        viewModel.addEdge(fromNodeId = from.id, toNodeId = to.id)

        assertEquals(
            "an edge is a structural change to the board, gated by nothing today",
            0,
            edgeDao.getForCanvas(seeded.canvasId).size,
        )
        assertPageUntouched(seeded.page)
    }

    @Test
    fun `a canvas edge cannot be deleted while View-Only is on`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val from = seedNode(seeded.canvasId, "from")
        val to = seedNode(seeded.canvasId, "to")
        edgeDao.insert(CanvasEdge(canvasId = seeded.canvasId, fromNodeId = from.id, toNodeId = to.id))
        val edge = edgeDao.getForCanvas(seeded.canvasId).single()
        val viewModel = canvasViewModel(seeded.page.id)
        lockEverything()

        viewModel.deleteEdge(edge)

        assertEquals(1, edgeDao.getForCanvas(seeded.canvasId).size)
        assertPageUntouched(seeded.page)
    }

    /**
     * The one that a funnel-shaped fix misses. Every other canvas mutation runs through
     * `launchAndTouch`, so a guard dropped into that one private helper looks complete — but
     * `updateTitle` calls `pageDao.update` directly (`CanvasViewModel.kt:75-80`) and would sail
     * straight past it, renaming a page and bumping its `updatedAt` in the same write. Kept as
     * its own test so an incomplete fix fails rather than passes.
     */
    @Test
    fun `a canvas page cannot be renamed while View-Only is on`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val viewModel = canvasViewModel(seeded.page.id)
        lockEverything()

        viewModel.updateTitle("Renamed under the lock")

        assertEquals(
            "updateTitle bypasses launchAndTouch, so a guard placed only in that funnel leaves the " +
                "rename — and the page-row bump it carries — completely ungated",
            "Board",
            pageDao.getById(seeded.page.id)!!.title,
        )
        assertPageUntouched(seeded.page)
    }

    /** The mirror of the six above: the guard must not turn the Canvas into a read-only screen
     * permanently. If this one fails, the harness is wrong and the rest prove nothing. */
    @Test
    fun `canvas edits still land when View-Only is off`() = runTest(mainDispatcher) {
        val seeded = seedCanvas()
        val viewModel = canvasViewModel(seeded.page.id)
        assertNotNull(viewModel.canvas.value)

        viewModel.addTextNode(x = 1f, y = 2f)
        viewModel.updateTitle("Renamed freely")

        assertEquals(1, nodeDao.getForCanvas(seeded.canvasId).size)
        assertEquals("Renamed freely", pageDao.getById(seeded.page.id)!!.title)
    }

    // =========================================================== 2. Road Map (§3.4, §3.1.2)

    /**
     * `relate` is `viewModelScope.launch { relationDao.addRelation(...) }` with no gate at all
     * (`RoadMapViewModel.kt:143-148`). A `page_relations` row is its own synced record, merged by
     * `PagesSyncEngine.mergeRelations` off no page's timestamp, so one made under the lock reaches
     * every other device on the next pass with nothing to mark it unintended.
     */
    @Test
    fun `a manual relation cannot be created while View-Only is on`() = runTest(mainDispatcher) {
        val from = seedPage("Alpha")
        val to = seedPage("Beta")
        val viewModel = roadMapViewModel()
        lockEverything()

        viewModel.relate(from.id, to.id)

        assertEquals(
            "Relate to… writes a syncing page_relations row through an ungated launch",
            0,
            relationDao.getAll().size,
        )
    }

    @Test
    fun `a manual relation is still created when View-Only is off`() = runTest(mainDispatcher) {
        val from = seedPage("Alpha")
        val to = seedPage("Beta")

        roadMapViewModel().relate(from.id, to.id)

        assertEquals(1, relationDao.getAll().size)
    }

    // ================================================ 3. Settings: import and restore (§9.4.1)

    /**
     * §3.1.2 as the user decided it: "View-Only is absolute, and it covers Settings." Notion
     * import and portable restore are the two largest create/destroy operations in the app, so
     * they refuse while the lock is on.
     *
     * The call sites are Compose (`SettingsScreen.kt:467` and `:517`) and there is no Robolectric
     * in this source set, so the refusal is asserted one layer down at [PortableArchive], which
     * is where the work actually happens — and where a guard also covers any future caller. That
     * means [PortableArchive] gains a [ViewLockState] constructor argument; these two tests do
     * not compile until it does.
     */
    private fun archive(entryDao: FakeEntryDao, backing: FakeContentResolverBacking) = PortableArchive(
        context = fakeContext(backing, temp.newFolder(), temp.newFolder()),
        entryDao = entryDao,
        habitDao = FakeHabitDao(),
        pageDao = mockk<PageDao>(relaxed = true),
        reminderDao = FakeReminderDao(),
        entryCompletionDao = FakeEntryCompletionDao(),
        habitCompletionDao = FakeHabitCompletionDao(),
        checkInDao = FakeCheckInDao(),
        timeLogDao = FakeTimeLogDao(),
        purgeRegistry = mockk(relaxed = true),
        pagesSyncEngine = mockk(relaxed = true),
        localImages = InMemoryLocalImageStore(),
        passphrase = { null },
        viewLockState = viewLockState,
        rearmAlarms = { _ -> },
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun archiveBytes(vararg records: EntrySnapshotRecord) = zipOfText(
        "manifest.json" to """{"appVersion":"0.1.0","exportedAtEpochMillis":1,"kind":"full","includedFiles":[]}""",
        "entries_active.json" to json.encodeToString(records.toList()),
    )

    private fun entryRecord(uid: String, title: String) =
        EntrySnapshotRecord(uid = uid, title = title, kind = "TASK", status = "PENDING", createdAt = 5_000L, updatedAt = 5_000L)

    private fun localEntry(uid: String, title: String) = Entry(
        id = 1,
        uid = uid,
        title = title,
        kind = EntryKind.TASK,
        startDate = null, startTime = null, endDate = null, endTime = null,
        recurrenceRule = null,
        status = EntryStatus.PENDING,
        createdAt = t0,
        updatedAt = t0,
    )

    /**
     * Deliberately indifferent to *how* the refusal is expressed — a thrown message (matching
     * this class's existing `require(...)`/"Nothing has been changed." idiom) and a silent
     * early return both satisfy it. What is asserted is the only thing that matters to the
     * person: the archive's records did not land. Swallowing the exception here keeps the test
     * failing on its assertion rather than on an unexpected throw.
     */
    @Test
    fun `a portable archive cannot be imported while View-Only is on`() = runTest(mainDispatcher) {
        val local = FakeEntryDao(listOf(localEntry("local-1", "Existing task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(archiveBytes(entryRecord("imported-1", "Imported task")))
        lockEverything()

        runCatching { archive(local, backing).importAdditive(uri) }

        assertEquals(
            "import is the largest create operation in the app and runs unguarded from Settings today",
            listOf("Existing task"),
            local.getAll().map { it.title },
        )
    }

    @Test
    fun `a backup cannot be restored while View-Only is on`() = runTest(mainDispatcher) {
        val local = FakeEntryDao(listOf(localEntry("local-1", "Precious local task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(archiveBytes(entryRecord("backup-1", "Task from the backup")))
        lockEverything()

        runCatching { archive(local, backing).restoreFromBackup(uri) }

        assertEquals(
            "restore wipes and replaces the whole store — the one operation View-Only most has to stop",
            listOf("Precious local task"),
            local.getAll().map { it.title },
        )
    }

    /** The mirror again: the lock must gate these, not disable them. */
    @Test
    fun `a portable archive still imports when View-Only is off`() = runTest(mainDispatcher) {
        val local = FakeEntryDao(listOf(localEntry("local-1", "Existing task")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(archiveBytes(entryRecord("imported-1", "Imported task")))

        archive(local, backing).importAdditive(uri)

        assertEquals(
            setOf("Existing task", "Imported task"),
            local.getAll().map { it.title }.toSet(),
        )
    }

    // ================================================ 4 & 5. The deliberate exemptions

    /**
     * NOT a defect — the decided policy, pinned so a later over-correction breaks a test instead
     * of the app.
     *
     * `ensureDefaultView` is idempotent repair-on-open, not an edit: §5.6 says "a Database always
     * has at least one Table view," and a database that predates the feature (or whose only view
     * was deleted) has to get one back the moment it is opened, lock or no lock. It is also the
     * one write on that screen deliberately outside `launchAndTouch`, so it claims no authorship
     * and cannot outrank a real schema edit made on another device. Gating it would leave a
     * viewless database permanently unrepairable for as long as View-Only is on.
     */
    /** Audit 2026-09-24: `setHue` (S13) wrote through `launchAndTouch`, which — unlike Canvas's
     * funnel of the same name — does not check the lock; the only gate was the `···` button, so a
     * hue sheet already open when View-Only went on still wrote on Done. */
    @Test
    fun `a database's hue cannot be changed while View-Only is on`() = runTest(mainDispatcher) {
        val page = seedPage("Tasks", PageKind.DATABASE)
        val databaseId = pageDatabaseDao.insert(PageDatabase(pageId = page.id, createdAt = t0, updatedAt = t0))
        val viewModel = pageDatabaseViewModel(page.id)
        lockEverything()

        viewModel.setHue(200)
        testScheduler.advanceUntilIdle()

        assertNull(pageDatabaseDao.getById(databaseId)!!.hue)
        assertPageUntouched(page)
    }

    @Test
    fun `a database's hue still changes when View-Only is off`() = runTest(mainDispatcher) {
        val page = seedPage("Tasks", PageKind.DATABASE)
        val databaseId = pageDatabaseDao.insert(PageDatabase(pageId = page.id, createdAt = t0, updatedAt = t0))
        val viewModel = pageDatabaseViewModel(page.id)

        viewModel.setHue(200)
        testScheduler.advanceUntilIdle()

        assertEquals("the refusal above must not pass vacuously", 200, pageDatabaseDao.getById(databaseId)!!.hue)
    }

    @Test
    fun `ensureDefaultView still repairs a viewless database while View-Only is on`() = runTest(mainDispatcher) {
        val page = seedPage("Tasks", PageKind.DATABASE)
        val databaseId = pageDatabaseDao.insert(PageDatabase(pageId = page.id, createdAt = t0, updatedAt = t0))
        val viewModel = pageDatabaseViewModel(page.id)
        assertNotNull("harness: the database must be observed or ensureDefaultView early-returns", viewModel.database.value)
        lockEverything()

        viewModel.ensureDefaultView()

        assertEquals(
            "idempotent repair-on-open is exempt by decision — §5.6's 'a Database always has at " +
                "least one Table view' must hold even while the lock is on",
            1,
            viewDao.countForDatabase(databaseId),
        )
        assertPageUntouched(page)
    }

    /**
     * Also NOT a defect. Entry *resolution* stays ungated by decision: the notification's inline
     * action, the Habits widget, Calendar and Tasks are quick-capture surfaces outside the Pages
     * hub, where the eye toggle is not visible and a person ticking a reminder off has no way to
     * know a lock they set on a different screen is silently swallowing the tap.
     * `PageDatabaseViewModel.toggleDone` stays gated as it is today, because that one *is* inside
     * Pages; the split is intentional. [ResolveEntryUseCase] itself must therefore never learn
     * about [ViewLockState], and no `entryDao` call site should be gated.
     */
    @Test
    fun `resolving a task still works while View-Only is on`() = runTest(mainDispatcher) {
        val id = entryDao.insert(localEntry("entry-1", "Take the tablets").copy(id = 0))
        lockEverything()

        resolveEntryUseCase.setDone(id, done = true)

        assertEquals(
            "entry resolution is exempt by decision — quick-capture surfaces outside Pages cannot " +
                "show the lock, so silently dropping the tap would be worse than honouring it",
            EntryStatus.DONE,
            entryDao.getById(id)!!.status,
        )
        assertTrue("and the completion was logged, so the resolution really ran", completionDao.rows.isNotEmpty())
    }
}

/**
 * Reached only because [ResolveEntryUseCase] and [DatabaseSyncManager] are constructor arguments
 * of what this file exercises. `WritePathSyncTest` has its own file-private equivalent; this one
 * additionally exposes [rows], which the entry-resolution exemption test asserts on.
 */
private class RecordingEntryCompletionDao : EntryCompletionDao {
    val rows = mutableListOf<EntryCompletion>()

    override suspend fun insert(completion: EntryCompletion): Long {
        rows += completion
        return rows.size.toLong()
    }

    override fun observeForEntry(entryId: Long): Flow<List<EntryCompletion>> =
        flowOf(rows.filter { it.entryId == entryId })
    /** §9.4 / S2 — the snapshot write pass republishes the whole table. */
    override suspend fun getAll(): List<EntryCompletion> = rows.toList()

    override suspend fun getByUid(uid: String): EntryCompletion? =
        rows.firstOrNull { it.uid == uid }


    override suspend fun deleteAll() { rows.clear() }
}
