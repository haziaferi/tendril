package com.tendril.desktopapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.ui.nav.DENSITY_PROFILE_KEY
import com.tendril.app.ui.nav.DensityProfile
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tendril.app.ui.nav.ShellTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.settings.AiSettingsSection

/**
 * §0.6.15 — the desktop's Settings: the shared Claude section, and a line about the rest. The
 * last `NotAvailableOnDesktop` stand-in went with this; theme, the sync folder, backups and
 * reminders are still Android's (`tendril-spec.md` §0.10 item 14 owns the desktop pass).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettingsScreen(core: WorkbenchCore, syncSection: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ShellTopBar(title = { Text("Settings") })
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Sync folder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            syncSection()
            HorizontalDivider()
            AiSettingsSection(core.aiKeyStore, core.keyValueStore)
            HorizontalDivider()
            DensitySection(core.keyValueStore)
            HorizontalDivider()
            Text(
                "Theme, backups and reminders are Android-only for now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * B§13.5 #4 — the desktop's one density choice. The three profiles multiply the same screen
 * factor (`ShellScale.kt`); Compact is the default, and the phone never shows this — it is Touch.
 */
@Composable
private fun DensitySection(store: KeyValueStore) {
    val stored by store.observe(DENSITY_PROFILE_KEY).collectAsState(initial = store.get(DENSITY_PROFILE_KEY))
    val current = DensityProfile.fromKey(stored)
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Density", style = MaterialTheme.typography.titleMedium)
        Text(
            "Every measurement scales with the window's shorter side; this sets how much room a row gets on top of that.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DensityProfile.entries.forEach { profile ->
                FilterChip(
                    selected = profile == current,
                    onClick = { store.put(DENSITY_PROFILE_KEY, profile.key) },
                    label = { Text(profile.label) },
                )
            }
        }
    }
}
