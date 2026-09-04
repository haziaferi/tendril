package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * §6.1 — how long one period of a habit is, in days.
 *
 * Extracted from [CheckInHabitUseCase], where it was private, because habit reminders need the
 * same answer: a reminder that disagrees with the streak logic about when a habit is next due
 * would tell the person one thing and score them by another. That is the shape of audit 2.2 --
 * one calculation living in one place, reimplemented at the second call site, drifting -- and
 * the cheapest moment to not repeat it is before the second copy exists.
 *
 * A month is 30 days here, matching what the check-in grace period already assumes.
 */
fun habitPeriodDays(habit: Habit): Int = when (habit.frequency.unit) {
    IntervalUnit.DAY -> habit.frequency.count
    IntervalUnit.WEEK -> habit.frequency.count * 7
    IntervalUnit.MONTH -> habit.frequency.count * 30
}

/**
 * The first date on or after [from] that this habit is due.
 *
 * A habit never checked in is due immediately — there is no history to count a period from, and
 * a brand-new habit that stayed silent until one period had elapsed would look broken.
 */
fun nextHabitDueDate(habit: Habit, from: LocalDate): LocalDate {
    val last = habit.lastCompletedDate ?: return from
    val earliest = last.plusDays(habitPeriodDays(habit).toLong())
    return if (earliest.isAfter(from)) earliest else from
}

/** Whether [habit] wants doing on [date] — nothing recorded yet, or a full period has passed. */
fun isHabitDueOn(habit: Habit, date: LocalDate): Boolean {
    val last = habit.lastCompletedDate ?: return true
    if (!last.isBefore(date)) return false // done today, or a clock that ran backwards
    return ChronoUnit.DAYS.between(last, date) >= habitPeriodDays(habit)
}

/**
 * When this habit should next be reminded about, or null if it never should.
 *
 * Null for a habit with no time of day: §3.3 makes the time optional, and a habit with no
 * particular hour has no hour to fire at — reminding at an arbitrary one would be inventing a
 * schedule the person did not set. Also null for a trashed habit, which has nothing to remind
 * about until it is restored.
 *
 * [now] is a parameter rather than read from the clock so this is decidable in a test; the
 * comparison is strictly in the future, matching §9.7's rule for Entries — an alarm scheduled
 * at or before now fires immediately, which turns a day of missed check-ins into a burst of
 * notifications for things already in the past.
 */
fun nextHabitReminderAt(habit: Habit, now: ZonedDateTime): Instant? {
    val time = habit.time ?: return null
    if (habit.deletedAt != null) return null

    val due = nextHabitDueDate(habit, now.toLocalDate())
    val candidate = ZonedDateTime.of(due, time, now.zone).toInstant()
    if (candidate.isAfter(now.toInstant())) return candidate

    // The hour has already gone by today. The next one is a whole period later, not tomorrow:
    // a habit due every three days should not start nagging daily because its slot was missed.
    return ZonedDateTime.of(due.plusDays(habitPeriodDays(habit).toLong()), time, now.zone).toInstant()
}
