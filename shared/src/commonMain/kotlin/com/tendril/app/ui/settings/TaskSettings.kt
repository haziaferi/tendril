package com.tendril.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.ui.theme.body

/** 14g·3 — the urgency ladder is drawn unless this is off (decided 2026-09-16: on by default). */
const val SHOW_URGENCY_KEY = "show_urgency"
/** §0.6.6 — the streak left the habit row; this puts the number back for whoever wants it. */
const val SHOW_HABIT_STREAKS_KEY = "show_habit_streaks"

/**
 * §0.5.1 / §0.5.2 — the two disclosures for Tasks & Habits, in [KeyValueStore] on both platforms
 * since 14g·3 (the phone's `tendril_task_prefs` file migrated once by [legacyTaskKeys]; the desktop
 * hardcoded both off before). Worded as what appears, not as what is "enabled".
 */
class TaskSettings(private val store: KeyValueStore) {
    fun showUrgency(): Boolean = store.getBoolean(SHOW_URGENCY_KEY, true)
    fun showHabitStreaks(): Boolean = store.getBoolean(SHOW_HABIT_STREAKS_KEY, false)
    fun setShowUrgency(on: Boolean) = store.putBoolean(SHOW_URGENCY_KEY, on)
    fun setShowHabitStreaks(on: Boolean) = store.putBoolean(SHOW_HABIT_STREAKS_KEY, on)

    @Composable
    fun observeShowUrgency(): Boolean {
        val v by store.observe(SHOW_URGENCY_KEY).collectAsState(initial = store.get(SHOW_URGENCY_KEY))
        return v?.toBooleanStrictOrNull() ?: true
    }

    @Composable
    fun observeShowHabitStreaks(): Boolean {
        val v by store.observe(SHOW_HABIT_STREAKS_KEY).collectAsState(initial = store.get(SHOW_HABIT_STREAKS_KEY))
        return v?.toBooleanStrictOrNull() ?: false
    }
}

/**
 * The one-time migration of the phone's `tendril_task_prefs` (`show_importance`, `show_habit_streaks`).
 * Pure: returns what to write. The old flag's *off* was its default and the ladder's default is
 * *on*, so only an explicit `true` is worth carrying (it becomes `show_urgency = true`, the
 * default anyway — carried so the intent is recorded); an old *off* is not written, since it
 * meant "never asked", not "hide the ladder". Nothing when the store already has a key.
 */
fun legacyTaskKeys(storedShowUrgency: String?, oldShowImportance: Boolean?, oldShowStreaks: Boolean?): Map<String, Boolean> {
    if (storedShowUrgency != null) return emptyMap()
    val out = mutableMapOf<String, Boolean>()
    if (oldShowImportance == true) out[SHOW_URGENCY_KEY] = true
    if (oldShowStreaks != null) out[SHOW_HABIT_STREAKS_KEY] = oldShowStreaks
    return out
}

/** The section both Settings homes render: two switches. */
@Composable
fun TaskSettingsSection(settings: TaskSettings, modifier: Modifier = Modifier) {
    val urgency = settings.observeShowUrgency()
    val streaks = settings.observeShowHabitStreaks()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SwitchRow("Show urgency on tasks", "The ladder's stripe on rows and calendar blocks — what you set, raised by a deadline nearing.", urgency) { settings.setShowUrgency(it) }
        Spacer(Modifier.height(6.dp))
        SwitchRow("Show habit streaks", null, streaks) { settings.setShowHabitStreaks(it) }
    }
}

@Composable
private fun SwitchRow(label: String, caption: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.body)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
