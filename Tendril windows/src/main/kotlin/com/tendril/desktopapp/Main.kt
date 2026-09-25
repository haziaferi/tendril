@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

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
import com.tendril.app.ui.nav.LocalSystemTextScale
import com.tendril.app.ui.nav.LocalTitleBarInstaller
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.isTraySupported
import com.tendril.app.ui.nav.CLOSE_TO_TRAY_KEY
import com.tendril.app.ui.nav.QUICK_ADD_CHORD_KEY
import com.tendril.app.ui.nav.QuickAddChord
import com.tendril.app.ui.nav.WorkbenchDestination
import com.tendril.app.domain.reminders.Firing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.swing.SwingUtilities
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
import com.tendril.app.ui.nav.PopOutRegistry
import com.tendril.app.ui.nav.WorkbenchNavState
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.switcher.SwitcherState
import com.tendril.app.ui.pages.PagesTreeState
import androidx.compose.ui.input.key.isCtrlPressed
import com.tendril.app.data.buildTendrilDatabase
import com.tendril.app.sync.DesktopFileSyncFileStore
import com.tendril.app.sync.PagesSyncEngine
import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.calendar.CalendarScreen
import com.tendril.app.ui.taskshabits.TasksHabitsScreen
import com.tendril.app.domain.ics.IcsWriter
import com.tendril.app.ui.nav.WorkbenchScaffold
import com.tendril.app.ui.trash.EntryTrashSheet
import com.tendril.app.ui.trash.HabitTrashSheet
import com.tendril.app.ui.reminders.ReminderSheet
import com.tendril.app.ui.nav.ShortcutActions
import com.tendril.app.ui.nav.ShortcutsState
import com.tendril.app.ui.nav.shortcutFor
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import com.tendril.app.ui.theme.TendrilTheme
import com.tendril.app.ui.theme.resolveDark
import com.tendril.desktopapp.sync.DesktopSyncFolderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

/**
 * Milestone 3 (tendril-windows-spec.md §6 step 3) — replaces Milestone 1/2's throwaway flat
 * two-pane viewer with the real, shared Workbench UI (theming, nav shell, Pages/PageDetail/
 * PageDatabase block editor). This is also the point desktop starts originating edits rather
 * than only ever writing back data just merged in from sync — see §3's re-affirmed gate.
 *
 * Every tab is shared now: Canvas (2026-09-11, `tendril-spec.md` §0.6.10), Calendar, Tasks &
 * Habits, Road Map (2026-09-12/13) and, with §0.6.15, Settings — a minimal [DesktopSettingsScreen]
 * holding the Claude section; the sync folder, backups and reminders are still Android's.
 * 14g·1 — the theme is the shared setting (`ThemeSettings`, `prefs.properties`): a register, a
 * mode whose System follows Windows through `isSystemInDarkTheme()`, a typeface; the phone's
 * OLED toggle never applies here.
 *
 * B§13.6 #7 — the notification area: an icon with *Open Tendril · Quick add… · Quit*, the
 * window's × hiding it there (`close_to_tray`, a Settings switch), reminder toasts from
 * [DesktopReminderScheduler], a global quick-add chord ([GlobalHotkey], `quick_add_chord`)
 * opening [QuickAddWindow]. §0.10 item 21 — uncaught exceptions are logged to
 * `~/.tendril-desktop-dev/crash.log` and the window kept (Compose's default handler showed a
 * dialog and exited; a clipboard hiccup closed the app on 2026-09-16).
 */
fun main() {
    val home = File(System.getProperty("user.home"), ".tendril-desktop-dev")
    installCrashLog(File(home, "crash.log"))
    val dbFile = File(home, "tendril.db")
    val database = buildTendrilDatabase(dbFile)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // The tray's state lives outside the composition so the scheduler (no composition) can toast.
    val trayState = TrayState()
    val scheduler = DesktopReminderScheduler(database, appScope) { firing ->
        System.err.println("Tendril: toast ${DesktopReminderScheduler.title(firing)} — ${DesktopReminderScheduler.message(firing)}")
        SwingUtilities.invokeLater {
            trayState.sendNotification(Notification(DesktopReminderScheduler.title(firing), DesktopReminderScheduler.message(firing), Notification.Type.Info))
        }
    }
    val container = DesktopAppContainer(database, scheduler)
    val core = container.workbenchCore
    scheduler.replan()
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
        database.reminderDao(), database.entryCompletionDao(), database.habitCompletionDao(), database.checkInDao(), database.timeLogDao(), pagesSyncEngine, container.purgeRegistry,
        DesktopLocalImageStore(File(dbFile.parentFile, "images")),
    )
    val folderManager = DesktopSyncFolderManager()
    val switcher = SwitcherState()
    // B§13.6 #6 — the main window's nav state is built here so a pop-out window can hand a page
    // to it; the pop-outs themselves and their remembered pages (`popout_pages`).
    val navState = WorkbenchNavState()
    val mainWindow = MainWindowActions(navState)
    val popOutRegistry = PopOutRegistry(core.keyValueStore)
    val popOuts = PopOuts(core, popOutRegistry).also { it.restore() }
    // 14c — the Pages tree pane's state, here so Ctrl+\ can reach it from the window's key handler.
    val treeState = PagesTreeState(core.keyValueStore)
    // 14e — the key table's actions (filled by the scaffold) and the overlay's open flag.
    val shortcutActions = ShortcutActions()
    val shortcuts = ShortcutsState()
    // B§13.6 #7 — the chord's popup, the chord itself (rebound when Settings changes it), and
    // whether the main window is showing (the × hides it when `close_to_tray` is on).
    val quickAdd = QuickAddState()
    val hotkey = GlobalHotkey { quickAdd.open = true }
    appScope.launch {
        core.keyValueStore.observe(QUICK_ADD_CHORD_KEY).map { QuickAddChord.fromKey(it) }.distinctUntilChanged().collect { chord ->
            hotkey.bind(chord)
            shortcuts.quickAddChordLabel = chord.label
        }
    }
    val mainVisible = mutableStateOf(true)

    application {
        val showMain = {
            mainVisible.value = true
            mainWindow.front()
        }
        val exceptionHandlers = remember {
            WindowExceptionHandlerFactory { _ -> WindowExceptionHandler { e -> logCrash(File(home, "crash.log"), Thread.currentThread(), e) } }
        }
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides exceptionHandlers) {
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
            // The × hides to the notification area when the switch is on and the OS has one (the
            // tray's *Quit* exits); otherwise it quits, as before.
            onCloseRequest = {
                if (core.keyValueStore.getBoolean(CLOSE_TO_TRAY_KEY, true) && isTraySupported) mainVisible.value = false else exitApplication()
            },
            visible = mainVisible.value,
            state = windowState,
            title = "Tendril",
            icon = painterResource("tendril_icon.png"),
            // Escape is the runtime's: Compose Multiplatform dispatches the Escape press to the
            // enabled `BackHandler`s itself (the scaffold's page, a sheet's, a card's) — that is
            // why the press never reached this preview and only its release did (observed
            // 2026-09-12). The release was dispatched here as a second back until 2026-09-17,
            // when L10's walk caught it: every Escape went back twice (a sheet's close *and* the
            // page's pop when a frame fell between the two). One dispatch now, the platform's.
            onPreviewKeyEvent = { event ->
                // 14e — every other chord is one table (`Shortcuts.kt`): Ctrl+K (§3.1.7, step 8a) and
                // Ctrl+\ (14c) moved into it; the overlay is generated from the same table.
                if (event.type == KeyEventType.KeyDown) {
                    shortcutFor(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)?.let { shortcutActions.run(it); return@Window true }
                }
                false
            },
        ) {
            // The system's text size, re-read when the window comes back from Settings.
            androidx.compose.runtime.DisposableEffect(window) {
                val listener = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { mainWindow.systemTextScale = WindowsTextScale.read() }
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) {}
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WINDOW.w, MIN_WINDOW.h)
                mainWindow.front = { window.toFront(); window.requestFocus() }
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
            val theme = core.themeSettings.observe()
            // L5 — the bar is the title bar; the scaffold installs it once it knows its scale.
            val titleBarInstaller = remember(window) { DesktopTitleBarInstaller(window) }
            CompositionLocalProvider(LocalTitleBarInstaller provides titleBarInstaller) {
            TendrilTheme(register = theme.register, dark = theme.mode.resolveDark(), typeface = theme.typeface) {
                CompositionLocalProvider(LocalSystemTextScale provides mainWindow.systemTextScale) {
                    App(core, orchestrator, folderManager, switcher, treeState, shortcuts, shortcutActions, navState, popOuts, mainWindow, hotkey,
                        // §9.7 — the folder merge is the one write path here that does not
                        // pass the EntryScheduleCoordinator, so it re-plans explicitly.
                        rearmReminders = { scheduler.replan() })
                }
            }
            }
        }
        // B§13.6 #6 — one window per popped-out page, after the main one.
        PopOutWindows(core, popOuts, popOutRegistry, mainWindow)
        // B§13.6 #7 — the icon, its three verbs, and the toasts' home. A click on a toast (or a
        // double-click on the icon) is `onAction`: the window comes back on the tab the last
        // firing belongs to.
        if (isTraySupported) {
            val chordLabel = shortcuts.quickAddChordLabel
            Tray(
                icon = painterResource("tendril_tray.png"),
                state = trayState,
                tooltip = "Tendril",
                onAction = {
                    showMain()
                    scheduler.lastFired?.let { f -> navState.switchTab(if (f.isEvent) WorkbenchDestination.CALENDAR else WorkbenchDestination.TASKS_HABITS) }
                },
                menu = {
                    Item("Open Tendril", onClick = showMain)
                    // Two spaces, not a `MenuShortcut`: AWT's would register a second accelerator.
                    Item("Quick add…  $chordLabel", onClick = { quickAdd.open = true })
                    Separator()
                    Item("Quit", onClick = { hotkey.unbind(); exitApplication() })
                },
            )
        }
        QuickAddWindow(core, quickAdd, mainWindow)
        }
    }
}

/** §0.10 item 21 — every thread's last resort: append the trace, keep running. */
private fun installCrashLog(file: File) {
    Thread.setDefaultUncaughtExceptionHandler { t, e -> logCrash(file, t, e) }
}

private fun logCrash(file: File, thread: Thread, e: Throwable) {
    runCatching {
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        file.parentFile?.mkdirs()
        file.appendText("${Instant.now()} [${thread.name}] $trace\n", Charsets.UTF_8)
    }
    System.err.println("Tendril: uncaught ${e::class.simpleName} on ${thread.name} — logged to ${file.path}")
}

@Composable
private fun App(core: WorkbenchCore, orchestrator: SnapshotSyncOrchestrator, folderManager: DesktopSyncFolderManager, switcher: SwitcherState, treeState: PagesTreeState, shortcuts: ShortcutsState, shortcutActions: ShortcutActions, navState: WorkbenchNavState, popOuts: PopOuts, mainWindow: MainWindowActions, hotkey: GlobalHotkey,
    /** §9.7 — passed down to [SyncBar]; see the parameter there for why it is required. */
    rearmReminders: () -> Unit) {
    // B§13.6 #6 — the pop-outs draw at this window's scale: its shorter side, in the platform's dp.
    val mainDensity = androidx.compose.ui.platform.LocalDensity.current
    val mainSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    mainWindow.shorterSideDp = minOf(mainSize.width, mainSize.height) / mainDensity.density
    // The dispatcher is a composition local of the window's content, so the key input can only be
    // attached from inside it; the key event itself arrives at the window, outside. Hence the
    // input is built in `main` and joined here.
    // Compose Multiplatform doesn't supply a default ViewModelStoreOwner outside NavHost (which
    // this app's hand-rolled nav doesn't use — see WorkbenchNavState's doc comment) — one
    // application-lifetime owner, provided once here, is what the ported screens' viewModel()
    // calls resolve against.
    val viewModelStoreOwner = remember { DesktopViewModelStoreOwner() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
        Column(modifier = Modifier.fillMaxSize()) {
            WorkbenchScaffold(
                core = core,
                navState = navState,
                popOuts = popOuts,
                switcher = switcher,
                treeState = treeState,
                shortcuts = shortcuts,
                shortcutActions = shortcutActions,
                // §0.8 step 6a — the shared Calendar. Google Calendar sync is Play Services, so the
                // settings slot says so; B§13.6 #7 — the bell is here now: reminders are toasts.
                calendarContent = { onOpenPage ->
                    CalendarScreen(
                        core = core,
                        onOpenPage = onOpenPage,
                        settingsSheet = { onDismiss -> DesktopCalendarSettingsSheet(core, onDismiss) },
                        reminderSheet = { entry, onDismiss -> ReminderSheet(core = core, entry = entry, onDismiss = onDismiss) },
                    )
                },
                // §0.8 step 7a — shared. No alarms here, so no bell. 14g·3 — the switches are the
                // store's (`show_urgency`, `show_habit_streaks`), set in this pane's Tasks section.
                tasksHabitsContent = { onOpenReview, quickAddRequested, onQuickAddConsumed ->
                    TasksHabitsScreen(
                        core = core,
                        onOpenReview = onOpenReview,
                        quickAddRequested = quickAddRequested,
                        onQuickAddConsumed = onQuickAddConsumed,
                        showUrgency = core.taskSettings.observeShowUrgency(),
                        showStreaks = core.taskSettings.observeShowHabitStreaks(),
                        // B§13.6 #7 — the shared sheet; the desktop fires them as Windows toasts.
                        reminderSheet = { entry, onDismiss -> ReminderSheet(core = core, entry = entry, onDismiss = onDismiss) },
                        // 14f·1 (§0.10 item 13) — the Trash sheets are shared now, so the desktop
                        // has its Trash button.
                        entryTrashSheet = { onDismiss -> EntryTrashSheet(core = core, onDismiss = onDismiss) },
                        habitTrashSheet = { onDismiss -> HabitTrashSheet(core = core, onDismiss = onDismiss) },
                    )
                },
                // §0.6.15 — the last stand-in gone: Settings holds the Claude section; 14a moved
                // the sync folder controls here too, off the top of every screen.
                settingsContent = { DesktopSettingsScreen(core, syncSection = { SyncBar(orchestrator, folderManager, rearmReminders = rearmReminders) }, onShowShortcuts = { shortcuts.open = true }, hotkeyError = hotkey.error) },
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
                "Google Calendar sync is Android-only — it needs Play Services. Reminders arrive as Windows notifications from the notification-area icon.",
                style = MaterialTheme.typography.body,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("Calendar (.ics)", style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Every task and event as an iCalendar file any calendar app opens; importing one brings its events and to-dos in, updating what came from Tendril before.",
                style = MaterialTheme.typography.description,
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
                            "Imported ${result.created} new, ${result.updated} updated" + if (result.skipped > 0) ", ${result.skipped} skipped" else "" + if (result.kept > 0) ", ${result.kept} unchanged" else ""
                        }.getOrElse { it.message ?: "Import failed." }
                    }
                }) { Text("Import .ics") }
            }
            status?.let { Text(it, style = MaterialTheme.typography.description, modifier = Modifier.padding(top = 8.dp)) }
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
internal fun SyncBar(
    orchestrator: SnapshotSyncOrchestrator,
    folderManager: DesktopSyncFolderManager,
    /**
     * §9.7's re-plan, for the one write path on this side that does not pass the
     * [EntryScheduleCoordinator]: the folder merge. Required rather than defaulted — a no-op
     * default would compile here and at the next call site someone adds, and the bug it hides is
     * silent (a reminder that simply never fires). The same argument Android's `PortableArchive`
     * makes for its own `rearmAlarms`.
     */
    rearmReminders: () -> Unit,
) {
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
                style = MaterialTheme.typography.body,
            )
        }
        TextButton(onClick = {
            scope.launch {
                val chosen = withContext(Dispatchers.IO) { pickDirectory() }
                if (chosen != null) folderManager.setFolder(chosen)
            }
        }) { Text("Choose folder…") }

        // This is the key to every snapshot in the folder, typed in whatever room the desktop
        // happens to be in; the desktop's 36 dp field (L6). Android's two passphrase fields
        // also offer a reveal toggle; this one doesn't yet.
        TendrilField(
            value = passphrase,
            onValueChange = { passphrase = it },
            placeholder = "Passphrase (optional)",
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
                    // Everything a pass decides now lives in `runSyncPass` (audit 1.17), which
                    // is what makes the re-plan reachable by a test: this lambda holds no branch
                    // of its own, only the two pieces of state the bar draws from. The `finally`
                    // stays here because `syncing` is this composable's, and without it one
                    // throw left the button disabled for the rest of the session with nothing on
                    // screen saying why.
                    try {
                        syncError = runSyncPass(
                            orchestrator = orchestrator,
                            store = DesktopFileSyncFileStore(path),
                            passphrase = passphrase.ifBlank { null },
                            rearmReminders = rearmReminders,
                        )
                    } finally {
                        syncing = false
                    }
                }
            },
        ) { Text(if (syncing) "Syncing…" else "Sync now") }
        }
        // Audit 1.5 — one line, not two. This block was emitted twice: once here inside the
        // Column and once again as a sibling after it closed, so every sync error was drawn
        // twice, one under the other. Walked 2026-09-22 with a changed passphrase: "24 file(s)
        // couldn't be decrypted — check the passphrase. Nothing was written." appeared on two
        // consecutive lines, identical. The copy inside the Column is the one kept — a `SyncBar`
        // that emits a Column *and* a loose Text as siblings is the accident — and it takes the
        // stray's padding with it so the spacing below the message is unchanged.
        syncError?.let {
            Text(
                it,
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
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
