package com.tendril.app.domain.ics

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * §0.8 step 6e / B§6 #7 — iCalendar (RFC 5545), by hand. The subset an offline personal
 * calendar needs: every undeleted Entry becomes a `VEVENT` (events) or a `VTODO` (tasks), with
 * `UID` = `Entry.uid` so a file re-imported updates rather than duplicates. Recurring
 * exception rows travel as their own component with `RECURRENCE-ID`, which is what the format
 * has for "this occurrence, moved"; skips travel as `EXDATE` on the base.
 *
 * Times are written in the device zone with `TZID`, and read back into it from `TZID`, from
 * `Z`, or floating — any of the three a Google or Apple export produces. All-day is
 * `VALUE=DATE`, and an all-day `DTEND` is exclusive as the standard says, so a one-day event
 * writes `DTEND` = the next day and reads it back as the same day.
 */
object IcsWriter {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun write(entries: List<Entry>, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
        val live = entries.filter { it.deletedAt == null }
        val skipsByBase = live.filter { it.isExceptionSkip == true && it.originalEntryId != null }.groupBy { it.originalEntryId!! }
        val out = StringBuilder()
        out.line("BEGIN:VCALENDAR")
        out.line("VERSION:2.0")
        out.line("PRODID:-//Tendril//Tendril//EN")
        out.line("CALSCALE:GREGORIAN")
        for (entry in live) {
            if (entry.isExceptionSkip == true) continue
            val component = if (entry.kind == EntryKind.EVENT) "VEVENT" else "VTODO"
            out.line("BEGIN:$component")
            out.line("UID:${entry.uid}")
            out.line("DTSTAMP:${STAMP.format(now.atOffset(ZoneOffset.UTC))}")
            out.line("SUMMARY:${escape(entry.title)}")
            entry.startDate?.let { start ->
                out.line(dateProperty("DTSTART", start, entry.startTime, zone))
                if (entry.kind == EntryKind.EVENT) {
                    val endDate = entry.endDate ?: start
                    val endTime = entry.endTime
                    if (entry.startTime != null && endTime != null) out.line(dateProperty("DTEND", endDate, endTime, zone))
                    else if (entry.startTime == null && (entry.endDate != null)) out.line(dateProperty("DTEND", endDate.plusDays(1), null, zone))
                }
            }
            if (entry.kind == EntryKind.TASK) {
                entry.dueDate?.let { out.line(dateProperty("DUE", it, null, zone)) }
                out.line("STATUS:" + if (entry.status == EntryStatus.DONE) "COMPLETED" else "NEEDS-ACTION")
                if (entry.status == EntryStatus.DONE) out.line("PERCENT-COMPLETE:100")
                if (entry.important) out.line("PRIORITY:1")
                entry.estimate?.let { out.line("DURATION:" + isoDuration(it)) }
            }
            when (val rule = entry.recurrenceRule) {
                is RecurrenceRule.Fixed -> out.line("RRULE:${rule.rrule}")
                is RecurrenceRule.Elastic -> out.line("RRULE:${periodToRrule(rule.period)}")
                null -> Unit
            }
            if (entry.originalEntryId != null && entry.originalOccurrenceDate != null) {
                val base = live.firstOrNull { it.id == entry.originalEntryId }
                out.line(dateProperty("RECURRENCE-ID", entry.originalOccurrenceDate, base?.startTime, zone))
                base?.let { out.line("RELATED-TO:${it.uid}") }
            }
            skipsByBase[entry.id]?.forEach { skip ->
                skip.originalOccurrenceDate?.let { out.line(dateProperty("EXDATE", it, entry.startTime, zone)) }
            }
            out.line("END:$component")
        }
        out.line("END:VCALENDAR")
        return out.toString()
    }

    private fun dateProperty(name: String, date: LocalDate, time: LocalTime?, zone: ZoneId): String =
        if (time == null) "$name;VALUE=DATE:${DATE.format(date)}"
        else "$name;TZID=${zone.id}:${DATE_TIME.format(LocalDateTime.of(date, time))}"

    private fun periodToRrule(p: Period): String = when {
        p.months > 0 -> "FREQ=MONTHLY" + (if (p.months > 1) ";INTERVAL=${p.months}" else "")
        p.days > 0 && p.days % 7 == 0 -> "FREQ=WEEKLY" + (if (p.days / 7 > 1) ";INTERVAL=${p.days / 7}" else "")
        else -> "FREQ=DAILY" + (if (p.days > 1) ";INTERVAL=${p.days}" else "")
    }

    private fun isoDuration(d: java.time.Duration): String {
        val h = d.toHours(); val m = d.toMinutesPart()
        return "PT" + (if (h > 0) "${h}H" else "") + (if (m > 0 || h == 0L) "${m}M" else "")
    }

    /** RFC 5545 §3.3.11 text escaping, then §3.1 line folding at 75 octets. */
    private fun escape(text: String): String = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    private fun StringBuilder.line(content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) { append(content).append("\r\n"); return }
        // Fold on character boundaries, never inside a multi-byte sequence.
        var current = StringBuilder()
        var size = 0
        for (ch in content) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (size + n > (if (current.isEmpty() && length == 0) 75 else 74)) {
                append(current).append("\r\n ")
                current = StringBuilder(); size = 0
            }
            current.append(ch); size += n
        }
        append(current).append("\r\n")
    }
}

/** One parsed component, as far as this reader goes; [kind] null means a component it skips. */
data class IcsComponent(
    val uid: String,
    val kind: EntryKind,
    val summary: String,
    val startDate: LocalDate?,
    val startTime: LocalTime?,
    val endDate: LocalDate?,
    val endTime: LocalTime?,
    val rrule: String?,
    val due: LocalDate?,
    val completed: Boolean,
    val recurrenceId: LocalDate?,
    val exdates: List<LocalDate>,
    /** Tendril's own export names an override's base here; other producers share the UID. */
    val relatedTo: String? = null,
)

object IcsReader {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** Every `VEVENT` and `VTODO` in [text]; anything else (VTIMEZONE, VALARM, VJOURNAL) is skipped. */
    fun read(text: String, zone: ZoneId = ZoneId.systemDefault()): List<IcsComponent> {
        val lines = unfold(text)
        val out = mutableListOf<IcsComponent>()
        var props: MutableMap<String, Pair<Map<String, String>, String>>? = null
        var kind: EntryKind? = null
        var exdates = mutableListOf<LocalDate>()
        var depth = 0
        for (raw in lines) {
            val (name, params, value) = split(raw) ?: continue
            when {
                name == "BEGIN" && (value == "VEVENT" || value == "VTODO") && props == null -> {
                    props = linkedMapOf(); kind = if (value == "VEVENT") EntryKind.EVENT else EntryKind.TASK; exdates = mutableListOf(); depth = 0
                }
                name == "BEGIN" && props != null -> depth++
                name == "END" && props != null && depth > 0 -> depth--
                name == "END" && props != null && (value == "VEVENT" || value == "VTODO") -> {
                    val p = props!!
                    val uid = p["UID"]?.second
                    if (uid != null) {
                        val (sd, st) = p["DTSTART"]?.let { (pp, v) -> parseDate(pp, v, zone) } ?: (null to null)
                        var (ed, et) = p["DTEND"]?.let { (pp, v) -> parseDate(pp, v, zone) } ?: (null to null)
                        // An all-day DTEND is exclusive; store the last covered day.
                        if (ed != null && et == null && st == null) ed = ed.minusDays(1)
                        if (ed == sd && et == null) ed = null
                        val due = p["DUE"]?.let { (pp, v) -> parseDate(pp, v, zone).first }
                        val status = p["STATUS"]?.second
                        out += IcsComponent(
                            uid = uid, kind = kind!!, summary = unescape(p["SUMMARY"]?.second.orEmpty()),
                            startDate = sd, startTime = st, endDate = ed, endTime = et,
                            rrule = p["RRULE"]?.second, due = due,
                            completed = status == "COMPLETED" || (p["PERCENT-COMPLETE"]?.second == "100"),
                            recurrenceId = p["RECURRENCE-ID"]?.let { (pp, v) -> parseDate(pp, v, zone).first },
                            exdates = exdates.toList(),
                            relatedTo = p["RELATED-TO"]?.second,
                        )
                    }
                    props = null; kind = null
                }
                props != null && depth == 0 -> {
                    if (name == "EXDATE") value.split(",").forEach { v -> parseDate(params, v.trim(), zone).first?.let { exdates += it } }
                    else props!![name] = params to value
                }
            }
        }
        return out
    }

    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (line in text.split("\r\n", "\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) out[out.lastIndex] = out.last() + line.substring(1)
            else if (line.isNotEmpty()) out += line
        }
        return out
    }

    private fun split(line: String): Triple<String, Map<String, String>, String>? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = head.split(";")
        val params = parts.drop(1).mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].trim('"') } }.toMap()
        return Triple(parts[0].uppercase(), params, value)
    }

    /** `VALUE=DATE` → a date; `…T…Z` → UTC converted to [zone]; `TZID=` → that zone converted to
     * [zone]; bare → floating, taken as [zone]. */
    private fun parseDate(params: Map<String, String>, value: String, zone: ZoneId): Pair<LocalDate?, LocalTime?> {
        val v = value.trim()
        return runCatching {
            if (params["VALUE"] == "DATE" || (v.length == 8 && !v.contains('T'))) {
                LocalDate.parse(v, DATE) to null
            } else {
                val utc = v.endsWith("Z")
                val local = LocalDateTime.parse(v.removeSuffix("Z"), DATE_TIME)
                val sourceZone = when {
                    utc -> ZoneOffset.UTC
                    params["TZID"] != null -> runCatching { ZoneId.of(params.getValue("TZID")) }.getOrDefault(zone)
                    else -> zone
                }
                val here = local.atZone(sourceZone).withZoneSameInstant(zone).toLocalDateTime()
                here.toLocalDate() to here.toLocalTime()
            }
        }.getOrDefault(null to null)
    }

    private fun unescape(text: String): String = text.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
