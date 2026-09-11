@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tendril.app.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageFtsDao
import com.tendril.app.data.page.searchPrefix
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class PagesViewModel(
    private val pageDao: PageDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val propertyDao: PropertyDao,
    private val pageFtsDao: PageFtsDao,
    private val labelDao: LabelDao,
    private val purgeRegistry: PurgeRegistry,
    private val databaseSyncManager: DatabaseSyncManager,
    private val templateManager: TemplateManager,
    private val viewLockState: ViewLockState,
) : ViewModel() {
    val rootPages: StateFlow<List<Page>> =
        pageDao.observeRootPages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §3.1.3 — "the person's own saved templates," listed in "New from template." */
    val templates: StateFlow<List<Page>> =
        pageDao.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §3.1.3 — "New from template" deep-clones through [TemplateManager]: a Page plus every
     * block of the template's structure, all of them records the next export carries to every
     * peer. Gated like every other create here; the template itself is only read. */
    fun createFromTemplate(template: Page, title: String, onCreated: (Long) -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            val id = templateManager.createFromTemplate(template, title)
            onCreated(id)
        }
    }

    val allLabels: StateFlow<List<Label>> =
        labelDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedLabelIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()
    fun toggleLabelFilter(tagId: Long) {
        _selectedTagIds.value = _selectedTagIds.value.let { if (tagId in it) it - tagId else it + tagId }
    }

    /** §3.1.6 — filters the page list by the selected labels (OR semantics, see [LabelDao.observePageIdsForTags]). */
    val filteredPages: StateFlow<List<Page>> = combine(
        rootPages,
        _selectedTagIds.flatMapLatest { ids -> if (ids.isEmpty()) flowOf(null) else labelDao.observePageIdsForTags(ids.toList()) },
    ) { pages, taggedIds ->
        if (taggedIds == null) pages else pages.filter { it.id in taggedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** View-Only lock (§3.1.2) — one global toggle, not per-page. Delegates to the
     * [com.tendril.app.ui.WorkbenchCore]-held [ViewLockState] rather than keeping its own copy,
     * since [PageDetailViewModel]/[PageDatabaseViewModel] need to observe this exact same flag
     * to gate editing, and each gets a fresh ViewModel instance per page opened. */
    val viewOnly: StateFlow<Boolean> = viewLockState.viewOnly
    fun setViewOnly(value: Boolean) = viewLockState.setViewOnly(value)

    /** §3.1.2 — see [PageDetailViewModel.viewOnlyLocked]'s note; the same single enforcement
     * point, duplicated per ViewModel rather than shared, because this hub is unscoped while
     * those two are per-page. Every mutating function below early-returns through here rather
     * than relying on the composable conditionals in [PagesScreen] alone: withholding an
     * affordance is not the same as refusing a write, and the writes on this screen are the
     * costliest in the app. A create inserts a record the next export carries to every peer
     * ([com.tendril.app.sync.SnapshotSyncOrchestrator]); "Delete forever" records a
     * `PurgedKind.PAGE` tombstone that *deletes the row on every peer that adopts it* (§5.5.1.1,
     * [PurgeRegistry.purgePage]) and is the one write in Tendril no `.tendril-lost-` copy can
     * undo; and Restore rewrites `pages.updatedAt`, so it wins the next last-write-wins merge
     * everywhere too. None of them is a local-only mistake. */
    private fun locked() = viewLockState.viewOnly.value

    /** §3.1.7 — the Pages-scoped search overlay, live at ~300ms debounce (debounced by the
     * caller via a `LaunchedEffect`/`snapshotFlow`, not here). */
    private val _searchResults = MutableStateFlow<List<PageSearchHit>>(emptyList())
    val searchResults: StateFlow<List<PageSearchHit>> = _searchResults.asStateFlow()

    fun onSearchQueryChange(query: String) {
        viewModelScope.launch { _searchResults.value = pageFtsDao.searchPrefix(query) }
    }

    /** §5.5.1.1 "Delete forever", from the Trash — through [PurgeRegistry], which records the
     * tombstone and drops the row as one operation so a purge both sticks here and propagates. */
    fun deleteForever(pageIds: List<Long>) {
        if (locked()) return
        viewModelScope.launch {
            val now = Instant.now()
            pageIds.forEach { purgeRegistry.purgePage(it, now) }
        }
    }

    /** §5.5.1 "Restore", from the Trash — a ViewModel method rather than a `pageDao.restore(…)`
     * call straight out of [PagesScreen]'s `onClick`, because a call site inside a composable is
     * somewhere no enforcement idiom can reach. [PageDao.restore] clears `deletedAt` *and*
     * rewrites `updatedAt` (it has to: §9.4, the merge gate reads that column and nothing else),
     * so an ungated Restore is an outbound sync write, not a local undo — it beats whatever any
     * other device did to that page since, on every device. */
    fun restore(pageIds: List<Long>) {
        if (locked()) return
        viewModelScope.launch {
            val now = Instant.now()
            pageIds.forEach { pageDao.restore(it, now) }
        }
    }

    fun createBlankPage(title: String, onCreated: (Long) -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            val now = Instant.now()
            val id = pageDao.insert(Page(title = title.ifBlank { "Untitled" }, kind = PageKind.PAGE, createdAt = now, updatedAt = now))
            onCreated(id)
        }
    }

    /** The in-page Canvas feature — a Canvas is its own [PageKind.CANVAS] page, sibling to
     * Database, not a block type nested inside a page (§10's deferred in-page mind-map,
     * brought into scope). [com.tendril.app.ui.canvas.CanvasViewModel] creates the actual
     * [com.tendril.app.data.canvas.PageCanvas] companion row lazily on first open (the same
     * lazy-creation pattern [com.tendril.app.ui.pages.PageDatabaseViewModel.ensureDefaultView]
     * uses for a database's default view) rather than eagerly here. */
    fun createCanvas(title: String, onCreated: (Long) -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            val now = Instant.now()
            val id = pageDao.insert(Page(title = title.ifBlank { "Untitled" }, kind = PageKind.CANVAS, createdAt = now, updatedAt = now))
            onCreated(id)
        }
    }

    /** §3.1.3 — "New → To-do database" pre-fills a Done checkbox and flips Sync-to-Tasks on
     * through the same `enableSync` path any other database's toggle uses (§5.2) — no rows
     * exist yet on a brand-new database, so there's nothing to seed. */
    fun createDatabase(title: String, asToDoDatabase: Boolean, onCreated: (Long) -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            val now = Instant.now()
            val pageId = pageDao.insert(Page(title = title.ifBlank { "Untitled" }, kind = PageKind.DATABASE, createdAt = now, updatedAt = now))
            val databaseId = pageDatabaseDao.insert(PageDatabase(pageId = pageId, createdAt = now, updatedAt = now))
            if (asToDoDatabase) {
                val donePropertyId = propertyDao.insert(Property(databaseId = databaseId, name = "Done", type = PropertyType.CHECKBOX, order = 0))
                val database = pageDatabaseDao.getById(databaseId)
                if (database != null) {
                    databaseSyncManager.enableSync(database, donePropertyId, deadlinePropertyId = null, recurrencePropertyId = null, rowIds = emptyList())
                }
            }
            onCreated(pageId)
        }
    }

    /** §3.1.4 — "created on first open of a given day and reused after that." Structurally an
     * ordinary Page parented under a permanent "Journal" root, created lazily on first use
     * rather than at app-install time, same laziness as [PageDatabaseViewModel.ensureDefaultView].
     *
     * §3.1.2 — the one mutation on this screen that is *not* gated at the top, because it is
     * read-and-write in the same call: the lookup half is navigation to a page that already
     * exists, and View-Only "makes pages read-only", it does not hide them. So the guard sits on
     * the lazy-creation branches only. Ordered lookup-first for that reason: both inserts (the
     * "Journal" root and the day's child) are new Page records the next export carries to every
     * peer, so under the lock an unwritten day resolves to nothing and [onOpen] is never called —
     * navigating to a page id that was refused would land on an empty screen.
     *
     * [onRefused] is the other half of that, and the reason this function reports rather than just
     * returns. Because the read half must stay reachable, [PagesScreen]'s Journal button is not
     * disabled under the lock the way the FAB is — so the person can and will pick a day nothing
     * was ever written for, and a refusal with no callback is a tap that opens nothing, explains
     * nothing, and is indistinguishable from a crash they cannot see. The write is refused either
     * way; this is what keeps the refusal honest. It fires only on the locked-and-unwritten path:
     * an existing day opens, and an unlocked one is created, exactly as before. Declared before
     * [onOpen] so the common `openJournal(date) { … }` trailing-lambda call still binds to
     * [onOpen] and callers that have nowhere to show a message need say nothing. */
    fun openJournal(date: java.time.LocalDate, onRefused: () -> Unit = {}, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            val title = "journal/$date"
            val root = pageDao.findRootByTitle("Journal")
            val existingId = root?.let { pageDao.findChildByTitle(it.id, title)?.id }
            if (existingId != null) {
                onOpen(existingId)
                return@launch
            }
            if (locked()) {
                onRefused()
                return@launch
            }
            val now = Instant.now()
            val journalRoot = root
                ?: pageDao.getById(pageDao.insert(Page(title = "Journal", kind = PageKind.PAGE, createdAt = now, updatedAt = now)))!!
            onOpen(pageDao.insert(Page(title = title, parentId = journalRoot.id, createdAt = now, updatedAt = now)))
        }
    }
}
