package com.tendril.desktopapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.tendril.app.data.buildTendrilDatabase
import com.tendril.app.sync.DesktopFileSyncFileStore
import com.tendril.app.sync.PagesSyncEngine
import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.sync.quarantineMessage
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.nav.WorkbenchScaffold
import com.tendril.app.ui.theme.TendrilColorTheme
import com.tendril.app.ui.theme.TendrilMode
import com.tendril.app.ui.theme.TendrilTheme
import com.tendril.app.ui.theme.TendrilTypeface
import com.tendril.desktopapp.sync.DesktopSyncFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * Milestone 3 (tendril-windows-spec.md §6 step 3) — replaces Milestone 1/2's throwaway flat
 * two-pane viewer with the real, shared Workbench UI (theming, nav shell, Pages/PageDetail/
 * PageDatabase block editor). This is also the point desktop starts originating edits rather
 * than only ever writing back data just merged in from sync — see §3's re-affirmed gate.
 *
 * Calendar/Tasks & Habits/Road Map/Settings and the Canvas page kind aren't ported this pass
 * (Android-integration-heavy: AlarmManager, Calendar Provider, Google Calendar, Notion import,
 * BiometricPrompt) — [NotAvailableOnDesktop] stands in for each. Theme is fixed (Ink/Light/Sans)
 * since there's no Settings screen yet to pick one on desktop.
 */
fun main() {
    val dbFile = File(File(System.getProperty("user.home"), ".tendril-desktop-dev"), "tendril.db")
    val database = buildTendrilDatabase(dbFile)
    val container = DesktopAppContainer(database)
    val core = container.workbenchCore

    val pagesSyncEngine = PagesSyncEngine(
        database.pageDao(), database.blockDao(), database.tagDao(), database.pageDatabaseDao(),
        database.propertyDao(), database.propertyValueDao(), database.pageDatabaseViewDao(),
        database.pageCanvasDao(), database.canvasNodeDao(), database.canvasEdgeDao(),
        database.pageRelationDao(), container.purgeRegistry, core.pageContentRepository,
    )
    val orchestrator = SnapshotSyncOrchestrator(
        database.entryDao(), database.habitDao(), database.pageDao(), pagesSyncEngine, container.purgeRegistry,
    )
    val folderManager = DesktopSyncFolderManager()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Tendril (desktop preview)",
            icon = painterResource("tendril_icon.png"),
        ) {
            TendrilTheme(colorTheme = TendrilColorTheme.INK, mode = TendrilMode.LIGHT, typeface = TendrilTypeface.SANS) {
                App(core, orchestrator, folderManager)
            }
        }
    }
}

@Composable
private fun App(core: WorkbenchCore, orchestrator: SnapshotSyncOrchestrator, folderManager: DesktopSyncFolderManager) {
    // Compose Multiplatform doesn't supply a default ViewModelStoreOwner outside NavHost (which
    // this app's hand-rolled nav doesn't use — see WorkbenchNavState's doc comment) — one
    // application-lifetime owner, provided once here, is what the ported screens' viewModel()
    // calls resolve against.
    val viewModelStoreOwner = remember { DesktopViewModelStoreOwner() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
        Column(modifier = Modifier.fillMaxSize()) {
            SyncBar(orchestrator, folderManager)
            HorizontalDivider()
            WorkbenchScaffold(
                core = core,
                calendarContent = { NotAvailableOnDesktop("Calendar") },
                tasksHabitsContent = { NotAvailableOnDesktop("Tasks & Habits") },
                roadMapContent = { NotAvailableOnDesktop("Road Map") },
                settingsContent = { NotAvailableOnDesktop("Settings") },
                canvasContent = { _, _, _ -> NotAvailableOnDesktop("Canvas") },
            )
        }
    }
}

/** Allocated once rather than per recomposition of the passphrase field it decorates. */
private val PASSPHRASE_MASK = PasswordVisualTransformation()

@Composable
private fun SyncBar(orchestrator: SnapshotSyncOrchestrator, folderManager: DesktopSyncFolderManager) {
    val folderPath by folderManager.folderPath.collectAsState()
    var passphrase by remember { mutableStateOf("") } // session-only, never persisted (§12.5/Milestone 2)
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folderPath?.toString() ?: "No sync folder chosen",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = {
            scope.launch {
                val chosen = withContext(Dispatchers.IO) { pickDirectory() }
                if (chosen != null) folderManager.setFolder(chosen)
            }
        }) { Text("Choose folder…") }

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase (optional)") },
            singleLine = true,
            // This is the key to every snapshot in the folder, typed in whatever room the
            // desktop happens to be in. Android's two passphrase fields also offer a reveal
            // toggle; this one doesn't yet.
            visualTransformation = PASSPHRASE_MASK,
            modifier = Modifier.width(220.dp).padding(horizontal = 8.dp),
        )

        TextButton(
            enabled = folderPath != null && !syncing,
            onClick = {
                val path = folderPath ?: return@TextButton
                syncing = true
                scope.launch {
                    // SyncFileStore's contract is that a write which cannot complete throws,
                    // and callers handle it. Without the finally, one throw left `syncing`
                    // stuck true and the button disabled for the rest of the session, with
                    // nothing on screen saying why. Android's caller already did this.
                    try {
                        val key = passphrase.ifBlank { null }
                        val store = DesktopFileSyncFileStore(path)
                        val merge = orchestrator.readAndMerge(store, key)
                        if (merge.passphraseMismatch) {
                            // Writing here would overwrite the folder's only copy with this
                            // device's state under the wrong key (§9.4.2).
                            syncError = "${merge.undecryptableFiles} file(s) couldn't be decrypted — " +
                                "check the passphrase. Nothing was written."
                        } else {
                            orchestrator.writeSnapshots(store, key)
                            // Quarantine is a survivable pass, but never a silent one — same
                            // reasoning and same channel as Android's SyncCoordinator. Null on a
                            // clean pass, so the notice clears itself.
                            syncError = merge.quarantineMessage()
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
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
    syncError?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

/** [JFileChooser.showOpenDialog] blocks the calling thread — call this off the Compose UI
 * thread (see [Dispatchers.IO] usage at the call site above). */
private fun pickDirectory(): Path? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choose Tendril sync folder"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else null
}
