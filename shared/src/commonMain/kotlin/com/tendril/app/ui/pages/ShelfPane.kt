package com.tendril.app.ui.pages

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tendril.app.domain.journal.displayTitle
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.nav.PageRoute
import com.tendril.app.ui.nav.PaneChrome
import com.tendril.app.ui.nav.ShellTopBar
import com.tendril.app.ui.nav.WorkbenchNavState
import com.tendril.app.ui.roadmap.DepthChips
import com.tendril.app.ui.roadmap.RoadMapNeighbourhood
import com.tendril.app.ui.theme.heading

/**
 * B§13.4 14h·1 — the shelf: the pane at the workspace's right edge, [widthDp] wide (the
 * remembered width clamped to 45 % of the workspace, [ShelfState.effectiveWidthDp]), a drag
 * handle on its left edge mirroring the tree's. One header rule (`docs/critiques/shelf-mock.md`
 * #1): a page in the shelf uses **its own bar** as the header — the kind's glyph in the leading
 * slot, `⇄` and `×` before its `···` — so there is one bar at one height across the three panes;
 * only the graph, which has no bar of its own, gets one drawn here at the same height.
 *
 * Links inside the shelf's page open in the shelf ([PageRoute]'s `onOpenPage`); Ctrl+F stays the
 * main pane's; Escape is the main pane's back — the shelf closes on `×` and Ctrl+Shift+\.
 */
@Composable
internal fun ShelfPane(
    core: WorkbenchCore,
    navState: WorkbenchNavState,
    shelfState: ShelfState,
    shown: Shelf,
    openPageId: Long,
    /** Today's Journal page, once `PagesViewModel.openJournal` has found or made it; null until then. */
    journalPageId: Long?,
    widthDp: Int,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)?,
) {
    val density = LocalDensity.current
    var dragStartWidth by remember { mutableStateOf(widthDp) }
    var dragDeltaPx by remember { mutableStateOf(0f) }
    Box(modifier = Modifier.fillMaxHeight().zIndex(1f)) {
        Column(modifier = Modifier.width(widthDp.dp).fillMaxHeight()) {
            when (shown) {
                is Shelf.Page, Shelf.Journal -> {
                    val pageId = if (shown is Shelf.Page) shown.pageId else journalPageId
                    if (pageId == null) {
                        // A beat while the day's page is found or made; the header keeps the shelf's shape.
                        ShellTopBar(
                            title = { Text("Today's Journal", style = MaterialTheme.typography.heading) },
                            navigationIcon = { ShelfGlyph(Icons.Outlined.MenuBook) },
                            actions = { CloseShelfButton(shelfState) },
                        )
                    } else {
                        ShelfPage(core, navState, shelfState, pageId, openPageId, journal = shown is Shelf.Journal, onCheckboxOnlyUnlockRequest)
                    }
                }
                Shelf.Graph -> ShelfGraph(core, navState, shelfState, openPageId)
            }
        }
        // The tree's handle, mirrored: astride the left hairline, dragging left widens.
        VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterStart))
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -HANDLE_WIDTH / 2)
                .width(HANDLE_WIDTH)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(shelfState) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStartWidth = widthDp; dragDeltaPx = 0f },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragDeltaPx += delta
                            shelfState.width.resizeTo(dragStartWidth - with(density) { dragDeltaPx.toDp() }.value.toInt())
                        },
                    )
                },
        )
    }
}

/** A page (or today's Journal) in the shelf: the same [PageRoute] as the main pane, with the shelf's chrome on its bar. */
@Composable
private fun ShelfPage(
    core: WorkbenchCore,
    navState: WorkbenchNavState,
    shelfState: ShelfState,
    pageId: Long,
    openPageId: Long,
    journal: Boolean,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)?,
) {
    val page by core.database.pageDao().observeById(pageId).collectAsState(initial = null)
    // The page trashed from anywhere but this pane (the tree, the main pane, a pop-out): the shelf
    // closes rather than keep a trashed page on screen (found on the pop-out walk).
    LaunchedEffect(page?.deletedAt) { if (page?.deletedAt != null) shelfState.close() }
    val glyph = when {
        journal -> Icons.Outlined.MenuBook
        page?.kind == PageKind.DATABASE -> Icons.Filled.TableChart
        page?.kind == PageKind.CANVAS -> Icons.Filled.Dashboard
        else -> Icons.Outlined.Description
    }
    val chrome = remember(glyph, pageId, openPageId) {
        PaneChrome(
            leading = { ShelfGlyph(glyph) },
            actions = {
                IconButton(onClick = { navState.showPage(shelfState.swap(openPageId, pageId)) }, modifier = Modifier.size(SHELF_BUTTON)) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap panes", modifier = Modifier.size(20.dp))
                }
                CloseShelfButton(shelfState)
            },
            menuItems = {},
            // The page trashed from inside the shelf: the shelf closes, the main pane stays.
            onClosed = shelfState::close,
            compact = true,
        )
    }
    PageRoute(
        core = core,
        pageId = pageId,
        onBack = null,
        navState = navState,
        onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
        paneChrome = chrome,
        onOpenPage = { shelfState.open(Shelf.Page(it)) },
        findRequest = 0,
    )
}

/** The Road Map around the main page: a header at the bar's height (glyph, title, the depth chips, *Open in Road Map*, `×`), then the canvas. */
@Composable
private fun ShelfGraph(core: WorkbenchCore, navState: WorkbenchNavState, shelfState: ShelfState, openPageId: Long) {
    val page by core.database.pageDao().observeById(openPageId).collectAsState(initial = null)
    var depth by remember { mutableStateOf(1) }
    ShellTopBar(
        title = {
            Text(
                "Around " + (page?.title?.let { displayTitle(it) }?.ifBlank { "Untitled" } ?: ""),
                style = MaterialTheme.typography.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = { ShelfGlyph(Icons.Outlined.AccountTree) },
        actions = {
            DepthChips(depth = depth, onDepthChange = { depth = it })
            IconButton(onClick = { navState.showOnRoadMap(openPageId) }, modifier = Modifier.size(SHELF_BUTTON)) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Open in Road Map", modifier = Modifier.size(20.dp))
            }
            CloseShelfButton(shelfState)
        },
    )
    RoadMapNeighbourhood(
        core = core,
        pageId = openPageId,
        depth = depth,
        onOpenHere = { shelfState.open(Shelf.Page(it)) },
        onOpenInMain = { navState.showPage(it) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The kind's glyph in the leading slot, where a screen's back arrow would be: a mark, not a button (the pop-out window's bar uses it too). */
@Composable
fun ShelfGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, end = 2.dp).size(20.dp))
}

@Composable
private fun CloseShelfButton(shelfState: ShelfState) {
    IconButton(onClick = shelfState::close, modifier = Modifier.size(SHELF_BUTTON)) { Icon(Icons.Filled.Close, contentDescription = "Close shelf (Ctrl+Shift+\\)", modifier = Modifier.size(20.dp)) }
}

private val HANDLE_WIDTH = 12.dp
/** The shelf's own buttons: 32 dp with a 20 dp glyph — the bar is narrow, and the page's `···` keeps its 48. */
private val SHELF_BUTTON = 32.dp
