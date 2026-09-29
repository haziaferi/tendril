package com.tendril.app.calendarprovider

import com.tendril.app.domain.calendar.CalendarItemFields
import com.tendril.app.domain.calendar.CalendarItemKey
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * One row of `CalendarContract.Events`, as §9.12's read-back needs it — the columns it maps and
 * the two that place an exception in its series. [SystemCalendars] fills it from a cursor; the
 * conversion below is pure so it can be tested without a device.
 */
data class SystemCalendarRow(
    val rowId: Long,
    /** `UID_2445` — Tendril's `Entry.uid` for an event it wrote; the iCalendar UID for one it read. */
    val uid: String?,
    val originalId: Long?,
    val originalInstanceTime: Long?,
    val title: String?,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val allDay: Boolean,
    /** `EVENT_TIMEZONE`, as the sync app stamped it. Kept for diagnosis only: [rowsToItems] reads
     * times in the phone's zone, because DAVx5 stamps an uploaded exception `UTC`. */
    val timeZone: String?,
    val rrule: String?,
    /** `STATUS = CANCELED` — on an exception, the skipped occurrence. */
    val cancelled: Boolean,
)

/** A row as a reconcile item, with the provider row it came from (for the write that answers it). */
data class ProviderItem(val rowId: Long, val fields: CalendarItemFields)

/**
 * The calendar's rows as reconcile items (§9.12). A series or single event is keyed by its UID; an
 * exception by its series' UID and the date of the occurrence it replaces. Timed rows read in [localZone], all-day rows in UTC.
 * A row with no UID anywhere has not been through its sync app yet and is left for a later pass.
 */
fun rowsToItems(rows: List<SystemCalendarRow>, localZone: ZoneId = ZoneId.systemDefault()): Map<CalendarItemKey, ProviderItem> {
    val byId = rows.associateBy { it.rowId }
    val items = LinkedHashMap<CalendarItemKey, ProviderItem>()
    for (row in rows) {
        val series = row.originalId?.let(byId::get)
        val uid = row.uid ?: series?.uid ?: continue
        // Times are instants and Tendril's are wall-clock times on this phone, so a timed row reads
        // in [localZone] whatever zone its sync app stamped on it — DAVx5 re-creates an uploaded
        // exception with `UTC` beside a Europe/Rome series (walked 2026-09-29). All-day rows are
        // UTC midnights, and read as dates in UTC.
        val zone = if (row.allDay) ZoneOffset.UTC else localZone
        val occurrence = row.originalInstanceTime?.let { Instant.ofEpochMilli(it).atZone(if (series?.allDay ?: row.allDay) ZoneOffset.UTC else localZone).toLocalDate() }
        val length = lengthOf(row) ?: series?.let(::lengthOf)
        items[CalendarItemKey(uid, occurrence)] = ProviderItem(row.rowId, fieldsOf(row, zone, length, isException = occurrence != null))
    }
    return items
}

/** How long one occurrence lasts: DTEND for a single event, DURATION for a series (the provider
 * refuses DTEND beside RRULE), nothing for an exception, which takes its series'. */
private fun lengthOf(row: SystemCalendarRow): Duration? =
    row.dtEnd?.let { Duration.ofMillis(it - row.dtStart) } ?: row.duration?.let(::parseRfcDuration)

/** RFC 5545 durations: `java.time.Duration` reads `PT1H`, `PT3600S` and `P1D`, but not weeks. */
private fun parseRfcDuration(text: String): Duration? {
    Regex("^P(\\d+)W$").find(text)?.let { return Duration.ofDays(7L * it.groupValues[1].toLong()) }
    return runCatching { Duration.parse(text) }.getOrNull()
}

private fun fieldsOf(row: SystemCalendarRow, zone: ZoneId, length: Duration?, isException: Boolean): CalendarItemFields {
    val start = Instant.ofEpochMilli(row.dtStart).atZone(zone)
    val end = length?.let { start.plus(it) }
    return if (row.allDay) {
        // All-day ends are exclusive midnights; Tendril stores the last day the event covers.
        val lastDay = end?.toLocalDate()?.minusDays(1)?.takeIf { !it.isBefore(start.toLocalDate()) } ?: start.toLocalDate()
        CalendarItemFields(row.title.orEmpty(), start.toLocalDate(), null, lastDay, null, row.rrule.takeUnless { isException }, row.cancelled)
    } else {
        CalendarItemFields(
            row.title.orEmpty(), start.toLocalDate(), start.toLocalTime(),
            end?.toLocalDate() ?: start.toLocalDate(), end?.toLocalTime(),
            row.rrule.takeUnless { isException }, row.cancelled,
        )
    }
}

/**
 * [fields] as the values of a series' or single event's row (§9.12's write path) — the same shape
 * the one-way mirror writes (`CalendarProviderSync`), plus `UID_2445`. Column names are
 * `CalendarContract.Events`'. A series carries DURATION and no DTEND: the provider refuses both.
 */
fun itemValues(fields: CalendarItemFields, uid: String, zone: ZoneId): Map<String, Any?> {
    val date = requireNotNull(fields.startDate) { "a calendar event needs a day" }
    val time = fields.startTime
    val values = LinkedHashMap<String, Any?>()
    values["uid2445"] = uid
    values["title"] = fields.title
    values["allDay"] = if (time == null) 1 else 0
    values["eventTimezone"] = if (time == null) "UTC" else zone.id
    values["rrule"] = fields.rrule
    if (time == null) {
        values["dtstart"] = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endExclusive = (fields.endDate ?: date).plusDays(1)
        if (fields.rrule != null) {
            values["duration"] = "P${ChronoUnit.DAYS.between(date, endExclusive).coerceAtLeast(1)}D"
            values["dtend"] = null
        } else {
            values["duration"] = null
            values["dtend"] = endExclusive.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    } else {
        val start = LocalDateTime.of(date, time).atZone(zone).toInstant()
        val end = LocalDateTime.of(fields.endDate ?: date, fields.endTime ?: time.plusHours(1)).atZone(zone).toInstant()
        values["dtstart"] = start.toEpochMilli()
        if (fields.rrule != null) {
            values["duration"] = "PT${Duration.between(start, end).seconds.coerceAtLeast(60)}S"
            values["dtend"] = null
        } else {
            values["duration"] = null
            values["dtend"] = end.toEpochMilli()
        }
    }
    return values
}

/**
 * [fields] as an exception row, inserted through `Events.CONTENT_EXCEPTION_URI` (§9.12).
 * `originalInstanceTime` names the occurrence by the instant it would have started — its day at
 * the series' own start time. **No DTEND**: walked 2026-09-29, the provider refuses an exception
 * that sets one ("Exceptions can't overwrite dtend"); the occurrence keeps its series' length. A
 * skip is the same row with `STATUS = CANCELED`.
 */
fun exceptionValues(fields: CalendarItemFields, occurrence: LocalDate, seriesStartTime: LocalTime?, zone: ZoneId): Map<String, Any?> {
    val values = LinkedHashMap<String, Any?>()
    values["originalInstanceTime"] = instantOf(occurrence, seriesStartTime, zone)
    values["title"] = fields.title
    values["allDay"] = if (fields.startTime == null) 1 else 0
    values["dtstart"] = instantOf(requireNotNull(fields.startDate), fields.startTime, zone)
    values["eventStatus"] = if (fields.isSkip) STATUS_CANCELED else STATUS_CONFIRMED
    return values
}

private fun instantOf(day: LocalDate, time: LocalTime?, zone: ZoneId): Long =
    (if (time == null) day.atStartOfDay(ZoneOffset.UTC).toInstant() else LocalDateTime.of(day, time).atZone(zone).toInstant()).toEpochMilli()

/** `CalendarContract.Events.STATUS_CONFIRMED` / `STATUS_CANCELED`, kept as numbers so this file
 * stays pure. */
private const val STATUS_CONFIRMED = 1
private const val STATUS_CANCELED = 2
