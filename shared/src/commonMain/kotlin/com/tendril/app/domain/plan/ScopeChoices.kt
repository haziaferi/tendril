package com.tendril.app.domain.plan

import java.time.DayOfWeek

/*
 * §6.3 (2026-09-29) — "Apply this change to…" (D10): which of the six scopes to offer for an entry,
 * and what a move writes for each. Ported from the planner's `askScope`, `scopeMaker`,
 * `placementSet` and `setForScope` (`planner/assets/app.js`), kept out of the UI so the rule that
 * hides identical choices is tested here rather than read off a sheet.
 */

/** The six choices, in the order the sheet lists them. */
enum class ScopeChoice { ONLY_THIS_ENTRY, THIS_DAY, THIS_WEEK, CHOSEN_WEEKDAYS_THIS_WEEK, FROM_NOW_ON, CHOSEN_WEEKDAYS_FROM_NOW_ON }

/**
 * §6.3 (D10) — the choices worth offering for [occurrence] in [week], from those [allowed]. A choice is hidden
 * when it would touch exactly the entries the choice before it touches: *This day* for a habit
 * once that day, *This week only* for a habit whose only entries this week are the ones already
 * covered. The weekday choices are never hidden: the person picks the days. An added entry (moved
 * from another day) and a habit that happens once have nothing to repeat into, so they get only
 * *Only this entry*, and the sheet need not be shown at all.
 */
fun scopeChoices(week: PlanWeek, occurrence: PlanOccurrence, allowed: Set<ScopeChoice> = ScopeChoice.entries.toSet()): List<ScopeChoice> {
    if (!occurrence.recurring || occurrence.moved) return listOf(ScopeChoice.ONLY_THIS_ENTRY)
    // the planner counted block entries only, because it dropped the rest; Tendril keeps Any time and outside entries, and an edit reaches them too
    val all = week.days.flatMap { d -> d.blocks.flatMap { it.timed + it.flexible } + d.anyTime + d.outside }.filter { it.habitUid == occurrence.habitUid }
    fun keys(of: List<PlanOccurrence>) = of.map { occurrenceId(it.habitUid, it.date, it.key) }.sorted()
    val occKeys = listOf(occurrenceId(occurrence.habitUid, occurrence.date, occurrence.key))
    val dayKeys = keys(all.filter { it.date == occurrence.date })
    val weekKeys = keys(all)
    val out = mutableListOf<ScopeChoice>()
    if (ScopeChoice.ONLY_THIS_ENTRY in allowed) out += ScopeChoice.ONLY_THIS_ENTRY
    if (ScopeChoice.THIS_DAY in allowed && !(ScopeChoice.ONLY_THIS_ENTRY in allowed && dayKeys == occKeys)) out += ScopeChoice.THIS_DAY
    if (ScopeChoice.THIS_WEEK in allowed && !(out.isNotEmpty() && weekKeys == (if (ScopeChoice.THIS_DAY in out) dayKeys else occKeys))) out += ScopeChoice.THIS_WEEK
    listOf(ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK, ScopeChoice.FROM_NOW_ON, ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON).filterTo(out) { it in allowed }
    return out
}

/** §6.3 — the scope a choice writes for an occurrence on [date]; an added entry is always addressed alone. */
fun scopeFor(choice: ScopeChoice, occurrence: PlanOccurrence, days: Set<DayOfWeek>? = null): EditScope {
    val d = occurrence.date
    if (occurrence.moved) return EditScope.Occurrence(d, occurrence.key)
    // the planner refused an empty choice; a null or empty set here would quietly widen to every day
    if (choice == ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK || choice == ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON) {
        require(!days.isNullOrEmpty()) { "choose at least one weekday" }
    }
    return when (choice) {
        ScopeChoice.ONLY_THIS_ENTRY -> EditScope.Occurrence(d, occurrence.key)
        ScopeChoice.THIS_DAY -> EditScope.Day(d)
        ScopeChoice.THIS_WEEK -> EditScope.Week(mondayOf(d))
        ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK -> EditScope.Week(mondayOf(d), days)
        ScopeChoice.FROM_NOW_ON -> EditScope.From(d)
        ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON -> EditScope.From(d, days)
    }
}

/**
 * §6.3 (P5) — a move of one entry, as the planner's `placementSet` expresses it: [entry] is what "Only this
 * entry" writes; [series] (if any) is what every wider choice writes instead, because a
 * several-a-day or every-N-hours habit keeps one slot per entry in its rule. [allowed] narrows the
 * sheet: an every-N-hours step cannot leave the series going forward (P5).
 */
data class PlacementChange(val entry: EditChanges, val series: EditChanges?, val allowed: Set<ScopeChoice>)

/**
 * [habit] as it stands on the occurrence's day; [time] and [blockUid] are what the move changes
 * (null [Patch] = unchanged); [blocks] in their order, for a several-a-day habit spread over them.
 */
fun placementChange(
    habit: PlanHabit,
    occurrence: PlanOccurrence,
    blocks: List<PlanBlock>,
    time: Patch<Int?>? = null,
    blockUid: String? = null,
    sortOrder: Double? = null,
): PlacementChange {
    val entry = EditChanges(blockUid = blockUid?.let { Patch(it) }, time = time, sortOrder = sortOrder)
    val rule = habit.rule
    val tag = occurrence.tag
    return when {
        // the series keeps one slot per occurrence: a block, or a set time
        rule is CalendarRule.TimesPerDay && tag is OccurrenceTag.Count && (time != null || blockUid != null) -> {
            val slots = rule.slots.ifEmpty { spread(blocks.size, rule.n).map { Slot.InBlock(blocks[it].uid) } }.toMutableList()
            val i = tag.i - 1
            if (i in slots.indices) {
                slots[i] = time?.value?.let { Slot.At(it) } ?: blockUid?.let { Slot.InBlock(it) } ?: slots[i]
            }
            PlacementChange(entry, EditChanges(rule = CalendarRule.TimesPerDay(rule.n, slots), sortOrder = sortOrder), ScopeChoice.entries.toSet())
        }
        rule is CalendarRule.EveryNHours && tag == OccurrenceTag.TimeStep -> {
            val to = time?.value
            val at = occurrence.time
            if (to != null && at != null) {
                val shift = to - at
                PlacementChange(entry, EditChanges(rulePatch = RulePatch(from = rule.from + shift, until = minOf(24 * 60 - 1, rule.until + shift)), sortOrder = sortOrder), ScopeChoice.entries.toSet())
            } else {
                // a regular series cannot drop one step going forward (P5)
                PlacementChange(entry, null, setOf(ScopeChoice.ONLY_THIS_ENTRY))
            }
        }
        else -> PlacementChange(entry, null, ScopeChoice.entries.toSet())
    }
}

/** What a [PlacementChange] writes under [choice]: the entry's own change for one entry, the series change for any wider scope. */
fun changesFor(change: PlacementChange, choice: ScopeChoice): EditChanges =
    if (change.series != null && choice != ScopeChoice.ONLY_THIS_ENTRY) change.series else change.entry