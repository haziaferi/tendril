package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.DayRow
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.habitDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * §6.3 (plan Phase 5a) — the Day view by time: what belongs on a day, where it sits, what was done,
 * and how past, present and later days differ (T2, T3). Written before [habitDay] had a body.
 */
class HabitDayTest {

    private val at = Instant.ofEpochMilli(1_000)
    private val today = LocalDate.of(2026, 9, 30) // a Wednesday

    private fun cal(id: Long, rule: CalendarRule, time: LocalTime? = null, block: String? = null, order: Double = 0.0, deleted: Boolean = false) = Habit(
        id = id, uid = "c$id", title = "Cal $id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at,
        scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = encodeRule(rule), blockUid = block, sortOrder = order, deletedAt = if (deleted) at else null,
    )

    private fun interval(id: Long, every: Int = 1, last: LocalDate? = null, time: LocalTime? = null, block: String? = null) = Habit(
        id = id, uid = "i$id", title = "Int $id", time = time, frequency = HabitFrequency(every, IntervalUnit.DAY),
        lastCompletedDate = last, createdAt = at, updatedAt = at, blockUid = block,
    )

    private fun done(habitId: Long, date: LocalDate = today, key: String? = null) = HabitCompletion(habitId = habitId, date = date, checkedAt = at, occurrenceKey = key)

    private fun day(date: LocalDate, habits: List<Habit>, completions: List<HabitCompletion> = emptyList(), blocks: List<com.tendril.app.data.habit.HabitBlock> = DEFAULT_HABIT_BLOCKS) =
        habitDay(date, today, habits, blocks, emptyList(), completions.filter { it.date == date })

    private fun HabitDayView.block(uid: String) = blocks.single { it.block.uid == uid }
    private fun List<DayRow>.ids() = map { it.habit.id to it.key }

    @Test
    fun `a set time sits in the block that holds it, a block habit in its block, the rest under Any time, a time outside every block kept aside`() {
        val v = day(today, listOf(
            cal(1, CalendarRule.Daily, time = LocalTime.of(7, 0)),
            cal(2, CalendarRule.Daily, block = "block-evening"),
            cal(3, CalendarRule.Daily),
            cal(4, CalendarRule.Daily, time = LocalTime.of(5, 30)),
            cal(5, CalendarRule.Daily, time = LocalTime.of(9, 0)), // on the boundary: Midday's
        ))
        assertEquals(listOf("block-morning", "block-midday", "block-afternoon", "block-evening", "block-night"), v.blocks.map { it.block.uid })
        assertEquals(listOf(1L to "1"), v.block("block-morning").timed.ids())
        assertEquals(listOf(5L to "1"), v.block("block-midday").timed.ids())
        assertEquals(listOf(2L to "1"), v.block("block-evening").flexible.ids())
        assertEquals(listOf(3L to "1"), v.anyTime.ids())
        assertEquals(listOf(4L to "1"), v.outside.ids())
        assertEquals(420, v.block("block-morning").timed.single().time)
    }

    @Test
    fun `set times come first in time order, then the rest in their manual order`() {
        val v = day(today, listOf(
            cal(1, CalendarRule.Daily, block = "block-morning", order = 2.0),
            cal(2, CalendarRule.Daily, time = LocalTime.of(8, 30)),
            cal(3, CalendarRule.Daily, block = "block-morning", order = 1.0),
            cal(4, CalendarRule.Daily, time = LocalTime.of(7, 0)),
        ))
        assertEquals(listOf(4L, 2L), v.block("block-morning").timed.map { it.habit.id })
        assertEquals(listOf(3L, 1L), v.block("block-morning").flexible.map { it.habit.id })
    }

    /** P3 — a habit several times a day is one row per occurrence, each done by its own check-in. */
    @Test
    fun `several a day is a row per occurrence, each with its own check-in`() {
        val water = cal(1, CalendarRule.TimesPerDay(3, listOf(Slot.At(480), Slot.InBlock("block-evening"), Slot.At(1230))))
        val v = day(today, listOf(water), listOf(done(1, key = "2")))
        assertEquals(listOf(1L to "1"), v.block("block-morning").timed.ids())
        assertEquals(listOf(1L to "2"), v.block("block-evening").flexible.ids())
        assertEquals(listOf(1L to "3"), v.block("block-evening").timed.ids())
        assertEquals(listOf(false, true, false), listOf(v.block("block-morning").timed, v.block("block-evening").flexible, v.block("block-evening").timed).map { it.single().done })
        assertEquals(1, v.doneCount)
    }

    /** V2 — an interval habit due today sits in its block or under its set time, like any other. */
    @Test
    fun `an interval habit due today is placed like a calendar one, and done by any check-in today`() {
        val stretch = interval(1, time = LocalTime.of(7, 30))
        val read = interval(2, block = "block-night", last = today)
        val v = day(today, listOf(stretch, read), listOf(done(2)))
        assertEquals(listOf(1L to null), v.block("block-morning").timed.ids())
        assertEquals(listOf(2L to null), v.block("block-night").flexible.ids())
        assertFalse(v.block("block-morning").timed.single().done)
        assertTrue(v.block("block-night").flexible.single().done)
        assertFalse("today is not a guess", v.block("block-night").flexible.single().projected)
    }

    @Test
    fun `an interval habit not due today is not on today`() {
        assertTrue(day(today, listOf(interval(1, every = 3, last = today.minusDays(1)))).anyTime.isEmpty())
    }

    /** T3 — ahead, an interval habit is shown where it would fall due if checked in on time, marked as a guess. */
    @Test
    fun `later days show an interval habit where it would next fall due, marked projected`() {
        val every3 = interval(1, every = 3, last = today.minusDays(1)) // next due: today + 2, then + 5
        assertEquals(listOf(false, true, false, false, true), (1L..5L).map { n -> day(today.plusDays(n), listOf(every3)).anyTime.isNotEmpty() })
        assertTrue(day(today.plusDays(2), listOf(every3)).anyTime.single().projected)
        val dueToday = interval(2, every = 2) // never checked: due today, then every 2 days after
        assertEquals(listOf(false, true, false, true), (1L..4L).map { n -> day(today.plusDays(n), listOf(dueToday)).anyTime.isNotEmpty() })
        assertFalse("a projected row is never done", day(today.plusDays(2), listOf(dueToday)).anyTime.single().done)
    }

    /** Presence, never absence (§0.5.2): a past day shows what was done there, nothing it was "owed". */
    @Test
    fun `a past day shows an interval habit only where it was checked in, and is read-only`() {
        val y = today.minusDays(1)
        val every1 = interval(1)
        val checked = interval(2)
        val v = day(y, listOf(every1, checked), listOf(done(2, date = y)))
        assertEquals(listOf(2L to null), v.anyTime.ids())
        assertTrue(v.anyTime.single().done)
        assertTrue(v.readOnly)
        assertFalse(day(today, listOf(every1)).readOnly)
        assertFalse("later days are editable (T2)", day(today.plusDays(7), listOf(every1)).readOnly)
    }

    @Test
    fun `a calendar habit shows on its days, past ones included, done as its check-ins say`() {
        val gym = cal(1, CalendarRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        assertEquals(1, day(today, listOf(gym)).anyTime.size)
        assertTrue(day(today.plusDays(1), listOf(gym)).anyTime.isEmpty())
        val monday = today.minusDays(2)
        val v = day(monday, listOf(gym), listOf(done(1, date = monday, key = "1")))
        assertTrue(v.anyTime.single().done)
        assertEquals(1, v.doneCount)
    }

    @Test
    fun `a trashed habit is on no day, and a trashed block sends its habits to Any time`() {
        assertTrue(day(today, listOf(cal(1, CalendarRule.Daily, block = "block-morning", deleted = true))).block("block-morning").flexible.isEmpty())
        val gone = DEFAULT_HABIT_BLOCKS.map { if (it.uid == "block-night") it.copy(deletedAt = at) else it }
        val v = day(today, listOf(cal(1, CalendarRule.Daily, block = "block-night")), blocks = gone)
        assertEquals(4, v.blocks.size)
        assertEquals(listOf(1L to "1"), v.anyTime.ids())
    }

    /** D4 — the day's blocks as the day has them; an overlap is noted, nothing coloured. */
    @Test
    fun `a weekday change applies on its days, and an overlap it makes is noted`() {
        val blocks = DEFAULT_HABIT_BLOCKS.map { if (it.uid == "block-midday") it.copy(overrides = "WED@510-780") else it }
        val v = day(today, emptyList(), blocks = blocks)
        assertEquals(510, v.block("block-midday").start)
        assertEquals(listOf("block-morning" to "block-midday"), v.overlaps.map { it.first to it.second })
        assertTrue("not on a Thursday", day(today.plusDays(1), emptyList(), blocks = blocks).overlaps.isEmpty())
    }
}
