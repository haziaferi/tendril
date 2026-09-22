package com.tendril.app.sync

import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.ui.canvas.CanvasViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * §3.7 — the arrow editor writes from the snapshot it was opened with.
 *
 * `CanvasScreen` captures the tapped edge into `editingEdge` and never re-resolves it against the
 * `edges` flow, so every control in the sheet acts on the row as it stood when the sheet opened.
 * §3.7 records one consequence of that ("direction never cycles past one step and the displayed
 * direction goes stale") and misses the worse one: the label field writes `edge.copy(label = …)`
 * on every keystroke, so a whole stale row goes back to the database — **including a direction the
 * person has already changed.** A change that landed is silently undone by typing.
 *
 * Both tests here drive the ViewModel the way the sheet does: holding one `CanvasEdge` value and
 * calling with it more than once. Neither re-reads between calls, because the screen does not.
 *
 * The fix makes this a property of the write rather than of the caller's freshness — the same move
 * as §9.7's re-arm seam, where leaving each call site to remember is what produced four separate
 * defects. A stale snapshot reaching these methods is then merely stale, not destructive.
 */
class CanvasStaleEdgeTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before fun installMainDispatcher() = Dispatchers.setMain(mainDispatcher)

    @After fun removeMainDispatcher() = Dispatchers.resetMain()

    private val t0: Instant = Instant.ofEpochMilli(1_000L)

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val blockDao = FakeBlockDao(store)
    private val pageDatabaseDao = FakePageDatabaseDao(store)
    private val propertyDao = FakePropertyDao(store)
    private val canvasDao = FakePageCanvasDao(store)
    private val nodeDao = FakeCanvasNodeDao(store)
    private val edgeDao = FakeCanvasEdgeDao(store)
    private val ftsDao = FakePageFtsDao(store)

    private val contentRepository = PageContentRepository(pageDao, blockDao, ftsDao, FakeBlockFtsDao(store))
    private val templateManager =
        TemplateManager(pageDao, blockDao, pageDatabaseDao, propertyDao, canvasDao, nodeDao, edgeDao)

    /** Never locked here: the point is what an *allowed* write does with a stale argument. */
    private val viewLockState = ViewLockState()

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
        // `canvas` is `WhileSubscribed`, and the write paths read `canvas.value ?: return`.
        // Without these collectors a test would assert that an early return wrote nothing.
        backgroundScope.launch { viewModel.canvas.collect { } }
        backgroundScope.launch { viewModel.edges.collect { } }
        backgroundScope.launch { viewModel.page.collect { } }
        testScheduler.advanceUntilIdle()
        return viewModel
    }

    private data class Board(val pageId: Long, val canvasId: Long, val edge: CanvasEdge)

    private suspend fun seedBoardWithOneEdge(): Board {
        val pageId = store.seedPage(Page(title = "Board", kind = PageKind.CANVAS, createdAt = t0, updatedAt = t0))
        val canvasId = canvasDao.insert(PageCanvas(pageId = pageId, createdAt = t0, updatedAt = t0))
        val from = nodeDao.insert(
            CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = 0f, y = 0f, text = "from", createdAt = t0, updatedAt = t0)
        )
        val to = nodeDao.insert(
            CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = 100f, y = 0f, text = "to", createdAt = t0, updatedAt = t0)
        )
        edgeDao.insert(
            CanvasEdge(canvasId = canvasId, fromNodeId = from, toNodeId = to, direction = CanvasArrowDirection.ONE_WAY)
        )
        return Board(pageId, canvasId, edgeDao.getForCanvas(canvasId).single())
    }

    private suspend fun edgeNow(canvasId: Long): CanvasEdge = edgeDao.getForCanvas(canvasId).single()

    @Test
    fun `pressing Change twice advances the direction twice`() = runTest(mainDispatcher) {
        val board = seedBoardWithOneEdge()
        val captured = board.edge
        val viewModel = canvasViewModel(board.pageId)
        assertNotNull("harness: the canvas must be observed or the write path early-returns", viewModel.canvas.value)

        // The sheet holds one value and hands it back each press — it has no other.
        viewModel.cycleEdgeDirection(captured)
        testScheduler.advanceUntilIdle()
        assertEquals(CanvasArrowDirection.TWO_WAY, edgeNow(captured.canvasId).direction)

        viewModel.cycleEdgeDirection(captured)
        testScheduler.advanceUntilIdle()
        assertEquals(
            "ONE_WAY -> TWO_WAY -> NONE is the whole cycle; computing the next step from the " +
                "snapshot the sheet opened with leaves it stuck one past the start",
            CanvasArrowDirection.NONE,
            edgeNow(captured.canvasId).direction,
        )
    }

    @Test
    fun `typing a label does not undo a direction the person already changed`() = runTest(mainDispatcher) {
        val board = seedBoardWithOneEdge()
        val captured = board.edge
        val viewModel = canvasViewModel(board.pageId)
        assertNotNull("harness: the canvas must be observed or the write path early-returns", viewModel.canvas.value)

        viewModel.cycleEdgeDirection(captured)
        testScheduler.advanceUntilIdle()
        assertEquals("precondition: the change landed", CanvasArrowDirection.TWO_WAY, edgeNow(captured.canvasId).direction)

        // One keystroke in the label field, which fires `onSetLabel` per character.
        viewModel.setEdgeLabel(captured, "blocks")
        testScheduler.advanceUntilIdle()

        val after = edgeNow(captured.canvasId)
        assertEquals("the label is what the person was editing", "blocks", after.label)
        assertEquals(
            "a whole-row copy() from the sheet's opening snapshot carries that snapshot's " +
                "direction back over the one the person chose a moment earlier",
            CanvasArrowDirection.TWO_WAY,
            after.direction,
        )
    }

    @Test
    fun `a blank label is still stored as null`() = runTest(mainDispatcher) {
        // §3.7: "blank is stored as null, so an emptied label is absent rather than an empty
        // string the merge would have to treat as content." Unpinned until now, and the fix
        // below rewrites this method, so it is pinned before the rewrite rather than after.
        val board = seedBoardWithOneEdge()
        val captured = board.edge
        val viewModel = canvasViewModel(board.pageId)
        assertNotNull("harness: the canvas must be observed or the write path early-returns", viewModel.canvas.value)

        viewModel.setEdgeLabel(captured, "temp")
        testScheduler.advanceUntilIdle()
        assertEquals("temp", edgeNow(captured.canvasId).label)

        viewModel.setEdgeLabel(edgeNow(captured.canvasId), "   ")
        testScheduler.advanceUntilIdle()
        assertEquals(null, edgeNow(captured.canvasId).label)
    }
}
