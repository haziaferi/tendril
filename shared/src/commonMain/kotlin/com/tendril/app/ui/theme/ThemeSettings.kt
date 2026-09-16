package com.tendril.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tendril.app.data.prefs.KeyValueStore

const val THEME_REGISTER_KEY = "theme_register"
const val THEME_MODE_KEY = "theme_mode"
const val THEME_TYPEFACE_KEY = "theme_typeface"
/** Phone-only (B§13.7.3 rule 3); the desktop never reads it. */
const val THEME_OLED_KEY = "theme_oled"

/** What the theme is right now — one read of the four keys. */
data class ThemeChoice(
    val register: Register,
    val mode: TendrilMode,
    val typeface: TendrilTypeface,
    val oled: Boolean,
)

/**
 * 14g·1 (decided 2026-09-16) — the theme lives in [KeyValueStore] on both platforms, so the
 * desktop has a theme at all and the phone's `ThemePreferences` (its own `SharedPreferences`
 * file, migrated once by [legacyThemeKeys]) is gone. Four keys, every value a lower-case name;
 * a missing or unknown value is the default (Ink · System · Sans · no OLED), never an error.
 *
 * [onColourChanged] is the phone's widget-refresh hook (§9.6 — Glance colours resolve at
 * placement and must be pushed): set by `AppContainer`, a no-op on the desktop.
 */
class ThemeSettings(private val store: KeyValueStore) {
    var onColourChanged: () -> Unit = {}

    fun current(): ThemeChoice = ThemeChoice(
        register = Register.fromKey(store.get(THEME_REGISTER_KEY)),
        mode = TendrilMode.fromKey(store.get(THEME_MODE_KEY)),
        typeface = TendrilTypeface.fromKey(store.get(THEME_TYPEFACE_KEY)),
        oled = store.getBoolean(THEME_OLED_KEY, false),
    )

    fun setRegister(register: Register) { store.put(THEME_REGISTER_KEY, register.key); onColourChanged() }
    fun setMode(mode: TendrilMode) { store.put(THEME_MODE_KEY, mode.key); onColourChanged() }
    fun setTypeface(typeface: TendrilTypeface) = store.put(THEME_TYPEFACE_KEY, typeface.key)
    fun setOled(on: Boolean) { store.putBoolean(THEME_OLED_KEY, on); onColourChanged() }

    /** The choice as Compose state — recomposes the root on every change to any of the four. */
    @Composable
    fun observe(): ThemeChoice {
        val register by store.observe(THEME_REGISTER_KEY).collectAsState(initial = store.get(THEME_REGISTER_KEY))
        val mode by store.observe(THEME_MODE_KEY).collectAsState(initial = store.get(THEME_MODE_KEY))
        val typeface by store.observe(THEME_TYPEFACE_KEY).collectAsState(initial = store.get(THEME_TYPEFACE_KEY))
        val oled by store.observe(THEME_OLED_KEY).collectAsState(initial = store.get(THEME_OLED_KEY))
        return ThemeChoice(
            Register.fromKey(register), TendrilMode.fromKey(mode), TendrilTypeface.fromKey(typeface),
            oled?.toBooleanStrictOrNull() ?: false,
        )
    }
}

/**
 * The one-time migration of the phone's `tendril_theme_prefs` (`color_theme` / `mode` /
 * `mode_is_explicit` / `typeface`, enum names in upper case) into the store's keys. Pure so a
 * test can run it on a map: returns what to write, or nothing when the store already has a
 * register (the migration ran) or the old file had no choice in it. An old mode counts only
 * when it was explicit — the old tri-state's "never touched" becomes System.
 */
fun legacyThemeKeys(
    storedRegister: String?,
    oldColorTheme: String?,
    oldMode: String?,
    oldModeIsExplicit: Boolean,
    oldTypeface: String?,
): Map<String, String> {
    if (storedRegister != null) return emptyMap()
    if (oldColorTheme == null && oldMode == null && oldTypeface == null) return emptyMap()
    val out = mutableMapOf<String, String>()
    out[THEME_REGISTER_KEY] = Register.fromKey(oldColorTheme).key
    out[THEME_MODE_KEY] = (if (oldModeIsExplicit) TendrilMode.fromKey(oldMode) else TendrilMode.SYSTEM).key
    out[THEME_TYPEFACE_KEY] = TendrilTypeface.fromKey(oldTypeface).key
    return out
}
