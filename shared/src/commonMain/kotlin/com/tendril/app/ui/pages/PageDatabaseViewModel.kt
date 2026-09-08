@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tendril.app.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.PageDatabaseViewDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.pagedatabase.encodeRelationConfig
import com.tendril.app.data.pagedatabase.encodeRelationValue
import com.tendril.app.data.pagedatabase.parseRelationConfig
import com.tendril.app.data.pagedatabase.parseRelationValue
import com.tendril.app.data.pagedatabase.SortDirection
import com.tendril.app.data.pagedatabase.formatPeriodAsInterval
import com.tendril.app.data.pagedatabase.ViewFilter
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.data.pagedatabase.setValue
import com.tendril.app.domain.BindingRole
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One Table-view row (§5.1/§5.6): the Row page itself, its stored cell values keyed by
 * property id, and its linked Task Entry if this database is Sync-to-Tasks-enabled. */
data class TableRow(val page: Page, val values: Map<Long, PropertyValue>, val linkedEntry: Entry?)

class PageDatabaseViewModel(
    private val pageId: Long,
    private val pageDao: PageDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val propertyDao: PropertyDao,
    private val propertyValueDao: PropertyValueDao,
    private val entryDao: EntryDao,
    private val pageDatabaseViewDao: PageDatabaseViewDao,
    private val blockDao: BlockDao,
    private val databaseSyncManager: DatabaseSyncManager,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
    private val templateManager: TemplateManager,
    private val purgeRegistry: PurgeRegistry,
    private val viewLockState: ViewLockState,
) : ViewModel() {
    /** §3.1.2 — see [PageDetailViewModel.viewOnlyLocked]'s note; the same single enforcement point,
     * duplicated per ViewModel rather than shared, since a Database Row's edits and a plain
     * Page's edits go through two entirely separate ViewModels. */
    private fun locked() = viewLockState.viewOnly.value

    /**
     * §9.4 — see [PageDao.touch]. Every mutation on this screen writes something that travels
     * *inside* a page's snapshot while leaving the page row alone, so every one of them runs
     * through here rather than `viewModelScope.launch` directly. The bump is a parameter of
     * launching, not a second call to remember after it — which is the whole shape of the bug
     * this fixes, and the shape it would come back in.
     *
     * [pageIdToBump] is required rather than defaulting to this screen's own page, because
     * which page it is is the part that is easy to get wrong: a cell hangs off its **row's**
     * page, while a property or a view hangs off the **database's**, and the merge gates those
     * as two separate records with two separate timestamps. Bumping the database for a cell
     * edit would propagate the schema and still lose the cell.
     *
     * Not used by [ensureDefaultView], which does write a synced row but does so on *open*
     * rather than on an edit. Under last-write-wins a bump is a claim of authorship, and a
     * device that merely opened a database would then outrank one that had genuinely edited its
     * schema a moment earlier and not yet synced. The lazily-created view costs nothing by
     * staying local — every device performs the same repair for itself.
     */
    private fun launchAndTouch(pageIdToBump: Long, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            pageDao.touch(pageIdToBump, Instant.now())
        }
    }

    val page: StateFlow<Page?> = pageDao.observeById(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val database: StateFlow<PageDatabase?> =
        pageDatabaseDao.observeByPageId(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val properties: StateFlow<List<Property>> = database.filterNotNull()
        .flatMapLatest { propertyDao.observeForDatabase(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rows: StateFlow<List<Page>> = database.filterNotNull()
        .flatMapLatest { pageDao.observeRowsOf(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allValues = database.filterNotNull().flatMapLatest { propertyValueDao.observeForDatabase(it.id) }

    private val linkedEntries = rows.flatMapLatest { rowList ->
        if (rowList.isEmpty()) flowOf(emptyList()) else entryDao.observeBySourceRowIds(rowList.map { it.id })
    }

    val tableRows: StateFlow<List<TableRow>> = combine(rows, allValues, linkedEntries) { rowList, values, entries ->
        val entriesByRow = entries.associateBy { it.sourceRowId }
        val valuesByRow = values.groupBy { it.rowPageId }
        rowList.map { row ->
            TableRow(row, valuesByRow[row.id].orEmpty().associateBy { it.propertyId }, entriesByRow[row.id])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §5.6 — saved views over this Database's rows; a Table view always exists (see
     * [ensureDefaultView]), Board/Gallery/Calendar are added on top, display-only. */
    val views: StateFlow<List<PageDatabaseView>> = database.filterNotNull()
        .flatMapLatest { pageDatabaseViewDao.observeForDatabase(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedViewId = MutableStateFlow<Long?>(null)
    val selectedView: StateFlow<PageDatabaseView?> = combine(views, _selectedViewId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectView(id: Long) { _selectedViewId.value = id }

    /** Created lazily the first time this database's view list is confirmed empty, rather
     * than at database-creation time, so a database that predates this feature (or one whose
     * only view got deleted) still always has one — §5.6: "a Database always has at least
     * one Table view." Checks ground truth via a one-shot query, not `views.value` — that
     * StateFlow's initial value is `emptyList()` until Room's own Flow has emitted once, so
     * checking it on first composition would race and insert a duplicate.
     *
     * §3.1.2 — the one write on this ViewModel deliberately *outside* [locked], and the omission
     * is the point rather than an oversight: this is idempotent repair-on-open, not an edit, so
     * gating it would leave a viewless database unopenable-as-a-database for exactly as long as
     * View-Only stayed on — a lock that hides data instead of protecting it. It is also outside
     * [launchAndTouch] on purpose, so it claims no authorship, moves no `pages.updatedAt`, and can
     * never outrank a real schema edit made on another device (§9.4) — every device simply
     * performs the same repair for itself. Same exemption, same two reasons, as
     * `CanvasViewModel`'s `init` block (its lazy `PageCanvas` shell), whose own note points back
     * here. Pinned by `ViewOnlySurfacesGuardTest`, so a later sweep that gates everything it can
     * find breaks a test rather than the app. */
    fun ensureDefaultView() {
        val db = database.value ?: return
        viewModelScope.launch {
            if (pageDatabaseViewDao.countForDatabase(db.id) == 0) {
                pageDatabaseViewDao.insert(PageDatabaseView(databaseId = db.id, name = "Table", viewType = ViewType.TABLE, order = 0))
            }
        }
    }

    fun addView(name: String, type: ViewType) {
        if (locked()) return
        val db = database.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        launchAndTouch(pageId) {
            val order = (views.value.maxOfOrNull { it.order } ?: -1) + 1
            val id = pageDatabaseViewDao.insert(PageDatabaseView(databaseId = db.id, name = trimmed, viewType = type, order = order))
            _selectedViewId.value = id
        }
    }

    /** Deleting a view only removes its display configuration — the underlying rows and
     * properties are untouched (§5.6). */
    fun deleteView(view: PageDatabaseView) {
        if (locked()) return
        launchAndTouch(pageId) {
            pageDatabaseViewDao.delete(view.id)
            if (_selectedViewId.value == view.id) _selectedViewId.value = null
        }
    }

    fun updateView(view: PageDatabaseView) {
        if (locked()) return
        launchAndTouch(pageId) { pageDatabaseViewDao.update(view) }
    }

    /** A bound property's (Done/Deadline/Recurrence) value is a live proxy read from its
     * linked Entry (§5.2.1), never `property_values` — the same rule [PropertyCell] applies
     * for editing, needed again here for filtering/sorting/grouping across every view type. */
    fun valueForCell(row: TableRow, propertyId: Long): String? {
        val db = database.value
        return when (propertyId) {
            db?.donePropertyId -> (row.linkedEntry?.status == EntryStatus.DONE).toString()
            db?.deadlinePropertyId -> row.linkedEntry?.startDate?.toString()
            // §5.2.2 — the same form DatabaseSyncManager.crystallize writes ("1:WEEK"), not
            // Period.toString()'s "P7D". Two forms for one column meant a view filter matched the
            // live proxy or the crystallised value but never both, and the displayed text changed
            // the moment a row was unbound.
            db?.recurrencePropertyId -> (row.linkedEntry?.recurrenceRule as? RecurrenceRule.Elastic)
                ?.period?.let(::formatPeriodAsInterval)
            else -> row.values[propertyId]?.value
        }
    }

    private fun matchesFilter(row: TableRow, filter: ViewFilter): Boolean {
        val value = valueForCell(row, filter.propertyId)
        return when (filter.comparator) {
            ViewFilter.Comparator.EQUALS -> value == filter.value
            ViewFilter.Comparator.NOT_EQUALS -> value != filter.value
            ViewFilter.Comparator.CONTAINS -> value?.contains(filter.value, ignoreCase = true) == true
            ViewFilter.Comparator.IS_EMPTY -> value.isNullOrBlank()
            ViewFilter.Comparator.IS_NOT_EMPTY -> !value.isNullOrBlank()
        }
    }

    /** Filter + sort applied (§5.6: per-view, never touching stored data) — the base list
     * every view type (Table directly, Board/Gallery/Calendar after their own
     * grouping/plotting step) renders from. */
    val displayedRows: StateFlow<List<TableRow>> = combine(tableRows, selectedView) { allRows, view ->
        if (view == null) return@combine allRows
        var result = view.filter?.let { filter -> allRows.filter { matchesFilter(it, filter) } } ?: allRows
        val sortPropertyId = view.sortPropertyId
        if (sortPropertyId != null) {
            result = result.sortedBy { valueForCell(it, sortPropertyId) ?: "" }
            if (view.sortDirection == SortDirection.DESC) result = result.reversed()
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Board view (§5.6) — one column per option of the grouping Select property; a row
     * whose value doesn't match any current option is dropped rather than shown in a
     * synthetic "other" column, matching the spec's "no silent fallback" instruction for a
     * database with no Select property yet (empty column list, handled by the UI). */
    val boardColumns: StateFlow<List<Pair<String, List<TableRow>>>> = combine(displayedRows, selectedView, properties) { rowsList, view, props ->
        if (view?.viewType != ViewType.BOARD) return@combine emptyList()
        val groupProperty = props.find { it.id == view.groupByPropertyId } ?: return@combine emptyList()
        val options = groupProperty.config?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        options.map { option -> option to rowsList.filter { valueForCell(it, groupProperty.id) == option } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveRowToColumn(row: TableRow, groupPropertyId: Long, option: String) {
        if (locked()) return
        // A board drag is a cell edit wearing a different gesture, and bumps the same page.
        launchAndTouch(row.page.id) { propertyValueDao.setValue(groupPropertyId, row.page.id, option) }
    }

    /** Gallery view cover (§5.6) — "the first Image block in the row's body." */
    val rowCovers: StateFlow<Map<Long, Block>> = rows.flatMapLatest { rowList ->
        if (rowList.isEmpty()) flowOf(emptyList()) else blockDao.observeImagesForPages(rowList.map { it.id })
    }.map { images -> images.groupBy { it.pageId }.mapValues { it.value.first() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _pendingSyncEnable = MutableStateFlow(false)
    val pendingSyncEnable: StateFlow<Boolean> = _pendingSyncEnable.asStateFlow()
    fun requestEnableSync() { _pendingSyncEnable.value = true }
    fun dismissEnableSync() { _pendingSyncEnable.value = false }

    private val _pendingSyncDisable = MutableStateFlow(false)
    val pendingSyncDisable: StateFlow<Boolean> = _pendingSyncDisable.asStateFlow()
    fun requestDisableSync() { _pendingSyncDisable.value = true }
    fun dismissDisableSync() { _pendingSyncDisable.value = false }

    fun confirmEnableSync(donePropertyId: Long, deadlinePropertyId: Long?, recurrencePropertyId: Long?, rowIds: List<Long>) {
        if (locked()) return
        val db = database.value ?: return
        viewModelScope.launch {
            databaseSyncManager.enableSync(db, donePropertyId, deadlinePropertyId, recurrencePropertyId, rowIds)
            _pendingSyncEnable.value = false
        }
    }

    fun confirmDisableSync() {
        if (locked()) return
        val db = database.value ?: return
        viewModelScope.launch {
            databaseSyncManager.disableSync(db)
            _pendingSyncDisable.value = false
        }
    }

    /** §5.2.1 rebind/unbind — `newPropertyId == null` means unbind (only ever offered for the
     * optional Deadline/Recurrence roles; Done is mandatory whenever sync is on, so its picker
     * never offers "None," matching [DatabaseSyncManager.unbindProperty]'s own precondition
     * that a role actually has something bound to unbind in the first place). */
    data class PendingRebind(val role: BindingRole, val currentPropertyId: Long, val newPropertyId: Long?)

    private val _pendingRebind = MutableStateFlow<PendingRebind?>(null)
    val pendingRebind: StateFlow<PendingRebind?> = _pendingRebind.asStateFlow()
    fun requestRebind(role: BindingRole, currentPropertyId: Long, newPropertyId: Long?) {
        _pendingRebind.value = PendingRebind(role, currentPropertyId, newPropertyId)
    }
    fun dismissRebind() { _pendingRebind.value = null }

    /** §5.2.1 — "Requires its own confirm dialog, stating plainly that the property freezes at
     * its current value and stops updating automatically." Same confirm-and-commit-immediately
     * pattern as [confirmDeleteProperty]/[confirmChangeType], not a new mechanism. */
    fun confirmRebind() {
        if (locked()) return
        val db = database.value ?: return
        val pending = _pendingRebind.value ?: return
        viewModelScope.launch {
            if (pending.newPropertyId != null) databaseSyncManager.rebindProperty(db, pending.role, pending.newPropertyId)
            else databaseSyncManager.unbindProperty(db, pending.role)
        }
        _pendingRebind.value = null
    }

    fun addProperty(name: String, type: PropertyType, config: String? = null) {
        if (locked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        // Not routed through `launchAndTouch`: its `block(); pageDao.touch(...)` shape runs the
        // touch unconditionally after the block returns, including when the block itself
        // early-returns via `return@launchAndTouch` — a bump with nothing behind it. Fetching
        // `db` needs to suspend (see the note below), which an early return inside that block
        // cannot prevent, so this inlines the same two steps in an order that can skip both.
        viewModelScope.launch {
            // `database.value ?: return` would silently drop the whole call whenever this runs
            // before anything has actually collected `database` — a freshly-constructed
            // ViewModel's `StateFlow.value` stays at its `null` initial value until a
            // `WhileSubscribed` upstream gets a subscriber, which a Compose `collectAsState()`
            // gives it in the app but nothing does in a direct call. `updateTitle` already falls
            // back to a direct fetch for the same reason; this does the same rather than trust
            // the cache is warm.
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return@launch
            val order = (propertyDao.getForDatabase(db.id).maxOfOrNull { it.order } ?: -1) + 1
            propertyDao.insert(Property(databaseId = db.id, name = trimmed, type = type, config = config, order = order))
            pageDao.touch(pageId, Instant.now())
        }
    }

    /** §5.4/DB1 — a `RELATION` property is created against a *target database* rather than
     * typed config text, and always creates its reverse alongside it in the same write: a
     * relation is never one-way by construction, so a device that only ever opens the target
     * database still sees the link looking back. Both inserts happen before either page is
     * touched, so a process death between them can never leave one side stitched to a uid the
     * other side does not have yet — the insert that didn't run simply didn't happen, same as
     * any other single insert failing.
     *
     * A self-relation (`targetDatabaseId == db.id`, e.g. "Blocked by" within one Tasks database)
     * needs no special case: both properties land in the same database as two ordinary columns,
     * and touching the same page twice with the same instant is harmless. */
    fun addRelationProperty(name: String, targetDatabaseId: Long) {
        if (locked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // See [addProperty]'s note on the same fallback — `database.value`/`page.value`
            // stay at their `null` initial value until something has actually collected them.
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return@launch
            val thisPage = page.value ?: pageDao.getById(pageId) ?: return@launch
            val targetDb = pageDatabaseDao.getById(targetDatabaseId) ?: return@launch
            val targetPage = pageDao.getById(targetDb.pageId) ?: return@launch

            val forwardUid = UUID.randomUUID().toString()
            val reverseUid = UUID.randomUUID().toString()
            val forwardOrder = (propertyDao.getForDatabase(db.id).maxOfOrNull { it.order } ?: -1) + 1
            val reverseOrder = (propertyDao.getForDatabase(targetDb.id).maxOfOrNull { it.order } ?: -1) + 1

            propertyDao.insert(
                Property(
                    uid = forwardUid, databaseId = db.id, name = trimmed, type = PropertyType.RELATION,
                    config = encodeRelationConfig(targetPage.uid, reverseUid), order = forwardOrder,
                )
            )
            // Named after the source database's own page title — the target database's schema
            // gets a reciprocal column it never asked for, so it should read as "this links back
            // to that thing", not an unlabelled duplicate of the forward property's name. Just a
            // starting value: renaming either side afterward is an ordinary property edit.
            propertyDao.insert(
                Property(
                    uid = reverseUid, databaseId = targetDb.id, name = thisPage.title, type = PropertyType.RELATION,
                    config = encodeRelationConfig(thisPage.uid, forwardUid), order = reverseOrder,
                )
            )
            val now = Instant.now()
            pageDao.touch(pageId, now)
            pageDao.touch(targetPage.id, now)
        }
    }

    private fun deleteProperty(property: Property) {
        val db = database.value
        launchAndTouch(pageId) {
            if (db != null) {
                when (property.id) {
                    db.donePropertyId -> databaseSyncManager.disableSync(db)
                    db.deadlinePropertyId -> databaseSyncManager.unbindProperty(db, BindingRole.DEADLINE)
                    db.recurrencePropertyId -> databaseSyncManager.unbindProperty(db, BindingRole.RECURRENCE)
                }
            }
            // Through the registry, not `propertyDao.delete`: §9.4's merge upserts every
            // property in a winning schema, so without a tombstone this column is reinstated by
            // the first peer that has not seen the deletion — and its cells with it, since the
            // schema record carries the column but no device's row values.
            purgeRegistry.purgeProperty(property.id)
        }
    }

    /** §5.5 — "Property deletion requires an explicit confirm dialog." [deleteProperty] now
     * only ever runs from [confirmDeleteProperty], after the dialog states the consequence
     * (plain deletion, or the bound-property/sync-disable case) in plain language. */
    private val _pendingDeleteProperty = MutableStateFlow<Property?>(null)
    val pendingDeleteProperty: StateFlow<Property?> = _pendingDeleteProperty.asStateFlow()
    fun requestDeleteProperty(property: Property) { _pendingDeleteProperty.value = property }
    fun dismissDeleteProperty() { _pendingDeleteProperty.value = null }
    fun confirmDeleteProperty() {
        if (locked()) return
        _pendingDeleteProperty.value?.let { deleteProperty(it) }
        _pendingDeleteProperty.value = null
    }

    /** §5.5 — "Property type conversion... opening 'Edit property type' shows a preview of
     * what would happen to existing values... the change commits immediately on confirm." A
     * bound property (Done/Deadline/Recurrence) never reaches this — [PropertyHeaderCell]
     * hides the menu item for those, since there's no real schema-editable field to convert. */
    private val _pendingTypeChange = MutableStateFlow<Property?>(null)
    val pendingTypeChange: StateFlow<Property?> = _pendingTypeChange.asStateFlow()
    fun requestChangeType(property: Property) { _pendingTypeChange.value = property }
    fun dismissChangeType() { _pendingTypeChange.value = null }

    /** The preview count driving the dialog's "N rows will lose..." copy — every row with a
     * genuinely non-blank stored value for this property, recomputed each time a new property
     * is targeted. */
    val typeChangeAffectedCount: StateFlow<Int> = _pendingTypeChange.filterNotNull()
        .flatMapLatest { property -> flow { emit(propertyValueDao.getAllForProperty(property.id).count { !it.value.isNullOrBlank() }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun confirmChangeType(property: Property, newType: PropertyType) {
        if (locked()) return
        launchAndTouch(pageId) {
            // Options only mean anything for Select/Multi-select — carrying them into an
            // unrelated type would just be a stale, invisible leftover.
            val config = if (newType == PropertyType.SELECT || newType == PropertyType.MULTI_SELECT) property.config else null
            propertyDao.update(property.copy(type = newType, config = config))
            _pendingTypeChange.value = null
        }
    }

    /** §5.5 — "Row deletion... requires an explicit confirm dialog." A deleted row is a
     * deleted Page (§5.1), same [pageDao.softDelete] Trash mechanism as everywhere else; its
     * linked Task (if this database is Sync-to-Tasks-enabled) is trashed alongside it, the
     * same pairing [DatabaseSyncManager.disableSync] already applies at the whole-database
     * level — an orphaned Task pointing at a trashed row would otherwise sit in Tasks/Calendar
     * with nothing behind it. */
    private val _pendingDeleteRow = MutableStateFlow<Page?>(null)
    val pendingDeleteRow: StateFlow<Page?> = _pendingDeleteRow.asStateFlow()
    fun requestDeleteRow(row: Page) { _pendingDeleteRow.value = row }
    fun dismissDeleteRow() { _pendingDeleteRow.value = null }
    fun confirmDeleteRow() {
        if (locked()) return
        val row = _pendingDeleteRow.value ?: return
        viewModelScope.launch {
            val now = Instant.now()
            entryDao.getBySourceRowId(row.id)?.let { resolveEntryUseCase.trash(it.id, now) }
            pageDao.softDelete(row.id, now)
            _pendingDeleteRow.value = null
        }
    }

    /** §3.1.3 — "Save as template," same operation as [PageDetailViewModel.saveAsTemplate]
     * but for a Database: clones its schema (properties), never its rows. */
    fun saveAsTemplate() {
        if (locked()) return
        val current = page.value ?: return
        viewModelScope.launch { templateManager.saveAsTemplate(current) }
    }

    fun updateTitle(title: String) {
        if (locked()) return
        viewModelScope.launch {
            val current = page.value ?: pageDao.getById(pageId) ?: return@launch
            pageDao.update(current.copy(title = title, updatedAt = Instant.now()))
        }
    }

    /** §5.2 — "every row in a sync-enabled database becomes its own linked Task", not just
     * the rows present at the moment sync was turned on: a row created afterward gets seeded
     * into the sync relationship immediately, via the same (idempotent) per-row step
     * `enableSync` itself uses. */
    fun addRow(title: String, onCreated: (Long) -> Unit) {
        if (locked()) return
        val db = database.value ?: return
        viewModelScope.launch {
            val now = Instant.now()
            val id = pageDao.insert(Page(title = title.ifBlank { "Untitled" }, databaseId = db.id, createdAt = now, updatedAt = now))
            // Local val, not `db.donePropertyId` directly: a nullable property declared in a
            // different module (`:shared`, §12.5) can't be smart-cast across the module boundary.
            val donePropertyId = db.donePropertyId
            if (db.syncToTasks && donePropertyId != null) {
                databaseSyncManager.enableSync(db, donePropertyId, db.deadlinePropertyId, db.recurrencePropertyId, listOf(id))
            }
            onCreated(id)
        }
    }

    /** Unbound cell edit — bound properties (Done/Deadline/Recurrence) never reach this path;
     * their cells edit through [toggleDone]/[setDeadline]/[setRecurrence] instead. */
    fun setCellValue(property: Property, row: Page, value: String?) {
        if (locked()) return
        launchAndTouch(row.id) { propertyValueDao.setValue(property.id, row.id, value) }
    }

    /** §5.4/DB1 — a relation cell edit writes both sides. This row's own value always updates;
     * the related row's *reverse* cell updates too, provided the reverse property still exists
     * — [addRelationProperty] always creates one, but nothing stops a person deleting one side
     * afterward (§5.5), and a relation orphaned that way degrades to one-way rather than being
     * blocked or auto-repaired, the same tolerant-reference posture the rest of this cell type
     * already takes on a missing row uid.
     *
     * Every affected row's page gets touched, not just this one — the reverse cell lives inside
     * the *other* row's own snapshot payload (§9.4), so a change there needs its own bump or it
     * never leaves this device. */
    fun setRelationValue(property: Property, row: Page, newRelatedUids: Set<String>) {
        if (locked() || property.type != PropertyType.RELATION) return
        viewModelScope.launch {
            val reverseProperty = parseRelationConfig(property.config)?.reversePropertyUid?.let { propertyDao.getByUid(it) }
            val oldUids = parseRelationValue(propertyValueDao.getForPropertyAndRow(property.id, row.id)?.value)
            val added = newRelatedUids - oldUids
            val removed = oldUids - newRelatedUids

            val touched = mutableSetOf(row.id)
            if (reverseProperty != null) {
                for (uid in added + removed) {
                    val otherRow = pageDao.getByUid(uid) ?: continue
                    val current = parseRelationValue(propertyValueDao.getForPropertyAndRow(reverseProperty.id, otherRow.id)?.value)
                    val next = if (uid in added) current + row.uid else current - row.uid
                    propertyValueDao.setValue(reverseProperty.id, otherRow.id, encodeRelationValue(next))
                    touched += otherRow.id
                }
            }

            propertyValueDao.setValue(property.id, row.id, encodeRelationValue(newRelatedUids))
            val now = Instant.now()
            touched.forEach { pageDao.touch(it, now) }
        }
    }

    /** Rows to offer in the relation picker (§5.4/DB1) — every row of the property's target
     * database. One-shot rather than a live Flow: the picker sheet reads this once on open, the
     * same shape [EnableSyncSheet] already uses for its own row list. */
    suspend fun relationCandidateRows(property: Property): List<Page> {
        val target = parseRelationConfig(property.config) ?: return emptyList()
        val targetPage = pageDao.getByUid(target.targetDatabasePageUid) ?: return emptyList()
        val targetDb = pageDatabaseDao.getByPageId(targetPage.id) ?: return emptyList()
        return pageDao.getRowsOf(targetDb.id)
    }

    /** Resolves a relation cell's stored uids to display titles for [RelationCell], dropping
     * any this device cannot currently find rather than showing a bare uid — deleted, or still
     * quarantined (§9.4). */
    suspend fun resolveRelatedTitles(uids: Set<String>): List<Pair<String, String>> =
        uids.mapNotNull { uid -> pageDao.getByUid(uid)?.let { uid to it.title } }

    /** Every database in the app, as candidates for a new relation property's target
     * (§5.4/DB1) — including this database's own page, since a self-relation ("Blocked by"
     * within one Tasks database) is a real, ordinary case rather than one needing separate UI. */
    suspend fun relationTargetOptions(): List<Pair<Page, Long>> =
        pageDao.getByKind(PageKind.DATABASE).mapNotNull { p -> pageDatabaseDao.getByPageId(p.id)?.let { p to it.id } }

    fun toggleDone(entry: Entry, checked: Boolean) {
        if (locked()) return
        viewModelScope.launch { resolveEntryUseCase.setDone(entry.id, checked) }
    }

    fun setDeadline(entry: Entry, date: LocalDate?) {
        if (locked()) return
        viewModelScope.launch {
            val updated = entry.copy(startDate = date, updatedAt = Instant.now())
            entryDao.update(updated)
            entryScheduleCoordinator.onEntryChanged(updated)
        }
    }

    fun setRecurrence(entry: Entry, count: Int, unit: com.tendril.app.data.entry.IntervalUnit) {
        if (locked()) return
        viewModelScope.launch {
            val updated = entry.copy(recurrenceRule = RecurrenceRule.Elastic(intervalToPeriod(count, unit)), updatedAt = Instant.now())
            entryDao.update(updated)
            entryScheduleCoordinator.onEntryChanged(updated)
        }
    }
}
