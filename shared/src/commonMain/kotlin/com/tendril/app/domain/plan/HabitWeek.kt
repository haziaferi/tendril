package com.tendril.app.domain.plan

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitBlock
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitScheduleEdit
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * §6.3 (plan Phase 5d) — the Habits tab's Week view and the Day view by area. Pure, as the day is.
 */

/** Q1 — an "X times a week" habit with this week's days not yet confirmed: the days suggested, placing nothing. */
data class WeekSuggestionCard(val habit: Habit, val n: Int, val days: List<LocalDate>)

data class HabitWeekView(
    val monday: LocalDate,
    /** Monday first, each built as the Day view builds it. */
    val days: List<HabitDayView>,
    /** Q1 — nothing is placed until the person confirms; a past week has none. */
    val suggestions: List<WeekSuggestionCard>,
)

/**
 * The week of [monday]. A suggestion for the current week offers only days from [today] on (the
 * 5c walk: the engine spreads over the whole week, as the planner did, and suggested a day already
 * past); a later week, any of its days; a past week, none.
 */
fun habitWeek(
    monday: LocalDate,
    today: LocalDate,
    habits: List<Habit>,
    blocks: List<HabitBlock>,
    edits: List<HabitScheduleEdit>,
    completions: List<HabitCompletion>,
): HabitWeekView {
    val days = (0L..6L).map { monday.plusDays(it) }.map { d -> habitDay(d, today, habits, blocks, edits, completions.filter { it.date == d }) }
    val thisWeek = mondayOf(today)
    if (monday.isBefore(thisWeek)) return HabitWeekView(monday, days, emptyList())
    val engine = days.first().week?.suggestions.orEmpty()
    val byUid = habits.filter { it.deletedAt == null }.associateBy { it.uid }
    val cards = engine.mapNotNull { s ->
        val h = byUid[s.habitUid] ?: return@mapNotNull null
        val rule = h.toPlanHabit()?.rule as? CalendarRule.TimesPerWeek ?: return@mapNotNull null
        if (monday != thisWeek) return@mapNotNull WeekSuggestionCard(h, rule.n, s.days)
        val remaining = rule.days.filter { it >= today.dayOfWeek }.toSet()
        if (remaining.isEmpty()) return@mapNotNull null
        val suggested = suggestedDays(h.uid, rule.copy(days = remaining), habits, blocks, edits, monday)
        suggested.takeIf { it.isNotEmpty() }?.let { WeekSuggestionCard(h, rule.n, it) }
    }
    return HabitWeekView(monday, days, cards)
}

/** The engine's suggestion for [uid] with [rule] in its place, beside every other calendar habit's load. */
internal fun suggestedDays(uid: String, rule: CalendarRule, habits: List<Habit>, blocks: List<HabitBlock>, edits: List<HabitScheduleEdit>, monday: LocalDate): List<LocalDate> {
    val live = blocks.filter { it.deletedAt == null }.sortedWith(compareBy({ it.position }, { it.uid }))
    val plans = habits.filter { it.deletedAt == null && it.uid != uid }.mapNotNull { it.toPlanHabit() }
    val mine = habits.firstOrNull { it.uid == uid }?.toPlanHabit()?.copy(rule = rule) ?: PlanHabit(uid, rule, 0)
    val model = PlanModel(
        blocks = live.map { PlanBlock(it.uid, it.startMinute, it.endMinute) },
        overrides = live.flatMap { decodeOverrides(it.uid, it.overrides) },
        habits = plans + mine,
        edits = edits.filter { it.deletedAt == null }.mapNotNull { it.toPlanEdit() },
    )
    return planWeek(model, monday).suggestions.firstOrNull { it.habitUid == uid }?.days.orEmpty()
}

/** Q1 — *Confirm*: the week's days for [habit], one edit scoped to that week alone (§6.3's rule — confirmed days do not reach later weeks). */
fun confirmWeekDays(habit: Habit, monday: LocalDate, days: Set<DayOfWeek>, now: java.time.Instant, uid: String): ScheduleEdit =
    ScheduleEdit(uid, EditTarget.HABIT, habit.uid, EditScope.Week(monday), EditChanges(weekDays = days), now)

/** One habit's entries on a day in the view by area: the several-a-day habit collapsed to one row (R3, as drawn). */
data class AreaRow(val habit: Habit, val rows: List<DayRow>) {
    /** The entry the row stands for: the first not done, else the last. */
    val shown: DayRow get() = rows.firstOrNull { !it.done } ?: rows.last()
    /** How many more the habit has that day, beside [shown]. */
    val more: Int get() = rows.size - 1
}

/** A Label's habits on the day; [labelId] null is *No area*, last. */
data class AreaGroup(val labelId: Long?, val rows: List<AreaRow>)

/**
 * The Day view by area (V1): the day's entries grouped by their habit's Label (P4), the groups in
 * [labelOrder], *No area* last; within a group, habits by their first entry's time, then title.
 */
fun byArea(view: HabitDayView, labelOrder: List<Long>): List<AreaGroup> {
    val all = view.blocks.flatMap { it.timed + it.flexible } + view.anyTime + view.outside
    val perHabit = all.groupBy { it.habit.id }.values.map { rows ->
        AreaRow(rows.first().habit, rows.sortedWith(compareBy<DayRow, Int?>(nullsLast()) { it.time }.thenBy { it.key?.toIntOrNull() ?: Int.MAX_VALUE }))
    }
    val order = compareBy<AreaRow, Int?>(nullsLast()) { it.rows.first().time }.thenBy { it.habit.title.lowercase() }
    val groups = perHabit.groupBy { it.habit.labelId }.map { (id, rows) -> AreaGroup(id, rows.sortedWith(order)) }
    fun rank(id: Long?) = when {
        id == null -> Int.MAX_VALUE
        id in labelOrder -> labelOrder.indexOf(id)
        else -> labelOrder.size
    }
    return groups.sortedBy { rank(it.labelId) }
}
