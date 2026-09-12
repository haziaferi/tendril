package com.tendril.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.tendril.app.AppContainer
import com.tendril.app.applock.showAppUnlockPrompt
import com.tendril.app.ui.calendar.CalendarScreen
import com.tendril.app.ui.reminders.ReminderSheet
import com.tendril.app.ui.trash.HabitTrashSheet
import com.tendril.app.ui.trash.EntryTrashSheet
import com.tendril.app.ui.calendar.CalendarSettingsSheet
import com.tendril.app.ui.roadmap.RoadMapScreen
import com.tendril.app.ui.settings.SettingsScreen
import com.tendril.app.ui.taskshabits.TasksHabitsScreen

/**
 * Android's call site for the shared `WorkbenchScaffold` (Milestone 3,
 * tendril-windows-spec.md §6 step 3). Holds everything about the nav shell that's genuinely
 * Android-only and couldn't move to `shared`:
 *  - the `Activity` window-flag calls for checkbox-only's lock-screen bypass (§3.1.2) and the
 *    `BiometricPrompt` unlock (`showAppUnlockPrompt`) for turning it back off — both threaded
 *    into the shared composable as nullable callbacks rather than `expect`/`actual`, since only
 *    Android ever supplies a real implementation (App Lock has no desktop analog, §1);
 *  - a `BackHandler` for the system back gesture/button, replacing what Navigation Compose used
 *    to provide for free — the hand-rolled `WorkbenchNavState` (see its own doc comment for why
 *    it isn't Navigation Compose) needs this wired explicitly;
 *  - the four screens this pass doesn't port (Calendar/Tasks&Habits/Road Map/Settings), supplied
 *    as real composables through the shared scaffold's slot params. Canvas used to be a fifth slot;
 *    since 2026-09-11 it is shared code and the scaffold routes to it itself (§0.6.10).
 */
@Composable
fun AndroidWorkbenchScaffold(container: AppContainer) {
    val navState = remember { WorkbenchNavState() }
    val activity = LocalActivity.current as? FragmentActivity

    BackHandler(enabled = navState.canGoBack) { navState.back() }

    WorkbenchScaffold(
        core = container.workbenchCore,
        navState = navState,
        onCheckboxOnlyWindowFlags = { active ->
            activity?.setShowWhenLocked(active)
            activity?.setTurnScreenOn(active)
        },
        onCheckboxOnlyUnlockRequest = { onResult ->
            activity?.let { showAppUnlockPrompt(it, onResult) } ?: onResult(false)
        },
        // §0.8 step 6a — the Calendar is shared; Android supplies the two surfaces only it has.
        calendarContent = { onOpenPage ->
            CalendarScreen(
                core = container.workbenchCore,
                onOpenPage = onOpenPage,
                settingsSheet = { onDismiss ->
                    CalendarSettingsSheet(
                        authManager = container.googleCalendarAuthManager,
                        syncEngine = container.googleCalendarSyncEngine,
                        preferences = container.googleCalendarPreferences,
                        onDismiss = onDismiss,
                    )
                },
                reminderSheet = { entry, onDismiss -> ReminderSheet(container = container, entry = entry, onDismiss = onDismiss) },
                showImportant = container.taskPreferences.showImportance.collectAsState().value,
            )
        },
        // §0.8 step 7a — Tasks & Habits is shared; Android supplies the switches and its sheets.
        tasksHabitsContent = { onOpenReview ->
            TasksHabitsScreen(
                core = container.workbenchCore,
                onOpenReview = onOpenReview,
                showImportance = container.taskPreferences.showImportance.collectAsState().value,
                showStreaks = container.taskPreferences.showHabitStreaks.collectAsState().value,
                reminderSheet = { entry, onDismiss -> ReminderSheet(container = container, entry = entry, onDismiss = onDismiss) },
                entryTrashSheet = { onDismiss -> EntryTrashSheet(container = container, onDismiss = onDismiss) },
                habitTrashSheet = { onDismiss -> HabitTrashSheet(container = container, onDismiss = onDismiss) },
            )
        },
        roadMapContent = { onOpenPage -> RoadMapScreen(container = container, onOpenPage = onOpenPage) },
        settingsContent = {
            SettingsScreen(
                themePreferences = container.themePreferences,
                syncFolderManager = container.syncFolderManager,
                secretStore = container.secretStore,
                appLockPreferences = container.appLockPreferences,
                taskPreferences = container.taskPreferences,
                syncStatusPreferences = container.syncStatusPreferences,
                syncCoordinator = container.syncCoordinator,
                portableArchive = container.portableArchive,
                markdownExporter = container.markdownExporter,
                entryDao = container.database.entryDao(),
                icsImporter = container.workbenchCore.icsImporter,
                notionImporter = container.notionImporter,
                databaseSyncManager = container.databaseSyncManager,
            )
        },
    )
}
