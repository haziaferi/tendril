package com.tendril.app.domain

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.plan.HabitCalendarSource
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
    /** §6.3 — a calendar habit's occurrences, which its check-ins are keyed by (P3). Required: a
     * default would let a call site check calendar habits in against no calendar at all. */
    private val calendar: HabitCalendarSource,
) {
    /**
     * §0.10 item 3 — a **counting** habit ([Habit.amountPerCheckIn] set) logs every tap: a new
     * completion carrying [value] (the amount typed) or the habit's amount per check-in, as many
     * times a day as it is tapped; the day still counts once for the streak and
     * `lastCompletedDate`. A plain habit keeps its one check a day.
     */
    suspend fun checkIn(habitId: Long, today: LocalDate = LocalDate.now(), value: Double? = null, occurrenceKey: String? = null) {
        val habit = habitDao.getById(habitId) ?: return
        if (habit.scheduleKind == HabitScheduleKind.CALENDAR) return checkInOccurrence(habit, today, value, occurrenceKey)
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
    suspend fun undoCheckIn(habitId: Long, today: LocalDate = LocalDate.now(), occurrenceKey: String? = null) {
        val habit = habitDao.getById(habitId) ?: return
        if (habit.lastCompletedDate != today) return // nothing to undo
        if (habit.scheduleKind == HabitScheduleKind.CALENDAR) return undoOccurrence(habit, today, occurrenceKey)

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

    /**
     * §6.3 (P3, H3) — a calendar habit: one check-in per occurrence, keyed by it. [occurrenceKey]
     * names the occurrence (the Habits tab); null takes the first of today's not yet checked
     * (the widget and the strip, which show the habit as one row). A key that is not one of
     * today's occurrences, or is already checked, writes nothing — a stale tap cannot log a day twice.
     * The streak stays 0 (Q4); `lastCompletedDate` records the day, with the one before it kept
     * for [undoOccurrence].
     */
    private suspend fun checkInOccurrence(habit: Habit, today: LocalDate, value: Double?, occurrenceKey: String?) {
        val keys = calendar.load().occurrences(habit, today).map { it.key }
        val done = habitCompletionDao.getLiveForDay(habit.id, today).mapNotNull { it.occurrenceKey }.toSet()
        val key = occurrenceKey ?: keys.firstOrNull { it !in done } ?: return
        if (key !in keys || key in done) return
        habitCompletionDao.insert(
            HabitCompletion(habitId = habit.id, date = today, checkedAt = Instant.now(), value = value ?: habit.amountPerCheckIn, occurrenceKey = key),
        )
        if (habit.lastCompletedDate == today) return
        habitDao.update(habit.copy(lastCompletedDate = today, previousCompletedDate = habit.lastCompletedDate, updatedAt = Instant.now()))
    }

    /** [checkInOccurrence]'s undo: [occurrenceKey]'s check-in, or with none named the day's latest; once none is left the day stops counting. */
    private suspend fun undoOccurrence(habit: Habit, today: LocalDate, occurrenceKey: String?) {
        val now = Instant.now()
        val live = habitCompletionDao.getLiveForDay(habit.id, today)
        val gone = if (occurrenceKey != null) live.filter { it.occurrenceKey == occurrenceKey } else listOfNotNull(live.maxByOrNull { it.checkedAt })
        if (gone.isEmpty()) return // nothing checked under that key: nothing to undo
        for (row in gone) habitCompletionDao.softDelete(row.id, now)
        if (live.size > gone.size) return
        habitDao.update(habit.copy(lastCompletedDate = habit.previousCompletedDate, previousCompletedDate = null, updatedAt = now))
    }
}
