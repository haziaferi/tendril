package com.tendril.app.data.entry

import java.time.Period

/**
 * §4.1 (R2 in the §9.8 architecture review): EVENT and TASK recurrence aren't the same
 * *shape* of data, so this is a sealed type rather than one loosely-typed string column
 * disambiguated by convention via `kind` — the same implicit-coupling shape that caused
 * the original TASK/EVENT conflation bug.
 *
 * **The kind/rule correspondence is a convention, not an enforced invariant.** Earlier text here
 * said it was "enforced in `ResolveEntryUseCase` and the create/edit paths"; it isn't.
 * `ResolveEntryUseCase` checks only the status and `kind == TASK`, and nothing checks the rule
 * against the kind anywhere. What actually holds it is the *shape* of the two writers —
 * `TasksHabitsViewModel.addTask` takes an `Elastic?` parameter, and `GoogleEvent.toEntry` always
 * builds EVENT+`Fixed` — so every restore path bypasses it: `SnapshotMappers` rebuilds the rule
 * from a `"FIXED:"`/`"ELASTIC:"` tag with no cross-check, and a hand-edited snapshot can produce
 * an illegal row Room will happily accept. A TASK carrying [Fixed] then resolves as a one-off
 * with its recurrence silently ignored.
 */
sealed class RecurrenceRule {
    /** EVENT recurrence — a real RFC5545 RRULE string, matching `CalendarContract.Events.RRULE`
     * and the Google Calendar API's recurrence format exactly (§4.1). */
    data class Fixed(val rrule: String) : RecurrenceRule()

    /**
     * TASK recurrence — a plain repeating interval, anchored to the **original schedule** (§6.2),
     * not to whenever the previous instance was resolved.
     *
     * The name is a misnomer and is kept deliberately. §4.1 coined "Elastic" for the opposite
     * rule (the gap stretching by however late you were), §6.2 decided against that rule, and the
     * two sat contradicting each other until the 2026-09-04 correction in §6.2 settled it. Renaming
     * the type now would invalidate the persisted `"ELASTIC:"` tag in `Converters` and in every
     * snapshot file already written (§9.4), for no behavioural gain.
     */
    data class Elastic(val period: Period) : RecurrenceRule()
}

enum class IntervalUnit { DAY, WEEK, MONTH }

/** Builds an ISO-8601 [Period] from a (number, unit) pair — the same representation
 * §5.2.2's `Interval` property type uses, reused here for standalone Task recurrence. */
fun intervalToPeriod(count: Int, unit: IntervalUnit): Period = when (unit) {
    IntervalUnit.DAY -> Period.ofDays(count)
    IntervalUnit.WEEK -> Period.ofWeeks(count)
    IntervalUnit.MONTH -> Period.ofMonths(count)
}
