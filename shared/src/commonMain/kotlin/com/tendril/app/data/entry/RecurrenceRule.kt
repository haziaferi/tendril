package com.tendril.app.data.entry

import java.time.Period

/**
 * §4.1 (R2 in the §9.8 architecture review): EVENT and TASK recurrence aren't the same
 * *shape* of data, so this is a sealed type rather than one loosely-typed string column
 * disambiguated by convention via `kind` — the same implicit-coupling shape that caused
 * the original TASK/EVENT conflation bug. The compiler, not developer memory, enforces
 * that a TASK row can never carry a [Fixed] rule or vice versa (enforced in
 * [com.tendril.app.domain.ResolveEntryUseCase] and the create/edit paths, not by the type
 * system alone — Room has no way to express "only one sealed subtype is legal for kind X").
 */
sealed class RecurrenceRule {
    /** EVENT recurrence — a real RFC5545 RRULE string, matching `CalendarContract.Events.RRULE`
     * and the Google Calendar API's recurrence format exactly (§4.1). */
    data class Fixed(val rrule: String) : RecurrenceRule()

    /** TASK recurrence — an elastic interval anchored to the *original fixed schedule*
     * (§6.2), not to whenever the previous instance was resolved. */
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
