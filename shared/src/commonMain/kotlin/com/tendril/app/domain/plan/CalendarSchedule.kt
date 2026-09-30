package com.tendril.app.domain.plan

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/*
 * §6.3 (2026-09-29) — the calendar habit's schedule: the standalone habit planner's engine
 * (`planner/assets/engine.js`) ported to pure Kotlin, with no Room type and no UI, so it is proved
 * against the planner's own ten golden cases before any column carries it (plan Phase 2).
 *
 * What it keeps from the planner: the rules, the time blocks with their weekday overrides, the
 * scoped edits applied in the order they were made, the set time choosing its block (start
 * inclusive, end exclusive), the evenly spaced least-loaded "X times a week" rotation, and the two
 * structural flags. What it drops, by the decision record: priority (D3), the daily limit and the
 * block-over-capacity flag (D4), per-habit names in two languages (D9). Three things it does
 * differently, each a Tendril rule winning over the planner's: "X times a week" is only *suggested*
 * until the person confirms the week's days (D5, Q1); an entry with a set time outside every block
 * is kept and reported, where the planner dropped it (the neutral note shows it, D4); and a skip
 * written for a day, a week or from a date takes the habit off those days, where the planner's
 * engine merged the skip into the item and never read it.
 */

/** A time block on the day, in minutes from midnight; [end] is not part of it (09:00 belongs to the block starting at 09:00). */
data class PlanBlock(val uid: String, val start: Int, val end: Int)

/** A block's start and end on some weekdays — the planner's `[[override]]`, applied before any edit. */
data class BlockOverride(val blockUid: String, val days: Set<DayOfWeek>, val start: Int, val end: Int)

/** One of a several-a-day habit's places: a block, or a set time. */
sealed interface Slot {
    data class InBlock(val blockUid: String) : Slot
    data class At(val minute: Int) : Slot
}

/**
 * The calendar rule, canonical: the planner's aliases (weekly, alternate days, alternate weeks)
 * are [Weekdays], [EveryNDays] with 2 and [EveryNWeeks] with 2 before they reach here.
 */
sealed interface CalendarRule {
    data object Daily : CalendarRule
    data class Once(val date: LocalDate) : CalendarRule
    data class Weekdays(val days: Set<DayOfWeek>) : CalendarRule
    data class EveryNDays(val n: Int, val anchor: LocalDate) : CalendarRule
    data class EveryNWeeks(val n: Int, val anchor: LocalDate, val days: Set<DayOfWeek>) : CalendarRule
    /** [slots] empty: spread evenly over the blocks in their order. */
    data class TimesPerDay(val n: Int, val slots: List<Slot> = emptyList()) : CalendarRule
    /** [days]: the days it may fall on; all seven when unrestricted. */
    data class TimesPerWeek(val n: Int, val days: Set<DayOfWeek> = DayOfWeek.entries.toSet()) : CalendarRule
    data class EveryNHours(val n: Int, val from: Int, val until: Int) : CalendarRule
}

/** A pause, both ends inclusive; a null [from] is "since ever", a null [until] is "until I resume it". */
data class PauseSpan(val from: LocalDate?, val until: LocalDate?)

/** A calendar habit as the engine reads it — the columns of §4's `habits` row that the schedule needs. */
data class PlanHabit(
    val uid: String,
    val rule: CalendarRule,
    val minutes: Int,
    val blockUid: String? = null,
    /** A set time, in minutes; when present it chooses the block and [blockUid] is not read. */
    val time: Int? = null,
    val sortOrder: Double = 0.0,
    val activeFrom: LocalDate? = null,
    val activeUntil: LocalDate? = null,
    val pause: PauseSpan? = null,
)

/** A field an edit sets, including to null ("remove the time"); a null [Patch] means "not changed". */
data class Patch<out T>(val value: T)

/** The fields of a rule an edit shifts without replacing it — the every-N-hours series moved as a whole. */
data class RulePatch(val n: Int? = null, val from: Int? = null, val until: Int? = null)

/** What an edit sets. Every field null is an edit that changes nothing. */
data class EditChanges(
    val blockUid: Patch<String?>? = null,
    val time: Patch<Int?>? = null,
    val minutes: Int? = null,
    val rule: CalendarRule? = null,
    val rulePatch: RulePatch? = null,
    val skip: Boolean? = null,
    val deleted: Boolean? = null,
    val pause: Patch<PauseSpan?>? = null,
    val sortOrder: Double? = null,
    /** Q1 — the days an "X times a week" habit falls on in one week, as the person confirmed them; written with a [EditScope.Week] scope. */
    val weekDays: Set<DayOfWeek>? = null,
    /** For a block edit: its new start and end. */
    val blockStart: Int? = null,
    val blockEnd: Int? = null,
)

/** Where an edit applies — the planner's five scope kinds. */
sealed interface EditScope {
    /** One occurrence on one day; [key] is its number within the day, or `x:<edit uid>` for an added one. */
    data class Occurrence(val date: LocalDate, val key: String) : EditScope
    data class Day(val date: LocalDate) : EditScope
    /** The ISO week starting on [monday], optionally only some of its days. */
    data class Week(val monday: LocalDate, val days: Set<DayOfWeek>? = null) : EditScope
    /** From [date] on, optionally only some weekdays. */
    data class From(val date: LocalDate, val days: Set<DayOfWeek>? = null) : EditScope
    /** An added occurrence of an existing habit on [date] ("Move to" another day). */
    data class Extra(val date: LocalDate) : EditScope
}

enum class EditTarget { HABIT, BLOCK }

/** One "Apply this change to…": applied in [createdAt] order, then by [uid], the same on every device. */
data class ScheduleEdit(
    val uid: String,
    val target: EditTarget,
    val refUid: String,
    val scope: EditScope,
    val changes: EditChanges,
    val createdAt: Instant,
)

data class PlanModel(
    val blocks: List<PlanBlock>,
    val overrides: List<BlockOverride> = emptyList(),
    val habits: List<PlanHabit>,
    val edits: List<ScheduleEdit> = emptyList(),
)

/** Why an occurrence exists, where the rule made several: which of the day's, which step, which of the week's. */
sealed interface OccurrenceTag {
    data class Count(val i: Int, val n: Int) : OccurrenceTag
    data object TimeStep : OccurrenceTag
    data class Week(val i: Int, val n: Int) : OccurrenceTag
}

data class PlanOccurrence(
    val habitUid: String,
    /** The number within the day as a string, or `x:<edit uid>`; `habitUid|date|key` is the occurrence's identity (P3). */
    val key: String,
    val date: LocalDate,
    val blockUid: String?,
    val time: Int?,
    val minutes: Int,
    val tag: OccurrenceTag?,
    val sortOrder: Double,
    /** False for a [CalendarRule.Once] habit: nothing to apply "from now on" to. */
    val recurring: Boolean,
    /** Added by "Move to" another day. */
    val moved: Boolean = false,
)

data class PlacedBlock(val uid: String, val start: Int, val end: Int, val timed: List<PlanOccurrence>, val flexible: List<PlanOccurrence>)

/** Two blocks that overlap on a day — one of D4's two neutral notes. */
data class BlockOverlap(val first: String, val second: String)

data class PlanDay(
    val date: LocalDate,
    val blocks: List<PlacedBlock>,
    /** Occurrences with a set time outside every block — D4's other note; kept, not dropped. */
    val outside: List<PlanOccurrence>,
    /** Flexible occurrences with no block, or a block that no longer exists: V2's "Any time today". */
    val anyTime: List<PlanOccurrence>,
    val overlaps: List<BlockOverlap>,
)

/** §6.3, D5 — the days the app suggests for an "X times a week" habit the person has not confirmed this week. Nothing is placed until they do. */
data class WeekSuggestion(val habitUid: String, val days: List<LocalDate>)

data class PlanWeek(val monday: LocalDate, val days: List<PlanDay>, val suggestions: List<WeekSuggestion>)

/** §6.3 — the plan of the week containing [day], Monday first: every calendar habit's occurrences placed in the day's blocks, the two notes, and the week's suggestions. */
fun planWeek(model: PlanModel, day: LocalDate): PlanWeek {
    val edits = EditIndex(model.edits)
    val monday = mondayOf(day)
    val days = (0L..6L).map { monday.plusDays(it) }
    val placed = expandWeek(model, days, edits)
    val out = days.map { d -> placeDay(d, effectiveBlocks(model, d, edits), placed.occurrences.getValue(d)) }
    return PlanWeek(monday, out, placed.suggestions)
}

/**
 * §6.3 — the largest counts a rule can mean: a step of a day at most, and a place every half hour.
 * Outside them the rule does nothing, rather than run a reader out of memory — `n * 60` wraps to a
 * negative step past `Int`, and a count of two billion builds two billion places (review of Phase 3).
 */
internal const val MAX_HOURS_STEP = 24
internal const val MAX_TIMES_PER_DAY = 48

internal fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

/** §6.3 — the planner's `covers`: whether a day/week/from scope takes in [d]. Occurrence and extra scopes are addressed by key, not by day. */
internal fun EditScope.covers(d: LocalDate): Boolean = when (this) {
    is EditScope.Day -> date == d
    is EditScope.Week -> mondayOf(d) == monday && (days.isNullOrEmpty() || d.dayOfWeek in days)
    is EditScope.From -> !d.isBefore(date) && (days.isNullOrEmpty() || d.dayOfWeek in days)
    is EditScope.Occurrence, is EditScope.Extra -> false
}

/** §6.3 — the edits sorted once, in the order they are applied — [ScheduleEdit.createdAt], then uid — and filed by what they address. */
internal class EditIndex(all: List<ScheduleEdit>) {
    private val sorted = all.sortedWith(compareBy<ScheduleEdit>({ it.createdAt }, { it.uid }))
    val blocks = sorted.filter { it.target == EditTarget.BLOCK }
    private val habits = sorted.filter { it.target == EditTarget.HABIT }
    val byHabit: Map<String, List<ScheduleEdit>> = habits.filter { it.scope is EditScope.Day || it.scope is EditScope.Week || it.scope is EditScope.From }.groupBy { it.refUid }
    val byOccurrence: Map<String, List<ScheduleEdit>> = habits.filter { it.scope is EditScope.Occurrence }
        .groupBy { (it.scope as EditScope.Occurrence).let { s -> occurrenceId(it.refUid, s.date, s.key) } }
    val extras: Map<LocalDate, List<ScheduleEdit>> = habits.filter { it.scope is EditScope.Extra }.groupBy { (it.scope as EditScope.Extra).date }
}

internal fun occurrenceId(habitUid: String, date: LocalDate, key: String) = "$habitUid|$date|$key"

/** A habit as it stands on one day, with the three things an edit can set that the row itself never carries. */
internal data class DayHabit(val habit: PlanHabit, val skip: Boolean = false, val deleted: Boolean = false, val weekDays: Set<DayOfWeek>? = null)

internal fun DayHabit.apply(c: EditChanges): DayHabit {
    var h = habit
    c.blockUid?.let { h = h.copy(blockUid = it.value) }
    c.time?.let { h = h.copy(time = it.value) }
    c.minutes?.let { h = h.copy(minutes = it) }
    c.rule?.let { h = h.copy(rule = it) }
    c.rulePatch?.let { h = h.copy(rule = h.rule.patched(it)) }
    c.pause?.let { h = h.copy(pause = it.value) }
    c.sortOrder?.let { h = h.copy(sortOrder = it) }
    return DayHabit(h, skip = c.skip ?: skip, deleted = c.deleted ?: deleted, weekDays = c.weekDays ?: weekDays)
}

/** The planner's partial `repeat` merge: only the fields the rule has. */
private fun CalendarRule.patched(p: RulePatch): CalendarRule = when (this) {
    is CalendarRule.EveryNHours -> copy(n = p.n ?: n, from = p.from ?: from, until = p.until ?: until)
    is CalendarRule.TimesPerDay -> copy(n = p.n ?: n)
    is CalendarRule.TimesPerWeek -> copy(n = p.n ?: n)
    is CalendarRule.EveryNDays -> copy(n = p.n ?: n)
    is CalendarRule.EveryNWeeks -> copy(n = p.n ?: n)
    CalendarRule.Daily, is CalendarRule.Once, is CalendarRule.Weekdays -> this
}

/** The planner's `effectiveItem`: every day/week/from edit covering [d], in order. */
internal fun dayHabit(habit: PlanHabit, d: LocalDate, edits: EditIndex): DayHabit =
    edits.byHabit[habit.uid].orEmpty().fold(DayHabit(habit)) { acc, e ->
        // a week's confirmed days belong to that week alone: written under any wider scope they would confirm every later week
        if (e.scope.covers(d)) acc.apply(if (e.scope is EditScope.Week) e.changes else e.changes.copy(weekDays = null)) else acc
    }

/** §6.3 — the planner's `isActive`, and a skip written for a day, a week or from a date — which the planner merged and never read. */
internal fun DayHabit.activeOn(d: LocalDate): Boolean {
    if (deleted || skip) return false
    habit.pause?.let { p -> if ((p.from == null || !d.isBefore(p.from)) && (p.until == null || !d.isAfter(p.until))) return false }
    if (habit.activeFrom != null && d.isBefore(habit.activeFrom)) return false
    if (habit.activeUntil != null && d.isAfter(habit.activeUntil)) return false
    return true
}

/** Blocks on [d]: as defined, then the weekday overrides, then the block edits covering [d]. */
internal fun effectiveBlocks(model: PlanModel, d: LocalDate, edits: EditIndex): List<PlanBlock> {
    val out = model.blocks.toMutableList()
    fun set(uid: String, f: (PlanBlock) -> PlanBlock) {
        val i = out.indexOfFirst { it.uid == uid }
        if (i >= 0) out[i] = f(out[i])
    }
    model.overrides.filter { d.dayOfWeek in it.days }.forEach { o -> set(o.blockUid) { it.copy(start = o.start, end = o.end) } }
    edits.blocks.filter { it.scope.covers(d) }.forEach { e ->
        set(e.refUid) { b -> b.copy(start = e.changes.blockStart ?: b.start, end = e.changes.blockEnd ?: b.end) }
    }
    return out
}

/** The planner's `spread`: [n] places evenly over [k], by index. */
internal fun spread(k: Int, n: Int): List<Int> = (0 until n).map { i -> minOf(k - 1, ((i + 0.5) * k / n).toInt()) }

private class Expanded(val occurrences: Map<LocalDate, List<PlanOccurrence>>, val suggestions: List<WeekSuggestion>)

private fun occurrence(h: PlanHabit, d: LocalDate, blockUid: String? = h.blockUid, time: Int? = h.time, tag: OccurrenceTag? = null, key: String = "", moved: Boolean = false) =
    PlanOccurrence(h.uid, key, d, blockUid, time, h.minutes, tag, h.sortOrder, recurring = h.rule !is CalendarRule.Once, moved = moved)

/** The planner's `expandWeek`, less priority; "X times a week" suggested unless the week's days are confirmed (D5). */
private fun expandWeek(model: PlanModel, days: List<LocalDate>, edits: EditIndex): Expanded {
    val placed = days.associateWith { mutableListOf<PlanOccurrence>() }
    val blockOrder = model.blocks.map { it.uid }
    val weekly = LinkedHashSet<PlanHabit>()

    for (habit in model.habits) for (d in days) {
        val dh = dayHabit(habit, d, edits)
        if (!dh.activeOn(d)) continue
        val h = dh.habit
        val add = { o: PlanOccurrence -> placed.getValue(d).add(o) }
        when (val r = h.rule) {
            CalendarRule.Daily -> add(occurrence(h, d))
            is CalendarRule.Once -> if (r.date == d) add(occurrence(h, d))
            is CalendarRule.Weekdays -> if (d.dayOfWeek in r.days) add(occurrence(h, d))
            is CalendarRule.EveryNDays -> if (r.n > 0 && Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(r.anchor, d), r.n.toLong()) == 0L) add(occurrence(h, d))
            is CalendarRule.EveryNWeeks -> {
                val weeks = java.time.temporal.ChronoUnit.DAYS.between(mondayOf(r.anchor), mondayOf(d)) / 7
                if (r.n > 0 && Math.floorMod(weeks, r.n.toLong()) == 0L && d.dayOfWeek in r.days) add(occurrence(h, d))
            }
            is CalendarRule.TimesPerDay -> {
                val slots = r.slots.ifEmpty { if (blockOrder.isEmpty() || r.n !in 1..MAX_TIMES_PER_DAY) emptyList() else spread(blockOrder.size, r.n).map { Slot.InBlock(blockOrder[it]) } }
                slots.forEachIndexed { i, s ->
                    val tag = OccurrenceTag.Count(i + 1, r.n)
                    add(
                        when (s) {
                            is Slot.At -> occurrence(h, d, blockUid = null, time = s.minute, tag = tag)
                            is Slot.InBlock -> occurrence(h, d, blockUid = s.blockUid, time = null, tag = tag)
                        },
                    )
                }
            }
            is CalendarRule.EveryNHours -> if (r.n in 1..MAX_HOURS_STEP) {
                var t = r.from
                while (t <= r.until) { add(occurrence(h, d, blockUid = null, time = t, tag = OccurrenceTag.TimeStep)); t += r.n * 60 }
            }
            is CalendarRule.TimesPerWeek -> weekly.add(habit)
        }
    }

    // X times a week: evenly spaced, rotated so the busiest day stays lightest; then confirmed, or only suggested.
    val load = days.associateWith { d -> placed.getValue(d).sumOf { it.minutes } }.toMutableMap()
    val suggestions = mutableListOf<WeekSuggestion>()
    for (habit in weekly.sortedWith(compareBy({ it.sortOrder }, { it.uid }))) {
        val onDay = days.associateWith { d -> dayHabit(habit, d, edits) }
        val pool = days.filter { d -> onDay.getValue(d).let { it.activeOn(d) && it.habit.rule.let { r -> r is CalendarRule.TimesPerWeek && d.dayOfWeek in r.days } } }
        if (pool.isEmpty()) continue
        val confirmed = days.firstNotNullOfOrNull { d -> onDay.getValue(d).weekDays }
        if (confirmed == null && (onDay.getValue(pool.first()).habit.rule as CalendarRule.TimesPerWeek).n <= 0) continue
        val chosen = if (confirmed != null) {
            pool.filter { it.dayOfWeek in confirmed }
        } else {
            val n = minOf((onDay.getValue(pool.first()).habit.rule as CalendarRule.TimesPerWeek).n, pool.size)
            val base = spread(pool.size, n)
            var best: Pair<Triple<Int, Long, Int>, List<LocalDate>>? = null
            for (off in pool.indices) {
                val pick = base.map { pool[(it + off) % pool.size] }.distinct().sorted()
                val trial = load.toMutableMap()
                pick.forEach { d -> trial[d] = trial.getValue(d) + onDay.getValue(d).habit.minutes }
                val score = Triple(trial.values.max(), trial.values.sumOf { it.toLong() * it }, off)
                if (best == null || compareValuesBy(score, best.first, { it.first }, { it.second }, { it.third }) < 0) best = score to pick
            }
            best!!.second.also { suggestions += WeekSuggestion(habit.uid, it) }
        }
        chosen.forEachIndexed { i, d ->
            val h = onDay.getValue(d).habit
            if (confirmed != null) placed.getValue(d).add(occurrence(h, d, tag = OccurrenceTag.Week(i + 1, chosen.size)))
            load[d] = load.getValue(d) + h.minutes
        }
    }

    // Number each habit's occurrences within the day; then the added ones and the one-entry edits.
    val habitsByUid = model.habits.associateBy { it.uid }
    val out = days.associateWith { d ->
        val count = HashMap<String, Int>()
        val numbered = placed.getValue(d).map { o -> o.copy(key = (count.merge(o.habitUid, 1, Int::plus)!!).toString()) }.toMutableList()
        for (e in edits.extras[d].orEmpty()) {
            val base = habitsByUid[e.refUid] ?: continue
            val h = dayHabit(base, d, edits).apply(e.changes).habit
            numbered += occurrence(h, d, key = "x:${e.uid}", moved = true)
        }
        numbered.mapNotNull { o ->
            var cur = o
            var skip = false
            var deleted = false
            for (e in edits.byOccurrence[occurrenceId(o.habitUid, d, o.key)].orEmpty()) {
                val c = e.changes
                c.blockUid?.let { cur = cur.copy(blockUid = it.value) }
                c.time?.let { cur = cur.copy(time = it.value) }
                c.minutes?.let { cur = cur.copy(minutes = it) }
                c.sortOrder?.let { cur = cur.copy(sortOrder = it) }
                skip = c.skip ?: skip
                deleted = c.deleted ?: deleted
            }
            cur.takeUnless { skip || deleted }
        }
    }
    return Expanded(out, suggestions)
}

/** Place one day's occurrences: a set time in the block that holds it, else the habit's block, else Any time. */
private fun placeDay(d: LocalDate, blocks: List<PlanBlock>, occurrences: List<PlanOccurrence>): PlanDay {
    val ordered = blocks.sortedBy { it.start }
    val overlaps = ordered.zipWithNext().filter { (a, b) -> b.start < a.end }.map { (a, b) -> BlockOverlap(a.uid, b.uid) }
    val timed = blocks.associate { it.uid to mutableListOf<PlanOccurrence>() }
    val flexible = blocks.associate { it.uid to mutableListOf<PlanOccurrence>() }
    val outside = mutableListOf<PlanOccurrence>()
    val anyTime = mutableListOf<PlanOccurrence>()
    for (o in occurrences) {
        val t = o.time
        if (t != null) {
            // a block runs from its start up to, not including, its end: 09:00 belongs to the block starting at 09:00
            val home = blocks.firstOrNull { it.start <= t && t < it.end }
            if (home == null) outside += o else timed.getValue(home.uid) += o
        } else {
            flexible[o.blockUid]?.add(o) ?: anyTime.add(o)
        }
    }
    // the key is a number as text ("10" after "9"), or x:<uid> for an added entry, which comes after
    val byOrder = compareBy<PlanOccurrence>({ it.sortOrder }, { it.habitUid }, { it.key.toIntOrNull() ?: Int.MAX_VALUE }, { it.key })
    return PlanDay(
        date = d,
        blocks = blocks.map { b ->
            PlacedBlock(b.uid, b.start, b.end, timed.getValue(b.uid).sortedWith(compareBy<PlanOccurrence> { it.time }.then(byOrder)), flexible.getValue(b.uid).sortedWith(byOrder))
        },
        outside = outside.sortedWith(compareBy<PlanOccurrence> { it.time }.then(byOrder)),
        anyTime = anyTime.sortedWith(byOrder),
        overlaps = overlaps,
    )
}
