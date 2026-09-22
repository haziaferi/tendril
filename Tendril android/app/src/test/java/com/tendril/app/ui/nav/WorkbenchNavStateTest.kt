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

    // ------------------------------------------------------------- 14e — forward (B§13.6 #2)

    @Test
    fun `forward restores what back popped`() {
        val nav = WorkbenchNavState()
        nav.showPage(1); nav.openPage(2)
        assertTrue(nav.back())
        assertTrue(nav.canGoForward)
        assertTrue(nav.forward())
        assertEquals(WorkbenchRoute.PageDetail(2, WorkbenchDestination.PAGES), nav.current)
        assertFalse("at the tip, forward has nothing", nav.forward())
    }

    @Test
    fun `a new move after back forgets the old future`() {
        val nav = WorkbenchNavState()
        nav.showPage(1); nav.openPage(2)
        nav.back()
        nav.openPage(3)
        assertFalse(nav.canGoForward)
        assertFalse(nav.forward())
        assertEquals(WorkbenchRoute.PageDetail(3, WorkbenchDestination.PAGES), nav.current)
    }

    @Test
    fun `switching tabs clears forward too`() {
        val nav = WorkbenchNavState()
        nav.openPage(1); nav.back()
        nav.switchTab(WorkbenchDestination.CALENDAR)
        assertFalse(nav.canGoForward)
    }

    @Test
    fun `quick add lands on Tasks with the request raised`() {
        val nav = WorkbenchNavState()
        nav.openPage(1)
        nav.requestQuickAdd()
        assertEquals(WorkbenchRoute.TabRoot(WorkbenchDestination.TASKS_HABITS), nav.current)
        assertTrue(nav.quickAddRequested)
    }

    @Test
    fun `depth counts the root, so a pop-out seeded with one page sits at two`() {
        val nav = WorkbenchNavState()
        assertEquals(1, nav.depth)
        nav.openPage(7)
        assertEquals(2, nav.depth)
        nav.openPage(8)
        assertEquals(3, nav.depth)
        nav.back()
        assertEquals(2, nav.depth)
    }

    /** v25 — a block hit opens the page with the jump set for it; the page clears it. */
    @Test
    fun `openBlock pushes the page and leaves the jump for it`() {
        val nav = WorkbenchNavState()
        nav.openBlock(pageId = 7, blockId = 70, query = "socks")
        assertEquals(BlockJump(7, 70, "socks"), nav.blockJump)
        assertTrue(nav.current is WorkbenchRoute.PageDetail && (nav.current as WorkbenchRoute.PageDetail).pageId == 7L)
        nav.blockJump = null
        assertEquals(null, nav.blockJump)
    }
}
