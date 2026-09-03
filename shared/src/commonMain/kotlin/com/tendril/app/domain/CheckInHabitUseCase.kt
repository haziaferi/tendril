package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitDao
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * §6.1's "no backlog" test, applied to check-in: a gap within one period-plus-a-day of grace
 * continues the streak; anything longer resets to 1 rather than ever showing debt.
 *
 * Extracted from `TasksHabitsViewModel` (originally the only call site) so the home-screen
 * Habits widget (§8 — added against Loop/uhabits prior art; checking a habit off from the
 * home screen without opening the app is their core interaction) can check a habit in through
 * the exact same streak math, rather than a Glance `ActionCallback` reimplementing it — the
 * same centralization principle as [ResolveEntryUseCase] (§9.8 R1) and [EntryScheduleCoordinator].
 */
class CheckInHabitUseCase(private val habitDao: HabitDao) {
    suspend fun checkIn(habitId: Long, today: LocalDate = LocalDate.now()) {
        val habit = habitDao.getById(habitId) ?: return
        if (habit.lastCompletedDate == today) return // already checked in today

        val periodDays = periodDays(habit)
        val withinGrace = habit.lastCompletedDate != null &&
            ChronoUnit.DAYS.between(habit.lastCompletedDate, today) <= periodDays + 1
        val newStreak = if (withinGrace) habit.streak + 1 else 1

        habitDao.update(
            habit.copy(
                streak = newStreak,
                previousStreak = habit.streak,
                lastCompletedDate = today,
                previousCompletedDate = habit.lastCompletedDate,
                updatedAt = Instant.now(),
            )
        )
    }

    /** §8.1.1 — reverts exactly the state [checkIn] overwrote, using the snapshot it stashed
     * rather than recomputing (a weekly+ habit's grace period means "subtract a day" can't
     * reconstruct what `lastCompletedDate` actually was). Only undoes today's check-in — a
     * no-op if today was never checked in, so a stale/duplicate tap can't corrupt older state. */
    suspend fun undoCheckIn(habitId: Long, today: LocalDate = LocalDate.now()) {
        val habit = habitDao.getById(habitId) ?: return
        if (habit.lastCompletedDate != today) return // nothing to undo

        habitDao.update(
            habit.copy(
                streak = habit.previousStreak,
                previousStreak = 0,
                lastCompletedDate = habit.previousCompletedDate,
                previousCompletedDate = null,
                updatedAt = Instant.now(),
            )
        )
    }

    private fun periodDays(habit: Habit) = when (habit.frequency.unit) {
        IntervalUnit.DAY -> habit.frequency.count
        IntervalUnit.WEEK -> habit.frequency.count * 7
        IntervalUnit.MONTH -> habit.frequency.count * 30
    }
}
