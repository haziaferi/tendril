@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.desktopapp

import com.tendril.app.sync.DesktopLocalImageStore
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.tendril.app.ui.nav.DEFAULT_WINDOW
import com.tendril.app.ui.nav.MIN_WINDOW
import com.tendril.app.ui.nav.WINDOW_FRAME_KEY
import com.tendril.app.ui.nav.WindowFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.Dimension
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.switcher.SwitcherState
import com.tendril.app.ui.pages.PagesTreeState
import androidx.compose.ui.input.key.isCtrlPressed
import com.tendril.app.data.buildTendrilDatabase
import com.tendril.app.sync.DesktopFileSyncFileStore
import com.tendril.app.sync.PagesSyncEngine
import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.sync.quarantineMessage
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.calendar.CalendarScreen
import com.tendril.app.ui.taskshabits.TasksHabitsScreen
import com.tendril.app.domain.ics.IcsWriter
import com.tendril.app.ui.nav.WorkbenchScaffold
import com.tendril.app.ui.trash.EntryTrashSheet
import com.tendril.app.ui.trash.HabitTrashSheet
import com.tendril.app.ui.nav.ShortcutActions
import com.tendril.app.ui.nav.ShortcutsState
import com.tendril.app.ui.nav.shortcutFor
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
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
 * Every tab is shared now: Canvas (2026-09-11, `tendril-spec.md` §0.6.10), Calendar, Tasks &
 * Habits, Road Map (2026-09-12/13) and, with §0.6.15, Settings — a minimal [DesktopSettingsScreen]
 * holding the Claude section; theme, the sync folder, backups and reminders are still Android's.
 * Theme is fixed (Ink/Light/Sans) until the desktop pass (§0.10 item 14) gives it a picker.
 */
fun main() {
    val dbFile = File(File(System.getProperty("user.home"), ".tendril-desktop-dev"), "tendril.db")
    val database = buildTendrilDatabase(dbFile)
    val container = DesktopAppContainer(database)
    val core = container.workbenchCore
    // §3.1.1 — index any page without an FTS row (all of them, once, after v16 emptied the table).
    kotlinx.coroutines.runBlocking(Dispatchers.IO) { core.pageContentRepository.healIndex() }

    val pagesSyncEngine = PagesSyncEngine(
        database.pageDao(), database.blockDao(), database.labelDao(), database.pageDatabaseDao(),
        database.propertyDao(), database.propertyValueDao(), database.pageDatabaseViewDao(),
        database.pageCanvasDao(), database.canvasNodeDao(), database.canvasEdgeDao(),
        database.pageRelationDao(), container.purgeRegistry, core.pageContentRepository, core.pageHistory,
    )
    val orchestrator = SnapshotSyncOrchestrator(
        database.entryDao(), database.habitDao(), database.pageDao(),
        database.reminderDao(), database.entryCompletionDao(), database.habitCompletionDao(), database.timeLogDao(), pagesSyncEngine, container.purgeRegistry,
        DesktopLocalImageStore(File(dbFile.parentFile, "images")),
    )
    val folderManager = DesktopSyncFolderManager()
    val escapeBack = EscapeBackInput()
    val switcher = SwitcherState()
    // 14c — the Pages tree pane's state, here so Ctrl+\ can reach it from the window's key handler.
    val treeState = PagesTreeState(core.keyValueStore)
    // 14e — the key table's actions (filled by the scaffold) and the overlay's open flag.
    val shortcutActions = ShortcutActions()
    val shortcuts = ShortcutsState()

    application {
        // B§13.4 14a — the window remembers itself: size and position from the last run, kept in
        // the same KeyValueStore as every other device preference (§0.10 item 12), written a
        // moment after the last change rather than on every drag frame. An unpositioned frame
        // (the default, or a stored one the OS should place) is left to the platform.
        val frame = remember { WindowFrame.decode(core.keyValueStore.get(WINDOW_FRAME_KEY)) ?: DEFAULT_WINDOW }
        val windowState = rememberWindowState(
            size = DpSize(frame.w.dp, frame.h.dp),
            position = if (frame.positioned) WindowPosition(frame.x.dp, frame.y.dp) else WindowPosition.PlatformDefault,
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Tendril",
            icon = painterResource("tendril_icon.png"),
            // Preview, not consume: a text field that wants Escape for itself still gets it, and
            // an Escape nobody handles falls through to nothing, as before. On the *release*,
            // because that is the half of an Escape press this callback sees: the window swallows
            // the press before preview (observed 2026-09-12 with a log on every event; `A` arrived
            // as down and up, Esc as up alone). A release also cannot auto-repeat.
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) escapeBack.back()
                // 14e — every other chord is one table (`Shortcuts.kt`): Ctrl+K (§3.1.7, step 8a) and
                // Ctrl+\ (14c) moved into it; the overlay is generated from the same table.
                if (event.type == KeyEventType.KeyDown) {
                    shortcutFor(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)?.let { shortcutActions.run(it); return@Window true }
                }
                false
            },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WINDOW.w, MIN_WINDOW.h)
                snapshotFlow { windowState.size to windowState.position }.collectLatest { (size, position) ->
                    delay(400)
                    // Only a floating frame is worth remembering: a maximised window reports the
                    // screen as its size, and restoring that as a floating frame overflows the screen.
                    if (windowState.isMinimized || windowState.placement != WindowPlacement.Floating) return@collectLatest
                    val stored = if (position.isSpecified) WindowFrame(size.width.value.toInt(), size.height.value.toInt(), position.x.value.toInt(), position.y.value.toInt())
                    else WindowFrame(size.width.value.toInt(), size.height.value.toInt(), -1, -1)
                    core.keyValueStore.put(WINDOW_FRAME_KEY, stored.encode())
                }
            }
            TendrilTheme(colorTheme = TendrilColorTheme.INK, mode = TendrilMode.LIGHT, typeface = TendrilTypeface.SANS) {
                App(core, orchestrator, folderManager, escapeBack, switcher, treeState, shortcuts, shortcutActions)
            }
        }
    }
}

/**
 * tendril-spec.md §0.10 item 10 — the desktop half of Back. Compose Multiplatform's `BackHandler`
 * (the mind map and the canvas block arm through it) registers on the `NavigationEventDispatcher`
 * the skiko window already provides, but nothing on desktop ever *feeds* that dispatcher: Android
 * has the system gesture, desktop has no equivalent, so an armed map ignored Escape. This is the
 * missing input — one key, one completed back event — and nothing else: the handlers that decide
 * what Back means stay in shared code, where Android's already are.
 */
private class EscapeBackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
}

@Composable
private fun App(core: WorkbenchCore, orchestrator: SnapshotSyncOrchestrator, folderManager: DesktopSyncFolderManager, escapeBack: EscapeBackInput, switcher: SwitcherState, treeState: PagesTreeState, shortcuts: ShortcutsState, shortcutActions: ShortcutActions) {
    // The dispatcher is a composition local of the window's content, so the key input can only be
    // attached from inside it; the key event itself arrives at the window, outside. Hence the
    // input is built in `main` and joined here.
    val backOwner = LocalNavigationEventDispatcherOwner.current
    DisposableEffect(backOwner) {
        backOwner?.navigationEventDispatcher?.addInput(escapeBack)
        onDispose { backOwner?.navigationEventDispatcher?.removeInput(escapeBack) }
    }
    // Compose Multiplatform doesn't supply a default ViewModelStoreOwner outside NavHost (which
    // this app's hand-rolled nav doesn't use — see WorkbenchNavState's doc comment) — one
    // application-lifetime owner, provided once here, is what the ported screens' viewModel()
    // calls resolve against.
    val viewModelStoreOwner = remember { DesktopViewModelStoreOwner() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
        Column(modifier = Modifier.fillMaxSize()) {
            WorkbenchScaffold(
                core = core,
                switcher = switcher,
                treeState = treeState,
                shortcuts = shortcuts,
                shortcutActions = shortcutActions,
                // §0.8 step 6a — the shared Calendar. Google Calendar sync is Play Services and
                // reminders are AlarmManager, so the settings slot says so and the bell is absent.
                calendarContent = { onOpenPage ->
                    CalendarScreen(
                        core = core,
                        onOpenPage = onOpenPage,
                        settingsSheet = { onDismiss -> DesktopCalendarSettingsSheet(core, onDismiss) },
                        reminderSheet = null,
                    )
                },
                // §0.8 step 7a — shared. No alarms here, so no bell; the switches off.
                tasksHabitsContent = { onOpenReview, quickAddRequested, onQuickAddConsumed ->
                    TasksHabitsScreen(
                        core = core,
                        onOpenReview = onOpenReview,
                        quickAddRequested = quickAddRequested,
                        onQuickAddConsumed = onQuickAddConsumed,
                        showImportance = false,
                        showStreaks = false,
                        reminderSheet = null,
                        // 14f·1 (§0.10 item 13) — the Trash sheets are shared now, so the desktop
                        // has its Trash button; the bell stays absent: no alarms here (§12.1).
                        entryTrashSheet = { onDismiss -> EntryTrashSheet(core = core, onDismiss = onDismiss) },
                        habitTrashSheet = { onDismiss -> HabitTrashSheet(core = core, onDismiss = onDismiss) },
                    )
                },
                // §0.6.15 — the last stand-in gone: Settings holds the Claude section; 14a moved
                // the sync folder controls here too, off the top of every screen.
                settingsContent = { DesktopSettingsScreen(core, syncSection = { SyncBar(orchestrator, folderManager) }, onShowShortcuts = { shortcuts.open = true }) },
            )
        }
    }
}

/**
 * The Calendar's `···` on desktop: what is Android-only, said plainly, and §0.8 step 6e's
 * `.ics` export and import — here rather than in Settings, which desktop does not have yet.
 */
@Composable
private fun DesktopCalendarSettingsSheet(core: WorkbenchCore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    TendrilSheet(title = "Calendar settings", onDismiss = onDismiss) {
        Column {
            Text(
                "Google Calendar sync and reminders are Android-only for now — they need Play Services and the alarm manager (tendril-windows-spec.md §1).",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("Calendar (.ics)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Every task and event as an iCalendar file any calendar app opens; importing one brings its events and to-dos in, updating what came from Tendril before.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row {
                TextButton(onClick = {
                    scope.launch {
                        status = runCatching {
                            val text = IcsWriter.write(core.database.entryDao().getAll())
                            val file = withContext(Dispatchers.IO) { pickFile(save = true) } ?: return@launch
                            withContext(Dispatchers.IO) { file.writeText(text, Charsets.UTF_8) }
                            "Exported to ${file.name}"
                        }.getOrElse { it.message ?: "Export failed." }
                    }
                }) { Text("Export .ics") }
                TextButton(onClick = {
                    scope.launch {
                        status = runCatching {
                            val file = withContext(Dispatchers.IO) { pickFile(save = false) } ?: return@launch
                            val result = core.icsImporter.import(withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) })
                            "Imported ${result.created} new, ${result.updated} updated" + if (result.skipped > 0) ", ${result.skipped} skipped" else ""
                        }.getOrElse { it.message ?: "Import failed." }
                    }
                }) { Text("Import .ics") }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

/** A save or open dialog for one `.ics` file — off the UI thread, as [pickDirectory]. */
private fun pickFile(save: Boolean): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = if (save) "Export calendar as .ics" else "Import an .ics file"
        if (save) selectedFile = File("tendril.ics")
    }
    val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Allocated once rather than per recomposition of the passphrase field it decorates. */
private val PASSPHRASE_MASK = PasswordVisualTransformation()

/**
 * The desktop's sync controls — folder, session passphrase, Sync now, the last result. Until
 * 14a a strip across the top of every screen; now the first section of Settings, which is where
 * Android keeps the same controls and the only place B§13.4's mock had room for them.
 */
@Composable
internal fun SyncBar(orchestrator: SnapshotSyncOrchestrator, folderManager: DesktopSyncFolderManager) {
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
