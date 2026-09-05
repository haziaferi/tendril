package com.tendril.app.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The `FREQ` values this app expands. Anything else makes [RecurrenceSpec.parse] return null. */
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** One `BYDAY` term: `MO` (no ordinal), `2TU` (second Tuesday), `-1FR` (last Friday). */
data class WeekdayNum(val ordinal: Int?, val day: DayOfWeek)

/**
 * A parsed RFC5545 `RRULE`, for expanding a recurring EVENT into the occurrences a calendar
 * surface actually shows (§4.1). [RecurrenceRule.Fixed][com.tendril.app.data.entry.RecurrenceRule.Fixed]
 * stores the raw string because that is what `CalendarContract.Events.RRULE` and the Google
 * Calendar API both take verbatim; this is the read side of it.
 *
 * **Hand-rolled, against §4.1's own suggestion of `lib-recur`** — see §4.1's 2026-09-04
 * correction for the reasoning: the rules this app has to read come from one producer (Google
 * Calendar's own API, the only thing that ever creates a `Fixed` rule here), the subset that
 * producer emits is small and stable, and this codebase's consistent call has been to
 * hand-roll a bounded grammar rather than take a dependency for it — the Notion CSV and
 * Markdown parsers (§7.4) and the nav shell were all decided the same way. The trade is real
 * and is the reason [parse] is strict rather than lenient.
 *
 * **Strict on purpose.** A rule carrying a part this does not implement returns `null`, and the
 * caller falls back to showing the series' first occurrence only — today's behaviour, and
 * wrong in the direction of *missing* an occurrence. Guessing would put an event on a day it
 * does not happen, which for a calendar is the worse failure: a missing entry is visibly
 * missing, a phantom one is believed.
 */
data class RecurrenceSpec(
    val frequency: Frequency,
    val interval: Int = 1,
    val count: Int? = null,
    /** Inclusive, date-granularity — see the note in [parse] about `UNTIL`'s time part. */
    val until: LocalDate? = null,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    companion object {
        /** Parts that genuinely change which dates are produced and that this does not
         * implement. Their presence makes the whole rule unsupported rather than silently
         * ignored, since ignoring a limiter *adds* occurrences that shouldn't exist. */
        private val UNSUPPORTED = setOf("BYSETPOS", "BYWEEKNO", "BYYEARDAY", "BYHOUR", "BYMINUTE", "BYSECOND")

        private val WEEKDAYS = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY,
        )

        private val BYDAY_TERM = Regex("^([+-]?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)$")

        /**
         * @return null when the rule is empty, malformed, or uses a part outside the supported
         * subset. Callers treat null as "not expandable" and show the base occurrence alone.
         */
        fun parse(rrule: String): RecurrenceSpec? {
            val body = rrule.trim().removePrefix("RRULE:").trim()
            if (body.isEmpty()) return null

            val parts = mutableMapOf<String, String>()
            for (chunk in body.split(';')) {
                if (chunk.isBlank()) continue
                val eq = chunk.indexOf('=')
                if (eq <= 0) return null
                val name = chunk.substring(0, eq).trim().uppercase()
                if (name in UNSUPPORTED) return null
                parts[name] = chunk.substring(eq + 1).trim()
            }

            val frequency = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> Frequency.DAILY
                "WEEKLY" -> Frequency.WEEKLY
                "MONTHLY" -> Frequency.MONTHLY
                "YEARLY" -> Frequency.YEARLY
                // SECONDLY/MINUTELY/HOURLY are legal RFC5545 and meaningless for a
                // day-granularity calendar grid; absent FREQ is malformed.
                else -> return null
            }

            val interval = parts["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null } ?: 1
            val count = parts["COUNT"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null }
            val until = parts["UNTIL"]?.let { parseUntil(it) ?: return null }
            // RFC5545 forbids both; a rule carrying both is malformed, not something to guess at.
            if (count != null && until != null) return null

            val byDay = parts["BYDAY"]?.split(',')?.map { term ->
                val match = BYDAY_TERM.find(term.trim().uppercase()) ?: return null
                val ordinal = match.groupValues[1].takeIf { it.isNotEmpty() }?.removePrefix("+")?.toIntOrNull()
                if (match.groupValues[1].isNotEmpty() && (ordinal == null || ordinal == 0)) return null
                WeekdayNum(ordinal, WEEKDAYS.getValue(match.groupValues[2]))
            } ?: emptyList()

            val byMonthDay = parts["BYMONTHDAY"]?.split(',')?.map {
                it.trim().toIntOrNull()?.takeIf { d -> d in 1..31 || d in -31..-1 } ?: return null
            } ?: emptyList()

            val byMonth = parts["BYMONTH"]?.split(',')?.map {
                it.trim().toIntOrNull()?.takeIf { m -> m in 1..12 } ?: return null
            } ?: emptyList()

            val weekStart = parts["WKST"]?.let { WEEKDAYS[it.trim().uppercase()] ?: return null } ?: DayOfWeek.MONDAY

            return RecurrenceSpec(frequency, interval, count, until, byDay, byMonthDay, byMonth, weekStart)
        }

        /**
         * `UNTIL` is either a date (`20260901`) or a UTC date-time (`20260901T000000Z`).
         *
         * Only the date part is kept. Converting the UTC instant into the event's own zone to
         * decide whether the final occurrence is in or out would need a zone this layer doesn't
         * have, and being generous by up to a day at the tail of a series shows one occurrence
         * too many at worst — against dropping a real one, which is what truncating risks.
         */
        private fun parseUntil(raw: String): LocalDate? {
            val date = raw.trim().substringBefore('T')
            if (date.length != 8) return null
            val year = date.substring(0, 4).toIntOrNull() ?: return null
            val month = date.substring(4, 6).toIntOrNull() ?: return null
            val day = date.substring(6, 8).toIntOrNull() ?: return null
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
    }
}

/**
 * Every occurrence start date of this rule anchored at [anchor], restricted to
 * `[rangeStart, rangeEnd]` inclusive.
 *
 * Iteration always begins at [anchor], never at [rangeStart], because `COUNT` is defined over
 * the whole series — a rule with `COUNT=3` must stop after three occurrences even when the
 * window being drawn starts years later. [MAX_PERIODS] bounds that walk so a far-future window
 * against a daily rule can't spin.
 *
 * Per RFC5545, `DTSTART` — here [anchor] — is always the first occurrence, whether or not it
 * satisfies the `BY*` parts.
 */
fun RecurrenceSpec.occurrenceDates(
    anchor: LocalDate,
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
): List<LocalDate> {
    if (rangeEnd.isBefore(rangeStart)) return emptyList()

    val collected = mutableListOf<LocalDate>()
    var emitted = 0

    fun offer(date: LocalDate): Boolean {
        // false = the series is over; stop the whole walk.
        if (date.isBefore(anchor)) return true
        if (until != null && date.isAfter(until)) return false
        if (count != null && emitted >= count) return false
        emitted++
        if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) collected += date
        return true
    }

    if (!offer(anchor)) return collected

    // From period 0, not 1: the anchor's own period can hold further occurrences. A
    // `FREQ=WEEKLY;BYDAY=MO,WE` series anchored on the Monday also occurs that same
    // Wednesday, and starting at period 1 silently dropped it. Candidates equal to the anchor
    // are skipped below rather than double-counted, and `offer` ignores anything before it.
    var period = 0
    while (period <= MAX_PERIODS) {
        // Terminate on where the *period* begins, not on the candidates it produced. A period
        // can legitimately produce none — `BYMONTHDAY=31` in February, a `BYDAY` limiter on a
        // DAILY rule — and testing an empty list would walk to MAX_PERIODS every time instead
        // of stopping at the edge of the window.
        if (periodStart(anchor, period).isAfter(rangeEnd)) break
        for (date in candidatesForPeriod(anchor, period)) {
            if (date == anchor) continue // DTSTART already offered, don't double-count it
            if (!offer(date)) return collected
        }
        period++
    }
    return collected
}

/** The first day of the [period]-th interval after [anchor]'s own — the loop bound, not an
 * occurrence. Always on or before every candidate that period can produce. */
private fun RecurrenceSpec.periodStart(anchor: LocalDate, period: Int): LocalDate {
    val step = period.toLong() * interval
    return when (frequency) {
        Frequency.DAILY -> anchor.plusDays(step)
        Frequency.WEEKLY ->
            anchor.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(step)
        Frequency.MONTHLY -> YearMonth.from(anchor).plusMonths(step).atDay(1)
        Frequency.YEARLY -> YearMonth.from(anchor).plusMonths(step * 12).atDay(1)
    }
}

private const val MAX_PERIODS = 5000

/** The candidate dates this rule produces in its [period]-th interval after [anchor]'s own,
 * already ordered and already filtered by the `BY*` parts that *limit* rather than expand. */
private fun RecurrenceSpec.candidatesForPeriod(anchor: LocalDate, period: Int): List<LocalDate> {
    val step = period.toLong() * interval
    val raw: List<LocalDate> = when (frequency) {
        // BYDAY/BYMONTHDAY/BYMONTH only limit here — the frequency itself picks the day.
        Frequency.DAILY -> listOf(anchor.plusDays(step))

        // BYDAY expands: one date per named weekday inside this week.
        Frequency.WEEKLY -> {
            val weekStartDate = anchor.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart))
                .plusWeeks(step)
            val days = byDay.map { it.day }.ifEmpty { listOf(anchor.dayOfWeek) }
            days.map { day ->
                weekStartDate.plusDays(((day.value - weekStart.value) + 7) % 7L)
            }.sorted()
        }

        Frequency.MONTHLY -> {
            val month = YearMonth.from(anchor).plusMonths(step)
            datesInMonth(month, defaultDayOfMonth = anchor.dayOfMonth)
        }

        // BYMONTH expands here rather than limiting.
        Frequency.YEARLY -> {
            // Via YearMonth rather than `anchor.year + step`: month arithmetic on the
            // java.time type clamps at the supported year range instead of overflowing Int.
            val year = YearMonth.from(anchor).plusMonths(step * 12).year
            val months = byMonth.ifEmpty { listOf(anchor.monthValue) }
            months.sorted().flatMap { m ->
                runCatching { YearMonth.of(year, m) }.getOrNull()
                    ?.let { datesInMonth(it, defaultDayOfMonth = anchor.dayOfMonth) }
                    .orEmpty()
            }
        }
    }

    return raw.filter { date ->
        // YEARLY has already consumed BYMONTH as an expander; for the rest it limits.
        (frequency == Frequency.YEARLY || byMonth.isEmpty() || date.monthValue in byMonth) &&
            // Likewise BYDAY: an expander for WEEKLY/MONTHLY/YEARLY, a limiter for DAILY.
            (frequency != Frequency.DAILY || byDay.isEmpty() || byDay.any { it.day == date.dayOfWeek }) &&
            (frequency != Frequency.DAILY || byMonthDay.isEmpty() || matchesMonthDay(date, byMonthDay))
    }.distinct().sorted()
}

/** MONTHLY/YEARLY day selection: `BYMONTHDAY` and ordinal `BYDAY` both expand, a plain
 * `BYDAY` alongside `BYMONTHDAY` limits it, and with neither the anchor's own day is used.
 * A day that doesn't exist in the month (the 31st of February) is skipped, per RFC5545 —
 * never clamped, which would silently move the occurrence. */
private fun RecurrenceSpec.datesInMonth(month: YearMonth, defaultDayOfMonth: Int): List<LocalDate> {
    val length = month.lengthOfMonth()

    val fromMonthDay = byMonthDay.mapNotNull { d ->
        val day = if (d > 0) d else length + d + 1
        if (day in 1..length) month.atDay(day) else null
    }

    val ordinalDays = byDay.filter { it.ordinal != null }
    val fromByDay = ordinalDays.mapNotNull { term ->
        val matching = (1..length).map { month.atDay(it) }.filter { it.dayOfWeek == term.day }
        val ordinal = term.ordinal!!
        matching.getOrNull(if (ordinal > 0) ordinal - 1 else matching.size + ordinal)
    }

    val plainDays = byDay.filter { it.ordinal == null }.map { it.day }

    return when {
        fromMonthDay.isNotEmpty() && plainDays.isNotEmpty() ->
            fromMonthDay.filter { it.dayOfWeek in plainDays }
        fromMonthDay.isNotEmpty() -> fromMonthDay
        fromByDay.isNotEmpty() -> fromByDay
        plainDays.isNotEmpty() -> (1..length).map { month.atDay(it) }.filter { it.dayOfWeek in plainDays }
        // A selector that matched nothing this month means *nothing this month* — falling
        // through to the anchor's own day would invent an occurrence the rule never described
        // (`BYMONTHDAY=31` anchored on the 1st would have produced the 1st of February).
        byMonthDay.isNotEmpty() || byDay.isNotEmpty() -> emptyList()
        else -> listOfNotNull(if (defaultDayOfMonth <= length) month.atDay(defaultDayOfMonth) else null)
    }.sorted()
}

private fun matchesMonthDay(date: LocalDate, byMonthDay: List<Int>): Boolean {
    val length = date.lengthOfMonth()
    return byMonthDay.any { d -> if (d > 0) date.dayOfMonth == d else date.dayOfMonth == length + d + 1 }
}
