package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.domain.plan.BlockEditError
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.DraftError
import com.tendril.app.domain.plan.RepeatKind
import com.tendril.app.domain.plan.ScheduleDraft
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.blockEditError
import com.tendril.app.domain.plan.draftOf
import com.tendril.app.domain.plan.editBlockTimes
import com.tendril.app.domain.plan.error
import com.tendril.app.domain.plan.hasWhere
import com.tendril.app.domain.plan.pausedOn
import com.tendril.app.domain.plan.toRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate

/** §6.3 (plan Phase 5c) — the sheet's calendar schedule, the blocks editor's rule, and pause. Written before the bodies. */
class ScheduleDraftTest {

    private val today = LocalDate.of(2026, 9, 30)
    private fun draft(kind: RepeatKind, n: Int = 2, days: Set<java.time.DayOfWeek> = emptySet(), times: List<Int> = emptyList(), from: Int = 480, until: Int = 1200) =
        ScheduleDraft(kind, n, days, today, times, from, until, today.plusDays(3))

    @Test
    fun `every choice makes its rule`() {
        assertEquals(CalendarRule.Daily, draft(RepeatKind.DAILY).toRule())
        assertEquals(CalendarRule.Weekdays(setOf(MONDAY, WEDNESDAY)), draft(RepeatKind.WEEKDAYS, days = setOf(MONDAY, WEDNESDAY)).toRule())
        assertEquals(CalendarRule.EveryNDays(3, today), draft(RepeatKind.EVERY_N_DAYS, n = 3).toRule())
        assertEquals(CalendarRule.EveryNWeeks(2, today, setOf(FRIDAY)), draft(RepeatKind.EVERY_N_WEEKS, days = setOf(FRIDAY)).toRule())
        assertEquals("no times: spread over the blocks", CalendarRule.TimesPerDay(3), draft(RepeatKind.TIMES_PER_DAY, n = 3).toRule())
        assertEquals(CalendarRule.TimesPerDay(2, listOf(Slot.At(480), Slot.At(1200))), draft(RepeatKind.TIMES_PER_DAY, n = 2, times = listOf(1200, 480)).toRule())
        assertEquals("no days chosen: any day", CalendarRule.TimesPerWeek(3), draft(RepeatKind.TIMES_PER_WEEK, n = 3).toRule())
        assertEquals(CalendarRule.EveryNHours(3, 480, 1200), draft(RepeatKind.EVERY_N_HOURS, n = 3).toRule())
        assertEquals(CalendarRule.Once(today.plusDays(3)), draft(RepeatKind.ONCE).toRule())
    }

    @Test
    fun `a draft that makes no rule says why`() {
        assertEquals(DraftError.NoDays, draft(RepeatKind.WEEKDAYS).error())
        assertEquals(DraftError.NoDays, draft(RepeatKind.EVERY_N_WEEKS).error())
        assertEquals(DraftError.NumberRange(2, 365), draft(RepeatKind.EVERY_N_DAYS, n = 1).error())
        assertEquals(DraftError.NumberRange(1, 48), draft(RepeatKind.TIMES_PER_DAY, n = 49).error())
        assertEquals(DraftError.NumberRange(1, 7), draft(RepeatKind.TIMES_PER_WEEK, n = 8).error())
        assertEquals(DraftError.NumberRange(1, 24), draft(RepeatKind.EVERY_N_HOURS, n = 0).error())
        assertEquals(DraftError.TimesCount(3), draft(RepeatKind.TIMES_PER_DAY, n = 3, times = listOf(480)).error())
        assertEquals(DraftError.Until, draft(RepeatKind.EVERY_N_HOURS, n = 2, from = 1200, until = 480).error())
        assertNull(draft(RepeatKind.DAILY).error())
    }

    @Test
    fun `a rule reads back into the sheet as the same rule`() {
        val rules = listOf(
            CalendarRule.Daily, CalendarRule.Weekdays(setOf(MONDAY)), CalendarRule.EveryNDays(4, today.minusDays(9)),
            CalendarRule.EveryNWeeks(3, today, setOf(FRIDAY)), CalendarRule.TimesPerDay(2), CalendarRule.TimesPerDay(2, listOf(Slot.At(420), Slot.At(1260))),
            CalendarRule.TimesPerWeek(3, setOf(MONDAY, WEDNESDAY, FRIDAY)), CalendarRule.EveryNHours(2, 360, 1320), CalendarRule.Once(today.plusDays(10)),
        )
        for (r in rules) assertEquals(r, draftOf(r, today).toRule())
    }

    @Test
    fun `where is asked unless the rule places each entry itself`() {
        assertTrue(draft(RepeatKind.DAILY).hasWhere())
        assertFalse("times a day places each entry itself — spread over the blocks, or at its times", draft(RepeatKind.TIMES_PER_DAY, n = 2).hasWhere())
        assertFalse(draft(RepeatKind.EVERY_N_HOURS).hasWhere())
        assertFalse(draft(RepeatKind.TIMES_PER_DAY, n = 2, times = listOf(480, 1200)).hasWhere())
    }

    private val now = Instant.ofEpochMilli(5_000)

    /** The planner's `actBlock`: an end moves the next block's start; past the next block's end is refused. */
    @Test
    fun `changing an end moves the next block's start, and pushing past its end is refused`() {
        val moved = editBlockTimes(DEFAULT_HABIT_BLOCKS, "block-morning", 390, 570, now)
        assertEquals(listOf("block-morning" to (390 to 570), "block-midday" to (570 to 780)), moved.map { it.uid to (it.startMinute to it.endMinute) })
        assertTrue(moved.all { it.updatedAt == now })
        val refused = blockEditError(DEFAULT_HABIT_BLOCKS, "block-morning", 390, 780)
        assertEquals(BlockEditError.PastNext(DEFAULT_HABIT_BLOCKS[1], 780), refused)
        assertNull(blockEditError(DEFAULT_HABIT_BLOCKS, "block-morning", 390, 779))
    }

    @Test
    fun `changing a start moves the previous block's end, the first start and the last end are free`() {
        val moved = editBlockTimes(DEFAULT_HABIT_BLOCKS, "block-midday", 510, 780, now)
        assertEquals(listOf("block-morning" to (390 to 510), "block-midday" to (510 to 780)), moved.map { it.uid to (it.startMinute to it.endMinute) }.sortedBy { it.second.first })
        assertEquals(BlockEditError.BeforePrev(DEFAULT_HABIT_BLOCKS[0], 390), blockEditError(DEFAULT_HABIT_BLOCKS, "block-midday", 390, 780))
        assertEquals(listOf("block-morning"), editBlockTimes(DEFAULT_HABIT_BLOCKS, "block-morning", 300, 540, now).map { it.uid })
        assertEquals(listOf("block-night"), editBlockTimes(DEFAULT_HABIT_BLOCKS, "block-night", 1290, 1440, now).map { it.uid })
        assertEquals(BlockEditError.Order, blockEditError(DEFAULT_HABIT_BLOCKS, "block-night", 1290, 1290))
        assertEquals(BlockEditError.Order, blockEditError(DEFAULT_HABIT_BLOCKS, "block-night", 1290, 1441))
    }

    @Test
    fun `a pause covers its days, both ends included, and an open one every day from its start`() {
        val h = Habit(uid = "h", title = "t", frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = now, updatedAt = now)
        assertFalse(h.pausedOn(today))
        val open = h.copy(pauseFrom = today)
        assertFalse(open.pausedOn(today.minusDays(1))); assertTrue(open.pausedOn(today)); assertTrue(open.pausedOn(today.plusDays(400)))
        val until = h.copy(pauseFrom = today, pauseUntil = today.plusDays(2))
        assertTrue(until.pausedOn(today.plusDays(2))); assertFalse(until.pausedOn(today.plusDays(3)))
    }
}
