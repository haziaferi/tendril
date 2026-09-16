package com.tendril.app.ui.calendar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.tendril.app.domain.plan.laneAt
import com.tendril.app.domain.plan.snapMinute
import com.tendril.app.domain.plan.timeOfMinute
import java.time.LocalDate
import java.time.LocalTime

/**
 * B§13.6 #5 — where a drag lands on the week grid, as one geometry both the grid's own block
 * drag and the tray's drag ask (`docs/critiques/drag-between-panes-mock.md` — one target, the
 * thing under the pointer). Root coordinates throughout; the grid fills this from its
 * `onGloballyPositioned`s and its scroll.
 */
data class WeekGeometry(
    /** The scrolling hour grid's bounds on screen. */
    val grid: Rect,
    /** The day-header row (a drop there is the day, no time). */
    val header: Rect,
    /** The all-day row, when the week has one; a drop there is the day too. */
    val allDay: Rect?,
    val gutterPx: Float,
    val laneWidthPx: Float,
    val hourPx: Float,
    val scrollPx: Float,
    val weekStart: LocalDate,
)

sealed class WeekDrop {
    abstract val day: LocalDate
    data class AllDay(override val day: LocalDate) : WeekDrop()
    data class Slot(override val day: LocalDate, val time: LocalTime) : WeekDrop()
}

/** Null outside every target; the gutter is outside. */
fun weekDropAt(pos: Offset, g: WeekGeometry): WeekDrop? {
    fun dayFor(bounds: Rect): LocalDate? {
        if (pos.x < bounds.left + g.gutterPx) return null
        return g.weekStart.plusDays(laneAt(pos.x - bounds.left, g.gutterPx, g.laneWidthPx).toLong())
    }
    if (g.header.contains(pos)) return dayFor(g.header)?.let { WeekDrop.AllDay(it) }
    g.allDay?.let { if (it.contains(pos)) return dayFor(it)?.let { d -> WeekDrop.AllDay(d) } }
    if (g.grid.contains(pos)) {
        val day = dayFor(g.grid) ?: return null
        val minute = (((pos.y - g.grid.top + g.scrollPx) / g.hourPx) * 60).toInt().coerceIn(0, 24 * 60 - 1)
        return WeekDrop.Slot(day, timeOfMinute(snapMinute(minute)))
    }
    return null
}

/** The day whose cell holds [pos] — the Week strip's cards, the Month's cells — or null. */
fun dayCellAt(pos: Offset, cells: Map<LocalDate, Rect>): LocalDate? =
    cells.entries.firstOrNull { it.value.contains(pos) }?.key
