@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.pages.LocalViewOnly
import com.tendril.app.ui.canvas.CanvasScreen
import com.tendril.app.ui.pages.PageDatabaseScreen
import com.tendril.app.ui.pages.PageDetailScreen
import com.tendril.app.ui.pages.PagesScreen
import com.tendril.app.ui.track.RunningTimerBar
import com.tendril.app.ui.track.RunningTimerRailFoot
import com.tendril.app.ui.review.ReviewScreen
import com.tendril.app.ui.roadmap.RoadMapScreen
import com.tendril.app.ui.switcher.QuickSwitcher
import com.tendril.app.ui.switcher.SwitcherState
import com.tendril.app.domain.SwitcherCommand
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.ui.pages.PagesViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Workbench nav shell (§2.1/§2.2): the five destinations as a bottom bar, or — B§13.4 14a,
 * from 840 dp of width ([shellLayoutFor]) — as a left rail with the running timer at its foot.
 * Both drawn by [ShellRail]/[ShellBottomBar] as the desktop mock draws them, not by Material's `Scaffold`; the
 * routes, the switcher and Back are the same in both forms; only where the five buttons stand
 * changes.
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
    /** §3.1.7 — the quick switcher's open flag; the desktop's Ctrl+K toggles it from `Main.kt`. */
    switcher: SwitcherState = remember { SwitcherState() },
    onCheckboxOnlyWindowFlags: ((active: Boolean) -> Unit)? = null,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
    calendarContent: @Composable (onOpenPage: (Long) -> Unit) -> Unit,
    tasksHabitsContent: @Composable (onOpenReview: () -> Unit) -> Unit,
    settingsContent: @Composable () -> Unit,
) {
    // Back pops the stack on both platforms: Android's gesture and desktop's Escape reach the
    // same Compose Multiplatform dispatcher (§0.10 item 10). Was Android-only until Review
    // (2026-09-12) showed Escape leaving a pushed route in place on desktop.
    BackHandler(enabled = navState.canGoBack) { navState.back() }
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
        // The route content, identical under either shell — only the chrome around it differs.
        val content: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = route) {
                    is WorkbenchRoute.TabRoot -> when (current.tab) {
                        WorkbenchDestination.PAGES -> PagesScreen(core = core, onOpenPage = navState::openPage, onOpenSwitcher = { switcher.open = true })
                        WorkbenchDestination.CALENDAR -> calendarContent(navState::openPage)
                        WorkbenchDestination.TASKS_HABITS -> tasksHabitsContent(navState::openReview)
                        // §0.8 step 8c — shared; the slot it used to fill is gone.
                        WorkbenchDestination.ROAD_MAP -> RoadMapScreen(
                            core = core,
                            onOpenPage = navState::openPage,
                            focusRequest = navState.roadMapFocusRequest,
                            onFocusConsumed = { navState.roadMapFocusRequest = null },
                        )
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
                                onShowOnRoadMap = navState::showOnRoadMap,
                                onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
                            )
                        }
                    }
                }
                if (switcher.open) {
                    // Over everything, on every route — the switcher is how you get anywhere.
                    QuickSwitcher(
                        core = core,
                        commands = switcherCommands(core, navState),
                        onOpenPage = navState::openPage,
                        onDismiss = { switcher.open = false },
                    )
                }
            }
        }
        // No top bar here — every destination owns its own [ShellTopBar] and handles the
        // status-bar/cutout inset itself; the content gets no inset from the shell, so nothing
        // is applied twice.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            when (shellLayoutFor(maxWidth.value)) {
                ShellLayout.BAR -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) { content() }
                    // §3.1.2 / audit 4.3 — no bottom bar while the keyguard is being bypassed. It used
                    // to render regardless, so a tap on Settings navigated there *over the lock screen*;
                    // the LaunchedEffect above deactivates on a page change, but it runs after that frame
                    // has already been drawn, which is exactly one frame of Settings too many. Removing
                    // the control removes the path, rather than racing it.
                    // §0.6.5 / step 7c — the running timer rides above the tabs: the one place every
                    // route on both platforms shares, since no screen's own top bar is.
                    if (!bypassingKeyguard) { RunningTimerBar(core); ShellBottomBar(navState) }
                }
                // 14a — the same rule for the rail: no navigation while the keyguard is bypassed.
                ShellLayout.RAIL -> Row(modifier = Modifier.fillMaxSize()) {
                    if (!bypassingKeyguard) ShellRail(navState, foot = { RunningTimerRailFoot(core) })
                    Box(modifier = Modifier.weight(1f)) { content() }
                }
            }
        }
    }
}

/**
 * The command palette's verbs (B§6 #11): navigation and creation, nothing that needs a form.
 * Built here because they are the scaffold's own moves — a tab, a route, the New sheet's
 * three kinds — with the page creation going through the same [PagesViewModel] paths the
 * Pages screen uses (View-Only refuses them the same way).
 */
@Composable
private fun switcherCommands(core: WorkbenchCore, navState: WorkbenchNavState): List<SwitcherCommand> {
    val pagesViewModel: PagesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PagesViewModel(
                    core.database.pageDao(), core.database.pageDatabaseDao(), core.database.propertyDao(), core.database.pageFtsDao(),
                    core.database.labelDao(), core.purgeRegistry, core.databaseSyncManager, core.templateManager, core.viewLockState,
                    core.pageContentRepository,
                )
            }
        }
    )
    val tasks by core.database.entryDao().observeTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    return remember(tasks) {
        buildList {
            add(SwitcherCommand("New page", "create blank") { pagesViewModel.createBlankPage("") { navState.openPage(it) } })
            add(SwitcherCommand("New database", "create table") { pagesViewModel.createDatabase("", asToDoDatabase = false) { navState.openPage(it) } })
            add(SwitcherCommand("New to-do database", "create tasks") { pagesViewModel.createDatabase("", asToDoDatabase = true) { navState.openPage(it) } })
            add(SwitcherCommand("New canvas", "create board") { pagesViewModel.createCanvas("") { navState.openPage(it) } })
            add(SwitcherCommand("Journal today", "daily note") { pagesViewModel.openJournal(LocalDate.now()) { navState.openPage(it) } })
            add(SwitcherCommand("Review", "weekly walk") { navState.openReview() })
            for (destination in WorkbenchDestination.entries) {
                add(SwitcherCommand("Go to " + destination.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, "tab") { navState.switchTab(destination) })
            }
            for (task in tasks.filter { it.status == EntryStatus.PENDING && it.deletedAt == null }) {
                add(SwitcherCommand("Start timer: " + task.title, "track time") { scope.launch { core.timeTracker.start(TrackTarget.Entry(task.id)) } })
            }
        }
    }
}

