# Tendril Windows — Desktop Companion Specification

Graduated out of `tendril-spec.md` §12 on 2026-08-30, once Milestone 1 (§5 below) turned "exploratory
reasoning" into real, running code — §4 below is the actual documentation-structure decision that set
the graduation condition this split fulfills. This file's own history starts at that split; everything
before it lived in the parent document's Revision Log (2026-08-29 and 2026-08-30 entries, "Desktop
Companion" / "§12" rows) and isn't repeated here.

## Revision Log

| Date | Summary | Sections touched |
|---|---|---|
| 2026-08-30 | File created — §12.1–§12.5 of `tendril-spec.md` moved here verbatim (renumbered §1–§6), per §4's graduation trigger. No content changed in the move; see `tendril-spec.md`'s Revision Log for this content's full history before the split. | all |
| 2026-08-30 (later same day) | Milestone 2 (folder-sync-on-desktop) implemented: new `SyncFileStore` interface + `SnapshotSyncOrchestrator` (ported from Android's `SnapshotSyncManager`) in a new `shared/jvmCommon` intermediate source set, platform implementations `AndroidSafSyncFileStore`/`DesktopFileSyncFileStore`, and a folder-picker/passphrase/"Sync now" UI in `Tendril windows`. §3's gate re-affirmed as still not triggered (desktop stays read-only this milestone too). Per §0's anti-drift rule, `tendril-spec.md`'s Revision Log has a matching entry. See §7. | §3, §6, §7 |
| 2026-09-05 (re-key) | Anti-drift entry (§0): `SnapshotSyncOrchestrator` (`shared/jvmCommon`) gains `rekey(store, currentPassphrase, newPassphrase)` and a `RekeyOutcome`, which merge under the current passphrase and refuse to rewrite the folder if anything failed to decrypt — re-encrypting can only preserve what the device can read. The desktop does not surface this yet: its passphrase box is session-only, so it has no stored passphrase to change and no Settings screen to offer the action from. The shared operation is available to it whenever that changes. No desktop behaviour changed this pass. | §1, §6, §7 |
| 2026-09-05 (integration) | Anti-drift entry (§0): the recurrence branch and the audit branch were merged, and the resolution changed `shared\` again. ~~`PagesSyncEngine` gains recurrence-aware queries and loses `getInRange`/`observeOnDate` (a date-window query never returns a recurring series, anchored as it is at its first occurrence).~~ (**Corrected 2026-09-06:** the class is `EntryDao`, not `PagesSyncEngine`. What the integration actually did was *keep* the recurrence-aware queries the 2026-09-04 (later same day) row below already recorded — `getAllDated`/`getExceptionsOf`/`getAllExceptions` — and drop `getInRange`/`observeOnDate`, the reasoning being unchanged: a date-window query never returns a recurring series, anchored as it is at its first occurrence, so expansion replaces both. `PagesSyncEngine` merges page snapshots and was never involved. Struck rather than quietly retyped because this row and `tendril-spec.md`'s row of the same date are two accounts of one change, which §0's rule exists to keep in agreement — and for a day they named different classes. The rule produced both rows on time and still did not catch that; an audit did.) `SnapshotSyncOrchestrator`'s `readAndMerge` returns a `SnapshotMergeResult` again — the count of files it could not decrypt — with the tally carried on the same value as the folder key rather than threaded separately; the desktop's "Sync now" reads it to refuse a write over a folder it could not read, alongside the write guard already there. `PageSearchHit` widens to carry title and icon. **Room schema is v8** (see `tendril-spec.md` for why neither branch's number survived). Desktop behaviour is otherwise unchanged; `Tendril windows` compiles and CI now assembles all three projects. | §1, §6, §7 |
| 2026-09-05 | Anti-drift entry (§0): a large pass over `shared\` landed on `main` — full reasoning in `tendril-spec.md`'s Revision Log, summarised here because the shared core changed under both consumers. `SnapshotSyncOrchestrator` and `SnapshotEncryption` (both `shared/jvmCommon`) gained a per-folder PBKDF2 salt in `sync_meta.json`, a refusal to trust plaintext inside a folder that declares itself encrypted, and a refusal to overwrite an encrypted folder the supplied key cannot open. `PagesSyncEngine` (`shared/commonMain`) stopped orphaning pages whose parent arrives in a later batch, and now returns the records that *lost* a last-write-wins race so the orchestrator can preserve them beside the winner rather than discarding them. `PurgeRegistry` gained `PurgedKind.HABIT` and a `HabitDao`; `EntryScheduleCoordinator` gained a defaulted `onHabitRemoved` (a no-op on desktop, which has no alarms — §1). New shared domain files: `BlockOutline.kt` (one level of block nesting, drawn on both platforms) and `HabitSchedule.kt`. `CheckboxOnlyState` now takes an injected `appLockEnabled` predicate, defaulted false for desktop. `DesktopAppContainer` updated for the `PurgeRegistry` signature; no desktop behaviour changes otherwise. | §1, §6, §7 |
| 2026-08-30 (later still) | Milestone 3 (Workbench UI port), first slice implemented: theming, the 5-tab nav shell, and Pages/PageDetail/PageDatabase (the actual block editor) moved into `shared/src/commonMain` and now render on both Android and desktop from one implementation, not two. Calendar/Tasks & Habits/Road Map/Settings and the Canvas page kind stay Android-only this pass (out of scope — see §8). §3's gate is now genuinely triggered: desktop originates edits for the first time. Per §0's anti-drift rule, `tendril-spec.md`'s Revision Log has a matching entry. See §8. | §3, §6, §8 |
| 2026-09-04 | Anti-drift-rule entry — a consistency audit of the whole app against both specs. Summary only, since it touches `shared\`; full detail in `tendril-spec.md`'s entry of the same date. What lands in `shared\`: Room's `@Database` `version` bumped 5→6 (three Canvas tables had been added to `entities` without it, and Room throws on an identity-hash mismatch *before* `fallbackToDestructiveMigration` can act, so it crashed on open rather than recreating); `ResolveEntryUseCase` now advances a recurring TASK to the first occurrence **not already past**, keeping §6.2's phase but ending the case where resolving one late left it still overdue, logged a fabricated `EntryCompletion` per catch-up tap, and — via §9.7's past-alarm rule — silently unscheduled it; `SnapshotSyncOrchestrator.readAndMerge` returns a `SnapshotMergeResult` so a caller can refuse to write when the folder holds snapshots it cannot decrypt (it previously merged nothing and then overwrote the folder's only copy under the wrong key — §9.4.2 promises "unreadable," not "destroyed"); `PageFtsDao.search` joins `pages` to exclude trashed rows and had `snippet()`'s arguments off by one position; `HabitSnapshotRecord` carries the undo stash; `EntryDao.getAllSchedulableTasks` is now `getAllSchedulable` and covers EVENTs. **Desktop-side changes**: `Main.kt`'s Sync now honours the new merge result, wraps the call in try/finally per `SyncFileStore`'s documented throwing contract, surfaces the error in the sync bar, and masks the passphrase field. §3's block-level-merge gate is untouched and still open. | §1, §3, §7, §8 |
| 2026-09-04 (later same day) | Anti-drift-rule entry — recurring EVENT expansion. Full reasoning in `tendril-spec.md` §4.1.1 and its Revision Log entry of the same date; summary only, since it adds to `shared\`. New `com.tendril.app.domain.recurrence` package in `shared/src/commonMain`: `RecurrenceSpec` (a hand-rolled RFC5545 subset parser) and `EntryOccurrences` (stored rows plus a date range → one occurrence per covered day, honouring multi-day spans and §4.1's skip/override exception rows). `EntryDao` gains `getAllDated`, `getExceptionsOf` and `getAllExceptions`. **No desktop-side change**: Calendar is one of the four tabs still showing `NotAvailableOnDesktop` (§8's scope boundary), so nothing in `Tendril windows` consumes the expander yet — it is there for when Calendar is ported, and the desktop build only needs to keep compiling against the widened `EntryDao`. §3's block-level-merge gate is unaffected. | §1, §6, §8 |
| 2026-09-04 (last of the day) | Sync now runs from the Android lifecycle rather than only from the Settings button — new `SyncCoordinator`, full detail in `tendril-spec.md` §9.4. **No `shared\` change**, so §0's rule doesn't compel this entry; it is here because the change carries a decision *about desktop*: desktop stays on its explicit "Sync now" button, and the reason is not just the missing lifecycle callbacks but the session-only passphrase (§7) — at launch there is nothing to decrypt an encrypted folder with. Recorded in §1. | §1, §7 |
| 2026-09-04 (last, really) | Anti-drift-rule entry — `.tendril` exports now honour §9.4.2's encryption toggle; full detail in `tendril-spec.md` §9.4.2 and its Revision Log entry of the same date. The `shared\` part is one field: `TendrilManifest` gains `encrypted`, defaulted false so archives written before this still decode. Everything else is in `Tendril android`'s `PortableArchive`, which desktop has no counterpart to — desktop reads and writes the sync folder (§7) but has no portable export/import UI at all. When it gets one it inherits the same constraint noted for automatic sync in §1: the passphrase is session-only, so an export would be unencrypted unless one had been typed that session. | §1, §7 |
| 2026-09-06 (audit corrections, and §0's own breach) | **The breach is recorded as part of the entry, because a rule whose failures go unlogged reads like a rule nobody breaks.** Four commits changed `shared\` after the rows above were written and none of them reached this file, while `tendril-spec.md` got rows for all of them. `0d2a932` (2026-09-05) added `PageDao.touch` and the launchers that carry it, so an edit *inside* a page finally moves `pages.updatedAt` — the change that turned §3's gate from nominal into load-bearing, and the one this file most needed to know about; see §3. `76b072e` and `8679d7f` (2026-09-06) stopped the merge destroying the *local* copy a winning record overwrites, and stopped it unlinking a page's own images while rebuilding blocks. `1f96a70` (2026-09-06) made a deleted column travel as a `PurgedKind.PROPERTY` tombstone rather than as mere absence, which added a `PropertyDao` to `PurgeRegistry`'s constructor and so edited `DesktopAppContainer.kt` directly — a `shared\` change that reached into desktop source and still produced no row here, which is as clear a demonstration as the rule will get of what it is for. `d0f7bd9` (2026-09-04) is the older half of the same story: it put `purgeRegistry` into `WorkbenchCore` and `DesktopAppContainer`, and the 2026-09-05 row above names `PurgeRegistry` only in passing, which is why §8's parameter list was wrong. Full reasoning for all five lives in `tendril-spec.md`'s rows of those dates; no desktop behaviour was changed by this pass, only the record of it. **Also corrected, from an audit of every claim in this file against `shared/src`, `Tendril windows/src` and git history:** the integration row's `PagesSyncEngine`→`EntryDao` misattribution (two files describing one change and naming different classes — exactly what §0 exists to catch, caught here by an audit instead); §4's out-of-scope analogy, whose two cited exclusions were reopened on 2026-09-06; §5's `EntryScheduleCoordinator` method count; §8's `WorkbenchCore` parameter list; and §0's own account of what `shared\` contains, which had not kept up with Milestone 3 moving the UI there. | §0, §3, §4, §5, §8 |
| 2026-09-07 (Canvas written up) | Anti-drift entry (§0): **no code changed in this pass — only the record of it** — but the record that changed is about `shared\`, which is where §0's rule keys, and the missing entry it repairs is itself a §0 breach. The Canvas page kind (`PageKind.CANVAS` and the `PageCanvas`/`CanvasNode`/`CanvasEdge` tables, all in `shared/commonMain`, plus its pass in `PagesSyncEngine`) shipped with no section in either spec and no row in either Revision Log; `tendril-spec.md` §3.4 recorded that breach on 2026-09-04 and said reconstructing the reasoning was beyond what a correction could do. It is now written up as `tendril-spec.md` **§3.7**, with the three entities added to that file's **§4** — per §0, the data model stays documented there and this file points at it rather than restating it. **The one part a desktop reader should not have to follow a pointer for:** the Canvas *surface* is Android-only and `Main.kt` renders `NotAvailableOnDesktop("Canvas")` for it (§8's scope boundary, unchanged), but the entities, the DAOs and the merge pass are all in `shared\` — so this desktop build already reads, merges and re-exports canvas nodes and edges it cannot draw. That is correct and deliberate (a client must never drop what it cannot render, or a sync becomes a data loss), and it means Canvas is not "not on desktop" in the way the four stubbed tabs are: it is invisible here and fully synced here. §3's block-level-merge gate is unaffected — a canvas replaces whole, keyed on the page row like every other page kind. | §0, §8; see `tendril-spec.md` §3.7, §4 |
| 2026-09-11 (§0 added to `tendril-spec.md`) | Anti-drift entry (§0): no code changed. `tendril-spec.md` gained a **§0 Objectives** that is cross-platform by nature — hard constraints, principles, the order of work — and its §0.11 extends this file's rule: any change to that §0 gets a same-day pointer here. Rows that bear on this file directly: §0.1.7 (two platforms, true parity, desktop waits for the shape to settle), §0.5.8 (shared code is the parity mechanism), §0.6.10 (the Canvas UI moves to `shared/` before any spatial feature). | Revision Log |
| 2026-09-11 (Canvas on desktop) | Anti-drift entry (§0): `shared/` gains `ui/canvas/` — `CanvasScreen` and `CanvasViewModel`, moved from the Android app under `tendril-spec.md` §0.6.10. `Main.kt` drops its `NotAvailableOnDesktop("Canvas")` stand-in; the shared `WorkbenchScaffold` routes `PageKind.CANVAS` itself. Desktop now opens a canvas, adds cards, drags and links them (verified on the running preview). The four remaining stand-ins are Calendar, Tasks & Habits, Road Map and Settings. | Revision Log, §8 |
| 2026-09-11 (schema v10) | Anti-drift entry (§0): `shared/` moves to **Room schema v10** — four columns on `entries` and a new `habit_completions` table (`tendril-spec.md` §0.6.4, §0.6.6, `MIGRATION_9_10`); `SnapshotSyncOrchestrator` publishes and merges `habit_completions.json`; `CheckInHabitUseCase` takes a `HabitCompletionDao`; `Main.kt` passes the new DAO. Desktop opens a v9 database through the same migration the phone runs. The Tasks & Habits UI that reads the new fields is Android-only, as that screen always was; the domain half (`Postpone`, `TaskTree`, `HabitPresence`) is shared. | Revision Log |
| 2026-09-11 (schema v11) | Anti-drift entry (§0): `shared/` moves to **Room schema v11** — `page_databases.dueDatePropertyId` (`tendril-spec.md` §0.8 step 2b, `MIGRATION_10_11`); `PageDatabaseSnapshotRecord.dueDatePropertyUid`; a fourth `BindingRole`. The database screens that render and bind it are shared code, so desktop has the binding wherever it has the table. Desktop opened its v10 database through the migration. | Revision Log |
| 2026-09-11 (schema v12, mind map, canvas block) | Anti-drift entry (§0): `shared/` moves to **Room schema v12** (`Block.mindMap`, `MIGRATION_11_12`), gains `BlockType.CANVAS`, `domain/MindMapLayout`, `ui/pages/MindMap.kt` and `CanvasBlock.kt`, and a new dependency, `org.jetbrains.compose.ui:ui-backhandler`. All of it is shared code, so desktop has unlimited depth, the mind map and the canvas block wherever it has the page editor — verified on the running preview. **Desktop gap, `tendril-spec.md` §0.10 item 10:** Escape does not close an armed map; the window has no back dispatcher for CMP's `BackHandler` to reach. | Revision Log |
| 2026-09-12 (desktop Escape) | Closes `tendril-spec.md` §0.10 item 10. Diagnosis from the CMP 1.12 sources: desktop's `BackHandler` registers on the `NavigationEventDispatcher` the skiko `Window` already provides (`DefaultArchitectureComponentsOwner`), but no *input* ever feeds that dispatcher — Android has the system gesture, desktop had nothing. `Main.kt` adds `EscapeBackInput : NavigationEventInput`, attached to the window content's dispatcher and driven from `Window(onPreviewKeyEvent)` on the Escape **release** (the press is swallowed before preview — logged and observed). Preview, not consume, so text fields keep Escape. One explicit dependency, `org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0`, already on the classpath transitively. Verified on the running preview: an armed mind map closes on Escape; Escape in a block's text field with nothing armed changes nothing. | Revision Log |
| 2026-09-12 (Label rename) | Anti-drift entry (§0): `shared/` renames the §3.1.6 types — `Label`, `PageLabel`, `LabelDao` (`TendrilDatabase.labelDao()`), `LabelColors`; `Main.kt`'s `PagesSyncEngine` wiring follows. No table, column, schema version or snapshot key changes; see `tendril-spec.md`'s row of the same date. | Revision Log |
| 2026-09-12 (schema v13, schema on a label) | Anti-drift entry (§0): `shared/` moves to **Room schema v13** (`PageDatabase.labelId`, `labelConfirmed`, `MIGRATION_12_13`), gains `domain/LabelMembership`, `ui/pages/BindLabelSheet.kt`, new queries on `PageDao`/`PageDatabaseDao`/`EntryDao`/`LabelDao`, and `WorkbenchCore.labelMembership` (derived, so `DesktopAppContainer` is unchanged). All shared, so desktop has schema on a label wherever it has the page editor — the whole §0.6.8 acceptance was walked on the running desktop preview, and the v12 → v13 upgrade ran in place on `~/.tendril-desktop-dev/tendril.db`. Two desktop-only observations, not regressions: the page's label picker had an invisible text field until typed into (now carries a placeholder), and `EnableSyncSheet`'s "Turn on" sits below the window until the sheet is expanded from its handle — pre-existing, noted for the desktop parity row. | Revision Log |
| 2026-09-12 (Quick Add parser) | Anti-drift entry (§0): `shared/` gains `domain/QuickAddParser.kt` and `ui/entries/QuickAddPreview.kt`. Desktop has no Calendar or Tasks surface yet, so neither is reachable there until the Calendar moves to `shared/` (`tendril-spec.md` §0.8 step 6a, next). | Revision Log |
| 2026-09-12 (Calendar on desktop) | Anti-drift entry (§0): `shared/` gains `ui/calendar/` — `CalendarScreen` and `CalendarViewModel`, moved from the Android app under `tendril-spec.md` §0.8 step 6a. `Main.kt` drops `NotAvailableOnDesktop("Calendar")`; the screen's two Android-only surfaces are slots, and desktop passes a one-paragraph settings sheet ("Google Calendar sync and reminders are Android-only") and no reminder sheet, so the bell is absent. Verified on the running preview: Day/Week/Month draw, Quick Add creates an event from a line, a weekday series shows on Monday and not Sunday. **The three remaining stand-ins are Tasks & Habits, Road Map and Settings.** | Revision Log, §8 |
| 2026-09-12 (Calendar edit path) | Anti-drift entry (§0): `shared/` gains `domain/EntryEditor.kt` and `ui/entries/EntryEditSheet.kt`, `WorkbenchCore.entryEditor` (derived), and the Calendar's tap-to-edit and Week drag — all shared, all verified on the desktop preview with the mouse (long-press-drag). Two tall sheets (`EntryEditSheet`, `EnableSyncSheet`) open fully expanded so their buttons are on screen in a desktop window (`tendril-spec.md` §0.10 item 11 resolved). Desktop's `showImportant` is false until it has a Settings screen. | Revision Log |
| 2026-09-12 (Agenda, layers) | Anti-drift entry (§0): `shared/` gains the Calendar's Agenda view and layer row, `CalendarLayers`, `PropertyValueDao.observeDateCells` + `DateCell`; `WorkbenchScaffold.calendarContent` now takes `onOpenPage`, and `Main.kt` passes it — a database row's date on the desktop Calendar opens its page (verified on the preview). Desktop has no habits yet (Tasks & Habits is still a stand-in), so the Habits layer is empty there until step 7a. | Revision Log |
| 2026-09-12 (ICS) | Anti-drift entry (§0): `shared/` gains `domain/ics/` (`IcsWriter`, `IcsReader`, `IcsImporter`) and `WorkbenchCore.icsImporter`. Desktop: the Calendar's `···` sheet grows *Export .ics* / *Import .ics* through a `JFileChooser` (save and open), off the UI thread like the sync-folder picker — the first export the desktop build has. Verified on the preview: export to a file, re-import "0 new, 4 updated". | Revision Log |
| 2026-09-12 (Tasks & Habits on desktop) | Anti-drift entry (§0): `shared/` gains `ui/taskshabits/` (`TasksHabitsScreen`, `TasksHabitsViewModel`, `AddDialogs`, `TaskSheets`), moved from the Android app under `tendril-spec.md` §0.8 step 7a, and `EntryScheduleCoordinator.onHabitChanged` (a no-op here). `Main.kt` drops `NotAvailableOnDesktop("Tasks & Habits")` and passes the switches off and the three sheets null — no bell, no Trash button on desktop for now (`tendril-spec.md` §0.10 item 13). **The two remaining stand-ins are Road Map and Settings.** | Revision Log, §8 |
| 2026-09-12 (Plan mode) | Anti-drift entry (§0): `shared/` gains `domain/plan/` and `ui/calendar/PlanView.kt`; the Calendar's Day view has a *Plan* chip on both platforms. One desktop-found fix worth knowing: a child's `onGloballyPositioned` does not fire again as its `verticalScroll` parent scrolls, so a grid measured by its own root bounds drops at the wrong hour — the view measures against the viewport plus `scroll.value` instead. Verified on the preview with the mouse (rail → 10:00, block → 14:15). | Revision Log |
| 2026-09-12 (tracking) | Anti-drift entry (§0): `shared/` gains `data/track/`, `domain/track/` and `ui/track/`; schema v14; `WorkbenchScaffold` draws the running-timer strip above the tabs, so desktop has the whole feature — ▶/■ on rows, the strip, the habit sheet's minutes. What desktop does *not* have is the phone's notification, and needs nothing in its place: the strip is the desktop's "now", and the open row is the timer either way. `Main.kt` passes `timeLogDao()` to the orchestrator. Verified on the preview: task ▶ → strip ticking across Pages/Tasks → habit ▶ switched it → ■ on the strip closed it; two rows in `~/.tendril-desktop-dev/tendril.db`. | Revision Log |
| 2026-09-12 (sheet frame) | Anti-drift entry (§0): `shared/ui/components/TendrilSheet.kt` is now the only caller of `ModalBottomSheet`; every sheet opens fully expanded — the fix §0.10 item 11 made for two sheets now covers all of them on both platforms, `DesktopCalendarSettingsSheet` included, so no desktop-only switch was needed. The bottom room scales with the window (3 %, capped at 48 dp). The wider question — the desktop still wearing the phone's layout — is tendril-spec.md §0.10 item 14. | Revision Log |
| 2026-09-12 (planned vs actual) | Anti-drift entry (§0): `shared/` gains `domain/plan/DayTotals.kt`; `PlanView` takes `logged` spans; the Day header, the Day rows, the Tasks & Habits rows and the habit sheet all read the same functions, so the desktop shows planned-vs-actual with no desktop code. Verified on the preview with seeded rows (a 45 m task at 15:00, a 20 m log): header *Planned 45m · Logged 20m*, row *20m of ~45m*, the strip at 15:00–15:20 beside the block in Plan mode. | Revision Log |
| 2026-09-12 (Review) | Anti-drift entry (§0): `shared/` gains `domain/review/` and `ui/review/`; `WorkbenchRoute.Review`; the `tasksHabitsContent` slot now passes `onOpenReview`, which `Main.kt` forwards — the one desktop line. Schema v15. Verified on the preview: the dot on Tasks, the *Books v12* card → Reviewed (row and page `updatedAt` bumped), a Someday task → Today, "Nothing left to review". Escape closes the screen through the existing back input. | Revision Log |
| 2026-09-12 (KeyValueStore) | Anti-drift entry (§0): `shared/src/desktopMain/.../data/prefs/PropertiesKeyValueStore.kt` — the desktop's preference store, a `.properties` file at `~/.tendril-desktop-dev/prefs.properties`, built by `DesktopAppContainer`. First desktop setting that survives a restart: the Calendar's layer chips. | Revision Log |
| 2026-09-14 (corrupt-file recovery) | `shared/` change, Android-only in effect: `androidMain` gains `KeepFileOnCorruptionDriver` and `openTendrilDatabase` uses it instead of `AndroidSQLiteDriver` (`tendril-spec.md` §9.10, corrected 2026-09-14). The desktop's `BundledSQLiteDriver` has no framework error handler — a non-database file throws at the first statement, the probe fails, the file is set aside — so nothing changes here; `TendrilDatabase.desktop.kt` untouched. | §8 |
| 2026-09-16 (the tray) | Anti-drift: **desktop** — `DesktopReminders.kt` (the `EntryScheduleCoordinator`, replacing `NoOpEntryScheduleCoordinator`, deleted), `GlobalHotkey.kt` (`User32.RegisterHotKey` on a daemon message-loop thread; `net.java.dev.jna:jna-platform:5.6.0` is a direct dependency now — the first non-Compose one, offline from the cache), `QuickAddWindow.kt`, `tendril_tray.png`, `Main.kt`'s `Tray`, `visible`/`onCloseRequest`, the crash handlers; **shared** — `domain/reminders/ReminderFirings.kt`, `domain/QuickAdd.kt`, `ui/reminders/` (moved from Android), `ui/entries/QuickAddField.kt`, `ui/nav/QuickAddChord.kt`, `ui/settings/NotificationAreaSection.kt`. `close_to_tray`, `quick_add_chord` in `prefs.properties`; `crash.log` beside them. §1's *no desktop analog* for `AlarmManager` and §12.1's absent bell are **amended**: reminders are Windows toasts here, the bell is present. Verified: the chord from another app, the popup's three states and focus rules, the bell → the slide-over → two firings by the clock (logged; the toast behind Do-not-disturb), the × with the switch on and off, F1, the settings sheet. Under `gradlew run` a toast's app line reads *Java(TM) Platform SE binary*; the packaged exe gives it *Tendril*. **Not queued:** firings that passed while the app was not running. | §1, §8, §12.1 |
| 2026-09-17 (the desktop audit) | Anti-drift: no code changed. Three critiques over every desktop surface on `main` after the tray — `docs/critiques/desktop-layout-full.md`, `desktop-type-full.md`, `desktop-function-full.md`; the disposition in `tendril-spec.md` §0.10 item 22 (nine High → the fix PR; Med/Low listed by id). Two things this file should know: the register and mode re-solve live on the desktop (verified by native grabs — the computer-use capture of the window was stale for a minute and said otherwise), and the F1 card omits Ctrl+Shift+\\ although `Shortcuts.kt` binds it (F2 there). | Revision Log, §8 |
| 2026-09-17 (the audit's fixes) | Anti-drift: shared throughout — `ui/components/TendrilMenu.kt` (every menu; the profile's row under a pointer), `ui/components/RowControls.kt` (the one-line row's helpers), `ui/nav/FindRequestGate.kt`, `domain/Words.kt`, the type sweep (196 shared + 14 desktop raw-role sites renamed — `Main.kt`, `DesktopSettingsScreen.kt`, `PopOutWindows.kt`, `QuickAddWindow.kt` read the seven styles now); `tools/type_sites.py`, `tools/type_table/`, `tools/tests/` (Python, run by the audit workflow) and `tools/audit.py` rules 13–15. `Main.kt:363`'s Calendar sheet copy loses its file-name citation. Grounds measured live on this machine: Notion, Obsidian, Todoist, TickTick menu rows 29 / 25 / 32 / 36 px against Tendril's 30. `docs/critiques/desktop-audit-fixes.md`. | Revision Log, §8 |
| 2026-09-17 (the Month grid) | Anti-drift: shared throughout — `ui/calendar/MonthGrid.kt`, `OccurrenceChip.kt`, `domain/plan/MonthLayout.kt`; the Calendar tab's Month and a database's Calendar view under a pointer; the phone's dot grid under Touch; `tools/audit.py` rule 16; `tools/type_sites.py` gains the grid's weekday-header rule. Nothing desktop-only. Measured beside Notion's database calendar: rows 149 = pane / 5, chips 22 px at 24, disc 22 (`docs/critiques/month-grid-function.md`). | §8 |
| 2026-09-17 (the Calendar's chrome, the borderless window) | **The toolchain is a JetBrains Runtime** — `build.gradle.kts` asks for vendor JETBRAINS at 21, `gradle.properties` registers Android Studio's `jbr` (21.0.10), `run` and jpackage use it (`javaHome`), so the installed app bundles the runtime with the custom-title-bar service; `org.jetbrains.runtime:jbr-api:1.9.0` from the offline cache. Desktop-only: `TitleBar.kt` (`DesktopTitleBarInstaller`: `JBR.getWindowDecorations()`, the bar's height in the runtime's user-space px, `controls.dark` from the register, the caption insets returned in device px; `markGround` = `forceHitTest(false)`), provided per window in `Main.kt` and `PopOutWindows.kt` through `LocalTitleBarInstaller`. Shared: `ui/nav/TitleBar.kt`, `WorkbenchEnvironment` installs it, `ShellTopBar` / the rail's top / the tree header answer it. **Fallback verified**: the same build on Temurin keeps the OS title bar (an init script pointing `javaHome` at it). Measured: bar 55 px, caption block 141, hour 48, gutter 59 beside Notion Calendar's 46 / 48 / 60 (`docs/critiques/calendar-chrome-function.md`). | §8 |
| 2026-09-17 (the quick switcher as a card) | Shared: `ui/components/CentredCard.kt` (the F1 card's frame extracted — the switcher at 20 % of the window, the F1 card centred; radius 10), `ui/components/TendrilField.kt` (the desktop's 36 dp text field), `ui/switcher/QuickSwitcher.kt` (the card under `RAIL`, the overlay under `BAR`), `domain/SwitcherQuery.kt` (`switcherSections`, `prefixRange`), `PageDao.getRecentlyEdited`, `ui/nav/Shortcuts.kt`'s `chordLabelOf`. Desktop-only: `Main.kt`'s sync passphrase field on `TendrilField` (no `OutlinedTextField` remains in the desktop module). Measured at the user's window: the card's top at 20 %, 558 px wide, field ≈ 35, rows 29 beside Notion Calendar's 678 / 46 / 36 and Obsidian's 699 / 48 / 32 (`docs/critiques/quick-switcher-function.md`). The arrows need real input on the walk: `keybd_event` without the extended-key flag never reaches the field. | §8 |
| 2026-09-17 (the quick-add strip, the tray's clamp) | Shared: `ui/entries/QuickAddField.kt` on `TendrilField` (`fieldHeight`, `inlinePreview`), `QuickAddChips`, `ui/calendar/QuickAddBar.kt` (the find bar's frame), `domain/plan/Tray.kt`'s `trayDrawnWidthDp`, `DayTotals.kt`'s `plannedLabel`, `CalendarScreen` (the drawn width), `WeekGridView` (the header's line). Desktop-only: `QuickAddWindow.kt` passes `fieldHeight = 36.dp` — the popup's field is the switcher's; nothing else desktop-specific. Measured: the strip 44 px (was ≈ 100), the field 28, the popup's 35, the tray 263 px at the user's window (30 %), lanes 78–79 (`docs/critiques/quick-add-strip-function.md`). The global chord in this profile is Ctrl+Shift+Space (`quick_add_chord`), not the default — the walk used it. | §8 |
| 2026-09-17 (the canvas's cards under a pointer) | Shared: `domain/canvas/CanvasView.kt` (`fitToCards`, `contentAtPaneCentre`), `ui/canvas/CanvasScreen.kt` (the bar's *Fit* and `+` on a wide window, selection + ring, badges on hover/selection, the ground's double-click, the discard rule, Delete/Backspace on the focused board), `CanvasViewModel.addTextNode(onInserted)`, `SlideOver`/`CentredCard` popups with `dismissOnBackPress = false`. **Desktop-only, and a fix:** `EscapeBackInput` is **deleted** — `Main.kt`'s and `PopOutWindows.kt`'s Escape key-up dispatch and the `DisposableEffect`s that joined the input to each window's `NavigationEventDispatcher` are gone, because Compose Multiplatform 1.12 feeds the dispatcher itself on the Escape key-down (that is why the 2026-09-12 row only ever saw the release): with both, every Escape went back twice — a sheet's close *and* the page's pop when a frame fell between. Verified after: a slide-over closes on one Esc with the page kept; a two-deep stack pops one level per Esc; a pop-out survives two Escapes at its seed and closes on Ctrl+W; the delete dialog closes on one Esc. Measured: a card 182 × 91 px at the user's window, the ring 3 px, beside Obsidian's 252 × 63 (`docs/critiques/canvas-cards-function.md`). Tool note: a synthetic Delete needs `KEYEVENTF_EXTENDEDKEY` to reach the Compose window. | §8 |
| 2026-09-18 (the design layer) | Shared, nothing desktop-only: `ui/theme/Registers.kt` (`surface2` at 6 % both modes; `third` solved to 4.6, `thirdStrong` and `accentStrong` gone), `Palette.kt`, `Theme.kt` (`TendrilShapes` on `MaterialTheme`; `secondary = accent`), `ui/roadmap/RoadMapScreen.kt` (edges at full alpha, 1 dp), `ui/components/ListKeyboard.kt` (`keyboardFocusRing`, `cursorOnFocus`) and its six sites, `tools/audit.py` rule 17. Walked on Chalk light and Ink dark at the user's window: a hovered tree row ΔE 2.46 on the grab, the mention edge 5.63:1 and the related 10.15:1, the delete dialog's corner 12 px, Tab's ring travelling the swatches and the tree's cursor moving with Tab (`docs/critiques/design-layer-function.md`). | §8 |
| 2026-09-18 (the last Material frames) | Shared, nothing desktop-only: `ui/components/TendrilField.kt` (+`readOnly`, `minLines`, keyboard options), twenty sites onto it (nineteen in `shared/`, the phone's passphrase in the Android app), `ui/components/DatePickers.kt` (`TendrilDatePicker`, nine sites), `ui/taskshabits/AddDialogs.kt` (both Add dialogs as `TendrilSheet`s), `SlideOver.kt` (the title at `pageTitle`; **the content under the title bar's row** — since L5 the caption buttons are painted over the top-right corner of everything, and a slide-over's × sat under Windows' ✕: a missed close hid the app to the tray), `TendrilSheet.kt`, 39 `overflow = Ellipsis` sites, `tools/audit.py` rules 18–19. Walked: the Add sheet as a slide-over at 440 dp, its field 36 px, its title 13 px caps, its × clear of ✕; the date picker one line (`docs/critiques/material-frames-function.md`). | §8 |
| 2026-09-18 (the database's views) | Shared, nothing desktop-only: `ui/pages/PageDatabaseScreen.kt` (the chip's ▾ and menu, the Delete view and Move to Trash confirms, `ViewNeedsPrompt`, the New view sheet, the `···` order), `PageDatabaseViewModel.kt` (`addView` makes or binds the property a view plots by; `BoardColumn` with the *No Status* column; `trashDatabase`), `TimelineView.kt`, `PageRoute.kt` (Show on Road Map reaches a database), `PagesWorkspace.kt` (*Open Trash…*), `domain/Words.kt` (the blurbs). Walked on *Books v12*: *Table ▾* → the two verbs at 29 dp rows; *Add view* → the sheet → *Board* → the Status Select made and bound, the rows in *No Status*; *Delete view…* → the 456 × 174 dialog → the view gone, the rows and the property kept; the `···` with twelve rows and three dividers (`docs/critiques/database-views-function.md`). Ground: Notion measured live on a throwaway page *Views ground* (the user's to delete). | §8 |
| 2026-09-18 (small things III) | Shared, nothing desktop-only: `ui/components/Submenu.kt` (`submenuSide`), `domain/journal/JournalToday.kt` (`displayTitle`) and its six sites, `domain/timeline/Timeline.kt` (`timelineInitialColumn`), `domain/reminders/ReminderFirings.kt` (`reminderFiresAt`), `ui/reminders/ReminderSheet.kt` (the list), `PageDetailViewModel`/`PageDetailScreen` (the label sheet), `TasksHabitsScreen` (F10, F13), `ui/roadmap/RoadMapScreen.kt` (F14). Walked at the user's window: the flipped submenu, the tree's *13 Sep 2026*, the Timeline on the 14th with *Call the library* whole, the reminder list, *● book* in the label sheet, *Show Someday · 2*, the shelf's graph apart in every frame (`docs/critiques/small-things-3-function.md`). Ground: TickTick measured live (a throwaway task *Reminder ground*, the user's to delete). **§0.10 item 22's Med/Low list is closed.** | §8 |
| 2026-09-18 (the quick-add tokens inline) | Shared, nothing desktop-only: `ui/entries/QuickAddTokens.kt` (`quickAddTokensTransformation`, `tokenTintRanges`), `QuickAddField` (the strip and the popup), `AddDialogs.kt` (the sheet, now focused on open), `CalendarScreen` (the phone's Day view field), `domain/QuickAddParser.kt` (an event drops its DEADLINE span). Walked at the user's window: the tint `#5D564A` = `findSoft`, a 17 px box in the 26 px strip field, six runs on a long line, the drop, the popup (Ctrl+Shift+Space in this profile) and the sheet (`docs/critiques/quick-add-tokens-function.md`). Ground: Todoist's quick add measured live (its window restored from a 32767 px tall rect to 1400 × 1000 for the grab — left so). **§0.10 item 22 is closed.** | §8 |
| 2026-09-18 (the phone catch-up) | Shared, one line: `ui/pages/PagesScreen.kt` `LabelFilterRow`'s bottom padding clamped at 0 (it went negative under Touch only — the desktop's pointer profile never hit it). The walk was the phone's (`docs/critiques/phone-catch-up.md`); nothing desktop-only. | §8 |
| 2026-09-18 (the phone's fixes) | Shared; on the desktop only two things move: the slide-over's body **scrolls** (`SlideOver(scrolls)`, from `TendrilSheet`'s new parameter — a short window no longer squashes the Add task sheet's rows) and `TendrilMenu` carries a `MenuLevel` it never pushes under a pointer (the submenu stays beside). `tools/audit.py` rule 20 *sheet scroll*; `domain/TaskRowMeta.kt` formats the desktop's row meta too (*sab 12 · 08:00*, was ISO). Phone walked (`docs/critiques/phone-fixes-measured.md`). | §8 |
| 2026-09-16 (the type vocabulary) | Anti-drift: shared throughout — `ui/theme/Type.kt` (the fonts at true weights: the desktop was the platform that showed the defect, Skia synthesising bold but not Medium), `ui/theme/TendrilType.kt`, the audit rule; `DesktopSettingsScreen`'s section titles read `titleMedium` = heading and needed no edit. Verified beside Notion at the user's 967-px window: Settings, the tree, the editor, the Calendar's Week and tray, Typeface → DM Sans / Serif / Inter at true weights; the measurements in `docs/critiques/type-vocabulary-function.md`. | §8 |
| 2026-09-16 (hover previews) | Anti-drift: shared throughout — `domain/preview/PagePreview.kt`, `ui/components/HoverPreview.kt`; nothing in `Main.kt` beyond what `LocalDensityProfile.pointer` already decides. Verified: the inline `@mention` (the span outlined, the card under the line), the mention block, the block reference (its line marked), the Road Map's nodes (a database's Table line, a canvas's cards), the flip above at the window's foot, the clamp at its right edge, the shelf's neighbourhood, a pop-out, typing with the pointer on the mention (no card). **Density:** Compact 0.85 / Comfortable 0.95 / Touch 1.23 (were 0.9 / 1.0 / 1.3) — the user's note beside Notion at the same window; measured in `docs/critiques/hover-preview-function.md`. Next: the type PR (Inter bundled at true weights — the desktop loaded DM Sans at its default instance only; headers bold, not large). | §8 |
| 2026-09-16 (drag between panes) | Anti-drift: shared throughout — `domain/plan/Tray.kt`, `ui/calendar/TaskTray.kt`, `DropGeometry.kt`, `ui/components/Pointer.kt` `dragSource`, `EntryEditor.clearWhen`; the Timeline's *No date* drag and its title-wide bars. `calendar_tray_width`, `calendar_tray_collapsed` in `prefs.properties`. Verified: a task onto Thursday's header (its time kept), onto Friday 17:30 (the slot lit), a block back to the tray (*Clear When*), a series refused, collapse/expand and the handle, a *No date* row onto the 22nd. **Process:** the first build's `detectDragGestures` start offset was the node's far edge on the desktop — `dragSource` is hand-rolled. | §8 |
| 2026-09-16 (pop-out windows) | Desktop-only surface, shared state: `PopOutWindows.kt` here (one `Window` per popped page — its own `WorkbenchNavState` seeded on the page, `EscapeBackInput` (moved here, `internal`), `DesktopViewModelStoreOwner` cleared on close, the theme, `WorkbenchEnvironment` at the main window's shorter side; keys Escape · Ctrl+F · Ctrl+W · Alt+← / →); `Main.kt` builds `WorkbenchNavState`, `MainWindowActions`, `PopOutRegistry`, `PopOuts` and composes `PopOutWindows` after the main window. `popout_pages`, `popout_frame_<id>` in `prefs.properties`. Verified: the three openers, a block typed in one window read in the other, Ctrl+F and the stack inside a pop-out, Escape stopping at the seed, *Open in the main window* and *Show on Road Map* fronting the main window, a trashed page closing its window, Ctrl+W, two pop-outs restored after a relaunch, the same block pixel-identical in both windows. **Process finding:** the installed 2026-09-08 preview `Tendril.exe` (`%LOCALAPPDATA%\Tendril`) shares `~/.tendril-desktop-dev/` and wiped the dev database to v8 when launched — recovered from the last checkpoint; uninstall it. | §8 |
| 2026-09-16 (14h·2 — the small things) | Anti-drift: shared throughout, nothing desktop-only — `ui/theme/Type.kt`'s `TypeScale`, `ui/components/Submenu.kt` and `Copy.kt`, `domain/MindMapFold.kt`, `domain/time/RelativeTime.kt`, `DensityProfile.rowHeightDp` / `listInteractiveMinDp`, `PageRelationDao.observeAll`. `tools/audit.py` rule 12 *off-scale font size*. Verified at Compact: Table rows 58–60 → 40.5 px, the chip row 14/20 → 11/12 px, *Mind map · open*, the mention on its tint, a find inside a folded map unfolding on ↵, the map redrawing after Ctrl+N without ↻, *Show beside ▸* beside its item, the ground click, the shelf's row ringed. The phone walk is pending (testing paused). **§0.10 item 14 closed — the desktop pass 14a…14h is done.** | §8 |
| 2026-09-16 (14h·1 — the shelf) | Anti-drift: shared throughout — `ui/pages/ShelfState.kt`, `ShelfPane.kt`, `ui/roadmap/RoadMapNeighbourhood.kt` (the map's `RoadMapCanvas`/`RoadMapNode` are `internal` and take a node width; the shelf draws them at 120 dp with a double-tap). `Main.kt` unchanged: the chord goes through the table (`TOGGLE_SHELF` = **Ctrl+Shift+\**, the F1 card lists it) and `WorkbenchScaffold` owns the `ShelfState`. `pages_shelf` (`page:<id>` · `graph` · `journal`), `pages_shelf_last`, `pages_shelf_width` in `prefs.properties`. Verified: *Show beside ▸ Today's Journal* with its live strip; `⇄`; `×`; the chord reopening; the graph at depth 1 and 2, click → the shelf, double-click → the main pane; a mention inside the shelf turning it into the graph (beside itself); *Open beside* and Ctrl+click on tree rows; the handle clamped at 45 % with 560 stored; a block typed in the shelf saved; the shelf back after a relaunch. The phone walk is pending (testing paused). | §8 |
| 2026-09-16 (14g·3 — the urgency ladder) | Anti-drift: shared throughout — `domain/urgency/`, `ui/components/UrgencyMarks.kt`, `ui/settings/TaskSettings.kt`; `Main.kt` reads `core.taskSettings` (the hardcoded `showImportance = false` / `showStreaks = false` are gone) and `DesktopSettingsScreen` gained the *Tasks & Habits* section; `show_urgency` / `show_habit_streaks` in `prefs.properties`. **Schema v20 migrates in place** — verified on the real desktop DB: `user_version` 19 → 20, `entries.important` gone and `importance` present, a task flagged before opening at *High*, `time_logs` still joining. Verified: the stripe on rows, timed blocks and all-day chips (the text/stripe overlap the user saw fixed), the pane's row and popup picker, the row menu's level list, a deadline set to yesterday → *Urgent (the deadline)*, `Dentist fri !!` → *Urgent*, the switch off/on, Light and Dark ladders. The phone walk is pending (testing paused). | §8 |
| 2026-09-16 (14g·2 — the token map) | Anti-drift: shared throughout — `shared/ui/theme/DataColours.kt`, `ui/calendar/LayerColours.kt`, `ui/components/LabelDot.kt` new; `ColorSolve.kt`'s `fanHue` / `tintFor` / `onColour`; nothing desktop-only. `tools/audit.py` gains *hardcoded colour* (rule 11) — a `Color(0x…)` or named literal in shared UI outside `ui/theme/` fails the stand-in. Verified: Ink dark/light and Console dark — the find mark in the third hue's tint and the current match solid (pixel-read), an `@mention` in the accent, a callout's bar + tint and its seven picker hues, `#book` chips on the tree, the strip and the label menu, the Week's event tint, the Road Map's related edge / database fill / canvas outline in the third, a canvas edge drawn in the accent and settling dim. The phone walk is pending (testing paused). | §8 |
| 2026-09-16 (14g·1 — the theme model) | Anti-drift: `shared/ui/theme/Registers.kt`, `ColorSolve.kt`, `ThemeSettings.kt`, `ui/settings/ThemeSection.kt` are shared; `Main.kt` reads `core.themeSettings` — the fixed Ink/Light/Sans is gone — and System follows Windows' app theme through `isSystemInDarkTheme()`; `theme_register` / `theme_mode` / `theme_typeface` in `prefs.properties` (`theme_oled` is the phone's; never read here). `DesktopSettingsScreen` gained the Theme section between Density and *Opens on*. **Seen and fixed:** with a dark register the Settings pane showed the AWT window's `#F0F0F0` between a dark rail and top bar — no route painted a ground; the shared scaffold now sits every route on one `Surface` (`tendril-spec.md` §2.3). Verified: a fresh store opens dark under dark Windows; Console, Blush, Ink in both modes across Settings, Pages, a page, a menu, Tasks; the keys written. The phone walk is pending (testing paused). | §8 |
| 2026-09-16 (14f·2 — the Calendar's week grid) | Anti-drift: `shared/ui/calendar/WeekGridView.kt`, `QuickAddBar.kt`, `CalendarDefaultView.kt`, `CalendarOpensOnSection` and `domain/plan/WeekLayout.kt` are shared; `DesktopSettingsScreen` gained *Opens on*; `calendar_default_view` in `prefs.properties`. Verified: opens on Week; the grid; a long-press drag of a series occurrence to another day and hour → *this one or all?* → moved; the bar's ⊕ → the strip → `Dentist fri 14:30` → the block; Esc; *Opens on: Day* written; the Tasks filters over the list. **Seen:** the app died on a paste — `cannot open system clipboard` from Compose's paste handler on the AWT thread (`tendril-spec.md` §0.10 item 21: a default uncaught-exception handler in `Main.kt`, not started). The phone walk is pending (testing paused). | §8 |
| 2026-09-16 (14f·1 — Tasks on a wide window) | Anti-drift: `shared/ui/taskshabits/TaskDetailPane.kt`, `ui/components/PaneHandle.kt`, `ui/nav/ShellLayout.kt`'s `LocalShellLayout` and **`shared/ui/trash/EntryTrashSheet.kt` / `HabitTrashSheet.kt`** (moved from the Android app; they take `WorkbenchCore`) are shared; `Main.kt` passes the two sheets, so the desktop's Tasks has its Trash button (§0.10 item 13); `reminderSheet` stays null — no alarms here (§12.1). `tasks_list_width` in `prefs.properties`. Verified: the split, the pane and its chips (Postpone → a slide-over, Start/Stop timer, Add a step), hover `···`, right-click at the pointer, the Trash slide-over, the habit pane, the handle. The phone walk is pending (testing paused). | §8 |
| 2026-09-16 (find in page) | Anti-drift: `shared/domain/find/FindInPage.kt`, `shared/ui/pages/FindBar.kt`, `FindMarks` in `SpanVisualTransformation.kt`, `Shortcuts.kt`'s `FIND_IN_PAGE` (Ctrl+F — a letter, layout-safe; on the F1 card) and `WorkbenchNavState.findRequested` are shared; nothing in `Main.kt`. Verified on the desktop: Ctrl+F → the bar, `child` → *1 of 2*, ↵ wraps, Shift+↵, *No matches*, Esc; a double-clicked word seeds the query; `···` → *Find in page*; a Database page ignores Ctrl+F; the card lists it. The phone walk is pending (testing paused 2026-09-16). | §8 |
| 2026-09-16 (14e — the keyboard) | Anti-drift: `shared/ui/nav/Shortcuts.kt` (the table, `ShortcutActions`, `ShortcutsState`), `ShortcutsOverlay.kt`, `ui/components/ListKeyboard.kt` and `Pointer.kt`'s `onPointerNavigation` are shared. `Main.kt` is down to Escape plus one `shortcutFor` lookup (Ctrl+K and Ctrl+\ moved into the table) and builds the two states; `DesktopSettingsScreen` gained *Keyboard shortcuts… (F1)*. **The desktop's set:** Ctrl+1…5 tabs · Alt+← / Alt+→ back, forward · Ctrl+\ tree · Ctrl+N page · Ctrl+Shift+N task · Ctrl+T journal · Ctrl+K switcher · F1 the card · Esc back; in the tree and the Tasks list ↑ ↓ Home End ↵ → ← and type-ahead. Layout-independent by rule (the developer's keyboard is Italian). Verified: every chord; Alt+arrows around a pushed page; the tree walk; ↵ on a task; F1 and the Settings line. **Not verified:** the mouse's side buttons — synthetic XBUTTON input never reached the window (nor did a synthetic primary click, so the test says nothing); a physical mouse is needed. | §8 |
| 2026-09-15 (14d — under a pointer) | Anti-drift: `shared/ui/components/Pointer.kt` (`onSecondaryClick`, `PointerMenu`) is shared — the right-click reaches an Android mouse too; `TextContextMenuExtras` is the one expect/actual, its desktop actual (`shared/src/desktopMain/…/TextContextMenuExtras.desktop.kt`) `ContextMenuDataProvider`, which appends *Block actions…* to a text field's own cut/copy/paste menu. Nothing in `Main.kt`. Verified on the desktop: hover `···` → menu; right-click a row → the menu at the pointer; *Move to Trash* → the Trash slide-over; right-click in text → the text menu + *Block actions…* → the block slide-over; select → the toolbar floats above, Bold applies, focus elsewhere drops it; View-Only greys *Move to Trash*. Critiques under `docs/critiques/`. | §8 |
| 2026-09-15 (14c — two panes) | Anti-drift: `shared/ui/pages/PagesWorkspace.kt`, `PagesTreeState.kt`, `ui/nav/PaneChrome.kt`, `ui/nav/PageRoute.kt` (the scaffold's page branch, extracted) and `PagesScreen.kt`'s `PagesHost` split are shared; `WorkbenchScaffold` gained `treeState`; the three detail screens take `onBack: (() -> Unit)?` and `paneChrome`. Desktop-only, in `Main.kt`: **Ctrl+\** toggles the tree (the third shortcut); `pages_tree_width` / `pages_tree_collapsed` in `prefs.properties`. Verified: tree, page, database and a journal day in the pane; Ctrl+\; the handle (a real mouse drag — synthetic input needs the 12 dp target); width survives a relaunch. Fourth mock under `docs/mockups/`. | §8 |
| 2026-09-15 (14c·0 — the scale) | Anti-drift: `shared/ui/nav/ShellScale.kt` and the `LocalDensity` override at `WorkbenchScaffold`'s root are shared; `WorkbenchScaffold` gained `fixedDensityProfile` (Android passes Touch; `Main.kt` passes nothing and the store's `density_profile` in `prefs.properties` decides, Compact by default). Desktop-only: the *Density* section in `DesktopSettingsScreen` (three chips). The window frame in `Main.kt` stays in the platform's raw dp — it is outside the override. Verified: Compact → Touch grows the whole window's contents live. | §8 |
| 2026-09-14 (14b — slide-overs) | Anti-drift: `shared/ui/components/SlideOver.kt` (new) and `TendrilSheet`'s branch on `sheetFormFor(windowWidthDp)` are shared; nothing desktop-specific. Escape closes a slide-over through the existing `EscapeBackInput` → `BackHandler` path — verified. Desktop-only fix in `Main.kt`: the frame is written only while `placement == Floating` (a maximised window had stored the screen as its floating size). `ShellTopBar`'s actions now sit in a nested row so `DropdownMenu`s anchor under their button. | §8 |
| 2026-09-14 (14a — the shell) | Anti-drift: `shared/ui/nav/Shell.kt` (rail, bottom bar, top bar) and `ShellLayout.kt` (the 840 dp breakpoint, `WindowFrame`) are shared; `WorkbenchScaffold` no longer uses Material's `Scaffold`; `RunningTimerBar` gained `RunningTimerRailFoot`. Desktop-only, in `Main.kt`: `rememberWindowState` seeded from and written back to `window_frame` in `prefs.properties` (debounced 400 ms; `"w,h,x,y"` in dp, `-1,-1` = OS-placed), `window.minimumSize` 800×600, title *Tendril*; `SyncBar` moved from the top of the window into `DesktopSettingsScreen` as the *Sync folder* section (its behaviour unchanged). §8's note updated. Verified: rail at 1200×800, bar below 840, floor at 800, frame survives a kill. | §8 |
| 2026-09-14 (data colour) | Document only: B§13.8 assigns every coloured element its channel for both platforms; nothing desktop-specific. Third mock under `docs/mockups/`. | §8 |
| 2026-09-14 (themes and colour) | Document only: B§13.7 replaces the theming model for both platforms (`tendril-spec.md` §2.3 amended). Desktop-relevant: the theme is shared and the *Deeper blacks* OLED toggle is phone-only — the desktop never shows it; the register menu and mode picker reach the desktop Settings pane with 14g. Both mocks under `docs/mockups/`. | §8 |
| 2026-09-13 (desktop layout benchmark) | Document only: `docs/benchmarks.md` §13 plans the desktop shell's revision (eight PRs, 14a–14h, decisions answered on `docs/mockups/desktop-shell.html`); §8 points at it. Nothing in `shared/` or here changes yet. **Correction to the *Claude verbs, Settings* row below:** `jna` and `jna-platform` 5.6.0 *are* in the Gradle cache (a direct dependency resolves offline), so a DPAPI wrap of `anthropic.key` and a global hotkey are possible — `tendril-spec.md` §0.10 item 20. | §8 |
| 2026-09-13 (Claude verbs, Settings) | Anti-drift entry (§0): `shared/` gains `data/prefs/AiKeyStore` (desktop actual `FileAiKeyStore` in `desktopMain`: `~/.tendril-desktop-dev/anthropic.key`, one line — **on NTFS it inherits the profile's ACL, this account and administrators; `File.setReadable` is a no-op there and a DPAPI wrap would need JNA, which the offline build cannot fetch**), `domain/ai/`, `ui/pages/AiResultSheet.kt`, `ui/settings/AiSettingsSection.kt`; `WorkbenchCore` takes an `AiKeyStore` (required). **Settings is no longer a stand-in**: `DesktopSettingsScreen` holds the Claude section and says the rest is Android's; `NotAvailableOnDesktop.kt` deleted — every tab is shared. Verified on the preview: no verb row without a key; a dummy key saved → the file created, `prefs.properties` untouched → *Rewrite* on a selection → a genuine 401 from `api.anthropic.com` → "The key was rejected"; Clear deleted the file. | Revision Log, §8 |
| 2026-09-13 (Timeline) | Anti-drift entry (§0): `shared/` moves to **Room schema v19** (`endDatePropertyId` on views, `blockedByPropertyId` on databases, `MIGRATION_18_19`; `19.json`) — the desktop DB migrated in place. `ViewType.TIMELINE`, `ui/pages/TimelineView.kt`, `domain/timeline/`; the snapshot records carry both uids. Nothing desktop-specific; the bar drag is a mouse drag. Verified on the preview: a Timeline on *Books v12* by *Read on*, today tinted, Monday hairlines, a bar dragged three columns wrote the cell; a self-relation *Blocked by* bound from `···`, *Blocked* appearing on the Table row when the blocker's Done was cleared, the dependency line drawn on the Timeline. | Revision Log |
| 2026-09-13 (page history) | Anti-drift entry (§0): `shared/` moves to **Room schema v18** (`page_revisions`, `MIGRATION_17_18`; `18.json`) — the desktop DB migrated in place on the preview. `domain/history/PageHistory`, `sync/BlockSnapshots.kt` (the block rebuild the merge and a restore share), `ui/pages/HistorySheet.kt`; `PagesSyncEngine` takes a `PageHistory` (`Main.kt` passes `core.pageHistory`) and keeps the local body a winning record replaces. The table never enters the sync folder. Verified on the preview: an empty History; ten keystrokes → one "before an edit" row; preview; Restore → the old text back and a "before a restore" row. | Revision Log |
| 2026-09-13 (block references) | Anti-drift entry (§0): `shared/` moves to **Room schema v17** (`blocks.referencedBlockUid`, `MIGRATION_16_17`; `17.json`) — the desktop DB migrated in place on the preview (`PRAGMA user_version` 16 → 17, column present). `BlockType.BLOCK_REFERENCE`, `ui/pages/BlockReference.kt`, `domain/references/`, `BlockDao.observeByUid`/`searchContent`; `BlockSnapshotRecord.referencedBlockUid` verbatim. Nothing desktop-specific. Verified on the preview: `((` on today's Journal → *Child* → the *Escape test* block as a card; the source edited → the card live; reopen → the cache refreshed, block and page `updatedAt` unchanged; *Call the library*'s Unlinked mentions → *Link* → Linked (2). | Revision Log |
| 2026-09-13 (Road Map on desktop) | Anti-drift entry (§0): `shared/` gains `ui/roadmap/` (`RoadMapScreen`, `RoadMapViewModel`, moved from the Android app) and `domain/roadmap/RoadMapGraph.kt`; `WorkbenchScaffold` renders the screen itself and its `roadMapContent` slot is gone — `Main.kt` drops `NotAvailableOnDesktop("Road Map")`. **Settings is the last stand-in.** The Road Map's filter persists through `PropertiesKeyValueStore` (`roadmap_filter`). Verified on the preview: the tab drew the seeded mention edges with arrowheads; the Journal chip hid and showed the day page; Databases off dropped *Books v12*; "Show on Road Map" from *Escape test*'s `···` opened the map focused on it, depth 2 reached the day page only with the Journal shown; "Relate to…" *Escape test* → *Books v12* drew a tertiary line; `#book` narrowed the map to its two rows. Two layout fixes found here (first-frame settle, px-space repulsion) — see `tendril-spec.md` §3.4. | Revision Log, §8 |
| 2026-09-13 (Journal shows today) | Anti-drift entry (§0): `shared/` gains `domain/journal/JournalToday.kt` and `ui/pages/JournalTodayStrip.kt`; `PageDetailViewModel` takes `HabitDao` and `CheckInHabitUseCase` (`DesktopAppContainer` needs nothing — both come off `WorkbenchCore`). Nothing desktop-specific. Verified on the preview: `>jour` → today's page showed *Read chapter 3 · 21:00* and *Stretch · Every 1 day(s)*; ticking Stretch wrote the check-in and streak, unticking tombstoned it; ticking the task resolved it `DONE`; *Escape test* (a plain page) showed no strip. | Revision Log |
| 2026-09-12 (quick switcher) | Anti-drift entry (§0): `shared/` gains `domain/SwitcherQuery.kt` and `ui/switcher/`; `WorkbenchScaffold` takes a `SwitcherState`. `Main.kt`: **Ctrl+K** opens the switcher (the second desktop shortcut after Escape; a `SwitcherState` built in `main` and handed to `App`), and `healIndex()` runs before the window opens. Schema v16. Verified on the preview: Ctrl+K → `boo` → Enter opened *Books v12*; `>rev` → Enter opened Review from a page; `child` found body text with its snippet; Esc closed it. | Revision Log |

---

## 0. Relationship to `tendril-spec.md` — the anti-drift rule

**This file does not duplicate the shared domain/data/sync-merge model.** `Entry`, `RecurrenceRule`,
the Room schema, the to-do database sync mechanism, the JSON snapshot format, and
`PagesSyncEngine`'s whole-page LWW merge algorithm are documented once, in `tendril-spec.md`
(§4 Data Model, §5 To-do Databases, §9.4 Sync, §9.10 migration policy) — because that model
originated there and Android remains the primary client. This file references those section numbers
rather than re-explaining them. If you're reading this file looking for what a `shared\` type or
table actually means, go there first.

**Corrected 2026-09-06 — that list is no longer what `shared\` holds, and the gap leaves things
with no owner.** It is kept above because it is the scope the rule was written against, back when
`shared\` was a domain/data/sync core and nothing else. `shared/src/commonMain` now also carries
`ViewLockState`, `CheckboxOnlyState`, `BlockOutline`, `HabitSchedule`, `PurgeRegistry` and
`WorkbenchCore`, a `domain/recurrence` package, and seventeen UI files — the theming, the nav shell
and the whole block editor, moved there by Milestone 3 (§8). None of that is named on either side,
so for anything in it neither file is obviously the owner, and the cost is not hypothetical: the
partial `ColorScheme` remap and the lost variable-weight font rendering are recorded here (§8) and
in no section of `tendril-spec.md`, and the shared checkbox-only confirm dialog still tells whoever
reads it that the page "will show over your lock screen" and that turning the mode off "needs a full
unlock" — two sentences a desktop user is shown and neither of which is true on a machine with no
keyguard and no `BiometricPrompt`. That text is owned by `tendril-spec.md` §3.1.2 and contradicted
by a platform only this file describes. Until the ownership question is settled the binding rule
below is unaffected, because it keys on *code in `shared\`* and not on which section explains it.

**Also stated plainly 2026-09-06, because two passes have now hesitated over it:** §5, §7 and §8
are dated milestone records — an account of what was built and what was learned building it, as of
the date in each heading — not live descriptions of the current tree. They get a dated note in
place wherever a stale sentence would mislead a reader about what the code does *today*; they are
not rewritten to track `shared\`'s present shape, and where no note exists the Revision Log is the
newer record. That is why §5 still says "the 7 pure `domain/` classes" while that package now holds
eleven files: the sentence is an accurate account of what Milestone 1 moved, and the three later
arrivals each have a Revision Log row above.

**Binding rule, stated identically in both files: whenever a change touches code in `shared\`, both
this file's Revision Log and `tendril-spec.md`'s Revision Log get an entry the same day** — even if
one of the two entries is just a one-line pointer to the other ("see tendril-spec.md's entry dated
X"). This is the mechanism that makes a two-file split safe rather than the drift risk §4's own
scoring table originally penalized a full split for. Whichever file's editor makes a `shared\` change
is responsible for adding both entries, not just their own file's.

**Folder layout** (full version in `tendril-spec.md` §11): three sibling folders —
`Tendril android\` (this project's parent, the APK build and `tendril-spec.md` itself), `shared\`
(the Kotlin Multiplatform core, consumed by both via Gradle composite builds), and this folder,
`Tendril windows\` (the desktop companion, this file, and the Windows EXE build).

---

## 1. Feasibility: reuses §9.4's sync design as-is

§9.4 (in `tendril-spec.md`) was never designed around "the Android app" as one half of a bespoke
pairing — it treats the synced folder as portable, domain-general JSON snapshot files
(`pages/<id>.json`, `entries_active.json`, `habits.json`, …) with per-record last-write-wins, atomic
writes, and explicit `.sync-conflict-*` handling for genuinely concurrent writers. A desktop client
is another reader/writer of that same format — **no new sync design is required.**

**Clarification, not a new decision:** SAF (`tendril-spec.md` §9.3) is specifically the Android
permission mechanism for accessing the sync folder under Android's scoped storage; it has no desktop
equivalent and doesn't need one — a desktop client just needs ordinary filesystem access to the
synced folder. It could even run unmodified upstream Syncthing rather than the fork, since the fork
exists specifically to work around Android scoped-storage friction that doesn't exist on desktop.

**Added 2026-09-04 — automatic sync triggers are Android-only, and not just for lifecycle
reasons.** Android now runs a sync pass on `onStart` and on `onStop`/backgrounding, through a new
`SyncCoordinator` (`tendril-spec.md` §9.4). Desktop deliberately stays on its explicit "Sync now"
button. `onStart`/`onStop` have no exact desktop analog, but the real blocker is narrower and
specific to this app: the desktop passphrase is session-only, typed into the sync bar and never
persisted (§7), so at launch there is nothing to decrypt an encrypted folder with. An automatic pass
would find nothing readable and — were it not for §9.4.2's guard, added the same day — would have
written this device's state over the folder under no key at all. Revisit alongside secure passphrase
storage on desktop, which §7 already lists as out of scope.

**What's Android-specific and does not carry over:** `AlarmManager`/notification scheduling
(`tendril-spec.md` §9.7 — *amended 2026-09-16: the desktop schedules its own toasts from the shared
firing arithmetic, `DesktopReminders.kt`; only `AlarmManager` itself stays Android's*), Jetpack Glance widgets (§8, §9.6), `BiometricPrompt` App Lock (§3.6), and
`CalendarContract` Provider registration (§3.2) are all Android platform APIs with no desktop
analog — a desktop client would need its own equivalents (or go without; widgets and lock-screen
presence don't obviously apply to a desktop window) rather than porting these directly. Room's
Kotlin Multiplatform support means the domain/persistence layer is plausibly shareable rather than
reimplemented — see §2.

## 2. Platform strategy (scored 2026-08-29)

Four candidates, scored against effort to reach a working client (inverted), long-term maintenance
burden (inverted), whether it actually delivers a working desktop companion, fidelity/consistency
with Workbench's existing design system, and sync/format risk beyond §9.4's already-accepted
limitations (weights 0.25/0.25/0.20/0.15/0.15):

| Option | Effort (inv.) | Maintenance (inv.) | Goal fit | Fidelity | Sync risk (inv.) | Score |
|---|---|---|---|---|---|---|
| Defer — no desktop client for now | 1.00 | 1.00 | 0.00 | 0.50 | 1.00 | 0.725 |
| Fully independent client (separate stack, e.g. Electron/Tauri; reads/writes the same JSON snapshot files, zero shared code) | 0.30 | 0.25 | 0.90 | 0.35 | 0.85 | 0.498 |
| Kotlin Multiplatform shared domain core, separate desktop UI | 0.55 | 0.60 | 0.85 | 0.65 | 0.95 | 0.698 |
| **Full Kotlin Multiplatform + Compose Multiplatform — shared domain *and* shared UI, platform-specific `expect/actual` only for widgets/alarms/SAF/biometrics/Calendar Provider (chosen)** | 0.65 | 0.75 | 0.90 | 0.90 | 0.95 | **0.808** |

**Decided, contingent on this work actually starting:** if/when a desktop companion is built, it's
built as Kotlin Multiplatform + Compose Multiplatform, not a separate-stack client. Tendril is
already 100% Kotlin/Compose, and `tendril-spec.md` §9.8's architecture review already pushed the
domain layer toward exactly the shape that makes this cheap — centralized use-cases
(`ResolveEntryUseCase`), sealed types (`RecurrenceRule`) instead of convention-typed columns, and a
snapshot format already designed for multiple concurrent writers. Compose Multiplatform lets
Workbench's actual composables (theming, §2.3's palettes are already just hex-value data; the block
editor; the calendar grid) render on desktop directly, so fidelity to Workbench holds by construction
rather than by two-codebases discipline. The independent-stack option scores worst specifically on
maintenance (0.25): every domain rule — recurrence math, LWW merge, resolve-and-advance — would need
hand-reimplementation and could silently drift, the same failure category §9.8 already caught twice
(the original Task/Event conflation, the anemic-domain-model risk) — and the same drift risk this
file's own §0 anti-drift rule exists to prevent at the documentation level, now that code is real.

**Implementation note (2026-08-30, Milestone 1 — see §5):** built as designed, with one structural
adjustment: the "shared domain core" lives in its own sibling folder (`shared\`), consumed by both
`Tendril android\` and this folder via Gradle composite builds, rather than as a monorepo submodule
under one root `settings.gradle.kts`. The user asked for two separate top-level folders (APK vs.
EXE); composite builds still deliver one real shared implementation, just linked differently. See §5.

## 3. Follow-up risk this creates, not yet acted on

`tendril-spec.md` §9.4's "Accepted v1 limitation" — concurrent edits to the *same page* from two
devices before either syncs means one edit is lost, LWW applied at the whole-page-snapshot level —
was accepted for the narrow case of two phones. A real desktop client makes this materially more
likely to actually happen: desktop editing sessions run longer and are more likely to overlap with a
phone edit before either syncs, unlike two phones that are rarely both open on the same page at once.
**Not decided, gated to the next desktop milestone that adds editing, not Milestone 1 (§5) or
Milestone 2 (§7):** revisit whether page-snapshot merging needs to move from whole-page LWW to
something block-level, before desktop becomes a primary editing surface rather than an occasional
one. Same "decide immediately before the phase that needs it" gating `tendril-spec.md` §9.9 already
uses.

**Re-affirmed after Milestone 2 (2026-08-30):** folder-sync-on-desktop (§7) gives desktop real
read+merge+write-back sync, but the desktop UI is still read-only — no editing was added. Every
write desktop performs is a direct write-back of data just merged in from the folder, never a
locally-originated edit, so this still isn't a genuine concurrent-edit collision against the phone.
The gate stays open, now pushed to the Workbench-UI-port milestone (§6 step 3) — that's the first
point desktop can actually originate an edit.

**Triggered by Milestone 3 (2026-08-30) — not yet resolved, just newly real.** The ported block
editor (§8) means desktop now writes locally-originated edits (typing in a block, toggling a
to-do, editing a database cell) between syncs, exactly the scenario this gate was watching for.
Still not decided: whether page-snapshot merging needs to move from whole-page LWW to
block-level. Nothing forces that decision yet — a desktop editing session colliding with a phone
edit to the *same page* before either syncs is now possible in principle, not confirmed to have
happened — but it's no longer a hypothetical this file can defer past "the next desktop milestone
that adds editing." That milestone is this one. Revisit before recommending desktop as a primary
(not occasional) editing surface.

**Corrected 2026-09-06 — the gate only became real on 2026-09-05, not on Milestone 3.** The
paragraph above says desktop "now writes locally-originated edits… between syncs," and that was
true of the database on disk but not of the folder. Until `PageDao.touch` landed
(`tendril-spec.md`'s 2026-09-05 write-path row), the only writers of `pages.updatedAt` were title,
soft-delete and restore, so typing in a block, toggling a to-do or editing a database cell changed
what a page's exported snapshot *contained* without changing the timestamp the merge decides on.
What a person got was worse than a lost race: the edit persisted on desktop, travelled to the
folder, arrived at the phone no newer than the copy already sitting there, and was skipped — and
skipped silently, because an equal timestamp reads as the same version rather than as a conflict,
so nothing was written down anywhere saying an edit had existed. Then it vanished on desktop too,
the moment the phone made any edit that did move the timestamp. So for the whole of Milestone 3
this gate was watching for a collision that could not yet occur. It can now: every desktop edit
genuinely wins or loses. The granularity question is untouched — whole-page LWW is still
`tendril-spec.md` §9.4's to state, not this file's to re-explain — but the gate has stopped being a
precaution about traffic that might one day exist.

## 4. Why this content ever lived in `tendril-spec.md`, and why it's here now (documentation-structure decision, scored 2026-08-29 via `optimization-engines:meta-optimizer`, Harmony Search over four discrete candidates)

Scored against goal-fit (does it give a clear build target), avoiding premature structure (weight
given nothing desktop-related was built yet at the time), drift-risk if the shared sync/domain model
changes, discoverability, cognitive load, and consistency with the parent doc's own pattern (weights
0.25/0.20/0.20/0.15/0.10/0.10):

| Option | Score |
|---|---|
| Fully separate parallel file (`tendril-desktop-spec.md`) immediately (2026-08-29) | 0.548 |
| Shared "core" spec + a separate spec per platform (Android + Windows), immediately | 0.623 |
| Plain appended section in `tendril-spec.md`, no explicit graduation condition | 0.675 |
| Bare one-line Open-item entry (§10-style) only | 0.678 |
| **A dedicated, fully-reasoned section in `tendril-spec.md`, with an explicit graduation trigger — chosen 2026-08-29, refined via one recombination + one refinement pass** | **0.855** |

A fully separate file scored worst *at the time*: two living documents covering the same shared
sync/domain model (`Entry`, `RecurrenceRule`, the snapshot format) drift apart the moment one is
updated and the other isn't, and nothing desktop-related had been built yet to justify the split —
the same "real complexity, no case yet forcing it" reasoning that kept Timeline/Gantt views and a
formula language out of scope elsewhere (`tendril-spec.md` §5.6, §10 — **both reopened 2026-09-06**,
so read this as what the comparison was worth on the day the scoring ran, not as either feature's
status; a reader who follows that cross-reference expecting to find "out of scope" now finds struck
bullets there instead). A shared-core-plus-per-platform
restructure scored almost as low: it would have required tearing apart the parent document's existing
maintained structure for a platform with zero code written. A bare Open-item bullet would have
under-delivered on the actual goal — it wouldn't have captured the platform-strategy decision (§2) at
all. The section-in-place option won because it was single source of truth (no drift possible — only
one file existed), fully reasoned, and named precisely.

**Graduation trigger, met 2026-08-30:** "split this section into its own file... when desktop work
actually enters the build sequence — not before." Milestone 1 (§5) is real, running, verified code —
the trigger fired, and this file is the result. The drift risk the original scoring table penalized a
split for is real and unmitigated by the split itself; §0's anti-drift rule is the actual mitigation,
not the file boundary.

## 5. Milestone 1 — shared KMP core + minimal desktop viewer (Implemented 2026-08-30)

**What was built.** Not a monorepo `:shared` submodule as §2 originally implied — the user asked for
two separate top-level folders instead (`Tendril android\` for the APK, `Tendril windows\` for the
EXE), so the shared domain/data/sync core lives in a third sibling folder, `shared\`, consumed by
both via Gradle composite builds (`includeBuild("../shared")`), not `include(":shared")`. This still
honors §2's actual decision — one real shared implementation, not two — just via composite-build
linking instead of a single root `settings.gradle.kts`. See `tendril-spec.md` §11 for the full
three-folder layout.

- **`shared\`**: Kotlin Multiplatform, `android` + `jvm("desktop")` targets, via the
  `com.android.kotlin.multiplatform.library` plugin — *not* `kotlin("multiplatform")` +
  `com.android.library`, which AGP 9 no longer allows together in one module (a real, non-obvious
  correction found during planning: AGP 9's Kotlin-support split applies to any module needing both
  an Android target and another KMP target, not just modules mixing `kotlin("multiplatform")` with
  Android *application* plugins as most still-circulating guidance implies). Contains: the 7 pure
  `domain/` classes (`DatabaseSyncManager`, `TemplateManager`, `PageContentRepository`,
  `ResolveEntryUseCase`, `CheckInHabitUseCase`, `ViewLockState`, `CheckboxOnlyState`), every `data/`
  entity/DAO (Room 2.8.4, unchanged annotations), and the four pure `sync/` files including
  `PagesSyncEngine`'s whole-page LWW merge logic.
- **`EntryScheduleCoordinator` — one real gap found only during the move, not anticipated by §1's
  audit**: `ResolveEntryUseCase` took it as a concrete constructor dependency, but the concrete class
  itself depends on Android-only `AlarmScheduler`/`CalendarProviderSync`. Fixed by turning it into an
  interface in `shared` (~~two methods, `onEntryChanged`/`onEntryRemoved`~~ — **corrected
  2026-09-06:** two as Milestone 1 created it, three since 2026-09-05, when `onHabitRemoved` was
  added for habit reminders and given an empty default body precisely so a platform that cannot
  schedule need not implement it. Desktop's `NoOpEntryScheduleCoordinator` overrides the first two
  and takes the default for the third, so a habit purged on the phone cancels a real alarm there
  and cancels nothing here, which is the correct outcome and worth having written down rather
  than inferred from a missing override) with the existing Android
  logic moved into a new `AndroidEntryScheduleCoordinator` implementation in `Tendril android\`'s
  `:app` — exactly the `expect`-style platform-abstraction point §1 anticipated in general terms,
  just as a plain interface rather than `expect`/`actual` (simpler, and sufficient since only one
  platform has a real implementation so far).
- **`TendrilDatabase`'s bootstrap split across three files**, following Room 2.8.x's actual KMP
  pattern (not simply `expect fun buildDatabase(...)`, which doesn't fit — the platform builder
  signatures genuinely differ, Context vs. a file path): the `@Database` class in `commonMain` gains
  `@ConstructedBy(TendrilDatabaseConstructor::class)` and an `expect object TendrilDatabaseConstructor`
  (Room's KSP codegen supplies the `actual` per target, not hand-written); `androidMain` and
  `desktopMain` each add a plain `buildTendrilDatabase(...)` function with their own builder args and
  driver (`AndroidSQLiteDriver` vs. `BundledSQLiteDriver` — a second gap the original plan's code
  sketch missed by hardcoding `BundledSQLiteDriver` for both platforms; corrected during
  implementation to a shared `finishBuilding(builder, driver)` helper that takes the driver as a
  parameter instead).
- **Five cross-module smart-cast compile errors in `Tendril android\`'s `:app`**, all the same root
  cause and the same fix: Kotlin can't smart-cast a nullable property declared in a *different*
  module even after an explicit null check (`Entry.startTime`, `PageDatabase.donePropertyId`,
  `Page.icon`, `Page.databaseId`, `Page.parentId`) — fixed by binding each to a local `val` right
  before the null-check, the standard idiomatic workaround, in `CalendarProviderSync.kt`,
  `PageDatabaseViewModel.kt`, and `PagesScreen.kt` (two sites). This class of error only appears once
  entities move to a separate module — worth expecting again if more entity-using code is added to
  `:app` later.
- **This folder, `Tendril windows\`**: a genuinely minimal Kotlin/JVM + Compose Multiplatform (1.12.0)
  app, not a KMP module itself (no target beyond the one JVM it runs on). `Main.kt` boots
  `buildTendrilDatabase(...)` against a dev-only local file (`~/.tendril-desktop-dev/tendril.db`, no
  folder sync yet), seeds two pages/blocks on first empty run, and renders a plain two-pane Material3
  window (page list, read-only block text) — no reuse of `Tendril android`'s themed `ui/pages/`
  composables, no editing. Verified live twice: first via `./gradlew run` printing
  `FTS smoke test: search("seeded*") -> 1 hit(s)`, confirming Room's FTS4 virtual table works under
  `BundledSQLiteDriver` on desktop (the one Room feature flagged in planning as most likely to
  misbehave there); then again via an actual screenshot of the running window on the user's desktop,
  showing "Second page" and "Welcome to Tendril desktop" listed as expected, before being closed.
- **Toolchain versions resolved during implementation** (the plan flagged these as needing
  verification rather than trusting a guess): `androidx.sqlite` 2.7.0 (both `sqlite-framework` for
  Android and `sqlite-bundled` for desktop), `kotlinx-coroutines-core` 1.11.0 — both confirmed current
  stable via live lookup, not assumed from training data, matching `tendril-spec.md` §9.2's practice.

**Regression check:** `Tendril android`'s `:app:assembleDebug` and `:app:testDebugUnitTest`
(`NotionImportParsingTest`, which imports `BlockType`/`PropertyType` from `shared`) both pass
unchanged — zero UI change, zero DI shape change beyond one `AppContainer.kt` bootstrap-call line,
zero sync behavior change. `shared`'s own `./gradlew build` compiles and runs KSP for both targets
cleanly.

## 6. Desktop Companion build sequence

Analogous to `tendril-spec.md` §9.9, not yet formalized into it (see §4's graduation trigger — that
was about *this document* existing, not about merging into the Android build sequence, which stays a
separate future question):

1. Shared KMP core + minimal read-only desktop viewer — **done, §5.**
2. Folder-sync-on-desktop — **done, §7.**
3. Port the real Workbench Compose UI (theming, block editor, nav shell) to desktop, replacing
   Milestone 1's throwaway minimal viewer. This is the point §3's block-level-merge question
   actually gates — desktop originates edits for the first time here. **First slice done, §8**
   (theming/nav/Pages/PageDetail/PageDatabase); Calendar/Tasks & Habits/Road Map/Settings/Canvas
   remain, tracked as their own open item in §8.
4. Packaging/installer for the Windows EXE.
5. **Flagged, explicitly not part of any step above:** Room 3.0 migration (new `androidx.room3` Maven
   group, KMP-first, all-suspend DAOs — a real option now that Room 2.8.x is in maintenance mode, but
   a breaking package migration across every `@Entity`/`@Dao` in `shared`, not a drop-in) is its own
   future decision, deliberately not conflated with any of this work. If it happens, it affects
   `shared\` and therefore both this file and `tendril-spec.md` — a §0-rule case to remember when it
   comes up.

## 7. Milestone 2 — folder-sync-on-desktop (Implemented 2026-08-30)

**What was built.** `Tendril windows` can now read, merge, and write back the same JSON snapshot
files (§9.4, in `tendril-spec.md`) the phone syncs — real folder sync, not the Milestone 1 stub.

- **`shared/src/jvmCommon/`** (new intermediate source set — see the Gradle note below):
  `SyncFileStore` (interface, file access only: `read/write/list/delete` × `Root`/`Page`, no JSON, no
  crypto), `SnapshotEncryption` (moved verbatim from Android's app module, one unused
  `android.util.Base64` import dropped — otherwise untouched AES-256-GCM/PBKDF2 code), and
  `SnapshotSyncOrchestrator` (Android's original `SnapshotSyncManager`, ported: every direct
  `DocumentFile`/`ContentResolver` call became a `SyncFileStore` call; the decode/merge/re-encode
  logic itself — including the `.sync-conflict-*` scanning loop and the `providerEventId` per-device
  carve-out on Entry merge — is byte-for-byte the same algorithm, just no longer coupled to Android).
- **Platform implementations**: `AndroidSafSyncFileStore` (`shared/androidMain`, wraps
  `DocumentFile`/`ContentResolver` exactly as the pre-split code did) and `DesktopFileSyncFileStore`
  (`shared/desktopMain`, wraps `java.nio.file`, atomic write via `Files.move(ATOMIC_MOVE)` with a
  fallback to a plain move if the sync folder turns out to be on a filesystem that doesn't support
  atomic rename — plausible for a FUSE/network-mounted Syncthing-watched folder).
- **`Tendril android`**: `SnapshotSyncManager.kt`/`SnapshotEncryption.kt` deleted (logic now in
  `shared`); `AppContainer.kt` and `SettingsScreen.kt`'s "Sync now" button updated to construct an
  `AndroidSafSyncFileStore` and call the renamed `SnapshotSyncOrchestrator` — zero behavior change,
  confirmed by `:app:assembleDebug`/`:app:testDebugUnitTest` passing unchanged.
- **`Tendril windows`**: a new `DesktopSyncFolderManager` (mirrors Android's `SyncFolderManager`
  shape — `StateFlow<Path?>`, persisted, but via `java.util.prefs.Preferences` instead of SAF's
  permission grant, since desktop needs no OS-level grant at all) plus a new sync bar in `Main.kt`:
  folder path display, a "Choose folder…" button (`JFileChooser`, `DIRECTORIES_ONLY`, invoked off the
  Compose UI thread since it blocks), a session-only passphrase field (intentionally not persisted —
  building secure passphrase storage on desktop is out of scope for this milestone), and "Sync now".
  Milestone 1's `seedIfEmpty` demo function was removed entirely — with real sync now in place, it
  would otherwise write fake "Welcome to Tendril desktop" pages into a real synced folder the moment
  someone picked one and hit Sync.

**Gradle finding: the `jvmCommon` intermediate source set.** `SnapshotEncryption` needs
`javax.crypto`/`java.security`, which aren't visible from true KMP `commonMain` even though both of
`shared`'s targets happen to be JVM-based. Fixed with a manual intermediate source set
(`val jvmCommon by creating { dependsOn(commonMain.get()) }`, `androidMain`/`desktopMain` each
`dependsOn(jvmCommon)`), which required adding `applyDefaultHierarchyTemplate()` first (a manual
`dependsOn` edge silently drops the free `commonMain→androidMain/desktopMain` edges otherwise). One
real gotcha avoided: there's a confirmed open JetBrains bug (KT-80409) against the *other* commonly
documented way to declare a custom intermediate group
(`applyDefaultHierarchyTemplate { common { group("jvmCommon") { ... } } }`) specifically for the
`com.android.kotlin.multiplatform.library` plugin `shared` uses — the `by creating` + explicit
`dependsOn` form used here is a different, unaffected code path. Also needed: `shared`'s `androidMain`
gained a direct `androidx.documentfile` dependency (previously only declared in `Tendril android`'s
own `app/build.gradle.kts`, not in `shared` — the compiler caught this immediately as an unresolved
`DocumentFile` reference).

**§3 gate re-affirmed, not triggered — see §3's own updated text.** Desktop still can't edit
anything; every write this milestone performs is a write-back of just-merged-in remote data, never a
locally-originated edit, so the concurrent-edit collision risk §3 flags still isn't load-bearing.

**Verification.** All three builds (`shared`, `Tendril android`, `Tendril windows`) succeed. Since
the desktop UI can't be driven programmatically in this environment (an ad-hoc Gradle-launched
window, not a Start-Menu-registered app the available automation tooling can grant access to — see
§5's screenshot workaround for Milestone 1), sync itself was verified with a throwaway headless
script exercising `SnapshotSyncOrchestrator`/`DesktopFileSyncFileStore` directly against a real temp
folder (written, then deleted, once verification passed — not part of the shipped app): confirmed
`writeSnapshots` produces the four expected plain-JSON files with correct content; a hand-edited
`habits.json` with a newer `updatedAt` gets adopted by `readAndMerge`; a `*.sync-conflict-*` file
gets merged then deleted; an encrypted `writeSnapshots` output starts with the `TDRLENC1` magic
prefix; a fresh database with the correct passphrase decrypts and merges correctly; a fresh database
with the wrong passphrase merges nothing and doesn't crash. All six checks passed.

## 8. Milestone 3 — Workbench UI port, first slice (Implemented 2026-08-30)

**What was built.** Theming, the 5-tab nav shell, and the actual block editor (Pages list,
PageDetailScreen, PageDatabaseScreen) moved from `Tendril android\`'s `:app` module into
`shared/src/commonMain/`, so they compile once and render on both Android and desktop — the
"shared UI" half of §2's original platform-strategy decision, not just shared domain/data. Scope
boundary, deliberate: Calendar, Tasks & Habits, Road Map, and Settings (all Android-integration-heavy
— `AlarmManager`, Calendar Provider, Google Calendar, Notion import, `BiometricPrompt`) and the
Canvas page kind are **not** ported this pass; `WorkbenchScaffold` takes them as composable slot
parameters so the shared nav shell never references an Android-only screen, and `Tendril windows`
supplies a plain `NotAvailableOnDesktop(...)` placeholder for each. Tracked as the remaining open
item under build-sequence step 3 (§6). *(**2026-09-13:** of those, only **Settings** is still a
stand-in — Canvas (2026-09-11), Calendar, Tasks & Habits (2026-09-12) and Road Map (2026-09-13)
have all moved to `shared/`, and the slots for the last three are gone; see the Revision Log.
**2026-09-13, later:** Settings too — a minimal desktop pane (§0.6.15). No stand-ins remain.)*

*The shell's desktop revision — a rail instead of the bottom bar, slide-overs instead of sheets,
tree + page, a pointer density, the fixed keyboard set, a remembered window, the theme — is
planned in `docs/benchmarks.md` §13 (2026-09-13) and answered on `docs/mockups/desktop-shell.html`;
`tendril-spec.md` §0.10 item 14 tracks it. **14a shipped 2026-09-14** — the shell as mocked
(`tendril-spec.md` §2.2), the remembered window, the title; the sync controls now live in
Settings. **14b shipped 2026-09-14** — sheets are right slide-overs on a wide window
(`tendril-spec.md` §3, *Bottom sheets*). **14c·0 shipped 2026-09-15** — the density scale (`tendril-spec.md` §2.2), the desktop's
*Density* setting. **14c shipped 2026-09-15** — Pages as two panes (`tendril-spec.md` §3.1).
**14d shipped 2026-09-15** — under a pointer (`tendril-spec.md` §2.2): hover `···`, right-click,
the floating toolbar. **14e shipped 2026-09-16** — the keyboard (`tendril-spec.md` §2.2 *The
keyboard*; the set is in this file's 2026-09-16 row). **Find in page shipped 2026-09-16** (Ctrl+F,
`tendril-spec.md` §3.1.1). **14f·1 shipped 2026-09-16** — Tasks as a desktop surface
(`tendril-spec.md` §3.3). **14f·2 shipped 2026-09-16** — the Calendar's week grid
(`tendril-spec.md` §3.2). The pass's surfaces are done. **14g·1 shipped 2026-09-16** — the theme
model: ten registers, mode System / Light / Dark following Windows, the theme in `prefs.properties`
(`tendril-spec.md` §2.3). **14g·2 shipped 2026-09-16** — the token map: three data hues fanned
from the accent, the error family, the find mark, labels and callouts rendered by the register, no
literal colour left in shared UI (`tendril-spec.md` §2.3 *The token map*). **14g·3 shipped
2026-09-16** — the urgency ladder: `importance` 0–4 (v20), the stripe, the Tasks switches in the
pane (`tendril-spec.md` §0.6.4). **14g is done.** **14h·1 shipped 2026-09-16** — the shelf:
a third pane beside the page (another page, the Road Map neighbourhood, today's Journal), Ctrl+Shift+\,
its keys in `prefs.properties` (`tendril-spec.md` §3.1). **14h·2 shipped 2026-09-16** — the thirteen
small things (`tendril-spec.md` §0.10 item 14, closed). **The pass is done: 14a…14h.** **Pop-out
windows shipped 2026-09-16** (B§13.6 #6): a page in its own window, remembered, at the main window's
scale (`tendril-spec.md` §3.1). **Drag between panes shipped 2026-09-16** (B§13.6 #5): the
Calendar's task tray beside the week, the drops the existing writes, the Timeline's *No date* rows
onto a day (`tendril-spec.md` §3.2, §0.6.14). **Hover previews shipped 2026-09-16** (B§13.6 #3):
a card under an `@mention`, a mention block, a block reference (its line in context) or a Road Map
node after 500 ms, desktop only (`tendril-spec.md` §3.1.1). **The type vocabulary shipped
2026-09-16**: Inter at true weights, seven styles, headers bold not large (`tendril-spec.md` §2.3).
**The tray shipped 2026-09-16** (B§13.6 #7): the notification-area icon, × hides, reminder toasts,
the global quick-add chord and its popup, the bell (`tendril-spec.md` §2.2 *The notification area*).
**The phone's fixes shipped 2026-09-18** — every sheet scrolls (rule 20), a Touch submenu pushes a level, the canvas's `···`, the rows' meta, a layout test. **The phone catch-up ran 2026-09-18** — the 25 pending phone halves walked; the phone had crashed on launch since 14h·2 (a Touch-only padding), fixed; five findings to a fix PR, then the phone audit (§0.10 item 23). **L7b shipped 2026-09-18** — the quick-add tokens tinted in the line on `findSoft`, the chips kept (Todoist's, measured); **§0.10 item 22 is closed**. **Small things III shipped 2026-09-18** — L11–L14, F9–F14; item 22's Med/Low list closed. **The database's views shipped 2026-09-18** — F4–F8: *Delete view…* asks, the ▾ on the active chip, a new view makes the property it plots by (Notion's), the New view sheet, one `···` vocabulary. **The last Material frames shipped 2026-09-18** — every field a `TendrilField`, the Add dialogs as sheets, the date picker's headline tamed, ellipsis everywhere, a sheet's title at `pageTitle`, a slide-over's content under the caption row. **The design layer shipped 2026-09-18** — D4–D8: the light hover perceptible, the Road Map's edges at full alpha, two idle tokens gone, one radius family through Material's shapes (rule 17), keyboard focus visible. **L9 + L10 shipped 2026-09-17** — the canvas's cards under a pointer: *Fit* and `+` in the bar, a double-click on the ground with the discard rule, the badges on hover or selection; **`EscapeBackInput` deleted** — the runtime dispatches Escape itself, the app's input had doubled every back. **L7 + L8 shipped 2026-09-17** — the quick-add strip on the find bar's field with the chips inline, the popup on the switcher's field, the tray ≤ 30 % of the pane, the Week header's minutes below a 90 dp lane. **L6 shipped 2026-09-17** — the switcher is a centred card on a wide window (Recents, sections, chords, a footer), the desktop's text field is `TendrilField` (the AI key, the sync passphrase, the find bar), the F1 card and the switcher share `CentredCard`. **L5 shipped 2026-09-17** — the window is borderless on the JetBrains Runtime (the bar is the title bar), every tab wears one bar, the time grid has Notion Calendar's proportions; the toolchain is Android Studio's JBR from this PR on. **The desktop audit ran 2026-09-17**: three full passes over every surface — `docs/critiques/desktop-layout-full.md`, `desktop-type-full.md`, `desktop-function-full.md`; the disposition in `tendril-spec.md` §0.10 item 22. **The audit's fixes shipped 2026-09-17**: menus, rows, the type classes and their audit rules, the copy, the find gate, the F1 card, the habit pane (`docs/critiques/desktop-audit-fixes.md`). **The Month grid shipped 2026-09-17** (L4; `docs/critiques/month-grid-mock.md`, `month-grid-function.md`). Next: item 22's Med/Low list — L5 (the Calendar's chrome) first.*

- **`WorkbenchCore`** (new, `shared`): a plain grouping class — `TendrilDatabase`,
  `DatabaseSyncManager`, `TemplateManager`, `ViewLockState`, `CheckboxOnlyState`,
  `ResolveEntryUseCase`, `EntryScheduleCoordinator`, `PageContentRepository`, and — **added
  2026-09-04, when purge tombstones started travelling between devices instead of staying on the
  machine that made them** — `PurgeRegistry` — the slice of
  Android's `AppContainer` the ported screens actually depend on. `AppContainer` itself stays
  Android-only (it also builds Context-only services with no desktop equivalent) and now just
  holds one `WorkbenchCore` instance; `Tendril windows`'s new `DesktopAppContainer` builds an
  equivalent one from its own database, with a no-op `EntryScheduleCoordinator` (no
  alarms/Calendar Provider on desktop, §1). It builds its own `PurgeRegistry` over that same
  no-op coordinator, which is where the coordinator's defaulted `onHabitRemoved` earns its
  default: a "Delete forever" arriving from the phone has an alarm to cancel there and nothing
  to cancel here.
- **Theming** (`ui/theme/{Palette,Theme,Type}.kt`, moved): `Palette.kt`'s one Android call
  (`android.graphics.Color.parseColor`) became a plain hex parser. `Type.kt`'s Android
  `R.font`/`FontVariation` weight-axis loading has no multiplatform equivalent — rewritten
  against Compose Multiplatform resources (`Res.font.*`), which cost the true variable-weight
  rendering (each bundled font now loads once at its default instance, per family, plus its
  italic file; Compose's synthetic bold fills in other weights) — visually close, not
  pixel-identical to Android's build; flagged as a possible future gap, not fixed here.
- **Compose Multiplatform resources adopted in `shared`** (new `compose.resources` config,
  `packageOfResClass = "com.tendril.app.generated.resources"`, module-internal — nothing outside
  `shared` references `Res.*`): fonts under `composeResources/font/`, and the handful of strings
  the moved screens use (`nav_*`, `empty_pages_*`, `empty_trash_message`) under
  `composeResources/values/strings.xml` — a small, deliberately partial copy of Android's
  `strings.xml`, not a move; everything else stays Android-only until its screen is ported.
- **Nav shell — hand-rolled, not navigation-compose.** `org.jetbrains.androidx.navigation:navigation-compose`
  (the Compose-Multiplatform-published artifact) is still alpha/beta-only at this project's
  Compose Multiplatform 1.12.0 pin as of 2026-08-30 (confirmed via live lookup, not assumed —
  matching §5/§7's own toolchain-verification practice) — adopting a pre-1.0 external nav library
  for a production nav shell was judged worse than hand-rolling, given how simple the actual need
  is (5 fixed tabs + one parameterized page-detail push). New `WorkbenchNavState`
  (`mutableStateListOf`-backed back stack) replaces `NavController`/`NavHost`/`rememberNavController`.
  Known simplification versus the old Navigation-Compose-backed shell: switching tabs always
  drops any page pushed on top of the *previous* tab (no per-tab saveState/restoreState) — each
  tab simply reopens at its own root. Revisit navigation-compose once it reaches a real stable
  release.
- **Two Android-only capabilities threaded as nullable callbacks, not `expect`/`actual`**
  (matching §1's "no desktop analog" precedent for App Lock): checkbox-only's lock-screen-bypass
  window flags (`Activity.setShowWhenLocked`/`setTurnScreenOn`) and its "turn off" `BiometricPrompt`
  unlock. Both live in a new Android-only wrapper, `AndroidWorkbenchScaffold.kt` (in `Tendril
  android\`'s `:app`, holds the `LocalActivity` read the shared file no longer needs), which also
  adds an explicit `BackHandler` for the system back gesture — Navigation Compose used to supply
  this for free; the hand-rolled `WorkbenchNavState` needs it wired explicitly. `MainActivity.kt`
  now calls `AndroidWorkbenchScaffold(container)` instead of calling the shared scaffold directly.
- **Desktop ViewModel wiring gaps found only during implementation, not anticipated by planning**:
  (1) Compose Multiplatform has no default `ViewModelStoreOwner` outside `NavHost` — new
  `DesktopViewModelStoreOwner` (one per app, `remember`ed in `Main.kt`'s `App()`) provided via
  `LocalViewModelStoreOwner`. (2) `lifecycle-viewmodel-compose` had to be declared `api`, not
  `implementation`, in `shared/build.gradle.kts` — `Tendril windows`'s own `Main.kt` needs
  `ViewModelStore`/`ViewModelStoreOwner`/`LocalViewModelStoreOwner` directly, which
  `implementation` doesn't expose transitively. (3) `ViewModel.viewModelScope`'s coroutine
  dispatcher needs `kotlinx-coroutines-swing` on the desktop classpath to resolve
  `Dispatchers.Main.immediate` — added to `Tendril windows/build.gradle.kts`.
- **`Tendril windows\`'s `Main.kt`**: rewritten to render `TendrilTheme { WorkbenchScaffold(...) }`
  instead of Milestone 1's flat two-pane viewer, with the existing folder-sync bar kept as a thin
  strip above the nav shell (its natural home, the Settings tab, isn't ported yet). Theme is fixed
  (Ink/Light/Sans) — no Settings screen yet to pick one on desktop.
- **`EnableSyncSheet`** (in the moved `PageDatabaseScreen.kt`) had its `internal` visibility
  dropped to public: Android's `NotionImportSection.kt` (Settings, not ported) reuses it verbatim
  across what's now a module boundary, and `internal` would have hidden it.
- **`shared/gradle.properties`** (new): `kotlin.daemon.jvmargs=-Xmx4096m` — a real
  `OutOfMemoryError` during `:compileKotlinDesktop` (Compose codegen + coroutine state machines
  in the newly-moved `PageDetailScreen`/`PageDatabaseScreen` pushed past the Kotlin daemon's
  default heap) forced this before the module would build at all.

**Regression check.** `Tendril android`'s `:app:assembleDebug` and `:app:testDebugUnitTest` pass
unchanged. `shared`'s `./gradlew build` compiles and runs KSP for both targets. `Tendril windows`'s
`./gradlew build` succeeds, and `./gradlew run` launches without a startup exception (checked via
its process and log output, not a stack trace).

**Visual verification: same automation limitation as Milestone 1 (§5), worked around the same
way.** This is still an ad-hoc Gradle-launched window, not a Start-Menu-registered app — this
environment's screenshot/interaction automation can't attach to it (confirmed by trying:
`request_access` against every plausible process/window name — the exact window title, `java`,
`javaw`, "OpenJDK Platform binary", "Java Platform SE binary", and the bare name "Tendril" — all
returned no match). As in Milestone 1, verification happened via the user driving the window
directly and reporting back, not Claude.

Confirmed by screenshot: the 5-tab nav shell (correct icons/labels via `Res.string.*`), the sync
bar, and the Pages screen (search/journal/View-Only/more icons, FAB, page list rendering the dev
database's existing pages). The bottom nav bar's purple-tinted container/selection-pill is
expected, not a porting bug — `TendrilTheme.applyPalette` only remaps a subset of `ColorScheme`
roles (§2.3's token set has no slot for `secondaryContainer`/`surfaceContainer`), so
`NavigationBar`'s default tint shows through exactly as it does in the pre-port Android build too.

**Confirmed by the user manually exercising it: the block editor genuinely works on desktop.**
Opened "Second page" (`WorkbenchNavState.openPage` → `PageDetailScreen`), typed into a block
(`PageDetailViewModel.updateBlockContent` → Room write → `BundledSQLiteDriver`), pressed back
(`WorkbenchNavState.back()`, popping to the Pages tab root), then reopened "Second page" and the
edit was still there — a real write/read round-trip through desktop's own database file, not
just a compile check. This is the first confirmed instance of desktop actually originating a
locally-persisted edit (as opposed to a sync write-back), which is exactly the scenario §3's gate
is about — still not a *concurrent* collision (nothing else was writing to this page at the same
time), but the mechanism §3 flags is now demonstrated to work end-to-end, not just theorized.

Still not click-verified: in-block formatting (bold/italic/slash-command menu/mentions),
PageDatabaseScreen's table view, and the four placeholder tabs correctly showing
`NotAvailableOnDesktop` rather than crashing.
