package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * §9.7 / audit §3 — when a habit's reminder should fire.
 *
 * `NotificationChannels.HABITS` was created and nothing ever posted to it, so the channel sat in
 * Android's notification settings as a control for a feature that did not exist. The scheduling
 * itself is AlarmManager and a receiver, neither reachable from a unit test; this is the part
 * that decides *when*, which is the part with behaviour worth pinning.
 *
 * `habitPeriodDays` is shared with [CheckInHabitUseCase] rather than reimplemented here. A
 * reminder that disagreed with the streak logic about when a habit is next due would tell the
 * person one thing and score them by another — the same duplication-drift that produced audit
 * 2.2, avoided before the second copy existed.
 */
class HabitScheduleTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun habit(
        count: Int = 1,
        unit: IntervalUnit = IntervalUnit.DAY,
        time: LocalTime? = LocalTime.of(9, 0),
        lastCompleted: LocalDate? = null,
        deleted: Boolean = false,
    ) = Habit(
        id = 1,
        title = "Stretch",
        time = time,
        frequency = HabitFrequency(count, unit),
        lastCompletedDate = lastCompleted,
        deletedAt = if (deleted) Instant.ofEpochMilli(1) else null,
        createdAt = Instant.ofEpochMilli(0),
        updatedAt = Instant.ofEpochMilli(0),
    )

    private fun at(date: String, time: String) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    // ------------------------------------------------------------ period length

    @Test
    fun `a period is counted in days whatever unit it was entered as`() {
        assertEquals(1, habitPeriodDays(habit(1, IntervalUnit.DAY)))
        assertEquals(3, habitPeriodDays(habit(3, IntervalUnit.DAY)))
        assertEquals(7, habitPeriodDays(habit(1, IntervalUnit.WEEK)))
        assertEquals(14, habitPeriodDays(habit(2, IntervalUnit.WEEK)))
        assertEquals(30, habitPeriodDays(habit(1, IntervalUnit.MONTH)))
    }

    // ------------------------------------------------------------ due-ness

    @Test
    fun `a habit never checked in is due immediately`() {
        // No history to count a period from, and a brand-new habit that stayed silent for a
        // period would look broken.
        assertTrue(isHabitDueOn(habit(lastCompleted = null), LocalDate.parse("2026-09-05")))
    }

    @Test
    fun `a habit done today is not due again today`() {
        val h = habit(lastCompleted = LocalDate.parse("2026-09-05"))
        assertFalse(isHabitDueOn(h, LocalDate.parse("2026-09-05")))
    }

    @Test
    fun `a daily habit becomes due the next day`() {
        val h = habit(count = 1, lastCompleted = LocalDate.parse("2026-09-04"))
        assertTrue(isHabitDueOn(h, LocalDate.parse("2026-09-05")))
    }

    @Test
    fun `a three-day habit is not due on day two`() {
        val h = habit(count = 3, lastCompleted = LocalDate.parse("2026-09-04"))
        assertFalse(isHabitDueOn(h, LocalDate.parse("2026-09-06")))
        assertTrue(isHabitDueOn(h, LocalDate.parse("2026-09-07")))
    }

    @Test
    fun `a date before the last check-in is not due`() {
        // A clock that ran backwards, or a device whose timezone moved. Not due beats "due
        // forever" -- the streak already covers that day.
        val h = habit(lastCompleted = LocalDate.parse("2026-09-05"))
        assertFalse(isHabitDueOn(h, LocalDate.parse("2026-09-01")))
    }

    // ------------------------------------------------------------ the next reminder

    @Test
    fun `a habit with no time of day is never reminded about`() {
        // §3.3 makes the time optional, and a habit with no hour has no hour to fire at.
        assertNull(nextHabitReminderAt(habit(time = null), at("2026-09-05", "08:00")))
    }

    @Test
    fun `a trashed habit is never reminded about`() {
        assertNull(nextHabitReminderAt(habit(deleted = true), at("2026-09-05", "08:00")))
    }

    @Test
    fun `a due habit reminds at its time today when that is still ahead`() {
        val next = nextHabitReminderAt(habit(lastCompleted = null), at("2026-09-05", "08:00"))
        assertEquals(at("2026-09-05", "09:00").toInstant(), next)
    }

    @Test
    fun `once the hour has passed the next reminder is a whole period later`() {
        // Not tomorrow: a habit due every three days should not start nagging daily because
        // its slot went by.
        val h = habit(count = 3, lastCompleted = null)
        val next = nextHabitReminderAt(h, at("2026-09-05", "10:00"))
        assertEquals(at("2026-09-08", "09:00").toInstant(), next)
    }

    @Test
    fun `a habit already done today reminds on its next due date`() {
        val h = habit(count = 1, lastCompleted = LocalDate.parse("2026-09-05"))
        val next = nextHabitReminderAt(h, at("2026-09-05", "08:00"))
        assertEquals(at("2026-09-06", "09:00").toInstant(), next)
    }

    @Test
    fun `a weekly habit reminds a week after its last check-in`() {
        val h = habit(count = 1, unit = IntervalUnit.WEEK, lastCompleted = LocalDate.parse("2026-09-01"))
        val next = nextHabitReminderAt(h, at("2026-09-05", "08:00"))
        assertEquals(at("2026-09-08", "09:00").toInstant(), next)
    }

    @Test
    fun `an overdue habit reminds today rather than being skipped`() {
        // Missed for a week. The next reminder is now, not a period after the missed slot --
        // §6.1's "no backlog" applies to the streak, not to whether it is still wanted.
        val h = habit(count = 1, lastCompleted = LocalDate.parse("2026-08-29"))
        val next = nextHabitReminderAt(h, at("2026-09-05", "08:00"))
        assertEquals(at("2026-09-05", "09:00").toInstant(), next)
    }

    @Test
    fun `the next reminder is always strictly in the future`() {
        // §9.7's rule for Entries, applied here: an alarm set at or before now fires at once,
        // which turns a run of missed days into a burst of notifications for the past.
        val now = at("2026-09-05", "09:00")
        for (count in 1..5) {
            for (last in listOf(null, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-05"))) {
                val next = nextHabitReminderAt(habit(count = count, lastCompleted = last), now)
                assertTrue("$count/$last produced $next", next == null || next.isAfter(now.toInstant()))
            }
        }
    }
}
