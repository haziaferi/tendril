@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tendril.app.R
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.notionimport.NotionImporter
import com.tendril.app.storage.AppLockPreferences
import com.tendril.app.storage.SecretStore
import com.tendril.app.storage.SyncFolderManager
import com.tendril.app.storage.SyncStatusPreferences
import com.tendril.app.storage.ThemePreferences
import com.tendril.app.sync.PortableArchive
import com.tendril.app.sync.AndroidSafSyncFileStore
import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.ui.theme.TendrilColorTheme
import com.tendril.app.ui.theme.TendrilMode
import com.tendril.app.ui.theme.TendrilTypeface
import com.tendril.app.ui.theme.defaultTendrilMode
import com.tendril.app.ui.theme.paletteFor

@Composable
fun SettingsScreen(
    themePreferences: ThemePreferences,
    syncFolderManager: SyncFolderManager,
    secretStore: SecretStore,
    appLockPreferences: AppLockPreferences,
    syncStatusPreferences: SyncStatusPreferences,
    snapshotSyncOrchestrator: SnapshotSyncOrchestrator,
    portableArchive: PortableArchive,
    notionImporter: NotionImporter,
    databaseSyncManager: DatabaseSyncManager,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding).verticalScroll(rememberScrollState())) {
            AppearanceSection(themePreferences)
            HorizontalDivider()
            SyncFolderSection(syncFolderManager, syncStatusPreferences, snapshotSyncOrchestrator, secretStore)
            HorizontalDivider()
            AtRestEncryptionSection(secretStore)
            HorizontalDivider()
            PortableBackupSection(portableArchive)
            HorizontalDivider()
            NotionImportSection(notionImporter, databaseSyncManager)
            HorizontalDivider()
            AnthropicKeySection(secretStore)
            HorizontalDivider()
            AppLockSection(appLockPreferences)
            HorizontalDivider()
        }
    }
}

/**
 * Settings → Appearance (§2.3): a collapsed-by-default disclosure row — icon, current summary
 * ("Ink · Light · Sans"), chevron — so scrolling Settings can't accidentally fire a theme change.
 */
@Composable
private fun AppearanceSection(prefs: ThemePreferences) {
    var expanded by remember { mutableStateOf(false) }

    val colorTheme by prefs.colorTheme.collectAsState()
    val modeIsExplicit by prefs.modeIsExplicit.collectAsState()
    val explicitMode by prefs.explicitMode.collectAsState()
    val typeface by prefs.typeface.collectAsState()
    val systemDefaultMode = defaultTendrilMode()
    val mode = if (modeIsExplicit) explicitMode ?: systemDefaultMode else systemDefaultMode

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Palette, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(
                            R.string.settings_appearance_summary_template,
                            colorTheme.label,
                            mode.label,
                            typeface.label,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.cd_collapse_appearance else R.string.cd_expand_appearance
                    ),
                )
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Color theme",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TendrilColorTheme.entries.forEach { theme ->
                        ThemeSwatchChip(
                            theme = theme,
                            selected = theme == colorTheme,
                            onClick = { prefs.setColorTheme(theme) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Mode",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow {
                    TendrilMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { prefs.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(index, TendrilMode.entries.size),
                        ) { Text(m.label) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Typeface",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow {
                    TendrilTypeface.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = typeface == t,
                            onClick = { prefs.setTypeface(t) },
                            shape = SegmentedButtonDefaults.itemShape(index, TendrilTypeface.entries.size),
                        ) { Text(t.label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchChip(theme: TendrilColorTheme, selected: Boolean, onClick: () -> Unit) {
    val swatch = paletteFor(theme, TendrilMode.LIGHT).accent
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(theme.label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
                tint = swatch,
                modifier = Modifier.size(16.dp).clip(CircleShape),
            )
        },
    )
}

/**
 * Settings → Sync folder permission (§3.5, §9.3) — SAF grant, no Shizuku/root. Also carries
 * the "last synced at ·" indicator and manual "Sync now" action §9.4 specifies, since both
 * are meaningless without a folder chosen first.
 */
@Composable
private fun SyncFolderSection(
    manager: SyncFolderManager,
    syncStatus: SyncStatusPreferences,
    orchestrator: SnapshotSyncOrchestrator,
    secretStore: SecretStore,
) {
    val context = LocalContext.current
    val folderUri by manager.folderUri.collectAsState()
    val lastSyncedAt by syncStatus.lastSyncedAt.collectAsState()
    val passphrase by secretStore.syncPassphrase.collectAsState()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri -> manager.onFolderGranted(uri) },
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_sync_folder), style = MaterialTheme.typography.bodyLarge)
                val summary = folderUri?.let {
                    stringResource(R.string.settings_sync_folder_summary_set, manager.displayNameFor(it))
                } ?: stringResource(R.string.settings_sync_folder_summary_unset)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { launcher.launch(null) }) {
                Text(
                    stringResource(
                        if (folderUri == null) R.string.settings_sync_folder_choose
                        else R.string.settings_sync_folder_change
                    )
                )
            }
        }

        if (folderUri != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val lastSyncedText = lastSyncedAt?.let {
                    "Last synced ${java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(java.time.ZoneId.systemDefault()).format(it)}"
                } ?: "Never synced"
                Text(lastSyncedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = !syncing,
                    onClick = {
                        val uri = folderUri ?: return@Button
                        syncing = true
                        scope.launch {
                            // `syncing` is cleared in a finally: a throw here used to leave the
                            // button disabled forever *and* take the app down with it. The
                            // store's writes now surface failures rather than silently doing
                            // nothing, so this is a path that genuinely reports.
                            try {
                                val store = AndroidSafSyncFileStore.create(context, uri)
                                if (store == null) {
                                    syncError = "Couldn't open the sync folder — try choosing it again."
                                } else {
                                    syncError = null
                                    orchestrator.readAndMerge(store, passphrase)
                                    orchestrator.writeSnapshots(store, passphrase)
                                    syncStatus.markSyncedNow()
                                }
                            } catch (e: Exception) {
                                syncError = e.message ?: "Sync failed."
                            } finally {
                                syncing = false
                            }
                        }
                    },
                ) { Text(if (syncing) "Syncing…" else "Sync now") }
            }
            syncError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Settings → at-rest snapshot encryption (§9.4.2) — optional, off by default. "Off" is
 * simply "no passphrase set"; there's no separate toggle to fall out of sync with that.
 */
@Composable
private fun AtRestEncryptionSection(secretStore: SecretStore) {
    val stored by secretStore.syncPassphrase.collectAsState()
    var draft by remember(stored) { mutableStateOf(stored ?: "") }
    var revealed by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Sync folder encryption", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (stored != null) "On — snapshots are encrypted at rest" else "Off — snapshots are plain JSON",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Passphrase") },
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide passphrase" else "Show passphrase",
                        )
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { showConfirm = true }, enabled = draft != (stored ?: "")) { Text("Save") }
        }
    }

    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Change sync encryption") },
            text = {
                Text(
                    "The same passphrase must be entered on every device sharing this sync " +
                        "folder, or their snapshots become unreadable to each other. Losing " +
                        "this passphrase makes the synced folder unreadable on any new device."
                )
            },
            confirmButton = {
                DialogTextButton(onClick = {
                    secretStore.setSyncPassphrase(draft.takeIf { it.isNotEmpty() })
                    showConfirm = false
                })
            },
            dismissButton = { DialogTextButton(onClick = { showConfirm = false }, label = "Cancel") },
        )
    }
}

@Composable
private fun DialogTextButton(onClick: () -> Unit, label: String = "Confirm") {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}

/** Settings → full data import/export (§3.5, §9.4.1) — a portable `.tendril` package,
 * distinct from the continuous background sync feed above. */
@Composable
private fun PortableBackupSection(archive: PortableArchive) {
    val scope = rememberCoroutineScope()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching { archive.export(uri) }
                    .fold({ "Exported" }, { it.message ?: "Export failed." })
            }
        },
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching { archive.importAdditive(uri) }.fold(
                    { "Imported ${it.entryFilesFound} entry file(s), ${it.habitFilesFound} habit file(s)" },
                    { it.message ?: "Import failed." },
                )
            }
        },
    )
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            pendingRestoreUri = uri
            showRestoreConfirm = true
        },
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("Full data import/export", style = MaterialTheme.typography.bodyLarge)
        Text(
            "A portable .tendril package — a one-off file, separate from the continuous sync above",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportLauncher.launch("tendril-export.tendril") }) { Text("Export") }
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import") }
            Button(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) { Text("Restore backup") }
        }
        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showRestoreConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore backup") },
            text = { Text("This erases all current tasks and habits on this device and replaces them with the backup's contents. This cannot be undone.") },
            confirmButton = {
                DialogTextButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        showRestoreConfirm = false
                        if (uri != null) {
                            scope.launch {
                                // An unreadable archive now throws before anything is deleted,
                                // so this message is the user's signal that their data is
                                // still intact rather than half-gone.
                                statusMessage = runCatching { archive.restoreFromBackup(uri) }
                                    .fold({ "Restored from backup" }, { it.message ?: "Restore failed." })
                            }
                        }
                    },
                    label = "Erase and restore",
                )
            },
            dismissButton = { DialogTextButton(onClick = { showRestoreConfirm = false }, label = "Cancel") },
        )
    }
}

/**
 * Settings → Anthropic API key (§3.5): stored via [SecretStore] (Keystore-backed encrypted
 * prefs) the moment it's saved here — but saving the key does not itself make any network
 * call. The app stays fully local until a feature that actually uses the key exists (§9.9
 * item 6+) and is invoked.
 */
@Composable
private fun AnthropicKeySection(secretStore: SecretStore) {
    val storedKey by secretStore.anthropicApiKey.collectAsState()
    var draft by remember(storedKey) { mutableStateOf(storedKey ?: "") }
    var revealed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Outlined.Key, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Anthropic API key", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (storedKey != null) "Key saved" else "Not set — local only until used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("API key") },
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide key" else "Show key",
                        )
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { secretStore.setAnthropicApiKey(draft) },
                enabled = draft != (storedKey ?: ""),
            ) { Text("Save") }
        }
    }
}

/**
 * Settings → App Lock (§3.6): off by default, `BiometricPrompt`-gated, independent of and
 * orthogonal to §3.1.2's View-Only lock / checkbox-only mode.
 */
@Composable
private fun AppLockSection(prefs: AppLockPreferences) {
    val enabled by prefs.enabled.collectAsState()
    val lockOnLaunch by prefs.lockOnLaunch.collectAsState()
    val lockOnBackground by prefs.lockOnBackground.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("App Lock", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Require your fingerprint, face, or device PIN to open Tendril",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { prefs.setEnabled(it) })
        }

        AnimatedVisibility(visible = enabled, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                AppLockToggleRow(
                    label = "Lock on launch",
                    checked = lockOnLaunch,
                    onCheckedChange = { prefs.setLockOnLaunch(it) },
                )
                AppLockToggleRow(
                    label = "Lock on background",
                    checked = lockOnBackground,
                    onCheckedChange = { prefs.setLockOnBackground(it) },
                )
            }
        }
    }
}

@Composable
private fun AppLockToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
