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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.empty_pages_cta
import com.tendril.app.generated.resources.empty_pages_message
import com.tendril.app.generated.resources.empty_trash_message
import com.tendril.app.generated.resources.nav_pages
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun PagesScreen(core: WorkbenchCore, onOpenPage: (Long) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PagesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PagesViewModel(
                    core.database.pageDao(),
                    core.database.pageDatabaseDao(),
                    core.database.propertyDao(),
                    core.database.pageFtsDao(),
                    core.database.tagDao(),
                    core.purgeRegistry,
                    core.databaseSyncManager,
                    core.templateManager,
                    core.viewLockState,
                )
            }
        }
    )
    val pages by viewModel.filteredPages.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTagIds by viewModel.selectedTagIds.collectAsState()
    val viewOnly by viewModel.viewOnly.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showNewSheet by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showJournalMenu by remember { mutableStateOf(false) }
    var showJournalDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_pages)) },
                actions = {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search pages")
                    }
                    Box {
                        IconButton(onClick = { showJournalMenu = true }) {
                            Icon(Icons.Filled.Book, contentDescription = "Journal")
                        }
                        DropdownMenu(expanded = showJournalMenu, onDismissRequest = { showJournalMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Today's journal") },
                                onClick = { showJournalMenu = false; viewModel.openJournal(java.time.LocalDate.now(), onOpenPage) },
                            )
                            DropdownMenuItem(
                                text = { Text("Pick a date…") },
                                onClick = { showJournalMenu = false; showJournalDatePicker = true },
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.setViewOnly(!viewOnly) }) {
                        Icon(
                            imageVector = if (viewOnly) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (viewOnly) "Turn off View-Only" else "Turn on View-Only",
                        )
                    }
                    IconButton(onClick = { showTrash = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "More")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!viewOnly) {
                FloatingActionButton(onClick = { showNewSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.empty_pages_cta))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = { viewModel.toggleTagFilter(tag.id) },
                            label = { Text(tag.name, maxLines = 1) },
                        )
                    }
                }
            }
            if (pages.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Description,
                    message = stringResource(Res.string.empty_pages_message),
                    ctaLabel = if (viewOnly) null else stringResource(Res.string.empty_pages_cta),
                    onCta = if (viewOnly) null else { { showNewSheet = true } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(pages, key = { it.id }) { page ->
                        PageCard(page = page, onClick = { onOpenPage(page.id) })
                    }
                }
            }
        }
    }

    if (showJournalDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showJournalDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = datePickerMillisToLocalDate(millis)
                        viewModel.openJournal(date, onOpenPage)
                    }
                    showJournalDatePicker = false
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { showJournalDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showNewSheet) {
        val templates by viewModel.templates.collectAsState()
        NewPageSheet(
            templates = templates,
            onDismiss = { showNewSheet = false },
            onBlankPage = { title -> viewModel.createBlankPage(title) { showNewSheet = false; onOpenPage(it) } },
            onBlankDatabase = { title -> viewModel.createDatabase(title, asToDoDatabase = false) { showNewSheet = false; onOpenPage(it) } },
            onToDoDatabase = { title -> viewModel.createDatabase(title, asToDoDatabase = true) { showNewSheet = false; onOpenPage(it) } },
            onCanvas = { title -> viewModel.createCanvas(title) { showNewSheet = false; onOpenPage(it) } },
            onFromTemplate = { template, title -> viewModel.createFromTemplate(template, title) { showNewSheet = false; onOpenPage(it) } },
        )
    }

    if (showSearch) {
        SearchOverlay(viewModel = viewModel, onDismiss = { showSearch = false }, onOpenPage = { showSearch = false; onOpenPage(it) })
    }

    if (showTrash) {
        TrashSheet(core = core, viewModel = viewModel, onDismiss = { showTrash = false })
    }
}

/** Horizontal row layout — icon, title, meta stacked to the right (§2.2), roughly half the
 * height of a stacked card so more pages are visible without scrolling. */
@Composable
private fun PageCard(page: Page, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Local val, not `page.icon` directly: a nullable property declared in a different
        // module (`:shared`, §12.5) can't be smart-cast across the module boundary.
        val pageIcon = page.icon
        val icon = when {
            pageIcon != null -> null
            page.kind == PageKind.DATABASE -> Icons.Filled.TableChart
            page.kind == PageKind.CANVAS -> Icons.Filled.Dashboard
            else -> Icons.Outlined.Description
        }
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (pageIcon != null) {
                Text(pageIcon, style = MaterialTheme.typography.titleMedium)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(page.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (page.kind) {
                    PageKind.DATABASE -> "Database"
                    PageKind.CANVAS -> "Canvas"
                    PageKind.PAGE -> "Page"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewPageSheet(
    templates: List<Page>,
    onDismiss: () -> Unit,
    onBlankPage: (String) -> Unit,
    onBlankDatabase: (String) -> Unit,
    onToDoDatabase: (String) -> Unit,
    onCanvas: (String) -> Unit,
    onFromTemplate: (Page, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("New", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(16.dp))
            // §3.1.3 — the built-in templates (blank page, blank database, to-do database)
            // listed first, the person's own saved templates after.
            NewOptionRow(Icons.Outlined.Description, "Blank page") { onBlankPage(title) }
            NewOptionRow(Icons.Filled.TableChart, "Blank database") { onBlankDatabase(title) }
            NewOptionRow(Icons.Filled.TableChart, "To-do database") { onToDoDatabase(title) }
            NewOptionRow(Icons.Filled.Dashboard, "Canvas") { onCanvas(title) }
            if (templates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("From template", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                templates.forEach { template ->
                    val icon = if (template.kind == PageKind.DATABASE) Icons.Filled.TableChart else Icons.Outlined.Description
                    NewOptionRow(icon, template.title) { onFromTemplate(template, title) }
                }
            }
        }
    }
}

@Composable
private fun NewOptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SearchOverlay(viewModel: PagesViewModel, onDismiss: () -> Unit, onOpenPage: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close search") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search pages…") },
                    keyboardActions = KeyboardActions(onSearch = { viewModel.onSearchQueryChange(query) }),
                )
            }
            if (query.isNotBlank() && results.isEmpty()) {
                EmptyState(icon = Icons.Filled.Search, message = "No pages match '$query'", modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn {
                    items(results, key = { it.pageId }) { hit ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenPage(hit.pageId) }.padding(16.dp),
                        ) {
                            Text(hit.snippet, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** §5.5.1 — unified Trash for Page/Row (a Row is a Page with `databaseId` set, §5.1, so one
 * list and one query already cover both without a separate mechanism). */
@Composable
private fun TrashSheet(core: WorkbenchCore, viewModel: PagesViewModel, onDismiss: () -> Unit) {
    val pages by core.database.pageDao().observeTrash().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteForever by remember { mutableStateOf<List<Long>?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // A fraction of the current screen's height, not a flat dp figure — stays
        // proportionate from small phones to tablets rather than over/under-filling.
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Trash", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (pages.isNotEmpty()) {
                    TextButton(onClick = { selectedIds = if (selectedIds.size == pages.size) emptySet() else pages.map { it.id }.toSet() }) {
                        Text(if (selectedIds.size == pages.size) "Select none" else "Select all")
                    }
                }
            }
            // §5.5.1 — "Selected items get the same Restore / Delete forever actions as a
            // single item, applied to the whole selection at once" — a real Notion gap this
            // deliberately closes.
            if (selectedIds.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    TextButton(onClick = {
                        val ids = selectedIds
                        scope.launch {
                            val now = java.time.Instant.now()
                            ids.forEach { core.database.pageDao().restore(it, now) }
                        }
                        selectedIds = emptySet()
                    }) { Text("Restore (${selectedIds.size})") }
                    TextButton(onClick = { pendingDeleteForever = selectedIds.toList() }) { Text("Delete forever (${selectedIds.size})") }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (pages.isEmpty()) {
                EmptyState(icon = Icons.Filled.Close, message = stringResource(Res.string.empty_trash_message), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn {
                    items(pages, key = { it.id }) { page ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = page.id in selectedIds,
                                onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + page.id else selectedIds - page.id },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(page.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    trashLocation(core, page),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                scope.launch { core.database.pageDao().restore(page.id, java.time.Instant.now()) }
                            }) { Text("Restore") }
                            TextButton(onClick = { pendingDeleteForever = listOf(page.id) }) { Text("Delete forever") }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteForever?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDeleteForever = null },
            title = { Text(if (ids.size == 1) "Delete forever?" else "Permanently delete ${ids.size} items?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteForever(ids)
                    selectedIds = selectedIds - ids.toSet()
                    pendingDeleteForever = null
                }) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteForever = null }) { Text("Cancel") } },
        )
    }
}

/** §5.5.1 — "noting the deleted item's former location (e.g., 'was in: Groceries database')
 * so restoring makes sense out of context." Resolved per-item since it needs a lookup the
 * Trash query itself doesn't carry (the parent/database's title, not just its id). */
@Composable
private fun trashLocation(core: WorkbenchCore, page: Page): String {
    var location by remember(page.id) { mutableStateOf("") }
    LaunchedEffect(page.id) {
        // Local vals, not `page.databaseId`/`page.parentId` directly: nullable properties
        // declared in a different module (`:shared`, §12.5) can't be smart-cast across the
        // module boundary.
        val databaseId = page.databaseId
        val parentId = page.parentId
        location = "was in: " + when {
            databaseId != null -> {
                val database = core.database.pageDatabaseDao().getById(databaseId)
                database?.let { core.database.pageDao().getById(it.pageId) }?.title ?: "a database"
            }
            parentId != null -> core.database.pageDao().getById(parentId)?.title ?: "a page"
            else -> "Pages"
        }
    }
    return location
}
