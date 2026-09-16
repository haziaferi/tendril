package com.tendril.app.ui.theme

import androidx.compose.ui.text.font.FontWeight
import com.tendril.app.data.prefs.MapKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 14g·1 — the store's four keys, the one-time migration of the phone's old file, the weight clamp. */
class ThemeSettingsTest {

    @Test
    fun `defaults are Ink, System, Sans, no OLED, and a write reads back`() {
        val store = MapKeyValueStore()
        val settings = ThemeSettings(store)
        assertEquals(ThemeChoice(Register.INK, TendrilMode.SYSTEM, TendrilTypeface.SANS, false), settings.current())
        var pushed = 0
        settings.onColourChanged = { pushed++ }
        settings.setRegister(Register.CONSOLE)
        settings.setMode(TendrilMode.DARK)
        settings.setTypeface(TendrilTypeface.SERIF)
        settings.setOled(true)
        assertEquals(ThemeChoice(Register.CONSOLE, TendrilMode.DARK, TendrilTypeface.SERIF, true), settings.current())
        assertEquals("console", store.get(THEME_REGISTER_KEY))
        assertEquals("dark", store.get(THEME_MODE_KEY))
        // The typeface is not a colour: three pushes, not four (§9.6 — the widgets need colour only).
        assertEquals(3, pushed)
    }

    @Test
    fun `a garbage value is the default, not a crash`() {
        val store = MapKeyValueStore(mapOf(THEME_REGISTER_KEY to "cocoa", THEME_MODE_KEY to "auto", THEME_OLED_KEY to "yes"))
        assertEquals(ThemeChoice(Register.INK, TendrilMode.SYSTEM, TendrilTypeface.SANS, false), ThemeSettings(store).current())
    }

    @Test
    fun `the old file migrates once, an explicit mode kept and an implicit one becoming System`() {
        val explicit = legacyThemeKeys(storedRegister = null, oldColorTheme = "CLAY", oldMode = "DARK", oldModeIsExplicit = true, oldTypeface = "SERIF")
        assertEquals(mapOf(THEME_REGISTER_KEY to "clay", THEME_MODE_KEY to "dark", THEME_TYPEFACE_KEY to "serif"), explicit)

        val implicit = legacyThemeKeys(storedRegister = null, oldColorTheme = "MOSS", oldMode = null, oldModeIsExplicit = false, oldTypeface = null)
        assertEquals(mapOf(THEME_REGISTER_KEY to "moss", THEME_MODE_KEY to "system", THEME_TYPEFACE_KEY to "sans"), implicit)

        // A stored mode that was never explicit is the old tri-state's "follow the system", whatever the file says.
        val stale = legacyThemeKeys(storedRegister = null, oldColorTheme = "INK", oldMode = "LIGHT", oldModeIsExplicit = false, oldTypeface = "SANS")
        assertEquals("system", stale[THEME_MODE_KEY])

        // Already migrated, or nothing to migrate: nothing written.
        assertTrue(legacyThemeKeys("console", "CLAY", "DARK", true, "SERIF").isEmpty())
        assertTrue(legacyThemeKeys(null, null, null, false, null).isEmpty())
    }

    @Test
    fun `the type scale carries 400 and 500 only`() {
        assertEquals(FontWeight.Normal, eyePassWeight(FontWeight.Light))
        assertEquals(FontWeight.Normal, eyePassWeight(FontWeight.Thin))
        assertEquals(FontWeight.Normal, eyePassWeight(null))
        assertEquals(FontWeight.Medium, eyePassWeight(FontWeight.Medium))
        assertEquals(FontWeight.Medium, eyePassWeight(FontWeight.SemiBold))
        assertEquals(FontWeight.Medium, eyePassWeight(FontWeight.Bold))
        assertFalse(eyePassWeight(FontWeight.W100).weight < 400)
    }

    /** 14g·3 — the Tasks switches: on by default for urgency, the old file's explicit choices carried. */
    @Test
    fun `task settings default to urgency on and streaks off, and the old file migrates once`() {
        val store = MapKeyValueStore()
        val settings = com.tendril.app.ui.settings.TaskSettings(store)
        assertTrue(settings.showUrgency()); assertFalse(settings.showHabitStreaks())
        settings.setShowUrgency(false); assertFalse(settings.showUrgency())

        assertEquals(mapOf(com.tendril.app.ui.settings.SHOW_URGENCY_KEY to true, com.tendril.app.ui.settings.SHOW_HABIT_STREAKS_KEY to true), com.tendril.app.ui.settings.legacyTaskKeys(null, true, true))
        // An old *off* on the flag was its default, not a wish to hide the ladder: not carried.
        assertEquals(mapOf(com.tendril.app.ui.settings.SHOW_HABIT_STREAKS_KEY to false), com.tendril.app.ui.settings.legacyTaskKeys(null, false, false))
        assertTrue(com.tendril.app.ui.settings.legacyTaskKeys("false", true, true).isEmpty())
        assertTrue(com.tendril.app.ui.settings.legacyTaskKeys(null, null, null).isEmpty())
    }
}
