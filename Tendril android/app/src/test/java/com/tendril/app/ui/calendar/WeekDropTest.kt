package com.tendril.app.ui.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** B§13.6 #5 — where a drag lands on the week: one geometry, one target under the pointer. */
class WeekDropTest {
    private val monday = LocalDate.of(2026, 9, 14)
    // Header 0..40, all-day 40..70, grid 70..570; gutter 44, lanes 100 wide, an hour 56 px, scrolled 112 px (two hours).
    private val g = WeekGeometry(
        grid = Rect(0f, 70f, 744f, 570f), header = Rect(0f, 0f, 744f, 40f), allDay = Rect(0f, 40f, 744f, 70f),
        gutterPx = 44f, laneWidthPx = 100f, hourPx = 56f, scrollPx = 112f, weekStart = monday,
    )

    @Test
    fun `a point on the grid is the lane's day at the quarter hour under it`() {
        // Lane 3 (Thursday); the grid is scrolled two hours, so y = 70 + 10h*56 - 112 (+ 7 min) → 10:07 → 10:00.
        val drop = weekDropAt(Offset(44f + 3 * 100f + 10f, 70f + 10 * 56f - 112f + 6.5f), g)
        assertEquals(WeekDrop.Slot(monday.plusDays(3), LocalTime.of(10, 0)), drop)
    }

    @Test
    fun `the header and the all-day row are the day with no time`() {
        assertEquals(WeekDrop.AllDay(monday), weekDropAt(Offset(50f, 20f), g))
        assertEquals(WeekDrop.AllDay(monday.plusDays(6)), weekDropAt(Offset(700f, 55f), g))
    }

    @Test
    fun `the gutter and the outside are nothing`() {
        assertNull(weekDropAt(Offset(10f, 200f), g))
        assertNull(weekDropAt(Offset(300f, 600f), g))
        assertNull(weekDropAt(Offset(10f, 20f), g))
    }

    @Test
    fun `without an all-day row the header still takes the day`() {
        val drop = weekDropAt(Offset(150f, 20f), g.copy(allDay = null))
        assertEquals(WeekDrop.AllDay(monday.plusDays(1)), drop)
    }

    @Test
    fun `a day cell holds the point or nothing does`() {
        val cells = mapOf(monday to Rect(0f, 0f, 50f, 50f), monday.plusDays(1) to Rect(50f, 0f, 100f, 50f))
        assertEquals(monday.plusDays(1), dayCellAt(Offset(75f, 10f), cells))
        assertNull(dayCellAt(Offset(120f, 10f), cells))
    }
}
