package com.tendril.app.calendarprovider

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.domain.calendar.CalendarItemFields
import com.tendril.app.domain.calendar.CalendarItemKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * §9.12 — [SystemCalendars] against the real system calendar, on the phone. Each test makes its own
 * LOCAL calendar under a throwaway account and deletes it after, so no server and none of the
 * person's calendars are touched. What DAVx5 then does with such writes was walked by hand on
 * 2026-09-29 (§9.12); this pins that Tendril's writes have the shape that walk used.
 */
class SystemCalendarsInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calendars = SystemCalendars(context)
    private val zone = ZoneId.systemDefault()
    private var calendarId = -1L

    private fun asOwner(uri: Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    @Before
    fun makeCalendar() {
        assumeTrue("needs calendar permission granted to the app", context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "probe")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Tendril instrumented test")
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 0)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        calendarId = context.contentResolver.insert(asOwner(CalendarContract.Calendars.CONTENT_URI), values)!!.lastPathSegment!!.toLong()
    }

    @After
    fun dropCalendar() {
        if (calendarId > 0) context.contentResolver.delete(asOwner(android.content.ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)), null, null)
    }

    @Test
    fun theColumnNamesTheValuesUseAreTheProvidersOwn() {
        val names = itemValues(CalendarItemFields("x", LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), null, null, null, false), "u", zone).keys +
            exceptionValues(CalendarItemFields("x", LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), null, null, null, false), LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), zone).keys
        val provider = setOf(
            CalendarContract.Events.UID_2445, CalendarContract.Events.TITLE, CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.DURATION,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME, CalendarContract.Events.STATUS,
        )
        assertEquals(emptySet<String>(), names - provider)
    }

    @Test
    fun theTestCalendarIsListedWithAStableKeyAndTendrilsOwnIsNot() {
        val listed = calendars.calendars()
        val mine = listed.single { it.id == calendarId }
        assertEquals("${CalendarContract.ACCOUNT_TYPE_LOCAL}|$ACCOUNT|probe", mine.key)
        assertTrue(mine.writable)
        assertFalse("Tendril's own mirror is never offered", listed.any { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL && it.accountName == "Tendril" })
        assertEquals(calendarId, calendars.idForKey(mine.key))
    }

    @Test
    fun aSingleEventWrittenIsReadBackAsTheSameItem() {
        val fields = CalendarItemFields("Dentist", LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), LocalDate.of(2026, 10, 1), LocalTime.of(11, 0), null, false)
        assertNotNull(calendars.insert(calendarId, itemValues(fields, "t-single", zone)))

        val item = rowsToItems(calendars.rows(calendarId))[CalendarItemKey("t-single", null)]
        assertEquals(fields, item?.fields)
    }

    @Test
    fun aSeriesWithAMovedAndASkippedOccurrenceIsReadBackItemByItem() {
        val series = CalendarItemFields("Yoga", LocalDate.of(2026, 10, 1), LocalTime.of(9, 0), LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), "FREQ=WEEKLY;COUNT=4", false)
        val seriesRow = calendars.insert(calendarId, itemValues(series, "t-series", zone))!!
        val moved = series.copy(startDate = LocalDate.of(2026, 10, 9), endDate = LocalDate.of(2026, 10, 9), rrule = null)
        assertNotNull("the provider accepts the exception", calendars.insertException(seriesRow, exceptionValues(moved, LocalDate.of(2026, 10, 8), LocalTime.of(9, 0), zone)))
        val skip = series.copy(startDate = LocalDate.of(2026, 10, 15), endDate = LocalDate.of(2026, 10, 15), rrule = null, isSkip = true)
        assertNotNull(calendars.insertException(seriesRow, exceptionValues(skip, LocalDate.of(2026, 10, 15), LocalTime.of(9, 0), zone)))

        val items = rowsToItems(calendars.rows(calendarId))
        assertEquals(series, items[CalendarItemKey("t-series", null)]?.fields)
        assertEquals(moved, items[CalendarItemKey("t-series", LocalDate.of(2026, 10, 8))]?.fields)
        assertEquals(true, items[CalendarItemKey("t-series", LocalDate.of(2026, 10, 15))]?.fields?.isSkip)
    }

    @Test
    fun anUpdateAndADeleteLand() {
        val fields = CalendarItemFields("Dentist", LocalDate.of(2026, 10, 1), LocalTime.of(10, 0), LocalDate.of(2026, 10, 1), LocalTime.of(11, 0), null, false)
        val row = calendars.insert(calendarId, itemValues(fields, "t-edit", zone))!!
        assertTrue(calendars.update(row, itemValues(fields.copy(title = "Dentist, moved"), "t-edit", zone)))
        assertEquals("Dentist, moved", rowsToItems(calendars.rows(calendarId)).getValue(CalendarItemKey("t-edit", null)).fields.title)

        assertTrue(calendars.delete(row))
        assertTrue(rowsToItems(calendars.rows(calendarId)).isEmpty())
    }

    private companion object {
        const val ACCOUNT = "tendril-instrumented-test"
    }
}
