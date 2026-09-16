package com.tendril.app.ui.roadmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.addRelation
import com.tendril.app.data.page.PageRelationDao
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.ViewLockState
import com.tendril.app.domain.roadmap.EdgeSource
import com.tendril.app.domain.roadmap.RoadMapEdge
import com.tendril.app.domain.roadmap.RoadMapFilter
import com.tendril.app.domain.roadmap.RoadMapGraph
import com.tendril.app.domain.roadmap.filtered
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * §3.4 — assembles the graph from the two decided edge sources (mentions, computed live off
 * Block data; manual relations, read from [PageRelationDao]) and exposes it as a plain
 * snapshot, not a continuously-observed Flow — matching [com.tendril.app.ui.pages.PageDetailViewModel]'s
 * own backlinks precedent ("loaded once per page-open... a backlink only changes when some
 * *other* page's content is edited, which this screen has no reason to be watching for").
 * [refresh] re-scans on demand (the screen's refresh action, and after "Relate to").
 *
 * The graph's own shape — the types, the BFS focus walk, the filters — is pure, in
 * `domain/roadmap/` (§0.8 step 8c), so this class is what it says: data + one-shot I/O + the
 * few pieces of filter *state*. Node positions/physics are deliberately NOT owned here —
 * they're ephemeral animation state scoped to the Composable's own lifecycle.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class RoadMapViewModel(
    private val pageDao: PageDao,
    private val relationDao: PageRelationDao,
    private val contentRepository: PageContentRepository,
    private val viewLockState: ViewLockState,
    private val labelDao: LabelDao,
    private val keyValueStore: KeyValueStore,
) : ViewModel() {
    /** §3.1.2 — the same single enforcement point as every other editing ViewModel (see
     * [com.tendril.app.ui.pages.PageDetailViewModel.viewOnlyLocked]). The Road Map is mostly a
     * *reader* — the graph scan, the focus filter and the page search all stay available, since
     * View-Only locks editing, not looking — so this gates exactly the one mutation the screen
     * has, [relate]. Read at call time rather than captured, so the eye toggle takes effect on a
     * map that is already open. */
    private fun locked() = viewLockState.viewOnly.value

    private val _graph = MutableStateFlow(RoadMapGraph.EMPTY)
    val graph: StateFlow<RoadMapGraph> = _graph.asStateFlow()

    /** §3.4 — "All Pages" bottom drawer content; every non-template, non-deleted page,
     * independent of whether it has any edges at all (an unconnected page still belongs on
     * the list — only the canvas itself needs an edge to be worth drawing). */
    val allPages: StateFlow<List<Page>> get() = _allPages
    private val _allPages = MutableStateFlow(emptyList<Page>())

    /** The Journal root and its day pages — what [RoadMapFilter.hideJournal] hides. */
    private val _journalPageIds = MutableStateFlow(emptySet<Long>())

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

    /** §3.4 (amended, step 8c) — the three filters, the Journal and kind halves persisted on the
     * device (`roadmap_filter`), the label ephemeral because its id is. */
    private val _filter = MutableStateFlow(RoadMapFilter.decode(keyValueStore.get(FILTER_KEY)))
    val filter: StateFlow<RoadMapFilter> = _filter.asStateFlow()

    val labels: StateFlow<List<Label>> =
        labelDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null while no label is chosen; else the pages carrying the chosen one, live. */
    private val labelledPageIds = _filter.flatMapLatest { f ->
        val id = f.labelId
        if (id == null) flowOf(null) else labelDao.observePageIdsForTags(listOf(id)).map { it.toSet() }
    }

    val displayedGraph: StateFlow<RoadMapGraph> =
        combine(_graph, _filter, _journalPageIds, labelledPageIds, _focusedPageId, _focusDepth) { values ->
            @Suppress("UNCHECKED_CAST")
            val full = values[0] as RoadMapGraph
            val filter = values[1] as RoadMapFilter
            val journal = values[2] as Set<Long>
            val labelled = values[3] as Set<Long>?
            val focus = values[4] as Long?
            val depth = values[5] as Int
            val shown = full.filtered(filter, journal, labelled)
            // A focus the filter just hid would show an empty map for no visible reason.
            if (focus == null || shown.nodes.none { it.id == focus }) shown else shown.around(focus, depth)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoadMapGraph.EMPTY)

    init {
        refresh()
        // 14h·2 (coloured-elements-function #2) — a new page, a rename, a trash or a relation
        // redraws the map without ↻; mention edges live in block text, which no page flow
        // announces, so the button stays for those.
        viewModelScope.launch {
            combine(pageDao.observeRootPages(), relationDao.observeAll()) { _, _ -> Unit }
                .drop(1).debounce(300).collect { refresh() }
        }
    }

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

            val journalRoot = pageDao.findRootByTitle("Journal")
            _journalPageIds.value = if (journalRoot == null) emptySet()
            else pages.filter { it.parentId == journalRoot.id }.mapTo(mutableSetOf(journalRoot.id)) { it.id }

            _allPages.value = pages
            _graph.value = RoadMapGraph(pages, edges)
            // A page trashed/renamed since the focus was set could otherwise leave a stale
            // focus pointing at nothing.
            if (_focusedPageId.value != null && pages.none { it.id == _focusedPageId.value }) _focusedPageId.value = null
        }
    }

    fun setFocus(pageId: Long?) { _focusedPageId.value = pageId }
    fun setFocusDepth(depth: Int) { _focusDepth.value = depth.coerceIn(1, 3) }

    fun setHideJournal(hide: Boolean) = updateFilter { it.copy(hideJournal = hide) }

    /** Refuses to switch the last kind off — an empty map with every chip dark explains nothing. */
    fun toggleKind(kind: PageKind) = updateFilter { f ->
        val kinds = if (kind in f.kinds) f.kinds - kind else f.kinds + kind
        if (kinds.isEmpty()) f else f.copy(kinds = kinds)
    }

    fun setLabel(labelId: Long?) = updateFilter { it.copy(labelId = labelId) }

    private fun updateFilter(change: (RoadMapFilter) -> RoadMapFilter) {
        val next = change(_filter.value)
        _filter.value = next
        keyValueStore.put(FILTER_KEY, next.encode())
    }

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

    /**
     * §3.4 — the manual "Relate to…" action, for pages conceptually connected but that
     * "don't reference each other in text."
     *
     * The only write this ViewModel makes, and so the only thing §3.1.2's lock has to stop here.
     * It is not a page edit that rides inside some page's snapshot: a `page_relations` row is its
     * own synced record, merged by `PagesSyncEngine.mergeRelations` off no page's timestamp at
     * all, so one made while the person believed the app was read-only reaches every other device
     * on the next pass with nothing left to mark it unintended (§9.4).
     */
    fun relate(fromPageId: Long, toPageId: Long) {
        if (locked()) return
        viewModelScope.launch {
            relationDao.addRelation(fromPageId, toPageId)
            refresh()
        }
    }

    companion object {
        const val FILTER_KEY = "roadmap_filter"
    }
}
