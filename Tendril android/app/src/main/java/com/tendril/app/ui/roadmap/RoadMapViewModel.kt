package com.tendril.app.ui.roadmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.addRelation
import com.tendril.app.data.page.PageRelationDao
import com.tendril.app.domain.PageContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** §3.4's two edge sources, kept distinct through rendering — a mention has a real
 * mentioner→mentioned direction (drawn as an arrow); a manual "Relate to" is genuinely
 * undirected (drawn as a plain line). */
enum class EdgeSource { MENTION, MANUAL }

data class RoadMapEdge(val fromPageId: Long, val toPageId: Long, val source: EdgeSource)

data class RoadMapGraph(val nodes: List<Page>, val edges: List<RoadMapEdge>) {
    /** Every id a given node touches, either direction — the neighbor-highlight set and the
     * BFS focus walk both read this rather than re-deriving it per call. */
    fun neighborsOf(pageId: Long): Set<Long> = edges.mapNotNull { edge ->
        when (pageId) {
            edge.fromPageId -> edge.toPageId
            edge.toPageId -> edge.fromPageId
            else -> null
        }
    }.toSet()

    fun degreeOf(pageId: Long): Int = edges.count { it.fromPageId == pageId || it.toPageId == pageId }
}

/**
 * §3.4 — assembles the graph from the two decided edge sources (mentions, computed live off
 * Block data; manual relations, read from [PageRelationDao]) and exposes it as a plain
 * snapshot, not a continuously-observed Flow — matching [com.tendril.app.ui.pages.PageDetailViewModel]'s
 * own backlinks precedent ("loaded once per page-open... a backlink only changes when some
 * *other* page's content is edited, which this screen has no reason to be watching for").
 * [refresh] re-scans on demand (the screen's refresh action, and after "Relate to").
 *
 * Node positions/physics are deliberately NOT owned here — they're ephemeral animation state
 * scoped to the Composable's own lifecycle, so this class stays plain data + one-shot I/O.
 */
class RoadMapViewModel(
    private val pageDao: PageDao,
    private val relationDao: PageRelationDao,
    private val contentRepository: PageContentRepository,
) : ViewModel() {
    private val _graph = MutableStateFlow(RoadMapGraph(emptyList(), emptyList()))
    val graph: StateFlow<RoadMapGraph> = _graph.asStateFlow()

    /** §3.4 — "All Pages" bottom drawer content; every non-template, non-deleted page,
     * independent of whether it has any edges at all (an unconnected page still belongs on
     * the list — only the canvas itself needs an edge to be worth drawing). */
    val allPages: StateFlow<List<Page>> get() = _allPages
    private val _allPages = MutableStateFlow(emptyList<Page>())

    private val _pageSearchResults = MutableStateFlow<List<Page>>(emptyList())
    val pageSearchResults: StateFlow<List<Page>> = _pageSearchResults.asStateFlow()

    /** The Obsidian "local graph" pattern — closes the "no way to pick which page to focus
     * on" gap: null shows the full graph, non-null shows just that page plus its neighbors
     * out to [focusDepth] hops. Both live in this ViewModel (not the canvas) since they're
     * real filter *state*, not ephemeral animation state — unlike node position/velocity. */
    private val _focusedPageId = MutableStateFlow<Long?>(null)
    val focusedPageId: StateFlow<Long?> = _focusedPageId.asStateFlow()

    private val _focusDepth = MutableStateFlow(1)
    val focusDepth: StateFlow<Int> = _focusDepth.asStateFlow()

    val displayedGraph: StateFlow<RoadMapGraph> = combine(_graph, _focusedPageId, _focusDepth) { full, focus, depth ->
        if (focus == null) full else localGraph(full, focus, depth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoadMapGraph(emptyList(), emptyList()))

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            // §3.1.3 — templates are "excluded from Road Map's edges," and a deleted page has
            // no business appearing on a map of *existing* relationships either.
            val pages = pageDao.getAll().filter { it.deletedAt == null && !it.isTemplate }
            val pageIds = pages.map { it.id }.toSet()

            val mentionEdges = contentRepository.allMentionEdges()
                .filter { (from, to) -> from in pageIds && to in pageIds && from != to }
                .map { (from, to) -> RoadMapEdge(from, to, EdgeSource.MENTION) }
            val manualEdges = relationDao.getAll()
                .filter { it.fromPageId in pageIds && it.toPageId in pageIds }
                .map { RoadMapEdge(it.fromPageId, it.toPageId, EdgeSource.MANUAL) }

            // Undirected dedupe — a mention edge and a manual edge (or two mentions, one each
            // direction) between the same pair render as one line, not a doubled/overlapping
            // pair. A real link wins over a manual annotation when both exist between the same
            // pair, since it's the more informative (directed) of the two to show.
            val edges = (mentionEdges + manualEdges).distinctBy { edge -> setOf(edge.fromPageId, edge.toPageId) }

            _allPages.value = pages
            _graph.value = RoadMapGraph(pages, edges)
            // A page trashed/renamed since the focus was set could otherwise leave a stale
            // focus pointing at nothing.
            if (_focusedPageId.value != null && pages.none { it.id == _focusedPageId.value }) _focusedPageId.value = null
        }
    }

    /** Breadth-first walk out to [depth] hops from [rootId], undirected — a mention's arrow
     * direction matters for rendering, not for "is this page part of the neighborhood." */
    private fun localGraph(full: RoadMapGraph, rootId: Long, depth: Int): RoadMapGraph {
        var frontier = setOf(rootId)
        val visited = mutableSetOf(rootId)
        repeat(depth) {
            val next = frontier.flatMap { full.neighborsOf(it) }.filterNot { it in visited }.toSet()
            visited += next
            frontier = next
        }
        val nodes = full.nodes.filter { it.id in visited }
        val edges = full.edges.filter { it.fromPageId in visited && it.toPageId in visited }
        return RoadMapGraph(nodes, edges)
    }

    fun setFocus(pageId: Long?) { _focusedPageId.value = pageId }
    fun setFocusDepth(depth: Int) { _focusDepth.value = depth.coerceIn(1, 3) }

    /** Backs both steps of "Relate to…" (§3.4) — picking the *from* page and then the *to*
     * page are the same search-as-you-type list, just with a different exclusion each time. */
    fun searchPages(query: String, excludePageId: Long? = null) {
        viewModelScope.launch {
            _pageSearchResults.value = if (query.isBlank()) emptyList() else pageDao.getAll().filter {
                it.deletedAt == null && !it.isTemplate && it.id != excludePageId && it.title.contains(query, ignoreCase = true)
            }.take(20)
        }
    }

    fun clearPageSearch() { _pageSearchResults.value = emptyList() }

    /** §3.4 — the manual "Relate to…" action, for pages conceptually connected but that
     * "don't reference each other in text." */
    fun relate(fromPageId: Long, toPageId: Long) {
        viewModelScope.launch {
            relationDao.addRelation(fromPageId, toPageId)
            refresh()
        }
    }
}
