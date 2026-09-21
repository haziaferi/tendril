@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.nav

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.Density
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.pages.PagesScreen
import com.tendril.app.ui.pages.PagesTreeState
import com.tendril.app.ui.pages.ShelfState
import com.tendril.app.ui.pages.PagesWorkspace
import com.tendril.app.ui.track.RunningTimerBar
import com.tendril.app.ui.track.RunningTimerRailFoot
import com.tendril.app.ui.review.ReviewScreen
import com.tendril.app.ui.roadmap.RoadMapScreen
import com.tendril.app.ui.switcher.QuickSwitcher
import com.tendril.app.ui.pages.rememberPagesViewModel
import com.tendril.app.ui.components.onPointerNavigation
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
 * a real implementation. Settings is still injected as a composable slot (`settingsContent`) —
 * each platform draws its own pane — while every other tab and the Canvas page kind are shared
 * code; the `NotAvailableOnDesktop` placeholders this slot pattern was made for are gone
 * (Canvas 2026-09-11, Calendar and Tasks 2026-09-12, Road Map and Settings 2026-09-13).
 */
@Composable
fun WorkbenchScaffold(
    core: WorkbenchCore,
    navState: WorkbenchNavState = remember { WorkbenchNavState() },
    /** §3.1.7 — the quick switcher's open flag; the desktop's Ctrl+K toggles it from `Main.kt`. */
    switcher: SwitcherState = remember { SwitcherState() },
    /** 14c — the tree pane's width, collapsed flag and expansions; the desktop's Ctrl+\ toggles it from `Main.kt`. */
    treeState: PagesTreeState = remember { PagesTreeState(core.keyValueStore) },
    /** 14h·1 — the shelf beside the page (what it holds, its width); Ctrl+Shift+\ toggles it. */
    shelfState: ShelfState = remember { ShelfState(core.keyValueStore) },
    /** 14e — the shortcuts overlay's open flag (Ctrl+/ and Settings open it) and the actions the
     * desktop's key table runs; the scaffold fills [shortcutActions] because it owns what they move. */
    shortcuts: ShortcutsState = remember { ShortcutsState() },
    shortcutActions: ShortcutActions? = null,
    onCheckboxOnlyWindowFlags: ((active: Boolean) -> Unit)? = null,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
    /** B§13.5 #4 — the density profile; a platform that fixes it (the phone: Touch) passes it,
     * one that lets the person choose (the desktop, in Settings) passes null and the store's
     * `density_profile` is read. */
    fixedDensityProfile: DensityProfile? = null,
    /** The platform applied the shell's scale to its own density already (Android) — see `WorkbenchEnvironment`. */
    platformScaled: Boolean = false,
    /** B§13.6 #6 — pop-out windows; null where there are no windows (the phone): no opener is composed. */
    popOuts: PopOutHost? = null,
    calendarContent: @Composable (onOpenPage: (Long) -> Unit) -> Unit,
    /** 14e — [quickAddRequested] is Ctrl+Shift+N's intent; the screen opens its Add sheet and calls [onQuickAddConsumed]. */
    tasksHabitsContent: @Composable (onOpenReview: () -> Unit, quickAddRequested: Boolean, onQuickAddConsumed: () -> Unit) -> Unit,
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

    // 14e — the desktop's key table runs through here; Android has no fixed set yet, so its
    // scaffold passes no actions and nothing is filled.
    val pagesViewModel = rememberPagesViewModel(core)
    shortcutActions?.run = { action ->
        when (action) {
            ShortcutAction.SWITCHER -> switcher.open = true
            ShortcutAction.SHORTCUTS -> shortcuts.open = true
            ShortcutAction.TOGGLE_TREE -> treeState.toggle()
            ShortcutAction.TOGGLE_SHELF -> shelfState.toggle()
            ShortcutAction.BACK -> navState.back()
            ShortcutAction.FORWARD -> navState.forward()
            ShortcutAction.NEW_PAGE -> pagesViewModel.createBlankPage("") { navState.openPage(it) }
            ShortcutAction.NEW_TASK -> navState.requestQuickAdd()
            ShortcutAction.FIND_IN_PAGE -> navState.requestFind()
            ShortcutAction.JOURNAL_TODAY -> pagesViewModel.openJournal(LocalDate.now()) { navState.openPage(it) }
            else -> action.tab()?.let { navState.switchTab(it) }
        }
    }

    // B§13.5 #4 — one scale for every measurement below this point (`WorkbenchEnvironment`,
    // shared with the pop-out windows, which take this window's scale rather than their own).
    WorkbenchEnvironment(core, fixedDensityProfile, platformScaled = platformScaled) {
        // The route content, identical under either shell — only the chrome around it differs.
        // 14c: on a wide window the Pages tab, root or page, is the workspace (tree + page).
        val content: @Composable (wide: Boolean) -> Unit = { wide ->
            // 14e — the mouse's side buttons are Back and Forward on every route.
            CompositionLocalProvider(LocalShellLayout provides if (wide) ShellLayout.RAIL else ShellLayout.BAR) {
            // 14g·1 — the ground and its content colour are set here, once: a route that draws no
            // background of its own (the desktop's Settings pane) otherwise shows the window's AWT
            // grey, and its unstyled text takes `LocalContentColor`'s black on a dark register.
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().onPointerNavigation(onBack = { navState.back() }, onForward = { navState.forward() })) {
                when (val current = route) {
                    is WorkbenchRoute.TabRoot, is WorkbenchRoute.PageDetail -> if (wide && current.tab == WorkbenchDestination.PAGES) {
                        PagesWorkspace(
                            core = core,
                            navState = navState,
                            treeState = treeState,
                            shelfState = shelfState,
                            popOuts = popOuts,
                            onOpenSwitcher = { switcher.open = true },
                            onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
                        )
                    } else when (current) {
                    is WorkbenchRoute.TabRoot -> when (current.tab) {
                        WorkbenchDestination.PAGES -> PagesScreen(core = core, onOpenPage = navState::openPage, onOpenSwitcher = { switcher.open = true }, onShowOnRoadMap = navState::showOnRoadMap)
                        WorkbenchDestination.CALENDAR -> calendarContent(navState::openPage)
                        WorkbenchDestination.TASKS_HABITS -> tasksHabitsContent(navState::openReview, navState.quickAddRequested) { navState.quickAddRequested = false }
                        // §0.8 step 8c — shared; the slot it used to fill is gone.
                        WorkbenchDestination.ROAD_MAP -> RoadMapScreen(
                            core = core,
                            onOpenPage = navState::openPage,
                            focusRequest = navState.roadMapFocusRequest,
                            onFocusConsumed = { navState.roadMapFocusRequest = null },
                        )
                        WorkbenchDestination.SETTINGS -> settingsContent()
                    }
                        is WorkbenchRoute.PageDetail -> PageRoute(
                            core = core,
                            pageId = current.pageId,
                            onBack = { navState.back() },
                            navState = navState,
                            onCheckboxOnlyUnlockRequest = onCheckboxOnlyUnlockRequest,
                        )
                        else -> {}
                    }
                    is WorkbenchRoute.Review -> ReviewScreen(core = core, onBack = { navState.back() }, onOpenPage = navState::openPage)
                }
                if (shortcuts.open) ShortcutsOverlay(onDismiss = { shortcuts.open = false }, quickAddChordLabel = shortcuts.quickAddChordLabel)
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
            }
        }
        // No top bar here — every destination owns its own [ShellTopBar] and handles the
        // status-bar/cutout inset itself; the content gets no inset from the shell, so nothing
        // is applied twice.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            when (shellLayoutFor(maxWidth.value)) {
                ShellLayout.BAR -> Column(modifier = Modifier.fillMaxSize()) {
                    // S4 (small things IV) — the bottom bar takes the navigation bar's inset for everything
                    // above it, so a screen's `Scaffold` (which pads for `contentWindowInsets` by default)
                    // no longer leaves a dead band above the bar; while the keyguard is bypassed there is no
                    // bar and the inset is left for the screen.
                    Box(modifier = Modifier.weight(1f).then(if (bypassingKeyguard) Modifier else Modifier.consumeWindowInsets(WindowInsets.navigationBars))) { content(false) }
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
                    Box(modifier = Modifier.weight(1f)) { content(true) }
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
                    core.pageContentRepository, core.database.entryDao(), core.resolveEntryUseCase,
                )
            }
        }
    )
    val tasks by core.database.entryDao().observeTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    return remember(tasks) {
        buildList {
            add(SwitcherCommand("New page", "create blank", chord = chordLabelOf(ShortcutAction.NEW_PAGE)) { pagesViewModel.createBlankPage("") { navState.openPage(it) } })
            add(SwitcherCommand("New database", "create table") { pagesViewModel.createDatabase("", asToDoDatabase = false) { navState.openPage(it) } })
            add(SwitcherCommand("New to-do database", "create tasks") { pagesViewModel.createDatabase("", asToDoDatabase = true) { navState.openPage(it) } })
            add(SwitcherCommand("New canvas", "create board") { pagesViewModel.createCanvas("") { navState.openPage(it) } })
            add(SwitcherCommand("Journal today", "daily note", chord = chordLabelOf(ShortcutAction.JOURNAL_TODAY)) { pagesViewModel.openJournal(LocalDate.now()) { navState.openPage(it) } })
            add(SwitcherCommand("Review", "weekly walk") { navState.openReview() })
            for (destination in WorkbenchDestination.entries) {
                // The tab's name and chord as the F1 card prints them (L6: "Tasks", "Road Map" — not the enum's spelling).
                val action = ShortcutAction.entries.firstOrNull { it.tab() == destination }
                val name = action?.label ?: destination.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                add(SwitcherCommand("Go to $name", "tab", chord = action?.let(::chordLabelOf)) { navState.switchTab(destination) })
            }
            for (task in tasks.filter { it.status == EntryStatus.PENDING && it.deletedAt == null }) {
                add(SwitcherCommand("Start timer: " + task.title, "track time") { scope.launch { core.timeTracker.start(TrackTarget.Entry(task.id)) } })
            }
        }
    }
}

