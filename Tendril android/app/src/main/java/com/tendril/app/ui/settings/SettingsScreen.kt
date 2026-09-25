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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.nav.ShellTopBar
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
import androidx.compose.ui.platform.LocalContext
import com.tendril.app.domain.plural
import com.tendril.app.markdown.MarkdownExporter
import com.tendril.app.domain.ics.IcsImporter
import com.tendril.app.domain.ics.IcsWriter
import com.tendril.app.notionimport.NotionImporter
import com.tendril.app.storage.AppLockPreferences
import com.tendril.app.ui.settings.TaskSettings
import com.tendril.app.ui.settings.TaskSettingsSection
import com.tendril.app.storage.SecretStore
import com.tendril.app.storage.SyncFolderManager
import com.tendril.app.storage.SyncStatusPreferences
import com.tendril.app.sync.PortableArchive
import com.tendril.app.sync.SyncCoordinator
import com.tendril.app.ui.pages.LocalViewOnly
import com.tendril.app.ui.settings.AiSettingsSection
import com.tendril.app.ui.settings.ThemeSection
import com.tendril.app.ui.theme.ThemeSettings
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description

@Composable
fun SettingsScreen(
    syncFolderManager: SyncFolderManager,
    secretStore: SecretStore,
    appLockPreferences: AppLockPreferences,
    syncStatusPreferences: SyncStatusPreferences,
    syncCoordinator: SyncCoordinator,
    portableArchive: PortableArchive,
    markdownExporter: MarkdownExporter,
    /** §0.8 step 6e — `.ics` out and in. */
    entryDao: com.tendril.app.data.entry.EntryDao,
    icsImporter: IcsImporter,
    notionImporter: NotionImporter,
    databaseSyncManager: DatabaseSyncManager,
    /** §0.6.15 — the key store and preferences the Claude section needs. */
    core: com.tendril.app.ui.WorkbenchCore,
    modifier: Modifier = Modifier,
) {
    // §3.1.2 as the user decided it: "View-Only is absolute, and it covers Settings." Read from
    // the same CompositionLocal every Pages screen reads (provided once in `WorkbenchScaffold`,
    // which composes this screen inside it) rather than taken as a parameter — the flag is global
    // and read-only below the root, and a new parameter here would have to be threaded through
    // `AndroidWorkbenchScaffold` for a value that is already in scope.
    val viewOnly = LocalViewOnly.current

    Scaffold(
        modifier = modifier,
        topBar = { ShellTopBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding).verticalScroll(rememberScrollState())) {
            AppearanceSection(core.themeSettings)
            HorizontalDivider()
            SyncFolderSection(syncFolderManager, syncStatusPreferences, syncCoordinator)
            HorizontalDivider()
            AtRestEncryptionSection(secretStore, syncCoordinator)
            HorizontalDivider()
            PortableBackupSection(portableArchive, markdownExporter, entryDao, icsImporter, viewOnly)
            HorizontalDivider()
            // The whole section is swapped out rather than merely disabled: its file picker is
            // launched from inside it, and the honest thing to show someone is why the button
            // they came here for isn't there. (Standing in for it here, rather than editing
            // `NotionImportSection` itself, also keeps that file's signature alone.)
            if (viewOnly) NotionImportLockedSection() else NotionImportSection(notionImporter, databaseSyncManager)
            HorizontalDivider()
            // §0.6.15 — the shared section: key, model, what is sent.
            AiSettingsSection(core.aiKeyStore, core.keyValueStore)
            HorizontalDivider()
            AppLockSection(appLockPreferences)
            HorizontalDivider()
            TasksHabitsSection(core.taskSettings)
            HorizontalDivider()
        }
    }
}

/**
 * Settings → Appearance (§2.3): a collapsed-by-default disclosure row — icon, current summary
 * ("Ink · System · Sans"), chevron — so scrolling Settings can't accidentally fire a theme change.
 * 14g·1 — the body is the shared [ThemeSection]; the phone shows its *Deeper blacks* row.
 */
@Composable
private fun AppearanceSection(settings: ThemeSettings) {
    var expanded by remember { mutableStateOf(false) }
    val choice = settings.observe()

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
                    Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.body)
                    Text(
                        text = stringResource(
                            R.string.settings_appearance_summary_template,
                            choice.register.label,
                            choice.mode.label,
                            choice.typeface.label,
                        ),
                        style = MaterialTheme.typography.description,
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
            ThemeSection(settings, showOled = true)
        }
    }
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
    coordinator: SyncCoordinator,
) {
    val folderUri by manager.folderUri.collectAsState()
    val lastSyncedAt by syncStatus.lastSyncedAt.collectAsState()
    // Both from the coordinator rather than local state: the same pass now also runs from the
    // Activity's lifecycle (§9.4), so this button has to reflect a sync it didn't start, and a
    // failure that happened while nothing was on screen has to surface somewhere.
    val syncing by coordinator.running.collectAsState()
    val syncError by coordinator.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri -> manager.onFolderGranted(uri) },
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_sync_folder), style = MaterialTheme.typography.body)
                val summary = folderUri?.let {
                    stringResource(R.string.settings_sync_folder_summary_set, manager.displayNameFor(it))
                } ?: stringResource(R.string.settings_sync_folder_summary_unset)
                Text(summary, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(lastSyncedText, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = !syncing,
                    // Every guard this used to hold inline — the store failing to open, the
                    // passphrase-mismatch refusal to write, clearing the in-flight flag in a
                    // finally — now lives in SyncCoordinator, because the lifecycle triggers
                    // need exactly the same ones and two copies of that is how they drift.
                    onClick = { scope.launch { coordinator.sync() } },
                ) { Text(if (syncing) "Syncing…" else "Sync now") }
            }
            syncError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Settings → at-rest snapshot encryption (§9.4.2) — optional, off by default. "Off" is
 * simply "no passphrase set"; there's no separate toggle to fall out of sync with that.
 */
@Composable
private fun AtRestEncryptionSection(secretStore: SecretStore, coordinator: SyncCoordinator) {
    val stored by secretStore.syncPassphrase.collectAsState()
    var draft by remember(stored) { mutableStateOf(stored ?: "") }
    var revealed by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showRekeyConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rekeying by coordinator.running.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Sync folder encryption", style = MaterialTheme.typography.body)
                Text(
                    text = if (stored != null) "On — snapshots are encrypted at rest" else "Off — snapshots are plain JSON",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TendrilField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = "Passphrase",
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
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

        // Offered only where it means something: the folder already has a passphrase, and the
        // box holds a different, non-empty one. Re-keying *to* no encryption is a different
        // operation with a different hazard (§9.4.2's cleartext downgrade) and is not offered.
        if (stored != null && draft.isNotEmpty() && draft != stored) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showRekeyConfirm = true }, enabled = !rekeying) {
                Text(if (rekeying) "Re-keying…" else "Re-key folder to this passphrase")
            }
        }
    }

    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Change sync encryption") },
            text = {
                Text(
                    "This changes the passphrase this device uses to read the folder — it does " +
                        "not change the folder. If the folder was written with a different one, " +
                        "sync pauses until the two match; use \"Re-key folder\" below to change the " +
                        "folder itself.\n\n" +
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

    if (showRekeyConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRekeyConfirm = false },
            title = { Text("Re-key this sync folder?") },
            text = {
                Text(
                    "Every snapshot in the folder will be re-encrypted under the new passphrase. " +
                        "This device syncs once under the current passphrase first, and refuses to " +
                        "re-key if anything in the folder can't be read — re-keying rewrites the whole " +
                        "folder, so it can only keep what this device can actually read.\n\n" +
                        "Your other devices will stop syncing until you give them the new passphrase. " +
                        "Their own data isn't lost: once they have it they'll merge and republish."
                )
            },
            confirmButton = {
                DialogTextButton(onClick = {
                    val target = draft
                    showRekeyConfirm = false
                    // The coordinator stores the new passphrase only after the folder carries
                    // it, so a failure here leaves this device on the one that still works.
                    scope.launch { coordinator.rekey(target) }
                })
            },
            dismissButton = { DialogTextButton(onClick = { showRekeyConfirm = false }, label = "Cancel") },
        )
    }
}

@Composable
private fun DialogTextButton(onClick: () -> Unit, label: String = "Confirm") {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}

/**
 * Settings → full data import/export (§3.5, §9.4.1) — a portable `.tendril` package,
 * distinct from the continuous background sync feed above.
 *
 * [viewOnly] (§3.1.2) takes Import and Restore away, and deliberately leaves Export alone:
 * exporting reads and changes nothing, and a lock that stopped someone taking a backup would be
 * working against the data it exists to protect. The refusal is stated rather than implied —
 * a disabled button with no reason beside it, on the screen where the eye toggle isn't visible,
 * is indistinguishable from a broken one. [PortableArchive] refuses these two calls itself as
 * well, so the guard doesn't depend on this composable being the only way in.
 */
@Composable
private fun PortableBackupSection(
    archive: PortableArchive,
    markdownExporter: MarkdownExporter,
    entryDao: com.tendril.app.data.entry.EntryDao,
    icsImporter: IcsImporter,
    viewOnly: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching { archive.export(uri) }.fold(
                    { result ->
                        if (result.encrypted) {
                            "Exported — encrypted with your sync passphrase. Anyone opening this " +
                                "file, including you on another device, will need that passphrase."
                        } else {
                            "Exported"
                        }
                    },
                    { it.message ?: "Export failed." },
                )
            }
        },
    )
    // §7 in reverse. A separate launcher rather than a mode on the one above: the two write
    // different formats to different file names, and a picker that produced one or the other
    // depending on a toggle elsewhere on the screen is how someone ends up with the wrong file.
    val markdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching {
                    // Throwing rather than no-op if the provider will not hand over a stream, for
                    // the reason `PortableArchive.export` gives: an export that writes nothing and
                    // reports success is discovered at the worst possible moment.
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Couldn't open the chosen file for writing — nothing was exported.")
                    stream.use { markdownExporter.export(it) }
                }.fold(
                    { "Exported ${plural(it.pages, "page")}, ${plural(it.canvases, "canvas", "canvases")} and ${plural(it.images, "picture")}" },
                    { it.message ?: "Markdown export failed." },
                )
            }
        },
    )
    // §0.8 step 6e — the calendar as a file other calendars read: every task and event, with
    // UIDs, so the same file imported back updates rather than duplicates.
    val icsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching {
                    val text = IcsWriter.write(entryDao.getAll())
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("Couldn't open the chosen file for writing — nothing was exported.")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    text.split("BEGIN:VEVENT").size - 1 to text.split("BEGIN:VTODO").size - 1
                }.fold(
                    { (events, tasks) -> "Exported $events event(s) and $tasks task(s) as .ics" },
                    { it.message ?: "Calendar export failed." },
                )
            }
        },
    )
    val icsImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Couldn't read the chosen file.")
                    icsImporter.import(text)
                }.fold(
                    { "Imported ${it.created} new, ${it.updated} updated" + if (it.skipped > 0) ", ${it.skipped} skipped" else "" + if (it.kept > 0) ", ${it.kept} unchanged" else "" },
                    { it.message ?: "Calendar import failed." },
                )
            }
        },
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                statusMessage = runCatching { archive.importAdditive(uri) }.fold(
                    { result ->
                        // Pictures named separately from the record counts above: they travel
                        // as their own archive entries, so "12 entry file(s)" can be true of an
                        // import that carried no images at all.
                        val pictures =
                            if (result.imagesRestored > 0) ", ${result.imagesRestored} picture(s)" else ""
                        val applied = "Imported ${result.entryFilesFound} entry file(s), " +
                            "${result.habitFilesFound} habit file(s)$pictures"
                        // A record this build can't read is skipped, never applied half-way and
                        // never written over the local copy — but it is said out loud, because an
                        // import that landed nine records of ten looks exactly like one that
                        // landed all ten until the day the tenth is missed.
                        if (result.quarantinedRecords > 0) {
                            "$applied. ${result.quarantinedRecords} record(s) were skipped — this " +
                                "version of Tendril can't read them, most likely because they were " +
                                "written by a newer one. Nothing of yours was changed for those."
                        } else {
                            applied
                        }
                    },
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
        Text("Full data import/export", style = MaterialTheme.typography.body)
        Text(
            "A portable .tendril package — a one-off file, separate from the continuous sync above",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        // P7 (item 23's Lows) — three buttons wrap whole on a 360 dp phone instead of breaking *Restore backup*.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { exportLauncher.launch("tendril-export.tendril") }) { Text("Export") }
            Button(enabled = !viewOnly, onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import") }
            Button(enabled = !viewOnly, onClick = { restoreLauncher.launch(arrayOf("*/*")) }) { Text("Restore backup") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Export as Markdown", style = MaterialTheme.typography.body)
        Text(
            "A zip of .md files any editor can open — an Obsidian vault, with every canvas as a .canvas file. " +
                "Databases export their pages, not their layout.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Not gated by View-Only, like Export above and for the same reason: a lock that stopped
        // someone taking a readable copy of their own notes would work against the data it exists
        // to protect.
        Button(onClick = { markdownLauncher.launch("tendril-markdown.zip") }) { Text("Export Markdown") }
        Spacer(Modifier.height(12.dp))
        Text("Calendar (.ics)", style = MaterialTheme.typography.body)
        Text(
            "Every task and event as an iCalendar file any calendar app opens; importing one brings " +
                "its events and to-dos in, updating what came from Tendril before.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { icsExportLauncher.launch("tendril.ics") }) { Text("Export .ics") }
            Button(enabled = !viewOnly, onClick = { icsImportLauncher.launch(arrayOf("text/calendar", "*/*")) }) { Text("Import .ics") }
        }
        if (viewOnly) {
            Spacer(Modifier.height(8.dp))
            ViewOnlyReason("Importing and restoring are")
        }
        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * §3.1.2 — the one sentence every View-Only refusal on this screen says, so they read as one rule
 * rather than as three separate malfunctions. It names where the toggle lives because Settings is
 * the one place the eye in the Pages toolbar isn't on screen: without that, "unavailable" is just
 * a dead button.
 *
 * [what] is the subject of the sentence, e.g. "Importing and restoring are".
 */
@Composable
private fun ViewOnlyReason(what: String) {
    Text(
        "$what unavailable while View-Only is on. Turn it off with the eye in the Pages toolbar.",
        style = MaterialTheme.typography.description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * §3.1.2 — what stands in for [NotionImportSection] while the lock is on. A Notion import creates
 * pages, databases, rows and assets in bulk, which makes it the largest create operation in the
 * app after Restore, and every one of those writes travels to every other device on the next sync
 * pass (§9.4) — so it is exactly the kind of write the lock exists to prevent someone making by
 * accident. The section keeps its heading and icon so the setting is visibly still there, and
 * simply has no button while it's locked.
 */
@Composable
private fun NotionImportLockedSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Import from Notion", style = MaterialTheme.typography.body)
                ViewOnlyReason("Importing a Notion export is")
            }
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
                Text("App Lock", style = MaterialTheme.typography.body)
                Text(
                    text = "Require your fingerprint, face, or device PIN to open Tendril",
                    style = MaterialTheme.typography.description,
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

/**
 * §0.5.1 / §0.5.2 — the two disclosures for Tasks & Habits, both off by default. Worded as what
 * appears, not as what is "enabled": the flag and the streak are things a person may want to
 * see, not features the app is withholding.
 */
@Composable
private fun TasksHabitsSection(settings: TaskSettings) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Tasks & Habits", style = MaterialTheme.typography.body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        // 14g·3 — the shared section (the desktop pane renders the same one).
        TaskSettingsSection(settings)
    }
}

@Composable
private fun AppLockToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.body, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
