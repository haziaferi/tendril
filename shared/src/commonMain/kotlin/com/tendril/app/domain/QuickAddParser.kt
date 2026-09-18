package com.tendril.app.domain

import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.temporal.TemporalAdjusters

/** What a token of the line turned into, so a preview can name it and offer to drop it. */
enum class TokenKind { KIND, DATE, TIME, SPAN, REPEAT, DEADLINE, IMPORTANT }

/** The characters of the original line that became one [TokenKind]. */
data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

/**
 * §0.8 step 5 / B§6 #3 — one line, read into the fields an Entry has. Nothing here writes: the
 * caller shows [spans] as chips, lets the person drop one, and only then creates the Entry.
 * [title] is the line with every recognised token removed and the whitespace closed up.
 */
data class ParsedEntry(
    val title: String,
    val kind: EntryKind,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val endTime: LocalTime? = null,
    val endDate: LocalDate? = null,
    val recurrence: RecurrenceRule? = null,
    /** TASK only — §0.6.4's second date, never the When. */
    val deadline: LocalDate? = null,
    /** TASK only — `for 45m` on a task is how long it takes, not a span. */
    val estimate: Duration? = null,
    /** TASK only — the ladder's set level (14g·3): `!` high, `!!` or more urgent, the word *important* high. */
    val importance: Int = 0,
    val spans: List<TokenSpan> = emptyList(),
)

/**
 * The grammar, English first. Recognised anywhere in the line, in any order; the first match
 * of each kind wins and later ones stay in the title. Dates are day-first where ambiguous
 * (`20/9`), because the device is European; ISO dates and 24-hour times read in any language,
 * which is the one concession to a phone running Italian until Italian words are added.
 *
 * Kind: the surface's default (Calendar → EVENT, Tasks → TASK); a time *span* forces EVENT — a
 * task is never a span (§4.1); a leading `todo`/`task` forces TASK, a leading `event` forces
 * EVENT. Recurrence is emitted in the kind's own shape: `Fixed` RRULE for an event, `Elastic`
 * period for a task, so `every monday` is `FREQ=WEEKLY;BYDAY=MO` on one and `P1W` anchored on
 * next Monday on the other.
 */
object QuickAddParser {

    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7, "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10, "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12,
    )
    private val BYDAY = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
    )
    private const val WD = "monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun"
    private const val MON = "january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec"
    private const val CLOCK = "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?"

    private val KIND_PREFIX = Regex("^\\s*(todo|task|event)\\b[:\\s]*", RegexOption.IGNORE_CASE)
    private val IMPORTANT = Regex("(?:\\s*!+\\s*$)|(?:\\bimportant\\b)", RegexOption.IGNORE_CASE)

    // Recurrence first: `every monday` must not be read as the date `monday`.
    private val REPEAT = Regex(
        "\\b(daily|weekly|monthly|every\\s+(?:day|week|month|weekday|(\\d+)\\s*(days?|weeks?|months?)|($WD)))\\b",
        RegexOption.IGNORE_CASE,
    )
    private val DEADLINE = Regex("\\b(?:by|due)\\s+", RegexOption.IGNORE_CASE)

    // Dates. Order matters within `DATE_FORMS`: the longer, more specific forms first.
    private val DATE_FORMS: List<Pair<Regex, (MatchResult, LocalDate) -> LocalDate?>> = listOf(
        Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b") to { m, _ -> runCatching { LocalDate.of(m.gv(1).toInt(), m.gv(2).toInt(), m.gv(3).toInt()) }.getOrNull() },
        Regex("\\b(today|tonight)\\b", RegexOption.IGNORE_CASE) to { _, today -> today },
        Regex("\\b(tomorrow|tmr|tmrw)\\b", RegexOption.IGNORE_CASE) to { _, today -> today.plusDays(1) },
        Regex("\\bin\\s+(\\d+)\\s*(days?|weeks?|months?)\\b", RegexOption.IGNORE_CASE) to { m, today ->
            val n = m.gv(1).toLong()
            when (m.gv(2).lowercase().first()) { 'd' -> today.plusDays(n); 'w' -> today.plusWeeks(n); else -> today.plusMonths(n) }
        },
        Regex("\\b(next\\s+)?($WD)\\b", RegexOption.IGNORE_CASE) to { m, today ->
            val day = WEEKDAYS.getValue(m.gv(2).lowercase())
            val next = today.with(TemporalAdjusters.next(day))
            if (m.groups[1] != null) next.plusWeeks(1) else next
        },
        Regex("\\b($MON)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", RegexOption.IGNORE_CASE) to { m, today -> monthDay(MONTHS.getValue(m.gv(1).lowercase()), m.gv(2).toInt(), today) },
        Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+($MON)\\b", RegexOption.IGNORE_CASE) to { m, today -> monthDay(MONTHS.getValue(m.gv(2).lowercase()), m.gv(1).toInt(), today) },
        Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b") to { m, today ->
            val year = m.groups[3]?.value?.toInt()
            if (year != null) runCatching { LocalDate.of(year, m.gv(2).toInt(), m.gv(1).toInt()) }.getOrNull()
            else monthDay(m.gv(2).toInt(), m.gv(1).toInt(), today)
        },
    )

    // Times. A span first, so `3-4pm` is not read as the time `3` and a stray `-4pm`.
    private val SPAN = Regex("\\b(?:at\\s+|from\\s+)?$CLOCK\\s*(?:-|–|to)\\s*$CLOCK\\b", RegexOption.IGNORE_CASE)
    private val TIME = Regex("\\b(?:at\\s+)?(?:(noon|midnight)|$CLOCK)\\b", RegexOption.IGNORE_CASE)
    // `for 1h`, `for 1h30`, `for 1 hour 15 min`, `for 45m` — an hour part may carry bare minutes
    // (`1h30`); a minutes-only form needs its unit, so `for 2 people` stays in the title.
    private val FOR_HOURS = Regex("\\bfor\\s+(\\d+)\\s*h(?:ours?|rs?)?(?:\\s*(\\d{1,2})\\s*(?:m(?:in(?:utes?)?)?)?)?\\b", RegexOption.IGNORE_CASE)
    private val FOR_MINUTES = Regex("\\bfor\\s+(\\d+)\\s*m(?:in(?:utes?)?)?\\b", RegexOption.IGNORE_CASE)

    /**
     * [ignore] — token kinds the person has dismissed from the preview ("no, `sat` is a word"):
     * they are not recognised and their text stays in the title. [kindOverride] is the preview's
     * kind chip, flipped; it beats the prefix and the default, but a span still means EVENT.
     */
    fun parse(
        line: String,
        today: LocalDate,
        defaultKind: EntryKind,
        ignore: Set<TokenKind> = emptySet(),
        kindOverride: EntryKind? = null,
    ): ParsedEntry {
        val spans = mutableListOf<TokenSpan>()
        var kind = kindOverride ?: defaultKind
        var kindForced = kindOverride != null

        KIND_PREFIX.find(line)?.let { m ->
            if (kindOverride == null) kind = if (m.gv(1).equals("event", ignoreCase = true)) EntryKind.EVENT else EntryKind.TASK
            kindForced = true
            spans += TokenSpan(m.range.first, m.range.last + 1, TokenKind.KIND)
        }

        val importance = if (TokenKind.IMPORTANT in ignore) 0 else
            IMPORTANT.find(line)?.let { m ->
                spans += TokenSpan(m.range.first, m.range.last + 1, TokenKind.IMPORTANT)
                if (m.value.count { it == '!' } >= 2) 4 else 3
            } ?: 0

        // Recurrence — read before dates so its weekday is not also a date.
        var repeat: Repeat? = null
        if (TokenKind.REPEAT !in ignore) REPEAT.find(line)?.let { m ->
            repeat = repeatOf(m)
            spans += TokenSpan(m.range.first, m.range.last + 1, TokenKind.REPEAT)
        }

        // Deadline: `by <date>` / `due <date>` — the date form right after the word.
        var deadline: LocalDate? = null
        if (TokenKind.DEADLINE !in ignore) for (m in DEADLINE.findAll(line)) {
            if (spans.any { it.covers(m.range.first) }) continue
            val rest = line.substring(m.range.last + 1)
            val hit = DATE_FORMS.firstNotNullOfOrNull { (re, f) ->
                re.find(rest)?.takeIf { it.range.first == 0 }?.let { d -> f(d, today)?.let { it to d } }
            } ?: continue
            deadline = hit.first
            spans += TokenSpan(m.range.first, m.range.last + 1 + hit.second.range.last + 1, TokenKind.DEADLINE)
            break
        }

        var date: LocalDate? = null
        if (TokenKind.DATE !in ignore) for ((re, f) in DATE_FORMS) {
            val m = re.findAll(line).firstOrNull { c -> spans.none { it.overlaps(c.range) } } ?: continue
            val d = f(m, today) ?: continue
            date = d
            spans += TokenSpan(m.range.first, m.range.last + 1, TokenKind.DATE)
            break
        }

        var time: LocalTime? = null
        var endTime: LocalTime? = null
        if (TokenKind.SPAN !in ignore) SPAN.findAll(line).firstOrNull { c -> spans.none { it.overlaps(c.range) } }?.let { m ->
            val (a, b) = spanTimes(m) ?: return@let
            time = a; endTime = b
            spans += TokenSpan(m.range.first, m.range.last + 1, TokenKind.SPAN)
        }
        if (time == null && TokenKind.TIME !in ignore) {
            TIME.findAll(line).firstOrNull { c -> spans.none { it.overlaps(c.range) } && clockTime(c, 1) != null }?.let { m ->
                time = clockTime(m, 1)
                spans += TokenSpan(m.range.first, m.range.last + 1, TokenKind.TIME)
            }
        }

        var duration: Duration? = null
        if (TokenKind.SPAN !in ignore) {
            val hours = FOR_HOURS.findAll(line).firstOrNull { c -> spans.none { it.overlaps(c.range) } }
            val minutes = if (hours == null) FOR_MINUTES.findAll(line).firstOrNull { c -> spans.none { it.overlaps(c.range) } } else null
            if (hours != null) {
                duration = Duration.ofHours(hours.gv(1).toLong()).plusMinutes(hours.groups[2]?.value?.toLong() ?: 0L)
                spans += TokenSpan(hours.range.first, hours.range.last + 1, TokenKind.SPAN)
            } else if (minutes != null) {
                duration = Duration.ofMinutes(minutes.gv(1).toLong())
                spans += TokenSpan(minutes.range.first, minutes.range.last + 1, TokenKind.SPAN)
            }
        }

        // A span is an event's; a task is never a span (§4.1).
        if (endTime != null && !kindForced) kind = EntryKind.EVENT
        if (kind == EntryKind.EVENT && endTime == null && time != null && duration != null) {
            endTime = time!!.plus(duration)
        }
        val estimate = if (kind == EntryKind.TASK) duration else null
        if (kind == EntryKind.TASK) endTime = null
        // A deadline is a task's (§0.6.4): an event's `by friday` was read, dropped, and its words
        // vanished from the title with no chip to say so — L7b's inline tint made that visible
        // (2026-09-18). The words go back to the title, untinted, unchipped.
        if (kind == EntryKind.EVENT) spans.removeAll { it.kind == TokenKind.DEADLINE }

        val title = line.removeSpans(spans).replace(Regex("\\s{2,}"), " ").trim(' ', ':', ',', '-')

        // A repeat with no date anchors on the next such weekday, or today, so the series has a start.
        val anchoredDate = date ?: repeat?.let { r -> r.anchorDay?.let { today.with(TemporalAdjusters.nextOrSame(it)) } ?: today }

        return ParsedEntry(
            title = title,
            kind = kind,
            date = anchoredDate,
            time = time,
            endTime = if (kind == EntryKind.EVENT) endTime else null,
            endDate = if (kind == EntryKind.EVENT && endTime != null) anchoredDate else null,
            recurrence = repeat?.let { if (kind == EntryKind.EVENT) it.fixed() else it.elastic() },
            deadline = if (kind == EntryKind.TASK) deadline else null,
            estimate = estimate,
            importance = importance,
            spans = spans.sortedBy { it.start },
        )
    }

    /** One recurrence read from the line, shaped for either kind on demand. */
    private class Repeat(val unit: Char, val interval: Int, val anchorDay: DayOfWeek? = null, val weekdays: Boolean = false) {
        fun elastic(): RecurrenceRule.Elastic = RecurrenceRule.Elastic(
            when (unit) { 'd' -> Period.ofDays(if (weekdays) 1 else interval); 'w' -> Period.ofWeeks(interval); else -> Period.ofMonths(interval) },
        )
        fun fixed(): RecurrenceRule.Fixed {
            val freq = when (unit) { 'd' -> "DAILY"; 'w' -> "WEEKLY"; else -> "MONTHLY" }
            val parts = mutableListOf("FREQ=$freq")
            if (interval > 1) parts += "INTERVAL=$interval"
            if (weekdays) parts += "BYDAY=MO,TU,WE,TH,FR"
            else anchorDay?.let { parts += "BYDAY=${BYDAY.getValue(it)}" }
            return RecurrenceRule.Fixed(parts.joinToString(";"))
        }
    }

    private fun repeatOf(m: MatchResult): Repeat {
        val whole = m.gv(1).lowercase()
        return when {
            whole == "daily" || whole == "every day" -> Repeat('d', 1)
            whole == "weekly" || whole == "every week" -> Repeat('w', 1)
            whole == "monthly" || whole == "every month" -> Repeat('m', 1)
            whole == "every weekday" -> Repeat('d', 1, weekdays = true)
            m.groups[2] != null -> Repeat(m.gv(3).lowercase().first(), m.gv(2).toInt())
            else -> Repeat('w', 1, anchorDay = WEEKDAYS.getValue(m.gv(4).lowercase()))
        }
    }

    private fun spanTimes(m: MatchResult): Pair<LocalTime, LocalTime>? {
        // Groups: 1-3 start (h, m, am/pm), 4-6 end. An unmarked start borrows the end's am/pm:
        // `3-4pm` is 15:00–16:00.
        val endMeridiem = m.groups[6]?.value
        val start = clock(m.gv(1).toInt(), m.groups[2]?.value?.toInt(), m.groups[3]?.value ?: endMeridiem) ?: return null
        val end = clock(m.gv(4).toInt(), m.groups[5]?.value?.toInt(), endMeridiem) ?: return null
        return if (end > start) start to end else null
    }

    private fun clockTime(m: MatchResult, offset: Int): LocalTime? {
        m.groups[offset]?.value?.lowercase()?.let { word -> return if (word == "noon") LocalTime.NOON else LocalTime.MIDNIGHT }
        val hour = m.groups[offset + 1]?.value?.toIntOrNull() ?: return null
        val minute = m.groups[offset + 2]?.value?.toInt()
        val meridiem = m.groups[offset + 3]?.value
        // A bare number with neither minutes nor am/pm is not a time — `in 3 days`, `20 sep`.
        if (minute == null && meridiem == null) return null
        return clock(hour, minute, meridiem)
    }

    private fun clock(hour: Int, minute: Int?, meridiem: String?): LocalTime? {
        var h = hour
        when (meridiem?.lowercase()) {
            "pm" -> if (h in 1..11) h += 12
            "am" -> if (h == 12) h = 0
        }
        if (h !in 0..23 || (minute ?: 0) !in 0..59) return null
        return LocalTime.of(h, minute ?: 0)
    }

    /** A month-and-day is the next such date: this year if still ahead, else next year. */
    private fun monthDay(month: Int, day: Int, today: LocalDate): LocalDate? {
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (thisYear.isBefore(today)) runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() else thisYear
    }

    private fun MatchResult.gv(i: Int): String = groups[i]!!.value
    private fun TokenSpan.covers(i: Int) = i in start until end
    private fun TokenSpan.overlaps(r: IntRange) = r.first < end && start <= r.last
    private fun String.removeSpans(spans: List<TokenSpan>): String {
        val sb = StringBuilder()
        var i = 0
        for (s in spans.sortedBy { it.start }) {
            if (s.start > i) sb.append(this, i, s.start)
            sb.append(' ')
            i = maxOf(i, s.end)
        }
        if (i < length) sb.append(this, i, length)
        return sb.toString()
    }
}

/**
 * The Entry a parsed line becomes, once the person has pressed Enter. [fallbackDate] is the
 * surface's own day — Calendar's selected date — used when the line named none; a Task with no
 * date at all is a Someday task, which Tasks' surface passes as null.
 */
fun ParsedEntry.toEntry(fallbackDate: LocalDate?, now: java.time.Instant): com.tendril.app.data.entry.Entry {
    val start = date ?: fallbackDate
    return com.tendril.app.data.entry.Entry(
        title = title.ifBlank { "Untitled" },
        kind = kind,
        startDate = start,
        startTime = if (start != null) time else null,
        endDate = if (kind == EntryKind.EVENT && endTime != null) (endDate ?: start) else null,
        endTime = if (kind == EntryKind.EVENT) endTime else null,
        recurrenceRule = if (start != null) recurrence else null,
        status = if (kind == EntryKind.TASK) com.tendril.app.data.entry.EntryStatus.PENDING else null,
        dueDate = if (kind == EntryKind.TASK) deadline else null,
        estimate = if (kind == EntryKind.TASK) estimate else null,
        importance = if (kind == EntryKind.TASK) importance else 0,
        createdAt = now,
        updatedAt = now,
    )
}

/**
 * B§13.6 #7 — the one write quick add makes, shared by the Calendar's ViewModel and the desktop's
 * chord-opened popup (which has no ViewModel owner): insert what the preview showed, then let the
 * platform re-arm its alarms (§9.7). Null for a blank title, as the strip refuses one.
 */
suspend fun quickAddEntry(
    entryDao: com.tendril.app.data.entry.EntryDao,
    coordinator: EntryScheduleCoordinator,
    parsed: ParsedEntry,
    date: LocalDate,
): com.tendril.app.data.entry.Entry? {
    if (parsed.title.isBlank()) return null
    val id = entryDao.insert(parsed.toEntry(fallbackDate = date, now = java.time.Instant.now()))
    return entryDao.getById(id)?.also { coordinator.onEntryChanged(it) }
}
