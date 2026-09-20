@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tendril.app.ui.canvas

import com.tendril.app.domain.canvas.CANVAS_CARD_MIN_W
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasEdgeDao
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeDao
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.data.canvas.PageCanvasDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.domain.ViewLockState
import com.tendril.app.domain.PageContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.canvas.FRAME_DEFAULT_H
import com.tendril.app.domain.canvas.FRAME_DEFAULT_LABEL
import com.tendril.app.domain.canvas.FRAME_DEFAULT_W
import com.tendril.app.domain.canvas.clampFrameSize
import com.tendril.app.domain.canvas.tidy
import com.tendril.app.domain.canvas.newChildPosition
import com.tendril.app.domain.canvas.TREE_SIBLING_GAP
import com.tendril.app.domain.canvas.CanvasTree
import com.tendril.app.domain.canvas.CanvasStructure
import com.tendril.app.domain.canvas.nodesInside
import java.time.Instant

/**
 * The in-page Canvas (Obsidian Canvas reference, §10's deferred in-page mind-map brought into
 * scope) — a [com.tendril.app.data.page.PageKind.CANVAS] page's own freeform node/edge graph,
 * one 1:1 [PageCanvas] companion row per Canvas page, same shape as
 * [com.tendril.app.ui.pages.PageDatabaseViewModel]'s relationship to `PageDatabase`.
 */
class CanvasViewModel(
    private val pageId: Long,
    private val pageDao: PageDao,
    private val pageCanvasDao: PageCanvasDao,
    private val canvasNodeDao: CanvasNodeDao,
    private val canvasEdgeDao: CanvasEdgeDao,
    private val viewLockState: ViewLockState,
    private val pageContentRepository: PageContentRepository,
    /** §0.10 item 15 — *Save as template* on a canvas; the manager clones the board. */
    private val templateManager: TemplateManager,
) : ViewModel() {
    /** §3.1.2 — the same single enforcement point every other editing ViewModel keeps (see
     * [com.tendril.app.ui.pages.PageDetailViewModel.viewOnlyLocked] and
     * [com.tendril.app.ui.pages.PageDatabaseViewModel.locked]), duplicated per ViewModel rather
     * than shared because a Canvas's edits and a Page's edits go through entirely separate
     * ViewModels. Read at call time rather than captured, so flipping the eye toggle in the
     * Pages topbar takes effect on a board that is already open. */
    private fun locked() = viewLockState.viewOnly.value

    val page: StateFlow<Page?> = pageDao.observeById(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canvas: StateFlow<PageCanvas?> = pageCanvasDao.observeByPageId(pageId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val nodes: StateFlow<List<CanvasNode>> = canvas.filterNotNull()
        .flatMapLatest { canvasNodeDao.observeForCanvas(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val edges: StateFlow<List<CanvasEdge>> = canvas.filterNotNull()
        .flatMapLatest { canvasEdgeDao.observeForCanvas(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Resolves each PAGE_EMBED node's target title/kind for rendering, without every node
     * composable needing its own page lookup. */
    val embeddedPages: StateFlow<Map<Long, Page>> = nodes.flatMapLatest { nodeList ->
        val ids = nodeList.mapNotNull { it.embeddedPageId }
        if (ids.isEmpty()) flowOf(emptyList()) else pageDao.observeByIds(ids)
    }.map { pages -> pages.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _pageSearchResults = MutableStateFlow<List<Page>>(emptyList())
    val pageSearchResults: StateFlow<List<Page>> = _pageSearchResults.asStateFlow()

    /**
     * The lazy [PageCanvas] shell, and the one write on this screen deliberately exempt from
     * [locked] — the same decided exemption as
     * [com.tendril.app.ui.pages.PageDatabaseViewModel.ensureDefaultView], for the same two
     * reasons. It is idempotent repair-on-open, not an edit: a Canvas page created before this
     * companion row existed (or whose row never landed) has no board at all until one is made,
     * so gating it would leave that page permanently unopenable-as-a-canvas for as long as
     * View-Only is on. And it claims no authorship — it is outside [launchAndTouch] on purpose,
     * so it bumps no page row and can never outrank a real edit made on another device (§9.4).
     * Every device performs the same repair for itself; the shell costs nothing by staying local.
     */
    init {
        viewModelScope.launch {
            if (pageCanvasDao.getByPageId(pageId) == null) {
                val now = Instant.now()
                pageCanvasDao.insert(PageCanvas(pageId = pageId, createdAt = now, updatedAt = now))
            }
        }
    }

    /**
     * §3.1.2 — gated on its own rather than by [launchAndTouch], because it is the one mutation
     * on this screen that does not go through that funnel: it writes `pages` directly, so a guard
     * placed only in the funnel would leave the rename — and the `updatedAt` bump it carries —
     * completely ungated, exporting a title nobody chose to every other device on the next pass.
     */
    fun updateTitle(title: String) {
        if (locked()) return
        viewModelScope.launch {
            val current = page.value ?: pageDao.getById(pageId) ?: return@launch
            pageDao.update(current.copy(title = title, updatedAt = Instant.now()))
            // The title is in the index (§3.1.1), so a rename re-indexes like a block edit does.
            pageContentRepository.rebuildFtsForPage(pageId)
        }
    }

    /** The phone's fix PR (P5, 2026-09-18): the canvas page's own *Move to Trash* — the database's
     * write (`PageDatabaseViewModel.trashDatabase`) without the row-linked entry a canvas never has;
     * refused under the lock like every write here. */
    fun trashPage(onDone: () -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            pageDao.softDelete(pageId, Instant.now())
            onDone()
        }
    }

    /**
     * §9.4 — see [PageDao.touch]. Nodes and edges travel *inside* this Canvas page's snapshot,
     * and the merge decides whether to apply them by looking at the page row alone: a node moved
     * or renamed without the page row saying so exports under an unchanged timestamp, loses on
     * the peer for not being newer, and is dropped.
     *
     * Every mutation below therefore goes through here rather than `viewModelScope.launch`
     * directly, the same reason [com.tendril.app.ui.pages.PageDetailViewModel] funnels its block
     * edits through `launchAndReindex` — a canvas edit that forgets the bump looks completely
     * saved on this device and simply never arrives on the other one.
     *
     * The lazy [PageCanvas] shell in `init` is left out on purpose: it runs on open rather than
     * on an edit, and a bump is a claim of authorship that would let merely opening a board
     * outrank a real edit made on another device and not yet synced.
     *
     * §3.1.2 — [locked] is checked *here*, not in each caller, for the same reason the touch
     * lives here: every node and edge mutation on this board already funnels through this one
     * helper, so the gate is a property of making a canvas write rather than a line each future
     * mutation has to remember. Refusing before [block] runs also refuses the bump, since a bump
     * under the lock would be a claim of authorship for an edit the person never made — enough
     * on its own to outrank a real edit waiting on another device (§9.4). [updateTitle] is the
     * one mutation outside this funnel and carries its own gate.
     */
    private fun launchAndTouch(block: suspend () -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            block()
            pageDao.touch(pageId, Instant.now())
        }
    }

    /** [onInserted] receives the new card's id — L10's double-click opens its editor at once. */
    fun addTextNode(x: Float, y: Float, onInserted: (Long) -> Unit = {}) {
        val canvasId = canvas.value?.id ?: return
        launchAndTouch {
            val now = Instant.now()
            val id = canvasNodeDao.insert(
                CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = x, y = y, text = "", createdAt = now, updatedAt = now)
            )
            onInserted(id)
        }
    }

    fun addPageEmbedNode(x: Float, y: Float, targetPage: Page) {
        val canvasId = canvas.value?.id ?: return
        launchAndTouch {
            val now = Instant.now()
            canvasNodeDao.insert(
                CanvasNode(
                    canvasId = canvasId, type = CanvasNodeType.PAGE_EMBED, x = x, y = y,
                    embeddedPageId = targetPage.id, createdAt = now, updatedAt = now,
                )
            )
        }
    }

    // The mind-map pass (2026-09-20, `domain/canvas/Tree.kt`) — the tree's verbs. Every one funnels
    // through `launchAndTouch`, so the lock and the sync bump hold for them as for every other write.

    /** The board's structure, from its row; `free` until set. */
    val structure: StateFlow<CanvasStructure> = canvas.map { CanvasStructure.fromKey(it?.structure) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CanvasStructure.FREE)

    private fun titleOf(id: Long): String? = embeddedPages.value[id]?.title
    private fun tree(): CanvasTree = CanvasTree(nodes.value, structure.value, ::titleOf)

    /** A node moves with its subtree (Xmind's, Freeplane's); a following frame moves the node it follows. */
    fun moveNode(node: CanvasNode, x: Float, y: Float) {
        val tree = tree()
        if (node.type == CanvasNodeType.FRAME) { tree.followedBy(node)?.let { anchor -> moveNode(anchor, anchor.x + (x - node.x), anchor.y + (y - node.y)); return } }
        val dx = x - node.x; val dy = y - node.y
        val carried = tree.descendants(node.id).filter { it.type != CanvasNodeType.FRAME }
        launchAndTouch {
            val now = Instant.now()
            canvasNodeDao.update(node.copy(x = x, y = y, updatedAt = now))
            carried.forEach { canvasNodeDao.update(it.copy(x = it.x + dx, y = it.y + dy, updatedAt = now)) }
        }
    }

    /** Tidy: the layout's positions for one subtree, or for every tree on the board (`rootId` null). The roots stay. */
    /** S8 — the tree a node belongs to, tidied from its root (a band that grew moves its neighbours, so the whole tree, not the parent's subtree alone). */
    fun tidyTreeOf(node: CanvasNode) {
        tree().rootOf(node.id)?.let { tidy(it.id) }
    }

    fun tidy(rootId: Long? = null) {
        val tree = tree()
        val roots = if (rootId != null) listOfNotNull(tree.byId[rootId]) else nodes.value.filter { it.parentId == null && it.type != CanvasNodeType.FRAME && tree.children(it.id).isNotEmpty() }
        val moves = roots.flatMap { r -> tidy(tree, r.id).entries }
        if (moves.isEmpty()) return
        launchAndTouch {
            val now = Instant.now()
            for ((id, at) in moves) {
                val n = tree.byId[id] ?: continue
                if (n.x != at.first || n.y != at.second) canvasNodeDao.update(n.copy(x = at.first, y = at.second, updatedAt = now))
            }
        }
    }

    /** The board's structure; the trees are tidied to it at once (Xmind re-lays on a structure change). */
    fun setBoardStructure(structure: CanvasStructure) {
        val current = canvas.value ?: return
        launchAndTouch {
            pageCanvasDao.update(current.copy(structure = structure.key, updatedAt = Instant.now()))
            val tree = CanvasTree(nodes.value, structure, ::titleOf)
            val now = Instant.now()
            for (r in nodes.value.filter { it.parentId == null && it.type != CanvasNodeType.FRAME && tree.children(it.id).isNotEmpty() }) {
                for ((id, at) in tidy(tree, r.id)) tree.byId[id]?.let { n -> canvasNodeDao.update(n.copy(x = at.first, y = at.second, updatedAt = now)) }
            }
        }
    }

    /** A node's own structure for its subtree (null inherits), then that subtree tidied. */
    fun setNodeStructure(node: CanvasNode, structure: CanvasStructure?) {
        launchAndTouch {
            canvasNodeDao.update(node.copy(structure = structure?.key, updatedAt = Instant.now()))
            val updated = nodes.value.map { if (it.id == node.id) it.copy(structure = structure?.key) else it }
            val tree = CanvasTree(updated, this.structure.value, ::titleOf)
            val now = Instant.now()
            for ((id, at) in tidy(tree, node.id)) tree.byId[id]?.let { n -> canvasNodeDao.update(n.copy(x = at.first, y = at.second, updatedAt = now)) }
        }
    }

    /** A child after the last sibling, beside or under its parent (`newChildPosition`); its editor opens through [onInserted]. */
    fun addChild(parent: CanvasNode, onInserted: (Long) -> Unit = {}) {
        val canvasId = canvas.value?.id ?: return
        val (x, y) = newChildPosition(tree(), parent)
        launchAndTouch {
            val now = Instant.now()
            val id = canvasNodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = x, y = y, text = "", parentId = parent.id, createdAt = now, updatedAt = now))
            if (parent.folded) canvasNodeDao.update(parent.copy(folded = false, updatedAt = now))
            onInserted(id)
        }
    }

    /** A sibling: the parent's next child; for a free card, a card under it. */
    fun addSibling(node: CanvasNode, onInserted: (Long) -> Unit = {}) {
        val parent = node.parentId?.let { id -> nodes.value.firstOrNull { it.id == id } }
        if (parent != null) addChild(parent, onInserted)
        else addTextNode(node.x, node.y + tree().box(node).h + TREE_SIBLING_GAP, onInserted)
    }

    /** The parent link: null detaches (the node keeps its place — a free node); a new parent places
     * the node after that parent's last child. A node can never be its own ancestor. */
    fun setParent(node: CanvasNode, parent: CanvasNode?) {
        if (parent != null) {
            if (parent.id == node.id || parent.type == CanvasNodeType.FRAME) return
            val tree = tree()
            if (tree.descendants(node.id).any { it.id == parent.id }) return
            val (x, y) = newChildPosition(tree, parent)
            val dx = x - node.x; val dy = y - node.y
            val carried = tree.descendants(node.id).filter { it.type != CanvasNodeType.FRAME }
            launchAndTouch {
                val now = Instant.now()
                canvasNodeDao.update(node.copy(parentId = parent.id, x = x, y = y, updatedAt = now))
                carried.forEach { canvasNodeDao.update(it.copy(x = it.x + dx, y = it.y + dy, updatedAt = now)) }
                if (parent.folded) canvasNodeDao.update(parent.copy(folded = false, updatedAt = now))
            }
        } else {
            launchAndTouch { canvasNodeDao.update(node.copy(parentId = null, updatedAt = Instant.now())) }
        }
    }

    fun toggleFold(node: CanvasNode) {
        launchAndTouch { canvasNodeDao.update(node.copy(folded = !node.folded, updatedAt = Instant.now())) }
    }

    /** A frame that follows this node's subtree (Xmind's boundary); it has no box of its own. */
    fun frameSubtree(node: CanvasNode, onInserted: (Long) -> Unit = {}) {
        val canvasId = canvas.value?.id ?: return
        launchAndTouch {
            val now = Instant.now()
            val id = canvasNodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.FRAME, x = node.x, y = node.y, width = FRAME_DEFAULT_W, height = FRAME_DEFAULT_H, text = FRAME_DEFAULT_LABEL, parentId = node.id, createdAt = now, updatedAt = now))
            onInserted(id)
        }
    }

    // §0.10 item 15 — frames (`domain/canvas/Frames.kt`).

    fun addFrame(x: Float, y: Float, onInserted: (Long) -> Unit = {}) {
        val canvasId = canvas.value?.id ?: return
        launchAndTouch {
            val now = Instant.now()
            val id = canvasNodeDao.insert(
                CanvasNode(canvasId = canvasId, type = CanvasNodeType.FRAME, x = x, y = y, width = FRAME_DEFAULT_W, height = FRAME_DEFAULT_H, text = FRAME_DEFAULT_LABEL, createdAt = now, updatedAt = now)
            )
            onInserted(id)
        }
    }

    /** The frame and every node wholly inside it move together — geometry, no parent (Obsidian's, measured);
     * a following frame moves the subtree it follows instead. */
    fun moveFrame(frame: CanvasNode, x: Float, y: Float) {
        if (frame.parentId != null) { moveNode(frame, x, y); return }
        val dx = x - frame.x; val dy = y - frame.y
        val carried = nodesInside(frame, nodes.value)
        launchAndTouch {
            val now = Instant.now()
            canvasNodeDao.update(frame.copy(x = x, y = y, updatedAt = now))
            carried.forEach { canvasNodeDao.update(it.copy(x = it.x + dx, y = it.y + dy, updatedAt = now)) }
        }
    }

    /** The card's width by hand (Obsidian's rule): the left edge moved by [dx] dp — the right edge stays, `width` is written,
     * the text keeps deciding the height; never under [CANVAS_CARD_MIN_W]. */
    fun resizeCard(card: CanvasNode, dx: Float) {
        val w0 = tree().box(card).w
        val w1 = (w0 - dx).coerceAtLeast(CANVAS_CARD_MIN_W)
        launchAndTouch { canvasNodeDao.update(card.copy(x = card.x + (w0 - w1), width = w1, updatedAt = Instant.now())) }
    }

    fun resizeFrame(frame: CanvasNode, width: Float, height: Float) {
        val (w, h) = clampFrameSize(width, height)
        launchAndTouch { canvasNodeDao.update(frame.copy(width = w, height = h, updatedAt = Instant.now())) }
    }

    /** §3.1.3's verb on a canvas: a new template page carrying the board (the page's `···` has it). */
    fun saveAsTemplate(onSaved: (Long) -> Unit = {}) {
        val current = page.value ?: return
        if (locked()) return
        viewModelScope.launch { onSaved(templateManager.saveAsTemplate(current)) }
    }

    fun setNodeText(node: CanvasNode, text: String) {
        launchAndTouch { canvasNodeDao.update(node.copy(text = text, updatedAt = Instant.now())) }
    }

    /** A deleted node lifts its children to its own parent (never a cascade); a frame following it goes with it. */
    fun deleteNode(node: CanvasNode) {
        val tree = tree()
        val lifted = tree.children(node.id)
        val followers = nodes.value.filter { it.type == CanvasNodeType.FRAME && it.parentId == node.id }
        launchAndTouch {
            val now = Instant.now()
            lifted.forEach { canvasNodeDao.update(it.copy(parentId = node.parentId, updatedAt = now)) }
            followers.forEach { canvasNodeDao.delete(it.id) }
            canvasNodeDao.delete(node.id)
        }
    }

    fun addEdge(fromNodeId: Long, toNodeId: Long) {
        val canvasId = canvas.value?.id ?: return
        if (fromNodeId == toNodeId) return
        launchAndTouch {
            canvasEdgeDao.insert(CanvasEdge(canvasId = canvasId, fromNodeId = fromNodeId, toNodeId = toNodeId))
        }
    }

    fun cycleEdgeDirection(edge: CanvasEdge) {
        val next = when (edge.direction) {
            CanvasArrowDirection.ONE_WAY -> CanvasArrowDirection.TWO_WAY
            CanvasArrowDirection.TWO_WAY -> CanvasArrowDirection.NONE
            CanvasArrowDirection.NONE -> CanvasArrowDirection.ONE_WAY
        }
        launchAndTouch { canvasEdgeDao.update(edge.copy(direction = next)) }
    }

    fun setEdgeLabel(edge: CanvasEdge, label: String) {
        launchAndTouch { canvasEdgeDao.update(edge.copy(label = label.ifBlank { null })) }
    }

    fun deleteEdge(edge: CanvasEdge) {
        launchAndTouch { canvasEdgeDao.delete(edge.id) }
    }

    fun searchPages(query: String) {
        viewModelScope.launch {
            _pageSearchResults.value = if (query.isBlank()) emptyList() else pageDao.getAll().filter {
                it.deletedAt == null && !it.isTemplate && it.id != pageId && it.title.contains(query, ignoreCase = true)
            }.take(20)
        }
    }

    fun clearPageSearch() { _pageSearchResults.value = emptyList() }
}
