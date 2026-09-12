package com.tendril.desktopapp

import com.tendril.app.data.TendrilDatabase
import com.tendril.app.sync.DesktopLocalImageStore
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.data.prefs.PropertiesKeyValueStore
import com.tendril.app.ui.WorkbenchCore

/**
 * Desktop's counterpart to Android's `AppContainer` (Milestone 3, tendril-windows-spec.md §6
 * step 3) — builds only the pieces the ported Workbench UI needs, from desktop's own database.
 * `entryScheduleCoordinator` is [NoOpEntryScheduleCoordinator]: alarms/Calendar Provider sync
 * are Android platform APIs with no desktop analog (§1), and nothing ported this pass needs a
 * real one — Row deadline/recurrence edits still persist to the database either way.
 */
class DesktopAppContainer(database: TendrilDatabase) {
    val workbenchCore: WorkbenchCore
    val purgeRegistry = PurgeRegistry(
        database.purgedRecordDao(), database.pageDao(), database.entryDao(), database.habitDao(),
        database.propertyDao(), NoOpEntryScheduleCoordinator,
    )

    init {
        val resolveEntryUseCase = ResolveEntryUseCase(database.entryDao(), database.entryCompletionDao(), NoOpEntryScheduleCoordinator)
        val pageContentRepository = PageContentRepository(database.pageDao(), database.blockDao(), database.pageFtsDao())
        val databaseSyncManager = DatabaseSyncManager(
            database.pageDao(), database.pageDatabaseDao(), database.propertyValueDao(),
            database.entryDao(), database.entryCompletionDao(), resolveEntryUseCase,
        )
        val templateManager = TemplateManager(database.pageDao(), database.blockDao(), database.pageDatabaseDao(), database.propertyDao())
        workbenchCore = WorkbenchCore(
            database, databaseSyncManager, templateManager, ViewLockState(), CheckboxOnlyState(),
            resolveEntryUseCase, NoOpEntryScheduleCoordinator, pageContentRepository, purgeRegistry,
            DesktopLocalImageStore(java.io.File(System.getProperty("user.home"), ".tendril-desktop-dev/images")),
            // §0.10 item 12 — one flat file beside the database.
            PropertiesKeyValueStore(java.io.File(System.getProperty("user.home"), ".tendril-desktop-dev/prefs.properties")),
        )
    }
}
