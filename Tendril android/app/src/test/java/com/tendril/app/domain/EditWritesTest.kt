package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditPos
import com.tendril.app.domain.plan.EditScope
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.Patch
import com.tendril.app.domain.plan.ScopeChoice
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.canMoveWeekly
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.habitDay
import com.tendril.app.domain.plan.moveChoices
import com.tendril.app.domain.plan.moveToDayWrites
import com.tendril.app.domain.plan.moveWrites
import com.tendril.app.domain.plan.originOf
import com.tendril.app.domain.plan.planMove
import com.tendril.app.domain.plan.skipChoices
import com.tendril.app.domain.plan.skipWrites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * §6.3 (plan Phase 5b) — what a settled move, a skip and *Move to…* write: scoped edits for a
 * calendar habit (D10, P5 through Phase 2's `placementChange`), the habit row for an interval one.
 * Written before the write side had bodies.
 */
class EditWritesTest {

    private val at = Instant.ofEpochMilli(1_000)
    private val now = Instant.ofEpochMilli(9_000)
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private var n = 0
    private val uid = { "e" + (++n) }

    private fun cal(id: Long, rule: CalendarRule = CalendarRule.Daily, time: LocalTime? = null, block: String? = null, order: Double = 0.0) = Habit(
        id = id, uid = "c$id", title = "H$id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at,
        scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = encodeRule(rule), blockUid = block, sortOrder = order,
    )

    private fun interval(id: Long, block: String? = null, time: LocalTime? = null) = Habit(
        id = id, uid = "i$id", title = "I$id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at, blockUid = block,
    )

    private fun view(vararg hs: Habit): HabitDayView = habitDay(today, today, hs.toList(), DEFAULT_HABIT_BLOCKS, emptyList(), emptyList())
    private fun HabitDayView.row(id: Long) = (blocks.flatMap { it.timed + it.flexible } + anyTime + outside).single { it.habit.id == id }

    @Test
    fun `a calendar habit moved to another block writes one scoped edit, and its new neighbours' order under the same scope`() {
        val v = view(cal(1, block = "block-morning"), cal(2, block = "block-midday", order = 5.0))
        val r = v.row(1)
        val plan = planMove(v, r, originOf(v, r), EditPos(1, timed = false, index = 1))!!
        assertTrue("a daily habit offers the wider scopes", ScopeChoice.THIS_WEEK in moveChoices(v, plan, null, emptyList()))
        val w = moveWrites(v, plan, null, ScopeChoice.THIS_WEEK, null, emptyList(), now, uid)
        assertTrue(w.habits.isEmpty())
        val mine = w.edits.single { it.refUid == "c1" }
        assertEquals(EditScope.Week(LocalDate.of(2026, 9, 28)), mine.scope)
        assertEquals(Patch("block-midday"), mine.changes.blockUid)
        assertEquals(20.0, mine.changes.sortOrder)
        val neighbour = w.edits.single { it.refUid == "c2" }
        assertEquals(EditScope.Week(LocalDate.of(2026, 9, 28)), neighbour.scope)
        assertEquals(10.0, neighbour.changes.sortOrder)
        assertEquals(now, mine.createdAt)
    }

    @Test
    fun `an interval habit's move changes the habit itself and asks no scope`() {
        val v = view(interval(1, block = "block-morning"), interval(2, time = LocalTime.of(7, 0)))
        val r = v.row(1)
        val plan = planMove(v, r, originOf(v, r), EditPos(1, timed = false, index = 0))!!
        assertTrue(moveChoices(v, plan, null, emptyList()).isEmpty())
        val w = moveWrites(v, plan, null, null, null, emptyList(), now, uid)
        assertTrue(w.edits.isEmpty())
        val h = w.habits.single()
        assertEquals(listOf<Any?>("block-midday", 10.0, now), listOf(h.blockUid, h.sortOrder, h.updatedAt))
        val timed = v.row(2)
        val out = planMove(v, timed, originOf(v, timed), EditPos(0, timed = false, index = 0))!!
        val h2 = moveWrites(v, out, null, null, null, emptyList(), now, uid).habits.single { it.id == 2L }
        assertEquals("leaving the set times takes the time off", null, h2.time)
        assertEquals("block-morning", h2.blockUid)
    }

    @Test
    fun `a typed time is written as the entry's set time`() {
        val v = view(cal(1, block = "block-morning"))
        val r = v.row(1)
        val plan = planMove(v, r, originOf(v, r), EditPos(0, timed = true, index = 0))!!
        val e = moveWrites(v, plan, 7 * 60 + 15, ScopeChoice.ONLY_THIS_ENTRY, null, emptyList(), now, uid).edits.single()
        assertEquals(Patch(435), e.changes.time)
        assertEquals(EditScope.Occurrence(today, "1"), e.scope)
    }

    /** P5 through Phase 2's `placementChange`: a several-a-day habit keeps one slot per entry in its rule. */
    @Test
    fun `a several-a-day entry moved for every week rewrites its slot in the rule`() {
        val water = cal(1, CalendarRule.TimesPerDay(2, listOf(Slot.InBlock("block-morning"), Slot.InBlock("block-evening"))))
        val v = view(water)
        val first = v.blocks.first { it.block.uid == "block-morning" }.flexible.single()
        val plan = planMove(v, first, originOf(v, first), EditPos(1, timed = false, index = 0))!!
        val e = moveWrites(v, plan, null, ScopeChoice.FROM_NOW_ON, null, emptyList(), now, uid).edits.single()
        assertEquals(EditScope.From(today), e.scope)
        assertEquals(CalendarRule.TimesPerDay(2, listOf(Slot.InBlock("block-midday"), Slot.InBlock("block-evening"))), e.changes.rule)
    }

    @Test
    fun `skip is offered for a calendar entry and written under its scope, never for an interval habit`() {
        val v = view(cal(1, CalendarRule.Weekdays(setOf(DayOfWeek.WEDNESDAY))), interval(2))
        assertTrue(skipChoices(v, v.row(2)).isEmpty())
        val choices = skipChoices(v, v.row(1))
        assertTrue(ScopeChoice.ONLY_THIS_ENTRY in choices)
        assertFalse("once a week: This day and This week only are the same entry", ScopeChoice.THIS_DAY in choices)
        val e = skipWrites(v, v.row(1), ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON, setOf(DayOfWeek.WEDNESDAY), now, uid)
        assertEquals(EditScope.From(today, setOf(DayOfWeek.WEDNESDAY)), e.scope)
        assertEquals(true, e.changes.skip)
    }

    @Test
    fun `moved to another day once, it is skipped here and added there`() {
        val v = view(cal(1, CalendarRule.Weekdays(setOf(DayOfWeek.WEDNESDAY)), block = "block-morning"))
        val out = moveToDayWrites(v, v.row(1), today.plusDays(1), "block-evening", removeTime = false, weekly = false, edits = emptyList(), now = now, newUid = uid)
        assertEquals(listOf(EditScope.Occurrence(today, "1"), EditScope.Extra(today.plusDays(1))), out.map { it.scope })
        assertEquals(true, out[0].changes.skip)
        assertEquals(Patch("block-evening"), out[1].changes.blockUid)
    }

    @Test
    fun `moved to another day every week, a weekday rule trades the day from the earlier of the two`() {
        val gym = cal(1, CalendarRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val v = view(gym)
        assertTrue(canMoveWeekly(v.row(1), emptyList(), today))
        val e = moveToDayWrites(v, v.row(1), today.plusDays(1), "block-afternoon", removeTime = false, weekly = true, edits = emptyList(), now = now, newUid = uid).single()
        assertEquals(EditScope.From(today), e.scope)
        assertEquals(CalendarRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), e.changes.rule)
        assertEquals(Patch("block-afternoon"), e.changes.blockUid)
        assertFalse("a daily habit has no weekday to trade", canMoveWeekly(view(cal(2)).row(2), emptyList(), today))
    }

    @Test
    fun `a set time that does not fit the other day's block is removed when the sheet said so`() {
        val v = view(cal(1, CalendarRule.Weekdays(setOf(DayOfWeek.WEDNESDAY)), time = LocalTime.of(7, 0)))
        val out = moveToDayWrites(v, v.row(1), today.plusDays(1), "block-evening", removeTime = true, weekly = false, edits = emptyList(), now = now, newUid = uid)
        assertEquals(Patch(null), out[1].changes.time)
        assertEquals(Patch("block-evening"), out[1].changes.blockUid)
    }
}
