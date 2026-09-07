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
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.PageDatabaseViewDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
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
     * checking it on first composition would race and insert a duplicate. */
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
        val db = database.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        launchAndTouch(pageId) {
            val order = (properties.value.maxOfOrNull { it.order } ?: -1) + 1
            propertyDao.insert(Property(databaseId = db.id, name = trimmed, type = type, config = config, order = order))
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
