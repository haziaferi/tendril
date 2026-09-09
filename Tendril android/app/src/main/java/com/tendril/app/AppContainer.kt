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
import com.tendril.app.sync.SyncCoordinator
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
    val entryScheduleCoordinator =
        AndroidEntryScheduleCoordinator(alarmScheduler, calendarProviderSync, database.entryDao())
    val resolveEntryUseCase = ResolveEntryUseCase(database.entryDao(), database.entryCompletionDao(), entryScheduleCoordinator)
    val pageContentRepository = PageContentRepository(database.blockDao(), database.pageFtsDao())
    val purgeRegistry = PurgeRegistry(
        database.purgedRecordDao(), database.pageDao(), database.entryDao(), database.habitDao(),
        database.propertyDao(), entryScheduleCoordinator,
    )
    val pagesSyncEngine = PagesSyncEngine(
        database.pageDao(), database.blockDao(), database.tagDao(), database.pageDatabaseDao(),
        database.propertyDao(), database.propertyValueDao(), database.pageDatabaseViewDao(),
        database.pageCanvasDao(), database.canvasNodeDao(), database.canvasEdgeDao(),
        database.pageRelationDao(), purgeRegistry, pageContentRepository,
    )
    val snapshotSyncOrchestrator = SnapshotSyncOrchestrator(
        database.entryDao(), database.habitDao(), database.pageDao(),
        database.reminderDao(), database.entryCompletionDao(), pagesSyncEngine, purgeRegistry,
    )
    /** §3.1.2's View-Only toggle. Declared ahead of [portableArchive] because the archive now
     * takes it: the lock is absolute and it covers Settings, so import and restore refuse at
     * the class rather than only at the two Settings buttons. */
    val viewLockState = ViewLockState()
    val portableArchive = PortableArchive(
        context, database.entryDao(), database.habitDao(), database.pageDao(),
        purgeRegistry, pagesSyncEngine,
        // §9.4.2 — one passphrase covers both surfaces: the continuous sync folder and a
        // `.tendril` package. "Off" is simply no passphrase set.
        passphrase = { secretStore.syncPassphrase.value },
        viewLockState = viewLockState,
    )
    /** §9.4's sync triggers — lifecycle and the Settings button both run through this one
     * place, so they can't overlap and a failure has somewhere to be reported from. */
    val syncCoordinator = SyncCoordinator(
        context, syncFolderManager, snapshotSyncOrchestrator, secretStore, syncStatusPreferences,
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
    // `by lazy`, not an eager val: GoogleCalendarAuthManager's constructor calls
    // Identity.getAuthorizationClient(...), so an eager one built a Play Services
    // authorization client on every cold start whether or not Google Calendar had ever been
    // connected. No network call was made by that — but §3.5 states the stronger property
    // outright ("no client/library is constructed at startup"), and this is what makes it true.
    val googleCalendarAuthManager by lazy { GoogleCalendarAuthManager(context, googleCalendarPreferences) }
    // Lazy for the same reason — an eager engine forces the auth manager above, which would
    // put the Play Services client straight back into the startup path.
    val googleCalendarSyncEngine by lazy {
        GoogleCalendarSyncEngine(
            database.entryDao(), googleCalendarAuthManager, googleCalendarPreferences, entryScheduleCoordinator,
        )
    }
    // audit 4.3 — checkbox-only mode draws over the keyguard, so it must refuse to turn on at
    // all when App Lock is the thing standing in front of the app.
    val checkboxOnlyState = CheckboxOnlyState { appLockPreferences.enabled.value }

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
