package com.tendril.app.storage

import android.content.Context
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.ui.theme.legacyThemeKeys

private const val LEGACY_PREFS_NAME = "tendril_theme_prefs"

/**
 * 14g·1 — the phone's theme moved into [KeyValueStore] (`ThemeSettings`, shared). This copies
 * the old `ThemePreferences` file's choice across once — an explicit mode stays, a never-touched
 * one becomes System, `CLAY` becomes the `clay` register — and deletes the file. Idempotent:
 * with `theme_register` already stored, or no old file, it does nothing.
 */
fun migrateLegacyThemePreferences(context: Context, store: KeyValueStore) {
    val prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    val keys = legacyThemeKeys(
        storedRegister = store.get(com.tendril.app.ui.theme.THEME_REGISTER_KEY),
        oldColorTheme = prefs.getString("color_theme", null),
        oldMode = prefs.getString("mode", null),
        oldModeIsExplicit = prefs.getBoolean("mode_is_explicit", false),
        oldTypeface = prefs.getString("typeface", null),
    )
    keys.forEach { (k, v) -> store.put(k, v) }
    if (prefs.all.isNotEmpty()) context.deleteSharedPreferences(LEGACY_PREFS_NAME)
}
