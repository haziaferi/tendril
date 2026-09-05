@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tendril.app.ui.canvas

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
) : ViewModel() {
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

    init {
        viewModelScope.launch {
            if (pageCanvasDao.getByPageId(pageId) == null) {
                val now = Instant.now()
                pageCanvasDao.insert(PageCanvas(pageId = pageId, createdAt = now, updatedAt = now))
            }
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            val current = page.value ?: pageDao.getById(pageId) ?: return@launch
            pageDao.update(current.copy(title = title, updatedAt = Instant.now()))
        }
    }

    fun addTextNode(x: Float, y: Float) {
        val canvasId = canvas.value?.id ?: return
        viewModelScope.launch {
            val now = Instant.now()
            canvasNodeDao.insert(
                CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = x, y = y, text = "", createdAt = now, updatedAt = now)
            )
        }
    }

    fun addPageEmbedNode(x: Float, y: Float, targetPage: Page) {
        val canvasId = canvas.value?.id ?: return
        viewModelScope.launch {
            val now = Instant.now()
            canvasNodeDao.insert(
                CanvasNode(
                    canvasId = canvasId, type = CanvasNodeType.PAGE_EMBED, x = x, y = y,
                    embeddedPageId = targetPage.id, createdAt = now, updatedAt = now,
                )
            )
        }
    }

    fun moveNode(node: CanvasNode, x: Float, y: Float) {
        viewModelScope.launch { canvasNodeDao.update(node.copy(x = x, y = y, updatedAt = Instant.now())) }
    }

    fun setNodeText(node: CanvasNode, text: String) {
        viewModelScope.launch { canvasNodeDao.update(node.copy(text = text, updatedAt = Instant.now())) }
    }

    fun deleteNode(node: CanvasNode) {
        viewModelScope.launch { canvasNodeDao.delete(node.id) }
    }

    fun addEdge(fromNodeId: Long, toNodeId: Long) {
        val canvasId = canvas.value?.id ?: return
        if (fromNodeId == toNodeId) return
        viewModelScope.launch {
            canvasEdgeDao.insert(CanvasEdge(canvasId = canvasId, fromNodeId = fromNodeId, toNodeId = toNodeId))
        }
    }

    fun cycleEdgeDirection(edge: CanvasEdge) {
        val next = when (edge.direction) {
            CanvasArrowDirection.ONE_WAY -> CanvasArrowDirection.TWO_WAY
            CanvasArrowDirection.TWO_WAY -> CanvasArrowDirection.NONE
            CanvasArrowDirection.NONE -> CanvasArrowDirection.ONE_WAY
        }
        viewModelScope.launch { canvasEdgeDao.update(edge.copy(direction = next)) }
    }

    fun setEdgeLabel(edge: CanvasEdge, label: String) {
        viewModelScope.launch { canvasEdgeDao.update(edge.copy(label = label.ifBlank { null })) }
    }

    fun deleteEdge(edge: CanvasEdge) {
        viewModelScope.launch { canvasEdgeDao.delete(edge.id) }
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
