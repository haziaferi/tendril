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

    val current: WorkbenchRoute get() = backStack.last()
    val currentTab: WorkbenchDestination get() = current.tab
    val canGoBack: Boolean get() = backStack.size > 1

    fun switchTab(tab: WorkbenchDestination) {
        if (backStack.size == 1 && backStack[0].tab == tab) return
        backStack.clear()
        backStack.add(WorkbenchRoute.TabRoot(tab))
    }

    fun openPage(pageId: Long) {
        backStack.add(WorkbenchRoute.PageDetail(pageId, currentTab))
    }

    /** §3.4 (step 8c) — a page's "Show on Road Map": the map opens focused on it. Set here,
     * read by the Road Map tab, cleared by it once applied — the one cross-tab intent. */
    var roadMapFocusRequest: Long? by mutableStateOf(null)

    fun showOnRoadMap(pageId: Long) {
        roadMapFocusRequest = pageId
        switchTab(WorkbenchDestination.ROAD_MAP)
    }

    fun openReview() {
        backStack.add(WorkbenchRoute.Review(currentTab))
    }

    /** Returns whether it actually popped something — callers (e.g. an Android `BackHandler`)
     * only intercept a system back gesture when this would return true; at a tab root, back
     * falls through to whatever the platform normally does (exit the app, on Android). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
