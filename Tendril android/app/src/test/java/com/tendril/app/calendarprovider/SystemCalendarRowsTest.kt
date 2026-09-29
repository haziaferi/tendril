package com.tendril.app.calendarprovider

import com.tendril.app.domain.calendar.CalendarItemFields
import com.tendril.app.domain.calendar.CalendarItemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * §9.12 — reading the system calendar's rows as reconcile items, and writing items back as row
 * values. The shapes are the ones walked on 2026-09-29 against DAVx5: a timed single event, a
 * weekly series with DURATION and no DTEND, and an exception naming its occurrence by
 * `originalInstanceTime` with no DTEND of its own.
 */
class SystemCalendarRowsTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(rome).toInstant().toEpochMilli()

    private val single = SystemCalendarRow(
        rowId = 750, uid = "u-1", originalId = null, originalInstanceTime = null, title = "Dentist",
        dtStart = at(2026, 9, 30, 10), dtEnd = at(2026, 9, 30, 11), duration = null, allDay = false,
        timeZone = "Europe/Rome", rrule = null, cancelled = false,
    )
    private val series = SystemCalendarRow(
        rowId = 751, uid = "u-2", originalId = null, originalInstanceTime = null, title = "Yoga",
        dtStart = at(2026, 10, 1, 9), dtEnd = null, duration = "PT1H", allDay = false,
        timeZone = "Europe/Rome", rrule = "FREQ=WEEKLY;COUNT=4", cancelled = false,
    )
    private val moved = SystemCalendarRow(
        rowId = 753, uid = "u-2", originalId = 751, originalInstanceTime = at(2026, 10, 8, 9), title = "Yoga",
        dtStart = at(2026, 10, 9, 9), dtEnd = null, duration = null, allDay = false,
        timeZone = "Europe/Rome", rrule = null, cancelled = false,
    )

    @Test
    fun `a timed single event reads with its day, times and no rule`() {
        val item = rowsToItems(listOf(single), rome)[CalendarItemKey("u-1", null)]!!
        assertEquals(750L, item.rowId)
        assertEquals(
            CalendarItemFields("Dentist", LocalDate.of(2026, 9, 30), LocalTime.of(10, 0), LocalDate.of(2026, 9, 30), LocalTime.of(11, 0), null, isSkip = false),
            item.fields,
        )
    }

    @Test
    fun `a series reads its end from DURATION, and its exception is its own item named by the occurrence it replaces`() {
        val items = rowsToItems(listOf(series, moved), rome)
        assertEquals(LocalTime.of(10, 0), items.getValue(CalendarItemKey("u-2", null)).fields.endTime)
        assertEquals("FREQ=WEEKLY;COUNT=4", items.getValue(CalendarItemKey("u-2", null)).fields.rrule)
        val exception = items.getValue(CalendarItemKey("u-2", LocalDate.of(2026, 10, 8)))
        assertEquals(LocalDate.of(2026, 10, 9), exception.fields.startDate)
        assertEquals("the exception inherits the series' hour", LocalTime.of(10, 0), exception.fields.endTime)
        assertFalse(exception.fields.isSkip)
    }

    /**
     * Walked 2026-09-29: after uploading a moved occurrence, DAVx5 re-created its row with
     * `eventTimezone = UTC` beside a series in Europe/Rome. Read in the row's own zone, the move
     * to 11:15 came back as 09:15 and was taken into Tendril as an edit made elsewhere. Times are
     * instants; Tendril's are wall-clock times on this phone, so a timed row reads in its zone.
     */
    @Test
    fun `a timed row reads in the phone's zone whatever zone its sync app stamped on it`() {
        val rewritten = moved.copy(dtStart = at(2026, 10, 8, 11, 15), dtEnd = at(2026, 10, 8, 12, 15), timeZone = "UTC")
        val exception = rowsToItems(listOf(series, rewritten), rome).getValue(CalendarItemKey("u-2", LocalDate.of(2026, 10, 8))).fields
        assertEquals(LocalTime.of(11, 15), exception.startTime)
        assertEquals(LocalTime.of(12, 15), exception.endTime)
        val elsewhere = single.copy(timeZone = "America/New_York")
        assertEquals(LocalTime.of(10, 0), rowsToItems(listOf(elsewhere), rome).values.single().fields.startTime)
    }

    @Test
    fun `an exception with no uid of its own takes its series' uid`() {
        val items = rowsToItems(listOf(series, moved.copy(uid = null)), rome)
        assertTrue(CalendarItemKey("u-2", LocalDate.of(2026, 10, 8)) in items)
    }

    @Test
    fun `a cancelled exception is a skip`() {
        val items = rowsToItems(listOf(series, moved.copy(cancelled = true)), rome)
        assertTrue(items.getValue(CalendarItemKey("u-2", LocalDate.of(2026, 10, 8))).fields.isSkip)
    }

    /** A row its sync app has not uploaded yet has no UID; it is read on a later pass. */
    @Test
    fun `a row with no uid anywhere is not read yet`() {
        assertTrue(rowsToItems(listOf(single.copy(uid = null)), rome).isEmpty())
    }

    @Test
    fun `an all-day event is UTC midnight with an exclusive end`() {
        val row = single.copy(
            allDay = true, timeZone = "UTC",
            dtStart = LocalDate.of(2026, 9, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            dtEnd = LocalDate.of(2026, 10, 2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val fields = rowsToItems(listOf(row), rome).values.single().fields
        assertEquals(LocalDate.of(2026, 9, 30), fields.startDate)
        assertEquals("the last day it covers, not the day after", LocalDate.of(2026, 10, 1), fields.endDate)
        assertEquals(null, fields.startTime)
    }

    @Test
    fun `DURATION in weeks and days is understood`() {
        val weekly = series.copy(duration = "P1W", allDay = true, timeZone = "UTC", dtStart = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        assertEquals(LocalDate.of(2026, 10, 7), rowsToItems(listOf(weekly), rome).values.single().fields.endDate)
    }

    // ---- writing

    @Test
    fun `a single event written and read back is the same item`() {
        val fields = rowsToItems(listOf(single), rome).values.single().fields
        val values = itemValues(fields, uid = "u-1", zone = rome)
        assertEquals("u-1", values["uid2445"])
        assertEquals(single.dtStart, values["dtstart"])
        assertEquals(single.dtEnd, values["dtend"])
        assertEquals(null, values["duration"])
        assertEquals(fields, rowsToItems(listOf(single.copy(dtStart = values["dtstart"] as Long, dtEnd = values["dtend"] as Long)), rome).values.single().fields)
    }

    @Test
    fun `a series is written with DURATION and no DTEND`() {
        val fields = rowsToItems(listOf(series), rome).values.single().fields
        val values = itemValues(fields, uid = "u-2", zone = rome)
        assertEquals("FREQ=WEEKLY;COUNT=4", values["rrule"])
        assertEquals("PT3600S", values["duration"])
        assertTrue("dtend" !in values || values["dtend"] == null)
    }

    /** Walked 2026-09-29: the provider refuses "Exceptions can't overwrite dtend". */
    @Test
    fun `an exception is written with its original instance and never a DTEND`() {
        val exception = rowsToItems(listOf(series, moved), rome).getValue(CalendarItemKey("u-2", LocalDate.of(2026, 10, 8))).fields
        val values = exceptionValues(exception, occurrence = LocalDate.of(2026, 10, 8), seriesStartTime = LocalTime.of(9, 0), zone = rome)
        assertEquals(at(2026, 10, 8, 9), values["originalInstanceTime"])
        assertEquals(at(2026, 10, 9, 9), values["dtstart"])
        assertFalse("dtend" in values)
        assertEquals(1, values["eventStatus"])
    }

    @Test
    fun `a skip is written as a cancelled exception`() {
        val skip = CalendarItemFields("Yoga", LocalDate.of(2026, 10, 8), LocalTime.of(9, 0), null, null, null, isSkip = true)
        assertEquals(2, exceptionValues(skip, LocalDate.of(2026, 10, 8), LocalTime.of(9, 0), rome)["eventStatus"])
    }
}
