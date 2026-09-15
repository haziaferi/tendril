package com.tendril.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 14c — the two ways a page opens: the tree replaces, a link pushes. */
class WorkbenchNavStateTest {

    @Test
    fun `showPage replaces an open page instead of piling on it`() {
        val nav = WorkbenchNavState()
        nav.showPage(1); nav.showPage(2); nav.showPage(3)
        assertEquals(WorkbenchRoute.PageDetail(3, WorkbenchDestination.PAGES), nav.current)
        assertTrue(nav.back())                         // one Escape…
        assertEquals(WorkbenchRoute.TabRoot(WorkbenchDestination.PAGES), nav.current)   // …closes to the tab root
        assertFalse(nav.back())
    }

    @Test
    fun `openPage pushes, so a link inside a page returns where it came from`() {
        val nav = WorkbenchNavState()
        nav.showPage(1); nav.openPage(2)
        assertEquals(WorkbenchRoute.PageDetail(2, WorkbenchDestination.PAGES), nav.current)
        assertTrue(nav.back())
        assertEquals(WorkbenchRoute.PageDetail(1, WorkbenchDestination.PAGES), nav.current)
    }

    @Test
    fun `showPage at a tab root pushes, and keeps the tab it was opened from`() {
        val nav = WorkbenchNavState(WorkbenchDestination.CALENDAR)
        nav.showPage(7)
        assertEquals(WorkbenchRoute.PageDetail(7, WorkbenchDestination.CALENDAR), nav.current)
        assertEquals(WorkbenchDestination.CALENDAR, nav.currentTab)
    }

    @Test
    fun `switching tabs drops any open page`() {
        val nav = WorkbenchNavState()
        nav.showPage(1)
        nav.switchTab(WorkbenchDestination.TASKS_HABITS)
        assertEquals(WorkbenchRoute.TabRoot(WorkbenchDestination.TASKS_HABITS), nav.current)
        assertFalse(nav.canGoBack)
    }
}
