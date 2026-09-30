package com.tendril.app.domain.plan

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitBlock
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * §6.3 (plan Phase 5c) — what the add and edit sheet asks for a calendar habit, and the blocks
 * editor's one rule. Pure: the sheet shows what these say, and never decides on its own.
 */

/** The sheet's *Repeats* choices, in its order; the planner's aliases are not separate choices. */
enum class RepeatKind { DAILY, WEEKDAYS, EVERY_N_DAYS, EVERY_N_WEEKS, TIMES_PER_DAY, TIMES_PER_WEEK, EVERY_N_HOURS, ONCE }

/** Why a draft does not make a rule; the sheet says it next to the field. */
sealed interface DraftError {
    data object NoDays : DraftError
    data class NumberRange(val lo: Int, val hi: Int) : DraftError
    data class TimesCount(val n: Int) : DraftError
    data object Until : DraftError
}

/**
 * The sheet's fields for a calendar rule. [times] are a several-a-day habit's set times (empty:
 * spread over the blocks); [anchor] is where "every few days / weeks" counts from; [date] a once's.
 */
data class ScheduleDraft(
    val kind: RepeatKind = RepeatKind.DAILY,
    val n: Int = 2,
    val days: Set<DayOfWeek> = emptySet(),
    val anchor: LocalDate,
    val times: List<Int> = emptyList(),
    val from: Int = 8 * 60,
    val until: Int = 20 * 60,
    val date: LocalDate,
    /** A several-a-day rule's places as edit mode left them (a block per entry): kept while [times] is empty, so an edit that does not touch them keeps them. */
    val slots: List<Slot> = emptyList(),
)

/** Why the draft makes no rule, or null when it makes one. */
fun ScheduleDraft.error(): DraftError? {
    fun range(lo: Int, hi: Int) = if (n in lo..hi) null else DraftError.NumberRange(lo, hi)
    return when (kind) {
        RepeatKind.DAILY, RepeatKind.ONCE -> null
        RepeatKind.WEEKDAYS -> if (days.isEmpty()) DraftError.NoDays else null
        RepeatKind.EVERY_N_DAYS -> range(2, 365)
        RepeatKind.EVERY_N_WEEKS -> if (days.isEmpty()) DraftError.NoDays else range(2, 52)
        RepeatKind.TIMES_PER_DAY -> range(1, MAX_TIMES_PER_DAY) ?: if (times.isNotEmpty() && times.size != n) DraftError.TimesCount(n) else null
        RepeatKind.TIMES_PER_WEEK -> range(1, 7)
        RepeatKind.EVERY_N_HOURS -> range(1, MAX_HOURS_STEP) ?: if (until <= from) DraftError.Until else null
    }
}

/** The rule the draft makes; only for a draft with no [error]. */
fun ScheduleDraft.toRule(): CalendarRule = when (kind) {
    RepeatKind.DAILY -> CalendarRule.Daily
    RepeatKind.ONCE -> CalendarRule.Once(date)
    RepeatKind.WEEKDAYS -> CalendarRule.Weekdays(days)
    RepeatKind.EVERY_N_DAYS -> CalendarRule.EveryNDays(n, anchor)
    RepeatKind.EVERY_N_WEEKS -> CalendarRule.EveryNWeeks(n, anchor, days)
    RepeatKind.TIMES_PER_DAY -> CalendarRule.TimesPerDay(n, if (times.isNotEmpty()) times.sorted().map(Slot::At) else slots.takeIf { it.size == n } ?: emptyList())
    RepeatKind.TIMES_PER_WEEK -> if (days.isEmpty()) CalendarRule.TimesPerWeek(n) else CalendarRule.TimesPerWeek(n, days)
    RepeatKind.EVERY_N_HOURS -> CalendarRule.EveryNHours(n, from, until)
}

/** A rule back into the sheet's fields, for editing; [today] fills the fields the rule does not have. */
fun draftOf(rule: CalendarRule, today: LocalDate): ScheduleDraft {
    val base = ScheduleDraft(anchor = today, date = today)
    return when (rule) {
        CalendarRule.Daily -> base
        is CalendarRule.Once -> base.copy(kind = RepeatKind.ONCE, date = rule.date)
        is CalendarRule.Weekdays -> base.copy(kind = RepeatKind.WEEKDAYS, days = rule.days)
        is CalendarRule.EveryNDays -> base.copy(kind = RepeatKind.EVERY_N_DAYS, n = rule.n, anchor = rule.anchor)
        is CalendarRule.EveryNWeeks -> base.copy(kind = RepeatKind.EVERY_N_WEEKS, n = rule.n, anchor = rule.anchor, days = rule.days)
        is CalendarRule.TimesPerDay -> {
            val times = rule.slots.filterIsInstance<Slot.At>().map { it.minute }
            if (times.size == rule.slots.size) base.copy(kind = RepeatKind.TIMES_PER_DAY, n = rule.n, times = times)
            else base.copy(kind = RepeatKind.TIMES_PER_DAY, n = rule.n, slots = rule.slots)
        }
        is CalendarRule.TimesPerWeek -> base.copy(kind = RepeatKind.TIMES_PER_WEEK, n = rule.n, days = rule.days.takeIf { it.size < 7 } ?: emptySet())
        is CalendarRule.EveryNHours -> base.copy(kind = RepeatKind.EVERY_N_HOURS, n = rule.n, from = rule.from, until = rule.until)
    }
}

/** Whether a Where (a block, or a set time) applies: a rule that places each entry itself — set times per entry, every few hours — has none. */
fun ScheduleDraft.hasWhere(): Boolean = kind != RepeatKind.TIMES_PER_DAY && kind != RepeatKind.EVERY_N_HOURS

/** Why a block's new times are refused. */
sealed interface BlockEditError {
    data object Order : BlockEditError
    /** [next] ends at [end]: the edited block must end before it. */
    data class PastNext(val next: HabitBlock, val end: Int) : BlockEditError
    /** [prev] starts at [start]: the edited block must start after it. */
    data class BeforePrev(val prev: HabitBlock, val start: Int) : BlockEditError
}

/**
 * The planner's `actBlock` rule on the blocks' own times: changing an end moves the next block's
 * start with it, changing a start moves the previous block's end; pushing past the neighbour's
 * other edge is refused; the first start and the last end are free, times within 00:00–24:00. The
 * blocks that change, [now] stamped; [blocks] in their order.
 */
fun blockEditError(blocks: List<HabitBlock>, uid: String, start: Int, end: Int): BlockEditError? {
    val (prev, b, next) = neighbours(blocks, uid) ?: return BlockEditError.Order
    return when {
        start < 0 || end > 24 * 60 || end <= start -> BlockEditError.Order
        next != null && end != b.endMinute && end >= next.endMinute -> BlockEditError.PastNext(next, next.endMinute)
        prev != null && start != b.startMinute && start <= prev.startMinute -> BlockEditError.BeforePrev(prev, prev.startMinute)
        else -> null
    }
}

private fun neighbours(blocks: List<HabitBlock>, uid: String): Triple<HabitBlock?, HabitBlock, HabitBlock?>? {
    val ordered = blocks.filter { it.deletedAt == null }.sortedWith(compareBy({ it.position }, { it.uid }))
    val i = ordered.indexOfFirst { it.uid == uid }.takeIf { it >= 0 } ?: return null
    return Triple(ordered.getOrNull(i - 1), ordered[i], ordered.getOrNull(i + 1))
}

/** The change [blockEditError] allowed: the edited block and the neighbour its edge moved. */
fun editBlockTimes(blocks: List<HabitBlock>, uid: String, start: Int, end: Int, now: java.time.Instant): List<HabitBlock> {
    val (prev, b, next) = neighbours(blocks, uid) ?: return emptyList()
    return listOfNotNull(
        prev?.takeIf { start != b.startMinute }?.copy(endMinute = start, updatedAt = now),
        b.copy(startMinute = start, endMinute = end, updatedAt = now),
        next?.takeIf { end != b.endMinute }?.copy(startMinute = end, updatedAt = now),
    )
}

/** A habit paused on [date]: from its pause's start (or ever) to its end (or until resumed), both inclusive. */
fun Habit.pausedOn(date: LocalDate): Boolean =
    (pauseFrom != null || pauseUntil != null) &&
        (pauseFrom == null || !date.isBefore(pauseFrom)) && (pauseUntil == null || !date.isAfter(pauseUntil))
