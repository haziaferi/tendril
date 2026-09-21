package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.domain.plan.HABIT_STROKE_MIN_DP
import com.tendril.app.domain.plan.habitStrokes
import com.tendril.app.domain.plan.strokeHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** §3.2 (2026-09-21) — a habit on the grids is a stroke across its span, one state, a dot only on today's lane. */
class HabitStrokesTest {
    private val today = LocalDate.of(2026, 9, 21)
    private fun habit(id: Long, title: String, time: LocalTime?, duration: Duration? = null, last: LocalDate? = null, deleted: Boolean = false) = Habit(
        id = id, uid = "u$id", title = title, frequency = HabitFrequency(1, IntervalUnit.DAY), time = time, duration = duration,
        lastCompletedDate = last, deletedAt = if (deleted) Instant.EPOCH else null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun `the span is the time and the duration, thirty minutes without one, and a habit with no time is not on the grid`() {
        val strokes = habitStrokes(listOf(habit(1, "Pages read", LocalTime.of(21, 0), Duration.ofHours(1)), habit(2, "Stretch", LocalTime.of(7, 30)), habit(3, "Water", null)), today, today)
        assertEquals(listOf("Stretch", "Pages read"), strokes.map { it.habit.title })
        assertEquals(7 * 60 + 30, strokes[0].startMinute); assertEquals(30, strokes[0].minutes)
        assertEquals(21 * 60, strokes[1].startMinute); assertEquals(60, strokes[1].minutes)
    }

    @Test
    fun `a stroke never runs past midnight and a trashed habit has none`() {
        val strokes = habitStrokes(listOf(habit(1, "Late", LocalTime.of(23, 30), Duration.ofHours(2)), habit(2, "Gone", LocalTime.of(8, 0), deleted = true)), today, today)
        assertEquals(listOf("Late"), strokes.map { it.habit.title })
        assertEquals(30, strokes.single().minutes)
    }

    @Test
    fun `the check-in marks today's lane only, never a past or a future day`() {
        val h = listOf(habit(1, "Stretch", LocalTime.of(7, 30), last = today))
        assertTrue(habitStrokes(h, today, today).single().checkedIn)
        assertFalse(habitStrokes(h, today.minusDays(1), today).single().checkedIn)
        assertFalse(habitStrokes(h, today.plusDays(1), today).single().checkedIn)
        assertFalse("checked in yesterday is not today's presence", habitStrokes(listOf(habit(1, "Stretch", LocalTime.of(7, 30), last = today.minusDays(1))), today, today).single().checkedIn)
    }

    @Test
    fun `the height follows the grid's hour and never falls under the name's line`() {
        assertEquals(45f, strokeHeightDp(60, 45f), 0.01f)
        assertEquals(HABIT_STROKE_MIN_DP, strokeHeightDp(20, 45f), 0.01f)   // 15 dp would be too short for the name
        assertEquals(18.67f, strokeHeightDp(20, 56f), 0.01f)                 // the phone's Plan view is just over the floor
    }
}
