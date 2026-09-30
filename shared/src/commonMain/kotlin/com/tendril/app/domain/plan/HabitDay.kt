package com.tendril.app.domain.plan

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitBlock
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitScheduleEdit
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.habitPeriodDays
import com.tendril.app.domain.nextHabitDueDate
import java.time.LocalDate

/*
 * §6.3 (plan Phase 5a) — one day of the Habits tab's Day view, by time: every habit that belongs on
 * the day, placed in the day's blocks by the engine (a set time in the block that holds it, else the
 * habit's block, else *Any time today*), with what was done. Pure, so the view's every rule is a test.
 */

/** One row: a habit, and for a calendar habit which of the day's occurrences ([key]; null for an interval habit). */
data class DayRow(
    val habit: Habit,
    val key: String?,
    /** A set time, minutes from midnight; null for an entry that sits in its block. */
    val time: Int?,
    val done: Boolean,
    /** T3 — an interval habit on a later day: when it would next fall due if checked in on time. A guess, drawn as one. */
    val projected: Boolean = false,
    /** Its manual order as the day has it (edits applied), which edit mode renumbers (5b). */
    val sortOrder: Double = 0.0,
    /** The engine's occurrence for a calendar habit — its tag, whether it recurs, whether a move added it; null for an interval habit. */
    val occurrence: PlanOccurrence? = null,
)

/** A block on this day — its times as the day has them (weekday changes and edits applied) — and its rows: set times first, in time order, then the rest in their order. */
data class DayBlock(val block: HabitBlock, val start: Int, val end: Int, val timed: List<DayRow>, val flexible: List<DayRow>)

data class HabitDayView(
    val date: LocalDate,
    val blocks: List<DayBlock>,
    /** V2 — an entry with no set time and no block (or a block that no longer exists). */
    val anyTime: List<DayRow>,
    /** D4 — an entry whose set time falls outside every block: kept, with a neutral note. */
    val outside: List<DayRow>,
    /** D4 — two blocks that overlap on this day, by uid. */
    val overlaps: List<BlockOverlap>,
    /** R3 as drawn — presence, counted up: the rows done on this day. Never "N of M". */
    val doneCount: Int,
    /** Past days are read only (carried over from the planner); today and later days are not (T2). */
    val readOnly: Boolean,
    /** The week the day is in, as the engine placed it — what "Apply this change to…" counts (D10, 5b). */
    val week: PlanWeek? = null,
)

/**
 * §6.3 (5a) — [date]'s view. [habits] are the live habits of both kinds; [completions] every live
 * check-in on [date]; [today] decides what is past, present and projected.
 */
fun habitDay(
    date: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    blocks: List<HabitBlock>,
    edits: List<HabitScheduleEdit>,
    completions: List<HabitCompletion>,
): HabitDayView {
    val live = habits.filter { it.deletedAt == null }
    val liveBlocks = blocks.filter { it.deletedAt == null }.sortedWith(compareBy({ it.position }, { it.uid }))
    val byUid = live.associateBy { it.uid }
    val onDay = completions.filter { it.date == date && it.deletedAt == null }

    // An interval habit enters the engine as a habit once on this day, so it is placed by the same
    // rules as every calendar habit (V2) and no second placement exists to drift from the first.
    val intervalOn = live.filter { it.scheduleKind != HabitScheduleKind.CALENDAR && intervalBelongsOn(it, date, today, onDay) }
    val planHabits = live.filter { it.scheduleKind == HabitScheduleKind.CALENDAR }.mapNotNull { it.toPlanHabit() } +
        intervalOn.map { PlanHabit(it.uid, CalendarRule.Once(date), it.duration?.toMinutes()?.toInt() ?: 0, it.blockUid, it.time?.let { t -> t.toSecondOfDay() / 60 }, it.sortOrder) }
    val calendarUids = planHabits.map { it.uid }.toSet()
    val planEdits = edits.filter { it.deletedAt == null }.mapNotNull { it.toPlanEdit() }
        .filter { it.target == EditTarget.BLOCK || it.refUid in calendarUids }
    val model = PlanModel(
        blocks = liveBlocks.map { PlanBlock(it.uid, it.startMinute, it.endMinute) },
        overrides = liveBlocks.flatMap { decodeOverrides(it.uid, it.overrides) },
        habits = planHabits,
        edits = planEdits,
    )
    val week = planWeek(model, date)
    val placed = week.days.first { it.date == date }

    fun row(o: PlanOccurrence): DayRow? {
        val h = byUid[o.habitUid] ?: return null
        val calendar = h.scheduleKind == HabitScheduleKind.CALENDAR
        val done = if (calendar) onDay.any { it.habitId == h.id && it.occurrenceKey == o.key } else onDay.any { it.habitId == h.id }
        return DayRow(h, if (calendar) o.key else null, o.time, done, projected = !calendar && date.isAfter(today), sortOrder = o.sortOrder, occurrence = if (calendar) o else null)
    }
    val blockRows = liveBlocks.associateBy { it.uid }
    val dayBlocks = placed.blocks.mapNotNull { b ->
        DayBlock(blockRows[b.uid] ?: return@mapNotNull null, b.start, b.end, b.timed.mapNotNull(::row), b.flexible.mapNotNull(::row))
    }
    val anyTime = placed.anyTime.mapNotNull(::row)
    val outside = placed.outside.mapNotNull(::row)
    val all = dayBlocks.flatMap { it.timed + it.flexible } + anyTime + outside
    return HabitDayView(date, dayBlocks, anyTime, outside, placed.overlaps, all.count { it.done }, date.isBefore(today), week)
}

/**
 * Whether an interval habit is on [date]: before [today], only where it was checked in (presence,
 * never a gap, §0.5.2); on [today], due or already done; after it, T3's projection — the days it
 * would fall due if checked in on time, from today when it is due or done today, else from its
 * next due date, one period apart.
 */
private fun intervalBelongsOn(h: Habit, date: LocalDate, today: LocalDate, onDay: List<HabitCompletion>): Boolean {
    val checkedThatDay = onDay.any { it.habitId == h.id }
    if (date.isBefore(today)) return checkedThatDay
    if (h.pausedOn(date)) return checkedThatDay // paused, it leaves the plan; a check-in made anyway still shows
    val dueToday = nextHabitDueDate(h, today) == today
    if (date == today) return dueToday || h.lastCompletedDate == today || checkedThatDay
    val period = habitPeriodDays(h).toLong().coerceAtLeast(1)
    val base = if (dueToday || h.lastCompletedDate == today) today else nextHabitDueDate(h, today)
    val gap = java.time.temporal.ChronoUnit.DAYS.between(base, date)
    return gap >= 0 && gap % period == 0L && date.isAfter(today)
}
