package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleEdit
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditScope
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.byArea
import com.tendril.app.domain.plan.confirmWeekDays
import com.tendril.app.domain.plan.encodeChanges
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.encodeScope
import com.tendril.app.domain.plan.habitDay
import com.tendril.app.domain.plan.habitWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.THURSDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** §6.3 (plan Phase 5d) — the Week view, Q1's confirmation, and the Day view by area. Written before the bodies. */
class HabitWeekTest {

    private val at = Instant.ofEpochMilli(1_000)
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private val monday = LocalDate.of(2026, 9, 28)

    private fun cal(id: Long, rule: CalendarRule, time: LocalTime? = null, label: Long? = null) = Habit(
        id = id, uid = "c$id", title = "H$id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at,
        scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = encodeRule(rule), labelId = label,
    )

    private fun week(habits: List<Habit>, edits: List<HabitScheduleEdit> = emptyList(), completions: List<HabitCompletion> = emptyList(), of: LocalDate = monday) =
        habitWeek(of, today, habits, DEFAULT_HABIT_BLOCKS, edits, completions)

    @Test
    fun `the week is seven days, Monday first, each built as the Day view builds it`() {
        val w = week(listOf(cal(1, CalendarRule.Daily)), completions = listOf(HabitCompletion(habitId = 1, date = monday, checkedAt = at, occurrenceKey = "1")))
        assertEquals((0L..6L).map { monday.plusDays(it) }, w.days.map { it.date })
        assertEquals(habitDay(monday, today, listOf(cal(1, CalendarRule.Daily)), DEFAULT_HABIT_BLOCKS, emptyList(), listOf(HabitCompletion(habitId = 1, date = monday, checkedAt = at, occurrenceKey = "1"))), w.days[0])
        assertTrue(w.days[0].readOnly); assertTrue(!w.days[2].readOnly)
    }

    /** Q1, D5 — suggested, never placed; and in the current week, from today on. */
    @Test
    fun `times a week is suggested from today on, placing nothing, and none for a past week`() {
        val yoga = cal(1, CalendarRule.TimesPerWeek(2))
        val w = week(listOf(yoga))
        val card = w.suggestions.single()
        assertEquals(2, card.n)
        assertEquals(2, card.days.size)
        assertTrue("not a day already past", card.days.all { !it.isBefore(today) })
        assertTrue("nothing placed", w.days.all { d -> d.anyTime.isEmpty() && d.blocks.all { it.timed.isEmpty() && it.flexible.isEmpty() } })
        assertTrue(week(listOf(yoga), of = monday.minusWeeks(1)).suggestions.isEmpty())
        assertEquals("a later week: any of its days", 2, week(listOf(yoga), of = monday.plusWeeks(1)).suggestions.single().days.size)
    }

    @Test
    fun `confirming writes the week's days for that week alone, and the week then places them`() {
        val yoga = cal(1, CalendarRule.TimesPerWeek(2))
        val e = confirmWeekDays(yoga, monday, setOf(THURSDAY, FRIDAY), at, "e1")
        assertEquals(EditScope.Week(monday), e.scope)
        assertEquals(setOf(THURSDAY, FRIDAY), e.changes.weekDays)
        val stored = HabitScheduleEdit(uid = e.uid, target = "HABIT", refUid = "c1", scope = encodeScope(e.scope), changes = encodeChanges(e.changes), createdAt = at)
        val w = week(listOf(yoga), edits = listOf(stored))
        assertTrue("confirmed: no card", w.suggestions.isEmpty())
        assertEquals(listOf(false, false, false, true, true, false, false), w.days.map { d -> d.anyTime.isNotEmpty() })
        assertTrue("not the next week", week(listOf(yoga), edits = listOf(stored), of = monday.plusWeeks(1)).suggestions.isNotEmpty())
    }

    /** V1, P4 — by area: grouped by Label, *No area* last; several a day collapsed to one row (R3 as drawn). */
    @Test
    fun `by area groups by Label, No area last, and a several-a-day habit is one row`() {
        val water = cal(1, CalendarRule.TimesPerDay(3, listOf(Slot.At(480), Slot.At(720), Slot.At(1200))), label = 7)
        val gym = cal(2, CalendarRule.Daily, time = LocalTime.of(18, 0), label = 9)
        val read = cal(3, CalendarRule.Daily, time = LocalTime.of(21, 0), label = 7)
        val plants = cal(4, CalendarRule.Daily)
        val done = listOf(HabitCompletion(habitId = 1, date = today, checkedAt = at, occurrenceKey = "1"))
        val v = habitDay(today, today, listOf(water, gym, read, plants), DEFAULT_HABIT_BLOCKS, emptyList(), done)
        val groups = byArea(v, labelOrder = listOf(9, 7))
        assertEquals(listOf(9L, 7L, null), groups.map { it.labelId })
        assertEquals(listOf(1L, 3L), groups[1].rows.map { it.habit.id })
        val w = groups[1].rows[0]
        assertEquals("the first not done", "2", w.shown.key)
        assertEquals(2, w.more)
        assertEquals(listOf(4L), groups[2].rows.map { it.habit.id })
    }
}
