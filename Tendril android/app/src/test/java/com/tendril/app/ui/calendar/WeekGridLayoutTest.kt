package com.tendril.app.ui.calendar

import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.domain.plan.TimelineBlock
import com.tendril.app.domain.plan.laneAt
import com.tendril.app.domain.plan.openScrollMinute
import com.tendril.app.domain.plan.visibleAllDay
import org.junit.Assert.assertEquals
import org.junit.Test

/** 14f·2 — the week grid's arithmetic and the opening view's rule. */
class WeekGridLayoutTest {
    private fun block(start: Int) = TimelineBlock("k$start", "t", start, start + 30, false, 0, 1, null, BlockKind.TASK)

    @Test
    fun `the lane under a pointer is clamped to the seven days`() {
        assertEquals(0, laneAt(xPx = 10f, gutterPx = 44f, laneWidthPx = 100f))     // in the gutter → the first day
        assertEquals(2, laneAt(xPx = 44f + 250f, gutterPx = 44f, laneWidthPx = 100f))
        assertEquals(6, laneAt(xPx = 5000f, gutterPx = 44f, laneWidthPx = 100f))
    }

    @Test
    fun `the grid opens an hour before the first block, else at seven`() {
        assertEquals(7 * 60, openScrollMinute(emptyList()))
        assertEquals(9 * 60, openScrollMinute(listOf(block(10 * 60), block(14 * 60))))
        assertEquals(0, openScrollMinute(listOf(block(30))))
    }

    @Test
    fun `the all-day row shows three, else two and a count`() {
        assertEquals(listOf(1, 2, 3) to 0, visibleAllDay(listOf(1, 2, 3)))
        assertEquals(listOf(1, 2) to 3, visibleAllDay(listOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `the opening view is the stored choice, else week when wide and day otherwise`() {
        assertEquals(CalendarViewKey.WEEK, defaultCalendarView(null, wide = true))
        assertEquals(CalendarViewKey.DAY, defaultCalendarView(null, wide = false))
        assertEquals(CalendarViewKey.MONTH, defaultCalendarView("month", wide = true))
        assertEquals(CalendarViewKey.WEEK, defaultCalendarView("garbage", wide = true))
    }
}
