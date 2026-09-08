@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.page.Page
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.SortDirection
import com.tendril.app.data.pagedatabase.ViewFilter
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.data.pagedatabase.RollupAggregation
import com.tendril.app.data.pagedatabase.parseRelationValue
import com.tendril.app.domain.BindingRole
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.LocalDate

private val CELL_WIDTH = 160.dp

@Composable
fun PageDatabaseScreen(core: WorkbenchCore, pageId: Long, onBack: () -> Unit, onOpenPage: (Long) -> Unit) {
    val viewModel: PageDatabaseViewModel = viewModel(
        key = "database_$pageId",
        factory = viewModelFactory {
            initializer {
                PageDatabaseViewModel(
                    pageId,
                    core.database.pageDao(),
                    core.database.pageDatabaseDao(),
                    core.database.propertyDao(),
                    core.database.propertyValueDao(),
                    core.database.entryDao(),
                    core.database.pageDatabaseViewDao(),
                    core.database.blockDao(),
                    core.databaseSyncManager,
                    core.resolveEntryUseCase,
                    core.entryScheduleCoordinator,
                    core.templateManager,
                    core.purgeRegistry,
                    core.viewLockState,
                )
            }
        }
    )
    val viewOnly = LocalViewOnly.current
    val page by viewModel.page.collectAsState()
    val database by viewModel.database.collectAsState()
    val properties by viewModel.properties.collectAsState()
    val tableRows by viewModel.tableRows.collectAsState()
    val displayedRows by viewModel.displayedRows.collectAsState()
    val views by viewModel.views.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()
    val boardColumns by viewModel.boardColumns.collectAsState()
    val rowCovers by viewModel.rowCovers.collectAsState()
    val pendingEnable by viewModel.pendingSyncEnable.collectAsState()
    val pendingDisable by viewModel.pendingSyncDisable.collectAsState()
    val pendingDeleteProperty by viewModel.pendingDeleteProperty.collectAsState()
    val pendingTypeChange by viewModel.pendingTypeChange.collectAsState()
    val pendingDeleteRow by viewModel.pendingDeleteRow.collectAsState()
    val pendingRebind by viewModel.pendingRebind.collectAsState()

    LaunchedEffect(database) { if (database != null) viewModel.ensureDefaultView() }

    var titleField by remember(page?.id) { mutableStateOf(page?.title ?: "") }
    var showMenu by remember { mutableStateOf(false) }
    var showAddProperty by remember { mutableStateOf(false) }
    var showAddView by remember { mutableStateOf(false) }
    var showViewConfig by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = titleField,
                        onValueChange = { titleField = it; viewModel.updateTitle(it) },
                        readOnly = viewOnly,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showMenu = true }, enabled = !viewOnly) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        val syncOn = database?.syncToTasks == true
                        DropdownMenuItem(
                            text = { Text(if (syncOn) "Turn off Sync to Tasks" else "Sync to Tasks") },
                            onClick = {
                                showMenu = false
                                if (syncOn) viewModel.requestDisableSync() else viewModel.requestEnableSync()
                            },
                        )
                        DropdownMenuItem(text = { Text("Add property") }, onClick = { showMenu = false; showAddProperty = true })
                        DropdownMenuItem(text = { Text("Save as template") }, onClick = { showMenu = false; viewModel.saveAsTemplate() })
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                views.forEach { view ->
                    val selected = view.id == (selectedView?.id ?: views.firstOrNull()?.id)
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { if (selected) { if (!viewOnly) showViewConfig = true } else viewModel.selectView(view.id) },
                        label = { Text(view.name) },
                    )
                }
                if (!viewOnly) {
                    AssistChip(onClick = { showAddView = true }, label = { Text("Add view") }, leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) })
                }
            }
            HorizontalDivider()
            when (selectedView?.viewType) {
                ViewType.BOARD -> BoardBody(boardColumns, properties, selectedView, viewModel, onOpenPage)
                ViewType.GALLERY -> GalleryBody(displayedRows, properties, rowCovers, viewModel, onOpenPage)
                ViewType.CALENDAR -> CalendarBody(displayedRows, selectedView, viewModel, onOpenPage)
                else -> TableBody(displayedRows, properties, database, hScroll, viewModel, onOpenPage)
            }
        }
    }

    if (showAddProperty) {
        // Fetched fresh each time the sheet opens rather than kept live — the candidate list
        // (every database in the app) changes rarely enough that a one-shot load, the same
        // shape `EnableSyncSheet` already uses, is not worth a live Flow's extra machinery.
        var relationTargets by remember { mutableStateOf<List<Pair<Page, Long>>>(emptyList()) }
        LaunchedEffect(Unit) { relationTargets = viewModel.relationTargetOptions() }
        AddPropertySheet(
            existingProperties = properties,
            targetDatabases = relationTargets,
            viewModel = viewModel,
            onDismiss = { showAddProperty = false },
            onAdd = { name, type, config -> viewModel.addProperty(name, type, config); showAddProperty = false },
            onAddRelation = { name, targetDatabaseId -> viewModel.addRelationProperty(name, targetDatabaseId); showAddProperty = false },
            onAddRollup = { name, relationPropertyId, targetPropertyId, aggregation ->
                viewModel.addRollupProperty(name, relationPropertyId, targetPropertyId, aggregation)
                showAddProperty = false
            },
        )
    }

    if (showAddView) {
        AddViewSheet(onDismiss = { showAddView = false }, onAdd = { name, type -> viewModel.addView(name, type); showAddView = false })
    }

    if (showViewConfig) {
        selectedView?.let { view ->
            ViewConfigSheet(
                view = view,
                properties = properties,
                onDismiss = { showViewConfig = false },
                onUpdate = { viewModel.updateView(it) },
                onDelete = { viewModel.deleteView(view); showViewConfig = false },
            )
        }
    }

    if (pendingEnable) {
        EnableSyncSheet(
            properties = properties,
            rows = tableRows.map { it.page },
            onDismiss = { viewModel.dismissEnableSync() },
            onConfirm = { done, deadline, recurrence, rowIds -> viewModel.confirmEnableSync(done, deadline, recurrence, rowIds) },
        )
    }

    if (pendingDisable) {
        DisableSyncConfirm(onDismiss = { viewModel.dismissDisableSync() }, onConfirm = { viewModel.confirmDisableSync() })
    }

    pendingDeleteProperty?.let { property ->
        DeletePropertyConfirm(
            property = property,
            database = database,
            onDismiss = { viewModel.dismissDeleteProperty() },
            onConfirm = { viewModel.confirmDeleteProperty() },
        )
    }

    pendingTypeChange?.let { property ->
        val affectedCount by viewModel.typeChangeAffectedCount.collectAsState()
        ChangeTypeDialog(
            property = property,
            affectedCount = affectedCount,
            onDismiss = { viewModel.dismissChangeType() },
            onConfirm = { newType -> viewModel.confirmChangeType(property, newType) },
        )
    }

    pendingDeleteRow?.let { row ->
        DeleteRowConfirm(
            row = row,
            synced = database?.syncToTasks == true,
            onDismiss = { viewModel.dismissDeleteRow() },
            onConfirm = { viewModel.confirmDeleteRow() },
        )
    }

    pendingRebind?.let { pending ->
        RebindConfirm(
            pending = pending,
            properties = properties,
            onDismiss = { viewModel.dismissRebind() },
            onConfirm = { viewModel.confirmRebind() },
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun TableBody(
    rows: List<TableRow>,
    properties: List<Property>,
    database: com.tendril.app.data.pagedatabase.PageDatabase?,
    hScroll: androidx.compose.foundation.ScrollState,
    viewModel: PageDatabaseViewModel,
    onOpenPage: (Long) -> Unit,
) {
    val viewOnly = LocalViewOnly.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.horizontalScroll(hScroll).padding(top = 8.dp)) {
                Box(modifier = Modifier.width(CELL_WIDTH).padding(horizontal = 12.dp)) {
                    Text("Title", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                properties.forEach { property ->
                    Box(modifier = Modifier.width(CELL_WIDTH).padding(horizontal = 12.dp)) {
                        PropertyHeaderCell(property, database, properties, viewModel)
                    }
                }
                Box(modifier = Modifier.width(40.dp))
            }
        }
        item { HorizontalDivider() }
        items(rows, key = { it.page.id }) { tableRow ->
            Row(
                modifier = Modifier.horizontalScroll(hScroll).fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(CELL_WIDTH).padding(horizontal = 12.dp)) {
                    Text(
                        tableRow.page.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().clickableRow { onOpenPage(tableRow.page.id) },
                    )
                }
                properties.forEach { property ->
                    Box(modifier = Modifier.width(CELL_WIDTH).padding(horizontal = 12.dp)) {
                        PropertyCell(property, database, tableRow, viewModel)
                    }
                }
                Box(modifier = Modifier.width(40.dp)) {
                    RowMenu(onDelete = { viewModel.requestDeleteRow(tableRow.page) })
                }
            }
            HorizontalDivider()
        }
        if (!viewOnly) {
            item {
                TextButton(onClick = { viewModel.addRow("Untitled") { onOpenPage(it) } }, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add row")
                }
            }
        }
    }
}

@Composable
private fun BoardBody(
    columns: List<Pair<String, List<TableRow>>>,
    properties: List<Property>,
    view: PageDatabaseView?,
    viewModel: PageDatabaseViewModel,
    onOpenPage: (Long) -> Unit,
) {
    val groupProperty = properties.find { it.id == view?.groupByPropertyId }
    if (groupProperty == null) {
        EmptyBoardPrompt()
        return
    }
    Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        columns.forEach { (option, rows) ->
            Column(modifier = Modifier.width(240.dp)) {
                Text(option, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.page.id }) { row ->
                        BoardCard(row, option, columns.map { it.first }, groupProperty.id, viewModel, onOpenPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBoardPrompt() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Board needs a Select property to group by. Add one, then configure this view.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoardCard(row: TableRow, currentOption: String, allOptions: List<String>, groupPropertyId: Long, viewModel: PageDatabaseViewModel, onOpenPage: (Long) -> Unit) {
    // A tap-to-reassign menu rather than a literal drag gesture — the same call already made
    // for block reordering (§3.1.1's Move Up/Down over a drag handle): same end capability
    // (any card can move to any column), far less gesture-tracking risk on touch targets this size.
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(row.page.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickableRow { onOpenPage(row.page.id) })
            Spacer(Modifier.height(6.dp))
            Box {
                TextButton(onClick = { showMenu = true }, enabled = !LocalViewOnly.current) { Text("Move…") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    allOptions.filter { it != currentOption }.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { viewModel.moveRowToColumn(row, groupPropertyId, option); showMenu = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBody(rows: List<TableRow>, properties: List<Property>, covers: Map<Long, com.tendril.app.data.page.Block>, viewModel: PageDatabaseViewModel, onOpenPage: (Long) -> Unit) {
    val viewOnly = LocalViewOnly.current
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        gridItems(rows, key = { it.page.id }) { row ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.padding(6.dp).clickableRow { onOpenPage(row.page.id) },
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surface)) {
                        val cover = covers[row.page.id]
                        if (cover == null) {
                            Text("No image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                        }
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(row.page.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        properties.take(3).forEach { property ->
                            val value = viewModel.valueForCell(row, property.id)
                            if (!value.isNullOrBlank()) {
                                Text("${property.name}: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (!viewOnly) {
            gridItems(listOf(Unit)) {
                TextButton(onClick = { viewModel.addRow("Untitled") { onOpenPage(it) } }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add row")
                }
            }
        }
    }
}

@Composable
private fun CalendarBody(rows: List<TableRow>, view: PageDatabaseView?, viewModel: PageDatabaseViewModel, onOpenPage: (Long) -> Unit) {
    val datePropertyId = view?.datePropertyId
    if (datePropertyId == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Calendar needs a Date property to plot by — configure this view to pick one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val grouped = rows.groupBy { viewModel.valueForCell(it, datePropertyId) }
    val sortedDates = grouped.keys.filterNotNull().sorted()
    val hasUndated = grouped.containsKey(null)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sortedDates.forEach { date ->
            item { Text(date, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(grouped[date].orEmpty(), key = { it.page.id }) { row ->
                Text(row.page.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickableRow { onOpenPage(row.page.id) }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        if (hasUndated) {
            item { Text("No date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(grouped[null].orEmpty(), key = { it.page.id }) { row ->
                Text(row.page.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickableRow { onOpenPage(row.page.id) }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AddViewSheet(onDismiss: () -> Unit, onAdd: (String, ViewType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ViewType.TABLE) }
    var showTypeMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("New view", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Box {
                TextButton(onClick = { showTypeMenu = true }) { Text("Type: ${type.name.lowercase()}") }
                DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                    ViewType.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name.lowercase()) }, onClick = { type = option; showTypeMenu = false })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { onAdd(name.ifBlank { type.name.lowercase().replaceFirstChar(Char::uppercase) }, type) }) { Text("Add") }
        }
    }
}

@Composable
private fun ViewConfigSheet(
    view: PageDatabaseView,
    properties: List<Property>,
    onDismiss: () -> Unit,
    onUpdate: (PageDatabaseView) -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Configure \"${view.name}\"", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

            when (view.viewType) {
                ViewType.BOARD -> BindingPicker("Group by (Select property)", properties.filter { it.type == PropertyType.SELECT }, view.groupByPropertyId) { id ->
                    onUpdate(view.copy(groupByPropertyId = id))
                }
                ViewType.CALENDAR -> BindingPicker("Plot by (Date property)", properties.filter { it.type == PropertyType.DATE }, view.datePropertyId) { id ->
                    onUpdate(view.copy(datePropertyId = id))
                }
                else -> {}
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Sort", style = MaterialTheme.typography.labelLarge)
            BindingPicker("Sort by", properties, view.sortPropertyId, allowNone = true) { id -> onUpdate(view.copy(sortPropertyId = id)) }
            if (view.sortPropertyId != null) {
                Row {
                    TextButton(onClick = { onUpdate(view.copy(sortDirection = SortDirection.ASC)) }) { Text(if (view.sortDirection != SortDirection.DESC) "● Ascending" else "Ascending") }
                    TextButton(onClick = { onUpdate(view.copy(sortDirection = SortDirection.DESC)) }) { Text(if (view.sortDirection == SortDirection.DESC) "● Descending" else "Descending") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Filter", style = MaterialTheme.typography.labelLarge)
            FilterEditor(view, properties, onUpdate)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onDelete) { Text("Delete view") }
        }
    }
}

@Composable
private fun FilterEditor(view: PageDatabaseView, properties: List<Property>, onUpdate: (PageDatabaseView) -> Unit) {
    var propertyId by remember(view.id) { mutableStateOf(view.filter?.propertyId) }
    var comparator by remember(view.id) { mutableStateOf(view.filter?.comparator ?: ViewFilter.Comparator.EQUALS) }
    var value by remember(view.id) { mutableStateOf(view.filter?.value ?: "") }

    BindingPicker("Property", properties, propertyId, allowNone = true) { id ->
        propertyId = id
        onUpdate(view.copy(filter = id?.let { ViewFilter(it, comparator, value) }))
    }
    if (propertyId != null) {
        var showComparatorMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showComparatorMenu = true }) { Text(comparator.name.lowercase().replace('_', ' ')) }
            DropdownMenu(expanded = showComparatorMenu, onDismissRequest = { showComparatorMenu = false }) {
                ViewFilter.Comparator.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name.lowercase().replace('_', ' ')) },
                        onClick = {
                            comparator = option
                            showComparatorMenu = false
                            onUpdate(view.copy(filter = ViewFilter(propertyId!!, comparator, value)))
                        },
                    )
                }
            }
        }
        if (comparator == ViewFilter.Comparator.EQUALS || comparator == ViewFilter.Comparator.NOT_EQUALS || comparator == ViewFilter.Comparator.CONTAINS) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onUpdate(view.copy(filter = ViewFilter(propertyId!!, comparator, it)))
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PropertyHeaderCell(
    property: Property,
    database: com.tendril.app.data.pagedatabase.PageDatabase?,
    properties: List<Property>,
    viewModel: PageDatabaseViewModel,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRebindPicker by remember { mutableStateOf(false) }
    val viewOnly = LocalViewOnly.current
    val role = bindingRoleOf(database, property.id)
    // §5.5 — "A bound property (Done/Deadline) never enters this flow to begin with — there's
    // no real schema-editable field there to convert." Bound properties keep Delete (routed
    // into the sync-disable/unbind confirm flow) but never offer Change type; instead they offer
    // §5.2.1's rebind/unbind, the correction lever a plain delete-and-recreate can't provide.
    // §5.4/DB1 — a RELATION property is excluded from Change type for the same reason: its
    // real "schema" is the target database and its paired reverse property, neither of which
    // this generic dialog knows how to re-derive. Delete-and-recreate through
    // [addRelationProperty][PageDatabaseViewModel.addRelationProperty] is the correction lever.
    // §5.4/DB2 — COMPUTED is excluded on the same reasoning again: its schema is the relation,
    // target property and aggregation triple, authored through [addRollupProperty]
    // [PageDatabaseViewModel.addRollupProperty], not a field this dialog could ever convert into.
    val canChangeType = role == null && property.type != PropertyType.RELATION && property.type != PropertyType.COMPUTED
    Box {
        Text(
            property.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.clickableRow { if (!viewOnly) showMenu = true },
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (canChangeType) {
                DropdownMenuItem(text = { Text("Edit property type") }, onClick = { viewModel.requestChangeType(property); showMenu = false })
            } else if (role != null) {
                DropdownMenuItem(text = { Text("Change binding…") }, onClick = { showMenu = false; showRebindPicker = true })
            }
            DropdownMenuItem(text = { Text("Delete property") }, onClick = { viewModel.requestDeleteProperty(property); showMenu = false })
        }
    }
    if (showRebindPicker && role != null) {
        RebindPickerDialog(
            role = role,
            currentProperty = property,
            candidates = properties.filter { it.type == bindingTypeFor(role) && it.id != property.id },
            onDismiss = { showRebindPicker = false },
            onPick = { newPropertyId ->
                showRebindPicker = false
                viewModel.requestRebind(role, property.id, newPropertyId)
            },
        )
    }
}

/** Which [BindingRole], if any, [propertyId] currently fills on [database] — null for an
 * ordinary, unbound property. */
private fun bindingRoleOf(database: com.tendril.app.data.pagedatabase.PageDatabase?, propertyId: Long): BindingRole? = when (propertyId) {
    database?.donePropertyId -> BindingRole.DONE
    database?.deadlinePropertyId -> BindingRole.DEADLINE
    database?.recurrencePropertyId -> BindingRole.RECURRENCE
    else -> null
}

private fun bindingTypeFor(role: BindingRole): PropertyType = when (role) {
    BindingRole.DONE -> PropertyType.CHECKBOX
    BindingRole.DEADLINE -> PropertyType.DATE
    BindingRole.RECURRENCE -> PropertyType.INTERVAL
}

private fun bindingRoleLabel(role: BindingRole): String = when (role) {
    BindingRole.DONE -> "Done"
    BindingRole.DEADLINE -> "Deadline"
    BindingRole.RECURRENCE -> "Recurrence"
}

/** §5.2.1 — the rebind/unbind picker. Done is mandatory whenever sync is on, so its list never
 * offers "None"; Deadline/Recurrence are optional and can be cleared back to an ordinary
 * property entirely. Picking either kind only stages the change — [RebindConfirm] is what
 * actually commits it, per §5.5's confirm-and-commit-immediately pattern. */
@Composable
private fun RebindPickerDialog(
    role: BindingRole,
    currentProperty: Property,
    candidates: List<Property>,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change ${bindingRoleLabel(role)} binding") },
        text = {
            Column {
                Text(
                    "\"${currentProperty.name}\" currently drives ${bindingRoleLabel(role)}. Pick another ${bindingTypeFor(role).name.lowercase()} property, or remove the binding.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Text("No other ${bindingTypeFor(role).name.lowercase()} property exists yet.", style = MaterialTheme.typography.bodySmall)
                }
                candidates.forEach { candidate ->
                    TextButton(onClick = { onPick(candidate.id) }) { Text(candidate.name) }
                }
                if (role != BindingRole.DONE) {
                    TextButton(onClick = { onPick(null) }) { Text("Remove binding") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** §5.2.1 — "Requires its own confirm dialog, stating plainly that the property freezes at its
 * current value and stops updating automatically." */
@Composable
private fun RebindConfirm(
    pending: PageDatabaseViewModel.PendingRebind,
    properties: List<Property>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val currentName = properties.find { it.id == pending.currentPropertyId }?.name ?: "This property"
    val newName = pending.newPropertyId?.let { id -> properties.find { it.id == id }?.name }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (newName != null) "Rebind ${bindingRoleLabel(pending.role)}?" else "Remove ${bindingRoleLabel(pending.role)} binding?") },
        text = {
            Text(
                if (newName != null) {
                    "\"$currentName\" freezes at its current value and stops updating automatically. \"$newName\" takes over as ${bindingRoleLabel(pending.role)} from here on — no Task is deleted or recreated."
                } else {
                    "\"$currentName\" freezes at its current value and stops updating automatically. This database keeps syncing without a ${bindingRoleLabel(pending.role).lowercase()} binding."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (newName != null) "Rebind" else "Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RowMenu(onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }, enabled = !LocalViewOnly.current) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Row options")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Delete row") }, onClick = { showMenu = false; onDelete() })
        }
    }
}

/** §5.5 — "Property deletion requires an explicit confirm dialog... If the property is
 * done_property_id- or deadline_property_id-bound, deleting it is just one more trigger into
 * the same Sync-to-Tasks-disable flow... stating plainly that confirming disables sync." */
@Composable
private fun DeletePropertyConfirm(
    property: Property,
    database: com.tendril.app.data.pagedatabase.PageDatabase?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val bound = database != null && property.id in listOfNotNull(database.donePropertyId, database.deadlinePropertyId, database.recurrencePropertyId)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${property.name}\"?") },
        text = {
            Text(
                if (bound) {
                    "This property drives Sync to Tasks — deleting it turns sync off and moves this database's linked recurring Tasks to Trash (restorable from there). A property re-added with the same name later starts a new sync relationship, not this one."
                } else {
                    "Its stored values are gone for good — a property re-added with the same name later starts fresh, it won't recover this one's data."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** §5.5 — "opening 'Edit property type' shows a preview of what would happen to existing
 * values... the change commits immediately on confirm." */
@Composable
private fun ChangeTypeDialog(
    property: Property,
    affectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (PropertyType) -> Unit,
) {
    var newType by remember(property.id) { mutableStateOf(property.type) }
    var showTypeMenu by remember { mutableStateOf(false) }
    // RELATION is never a *target* of a generic type change (same reasoning as INTERVAL):
    // converting some other property to it here would set `type = RELATION` with no target
    // database and no reverse property, since only `addRelationProperty` knows how to create
    // that pairing. COMPUTED is excluded for the same reason — no target property, aggregation,
    // or relation to aggregate across. This dialog never opens for a property that already *is*
    // one of these — `PropertyHeaderCell` hides "Edit property type" for those — so this filter
    // only needs to cover the "convert into" direction.
    val offeredTypes = PropertyType.entries.filter { it != PropertyType.INTERVAL && it != PropertyType.RELATION && it != PropertyType.COMPUTED }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit property type") },
        text = {
            Column {
                Text("\"${property.name}\" is currently ${property.type.name.lowercase()}.")
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton(onClick = { showTypeMenu = true }) { Text("Change to: ${newType.name.lowercase()}") }
                    DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        offeredTypes.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name.lowercase()) }, onClick = { newType = option; showTypeMenu = false })
                        }
                    }
                }
                if (newType != property.type) {
                    Spacer(Modifier.height(8.dp))
                    Text(typeChangePreview(property.type, newType, affectedCount), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = newType != property.type, onClick = { onConfirm(newType) }) { Text("Change") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun typeChangePreview(oldType: PropertyType, newType: PropertyType, count: Int): String {
    if (count == 0) return "No rows currently have a value here — safe to convert."
    return when {
        oldType == PropertyType.MULTI_SELECT ->
            "$count row(s) will lose their multi-select tags, collapsed into plain text that won't cleanly re-split."
        oldType == PropertyType.SELECT && newType != PropertyType.MULTI_SELECT ->
            "$count row(s) will keep their current text but lose the Select option list."
        oldType == PropertyType.DATE ->
            "$count row(s) will lose their date semantics — the value becomes plain text."
        newType == PropertyType.CHECKBOX ->
            "$count row(s) will collapse to checked/unchecked — anything other than \"true\" becomes unchecked."
        newType == PropertyType.DATE ->
            "$count row(s) will keep their text, but it may not parse as a valid date."
        else -> "$count row(s) currently have a value here — converting may change how it displays."
    }
}

/** §5.5 — "Row deletion... requires an explicit confirm dialog." */
@Composable
private fun DeleteRowConfirm(row: Page, synced: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${row.title}\"?") },
        text = {
            Text(
                if (synced) {
                    "This row and its linked Task both move to Trash — restorable from there, not deleted outright."
                } else {
                    "This row moves to Trash — restorable from there, not deleted outright."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PropertyCell(
    property: Property,
    database: com.tendril.app.data.pagedatabase.PageDatabase?,
    row: TableRow,
    viewModel: PageDatabaseViewModel,
) {
    when (property.id) {
        database?.donePropertyId -> {
            val entry = row.linkedEntry
            Checkbox(
                checked = entry?.status == com.tendril.app.data.entry.EntryStatus.DONE,
                onCheckedChange = { checked -> entry?.let { viewModel.toggleDone(it, checked) } },
                enabled = !LocalViewOnly.current,
            )
        }
        database?.deadlinePropertyId -> DeadlineCell(row.linkedEntry, viewModel)
        database?.recurrencePropertyId -> RecurrenceCell(row.linkedEntry, viewModel)
        else -> UnboundCell(property, row, viewModel)
    }
}

@Composable
private fun DeadlineCell(entry: Entry?, viewModel: PageDatabaseViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    val viewOnly = LocalViewOnly.current
    Text(
        entry?.startDate?.toString() ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickableRow { if (entry != null && !viewOnly) showPicker = true },
    )
    if (showPicker && entry != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (entry.startDate ?: LocalDate.now()).toDatePickerMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.setDeadline(entry, datePickerMillisToLocalDate(millis))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun RecurrenceCell(entry: Entry?, viewModel: PageDatabaseViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    val viewOnly = LocalViewOnly.current
    val rule = entry?.recurrenceRule as? com.tendril.app.data.entry.RecurrenceRule.Elastic
    Text(
        rule?.period?.toString() ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickableRow { if (entry != null && !viewOnly) showPicker = true },
    )
    if (showPicker && entry != null) {
        IntervalPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { count, unit -> viewModel.setRecurrence(entry, count, unit); showPicker = false },
        )
    }
}

@Composable
private fun UnboundCell(property: Property, row: TableRow, viewModel: PageDatabaseViewModel) {
    val value = row.values[property.id]?.value
    val viewOnly = LocalViewOnly.current
    when (property.type) {
        PropertyType.CHECKBOX -> Checkbox(
            checked = value == "true",
            onCheckedChange = { viewModel.setCellValue(property, row.page, it.toString()) },
            enabled = !viewOnly,
        )
        PropertyType.DATE -> {
            var showPicker by remember { mutableStateOf(false) }
            Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickableRow { if (!viewOnly) showPicker = true })
            if (showPicker) {
                val initial = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                val state = rememberDatePickerState(initialSelectedDateMillis = initial.toDatePickerMillis())
                DatePickerDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let {
                                val date = datePickerMillisToLocalDate(it)
                                viewModel.setCellValue(property, row.page, date.toString())
                            }
                            showPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
                ) { DatePicker(state = state) }
            }
        }
        PropertyType.SELECT -> {
            var showMenu by remember { mutableStateOf(false) }
            val options = property.config?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            Box {
                Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickableRow { if (!viewOnly) showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { viewModel.setCellValue(property, row.page, option); showMenu = false })
                    }
                }
            }
        }
        PropertyType.MULTI_SELECT -> {
            var showMenu by remember { mutableStateOf(false) }
            val options = property.config?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val selected = value?.split(",")?.filter { it.isNotBlank() }.orEmpty().toSet()
            Box {
                Text(if (selected.isEmpty()) "—" else selected.joinToString(", "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickableRow { if (!viewOnly) showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text((if (option in selected) "✓ " else "") + option) },
                            onClick = {
                                val newSelected = if (option in selected) selected - option else selected + option
                                viewModel.setCellValue(property, row.page, newSelected.joinToString(","))
                            },
                        )
                    }
                }
            }
        }
        PropertyType.RELATION -> RelationCell(property, row, viewModel)
        PropertyType.COMPUTED -> ComputedCell(property, row, viewModel)
        else -> {
            var text by remember(value) { mutableStateOf(value ?: "") }
            BasicTextField(
                value = text,
                onValueChange = { text = it; viewModel.setCellValue(property, row.page, it) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                readOnly = viewOnly,
                singleLine = true,
            )
        }
    }
}

/** §5.4/DB1 — displays a relation cell as the related rows' titles rather than their stored
 * uids, resolved fresh whenever the stored set changes; a uid this device cannot currently
 * resolve (deleted, or still quarantined, §9.4) is silently absent from the title list rather
 * than shown as a bare id. */
@Composable
private fun RelationCell(property: Property, row: TableRow, viewModel: PageDatabaseViewModel) {
    val viewOnly = LocalViewOnly.current
    val uids = parseRelationValue(row.values[property.id]?.value)
    var showPicker by remember { mutableStateOf(false) }
    var titles by remember(property.id, row.page.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(uids) { titles = viewModel.resolveRelatedTitles(uids) }
    Text(
        if (titles.isEmpty()) "—" else titles.joinToString(", ") { it.second },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickableRow { if (!viewOnly) showPicker = true },
    )
    if (showPicker) {
        RelationPickerSheet(
            property = property,
            selectedUids = uids,
            viewModel = viewModel,
            onDismiss = { showPicker = false },
            onToggle = { uid -> viewModel.setRelationValue(property, row.page, if (uid in uids) uids - uid else uids + uid) },
        )
    }
}

/** A checklist of the target database's rows, matching [PropertyType.MULTI_SELECT]'s own
 * dropdown-with-checkmarks shape — no search field, since the same "cheap enough at personal
 * scale" reasoning this codebase already applies elsewhere (e.g. [PageDao.findRootByTitle])
 * applies here too. Loaded once per open rather than kept live, the same shape
 * [relationTargetOptions][PageDatabaseViewModel.relationTargetOptions]'s caller already uses. */
@Composable
private fun RelationPickerSheet(
    property: Property,
    selectedUids: Set<String>,
    viewModel: PageDatabaseViewModel,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
) {
    var candidates by remember { mutableStateOf<List<Page>>(emptyList()) }
    LaunchedEffect(property.id) { candidates = viewModel.relationCandidateRows(property) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Relate to", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            if (candidates.isEmpty()) {
                Text("No rows in the related database yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickableRow { onToggle(candidate.uid) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = candidate.uid in selectedUids, onCheckedChange = { onToggle(candidate.uid) })
                        Text(candidate.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** §5.4/DB2 — a rollup cell has no tap-to-edit interaction at all: "compute on read" means
 * there is nothing stored here to edit, only the underlying relation, which this cell does not
 * even hold a reference to editing. It resolves fresh whenever the row's own values change,
 * since a rollup can shift with no edit to this cell at all — the related row's own value
 * moving is enough. */
@Composable
private fun ComputedCell(property: Property, row: TableRow, viewModel: PageDatabaseViewModel) {
    var display by remember(property.id, row.page.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(property.id, row.values) { display = viewModel.computeRollupValue(property, row) }
    Text(display ?: "—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IntervalPickerDialog(onDismiss: () -> Unit, onPick: (Int, IntervalUnit) -> Unit) {
    var countText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(IntervalUnit.WEEK) }
    var showUnitMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Repeat every", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = countText,
                    onValueChange = { countText = it.filter(Char::isDigit) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.width(48.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box {
                    TextButton(onClick = { showUnitMenu = true }) { Text(unit.name.lowercase() + "(s)") }
                    DropdownMenu(expanded = showUnitMenu, onDismissRequest = { showUnitMenu = false }) {
                        IntervalUnit.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.name.lowercase()) }, onClick = { unit = option; showUnitMenu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { countText.toIntOrNull()?.let { onPick(it, unit) } }) { Text("Save") }
        }
    }
}

@Composable
private fun AddPropertySheet(
    existingProperties: List<Property>,
    targetDatabases: List<Pair<Page, Long>>,
    viewModel: PageDatabaseViewModel,
    onDismiss: () -> Unit,
    onAdd: (String, PropertyType, String?) -> Unit,
    onAddRelation: (String, Long) -> Unit,
    onAddRollup: (String, Long, Long?, RollupAggregation) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PropertyType.TEXT) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var optionsText by remember { mutableStateOf("") }
    var showTargetMenu by remember { mutableStateOf(false) }
    var selectedTarget by remember(targetDatabases) { mutableStateOf(targetDatabases.firstOrNull()) }
    // §5.4/DB2 — a rollup can only aggregate across a relation that already exists on this
    // database; there is no "and also create the relation" shortcut here, the same way
    // creating a relation offers no "and also create the database" shortcut.
    val relationProperties = existingProperties.filter { it.type == PropertyType.RELATION }
    var showRelationMenu by remember { mutableStateOf(false) }
    var selectedRelationProperty by remember(relationProperties) { mutableStateOf(relationProperties.firstOrNull()) }
    var showAggregationMenu by remember { mutableStateOf(false) }
    var aggregation by remember { mutableStateOf(RollupAggregation.COUNT) }
    var rollupTargetProperties by remember { mutableStateOf<List<Property>>(emptyList()) }
    var showRollupTargetMenu by remember { mutableStateOf(false) }
    var selectedRollupTarget by remember(rollupTargetProperties) { mutableStateOf(rollupTargetProperties.firstOrNull()) }
    LaunchedEffect(selectedRelationProperty) {
        rollupTargetProperties = selectedRelationProperty?.let { viewModel.relationCandidateProperties(it) }.orEmpty()
    }
    // §5.2.2 — INTERVAL is never offered here; it's only ever created inside the
    // recurrence-binding flow, to avoid a second, uglier way to represent a plain number.
    val offeredTypes = PropertyType.entries.filter { it != PropertyType.INTERVAL }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("New property", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Box {
                TextButton(onClick = { showTypeMenu = true }) { Text("Type: ${type.name.lowercase()}") }
                DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                    offeredTypes.forEach { option ->
                        DropdownMenuItem(text = { Text(option.name.lowercase()) }, onClick = { type = option; showTypeMenu = false })
                    }
                }
            }
            if (type == PropertyType.SELECT || type == PropertyType.MULTI_SELECT) {
                Text("Options (comma-separated)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                BasicTextField(
                    value = optionsText,
                    onValueChange = { optionsText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            if (type == PropertyType.RELATION) {
                Text("Related database", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                if (targetDatabases.isEmpty()) {
                    Text("No other databases yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Box {
                        TextButton(onClick = { showTargetMenu = true }) { Text(selectedTarget?.first?.title ?: "Choose…") }
                        DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
                            targetDatabases.forEach { option ->
                                DropdownMenuItem(text = { Text(option.first.title) }, onClick = { selectedTarget = option; showTargetMenu = false })
                            }
                        }
                    }
                }
            }
            if (type == PropertyType.COMPUTED) {
                Text("Relation to aggregate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                if (relationProperties.isEmpty()) {
                    Text("No relation property yet — add one first", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Box {
                        TextButton(onClick = { showRelationMenu = true }) { Text(selectedRelationProperty?.name ?: "Choose…") }
                        DropdownMenu(expanded = showRelationMenu, onDismissRequest = { showRelationMenu = false }) {
                            relationProperties.forEach { option ->
                                DropdownMenuItem(text = { Text(option.name) }, onClick = { selectedRelationProperty = option; showRelationMenu = false })
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { showAggregationMenu = true }) { Text("Aggregation: ${aggregation.name.lowercase()}") }
                        DropdownMenu(expanded = showAggregationMenu, onDismissRequest = { showAggregationMenu = false }) {
                            RollupAggregation.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.name.lowercase()) }, onClick = { aggregation = option; showAggregationMenu = false })
                            }
                        }
                    }
                    if (aggregation != RollupAggregation.COUNT) {
                        Text("Property to read from each related row", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        if (rollupTargetProperties.isEmpty()) {
                            Text("The related database has no properties yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            Box {
                                TextButton(onClick = { showRollupTargetMenu = true }) { Text(selectedRollupTarget?.name ?: "Choose…") }
                                DropdownMenu(expanded = showRollupTargetMenu, onDismissRequest = { showRollupTargetMenu = false }) {
                                    rollupTargetProperties.forEach { option ->
                                        DropdownMenuItem(text = { Text(option.name) }, onClick = { selectedRollupTarget = option; showRollupTargetMenu = false })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                when (type) {
                    PropertyType.RELATION -> selectedTarget?.let { (_, targetDatabaseId) -> onAddRelation(name, targetDatabaseId) }
                    PropertyType.COMPUTED -> selectedRelationProperty?.let { relationProperty ->
                        val targetId = if (aggregation == RollupAggregation.COUNT) null else selectedRollupTarget?.id
                        onAddRollup(name, relationProperty.id, targetId, aggregation)
                    }
                    else -> {
                        val config = if (type == PropertyType.SELECT || type == PropertyType.MULTI_SELECT) {
                            optionsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
                        } else null
                        onAdd(name, type, config)
                    }
                }
            }) { Text("Add") }
        }
    }
}

// Not private — reused verbatim by Settings' post-Notion-import binding step
// (com.tendril.app.ui.settings.NotionImportSection, a different Gradle module since Milestone
// 3), the same "reuse the property-binding step from §5.2/§5.2.1, not new surface area" §7.3.6
// already calls for.
@Composable
fun EnableSyncSheet(
    properties: List<Property>,
    rows: List<Page>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?, Long?, List<Long>) -> Unit,
) {
    val checkboxProps = properties.filter { it.type == PropertyType.CHECKBOX }
    val dateProps = properties.filter { it.type == PropertyType.DATE }
    val intervalProps = properties.filter { it.type == PropertyType.INTERVAL }

    var donePropertyId by remember { mutableStateOf(checkboxProps.firstOrNull()?.id) }
    var deadlinePropertyId by remember { mutableStateOf<Long?>(null) }
    var recurrencePropertyId by remember { mutableStateOf<Long?>(null) }
    var selectedRowIds by remember { mutableStateOf(rows.map { it.id }.toSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f)) {
            Text("Sync to Tasks", style = MaterialTheme.typography.titleMedium)
            Text(
                "Every row becomes its own linked Task. Pick which property means Done — required — and optionally Deadline and Recurrence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (checkboxProps.isEmpty()) {
                Text("Add a checkbox property first — Sync to Tasks needs one to bind as Done.", style = MaterialTheme.typography.bodyMedium)
            } else {
                BindingPicker("Done (required)", checkboxProps, donePropertyId) { donePropertyId = it }
                BindingPicker("Deadline (optional)", dateProps, deadlinePropertyId, allowNone = true) { deadlinePropertyId = it }
                BindingPicker("Recurrence (optional)", intervalProps, recurrencePropertyId, allowNone = true) { recurrencePropertyId = it }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rows to sync (${selectedRowIds.size}/${rows.size})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedRowIds = if (selectedRowIds.size == rows.size) emptySet() else rows.map { it.id }.toSet() }) {
                    Text(if (selectedRowIds.size == rows.size) "Select none" else "Select all")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(rows, key = { it.id }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickableRow {
                            selectedRowIds = if (row.id in selectedRowIds) selectedRowIds - row.id else selectedRowIds + row.id
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = row.id in selectedRowIds, onCheckedChange = { checked ->
                            selectedRowIds = if (checked) selectedRowIds + row.id else selectedRowIds - row.id
                        })
                        Text(row.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TextButton(
                enabled = donePropertyId != null,
                onClick = { donePropertyId?.let { onConfirm(it, deadlinePropertyId, recurrencePropertyId, selectedRowIds.toList()) } },
            ) { Text("Turn on") }
        }
    }
}

@Composable
private fun BindingPicker(label: String, options: List<Property>, selectedId: Long?, allowNone: Boolean = false, onSelect: (Long?) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val selectedName = options.find { it.id == selectedId }?.name ?: if (allowNone) "None" else "Pick one"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { showMenu = true }, enabled = options.isNotEmpty() || allowNone) { Text(selectedName) }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (allowNone) DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); showMenu = false })
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.name) }, onClick = { onSelect(option.id); showMenu = false })
                }
            }
        }
    }
}

@Composable
private fun DisableSyncConfirm(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn off Sync to Tasks?") },
        text = { Text("This database's linked recurring Tasks will move to Trash — restorable from there, not deleted outright.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Turn off") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
