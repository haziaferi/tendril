package com.tendril.app.ui

import com.tendril.app.data.TendrilDatabase
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState

/**
 * Milestone 3 (tendril-windows-spec.md §6 step 3) — the slice of Android's `AppContainer` that
 * the ported Workbench UI (nav shell, Pages, PageDetail, PageDatabase) actually depends on.
 * `AppContainer` itself can't move here: it also builds Context-only Android services
 * (`AlarmScheduler`, `CalendarProviderSync`, `NotionImporter`, App Lock prefs) that have no
 * desktop equivalent and back screens this pass doesn't port. This is plain grouping of pieces
 * already living in `shared` since Milestone 1 — not a DI framework, matching `AppContainer`'s
 * own "doesn't earn a DI framework's cost" reasoning. Android's `AppContainer` and
 * `Tendril windows`'s `DesktopAppContainer` each construct one from their own database;
 * desktop's `entryScheduleCoordinator` is a no-op (see `NoOpEntryScheduleCoordinator` there) —
 * alarms/Calendar Provider sync have no desktop analog (tendril-windows-spec.md §1).
 */
class WorkbenchCore(
    val database: TendrilDatabase,
    val databaseSyncManager: DatabaseSyncManager,
    val templateManager: TemplateManager,
    val viewLockState: ViewLockState,
    val checkboxOnlyState: CheckboxOnlyState,
    val resolveEntryUseCase: ResolveEntryUseCase,
    val entryScheduleCoordinator: EntryScheduleCoordinator,
    val pageContentRepository: PageContentRepository,
    val purgeRegistry: PurgeRegistry,
    /** §3.1.1 / P2 — where a chosen image is copied to. The same store §9.4's sync writes
     * fetched images into, so an image inserted here and one that arrived from a peer end up
     * indistinguishable, which is what makes a round trip work. */
    val localImages: com.tendril.app.sync.LocalImageStore,
)
