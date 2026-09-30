package com.tendril.app.domain.plan

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitScheduleKind
import java.time.LocalDate

/**
 * §3.2 (2026-09-21) — a habit on the calendar's grids is not a block but a brush-stroke across
 * its span of time: drawn under the day's blocks, which may cover it, a reminder to keep that
 * time for oneself. The span is the habit's time and its duration — [defaultMinutes] when it has
 * none; a habit with no time is not on the grid at all (the all-day row never holds one). The
 * stroke has one state — the check-in changes no wash (§0.6.6: presence, never absence) — and
 * [checkedIn] is true only on today's lane once the habit was checked in today, where a dot
 * before the name is presence; a past day never carries one, so a stroke without a dot is never
 * an absence. Every active timed habit is on every day, as the habit chips were (the period is
 * not read; a period-aware rule needs the log and is recorded, not built).
 *
 * §6.3 (H4) — a **calendar** habit is on a grid only on its days, one stroke per occurrence with a
 * set time (a time slot, an every-N-hours step, or the habit's own time); an occurrence that sits
 * only in a block has no hour and stays off, as a habit with no time does. Its dot is per
 * occurrence: that occurrence's check-in, today.
 */
data class HabitStroke(val habit: Habit, val startMinute: Int, val minutes: Int, val checkedIn: Boolean)

const val HABIT_STROKE_DEFAULT_MINUTES = 30

/** A stroke is never drawn under this height — a brush has a minimum width, the name's line. */
const val HABIT_STROKE_MIN_DP = 18f

/** A stroke under this height centres its name; a taller one tops it. */
const val HABIT_STROKE_SHORT_DP = 30f

fun habitStrokes(
    habits: List<Habit>,
    day: LocalDate,
    today: LocalDate,
    calendar: HabitCalendar,
    /** Today's live check-ins, for a calendar occurrence's dot. */
    liveToday: List<HabitCompletion>,
    defaultMinutes: Int = HABIT_STROKE_DEFAULT_MINUTES,
): List<HabitStroke> =
    habits.filter { it.deletedAt == null }
        .flatMap { h ->
            val len = (h.duration?.toMinutes()?.toInt() ?: defaultMinutes).coerceAtLeast(1)
            fun stroke(start: Int, checked: Boolean, length: Int = len) = HabitStroke(h, start, minOf(length, 24 * 60 - start), checkedIn = day == today && checked)
            if (h.scheduleKind == HabitScheduleKind.CALENDAR) {
                val done = liveToday.filter { it.habitId == h.id && it.date == today }.mapNotNull { it.occurrenceKey }.toSet()
                // the occurrence's own length, so a length changed for a day or a week reaches the grid
                calendar.occurrences(h, day).mapNotNull { o -> o.time?.let { stroke(it, o.key in done, o.minutes.takeIf { m -> m > 0 } ?: defaultMinutes) } }
            } else {
                listOfNotNull(h.time?.let { stroke(it.toSecondOfDay() / 60, h.lastCompletedDate == today) })
            }
        }
        .sortedWith(compareBy({ it.startMinute }, { it.habit.title }))

/** The stroke's height on a grid of [hourDp] an hour, never under [HABIT_STROKE_MIN_DP]. */
fun strokeHeightDp(minutes: Int, hourDp: Float): Float = maxOf(minutes / 60f * hourDp, HABIT_STROKE_MIN_DP)
