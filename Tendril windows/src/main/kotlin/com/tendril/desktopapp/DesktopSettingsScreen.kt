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
import javax.swing.JFileChooser
import java.io.FileOutputStream
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.tendril.app.ui.components.BarPillButton
import com.tendril.app.domain.plural
import com.tendril.app.markdown.MarkdownExporter
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.calendar.CalendarOpensOnSection
import com.tendril.app.ui.settings.AiSettingsSection
import com.tendril.app.ui.settings.NotificationAreaSection
import kotlinx.coroutines.flow.StateFlow
import com.tendril.app.ui.settings.TaskSettingsSection
import com.tendril.app.ui.settings.ThemeSection
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading

/**
 * §0.6.15 — the desktop's Settings: the shared Claude section, and a line about the rest. The
 * last `NotAvailableOnDesktop` stand-in went with this; backups are still Android's. 14g·1 — the
 * theme section is the shared one (`ThemeSection`), OLED-less. B§13.6 #7 — *Notification area*.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSettingsScreen(core: WorkbenchCore, syncSection: @Composable () -> Unit, onShowShortcuts: () -> Unit, hotkeyError: StateFlow<String?>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ShellTopBar(title = { Text("Settings") })
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Sync folder", style = MaterialTheme.typography.heading, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            syncSection()
            HorizontalDivider()
            AiSettingsSection(core.aiKeyStore, core.keyValueStore)
            HorizontalDivider()
            DensitySection(core.keyValueStore)
            HorizontalDivider()
            Text("Theme", style = MaterialTheme.typography.heading, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            ThemeSection(core.themeSettings, showOled = false)
            HorizontalDivider()
            // 14f·2 — the Calendar's opening view (`calendar_default_view`), shared with the phone's sheet.
            CalendarOpensOnSection(core.keyValueStore)
            HorizontalDivider()
            // 14g·3 — the Tasks switches, shared with the phone's Settings; the ladder is on by default.
            Text("Tasks & Habits", style = MaterialTheme.typography.heading, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            TaskSettingsSection(core.taskSettings)
            HorizontalDivider()
            // 14e — the overlay's second door, so the chord is not the only way to learn the chords.
            Text("Keyboard", style = MaterialTheme.typography.heading, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
            androidx.compose.material3.TextButton(onClick = onShowShortcuts, modifier = Modifier.padding(start = 8.dp)) { Text("Keyboard shortcuts… (F1)") }
            HorizontalDivider()
            // B§13.6 #7 — the × rule and the global quick-add chord; the section is shared code the phone never draws.
            NotificationAreaSection(core.keyValueStore, hotkeyError)
            HorizontalDivider()
            // §0.10 item 6 (2026-09-19) — the Markdown export on the desktop too: the exporter is
            // `jvmCommon`, only the file dialog is this platform's. The zip is an Obsidian vault
            // whose canvases are `.canvas` files.
            MarkdownExportSection(core)
            HorizontalDivider()
            Text(
                "Backups are Android-only for now.",
                style = MaterialTheme.typography.description,
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
        Text("Density", style = MaterialTheme.typography.heading)
        Text(
            "Every measurement scales with the window's shorter side; this sets how much room a row gets on top of that.",
            style = MaterialTheme.typography.description,
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

/** §0.10 item 6 — *Export as Markdown* for the desktop: one zip through a save dialog. */
@Composable
private fun MarkdownExportSection(core: WorkbenchCore) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val exporter = remember(core) {
        MarkdownExporter(core.database.pageDao(), core.database.blockDao(), core.localImages, core.database.pageCanvasDao(), core.database.canvasNodeDao(), core.database.canvasEdgeDao())
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Export as Markdown", style = MaterialTheme.typography.heading)
        Text(
            "A zip of .md files any editor can open — an Obsidian vault, with every canvas as a .canvas file. Databases export their pages, not their layout.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        BarPillButton(label = "Export Markdown", onClick = {
            scope.launch {
                status = runCatching {
                    val file = withContext(Dispatchers.IO) {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.FILES_ONLY
                            dialogTitle = "Export as Markdown"
                            selectedFile = File("tendril-markdown.zip")
                        }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                    } ?: return@launch
                    val result = withContext(Dispatchers.IO) { FileOutputStream(file).use { exporter.export(it) } }
                    "Exported ${plural(result.pages, "page")}, ${plural(result.canvases, "canvas", "canvases")} and ${plural(result.images, "picture")} to ${file.name}"
                }.getOrElse { it.message ?: "Export failed." }
            }
        })
        status?.let { Text(it, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    }
}
