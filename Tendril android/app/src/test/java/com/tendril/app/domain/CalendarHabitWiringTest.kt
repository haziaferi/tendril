package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleEdit
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.journal.todayHabits
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditChanges
import com.tendril.app.domain.plan.EditScope
import com.tendril.app.domain.plan.HabitCalendar
import com.tendril.app.domain.plan.HabitCalendarSource
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.encodeChanges
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.encodeScope
import com.tendril.app.domain.plan.habitDoneOn
import com.tendril.app.domain.plan.habitStrokes
import com.tendril.app.sync.FakeHabitBlockDao
import com.tendril.app.sync.FakeHabitCompletionDao
import com.tendril.app.sync.FakeHabitDao
import com.tendril.app.sync.FakeHabitScheduleEditDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * §6.3 (v27, plan Phase 3 step 3) — every reader outside the Habits tab asks the engine about a
 * calendar habit, and an interval habit answers exactly as before. The interval half is also
 * pinned by the suites that predate v27 (`HabitScheduleTest`, `HabitStrokesTest`,
 * `JournalTodayTest`, `CheckInHabitLogTest`), which now pass an empty calendar and are unchanged.
 */
class CalendarHabitWiringTest {

    private val at = Instant.ofEpochMilli(1_000)
    private val wed = LocalDate.of(2026, 9, 30)
    private val thu = wed.plusDays(1)
    private val calendar = HabitCalendar(DEFAULT_HABIT_BLOCKS, emptyList())

    private fun calendarHabit(id: Long, rule: CalendarRule?, time: LocalTime? = null, blockUid: String? = null, ruleText: String? = rule?.let(::encodeRule)) = Habit(
        id = id, uid = "h$id", title = "Habit $id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY),
        createdAt = at, updatedAt = at, scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = ruleText, blockUid = blockUid,
    )

    private fun interval(id: Long, time: LocalTime? = null, last: LocalDate? = null) = Habit(
        id = id, uid = "i$id", title = "Interval $id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY),
        lastCompletedDate = last, createdAt = at, updatedAt = at,
    )

    private fun done(habitId: Long, key: String?, date: LocalDate = wed) =
        HabitCompletion(habitId = habitId, date = date, checkedAt = at, occurrenceKey = key)

    private val waterThreeTimes = calendarHabit(1, CalendarRule.TimesPerDay(3, listOf(Slot.At(480), Slot.InBlock("block-evening"), Slot.At(1230))))

    @Test
    fun `a calendar habit is due on the days of its rule and on no other`() {
        val gym = calendarHabit(2, CalendarRule.Weekdays(setOf(MONDAY, WEDNESDAY)))
        assertTrue(isHabitDueOn(gym, wed, calendar))
        assertFalse(isHabitDueOn(gym, thu, calendar))
        assertFalse("trashed", isHabitDueOn(gym.copy(deletedAt = at), wed, calendar))
        assertFalse("a rule this build cannot read has no days", isHabitDueOn(calendarHabit(3, null, ruleText = "HOURLY"), wed, calendar))
        assertFalse("a calendar habit with no rule has no days", isHabitDueOn(calendarHabit(4, null), wed, calendar))
    }

    @Test
    fun `an interval habit ignores the calendar entirely`() {
        val stretch = interval(5, last = wed.minusDays(1))
        assertTrue(isHabitDueOn(stretch, wed, calendar))
        assertFalse(isHabitDueOn(stretch.copy(lastCompletedDate = wed), wed, calendar))
    }

    @Test
    fun `a stored skip for a day takes the occurrence off that day, read through the edit's text`() {
        val gym = calendarHabit(2, CalendarRule.Daily)
        val skip = HabitScheduleEdit(uid = "e1", target = "HABIT", refUid = gym.uid, scope = encodeScope(EditScope.Day(wed)), changes = encodeChanges(EditChanges(skip = true)), createdAt = at)
        assertFalse(isHabitDueOn(gym, wed, HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(skip))))
        assertTrue("an undone edit does nothing", isHabitDueOn(gym, wed, HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(skip.copy(deletedAt = at)))))
        assertTrue("an edit whose text cannot be read does nothing", isHabitDueOn(gym, wed, HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(skip.copy(changes = """{"skip":"yes"}""")))))
        assertTrue("the next day is untouched", isHabitDueOn(gym, thu, HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(skip))))
    }

    @Test
    fun `a trashed block takes its entries to Any time, where they still count`() {
        val read = calendarHabit(6, CalendarRule.Daily, blockUid = "block-night")
        val gone = DEFAULT_HABIT_BLOCKS.map { if (it.uid == "block-night") it.copy(deletedAt = at) else it }
        assertEquals(listOf("1"), HabitCalendar(gone, emptyList()).occurrences(read, wed).map { it.key })
    }

    @Test
    fun `the keys are in order, so the next open one is the lowest unchecked`() {
        assertEquals(listOf("1", "2", "3"), calendar.occurrences(waterThreeTimes, wed).map { it.key })
        assertEquals(listOf(480, null, 1230), calendar.occurrences(waterThreeTimes, wed).map { it.time })
    }

    /** Q5 — out of scope, and stated so it is not mistaken for a bug in the walk: on the phone and the desktop alike, through `habitFiring`. */
    @Test
    fun `a calendar habit with a time arms no reminder, an interval habit still does`() {
        val now = ZonedDateTime.of(wed, LocalTime.of(6, 0), ZoneId.of("UTC"))
        assertNull(nextHabitReminderAt(calendarHabit(2, CalendarRule.Daily, time = LocalTime.of(7, 0)), now))
        assertNull(com.tendril.app.domain.reminders.habitFiring(calendarHabit(2, CalendarRule.Daily, time = LocalTime.of(7, 0)), now))
        assertEquals(ZonedDateTime.of(wed, LocalTime.of(7, 0), ZoneId.of("UTC")).toInstant(), nextHabitReminderAt(interval(5, time = LocalTime.of(7, 0)), now))
    }

    @Test
    fun `today's strip holds a calendar habit on its days, done or not`() {
        val gym = calendarHabit(2, CalendarRule.Weekdays(setOf(MONDAY)))
        val habits = listOf(waterThreeTimes, gym, interval(5))
        assertEquals(listOf(1L, 5L), todayHabits(habits, wed, calendar).map { it.id }.sorted())
        assertEquals(listOf(1L), todayHabits(listOf(waterThreeTimes.copy(lastCompletedDate = wed)), wed, calendar).map { it.id })
    }

    /** H3 — one row reads done once every one of the day's occurrences is. */
    @Test
    fun `a several-a-day habit's row reads done only when all of today's occurrences are`() {
        assertFalse(habitDoneOn(waterThreeTimes, wed, calendar, emptyList()))
        assertFalse(habitDoneOn(waterThreeTimes, wed, calendar, listOf(done(1, "1"), done(1, "2"))))
        assertFalse("another day's check-ins", habitDoneOn(waterThreeTimes, wed, calendar, listOf(done(1, "1", thu), done(1, "2", thu), done(1, "3", thu))))
        assertFalse("an undone one", habitDoneOn(waterThreeTimes, wed, calendar, listOf(done(1, "1"), done(1, "2"), done(1, "3").copy(deletedAt = at))))
        assertTrue(habitDoneOn(waterThreeTimes, wed, calendar, listOf(done(1, "1"), done(1, "2"), done(1, "3"))))
        assertFalse("a day with no occurrence is not done", habitDoneOn(calendarHabit(2, CalendarRule.Weekdays(setOf(MONDAY))), wed, calendar, emptyList()))
        assertTrue("an interval habit: checked in today", habitDoneOn(interval(5, last = wed), wed, calendar, emptyList()))
    }

    /** H4 — a stroke for each occurrence with a set time, only on its days; the block-only one stays off. */
    @Test
    fun `a calendar habit draws one stroke per timed occurrence, dotted per occurrence`() {
        val strokes = habitStrokes(listOf(waterThreeTimes, interval(5, time = LocalTime.of(7, 0))), wed, wed, calendar, listOf(done(1, "3")))
        assertEquals(listOf(420 to false, 480 to false, 1230 to true), strokes.map { it.startMinute to it.checkedIn })
        assertTrue("not on a day it has no occurrence", habitStrokes(listOf(calendarHabit(2, CalendarRule.Weekdays(setOf(MONDAY)), time = LocalTime.of(7, 0))), wed, wed, calendar, emptyList()).isEmpty())
        assertTrue("a block-only habit has no hour", habitStrokes(listOf(calendarHabit(3, CalendarRule.Daily, blockUid = "block-morning")), wed, wed, calendar, emptyList()).isEmpty())
        assertFalse("a dot is today's alone", habitStrokes(listOf(waterThreeTimes), thu, wed, calendar, listOf(done(1, "3"))).any { it.checkedIn })
    }

    /** P3, H3 — the check-in funnel: one row per occurrence, the next open one when none is named. */
    @Test
    fun `a calendar check-in is keyed by occurrence, and the next open one when none is named`() = runBlocking {
        val habits = FakeHabitDao(listOf(waterThreeTimes))
        val log = FakeHabitCompletionDao()
        val useCase = CheckInHabitUseCase(habits, log, HabitCalendarSource(FakeHabitBlockDao(DEFAULT_HABIT_BLOCKS), FakeHabitScheduleEditDao(), log))

        useCase.checkIn(1, wed)
        useCase.checkIn(1, wed, occurrenceKey = "3")
        useCase.checkIn(1, wed, occurrenceKey = "3") // a stale second tap
        useCase.checkIn(1, wed, occurrenceKey = "9") // not one of today's
        assertEquals(listOf("1", "3"), log.getLiveForDay(1, wed).map { it.occurrenceKey })
        useCase.checkIn(1, wed)
        useCase.checkIn(1, wed) // all three done: nothing left to check
        assertEquals(listOf("1", "3", "2"), log.getLiveForDay(1, wed).map { it.occurrenceKey })

        val h = habits.getById(1)!!
        assertEquals("Q4 — no streak on a calendar habit", 0, h.streak)
        assertEquals(wed, h.lastCompletedDate)

        useCase.undoCheckIn(1, wed, occurrenceKey = "3")
        assertEquals(listOf("1", "2"), log.getLiveForDay(1, wed).map { it.occurrenceKey })
        assertEquals("the day still counts on the rest", wed, habits.getById(1)!!.lastCompletedDate)
        useCase.undoCheckIn(1, wed, occurrenceKey = "3") // already undone: nothing
        useCase.undoCheckIn(1, wed)
        useCase.undoCheckIn(1, wed)
        assertTrue(log.getLiveForDay(1, wed).isEmpty())
        assertNull("the last undo takes the day back", habits.getById(1)!!.lastCompletedDate)
    }

    /** Review of Phase 3: a scoped length change reached the Habits tab's engine but not the grid. */
    @Test
    fun `a stroke is as long as its occurrence, a scoped length change included`() {
        val walk = calendarHabit(7, CalendarRule.Daily, time = LocalTime.of(7, 0)).copy(duration = java.time.Duration.ofMinutes(30))
        val longer = HabitScheduleEdit(uid = "e2", target = "HABIT", refUid = walk.uid, scope = encodeScope(EditScope.Day(wed)), changes = encodeChanges(EditChanges(minutes = 90)), createdAt = at)
        assertEquals(listOf(90), habitStrokes(listOf(walk), wed, wed, HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(longer)), emptyList()).map { it.minutes })
        assertEquals(listOf(30), habitStrokes(listOf(walk), thu, wed, HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(longer)), emptyList()).map { it.minutes })
    }

    /**
     * Review of Phase 3: a count no build of Tendril writes — from a corrupt file, or a newer peer's
     * meaning of it — must do nothing, not run every reader out of memory. `n * 60` wrapped to a
     * negative step and the every-few-hours loop never ended; two billion a day built two billion.
     */
    @Test(timeout = 10_000)
    fun `a rule with a runaway count has no occurrences, stored or reached by an edit`() {
        for (text in listOf("EVERY_N_HOURS:71582788:00:00:23:59", "TIMES_PER_DAY:2000000000", "EVERY_N_HOURS:0:08:00:20:00")) {
            assertEquals(text, emptyList<Any>(), calendar.occurrences(calendarHabit(8, null, ruleText = text), wed))
        }
        val steady = calendarHabit(9, CalendarRule.EveryNHours(2, 480, 1200))
        val runaway = HabitScheduleEdit(uid = "e3", target = "HABIT", refUid = steady.uid, scope = encodeScope(EditScope.From(wed)), changes = """{"rulePatch":{"n":71582788}}""", createdAt = at)
        assertEquals(emptyList<Any>(), HabitCalendar(DEFAULT_HABIT_BLOCKS, listOf(runaway)).occurrences(steady, wed))
    }

    /** §6.3 (5c) — a pause reaches an interval habit too: not due while paused, due again after. */
    @Test
    fun `a paused interval habit is not due on its paused days, and is again after`() {
        val stretch = interval(5).copy(pauseFrom = wed, pauseUntil = thu)
        assertFalse(isHabitDueOn(stretch, wed, calendar))
        assertFalse(isHabitDueOn(stretch, thu, calendar))
        assertTrue(isHabitDueOn(stretch, thu.plusDays(1), calendar))
        assertFalse("an open pause", isHabitDueOn(interval(5).copy(pauseFrom = wed), wed.plusDays(30), calendar))
        assertTrue("not on the Day view either", com.tendril.app.domain.plan.habitDay(wed, wed, listOf(stretch), DEFAULT_HABIT_BLOCKS, emptyList(), emptyList()).anyTime.isEmpty())
    }

    /** A paused habit's reminder waits for the pause's end, and an open pause arms nothing. */
    @Test
    fun `a paused interval habit's reminder moves past the pause, an open pause arms none`() {
        val now = ZonedDateTime.of(wed, LocalTime.of(6, 0), ZoneId.of("UTC"))
        val until = interval(5, time = LocalTime.of(7, 0)).copy(pauseFrom = wed, pauseUntil = thu)
        assertEquals(ZonedDateTime.of(thu.plusDays(1), LocalTime.of(7, 0), ZoneId.of("UTC")).toInstant(), nextHabitReminderAt(until, now))
        assertNull(nextHabitReminderAt(interval(5, time = LocalTime.of(7, 0)).copy(pauseFrom = wed), now))
    }
}
