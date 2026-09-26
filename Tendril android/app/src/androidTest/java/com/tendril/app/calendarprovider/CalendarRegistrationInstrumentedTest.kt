package com.tendril.app.calendarprovider

import android.content.Context
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.AppContainer
import com.tendril.app.storage.CalendarProviderPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Audit 5.11, found on the 2026-09-26 walk: the phone held eight calendars called Tendril, two of
 * them still holding events. `ensureCalendar` knew its calendar only by an id in the app's own
 * preferences, so every time those were lost — the app's data cleared, a reinstall after an
 * uninstall — it created another, and the old one stayed visible with everything it held. The
 * phone's calendar app showed each of those entries twice.
 */
class CalendarRegistrationInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun tendrilCalendars(): Int {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf("Tendril", CalendarContract.ACCOUNT_TYPE_LOCAL), null,
        ) ?: return -1
        return cursor.use { it.count }
    }

    @Test
    fun theSweepLeavesExactlyOneTendrilCalendar() = runBlocking {
        val sync = AppContainer.from(context).calendarProviderSync
        assumeTrue("needs calendar permission granted to the app", sync.hasPermission())

        sync.ensureCalendarAndBackfill()

        assertEquals(1, tendrilCalendars())
    }

    /** The loss itself: the preferences gone, as after clearing the app's data. */
    @Test
    fun forgettingTheCalendarDoesNotLeaveASecondOne() = runBlocking {
        val container = AppContainer.from(context)
        assumeTrue("needs calendar permission granted to the app", container.calendarProviderSync.hasPermission())
        container.calendarProviderSync.ensureCalendarAndBackfill()
        context.getSharedPreferences("tendril_calendar_provider_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        CalendarProviderSync(context, container.database.entryDao(), CalendarProviderPreferences(context)).ensureCalendarAndBackfill()

        assertEquals(1, tendrilCalendars())
    }
}
