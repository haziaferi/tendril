package com.tendril.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.canvas.CanvasScreen
import com.tendril.app.ui.pages.PageDatabaseScreen
import com.tendril.app.ui.pages.PageDetailScreen

/**
 * One page, drawn by the screen its kind calls for — the scaffold's route branch since
 * Milestone 3, extracted in 14c because the Pages workspace draws the same three screens in its
 * right pane. A Database page (§5.1) gets the Table view; every other page (including a
 * Database's own Row, which is `kind = PAGE` with `databaseId` set) gets the ordinary block
 * editor — routing branches on `kind` alone, not `databaseId`.
 *
 * [onBack] null means "no back arrow": the page is a pane beside the tree, and Escape closes it.
 * 14h·1 — the shelf passes [onOpenPage] (a link inside the shelf opens in the shelf, never on the
 * main stack) and `findRequest = 0` (Ctrl+F is the main pane's); both default to the nav state.
 */
@Composable
fun PageRoute(
    core: WorkbenchCore,
    pageId: Long,
    onBack: (() -> Unit)?,
    navState: WorkbenchNavState,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)?,
    paneChrome: PaneChrome? = null,
    onOpenPage: ((Long) -> Unit)? = null,
    findRequest: Int? = null,
) {
    val page by core.database.pageDao().observeById(pageId).collectAsState(initial = null)
    val open = onOpenPage ?: navState::openPage
    when (page?.kind) {
        PageKind.DATABASE -> PageDatabaseScreen(
            core = core,
            pageId = pageId,
            onBack = onBack,
            onOpenPage = open,
            paneChrome = paneChrome,
        )
        PageKind.CANVAS -> CanvasScreen(
            core = core,
            pageId = pageId,
            onBack = onBack,
            onOpenPage = open,
            paneChrome = paneChrome,
        )
        else -> PageDetailScreen(
            core = core,
            pageId = pageId,
            onBack = onBack,
            onOpenPage = open,
            onShowOnRoadMap = navState::showOnRoadMap,
            onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
            paneChrome = paneChrome,
            findRequest = findRequest ?: navState.findRequested,
        )
    }
}
