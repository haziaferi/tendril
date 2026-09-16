package com.tendril.desktopapp

import com.tendril.app.data.TendrilDatabase
import com.tendril.app.sync.DesktopLocalImageStore
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.data.prefs.FileAiKeyStore
import com.tendril.app.data.prefs.PropertiesKeyValueStore
import com.tendril.app.ui.WorkbenchCore

/**
 * Desktop's counterpart to Android's `AppContainer` (Milestone 3, tendril-windows-spec.md §6
 * step 3) — builds only the pieces the ported Workbench UI needs, from desktop's own database.
 * [scheduler] is the [EntryScheduleCoordinator] every write reports to: since B§13.6 #7 the
 * [DesktopReminderScheduler] (Windows toasts from the notification-area icon), which replaced a
 * no-op — Calendar Provider sync stays Android's (§1).
 */
class DesktopAppContainer(database: TendrilDatabase, scheduler: EntryScheduleCoordinator) {
    val workbenchCore: WorkbenchCore
    val purgeRegistry = PurgeRegistry(
        database.purgedRecordDao(), database.pageDao(), database.entryDao(), database.habitDao(),
        database.propertyDao(), scheduler,
    )

    init {
        val resolveEntryUseCase = ResolveEntryUseCase(database.entryDao(), database.entryCompletionDao(), scheduler)
        val pageContentRepository = PageContentRepository(database.pageDao(), database.blockDao(), database.pageFtsDao())
        val databaseSyncManager = DatabaseSyncManager(
            database.pageDao(), database.pageDatabaseDao(), database.propertyValueDao(),
            database.entryDao(), database.entryCompletionDao(), resolveEntryUseCase,
        )
        val templateManager = TemplateManager(database.pageDao(), database.blockDao(), database.pageDatabaseDao(), database.propertyDao())
        workbenchCore = WorkbenchCore(
            database, databaseSyncManager, templateManager, ViewLockState(), CheckboxOnlyState(),
            resolveEntryUseCase, scheduler, pageContentRepository, purgeRegistry,
            DesktopLocalImageStore(java.io.File(System.getProperty("user.home"), ".tendril-desktop-dev/images")),
            // §0.10 item 12 — one flat file beside the database.
            PropertiesKeyValueStore(java.io.File(System.getProperty("user.home"), ".tendril-desktop-dev/prefs.properties")),
            // §0.6.15 — the key in a file of its own, never the .properties beside it.
            FileAiKeyStore(java.io.File(System.getProperty("user.home"), ".tendril-desktop-dev/anthropic.key")),
        )
    }
}
