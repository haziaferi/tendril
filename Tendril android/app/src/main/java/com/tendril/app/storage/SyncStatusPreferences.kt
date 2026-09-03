package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

private const val PREFS_NAME = "tendril_sync_status_prefs"
private const val KEY_LAST_SYNCED_AT = "last_synced_at"

/** §9.4 — backs the "last synced at ·" indicator in Settings. */
class SyncStatusPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastSyncedAt = MutableStateFlow(
        prefs.getLong(KEY_LAST_SYNCED_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
    )
    val lastSyncedAt: StateFlow<Instant?> = _lastSyncedAt.asStateFlow()

    fun markSyncedNow() {
        val now = Instant.now()
        _lastSyncedAt.value = now
        prefs.edit { putLong(KEY_LAST_SYNCED_AT, now.toEpochMilli()) }
    }
}
