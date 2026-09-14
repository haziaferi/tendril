package com.tendril.desktopapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
            Text(
                "Theme, backups and reminders are Android-only for now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
