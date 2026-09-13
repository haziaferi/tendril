package com.tendril.app.ui

import com.tendril.app.data.TendrilDatabase
import com.tendril.app.data.prefs.AiKeyStore
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.EntryEditor
import com.tendril.app.domain.ics.IcsImporter
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.LabelMembership
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.track.TimeTracker
import com.tendril.app.domain.review.Review
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
    /** §0.10 item 12 — device preferences. Required, not defaulted: a platform that forgot it
     * would silently forget every setting on restart, which is the bug this closes. */
    val keyValueStore: KeyValueStore,
    /** §0.6.15 — the Anthropic key's home on this platform; a secret, so never [keyValueStore].
     * Required for the same reason: a platform that forgot it would hide the feature silently. */
    val aiKeyStore: AiKeyStore,
) {
    /** §0.6.8 — built from what is already here rather than passed in, so the two containers
     * need no change; [LabelMembership] holds no state of its own. */
    /** §0.6.6 — a habit's check-in log; derived here since §0.8 step 7a moved the screen. Android's
     * `AppContainer` keeps its own instance for the widget and notification paths. */
    val checkInHabitUseCase: CheckInHabitUseCase by lazy { CheckInHabitUseCase(database.habitDao(), database.habitCompletionDao()) }

    /** §0.8 step 6e — `.ics` in; derived like the rest. Out is [com.tendril.app.domain.ics.IcsWriter], pure. */
    val icsImporter: IcsImporter by lazy { IcsImporter(database.entryDao(), entryScheduleCoordinator) }

    /** §0.8 step 6b — the one edit path for a stored Entry; derived like [labelMembership]. */
    val entryEditor: EntryEditor by lazy { EntryEditor(database.entryDao(), entryScheduleCoordinator) }

    /** §0.6.5 / step 7c — the one start/stop funnel; derived like the rest. Android's
     * `AppContainer` builds its own on the same DAO for the notification's Stop action. */
    val timeTracker: TimeTracker by lazy { TimeTracker(database.timeLogDao()) }

    /** §0.6.13 — a page's kept bodies; derived like the rest. Stateless, so the sync engine's
     * own instance (built in each container) and this one are the same thing. */
    val pageHistory: PageHistory by lazy { PageHistory(database.pageDao(), database.blockDao(), database.pageRevisionDao()) }

    /** §0.6.11 — the weekly walk; derived like the rest, over DAOs that already exist. */
    val review: Review by lazy {
        Review(
            database.pageDao(), database.pageDatabaseDao(), database.entryDao(), database.entryCompletionDao(),
            database.habitCompletionDao(), database.timeLogDao(), entryEditor, resolveEntryUseCase, keyValueStore,
        )
    }

    val labelMembership: LabelMembership by lazy {
        LabelMembership(
            database.pageDao(), database.pageDatabaseDao(), database.labelDao(), database.entryDao(),
            databaseSyncManager, resolveEntryUseCase,
        )
    }
}
