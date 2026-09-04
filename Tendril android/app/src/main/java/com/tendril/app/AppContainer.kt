package com.tendril.app

import android.content.Context
import com.tendril.app.calendarprovider.CalendarProviderSync
import com.tendril.app.data.TendrilDatabase
import com.tendril.app.data.buildTendrilDatabase
import com.tendril.app.domain.AndroidEntryScheduleCoordinator
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.googlecalendar.GoogleCalendarAuthManager
import com.tendril.app.googlecalendar.GoogleCalendarSyncEngine
import com.tendril.app.notifications.AlarmScheduler
import com.tendril.app.notionimport.NotionImporter
import com.tendril.app.storage.AppLockPreferences
import com.tendril.app.storage.CalendarProviderPreferences
import com.tendril.app.storage.GoogleCalendarPreferences
import com.tendril.app.storage.SecretStore
import com.tendril.app.storage.SyncFolderManager
import com.tendril.app.storage.SyncStatusPreferences
import com.tendril.app.storage.ThemePreferences
import com.tendril.app.sync.PagesSyncEngine
import com.tendril.app.sync.PortableArchive
import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.ui.WorkbenchCore

/**
 * Plain manual DI — no Hilt/Dagger. A handful of screens and two BroadcastReceivers share
 * these singletons; a service-locator this small doesn't earn a DI framework's build-time
 * codegen and learning-curve cost for a single-developer app.
 */
class AppContainer(context: Context) {
    val database: TendrilDatabase = buildTendrilDatabase(context)
    val themePreferences = ThemePreferences(context)
    val syncFolderManager = SyncFolderManager(context)
    val secretStore = SecretStore(context)
    val appLockPreferences = AppLockPreferences(context)
    val syncStatusPreferences = SyncStatusPreferences(context)
    val alarmScheduler = AlarmScheduler(context, database.reminderDao())
    val calendarProviderPreferences = CalendarProviderPreferences(context)
    val calendarProviderSync = CalendarProviderSync(context, database.entryDao(), calendarProviderPreferences)
    val entryScheduleCoordinator = AndroidEntryScheduleCoordinator(alarmScheduler, calendarProviderSync)
    val resolveEntryUseCase = ResolveEntryUseCase(database.entryDao(), database.entryCompletionDao(), entryScheduleCoordinator)
    val pageContentRepository = PageContentRepository(database.blockDao(), database.pageFtsDao())
    val purgeRegistry = PurgeRegistry(
        database.purgedRecordDao(), database.pageDao(), database.entryDao(), database.habitDao(),
        entryScheduleCoordinator,
    )
    val pagesSyncEngine = PagesSyncEngine(
        database.pageDao(), database.blockDao(), database.tagDao(), database.pageDatabaseDao(),
        database.propertyDao(), database.propertyValueDao(), database.pageDatabaseViewDao(),
        database.pageCanvasDao(), database.canvasNodeDao(), database.canvasEdgeDao(),
        database.pageRelationDao(), purgeRegistry, pageContentRepository,
    )
    val snapshotSyncOrchestrator = SnapshotSyncOrchestrator(
        database.entryDao(), database.habitDao(), database.pageDao(), pagesSyncEngine, purgeRegistry,
    )
    val portableArchive = PortableArchive(
        context, database.entryDao(), database.habitDao(), database.pageDao(),
        purgeRegistry, pagesSyncEngine,
    )
    val databaseSyncManager = DatabaseSyncManager(
        database.pageDao(), database.pageDatabaseDao(), database.propertyValueDao(),
        database.entryDao(), database.entryCompletionDao(), resolveEntryUseCase,
    )
    val templateManager = TemplateManager(database.pageDao(), database.blockDao(), database.pageDatabaseDao(), database.propertyDao())
    val notionImporter = NotionImporter(
        context, database.pageDao(), database.blockDao(), database.pageDatabaseDao(),
        database.propertyDao(), database.propertyValueDao(), pageContentRepository,
    )
    val checkInHabitUseCase = CheckInHabitUseCase(database.habitDao())
    val googleCalendarPreferences = GoogleCalendarPreferences(context)
    val googleCalendarAuthManager = GoogleCalendarAuthManager(context, googleCalendarPreferences)
    val googleCalendarSyncEngine = GoogleCalendarSyncEngine(
        database.entryDao(), googleCalendarAuthManager, googleCalendarPreferences, entryScheduleCoordinator,
    )
    val viewLockState = ViewLockState()
    val checkboxOnlyState = CheckboxOnlyState()

    // Milestone 3 (tendril-windows-spec.md §6 step 3) — the slice of this container the ported
    // Workbench UI (nav shell, Pages, PageDetail, PageDatabase — now in `shared`) depends on.
    val workbenchCore = WorkbenchCore(
        database, databaseSyncManager, templateManager, viewLockState, checkboxOnlyState,
        resolveEntryUseCase, entryScheduleCoordinator, pageContentRepository, purgeRegistry,
    )

    companion object {
        fun from(context: Context): AppContainer =
            (context.applicationContext as TendrilApp).container
    }
}
