package com.tendril.app.domain

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitCompletionDao
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
class CheckInHabitUseCase(
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
) {
    /**
     * §0.10 item 3 — a **counting** habit ([Habit.amountPerCheckIn] set) logs every tap: a new
     * completion carrying [value] (the amount typed) or the habit's amount per check-in, as many
     * times a day as it is tapped; the day still counts once for the streak and
     * `lastCompletedDate`. A plain habit keeps its one check a day.
     */
    suspend fun checkIn(habitId: Long, today: LocalDate = LocalDate.now(), value: Double? = null) {
        val habit = habitDao.getById(habitId) ?: return
        val counting = habit.amountPerCheckIn != null
        if (counting) {
            habitCompletionDao.insert(HabitCompletion(habitId = habitId, date = today, checkedAt = Instant.now(), value = value ?: habit.amountPerCheckIn))
            if (habit.lastCompletedDate == today) return // the day already counts; only the amount grew
        } else if (habit.lastCompletedDate == today) return // already checked in today

        // §0.6.6 — the log, written first and unconditionally on a fresh check-in. A new row rather
        // than a revived one even when today was checked in and undone earlier: the tombstone
        // stays, and §9.4's "deleted wins" merge stays a rule with no exceptions. Guarded on
        // "no live row for today" so a stale second tap cannot log the same day twice from one
        // device; two devices offline can, and every read tolerates that (see [HabitCompletion]).
        if (!counting && habitCompletionDao.getLiveForDay(habitId, today).isEmpty()) {
            habitCompletionDao.insert(HabitCompletion(habitId = habitId, date = today, checkedAt = Instant.now()))
        }

        val periodDays = habitPeriodDays(habit)
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

        val now = Instant.now()
        val live = habitCompletionDao.getLiveForDay(habitId, today)
        if (habit.amountPerCheckIn != null && live.size > 1) {
            // Item 3 — a counting habit undoes its *last* cup; the day still stands on the rest.
            habitCompletionDao.softDelete(live.maxByOrNull { it.checkedAt }!!.id, now)
            return
        }
        // §0.6.6 — tombstone today's live rows (plural only after an offline double-check-in).
        for (row in live) habitCompletionDao.softDelete(row.id, now)

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
}
