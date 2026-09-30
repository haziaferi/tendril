package com.tendril.app.ui

import com.tendril.app.data.prefs.MapKeyValueStore
import com.tendril.app.ui.settings.APP_LANGUAGE_KEY
import com.tendril.app.ui.settings.AppLanguage
import com.tendril.app.ui.settings.LanguageSettings
import com.tendril.app.ui.settings.appLocale
import com.tendril.app.ui.settings.applyAppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Plan Phase 4 — the app's language (L1: English or Italian, English by default) and what it
 * reaches (L2: the words and the dates, with the device's region kept).
 */
class AppLanguageTest {

    private val before = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(before)

    @Test
    fun `English is the default, and an unknown value reads as English`() {
        assertEquals(AppLanguage.ENGLISH, LanguageSettings(MapKeyValueStore()).current())
        assertEquals(AppLanguage.ENGLISH, LanguageSettings(MapKeyValueStore(mapOf(APP_LANGUAGE_KEY to "fr"))).current())
    }

    @Test
    fun `the choice is stored and read back`() {
        val store = MapKeyValueStore()
        LanguageSettings(store).set(AppLanguage.ITALIAN)
        assertEquals("it", store.get(APP_LANGUAGE_KEY))
        assertEquals(AppLanguage.ITALIAN, LanguageSettings(store).current())
    }

    /** An English app on an Italian phone keeps the phone's region: only the words change. */
    @Test
    fun `the language replaces the device's, and the region stays`() {
        assertEquals(Locale.forLanguageTag("en-IT"), appLocale(AppLanguage.ENGLISH, Locale.forLanguageTag("it-IT")))
        assertEquals(Locale.forLanguageTag("it-GB"), appLocale(AppLanguage.ITALIAN, Locale.forLanguageTag("en-GB")))
        assertEquals(Locale.ITALIAN, appLocale(AppLanguage.ITALIAN, Locale.ENGLISH))
    }

    /** L2 — every date in the app is formatted with the default locale, so setting it is what reaches them. */
    @Test
    fun `applying a language changes the default the dates are formatted with`() {
        val friday = LocalDate.of(2026, 10, 2).dayOfWeek
        applyAppLanguage(AppLanguage.ITALIAN)
        assertEquals("it", Locale.getDefault().language)
        assertEquals("venerdì", friday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        applyAppLanguage(AppLanguage.ENGLISH)
        assertEquals("en", Locale.getDefault().language)
        assertEquals("Friday", friday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
    }
}
