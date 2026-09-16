@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.ui.input.pointer.isShiftPressed
import com.tendril.app.ui.nav.PopOutHost
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.nav.PageRoute
import com.tendril.app.ui.nav.PaneChrome
import com.tendril.app.ui.nav.TOP_BAR_HEIGHT
import com.tendril.app.ui.components.PointerMenu
import com.tendril.app.ui.components.SubmenuItem
import com.tendril.app.ui.components.onSecondaryClick
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.border
import androidx.compose.ui.backhandler.BackHandler
import com.tendril.app.ui.components.ListKeyState
import com.tendril.app.ui.components.TypeAheadReset
import com.tendril.app.ui.components.keyedTitle
import com.tendril.app.ui.components.keyboardCursorShown
import com.tendril.app.ui.components.listKeyboard
import com.tendril.app.ui.nav.ShellTopBar
import com.tendril.app.ui.nav.WorkbenchNavState
import com.tendril.app.ui.nav.WorkbenchRoute
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.heading

/**
 * B§13.4 14c — the Pages tab on a wide window: the tree beside the open page, the chrome split
 * the way Obsidian and Bear split it (decided 2026-09-15 on `docs/mockups/pages-two-panes.html`,
 * option B): what acts on *pages* — the name, New, search, the journal, the collapse chevron —
 * sits on the tree's 44 dp header; what acts on *this page* — its title, View-Only, its menu with
 * *Trash…* appended — sits on the page's own bar, which loses its back arrow because the tree is
 * beside it. Collapsed (the chevron or Ctrl+\), the page bar's leading slot takes the expand
 * chevron and the tree's search and journal, so nothing is lost. No tab bar over the page: the
 * doubled label the first mock had is gone.
 *
 * A tree click *replaces* the open page ([WorkbenchNavState.showPage]); a link inside a page
 * still pushes, so Escape returns where you came from. At the tab root the pane is the quiet
 * empty state, never a stale page. The tree's width and collapsed flag persist ([PagesTreeState]).
 *
 * 14h·1 — a third pane, the shelf ([ShelfPane]), at the right edge when [ShelfState.content] is
 * set and a page is open: another page, this page's Road Map neighbourhood, or today's Journal.
 * Opened from the page's `···` → *Show beside ▸*, the tree row's *Open beside* or Ctrl+click;
 * closed on `×` or Ctrl+Shift+\; remembered.
 */
@Composable
fun PagesWorkspace(
    core: WorkbenchCore,
    navState: WorkbenchNavState,
    treeState: PagesTreeState,
    shelfState: ShelfState,
    popOuts: PopOutHost?,
    onOpenSwitcher: () -> Unit,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)?,
) {
    PagesHost(core, navState::openPage) { vm, actions, snackbarHostState ->
        val openPageId = (navState.current as? WorkbenchRoute.PageDetail)?.pageId
        // The shelf's Journal is *today's*: the id is resolved here through the same create-or-find
        // the Journal button uses (refused under View-Only when the day has no page yet — the
        // button's message, and the shelf closes rather than sit empty), and re-resolved when the
        // date turns over on a window left open.
        var today by remember { mutableStateOf(LocalDate.now()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); today = LocalDate.now() } }
        var journalPageId by remember { mutableStateOf<Long?>(null) }
        val wantsJournal = shelfState.content == Shelf.Journal
        val scope = rememberCoroutineScope()
        LaunchedEffect(wantsJournal, today) {
            if (wantsJournal) vm.openJournal(
                date = today,
                onRefused = { scope.launch { snackbarHostState.showSnackbar(JOURNAL_LOCKED_MESSAGE) }; shelfState.close() },
                onOpen = { journalPageId = it },
            )
        }
        val shelfShown = shelfState.shown(openPageId, journalPageId)
        // 14h·2 — the tree marks the shelf's page too (a ring and a glyph; the graph marks nothing).
        val shelfPageId: Long? = when (val c = shelfShown) { is Shelf.Page -> c.pageId; Shelf.Journal -> journalPageId; else -> null }
        val paneChrome = remember(treeState.collapsed, actions, popOuts) {
            PaneChrome(
                leading = {
                    if (treeState.collapsed) {
                        IconButton(onClick = treeState::toggle) { Icon(Icons.Filled.ChevronRight, contentDescription = "Show tree (Ctrl+\\)") }
                        IconButton(onClick = onOpenSwitcher) { Icon(Icons.Filled.Search, contentDescription = "Search pages") }
                        JournalButton(actions)
                    }
                },
                actions = { ViewOnlyButton(actions) },
                menuItems = { close ->
                    // 14h·1 — the two things that can sit beside this page, as a submenu (the
                    // 14g·3 pattern); on every kind of page, since the chrome is the workspace's.
                    SubmenuItem(text = { Text("Show beside") }) { closeBeside ->
                        DropdownMenuItem(text = { Text("Road Map around this page") }, onClick = { closeBeside(); close(); shelfState.open(Shelf.Graph) })
                        DropdownMenuItem(text = { Text("Today's Journal") }, onClick = { closeBeside(); close(); shelfState.open(Shelf.Journal) })
                    }
                    // B§13.6 #6 — the page in its own window; the page stays open here too.
                    // Read at click time: the chrome is remembered across pages, so `openPageId` above is stale here.
                    val pageHere = (navState.current as? WorkbenchRoute.PageDetail)?.pageId
                    if (popOuts != null && pageHere != null) {
                        DropdownMenuItem(text = { Text("Open in a window") }, onClick = { close(); popOuts.open(pageHere) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Trash…") }, onClick = { close(); actions.openTrash() })
                },
                onClosed = { navState.back() },
                openBeside = { shelfState.open(Shelf.Page(it)) },
                openInWindow = popOuts?.let { h -> { id: Long -> h.open(id) } },
            )
        }
        // The workspace paints its own ground: the phone's Scaffold used to, and a pane over the
        // window's default grey read as a card.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val workspaceWidthDp = maxWidth.value
            Row(modifier = Modifier.fillMaxSize()) {
                if (!treeState.collapsed) {
                    PagesTreePane(
                        vm, actions, treeState, openPageId, shelfPageId, popOuts, onOpenSwitcher,
                        onOpen = navState::showPage,
                        onOpenBeside = { shelfState.open(Shelf.Page(it)) },
                        onShowOnRoadMap = navState::showOnRoadMap,
                        // Trashing the open page closes the pane to the tab root, as the page's own menu does.
                        onMoveToTrash = { id -> vm.moveToTrash(id); if (id == openPageId) navState.back() },
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (openPageId != null) {
                        PageRoute(
                            core = core,
                            pageId = openPageId,
                            onBack = null,
                            navState = navState,
                            onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
                            paneChrome = paneChrome,
                        )
                    } else {
                        EmptyDetail(paneChrome, onOpenTrash = actions.openTrash)
                    }
                }
                if (shelfShown != null && openPageId != null) {
                    ShelfPane(
                        core = core,
                        navState = navState,
                        shelfState = shelfState,
                        shown = shelfShown,
                        openPageId = openPageId,
                        journalPageId = journalPageId,
                        widthDp = shelfState.effectiveWidthDp(workspaceWidthDp),
                        onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
                    )
                }
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** The tree: header, label chips, rows; a drag handle on its right edge. */
@Composable
private fun PagesTreePane(
    viewModel: PagesViewModel,
    actions: PagesActions,
    treeState: PagesTreeState,
    openPageId: Long?,
    shelfPageId: Long?,
    popOuts: PopOutHost?,
    onOpenSwitcher: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenBeside: (Long) -> Unit,
    onShowOnRoadMap: (Long) -> Unit,
    onMoveToTrash: (Long) -> Unit,
) {
    val pages by viewModel.filteredPages.collectAsState()
    // 14h·1 — Ctrl+click on a row opens it beside (read at the click: the modifiers are the window's).
    val windowInfo = LocalWindowInfo.current
    val parentsWithChildren by viewModel.parentsWithChildren.collectAsState()
    val density = LocalDensity.current
    var dragStartWidth by remember { mutableStateOf(treeState.widthDp) }
    var dragDeltaPx by remember { mutableStateOf(0f) }
    // The tree, with the handle laid over its right edge: 12 dp astride the 1 dp divider, six
    // into each pane, above the page (zIndex) so the page's own pointer handling does not
    // shadow the outer half. No gutter between the panes — the mock's `.handle` overlaps its
    // border the same way. Dragged in the platform's pixels, stored in dp.
    Box(modifier = Modifier.fillMaxHeight().zIndex(1f)) {
        Column(modifier = Modifier.width(treeState.widthDp.dp).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(TREE_HEADER_HEIGHT).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pages", style = MaterialTheme.typography.heading, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (!actions.viewOnly) {
                    IconButton(onClick = actions.newPage, modifier = Modifier.size(TREE_ICON_BUTTON)) { Icon(Icons.Filled.Add, contentDescription = "New page", modifier = Modifier.size(18.dp)) }
                }
                IconButton(onClick = onOpenSwitcher, modifier = Modifier.size(TREE_ICON_BUTTON)) { Icon(Icons.Filled.Search, contentDescription = "Search pages", modifier = Modifier.size(18.dp)) }
                JournalButton(actions, modifier = Modifier.size(TREE_ICON_BUTTON))
                IconButton(onClick = treeState::toggle, modifier = Modifier.size(TREE_ICON_BUTTON)) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Hide tree (Ctrl+\\)", modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LabelFilterRow(viewModel, horizontalPadding = 18.dp, listTopPadding = 6.dp)   // the row's content start: 6 dp margin + 12 dp padding
            // 14e (B§13.6 #8) — the visible rows as one list, in drawn order: a root, then its
            // children while it is expanded. The keyboard walks this list; ↵ shows the page, → and
            // ← expand and collapse a parent (← on a child climbs to its parent).
            val flat = ArrayList<TreeEntry>()
            pages.forEach { page ->
                val hasChildren = page.id in parentsWithChildren
                val expanded = page.id in treeState.expanded
                flat.add(TreeEntry(page, depth = 0, hasChildren = hasChildren, expanded = expanded, parentIndex = -1))
                if (hasChildren && expanded) {
                    val parentIndex = flat.lastIndex
                    val children by viewModel.childrenOf(page.id).collectAsState()
                    children.forEach { child ->
                        flat.add(TreeEntry(child, depth = 1, hasChildren = child.id in parentsWithChildren, expanded = false, parentIndex = parentIndex))
                    }
                }
            }
            val keyState = remember { ListKeyState() }
            TypeAheadReset(keyState)
            BackHandler(enabled = keyState.hasSomethingToClear) { keyState.clear() }
            LazyColumn(
                modifier = Modifier.fillMaxSize().listKeyboard(
                    state = keyState,
                    count = { flat.size },
                    titles = { flat.map { it.page.title } },
                    onOpen = { i -> flat.getOrNull(i)?.let { onOpen(it.page.id) } },
                    onExpand = { i, expand ->
                        val entry = flat.getOrNull(i) ?: return@listKeyboard
                        when {
                            expand && entry.hasChildren && !entry.expanded -> treeState.toggleExpanded(entry.page.id)
                            !expand && entry.expanded -> treeState.toggleExpanded(entry.page.id)
                            !expand && entry.parentIndex >= 0 -> keyState.focused = entry.parentIndex
                        }
                    },
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
            ) {
                itemsIndexed(flat, key = { _, it -> it.page.id }) { index, entry ->
                    TreeRow(entry.page, depth = entry.depth, current = entry.page.id == openPageId, inShelf = entry.page.id == shelfPageId, hasChildren = entry.hasChildren, expanded = entry.expanded,
                        onClick = {
                            keyState.clickedRow(index)
                            val mods = windowInfo.keyboardModifiers
                            when {
                                mods.isCtrlPressed -> onOpenBeside(entry.page.id)
                                mods.isShiftPressed && popOuts != null -> popOuts.open(entry.page.id)   // B§13.6 #6
                                else -> onOpen(entry.page.id)
                            }
                        },
                        onToggle = { if (entry.hasChildren) treeState.toggleExpanded(entry.page.id) },
                        onOpenBeside = { onOpenBeside(entry.page.id) },
                        onOpenInWindow = popOuts?.let { h -> { h.open(entry.page.id) } },
                        inWindow = popOuts?.openPageIds?.contains(entry.page.id) == true,
                        onShowOnRoadMap = { onShowOnRoadMap(entry.page.id) }, onMoveToTrash = if (actions.viewOnly) null else ({ onMoveToTrash(entry.page.id) }),
                        keyFocused = keyState.focused == index && keyboardCursorShown(), typed = keyState.typed)
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterEnd))
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = HANDLE_WIDTH / 2)
                .width(HANDLE_WIDTH)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStartWidth = treeState.widthDp; dragDeltaPx = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragDeltaPx += dragAmount
                            treeState.resizeTo(dragStartWidth + with(density) { dragDeltaPx.toDp() }.value.toInt())
                        },
                    )
                },
        )
    }
}

/**
 * One row of the tree: a chevron where there are children, the kind's icon, the title, and —
 * B§13.4 14d — a `···` that fades in on hover (drawn at alpha 0 otherwise, never laid out away,
 * so the title's width is stable) opening the row's menu; right-click opens the same menu at
 * the pointer. The target is 28 dp with an 18 dp glyph: 23.8 px at the 0.85 minimum scale, the
 * pointer floor the critique measured 16 dp against (`docs/critiques/pages-desktop.md` #2).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    page: Page,
    depth: Int,
    current: Boolean,
    /** 14h·2 — the page is in the shelf: a 1 dp ring and the shelf's glyph, no fill. */
    inShelf: Boolean = false,
    hasChildren: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onOpenBeside: () -> Unit,
    onShowOnRoadMap: () -> Unit,
    onMoveToTrash: (() -> Unit)?,
    /** B§13.6 #6 — the desktop's *Open in a window*; null where there are no windows. */
    onOpenInWindow: (() -> Unit)? = null,
    /** The page is open in a pop-out window: a small ⧉ before the `···`. */
    inWindow: Boolean = false,
    /** 14e — this row is the keyboard cursor: a 2 dp ring, the typed prefix underlined. */
    keyFocused: Boolean = false,
    typed: String = "",
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val background = when {
        current -> MaterialTheme.colorScheme.primaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val colour = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height }) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(TREE_ROW_HEIGHT)
            .background(background, RoundedCornerShape(6.dp))
            .then(
                when {
                    keyFocused -> Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    inShelf -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    else -> Modifier
                }
            )
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = { menuAt = null; menuOpen = true })
            .onSecondaryClick { menuAt = it; menuOpen = true }
            .padding(start = 12.dp + (TREE_INDENT * depth), end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(16.dp).clickable(enabled = hasChildren, onClick = onToggle), contentAlignment = Alignment.Center) {
            if (hasChildren) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
                )
            }
        }
        val pageIcon = page.icon
        if (pageIcon != null) {
            Text(pageIcon, style = MaterialTheme.typography.body)
        } else {
            Icon(
                when (page.kind) {
                    PageKind.DATABASE -> Icons.Filled.TableChart
                    PageKind.CANVAS -> Icons.Filled.Dashboard
                    PageKind.PAGE -> Icons.Outlined.Description
                },
                contentDescription = null,
                tint = if (current) colour else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(keyedTitle(page.title, typed, keyFocused), style = MaterialTheme.typography.body, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (inShelf) {
            Icon(Icons.Outlined.VerticalSplit, contentDescription = "In the shelf", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
        if (inWindow) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = "Open in a window", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
        IconButton(
            onClick = { menuAt = null; menuOpen = true },
            modifier = Modifier.size(TREE_MORE_TARGET).alpha(if (hovered || menuOpen) 1f else 0f),
        ) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
    PointerMenu(expanded = menuOpen, at = menuAt, fallback = IntOffset(0, rowHeightPx), onDismiss = { menuOpen = false }) {
        PageRowMenuItems(onOpen = { menuOpen = false; onClick() }, onShowOnRoadMap = { menuOpen = false; onShowOnRoadMap() },
            onMoveToTrash = onMoveToTrash?.let { f -> { menuOpen = false; f() } }, onOpenBeside = { menuOpen = false; onOpenBeside() },
            onOpenInWindow = onOpenInWindow?.let { f -> { menuOpen = false; f() } })
    }
    }
}

/** The right pane at the tab root: the page bar's chrome (so View-Only and Trash stay reachable), and a quiet line. */
@Composable
private fun EmptyDetail(paneChrome: PaneChrome, onOpenTrash: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        ShellTopBar(
            title = {},
            navigationIcon = paneChrome.leading,
            actions = {
                paneChrome.actions(this)
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Trash…") }, onClick = { showMenu = false; onOpenTrash() })
                    }
                }
            },
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Choose a page from the tree — or press Ctrl+K to find one.",
                style = MaterialTheme.typography.body,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** The page bar's height, so the one hairline runs across both panes (critique pass 1, #3). */
private val TREE_HEADER_HEIGHT = TOP_BAR_HEIGHT

/** One visible row of the tree, as the keyboard and the list both see it. */
private class TreeEntry(val page: Page, val depth: Int, val hasChildren: Boolean, val expanded: Boolean, val parentIndex: Int)
private val TREE_ROW_HEIGHT = 32.dp
private val TREE_INDENT = 18.dp
private val TREE_ICON_BUTTON = 28.dp
private val TREE_MORE_TARGET = 28.dp
private val HANDLE_WIDTH = 12.dp
