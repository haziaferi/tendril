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
import com.tendril.app.data.page.Tag
import com.tendril.app.data.page.TagDao
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
    private val tagDao: TagDao,
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

    fun createFromTemplate(template: Page, title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = templateManager.createFromTemplate(template, title)
            onCreated(id)
        }
    }

    val allTags: StateFlow<List<Tag>> =
        tagDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()
    fun toggleTagFilter(tagId: Long) {
        _selectedTagIds.value = _selectedTagIds.value.let { if (tagId in it) it - tagId else it + tagId }
    }

    /** §3.1.6 — filters the page list by the selected tags (OR semantics, see [TagDao.observePageIdsForTags]). */
    val filteredPages: StateFlow<List<Page>> = combine(
        rootPages,
        _selectedTagIds.flatMapLatest { ids -> if (ids.isEmpty()) flowOf(null) else tagDao.observePageIdsForTags(ids.toList()) },
    ) { pages, taggedIds ->
        if (taggedIds == null) pages else pages.filter { it.id in taggedIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** View-Only lock (§3.1.2) — one global toggle, not per-page. Delegates to the
     * [com.tendril.app.ui.WorkbenchCore]-held [ViewLockState] rather than keeping its own copy,
     * since [PageDetailViewModel]/[PageDatabaseViewModel] need to observe this exact same flag
     * to gate editing, and each gets a fresh ViewModel instance per page opened. */
    val viewOnly: StateFlow<Boolean> = viewLockState.viewOnly
    fun setViewOnly(value: Boolean) = viewLockState.setViewOnly(value)

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
        viewModelScope.launch {
            val now = Instant.now()
            pageIds.forEach { purgeRegistry.purgePage(it, now) }
        }
    }

    fun createBlankPage(title: String, onCreated: (Long) -> Unit) {
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
     * rather than at app-install time, same laziness as [PageDatabaseViewModel.ensureDefaultView]. */
    fun openJournal(date: java.time.LocalDate, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            val now = Instant.now()
            val root = pageDao.findRootByTitle("Journal")
                ?: pageDao.getById(pageDao.insert(Page(title = "Journal", kind = PageKind.PAGE, createdAt = now, updatedAt = now)))!!
            val title = "journal/$date"
            val pageId = pageDao.findChildByTitle(root.id, title)?.id
                ?: pageDao.insert(Page(title = title, parentId = root.id, createdAt = now, updatedAt = now))
            onOpen(pageId)
        }
    }
}
