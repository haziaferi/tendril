package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

private const val PREFS_NAME = "tendril_google_calendar_prefs"
private const val KEY_CONNECTED = "connected"
private const val KEY_LAST_SYNCED_AT = "last_synced_at"

/**
 * Google Calendar sync connection state (§3.2, §9.5) — independent of, and does not gate,
 * Calendar Provider registration (§3.2's "stays independent" decision). Not encrypted like
 * [SecretStore]: an Android-type OAuth client has no client secret to protect, and this only
 * ever stores a boolean connection flag — the short-lived access token itself is never
 * persisted (kept in memory for one sync pass, re-requested silently before the next,
 * see [com.tendril.app.googlecalendar.GoogleCalendarAuthManager]).
 */
class GoogleCalendarPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isConnected = MutableStateFlow(prefs.getBoolean(KEY_CONNECTED, false))
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
        prefs.edit { putBoolean(KEY_CONNECTED, connected) }
    }

    /** §9.5's incremental-sync cursor — used both as `events.list`'s `updatedMin` on pull and
     * as the "has this Entry changed since last sync" filter on push. */
    private val _lastSyncedAt = MutableStateFlow(
        prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
    )
    val lastSyncedAt: StateFlow<Instant?> = _lastSyncedAt.asStateFlow()

    fun setLastSyncedAt(instant: Instant) {
        _lastSyncedAt.value = instant
        prefs.edit { putLong(KEY_LAST_SYNCED_AT, instant.toEpochMilli()) }
    }
}
