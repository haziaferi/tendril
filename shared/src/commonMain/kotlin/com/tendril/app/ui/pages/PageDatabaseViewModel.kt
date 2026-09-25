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
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
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
import com.tendril.app.data.pagedatabase.RollupAggregation
import com.tendril.app.data.pagedatabase.encodeFormulaConfig
import com.tendril.app.data.pagedatabase.encodeRollupConfig
import com.tendril.app.data.pagedatabase.formatRollupNumber
import com.tendril.app.data.pagedatabase.parseFormulaConfig
import com.tendril.app.data.pagedatabase.parseRelationConfig
import com.tendril.app.data.pagedatabase.parseRelationValue
import com.tendril.app.data.pagedatabase.parseRollupConfig
import com.tendril.app.data.pagedatabase.SortDirection
import com.tendril.app.data.pagedatabase.formatPeriodAsInterval
import com.tendril.app.data.pagedatabase.ViewFilter
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.data.pagedatabase.setValue
import com.tendril.app.domain.BindingRole
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.LabelMembership
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.domain.formula.FormulaCheckResult
import com.tendril.app.domain.formula.FormulaError
import com.tendril.app.domain.formula.FormulaNode
import com.tendril.app.domain.formula.FormulaPropertyKind
import com.tendril.app.domain.formula.FormulaPropertyResolver
import com.tendril.app.domain.formula.FormulaPropertyTypeLookup
import com.tendril.app.domain.timeline.BlockedState
import com.tendril.app.domain.timeline.TimelineBar
import com.tendril.app.domain.timeline.blockedBy
import com.tendril.app.domain.timeline.shifted
import com.tendril.app.domain.formula.FormulaSyntaxError
import com.tendril.app.domain.formula.FormulaType
import com.tendril.app.domain.formula.FormulaValue
import com.tendril.app.domain.formula.checkAllFormulas
import com.tendril.app.domain.formula.evaluateFormula
import com.tendril.app.domain.formula.parseFormula
import com.tendril.app.domain.formula.propertyReferences
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
 * property id, and its linked Task Entry if this database is Sync-to-Tasks-enabled.
 * [viaLabel] (§0.6.8) marks a member that lives elsewhere and is here through the bound label:
 * the same row in every respect, except that removing it from the database is removing the
 * label, not trashing the page. */
data class TableRow(val page: Page, val values: Map<Long, PropertyValue>, val linkedEntry: Entry?, val viaLabel: Boolean = false)

/** A Board column: [key] is what a card moved here is set to ("" clears the cell), [label] what the header says. */
data class BoardColumn(val key: String, val label: String, val rows: List<TableRow>)

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
    private val labelDao: LabelDao,
    private val labelMembership: LabelMembership,
    private val pageContentRepository: PageContentRepository,
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

    // §0.6.8 — members, not native rows: the union with the pages carrying the bound label.
    private val rows: StateFlow<List<Page>> = database.filterNotNull()
        .flatMapLatest { pageDao.observeMembersOf(it.id, it.labelId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §0.6.8 — the bound label, live, so the menu and the bind sheet read the current one. */
    val boundLabel: StateFlow<Label?> = combine(database, labelDao.observeAll()) { db, all ->
        db?.labelId?.let { id -> all.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val allValues = database.filterNotNull().flatMapLatest { propertyValueDao.observeForDatabase(it.id) }

    private val linkedEntries = rows.flatMapLatest { rowList ->
        if (rowList.isEmpty()) flowOf(emptyList()) else entryDao.observeBySourceRowIds(rowList.map { it.id })
    }

    val tableRows: StateFlow<List<TableRow>> = combine(rows, allValues, linkedEntries, database) { rowList, values, entries, db ->
        val entriesByRow = entries.associateBy { it.sourceRowId }
        val valuesByRow = values.groupBy { it.rowPageId }
        rowList.map { row ->
            TableRow(row, valuesByRow[row.id].orEmpty().associateBy { it.propertyId }, entriesByRow[row.id], viaLabel = db != null && row.databaseId != db.id)
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

    /**
     * F6 (PR C, 2026-09-18 — Notion's, measured): a new view never opens empty. A Board with no
     * Select property to group by gets a *Status* Select (Not started · In progress · Done) made
     * for it and grouped by at once; a Calendar or Timeline with no Date property gets a *Date*.
     * Where the database already has one, the first is bound. The two-button empty state stays
     * for the state a later deletion leaves.
     */
    fun addView(name: String, type: ViewType) {
        if (locked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        launchAndTouch(pageId) {
            // Fetched, not read from the cold StateFlows (`addProperty`'s note): a direct call has no collector.
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return@launchAndTouch
            val order = (pageDatabaseViewDao.getForDatabase(db.id).maxOfOrNull { it.order } ?: -1) + 1
            val props = propertyDao.getForDatabase(db.id)
            var view = PageDatabaseView(databaseId = db.id, name = trimmed, viewType = type, order = order)
            when (type) {
                ViewType.BOARD -> view = view.copy(groupByPropertyId = props.firstOrNull { it.type == PropertyType.SELECT }?.id
                    ?: propertyDao.insert(Property(databaseId = db.id, name = "Status", type = PropertyType.SELECT, config = "Not started,In progress,Done", order = (props.maxOfOrNull { it.order } ?: -1) + 1)))
                ViewType.CALENDAR, ViewType.TIMELINE -> view = view.copy(datePropertyId = props.firstOrNull { it.type == PropertyType.DATE }?.id
                    ?: propertyDao.insert(Property(databaseId = db.id, name = "Date", type = PropertyType.DATE, order = (props.maxOfOrNull { it.order } ?: -1) + 1)))
                else -> {}
            }
            val id = pageDatabaseViewDao.insert(view)
            _selectedViewId.value = id
        }
    }

    /** F8 (PR C) — *Move to Trash* from the database's own `···`: the same write the tree row makes (`PagesViewModel.moveToTrash`). */
    fun trashDatabase(onDone: () -> Unit) {
        if (locked()) return
        viewModelScope.launch {
            val now = Instant.now()
            entryDao.getBySourceRowId(pageId)?.let { resolveEntryUseCase.trash(it.id, now) }
            pageDao.softDelete(pageId, now)
            onDone()
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
            db?.dueDatePropertyId -> row.linkedEntry?.dueDate?.toString()
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

    /** §DB8 — `PageDatabaseView.visiblePropertyIds`'s own doc comment: "Empty means all
     * properties," so every view created before this shipped (and every view whose chooser
     * nobody has touched since) keeps showing every column unchanged. Consumed by the Table and
     * Gallery bodies; schema-management surfaces (rebind candidates, the chooser itself,
     * `AddPropertySheet`, `EnableSyncSheet`) read [properties] directly instead — hiding a
     * column changes what's *displayed*, not what a property picker can still reach. */
    val visibleProperties: StateFlow<List<Property>> = combine(properties, selectedView) { props, view ->
        val ids = view?.visiblePropertyIds.orEmpty()
        if (ids.isEmpty()) props else props.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Board view (§5.6/DB5) — one column per group. A `SELECT` group property's columns are
     * its fixed `config` option list, same as always; a row whose value doesn't match any
     * current option is dropped rather than shown in a synthetic "other" column, matching the
     * spec's "no silent fallback" instruction. A formula-authored `COMPUTED` group property (see
     * [PropertyType.COMPUTED]'s own note — a rollup is not offered here, see [groupPropertyValue]'s
     * note on why) has no such fixed list — there is nothing in its `config` to read one from —
     * so its columns are instead every distinct non-empty value the formula actually produced
     * across the currently displayed rows, sorted for a stable column order across
     * recompositions. Either way a row
     * with a value matching no option has no column of its own — the same "no silent fallback"
     * rule, not a bug specific to one group-property kind. **A blank `SELECT` cell is not a
     * mismatch, it is unset** (PR C, 2026-09-18): those rows sit in a named first column, *No
     * Status*, so a Board made on an existing database (F6 makes the property with the view)
     * shows every row at once and a card dragged out of that column takes its option; dragged
     * back in, the cell is cleared. A formula's `Empty` stays dropped — nothing could be set. */
    val boardColumns: StateFlow<List<BoardColumn>> = combine(displayedRows, selectedView, properties) { rowsList, view, props ->
        if (view?.viewType != ViewType.BOARD) return@combine emptyList()
        val groupProperty = props.find { it.id == view.groupByPropertyId } ?: return@combine emptyList()
        val valueOf: (TableRow) -> String? = { row -> groupPropertyValue(groupProperty, row, props) }
        if (groupProperty.type == PropertyType.COMPUTED) {
            rowsList.mapNotNull(valueOf).distinct().sorted().map { option -> BoardColumn(option, option, rowsList.filter { valueOf(it) == option }) }
        } else {
            val options = groupProperty.config?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val unset = rowsList.filter { valueOf(it).isNullOrBlank() }
            val named = options.map { option -> BoardColumn(option, option, rowsList.filter { valueOf(it) == option }) }
            if (unset.isEmpty()) named else listOf(BoardColumn("", "No ${groupProperty.name}", unset)) + named
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A Board group property's value for one row — [valueForCell] for everything already
     * synchronous, or [resolvePropertyFormulaValue] for a formula-authored `COMPUTED` property
     * (row-local, so still synchronous — no suspend inside a `combine` block). A rollup-authored
     * `COMPUTED` property ([parseFormulaConfig] `null` for it) is deliberately not offered by the
     * group-by picker in the first place ([PageDatabaseScreen]'s `ViewConfigSheet`) — aggregating
     * across a relation needs suspend DAO calls, which grouping every displayed row on every
     * recomposition cannot afford to make one-by-one, the same "not yet, needs its own PR" scope
     * cut [addFormulaProperty] already made for referencing a relation from a formula. */
    private fun groupPropertyValue(groupProperty: Property, row: TableRow, allProperties: List<Property>): String? =
        if (groupProperty.type == PropertyType.COMPUTED) {
            parseFormulaConfig(groupProperty.config)?.let { resolvePropertyFormulaValue(groupProperty, row, allProperties, mutableSetOf()).toCellText() }
        } else {
            valueForCell(row, groupProperty.id)
        }

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

    fun confirmEnableSync(donePropertyId: Long, deadlinePropertyId: Long?, recurrencePropertyId: Long?, rowIds: List<Long>, dueDatePropertyId: Long? = null) {
        if (locked()) return
        val db = database.value ?: return
        viewModelScope.launch {
            databaseSyncManager.enableSync(db, donePropertyId, deadlinePropertyId, recurrencePropertyId, rowIds, dueDatePropertyId = dueDatePropertyId)
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
    /** §5.2.1 — bind an unbound property to an unfilled optional role on a database whose sync
     * is already on. No confirm dialog, unlike a rebind: nothing currently bound is frozen or
     * replaced, the property's stored values simply become the seed and then the live proxy. */
    fun bindProperty(role: BindingRole, propertyId: Long) {
        if (locked()) return
        val db = database.value ?: return
        launchAndTouch(pageId) { databaseSyncManager.bindProperty(db, role, propertyId) }
    }

    /** §0.6.8 — bind a label by name, creating it if it is new, exactly as a page's label
     * picker does. No confirm dialog: the sheet's own copy says what binding means, and the
     * once-only question about tasks is asked where the label is *applied*. */
    fun bindLabel(name: String) {
        if (locked()) return
        val db = database.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val label = labelDao.findByName(trimmed) ?: run {
                labelDao.insert(Label(name = trimmed))
                labelDao.findByName(trimmed)!!
            }
            labelMembership.bindLabel(db, label.id)
        }
    }

    fun unbindLabel() {
        if (locked()) return
        val db = database.value ?: return
        viewModelScope.launch { labelMembership.unbindLabel(db) }
    }

    /** §0.6.8 — a labelled member leaves the database by losing the label; the page stays where
     * it lives. Native rows go through [requestDeleteRow] instead. */
    fun removeMember(row: Page) {
        if (locked()) return
        val label = boundLabel.value ?: return
        viewModelScope.launch {
            labelMembership.removeLabel(row.id, label)
            pageDao.touch(row.id, Instant.now())
        }
    }

    suspend fun searchLabels(query: String): List<Label> = labelDao.search(query)

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

    /** §5.4/DB2 — a rollup, authored from pickers rather than typed text: which `RELATION`
     * property on *this* database to aggregate across, which property on the relation's target
     * database to read from each related row, and how. [relationPropertyId] is validated to
     * actually be a `RELATION` property on this database — the picker that calls this only ever
     * offers those, but the check makes an invalid config unrepresentable rather than merely
     * unintended. Stores no value anywhere: a `COMPUTED` property never gets a `PropertyValue`
     * row at all, since [computeRollupValue] derives it fresh every time it is read. */
    fun addRollupProperty(name: String, relationPropertyId: Long, targetPropertyId: Long?, aggregation: RollupAggregation) {
        if (locked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return@launch
            val relationProperty = propertyDao.getById(relationPropertyId)?.takeIf { it.databaseId == db.id && it.type == PropertyType.RELATION } ?: return@launch
            val targetProperty = targetPropertyId?.let { propertyDao.getById(it) }

            val order = (propertyDao.getForDatabase(db.id).maxOfOrNull { it.order } ?: -1) + 1
            propertyDao.insert(
                Property(
                    databaseId = db.id, name = trimmed, type = PropertyType.COMPUTED,
                    config = encodeRollupConfig(relationProperty.uid, targetProperty?.uid, aggregation), order = order,
                )
            )
            pageDao.touch(pageId, Instant.now())
        }
    }

    /** §5.4/DB3 wiring — the `ƒ` half of a `COMPUTED` property (docs/scope-decisions.md's DB3
     * wiring entry): a real formula, checked with [checkAllFormulas] before it is ever stored,
     * exactly the way §5.4 requires ("errors are caught when the formula is written"). Deliberately
     * narrower than [addRollupProperty] in one way — [expression] may reference this database's
     * own plain properties and other formula-authored `COMPUTED` properties, never a `RELATION` or
     * a rollup — because a formula that *does* traverse a relation needs the evaluator to resolve
     * a relation's related rows, which [FormulaPropertyResolver] has no way to do yet (it is a
     * synchronous, row-local function; a relation's related rows live on a different database and
     * resolving them needs suspend DAO calls the same way [computeRollupValue] already makes).
     * Building that is real, separate scope, cut from this PR rather than rushed — see this file's
     * own note by [computeComputedValue].
     *
     * [onResult] always fires — including the empty-errors success case — so the sheet that calls
     * this can dismiss on success and stay open showing [FormulaError.position]-anchored errors on
     * failure, the same shape a compiler gives an editor. Every other formula-authored `COMPUTED`
     * property on this database is checked *together* with the new one, via [checkAllFormulas],
     * so a formula referencing another formula gets a real cross-check — including cycle rejection
     * — rather than being validated alone and only failing once a cell tries to render it. */
    fun addFormulaProperty(name: String, expression: String, onResult: (FormulaCheckResult) -> Unit) {
        if (locked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || expression.isBlank()) return
        viewModelScope.launch {
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return@launch
            val allProps = propertyDao.getForDatabase(db.id)

            val ast = try {
                parseFormula(expression)
            } catch (e: FormulaSyntaxError) {
                onResult(FormulaCheckResult(FormulaType.ANY, listOf(FormulaError(e.message ?: "syntax error", e.position))))
                return@launch
            }

            // Every other formula-authored COMPUTED property, so a chained reference resolves
            // against a real checked type rather than falling through to `lookup` and getting
            // rejected as "no property named" — see checkAllFormulas' own note on why `nodes`
            // has to carry the whole set to be accurate, not just the one being added.
            val existingFormulaNodes = allProps.mapNotNull { p ->
                parseFormulaConfig(p.config)?.let { expr -> runCatching { parseFormula(expr) }.getOrNull()?.let { FormulaNode(p.name, it) } }
            }
            val results = checkAllFormulas(existingFormulaNodes + FormulaNode(trimmed, ast), formulaPropertyTypeLookup(allProps))
            val result = results[trimmed] ?: FormulaCheckResult(FormulaType.ANY, listOf(FormulaError("could not check this formula", 0)))
            onResult(result)
            if (result.errors.isNotEmpty()) return@launch

            val order = (allProps.maxOfOrNull { it.order } ?: -1) + 1
            propertyDao.insert(
                Property(databaseId = db.id, name = trimmed, type = PropertyType.COMPUTED, config = encodeFormulaConfig(expression), order = order)
            )
            pageDao.touch(pageId, Instant.now())
        }
    }

    /** Maps every plain-typed property on this database to the [FormulaPropertyKind] a formula
     * needs to type-check against it. A `RELATION` property resolves to [FormulaPropertyKind.Relation]
     * — a real, already-checked write-time error (see that case's own doc). A `COMPUTED` property
     * resolves to `null`, i.e. "no property by this name" — a formula-authored one is meant to be
     * found through [checkAllFormulas]'s own `nodes` list instead (see [addFormulaProperty]'s
     * note), and a rollup-authored one is not referenceable by a formula yet at all, matching
     * [formulaValueForProperty]'s identical exclusion at evaluation time. */
    private fun formulaPropertyTypeLookup(allProperties: List<Property>): FormulaPropertyTypeLookup =
        FormulaPropertyTypeLookup { name ->
            when (allProperties.find { it.name == name }?.type) {
                null -> null
                PropertyType.NUMBER -> FormulaPropertyKind.Typed(FormulaType.NUMBER)
                PropertyType.CHECKBOX -> FormulaPropertyKind.Typed(FormulaType.BOOLEAN)
                PropertyType.DATE -> FormulaPropertyKind.Typed(FormulaType.DATE)
                PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE,
                PropertyType.SELECT, PropertyType.MULTI_SELECT, PropertyType.INTERVAL -> FormulaPropertyKind.Typed(FormulaType.TEXT)
                PropertyType.RELATION -> FormulaPropertyKind.Relation
                PropertyType.COMPUTED -> null
            }
        }

    private fun deleteProperty(property: Property) {
        val db = database.value
        launchAndTouch(pageId) {
            if (db != null) {
                when (property.id) {
                    db.donePropertyId -> databaseSyncManager.disableSync(db)
                    // Dropped, not unbound: crystallizing into a column purged two lines down
                    // wrote nothing that survives and touched every row (audit 5a.3).
                    db.deadlinePropertyId -> databaseSyncManager.dropBinding(db, BindingRole.DEADLINE)
                    db.dueDatePropertyId -> databaseSyncManager.dropBinding(db, BindingRole.DUE_DATE)
                    db.recurrencePropertyId -> databaseSyncManager.dropBinding(db, BindingRole.RECURRENCE)
                    // §0.6.14 — a pointer, not a binding: nothing to crystallise, just cleared.
                    db.blockedByPropertyId -> pageDatabaseDao.update(db.copy(blockedByPropertyId = null, updatedAt = Instant.now()))
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

    /** S13 (B§13.8.2) — the database's hue on the wheel; null returns to the default hashed from the title. */
    fun setHue(hue: Int?) {
        // §3.1.2 — [launchAndTouch] does not check the lock (Canvas's funnel of that name does),
        // and a hue sheet open when View-Only went on still called this on Done.
        if (locked()) return
        launchAndTouch(pageId) {
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return@launchAndTouch
            pageDatabaseDao.update(db.copy(hue = hue))
        }
    }

    fun updateTitle(title: String) {
        if (locked()) return
        viewModelScope.launch {
            val current = page.value ?: pageDao.getById(pageId) ?: return@launch
            pageDao.update(current.copy(title = title, updatedAt = Instant.now()))
            // The title is in the index (§3.1.1), so a rename re-indexes like a block edit does.
            pageContentRepository.rebuildFtsForPage(pageId)
        }
    }

    /** §5.2 — "every row in a sync-enabled database becomes its own linked Task", not just
     * the rows present at the moment sync was turned on: a row created afterward gets seeded
     * into the sync relationship immediately, via the same (idempotent) per-row step
     * `enableSync` itself uses — and only that step, so no other row claims an edit (audit 5a.2). */
    fun addRow(title: String, onCreated: (Long) -> Unit) {
        if (locked()) return
        val db = database.value ?: return
        viewModelScope.launch {
            val now = Instant.now()
            val id = pageDao.insert(Page(title = title.ifBlank { "Untitled" }, databaseId = db.id, createdAt = now, updatedAt = now))
            if (db.syncToTasks) databaseSyncManager.addRowToSync(db, id, now)
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
        return pageDao.getMembersOf(targetDb.id, targetDb.labelId)
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

    /** The rollup picker's second step (§5.4/DB2): every property on the chosen relation's
     * *target* database, as candidates for what to aggregate. Deliberately unrestricted by
     * [RollupAggregation] — a `SUM` over a non-numeric property is a valid, if useless, choice
     * that computes to nothing rather than one the picker has to know how to forbid, the same
     * posture [computeRollupValue] itself takes on a mismatch. */
    suspend fun relationCandidateProperties(relationProperty: Property): List<Property> {
        val target = parseRelationConfig(relationProperty.config) ?: return emptyList()
        val targetPage = pageDao.getByUid(target.targetDatabasePageUid) ?: return emptyList()
        val targetDb = pageDatabaseDao.getByPageId(targetPage.id) ?: return emptyList()
        return propertyDao.getForDatabase(targetDb.id)
    }

    /** §5.4/DB2 — a `COMPUTED` rollup cell's value, derived fresh from the currently related
     * rows rather than stored: "compute on read" is the whole reason this needed no schema
     * change, since caching a result in a column would have been exactly that. Cross-database,
     * so — like [resolveRelatedTitles] — this suspends and is meant to be driven from a
     * `LaunchedEffect`, not read synchronously the way every stored cell type is. */
    suspend fun computeRollupValue(property: Property, row: TableRow): String? {
        val rollup = parseRollupConfig(property.config) ?: return null
        val relationProperty = propertyDao.getByUid(rollup.relationPropertyUid) ?: return null
        val relatedUids = parseRelationValue(row.values[relationProperty.id]?.value)

        if (rollup.aggregation == RollupAggregation.COUNT) return relatedUids.size.toString()
        if (relatedUids.isEmpty()) return null
        val targetProperty = rollup.targetPropertyUid?.let { propertyDao.getByUid(it) } ?: return null

        val rawValues = relatedUids.mapNotNull { uid ->
            pageDao.getByUid(uid)?.let { propertyValueDao.getForPropertyAndRow(targetProperty.id, it.id)?.value }
        }
        if (rawValues.isEmpty()) return null

        return when (rollup.aggregation) {
            RollupAggregation.SUM -> rawValues.mapNotNull { it.toDoubleOrNull() }.sum().let(::formatRollupNumber)
            RollupAggregation.MIN -> rawValues.mapNotNull { it.toDoubleOrNull() }.minOrNull()?.let(::formatRollupNumber)
            RollupAggregation.MAX -> rawValues.mapNotNull { it.toDoubleOrNull() }.maxOrNull()?.let(::formatRollupNumber)
            RollupAggregation.EARLIEST -> rawValues.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()?.toString()
            RollupAggregation.LATEST -> rawValues.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.maxOrNull()?.toString()
            RollupAggregation.SHOW_ORIGINAL -> rawValues.joinToString(", ")
            RollupAggregation.COUNT -> relatedUids.size.toString() // unreachable — handled above
        }
    }

    /** §5.4/DB3 wiring — a `COMPUTED` cell's value, dispatching on which of the two config shapes
     * [property] carries (see [PropertyType.COMPUTED]'s own note): [parseFormulaConfig] for a
     * formula-authored property, [computeRollupValue] otherwise. This is the one entry point
     * [ComputedCell] calls regardless of which authoring path created the property — the same way
     * a cell never needs to know how its own value came to be stored.
     *
     * Formula evaluation itself never suspends — every value a plain-property formula can
     * reference already lives in [row] and the database's own schema — but this fetches the
     * schema fresh via [propertyDao] rather than trusting `properties.value`, the same
     * StateFlow-race fix already applied to [addProperty]/[addRelationProperty]: a freshly
     * constructed ViewModel's `properties` stays at its `emptyList()` initial value until
     * something has actually collected it, which nothing does the first time a cell renders in a
     * newly opened database screen. */
    suspend fun computeComputedValue(property: Property, row: TableRow): String? {
        val expression = parseFormulaConfig(property.config) ?: return computeRollupValue(property, row)
        val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return null
        val allProps = propertyDao.getForDatabase(db.id)
        return formulaValueForProperty(property, expression, row, allProps, mutableSetOf()).toCellText()
    }

    /** §5.4/DB4 — "tapping a computed cell shows its inputs." One `COMPUTED` cell's derivation,
     * for whichever authoring path produced it: the referenced properties (and their current
     * values) a formula reads, or the related rows (and the value read from each) a rollup
     * aggregates — either way, exactly what the cell's own [result] was built from, not the cell
     * value alone. */
    sealed class ComputedExplanation {
        data class Formula(val expression: String, val inputs: List<Pair<String, String>>, val result: String?) : ComputedExplanation()
        data class Rollup(
            val relationName: String,
            val aggregation: RollupAggregation,
            val targetName: String?,
            val relatedRows: List<Pair<String, String?>>,
            val result: String?,
        ) : ComputedExplanation()

        /** [property] isn't a `COMPUTED` property at all, or its config no longer parses as
         * either shape (a schema change since it was authored) — nothing to explain. */
        object Unavailable : ComputedExplanation()
    }

    /** §5.4/DB4 — builds [ComputedExplanation] for [property] on [row]. A formula's inputs are
     * its *direct* `prop()` references only ([FormulaAst.propertyReferences] is not recursive
     * through a nested formula reference) — one level is what a person tapping a cell actually
     * wants to see, the same way [checkAllFormulas]'s own errors point at one formula at a time
     * rather than unrolling an entire dependency chain into one report. */
    suspend fun explainComputedValue(property: Property, row: TableRow): ComputedExplanation {
        val expression = parseFormulaConfig(property.config)
        if (expression != null) {
            val db = database.value ?: pageDatabaseDao.getByPageId(pageId) ?: return ComputedExplanation.Unavailable
            val allProps = propertyDao.getForDatabase(db.id)
            val ast = runCatching { parseFormula(expression) }.getOrNull() ?: return ComputedExplanation.Unavailable
            val inputs = ast.propertyReferences().distinctBy { it.propertyName }.map { ref ->
                val referenced = allProps.find { it.name == ref.propertyName }
                val text = referenced?.let { resolvePropertyFormulaValue(it, row, allProps, mutableSetOf()).toCellText() } ?: "—"
                ref.propertyName to (text ?: "—")
            }
            return ComputedExplanation.Formula(expression, inputs, computeComputedValue(property, row))
        }

        val rollup = parseRollupConfig(property.config) ?: return ComputedExplanation.Unavailable
        val relationProperty = propertyDao.getByUid(rollup.relationPropertyUid) ?: return ComputedExplanation.Unavailable
        val relatedUids = parseRelationValue(row.values[relationProperty.id]?.value)
        val targetProperty = rollup.targetPropertyUid?.let { propertyDao.getByUid(it) }
        val relatedRows = relatedUids.mapNotNull { uid ->
            val relatedPage = pageDao.getByUid(uid) ?: return@mapNotNull null
            val value = targetProperty?.let { propertyValueDao.getForPropertyAndRow(it.id, relatedPage.id)?.value }
            relatedPage.title to value
        }
        return ComputedExplanation.Rollup(relationProperty.name, rollup.aggregation, targetProperty?.name, relatedRows, computeComputedValue(property, row))
    }

    /** §5.4/DB4 — "a column footer shows sum/avg/empty." Only `NUMBER` and `COMPUTED` columns
     * carry a summary at all — a `SELECT`'s options or a `DATE`'s range aren't a sum/average
     * either way, and this feature's own acceptance test only asks for the numeric case, so
     * every other type reads as the "empty" half of that sentence rather than this function
     * guessing at a summary shape nothing asked for. `null` here means exactly that — [rows]
     * carried nothing numeric for this column, or the column isn't summarizable at all — and
     * both render the same "—" the caller already uses for a blank cell. */
    suspend fun computeColumnSummary(property: Property, rows: List<TableRow>): String? {
        val values = when (property.type) {
            PropertyType.NUMBER -> rows.mapNotNull { it.values[property.id]?.value?.toDoubleOrNull() }
            PropertyType.COMPUTED -> rows.mapNotNull { computeComputedValue(property, it)?.toDoubleOrNull() }
            else -> return null
        }
        if (values.isEmpty()) return null
        return "Σ ${formatRollupNumber(values.sum())} · ⌀ ${formatRollupNumber(values.sum() / values.size)}"
    }

    /** Resolves one formula-authored `COMPUTED` property's value against [row], row-local only —
     * no relation traversal, see [addFormulaProperty]'s own note on why that is cut from this PR.
     * [evaluating] guards a cycle at read time the same way write-time validation already rejects
     * one via [checkAllFormulas]/`topologicallySortFormulas` — a defensive backstop for a database
     * whose schema changed after a formula was saved (another formula it referenced got deleted
     * and a new one with the same name recreated a cycle), not a path any freshly-validated
     * formula should reach. */
    private fun formulaValueForProperty(
        property: Property,
        expression: String,
        row: TableRow,
        allProperties: List<Property>,
        evaluating: MutableSet<Long>,
    ): FormulaValue {
        if (property.id in evaluating) return FormulaValue.Empty
        val ast = runCatching { parseFormula(expression) }.getOrNull() ?: return FormulaValue.Empty
        evaluating += property.id
        val result = evaluateFormula(
            ast,
            FormulaPropertyResolver { name ->
                val referenced = allProperties.find { it.name == name } ?: return@FormulaPropertyResolver FormulaValue.Empty
                resolvePropertyFormulaValue(referenced, row, allProperties, evaluating)
            },
        )
        evaluating -= property.id
        return result
    }

    /** Any property's current value as a [FormulaValue], whichever of [plainFormulaValue] or
     * [formulaValueForProperty] applies — the one place both [formulaValueForProperty]'s own
     * resolver and [explainComputedValue] need this same "resolve by property, not by which kind
     * it is" logic, factored out so the two could not silently drift apart. */
    private fun resolvePropertyFormulaValue(
        property: Property,
        row: TableRow,
        allProperties: List<Property>,
        evaluating: MutableSet<Long>,
    ): FormulaValue = plainFormulaValue(property, row)
        ?: parseFormulaConfig(property.config)?.let { formulaValueForProperty(property, it, row, allProperties, evaluating) }
        ?: FormulaValue.Empty

    /** A row-local, non-`COMPUTED` property's stored value as a [FormulaValue], typed per its own
     * [PropertyType] — `null` for a `RELATION` or `COMPUTED` property, which [formulaValueForProperty]
     * handles itself (a rollup is not referenceable by a formula yet; a formula-authored one
     * recurses). Kept `null`-returning rather than folding into one big `when` so the "is this
     * even a plain property" question reads as its own check at the call site. */
    private fun plainFormulaValue(property: Property, row: TableRow): FormulaValue? {
        val raw = row.values[property.id]?.value
        return when (property.type) {
            PropertyType.NUMBER -> raw?.toDoubleOrNull()?.let { FormulaValue.Number(it) } ?: FormulaValue.Empty
            PropertyType.CHECKBOX -> raw?.toBooleanStrictOrNull()?.let { FormulaValue.Bool(it) } ?: FormulaValue.Empty
            PropertyType.DATE -> raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { FormulaValue.DateValue(it) } ?: FormulaValue.Empty
            PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE, PropertyType.SELECT, PropertyType.MULTI_SELECT, PropertyType.INTERVAL ->
                raw?.let { FormulaValue.Text(it) } ?: FormulaValue.Empty
            PropertyType.RELATION, PropertyType.COMPUTED -> null
        }
    }

    /** A formula result's cell text — `null` (rendered as "—" by [ComputedCell]) for [FormulaValue.Empty],
     * and [formatRollupNumber]'s same whole-number-drops-its-decimal form for a [FormulaValue.Number]
     * result, so `prop("Score") * 2` reads `6` rather than `6.0` the same way a rollup total already does. */
    private fun FormulaValue.toCellText(): String? = when (this) {
        is FormulaValue.Number -> formatRollupNumber(value)
        is FormulaValue.Text -> value
        is FormulaValue.Bool -> value.toString()
        is FormulaValue.DateValue -> value.toString()
        FormulaValue.Empty -> null
    }

    fun toggleDone(entry: Entry, checked: Boolean) {
        if (locked()) return
        viewModelScope.launch { resolveEntryUseCase.setDone(entry.id, checked) }
    }

    // ------------------------------------------------------------------ §0.6.14 Timeline

    /** One routing call for every date write a view makes: a bound When/Deadline on a synced row
     * goes through the Entry ([setBoundDate] — alarms re-armed), anything else is a stored cell.
     * The Timeline's drag and the Table's date cell both come here, so dragging a synced row's
     * bar *is* moving its task. */
    fun setDateCell(row: TableRow, propertyId: Long, date: LocalDate?) {
        val db = database.value
        val entry = row.linkedEntry
        val role = when (propertyId) {
            db?.deadlinePropertyId -> BindingRole.DEADLINE
            db?.dueDatePropertyId -> BindingRole.DUE_DATE
            else -> null
        }
        if (role != null && entry != null) {
            setBoundDate(entry, role, date)
        } else {
            val property = properties.value.find { it.id == propertyId } ?: return
            setCellValue(property, row.page, date?.toString())
        }
    }

    /** A bar dragged by [days]: start, and the end when the view has one, move together. */
    fun moveBar(view: PageDatabaseView, bar: TimelineBar<TableRow>, days: Long) {
        val startId = view.datePropertyId ?: return
        val moved = bar.shifted(days)
        setDateCell(bar.row, startId, moved.start)
        view.endDatePropertyId?.let { endId -> if (valueForCell(bar.row, endId) != null) setDateCell(bar.row, endId, moved.end) }
    }

    /** A row's date cell as a date, through the same bound-or-stored read every view uses. */
    fun dateForCell(row: TableRow, propertyId: Long): LocalDate? =
        valueForCell(row, propertyId)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** §0.6.14 — per row, what blocks it; empty when the database names no "blocked by" column.
     * Done is read through the Done binding; without one every blocker counts (see [blockedBy]). */
    val blockedStates: StateFlow<Map<Long, BlockedState>> = combine(tableRows, database) { rowList, db ->
        val propertyId = db?.blockedByPropertyId ?: return@combine emptyMap()
        val byUid = rowList.associateBy { it.page.uid }
        val doneId = db.donePropertyId
        rowList.associate { row ->
            row.page.id to blockedBy(
                relationValue = row.values[propertyId]?.value,
                rowByUid = { uid -> byUid[uid]?.page },
                isDone = { page -> doneId != null && byUid[page.uid]?.let { valueForCell(it, doneId) } == "true" },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The RELATION properties that point back at this database — the only ones "blocked by"
     * can name. */
    val selfRelationProperties: StateFlow<List<Property>> = combine(properties, page) { props, p ->
        props.filter { it.type == PropertyType.RELATION && parseRelationConfig(it.config)?.targetDatabasePageUid == p?.uid }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §0.6.14 — the pointer itself; null clears it. Not a [BindingRole]: the relation values
     * stay stored, nothing is proxied through an Entry. Travels with the database record. */
    fun setBlockedByProperty(propertyId: Long?) {
        if (locked()) return
        val db = database.value ?: return
        launchAndTouch(pageId) { pageDatabaseDao.update(db.copy(blockedByPropertyId = propertyId, updatedAt = Instant.now())) }
    }

    /** Writes the date a bound cell edits: the When for [BindingRole.DEADLINE], the deadline
     * for [BindingRole.DUE_DATE] (§0.6.4). Only the When re-arms alarms — a deadline fires
     * nothing by itself. */
    fun setBoundDate(entry: Entry, role: BindingRole, date: LocalDate?) {
        if (locked()) return
        viewModelScope.launch {
            if (role == BindingRole.DUE_DATE) {
                entryDao.update(entry.copy(dueDate = date, updatedAt = Instant.now()))
                return@launch
            }
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
