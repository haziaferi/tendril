package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "tendril_calendar_provider_prefs"
private const val KEY_CALENDAR_ID = "calendar_id"

/**
 * System Calendar Provider registration (§3.2, §9.9 item 3) — the local, per-device
 * `CalendarContract.Calendars._ID` Tendril created for itself. Deliberately per-device only,
 * like [com.tendril.app.data.entry.Entry.providerEventId] — never synced across devices,
 * since each device's Calendar Provider is its own independent database.
 */
class CalendarProviderPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _calendarId = MutableStateFlow(prefs.getLong(KEY_CALENDAR_ID, -1L).takeIf { it >= 0 })
    val calendarId: StateFlow<Long?> = _calendarId.asStateFlow()

    fun setCalendarId(id: Long) {
        _calendarId.value = id
        prefs.edit { putLong(KEY_CALENDAR_ID, id) }
    }
}
