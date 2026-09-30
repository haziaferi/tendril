package com.tendril.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.settings_language
import com.tendril.app.generated.resources.settings_language_english
import com.tendril.app.generated.resources.settings_language_italian
import com.tendril.app.generated.resources.settings_language_note
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * Plan Phase 4 (L1, 2026-09-30) — the app's language, chosen in Settings: English or Italian,
 * English by default. No "follow the system" yet: the phone's system is Italian, and following it
 * would show a half-translated app until every screen has its Italian (L3 keeps that to what the
 * habit planner adds).
 */
enum class AppLanguage(val key: String) {
    ENGLISH("en"),
    ITALIAN("it"),
    ;

    companion object {
        /** An unknown or missing value is English, the default (L1). */
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: ENGLISH
    }
}

const val APP_LANGUAGE_KEY = "app_language"

class LanguageSettings(private val store: KeyValueStore) {
    fun current(): AppLanguage = AppLanguage.fromKey(store.get(APP_LANGUAGE_KEY))

    fun set(language: AppLanguage) = store.put(APP_LANGUAGE_KEY, language.key)

    /** The choice as Compose state, so the root can recompose into the other language. */
    @Composable
    fun observe(): AppLanguage {
        val key by store.observe(APP_LANGUAGE_KEY).collectAsState(initial = store.get(APP_LANGUAGE_KEY))
        return AppLanguage.fromKey(key)
    }
}

/**
 * The locale the app runs in: the device's, with its language replaced (L2). The region stays the
 * device's, so an English app on an Italian phone keeps 24-hour times and day-before-month dates,
 * and only the words change.
 */
fun appLocale(language: AppLanguage, device: Locale): Locale = Locale.Builder().setLocale(device).setLanguage(language.key).build()

/** The device's locale as the process found it, captured before the first override. */
private object DeviceLocale {
    val original: Locale = Locale.getDefault()
}

/**
 * Plan Phase 4 — makes [language] the process's default locale. That is the one lever both
 * platforms share: Compose Multiplatform's resources pick their language from `Locale.current`,
 * which reads the JVM's default on the desktop and on Android alike, and every date, weekday and
 * month name in the app is formatted with `Locale.getDefault()` (L2). Called at start-up, before
 * the first window, and by [WithAppLanguage] on each change; Android also calls it after a
 * configuration change, which resets the default to the device's.
 */
fun applyAppLanguage(language: AppLanguage) {
    Locale.setDefault(appLocale(language, DeviceLocale.original))
}

/**
 * The app's content in the chosen language. On a change the default locale is set first and the
 * content is keyed on the language, so everything below recomposes in the new one at once —
 * remembered state below is reset, as a language change on Android resets an activity.
 */
@Composable
fun WithAppLanguage(settings: LanguageSettings, content: @Composable () -> Unit) {
    val language = settings.observe()
    remember(language) { applyAppLanguage(language) }
    key(language) { content() }
}

/** The Settings row both platforms render: the two languages, each named in itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSection(settings: LanguageSettings, modifier: Modifier = Modifier) {
    val current = settings.observe()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(Res.string.settings_language), style = MaterialTheme.typography.heading, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) // type: SECTION_HEADING — the row's name, as the Settings sections' own headings
        val options = listOf(AppLanguage.ENGLISH to Res.string.settings_language_english, AppLanguage.ITALIAN to Res.string.settings_language_italian)
        // compact, as the Theme's Mode and Typeface rows are (the desktop walk: full width stood out)
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { i, (language, label) ->
                SegmentedButton(
                    selected = language == current,
                    onClick = { settings.set(language) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                ) { Text(stringResource(label)) }
            }
        }
        Text(stringResource(Res.string.settings_language_note), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) // type: EXPLAINER — what the choice reaches
    }
}
