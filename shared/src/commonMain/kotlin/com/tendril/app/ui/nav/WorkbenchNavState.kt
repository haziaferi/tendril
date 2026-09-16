package com.tendril.app.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Which screen is on top: either a tab's own root, or a page pushed on top of one. [tab] on
 * [PageDetail] is the tab it was opened from, so switching back to the bottom bar's current
 * selection after opening a page still highlights the right tab. */
sealed interface WorkbenchRoute {
    val tab: WorkbenchDestination

    data class TabRoot(override val tab: WorkbenchDestination) : WorkbenchRoute
    data class PageDetail(val pageId: Long, override val tab: WorkbenchDestination) : WorkbenchRoute
    /** §0.6.11 — the weekly walk, pushed from Tasks; Back returns there. */
    data class Review(override val tab: WorkbenchDestination = WorkbenchDestination.TASKS_HABITS) : WorkbenchRoute
}

/**
 * Milestone 3 (tendril-windows-spec.md §6 step 3) — a hand-rolled back stack, not the official
 * Compose Multiplatform navigation-compose artifact: that library is still alpha/beta-only at
 * this project's Compose Multiplatform 1.12.0 pin, and WorkbenchScaffold's actual nav needs (5
 * fixed tabs + one parameterized page-detail push) are simple enough that hand-rolling carries
 * far less version risk than depending on a pre-1.0 external navigation library for a
 * production nav shell. Revisit once navigation-compose reaches a real stable release.
 *
 * Simplification versus Android's previous Navigation-Compose-backed shell: switching tabs
 * always drops any page pushed on top of the *previous* tab (no per-tab saveState/restoreState
 * back stack) — each tab simply reopens at its own root.
 */
class WorkbenchNavState(startTab: WorkbenchDestination = WorkbenchDestination.PAGES) {
    private val backStack = mutableStateListOf<WorkbenchRoute>(WorkbenchRoute.TabRoot(startTab))
    /** 14e (B§13.6 #2) — what Back popped, so Forward can put it back; emptied by any new move. */
    private val forwardStack = mutableStateListOf<WorkbenchRoute>()

    val current: WorkbenchRoute get() = backStack.last()
    val currentTab: WorkbenchDestination get() = current.tab
    val canGoBack: Boolean get() = backStack.size > 1
    /** How deep the stack is — a pop-out window (B§13.6 #6) seeds itself with one page over the root and lets Back stop there. */
    val depth: Int get() = backStack.size
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()

    fun switchTab(tab: WorkbenchDestination) {
        if (backStack.size == 1 && backStack[0].tab == tab) return
        forwardStack.clear()
        backStack.clear()
        backStack.add(WorkbenchRoute.TabRoot(tab))
    }

    fun openPage(pageId: Long) {
        forwardStack.clear()
        backStack.add(WorkbenchRoute.PageDetail(pageId, currentTab))
    }

    /** 14c — the tree's way of opening: the page *replaces* an open page rather than piling on
     * it, so clicking through the tree never grows the stack and Escape closes to the tab root.
     * A link inside a page uses [openPage] and still pushes, so Back returns where it came from. */
    fun showPage(pageId: Long) {
        forwardStack.clear()
        val top = current
        if (top is WorkbenchRoute.PageDetail) backStack[backStack.lastIndex] = WorkbenchRoute.PageDetail(pageId, top.tab)
        else backStack.add(WorkbenchRoute.PageDetail(pageId, currentTab))
    }

    /** §3.4 (step 8c) — a page's "Show on Road Map": the map opens focused on it. Set here,
     * read by the Road Map tab, cleared by it once applied — the one cross-tab intent. */
    var roadMapFocusRequest: Long? by mutableStateOf(null)

    fun showOnRoadMap(pageId: Long) {
        roadMapFocusRequest = pageId
        switchTab(WorkbenchDestination.ROAD_MAP)
    }

    fun openReview() {
        forwardStack.clear()
        backStack.add(WorkbenchRoute.Review(currentTab))
    }

    /** Returns whether it actually popped something — callers (e.g. an Android `BackHandler`)
     * only intercept a system back gesture when this would return true; at a tab root, back
     * falls through to whatever the platform normally does (exit the app, on Android). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        forwardStack.add(backStack.removeAt(backStack.lastIndex))
        return true
    }

    /** 14e — the route Back left, restored; false at the tip. A new push in between forgets
     * it (the browsers' rule: a new branch has no old future). */
    fun forward(): Boolean {
        if (forwardStack.isEmpty()) return false
        backStack.add(forwardStack.removeAt(forwardStack.lastIndex))
        return true
    }

    /** 14e — Ctrl+Shift+N from anywhere: Tasks opens with its Add sheet. Set here, read and
     * cleared by the Tasks tab — the second cross-tab intent after [roadMapFocusRequest]. */
    var quickAddRequested: Boolean by mutableStateOf(false)

    fun requestQuickAdd() {
        quickAddRequested = true
        switchTab(WorkbenchDestination.TASKS_HABITS)
    }

    /** §0.10 item 19 — Ctrl+F: a counter the open page reads; each bump opens (or re-focuses)
     * its find bar. A Database or Canvas page ignores it — their own filters. The third
     * cross-tab intent, and the first that changes no route. */
    var findRequested: Int by mutableStateOf(0)

    fun requestFind() { findRequested++ }
}
