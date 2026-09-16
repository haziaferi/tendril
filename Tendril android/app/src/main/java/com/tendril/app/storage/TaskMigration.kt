package com.tendril.app.storage

import android.content.Context
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.ui.settings.SHOW_URGENCY_KEY
import com.tendril.app.ui.settings.legacyTaskKeys

private const val LEGACY_PREFS_NAME = "tendril_task_prefs"

/**
 * 14g·3 — the Tasks switches moved into [KeyValueStore] (`TaskSettings`, shared). This copies
 * the old `TaskPreferences` file across once — an explicit *show importance* becomes
 * *show urgency*, the streak switch as it was — and deletes the file. Idempotent.
 */
fun migrateLegacyTaskPreferences(context: Context, store: KeyValueStore) {
    val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    val keys = legacyTaskKeys(
        storedShowUrgency = store.get(SHOW_URGENCY_KEY),
        oldShowImportance = if (prefs.contains("show_importance")) prefs.getBoolean("show_importance", false) else null,
        oldShowStreaks = if (prefs.contains("show_habit_streaks")) prefs.getBoolean("show_habit_streaks", false) else null,
    )
    keys.forEach { (k, v) -> store.putBoolean(k, v) }
    if (prefs.all.isNotEmpty()) context.deleteSharedPreferences(LEGACY_PREFS_NAME)
}
