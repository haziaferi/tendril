package com.tendril.app.domain.recurrence

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * One day of one occurrence of an [Entry] — what a calendar surface actually draws.
 *
 * A single-day, non-recurring Entry produces exactly one of these, so every existing
 * `entries.filter { it.startDate == day }` becomes `occurrences.filter { it.date == day }` with
 * no change in behaviour. A three-day event produces three; a weekly meeting produces one per
 * week in the window.
 *
 * [entry] is the row to act on when the occurrence is tapped: the series' base row for a
 * generated instance, or the override row itself where one exists (§4.1's `RECURRENCE-ID`-shaped
 * exception model). It is deliberately *not* a copy with the occurrence's dates patched in —
 * writing back through this must go to a real row, and inventing one would hide which.
 */
data class EntryOccurrence(
    val entry: Entry,
    /** The day inside the queried range that this covers. */
    val date: LocalDate,
    /** This occurrence's own first day — [date] for anything single-day. */
    val startDate: LocalDate,
    /** This occurrence's own last day — equal to [startDate] unless it is a multi-day span. */
    val endDate: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    /** True when this was generated from a recurrence rule rather than being the base row's
     * own single date — the flag a UI needs to say "this is one of a series." */
    val isRecurrenceInstance: Boolean,
) {
    val isMultiDay: Boolean get() = endDate.isAfter(startDate)

    /** False on the continuation days of a span — the day a "day 2 of 3" affordance keys off,
     * and the day on which times should be hidden (an all-day middle day has none). */
    val isFirstDay: Boolean get() = date == startDate
}

/**
 * Turns stored [Entry] rows into the occurrences a date range contains (§4.1).
 *
 * Written 2026-09-04 to close a gap that had been open since Phase 3: `RecurrenceRule.Fixed`
 * was only ever written *outward*, to `CalendarContract` (§9.11) and the Google Calendar API
 * (§9.5.1), and nothing ever read it back. Every Tendril surface rendered one row on one day,
 * so a weekly meeting appeared **once** in Tendril's own Calendar while appearing on every
 * occurrence in the system calendar Tendril itself publishes to — §9.11 defines the mirror's
 * scope as "whatever Tendril's own Calendar screen already shows," and it showed strictly less.
 * Multi-day spans (§4.1 round 1) and exception rows (§4.1 round 3, §9.8 R5) were dead for the
 * same reason: the fields were stored, synced, and never read.
 *
 * §9.8 R3 is unaffected and still holds — Room remains the single source of truth, and this
 * expands Room's own rows. Nothing here reads `CalendarContract.Instances`; that would be the
 * second, silently-divergent expander R3 exists to rule out.
 */
object EntryOccurrences {

    /**
     * Every occurrence-day in `[from, to]` (both inclusive), ordered by day, then timed
     * entries by time with untimed ones last, then title — the order `EntryDao.observeOnDate`
     * already uses (`ORDER BY startTime IS NULL, startTime`) and the one the Day view was
     * sorting into by hand.
     *
     * [entries] should be the live, dated, undeleted set — filtering by `startDate BETWEEN`
     * before calling this would be wrong, since a weekly series anchored a year ago has no row
     * inside the window at all.
     */
    fun expand(entries: List<Entry>, from: LocalDate, to: LocalDate): List<EntryOccurrence> {
        if (to.isBefore(from)) return emptyList()

        val exceptions = entries.filter { it.originalEntryId != null }
        val bases = entries.filter { it.originalEntryId == null && it.startDate != null }
        val overrideBy = exceptions
            .filter { it.originalOccurrenceDate != null }
            .associateBy { it.originalEntryId!! to it.originalOccurrenceDate!! }
        val consumed = mutableSetOf<Long>()

        val out = mutableListOf<EntryOccurrence>()
        for (base in bases) {
            val anchor = base.startDate ?: continue
            val span = spanDays(base)
            // Widen the search backwards by the span so an occurrence that *starts* before the
            // window but still covers a day inside it isn't missed.
            val starts = occurrenceStarts(base, anchor, from.minusDays(span), to)

            for (start in starts) {
                val override = overrideBy[base.id to start]
                if (override != null) {
                    consumed += override.id
                    // A skip tombstone means this date has no occurrence at all (§4.1) — the
                    // EXDATE-equivalent. Nothing else on the row matters.
                    if (override.isExceptionSkip == true) continue
                    if (override.deletedAt != null) continue
                    val overrideStart = override.startDate ?: continue
                    out += daysOf(
                        entry = override,
                        start = overrideStart,
                        end = overrideStart.plusDays(spanDays(override)),
                        startTime = override.startTime,
                        endTime = override.endTime,
                        isRecurrenceInstance = true,
                        from = from,
                        to = to,
                    )
                    continue
                }
                out += daysOf(
                    entry = base,
                    start = start,
                    // §4.1: a recurring occurrence preserves the *duration*, never the base
                    // row's absolute end date.
                    end = start.plusDays(span),
                    startTime = base.startTime,
                    endTime = base.endTime,
                    isRecurrenceInstance = start != anchor,
                    from = from,
                    to = to,
                )
            }
        }

        // An override whose base is absent from [entries], or whose target date the base no
        // longer produces, would otherwise vanish silently. Emit it on its own terms instead:
        // a stale exception showing up as an ordinary entry is recoverable, a disappeared one
        // is not.
        for (orphan in exceptions) {
            if (orphan.id in consumed || orphan.isExceptionSkip == true || orphan.deletedAt != null) continue
            val start = orphan.startDate ?: continue
            out += daysOf(
                entry = orphan,
                start = start,
                end = start.plusDays(spanDays(orphan)),
                startTime = orphan.startTime,
                endTime = orphan.endTime,
                isRecurrenceInstance = true,
                from = from,
                to = to,
            )
        }

        return out.sortedWith(
            compareBy<EntryOccurrence> { it.date }
                .thenBy { it.startTime == null }
                .thenBy { it.startTime ?: LocalTime.MIDNIGHT }
                .thenBy { it.entry.title }
        )
    }

    /** [expand] grouped for the grid-shaped surfaces — the Month view and the two widgets. */
    fun byDay(entries: List<Entry>, from: LocalDate, to: LocalDate): Map<LocalDate, List<EntryOccurrence>> =
        expand(entries, from, to).groupBy { it.date }

    /** [expand] for one day — the Day view's replacement for `filter { it.startDate == day }`. */
    fun onDay(entries: List<Entry>, day: LocalDate): List<EntryOccurrence> = expand(entries, day, day)

    /**
     * The first occurrence of [base] starting on or after [from], or null if the series has
     * none within [horizonDays].
     *
     * This is what lets alarms follow a recurring EVENT (§9.7). `AlarmScheduler` anchored every
     * alarm to `entry.startDate`, so once a series' first occurrence had passed, its
     * never-schedule-in-the-past guard suppressed everything after it — a weekly meeting
     * reminded exactly once, ever. The horizon is bounded because a rule with no `COUNT`/`UNTIL`
     * has no last occurrence to find.
     */
    fun nextOccurrenceOnOrAfter(
        base: Entry,
        exceptions: List<Entry>,
        from: LocalDate,
        horizonDays: Long = DEFAULT_ALARM_HORIZON_DAYS,
    ): EntryOccurrence? =
        expand(listOf(base) + exceptions, from, from.plusDays(horizonDays))
            .firstOrNull { occurrence ->
                // First day only: alarms anchor to when an occurrence starts, not to each day
                // a span covers. Either the base row itself, or an override standing in for one
                // of its occurrences — an override moves the occurrence, it doesn't remove it.
                occurrence.isFirstDay &&
                    (occurrence.entry.id == base.id || occurrence.entry.originalEntryId == base.id)
            }

    /** Far enough ahead to catch any realistic series, close enough that a rule with no end
     * can't make the walk unbounded. */
    const val DEFAULT_ALARM_HORIZON_DAYS: Long = 400

    private fun spanDays(entry: Entry): Long {
        val start = entry.startDate ?: return 0
        val end = entry.endDate ?: return 0
        // §4.1 restricts spans to EVENT; a TASK that somehow carries an end date is treated as
        // the single day it is specified to be rather than trusted into a span.
        if (entry.kind != EntryKind.EVENT) return 0
        return ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
    }

    /** The occurrence start dates [base] contributes inside `[rangeStart, rangeEnd]`. */
    private fun occurrenceStarts(
        base: Entry,
        anchor: LocalDate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        val rule = base.recurrenceRule
        // Only EVENT recurrence expands. A recurring TASK has exactly one live row at any
        // moment by design (§5.2, §6.2's resolve-and-advance) — expanding it would invent
        // occurrences that don't exist and can't be resolved.
        if (base.kind != EntryKind.EVENT || rule !is RecurrenceRule.Fixed) {
            return if (anchor in rangeStart..rangeEnd) listOf(anchor) else emptyList()
        }
        val spec = RecurrenceSpec.parse(rule.rrule)
            // Unparseable or outside the supported subset: fall back to the series' first
            // occurrence, which is exactly what every surface showed before this existed.
            ?: return if (anchor in rangeStart..rangeEnd) listOf(anchor) else emptyList()
        return spec.occurrenceDates(anchor, rangeStart, rangeEnd)
    }

    private fun daysOf(
        entry: Entry,
        start: LocalDate,
        end: LocalDate,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isRecurrenceInstance: Boolean,
        from: LocalDate,
        to: LocalDate,
    ): List<EntryOccurrence> {
        val firstVisible = maxOf(start, from)
        val lastVisible = minOf(end, to)
        if (lastVisible.isBefore(firstVisible)) return emptyList()
        return generateSequence(firstVisible) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastVisible) }
            .map { day ->
                EntryOccurrence(
                    entry = entry,
                    date = day,
                    startDate = start,
                    endDate = end,
                    startTime = startTime,
                    endTime = endTime,
                    isRecurrenceInstance = isRecurrenceInstance,
                )
            }
            .toList()
    }
}
