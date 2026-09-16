package com.tendril.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
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
import com.tendril.app.ui.nav.CLOSE_TO_TRAY_KEY
import com.tendril.app.ui.nav.QUICK_ADD_CHORD_KEY
import com.tendril.app.ui.nav.QuickAddChord
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label
import kotlinx.coroutines.flow.StateFlow

/**
 * B§13.6 #7 — Settings › Notification area, drawn by the desktop pane only (the phone has no
 * notification area and never composes this): the × rule and the global quick-add chord
 * (`docs/mockups/tray.html` frame D; `docs/critiques/tray-mock.md` #4 for the overflow clause).
 * [hotkeyError] is the desktop's registration failure for the chosen chord, shown beside the
 * picker in the error colour so the person picks another rather than wondering why nothing opens.
 */
@Composable
fun NotificationAreaSection(store: KeyValueStore, hotkeyError: StateFlow<String?>, modifier: Modifier = Modifier) {
    val closeStored by store.observe(CLOSE_TO_TRAY_KEY).collectAsState(initial = store.get(CLOSE_TO_TRAY_KEY))
    val closeToTray = closeStored?.toBooleanStrictOrNull() ?: true
    val chordStored by store.observe(QUICK_ADD_CHORD_KEY).collectAsState(initial = store.get(QUICK_ADD_CHORD_KEY))
    val chord = QuickAddChord.fromKey(chordStored)
    val error by hotkeyError.collectAsState()
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text("Notification area", style = MaterialTheme.typography.heading)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep Tendril running in the notification area when the window closes", style = MaterialTheme.typography.body)
                Text(
                    "Reminders arrive as Windows notifications while it runs; the icon may sit behind the taskbar's ^ until you drag it out. Quit from the icon's menu.",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = closeToTray, onCheckedChange = { store.putBoolean(CLOSE_TO_TRAY_KEY, it) })
        }
        Text("Quick add from anywhere", style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAddChord.entries.forEach { option ->
                FilterChip(
                    selected = option == chord,
                    onClick = { store.put(QUICK_ADD_CHORD_KEY, option.key) },
                    label = { Text(option.label) },
                )
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
