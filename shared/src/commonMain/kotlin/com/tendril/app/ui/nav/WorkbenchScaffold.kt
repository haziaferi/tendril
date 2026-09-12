package com.tendril.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.pages.LocalViewOnly
import com.tendril.app.ui.canvas.CanvasScreen
import com.tendril.app.ui.pages.PageDatabaseScreen
import com.tendril.app.ui.pages.PageDetailScreen
import com.tendril.app.ui.pages.PagesScreen
import com.tendril.app.ui.track.RunningTimerBar
import com.tendril.app.ui.review.ReviewScreen
import org.jetbrains.compose.resources.stringResource

/**
 * The Workbench nav shell (§2.1/§2.2): persistent bottom tabs across five destinations.
 *
 * Milestone 3 (tendril-windows-spec.md §6 step 3) — moved here so it renders on both Android and
 * desktop. Two Android-only capabilities that have no desktop equivalent (App Lock's
 * `BiometricPrompt` unlock, and the checkbox-only lock-screen-bypass window flags) are threaded
 * in as nullable callbacks rather than `expect`/`actual`, since only one platform ever supplies
 * a real implementation. The four screens this pass doesn't port (Calendar/Tasks&Habits/Road
 * Map/Settings) and the Canvas page kind are injected as composable slots so this file never
 * references an Android-only screen directly — Android's call site passes the real screens,
 * `Tendril windows`'s passes a placeholder for each (see `NotAvailableOnDesktop`).
 */
@Composable
fun WorkbenchScaffold(
    core: WorkbenchCore,
    navState: WorkbenchNavState = remember { WorkbenchNavState() },
    onCheckboxOnlyWindowFlags: ((active: Boolean) -> Unit)? = null,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
    calendarContent: @Composable (onOpenPage: (Long) -> Unit) -> Unit,
    tasksHabitsContent: @Composable (onOpenReview: () -> Unit) -> Unit,
    roadMapContent: @Composable (onOpenPage: (Long) -> Unit) -> Unit,
    settingsContent: @Composable () -> Unit,
) {
    val viewOnly by core.viewLockState.viewOnly.collectAsState()
    val checkboxOnlyPageId by core.checkboxOnlyState.activePageId.collectAsState()
    val route = navState.current
    val currentPageId = (route as? WorkbenchRoute.PageDetail)?.pageId

    LaunchedEffect(checkboxOnlyPageId, currentPageId) {
        // Navigating away (or never having been on that page in the first place) reverts
        // automatically — no unlock required for this path, only for the explicit "turn it
        // off while still here" affordance in PageDetailScreen's own menu.
        if (checkboxOnlyPageId != null && checkboxOnlyPageId != currentPageId) {
            core.checkboxOnlyState.deactivate()
        }
    }
    // The single condition the whole mode turns on: this page, showing now, over the keyguard.
    val bypassingKeyguard = checkboxOnlyPageId != null && checkboxOnlyPageId == currentPageId
    LaunchedEffect(bypassingKeyguard) {
        onCheckboxOnlyWindowFlags?.invoke(bypassingKeyguard)
    }
    // The flags are window-level, so they outlive this composable. Without this, leaving the
    // scaffold while checkbox-only was active (App Lock engaging and swapping in the lock
    // screen is the reachable case) left `showWhenLocked` set on the Activity — and the next
    // thing drawn over the keyguard would have been whatever replaced this.
    DisposableEffect(Unit) {
        onDispose { onCheckboxOnlyWindowFlags?.invoke(false) }
    }

    CompositionLocalProvider(LocalViewOnly provides viewOnly) {
        Scaffold(
            // No topBar here — every destination owns its own TopAppBar and handles the
            // top status-bar/cutout inset itself. Leaving Scaffold's default contentWindowInsets
            // (safeDrawing, top included) would apply that same top inset a second time on top
            // of what each screen's own TopAppBar already consumes, pushing every header down
            // by a redundant status-bar-height gap.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // §3.1.2 / audit 4.3 — no bottom bar while the keyguard is being bypassed. It used
            // to render regardless, so a tap on Settings navigated there *over the lock screen*;
            // the LaunchedEffect above deactivates on a page change, but it runs after that frame
            // has already been drawn, which is exactly one frame of Settings too many. Removing
            // the control removes the path, rather than racing it.
            // §0.6.5 / step 7c — the running timer rides above the tabs: the one place every
            // route on both platforms shares, since no screen's own TopAppBar is.
            bottomBar = { if (!bypassingKeyguard) Column { RunningTimerBar(core); WorkbenchBottomBar(navState) } },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (val current = route) {
                    is WorkbenchRoute.TabRoot -> when (current.tab) {
                        WorkbenchDestination.PAGES -> PagesScreen(core = core, onOpenPage = navState::openPage)
                        WorkbenchDestination.CALENDAR -> calendarContent(navState::openPage)
                        WorkbenchDestination.TASKS_HABITS -> tasksHabitsContent(navState::openReview)
                        WorkbenchDestination.ROAD_MAP -> roadMapContent(navState::openPage)
                        WorkbenchDestination.SETTINGS -> settingsContent()
                    }
                    is WorkbenchRoute.Review -> ReviewScreen(core = core, onBack = { navState.back() }, onOpenPage = navState::openPage)
                    is WorkbenchRoute.PageDetail -> {
                        // A Database page (§5.1) gets the Table view; every other page (including a
                        // Database's own Row, which is `kind = PAGE` with `databaseId` set) gets the
                        // ordinary block editor — routing branches on `kind` alone, not `databaseId`.
                        val page by core.database.pageDao().observeById(current.pageId).collectAsState(initial = null)
                        when (page?.kind) {
                            PageKind.DATABASE -> PageDatabaseScreen(
                                core = core,
                                pageId = current.pageId,
                                onBack = { navState.back() },
                                onOpenPage = navState::openPage,
                            )
                            PageKind.CANVAS -> CanvasScreen(
                                core = core,
                                pageId = current.pageId,
                                onBack = { navState.back() },
                                onOpenPage = navState::openPage,
                            )
                            else -> PageDetailScreen(
                                core = core,
                                pageId = current.pageId,
                                onBack = { navState.back() },
                                onOpenPage = navState::openPage,
                                onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchBottomBar(navState: WorkbenchNavState) {
    val currentTab = navState.currentTab
    NavigationBar {
        WorkbenchDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentTab == destination,
                onClick = { navState.switchTab(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
