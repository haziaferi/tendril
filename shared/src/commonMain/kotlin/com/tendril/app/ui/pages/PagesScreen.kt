@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.labelColours
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
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.tendril.app.ui.components.PointerMenu
import com.tendril.app.ui.components.onSecondaryClick
import com.tendril.app.ui.components.ListKeyState
import com.tendril.app.ui.components.TypeAheadReset
import com.tendril.app.ui.components.keyboardCursorShown
import com.tendril.app.ui.components.keyedTitle
import com.tendril.app.ui.components.listKeyboard
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tendril.app.ui.nav.ShellTopBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.domain.time.relativeTime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.SEARCH_HL_CLOSE
import com.tendril.app.data.page.SEARCH_HL_OPEN
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.empty_pages_cta
import com.tendril.app.generated.resources.empty_pages_message
import com.tendril.app.generated.resources.empty_trash_message
import com.tendril.app.generated.resources.nav_pages
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.TendrilMenuItem
import com.tendril.app.ui.components.keyboardCursorRing

/** §3.1.2 — what a refused `openJournal` says out loud. Names the lock (so the tap does not read
 * as a fault), names what was refused (creating the day, not opening it), and names the way out,
 * which is one tap away in the same top bar. */
internal const val JOURNAL_LOCKED_MESSAGE =
    "Nothing is written for that day yet, and View-Only is on — turn it off to start it."

/**
 * The Pages tab on a phone (and on any window below 840 dp): the list, the FAB, a page pushed
 * full-screen. On a wide window the same tab is [PagesWorkspace] — the tree beside the page —
 * and both are built on [PagesHost], which owns the ViewModel, the New sheet, the journal date
 * picker, the Trash sheet and the snackbar, so the two shapes share one set of actions.
 */
@Composable
fun PagesScreen(core: WorkbenchCore, onOpenPage: (Long) -> Unit, onOpenSwitcher: () -> Unit, onShowOnRoadMap: (Long) -> Unit, modifier: Modifier = Modifier) {
    PagesHost(core, onOpenPage) { vm, actions, snackbarHostState ->
        val pages by vm.filteredPages.collectAsState()
        val viewOnly = actions.viewOnly
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ShellTopBar(
                    title = { Text(stringResource(Res.string.nav_pages)) },
                    actions = {
                        IconButton(onClick = onOpenSwitcher) {
                            Icon(Icons.Filled.Search, contentDescription = "Search pages")
                        }
                        JournalButton(actions)
                        ViewOnlyButton(actions)
                        IconButton(onClick = actions.openTrash) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!viewOnly) {
                    FloatingActionButton(onClick = actions.newPage) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.empty_pages_cta))
                    }
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LabelFilterRow(vm)
                if (pages.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        message = stringResource(Res.string.empty_pages_message),
                        ctaLabel = if (viewOnly) null else stringResource(Res.string.empty_pages_cta),
                        onCta = if (viewOnly) null else actions.newPage,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 14f·1 (B§13.6 #8) — the phone's list answers a hardware keyboard too: ↑ ↓ ↵ opens, type-ahead.
                    val keyState = remember { ListKeyState() }
                    TypeAheadReset(keyState)
                    BackHandler(enabled = keyState.hasSomethingToClear) { keyState.clear() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().listKeyboard(
                            state = keyState,
                            count = { pages.size },
                            titles = { pages.map { it.title } },
                            onOpen = { i -> pages.getOrNull(i)?.let { onOpenPage(it.id) } },
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(pages, key = { _, it -> it.id }) { index, page ->
                            PageCard(
                                page = page,
                                onClick = { keyState.clickedRow(index); onOpenPage(page.id) },
                                onShowOnRoadMap = { onShowOnRoadMap(page.id) },
                                onMoveToTrash = if (viewOnly) null else ({ vm.moveToTrash(page.id) }),
                                keyFocused = keyState.focused == index && keyboardCursorShown(),
                                typed = keyState.typed,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The tab's verbs, handed to whichever shape draws the tab. [viewOnly] is read so a control can hide or grey itself. */
class PagesActions(
    val viewOnly: Boolean,
    val newPage: () -> Unit,
    val openJournalToday: () -> Unit,
    val pickJournalDate: () -> Unit,
    val toggleViewOnly: () -> Unit,
    val openTrash: () -> Unit,
)

@Composable
internal fun rememberPagesViewModel(core: WorkbenchCore): PagesViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            PagesViewModel(
                core.database.pageDao(),
                core.database.pageDatabaseDao(),
                core.database.propertyDao(),
                core.database.pageFtsDao(),
                core.database.labelDao(),
                core.purgeRegistry,
                core.databaseSyncManager,
                core.templateManager,
                core.viewLockState,
                core.pageContentRepository,
                core.database.entryDao(),
                core.resolveEntryUseCase,
            )
        }
    }
)

/**
 * Owns what both shapes of the tab share: the ViewModel, the New sheet, the journal date
 * picker, the Trash sheet, the snackbar the lock speaks through. [content] draws the tab.
 */
@Composable
internal fun PagesHost(
    core: WorkbenchCore,
    onOpenPage: (Long) -> Unit,
    content: @Composable (PagesViewModel, PagesActions, SnackbarHostState) -> Unit,
) {
    val viewModel = rememberPagesViewModel(core)
    val viewOnly by viewModel.viewOnly.collectAsState()
    var showNewSheet by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var showJournalDatePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // §3.1.2 — both Journal entry points route through here rather than calling the ViewModel
    // directly, so the one path that can refuse says so. [PagesViewModel.openJournal] is
    // read-and-write: it navigates to a day already written (reading is not writing, so the lock
    // must not block it) and refuses only the lazy creation of a day nobody has written yet.
    // Under the lock that second case produces no navigation and no error — a dead tap — unless
    // something speaks for it. A message costs a line; a button that appears broken costs trust
    // in the lock itself.
    val openJournalDay: (java.time.LocalDate) -> Unit = { date ->
        viewModel.openJournal(
            date = date,
            onRefused = { scope.launch { snackbarHostState.showSnackbar(JOURNAL_LOCKED_MESSAGE) } },
            onOpen = onOpenPage,
        )
    }
    val actions = remember(viewOnly) {
        PagesActions(
            viewOnly = viewOnly,
            newPage = { showNewSheet = true },
            openJournalToday = { openJournalDay(java.time.LocalDate.now()) },
            pickJournalDate = { showJournalDatePicker = true },
            toggleViewOnly = { viewModel.setViewOnly(!viewOnly) },
            openTrash = { showTrash = true },
        )
    }

    content(viewModel, actions, snackbarHostState)

    if (showJournalDatePicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showJournalDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        openJournalDay(datePickerMillisToLocalDate(millis))
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

    if (showTrash) {
        TrashSheet(core = core, viewModel = viewModel, onDismiss = { showTrash = false })
    }
}

/** The journal button and its two-item menu — today, or a picked date. */
@Composable
internal fun JournalButton(actions: PagesActions, modifier: Modifier = Modifier) {
    var showJournalMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showJournalMenu = true }, modifier = modifier) {
            Icon(Icons.Outlined.MenuBook, contentDescription = "Journal")
        }
        TendrilMenu(expanded = showJournalMenu, onDismissRequest = { showJournalMenu = false }) {
            TendrilMenuItem(
                text = { Text("Today's journal") },
                onClick = { showJournalMenu = false; actions.openJournalToday() },
            )
            TendrilMenuItem(
                text = { Text("Pick a date…") },
                onClick = { showJournalMenu = false; actions.pickJournalDate() },
            )
        }
    }
}

/** The View-Only eye (§3.1.2) — the one control that stays reachable while the lock is on. */
@Composable
internal fun ViewOnlyButton(actions: PagesActions, modifier: Modifier = Modifier) {
    IconButton(onClick = actions.toggleViewOnly, modifier = modifier) {
        Icon(
            imageVector = if (actions.viewOnly) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (actions.viewOnly) "Turn off View-Only" else "Turn on View-Only",
        )
    }
}

/** The label filter chips, when there are labels; a horizontal row on the phone and in the tree alike. */
@Composable
internal fun LabelFilterRow(
    viewModel: PagesViewModel,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    /** What the list below already pads at its top, so the visible gap under the chips is 12 dp too. */
    listTopPadding: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val allLabels by viewModel.allLabels.collectAsState()
    val selectedLabelIds by viewModel.selectedLabelIds.collectAsState()
    val boundLabelIds by viewModel.boundLabelIds.collectAsState()
    if (allLabels.isNotEmpty()) {
        // 14h·2 — 12 dp of *visible* air above and below (measured 14 / 20 px before: the chip's
        // invisible 48 dp target inset made the two unequal); under a pointer the chip is its
        // 32 dp, on Touch it keeps the target. The caller's padding is the row's content start.
        val profile = LocalDensityProfile.current
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (profile.pointer) 32.dp else 48.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = horizontalPadding, end = horizontalPadding, top = if (profile.pointer) 12.dp else 4.dp, bottom = (if (profile.pointer) 12.dp else 4.dp) - listTopPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allLabels.forEach { label ->
                // 14g·2 — the label's stored hue, rendered by the register: the tint at rest, the
                // hue solid when the filter is on (§3.1.6).
                val colours = labelColours(label.color, LocalTendrilPalette.current)
                val selected = label.id in selectedLabelIds
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleLabelFilter(label.id) },
                    label = { Text(label.name, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colours.tint, labelColor = colours.hue, iconColor = colours.hue,
                        selectedContainerColor = colours.hue, selectedLabelColor = colours.onHue, selectedLeadingIconColor = colours.onHue,
                    ),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = colours.hue.copy(alpha = 0.4f)),
                    leadingIcon = if (label.id in boundLabelIds) {
                        { Icon(Icons.Filled.TableChart, contentDescription = "Brings a database's fields", modifier = Modifier.size(14.dp)) }
                    } else null,
                )
            }
        }
        }
    }
}

/** Horizontal row layout — icon, title, meta stacked to the right (§2.2), roughly half the
 * height of a stacked card so more pages are visible without scrolling. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCard(page: Page, onClick: () -> Unit, onShowOnRoadMap: () -> Unit, onMoveToTrash: (() -> Unit)?, keyFocused: Boolean = false, typed: String = "") {
    // B§13.4 14d — the row's menu, on long-press (the finger) or right-click (a mouse on the
    // phone gets it too); no hover `···` here, hover is the tree's.
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableStateOf(0) }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height }) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuAt = null; menuOpen = true })
            .onSecondaryClick { menuAt = it; menuOpen = true }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(Modifier.keyboardCursorRing(keyFocused, 6)),
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
                Text(pageIcon, style = MaterialTheme.typography.heading)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(keyedTitle(page.title, typed, keyFocused), style = MaterialTheme.typography.body)
            // 14h·2 — when it was last edited; the kind is the icon's (`pages-phone.md` #1).
            Text(
                "edited " + relativeTime(page.updatedAt),
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    PointerMenu(expanded = menuOpen, at = menuAt, fallback = IntOffset(0, rowHeightPx), onDismiss = { menuOpen = false }) {
        PageRowMenuItems(onOpen = { menuOpen = false; onClick() }, onShowOnRoadMap = { menuOpen = false; onShowOnRoadMap() },
            onMoveToTrash = onMoveToTrash?.let { f -> { menuOpen = false; f() } })
    }
    }
}

/**
 * B§13.4 14d — the three things a row's menu offers, the same on the tree (hover `···`,
 * right-click) and the phone's list (long-press): open it, see it on the Road Map, trash it.
 * [onMoveToTrash] null under View-Only — the item is greyed, not gone, so the lock is visible.
 */
@Composable
internal fun PageRowMenuItems(onOpen: () -> Unit, onShowOnRoadMap: () -> Unit, onMoveToTrash: (() -> Unit)?, onOpenBeside: (() -> Unit)? = null, onOpenInWindow: (() -> Unit)? = null) {
    TendrilMenuItem(text = { Text("Open") }, onClick = onOpen)
    // 14h·1 — the tree's rows only (the phone has no shelf); Ctrl+click on the row is the same.
    if (onOpenBeside != null) TendrilMenuItem(text = { Text("Open beside") }, onClick = onOpenBeside)
    // B§13.6 #6 — the desktop's rows only; Shift+click on the row is the same.
    if (onOpenInWindow != null) TendrilMenuItem(text = { Text("Open in a window") }, onClick = onOpenInWindow)
    TendrilMenuItem(text = { Text("Show on Road Map") }, onClick = onShowOnRoadMap)
    TendrilMenuItem(text = { Text("Move to Trash") }, onClick = onMoveToTrash ?: {}, enabled = onMoveToTrash != null)
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
    TendrilSheet(title = "New", onDismiss = onDismiss) {
        Column {
            OutlinedTextField(
                textStyle = MaterialTheme.typography.body,
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
                Text("From template", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(label, style = MaterialTheme.typography.body)
    }
}

/** §5.5.1 — unified Trash for Page/Row (a Row is a Page with `databaseId` set, §5.1, so one
 * list and one query already cover both without a separate mechanism).
 *
 * §3.1.2 — the sheet itself opens under View-Only (reading the Trash is reading), but both of
 * its actions are writes that leave this device: Restore rewrites `updatedAt` and so wins the
 * next merge everywhere, and Delete forever records a propagating tombstone. Both therefore go
 * `enabled = false` rather than disappearing — an absent button reads as "there is nothing to
 * restore", a greyed one as "not while the lock is on", and the lock's own toggle is one tap
 * away in the top bar. Both also route through [PagesViewModel] now: the earlier
 * `core.database.pageDao().restore(…)` here in the `onClick` was a write with no ViewModel
 * between it and the DAO, so there was no gate for the guard to live in. */
@Composable
private fun TrashSheet(core: WorkbenchCore, viewModel: PagesViewModel, onDismiss: () -> Unit) {
    val pages by core.database.pageDao().observeTrash().collectAsState(initial = emptyList())
    val viewOnly by viewModel.viewOnly.collectAsState()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteForever by remember { mutableStateOf<List<Long>?>(null) }

    TendrilSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.6f)) {
        // A fraction of the current screen's height, not a flat dp figure — stays
        // proportionate from small phones to tablets rather than over/under-filling.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Trash", style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f))
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
                    TextButton(
                        enabled = !viewOnly,
                        onClick = {
                            viewModel.restore(selectedIds.toList())
                            selectedIds = emptySet()
                        },
                    ) { Text("Restore (${selectedIds.size})") }
                    TextButton(
                        enabled = !viewOnly,
                        onClick = { pendingDeleteForever = selectedIds.toList() },
                    ) { Text("Delete forever (${selectedIds.size})") }
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
                                Text(page.title, style = MaterialTheme.typography.body)
                                Text(
                                    trashLocation(core, page),
                                    style = MaterialTheme.typography.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                enabled = !viewOnly,
                                onClick = { viewModel.restore(listOf(page.id)) },
                            ) { Text("Restore") }
                            TextButton(
                                enabled = !viewOnly,
                                onClick = { pendingDeleteForever = listOf(page.id) },
                            ) { Text("Delete forever") }
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
