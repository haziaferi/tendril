# Tendril — Product & Technical Specification

**Status:** living document, consolidated from design conversation. Sections are marked **Decided**,
**Open**, or **Deferred** — treat anything not marked Decided as unsettled, even if it reads
confidently. App name **decided 2026-08-04: Tendril** (previously carried as the "Noema" working
title). The 2026-08-04 Revision Log entry claimed the document was "retitled throughout"; it wasn't —
22 prose references to the old name survived until 2026-09-04, when they were finally replaced. The
two that remain, here and in that log row, are deliberate: they are *about* the rename. The
prototype **filenames** below still start `noema-`, because those are real files on disk.

**Reference artifacts** (interactive HTML prototypes, ground truth for pixel-level detail). They
live at the **repository root — beside this file** since §0.10 item 9 moved it there (2026-09-18); the
`../` in the paths below dates from when the file sat under `Tendril android/` and is kept only so the
2026-09-04 correction still reads.
*(**Corrected 2026-09-04:** this list named `noema-nav-prototype.html`, which is on disk under a
browser-numbered name, and `noema-accent2-fallback-comparison.html`, which is not in the repository
at all — while asserting the names were left alone because they were "real files on disk." Both
claims were checked against the tree and corrected.)*
- `../noema-nav-prototype (4).html` — full app shell, 4 navigation explorations, Workbench theming
  system. The " (4)" is part of the real filename.
- `../noema-widgets-audit.html` — home-screen widget designs, live contrast-audit tool
- ~~`noema-accent2-fallback-comparison.html`~~ — scratch tool from the widget-color investigation,
  superseded by the shade/hue system built into the widgets file. **Not in the repository**; treat
  every reference to it as historical.

---

## Revision Log

Full reasoning for every entry below lives in its home section, tagged
**Decided**/**Confirmed**/**Reopened**/**Corrected** there — this table is a pointer index, not a
second copy of the reasoning.

| Date | Summary | Sections touched |
|---|---|---|
| 2026-07-13 (pass 1) | Pre-build gap-analysis: SAF replaces Shizuku, sync/backup design, targetSdk 36 behavior changes, notification scope, Task/Event single-table schema, Habits confirmed separate, to-do eligibility, Road Map data model, Calendar→Week hour-grid, block-editor scope drafted | §2.2, §3.1.1, §3.3, §3.4, §3.5, §4, §5.3, §9.2.1, §9.3, §9.4, §9.7, §9.9 |
| 2026-07-13 (pass 2) | Block editor scope confirmed Notion-lite (Pareto analysis); Calendar Provider vs. Google sync decided independent (dominance case) | §3.1.1, §3.2 |
| 2026-07-14 (correction) | Task/Event split via `kind: TASK \| EVENT` (renamed `Task` → `Entry`) — fixes non-actionable items (birthdays, holidays) wrongly appearing as Tasks | §4 |
| 2026-07-14 (round 2) | Multi-day span, TASK/EVENT recurrence split, `status` as tri-state, all-day reminder time-anchor, overdue notification | §4, §4.1, §9.7 |
| 2026-07-14 (round 3 + architecture audit) | Multi-day restricted to EVENT; per-occurrence recurring-EVENT durations; single-occurrence exceptions; typed `RecurrenceRule`; `EntryCompletion` history log; centralized `ResolveEntryUseCase`; `AlarmScheduler`; boot-completed permission | §4.1, §5.2, §9.7, §9.8 |
| 2026-07-15 | Property/Row lifecycle confirm dialogs (type conversion, deletion, sync toggle-off); Editing/Viewing draft model proposed and architecture-reviewed (later withdrawn — see next entry) | §5.5, §9.8.1 |
| 2026-07-15 (correction, same day) | Editing/Viewing/draft model withdrawn; replaced with View-Only lock + checkbox-only mode; Pages back to instant-write; Page-snapshot sync timing reopened | §3.1.2, §5.5, §9.4 |
| 2026-07-16 | Retroactive bind/unbind/rebind mechanism; `Entry.source_row_id` named; `recurrence_property_id` binding + new `Interval` property type; alarm-flood fix + batched summary notification; portable export/import split from continuous sync (additive Import vs. full-replace Restore) | §4, §5.2.1, §5.2.2, §9.4.1, §9.7 |
| 2026-08-04 | Page-snapshot sync timing resolved (2s debounce + navigate-away/backgrounding flush). App-naming reopened — "Noema" was only ever a placeholder working title, not a considered decision (§9.4.1 reverted to `.<app>`). Five further open items logged, none blocking Phase 1: Room migration policy, accessibility scope, first-run/empty states, widget config flow, in-app search UX | §9.4, §9.4.1, §10 |
| 2026-08-04 (later same day) | App name decided: **Tendril**, export extension `.tendril` — closes the app-naming open item. Document retitled throughout | title, §9.4.1, §10 |
| 2026-08-08 | Cross-reference pass against Anytype/AppFlowy/Logseq/Joplin/Super Productivity/Notesnook/SiYuan. Eight additions: page templates, daily journal, backlinks panel, and Tags (resolving the undefined `category` field) added to Pages; App Lock added as a new cross-cutting security feature; Trash/soft-delete supersedes the "no way to back out" deletion limitation; at-rest snapshot encryption added as an optional sync-folder protection; database views (Board/Gallery/Calendar) reopens and reverses the earlier "decided out of scope" table-only-database limitation | §3.1.3, §3.1.4, §3.1.5, §3.1.6, §3.6, §4, §5.5.1, §5.6, §9.4.2, §9.9, §10 |
| 2026-08-08 (correction, later same day) | Trash's 30-day auto-purge removed — retention is indefinite until the person manually chooses Delete forever. Reopens the question of whether Entry/Habit should move from shared-array snapshot files to per-record files (matching Page/Row), given deleted records no longer age out and Trash now grows unbounded — not yet decided, see §9.4 | §5.5.1, §9.4 |
| 2026-08-08 (further same day) | Trash gains multi-select + "Select all" bulk Restore/Delete forever, closing a real Notion gap. Optimizer run (5 candidates, weighted write-amplification/file-count/complexity/query-ease/growth-safety) resolves the previous entry's open question: `tasks_events.json` splits into `entries_active.json` + `entries_archived.json` (0.87 fitness, beating monthly sharding and per-record alternatives) — `habits.json` stays a single file, its record count never approaches the volume that motivated the split | §5.5.1, §9.4 |
| 2026-08-26 | All five remaining §10 open items resolved via weighted-criteria scoring against the prior-art already cited elsewhere in this doc (Fossify Calendar, Notion/AppFlowy/Anytype/Joplin, MedTimer, Catima): accessibility scope (§2.4), first-run/empty states (§2.5), in-app search UX (§3.1.7), widget configuration flow (§8.7), Room schema migration policy (§9.10). §9.9's phase list annotated with each decision's gating point so nothing is resolved earlier or later than the phase that actually needs it. Nothing now blocks Phase 1. | §2.4, §2.5, §3.1.7, §8.7, §9.9, §9.10, §10, §11 |
| 2026-08-29 | Desktop companion explored as a future direction, not scheduled into §9.9. Feasibility confirmed against the existing snapshot-sync design (§9.4) — no new sync mechanism needed, SAF has no desktop equivalent and none is required. Platform strategy scored: Kotlin Multiplatform + Compose Multiplatform chosen (0.808) over an independent-stack client, a KMP-domain-only split, and deferring entirely. New §12 added as a dedicated in-document section rather than a separate spec file — that documentation-structure choice itself scored via `meta-optimizer` (Harmony Search over four discrete candidates), with an explicit graduation trigger tied to §9.9. | §9.4, §10, §12 |
| 2026-08-29 (later same day) | Correction, stated plainly: Google Calendar sync's OAuth mechanism (§9.5) doesn't need a user-entered client ID field (§3.2) or a stored refresh token (§3.5) after all — checking the real `AuthorizationClient` API before implementing found `requestOfflineAccess()` requires a backend-held Web-client secret this local-first app doesn't have. Corrected to the on-device Android-type-client flow (auto-resolved via package name + signing certificate, short-lived tokens re-requested silently, nothing persisted) while OAuth setup was actually being built. Google Cloud Console setup (Production consent screen, Android-type client registration) is unchanged. | §3.2, §3.5, §9.5 |
| 2026-08-29 (further same day) | Google Calendar sync engine implemented (new §9.5.1): EVENT-only (Tasks never sync, matching §4.1's RRULE-vs-elastic recurrence split), `primary` calendar only for v1, incremental via `updatedMin`/`showDeleted` rather than syncToken, push-before-pull with an accepted concurrent-edit trade-off mirroring §9.4's own Page-level LWW limitation, and a Trash↔Google-delete interaction consistent with Trash's reversible philosophy (§5.5.1). `Entry.googleEventId` added (Room v3→v4, destructive pre-v1 migration per §9.10) and threaded through the snapshot record (§9.4) so the link survives cross-device sync. | §3.2, §4.1, §5.5.1, §9.4, §9.5.1, §9.10 |
| 2026-08-29 (yet further same day) | System Calendar Provider registration implemented (new §9.11), correcting §3.2's original "sync-adapter-backed account" wording — `CalendarContract.ACCOUNT_TYPE_LOCAL` needs no real `Account`/`AbstractAccountAuthenticator`/`AbstractThreadedSyncAdapter` at all, simpler than planned. Both TASK and EVENT mirror (broader than §9.5.1's EVENT-only Google sync); Google-sourced Entries excluded; recurring EVENT/TASK handled per §4.1/§9.7's existing RRULE/elastic split. New `EntryScheduleCoordinator` centralizes the Provider mirror alongside `AlarmScheduler` across nine call sites, the same fan-out problem §9.8 R1 already fixed once for Entry resolution. `Entry.providerEventId` added (Room v4→v5) — deliberately excluded from the cross-device snapshot record, unlike `googleEventId`, since each device's Calendar Provider is its own independent database. | §3.2, §4.1, §9.4, §9.5.1, §9.7, §9.8, §9.10, §9.11 |
| 2026-08-29 (one more same day) | Habits quick-check widget added (new §8.1.1) — a fourth widget type beyond the three originally prototyped, closing a gap against Loop/uhabits prior art (checking a habit off from the home screen, their core interaction, had no equivalent here). Reuses §8's existing theming/config infrastructure unchanged; the check-in streak math was extracted into `CheckInHabitUseCase` so the widget's Glance `ActionCallback` and the in-app Tasks & Habits screen share one implementation rather than two, matching the centralization principle §9.8 R1 and §9.11 already established. | §6.1, §8.1.1, §8.7, §9.6, §9.8, §9.11 |
| 2026-08-30 | Phase 5 gap-closing pass, following an audit that found the Pages/Notion-like system (§3.1, §5, §7) already substantially built from prior work, contradicting the assumption it was still pending. Two of the three real gaps found are now closed: View-Only lock was a no-op past the Pages hub itself (never reached `PageDetailScreen`/`PageDatabaseScreen`) — fixed via a shared `ViewLockState` plus a `LocalViewOnly` CompositionLocal, enforced both in the UI and, defense-in-depth, inside every mutating ViewModel function. Checkbox-only mode (§3.1.2) was entirely unbuilt — implemented with a `CheckboxOnlyState` (one active page at a time) and `WorkbenchScaffold`-level `Activity.setShowWhenLocked`/`setTurnScreenOn` wiring gated on the active page id matching the current nav destination. §5.2.1's rebind/unbind UI gap (domain logic existed, unreachable from any screen) was also closed via a property header-cell "Change binding…" menu. Notion import (§7, wholly greenfield) remains open. | §3.1.2, §5.2.1, §9.9 |
| 2026-08-30 (later same day) | Notion import architecture decided (new §7.4), scored via `optimization-engines:meta-optimizer` (Harmony Search over four discrete dimensions — Markdown parsing, CSV parsing, link-remapping mechanism, property-type-inference strategy). Chosen: a hand-rolled line-oriented streaming state machine emitting `Block`/`FormattingSpan` directly (no generic Markdown AST library), a small hand-rolled RFC4180 CSV parser, two-pass link remapping (build the full Notion-ID→Tendril-ID map before parsing any body content, rather than lazy or patch-based resolution), and hybrid property-type inference (heuristic guess, confirmed through the existing §5.2/§5.2.1 binding-step UI). Scored 0.832, beating a library-based candidate (0.678) mainly on build-footprint and fit with this codebase's existing bespoke Block model, once two-pass linking had already closed the bigger fidelity risk (dangling links) a better Markdown parser wouldn't have touched. No code written yet — this is the architecture decision only. | §7.4 |
| 2026-08-30 (yet later same day) | Notion import (§7) implemented against the §7.4 architecture decision: `com.tendril.app.notionimport` (`NotionMarkdownParser`/`NotionCsvParser`/`NotionPropertyTypeInference`/`NotionImporter`) plus a Settings entry point reusing `EnableSyncSheet` (§5.2) for the post-import Sync-to-Tasks step. Two gaps found only during implementation, not anticipated by §7.1-§7.3: no native Table `BlockType` exists (tables degrade to a verbatim Code block, the same trade-off already accepted for Callouts); nested-block rendering was never actually built in the block editor, so imported nesting (sub-lists, toggle children) flattens to a top-level sequence rather than risking silently-invisible content. This project's first test file (`NotionImportParsingTest`) exercises the three pure-logic classes and caught a real bug during development — internal links resolving to a linked page's *parent folder's* Notion id instead of its own, since a nested export path carries both; fixed by taking the link href's last 32-hex match, not its first. `assembleDebug` verified clean against a synthetic export; full on-device Settings-UI exercise wasn't completed (the only available device was in active personal use). | §7, §7.4 |
| 2026-08-30 (final same day) | Phase 5 gap-closing pass (View-Only lock/checkbox-only enforcement, Habits widget, Calendar Provider mirror, Google Calendar sync, Notion import) is complete and verified (`assembleDebug` clean, `NotionImportParsingTest` passing) but exists only as an uncommitted local working tree — this environment has no git author identity configured and no git hosting account, so the changes were never committed. Recorded here rather than in git history; see §11. | §11 |
| 2026-08-30 (following day, pass 1) | Git dropped entirely (no git account) after the working tree was found physically reorganized outside of git's knowledge into a `Tendril android\` subfolder — rather than reconcile tracking to the new location, `.git` was deleted and this spec's Revision Log becomes the sole change history going forward. **(Superseded 2026-09-04 — the project is under git again; see that entry and §11.)** Superseding the previous entry's framing: nothing is "uncommitted" any more, because nothing is tracked at all. | §11 |
| 2026-08-30 (following day, pass 2) | Desktop Companion (§12) started, in dependency order, now that every §9.9 phase is implemented. §12.2's KMP + Compose Multiplatform decision honored, but via a Gradle composite build across three sibling folders (`Tendril android\`, `shared\`, `Tendril windows\`) rather than a monorepo submodule — the user asked for two separate top-level folders (Android APK vs. Windows EXE), not one. Milestone 1 implemented: the pure domain/data/sync-merge layer moved into `shared\` (Room 2.8.4, `android` + `desktop` targets via `com.android.kotlin.multiplatform.library`, a real AGP-9 correction found during planning), a minimal read-only Compose Multiplatform desktop viewer built and verified live (`./gradlew run`, including an FTS4-under-`BundledSQLiteDriver` smoke test), `EntryScheduleCoordinator` turned into a shared interface with an `AndroidEntryScheduleCoordinator` implementation (the one dependency-shape gap found only during the move), and five cross-module smart-cast compile errors fixed in `:app`. §12.3's block-level-merge question stays open, now explicitly gated to the *next* desktop milestone (folder-sync-on-desktop) rather than this one. See §12.5 (this content later moved — see next entry). | §11, §12, §12.3, §12.5 |
| 2026-08-30 (following day, pass 3) | Milestone 1 verified live on-screen (a real screenshot of the running "Tendril (desktop preview)" window, confirming the seeded pages render — not just the log-based smoke test the previous entry recorded), then closed. Desktop Companion content graduated out of this file into its own `tendril-windows-spec.md` (in `Tendril windows\`), per the user's explicit request and §12.4's graduation trigger — the former §12.1–§12.5 moved there as §1–§6, this file's §12 shrank to a pointer plus a new anti-drift rule (repeated verbatim in both files): any change touching `shared\` code gets a same-day Revision Log entry in *both* files, so the two documents can't silently diverge on the model they share. This rule is the actual mitigation for the drift risk §12.4's original scoring table penalized a full split for — the split itself doesn't prevent drift, the rule does. | §11, §12 |
| 2026-08-30 (following day, pass 4) | Anti-drift-rule entry: Milestone 2 (folder-sync-on-desktop) implemented — full reasoning and detail in `tendril-windows-spec.md` §7, not repeated here per that file's §0. Summary only, since this touches `shared\`: Android's `SnapshotSyncManager`/`SnapshotEncryption` moved into a new `shared/jvmCommon` intermediate source set (a real Gradle finding — `javax.crypto` isn't visible from true KMP `commonMain` even though both targets are JVM-based) behind a new `SyncFileStore` interface, with `AndroidSafSyncFileStore`/`DesktopFileSyncFileStore` platform implementations; `Tendril android`'s two deleted files' logic is now `SnapshotSyncOrchestrator`, called via `AndroidSafSyncFileStore` from `AppContainer.kt`/`SettingsScreen.kt` with no behavior change (`assembleDebug`/`testDebugUnitTest` pass unchanged). | §9.4, §12 |
| 2026-08-30 (following day, pass 5) | Anti-drift-rule entry: Milestone 3 (Workbench UI port), first slice, implemented — full reasoning and detail in `tendril-windows-spec.md` §8, not repeated here per that file's §0. Summary only, since this touches `shared\`: theming (`ui/theme/`), the nav shell (`ui/nav/WorkbenchScaffold.kt` + new hand-rolled `WorkbenchNavState`, not navigation-compose — still alpha/beta-only for Compose Multiplatform at this project's pin), and the block editor (`PagesScreen`/`PageDetailScreen`/`PageDatabaseScreen` + their ViewModels) moved from `:app` into `shared/src/commonMain/`, now rendering on both Android and desktop from one implementation. New `WorkbenchCore` groups the shared pieces these screens need; `AppContainer.kt` now holds one, `MainActivity.kt` calls a new Android-only `AndroidWorkbenchScaffold` wrapper (holds the `Activity`/`BiometricPrompt` calls the shared file no longer can) instead of the old `WorkbenchScaffold` directly — `assembleDebug`/`testDebugUnitTest` pass unchanged. Calendar/Tasks & Habits/Road Map/Settings/Canvas are not ported this pass (Android-integration-heavy, out of scope) — desktop renders a placeholder for each via `WorkbenchScaffold`'s new slot parameters. | §9.4, §12 |
| 2026-09-04 | **Corrected:** the 2026-08-30 "No version control" decision is reversed — the project is now under git in a single repository (`haziaferi/tendril`) spanning all three sibling folders. One repo rather than three because both consumers resolve the shared core as `includeBuild("../shared")`, a relative sibling path only a single clone reproduces; a submodule would have to nest `shared\` and break both build files. Revision Log keeps its role for *why*; `git log` covers *what changed when*. Build/setup instructions moved out of this spec into `README.md` at the repository root. | §11 |
| 2026-09-04 (later same day) | **Consistency audit of the whole app against this document — corrections, not new scope.** Nine spec-internal contradictions fixed: §6.2's anchoring rule and §4.1's "elastic" gloss asserted opposite recurrence semantics (§6.2 wins; §4.1's sentence withdrawn, `RecurrenceRule.Elastic` acknowledged as a kept misnomer, and §4.1's justification for event-driven alarms restated on grounds that actually hold); §5.5.1 still carried "auto-purge"/"recoverable for 30 days" in two bullets the 2026-08-08 no-auto-purge correction never reached; §9.10's Acceptance block was printed at the end of §9.11, so §9.10 had none and §9.11 acceptance-tested a different section (both now have their own); §3.6's "widgets show only summary data, not editable content" was written three weeks before §8.1.1 added a widget that writes; §3.4/§10 said the in-page mind-map "does not exist yet / not started" while a full Canvas page kind ships (three Room tables, a screen, snapshot sync) with no Revision Log entry at all — an anti-drift-rule breach, since it lives in `shared\`; §8.1 said "four density tiers" and named three; §8.3 listed the Monthly grid's weekday letters as accent2 while §8.4's own fix #2 reassigns them to `textDim`; §8.3's "rotation preserves lightness" rule never recorded the two palettes darkened to clear AA; §1 still opened "Noema is a personal productivity Android app" and 22 further prose references to the old name survived a Revision Log entry claiming the doc was "retitled throughout"; the reference-artifact list named one file under the wrong name and one that isn't in the repository. Code fixes in the same pass — Room `version` left at 5 after three Canvas tables were added (identity-hash crash on open, which `fallbackToDestructiveMigration` cannot catch); a recurring task resolved late advanced to a date still in the past; unchecking a task in Tasks/Calendar wrote a second terminal resolution instead of undoing; the boot sweep rescheduled TASKs only, losing every EVENT reminder at reboot; an undecryptable sync folder was overwritten rather than left alone; FTS returned trashed pages and mis-ordered `snippet()`'s arguments; the Habits widget wrote to Room with App Lock on. See §11. | title, §1, §3.4, §3.6, §4.1, §5.5.1, §6.2, §8.1, §8.3, §9.10, §9.11, §10, §11, §12 |
| 2026-09-04 (later still) | **Recurring EVENT expansion built — new §4.1.1.** Closes the largest gap the consistency audit above found but did not fix: `RecurrenceRule.Fixed` was only ever written *outward*, to `CalendarContract` (§9.11) and Google (§9.5.1), and nothing read it back, so a weekly meeting appeared once in Tendril's own Calendar while recurring properly in the system calendar Tendril publishes to. Multi-day spans (§4.1 round 1) and exception rows (§4.1 round 3, §9.8 R5) were dead for the same reason — declared, synced, never read. New `EntryOccurrences` expander plus a hand-rolled RRULE parser in `shared\`, consumed by Calendar's Day/Week/Month views, the Agenda and Monthly-grid widgets, and `AlarmScheduler` (which anchored to a series' *first* occurrence, so a recurring EVENT reminded once and then never again). Corrects §4.1's "use an existing RFC5545 library such as `lib-recur`": the only producer of a `Fixed` rule is the Google pull, its subset is small and stable, and a bounded grammar under our own control matched this codebase's own calls elsewhere (§7.4). The subset's limits, and the visible divergence an unsupported rule leaves against the system calendar, are stated in §4.1.1 rather than left to be discovered. §9.8 R3 is now satisfied properly rather than vacuously — expansion reads Room, never `CalendarContract.Instances`. | §3.2, §4.1, §4.1.1, §9.7, §9.11 |
| 2026-09-04 (last of the day) | **Sync actually runs on its own.** Third and last item the consistency audit found and left open. §9.4 specified a conflict sweep "on resume/launch", an `onStop`/backgrounding flush, and a periodic background pass; none existed — the only caller of `readAndMerge`/`writeSnapshots` in the whole app was the Settings button, so a `.sync-conflict-*` file sat undetected and an editing session reached the folder only if the person remembered to tap. New `SyncCoordinator` (`:app`) is the single place a pass runs from, non-reentrant, always read-merge-then-write, on an application-scoped **non-cancellable** coroutine — the Android SAF write is not atomic, so a pass cancelled by the Activity going away can leave the synced folder with no copy of a file at all. Wired to `onStart` and to `onStop` (skipped on a configuration change — a rotation is not a backgrounding); the Settings button now delegates to it rather than holding a second copy of the same guards. **The 2-second per-page debounce is explicitly still open**, with the reason recorded in §9.4 rather than approximated: it is specified per page, `writeSnapshots` has no per-page mode, and putting a whole-database write on a 2-second typing timer would be worse than the per-mutation write that decision already rejected. | §9.4 |
| 2026-09-04 (later than the last) | **Trashed Habits are recoverable again.** `HabitDao` had `observeTrash`/`restore`/`deleteForever` from the start and no caller for any of them, so trashing a Habit set `deleted_at`, removed it from the habits list, the Merged view and the quick-check widget, and left no way back — a permanent delete wearing a soft delete's field, against §5.5.1's "a deleted standalone Task/Event **or Habit** is recoverable". The Tasks & Habits Trash sheet (`EntryTrashSheet` → `TasksHabitsTrashSheet`) now lists Entries and Habits in one merged newest-first list through a small `TrashItem` sealed type, so the selection set, bulk Restore / Delete forever and counted confirm are written once rather than twice. Selection is keyed by kind+id, since Entry 3 and Habit 3 are different things. §5.5.1's "one list" is still not satisfied — Pages/Rows keep their own sheet — and that is now recorded there as open, together with the related bug it has to be fixed alongside: restoring a database Row doesn't restore its linked Entry. | §5.5.1 |
| 2026-09-04 (last, really) | **`.tendril` exports are encrypted when the toggle is on.** §9.4.2 says conflict files *and* portable packages carry the same at-rest protection "not a separate case to design" — but `PortableArchive` never referenced `SnapshotEncryption` at all, so Export was the plaintext way around the toggle, for a file meant to leave the device. Now encrypted per zip entry with the same magic/cipher/fresh-IV scheme as the sync folder; `manifest.json` stays readable on purpose (uids, not titles — see §9.4.2 for the trade) and gains an `encrypted` flag, defaulted so older archives still decode. An undecryptable archive is refused *before* Restore's wipe, with a message naming the passphrase rather than blaming the file. The passphrase now reaches `PortableArchive` as a constructor-supplied supplier, because the gap existed precisely as something a call site had to remember and none did. Also recorded: this cuts against §9.4.1's "send a Page to someone else" — an encrypted export needs the whole sync passphrase to open, so the export confirmation now says so. Six round-trip tests. | §9.4.1, §9.4.2 |
| 2026-09-04 (audit) | Add-dialog time pickers (a Task's time, a Habit's time-of-day) — both dialogs previously hard-passed `null`, so no Habit could reach the Merged tab and no same-day Task ever alarmed. "Delete forever" made to stick: a `(kind, uid, purged_at)` tombstone recorded with the row delete, covering Pages and Entries | §3.3, §5.5.1.1, §9.4 |
| 2026-09-05 (correction) | §5.5.1's parenthetical still described hard-delete propagation as an open gap needing "a tombstone the merge can act on, which is a format change" — that format change shipped on 2026-09-04/05 and the section now says so. Struck rather than deleted: the paragraph is the only record of *why* the additive-merge rule had to be narrowed rather than dropped, and the Revision Log rows above describe the fix without describing the problem it answers. No code change; the behaviour has been on `main` since PR #3. | §5.5.1, §5.5.1.1, §9.4 |
| 2026-09-05 (re-key) | §9.4.2 gains a re-key action, closing the last item the audit left to a product decision. Two problems, one small and one structural. The small one: changing the passphrase paused sync with "check the passphrase", which is right for a typo and tells someone who changed it on purpose that they made a mistake they did not make — the message now names both remedies. The structural one: after the September integration, *two* guards sat in series (the read-side refusal to write over a folder that would not decrypt, and the write-side refusal to overwrite a folder the key cannot open), so `allowRekey` had become unreachable from any caller and wiring it alone would have changed nothing. Re-keying is now a single operation in `SnapshotSyncOrchestrator` that merges under the current passphrase, refuses if anything failed to decrypt, and only then writes under the new one — `allowRekey` is no longer a flag a caller may set but the far side of that check. The coordinator stores the new passphrase only after the folder carries it, since storing it first and failing the write leaves the device holding a passphrase the folder does not use, which is the state the whole feature exists to get people out of. | §9.4.2 |
| 2026-09-05 (integration) | PR #1's recurring-EVENT expansion (§4.1.1) integrated with the same day's audit pass, which had been developed independently — both branches reported mergeable/CLEAN because GitHub computes that against the base branch and never against another open PR, while conflicting in 18 files. Resolved keeping every feature from both: `.tendril` exports are encrypted *and* carry purge tombstones; the orchestrator's undecryptable-file tally rides on the same threaded value as the folder key, rather than a second plumbing of identical shape through the same nine functions; the desktop keeps its masked passphrase field *and* its "N file(s) couldn't be decrypted" reporting. Three deliberate losses, each because keeping the alternative would have removed a capability: the combined Tasks-and-Habits trash sheet (it purged without recording a tombstone, so "Delete forever" would have stopped propagating), and `EntryDao.getInRange`/`observeOnDate` (a date-window query never returns a recurring series, which is anchored at its first occurrence — expansion replaces both). **Room schema is v8**, a number in neither branch's history: one bumped a shared v5 to 6 for the Canvas tables and the other to 7 for tombstones, and the combined entity set hashes to neither — Room compares that hash *before* migration runs, so an unbumped version throws where `fallbackToDestructiveMigration` cannot reach. | §4.1.1, §5.5.1.1, §9.4, §9.4.1, §9.4.2, §9.10 |
| 2026-09-05 | Audit sections 2, 3 and 4 closed in full, plus §5 items 1, 2 and 4 (PR #3). **Security:** the PBKDF2 salt moved into `sync_meta.json` per folder rather than a compile-time constant, and a folder that declares itself encrypted now refuses plaintext — the two are one fix, since the marker is what distinguishes an injected file from a folder mid-migration (§9.4.2). Checkbox-only mode refuses to activate while App Lock is on and hides the nav bar while bypassing the keyguard, App Lock and a keyguard bypass being in direct contradiction (§3.1.2). A write no longer overwrites an encrypted folder whose key it cannot open — a *mistyped* passphrase derives a perfectly valid key, so the previous `key == null` guard passed it straight through and re-encrypted everything under a key nobody knows. **Sync correctness:** a page whose parent arrived in a later batch was orphaned permanently (Pass 2 resolved parents for winners only); repair now runs for every record, but a non-winner may only *fill* an unresolved position, never overwrite one, or a stale record could move a page. Whole-page LWW is unchanged (§9.4) but the losing record is now written beside the winner as `<uid>.tendril-lost-<updatedAt>.json` instead of being discarded unread. Purge tombstones extended to Habits, which had no "Delete forever" at all. **Features:** one level of block nesting now renders (§3.1.1) and Notion import preserves it (§7 — the flattening was never about the format, the editor filtered children out of its own list); `Habit.duration` wired end to end (§3.3); habit reminders implemented on the notification channel that had been created for them and never posted to (§9.7). **Process:** CI compiles and tests the app rather than only reading it — a compile break had shipped through a green static-only check. 204 unit tests. | §3.1.1, §3.1.2, §3.3, §5.5.1, §5.5.1.1, §7, §9.4, §9.4.2, §9.7 |
| 2026-09-04 (audit, correction) | Purge tombstones **travel** rather than staying local — a local-only tombstone made "Delete forever" unachievable on more than one device, since the next sync restored everything from whichever device hadn't purged. §9.4's additive-merge rule is narrowed accordingly: absence still never implies deletion, an explicit tombstone does, and record-vs-tombstone resolves by later timestamp so a stale delete cannot destroy a newer edit | §5.5.1.1, §9.4, §9.4.1 |
| 2026-09-05 (write path) | **An edit to a cell, a block, a tag, a column or a canvas node now reaches the other device.** §9.4's merge decides per page by last-write-wins on `pages.updated_at`, and outside the merge the only writers of that column were `updateTitle`, `softDelete` and `restore` — so every mutation that changes what travels *inside* a page's snapshot left the timestamp alone. The exported `pages/<page_id>.json` carried new content under an unchanged timestamp, was not newer on the peer, lost, and the passes that apply schema, canvas, blocks, tags and cell values were skipped. Not recorded as a conflict either: an *equal* timestamp is the same version by definition, so no `.tendril-lost` copy was written and the edit vanished with nothing anywhere saying it had existed — then vanished on the editing device too, as soon as the peer made any timestamp-moving edit. `PageDao.touch(id, at)` added, and reached through a launcher rather than a second call — `launchAndReindex`/`launchTouching`, `launchAndTouch(pageIdToBump)`, and one `commit` exit in `DatabaseSyncManager` — so the write and the bump are one operation and there is no bump to forget, which is how this arose in the first place. *Which* page is the load-bearing part and is a required parameter, not a default: a cell hangs off its row's page, a property or view off the database's, a binding change off both. Two documented exceptions keep explicit bumps (`setChecked`, narrower lock gate; `addTag`, conditional). LWW itself is unchanged. Excluded on purpose: the lazy `ensureDefaultView` and `PageCanvas` shell writes, which run on open rather than on an edit and would let a device that merely looked at a page outrank one that had edited it. New `WritePathSyncTest` drives the real ViewModels across two stores rather than hand-building snapshot records — the only arrangement in which the defect is visible, and the reason `PageMergeTest` passed throughout. 256 unit tests. | §9.4 |
| 2026-09-05 (merge losses) | **Three things the merge destroyed as it went past, all of which the write-path fix above turned from latent into routine.** (1) Only *incoming* losers were preserved as `.tendril-lost-`; when an arriving record won, the local copy was overwritten with nothing naming it on either device. The local export is now captured before Pass 1 and compared on content with the timestamp normalised, so a routine catch-up does not litter the folder. (2) `Block.imagePath` is deliberately absent from the snapshot, so rebuilding a winner's blocks wrote null over it and unlinked every picture on the receiving device; held by uid across the delete-and-reinsert instead. (3) A property absent from a winning schema was deleted, taking every row's cell with it by cascade — the single place breaking `SnapshotSyncOrchestrator`'s own "absence never implies deletion" rule, and encoded in a passing test, which is why it survived review. Absence now means "hasn't arrived"; a real deletion travels as a `PurgedKind.PROPERTY` tombstone, which the merge refuses to re-insert and which needs no supersede rule, a re-added column getting a fresh uid. Still open: nothing in the app surfaces a preserved lost version. 261 unit tests. | §5.5.1.1, §9.4 |
| 2026-09-06 (scope reopened) | **Every "kept out of v1" decision in this document was reopened at once, by instruction.** The immediate consequence recorded here is §5.4's: the Notion-style formula language is reversed, and relation, rollup and formula land **together** as one feature — rollup aggregates across a relation and cannot exist without one, and a formula that cannot traverse a relation is a calculator over one row. The shape is a single `COMPUTED` type with two authoring paths onto one evaluator: the rollup pickers *write an expression*, and a `ƒ` reveals it, so choosing the easy path teaches the language rather than capping the person who chose it. Three properties justify building rather than copying — authoring-time type errors instead of Notion 2.0's silently empty cell, a real dependency graph with cycle rejection naming the path instead of an opaque 15-layer budget, and a computed cell that can explain its own derivation, which spreadsheets have had for thirty years and no database app in this category ships. §5.6's grouping exclusion falls out for free (grouping by a computed value is the same code path as grouping by a Select once the evaluator exists); §4, §5.6 and §10's bullets are struck in place rather than deleted, per §5.6's own 2026-08-08 precedent. *(**The strikes were not actually applied until 2026-09-07.** This row asserted a convention the edit had not carried out — no `~~` existed on any of the three — and it was caught by an audit checking this document against itself, not by a reader. A convention claimed but not applied is worse than one never claimed, because the next editor copies the claim.)* **A precondition outranks the feature**: `PagesSyncEngine`'s bare `PropertyType.valueOf` throws out of `mergePages` and aborts the *entire* sync pass — pages, entries, habits, purges — so an un-upgraded device receiving a new property type stops syncing altogether rather than degrading one column. The tolerant decode must reach every device first. **Recorded late, and that cost something**: an automated classifier reading §10 on 2026-09-06 found the formula language still filed under "Decided out of scope (unlikely to resurface)" and correctly recommended dropping it. In a document that is the source of truth, an unrecorded decision is not neutral — downstream readers actively decide against it. | §4, §5.4, §5.6, §10 |
| 2026-09-06 (spec audit, **partial**) | **Both specs were checked claim by claim against the code, and this row is written mid-pass because stopping without one is the failure the pass exists to fix.** Method, recorded because the result depends on it: 399 checkable claims were read against the working tree, 107 were reported wrong, and every one was then handed to an adversarial reviewer whose default was to *refute* it — **34 were thrown out**, one region producing 24 findings of which only 7 survived. That rate is the point. An unrefuted audit applied to this document would have written 34 fresh errors into the thing every later reader and every later automated agent treats as true, which is strictly worse than the staleness it set out to remove. **Status: complete — all 68 android corrections applied, and the windows spec with them.** The pass ran out of budget partway and this row was written mid-flight saying 31 remained; they were finished afterwards, and the row is corrected rather than rewritten because a status line that only ever shows the happy ending is not a status line. **The most important finding is a live defect, not a documentation error, and no code changed in this pass**: §3.1.2 promises Pages are read-only "as a group… no per-page exception" and claims defense-in-depth through a guard on every mutating ViewModel function. `PageDetailViewModel` has 13 such guards and `PageDatabaseViewModel` 19; **`PagesViewModel` has none**, and `PagesScreen`'s Trash sheet reaches `PageDao.restore`/`deleteForever` straight from the composable, past every ViewModel, behind a Trash button that is ungated while the FAB three lines below it is correctly gated. So Trash → **Delete forever** permanently destroys pages while the app reports itself read-only. The sentence that should have caught it is the one this document already contains: it was true when written and nothing re-checked it. Also corrected in this pass: the §10 note added hours earlier today pointed this document at a location outside version control, which a reader of the repository cannot follow. | §3.1.2, §10, and the sections named per correction |
| 2026-09-07 (Milestone 0 — written up after the fact) | **The largest change in the working tree had no row at all, in either spec; this is it.** Milestone 0 is hardening, not a feature, in two halves. **(1) §3.1.2's View-Only lock adopted on the surfaces that never had it** — the Pages hub (`PagesViewModel`: create, Restore, Delete forever), Canvas §3.7 (`CanvasViewModel`, gated in the `launchAndTouch` funnel every node and edge mutation already goes through, plus `updateTitle`, which sits outside that funnel and writes `pages` directly), the Road Map (`RoadMapViewModel.relate`, the screen's only write), and Settings' two portable-archive operations, Import and Restore backup — refused by `PortableArchive` itself as well as by disabled buttons, so the guard does not depend on the composable being the only way in, with the Notion import section swapped for a stand-in that says *why* rather than showing a dead button. Export is deliberately left ungated: it reads and changes nothing, and a lock that stopped someone taking a backup would work against the data it exists to protect. Each gate is a `locked()` read at call time rather than a captured value, so the eye toggle takes effect on a screen already open. The reason these four needed gating at all is that none of their writes is a local mistake: a create is exported to every peer on the next pass, Restore rewrites `pages.updatedAt` and so wins the next last-write-wins merge everywhere, a `page_relations` row is its own synced record merged off no page's timestamp, and Delete forever records a `PurgedKind.PAGE` tombstone that deletes the row on every device that adopts it (§5.5.1.1) — the one write in Tendril no `.tendril-lost-` copy can undo. **Two exemptions, deliberate and stated at their own call sites**, both pinned by `ViewOnlySurfacesGuardTest` so a later sweep that gates everything it can find breaks a test rather than a person's app: *idempotent repair-on-open* (`PageDatabaseViewModel.ensureDefaultView` and `CanvasViewModel`'s lazy `PageCanvas` shell) — gating those would leave a viewless database or a boardless canvas unopenable for exactly as long as View-Only stayed on, a lock hiding data instead of protecting it, and both sit outside `launchAndTouch` so they claim no authorship and move no `pages.updatedAt`; and *entry resolution* through `ResolveEntryUseCase` from the notification inline action (`EntryActionReceiver`), the Habits widget, `CalendarScreen` and `TasksHabitsScreen` — quick-capture surfaces outside the Pages hub where the eye toggle is not on screen and often the app is not even open, so a gate would swallow the tap silently at the moment someone was recording that something really happened, and an unlogged completion is itself lost data. Its one gated caller is `PageDatabaseViewModel.toggleDone`, which is inside Pages on a page whose lock the person can see. **(2) A quarantine policy for records this build cannot read, at every sync boundary.** Every enum persists as its `.name` String, so a newer build routinely writes values an older one must read; those values used to be parsed *in the middle of* a merge pass — `blockDao.deleteForPage(pageId)` and only then `BlockType.valueOf` — which destroyed this device's own blocks and *then* threw, aborting the pass for every other page in the batch. Now the whole record decodes before anything local is touched, an unreadable one is skipped entire with the local copy untouched, it is reported to the person through the channel sync problems already travel on (`SnapshotMergeResult` → `SyncCoordinator` → Settings' `lastError`), and — the clause that makes it quarantine rather than a plain skip — **it is suppressed from that same pass's export**, since the write half republishes every local record unconditionally and a mere skip would put this device's stale copy over the peer's newer file with no `.tendril-lost-` copy kept, manufacturing the exact loss the work exists to prevent. Restore-from-backup takes the opposite policy on purpose (§9.4.1): it clears the database first, so there is no local copy to fall back on and it refuses the whole archive up front via `undecodablePages` instead. **The suppression is now structural rather than remembered:** a private `FolderArrayFile` enum names the five folder-wide array files (`entries_active.json`, `entries_archived.json`, `habits.json`, `page_relations.json`, `purged_records.json`), `publishArrayFile` takes that enum rather than a filename and consults the held set itself so publishing without answering the suppression question is not something a caller can express, and two exhaustive `when`s with no `else` — one for where a file's local records come from, one for where its held records come from — make a sixth file a compile error in exactly two places rather than a silent omission. Held records travel as raw `JsonElement`s, not as decoded records, so republication is byte-faithful. **It took four rounds, and the shape of the sequence is the useful part.** Rounds 1–3 each turned up one more folder-wide file that had been forgotten: pages first, then the `entries_active`/`entries_archived`/`habits`/`purged_records` group, then `page_relations.json`. Round 4 named what made that third one hard to see and found it was general: **quarantine has to propagate along references between records.** No value in `page_relations.json` is unreadable at all — the file decodes perfectly, and the loss is second-order: an edge points at a page that was quarantined, so that page never lands locally, so `mergeRelations` drops the edge as having a missing endpoint, so it never appears in `exportRelations()`, and publishing that local view deletes the peer's link from the folder for everyone. The same shape, found the same round: a purge tombstone that this build reads perfectly well, naming a page file it cannot parse, deleted that file on the strength of half a comparison — `PurgeRegistry`'s supersede check lives inside `mergePages`, which a file that never decodes never reaches. Both are now held rather than dropped, and the tombstone still travels so a device that *can* read the page settles it properly. Counted rather than copied, at the time this row was written: 321 `@Test` methods across 29 classes under `Tendril android\app\src\test`, none carrying `@Ignore`. **What Milestone 0 did *not* close is recorded as an Open item in §9.4** — see the 2026-09-07 (deferred issue put on the record) row below. Note against convention: this row carries its own reasoning because Milestone 0 has no home-section write-up; §3.1.2 and §9.4 are still owed the prose, and that debt is the likeliest reason the change reached this log later than the code did. | §3.1.2, §3.7, §5.5.1.1, §9.4, §9.4.1 |
| 2026-09-07 (Canvas written up; three stale statements retired) | **Canvas finally has a design record.** New **§3.7**, written from the tree rather than from memory: the page-kind-not-a-block-type decision and both arguments for it, the single-`graphicsLayer` transform that keeps cards and arrows aligned at any zoom, the node and edge model, the View-Only enforcement that landed the same day plus the one exemption it keeps (the lazy `PageCanvas` shell, ungated *and* unbumped — repair-on-open must not claim authorship), what travels in the snapshot and what deliberately does not, the desktop asymmetry (the surface is Android-only while the entities, DAOs and merge pass are in `shared\`, so desktop is a full participant in canvas sync while rendering a placeholder), and the two `docs/audit-2026-09-04.md` §1 defects that make arrows unreachable and are confirmed still open. This closes what §3.4's 2026-09-04 correction acknowledged and then explicitly declined to reconstruct in passing. **§4** gains the three Canvas entity rows, and its "seven entities have no row here" note is marked partly closed at four rather than edited down, since the size of the original omission is why the paragraph exists. **§1's "Five pages" is deliberately *not* changed to six** — it counts nav destinations (`WorkbenchDestination` has five) and Canvas is a page kind rendered inside the Pages destination; the fix is a note saying exactly that, because the ambiguity between "uncounted sixth tab" and "page kind that was never counted" is what let the absence go unnoticed. **§10**'s desktop-companion deferral struck: its own graduation condition was met by Milestone 1 on 2026-08-30 and three milestones have shipped since, leaving a superseded deferral in the one list a reader consults for what is not built. **§11**'s next-step bullet named a reopened backlog but not its first item — Milestone 0 has landed (View-Only gates on the four surfaces that had never adopted them, plus enum quarantine at every sync boundary) and the next step is §9.10's Room destructive-migration switch, first on a dependency argument: nearly every reopened item adds to the schema, so each one built before the switch is another migration to hand-write afterwards. Its test count is **recounted, not copied** — 321 `@Test` across 29 classes, static, none ignored — with the disagreement against the 313 handed over explained rather than smoothed over. Separately, `docs/audit-2026-09-04.md` row 5.1 (nested blocks — that audit's own top-ranked gap, still listed as open) is marked closed against `988f8c7` and `4e63f08`. | §1, §3.7, §4, §10, §11; `docs/audit-2026-09-04.md` |
| 2026-09-07 (deferred issue put on the record) | **New Open item in §9.4: a record this build reads *well enough* is republished with the fields it did not understand stripped out.** Verified against the tree rather than assumed: `SnapshotSyncOrchestrator`'s `Json` is configured `ignoreUnknownKeys = true`, and all 24 `@Serializable` declarations in `SnapshotRecords.kt` and `PageSnapshotRecords.kt` are plain data classes with no catch-all — no `JsonObject`, no leftover-property map, in either file. A record from a newer build carrying one additional field therefore decodes with it silently discarded, is adopted into Room, and is re-encoded on the next write pass **from Room rows** (`exportPages()`, and the `SnapshotMappers` path off `entryDao.getAll()`) rather than from the bytes that arrived — so it goes back to the folder without the field, and every other device adopts that. The asymmetry is why it earns a written record: a record this build *cannot* read is now the safe case, because quarantine holds it as a raw `JsonElement` and republishes it byte-faithfully, so it is precisely the records understood *well enough to adopt* that lose data — the failure gets quieter as two builds grow closer, and no unreadable value exists anywhere for a guard to trip on. **Deferred, not fixed**: the fix is a format change (every record carrying its raw `JsonObject` alongside its typed fields, merged on re-encode), not a guard, and it rewrites `SnapshotRecords.kt` and `SnapshotMappers.kt` end to end — the same two files the backlog's SYNC lane (S2/S3/S4) already rewrites and marks strictly serial for that reason, so doing it separately would rewrite both twice. Stated in the section as plainly as it is here: it is not fixed today, and it bites the first time two builds of different versions share a folder. **(Fixed 2026-09-09.** Not as this row predicted: the fix is not a format change and does not touch `SnapshotRecords.kt` or `SnapshotMappers.kt` at all. Those records are re-encoded *from Room rows*, so carrying a raw `JsonObject` on the record class would preserve nothing unless the unknown JSON were also persisted — a column on every snapshot-bearing entity. The seam that works is the write path, which already republishes raw `JsonElement`s for quarantine: `publishArrayFile` and `publishPageFile` now read the folder's existing copy and carry across every key the record's serializer descriptor does not declare. "Not declared" rather than "not present" is load-bearing — an omitted default is indistinguishable from an unknown field otherwise, and copying those back would resurrect values this build had deliberately cleared. Recursive, matched by `uid`; the three record types without one are left alone rather than matched positionally. Preservation is best-effort and can never fail a write. **Detection remains open**: this stops the loss, not the silence.)* | §9.4 |
| 2026-09-11 (§0 added) | **New §0 Objectives, upstream of every later section**: the eight hard constraints, purpose and goals, the bar per surface (`docs/benchmarks.md`), eight principles, ten decisions with acceptance criteria, out-of-scope, the order of work, risks, open items. Drafted as `OBJECTIVES.md` and folded in the same day so that one file needs no precedence rule. Three later sections are **Corrected** in place by §0.6 rows and owe their own amendment in the pass that builds them: §3.1.1 (nesting depth), §3.3 (streak-based habits), §3.1.6 (Tags → Label). | §0 (new), §1 (pointer) |
| 2026-09-11 (Canvas UI to `shared/`) | §0.8 step 1 done: `CanvasScreen` and `CanvasViewModel` moved from the Android app into `shared/src/commonMain` (renames, history kept); the screen takes `WorkbenchCore` in place of `AppContainer`, matching `PageDetailScreen`; the shared scaffold routes `PageKind.CANVAS` itself and its `canvasContent` slot is removed from both platform callers. Desktop opens, edits and links cards on a canvas for the first time. Nothing inside the board changed; 550 tests pass. | §0.6.10, §0.8, §3.7 |
| 2026-09-11 (step 2: Entry fields, habit log) | §0.8 step 2 done. **Schema v10** (`MIGRATION_9_10`): `entries` gains `dueDate`, `parentEntryId`, `estimate`, `important`; new `habit_completions`, backfilled from `lastCompletedDate`/`previousCompletedDate`. Both travel in the snapshot and the `.tendril` archive; the habit log merges by the Reminder rule (tombstoned, deleted wins). Tasks UI: deadline, steps, Postpone (moves the When), Someday, the opt-in important flag; Habits: streak off the row by default, a presence sheet. §5.2's binding label corrected to "Date (when)"; the second binding is step 2b. 570 tests. | §0.6.4, §0.6.6, §0.8, §0.10, §3.3, §5.2 |
| 2026-09-11 (step 2b: deadline binding) | §0.8 step 2b done. **Schema v11** (`MIGRATION_10_11`): `page_databases.dueDatePropertyId`. `BindingRole.DUE_DATE` binds a `DATE` property to `Entry.dueDate`; the two date cells and two row editors become one each, taking the role. The enable-sync sheet gains a Deadline picker. **§5.2.1 corrected**: post-hoc bind had no UI path; the header menu now offers "Bind as <role>". Migration verified on desktop and phone; the binding verified end to end on the phone. 573 tests. | §0.6.4, §0.8, §5.2, §5.2.1 |
| 2026-09-11 (step 3: depth, mind map, canvas block) | §0.8 step 3 done. **§0.6.1** unlimited nesting — `outlineOf` walks the tree, `indentTargetFor`/`outdentPlanFor` are the outliner pair, the writer and the Notion parser follow. **§0.6.2** the outline mind map, `Block.mindMap` (**schema v12**), inert card and armed full screen, CMP `BackHandler` added. **§0.6.3** `BlockType.CANVAS`, the live board in a page. §3.1.1's one-level rule and §3.7's "never nested" corrected in place; §0.10 items 2 and 8 resolved, item 10 (desktop Escape) opened. Verified on desktop and phone. 585 tests. | §0.6.1–3, §0.8, §0.10, §3.1.1, §3.7 |
| 2026-09-12 (desktop Escape) | §0.10 item 10 resolved — desktop-only change in `Tendril windows/`, no `shared/` code touched; the substantive entry is `tendril-windows-spec.md`'s row of the same date. | §0.10 |
| 2026-09-12 (Label rename) | §0.6.9 done: `Tag`/`PageTag`/`TagDao`/`TagColors` → `Label`/`PageLabel`/`LabelDao`/`LabelColors` across `shared/`, the Android app and the desktop app; UI strings say "label". Tables, columns and the snapshot key unchanged, so no schema version and no folder-format change. §3.1.6 and §4's entity table corrected in place. 585 tests. | §0.6.9, §0.8, §3.1.6, §4 |
| 2026-09-12 (step 4: schema on a label) | §0.8 step 4 done. **§0.6.8** — `PageDatabase.labelId`/`labelConfirmed` (**schema v13**), `PageDao.getMembersOf` (native ∪ labelled), `domain/LabelMembership`, `DatabaseSyncManager` over members, the snapshot's `labelName`/`labelConfirmed`, cell values on any page, the generalised unknown-column hold, `BindLabelSheet`, membership strips in the page header, the chip mark, the once-only dialog. §5.1 and §5.5 corrected in place. Verified on desktop and on the phone's v12 → v13 upgrade. 596 tests. | §0.6.8, §0.8, §5.1, §5.5 |
| 2026-09-12 (step 5: natural-language Quick Add) | §0.8 step 5 done. `domain/QuickAddParser` (pure, English first, ISO/24h in any language) + `ParsedEntry.toEntry`; `ui/entries/QuickAddPreview` chip row in `shared/`; Calendar's Quick Add creates a Task or an Event from the line, Tasks' add dialog pre-fills from its title. B§6 #3's open decisions answered: English only first; recurrence phrases `daily/weekly/monthly`, `every N days/weeks/months`, `every <weekday>`, `every weekday`. §3.2 and §3.3 amended in place. Verified on the phone (`Dentist tmr 3pm` → event 15:00 tomorrow; `todo Call bank by friday !` → flagged task due Friday; `Gym every monday 7am` → weekly task from Monday 07:00). 612 tests. | §0.8, §3.2, §3.3 |
| 2026-09-12 (step 6a: Calendar → shared) | `CalendarScreen`/`CalendarViewModel` moved to `shared/src/commonMain/.../ui/calendar/`, the same move step 1 made for the Canvas: the screen takes `WorkbenchCore` and two slots — the Google Calendar settings sheet (now `CalendarSettingsSheet.kt` in the Android app, Play Services) and the Reminders sheet (alarms; null on desktop hides the bell). Six strings join `shared/`'s resources. No behaviour change on Android; desktop gains the Calendar. Verified on the desktop preview: Day/Week/Month, Quick Add of `Standup 9-9:30am every weekday` drawn on Monday and not Sunday, the `···` sheet. 612 tests. | §0.8, §3.2 |
| 2026-09-12 (step 6b: edit path, drag-to-move) | `domain/EntryEditor` (`save` normalises for the kind, `move` keeps a span's length and makes an override row for *this one* of a series), `ui/entries/EntryEditSheet`, a tap target on the Day row, long-press-drag between Week's day cards with a ghost and a *this one / all* question. Sheets that are taller than a desktop window now open fully expanded — §0.10 item 11 resolved. §3.2 amended in place; the Day view's hour-drag deferred to step 7's timeline, said there. Verified on desktop: a series edited (title, time; its `BYDAY` rule kept), a Saturday occurrence dragged to Monday as *this one* → an override row, the series untouched. 620 tests. | §0.8, §0.10, §3.2 |
| 2026-09-12 (step 6c+6d: Agenda, layers) | `CalendarView.AGENDA` (30 days grouped by day); `CalendarLayers` in the ViewModel with a chip row — Tasks/Events filter the rows, Habits draws timed habits on every day, Database dates draws every stored `DATE` cell (`PropertyValueDao.observeDateCells`, a joined projection) and opens the page on tap; Week lists and Month counts the extras too. `CalendarScreen` gains `onOpenPage`, threaded through the scaffold's slot. §3.2 amended in place (Agenda; Show Habits built as a layer); §0.10 item 12 opened (layer persistence). Verified on desktop (Agenda; a seeded `Read on` cell on Wed 16 opening its page) and on the phone (Agenda; a timed habit *Stretch 09:00* appears with the layer on and hides with it off; 6b's sheet and Week drag also walked there: estimate saved, *Call bank* dragged Sat → Sun). 620 tests. | §0.8, §0.10, §3.2 |
| 2026-09-12 (step 6e: ICS) | `domain/ics/` — `IcsWriter` (RFC 5545 by hand: VEVENT/VTODO, TZID times, exclusive all-day DTEND, RECURRENCE-ID + RELATED-TO for moved occurrences, EXDATE for skips, 75-octet folding), `IcsReader` (unfolding, `Z`/`TZID`/floating/`VALUE=DATE`, nested VALARM skipped) and `IcsImporter` (by UID, updates not duplicates, overrides and skips into §4.1's rows, every write through the coordinator). Android: Settings › Calendar (.ics); desktop: the Calendar's `···`. §3.2 gains the bullet; §0.10 item 6 annotated. Verified: desktop export → a valid file → re-import "0 new, 4 updated", still 4 rows; phone export ("1 event(s) and 6 task(s)") → re-import "0 new, 7 updated", still 7. 624 tests. **Step 6 complete.** | §0.8, §0.10, §3.2 |
| 2026-09-12 (step 7a: Tasks & Habits → shared) | The four `ui/taskshabits/` files moved to `shared/`, the move step 6a made for the Calendar: the screen takes `WorkbenchCore`, the two Settings switches, and three slots — the Reminders sheet and the two Trash sheets — null on desktop. `EntryScheduleCoordinator.onHabitChanged` (defaulted) replaces the view-model's `AlarmScheduler`; `WorkbenchCore` gains a derived `checkInHabitUseCase`. 14 strings join `shared/`'s resources. Desktop gains Tasks & Habits; §0.10 item 13 opened for its two missing sheets. Rows show the bell only when a Reminders sheet was supplied (a composition local), so desktop has none. Verified on the phone (Habits tab, a check-in writes its row) and on desktop (Add task with `Read chapter 3 tmr 9pm for 45m` pre-fills the dialog; Merged › This week lists it, no bell); 624 tests. | §0.8, §0.10, §3.3 |
| 2026-09-12 (step 7b: Plan mode) | `domain/plan/DayTimeline` (blocks by span/estimate/default, greedy lanes, quarter-hour snap, the unplanned rail's rule), `ui/calendar/PlanView` (hour grid, dashed estimated blocks, the now line, all-day chips, the rail, both long-press drags through `EntryEditor.move`, which now places a time on an untimed entry). A *Plan* chip on the Day view. §0.6.5's plan half done; §3.2's deferred hour-drag delivered. Verified on desktop (rail → 10:00, block → 14:15) and the phone (*Trip* → 08:00, the Provider mirror at 08:00). 630 tests. | §0.6.5, §0.8, §3.2 |
| 2026-09-12 (step 7c: tracking) | Schema **v14**: `time_logs` (`MIGRATION_13_14`, verified in place on both real databases). `domain/track/TimeTracker` (one running timer; start closes the rest, stop, toggle) and `TimeLogTotals` (window-clipped minutes, an open log counts to now). `time_logs.json` in the sync folder and the archive: deleted-wins, else LWW; an unresolvable owner is held and republished (negative control). UI: ▶/■ on task and habit rows and Day rows, the running strip above the tabs (`ui/track/`), the habit sheet's logged minutes. Phone: `NotificationChannels.TIMER`, a chronometer notification with a *Stop* broadcast (`TimerStopReceiver`), no service. 642 tests. | §0.6.5, §0.8 |
| 2026-09-12 (sheet frame) | `ui/components/TendrilSheet` replaces all 29 `ModalBottomSheet` sites: one frame (sides, title slot, proportional bottom room), always fully expanded; the two refused alternatives recorded. §0.10 item 11 applied everywhere, phone included; item 14 opened (desktop layout revision). 642 tests. | §3, §0.10 |
| 2026-09-12 (step 7d: planned vs actual) | `domain/plan/DayTotals` (planned = blocks + untimed estimates; logged per entry/habit; the day's logged spans, midnight-clipped; the mean session; the row segment), `TimeLogDao.observeBetween` back with its caller, `minuteTicker`. Day header *Planned · Logged*; row segments on the Day view and Tasks & Habits; the logged strip along Plan mode's gutter; *About N min each* on the habit detail. §0.6.5 complete; §0.8 step 7 done bar Review. 647 tests. | §0.6.5, §0.8 |
| 2026-09-12 (step 7e: Review) | **§0.6.11** written and done. Schema **v15** (`page_databases.lastReviewedAt`, `MIGRATION_14_15`, in the page record, LWW-carried by touching the page). `domain/review/ReviewPlanner` (due-by-cadence, stale rows, open tasks by `sourceRowId`, Someday and past-When selection, walk order, the week's three numbers) and `Review` (loads with existing DAOs; Reviewed/Today/Someday/Done/Trash through `EntryEditor`/`ResolveEntryUseCase`). `ui/review/ReviewScreen`, `WorkbenchRoute.Review`, the checklist icon with a dot on Tasks. §0.8 step 7 complete. 653 tests. | §0.6.11, §0.8 |
| 2026-09-12 (step 8·0: KeyValueStore) | §0.10 item 12 resolved: `data/prefs/KeyValueStore` (+ `MapKeyValueStore`, `AndroidKeyValueStore`, `PropertiesKeyValueStore`) on `WorkbenchCore`; the calendar layers persist on both platforms (`CalendarLayers.encode/decode`); `Review.cadence` reads `review_cadence_days`. §9.1 note. 658 tests. | §0.10, §9.1 |
| 2026-09-14 (corrupt-file recovery) | §9.10's "probe would catch file-level corruption" corrected: on Android it did not — `AndroidSQLiteDriver` opens with the framework's `DefaultDatabaseErrorHandler`, which deleted the file and reopened empty before the probe ran. `KeepFileOnCorruptionDriver` (a no-op handler) closes it; `DatabaseFileTest` (Robolectric, first in the suite) proved the hole and now pins the fix; both builds then run on the OnePlus (Android 14) — `main` logs `DefaultDatabaseErrorHandler: deleting the database file`, the fix leaves `tendril.db.unopenable-<stamp>` with the bytes intact. Desktop unaffected. Tests 690 → 691. | §9.10 |
| 2026-09-16 (the tray) | §2.2 gains *The notification area* (B§13.6 #7): the icon and its three verbs, × hides (`close_to_tray`), Windows toasts from `DesktopReminderScheduler` over the shared `domain/reminders/ReminderFirings.kt` (Android's `AlarmScheduler` reads it too; `ReminderFiringsTest`), the bell on both platforms (`ui/reminders/` shared, `EntryScheduleCoordinator.onReminderRemoved`), the global chord (`GlobalHotkey.kt`, `ui/nav/QuickAddChord.kt`, Win+Alt+N default — Win+Alt+T is Game Bar's) and the popup (`QuickAddWindow.kt`, `ui/entries/QuickAddField.kt`, `domain/QuickAdd.kt`), Settings › Notification area (`ui/settings/NotificationAreaSection.kt`); §0.10 item 21 resolved, item 20's hotkey half done. **Rows measured beside Notion** (the user's mid-walk brief): task/habit rows one line under a pointer, `rowHeightDp` Compact 29 / Comfortable 36, the tree 29 — 65 → 30 px against Notion's 30. §3.2, §3.3 one line each. Critiques: `docs/critiques/tray-mock.md`, `tray-function.md`. Desktop verified (the toast itself behind this machine's Do-not-disturb; the icon behind the taskbar's overflow — the user's check); the phone pending. Tests 795 → 805. | §2.2, §3.2, §3.3, §0.10 |
| 2026-09-17 (the desktop audit) | Three read-only `design-critique-plus` passes over every desktop surface on `main` after the tray (`57fc375`): `docs/critiques/desktop-layout-full.md` (1a/1b/1e/1f/1h — every `DropdownMenu` row at 49 px, the Calendar's Day/Agenda rows at 65/52–61 px against the lists' 30, the Table's 50, the Month as the phone's dot grid; measured natively), `desktop-type-full.md` (1c — 113 vocabulary sites beside 204 raw Material roles; *(s)* plurals, raw enum names and two spec citations in UI copy; Material's 16/24/32 leaking through fields, the Add dialog and the date picker), `desktop-function-full.md` (1g/1h/1i — the find bar reopening on every page after one Ctrl+F, the F1 card omitting the shelf's chord, the habit pane a stub). **§0.10 item 22** carries the disposition: nine High findings → the fix PR *the audit's fixes*; the Med/Low rows listed there by id. No code changed. Desktop only; the phone pending. | §0.10 |
| 2026-09-17 (the audit's fixes) | The eight High findings of §0.10 item 22 fixed (L4, the Month grid, is its own PR) plus D1–D3 of the design-layer pre-pass (`docs/critiques/desktop-design-layer.md`). **Type by class:** the seven styles are aliases of Material's roles (`TendrilTypeTest`), so 196 + 36 raw-role sites were renamed by script; `tools/type_sites.py` classifies every text site by rule (31 classes, `tools/tests/`), `tools/type_table/table.json` gives each class one style — chosen by `meta-optimizer` over a deterministic scorer (`tools/type_table/spec.json`, `scorers.py`; the near-tie went to the table that changes fewer sites) — and `tools/audit.py` rules 13–15 (*material type role*, *type class*, *bare menu*) hold it; 83 sites moved by their class, 3 carry an explicit `// type:` comment. **Menus** through `ui/components/TendrilMenu.kt` at the profile's row (49 → 30 px; Notion 29, Obsidian 25, Todoist 32, TickTick 36 measured live); **the Calendar's Day/Agenda rows and the Table** through `ui/components/RowControls.kt` (65 / 52–61 / 50 → 30 / 30 / 34). **Copy**: `domain/Words.kt` (`plural`, `HabitFrequency.label`, `ViewType.label`/`blurb`, `PropertyType.label`; `WordsTest`), the New view sheet as chips, the citations struck. **Function**: `ui/nav/FindRequestGate.kt` (the find bar no longer reopens on the next page; `FindRequestGateTest`), the F1 card generated from `SHORTCUTS` (`ShortcutsTest` holds it), the habit pane's facts and verbs (`HabitDetailPane`). **Design layer**: `keyboardCursorRing` in `outlineVariant`, the tab-root lines `onSurfaceVariant`, `textDim`/`textFaint` solved on `accentSoft` too (`RegisterSolveTest`), one `TextStyle.tabular()`. Critique: `docs/critiques/desktop-audit-fixes.md`. Desktop verified; the phone pending. Tests 805 → 814 (+ 10 Python). | §2.2, §2.3, §3.1.1, §3.2, §3.3, §0.10 |
| 2026-09-17 (the Month grid) | L4 of §0.10 item 22 fixed, the dots PR folded in. **On a wide window the Month is a grid of titled cells** (`ui/calendar/MonthGrid.kt`, one composable with two homes — the Calendar tab and a database's Calendar view): rows the weeks the month spans, equal, filling the pane; the chips a cell shows computed from its height (`domain/plan/MonthLayout.kt`: `monthGridDays`, `cellCapacity`, `visibleInCell` — the Week's `visibleAllDay` is the same rule at three; `MonthLayoutTest`); *+n* opens a popover listing the day; the number opens the Day; the empty ground seeds the quick-add strip with the day; chips drag between cells through the strip's write and its series prompt; adjacent-month days dim with their items; `Sep 1`; the Week's all-day chip extracted (`OccurrenceChip.kt`, 22 dp, the time first in tabular figures in `onSurface`). **The phone keeps its dot grid** and gains a weekday header and one dot per layer (`monthDots`, `dotColour`). `tools/audit.py` rule 16 refuses a second seven-column grid. Grounds: Notion's database calendar measured live (rows 124 / chips 28 at 34 / disc 22 px); TickTick's and Todoist's month views are paid tiers; Notion Calendar, Google, Fantastical [Assumed]. Critiques: `docs/critiques/month-grid-mock.md` (the feature scoring), `month-grid-function.md` (rows 149 = pane / 5, chips 22 at 24, four a cell). Desktop verified; the phone pending. Tests 814 → 820 (+ 1 Python). | §3.2, §5.6, §0.10 |
| 2026-09-17 (the Calendar's chrome, the borderless window) | L5 of §0.10 item 22 fixed, widened to **every tab's header** and to a **borderless window**. On Windows the 52 dp bar is the window's title bar (JetBrains Runtime's custom title bar, `Tendril windows/…/TitleBar.kt` + `ui/nav/TitleBar.kt`: a bar at the window's top edge drags on its ground, one reaching its right edge insets its actions by the caption block; the OS keeps painting — ☐ ✕, snap, resize, double-click; the rail's top row carries the mark; a page bar's title field takes its text's width there; pop-outs the same; a runtime without the API keeps the OS title bar). **One bar per tab**: the Calendar's `‹ › range · Today` in the title slot, `Week ▾` and `Layers ▾` as menus (`ui/components/BarControls.kt`; `ui/calendar/CalendarTitles.kt`, tested), the grids' own ‹ › rows off; Tasks' two rows as the list column's 52 dp `PaneHeader` (tabs + `Today ▾`), *new* in the bar, no FAB (L9's Tasks half); Road Map's chip row as `Filter ▾`, the legend at the canvas's foot. **The time grid takes Notion Calendar's measured proportions** (`CalendarGeometry`: hour 45 dp, gutter 56 — 48 / 60 px beside its 48 / 60); today's *Planned* line in `onSurface`. The phone's rows unchanged. Grounds: Notion Calendar measured live (toolbar 46 px as its title bar, first hour line 130 px, hour 48, gutter 60); Notion, Obsidian borderless by eye. Critiques: `docs/critiques/calendar-chrome-mock.md` (L1–L3, C1–C3 scored; L1 + C1 taken), `calendar-chrome-function.md` (bar 55, caption 141, hour 48, gutter 59). Desktop verified; the phone pending. Tests 820 → 824. | §2.2, §3.2, §3.3, §3.4, §0.10 |
| 2026-09-17 (the quick switcher as a card) | L6 of §0.10 item 22 fixed, with the audit's #6 field family. On a wide window the switcher is **a centred card** (`ui/components/CentredCard.kt` — the F1 card's frame extracted and shared: a `Popup` over the 32 % scrim, ≤ 560 dp and ≤ 60 % of the window, radius 10; the switcher's top at 20 % of the window, the F1 card centred), a **36 dp field** (`ui/components/TendrilField.kt` — a `BasicTextField` in one hairline row: the switcher, the find bar at 28, the AI key and the desktop's sync passphrase; 48 under Touch), rows at the profile's height, **Recents** when nothing is typed (the eight most recently edited pages with their age — `PageDao.getRecentlyEdited`), section eyebrows, the typed prefix bold, the chord beside a command from the shortcuts table (`SwitcherCommand.chord`, `chordLabelOf`), a footer of key hints; a plain query lists the pages then up to three matching commands, `>` commands alone (`switcherSections`, tested). The phone's overlay unchanged in form, gaining the Recents. Grounds measured live: Notion Calendar's Ctrl+K (a centred card 678 px at 125 %, top 21 %, field 46, rows 36 = the 29 dp menu row, a footer), Obsidian's Ctrl+O / Ctrl+P (699, at the top edge, field 48, rows 32, the prefix bold), Notion's Ctrl+K (a 1008 px search dialog with Recents). Critiques: `docs/critiques/quick-switcher-mock.md` (a selected row's meta in `onSurface` — `dim` on the tint 3.45 : 1), `quick-switcher-function.md` (card 558 px, field ≈ 35, rows 29, top 20 %). Desktop verified; the phone pending. Tests 824 → 828. | §2.2, §3.1.7, §3.5, §0.10 |
| 2026-09-17 (the quick-add strip, the tray's clamp) | L7 and L8 of §0.10 item 22 fixed. **L7**: `QuickAddField` on `TendrilField` — 28 dp in the Calendar's strip (the find bar's 44 dp frame) with the preview chips inline at its right in a `FlowRow`, wrapping under it only when they do not fit (the strip 44 dp, one wrapped row 80); 36 dp in the desktop's popup with the chips under; the chips' targets 28 under a pointer; `QuickAddChips` the loose form. No Material text field remains on the desktop. **L8**: the tray draws at most 30 % of the pane (`trayDrawnWidthDp`, tested; the handle still remembers 240–360) and the Week header hides its minutes below a 90 dp lane, keeping *Planned* (`plannedLabel`, tested; ellipsis as the belt). Grounds measured live: TickTick's add bar 36 / 38 px (≈ 30 CSS, the row); side panes Todoist 21 %, TickTick 21 %, Notion Calendar 13 %, Obsidian 34 % (user-set). Critiques: `docs/critiques/quick-add-strip-mock.md` (the plan's 90 dp lane corrected to 82 at the user's window — the rule reads the lane), `quick-add-strip-function.md` (strip 44 px, field 28, popup field 35, tray 263 px = 30 %, lanes 78–79; collapsed 115 with the minutes back). Desktop verified; the phone pending. Tests 828 → 830. | §2.2, §3.2, §0.10 |
| 2026-09-17 (the canvas's cards under a pointer) | L9's canvas half and L10 of §0.10 item 22 fixed. *New card*'s home **scored** at the user's ask (quick-add ease · misclick · consistency · small pane · lock, weights .30/.30/.20/.15/.05): **B 4.55** — `+` in the bar with the Text / Page menu **and a double-click on the ground makes a text card under the pointer** with its editor open, with the **discard rule** (a text card whose editor closes with nothing typed is deleted — `pendingNewNodeId`); A the bar alone 4.30, C Obsidian's floating toolbar 3.05. The badges show **on hover or on the selected card** (a click selects — a 2 dp accent ring; the ground clears; the reserved row stays so the text never moves); Delete / Backspace with a selection opens the delete dialog (the board takes focus on selection); new cards land at the visible centre (`contentAtPaneCentre`) and a *Fit* bar button pans and zooms to every card at ≤ 1× (`fitToCards`, `domain/canvas/CanvasView.kt`, tested); the caption *Empty card — open* in `onSurface`; the FAB, the always-on badges and *tap to edit* stay the phone's. **App-wide fix found on the walk (§0.10 item 10 amended):** the desktop dispatched every Escape twice — the runtime's key-down and the app's key-up `EscapeBackInput` — so a sheet's Esc could also pop the page; the app's input removed, one dispatch. Ground measured live: Obsidian's Canvas (card 252 × 63 bare at rest; a click → ring + 36 px toolbar; double-click creates; zoom-to-fit). Critiques: `docs/critiques/canvas-cards-mock.md` (the scoring), `canvas-cards-function.md` (card 182 × 91 px, ring 3 px, Fit, the discard rule read from `canvas_nodes`). Desktop verified; the phone pending. Tests 830 → 835. | §0.10, §2.2, §3.7 |
| 2026-09-18 (the design layer) | D4–D8 of §0.10 item 22 fixed — the first of the four PRs that close the item (A the design layer → B the last Material frames → C the database's views → D small things III, then L7b). **D4** `surface2` at 6 % in both modes (light measured 1.07:1, ΔE 1.6 — under the 2.0 JND; now ΔE 2.42–2.55, tested); **D5** the Road Map's edges at their tokens' full alpha with a 1 dp stroke (the 0.6 undid the solve: 2.50 / 2.31:1 → 5.63 / 4.61+); **D6** `accentStrong` deleted (never read) and `thirdStrong` folded into `third`, solved to 4.6 (the same colour in 28 of 30 palettes; `onThird` now solved against the token that is drawn — Console light 3.15 → 4.83); `event` stays (the Month dots read it); **D7** `TendrilShapes` 4 · 6 · 10 · 12 · 12 on `MaterialTheme` so every dialog, the date picker and the bottom sheet stop drawing Material's 28 dp (the delete dialog's corner measured 12 px), the family documented as 4 · 6 · 8 · 10 · 12, `tools/audit.py` rule 17 *radius family*; **D8** keyboard focus visible: `keyboardFocusRing` on the rail pill and the register swatch (drawn only when focus arrives from the keyboard), and on a list's row Tab **moves the cursor** (`cursorOnFocus`) so the 14e ring is the one ring — the walk's first build drew two. Critiques: `docs/critiques/design-layer-measured.md`, `design-layer-function.md` (a new Low recorded: Tab stops on a hidden hover `···`). Desktop verified on Chalk light and Ink dark; the phone pending. Tests 835 → 836. | §2.2, §2.3, §0.10 |
| 2026-09-18 (the last Material frames) | L6b, L15 + T3, T4 and T5 of §0.10 item 22 fixed — PR B of the four. **L6b** every text field is a `TendrilField`: the nineteen `OutlinedTextField`s in `shared/…/ui/` and the phone's sync passphrase (Material's 56 dp at 16 sp) → 36 dp under a pointer, 48 under Touch, at `body`; `readOnly`, `minLines` (the canvas card editor grows from three lines in the same frame) and the keyboard options on the String form; `tools/audit.py` rule 18 *material field*. **L15 + T3** the Add task and Add habit dialogs are `TendrilSheet`s — a slide-over on a wide window, a bottom sheet on the phone — with the edit sheet's frame; **T3** `TendrilDatePicker` (`ui/components/DatePickers.kt`) replaces Material's 32 sp headline in the nine date pickers with *Pick a day* at `heading`, one line. **T4** `overflow = Ellipsis` on every one-line text (39 sites); rule 19 *clip* reads the call around each `maxLines = 1`. **T5** a sheet's title at `pageTitle` on both forms (`// type: BAR_TITLE`). **Found on the walk, pre-existing since L5, fixed:** a slide-over's × sat under Windows' ✕ — the panel's content now starts under the title bar's row where a handle exists. Critiques: `docs/critiques/material-frames-measured.md`, `material-frames-function.md` (the field 36 px, the sheet's title 13 px caps, the picker one line). Desktop verified; the phone pending. Tests 836. | §2.2, §2.3, §3.3, §0.10 |
| 2026-09-18 (the database's views) | F4–F8 of §0.10 item 22 fixed — PR C of the four. Ground measured live: Notion's database views (a throwaway page; the view tab's menu 223 px at a 29–30 px pitch with *Delete view* absent on the last view; *Delete this view?* confirm 326 × 141; the add-view panel of icon tiles, then a *New view* side panel with a name field and the layouts as tiles; **a Board on a database with no Select never opens empty — a *Status* is made for it**). **F4** *Delete view…* asks (the app's dialog: the view's name, what goes, what stays; the error colour on the verb); the last view cannot be deleted. **F5** a ▾ on the active chip opening *Configure view… · Delete view…*; *Configure this view…* in the `···`. **F6** *taken from the ground, improving on the plan's two buttons:* `addView` makes the property a view plots by when the database has none — a Select *Status* (Not started · In progress · Done) for a Board, a *Date* for a Calendar or Timeline — or binds the first that exists (tested); the two-button `ViewNeedsPrompt` remains for the state a later deletion leaves. **F6′** (found on the walk) a blank Select cell is *unset*, not a mismatch: those rows sit in a named first column *No Status* (`BoardColumn`, key "" — a card moved in is cleared; tested); a value matching no option stays dropped (§5.6 amended). **F7** the New view sheet: the name on the 36 dp field, the layouts as chips, one line under the chosen kind, *Cancel · Add*. **F8** one menu vocabulary: the database's `···` gains *Show on Road Map* and *Move to Trash* (`trashDatabase`, the tree row's write, behind a confirm), its order the page's verbs → the kind's → *Move to Trash* → the chrome's *Show beside ▸ · Open in a window · Open Trash…*; *Trash…* → *Open Trash…* on both kinds. Critiques: `docs/critiques/database-views-mock.md` (the ground), `database-views-function.md` (the dialog 456 × 174, the Status made and bound, the rows in *No Status*). Desktop verified; the phone pending. Tests 836 → 838. | §0.10, §5.6 |
| 2026-09-18 (small things III) | L11–L14 and F9–F14 of §0.10 item 22 fixed — PR D, the last of the four; **item 22's Med/Low list is closed** (L7b stays its own PR; F15 unverified). **L11** a submenu opens to the left when it would cross the window's edge (`submenuSide`, tested). **L12** a Journal day page is *shown* as its date — *13 Sep 2026* — in the tree, the shelf's graph header, the hover card, the switcher and the map (`displayTitle`, tested; the page bar keeps the storage name, being the editable title). **L13** the Timeline opens with today three columns in and never past the day before the first bar (`timelineInitialColumn`, tested). **L14 / F12** the reminder presets are a list under the eyebrow *REMIND ME … BEFORE*, each row with the moment it would fire (`reminderFiresAt`, tested) — **TickTick's reminder list measured live** (*1 day early (9:00 AM)*), taken as the ground over the plan's `FlowRow`; the anchor presets wrap. **F9** the Add label sheet lists every label the page lacks, with its hue dot, before a letter is typed (Notion's, Obsidian's [Assumed]); *Create* only when nothing matches. **F10** *Show Someday · n* beside its switch, never a header. **F11** the hover card's 320 dp and the folded map's one line stand in code and test; the 240 px read as the window-edge clamp. **F13** no icons on the habit row's menu. **F14** the force layout separates overlapping boxes every frame and draws nothing until it has settled once. Critique: `docs/critiques/small-things-3-function.md`. Desktop verified; the phone pending. Tests 838 → 842. | §0.10, §3.1.4, §3.1.6, §3.2, §3.4, §5.6 |
| 2026-09-18 (the quick-add tokens inline) | **L7b** of §0.10 item 22 — the last row; **item 22 is closed** (F15 unverified). The words the parser read as a token are **tinted where they sit in the line** as it is typed (`ui/entries/QuickAddTokens.kt`: a `VisualTransformation` over `ParsedEntry.spans`, `tokenTintRanges` tested), the chips unchanged — they name the token and carry its ✕. **Decided:** the tint is `findSoft` (the third hue's tint that already means *recognised text* — the find mark), never `accentSoft` (selection's); Compose's span background is a flat line box — Todoist's padded pill and Fantastical's coloured words are the deleted branches. **Ground measured live: Todoist's quick add** (a tinted background behind the recognised runs — `#6F2625` on `#282828`, the text white; the chips beneath); TickTick's recognition is off in the user's install (not toggled); Fantastical [Assumed]. Four homes, one rule: the Calendar's strip, the chord's popup, the Add task sheet, the phone's Day view field. Two defects fixed on the walk: an event line's *by friday* was consumed with no chip — the parser now keeps an event's deadline words in the title (tested); the Add task sheet's title field takes focus on open. Critiques: `docs/critiques/quick-add-tokens-mock.md`, `quick-add-tokens-function.md`. Desktop verified; the phone pending. Tests 842 → 845. | §0.10, §2.3, §3.2 |
| 2026-09-18 (the phone catch-up) | The pending phone halves of #82–#106 walked in one pass on the OnePlus (`docs/critiques/phone-catch-up.md`), `main` installed over the phone's v19 data — **the v19 → v20 rebuild verified in place** (`important` → `importance` 3, ids intact, the uid index back). **Decided:** the catch-up first, then a measured phone audit beside the five apps installed for it; every High to one follow-up fix PR; the phone's data kept. **One High fixed here as the walk's prerequisite:** `main` had crashed on every launch since 14h·2 — `LabelFilterRow`'s bottom padding went negative under Touch (4 − 8 dp) whenever a label existed; clamped. Found, to the fix PR: a `TendrilSheet` on the phone does not scroll (the Add task sheet's rows overlap with the keyboard up); the Urgency submenu covers its parent on a 360 dp window; the Pages list's ↓ lands on the label chip; the canvas page has no `···` on the phone; task rows' meta in raw ISO. Recorded: *Opens on*'s *Agenda* wraps, the Day view's chips at the edge, the find bar's key hints, the Italian day header's order. Verified as claimed: the drag onto a day, the Month's dots and header, the switcher's Recents, the reminder list, the label sheet, the New view sheet and the *No Status* Board, the delete confirm, L7b's tint in the Day view and the Add sheet. §0.10 item 23 opened. No tests changed. | §0.10 |
| 2026-09-18 (the phone's fixes) | P2–P6 and P11 of §0.10 item 23, **the grounds measured on the phone first** (Todoist, TickTick, Notion, Notion Calendar — `docs/critiques/phone-fixes-measured.md`: Todoist's row 57 dp with a 17 sp title over a 14 sp date, TickTick's 46, Notion's list 48 and menu rows 45, its `···` a sheet that pushes a level). **P2** every `TendrilSheet` scrolls; a sheet whose content is a lazy list passes `scrolls = false` and keeps a bounded height — **audit rule 20 *sheet scroll*** pairs the two (twelve sites). **P3** under Touch a submenu pushes a level into its menu (a back row, then the rows — Notion's); under a pointer it stays beside. **P5** the canvas page's `···` on the phone: *Show on Road Map · Move to Trash* (asks; refused under the lock, guard-tested). **P6** a task row's meta through `taskRowMeta` (tested): *sab 12 · due ven 11 · 0/1 steps*, one line, the row 59 dp (from 67/86). **P11** `PhoneLayoutTest` — a layout composed under Touch with Robolectric, shown to fail on the old padding. **P4 withdrawn**: no phone app navigates a list by hardware keys; the chip row is first in focus order and a tap opens a page. Recorded: the 48 dp checkbox holds the phone's row at 59 against Todoist's 57 / TickTick's 46; menu rows 48 against Notion's 45. Tests 845 → 849. | §0.10, §2.2, §3.3 |
| 2026-09-18 (the phone audit) | Three `design-critique-plus` passes over every phone surface of `main` (#108), measured from `uiautomator` dumps beside TickTick, Todoist, Notion and Notion Calendar on the same phone (Obsidian a web view — its frame only): `docs/critiques/phone-layout-full.md`, `phone-type-full.md`, `phone-function-full.md`. **One High:** the phone's primary text is 14 sp where every ground sets 16–17 (a row's second line 12.5 vs 14) — the Touch profile inherits the desktop's Notion-cut vocabulary; the fix is a decision (a ×1.14 Touch scale, recommended). **Med:** six row pitches under Touch (53–71) against the grounds' 46–48 / 57; ≈ 200 dp of stacked chrome on the Calendar and Tasks tabs (the desktop's L5, on the phone); targets under 48 dp (the find bar's buttons 36 wide, the row page's property values 31 tall); Material's 15 / 17 dp bands beside the app's 14 / 16 / 18 on one screen; the row page's strip read-only with tap targets; the edit sheet's ISO *When*. **Low:** the Month's 42 dp cells, the Postpone units wrapping, the Timeline's English initials, a mind-map node's long-press leaving a revision, a swipe on the map's filter row setting a focus. Decisions listed once per pass; findings into §0.10 item 23 by id. No code. | §0.10 |
| 2026-09-18 (the phone's second fixes) | The audit's High and decided Meds, all four decisions **A** (`docs/critiques/phone-fixes-2-measured.md`). **T·P1 the Touch type scale**: under Touch every chrome role steps one size up the same scale — body 16 · description and label 14 · caption and eyebrow 12.5 · heading 16 · pageTitle 20; the editor's three unchanged (`touchTypography`, tested; provided by `WorkbenchEnvironment`, so sheets and menus inherit it) — text bands 14 · 16 · 18 · 25 → 16 · 18 · 20 · 27 beside the grounds' 16–17 / 14. **L·P1** the profile's 56 dp minimum on every list row. **L·P2** the phone's Calendar and Tasks chrome folded as the desktop's L5: `Day ▾` in the bar, the layers as a pushed level under `···` with *Plan the day* and *Calendar settings…*; the Tasks' pane header (tabs + `This month ▾`) on the phone — ≈ 200 / 150 dp → ≈ 100. **L·P3** 48 dp targets: the find bar's buttons, the strip's values, the Month's cells; every bar pill and pane tab 40 dp under Touch (`barControlHeight`). **T·P2** `TendrilMenuItem` the app's row on both profiles. **F·P1** the strip's taps did open the editor — the target was the defect. **F·P4** the edit and Add sheets' dates as *ven 25*. Tests 849 → 850. | §0.10, §2.2, §2.3, §3.2, §3.3 |
| 2026-09-18 (item 23's Lows) | The phone audit's twelve Lows in one pass (`docs/critiques/phone-lows-measured.md`); **§0.10 item 23 closes.** Two general causes under **T·P3** (one style at two heights): a chrome `TextStyle` without a `LineHeightStyle` trimmed its single line to the font's own height (`chromeStyle` carries Material's `Center / Trim.None` and platform style — a line is its style's line height on both platforms, tested); and Android's windows — every sheet, menu and dialog — started from the platform's density, not the shell's ×1.046, so they drew 4.6 % under the page (`MainActivity.attachBaseContext` applies `shellScaleFor` to the activity's density; `WorkbenchEnvironment(platformScaled)` scales nothing on Android). **T·P4 / F·P3** `weekdayInitial(day, locale)` for the Timeline and the Month header (*L M M G V S D*). **P10** the Day header is `rangeTitle(DAY)` (*venerdì 18 settembre*). **L·P5** the Postpone sheet's unit as a `days ▾` menu on both platforms (the desktop's small-things-III #3 closes). **L·P6 / P8** the Day view's preview chips inset with the field. **F·P2** a block field writes only when its text changed — a selection alone had captured a revision and re-stamped the page on both platforms. **P7** *Opens on* and the backup buttons as `FlowRow`s. **P9** the find bar's hints name keys under a pointer only. **T·P5** = F·P4; the desktop pane's *When* / *Deadline* on `dayLabel` with it. **L·P4** closed by L·P3's measurement (48 dp rows, one value column). **F·P5** cannot reproduce — recorded. Tests 850 → 853. | §0.10, §2.2, §2.3, §3.1.1, §3.2, §3.3 |
| 2026-09-18 (items 9 and 20) | **This file moved to the repository root** (`git mv`, history kept; §0.10 item 9 resolved; the README's paths, the windows `settings.gradle.kts` note and §11's layout line updated). **The desktop's AI key file is DPAPI-wrapped** (§0.10 item 20 closed): `SecretWrap` in `shared/` (a `protect` / `unprotect` pair; `SecretFileFormat` writes one line — `dpapi:` + base64 of the blob, or the bare key where no wrap exists — tested with a stand-in wrap, 5 cases), `FileAiKeyStore(file, wrap)` reads a bare line from before and rewrites it wrapped on first load, `DpapiWrap` in the desktop module over `Crypt32Util` (`jna-platform`, the tray's dependency); a blob from another account reads as no key. §0.6.15's caveat amended. Tests 853 → 858. | §0.6.15, §0.10, §11 |
| 2026-09-18 (item 7 — one canvas at two sizes) | §0.10 item 7 resolved on its recommendation (`docs/critiques/canvas-embed-measured.md`; Obsidian's canvas embed measured live). The canvas block in a page draws **the board's own layer** — `CanvasLayer` (the edges with their arrowheads, the cards with their text or page title) extracted from `CanvasBoard`, inert on the block (`interactive = null`: no gesture, badge or hover) at `fitToCards`'s scale; the second renderer (`CanvasThumbnail`, boxes and lines) deleted. The block's height follows the board's shape at the column's width, 160…320 dp (`canvasEmbedHeightDp`, tested — Obsidian's rule); a card's text is drawn while the fit ≥ 0.5 (`CANVAS_CONTENT_MIN_SCALE`, Obsidian's threshold). §3.7 amended. **§0.10 item 24 opened** with four Lows seen on the walk (a Date cell's ISO value on the row page, two slide-overs' 47 px rows, the field's unthemed context menu, the phone's inset band). Tests 858 → 859. | §0.10, §3.7 |
| 2026-09-18 (item 15 — frames and canvas templates) | §0.10 item 15 resolved on the four decisions (`docs/mockups/canvas-frames.html`, `docs/critiques/canvas-frames-mock.md` — Obsidian's group measured on the desktop **and on the phone**; `canvas-frames-function.md`). **Frames**: `CanvasNodeType.FRAME` on the existing `width` / `height` / `text` columns (no schema); a node belongs to a frame while its whole box lies inside the frame's — geometry, no parent (`domain/canvas/Frames.kt`: `nodesInside`, `clampFrameSize`, the fit over boxes, tested); the label is the handle (drag moves the frame and what it holds; a click selects, a second opens the editor; the body pans the board); a corner handle resizes (212 × 122 the floor, 388 × 212 new); deleting a frame keeps its cards; `+ → Frame` on both platforms. **The phone's canvas gesture** (decided): a long press lifts a card or a frame, a swipe pans — Obsidian mobile's, the tray's rule under Touch. **Canvas templates**: *Save as template* on the canvas's `···`; `TemplateManager` clones the board (nodes, frames, arrows rejoined; page cards keep their pages, tested); the New sheet lists them with the canvas glyph. **Two older defects fixed on the walk**: the board never panned past one delta (`pan` captured in a `pointerInput(Unit)`), and the arrow-tap detector consumed every drag. Collapsible frames recorded for after item 6. Tests 859 → 866. | §0.10, §2.2, §3.1.3, §3.7 |
| 2026-09-19 (item 6 — JSON Canvas) | §0.10 item 6 resolved: **a Canvas page exports as a `.canvas` file** (JSON Canvas 1.0, `obsidianmd/jsoncanvas`) at its place in the Markdown zip — the zip is an Obsidian vault whose canvases open; no `.md` twin; a note's mention links `Route ideas.canvas`. `domain/canvas/JsonCanvas.kt` (pure, tested): text cards → `text`, page cards → `file` at the page's zip path (a page the export lacks → a `text` node with its title), frames → `group` with the label, arrows → edges (`TWO_WAY` adds `fromEnd: arrow`, `NONE` sets `toEnd: none`), dp rounded to integers, uids as ids. `MarkdownExporter` takes the three canvas DAOs; the result sentence counts canvases. **The desktop gains *Export as Markdown*** (Settings; the exporter was `jvmCommon`, only the save dialog is the platform's). Verified by Obsidian opening the phone's and the desktop's exports (`docs/critiques/json-canvas-function.md`). §0.6.7's "JSON Canvas export" built. Tests 866 → 868. | §0.10, §0.6.7, §3.7 |
| 2026-09-19 (item 3 — measurable habits) | §0.10 item 3 resolved on the four decisions (`docs/mockups/measurable-habits.html`, `docs/critiques/measurable-habits-mock.md` — TickTick's amount habit measured on the phone; `measurable-habits-function.md`). **Schema v21**: `habits.unit`, `habits.amountPerCheckIn`, `habits.dailyAmount` and `habit_completions.value`, all nullable (`MIGRATION_20_21`, `21.json`; verified in place on both devices); the snapshot and archive records carry them as defaulted fields an older peer ignores. A habit **counts** when `amountPerCheckIn` is set: every tap on its `+` disc (the checkbox's slot) logs one completion carrying the amount, or the number typed behind the sheet's collapsed *Log an amount…*; the day still counts once for `lastCompletedDate` and the streak; *Undo the last one* tombstones the last completion and the day stands on the rest. **Presence gains the amounts** (`HabitPresence.amountToday / amountThisMonth`, sums of what was logged): *2 cups today*, *41 cups this month, on 12 days*; the number the person set is one dim sentence, last — never a fraction, a bar or a rate (§0.6.6). The Add habit sheet's *Counts something* switch: unit, each check-in, a day if you like. Tests 868 → 871. | §0.6.6, §0.10, §3.3, §4 |
| 2026-09-19 (item 4 — the check-in) | §0.10 item 4 resolved in two rounds (`docs/mockups/check-in.html`, `check-in-options.html`; `docs/critiques/check-in-mock.md`, `check-in-function.md` — Daylio's shape [Assumed], Notion's journal *Mood* select [Verified] as the setup it improves on). **Schema v22**: `check_ins` (uid, date, at, mood, energy, deletedAt — `MIGRATION_21_22`, `22.json`; verified in place on both devices), synced as `check_ins.json` on the completions' tombstone rule, in the archive, wiped on Restore. **The row** on the Journal's day page above the Today strip — any day that has happened, never a future one: *How are you? ›*, five words for mood (*Awful · Bad · Okay · Good · Great*) and five for energy (*Drained · Low · Steady · High · Full*) as the bar's pills, either alone; a tap logs one row at that moment, a tap on the filled word undoes it; the line under says *Good at 14:32 · energy high at 14:35*. No note field — the page's blocks are the words. **The sheet** from the title: the month as discs in the day's latest mood (five stored hues through `labelColours`, no red), a ring for energy alone, nothing where nothing; the month's check-ins listed. No count, no average, no chart (B§10.3). Tests 871 → 879. | §0.6.6, §0.10, §3.1.4, §4 |
| 2026-09-19 (items 16 and 17 — closed) | §0.10 **item 16** (database-level history) closed as recorded, not built: §0.6.13 versions a page's title and blocks; a database's columns, views and cell values have none, and the design stands in the item for the day one is missed (the full `PageSnapshotRecord`, restore through the merge under a fresh timestamp). Notion's history is per page — a database page's holds its views, a row's page its blocks, property edits are a feed, not versions [Assumed]. **Item 17** (an agent over the export, a local model) closed with `docs/agent-over-export.md`: the export is the agent's interface (which export carries what; pointing Claude Code at the unzipped Markdown; read-only towards the app, a snapshot, where the agent runs is where the notes go); no verb in the app beyond §0.6.15's selection, no local model — the reasons written once. **Item 19** decided the same day — block selection and block undo, built next. No code. | §0.10 |
| 2026-09-19 (item 19 — the block selection) | §0.10 item 19 resolved on the mock (`docs/mockups/block-selection.html`, `docs/critiques/block-selection-mock.md` — Notion's, Logseq's and Craft's block mode [Assumed]; `block-selection-function.md`). **A block selection** beside the text selection: Esc from a field selects its block, Shift+↑↓ / Shift+click extend a run in reading order (a parent brings its subtree), Ctrl+A twice selects every block, a marquee on the ground under a pointer, a long press on the row's margin under Touch with the page bar becoming *n selected · Copy · Delete · ···*. The run takes Delete, Ctrl+C/X/V, Ctrl+D, a group drag with a ghost and an insertion line, and the block action sheet titled by its count. **A block undo**: `domain/blocks/BlockUndo.kt` — the affected rows before and after each block operation (delete, move, indent/outdent, turn into, cut, paste), replayed as inverses through the same DAO writes; Ctrl+Z / Ctrl+Y with no field focused, *n blocks deleted · Undo* as the snackbar; typing stays the field's, page history the net. `domain/blocks/BlockSelection.kt`, `ui/pages/BlockGround.kt`; the F1 card's *Editor* group. No schema. Tests 879 → 886. | §0.10, §2.2, §3.1.1 |
| 2026-09-19 (the desktop's measurement reference) | The user's brief mid-walk of item 19: the desktop's type and elements resized with the window — no other app does this. **On the desktop the reference is now Windows' own text size** (Settings › Accessibility › Text size, 100…225 %) times the density profile; the window's size plays no part, and display scaling stays the runtime's. `ui/nav/ShellScale.kt`: `systemTextScale(percent)`, `desktopShellScale`, `LocalSystemTextScale` (tested); `WorkbenchEnvironment` reads the local before the window rule, which stays the phone's (its screen is fixed). Desktop: `WindowsTextScale.kt` reads `HKCU\Software\Microsoft\Accessibility\TextScaleFactor` through JNA, again on every focus gain. Measured: the rail, the tree and the bar pixel-identical at 700 × 520 and 1600 × 1000. Tests 886 → 887. | §2.2 |
| 2026-09-20 (the rail's labels) | The user: the rail's text is small — measured beside Notion and the Claude app (native grabs, 125 %): both set their navigation items at **10 px of cap height**, the same as their rows and as Tendril's tree; the rail's labels stood at 7 (`caption` under Compact). **The rail's labels take `body`** (14 sp, Medium when current) — 10 px measured after; the phone's bar keeps `caption`. `Shell.kt`'s `ShellItem(labelStyle)`. | §2.2 |
| 2026-09-20 (the pointer type scale) | The user: the other text elements on the page, on real examples. Notion and the Claude app measured (`docs/critiques/desktop-type-grounds.md`): one size for all chrome text (10 px cap), a smaller one for section headers only, hierarchy by weight and colour. **Under a pointer `label`, `description` 12.5 → 14 and `caption`, `eyebrow` 11 → 12.5** (`pointerTypography`, `Type.kt`, tested; `WorkbenchEnvironment` provides it as it does the Touch remap); body, heading, pageTitle, the editor hold. Measured after: chips, property labels, the membership line and captions 8 → 9–10. The phone untouched. Tests 887 → 888. | §2.3 |
| 2026-09-20 (the size count) | `/typography-spacing-core` over the desktop page (`docs/critiques/desktop-type-grounds.md` § the size count): `dk scale` holds every size but the bar's 18. Decided in one batch — **two chrome sizes** (14, 12.5), **the bar's title 16 / 600** (`pointerTypography`), **scaled drawings' words never under 12.5** (`CANVAS_CONTENT_MIN_SCALE` 0.5 → 12.5 ÷ 14; below it the boxes bare and `ReadableLabels` over them at `caption` — amended from *readable or absent* on the user's look at the build), desktop only. The page: 12.5 · 14 · 16 (+ 20 · 24 for H2 / H1). | §2.3, §3.7 |
| 2026-09-20 (the canvas card) | The user, on Garden plan: the cards were disproportionately big (180 × 90 dp around one line). **A card is a strip that grows with its text** — 200 × 48 dp, 68 for two lines, 88 for three (`cardLines`, `cardHeight`, `nodeBox` in `domain/canvas/Frames.kt`, tested; Obsidian's 250 × 60 measured on L9); a page card one line; frames keep their stored sizes. The badges as a column at the strip's right edge, the text with a 28 dp end margin. The JSON Canvas export writes the strip's box. *Two defects on the user's look at the build, fixed before the merge: the readable overlay wrote a frame's label inside the frame's top band (over a card's words) — now a pill above the frame's top-left edge, where the board draws it; the arrowhead was pulled back by half the target's height, under the card for any line arriving from the side — now `boxEdgeDistance` (tested), the tip on the target's edge. And the frame's name "very small, blends in" at `label` in the dim colour: a frame's name is the board's section heading, `heading` (14 / 600) in `onSurface` at rest, on the board and in the overlay's pill. And an arrow "falls underneath the border of the frame": the layer's order is now frames → edges → cards → the frames' labels (Obsidian draws edges above groups).* | §3.7 |
| 2026-09-20 (the mind-map pass) | The user, on three zooms of Garden plan: "consider the structure of some mind-mapping apps to smooth out this part". Grounds read (nine open-source projects' READMEs — three models: a tree from a root · a tree plus relationships · a free graph with groups) and measured live (Xmind: three levels, main topic 41 px, branches 2 px headless curves, structure per topic; Mindomo: centre 70 px, main 43, text on the line from level 3; Freeplane: the outline pane and the map one document, free nodes, clouds, fold markers; draw.io's template from its XML) — `docs/critiques/mind-map-grounds.md`. The user's question — the most versatile option, Mindomo's templates as a list "without it being a fixed option" — answered: one model drawn several ways. **Decided (one batch):** a node may have a **parent** (**schema v23** — `canvas_nodes.parentId`, `folded`, `structure`; `page_canvases.structure`), a **structure** on the board and on any node's subtree — *Free · Map · Right · Down* (Timeline and Fishbone next) — applied by **Tidy**; the grammar per structure (the map structures three levels — the root at `pageTitle` in a bordered box, a branch the strip, deeper nodes text on the line; Down the strip at every level); a frame around a tree node **follows its subtree** (hand-sized around free nodes); subtrees **fold** with the hidden count at the branch's end; branches **curved** in the map structures, **elbowed** under Down, headless, one colour per main branch from the register's fanned hues; relationships (the old arrows) dashed with a head and a label on the line, routed from the outer sides; Tidy on demand, a dragged node free until the next; the outline's mind map drawn by the same grammar. `domain/canvas/Tree.kt` (tested, 7), `ui/canvas/TreeGrammar.kt`, the tree's verbs on `CanvasViewModel`, Insert / Enter (Tab where the platform passes it) and the node's menu, a card dropped on a card becomes its child. `docs/mockups/mind-map.html`, `docs/critiques/mind-map-mock.md`, `mind-map-function.md`. Tests 890 → 897. | §0.6.2, §3.7, §4 |
| 2026-09-20 (small things IV) | §0.10 item 24's S1–S7, the disposition's cheap seven, in one PR after the assessment of every recorded line (the same day): the row page's Date cell by F·P4's rule; the *Insert block* and *Block actions* sheets on `TendrilMenuItem`; the desktop's text context menu in the register's colours with the app's words — `ui/components/PlatformTextMenus` (expect/actual, provided by `WorkbenchEnvironment`; Compose Desktop's default is a light menu with the OS locale's labels); the shell's content consumes the navigation bar's inset while the bottom bar is drawn (every screen's `Scaffold` had padded for it — a 40 dp band above the bar); a Journal day's bar reads its date (`displayTitle`, not editable there); the Today strip's `HabitCheck`; Tab skips an invisible tree `···`. `docs/critiques/small-things-4-function.md`. Tests 897. | §0.10, §2.2, §3.1.1, §3.1.4 |
| 2026-09-20 (the card's width) | The user: shorter boxes that grow with their text and wrap past a length, to avoid overlap among many short cards; a 1 px border. Decided in one batch: `meta-optimizer` with a deterministic scorer (overlap at a hand's pitch · a readable band from Xmind's measure · grounds fit · alignment · compactness) over the user's own texts; every strip, the root's box kept; a hand's width by a handle; the band from the grounds. Xmind measured live (a topic is its text plus padding, capped at *Lunghezza* 307 px at 18 px, then wrapped). **Fit to the text between 60 and 220 dp, 8 dp a side — 0.847** (fixed 0.466, steps 0.761); the estimate 8.5 dp a character after two cut words on the walk; the badges straddle the card's edge; the width handle on the left edge (the right stays); a `Box` in place of the clipping `Surface`; the phone's `···` gains Tidy and Fit. `docs/critiques/card-width-optimizer.md`. Tests 897 → 898. | §3.7, §0.10 |
| 2026-09-20 (S8 — sibling order by drag) | One batch: the order is the position's (no schema), a drop among siblings under a structure tidies at once, the Map side follows the node's x. `Tree.kt`: `sideOf` by the node's centre against the root's, a new child to the emptier side; the board's `onDropAt` → `tidyTreeOf`. Walked on Garden plan. `docs/critiques/sibling-order-function.md`. Tests 898 → 899. | §3.7, §0.10 |
| 2026-09-20 (S10 — the edit-habit sheet) | The Add habit sheet edits too (`AddHabitDialog(initial)` — *Edit habit*, *Save*), `Habit.edited` keeps the identity and the streak's record (tested), `updateHabit` re-arms; *Edit…* on the habit row's `···`, the pane's chips and the phone's sheet. Walked on both. `docs/critiques/edit-habit-function.md`. Tests 899 → 901. | §3.3, §0.10 |
| 2026-09-21 (habits as brush-strokes) | The user's brief: a habit is not a task's shape and never on the Month; on the grids a brush-stroke across its span, under the blocks, a reminder to keep time for oneself. Mock (`docs/mockups/habit-strokes.html`) and its critique; the user on it: softer, closer to §0.6.6 — four readings (`habit-strokes-soft.html`), one batch: the quiet wash at 12 %, one state, the name mixed case Regular in the hue without a time, a dot on today's lane after the check-in. Built: `domain/plan/HabitStrokes.kt` (4 tests), `ui/calendar/HabitStrokeLayer.kt` under the blocks of `WeekGridView` and `PlanView`; habits out of `timelineBlocks`, `BlockKind.HABIT` gone, the Month grid and the phone's dots without habits, a click → `HabitDetailSheet` (+ *Edit…*). Walked on both. `docs/critiques/habit-strokes-function.md`. Tests 901 → 905. | §3.2 |
| 2026-09-21 (S9 — Timeline and Fishbone) | Xmind's *Linea del tempo* and *Lisca di pesce* measured live; the mock generated by the layout rule (`docs/mockups/timeline-fishbone.html`, its critique); one batch: the org chart away from the spine, sides alternating, the head at the left, bones as text on the line. `Tree.kt`: the two structures, `TreeSide.UP`, the spine geometry pure, `tidy`'s spine branches, `newChildPosition` on a spine and a rib; the painter's spine, dots, stems, ribs, bones. Walked on both. `docs/critiques/timeline-fishbone-function.md` (S14 recorded). Tests 905 → 909. | §3.7, §0.10 |
| 2026-09-21 (S11 — the Touch gutter) | The block's way in on the phone, on `docs/mockups/touch-gutter.html` (today, a wider gutter, the block's own long press in two halves, a handle in selection mode); the user's question — which conflicts least — answered A: the margin 8 → 24 dp under Touch, 32 dp before a paragraph's text, no gesture changed; D and C recorded. Walked on the phone (31.6 dp measured). `docs/critiques/touch-gutter-function.md`. | §3.1.1, §0.10 |
| 2026-09-21 (S12 — collapsible frames) | Advanced Canvas and draw.io as grounds; the mock (`docs/mockups/collapsible-frames.html`) and one batch: a strip at the top-left, relationships hidden with the card, a following frame's own state, the pill's chevron and the menu. `Tree.kt`: `frameContents`, `hidden` for a collapsed frame, the strip's box, `tidy` on hidden nodes (4 tests); the strip, the chevron, the menu's verb, the frame's right-click menu. Three defects fixed on the walk (the strip under the ground, its lost label, a following frame hiding itself). Walked on both. Tests 909 → 913. | §3.7, §0.10 |
| 2026-09-21 (S13 — a hue for a database and a card) | B§13.8.2 built: Obsidian's colour row measured live; the mock (`docs/mockups/hue.html`) and one batch — one hue bar with the six presets, cards and frames in Obsidian's form, the database's hue on the map, the calendar and its glyph, the default from the title. **v24** (`hue` on `page_databases` and `canvas_nodes`; the records; in place on the desktop). `domain/colour/Hues.kt`, `hueColours`, `LocalDatabaseHues`, `HueSheet`, the sites; JSON Canvas `color`. Walked on the desktop; the phone's walk pending (it dropped off USB after the install). `docs/critiques/hue-mock.md`, `hue-function.md`. Tests 913 → 916. | §2.3, §0.10 |
| 2026-09-21 (the scrim) | The slide-over's and the centred card's scrim: the text colour at 32 % → **black at 24 %** (`Scrim` in `ui/theme/Palette.kt`) — the old rule lightened a dark ground and inverted the panel's depth; Notion / Obsidian / Material read from their bundles, the candidates measured through the scrim (`docs/mockups/scrim.html`, `docs/critiques/scrim-mock.md`). The phone's sheet unchanged. | §3, §2.2 |
| 2026-09-21 (S14 — the text's width) | Item 24's last line: a leaf's and a card's width from **the font's own advances** (`domain/canvas/TextWidth.kt` — Inter at 14 sp measured from the bundled file; DM Sans within 1 %, the serif narrower), summed with a 5 % margin, words wrapped on them; the flat 7.5 / 8.5 dp a character gone, and with them the 4 dp `CANVAS_CARD_PAD` had understated (8 declared, 12 drawn — every card ellipsised once the width was exact). Walked on Garden plan under Down and Right: every word whole, *Shredded paper*'s leaf 127 dp over 108 of ink; the tree's rows agree with the table to 0.3 %. `docs/critiques/leaf-width-function.md`. Tests 916 → 917. **Item 24 closed.** | §3.7, §0.10 |
| 2026-09-21 (the phone's walk of #128–#134) | Habit strokes (12.3 %, 28 dp), S9 (Timeline, Fishbone), S11 (31.4 dp), S12 (the strip), **S13** (the glyph, the sheet under a finger, the dot in the database's hue; v24 on the phone) and S14 walked on the phone. Two defects fixed: **a drawing keeps the desktop's type** under Touch (`DrawingType`, `LocalDrawingTypography` — the Touch step had reached the canvas's and the mind map's text while their boxes were sized for 14 sp; every card cut) and the phone's badges 16 dp out; the hue sheet's swatch line shortened (it ellipsised at 16 sp). #134's generator had dropped `wrappedLines`' float tolerance on regeneration — restored in `tools/make_textwidth.py`; 917 / 0. The phone's walk is current. | §3.2, §3.7, §2.3 |
| 2026-09-21 (the spec levelled with the build) | The user: match the build exactly, future objectives kept. Read against `main` at 3943cb4: **§4's register** completed to schema v24 — the four rows it said were unwritten (`PropertyValue`, `PageRelation`, `PurgedRecord`, `PageFts`) plus `HabitCompletion` and `TimeLog`, and every row's key fields re-read from the entity classes (`uid`, `hue`, `FRAME`, the Entry's deadline / step / estimate / provider ids, the Database's label and review columns, `order`, `deleted_at`); §0.10 items 14, 20, 22 and 24 struck (closed in their bodies, never in their headings); six "the phone walk is pending" claims in the body pointed at the 2026-09-18 catch-up; §9.2 gains the toolchain as built (AGP 9.3.2, Kotlin 2.4.10, CMP 1.12.0, Room 2.8.4, Gradle 9.7.1, SDK 37 / 30 / 36); §3.5 the Settings sections as built on both platforms; §10's "four tabs render `NotAvailableOnDesktop`" superseded; the header's `../` paths explained (the file is at the root); the windows spec's §8 tail ("still not click-verified") and its §3 gate (answered by §0.6.13's merge-loser capture) amended. Nothing deferred was dropped: the API-generated mind map, the Notion API import, the widget wallpaper model, block-level FTS, syntax highlighting and the device id stay deferred in §10. | §4, §0.10, §9.2, §3.5, §10, §1 |
| 2026-09-22 (the fourth verb — Mind map) | The deferred list ordered and scored in one batch (Tier 1 + block FTS chosen; the selection is what is sent; a named language set for highlighting; one mock — the sheet). **§10's Claude-generated mind map built** as §0.6.15's fourth verb: `AiVerb.MIND_MAP` (one instruction — a nested Markdown list, one root), `domain/ai/OutlineReply.kt` (`parseOutline`: markers, tabs or any step, a preamble dropped, a depth jump clamped — pure, 6 cases), `PageDetailViewModel.insertOutline` (bulleted blocks after the selection's block at its depth, the parent chain, §0.6.2's flag on the root, one recorded edit — one undo), the sheet's rows preview (`heading` root, 18 dp a level, ellipsised) with *Insert as a mind map* / *Insert as a list*; the verb row a `FlowRow`. `docs/mockups/ai-mind-map.html`, `docs/critiques/ai-mind-map-mock.md` (three findings, all applied: no accent dot, no `nowrap`, no *Cancel* under Touch) and `-function.md`. Walked on both devices against the real endpoint (a dummy key, a genuine 401); the success path pinned by `WritePathSyncTest` (the tree on both devices, one undo). Two defects fixed on the walk: the phone's verb row had 20 px for the fourth button (letter-wrapped) and the saved-key line named three verbs. Tests 917 → 924. | §0.6.15, §10 |
| 2026-09-22 (syntax highlighting; the export's CLAUDE.md) | §10's code-block deferral built: `domain/code/Highlight.kt` — a hand-rolled tokenizer (keyword · string · comment · number; grammars for the named set, a family's comments for the rest, strings and numbers for any tag; pure, 5 cases), painted by `spansVisualTransformation` (`CodeColours`) from the token map — keyword `third`, string `event`, number `habit`, comment `textDim`; measured on Ink dark 9.1 / 9.8 / 6.7 / 7.2 : 1, the three hues 60°+ apart, none the accent. Walked on both devices (Kotlin on the desktop, Python on the phone, the generic pass on *Plain text*); Kotlin's soft keywords (`open`, `set`, `get`, `it`…) taken out of the set on the walk. **Item 17's remainder**: the Markdown zip carries `CLAUDE.md` at its root (`markdown/AgentReadme.kt` — the folder's shape, the three rules; tested through the real zip). `docs/critiques/syntax-highlighting-function.md`. Tests 924 → 930. | §3.1.1, §10, §0.10 |
| 2026-09-22 (the widget readout on the real wallpaper) | §8.6 resolved and §10's two widget bullets closed: the configure screen reads `WallpaperColors` (`widget/WallpaperRead.kt`), audits the five roles on that colour beside the bracket (`auditOnWallpaper`) and offers the smallest passing opacity as a tap (`suggestedOpacity`); the mode stays the app's (§8.7). One robustness defect fixed on the walk (the saved-state read threw for a foreign widget id). Walked on the phone: #AC0B23, 88 % → 35 % offered; at 35 % the bracket fails all five, the wallpaper passes all five. `docs/critiques/widget-wallpaper-function.md`. Tests 930 → 934 (`WallpaperAuditTest`, the widget module's first). | §8.6, §10 |
| 2026-09-22 (block-level search — v25) | §10's last search deferral built: `block_fts` (`data/page/BlockFts.kt`, one FTS4 row per block with text) beside `page_fts`, rebuilt by `PageContentRepository.rebuildFtsForPage` and healed on open (`healIndex`'s block half); `MIGRATION_24_25` creates the table, the first launch fills it; the switcher searches blocks (`rankPageHits` over `BlockSearchHit`: title-prefix pages first, then at most three block rows a page, a multi-line block's snippet on one line) and a block hit opens the page at the block with the find bar on the query (`WorkbenchNavState.openBlock` → `BlockJump` → `PageDetailScreen`'s `findTarget`). `PageContentRepository` takes the DAO as a required parameter (both containers; thirteen test sites on `FakeBlockFtsDao`). Walked on both devices: v24 → v25 in place, the heal's rows counted, a hit landing on its block with the marks; one defect fixed on the walk (a three-line block's match past the row's line). `docs/critiques/block-fts-function.md`; §3.1.1 amended, §4's register row. Tests 934 → 935; `25.json`. | §3.1.1, §3.1.7, §4, §10 |
| 2026-09-22 (audit: a merge re-arms and publishes what it changed) | §9.7's seam, third and final shape. A merge writes entries and reminders straight to Room without passing the `EntryScheduleCoordinator`, so nothing armed an alarm or published to the Calendar Provider (§3.2) for what arrived. The first fix called `reconcileAlarms` after every pass — but that is two sweeps, the alarm one *and* the Provider backfill, which upserts every dated non-Google entry cross-process; a pass runs on `onStart` **and** `onStop`, so opening and closing the app rewrote the whole calendar twice, on an application scope under `NonCancellable`. The second fix split the alarm half out, which took the cost away and left the system calendar a launch behind. Neither was necessary: `SnapshotMergeResult` now reports `touchedEntryIds` — the local ids the pass actually wrote, entries and the entries owning written reminders — and `SyncCoordinator` puts each through `onEntryChanged`, which is the one place that arms the alarm and publishes the row. Work proportional to what changed; usually nothing, because the last-write-wins guard skips an unchanged record before any write. `MergeTouchedEntriesTest` (4 cases) pins the ids, that a row both passes touch is reported once, and that a second merge of the same folder reports nothing — the last is what makes "proportional" a claim rather than a hope. The Android call site has no JVM proof (`SyncCoordinator` resolves through `AppContainer.from`); it is pinned by the compiler and the walk. Found by `/code-review` on the branch. 941 → 945 tests. | §3.2, §9.7, §9.9 |
| 2026-09-22 (audit: every bulk write re-arms what it moved) | §9.7's invariant — every write that moves when something is next due re-arms it — held in the ViewModels and nowhere else. Four write paths reached Room around them and armed nothing: an Import and a Restore (`PortableArchive`, which now takes a required `rearmAlarms` supplier wired to `reconcileAlarms`), the folder merge (`SyncCoordinator`, unconditionally after a successful pass — `SnapshotMergeResult` does not report whether entries changed, and a flag for it would put the invariant behind a boolean a later path could forget to set), and the Habits widget's check-in callback (the one of five check-in paths that did not re-arm; its undo direction does not self-heal). A Restore was the worst of them: `reconcileAlarms` runs only in `MainActivity.onCreate` and the boot receiver, and `configChanges` swallows a rotation, so every restored reminder sat unarmed until the next cold start. Proved red-to-green on the two archive paths; the widget's is pinned by the compiler and a walk, a Glance `ActionCallback` being unreachable from the JVM suite. 941 tests. | §9.7 |
| 2026-09-22 (audit: an ordinal `BYDAY` that can only limit) | §4.1.1 amended. `RecurrenceSpec.parse` returns null for an ordinal `BYDAY` beside `BYMONTHDAY`, and for one under DAILY or WEEKLY (RFC 5545 forbids a numeric `BYDAY` there) — the expander could only *expand*, so it dropped the term and emitted every `BYMONTHDAY` date: `FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=1FR` produced all twelve 13ths of 2026, eleven phantom, against §4.1.1's own doctrine that a missing occurrence beats a phantom one. Such a rule now falls to its first occurrence alone. A plain `BYDAY` still limits (`BYMONTHDAY=13;BYDAY=FR` — Friday the 13th) and an ordinal alone still expands. Reachable from any imported `.ics` (`IcsImporter` passes an RRULE through verbatim), not only from the Google pull. Found by `/tendril-audit`; `RecurrenceExpansionTest` gains 2 cases. 947 tests. | §3.2, §4.1.1 |
| 2026-09-22 (audit: a stale comment about the desktop's scheduler) | Comment only, no behaviour change. `WorkbenchCore`'s KDoc said desktop's `entryScheduleCoordinator` is "a no-op (see `NoOpEntryScheduleCoordinator` there)" — that class no longer exists anywhere in the tree, and desktop has wired the real `DesktopReminderScheduler` since B§13.6 #7 (which `DesktopAppContainer`'s own KDoc records). Read literally the old text says the desktop never arms anything, which is the wrong conclusion that let the merge go unplanned there (audit row 1.17). Logged because it is a `shared/` edit, not because a decision changed. | §9.7 |
| 2026-09-22 (audit: the arrow editor wrote from a stale snapshot) | §3.7 traced against the tree, claim by claim — the first spec-trace pass this repository has run (both recorded `--full` audits skipped it for want of an `--against`). Four claims checked, three stale, one accurate and understated. **Stale:** "not available on desktop" (it has been since 2026-09-11, which the *companion spec already recorded* — the two documents disagreed for eleven days); "the screen and ViewModel live in `Tendril android`" (both in `shared/commonMain/ui/canvas/`, routed by `PageRoute`); "`updateTitle` is the one mutation outside the funnel" (three are — `trashPage` and `saveAsTemplate` joined it, all three gated, so the count was stale and not the guarantee). **Accurate:** the arrow editor writes from the snapshot it was opened with. Its recorded consequence, a direction stuck one step past the start, is the mild half: the label field fires per keystroke and wrote `edge.copy(label = …)` on that same snapshot, so typing a label put the *opening* direction back — a change that had landed, silently undone, in a section that claimed neither defect was data-loss. `CanvasEdgeDao.getById` added (a `@Query`, so no schema version and no migration); `cycleEdgeDirection` and `setEdgeLabel` re-read through it and take only `id` from their argument, following §9.7's precedent that correctness belongs to the write rather than to each caller's freshness; `CanvasScreen` resolves `editingEdge` against the live `edges` list so the sheet shows the new value. `CanvasStaleEdgeTest` (3 cases), both defect cases red before green. The display half is pinned by the walk, not by a test. 945 → 948 tests. | §3.7, §9.4 |
| 2026-09-23 (audit 1.8: §2.2's “Enter writes once” becomes executable) | Desktop code only — `Tendril windows/tendril-windows-spec.md` carries the implementation row; this one exists because the claim being pinned is **written here**, in §2.2's description of the chord's popup, and because `spec_trace.py --section 2.2` reported **23 claims, 11 source citations, 0 test citations**. §2.2 was already the top row of that tool's unpinned worklist and the reason Pass 1 was made default-on; this is the first of its claims to acquire a test. The one-write guard moves from a flag inside `QuickAddWindow`'s Enter lambda to `QuickAddGate.kt` (`QuickAddWriteGate.start()`, true once per instance, claimed outside `launch`), and `QuickAddGateTest` pins it in four cases — the second Enter with the first write held in flight by a `CompletableDeferred`, a fresh open writing again so the first cannot pass vacuously, a blank line, and the claim being taken before the dispatcher runs at all. Mutant killed: dropping the early return gives `expected:<1> but was:<2>`, the duplicate pair the 2026-09-22 walk measured. **No behaviour changed** — same branch, same order, same 700 ms. §2.2 amended in place to name the guard's home. Desktop suite 8 → 12. *(Dated 2026-09-24 until that day's audit: the commit date, where its Windows twin and the audit give the day the work was done.)* | §2.2 |
| 2026-09-24 (audit 1.1: a root page called CLAUDE broke the Markdown export) | §0.10 item 17 amended. Since 2026-09-22 the zip's root carries its own `CLAUDE.md`, written last, but `MarkdownExporter.filePathsFor` resolved name collisions per directory without knowing that name was taken — so a root page titled `CLAUDE` wrote a second `CLAUDE.md`, and `ZipOutputStream` threw a `ZipException` that **failed the whole export**; one titled `Claude` wrote two entries that extraction onto Windows or macOS merges into one file, the readme or the page lost. `docs/critiques/syntax-highlighting-function.md` recorded the collision on the walk and left it "recorded, not fixed". The root's collision set now starts with the readme's name (`README_STEM`), so such a page becomes `CLAUDE (2).md`; below the root the name is free. `jvmCommon`, so both apps' exports (Android Settings, the desktop's `DesktopSettingsScreen`) take it. `MarkdownExporterTest` +3 — red first (the `ZipException`, then the merge), green after. | §0.10 |
| 2026-09-24 (audit 1.2, 1.3: two writes outside View-Only) | §3.1.2 amended, from a sweep of all 164 mutating entry points. **1.2** — `PageDatabaseViewModel.setHue` checked no lock (its `launchAndTouch` does not, unlike Canvas's), so a hue sheet open when View-Only went on still wrote and bumped the page. **1.3** — `PageDetailViewModel.undo`/`redo` moved `BlockUndoStack` before `launchAndReindex` refused the write, spending the step: after unlocking, Ctrl+Z reversed the edit before the newest, and the newest could not be undone. Both check the lock first now. `onOpened`'s reference-cache refresh is named as a repair-on-open exemption; the Notion import's UI-only gate is recorded. Shared ViewModels, so both apps. `ViewOnlySurfacesGuardTest` +2 (the refusal and its unlocked control), `WritePathSyncTest` +1, each red first. | §3.1.2 |
| 2026-09-24 (audit 1.4, 1.5: Sync-to-Tasks armed nothing; a moved occurrence left its series' alarm) | §9.7 amended. **1.4** — `DatabaseSyncManager.enableSync`/`bindProperty` (and `rebindProperty` through it) wrote dated Entries with no coordinator anywhere on the path; it is now a required constructor parameter (both containers, six test sites) and every insert or move is reported. On the desktop each report is a `replan()`, which cancels the previous loop, so a bulk sync-on collapses to one. **1.5** — Android only: `AndroidEntryScheduleCoordinator` handed an exception row re-armed that row alone; it now re-arms the series with its exceptions. The desktop's `replan()` reads every entry and was not affected. `DueDateBindingTest` +2, `AndroidEntryScheduleCoordinatorTest` (new: the series case as control, the exception case red first). The sweep's other misses are recorded in §9.7 as device-bound. | §9.7 |
| 2026-09-24 (audit 1.6: an .ics import armed the occurrences it had just skipped; §3.2's stale sentences) | §3.2 amended. `IcsImporter` reported a recurring base to the coordinator before inserting its EXDATE skip rows, and never reported the skips — so the scheduler, which reads a series' exceptions when it arms it, armed the skipped occurrences on both platforms, against §3.2's "writes every row through the coordinator". The base is now reported after its skips. `IcsRoundTripTest` +1, red first. The same claim-by-claim trace of §3.2 (99 claims, 79 matching) found five sentences a later decision superseded that still read as current — the week grid's 56/44, "day N of M" being the Day view's alone, session-only layers and the unbuilt habit layer, "desktop has no Settings" — each now carries a dated note; the rest are listed in `docs/audit-2026-09-24.md`. | §3.2 |
| 2026-09-25 (audit 5a.2: one added row claimed an edit to every row) | §9.4 amended. On a Sync-to-Tasks database, `addRow` and `LabelMembership.ensureTask` seeded a row's task by calling `enableSync` with that one id, and `enableSync`'s `commit(touchRows = true)` bumped the database page and every row. Under last-write-wins each untouched row's new timestamp beat any real edit to it still unsynced on the other device, so adding a row on the phone could silently undo a cell edited on the desktop. `DatabaseSyncManager.addRowToSync` seeds the one row's Entry and writes nothing else; `enableSync` keeps its commit for the real sync-on, which does clear every row's bound columns. Shared, so both apps. `WritePathSyncTest` +1, red first (the untouched row's timestamp moved). Chosen over per-block merge: whole-page LWW stands, and only the false claim goes. | §9.4 |
| 2026-09-25 (audit 5a.1: a Google event id the merge could not carry) | §9.5.1 amended. The phone sets `Entry.googleEventId` after a push without moving `updatedAt`, so the snapshot merge's record-wise last-write-wins never carried it. A peer at the same timestamp never learned the id. A peer's later edit, made without it, won and wiped it on the phone, whose next push inserted the event into Google a second time. This happens with one phone and the desktop: the desktop never pushes to Google itself, but its edits carried a null id. Bumping on the push was rejected, because it claims an edit nobody made (5a's class). `SnapshotSyncOrchestrator.adoptedGoogleEventId` merges the one field field-wise: a known id beats an absent one whichever record is newer; between two known ids the newer record's stands; a trashed winner keeps its own. `jvmCommon`, so both apps merge this way. `GoogleEventIdSyncTest` (new): two tests red first, one control. | §9.5.1, §9.4 |
| 2026-09-16 (the type vocabulary) | §2.3 amended: Inter bundled and the default (`THIRD_PARTY_NOTICES/OFL-Inter.txt`), every family at true 400/500/600 through `variationSettings` (the desktop had drawn every Medium as Regular), the eye pass 400/500/600, the scale 11 · 12.5 · 14 · 16 · 18 (+ 20 / 24 for the editor's H2 / H1), **seven styles** in `ui/theme/TendrilType.kt` with the element map, the editor's own sizes, `tools/audit.py` rule 12 *literal type*; 45 literal sizes, 31 weights and 40 `bodyLarge` chrome sites folded. Critiques: `docs/critiques/type-vocabulary-mock.md`, `-function.md` (measured beside Notion). Desktop verified; the phone pending. Tests 795. | §2.3 |
| 2026-09-16 (hover previews) | §3.1.1 amended (B§13.6 #3): `domain/preview/PagePreview.kt` (`pagePreview`, `referencePreview`, `databasePreview`, `canvasPreview`), `ui/components/HoverPreview.kt` (`hoverPreview`, `HoverPreviewState`, `HoverPreviewCard`); the four targets (the inline span through the field's `TextLayoutResult`, the mention block, the block-reference card, the Road Map's nodes — the shelf's and a pop-out's too); §3.4 one line; §2.2 the density factors **0.85 / 0.95 / 1.23** (the user's mid-walk note beside Notion — Compact read a bit large; measured in `docs/critiques/hover-preview-function.md` #4); §0.10 item 14's after-the-pass list: #3 done. `PaneChrome.openBeside` / `openInWindow`. Critiques: `docs/critiques/hover-preview-mock.md`, `-function.md`. Desktop verified; the phone composes nothing. Tests 788 → 795. | §3.1.1, §3.4, §2.2, §0.10 |
| 2026-09-16 (drag between panes) | §3.2 amended (B§13.6 #5): the Calendar's task tray (`domain/plan/Tray.kt`, `ui/calendar/TaskTray.kt` — the pane and the Touch strip), the drag (`ui/components/Pointer.kt` `dragSource`), the targets (`ui/calendar/DropGeometry.kt`), `EntryEditor.clearWhen` / `CalendarViewModel.unschedule`; `WeekGridView` reports its geometry and takes an external target; the Week strip and the Month grid report their cells. §0.6.14: the Timeline's *No date* rows drag onto a day, and **the bar envelops its title** (the user's three mid-walk notes — Notion's rule). §2.2 *A drag's start*. §0.10 item 14's after-the-pass list: #5 done. Critiques: `docs/critiques/drag-between-panes-mock.md`, `-function.md`. Desktop verified; the phone's strip pending. Tests 778 → 788. | §3.2, §0.6.14, §2.2, §0.10 |
| 2026-09-16 (pop-out windows) | §3.1 amended (B§13.6 #6): a page in its own OS window on the desktop — `ui/nav/WorkbenchEnvironment.kt` (the scaffold's four locals, extracted; a pop-out takes the main window's scale), `ui/nav/PopOuts.kt` (`PopOutHost`, `PopOutRegistry`), `PageRoute.onShowOnRoadMap`, `WorkbenchNavState.depth`, `WindowFrame.decode(min)`, the openers in the workspace chrome and tree rows (Shift+click), the tree's ⧉; the desktop's `PopOutWindows.kt`. §2.2 the keyboard row. §0.10 item 14's after-the-pass list: #6 done. The shelf now closes on a trashed page. Critiques: `docs/critiques/pop-out-mock.md`, `pop-out-function.md` (and the dev-database wipe by the installed preview exe, recovered). Desktop verified; the phone untouched by construction. Tests 773 → 778. | §3.1, §2.2, §0.10 |
| 2026-09-16 (14h·2 — the small things) | Thirteen items, each at its home: §2.3 the type scale (`TypeScale`, six sizes on 1.125; `tools/audit.py` rule 12); §2.2 *Submenus and captions* (`ui/components/Submenu.kt`, `openVerb()`, `DensityProfile.rowHeightDp` / `listInteractiveMinDp`); §3.1.1 find over the whole outline with folded matches counted and unfolded on ↵ (`domain/MindMapFold.kt`), the scroll only off-screen, the mention on `accentSoft`, the ground click; §3.1 the tree's second mark, the chip row, the card's *edited* line (`domain/time/RelativeTime.kt`, `rowsLabel`); §3.4 the map's self-refresh (`PageRelationDao.observeAll`); §5.6 the Table's rows and footer. **§0.10 item 14 closed — the desktop pass is done.** Critiques: `docs/critiques/small-things-measured.md`, `small-things-function.md`. Desktop verified; the phone walk pending. Tests 765 → 773. | §2.2, §2.3, §3.1, §3.1.1, §3.4, §5.6, §0.10 |
| 2026-09-16 (14h·1 — the shelf) | §3.1 amended: a third pane on a wide window (`ui/pages/ShelfState.kt`, `ShelfPane.kt`; `ui/roadmap/RoadMapNeighbourhood.kt` over the shared `RoadMapCanvas`); *Show beside ▸* in the workspace chrome (`PaneChrome.menuItems` gains `close`, `compact`), *Open beside* and Ctrl+click on tree rows, `PageRoute` takes `onOpenPage`/`findRequest`; `TOGGLE_SHELF` = Ctrl+Shift+\ in the keyboard table; `pages_shelf`, `pages_shelf_last`, `pages_shelf_width`. §0.10 item 14: 14h·1 shipped. Critiques: `docs/critiques/shelf-mock.md`, `shelf-function.md`. Desktop verified; the phone walk pending. Tests 759 → 765. | §3.1, §2.2, §0.10 |
| 2026-09-16 (14g·3 — the urgency ladder) | §0.6.4 amended: `Entry.importance` 0–4 replaces the flag, **schema v20** (`MIGRATION_19_20` rebuilds `entries`, `important = 1 → 3`; `20.json`), `domain/urgency/Urgency.kt` (`timePressure`, `urgencyOf`), the ladder in the engine (`Ladder`, `solveToTarget`, `TendrilPalette.ladder`), `ui/components/UrgencyMarks.kt` (stripe, dot, picker), rows, timed blocks and all-day chips striped, the pane's row and chip, the row menu's *Urgency ▸*, the edit sheet's picker, quick add `!` / `!!`, ICS `PRIORITY`, the snapshot's two fields; `ui/settings/TaskSettings.kt` shared (`show_urgency` on by default, `show_habit_streaks`; Android's `TaskPreferences` migrated once and deleted; the desktop pane's Tasks section). §0.5.2's clause; §2.3's ladder row; §3.2, §3.3, §4 amended; §0.10 item 14: 14g done. `tools/audit.py`'s stripper now skips char literals (`'"'` had swallowed the rest of `Ics.kt`). Critiques: `docs/critiques/urgency-ladder-mock.md`, `urgency-ladder-function.md` (the stripe/text overlap seen by the user, fixed). Desktop verified on the real DB; the phone walk pending. Tests 753 → 759. | §0.6.4, §0.5.2, §2.3, §3.2, §3.3, §4, §0.10 |
| 2026-09-16 (14g·2 — the token map) | §2.3 gains *The token map*: `fanHue` (event −120°, habit +60°, the third 180°, the second channel taking the third slot), the error family solved on its soft, `findSoft`, `TendrilPalette` +9 tokens and `dark`, `ui/theme/DataColours.kt` (`labelColours`, `calloutColours`, `CALLOUT_COLORS`; `solveHue`'s `tintShare`), `Theme.kt` maps `tertiary*`/`error*`/`secondaryContainer`; `spansVisualTransformation` takes the link and mention colours (the two hardcoded blues gone), canvas edges dim, `ui/calendar/LayerColours.kt`, the Road Map's node kinds, the Timeline's blocker and today, callouts as bar + tint, label chips coloured (§3.1.6, §3.1.1 amended). `tools/audit.py` rule 11 *hardcoded colour*. §0.10 item 14: 14g·2 shipped; the small-things list closes the `find` token and gains two. Critiques: `docs/critiques/coloured-elements-mock.md`, `coloured-elements-function.md`. Desktop verified; the phone walk pending. Tests 748 → 753. | §2.3, §3.1.1, §3.1.6, §0.10 |
| 2026-09-16 (14g·1 — the theme model) | §2.3 rewritten as the model: ground × hue × mode, ten registers (`ui/theme/Registers.kt` — Clay, Moss and Mauve return solved on the cold ground), every token solved to a floor (`ColorSolve.kt`, `paletteFor`; text into the 9.5–13:1 band, dim/faint on both grounds, the accent by lightness alone, marks to 3:1), mode System default, `ThemeSettings` in `KeyValueStore` on both platforms with the phone's `ThemePreferences` migrated once and deleted, `ThemeSection` shared, the type scale clamped to 400/500. `Theme.kt` maps the surface-container family; the scaffold sits every route on one `Surface`. §0.10 item 14: 14g·1 shipped. Critiques: `docs/critiques/registers-mock.md`, `registers-function.md`. Desktop verified; the phone walk pending. Tests 736 → 748. | §2.3, §0.10 |
| 2026-09-16 (14f·2 — the Calendar's week grid) | §3.2 amended: `ui/calendar/WeekGridView.kt` (seven Plan lanes; date and time in one drag, `CalendarViewModel.moveBlock`), `domain/plan/WeekLayout.kt` (`laneAt`, `openScrollMinute`, `visibleAllDay`), `CalendarDefaultView.kt` (`calendar_default_view`, `defaultCalendarView`), `CalendarOpensOnSection` on both platforms' settings, `QuickAddBar.kt` (the strip on a wide window). §3.3's filter rows into the list column. §0.10 item 14: 14f·2 shipped — the pass's surfaces done; item 21 added (the desktop's clipboard crash). Critique: `docs/critiques/calendar-function.md` (+ the addendum in `tasks-calendar-mock.md`). Desktop verified; the phone walk pending. Tests 732 → 736. | §3.2, §3.3, §0.10 |
| 2026-09-16 (14f·1 — Tasks on a wide window) | §3.3 amended: two panes from 840 dp (`LocalShellLayout`), `ui/taskshabits/TaskDetailPane.kt` (task and habit panes, chips = the menu by name), `ui/components/PaneHandle.kt` (`PaneWidthState`, `tasks_list_width`), rows' hover `···` / right-click / long-press, the habit row's × → its menu, `ui/trash/` shared (`EntryTrashSheet`, `HabitTrashSheet` take `WorkbenchCore`; §0.10 item 13 resolved), the keyboard on Habits, Merged and the phone's Pages list. Critiques: `docs/critiques/tasks-calendar-mock.md`, `tasks-function.md`. Desktop verified; the phone walk pending. Tests 730 → 732. | §3.3, §0.10 |
| 2026-09-16 (find in page) | §3.1.1 amended: the find bar (option A of `docs/mockups/find-in-page.html`), `domain/find/FindInPage.kt`, `ui/pages/FindBar.kt`, `FindMarks` on `spansVisualTransformation`, Ctrl+F in `Shortcuts.kt` and `WorkbenchNavState.findRequested`, *Find in page* in the page's `···`. §0.10 item 19 resolved; item 14's list gains the `find` token and two find notes. Critiques: `docs/critiques/find-in-page-mock.md`, `find-in-page-function.md`. Desktop verified; the phone walk pending. Tests 725 → 730. | §3.1.1, §0.10 |
| 2026-09-16 (14e — the keyboard) | §2.2 *The keyboard* (new bullet): one binding table (`ui/nav/Shortcuts.kt`) and the F1 card generated from it (`ShortcutsOverlay.kt`); Alt+← / Alt+→ and the mouse's side buttons with a forward list on `WorkbenchNavState`; Ctrl+Shift+N's `requestQuickAdd`; `ui/components/ListKeyboard.kt` on the tree and the Tasks list. §0.10 item 14: 14e shipped; item 18 struck (resolved by 14d); item 19 (find in page) added. Critiques: `docs/critiques/keyboard-desktop.md`, `keyboard-function.md`. Tests 713 → 725. | §2.2, §0.10 |
| 2026-09-15 (14d — under a pointer) | §2.2 *Under a pointer* (new bullet): right-click = the pointer's long-press on rows and blocks (`ui/components/Pointer.kt`, `TextContextMenuExtras` expect/actual), hover-revealed `···` at 28 dp, the toolbar floating under a pointer profile (`LocalDensityProfile`), `PagesViewModel.moveToTrash` lock-gated; §3.1.1 amended (the toolbar's two homes). Critique passes in `docs/critiques/`; their High: the RELATION branch in the row-as-page strip. `MoreHoriz` on every bar, the tree header at the page bar's height, the outlined journal book. §0.10 item 14: 14d shipped, the deferred list. Tests 711 → 713. | §2.2, §3.1.1, §0.10 |
| 2026-09-15 (14c — two panes) | §3.1 amended: on a wide window the Pages tab is the tree beside the open page (`PagesWorkspace`, `PagesTreeState`, `PaneChrome`, `PageRoute`); chrome split per pane — option B of `docs/mockups/pages-two-panes.html`; `showPage` replaces, `openPage` pushes; `PageDao.observeChildren`/`observeParentsWithChildren`; Ctrl+\ on the desktop; the phone unchanged. §0.10 item 14: 14c shipped. Tests 703 → 711. | §3.1, §0.10 |
| 2026-09-15 (14c·0 — the scale) | §2.2: every measurement proportional to the screen on both platforms — `LocalDensity` × `shellScaleFor` (shorter side ÷ 800 dp clamped 0.85…1.25, × Compact 0.9 / Comfortable 1.0 / Touch 1.3); the phone fixed Touch, the desktop's profile in Settings (`density_profile`). Measured on both devices. §0.10 item 14: 14c·0 shipped. Tests 698 → 703. | §2.2, §0.10 |
| 2026-09-14 (14b — slide-overs) | §3 *Bottom sheets* amended: on a wide window (the shell's 840 dp rule, `sheetFormFor`) `TendrilSheet` is a right slide-over as mocked (`ui/components/SlideOver.kt`: 32 % scrim, 440 dp panel, header with × unless the sheet draws its own, bounded body; scrim / Escape / Back close); signature unchanged at 33 sites; menus and dialogs stay. §0.10 item 14: 14b shipped, two 14a fixes noted. Tests 697 → 698. | §3, §0.10 |
| 2026-09-14 (14a — the shell) | §2.2 amended: the shell drawn as the desktop mock draws it on both platforms (`ui/nav/Shell.kt` — 84 dp rail / 80 dp bottom bar with pills, 36 dp timer strip or rail foot, 52 dp top bar at twelve screens; no Material `Scaffold`/`NavigationBar`/`NavigationRail`/`TopAppBar` in the shell), the 840 dp window-width breakpoint (`ShellLayout`), the desktop window's remembered frame and *Tendril* title, desktop sync controls into Settings. §0.10 item 14: 14a shipped. Tests 691 → 697. | §2.2, §0.10 |
| 2026-09-14 (data colour) | B§13.8: colour is assigned to a dimension, not a thing. Tasks — urgency is the colour (a five-step heat ladder, a nearing deadline moves a task up it; `Entry.important` → `importance` 0–4 when built; the ladder one coral family in every register, a mark never text, low at 3:1, dark grounds pale → saturated), deadline ⚑ and repeat ↻ are glyphs, category is the database's or first label's hue as chip and calendar fill. Databases — any hue on the wheel, icon mandatory, their dates wear it. Event/habit layers fanned from the accent; labels re-solved per ground; callouts as tints; the hardcoded blues and grey canvas edges tokenised. Third mock `docs/mockups/coloured-elements.html`. Document only. | §2.3 |
| 2026-09-14 (themes and colour) | §2.3 amended to B§13.7's model: ground (neutral or cold, never warm) × one hue solved to 8.0/7.6:1 × mode (System default); seven registers — Ink, Console, Swiss, Playground, Blush, Chalk, Kodachrome — settled on the two mocks under `docs/mockups/`; derived tokens solved to a floor; the eye pass; OLED as a phone-only setting; data-colour capacity measured per structure (importance ladder passes everywhere, six database colours nowhere) → *colour is never the only channel*. Not yet built; lands with 14g. Document only. | §2.3 |
| 2026-09-13 (§0.10 item 14: desktop layout benchmark) | `docs/benchmarks.md` §13 written: the desktop's layout today measured (bottom bar, 29 sheets + 31 dialogs, no `WindowState`, phone rows, two shortcuts, fixed theme), the bar on six layout axes (Notion, Obsidian, Things as the frame; ten more), the patterns, and a pass of seven PRs 14a–14g with every decision answered on the interactive mock `docs/mockups/desktop-shell.html` — rail, right slide-overs, resizable + collapsible tree, measurements proportional to the screen on both platforms (one `LocalDensity` scale × a Compact / Comfortable / Touch profile chosen in desktop Settings, Compact default, hover-only controls), no Alt-mnemonics, the calendar's opening view as a setting (Week default), theme System / Light / Dark (System default). B§13.6's ten further diffs answered (1, 2, 8 folded into 14d/14e; 4 → 14h; 3, 5, 6, 7 after the pass; 9 no; 10 → item 19); item 20 opened (JNA 5.6.0 found in the Gradle cache: DPAPI wrap and a global hotkey are possible offline). §0.10 item 13 folded into 14f. Document only; no code. | §0.10 |
| 2026-09-13 (step 8g: Claude verbs) | **§0.6.15** written and done; **§0.8 step 8 complete** (the row updated). `domain/ai/` (verbs, request/response records, failure wording — pure, tested; `ClaudeClient` over `HttpURLConnection`, built per press), `ui/pages/AiResultSheet.kt`, the verb row on the selection toolbar (key-gated), `data/prefs/AiKeyStore` (Android over `SecretStore`, desktop `FileAiKeyStore`), `ui/settings/AiSettingsSection.kt` shared (Android's private section deleted), the desktop's first Settings pane — `NotAvailableOnDesktop` deleted. §3.5 amended; §0.10 items 17 and 18. Verified on both devices: no row without a key; a dummy key → the row → *Rewrite* → a genuine 401 → "The key was rejected"; Clear removed the desktop's file; `prefs.properties` never held the key. 690 tests. | §0.6.15, §0.8, §3.5, §0.10 |
| 2026-09-13 (step 8f: Timeline) | **§0.6.14** written and done. Schema **v19** (`page_database_views.endDatePropertyId`, `page_databases.blockedByPropertyId`, `MIGRATION_18_19`). `ViewType.TIMELINE`, `ui/pages/TimelineView.kt`, `domain/timeline/Timeline.kt` (pure, tested); `PageDatabaseViewModel.setDateCell` routes every view's date write; *Blocked* on Table/Board/Gallery, dependencies drawn on the Timeline; `···` → *Blocked by…*. §5.6 and §4 updated. Verified on both devices (v18→v19 in place; desktop: a bar dragged three columns → *Read on* 2026-09-16 → 09-19, a self-relation bound, the chip appearing when the blocker's Done is cleared, the dependency line; phone: *Errands*' Deadline is bound as the task's due date, the dragged bar moved `entries.dueDate` and the Tasks tab showed *due 2026-09-13*). 686 tests. | §0.6.14, §5.6, §4 |
| 2026-09-13 (step 8e: page history) | **§0.6.13** written and done. Schema **v18** (`page_revisions`, `MIGRATION_17_18`) — the first table that stays home (§9.4 note, §4 entity). `domain/history/PageHistory` (edit / merge / restore captures; ten-minute window, dedupe, fifty per page; serialised), `sync/BlockSnapshots.kt` (the merge's block rebuild extracted and shared), `ui/pages/HistorySheet.kt`; `PagesSyncEngine` keeps the local body before a winning record replaces it. §0.10 item 16. Verified on both devices (v17→v18 in place; ten keystrokes → one revision; preview; Restore brought a nested tree back with children relinked and uids kept; the merge-loser case by `PageMergeTest`). 682 tests. | §0.6.13, §9.4, §4, §0.10 |
| 2026-09-13 (step 8d: block references) | **§0.6.12** written and done. Schema **v17** (`blocks.referencedBlockUid`, `MIGRATION_16_17`). `BlockType.BLOCK_REFERENCE` + `ui/pages/BlockReference.kt` (card, picker); `((` and the slash sheet; the cache refreshed on open without timestamps; the snapshot carries the uid verbatim, no quarantine. §3.1.5 amended: **Unlinked mentions** with *Link* (`domain/references/UnlinkedMentions.kt`, pure, tested). Also: a page's mentions now reload on every open (they loaded once per ViewModel life, which outlives the route). Verified on both devices (v16→v17 in place; desktop: `((` → *Child* → card, source edited → card live, cache refreshed on reopen with timestamps unchanged, *Link* moved the Journal root from Unlinked to Linked; phone: the slash sheet's *Block reference* → *Jackets · Trip*, tap → Trip, *Link* on "notes for the trip"). 677 tests. | §0.6.12, §3.1.1, §3.1.5, §4 |
| 2026-09-13 (step 8c: Road Map) | §3.4 amended: `ui/roadmap/` → `shared/` (desktop parity; `roadMapContent` slot retired), `domain/roadmap/RoadMapGraph.kt` pure and tested (types, depth walk, `RoadMapFilter` — Journal hidden by default, kinds, one label; persisted `roadmap_filter`), tinted edge kinds, "Show on Road Map" from a page's `···`. Two layout defects fixed (first-frame settle; px-space repulsion). §0.10 item 15 (B§6 #16's remainder). Verified on both devices: chips, the label filter, the focus handoff at depth 1 and 2, "Relate to…" on the desktop; the filter survived a process kill on the phone. 671 tests. | §3.4, §0.10 |
| 2026-09-13 (step 8b: Journal shows today) | §3.1.4 amended: today's Journal page opens with a checkable *Today* strip — the day's tasks (`EntryOccurrences.onDay`) and due-or-done habits — live, never blocks; today only. `domain/journal/JournalToday` pure and tested; `PageDetailViewModel` takes `HabitDao` + `CheckInHabitUseCase`. Verified on both devices (desktop: `>jour` → *Read chapter 3 · 21:00* and *Stretch*; a tick and its undo landed as a check-in + tombstone and a `DONE` resolution; no strip on a plain page. Phone: *Call bank*, *Stretch 09:00* first, *Meditate*; Meditate ticked from the strip showed filled on the Habits tab with its streak; yesterday's page and the Journal root show nothing). 666 tests. | §3.1.4 |
| 2026-09-12 (step 8a: switcher) | §3.1.7 amended: the quick switcher / command palette (`domain/SwitcherQuery` pure and tested; `ui/switcher/QuickSwitcher`, owned by the scaffold, Ctrl+K on desktop) replaces the Pages search overlay. §3.1.1's defect fixed: titles in the FTS index, re-index on rename and at creation, **schema v16** (`page_fts` emptied) + `healIndex` at start. §0.10 item 5 resolved. Verified on both devices (the heal: 3/3 and 4/4 pages re-indexed with titles first; `boo` → *Books v12*; `>rev` → Review; `trip` found by title on the phone; `>jour` opened today's Journal). 663 tests. | §3.1.1, §3.1.7, §0.10 |

---

## 0. Objectives (Decided 2026-09-11 — the section every later one is filtered through)

**Why this section exists, and why it is §0.** The rest of this file records *how*: one feature at
a time, with the corrections each one accumulated. This section records *what for*, *what it must
never do*, *what it is measured against*, and *in what order* — the things a reader needs before
§1 and that, until now, lived in a session survey (2026-09-10) and a benchmark
(`docs/benchmarks.md`, 2026-09-11, cited below as **B§n**). It was drafted as a separate
`OBJECTIVES.md` and folded in the same day: two files addressing one product need a precedence
rule between them, and a precedence rule is a drift rule in disguise. Inside one file the rule is
ordinary — **a later section defers to §0**; where one disagrees, it is amended and tagged
**Corrected** with a pointer here. The numbering starts at 0 so that nothing else moved.

Reasoning lives once, at the pointer. Nothing is restated here that a `B§` or a `§` can carry.

**Confidence tags used in this section:** **[Verified]** — checked against the tree on
2026-09-11; **[Assumed]** — asserted from product knowledge, to be confirmed before anything is
built on it.

### 0.1 Hard constraints

Non-negotiable. A proposal that needs one relaxed is out of scope by definition (§0.7), not an
open item (§0.10).

| # | Constraint | Source |
|---|---|---|
| 0.1.1 | **Personal use only.** Not distributed, not sold, no store. One person's data on that person's devices. | survey B5 |
| 0.1.2 | **Offline-first.** Every feature works with no network. No network call unless the person explicitly enabled one (0.1.6; Google Calendar, §9.5). | survey D9; §3.5 |
| 0.1.3 | **No telemetry.** Nothing leaves the device that the person did not put there. | survey D9 |
| 0.1.4 | **No first-party sync.** Replication is an external Syncthing-fork over a folder of snapshot files this app reads and writes; the app never runs a server, an account or a relay. | survey D10; §9.4 |
| 0.1.5 | **No collaboration.** No presence, no comments-as-conversation, no public sharing, no web clipper, no projects-as-teams. A different product. | survey A3; scope C1 |
| 0.1.6 | **AI is opt-in, with the person's own key**, or a local model. Off, the app is whole. | survey A3; scope C4 |
| 0.1.7 | **Two platforms: Android (primary) and Windows desktop.** No others. True parity is the goal; desktop waits for the shape to settle (§0.8). | survey C7, C8 |
| 0.1.8 | **Unrooted, Storage Access Framework, no Play distribution.** | §1 |

### 0.2 Purpose, goals, non-goals

**Purpose.** One app for a single person's pages, databases, calendar, tasks, habits and the map
between them — built to **improve on Notion's features for one person**, not to integrate a
subset of them. Notion was the starting point, not the ceiling (survey A2).

**Goals**

1. **Exceed, including on the data model** (survey A4). The concrete meaning is §0.6.8: a schema
   can be attached to a label, so any page anywhere can be a row of its kind.
2. **A base as simple as possible on a structure as scalable as possible** (survey E11). One
   person tracks groceries and a company roadmap without the app feeling like two products (§1).
3. **Every power feature present, none obligatory** — progressive disclosure (survey E12; §0.5.1).
4. **A human layer.** Habits are not a tracker (§0.5.2).
5. **Durable data.** Everything the app stores can leave it in a form something else reads:
   Markdown (§7 in reverse), JSON Canvas, `.tendril` (§9.4.1), ICS. The app is not a hostage-taker.
6. **True parity between the two platforms**, reached by sharing code, not by porting twice.

**Non-goals** — not "later", but *not this app*: anything in §0.1; a plain-files vault
(Markdown is an export format, not the storage format — §3.1.1's span model stands, B§1.2);
gamified upkeep (§0.5.2); inferred relationships between pages (§3.4).

### 0.3 Blast radius of the 2026-09-11 pass

What changes against the tree on that date; **[Verified]** where B§ says so.

| | |
|---|---|
| **New** | Time on tasks — estimate, planning, tracking (§0.6.5, §0.8); a completion log and a presence view for habits (§0.6.6); a second task date, sub-tasks, an opt-in importance flag, Postpone (§0.6.4); schema on a label (§0.6.8); an outline mind map (§0.6.2); a live canvas block (§0.6.3); natural-language entry, calendar layers, an agenda, ICS (§0.8). |
| **Changed** | Nesting depth unlimited (§0.6.1); the §3.1.6 feature renamed *Label* (§0.6.9); the streak retired from the habit row (§0.6.6); §3.2's "deadline" wording corrected to *When* (§0.6.4); the Canvas UI moves to `shared/` (§0.6.10). |
| **Untouched** | The block/span model (§3.1.1); databases, views, bindings, computed properties (§5); snapshot sync, merge, encryption (§9.4); Notion import (§7); Canvas as a page kind (§3.7); the five nav destinations (§1). |

### 0.4 The bar

Per surface, the frame is the full-featured *paid* option where one exists; its reach is the
ceiling scores are measured against. Scores are **[Assumed]**; the gaps are **[Verified]**
against the tree. Full tables: B§1–4, B§8, B§10.

| Surface | Frame | What it has that Tendril lacks, in one line |
|---|---|---|
| Pages | Notion Plus; data model: Tana / Anytype | Linked views in a page, sub-pages from inside a page, columns, history, transclusion; a schema on any page |
| Calendar | Fantastical Premium | Natural-language entry, an agenda, drag-to-move, an edit path at all (§3.2 as corrected 2026-09-06) |
| Tasks & Habits | TickTick Premium; restraint: Things 3 | Sub-tasks, a second date, an estimate; for habits, any memory beyond a streak |
| Road Map | Obsidian's graph | Filters, colour by label, local depth, typed edges |
| The human layer | **Tiimo** | A visual day, a focus mode showing one thing, notifications that offer rather than demand |

Six ideas score high on every surface they touch (B§5): **time** (estimate → plan → track →
compare), **a schema on a label**, **natural-language entry**, **a task with When, Deadline and
Someday**, **habit presence**, **graph filters**. §0.6 and §0.8 are those six, in dependency order.

### 0.5 Principles

Each is a rule a future decision is checked against, with its home.

- **0.5.1 Progressive disclosure.** A person who never opens a power feature has today's app. A
  database gets a label only when asked; importance is a flag hidden until enabled; the streak is
  a number behind a disclosure. Amazing Marvin is the reference (B§10.2).
- **0.5.2 The human layer.** Habits exist to keep the things that are *not* work — rest, care,
  practice — visible as part of a life, for a neurodiverse person as much as anyone. **Show
  presence, never absence**: no misses, no gaps, no red, no chain, no percentage. Offer, don't
  demand. The rule leaks, deliberately, into Tasks: a *When* is a plan that moves without guilt;
  a *Deadline* is rare and real; there is no priority scale — *urgency* is (B§13.8.1, 14g·3):
  the ladder reads the deadline the person set and the level they chose, and ranks nothing for
  them. Home: B§10.
- **0.5.3 What is indexed is what is stored.** No second copy of content. The mind map is a view
  of blocks; the export is a rendering; a schema's values live on the page. Home: §3.1.1, B§9.4.
- **0.5.4 A page keeps its own home.** Membership in a database is shown, not contained.
  Deleting a database never deletes a page that lived elsewhere. Home: B§12.5.
- **0.5.5 Arm to interact.** A pannable surface inside a scrolling page is inert until tapped,
  and grows in place when armed. Explicit modes over guessed gestures. Home: B§9.6.
- **0.5.6 Absence never implies deletion.** In sync, in merge, and now in schema values on
  untag. Home: §9.4.
- **0.5.7 Explicit over inferred.** Edges are drawn or written, never scored from similarity.
  Home: §3.4.
- **0.5.8 Shared code is the parity mechanism.** A feature reaches desktop by living in
  `shared/`, not by a second implementation. Home: §12, §0.6.10.

### 0.6 Decisions of 2026-09-11

Verdicts, each with **Finding / Decision / Acceptance** where something will be built against it.
Reasoning lives at the pointer.

**0.6.1 Nesting depth — unlimited.** Finding **[Verified]**: `Block.parentBlockId` is unbounded;
`indentTargetFor` alone enforces one level. Decision: lift it; §3.1.1's "nestable one level" is
**Corrected** by this row. Acceptance: a list nests to any depth; export renders the depth; FTS
content unchanged. (B§9.5)
**Done 2026-09-11.** `outlineOf` is a depth-first walk; `indentTargetFor` is the outliner rule
(under the previous sibling, at any depth) and `outdentPlanFor` its inverse (one level up, later
siblings adopted, so the page keeps its reading order). The Markdown writer follows the outline —
a collapsed toggle's subtree is written too — and the Notion parser stops clamping at one level.
Rendering past six levels steps by 8dp instead of 24dp, which resolves §0.10 item 8. Verified on
desktop and phone: Packing › Clothes › Jackets at three depths, "Indent" offered on an already
indented block.

**0.6.2 The in-page mind map is a rendering of a nested list.** Finding: an outline and a mind
map are the same data (Xmind, markmap). Decision: no mind-map entity; a subtree drawn as a tree,
inert inline until tapped, then armed and grown in place to edit; a page may hold several. §10's
deferred "in-page mind-map block" is resolved by this row. Acceptance: creating, editing and
deleting a node is creating, editing and deleting a block; the map has no table of its own; the
Markdown export shows the list. (B§9.4, B§9.6)
*(Amended 2026-09-20 — the mind-map pass: the map is drawn by **the canvas's grammar** — the root at `pageTitle` in a bordered box, its children the strip, deeper nodes text on the line, headless coloured branches (`ui/canvas/TreeGrammar.kt`); its structure is Right, its tree the outline's; §3.7.)*
**Done 2026-09-11.** `layoutMindMap` (a left-to-right tidy tree over the outline) plus one stored
bit, `Block.mindMap` (v12), a view preference like `toggleExpanded`. Inert: a card in the block
list where the rows would have been. Armed: the same drawing filling the viewport with pan and
zoom on one `graphicsLayer`; a node tap selects; edit, add child and delete are the ordinary
block edits; Back closes it through Compose Multiplatform's own `BackHandler` (a new dependency).
Verified on desktop (card → armed → "Passport" added under Documents → rows show it at depth 2)
and on the phone, where the **system back gesture closed the armed map and stayed on the page**.
One gap, §0.10: Escape does not close it on desktop; the X does.

**0.6.3 The canvas block is the live board.** Finding: Canvas is a shipped page kind with an
unbounded content space (§3.7). Decision: a block that embeds a Canvas page, inert until armed,
grown in place; no second canvas model — §3.7's "a page kind, not a block type" stands, because
the block *embeds* a page. Acceptance: the block points at a `CANVAS` page by id; arming captures
pan/zoom/drag; Back disarms; the page list still scrolls when inert. (B§9.3, B§9.6)
**Done 2026-09-11.** `BlockType.CANVAS`, pointing at a Canvas page through `mentionedPageId`.
Inert: a card drawing the board's nodes and edges at thumbnail scale from the rows the board
reads. Armed: the whole screen's `CanvasScreen`, over the page, until its back arrow or the
system back gesture disarms it. Inserted from the slash menu through a picker — an existing
canvas, or a new one created as a child of the page. Exported as a labelled link to the canvas
page's file. Verified on the phone end to end: created "Route ideas" from the picker, armed it,
added a text card, backed out, the inert card drew the node. §0.10 item 2 is resolved: the inert
card draws live from the rows, not from a cached image — at thumbnail scale that is cheaper than
keeping a bitmap current.

**0.6.4 A task has a When and an optional Deadline.** Finding **[Verified]**: `Entry.startDate`
is a task's only date and is both where Calendar draws it and what §5.2 binds as "deadline".
Decision: `startDate` stays the *When*; add optional `dueDate`; add `parentEntryId`
(checklist-style sub-tasks), a hidden `estimate`, a single opt-in *important* flag, and a
**Postpone** control that moves a date forward by minutes / hours / days / months. "Someday" is
the undated task, named. Labels on entries after §0.6.8. Acceptance: an existing task gains no
deadline by migration; §5.2's `deadlinePropertyId` is renamed to a *date* binding and a second,
optional deadline binding exists. Open: which date Postpone moves by default (§0.10). (B§11)
**Done 2026-09-11, same day, with one item narrowed and stated.** All five fields are stored
(v10), travel, and read: the Tasks list shows the deadline as "due <date>" in the same colour as
everything else, steps under their parent with a "done/total" count, and the *important* star
only while Settings says so; the row's `···` offers Postpone, Add a step, Set/Change deadline,
Important (when shown) and Delete; the add dialog takes an optional deadline. "Someday" is the
undated section's name. Postpone moves the *When* — §0.10 item 1 is resolved that way. **Narrowed:**
the §5.2 binding keeps its storage name `deadlinePropertyId`, because a rename would make a v9
peer's snapshot mean the wrong thing; its UI label is corrected to "Date (when)". The *second*
binding, for `dueDate`, is **not built here** — it touches the database views on eleven sites and
is its own row in §0.8. `estimate` is stored and read by nothing yet, by this row's own design;
`tools/audit.py` carries it in its baseline with that citation.
**[Amended] 2026-09-16 (14g·3, B§13.8.1 — the urgency ladder; decided on
`docs/critiques/urgency-ladder-mock.md`, walked in `urgency-ladder-function.md`).** The opt-in
flag becomes the ladder's *set* half: **`Entry.importance` 0–4** (none · low · mid · high ·
urgent), **schema v20** — `entries` rebuilt (Android's platform SQLite predates `DROP COLUMN`),
ids kept, `important = 1 → 3`. The level a row shows is **the greater of the set level and the
Deadline's pressure today** (`domain/urgency/Urgency.kt`: within 7 days low, 3 mid, today or
tomorrow high, past urgent; the When never counts; a done task has none), derived at read time,
never stored. Drawn as a **4 dp stripe** on task rows, timed calendar blocks and all-day chips and
as a **disc** in the picker, the pane's chip and the row menu's *Urgency ▸* — shapes, never a glyph
(the mock's star measured 3.05:1 as text); *none* draws nothing. The row's *due* date keeps the
row's one colour after it passes — the stripe already says urgent, and two reds for one fact would
break B§13.8.1's own rule; the Table's *Blocked* chip and the Timeline's dependency line keep the
error family. **On by default** behind *Show urgency on tasks*, one of two Tasks switches now in
`KeyValueStore` on both platforms (`show_urgency`, `show_habit_streaks`; the phone's
`tendril_task_prefs` migrated once — an explicit *show importance* carried, an old *off* not, since
it was the flag's default; the desktop pane gained the section). Quick add: `!` high, `!!` or
more urgent, the word *important* high. The snapshot carries `importance` **and** the old
`important` (written as `importance >= 3`) so a v19 peer keeps reading the flag; a v19 record's
flag lands on high. ICS `PRIORITY` 1 / 3 / 5 / 7 out and mapped back in. The ladder's colours:
§2.3 *The token map*.
**The narrowed item closed the same day (step 2b, schema v11).** `BindingRole.DUE_DATE` binds a
`DATE` property to `Entry.dueDate` beside the one that binds the When: `PageDatabase.dueDatePropertyId`,
`dueDatePropertyUid` in the snapshot with a default so a v10 peer's record reads unchanged, a fourth
branch in every `when` — seeding on enable, editing through the cell, freezing on unbind — and a
second picker on the enable-sync sheet (one property cannot fill both date roles). Verified on the
phone: a to-do database's new `Deadline` column bound, a row's bound cell set, the linked task
showing "due 2026-09-11" under Someday. That run also found and closed a gap older than this
row — see §5.2.1's correction of the same date.

**0.6.5 Time is a first-class concern.** Decision in principle: estimate → plan → track →
compare, in that order (§0.8). Shape: Tiimo's visible day and Llama Life's "now", not a workload
chart. *(Written before step 7: nothing was built yet, and this was recorded so the estimate field
(0.6.4) would not be designed without its consumers. All four halves are now done — see below.)*
(B§5, B§10.3)
**Plan — done 2026-09-12 (step 7b).** The estimate has its first consumer. A *Plan* chip on the
Day view turns the list into a timeline: hours down the side, a block per timed thing sized by
its span (events) or its estimate (tasks; thirty minutes when there is none, drawn dashed so the
guess reads as one), overlaps side by side, the current minute as a line, untimed items in a strip
above and a rail of *unplanned* tasks — today's untimed ones and Someday — below. Placing is the
person's: a rail task long-pressed and dragged onto the grid lands at the drop, snapped to a
quarter hour; a block dragged up or down moves the same way, a series asking *this one / all*.
**No automatic placement** (B§6 #8, drag-only first): the day is arranged by hand, not by an
algorithm the person then argues with (§0.5.2). `domain/plan/DayTimeline` is the layout, pure and
tested; `EntryEditor.move` gained "a time places an untimed entry". Track and compare follow.
**Track — done 2026-09-12 (step 7c).** A ▶ on every task and habit row (Tasks & Habits, the
Calendar's Day view); Llama Life's rule, **one thing runs at a time** — starting another stops
the first, so there is no "already running" state to explain. What runs shows as one line above
the tabs on every route, on both platforms: the title, the elapsed time, a ■. The habit detail
gains "N min logged this month", silent at zero like the rest of its sentences. **The row is the
timer**: `TimeLog` (v14; `entryId`/`habitId`, one of the two, `startedAt`, `endedAt` null while
it runs, a tombstone, `updatedAt`) is inserted open and closed on stop; nothing in memory
remembers a timer, so process death cannot lose one. That answers B§6 #9's open question with
less than it asked for — on the phone a plain ongoing notification with a chronometer, which
SystemUI ticks with no process of ours alive, and a *Stop* action that is a broadcast writing the
missing `endedAt`. No foreground service, no `FOREGROUND_SERVICE_*` permission. Verified: the
process killed, the shade still counting at 02:21, *Stop* from the shade closed the row (162 s)
and cleared it. `time_logs.json` travels with the one merge rule this table needs and no other
has — both edited and tombstoned, so "deleted on any device wins", else the later `updatedAt`.
Compare (7d) follows on `loggedMinutes`.
**Compare — done 2026-09-12 (step 7d). §0.6.5 complete.** The Day view's header gains one
line, *Planned 2h 15m · Logged 45m* — planned is every timeline block plus the estimates of the
day's untimed tasks; logged is the day's logs, an open one counted to now, ticking by the minute.
A row with time on it says *20m of ~45m* beside an estimate and *20m logged* without; a habit
row says *12m today*. Plan mode draws the logged stretches as a strip along the hour gutter — no border, no
text, nothing to tap — so the person sees where the time went against where it was meant to go.
(A wash *under* the blocks was tried first and was invisible: a block's container is opaque.) The habit detail adds *About 12m each*, the mean of its closed
sessions once there are two. **No score, no over/under colour, no percentage**: the two numbers
sit next to each other and the person draws the conclusion (§0.5.2). `domain/plan/DayTotals`
is pure and tested; every surface reads the same functions.

**0.6.6 Habits keep a completion log and show presence.** Finding **[Verified]**: `Habit` holds
only `streak`, `previousStreak`, `lastCompletedDate`. Decision: add a completion log; the streak
leaves the row and becomes an opt-in derived number; the habit detail shows "four times this
month", "usually mornings", "last: Tuesday" and never a miss. §3.3's "streak-based" is
**Corrected** by this row to "log-based, presence shown". Acceptance: no screen shows a gap, a
percentage or a broken chain by default. (B§10.3)
**Done 2026-09-11, same day.** `habit_completions` (v10), written by the one check-in funnel,
backfilled from the two dates a habit used to keep, tombstoned on undo so that "deleted on any
device wins" merges it. The habit row no longer shows a streak unless Settings says so; tapping a
habit opens a presence sheet — "four times this month", "usually mornings", "last: Tuesday" — and
a month of dots where a day without a check-in is empty space. Whether habits become measurable
stays §0.10 item 3. **The migration was verified on a real database, on desktop** — no phone was
attached: a v9 `tendril.db` holding this morning's canvas was seeded with two habits (one with
both dates, one with none) and a task, and the v10 build opened it through `MIGRATION_9_10`:
`user_version` 10, the four columns present, the old task with no deadline and `important = 0`,
two backfilled `habit_completions` rows for the dated habit and none for the other, all three
indexes, the canvas's two nodes and one edge untouched, `integrity_check` ok, no foreign-key
violations, no exception in the log. **And then on the phone, the same afternoon.** The app had
been uninstalled after #33, so there was no v9 database to migrate; one was made: the v9 build
(`main` at `a7d69e6`) installed, a task added and a habit created and checked in through the
UI, the database pulled (`user_version` 9, no `habit_completions`), then the v10 build installed
over it. After: `user_version` 10, the four columns, the task with no deadline and `important =
0`, the habit kept, **one backfilled check-in dated today**, all three indexes, `integrity_check`
ok, no foreign-key violations, no Room exception in logcat. The UI on top of it: the row's `···`
shows Postpone / Add a step / Set deadline / Delete and *no* Important while the switch is off;
+1 day moved the task from the 11th to the 12th and out of "Today"; a deadline set to the 11th
stayed there; a step appeared under its parent as "0/1 steps"; the habit row read "Every 1
day(s)" with no streak, and tapping it opened "Once this month · Last: venerdì, 11 set" over a
month of dots; flipping both Settings switches put "streak 1" back on the row and "Important"
into the menu, and marking the task drew the star.

*(**Amended 2026-09-19 — §0.10 item 3, measurable habits.** A habit may **count something**: a unit as the person writes it after a number (*cups*, *km*, *pages* — never pluralised by the app), the amount one tap adds, and, if they like, a number for a day. Ground: TickTick's amount habit measured on the phone — its unit, per-check-in amount and repeatable tap are taken; its *'1 / 3 Tazza'* on the row, its progress bar and its monthly rate are not. The value lives on the log entry (`habit_completions.value`); the row's checkbox becomes a `+` disc in the same slot (28 dp under a pointer, 48 on the phone) and every tap logs one completion carrying the amount — a tap is only ever *more*; a manual amount sits collapsed behind the sheet's *Log an amount…*; *Undo the last one* tombstones the last completion. The day counts once for the streak and `lastCompletedDate`, however many cups. Presence gains the amounts — *2 cups today*, *41 cups this month, on 12 days* — sums of things done; the number the person set is one sentence in the dim colour, last: *3 cups a day is what you set*. No fraction, no bar, no rate, no colour by size on the month of dots: the rule stands. Schema v21, four nullable columns; a plain habit's rows carry null and nothing changes for it.)*

**0.6.7 Canvas grows additively.** Colours, groups, image nodes, nested boards as a
`PAGE_EMBED` of a Canvas page, a mind-map layout mode sharing 0.6.2's layout code, JSON Canvas
export. (B§9.2, B§9.5) *Groups built 2026-09-18 as frames (§0.10 item 15), JSON Canvas export 2026-09-19 (item 6); nested boards were the page-embed already (step 8c).*

**0.6.8 A schema on a label — shape A, opt-in.** Finding **[Verified]**: `PropertyValue` is
keyed by page, not by membership; a row is a page with a single `databaseId`. Decision: a database
may *bind a label*; a page carrying it is a full row in that database's views and gains the
database's fields in its header, while keeping its own home (§0.5.4). Untag hides the values;
they purge with the database's own trash (§5.5.1.1). The first application of a label bound to a
to-do database asks once. Acceptance: a plain page under any parent can be labelled into a
database, edited in its table, unlabelled and relabelled without loss; deleting the database
leaves the page where it was. (B§12)
**Done 2026-09-12.** Two columns on `page_databases` (**schema v13**): `labelId`, the doorway, and
`labelConfirmed`, the once-only answer. Membership is one query — native rows ∪ pages carrying the
label (`PageDao.getMembersOf`) — read by the views, by every §5.2.1 binding operation and by the
relation picker; nothing about membership is stored twice. `LabelMembership` (domain) is the one
place a label change becomes a Task change: applying a label whose database syncs makes the page a
task; removing it sends the task to Trash unless another membership keeps it; relabelling restores
the *same* task, so Done state survives; a page in two syncing databases is one task. Two decisions
taken while building: **native rows are not auto-labelled** (the union already lists them, and
labelling every row would touch every row's `updatedAt` — an authorship claim under §9.4 for
nothing), and the bound label **syncs by name**, as a page's labels already do. Sync: cell values
now travel for every page, and a page whose column nothing in the batch defines is held rather than
merged with its values dropped — a rule that applied only to rows before. UI: "Bind a label…" on the
database's `···`; one property strip per membership in the page header, titled when the membership
is through a label; the bound label's chip carries a small table mark; a labelled member's row menu
offers "Remove label from this page" where a native row's offers "Delete row"; the once-only
dialog. Verified on desktop end to end (bind → label → row in the table, Author typed on the page
read in the table → removed from the table, value still stored → relabelled, value back → Sync to
Tasks on → a new page labelled, the dialog once, a task → label removed, task in Trash with its
Done → relabelled, no dialog, the same task back); the same on the phone (Errands, which syncs:
bind `errand` → label *Trip* → the dialog → a row in Errands' table with "Remove label from this
page" where the native row has "Delete row" → removed, task in Trash → relabelled, no dialog, the
same task back), where the LazyColumn's anchor had hidden the new strip until the list is scrolled
to the top on a membership gained; the v12 → v13 upgrade on the desktop and the phone databases in
place, `integrity_check` ok. 596 tests.
**0.6.9 The §3.1.6 feature is called *Label*.** So that *tag* keeps its Notion meaning — a Select
property inside one database, which this app also has (§4). §3.1.6 is **Corrected** by this row
in name only; the code's `Tag`/`PageTag` rename is separate and mechanical. (B§12.5)
**Done 2026-09-12.** `Label`, `PageLabel`, `LabelDao`, `LabelColors`; the view-model and screen
names follow (`addLabel`, `allLabels`, …); the three UI strings read "label". On disk nothing moved:
`tags`, `page_tags.tagId`, and the snapshot key `tags` (now `@SerialName`) are the same bytes a v12
peer writes, so the rename is invisible to the folder. §3.1.6 corrected in place.

**0.6.10 The Canvas UI moves to `shared/` first.** Finding **[Verified]**: 821 lines in
`Tendril android/…/ui/canvas/`, none in `shared/`. Decision: the move precedes every spatial
feature. Acceptance: `CanvasScreen` compiles for both targets; desktop opens a canvas. (B§9.5)
**Done 2026-09-11, same day** — both files moved to `shared/src/commonMain/.../ui/canvas/`, the
screen takes `WorkbenchCore` where it took `AppContainer`, the scaffold's `canvasContent` slot is
gone. Verified on the running desktop preview: a canvas created from the New sheet opens, a text
card is added, a mouse drag moves it, a link drag draws an edge.

**0.6.11 Review is a weekly walk, not a report.** Finding: nothing in the app revisits what has
gone quiet — a database nobody has opened in weeks, a task parked in Someday, a task whose When
passed without a word. OmniFocus's review mode does exactly that on a cadence; Sunsama ends the
week with a few numbers. Decision (2026-09-12): a **Review** screen off Tasks (an icon in its
top bar, both platforms), one card at a time in a fixed order — databases due for review (never
reviewed first, then longest ago), then Someday tasks, then tasks whose When is more than a week
past — each with the few answers a review needs: *Open / Reviewed* for a database, *Keep / Today
/ Someday / Done / Trash* for a task, *Skip* everywhere. Above the cards, last week as three
numbers: logged time, tasks done, habit check-ins. `page_databases.lastReviewedAt` (**schema
v15**) travels in the page record; marking reviewed touches the page so the LWW merge carries it.
**Cadence: one global week, fixed for now** — a setting waits for the preference store (§0.10
item 12); per-database is one column later if a week ever fits nothing. **Tone (§0.5.2)**: the
only trace outside the screen is a dot on the icon when there is something to walk through —
no count, no colour, no "overdue" anywhere else in the app; a review offers a thing again, it
does not say it was missed. Acceptance: with nothing due the screen says so; a database
reviewed today does not return for seven days on either device; steps and series overrides
never appear as cards. (B§6 #10) **Done 2026-09-12** — `domain/review/ReviewPlanner` (pure,
tested), `Review` over DAOs that already existed, `ui/review/ReviewScreen`, the
`WorkbenchRoute.Review` route. Verified on desktop and phone.

**0.6.12 A block reference is a block, and a plain-text title is one tap from a link.** Finding
**[Verified]**: the block editor is a `BasicTextField` per block, so an inline span is not
tappable — today's inline `@mention` spans never navigate; only the standalone `PAGE_MENTION`
*block* does. Logseq's `((uid))` transclusion is the benchmark (B§6 #13), and its inline form
would have to copy the source's text into the field and re-copy it on every source edit.
Decision (2026-09-13): **`BlockType.BLOCK_REFERENCE`** — an inert card of the source block's
*live* text behind an accent bar, its page's title under it, a tap opening that page; `content`
caches the words at insertion so the Markdown export and a device without the source still have
them, and each open of the page refreshes that cache from the source **without moving any
timestamp** (derived, not authored — a bumped `updatedAt` would make every open look like an edit
to the page-level LWW). `blocks.referencedBlockUid` (**schema v17**) names the block by its uid —
the one identity a block keeps across the sync's delete-and-reinsert and across devices — so the
snapshot carries it verbatim and **no quarantine clause** is needed: a source not here yet costs
only the card showing its cache; `mentionedPageId` names the source *page*, as a `PAGE_MENTION`'s
does, and is a page reference like any other. Inserted by typing `((` at the end of a block
(Logseq's convention, beside `/` and `@`) or from the slash sheet, through a picker that searches
the words (`BlockDao.searchContent`, a substring scan — the page-level FTS cannot say *which*
block matched). A reference to a reference is refused. Edit the words at their source: the card
is not an editor. **Unlinked mentions**: §3.1.5's v1 exclusion lifted — the same substring scan
finds other live pages whose text contains this page's title (three characters or more, never a
Journal day's), minus pages that already link and occurrences already under a mention span;
each row has Obsidian's **Link**, which adds a `PageMention` span over the words on the *other*
page (that page is what changed: re-indexed and touched). A block reference counts as a link to
its source page — it appears under Linked mentions and as a Road Map edge — which is what makes
Linked and Unlinked disjoint. Acceptance: a source edited on its own page shows the new words on
every card at the next open of the referencing page, on both devices, with the referencing
page's `updatedAt` unchanged; a record whose source page is missing merges by uid; Link moves the
row from Unlinked to Linked. **Done 2026-09-13** — `domain/references/UnlinkedMentions.kt` (pure,
tested), `ui/pages/BlockReference.kt`, `MIGRATION_16_17`. Verified on desktop and phone.

**0.6.13 A page keeps its own history; the sync's loser is kept too.** Finding: nothing in the
app remembers what a page said yesterday, and §9.4's accepted limitation — concurrent edits to
one page before either syncs lose one side by LWW — loses it silently. Notion keeps page history;
the benchmark (B§6 #12) asked what a revision holds, how often, and whether it syncs. Decision
(2026-09-13): **a revision is the title and the body's blocks** — as the sync's own
`BlockSnapshotRecord` JSON, so a restore rebuilds through the merge's proven block rebuild
(`sync/BlockSnapshots.kt`, `replaceBlocks`, extracted from Pass 5 for exactly that). Not labels,
property values, a database's schema or a canvas's board: each has its own merge rule, none is
where "I lost a paragraph" lives, and the full-record alternative is a second merge path
(database-level history is §0.10 item 16 if it is ever missed). **Three captures, three rules:**
*before an edit*, the body as it stood when an editing window opened — at most one per ten
minutes per page, serialised so a burst of keystrokes is one capture, not one each; *before a
merge*, the local body a winning remote record is about to replace — unthrottled but skipped when
it equals the last kept body, so an unchanged page produces nothing and §9.4's loser is what
lands in History; *before a restore*, always, so a restore is undoable. Fifty per page.
`page_revisions` (**schema v18**) **stays home**: not in the sync folder, not in the `.tendril`
archive — history is this device's memory of this device's edits and of what a sync overwrote
here; the page row is what travels. `···` → **History** on an ordinary page (a Database's or a
Canvas's body is not its content): rows "12 min ago · before an edit · 7 blocks", a read-only
preview, *Restore* with a confirm; a restore is an edit like any other — locked, re-indexed,
touched — and keeps block uids, so a block reference to a restored block still resolves.
Acceptance: ten keystrokes make one revision; a page merged over with a local change shows a
"replaced by a sync" row holding the local words; a restore brings back a nested tree with its
children relinked and leaves a "before a restore" row of what it replaced. **Done 2026-09-13** —
`domain/history/PageHistory.kt` (tested), `ui/pages/HistorySheet.kt`, `MIGRATION_17_18`.
Verified on desktop and phone.

**0.6.14 A Timeline is the fifth view, and "blocked by" is a bound relation column.** Finding
**[Verified]**: a database's rows have dates but no view that shows *duration* or *sequence*;
`PropertyType.RELATION` exists (§5.4) and its own doc names "Blocked by" as the example column;
`PageRelation` (§3.4's manual edges) is page-level and undirected; a standalone Entry is not a
page at all. Decision (2026-09-13, B§6 #17): **`ViewType.TIMELINE`** — a day per column at one
scale, scrolling sideways and opening on today, a lane per row with a bar from a start Date
property (the Calendar view's `datePropertyId`, reused) to an optional end one
(`endDatePropertyId`, **schema v19**; one day when absent). **Drag a bar and it moves by whole
days through the one date write every view makes** (`PageDatabaseViewModel.setDateCell`: a
bound When/Deadline on a synced row goes through the Entry with its alarms re-armed, anything
else is a stored cell) — so dragging a synced row's bar *is* moving its task. Undated rows are
listed under the grid, the Calendar view's precedent — *and, since B§13.6 #5 (2026-09-16),
dragged from that list onto a day column, which is `setDateCell` for the start property; the
column lights, the ghost names it (*Read on → 22 Sep*)*. **The bar envelops its title** (the
user, 2026-09-16, three notes the same hour — Notion's rule): a bar is as wide as its days or as
wide as its title with 16 dp of air, whichever is more, so a one-day row is never an ellipsis and
its label never sits on the ground looking like the next day's; a 2 dp line in the ink along the
bar's foot marks the true span. **"Blocked by"** is a database-level
pointer, `blockedByPropertyId`, naming one of its own RELATION columns that points back at the
database — chosen in `···` → *Blocked by…*. A pointer, not a `BindingRole`: `bindProperty`
crystallises and proxies through an Entry, and a relation's values must stay stored. A row with
an **open** blocker shows *Blocked* on every view and the Timeline draws a line from the
blocker's bar into it; open means not Done through the Done binding, and **without a Done
binding every blocker counts** — a database that cannot say "done" cannot say "unblocked".
**Not** a kind on `PageRelation` (undirected, page-level, a second table to read per row) and
**not** on standalone tasks (the Tasks tab has steps via `parentEntryId`, §0.6.4; dependencies
live where projects live, on rows). Acceptance: a two-day drag changes the cell by two days and,
on a synced row, the task's date in Tasks; ticking a blocker Done removes the chip; a view
whose start column is deleted falls back to the configure prompt. **Done 2026-09-13** —
`domain/timeline/Timeline.kt` (pure, tested), `ui/pages/TimelineView.kt`, `MIGRATION_18_19`.
Verified on desktop and phone.

**0.6.15 Three verbs on a selection — four since 2026-09-22 — with the person's own key, and nothing else.** Finding
**[Verified]**: §3.5's key field has stored an Anthropic key in the Keystore since 2026-07 with
nothing using it; the app already speaks HTTP through `HttpURLConnection` (§9.5) and the build is
offline, so no library can be added; §0.1/§0.2 stand — offline-first, no telemetry, opt-in.
Decision (2026-09-13, B§6 #18, C4): the selection toolbar gains **Rewrite · Expand · Summarise**,
present **only while a key is set**. A press builds one client, sends **only the selected text
and the verb's fixed instruction** — never the page title, other blocks, or anything about the
person — to the Messages API with the key as a header, and drops the client with the reply. The
answer opens a **result sheet**: *Replace selection* or *Insert below as a new block* are the only
writes, Cancel is free, and the sheet says what left the device; a failure reads as its cause
(the key rejected, rate limited, the service down, no connection). Applying is an ordinary edit
— locked, re-indexed, touched, kept in History first. **The key's home is `AiKeyStore`**: Android's
Keystore-backed `SecretStore`, the desktop's own file (`~/.tendril-desktop-dev/anthropic.key`,
owner-only where the filesystem speaks POSIX; on NTFS it inherits the profile's ACL — this
account and administrators — since the JDK's `setReadable` is a no-op there; *[Amended] 2026-09-18 — §0.10 item 20: the line is **DPAPI-wrapped** on Windows (`SecretWrap` / `SecretFileFormat` in `shared/`, `DpapiWrap` over `jna-platform`'s `Crypt32Util` in the desktop module — Electron's `safeStorage` rule, which is how Notion's and VS Code's tokens sit on Windows), so the file holds a blob only this account can open and the ACL is the second fence; a bare line from before is rewritten wrapped on first read; off Windows the bare line stays under the POSIX permissions*); **never the `KeyValueStore`**, which holds
only the model (`ai_model`, default `claude-sonnet-5`; Opus 5 and Haiku 4.5 offered). A shared
Settings section (key, model, "what is sent") replaces Android's private one, and the desktop's
first Settings pane holds it — no `NotAvailableOnDesktop` stand-in remains. **Out, on purpose:**
page-wide or vault-wide context in a request, an agent over the Markdown export, a local-model
option (§0.10 item 17 — the export is the honest interface for an agent, and nothing here
precludes one); ~~the Claude-generated mind map stays deferred (§10)~~. Acceptance: with no key the
toolbar is unchanged and no connection is ever opened; a wrong key yields a real 401 and the
sheet's "rejected" line; the request body never contains the key. **Done 2026-09-13** —
`domain/ai/AiVerbs.kt` (pure, tested) and `ClaudeClient.kt`, `ui/pages/AiResultSheet.kt`,
`ui/settings/AiSettingsSection.kt`, `data/prefs/AiKeyStore.kt` (+ `FileAiKeyStore`,
`AndroidAiKeyStore`). Verified on desktop and phone against the real endpoint (the failure path,
by design — a dummy key, a genuine 401). **§0.8 step 8 complete.**

*[Amended] 2026-09-22 — the fourth verb, **Mind map** (§10's Claude-generated map, the reason
the Road Map was renamed; decided in one batch with the rest of the deferred list, the mock
`docs/mockups/ai-mind-map.html` critiqued in `docs/critiques/ai-mind-map-mock.md`).* The same
rule, the same path: **the selection and one instruction** go out (*a nested Markdown list, one
root, two spaces a level, nothing else*), and the reply is **parsed, not trusted** —
`domain/ai/OutlineReply.kt`'s `parseOutline` reads `-` / `*` / `•` / numbered markers, tabs or any
consistent space step, drops a preamble, trims a trailing colon, and clamps a depth jump to its
parent + 1, so the rows are always a tree with one root. The sheet previews them **as the rows
they will become** (the tree's 18 dp indent, the root at `heading`, one line each ellipsised; the
accent nowhere — it is reserved) and offers **Insert as a mind map** — `insertOutline` writes
bulleted blocks after the selection's block at its depth with §0.6.2's `mindMap` flag on the root,
so the page draws the map at once — or **Insert as a list**, the same rows without the flag. No
*Replace selection*: a nested list cannot live in one field. A reply with no list is a failure
with *Try again*; under Touch the sheet's dismiss is the cancel (no third button). One recorded
edit — one undo removes the subtree (§0.10 item 19's stack). Acceptance: the request body carries
the selection alone (`AiVerbsTest`); `OutlineReplyTest` (6 cases) pins the parse; a real reply
inserted on both devices and drawn by the existing map.

### 0.7 Explicitly out of scope

Ruled out on purpose. Not to be reopened without amending §0.1 or §0.2. The evidence for each
row is in `docs/scope-decisions.md`; this is the short list.

- Everything §0.1 excludes: distribution, servers, accounts, presence, sharing, clipping,
  telemetry.
- A mind-map entity of its own (B§9.4, M3) and an always-live canvas inside a scrolling page
  (B§9.3, O2) — 0.6.2 and 0.6.3 are the forms that survive.
- Streak chains, heat-maps of misses, scores that punish (§0.5.2).
- A priority *scale* (§0.6.4).
- Inferred edges (§0.5.7). Character-level CRDT (scope X1). Embedded P2P sync (scope C3).
  Desktop home-screen widgets (scope C5).
- Markdown as the storage format (§0.2).

### 0.8 Order of work

By dependency, then by value (B§6, B§9.5). Each row is a PR or a short chain of them; the section
of this file it touches is amended in the same pass (§0.11).

| Step | Work | Unblocks |
|---|---|---|
| 1 | **0.6.10** Canvas UI → `shared/` — *done 2026-09-11* | every spatial row on desktop |
| 2 | **0.6.4** Entry fields + Postpone; **0.6.6** habit log + presence view — *done 2026-09-11; the second §5.2 binding is its own row below* | 3, 6, 7 |
| 2b | **0.6.4**'s second binding: a database property bound to `Entry.dueDate` — *done 2026-09-11* | — |
| 3 | **0.6.1** depth, then **0.6.2** mind map, **0.6.3** canvas block — *done 2026-09-11*; **0.6.7** as time allows | — |
| 4 | **0.6.8** schema on a label; **0.6.9** rename — *done 2026-09-12* | labels on entries; linked views in a page |
| 5 | Natural-language Quick Add (B§6 #3) — a Task *or* an Event from one line — *done 2026-09-12* | pays §3.2's debt |
| 6 | Calendar: **6a** the screen → `shared/`; **6b** edit path + drag-to-move; **6c+6d** Agenda, layers incl. "Show Habits" and database dates; **6e** ICS — *all done 2026-09-12* | 7 |
| 7 | Time: **7a** Tasks & Habits → `shared/` — *done 2026-09-12*; **7b** Plan mode — *done 2026-09-12*; **7c** tracking — *done 2026-09-12*; **7d** planned-vs-actual — *done 2026-09-12*. §0.6.5 complete (B§6 #9); **7e** Review — *done 2026-09-12* (§0.6.11, B§6 #10) | — |
| 8 | The rest of B§6 by value — **8·0** KeyValueStore, **8a** switcher, **8b** Journal shows today, **8c** Road Map shared + filters, **8d** block references, **8e** page history, **8f** Timeline + blocked by, **8g** Claude verbs: *all done 2026-09-12/13* (§0.6.12–§0.6.15, §3.1.4/§3.1.7/§3.4 amendments). Schema v19. **Step 8 complete; §0.8 complete.** | — |
| ∥ | **This file's refresh**, section by section, against §0; desktop parity tracked per row | — |

Desktop **[Assumed]** *(two of five since 2026-09-12: the Calendar moved to `shared/` at step 6a, Tasks & Habits at 7a)* *(**none since — re-read 2026-09-24:** every destination is shared code and the `NotAvailableOnDesktop` placeholders are gone (`WorkbenchScaffold.kt`, `DesktopSettingsScreen.kt` KDoc); the desktop has *Export as Markdown* (§0.10 item 6); only the `.tendril` backup is still Android's)*: four of five destinations are stubs and neither export reaches it
(`tendril-windows-spec.md`); parity is tracked per row above rather than as one milestone, so it
never becomes "later".

### 0.9 Risks

- **Pressure creep.** Every task feature is one badge away from a tracker. §0.5.2 is the check; a
  reviewer asks "does this show absence?" of each new screen.
- **Two implementations.** The Canvas UI already diverged onto one platform once. §0.5.8 and step
  1 are the mitigation; a feature that lands Android-only is incomplete, not shipped.
- **Scale.** Canvas and the mind map draw every node; past a few hundred, viewport culling is a
  filter before drawing, not an architecture change (B§9.5). Images are already budgeted (#40).
- **Schema on a label meets Sync-to-Tasks.** A label bound to a to-do database turns pages into
  tasks. The once-per-label confirmation is the mitigation; the risk is a surprised person, not
  broken data.
- **The span model and transclusion.** Block references are a new span style; section embeds are
  not. If transclusion of whole sections is ever wanted, that is a real design, not a span.

### 0.10 Open items

Genuinely undecided — distinct from §0.7.

1. ~~Which date **Postpone** moves by default — the When (recommended) or the Deadline (B§11).~~ *Resolved 2026-09-11: the When, always; the deadline is changed only by editing the deadline. The sheet says so.*
2. ~~Whether the inert canvas block draws nodes live or a cached thumbnail (B§9.6).~~ *Resolved 2026-09-11: live, from the rows.*
3. ~~Whether habits become **measurable** (a value on the log entry) now or later (B§10.3).~~ *Resolved 2026-09-19 — now, on presence's terms: `habit_completions.value` and a habit's unit / amount per check-in / optional daily number (schema v21); §0.6.6's amendment; `docs/critiques/measurable-habits-mock.md`, `measurable-habits-function.md`.*
4. ~~Whether a one-tap **mood/energy check-in** joins the human layer, and when — it is not a
   habit.~~ *Resolved 2026-09-19 — built: schema v22 `check_ins`, the row on the Journal's day page, words not faces, the month as a sheet; §3.1.4's amendment; `docs/critiques/check-in-mock.md`, `check-in-function.md`.* *Mocked 2026-09-19 (`docs/mockups/check-in.html`, `docs/critiques/check-in-mock.md`). **Decided:** its home is the Journal's day page, above the Today strip — past days too, never a future one; the page's blocks are the words, so no note field. **Deferred:** the scales (mood + energy at five steps each, or mood alone) and the month view (a sheet of coloured discs by the day's latest mood, no count or average); schema v22 (`check_ins`: uid, date, at, mood, energy, deletedAt — the completions' tombstone rule) is the shape when built. Not built.*
5. ~~**Command palette / quick switcher**: reopens §3.1.7's deferral; the FTS title-not-indexed
   defect (§3.1.1) is fixed first regardless.~~ *Resolved 2026-09-12 (step 8a): both — see §3.1.7's amendment.*
6. ~~Where **JSON Canvas** files go — in the Markdown zip or beside `.tendril`.~~ *(2026-09-12: the `.ics` export answered the same question for itself — a file the person picks, never the sync folder — and JSON Canvas should follow when built.)* *Resolved 2026-09-19: **in the Markdown zip**, at the page's place in the tree — the zip is then an Obsidian vault whose canvases work; §3.7's amendment, `docs/critiques/json-canvas-function.md`. Not beside `.tendril` (that archive carries the board already), not the sync folder. Import of `.canvas` files is not built.*
7. ~~Whether the Canvas page kind and the block share one composable at two sizes exactly as the
   mind map does (recommended) or the page kind keeps its own screen.~~ *Resolved 2026-09-18 as recommended: `CanvasLayer` is the one drawing, the board interactive and the block inert at the fit — §3.7's amendment, `docs/critiques/canvas-embed-measured.md`.*
8. ~~Nesting **rendering** past a few levels on a phone width — an indentation budget, or a fold.~~ *Resolved 2026-09-11: a 24dp step for six levels, 8dp after.*
10. ~~**Escape on desktop** does not close an armed mind map or canvas; the X and Android's back gesture do. Compose Multiplatform's `BackHandler` needs a desktop back dispatcher that the window does not provide by default — a small wiring item in `Main.kt`, not a design question.~~ *Resolved 2026-09-12: the window did provide the dispatcher; nothing fed it. `Main.kt` adds one `NavigationEventInput` driven by the Escape key (see `tendril-windows-spec.md`, same date).* **[Amended] 2026-09-17:** *the runtime feeds the dispatcher itself since Compose Multiplatform 1.12 (the Escape key-down — which is why only the release ever reached the app's preview); the app's key-up input had become a second dispatch, so every Escape went back twice. The input is removed; Escape is the platform's one dispatch (`docs/critiques/canvas-cards-function.md` #1).*
9. ~~Whether this file should move out of `Tendril android/` to the repository root, now that its
   §0 is cross-platform — a mechanical move with a handful of path references to update.~~ *Resolved 2026-09-18: moved (`git mv`, history kept) — `tendril-spec.md` at the root beside `README.md`; the README's two paths, the windows `settings.gradle.kts` note and §11's layout line updated; every other reference was already the bare name.*
21. ~~**The desktop dies on a clipboard failure.** Seen 2026-09-16 (14f·2's walk): a paste into a
    text field raised `IllegalStateException: cannot open system clipboard` from Compose's
    `TextFieldSelectionManager.paste` on the AWT thread — uncaught, so the window closed.~~
    *Resolved 2026-09-16 (the tray PR): `Main.kt` installs `Thread.setDefaultUncaughtExceptionHandler`
    and a `WindowExceptionHandlerFactory` — the trace to `~/.tendril-desktop-dev/crash.log`, the
    window kept; Compose's default handler was the dialog-and-exit. Untested by a real fault.*
22. ~~**The desktop audit (2026-09-17)**~~ *Closed 2026-09-18 — L7b was the last line.* — *(**Decided 2026-09-18** — the rest of the list ships as four PRs by dependency, then L7b: **A the design layer** (D4–D8 — shipped), **B the last Material frames** (L6b, L15 + T3, T4, T5 — shipped), **C the database's views** (F4–F8 — shipped), **D small things III** (L11–L14, F9–F14); F4 a confirm dialog, not an undo; the Add task dialog a `TendrilSheet`; T5, D6, D7 and L7b all taken; F6 on Notion's ground — the property made with the view. The per-PR process: the ground measured live, a mock only where a surface is new, a measured pass before and after, one presentation at the PR.)* ***the eight High fixed 2026-09-17 (the audit's fixes PR; ***L4 fixed 2026-09-17 — the Month grid PR (`docs/critiques/month-grid-function.md`: rows = pane / 5, chips 22 px at a 24 px pitch, four a cell at the user's window, beside Notion's 124 / 28 / 34);***  D1–D3 of `docs/critiques/desktop-design-layer.md` fixed with them; `docs/critiques/desktop-audit-fixes.md` re-measures: menus 49 → 30 px, Day rows 65 → 30, Agenda 52–61 → 30, Table 50 → 34, raw roles 196 → 0, three annotated sites). The design-layer pass's D4–D8 join the Med/Low list below.*** Three full `design-critique-plus` passes over every desktop
    surface after the tray merged: `docs/critiques/desktop-layout-full.md`, `desktop-type-full.md`,
    `desktop-function-full.md` (the grab set lives in the session scratchpad, not the repo). **High,
    to the fix PR *the audit's fixes*:** L1 every `DropdownMenu` row is 48 dp under a pointer (one
    `TendrilMenu` at the list minimum); L2 the Calendar's Day and Agenda rows are the phone's two-line
    rows (the tray PR's one-line rule); L3 the Table's rows at 50 px (a cell's 40 dp control); L4 the
    Month is the phone's dot grid — *fixed 2026-09-17* — (titled cells on a wide window; the dots PR folds in); T1 two type
    spellings coexist (the sweep onto the seven styles + an audit rule refusing Material role names in
    `ui/`); T2 copy that is not the app's (*(s)* plurals, raw enum names, spec citations); F1 the find
    bar reopens on every page after one Ctrl+F (`findRequested` never consumed); F2 the F1 card omits
    the shelf's chord (rows hand-written, not generated — the 14e invariant, now a test); F3 the habit
    pane is a stub. **Med / Low, recorded here by the reports' row ids, never dropped:** ***L5 fixed 2026-09-17 — the Calendar's chrome PR (`docs/critiques/calendar-chrome-function.md`: one 52 dp bar on every tab, the bar the window's title bar; L9's Tasks half with it);*** ***L6 fixed 2026-09-17 — the quick switcher PR (`docs/critiques/quick-switcher-function.md`: a centred card, a 36 dp field, rows at the profile's height, Recents; the AI key and the desktop passphrase on the same field);*** ***L6b fixed 2026-09-18 — the Material frames PR (`docs/critiques/material-frames-function.md`: the nineteen and the phone's passphrase on `TendrilField`; rule 18);*** ***L7 and L8 fixed 2026-09-17 — the quick-add strip PR (`docs/critiques/quick-add-strip-function.md`: the strip 44 dp on the find bar's field with the chips inline, the popup on the 36 dp field; the tray ≤ 30 % of the pane, the header's minutes below a 90 dp lane);*** ***L7b fixed 2026-09-18 — the quick-add tokens PR (`docs/critiques/quick-add-tokens-function.md`: the recognised runs tinted in the line on `findSoft`, the chips kept — Todoist's quick add, measured; an event's *by friday* stays in its title; the Add task sheet's field focused on open);*** ***L9 and L10 fixed 2026-09-17 — the canvas cards PR (`docs/critiques/canvas-cards-function.md`: the bar's *Fit* and `+` replace the canvas FAB on a wide window — Tasks' half went with L5; a double-click on the ground makes a card under the pointer with the discard rule; the badges on hover or on the selected card; the caption *Empty card — open*; *Fit* brings a card past the edge back);*** ***D4–D8 fixed 2026-09-18 — the design layer PR (`docs/critiques/design-layer-function.md`: `surface2` at 6 % both modes, the Road Map's edges at full alpha, `accentStrong` and `thirdStrong` gone, the radius family through Material's shapes with audit rule 17, Tab's ring and the list cursor as one);*** D9 (new, Low) Tab stops on a tree row's hover-revealed `···` while it is invisible — skip it or show it while focused; L11 submenus
    over their parent at the window's edge; L12 Journal day pages read `journal/…` in the tree and
    the shelf (show the date); L13 the Timeline's first bar clipped; L14 the Reminders presets
    overflow (`FlowRow`); ***L15, T3, T4, T5 fixed 2026-09-18 — the Material frames PR (the Add sheets as `TendrilSheet`s; the date picker's headline at `heading`; the ellipsis rule 19; a sheet's title at `pageTitle`; a slide-over's content under the caption row — its × had sat under Windows' ✕ since L5);*** T6
    (no change — recorded); ***F4–F8 fixed 2026-09-18 — the database views PR (`docs/critiques/database-views-function.md`: *Delete view…* asks; a ▾ on the active chip; a new view makes the property it plots by — Notion's, measured — and a blank Select cell's rows sit in *No Status*; the New view sheet with a name and the layouts as chips; one `···` vocabulary with *Open Trash…*);*** ***L11–L14, F9–F14 fixed 2026-09-18 — small things III (`docs/critiques/small-things-3-function.md`: the submenu flips at the window's edge; Journal days shown as dates; the Timeline's first bar in view; the reminder presets as a list with their fire moments — TickTick's, measured; the label sheet lists all; *Show Someday · n*; no icons on the row menus; the map never rests overlapped and reveals settled) — the Med/Low list is closed;*** ***L7b fixed 2026-09-18 — **item 22 is closed**;*** F15 a black frame on
    Agenda, unverified. A tool note for every later walk: the computer-use screenshot of a Compose
    window can be a minute stale — measure from native grabs only.
24. ~~**Small things IV — Lows seen on later walks (opened 2026-09-18, item 7's walk; `docs/critiques/canvas-embed-measured.md`).**~~ *Closed 2026-09-21 — S14 was the last line; every S below is struck.* **S1** the row page's strip shows a Date cell's value as ISO (*Read on 2026-09-19*) on both platforms — the Table's cell format; F·P4's *ven 25* has not reached the database's date cells. **S2** the *Insert block* and *Block actions* slide-overs list rows at ≈ 47 px on the desktop against the profile's 29 (Material rows outside the type-class audit's reach). **S3** a text field's right-click on the desktop opens Compose's default context menu — white on a dark register, the system's Italian labels — with *Block actions…* appended; it wants the register's colours. **S4** on the phone a swipe that starts in the ≈ 40 dp band between the page's last item and the bottom bar does not scroll (the band is an inset's padding outside the list). ***Assessed 2026-09-20 against the build (every critique's *Recorded* list and this item, grep-verified):*** **obsolete, struck** — ~~the phone's missing *Fit* (the bar's *Fit* is unconditional since the mind-map pass)~~ *(struck in error — the bar's buttons sit under `if (wide)`; **built 2026-09-20** with the card's width: the phone's `···` holds *Tidy* and *Fit every card*)*, the Postpone chips' wrap and the weekday initials (item 23's Lows), the Merged list's plain checkbox on a counting habit (`HabitRow` draws `HabitCheck`), the Month's dots (the Month grid PR), F·P5 (no path, twice). ***S1–S7 fixed 2026-09-20 — small things IV (`docs/critiques/small-things-4-function.md`: the row page's date by F·P4's rule; the slash and Block actions sheets on `TendrilMenuItem`; the desktop's text menu in the register's colours with the app's words (`PlatformTextMenus`); the shell consumes the navigation bar's inset for its content; a Journal day's bar reads its date; the Today strip's `HabitCheck`; Tab skips an invisible `···`).*** **Kept, cheap — one PR next:** ~~S1–S4 above;~~ ~~**S5** the page bar shows a Journal day's raw `journal/…` title where the tree, the shelf and the switcher show the date (the one L12 site missed); **S6** the Journal's Today strip checks a counting habit with a plain `Checkbox` (`JournalTodayStrip.kt`) — `HabitCheck` with the amount, as the Habits and Merged rows; **S7** D9 — Tab stops on a tree row's hover-revealed `···` while it is invisible.~~ **Kept, each its own PR when wanted:** ~~**S8** the mind map's sibling reorder by drag (order is creation order; Xmind's)~~ ***S8 fixed 2026-09-20** — the order is the position's, a drop tidies at once, the Map side follows the node (§3.7's amendment);* ~~**S9** Timeline and Fishbone (grounded and decided, deferred until Down has been used)~~ ***S9 built 2026-09-21** — the spine structures (§3.7's amendment; `docs/critiques/timeline-fishbone-function.md`)*; ~~**S14** a leaf's width at 7.5 dp per character cuts a wide word (*Shredded paper*) where the strip's 8.5 fits (S9's walk)~~ ***S14 fixed 2026-09-21** — the font's own advances for the leaf and the card (`TextWidth.kt`), the card's padding constant corrected to what it draws (`docs/critiques/leaf-width-function.md`); item 24 is closed*; ~~**S10** an edit-habit sheet — no field of a habit is editable after creation, the largest gap on the list~~ ***S10 fixed 2026-09-20** — the Add sheet edits (`initial`), `Habit.edited`, *Edit…* on the row's menu, the pane and the phone's sheet (`docs/critiques/edit-habit-function.md`)*; ~~**S11** the block selection's Touch gutter (16 dp before a paragraph is thin for a thumb — a drag handle or a wider gutter)~~ ***S11 fixed 2026-09-21** — the margin 24 dp under Touch, 32 before a paragraph's text (`docs/mockups/touch-gutter.html`, `docs/critiques/touch-gutter-function.md`); the block's own long press (D) and a handle in selection mode (C) recorded there, not taken*; ~~**S12** collapsible frames (decided *not now* after item 15; still valid)~~ ***S12 built 2026-09-21** — §3.7's frames amendment; `docs/critiques/collapsible-frames-function.md`*; ~~**S13** the database hue slider and card colours — the token map's last open slot, on which the Road Map's database fill and JSON Canvas colours wait~~ ***S13 built 2026-09-21** (v24) — §2.3 *The token map*'s amendment; `docs/critiques/hue-mock.md`, `hue-function.md` (the phone's walk pending)*. **Notes, not work:** a Down child's relationship crossing the tree (the geometry's — the outer sides are honoured), nested frames drawn alike, the marquee's no-scroll rule, the undo stack per page open, the strip's fast-typing loss (unreproduced), T6 and F15, `.canvas` import (no importer to extend), `pageTitle` at two line heights (T·P3), B§13.6's tabs (no), a wider canvas card (the strip answered the proportion).
23. ~~**The phone catch-up and the phone audit (2026-09-18)**~~ *Closed 2026-09-18 — item 23's Lows PR.* — *(**Decided 2026-09-18** — two passes, two PRs: the catch-up of the phone halves pending since #82 (`docs/critiques/phone-catch-up.md`), then a measured `design-critique-plus` audit of every phone surface beside TickTick, Todoist, Obsidian, Notion and Notion Calendar on the same phone; every High into one follow-up fix PR; Med/Low here; the phone's data kept.)* **Catch-up findings:** ***P1 fixed 2026-09-18 with the catch-up — the launch crash (`LabelFilterRow`'s negative padding under Touch, since 14h·2);*** ***P2, P3, P5, P6, P11 fixed 2026-09-18 — the phone's fix PR (`docs/critiques/phone-fixes-measured.md`: every sheet scrolls with rule 20, a submenu pushes a level under Touch, the canvas's `···`, the rows' meta on one line at 59 dp, a Robolectric layout test); P4 withdrawn — no phone app navigates a list by hardware keys;*** **P2** (High) a `TendrilSheet` on the phone does not scroll — with the keyboard up the Add task sheet's rows overlap; **P3** (Med) the Urgency submenu covers its parent on a 360 dp window — under Touch a submenu opens below its item; **P4** (Med) the Pages list's DPAD ↓ lands on the label chip, not the list's cursor; **P5** (Med) the canvas page has no `···` on the phone; **P6** (Med) task rows' meta is raw ISO and wraps; **P7** (Low) *Opens on*'s *Agenda* chip and *Restore backup* wrap; **P8** (Low) the Day view's preview chips start at the screen's edge; **P9** (Low) the find bar's hints name keys to a finger; **P10** (Low) the Day header's Italian order (*venerdì, settembre 18*); **P11** (process) nothing in the stand-in composes a layout — a Compose UI test of the Pages tab with a label is the fix PR's call. **The phone audit (2026-09-18 — `docs/critiques/phone-layout-full.md`, `phone-type-full.md`, `phone-function-full.md`; grounds dumped on the phone: TickTick's row 46, Todoist's 57 with 17 / 14 sp, Notion's 48 / 45 at 16 sp, Notion Calendar's 100 dp hour):** **T·P1** (High) the phone's primary text 14 sp against 16–17 on every ground, the second line 12.5 against 14 — *decision:* a ×1.14 Touch type scale (body 16 · description 14 · caption 12.5 · label 14 · heading 16 · pageTitle 20; the editor's sizes kept), recommended, or keep; **L·P1** (Med) six row pitches under Touch (53–71) — *decision:* the profile's 56 for every list row with the checkbox at 40 in a 48 target, recommended; **L·P2** (Med) ≈ 200 dp of chrome before the Calendar's and Tasks' first row — *decision:* the pickers as bar menus and the layers in `···` (L5's answer), recommended; **L·P3** (Med) targets under 48 dp — the find bar's buttons 36 wide, the row page's values 31 tall, the Month's cells 42; **T·P2** (Med) Material's 15 / 17 dp bands beside the app's 14 / 16 / 18 — *decision:* `TendrilMenuItem` the app's row under Touch too once the scale is set; **F·P1** (Med) the row page's strip values do nothing on tap; **F·P4** (Med) the edit sheet's *When* is ISO; **T·P3** (Low) `pageTitle` at two line heights (25 / 22); **L·P4–L·P6, T·P4–T·P5, F·P2, F·P3, F·P5** (Low) as the reports list them. ***T·P1, L·P1–L·P3, T·P2, F·P1, F·P4 fixed 2026-09-18 — the phone's second fix PR (`docs/critiques/phone-fixes-2-measured.md`), all four decisions A;*** ***the Lows — T·P3–T·P5, L·P4–L·P6, F·P2, F·P3, P7–P10 — fixed 2026-09-18 (`docs/critiques/phone-lows-measured.md`), F·P5 recorded as *cannot reproduce*; **item 23 is closed.***
20. ~~**The desktop key file could be DPAPI-wrapped after all.**~~ *Closed 2026-09-18 — see the end of the item.* §0.6.15 and the windows spec said a
    DPAPI wrap needs JNA, which the offline build cannot fetch — but `net.java.dev.jna:jna` and
    `jna-platform` 5.6.0 are in the Gradle cache (found 2026-09-13, B§13.6 #7): a direct dependency
    resolves offline. `Crypt32.CryptProtectData` would replace the NTFS-ACL caveat; the same jar gives
    the desktop a global quick-add hotkey. *The hotkey half shipped 2026-09-16 (the tray PR, §2.2
    *The notification area*: `jna-platform` is a direct dependency now); the DPAPI wrap stays open.*
    ***Closed 2026-09-18 — the DPAPI wrap shipped with item 9's move (`SecretWrap`, `DpapiWrap`; §0.6.15 amended).***
19b. *(Numbered 19 when opened, which **Find in page** below already was — noted 2026-09-24 rather
    than renumbered, because both are cited as "item 19": `BlockSelection.kt`, `BlockUndo.kt`,
    `ShortcutsOverlay.kt` and `docs/critiques/block-selection-*.md` mean this one; `FindInPage.kt`
    and `docs/critiques/find-in-page-*.md` mean that one.)*
    ~~**Cross-block selection and undo** (B§13.6 #10): the editor is one `BasicTextField` per block,~~
    so a selection cannot span blocks and undo is the platform's per field. To scope in the per-block
    editor pass, not the desktop layout pass. Raised 2026-09-13. *Decided 2026-09-19 — build:
    a block selection (Esc from a field selects the block, Shift+↑↓ / Shift+click extend, Ctrl+A
    twice selects all, a marquee on the ground under a pointer) with Delete, Ctrl+C/X/V and a
    group drag, and a session undo stack for block operations on Ctrl+Z / Ctrl+Y outside a field —
    Notion's, Logseq's and Craft's shape [Assumed]; Obsidian gets it from its one text field. Mock
    first, then the build.* *Resolved 2026-09-19 — built as decided; §3.1.1's amendment;
    `docs/critiques/block-selection-function.md`. Recorded there: the phone's block long press is
    the row's margin and prefix (the text's long press is the platform's word selection) — a
    drag handle or a wider Touch gutter is a later pass.*
17. ~~**An agent over the export, and a local model** — B§6 #18's other two halves, kept out of
    §0.6.15 on purpose: the Markdown export (§7) is the honest interface for an agent (it reads
    files, not the app), and a local model would need a runtime the offline build cannot fetch.
    Neither is precluded by the verbs; neither is planned.~~ *Closed 2026-09-19 —
    `docs/agent-over-export.md` (and, since 2026-09-22, the zip carries a `CLAUDE.md` at its root —
    `markdown/AgentReadme.kt` — with the folder's shape and the three rules, so the export explains
    itself to the agent; the name is reserved at the root since 2026-09-24, so a page called
    `CLAUDE` there is written as `CLAUDE (2).md` rather than failing the export): which export carries what (the Markdown zip is the one to hand
    over; the sync folder is live and never an agent's to write), pointing Claude Code at the
    unzipped folder, the three rules (read-only towards the app, a snapshot, where the agent runs
    is where the notes go), and why neither a verb in the app nor a local model. Nothing built.*
18. ~~**A relation cell on a row's own page shows raw uids** — the row-as-page property strip
    (`RowPropertyEditor`) renders a RELATION value as its comma-joined page uids; the Table's
    cell resolves them to titles. Seen 2026-09-13 on *Escape test* after step 8f bound "Blocked by".~~
    *Resolved 2026-09-15 (14d): `RowUnboundEditor` gained a read-only RELATION branch — the
    titles, "—" when empty — after `docs/critiques/pages-desktop.md` #1 found the uid in an
    editable field; editing stays in the Table.*
19. ~~**Find in page** (Ctrl+F) — decided 2026-09-15 as its own PR after 14e: a find bar over the
    open page's blocks, a match count, next/previous, the match highlighted in the text through
    the span transformation (`spansVisualTransformation`), Esc closing it; the chord joins
    `Shortcuts.kt`'s table and the F1 card when it ships.~~ *Resolved 2026-09-16 — §3.1.1
    (amended): the bar, the blocks-only match set, the marks through the transformation, Ctrl+F
    in the table. Desktop verified; walked on the phone 2026-09-18 (`docs/critiques/phone-catch-up.md`, `phone-function-full.md`).*
16. ~~**Database-level history** — §0.6.13 versions a page's title and blocks only. A database's
    columns, views and a row's property values have no history; if a lost column or value is ever
    missed, the record to version is the full `PageSnapshotRecord` with a restore that goes through
    the real merge under a fresh timestamp (so it syncs as a newer edit). Not needed until it is.~~
    *Closed 2026-09-19 — recorded, not built. The design above is the answer when a lost column or
    value is first missed (schema then: a `kind` on `page_revisions`, the snapshot record as the
    body). Notion's history is per page — a database page's version holds its views, a row's page
    its blocks, and property edits are the row's *Updates* feed, not restorable versions [Assumed];
    nothing there argues for building ahead of the need.*
15. ~~**Canvas frames/sections and canvas templates** — B§6 #16's remainder after step 8c's check: a
    nested canvas already exists as a `PAGE_EMBED` card pointing at a Canvas page (no new entity,
    the benchmark's guess), and the mind-map layout lives on the outline block (§0.6.2), not the
    canvas. Frames and templates are not built; neither is needed until a canvas outgrows one
    screen.~~ *Resolved 2026-09-18 — frames (§3.7's amendment) and canvas templates (§3.1.3); `docs/critiques/canvas-frames-mock.md`, `canvas-frames-function.md`. **Still open here: collapsible frames** (Advanced Canvas's, 830 k installs — a frame folds to its label; Tendril-only in the file, since JSON Canvas has no such field), decided *not now*: after item 6, when the export's shape is settled.*
14. ~~**The desktop layout mirrors the phone's.**~~ *Closed 2026-09-16 — 14h·2; the body below is the record.* The desktop app drew the phone's touch layout — a
    bottom tab bar, sheets, finger-sized rows — in a window driven by a keyboard and mouse. To be
    revised against real desktop apps of similar purpose (which ones, and what changes: a side
    rail or menu instead of bottom tabs, dialogs or panes where the phone uses sheets, denser
    rows, keyboard shortcuts); a benchmark section to gather first. Raised 2026-09-12 while
    settling the sheet frame (§3); the frame is the phone's answer and a placeholder for this.
    *Benchmark written 2026-09-13 — B§13: six layout axes, thirteen apps, a pass of seven PRs
    (14a shell → 14g theme) whose decisions were answered the same day on the interactive mock
    `docs/mockups/desktop-shell.html` (rail; slide-overs; a resizable, collapsible tree; every
    measurement proportional to the screen on both platforms through one `LocalDensity` scale,
    the profile — Compact / Comfortable / Touch — and the calendar's opening view as desktop
    settings, Compact and Week by default; no mnemonics; System / Light / Dark, default System).
    B§13.6's ten further diffs answered the same day: right-click, back/forward and list keyboard
    folded into 14d/14e; a shelf pane as 14h; pop-out windows *(done 2026-09-16, §3.1)*, drag
    between panes (phone too) *(done 2026-09-16, §3.2, §0.6.14; the phone's strip unwalked)*, hover
    previews *(done 2026-09-16, §3.1.1, §3.4)*, and the tray with a global hotkey (JNA is in the cache, item 20) after the pass; tabs
    no; cross-block undo to item 19. The item closes when 14h ships.* ***14a shipped 2026-09-14**
    — the shell as mocked on both platforms (§2.2), the remembered window, the title, the sync
    controls into desktop Settings; reshaped mid-PR from "a Material rail behind the breakpoint"
    after the first desktop walk showed stock Material chrome and none of the mock — the plan's
    slicing had put every visible thing behind 14a. Verified on desktop (1200×800 rail; dragged
    below 840 → bar; refused below 800×600; killed and relaunched → same frame) and on the
    OnePlus in both orientations (bar; its landscape is 804 dp).* ***14b shipped 2026-09-14** —
    every sheet a right slide-over on a wide window (§3, *Bottom sheets*, amended), the phone's
    bottom sheet untouched. Verified on desktop (Calendar settings, History, New as slide-overs
    beside a readable page; scrim click and Escape close them; below 840 dp the same sheet is a
    bottom sheet) and on the phone (New still a bottom sheet). Two 14a leftovers fixed on the
    way: a `···` menu anchored at the top bar's left edge (the actions now sit in their own row,
    as `TopAppBar`'s did), and a maximised window's size stored as a floating frame (only a
    floating placement is remembered now).* ***14c·0 shipped 2026-09-15** — B§13.5 #4's scale,
    pulled ahead of 14c so the tree pane is proportional from its first line (§2.2, *Every
    measurement is proportional*); the desktop's Density setting.* ***14c shipped 2026-09-15** — Pages as two panes on a wide window (§3.1,
    amended): the tree beside the page, chrome split per pane (option B on
    `docs/mockups/pages-two-panes.html`), resizable and collapsible (Ctrl+\), `showPage` vs
    `openPage`. Verified on desktop (tree + empty state; a page, a database, a journal day in the
    pane; Journal expands; a block reference pushes and Escape returns; Ctrl+\ both ways; the
    handle drags and the width survives a relaunch) and on the phone (unchanged). Three fixes on
    the walk: the workspace painted no ground; a `LazyColumn` sheet's crash lesson reapplied; the
    handle's 8 dp target was a 5 px strip under the scale — now 12 dp astride the divider.*
    ***14d shipped 2026-09-15** — density under a pointer (§2.2 *Under a pointer*): hover `···`,
    right-click as the pointer's long-press, the floating toolbar; three `design-critique-plus`
    passes around it (`docs/critiques/pages-desktop.md`, `pages-phone.md`, `pages-function.md`),
    which also found a RELATION value shown as a raw uid in the row-as-page strip (fixed) and
    left this list for later PRs: ~~type steps too close on the desktop (14g), the Table's touch
    row height beside 32 dp tree rows (14f), the canvas caption's 2.8:1 contrast and "tap"
    wording, the chip row's rhythm, "2 row(s)" with dashes, the phone card's kind line that
    repeats its icon, a page-ground click that clears no focus,~~ ~~a `find` token per register so
    a found word and a selected row stop sharing `accentSoft` (14g)~~ *(done, 14g·2)*, ~~an
    `@mention` in Ink told from body text by weight alone (a faint underline?), the Road Map's
    ↻ needed before a new page appears, the row menu's *Urgency ▸* list opening over the parent
    menu rather than beside it, find's scroll landing a
    match already on screen at the top, a word living only inside a mapped-away subtree not
    found (small things, to close with 14h)~~ *(all done, 14h·2)*.*
    ***14e shipped 2026-09-16** — the keyboard (§2.2 *The keyboard*): one table, the F1 card
    generated from it, Alt+arrows and the side buttons for back/forward, Ctrl+Shift+N's
    quick-add intent, ↑↓↵→← and type-ahead in the tree and the Tasks list. Two critique passes
    (`docs/critiques/keyboard-desktop.md` on the mock, `keyboard-function.md` on the build); the
    build pass replaced the benchmark's layout-bound chords. Added to the small-things list: a
    page opened by Ctrl+N or Ctrl+T from another tab stacks over that tab rather than landing in
    the Pages workspace.*
    ***14f·1 shipped 2026-09-16** — Tasks as a desktop surface (§3.3, amended): the list beside a
    pane whose chips are the row's menu by name, rows under a pointer, the Trash sheets shared
    (§0.10 item 13 closed), the keyboard on Habits, Merged and the phone's Pages list. Two
    critique passes (`docs/critiques/tasks-calendar-mock.md` before, `tasks-function.md` on the
    build). Added to the small-things list: the filter rows span both panes above the split
    (14f·2 keeps them over the list); a task's repeat cannot be changed after the Add sheet.
    14f·2 (the Calendar) next.*
    ***14f·2 shipped 2026-09-16** — the Calendar's week grid (§3.2, amended), the opening view
    as a Settings choice, quick add's strip on a wide window, the Tasks filters over the list
    column. `docs/critiques/calendar-function.md` walked the build (the opening scroll fixed; the
    clipboard crash recorded as item 21). **The pass's surfaces are done — 14a…14f·2**; 14g (the
    theme model and the registers) next, then 14h (the shelf).*
    ***14g·1 shipped 2026-09-16** — the theme model (§2.3, rewritten): ten registers on two
    grounds, every token solved to a floor and test-walked, mode System / Light / Dark with System
    default, the theme in `KeyValueStore` on both platforms (the phone's file migrated once), one
    shared Settings section, DM Sans 400/500 only. `docs/critiques/registers-function.md` walked
    the build (the desktop's unpainted ground fixed in the scaffold).* ***14g·2 shipped
    2026-09-16** — the token map (§2.3 *The token map*): three data hues fanned from the accent by
    one rule, the error family solved on its soft, the find mark, labels and callouts as stored
    hues rendered by the register, the two hardcoded blues and the grey canvas edges gone, an audit
    rule so no literal colour returns.* ***14g·3 shipped 2026-09-16** — the urgency ladder (§0.6.4
    as amended): `importance` 0–4 (v20, the table rebuilt), the level the greater of the set level
    and the deadline's pressure, a stripe on rows and blocks, the two Tasks switches shared and
    the ladder on by default. **14g is done; 14h (the shelf) next.*** ***14h·1 shipped
    2026-09-16** — the shelf (§3.1, amended): a third pane holding another page, this page's Road
    Map neighbourhood or today's Journal; the openers, the one-header rule, the swap, the 45 %
    clamp, the chord; remembered. `docs/critiques/shelf-mock.md` (the pass on the mock drawn for
    it) and `shelf-function.md` (the build: the depth control and the title size fixed).
    **14h·2 (the small things) closes the item.*** ***14h·2 shipped 2026-09-16 — the item is
    closed.** The thirteen small things above (struck through below), each at its home: the type
    scale (§2.3), the Table's rows and the submenus (§2.2), the captions, the chip row and the
    tree's second mark (§3.1), the mention's tint, the ground click and find's two rules
    (§3.1.1), the map's refresh (§3.4), the footer's words (§5.6), the phone card's *edited*
    line. `docs/critiques/small-things-measured.md` (before) and `small-things-function.md`
    (after). **The desktop pass is done: 14a…14h, B§13.4.***
13. ~~**Desktop's Tasks & Habits has no Trash button and no reminder bell** (step 7a): the Entry and Habit Trash sheets and the Reminders sheet are still Android files taking `AppContainer`; the restore/purge they need is shared already, so moving the two Trash sheets is a small follow-up. Reminders stay Android's (no alarms on desktop, §12.1 of the windows spec). *Folded into B§13's 14f (2026-09-13): the Trash sheets move once sheets are slide-overs on desktop.*~~ *Resolved 2026-09-16 (14f·1): the two Trash sheets are shared and the desktop has the button; the bell stays Android's, as the reason stands.*
12. ~~**Calendar layer state does not persist** across app starts: it lives in the ViewModel because the app has no cross-platform preference store (`TaskPreferences` is Android `SharedPreferences`). One small `KeyValueStore` expect/actual would serve this and every later desktop setting.~~ *Resolved 2026-09-12 (step 8·0): `data/prefs/KeyValueStore` — an interface with one shared map-and-flows body and a platform `persist` (Android `SharedPreferences`, desktop a `.properties` file), on `WorkbenchCore`; the layers are its first consumer and Review's cadence its second (`review_cadence_days`, no UI yet). Not for secrets.* B§6 #6's *calendar sets* are not built; a label filter on the layer row is the cheap version if wanted.
11. ~~**Desktop: `EnableSyncSheet`'s "Turn on" sits below the window** until the sheet is expanded from its drag handle (Tab to the handle, Space). Its `Column` is `fillMaxHeight(0.8f)` of a sheet the desktop window does not clip to; a phone never shows it. Pre-existing, found 2026-09-12 while verifying §0.6.8; a layout fix, not a design question.~~ *Resolved 2026-09-12 (step 6b): the sheet opens fully expanded (`skipPartiallyExpanded`), as does the new edit sheet. Applied to every sheet on both platforms later that day through `TendrilSheet` (§3) — the phone had the same failure on its taller sheets.*

### 0.11 Relationship to the rest of this file and to the companion documents

- **Within this file:** a later section defers to §0. Where one disagrees, it is amended at the
  section, marked **Corrected** with a pointer here, and the Revision Log gets its one line.
  Nothing in §0.6 is "done" until the section it touches says so.
- **`tendril-windows-spec.md`:** §12's anti-drift rule gains a third line — §0 is cross-platform
  content, so any change to it gets that file's Revision Log a same-day pointer, as a `shared/`
  change would.
- **`docs/scope-decisions.md`** remains the register of what is *not* built and why; §0.7 is the
  short list, that file is the evidence.
- **`docs/benchmarks.md`** is dated and **[Assumed]** by nature; rescore before relying on any row
  older than a release of the app it describes.

---

## 1. Overview

*The purpose this overview serves, the constraints it is filtered through, and the bar it is
measured against are §0 (added 2026-09-11); this section keeps the description and the build
environment.*

Tendril is a personal productivity app — an Android app plus a Windows desktop companion (§12) over
a shared Kotlin Multiplatform core — combining a Notion-like page/database system, a
calendar capable of replacing the phone's default calendar, a combined tasks-and-habits tracker, a
relationship map between pages, and a themeable settings layer — all built to avoid the two failure
modes of this category: burying power-user features, and overwhelming casual use. One person should
be able to use it to track groceries and a company roadmap without the app feeling like two
different products.

**Five pages:** Pages, Calendar, Tasks & Habits, Road Map, Settings — see §3 for the full functional
spec of each. *(**Clarified 2026-09-07 — the count is five and stays five.** It counts the
Workbench's nav destinations (§2.1), of which `WorkbenchDestination` has exactly these five.
**Canvas** is not a sixth: it is a third page *kind* — `PageKind.CANVAS`, alongside `PAGE` and
`DATABASE` — created from the Pages hub's New sheet and rendered full-screen inside the Pages
destination, the same way a Database page is. It is named here because §3.4 recorded its absence
from this line as one of the gaps in its own record, and because a reader who met `PageKind.CANVAS`
in the code and this sentence in the spec had no way to tell an uncounted sixth tab from a page kind
that was never meant to be counted. Its functional record is §3.7; its entities are in §4.)*

**Target device / build environment** (Decided):
1. minSdk 30 (Android 11+), targetSdk 36+ (Android 16+) — see §9.2.1 for behavior changes this
   triggers regardless of distribution channel
2. Unrooted
3. Storage Access Framework (SAF) folder access for the sync folder — no Shizuku, no root (Decided
   2026-07-13, supersedes the original Shizuku plan; see §9.3)
4. An external Syncthing-fork app handles the actual sync; Tendril only reads/writes snapshot files
   inside a folder that app is already syncing — never the live database itself (see §9.4)
5. Personal use only — no Google Play distribution
6. Built on Windows 11, Android Studio, JDK 21 (the desktop app on Android Studio's JetBrains Runtime 21 — `tendril-windows-spec.md`; the toolchain as built is in §9.2)

---

## 2. Navigation & Visual System

### 2.1 Four navigation explorations — Workbench chosen

Four full navigation architectures were prototyped for the same five pages and feature set, varying
chrome and information density: **Workbench** (persistent bottom tabs, structured cards/grids),
**Ledger** (same persistent tabs, flowing document-style content), **Deck** (dark, floating capsule
dock, dense technical layout), **Atelier** (chrome nearly invisible, hairline tab indicator, airiest
option).

**Decided:** Workbench is the one being built out. The other three remain fixed design explorations
in the prototype file — not being developed further, but kept as reference for anyone revisiting the
navigation-paradigm question later.

### 2.2 Workbench-specific decisions

- **Every measurement is proportional to the screen** *(**Decided 2026-09-13, B§13.5 #4; built
  2026-09-15 as 14c·0.** One number, on both platforms: the shell multiplies `LocalDensity` by
  `shellScaleFor(shorterSideDp, profile)` = clamp(shorter side ÷ 800 dp, 0.85…1.25) × profile —
  Compact 0.85 (the desktop's default, chosen in its Settings under *Density*, key
  `density_profile`), Comfortable 0.95, Touch 1.23 (the phone, fixed: a finger does not get a
  setting). *[Amended] 2026-09-16 — the three were 0.9 / 1.0 / 1.3; beside Notion at the same
  728-px window Compact read "a bit large" (the user, mid-walk of B§13.6 #3), so Compact went to
  0.85 and the other two by the same ratio; the measurements below are the original build's.* Every `.dp` and `.sp` below the shell converts to pixels through that factor, so no
  site is edited and none can forget — the rail's 84, a row's 48, the calendar's `DAY_WIDTH`, a
  slide-over's 440 all follow. The shorter side is read in the platform's own dp before the
  override (or the scale would feed itself); the 840 dp breakpoint is read in the scaled dp,
  since "room for a rail" is a question about scaled things. Measured: the phone's bottom bar
  went from 243 px to 268 px (80 dp × 0.85 × 1.3 × 3 + a hairline, the formula's own number);
  on the desktop at 1200×800 Compact is 0.9 and switching the chip to Touch grows every element
  by the same factor, live. `ui/nav/ShellScale.kt`, tested. *[Amended] 2026-09-19 — the user's brief: the desktop must not resize with its window (no other app does). **The desktop's reference is Windows' text size** — Settings › Accessibility › Text size, 100…225 % (`HKCU\Software\Microsoft\Accessibility\TextScaleFactor`, read through JNA at launch and on every focus gain, `WindowsTextScale.kt`) — times the profile: `desktopShellScale(percent, profile)` = clamp(percent, 100…225) ÷ 100 × profile, provided as `LocalSystemTextScale` per window (the main, the pop-outs, the quick-add popup) and read by `WorkbenchEnvironment` before the window rule. Display scaling (125 %, 150 %) stays the runtime's density. The window-side rule above is the phone's, whose screen is fixed. Measured: pixel-identical chrome at 700 × 520 and 1600 × 1000.* *[Amended] 2026-09-18 — item 23's Lows, T·P3: on Android the factor is applied to the activity's own density (`MainActivity.attachBaseContext`, the configuration's smallest width as the shorter side) rather than to the composition — a sheet, a menu or a dialog there is its own window and had started from the platform's density, drawing 4.6 % under the page on the phone (and would have drawn 23 % under it on a tablet); `WorkbenchEnvironment(platformScaled = true)` then scales nothing. The desktop's windows inherit the composition's density and the shell keeps scaling there.*)*
- **The shell** *(**Amended 2026-09-14 — B§13.4 14a.** The five destinations, the running timer
  and every screen's top bar are drawn as `docs/mockups/desktop-shell.html` draws them, by
  `ui/nav/Shell.kt` rather than by Material's `Scaffold`/`NavigationBar`/`NavigationRail`/`TopAppBar`,
  on **both platforms**: below **840 dp** of width (`ShellLayout.BAR`) an 80 dp bottom bar on
  `surface2` with a top hairline, 64×32 pills and 12 sp labels, the timer as a 36 dp strip on
  `accentSoft` above it (title, tabular clock, *Stop*); from 840 dp (`RAIL`) an 84 dp rail with
  a right hairline, 52×30 pills and 11.5 sp labels *(the labels are `body` — 14 sp, Medium when current — since 2026-09-20: Notion's and the Claude app's navigation items measured 10 px of cap height beside the rail's 7 at `caption`; the phone's bar keeps `caption`)*, the timer at its foot (▶ title, tabular
  clock, *Stop*). The breakpoint is the *window's* width, not the platform's — a phone in
  landscape past 840 dp would get the rail; the OnePlus at 804 dp does not. The top bar is
  52 dp with a 17 sp/500 title and a bottom hairline, with `TopAppBar`'s three slots so the
  twelve screens swapped it by name. Colours are the theme's tokens through `Theme.kt`'s
  mapping, so 14g's registers reach this chrome unchanged; the density scale is 14d's. The
  desktop window remembers its frame (`window_frame` in `KeyValueStore`, default 1200×800,
  minimum 800×600, OS-placed until moved) and is titled *Tendril*; its sync controls left the
  top of every screen for a *Sync folder* section at the head of Settings, where Android keeps
  them and the only place the mock has room. Exempt: the widget-configure Activity, outside the
  shell.)*
- **A drag's start** *(B§13.6 #5.)* Under a pointer a drag begins on press-and-move; under
  Touch on a long press (a plain touch-drag would fight the scroll; a long press on a mouse is a
  wait) — `dragSource`, one modifier both surfaces use. The Plan rail and the grid's own block
  drag keep their long press on both (their gesture predates the rule; recorded).
- **Submenus and captions** *(14h·2.)* A menu item with a `▸` (*Urgency ▸*, *Show beside ▸*)
  opens its list **beside** the item, level with it at the menu's right edge (`ui/components/
  Submenu.kt` — the platforms' placement; a nested `DropdownMenu` lands under the parent). A
  caption that says how to open something says *· open* under a pointer profile and *· tap to
  open* under Touch (`openVerb()`, from `LocalDensityProfile`). A list under a pointer profile
  keeps its rows at the profile's height — `DensityProfile.rowHeightDp` 36 / 44 / 56 — with its
  controls in 28 dp targets (`listInteractiveMinDp`; Material's 48 under Touch): the Table's rows
  measured 58–60 px against the tree's 27 because a `Checkbox` alone is 48 dp.
- **Under a pointer** *(**Amended 2026-09-15 — B§13.4 14d, B§13.6 #1.** Three behaviours that
  exist only where there is a pointer, on both platforms' code (`ui/components/Pointer.kt`; a
  mouse on an Android tablet gets them). **Right-click is the pointer's long-press, everywhere
  a long-press exists:** a page row (the tree's, the phone's card) opens *Open · Show on Road
  Map · Move to Trash* — at the pointer after a right-click, hanging off the row after a
  long-press or a click on its `···`; a block opens its action sheet. Inside a block's *text*
  the field's own menu wins on both platforms (the desktop's cut/copy/paste, the phone's
  selection handles), and on the desktop the block's actions are one item appended to it,
  *Block actions…* (`TextContextMenuExtras`, an expect/actual: `ContextMenuDataProvider` there,
  the content unchanged on Android). **Hover reveals the row's `···`** — drawn at alpha 0 and
  never laid out away, so a title's width does not change when the pointer arrives; a 28 dp
  target with an 18 dp glyph, which is 23.8 px at the scale's 0.85 floor (`docs/critiques/pages-desktop.md`
  measured the mock's 16 dp against the 24 px pointer floor); absent, not disabled, on touch.
  **The formatting toolbar floats above the selected block** under a pointer profile — a popup
  on `surface` with a hairline and a soft shadow, 26 dp buttons, flipped below when the window
  has no room above, following the field's focus — and sits inline exactly as before under
  Touch. `LocalDensityProfile`, provided by the scaffold beside the scaled density, is how a
  surface asks "pointer or finger?" without a platform check; Touch when nothing provides it.
  *Move to Trash* from a row is `PagesViewModel.moveToTrash` — the same write the page's own
  menu makes, a row-linked task going with its row — refused under View-Only, the item greyed
  so the lock is visible. Three critique passes around this PR live in `docs/critiques/`
  (desktop, phone, the function walk); their High findings are fixed here, the rest listed in
  §0.10 item 14. **The canvas** *(2026-09-17 — L9 + L10)*: under a pointer a click on a card
  **selects** it (a 2 dp accent ring; the badges — delete disc, link handle — show on hover or
  on the selected card and are drawn at alpha 0 otherwise, never laid out away), a second click
  opens it, a click on the ground clears, Delete / Backspace with a selection opens the delete
  dialog, and **a double-click on the ground makes a text card under the pointer** with its
  editor open — a card whose editor closes with nothing typed is discarded (§3.7). A finger
  keeps the phone's tap-opens and always-on badges. **Keyboard focus is visible** *(the design
  layer, 2026-09-18)*: the controls that draw no ripple — the rail pill, a register swatch — show a
  2 dp `primary` ring while focused *from the keyboard* (a click focuses without a ring, as Android
  draws none for touch); on a list's row Tab moves the list's cursor, so the 14e cursor ring is the
  one ring and ↑↓ continue from where Tab landed.)* *(Amended 2026-09-18 — §0.10 item 15: on a canvas under Touch a **long press lifts** a card or a frame and a swipe pans (Obsidian mobile measured; the tray's rule); under a pointer press-and-move drags — a pointer never pans by dragging a card.)*
- **The keyboard** *(**Amended 2026-09-16 — B§13.4 14e, B§13.6 #2 and #8.** The desktop's
  fixed set is **one table** (`ui/nav/Shortcuts.kt`), and the shortcuts card (F1, or *Keyboard
  shortcuts…* in Settings) is generated from it, so what is listed is what is bound — a test
  holds every action to one unique chord. The table: **Ctrl+1…5** the tabs · **Alt+← / Alt+→**
  back and forward (also the mouse's side buttons) · **Ctrl+\** the tree · **Ctrl+Shift+\** the
  shelf (14h·1) · **Ctrl+W** closes a pop-out window and **Shift+click** on a tree row opens one
  (B§13.6 #6; Ctrl+W is a pop-out's key, a static row on the card) · **Ctrl+N** new page ·
  **Ctrl+Shift+N** new task (Tasks opens with its Add sheet — a cross-tab intent like *Show on
  Road Map*) · **Ctrl+T** today's Journal · **Ctrl+K** the switcher · **F1** the card; Esc is
  back. Every key is one a person can press on any layout without AltGr or Shift: the
  benchmark's Ctrl+[ ] and Ctrl+/ are AltGr+è and Shift+7 on an Italian keyboard, the one this
  app is written on, so the browsers' Alt+arrows and Windows' F1 stand in
  (`docs/critiques/keyboard-function.md` #1). No menu bar, no Alt-mnemonics (2026-09-13).
  **Back has a forward**: `WorkbenchNavState` keeps what Back popped until any new move
  (browsers' rule). **Lists** (`ui/components/ListKeyboard.kt`, shared — a hardware keyboard on
  the phone runs it): the Pages tree and the Tasks list take ↑ ↓ (clamped), Home/End, ↵ (the
  tree shows the page; a task opens its `···` menu, having no detail screen), → / ← (expand,
  collapse or climb to the parent — the Finder's), letters as type-ahead with a one-second reset
  and the matched prefix **underlined in the row** (not a caption elsewhere —
  `docs/critiques/keyboard-desktop.md` #3), Esc clearing the cursor before it goes back (a
  `BackHandler` armed only while there is something to clear). The cursor is a 2 dp `outline`
  ring inside the row, distinct from the current row's tint and the hover's, drawn only while
  the input mode is the keyboard's — a finger sets the cursor but sees no ring, as Android
  draws none for touch. The rest of the lists (the phone's Pages list, Calendar, the Trash
  sheets) take the same modifier when 14f redraws them. Android has no fixed set yet; the table
  is shared and its Activity could adopt it. Find in page (Ctrl+F) is §0.10 item 19, its own
  PR.)* *(**Amended 2026-09-19 — §0.10 item 19, the block selection.** The editor's keys are
  static rows on the card under *Editor*, never chords in the table — they are the page's,
  active only with a field or a block selection: Esc selects the block (a second clears the
  selection, a third is back), Shift+↑↓ extend, Ctrl+A twice selects every block, Delete,
  Ctrl+C/X/V/D, Ctrl+Z/Y over block operations with no field focused. A right-click on a
  selected block's margin opens the sheet over the run; inside its text the field's own menu
  wins, with *Block actions…* last (14d).)*
- **The title bar, and one bar per tab** *(**Amended 2026-09-17 — L5 of §0.10 item 22, widened by the user's brief;** `docs/mockups/calendar-chrome.html`, `docs/critiques/calendar-chrome-mock.md`, `calendar-chrome-function.md`.)* On Windows the 52 dp bar **is the window's title bar**: the OS's own is removed (JetBrains Runtime's custom title bar — the toolchain is Android Studio's JBR 21, `jbr-api` from the cache; `Tendril windows/…/TitleBar.kt` installs, `ui/nav/TitleBar.kt` asks), Windows keeps painting minimize / maximize / close over the bar's right end and keeps its drag, double-click, snap layouts and edge resize. Two questions, answered by every bar from its own place in the window at runtime: **at the window's top edge?** — then its *ground* (where no control sits: a box behind the content that the hit test reaches only where no pointer node lies on top) is title-bar area and drags the window; controls stay controls without marking. **Reaching the window's right edge?** — then its actions end before the caption block, whose width the runtime reports (141 px at 125 %). The rail's top 52 dp is the same row, the app's mark on it; a page bar's title field takes its text's width under a title bar (a pop-out has no other ground); a pop-out's page bar is its title bar; nested bars lower down are only bars. A runtime without the API (Temurin) keeps the OS title bar and every inset is zero. Notion Calendar's 46 px toolbar, measured, is the same pattern; Notion's and Obsidian's too. And **every tab's header is Pages'** — one 52 dp bar, nothing under it: the Calendar's `‹ › September 2026 · Today` in the title slot with `Week ▾` (the four views; *Plan the day* on the Day) and `Layers ▾` (check items with the layer's dot, the menu staying open while toggling) in the actions, the grids drawing no ‹ › row of their own; Tasks' kind as the list column's 52 dp header tabs (`PaneHeader` — the tree header's row) with `Today ▾` at its right and *new* in the bar (no FAB on a wide window); the Road Map's filters as a `Filter ▾` check-menu (Journal, the kinds, one label), the legend at the canvas's foot. The phone keeps its rows. The bar's controls: `BarMenuButton`, `BarPillButton`, `MenuCheck` (`ui/components/BarControls.kt`), 28 dp at `label`.
- **The desktop's field and card** *(**Amended 2026-09-17 — L6 of §0.10 item 22;** `docs/mockups/quick-switcher.html`, `docs/critiques/quick-switcher-mock.md`, `quick-switcher-function.md`.)* The desktop's text field is **`TendrilField`** (`ui/components/TendrilField.kt`): a `BasicTextField` in one row of 36 dp (the find bar's 28; 48 under Touch), a hairline on `outline`, radius 6, `surface`, 12 dp side padding, the placeholder in `onSurfaceVariant`, a leading or trailing slot inside the hairline — the switcher's, the find bar's, the AI key's, the sync passphrase's, the quick-add strip's (28) and popup's (36) since L7, and since L6b (2026-09-18) **every** field on both platforms — the sheets' and dialogs' name fields, the counts, the searches, the canvas card editor (`minLines`), the phone's passphrase; `tools/audit.py` rule 18 refuses a Material field outside `ui/components/`, rule 19 a one-line text without an ellipsis. A slide-over's content starts under the title bar's row where the window has one (its × had sat under Windows' ✕); Notion Calendar's and Obsidian's palette fields measured 37 / 38 CSS px where Material's `OutlinedTextField` is 56. The frame for a reference card is **`CentredCard`** (`ui/components/CentredCard.kt`): a `Popup` over the scrim (`Scrim`, black at 24 % since 2026-09-21 — §3 *Bottom sheets*), one card ≤ 560 dp and ≤ 60 % of the window, radius 10, a hairline, a 16 dp shadow; its top at a share of the window's height (the switcher 20 %) or centred (the F1 card); Esc, the scrim and the caller's × close it; `KeyChip` is its key chip, the F1 card's and the switcher's chords. Every dimension is a share or a dp, never a px. *(Amended 2026-09-20 — small things IV, S3: the field's right-click menu on the desktop — Cut · Copy · Paste · Select all, and whatever a site appends — is drawn in the register's `surface` / `onSurface` / `surfaceVariant` with the app's English words through `ui/components/PlatformTextMenus`, provided once by `WorkbenchEnvironment`; Compose Desktop's default had been a light menu with the OS locale's labels. The two block sheets' rows are `TendrilMenuItem`s (S2).)*
- **The notification area** *(**Amended 2026-09-16 — B§13.6 #7, the tray;** `docs/mockups/tray.html`,
  `docs/critiques/tray-mock.md`, `tray-function.md`.)* The desktop lives in Windows' notification
  area: an icon (the colour mark) with three verbs — *Open Tendril · Quick add…  〈chord〉 · Quit*
  — drawn by the OS (a Win32 `PopupMenu`, AWT's), a tooltip, and `onAction` (a toast's click, a
  double-click) bringing the window back on the tab the last firing belongs to. **The window's ×
  hides it there** when *Keep Tendril running in the notification area when the window closes*
  is on (`close_to_tray`, default on; the tray's *Quit* exits) and quits when it is off —
  Slack's, Telegram's, Todoist-Windows' convention; Microsoft's Win32 UX guide prefers Minimize
  for this and is recorded, not followed: a reminder needs the process alive. **Reminders on the
  desktop** are Windows toasts (`TrayState.sendNotification`): `DesktopReminderScheduler` keeps
  one sleeping coroutine for the earliest firing owed and re-plans on every
  `EntryScheduleCoordinator` callback (it *is* the desktop's coordinator, replacing the no-op),
  with a fifteen-minute ceiling through a laptop's sleep; the arithmetic — the gates, a recurring
  EVENT's next occurrence, the overdue firing at start + time or midnight, each reminder at base −
  offset with the anchor rule, nothing in the past — is **`domain/reminders/ReminderFirings.kt`**,
  shared with Android's `AlarmScheduler` (which now only owns request codes and receivers) and
  tested for the first time (`ReminderFiringsTest`). A toast's title leads with the moment (*Due
  now · 〈title〉*, *In 10 min · 〈title〉*, *Habit · 〈title〉*). Nothing is queued for a moment that
  passed while the app was not running (the phone's boot sweep has no desktop twin). **The bell
  is on both platforms**: `ui/reminders/ReminderSheet.kt` moved to `shared/`, and
  `EntryScheduleCoordinator.onReminderRemoved` (default no-op; Android cancels the alarm) closes
  the asterisk its ViewModel carried. **A global quick-add chord** — `User32.RegisterHotKey` on a
  message-loop thread (`GlobalHotkey.kt`, JNA 5.6.0 from the cache) — opens **the popup**
  (`QuickAddWindow.kt`): 560 dp at 22 % of the screen's height, always on top, undecorated, the
  hover card's radius, the same line and chips as the Calendar's strip (`ui/entries/QuickAddField.kt`,
  the strip's field extracted; `domain/QuickAdd.kt` the one write) behind the accent's `+`; a line
  with no kind token is a **TASK** here (the strip's is an EVENT); Enter writes once and shows
  *Added · 〈title〉 · 〈when〉* for 700 ms, Esc closes, focus lost with the line blank closes and
  with text keeps it. *(Amended 2026-09-24 — audit 1.8: **once** is `QuickAddGate.kt`'s
  `QuickAddWriteGate`, claimed on the UI thread before the write's coroutine starts, and pinned
  since this date by `QuickAddGateTest` rather than by a walk. It had been the popup's `added`
  state, which cannot say “a write has started” until the database has answered — so both
  Enters of a double tap wrote, measured five times out of five. The extraction changed no
  behaviour; what it changed is that this sentence is now executable.)* The chord is **a pick list in Settings** (`ui/nav/QuickAddChord.kt`,
  `quick_add_chord`; a registration failure shows beside it): **Win+Alt+N** the default —
  Win+Alt+T, the planned one, is Windows' Game Bar's (found on the walk) — then Win+Alt+Q
  (Todoist's), Ctrl+Shift+Space, Shift+Alt+A (TickTick's), Ctrl+Alt+Space; no Ctrl+Alt+letter
  (AltGr on Icelandic and most European layouts), no Ctrl+Space (an IDE's completion). The F1
  card lists the stored chord as a static row. **§0.10 item 21** in the same PR: uncaught
  exceptions on any thread and inside a window's composition are appended to
  `~/.tendril-desktop-dev/crash.log` and the window kept (Compose's default handler showed a
  dialog and exited). **Rows, measured beside Notion on the walk** (the user's mid-walk brief):
  a task row was 65 px against Notion's one-line 30 and the tree's 33 — under a pointer task and
  habit rows are **one line** (the meta a `caption` at the title's right — Things' date tag,
  Notion's list view), the buttons 28 dp, the list's interactive minimum 28, and every one-line
  row takes Notion's measure: `DensityProfile.rowHeightDp` Compact **29** / Comfortable 36 /
  Touch 56, the tree 29 dp, the tray chip the profile's row; measured after: task rows 30, the
  tree 30, Notion 30. Under Touch the rows keep their second line. The Table's rows (50 px) are
  the audit's. *(Amended 2026-09-17 — the audit's fixes: the one-line row is one composable set, `ui/components/RowControls.kt` — `listRow`, `TitleAndMeta`, `rowButtonModifier`, `ListInteractiveMinimum` — used by the Tasks list, the Calendar's Day and Agenda lists and the Table (65 / 52–61 / 50 → 30 / 30 / 34 px); the meta is a `description`, not a `caption` — the class table's choice against Notion's 12 : 14; and **every menu** is `ui/components/TendrilMenu.kt`, whose items take the profile's row under a pointer (49 → 30 px beside Notion 29, Obsidian 25, Todoist 32, TickTick 36) and Material's 48 under Touch — `tools/audit.py`'s *bare menu* rule keeps it so.)*
- **Page cards** (Pages tab): horizontal row layout (icon, title, meta stacked to the right of the
  icon) rather than the original stacked/vertical card — roughly half the height of the first
  version, so more pages/databases are visible without scrolling, while keeping icon and font size
  unchanged.
- **Page-level settings icon**: always the horizontal three-dot (⋯) icon on ~~Calendar and Tasks &
  Habits' own settings entry points~~ Calendar's own settings entry point — never the gear icon,
  which is reserved exclusively for the main Settings tab, so the two are never visually confused.
  *(**Corrected 2026-09-06:** Calendar honours the rule and cites this bullet by number in its own
  code. Tasks & Habits has no per-page settings entry point at all — its single top-bar action is a
  Delete glyph opening the Entry/Habit Trash. The rule stays the rule for whenever one is added;
  stating it as current fact sends a reader looking for a control that is not there. §3.3's
  sync-folder-picker bullet, corrected the same day, is the other half of the same missing screen.)*
- **Road Map** (renamed from "Mind Map" — see §3.4 for why): occupies the full screen as an
  interactive canvas; "All Pages" is a collapsible bottom sheet/drawer over the canvas, not a fixed
  side panel.
- **Tasks view**: a Today / This week / This month filter bar sits directly below the
  Tasks/Habits/Merged switcher and above the Undated toggle.
- **Calendar default view**: Day, not Month (changed from the initial build).
- **Calendar → Week**: **Decided (2026-07-13)** — Workbench's current Week view (a horizontal strip
  of day-cards, per the prototype's `week-strip`) loses time-of-day placement — two events look
  identical whether they're an hour apart or eight. Adding a second layout, a true hour-grid (hours
  down the side, one column per day, events placed/sized by time — the shape the Deck exploration's
  `week-grid-deck` already has, restyled to Workbench), as a "Cards / Grid" toggle remembered as a
  preference.

### 2.3 Theming system (Settings → Appearance)

**Rewritten 2026-09-16 (14g·1, B§13.7) — the model is built.** The eight hand-typed palettes that
stood here from 2026-08 to 2026-09-16 are in git history (`Palette.kt` at `854ba1b`); nothing in
the app reads a typed hex any more.

**The type scale (Amended 2026-09-16 — 14h·2, `docs/critiques/small-things-measured.md` #1).**
Material's default scale put nine roles on five sizes, seven of them inside 11…16 sp, `titleMedium`
and `bodyLarge` both 16, and 27 literal `sp` sizes in eleven files had drifted onto eight values
inside 10.5…14. `Type.kt`'s `TypeScale` is now **six sizes a 1.125 step apart** — 11 · 12.5 · 14 ·
16 · 18 · 22 (headlineSmall 24; titleLarge skips one step) — each with its line height; labelSmall
11, labelMedium and bodySmall 12.5, labelLarge, bodyMedium and titleSmall 14, bodyLarge 16,
titleMedium 18, titleLarge 22. Every literal `fontSize` in shared UI takes one of them —
`tools/audit.py` rule 12 *off-scale font size* fails the run otherwise, the colour rule's shape.
The density profile still multiplies (B§13.7.3 rule 2). `docs/critiques/small-things-function.md`
measured the result.

**The type vocabulary (Amended 2026-09-16 — the type PR; `docs/mockups/type-vocabulary.html`,
`docs/critiques/type-vocabulary-mock.md`, `-function.md`).** *(Amended 2026-09-17 — the audit's fixes: **a kind of text is a class, a class has one style, the audit proves it.** The seven styles are aliases of Material's roles (`TendrilTypeTest`), so a raw role name is the same style spelled twice and rule 13 refuses it. `tools/type_sites.py` reads every text site's features from the code — slot, colour token, content, position among its block's Texts, the block's other calls — and assigns one of 31 classes by rules in a fixed order; `tools/type_table/table.json` maps each class to its style, chosen once by `meta-optimizer` over a scorer of the rules this section states (hierarchy, one style per place, preservation, Notion's 12 : 14 title-to-meta, headers Medium or above, ≤ 4 styles a screen); rule 14 re-classifies every site on every run. The one escape is a `// type: CLASS` comment on the site's line — a judgement written where the audit reads it (three today). One derivation is allowed, `TextStyle.tabular()`, for counts and clocks.)* Two findings, beside Notion at the
same window (the user's mid-walk notes on B§13.6 #3): *the font size in Compact is a bit large*
and *headers should be bold rather than large* — and, measured, the desktop had been drawing
every `Medium` as Regular, because each family was loaded at its default instance only and
Compose synthesises bold, not Medium. **Decided:** **Inter, bundled, the default** (what Notion
looks like on macOS; DM Sans kept as a choice — a stored `sans` keeps it, a phone that never chose
takes Inter), every family loaded at **true 400 / 500 / 600** through the resources `Font()`
overload that takes `variationSettings` (both platforms, CMP 1.12); the eye pass carries **400,
500 and 600** (never 700, never thin; the editor's `Bold` span is content and stays bold);
**Notion's rule — weight carries the hierarchy, the sizes step down**: the scale is now 11 · 12.5
· 14 · 16 · 18, with 20 and 24 for the editor's H2 and H1 only, and nothing in the chrome above
18. **Seven styles, one spelling per kind of chrome text** — `ui/theme/TendrilType.kt`, read as
`MaterialTheme.typography.heading` and friends, the Material roles set so a chip, a button or a
dialog inherits them without being told:

| style | size / weight | where |
|---|---|---|
| `pageTitle` | 18 / 600 | a screen's title, the page bar's editable title, a task pane's title, **a sheet's title** (the slide-over's header and the bottom sheet's — T5, 2026-09-18) |
| `heading` | 14 / 600 | the tree's *Pages*, the tray's *Tasks*, a shelf's header, Settings' and a sheet's sections, a card's title, the date picker's *Pick a day* |
| `body` | 14 / 400 | rows (tree, tasks, the Table's cells, the switcher), dialogs' copy, toggle labels |
| `label` | 12.5 / 500 | field labels (*Register*, *Mode*), chips, the FocusBar |
| `description` | 12.5 / 400, dim | explainers under a section, a card's second line, a due date |
| `caption` | 11 / 400, dim | *edited 2 h ago*, a card's footer, a count, the rail's labels |
| `eyebrow` | 11 / 500, uppercase, +0.06 em | *UNSCHEDULED · 3*, the shortcuts' groups, *Steps* |

The editor's content is not chrome and keeps its own sizes: `editorBody` 16, `editorH1` 24 /
`editorH2` 20 / `editorH3` 16 SemiBold. **Enforced:** `tools/audit.py` rule 12 is now *literal
type* — a literal `fontSize`, `N.sp` or `fontWeight = FontWeight.X` in shared UI outside
`ui/theme/` fails the run (the 45 sizes and 31 weights that were there folded onto the seven; 40
chrome sites at `bodyLarge` 16 became `body`). Measured after, beside Notion: section titles
12–14 px SemiBold against Notion's 15, the page title 18 against 33, descriptions 10 against
Notion's 12 body; the tree's row pitch unchanged at 33 px against Notion's 29–30 (recorded).

**A theme is ground × hue × mode; a register is a named preset of the first two.** Shared by both
platforms through `KeyValueStore` (`theme_register`, `theme_mode`, `theme_typeface`, `theme_oled` —
`ui/theme/ThemeSettings.kt`; the phone's old `tendril_theme_prefs` file is migrated once at start
and deleted, an explicit old mode kept, a never-touched one becoming System).

- **Grounds** (`ui/theme/Registers.kt`, `Ground`): *neutral* (`#FFFFFF` / `#1B1D21`, Ink's), *cold*
  (`#FBFBFA` / `#101216`, Console's), and Swiss's own cold pair (`#F4F6F8` / `#0E1018`). Never warm
  (B§13.7.1). Each ground carries a text *seed* — its near-black and near-white — that the solver
  starts from.
- **Registers** (`Register.ALL`, ten): Ink (neutral, slate), Console (violet), Swiss (navy + signal
  yellow as a second channel), Playground (teal + warm), Blush (pink), Chalk (sand), Kodachrome
  (rust), and — returned 2026-09-16 as registers, their old accents on the cold ground — **Clay,
  Moss, Mauve**. A stored old name (`CLAY`) resolves to its register. Adding one is a hue and a
  label; the solver does the rest.
- **Mode**: System / Light / Dark, **default System** (Android's night mode, Windows' app theme
  through `isSystemInDarkTheme()`). The recorded tri-state defect is closed: no explicit flag,
  System is a choice like the other two.
- **Typeface**: Inter (the default since the type PR, 2026-09-16) / DM Sans / Serif (Source Serif
  4), one family for everything at true 400 / 500 / 600; the scale carries **400, 500 and 600
  only** (`eyePassWeight`, B§13.7.3 rule 2 as amended) and there is no base-size control —
  density (§2.2) is the size lever.
- **Deeper blacks** (`theme_oled`, phone only): the register's dark ground at 4 % lightness, every
  token re-solved on it. The desktop never reads the key (B§13.7.3 rule 3).

**Every token is solved, not typed** (`paletteFor(register, dark, oled)`, `ColorSolve.kt`). The
floors, `Floors` in code:

| token | rule | floor |
|---|---|---|
| `text` | the seed pulled toward the ground until it reads in the **9.5–13:1 band**, stopping at ~12 | B§13.7.3 rule 2; the critique measured the mock's registers at 15–17.5 |
| `surface2` | text mixed **6 %** into the ground in both modes *(the design layer, 2026-09-18 — at 4 % the light hover measured ΔE 1.6, under the 2.0 JND)* | ΔE ≥ 2.0 on the ground, tested |
| `textDim` | the smallest share of text over the ground that clears the floor **on the ground and on `surface2`** | 4.6:1 (the mock solved on the ground only and dim read 4.28–4.51 on `surface2`) |
| `textFaint` | the same, both grounds | 3.05:1 |
| `accent` | the hue, darkened (light) or lightened (dark) **by lightness alone** until it clears — hue and saturation kept, so Clay stays clay | 8.0:1 light / 7.6:1 dark |
| `onAccent` | white on a light ground, the ground on a dark one, whichever clears | 4.6:1 |
| `accentSoft` / `accentSoftText` | accent 12 % / 22 % into the ground; its text solved on it | 4.6:1 |
| `border` | text 11 % / 14 % into the ground — a meaningless hairline; a hairline that means something takes `textFaint` | — |
| `third` / `thirdSoft` | the second channel, else the accent fanned 180°, solved to read as data — **one token** since the design layer (2026-09-18: `thirdStrong` was the same colour in 28 of 30 palettes) | 4.6:1 (Swiss's yellow measured 2.6 on the mock) |

`RegisterSolveTest` walks every register × mode × OLED — 30 palettes — against these floors on every
build, so the AA claim of this section is a property of the code. Ink is solved like the rest (its
dark accent measured 5.4 where the model asks 7.6; the "unchanged" exception is withdrawn). The
Material mapping (`Theme.kt`) covers the surface-container family too, so a dark register never
shows Material's own purple-grey through a menu; `tertiary` is 14g·2's to map.

**The section**: `ui/settings/ThemeSection.kt`, shared — the ten swatches (each the accent on its
own ground in the mode being shown, the name under, the register's *use* as a caption), Mode,
Typeface, and on the phone *Deeper blacks*; rendered inside the phone's collapsed-by-default
Appearance disclosure (kept: icon, summary "Ink · System · Sans", chevron) and in the desktop pane
between Density and *Opens on*. `docs/critiques/registers-mock.md` shaped the floors;
`registers-function.md` walked the build (the desktop's unpainted ground fixed — the scaffold now
sits every route on one `Surface`). The phone walked on the phone 2026-09-18 (`docs/critiques/phone-catch-up.md`, `phone-function-full.md`).

**Data colour** is B§13.8: urgency is a task's colour (a five-step ladder, `Entry.important`
becoming `importance` 0–4 — 14g·3, schema v20), category its database's or first label's hue, a
database any hue with a mandatory icon, calendar layers dots in Month and Agenda. ~~Links and
`@` mentions take the accent token and find gets its own mark with the token map (14g·2). The
recorded hardcoded-blue defect closes there.~~ *Built — the token map below.*

**The token map** *(Amended 2026-09-16 — 14g·2, B§13.8.3; decided on
`docs/critiques/coloured-elements-mock.md`, walked in `coloured-elements-function.md`).* Three
data hues come from the accent by one rule (`fanHue`, `ColorSolve.kt`): **event** at −120°,
**habit** at +60°, the **third** at 180° — the accent's hue turned, its saturation raised to at
least 0.40 (Ink's slate would fan to greys), solved by lightness to the floor — so every register's
data hues sit ≥ 60° from its accent and from each other; a register's second channel (Swiss's
yellow, Playground's orange) takes the third slot instead. The **error family** is a red at 5°
solved on the ground *and* on its own soft (the mock's chips measured 4.1–4.3 in dark). Every
tint is the hue at 14 % / 24 %; the **find mark** is the third at up to 30 % / 36 %, backed off
until the text reads on it. A **stored hue** — a label's `color`, a callout's `calloutColor`, hexes
that sync and never change — is rendered by the register (`ui/theme/DataColours.kt`): a label's
solved to 4.6 on the ground and on its own 14 % chip (`solveHue`'s `tintShare` — the check moves
inside the lightness loop because the tint moves with the candidate), a callout's to 3.0 as a mark;
saturation is never raised for a stored hue, so the grey pastel stays grey. What wears what:

| Element | Token |
|---|---|
| Selection, today, the now-line, the timer, a link (underlined), an `@mention` (Medium, on an `accentSoft` fill since 2026-09-16 — §3.1.1; this cell said "no fill" until 2026-09-24) | accent |
| Find marks · the current match | `findSoft` with the text · `third` with `onThird` |
| Quick add's recognised tokens, in the line (L7b) | `findSoft` with the text — the same *read* tint as a find mark, never selection's |

*(Amended 2026-09-18 — the phone's second fix PR, T·P1: **the Touch type scale.** The seven styles were cut against Notion's desktop; on a phone every ground sets its primary text at 16–17 sp and its second line at 14 (`docs/critiques/phone-type-full.md`). Under Touch each chrome role steps one size up the same scale — body 16 · description and label 14 · caption and eyebrow 12.5 · heading 16 · pageTitle 20 — and the editor's three stay; `touchTypography` remaps the theme's typography where the profile is known, so nothing else in the app names a size. The pointer profiles keep 14 / 12.5.)* *(Amended 2026-09-20 — **the pointer type scale**, the user's brief after the rail's labels: `docs/critiques/desktop-type-grounds.md`. Notion and the Claude app measured at 125 % set every piece of chrome text at one size — labels, values, chips, tabs, buttons, secondary lines, 10 px of cap height — and keep a single smaller size for section headers; hierarchy is weight and colour. Tendril's `label`, `description`, `caption` and `eyebrow` stood at 8. Under a pointer profile the four step one size up the same scale — label and description 14 (Medium, and Regular-dim, beside `body`), caption and eyebrow 12.5 — and body, heading, pageTitle and the editor's sizes hold (`pointerTypography`, the mirror of the Touch remap, provided the same way). Measured after: chips, labels and captions 9–10 beside the grounds' 10. **The size count**, decided the same day: two chrome sizes on the desktop — 14 and 12.5 — the bar's title at 16 / 600 (the editor's size; 18 was the one chrome size off the 1.125 scale), and text inside a scaled drawing **never under the smallest chrome size**: below `CANVAS_CONTENT_MIN_SCALE` = 12.5 ÷ 14 the canvas embed and the mind-map card draw their boxes bare and `ReadableLabels` writes each box's words over it at `caption`, clipped to the box (amended the same day from *readable or absent* — the user, on the build: a map's boxes say nothing without their words; `docs/critiques/desktop-type-grounds.md`). The desktop's page carries 12.5 · 14 · 16, with 20 · 24 for H2 / H1.)*

*(Amended 2026-09-18 — item 23's Lows, T·P3: **a line is its style's line height.** A `TextStyle` built without a `LineHeightStyle` trims its first and last lines to the font's ascent and descent, so a one-line `Text` measured ~1.21 em — 22 dp for `pageTitle` at 18, 24 at 20 — while a text field's line took the declared 24 / 26; one style, two heights, on both platforms. `chromeStyle` carries Material's `LineHeightStyle(Center, Trim.None)` and platform style, so every chrome text's box is its line height (body 20 · label and description 17 · caption 16 · pageTitle 24; under Touch 24 · 20 · 17 · 26); every measured pitch sits on a `heightIn(min)` floor and did not move. Tested.)*
| Block-reference bar, related edges, a canvas node's outline | third · `thirdSoft` |
| A database — the Road Map's fill and border, a database date's chip, dot and row on the calendar, its glyph in the tree, the cards and the rows; a canvas card's or frame's fill and border *(S13, 2026-09-21)* | its **hue** (`page_databases.hue` / `canvas_nodes.hue`, v24) solved by `hueColours` — 4.6 : 1 on the ground and on its 14 % tint; the default a database's title hashed ≥ 30° from the accent (`defaultDatabaseHue`); a card's *None* by default |
| Calendar event / habit blocks and all-day chips | `eventSoft` / `habitSoft`; task blocks `accentSoft` until 14g·3 |
| Canvas edges, mention edges, the Month's "something today" dot | dim |
| Blocked, overdue, delete, a Timeline dependency line | error / `errorSoft` |
| A label chip | its hue as text on its tint; solid with `onHue` when the filter is on |
| A callout | its hue as a 3 px left bar over a 20 % / 28 % tint, the register's text on it |
| A task's urgency (14g·3) | the **ladder** — four steps, low → urgent, one coral family (hue 10→4° light, 12→6° dark) with lightness solved to a contrast *target* on the ground: 3.0 / 5.0 / 7.9 / 13.0 light, 11.5 / 8.1 / 6.1 / 4.6 dark (pale → saturated, urgent the deepest); adjacent steps ≥ 10 ΔE; *none* is nothing on a row, dim in the legend (`Ladder`, `Registers.kt`) |

Material's roles follow: `tertiary*` = the third family, `error*` = the error family,
`secondaryContainer` = `surface2` (the calendar reads its layers from the palette, not from roles).
**The radius family** *(the design layer, 2026-09-18 — `desktop-design-layer.md` #7)*: **4 · 6 · 8 · 10 · 12** — 6 the workhorse (rows, fields, chips), 8 cards, 10 the switcher, the hover card and the F1 card, 12 sheets and dialogs; `TendrilShapes` sets Material's `extraSmall … extraLarge` to 4 · 6 · 10 · 12 · 12 so a dialog, the date picker or a bottom sheet never draws Material's 28; `tools/audit.py` rule 17 refuses any other literal radius in `ui/` (2 allowed for a stripe's ends). A required graphic is drawn at its token's full alpha — the Road Map's edges were at 0.6 and measured 2.3–2.5:1 on light grounds; de-emphasis is a thinner stroke or the dimmed set's own alpha, never a floor undone.
`tools/audit.py` fails on a `Color(0x…)` or a named `Color.Gray`-style literal in shared UI outside
`ui/theme/` — the nearest thing to a compiler check that the map stays whole. Tested over all 30
palettes: the fan's angles, every data hue and tint at its floor, the eight label hexes and the
seven callout pastels (`RegisterSolveTest`); a link and a mention carry the colour they are given
(`SpanTransformationTest`). Desktop walked; the phone walked on the phone 2026-09-18 (`docs/critiques/phone-catch-up.md`, `phone-function-full.md`).

### 2.4 Accessibility scope (Decided 2026-08-26)

No formal accessibility program is warranted for a personal, unlisted, single-user build, but the
"close to free if planned in from the start, expensive to retrofit" calculus that originally flagged
this item (§10) holds regardless of audience size. Four candidate scopes, scored against build-now
effort, retrofit-cost avoided, practical benefit to the actual person using the app, and scope-creep
risk (weights 0.30/0.30/0.25/0.15, creep inverted so lower creep scores higher):

| Scope | Build-now effort | Retrofit avoided | Practical benefit | Scope-creep (inverted) | Score |
|---|---|---|---|---|---|
| Contrast-only (status quo, nothing more) | 1.00 | 0.00 | 0.30 | 1.00 | 0.475 |
| Full WCAG (TalkBack labels + font-scale audit, every screen) | 0.40 | 1.00 | 0.60 | 0.30 | 0.585 |
| Defer to a post-v1 polish phase | 0.90 | 0.10 | 0.20 | 0.80 | 0.545 |
| **Compose-default + targeted labels (chosen)** | 0.75 | 0.85 | 0.55 | 0.80 | **0.7525** |

**Decided:** rely on Compose's free defaults everywhere (auto-generated semantics on standard
Material components, `sp`-based text that already respects the system font-scale setting), and spend
the small remaining effort only where Compose provides nothing automatically: an explicit
`contentDescription` on every icon-only control (the ⋯/gear/eye row, §2.2/§3.1.2), and on Road Map's
hand-rolled Canvas nodes (§3.4) and the Glance-based widget surfaces (§8), both of which sit
entirely outside Compose's semantic tree. Verifying Workbench's nav labels and Page-card text
survive a 200%-scale device font setting rides along with Phase 1's own edge-to-edge/`WindowInsets`
work (§9.2.1) rather than becoming a separate pass — both are "get it right in the initial layout or
pay for it later" concerns landing in the same phase anyway. A full formal audit (screen-reader
walkthroughs, automated scanner passes) has no real payoff for a build with one user and no store
listing, and stays out of scope.

**Acceptance:** every icon-only button has a non-null `contentDescription`; Road Map node labels and
widget text are exposed via explicit `semantics {}` blocks where Canvas/Glance don't supply them
automatically; Workbench's nav bar and Page-card text visibly survive a 200% system font-scale
setting without truncation or overlap.

### 2.5 First-run / empty states (Decided 2026-08-26)

Four candidates, scored against build effort, cross-tab consistency (one composable reusable across
Pages/Calendar/Tasks/Habits/Road Map/Trash), actual usefulness to a user who already knows the app
intimately (there is exactly one, and it's the person who wrote this spec), and visual coherence
with Workbench's existing card-forward aesthetic (§2.2) (weights 0.30/0.30/0.25/0.15):

| Option | Effort | Consistency | Usefulness (this user) | Aesthetic fit | Score |
|---|---|---|---|---|---|
| Plain blank text ("No pages yet") | 0.90 | 0.60 | 0.50 | 0.20 | 0.615 |
| Guided onboarding wizard (multi-step tour) | 0.15 | 0.50 | 0.30 | 0.60 | 0.3675 |
| Pre-seeded sample content per tab | 0.50 | 0.40 | 0.55 | 0.60 | 0.4975 |
| **Contextual empty-state card: icon + one line + one primary CTA (chosen)** | 0.75 | 0.90 | 0.80 | 0.80 | **0.8075** |

**Decided:** one reusable `EmptyState(icon, message, ctaLabel, onCta)` composable, used wherever a
list-shaped screen in §3 can be empty — Pages ("No pages yet — New page"), Tasks ("Nothing due — Add
a task"), Habits ("No habits yet — Add a habit"), Road Map ("Nothing to map yet — mention another
page to get started"), Trash ("Trash is empty"). ~~Calendar needs no bespoke empty state, since Day
view always renders a date grid regardless of content.~~ *(**Corrected 2026-09-06:** the premise was
wrong and the conclusion followed it out. Day view is a header, a quick-add field and a list of
occurrences — there is no grid in it at all; the only grid on that screen is Month's. So an empty day
is genuinely blank, and Day view does render an empty state, with copy this section never fixed
("Nothing scheduled") on a chevron icon borrowed from the next-day button. Calendar belongs in the
list above, and its copy and icon should be settled here like every other tab's — which is exactly
what this section's own Acceptance clause requires.)* No onboarding wizard and no seeded sample
content: this is a single-developer, single-user build — a walkthrough only its own author would
ever see is pure sunk cost, and seed content is one more thing to delete rather than a genuine aid.

**Acceptance:** every list-shaped screen in §3 renders through the shared `EmptyState` composable
rather than a bespoke per-screen layout; each tab's copy and CTA are fixed here, not improvised ad
hoc mid-build.

---

## 3. Page-by-Page Functional Spec

**Bottom sheets (Decided 2026-09-12).** Every sheet is `ui/components/TendrilSheet` — one frame:
20 dp sides, the title in one style, a bottom room proportional to the window (3 %, 24–48 dp,
so 24 dp on a phone) above the system inset, and **always fully expanded** — Material's
half-expanded state was §0.10 item 11 on the desktop and, found on the phone the same day, the
Postpone and Reminders sheets opening with their buttons under the navigation bar until dragged
up; every sheet here is content-sized, so the partial state buys nothing. No site calls `ModalBottomSheet`; `grep` is the check. Two things
were considered and refused: sheets rising from *above* the tab bar (a modal sheet takes the
screen — Material 3 and every benchmarked app; a visible tab bar under a scrim is a question
with no good answer), and a decorative line along the bottom (chrome for its own sake, §0.5;
the drag handle is the sheet's affordance). What read as "stark" was content touching the edge,
and no two sheets alike — the frame is the fix. *(**Amended 2026-09-14 — B§13.4 14b.** On a wide
window the same frame is a **right slide-over**, drawn as `docs/mockups/desktop-shell.html`
draws it: a scrim over the window — **black at 24 %** (`Scrim`, `ui/theme/Palette.kt`) since 2026-09-21; it had been the text colour at 32 %, Material's share on Material's black scrim, which on a dark ground lightened the page and left the panel darker than what it covered; every ground darkens (Notion #0F0F0F at 30 % dark / 60 % light, Obsidian #0A0A0A at 40 %, Material black at 32 %, read from their bundles), and the share is the light ground's, where it does the work (the page's text 7.9 : 1 through it), a dark ground barely darkening and the panel's separation there being its shadow's; the centred card shares it, the phone's bottom sheet keeps Material's own (`docs/mockups/scrim.html`, `docs/critiques/scrim-mock.md`) — a 440 dp panel flush right and
full height (never more than three fifths of the window), a soft shadow to its left, a header
with the title and a close × — none when the sheet draws its own first row, as History and the
Trash sheets do — and a body of bounded height with no scroll of its own, as the bottom sheet's
column has none, so a sheet that is a `LazyColumn` measures against a real height. Scrim click,
Escape and Back close it. "Wide" is the shell's own breakpoint — `sheetFormFor` is one line over
`shellLayoutFor`, 840 dp of window width, tested so the rail and the slide-over cannot switch at
different widths — so a phone keeps the bottom sheet and a tablet in landscape gets the panel.
`TendrilSheet`'s signature did not change: the 33 sites that open a sheet never choose its form.
What stays what it was, and why: `DropdownMenu`s are already anchored popovers, `DatePickerDialog`
and `AlertDialog` already centred dialogs — the decision was against turning *sheets* into
centred dialogs (the page stays readable beside a slide-over; a sheet's content already expects
height), not against dialogs as such. `SlideOver` is called from `TendrilSheet` and nowhere
else — `grep` is the check, as for `ModalBottomSheet`.)*

*(Amended 2026-09-18 — the phone's fixes, P2 and P3: both frames **scroll** — with the keyboard up a phone's sheet is squeezed and a plain column squashed its rows — unless the sheet's content is itself a lazy list, which passes `scrolls = false` and keeps the bounded height a `LazyColumn` needs; `tools/audit.py` rule 20 *sheet scroll* pairs the two. Under Touch a submenu **pushes a level** into its menu — a back row with the item's text, then the rows — instead of opening beside it, where a 360 dp window has no room (Notion's phone sheet pushes *Duplica ›* the same way); under a pointer it stays beside. `docs/critiques/phone-fixes-measured.md`. The second fix PR (T·P2, L·P3): a `TendrilMenuItem` is the app's row on both profiles — `body` text, 12 dp sides, the 48 dp target under Touch; the bar's pills, menu buttons and pane tabs stand 40 dp under Touch (`barControlHeight`), 28 under a pointer; the find bar 56 / 48 under Touch.)*

### 3.1 Pages (Notion-like)

Capable of creating many pages, projects, and databases without burying quick settings, save/sync
status, or import/export behind menus. Must handle a large personal knowledge base without becoming
visually noisy.

*(**Amended 2026-09-15 — B§13.4 14c, the tab on a wide window.** From 840 dp (the shell's own
rule) the Pages tab is two panes: the **tree** at 280 dp beside the **open page**. The chrome is
split the way Obsidian and Bear split it, chosen on `docs/mockups/pages-two-panes.html` (option B
of three; A was Apple Notes' single segmented bar, C Things' sidebar footer): what acts on *pages*
— the name, New, search, the journal, the collapse chevron — sits on the tree's 44 dp header; what
acts on *this page* — its title, View-Only, its `···` with *Trash…* appended — sits on the page's
own bar, which loses its back arrow because the tree is beside it (`PaneChrome`, handed to the
three detail screens; null on the phone). No tab bar over the page, so the "Pages" title over a
"PAGES" header that the first mock had is gone. Collapsed — the chevron or **Ctrl+\** — the
page bar's leading slot takes the expand chevron and the tree's search and journal. The tree: 32 dp
rows, the current page on `accentSoft`, a chevron on any page with live children
(`PageDao.observeParentsWithChildren`; today Journal's days) expanding one level per click, the
label chips above; a 12 dp handle astride the divider resizes it, 200…480 dp, kept in
`pages_tree_width`, the collapsed flag in `pages_tree_collapsed`, expansions for the session only
(`PagesTreeState`). A tree click *replaces* the open page (`WorkbenchNavState.showPage`), so the
stack never grows by browsing and Escape closes to the tab root; a link inside a page still
*pushes* (`openPage`), so Escape returns where it came from. At the tab root the right pane is a
quiet line, never a stale page; it still wears View-Only and the Trash. The phone's tab —
`PagesScreen` over the same `PagesHost` — is what it was, to the pixel (0.06 % of sampled pixels
differ against the previous build, the clock). Not yet: `···` on a row and hover-revealed
controls (14d), keyboard movement in the tree (14e).)*

*(**Amended 2026-09-16 — B§13.4 14h·1, the shelf.** On a wide window a **third pane** can stand
at the workspace's right edge beside the open page, holding one of three things (decided
2026-09-16 on `docs/mockups/shelf.html`, drawn for the pass, and its critique
`docs/critiques/shelf-mock.md`): **another page**, **this page's Road Map neighbourhood**, or
**today's Journal**. Opened from the page's `···` → *Show beside ▸* (*Road Map around this page* ·
*Today's Journal* — the item is the workspace chrome's, so every kind of page has it and the
phone never does), from a tree row's menu (*Open beside*) or a **Ctrl+click** on the row; closed
on its `×` or **Ctrl+Shift+\** (which also reopens the last one — the tree's chord with Shift).
**One header rule** (the critique's #1): a page in the shelf uses its *own* bar as the header —
the kind's glyph where the back arrow would be, `⇄` and `×` before its `···`, the title at
`titleMedium` (`PaneChrome.compact`) — so three panes share one bar height and one hairline; only
the graph, which has no bar, gets one drawn at the same height with the depth as three 28 dp
targets and *Open in Road Map*. **The shelf's page is editable** — the same `PageRoute` — and a
link inside it opens *in the shelf* (a stack of one, never the main stack); Ctrl+F and Escape
stay the main pane's. **`⇄` swaps** the two panes (a swapped-out Journal becomes that day's page).
**A page never sits beside itself**: while the shelf's page is the open page the shelf shows its
graph instead, and the stored content is untouched — a pin, not a redirect. **The graph**
(`RoadMapNeighbourhood`) is the map's canvas (`RoadMapCanvas`, shared, a 120 dp node) over the
*full* graph's `around(page, depth)` — not the map's filtered one, so a Journal page's
neighbourhood is never empty for the Journal filter — on its own ViewModel instance; the map's
gestures plus one: a click focuses, a click on the focused node opens it in the shelf, a
double-click in the main pane. **Today's Journal** is stored as the kind, not a page id
(`pages_shelf=journal`), resolved each day through the Journal button's create-or-find (refused
under View-Only on a day with no page: the button's message, the shelf closes). **Width** 380 dp,
280…560 (`pages_shelf_width`), the tree's handle mirrored on the left edge, and **clamped to 45 %
of the workspace** at draw time so the main pane keeps ≥ 360 dp (the critique's #3). **Remembered**:
`pages_shelf` (`page:<id>` · `graph` · `journal`), `pages_shelf_last`; hidden at the tab root
(`ShelfState.shown`). The phone keeps its stack. `docs/critiques/shelf-function.md` walked the
build.)* *(14h·2: the tree marks the shelf's page too — a 1 dp ring and the split glyph at the
row's end, no fill; the chip row above the tree sits with 12 dp of visible air above and below
and starts on the row's content edge. The phone's card says *edited 2 h ago* — `domain/time/
RelativeTime.kt`, shared with the History sheet — instead of the kind its icon already shows.)*

*(**Amended 2026-09-16 — B§13.6 #6, pop-out windows.** On the desktop a page can be **its own OS
window** (decided 2026-09-16 on `docs/mockups/pop-out.html`, drawn for the pass, and
`docs/critiques/pop-out-mock.md`): the same `PageRoute` — a page, a database's Table, a canvas —
with its own back stack, its own `···`, the theme, and **the main window's scale** (`shellScaleFor`
of the main window's shorter side, not its own, so 14 sp means the same in both). No rail, tree,
timer, switcher or shortcuts card: a second window holds one thing beside the first. **Opened**
from the page's `···` → *Open in a window* (the workspace chrome's, so every kind has it and the
phone never does), the tree row's menu, or **Shift+click** on a row (Ctrl+click is the shelf's);
the page stays open in the main window. **Inside it** a link pushes, Escape pops and **stops at
the seed** (a placed window is never closed by a stray key); Ctrl+F is its own find; Ctrl+W and
the OS × close it; Alt+← / → walk its stack; every other chord is the main window's (the F1 card
lists Ctrl+W as a static row). Its `···` ends with *Open in the main window* and *Close window*;
that verb and *Show on Road Map* act in the main window, **front it**, and close the pop-out. The
OS title mirrors the page on top of its stack as it is typed. A page trashed anywhere closes its
window. **Remembered**: `popout_pages` (ids, in order) and `popout_frame_<id>`; a relaunch reopens
them where they were, dropping ids whose pages are gone; a new one opens 720 × 600 (minimum
480 × 400 — the editor's floor) cascaded 32 dp from the last. Two windows on one page are two
ViewModels over one database (`WorkbenchEnvironment`, `PopOutHost`, `PopOuts.kt` shared;
`PopOutWindows.kt` on the desktop). The tree marks a popped-out page with ⧉.
`docs/critiques/pop-out-function.md` walked the build — and records the day's process finding:
an installed 2026-09-08 preview `Tendril.exe` shares the dev data directory and wiped it to its
v8 schema when launched by name (recovered from the last checkpoint).)*

- Import from Notion (Markdown & CSV export format — see §7 for the full fidelity spec)
- Full import/export of pages and projects
- Page cards show icon (page vs. database vs. Canvas — *three kinds since Canvas shipped (§3.4);
  recorded here 2026-09-06*), title, and metadata; horizontal layout per §2.2

**Databases and to-do behaviour** — see §5 for the full design (row-as-page, Sync-to-Tasks
mechanism, property bindings).

### 3.1.1 Block editor scope (Notion-lite — Decided 2026-07-13)

Never scoped before this pass, not even at the UI level — the single largest undefined piece of the
app going into §9.9. Scoped now to **Notion-lite**: matches everything §7.2 says a real Notion
export can carry without degrading, plus page-linking (which Road Map, §3.4, depends on) —
deliberately short of full Notion parity (no embeds, no inline databases, no nested-page-within-page
canvases), which would be a multi-month subsystem on its own and isn't needed for a personal build.

**Block type inventory (v1):**
- Paragraph
- Heading 1 / 2 / 3
- Bulleted list item, numbered list item ~~(nestable one level via indent — matches typical
  personal-notes depth, not arbitrary nesting)~~ *(**Corrected 2026-09-11 (§0.6.1):** nestable to
  any depth. Every block type nests, not only list items; the outliner rule applies — indent puts
  a block under its previous sibling, outdent lifts it one level and adopts the siblings after it.)*
- To-do (checkbox) — a **block-level checkbox for freeform checklist text inside a page**,
  structurally distinct from the Property-level Done checkbox that drives Sync-to-Tasks (§5.2).
  Checking one here never creates a Task.
- Quote
- Code block (with a language tag; ~~no syntax highlighting required for v1 — a nice-to-have, not
  needed for import fidelity~~ *[Amended] 2026-09-22 — **highlighted**: `domain/code/Highlight.kt`, a
  tokenizer of four kinds — keyword, string, comment, number — with a grammar per language for
  Kotlin, Python, JavaScript / TypeScript, JSON, Bash, SQL and Markdown, a family's comment shape
  for the C-like and `#` languages, and strings-and-numbers alone for any other tag; painted through
  the span transformation from the token map — keyword `third`, string `event`, number `habit`,
  comment `textDim` — so every ink is a solved data hue, none the accent (measured 6.7–9.8 : 1 on
  Ink dark). No library: none is in the offline cache and four kinds need none. Kotlin's soft
  keywords that double as identifiers stay plain; `docs/critiques/syntax-highlighting-function.md`*)
- Divider
- Image (local file, copied into app-private storage on insert — kept out of the SAF-synced snapshot
  folder, §9.4, to keep the sync payload small; worth confirming this split is acceptable)
  *(**Status corrected 2026-09-06 — only half of this exists.** `Block.imagePath` has exactly one
  writer, the Notion importer, which does copy into app-private storage as specified, and the
  snapshot exclusion is real and covered by a test. The editor can neither insert nor draw one: the
  slash menu offers "Image", and the block that results renders as an empty editable text line,
  because no composable anywhere reads `imagePath`. So an imported picture is invisible in the app
  that stores it, and §5.6's Gallery cover — which picks exactly the right block — has nothing to
  paint. The open question above stays open, and is **not** answered by the merge work of 2026-09-05:
  holding `imagePath` by uid across a rebuild protects *this* device's path precisely because the
  snapshot never carries one, so a picture still does not reach a second device at all.)*
- Toggle (actually collapses, unlike §7.2's degraded-on-import case, which is permanently open)
- Callout (icon + colored background — natively rendered; an *imported* callout starts as a raw-HTML
  fallback block, per §7.2, until manually converted) *(**14g·2, 2026-09-16:** the stored
  `calloutColor` is one of seven hues (`CALLOUT_COLORS`, `ui/theme/DataColours.kt`) and is rendered
  by the register — the hue as a 3 px left bar over a 20 % tint the register's text reads on, §2.3
  *The token map*; the seven picker dots show the solved hues.)* *(**Two corrections, both 2026-09-06.** The
  icon renders; the coloured background does not. `Block.calloutColor` exists in the schema and
  travels in the snapshot, but nothing writes it and no composable reads it, so a callout is visually
  a paragraph with an emoji in front of it — a rendering gap, not a data-model decision to reopen,
  which is why the intended design is left standing here rather than struck. And an imported callout
  no longer starts as raw HTML: the parser matches Notion's `<aside>` export structurally and emits a
  native callout with its leading emoji lifted into `calloutIcon`, which is §7.3.3's "nice to have"
  having actually been built — see §7.4's correction of the same date. The raw-HTML fallback still
  exists and still catches every *other* embedded HTML line.)*
- Page mention (`@Page Title`) — inline reference to another Page; the primary source of Road Map's
  edges (§3.4)

**Data model:** a `Block` entity per row — `id, page_id, type, order, parent_block_id (nullable, for
list/toggle nesting), content (typed per block type), created/updated` *(2026-09-13: and
`referenced_block_uid`, nullable, for the BLOCK_REFERENCE block — §0.6.12)*. Inline formatting
(bold/italic/strikethrough/inline code/links/page-mentions) is stored as `(start, end, style)` spans
over a block's plain-text content rather than embedded markup — keeps FTS indexing (below) simple,
since the indexed text is just the plain content with spans stripped.

**Interaction model:** a slash-command menu (`/`) to insert any block type at the cursor; ~~a drag
handle on hover/long-press to reorder~~; a ~~floating~~ selection toolbar for inline formatting on
text selection. *(**Corrected 2026-09-06 — two divergences in one sentence.** Reordering shipped as a
long-press action sheet with explicit **Move up** / **Move down**, not a drag handle. That is
defensible on a phone, where a drag handle inside a scrolling column of editable text fields fights
the text-selection gesture for the same long-press, and it is the same call the Board view made for
the same reason (§5.6) — but it is a different interaction from the one specified here, and this
section should say which one a reader will actually find. The formatting toolbar is not floating
either: it renders inline, beneath the block being edited and inside that block's own column, rather
than as a popup over the page.)* *(**Amended 2026-09-15 — 14d.** Two homes now: floating above the
block under a pointer profile, inline under Touch — §2.2 *Under a pointer*.)* *(**Amended
2026-09-16 — B§13.6 #3, hover previews.** Hold the pointer on an inline `@mention`, a mention
block, a block reference or a Road Map node (§3.4) for **500 ms** and a card shows what it points
at — Obsidian's page preview and Notion's, without the modifier key; decided 2026-09-16 on
`docs/mockups/hover-preview.html` and `docs/critiques/hover-preview-mock.md`. The card
(`ui/components/HoverPreview.kt`, 320 dp): the kind's glyph, the title, *edited 2 h ago* in
`onSurfaceVariant` (the critique's #1 — text, never the faint token), then the page's **lines**
from `domain/preview/PagePreview.kt` — one per block in the outline's order, a heading Medium, a
to-do as ☐/☑, a mind map's root with its subtree as *Mind map · n nodes*, a canvas block as
*Canvas*, a mention as *→ title*, dividers and pictures skipped, each cut at 90 characters, a
multi-line block joined with a middle dot — **up to six, as tall as its lines**, and a footer only
when blocks were left out (*3 more blocks · click to open*). **A block reference previews its
line in context**: the source page's title, the referenced line marked on the third hue's tint
with the reference bar's own 3 dp mark, the line before and the line after when they exist (the
benchmark's "better than theirs"). A database previews *Table · 3 rows · Read on, Author, Done*
and its first row titles; a canvas *Canvas · 4 cards · 2 links* and its first cards. Placement:
under the target's line with 8 dp of air, flipped above near the window's foot, clamped to the
window's width, never over the target; a non-focusable popup, so typing continues. The wait is
cancelled by a leave, a press, a scroll or **a keystroke** (the person is writing, not reading);
the card stays while the pointer is on it and hides 150 ms after it leaves; a click on it opens
the page (Ctrl → beside, Shift → in a window, where the workspace offers those). The hovered
span wears a 1.5 dp accent outline only while the card is up; the block cards wear nothing (their
lift is their hover state). One state per screen — a shelf page, a pop-out and the map each their
own. **Desktop only** by `LocalDensityProfile.pointer`: nothing composes under Touch. No schema.
`docs/critiques/hover-preview-function.md` walked the build.)* *(**Amended
2026-09-16 — §0.10 item 19, find in page.** A 44 dp bar under the page's bar on both platforms —
option A of `docs/mockups/find-in-page.html`, the browsers' and Bear's home; B's floating card
measured as covering the first line and the `···` (`docs/critiques/find-in-page-mock.md`) — with a
full-width field, the count (*2 of 3*, *No matches* in the count's own colour), ↑ ↓ ×. Opened
from `···` → *Find in page* on both platforms and by **Ctrl+F** on the desktop (`Shortcuts.kt`'s
table, the F1 card; `WorkbenchNavState.findRequested`, the third cross-tab intent and the first
that changes no route — read through `ui/nav/FindRequestGate.kt` since 2026-09-17, so a page opened after a Ctrl+F does not inherit the bar); a selection in a block seeds the query. **The blocks' text only**
(decided 2026-09-16): every occurrence in outline order, case-insensitive, `domain/find/
FindInPage.kt`; not the title, which is on screen, not a row's property values, which the
Table's filter owns; dividers, images, mention and canvas blocks skipped, a block reference's
cached text counted. ↵ next, Shift+↵ previous, wrapping; Esc closes through a `BackHandler`
armed only while open (the 14e rule) and leaves the page on the current match. The marks are
drawn **inside the text** by `spansVisualTransformation` (`FindMarks`), so a match in bold is
bold and marked and the marks follow edits; the marks wear `accentSoft` and the current one
`accent` — a tint shared with selection until 14g's registers give find its own token. A
Database or Canvas page ignores Ctrl+F. The phone half walked on the phone 2026-09-18 (`docs/critiques/phone-catch-up.md`, `phone-function-full.md`); `docs/critiques/find-in-page-function.md`.)* *(**Amended 2026-09-16 — 14h·2.** The
matches run over the **whole outline**; the ones inside a folded mind map are counted apart —
*1 of 3 · 2 inside a mind map* — and ↵ past the last visible match **unfolds that map for the
session** (`domain/MindMapFold.kt`: `mappedDescendants(outline, opened)`, `owningMap`; state in
the screen, never a write), so the next ↵ lands on it. The bar scrolls only when the match's row
is not wholly on screen. An `@mention` takes `accentSoft` behind its accent text (decided
2026-09-16 — the find mark is the third hue's tint now, so nothing collides; 14g·2's #7 reversed).
A click on the page's ground — beside or below the blocks — clears the block focus.)*

*(**Amended 2026-09-19 — §0.10 item 19, the block selection and the block undo;** `docs/mockups/block-selection.html`, `docs/critiques/block-selection-mock.md`, `block-selection-function.md`.) The editor is one field per block, so beside the text selection there is a **block selection**: a set of block ids closed under the outline's subtrees (a parent brings its children), an anchor and a focus. Two ways in — under a pointer, Esc from a field selects its block, Shift+↑↓ and Shift+click extend a contiguous run in reading order, Ctrl+click toggles, Ctrl+A twice selects every block, and a press on the page's ground dragged across rows draws a **marquee** that selects what it crosses among the rows laid out (it does not scroll — Shift+↓ reaches what it cannot); under Touch a **long press on a block's margin** selects it *(the margin 24 dp under Touch since 2026-09-21 — S11: 32 dp before a paragraph's text, the option that meets none of the field's gestures)*, a tap on another toggles it, and the page bar becomes *n selected* with ×, Copy, Delete and `···`. A selected row wears `accentSoft` under a 2 dp accent ring — the tint alone measured 1.55 / 1.21:1 on the two grounds, so the ring is the selection and the soft the family it shares with the text selection (`block-selection-mock.md` #1). The run takes **Delete** (Backspace too), **Ctrl+C / X / V / D** — a copy keeps types, spans and nesting and puts the Markdown on the system clipboard; a paste mints new rows after the current block and selects them — a **group drag** under a pointer (the ghost names the first line and the count, an insertion line follows the pointer between rows, the run lands as siblings of the block it follows), and the **block action sheet** titled by its count (Move up/down, Indent/Outdent, Turn into over the run; Cut and Paste below on the phone; a map, a language, a colour for one block only). The count is of rows on screen; a folded subtree comes along with its map block. Not behind View-Only for a copy (a read); every write refuses under the lock as every write does. **The block undo** (`domain/blocks/BlockUndo.kt`): each block operation — delete, move, indent, outdent, turn into, cut, paste — is one entry holding the affected rows before and after, and undo writes the *before* back through the same DAO calls (a deleted block returns with its own id, so its children still point at it; Room keeps a given id); redo is the entry the other way; fifty per page, the session's, a new edit forgets the redo branch. Ctrl+Z / Ctrl+Y with no field focused reach it; a field keeps the platform's own undo; the snackbar *n blocks deleted · Undo* is the same entry, the phone's way back. Page history (§0.6.13) stays the ten-minute net under all of it. `domain/blocks/BlockSelection.kt` (the run, the subtree rule, the marquee's hits, the steps — tested), `ui/pages/BlockGround.kt` (the marquee and the drag on the Initial pass, so the list never scrolls under them and a press that never moves is left to the field).*

*(Amended 2026-09-18 — item 23's Lows, F·P2: a block's field writes only when its **text** changed. A long-press or a double-click selects a word and the field reports the same text with a new selection; writing it had captured a History revision, re-stamped the page and rebuilt its FTS row, on both platforms.)*

**Full-text search (confirmed necessary):** a Room FTS4/5 virtual table indexing each page's
concatenated block plain-text, rebuilt on block write. ~~Page-level granularity for v1, not per-block
— a search hit opens the page ~~and scrolls/highlights the first match~~; per-block result
granularity is a reasonable later refinement (§10) if page-level feels too coarse.~~ *[Amended]
2026-09-22 — **per block** (v25): `block_fts`, one FTS4 row per block with text beside `page_fts`
(kept for the title + body row the unlinked-mentions search reads), rebuilt with the page's row
and healed on the first launch; the switcher's body hits are block hits — the block's own snippet,
at most three rows a page, none for a page already listed by its title — and a hit **opens the
page with the find bar on the query and the cursor on that block** (`WorkbenchNavState.openBlock`,
`BlockJump`, the bar's own marks and scroll), so the struck promise below is kept.
`docs/critiques/block-fts-function.md`.* *(**Corrected
2026-09-06:** it opens the page at the top. The highlight half of that promise went somewhere real —
the search overlay highlights the match inside its own result snippet — but nothing scrolls, and
nothing can yet: opening a page carries a page id and has no slot for a block id to scroll to.
§3.1.7 restates the same sentence and is corrected with it.)*

*(**Also recorded 2026-09-06, because a reader would predict the opposite.** "Each page's
concatenated block plain-text" is literally all that is indexed — **the title is not a searchable
column.** A page called "Mortgage" whose body never says the word cannot be found by typing it, and a
page with no blocks at all has no index row and cannot be found by any query; the title is joined
back in only to draw the result row. Results are also capped at fifty. None of that was decided by
anyone — it is simply what indexing block text alone produces — and it is the first thing to look at
if search ever feels broken.)*

**Decided (2026-07-13, via Pareto trade-off analysis)** — three objectives that genuinely don't
collapse into one score: build effort (lower better), feature completeness for daily personal use
(higher better), and Notion-import fidelity (higher better, per §7.2's fidelity table). None of the
six candidates below is dominated on all three axes at once, confirming this is a real frontier, not
a false choice:

| Scope | Effort (cheap→expensive) | Completeness | Import fidelity |
|---|---|---|---|
| Minimal (no toggle/callout/mention) | 0.85 | 0.45 | 0.55 |
| + toggle/callout, no mentions | 0.75 | 0.60 | 0.75 |
| Notion-lite − page mentions | 0.70 | 0.65 | 0.85 |
| **Notion-lite** (chosen) | 0.60 | 0.80 | 0.90 |
| Full-parity − nested-page canvases | 0.35 | 0.90 | 0.95 |
| Full-parity | 0.15 | 1.00 | 0.95 |

Notion-lite sits at the frontier's knee: fidelity and completeness are already within striking
distance of Full-parity (0.90/0.80 vs. 0.95/1.00), but every step past it buys a shrinking fidelity
gain for a much steeper effort cost — Full-parity roughly quadruples the effort for a completeness
gain that mostly serves embeds/inline-databases/nested-page canvases, none of which came up anywhere
else in this spec as an actual need. Going the other direction (Minimal) saves effort but visibly
degrades import fidelity (toggles/callouts don't survive round-trip per §7.2) and blocks Road Map's
edge source (§3.4) entirely, for a personal knowledge base explicitly meant to hold "a large" one
(§3.1). Committing to **Notion-lite** as specified above.

### 3.1.2 View-Only lock and checkbox-only mode (Decided 2026-07-15, corrects and replaces the Editing/Viewing draft model below; Implemented 2026-08-30)

**Correction, stated plainly rather than silently overwritten** (same practice as the Task/Event
rename): an earlier version of this section proposed a whole-page Editing/Viewing split with
in-memory drafts, a Save/Cancel pair, and whole-session revert, modeled on preventing misclick edits
the way Notion itself can't. Working through the actual placement of the "eye" toggle surfaced that
the real intent was narrower and simpler than that — a per-page lock, not a whole parallel
editing-session apparatus. The Editing/Viewing/draft/Save/Cancel/whole-session-revert design is
withdrawn in favor of the model below. **Pages remain instant-write, matching every other
interaction in the app** (checkboxes, themes, toggles) — the existing §5.5 confirm/cancel dialogs
(property type conversion, property/row deletion, sync toggle-off) remain the only safety net for
destructive actions, with no separate draft/session layer wrapping them. Applies to every Page,
including Rows and Databases (both are Pages, §5.1).

- **View-Only lock** — one global toggle, not per-page: an eye icon in the **Pages hub's own
  topbar**, alongside its existing `search` and `···` "more" icons (confirmed against the real
  prototype's `iconBtn` row — this placement, not a per-page one, is what "an eye button... to the
  left of the dots" referred to). When on, every page under Pages becomes read-only **as a group** —
  checkboxes and toggles included, no per-page exception, no selective view mode. This replaces the
  earlier per-page View-Only concept entirely.
- **Checkbox-only mode** — separate, per-page, opt-in, orthogonal to View-Only. Available on any
  page containing block-level checkboxes not linked to a Task (§3.1.1's to-do block), independent of
  whether that same page also has Task-linked checkboxes. Turning it on for a page means: only that
  page's unlinked checkboxes stay tappable, nothing else can be edited; the page survives the
  lockscreen while it's the active foreground screen (`Activity.setShowWhenLocked(true)` +
  `setTurnScreenOn(true)`, the mechanism Catima uses for its card-display screens — not reachable
  fresh from a cold/locked state, only stays visible if the phone locks while the page is already
  open); it reverts automatically the moment the person navigates away from that page or closes the
  app, a natural consequence of the Activity lifecycle rather than a special case to build; turning
  it back off requires a full device unlock.
- **View-Only overrides checkbox-only.** If View-Only is on, no page is interactive regardless of
  its own checkbox-only setting — "lock all pages as just views" is the absolute case.
- **No separate accidental-activation gate beyond the toggle-and-confirm already required.** Opening
  the app, finding the specific page, and toggling checkbox-only on plus confirming is judged
  sufficient friction on its own — no added interstitial screen. The confirm dialog's copy states
  the concrete consequence in plain words each time ("this page will show over your lock screen
  while this is on"), so the trade-off stays visible rather than fading into muscle memory, without
  adding a step.
- **No new Android permissions.** `setShowWhenLocked`/`setTurnScreenOn` are plain Activity-level API
  calls, not a runtime-permission-gated capability — §9.7's permission list is unaffected.
- **Architecture note for Phase 5 (§9.9).** Tendril is expected to be single-Activity (standard for a
  Compose app), and these lock-screen flags are window-level, not per-screen — so the app's own
  navigation logic must gate exactly when they're active (checkbox-only is on, and this specific
  page is the one currently showing), clearing them the instant navigation moves away. Worth testing
  deliberately that a mistimed transition can never show the wrong page's content over the lock
  screen, even for one frame.
- **Reopened by this correction, not yet answered: Page sync timing.** The withdrawn draft model was
  what gave Page edits a clean "nothing syncs until Save" rule (§9.4). With Pages back to
  instant-write, that rule no longer has a natural anchor point, and needs a fresh answer — see the
  note in §9.4.

**Implementation note (2026-08-30):** built against a gap found while auditing Phase 5's actual state
— the eye-toggle and its `viewOnly` flag already existed in `PagesViewModel`, but were never reaching
`PageDetailScreen`/`PageDatabaseScreen` (each gets its own separate ViewModel instance per page
opened), so toggling View-Only was a no-op past the Pages hub itself. Fixed by hoisting the flag into
a plain `ViewLockState` held on `AppContainer` (session-scoped, matching the original "not data worth
persisting" call) rather than duplicating it per-ViewModel, threaded through the UI via a
`LocalViewOnly` CompositionLocal provided once in `WorkbenchScaffold` rather than as an explicit
parameter on every one of the dozens of leaf composables that needed to react to it. Enforcement is
defense-in-depth: ~~every mutating ViewModel function also early-returns through the same flag (a
`locked()`/`contentLocked()` guard), not just the UI disabling the affordance, so a missed UI gate
can't silently let an edit through.~~

*(**Corrected 2026-09-06 — this is true of two ViewModels out of three, and the third is the one that
deletes. View-Only does not actually prevent editing.** `PageDetailViewModel` and
`PageDatabaseViewModel` carry the guard at eleven and eighteen call sites respectively *(**both
figures corrected 2026-09-07** — the originals counted each `private fun` definition as though it
were a call, and "throughout" was wrong besides: `PageDatabaseViewModel.ensureDefaultView` carries
no gate. That one is deliberate — it is idempotent repair-on-open — but a deliberate exemption is
not "throughout," and this sentence is what an implementer copies to learn the pattern)*.
`PagesViewModel` carries none at all — not on `deleteForever`, not on `createBlankPage`,
`createCanvas`, `createDatabase`, `createFromTemplate` or `openJournal` — and takes `ViewLockState`
only in order to publish the toggle. The UI gate is missed in the same place: the Pages hub's `···`
button, which opens Trash, and its Journal button both sit outside the `if (!viewOnly)` that
correctly hides the FAB beside them. Inside the Trash sheet, **Restore** writes straight to `PageDao`
from the composable, bypassing every ViewModel, and **Delete forever** goes through the unguarded
`PagesViewModel.deleteForever`. Said plainly, because that is what this document is for: with
View-Only on, a person can open Trash and permanently destroy pages, and can still create today's
journal page. The app says it is read-only and it is not.*

*This bullet is struck rather than narrowed to whatever happens to be true, because it is the rule
that should have caught this. "Read-only **as a group**, no per-page exception" (above) is only safe
to state if a missed UI gate cannot let an edit through, and the whole point of writing the
defense-in-depth rule down was that the UI gates would eventually be missed — they were, on the
button next to the one that was gated correctly. **Known open defect; nothing was fixed in this
pass** and no code changed on 2026-09-06. The fix is the one this bullet already describes: the same
guard on `PagesViewModel`'s mutating functions, and the Trash sheet reaching the DAO through a
ViewModel like everything else.)*

Checkbox-only mode is built the same way — a second
`CheckboxOnlyState` (one active page at a time, since the window flags it drives are window-level)
combined with `LocalViewOnly` into a `LocalContentLocked` CompositionLocal that every control except
a to-do block's own checkbox respects. `WorkbenchScaffold` owns the "architecture note" concern
directly: a `LaunchedEffect` keyed on the active page id and the current nav back-stack entry calls
`Activity.setShowWhenLocked`/`setTurnScreenOn` only when they match, and deactivates the state
entirely the instant they don't (covering "navigates away" without needing an unlock). Turning it
back off while still on the page reuses the existing `showAppUnlockPrompt` (§3.6's `BiometricPrompt`
mechanism) rather than a second bespoke prompt.

*(**Amended 2026-09-24 — an invariant sweep of every mutating entry point, 164 of them.** Two were
outside the guard and are now inside it. `PageDatabaseViewModel.setHue` (S13) wrote through a
private `launchAndTouch` that, unlike Canvas's funnel of the same name, never checks the lock; the
`···` button hid the affordance, so the sheet had to be open already when View-Only went on — the
"missed UI gate" this section exists to make harmless. And `PageDetailViewModel.undo`/`redo` moved
the stack before `launchAndReindex` refused the write: the edit stayed, but its undo step was spent,
so the first undo after unlocking reversed the edit *before* it. The lock is now checked before the
stack moves. A third write is named here as an exemption rather than gated:
`PageDetailViewModel.onOpened` refreshes reference blocks' cached `content` — derived from the
referenced block, no `updatedAt` bump — the same idempotent repair-on-open as `ensureDefaultView`
and Canvas's lazy `PageCanvas`. Still guarded in the UI only, and recorded as such: the Notion
import (`NotionImporter` takes no `ViewLockState`; `SettingsScreen` swaps the section out under the
lock), where `PortableArchive` refuses on its own.)*

### 3.1.3 Page templates (Decided 2026-08-08)

Any Page or Database can be saved as a template from its "···" menu — "Save as template." *(Amended 2026-09-18 — §0.10 item 15: a **Canvas** too — its `···` has the verb on both platforms, and the template is the board: every card, frame and arrow cloned with the arrows rejoined, a page card still pointing at its page (a board of the same pages, again); the New sheet lists it with the canvas glyph. `TemplateManager` takes the three canvas DAOs; tested.)* A template
captures the page's block structure (for a Notion-like page) or its schema ~~plus default property
values~~ (for a Database), not a specific content instance. *(**Corrected 2026-09-06:** "default
property values" names a feature this data model does not have. A `Property` carries a name, a type,
a config and an order and no default; a value exists only as a `PropertyValue` bound to one specific
row, and `TemplateManager` clones properties and deliberately never clones row data — which is the
right behaviour for "structure, not instance", and is what its own doc comment says while
conspicuously dropping this phrase. The phrase was aspirational and reads as a promise; a
per-property default would be a new column and a new decision, not something a template does.)*
Creating a new page offers "New from template" alongside four fixed starting points — blank page,
blank database, to-do database, and Canvas (§3.4's page kind, added long after this section) — and
then lists the person's own saved templates. *(**Corrected 2026-09-06:** this read "a small built-in
set (blank page, blank database, to-do database — ~~folding §5.3's existing shortcut in as the first
built-in template rather than a separate mechanism~~)". It was not folded in. "To-do database" still
runs `createDatabase(asToDoDatabase = true)`, which wires the Done property and the §5.2 binding
directly and never touches `TemplateManager`, whose own doc comment says as much. That is arguably
the better arrangement — a to-do database needs a *binding*, which is a relationship over live rows
and precisely what a template deliberately does not carry — so the mechanism is kept and the claim
corrected, not the other way round. Canvas as a fourth starting point was never recorded here at
all.)* Templates live as ordinary Pages internally,
flagged `is_template = true`, excluded from Road Map's edges (§3.4) and from the Pages hub's default
list — surfaced only via "New from template" — so they don't clutter the page tree.

Deliberately no template *variables/placeholders* (e.g., an auto-filled "created date" field) for v1
— real Notion feature, but nothing elsewhere in this spec needs it; a template here is a structural
starting point, filled in by hand like any other page.

### 3.1.4 Daily journal (Decided 2026-08-08)

*(Amended 2026-09-18 — small things III, L12: a day page's stored title stays `journal/YYYY-MM-DD` (the sync's and the parser's name); wherever a title is *shown* — the tree, the shelf's graph header, the hover card, the switcher, the Road Map — it reads as its date, *13 Sep 2026* (`domain/journal/displayTitle`). ~~The page bar shows the storage name because it is the editable title.~~ *Amended 2026-09-20 — small things IV, S5: the page bar shows the date too, as text; a day's stored name is its link and is not edited from the bar (renaming it there had broken the day). The Today strip checks a habit with `HabitCheck` — a counting habit's `+` disc — as the Habits and Merged rows do (S6).*)*

A single "Journal" entry point (Pages hub topbar, alongside the existing search/···/eye icons,
§3.1.2) opens or creates today's journal page — `journal/YYYY-MM-DD`, auto-titled by date, created
on first open of a given day and reused after that. Structurally an ordinary Page (same Block model,
§3.1.1), parented under a permanent "Journal" root page in the tree rather than a new entity —
reuses everything Pages already provides (instant-write, FTS, page mentions) with no new mechanism.
A small date picker (reusing the Date widget's rendering logic where reasonable, §8.1) lets the
person jump to any past or future day's journal page, creating it on first visit if it doesn't exist
yet.

No streak/gamification here — that's Habits' job (§6). A day with no journal entry simply has no
page; there's no "missed" state to track, deliberately unlike Habits' streak model.

**[Amended] 2026-09-13 (§0.8 step 8b, B§6 #15) — today's page shows the day.** Above its blocks,
today's Journal page carries a *Today* strip: the tasks that fall on today (through
`EntryOccurrences.onDay`, so the Calendar and the Journal never disagree about a day; done ones
kept so the box can be unticked) and the habits due today or already checked in today (the
just-ticked habit must stay). **Checkable, not a mirror:** a task ticks through
`ResolveEntryUseCase.setDone` and a habit through `CheckInHabitUseCase`, the one path each already
has (§9.8 R1), so the Journal is a place to work the day and not only to look at it. **Live,
never blocks:** the page's text, its FTS row and its Markdown export stay what the person wrote —
the benchmark's open question, closed on that side. **Today only:** a past day's habit state would
need per-day queries and a resolved recurring task's past occurrence cannot be honestly
reconstructed from its advanced `startDate` (§6.2); the root page and every other day show
nothing. Not overdue tasks either — the Tasks tab's *Today* filter is `date == today` and the two
surfaces agree: the strip is the day, Tasks is the backlog. An empty day says "Nothing due today"
rather than hiding the strip (the deliberate opposite of §3.1.5's silent panel: a Journal reader
wants to know the day is clear). Not behind View-Only: these are task and habit writes, which
§3.1.2 never covered. Home: `domain/journal/JournalToday.kt` (pure, tested — `journalDayOf`,
`todayTasks`, `todayHabits`), `PageDetailViewModel.journalToday` (null on every page but
today's Journal; the day re-read each minute so a page left open past midnight stops claiming to
be today), `ui/pages/JournalTodayStrip.kt`.

*(**Amended 2026-09-19 — §0.10 item 4, the check-in.** Above the Today strip, on any Journal day that has happened (today or past, never a future day), the page asks *How are you?* — five words for mood and five for energy as the bar's pills, either alone. A tap logs one `check_ins` row (the day's date, the moment, one scale); the filled word tapped again tombstones it; several a day are the day as it went, and the line under the pills says the latest of each — *Good at 14:32 · energy high at 14:35*. No note field: the page's blocks are the words. The title opens *How you've been* — the month as coloured discs by the day's latest mood, a ring where only energy was logged, nothing where nothing — and the month's check-ins listed. Words, not faces (decided); no score, count, average or chart (B§10.3). Live rows, never blocks, for the strip's reason; not behind View-Only, for the strip's reason. `domain/checkin/CheckIns.kt`, `ui/pages/CheckInRow.kt`; the five mood hues are stored hexes rendered by the register as a label's are.)*

### 3.1.5 Backlinks panel (Decided 2026-08-08)

A collapsed-by-default "Linked mentions" section at the bottom of every page *that has any*
*(**clarified 2026-09-06:** the panel renders nothing at all when there are no backlinks, rather than
an empty collapsed header — a deliberate call and the right one, but "every page" is how this reads
and is not what ships)*, listing every other
Page whose body contains an `@Page Title` mention of this one (§3.1.1) — the same edge data Road Map
(§3.4) already computes, consumed here as a second, lighter-weight view rather than a second query
or a new data source. Each entry shows the linking page's title and the block containing the
mention; tapping opens that page.

~~No "unlinked mentions" (plain-text title occurrences that were never turned into a real `@mention`)
for v1 — surfacing those requires scanning all page content for substring matches, a real
FTS-adjacent feature of its own scope, not needed to satisfy "what links here."~~ **[Amended]
2026-09-13 (§0.6.12, step 8d):** a second collapsed section, **Unlinked mentions**, lists the live
pages whose text contains this page's title without linking to it, each with a *Link* that adds
the real mention span on that page; the scan is the block-reference picker's substring query,
cheap at personal scale. A block reference to a block of this page counts as a linked mention.

### 3.1.6 Labels (Decided 2026-08-08 — resolves the previously-undefined `category` field)

*(Amended 2026-09-18 — small things III, F9: the Add label sheet lists every label the page does not carry, each with its hue dot, before a letter is typed, filtered as typed; *Create "…"* only when nothing matches — Notion's and Obsidian's pickers list the existing first.)*

*(**Corrected 2026-09-12 (§0.6.9):** the feature is called **Label** — in the UI ("Add label") and
in the code (`Label`, `PageLabel`, `LabelDao`). The text below still says *tag*, the word it was
built under; read it as *label*. The table `tags`, the join `page_tags` with its `tagId`, and the
snapshot key `tags` keep their names — the key must for every peer on the folder, and the tables
are not worth a migration. *Tag* from here on means Notion's: a Select property inside a database.)*

§4's Page entity carried a `category` field from an early draft with no behavior ever specified
behind it — flagged during this pass as a genuine spec gap rather than a deliberate decision.
Resolved by replacing it with a proper tag system: a global, reusable, freeform set of tags
(create-on-type, matching Joplin's own pattern), applied many-to-many to any Page — not just
Database rows, which already have Select/multi-select properties for this purpose within a database
(§4). A `Tag` entity (`id, name, color`) plus a `PageTag` join table connect the two (§4).

Tags surface as a filter-chip row in the Pages hub (filter the page list by one or more tags) and a
small tag editor on each page itself, using the same type-to-search-or-create interaction already
established for page mentions (§3.1.1). *(**14g·2, 2026-09-16:** the label's `color` is drawn at
last — its hue as text on a 14 % tint on the Pages filter row and the page's strip (solid when a
filter is on), a hue dot in the Road Map's label menu and the Add-label sheet; the stored hex is
unchanged and rendered per ground, §2.3 *The token map*.)* Deliberately flat — no tag hierarchy/nesting; Joplin's own
nested-tag feature was considered and dropped as unneeded complexity at this build's scale.

### 3.1.7 In-app search UX (Decided 2026-08-26)

Four candidates, scored against fit with the already-decided FTS backend and icon placement (§3.1.1,
§3.1.2), real value for actual personal-productivity use, engineering scope added beyond what's
already specced, and consistency with this spec's running "obvious 80% subset" bias (the block
editor scope §3.1.1, single-condition view filters §5.6, the `Interval` property §5.2.2) (weights
0.30/0.25/0.25/0.20):

| Option | Architecture fit | Value | Scope added (inverted) | 80%-subset consistency | Score |
|---|---|---|---|---|---|
| **Pages-scoped overlay off the existing search icon (chosen)** | 1.00 | 0.85 | 0.90 | 0.90 | **0.9175** |
| Same, plus a later per-database "find in table" mode | 0.90 | 0.80 | 0.80 | 0.85 | 0.8400 |
| App-wide search including Entries/Habits | 0.40 | 0.90 | 0.30 | 0.50 | 0.5200 |
| Command-palette / omnibox (search + run actions) | 0.50 | 0.75 | 0.35 | 0.40 | 0.5050 |

**Decided:** tapping the search icon already placed in the Pages hub's topbar (§3.1.2) opens a
full-screen search overlay; it queries the page-level FTS index (§3.1.1) live at roughly a 300ms
debounce; results show each matching page's icon, title, and a highlighted snippet from the first
matching block; tapping a result opens that page scrolled to the match — exactly the behavior §3.1.1
already specified for a search hit, now given a trigger and a results surface. Database rows are
automatically included at zero extra cost, since a Row is a Page (§5.1) and already feeds the same
FTS index — this was already true structurally, just never stated as a search-UX answer until now.
App-wide search (folding in Entries/Habits, which are plain Room columns, not FTS content) and a
command-palette/omnibox layer are both real, larger features that nothing else in this spec actually
calls for; both stay explicitly deferred alongside §10's existing block-level-granularity deferral —
not decided against forever, just not v1.

**[Amended] 2026-09-17 (L6 of §0.10 item 22) — the switcher on a wide window is a centred card.** `CentredCard`'s frame with its top at 20 % of the window (Notion Calendar's command menu measured at 21 %), 560 dp (Obsidian's 560 CSS px), a 36 dp `TendrilField`, rows at the profile's height (29 dp Compact — Notion Calendar's rows measured 36 px at 125 %), the page readable behind the scrim. **Before anything is typed the card lists Recent** — the eight pages most recently edited (`pages.updatedAt`, no new state; Notion's Recents) with their age at the right, then a hint row for the two rules. A query lists **Pages** (the ranking below) and then, when any match, **up to three Commands** under their own eyebrow; `>` lists commands alone and every match (`domain/SwitcherQuery.kt`'s `switcherSections`, tested). The typed letters sit at heading weight where a title starts with them (Obsidian's rule); a command's chord is drawn beside it in `KeyChip`s, looked up from the shortcuts table (`chordLabelOf`) so the card and the F1 card cannot disagree; a selected row's secondary text takes `onSurface` (`dim` on the tint measured 3.45 : 1); a footer reads *↑↓ Navigate · ↵ Open · Esc Close*; no match shows one `description` line inside the card. The phone keeps its full-screen overlay, now with the Recents when empty.

**[Amended] 2026-09-12 (§0.8 step 8a, B§6 #11) — the palette is built, the deferral lifted.**
The search icon opens the **quick switcher** (`ui/switcher/QuickSwitcher`), on both platforms
and from every route (the desktop also on **Ctrl+K**): one field; text finds pages — those whose
*title* starts with it first, then the FTS hits — and a leading `>` finds commands (new page /
database / to-do database / canvas, Journal today, Review, the five tabs, a timer on any pending
task). ↑↓ Enter Esc; Back closes it. The overlay it replaces lives on in its rows and highlighting.
**The FTS defect went with it**: `page_fts` never held titles, so a page could not be found by its
own name; the index now starts with the title, every rename re-indexes, a new page is indexed at
creation, and **schema v16** empties the table so `PageContentRepository.healIndex` (run at every
start, idle when nothing is missing) rebuilds it once with titles in. Entries and Habits are
still not searched here — that half of the deferral stands.

**Acceptance:** the search icon opens a dedicated full-screen search surface, not an inline
dropdown; an empty query shows nothing; a query with no matches uses the §2.5 `EmptyState`
composable ("No pages match '…'").

### 3.2 Calendar

*(**Amended 2026-09-17 — the quick-add strip and the tray's clamp (L7 + L8).** The strip is the find bar's twin: 44 dp, a 28 dp `TendrilField`, the preview chips at the field's right on the same line and under it only when the line is full (a wrapped row adds one row, never more); the desktop's popup takes the switcher's 36 dp field with the chips under. The tray draws at most **30 % of the pane** (the shelf's rule at 45 %; grounds: Todoist's and TickTick's panes 21 %, Notion Calendar's 13 %) while the handle remembers 240–360; the Week header's second line names the minutes from a **90 dp lane** and reads *Planned* alone below — at the user's 967 px window a 30 % tray leaves ≈ 80 dp lanes, so the word alone shows there and the number returns with the tray collapsed (≈ 117 dp) or on a wider window. Both are shares, never px. `docs/critiques/quick-add-strip-mock.md`, `quick-add-strip-function.md`.)*

*(Amended 2026-09-18 — item 23's Lows: the phone's Day header is `rangeTitle(DAY)`, the wide bar's own title, in the locale's order (*venerdì 18 settembre*; P10); the Day view's preview chips are inset with the field (P8); the Timeline's and the Month header's weekday initials come from `weekdayInitial(day, locale)` (T·P4).)*

*(**Amended 2026-09-18 — the phone's second fixes (L·P2).** The phone's Calendar wears one bar: `Day ▾` (the views; *Plan the day* on the Day) and a `···` menu — *Layers ▸* as a pushed level with the dots and checks, *Calendar settings…* — above the day header; the segmented row and the layer chips are gone (≈ 200 dp of chrome before the first row → ≈ 100; TickTick's one bar, Notion Calendar's 40 dp bar). The Month's cells are 48 dp under Touch.)*

*(**Amended 2026-09-18 — the quick-add tokens inline (L7b).** The words the parser read as a token — the kind prefix, a date, a time or span, a length, a repeat, a deadline, a level — are tinted in the line as it is typed, on `findSoft` with the text unchanged (Todoist's quick add, measured: a tinted background behind the recognised runs; the chips beneath name them and carry the ✕). The tint says *read*, the chip says *as what*; ✕ on a chip untints its words on the same recomposition. One `VisualTransformation` from `ParsedEntry.spans`, applied at every home — the strip, the popup, the Add task sheet, the phone's Day view field — so no home can drift. A deadline is a task's: an event line keeps its *by friday* in the title, untinted and unchipped, rather than losing the words silently. `docs/critiques/quick-add-tokens-mock.md`, `quick-add-tokens-function.md`.)*

*(**Amended 2026-09-17 — the Calendar's chrome (L5).** On a wide window the bar is the Calendar's whole chrome (§2.2's *one bar per tab*): `‹ ›` step the view's unit (`shiftedAnchor`), the range reads `rangeTitle` (*Thursday 17 September* · *14 – 20 September 2026* · *September 2026* · *Next 30 days*, the Agenda without arrows), *Today* returns; `Week ▾` and `Layers ▾` replace the segmented row and the chips (`docs/critiques/calendar-chrome-mock.md` L1 + C1). **The time grid's proportions are Notion Calendar's, measured**: 45 dp per hour and a 56 dp gutter (`CalendarGeometry`, one home for the Day's Plan lanes and the Week grid — 48 / 60 px at 125 % beside its 48 / 60), the all-day row at the 22 dp chip pitch; nineteen hours fit the user's window where eleven did. Today's *Planned* line in `onSurface` (`onSurfaceVariant` measured 3.45 : 1 on the tint). The phone's rows are unchanged.)*

*(**Amended 2026-09-21 — habits as brush-strokes.** A habit on the calendar's grids is **not a block**: it is **a brush-stroke across its span of time**, drawn *under* the day's blocks — which may cover it — a reminder to keep that time for oneself (the user's brief; decided in one batch on `docs/mockups/habit-strokes.html`, softened the same day on `habit-strokes-soft.html`; `docs/critiques/habit-strokes-mock.md`, `habit-strokes-function.md`). **The span** is the habit's time and its duration — 30 min when it has none; a habit with no time is not on the grid at all (the all-day row never holds one). **The stroke**: the full lane, the habit hue at **12 %** feathered 6 dp at each end (4 on a short stroke), never under **18 dp** (a brush has a minimum width — the name's line; a stroke under 30 dp centres its name), the name in `caption` at Regular in the hue along its start, **no time** — the grid says when. **One state**: the check-in changes no wash (§0.6.6 — a pale *not yet* would be absence); on **today's lane only** a 5 dp dot before the name is presence, and a past day never carries one, so a stroke without a dot is never an absence. Measured: the wash 1.22 / 1.19 : 1 against the two grounds (a tint under the day, under the 3 : 1 non-text floor by design, as `surface2` is), the name in the hue 5.5 : 1 on it — `dim` clears AA on no share (4.4 / 4.2), so the softening is the weight and the case, never the colour. **Where**: the Week grid and the Day's Plan view (`WeekGridView`, `PlanView`, one painter `ui/calendar/HabitStrokeLayer.kt` composed before the blocks; `domain/plan/HabitStrokes.kt` pure and tested); **never the Month** — no chip on the grid, no dot on the phone's cells; the Agenda and the Day's list keep their habit rows. A habit is no longer in `timelineBlocks` nor in *Planned* — reserved, not booked. **Inert** to drags and drops (the grids' drop arithmetic never sees it); a click on bare stroke opens the habit's sheet — the Tasks tab's own, with *Edit…* (S10). The Layers menu keeps *Habits* and governs the strokes. Every active timed habit is on every day, as the chips were — the period is not read (recorded: a period-aware rule needs the log). Grounds [Assumed]: Reclaim's habits (flexible blocks drawn lighter, yielding to meetings), Google Calendar's retired Goals; none draws a habit *under* the day — the stroke is Tendril's reading of *reserved, not booked*. Not taken, recorded: a hairline hatch (Outlook's *tentative*), strokes only on today and ahead.)*

*(**Amended 2026-09-17 — the Month grid (L4).** On a wide window the Month is a grid of titled cells, `ui/calendar/MonthGrid.kt`: **rows are the weeks the month spans** (four, five or six — `monthGridDays`, never a padding row), equal, filling the pane; a weekday header of eyebrows; the number top-left at the Week header's alignment (Notion's top-right is the one convention not taken), today a 22 dp `primary` disc, adjacent-month numbers dim with their items at full strength, the 1st of any month labelled with its month; each cell lists its occurrences and extras as the Week's all-day chip (`OccurrenceChip.kt`, 22 dp — Notion's 28 px at 125 %; the layer's tint, the urgency stripe, the time first in tabular figures, both in `onSurface` because `onSurfaceVariant` measures 3.0–4.2 : 1 on the tints), **as many as the cell's height holds** (`cellCapacity`; the Week's all-day row is the same rule at three), the last slot ***+n*** when the day overflows — which opens a **popover listing the day** (the hover card's frame; Google's and Outlook's *more*, where Notion's database calendar grows the row) with *Open the day ›* at its foot. Clicks: the number → the Day view; a chip → its edit sheet (a database date → its page, a habit → the Day); **the empty ground → the quick-add strip seeded with that day** (Notion's hover `+`, Google's click-to-create, through the parser; a `+` shows on hover); a double-click on the ground → the Day. A chip **drags to another cell** on press-and-move — the Week strip's `move` and its *this one or all?* prompt; an extra never lifts (a habit's day is its rule's, a database date the Table's cell). The range expanded for the Month is the grid's 35 or 42 days. **The phone keeps its dot grid** — a 40 dp cell holds no chip — with a weekday header and **one dot per layer** present that day, at most three by precedence task › event › habit › database date (`monthDots`): a task's in the ladder's *none* (dim, never the accent), an event's in `event`, a habit's in `habit`, a database date's in `third` (`dotColour`; the token map's note). `tools/audit.py` rule 16 refuses a second seven-column grid. Grounds and scoring: `docs/critiques/month-grid-mock.md`; measured after: `month-grid-function.md`.)*

*(Amended 2026-09-17 — the audit's fixes: the Day list's rows, `ExtraRow` and the Agenda's rows are the one-line row under a pointer — §2.2's `RowControls.kt` — 65 and 52–61 px → 30.)*

*(**Amended 2026-09-16 — B§13.6 #7.** The bell is on the desktop too — reminders are Windows toasts (§2.2 *The notification area*); quick add has a third home, the chord's popup.)*

*(**Amended 2026-09-16 — B§13.6 #5, drag between panes.** The Calendar gains a **task tray** —
the Day view's Plan rail grown into a pane beside the week (Sunsama's, Akiflow's backlog; decided
2026-09-16 on `docs/mockups/drag-between-panes.html` and `docs/critiques/drag-between-panes-mock.md`):
**Unscheduled** (pending tasks with no When — never a step, never a series) and **Overdue**
(dated before today), `domain/plan/Tray.kt`, 36 dp chips with the urgency stripe, the overdue
date in the dim colour (the stripe says how urgent). On a wide window a pane at the week's left,
240…360 dp (`calendar_tray_width`), collapsible from its header to a chevron in the bar
(`calendar_tray_collapsed`), the tree's handle; under Touch a collapsible **strip** under the Week
strip and the Month grid (the Day view keeps its rail, which also places a time). **The drag**
starts on press-and-move under a pointer and on a long press under a finger
(`ui/components/Pointer.kt` `dragSource` — hand-rolled over `awaitEachGesture`, since
`detectDragGestures`' start offset was not the pointer's); the screen owns it and draws the ghost
(the chip itself, a shadow, the target named: *Thu 17 · 10:00*, *Thu 17*, *Clear When*).
**Exactly one target lights** — the thing under the pointer: a day header or the all-day row (the
day, the task's own time kept), a quarter-hour slot on the grid (the day and the time), a Week
strip card or a Month cell on the phone (`ui/calendar/DropGeometry.kt`: one `WeekGeometry` both
the grid's own drag and the tray's ask, `weekDropAt`, `dayCellAt`), or the tray itself. **The
drops are the existing writes** (`place`, `move`), so a series meets the same *this one or all?*;
**a block dropped on the tray clears its When** (`EntryEditor.clearWhen` — the four date/time
fields; the Deadline stays, two dates being two things; a series is refused with a snackbar).
Nothing is placed for the person (§0.5.2): the tray offers, the drag decides.
`docs/critiques/drag-between-panes-function.md` walked the build; the phone's strip is unwalked.)*

*(**Amended 2026-09-16 — B§13.4 14f·2, the tab on a wide window.** From 840 dp (`LocalShellLayout`)
the **Week is a time grid** (`ui/calendar/WeekGridView.kt`): seven of the Day view's Plan lanes
under one scroll and one header — the same 56 dp hour, 44 dp gutter *(45 dp hour, 56 dp gutter since 2026-09-17 — `CalendarGeometry.kt`; noted 2026-09-24)*, `timelineBlocks`, now-line
(today's lane only) and quarter-hour snap — so the week has the day's shape rather than the
strip's seven bulleted cards (`docs/critiques/tasks-calendar-mock.md` #11). The header is the Day
view's per column (day and date, today tinted, *Planned Nm* as a second line; a click opens the
Day); the all-day row holds the untimed and multi-day continuations, three then "+n"; blocks wear
the layer's tint with `onSurface` text (the mock's 2.70:1 chips, #7). It opens scrolled an hour
before the week's first block, else 07:00, keyed on the blocks' arrival (the mock's grid ran off
its frame with nothing scrolling, #6). **A drag changes date and time in one gesture** (decided
2026-09-16): the lane under the pointer is the day, the quarter-hour the time, one
`EntryEditor.move` call for both axes (`CalendarViewModel.moveBlock`); a series asks *this one or
all?* as every move does. Below 840 dp the strip stays. **The opening view is a Settings choice**
(B§13.4, 2026-09-13): `calendar_default_view` — an *Opens on* chips row in the desktop's Settings
pane and the phone's Calendar settings sheet (`CalendarOpensOnSection`, shared); unset, a wide
window opens on the **week** (Fantastical, Sunsama, Akiflow) and a phone on the **day** — §2.2's
default, not Month, which B§13.4's row had assumed. **Quick add on a wide window** is a strip
under the bar (the find bar's pattern; decided 2026-09-16) opened by the bar's ⊕, the same field
and chip preview as the Day view's inline field, which hides on a wide window and stays on the
phone; no chord (Ctrl+Shift+N is *New task*, #8); Enter writes, Esc closes. The Tasks tab's two
filter rows moved into its list column on a wide window (14f·1's walk, #2).
`docs/critiques/calendar-function.md` walked the build; the phone's half walked on the phone 2026-09-18 (`docs/critiques/phone-catch-up.md`, `phone-function-full.md`).)*
*(**14g·3, 2026-09-16:** a task block — timed or all-day — wears its urgency as a 4 dp left stripe
(§0.6.4 as amended); the text starts 12 dp in so the two never meet.)*

- Day, Week, Month views *(**and Agenda, added 2026-09-12 (§0.8 step 6c):** the next 30 days as one list grouped by day, empty days skipped — the fourth view, off the same segmented row)*; **week starts Monday**; defaults to **Day** view on open. All three draw
  *occurrences*, not stored rows (§4.1.1) — a recurring EVENT appears on every occurrence in view
  and a multi-day one on every day it covers. The "day N of M" label is the **Day view's alone** *(and the Agenda's since 2026-09-12, which calls the same `occurrenceSubtitle`; noted 2026-09-24)*
  *(corrected 2026-09-06)*: Week lists bare titles and Month draws a presence dot, so neither has
  anywhere to put it. That is the right trade at those densities, but it means the disambiguation
  §4.1 asked for — three consecutive identical titles with nothing to tell them apart — is delivered
  in one view out of three.
- ~~Create and edit events and tasks directly~~ — **corrected 2026-09-06: create events, through
  Quick Add, and nothing else.** *(**Amended 2026-09-12 (§0.8 step 5):** create events **or tasks**,
  through Quick Add, which now reads the line — see the Quick Add bullet below. The edit half is
  still true: nothing on this screen edits; that is step 6.)* *(**Amended again 2026-09-12 (§0.8 step
  6b):** the edit half is no longer true. A Day row opens `EntryEditSheet` — title, kind (Task ↔
  Event, the other kind's fields hidden and dropped), When with optional time, an Event's end, the
  repeat presets (a richer stored rule is kept unless a preset is picked), a Task's deadline,
  estimate and — behind the Settings switch — the flag; Delete → Trash. Edits to a series apply to
  every occurrence. On Week, an occurrence long-pressed and dragged onto another day's card moves —
  a series asks *this one / all*, "this one" through §4.1's override row. Every write goes through
  one use case, `EntryEditor` (`save`, `move`), which enforces the §4 kind invariants and calls
  `EntryScheduleCoordinator.onEntryChanged` — §4.1's "every write path" is now literally one path.
  The Day view is a list, not an hour grid, so dragging to another *hour* waits for the timeline
  step 7's Plan mode builds; the sheet changes the time meanwhile.)* *(**2026-09-12, step 7b:** it
  no longer waits — Plan mode's grid is that timeline, and a block dragged on it moves to the hour
  it is dropped on. See §0.6.5.)* Neither half held on this screen. Quick Add is Calendar's only write
  path and it hard-codes `kind = EVENT` with a title and a date, so a Task cannot be created here at
  all — that is Tasks & Habits' own add dialog (§3.3). And nothing on this screen edits: a Day row
  offers a Done checkbox and a reminders bell and no tap target on the row itself, while Week and
  Month rows only navigate. The only surfaces in the app that edit a stored Entry are §5.2's bound
  Database cells, where a row's deadline and its recurrence can be changed from the database table or
  from the row's own page; a title, a time or a span has no edit path anywhere. §4.1's "every write
  path that can move an Entry's `start_date` (create, **manual edit**, or the resolve-and-advance
  step)" is therefore satisfied by those cells rather than aspirational — the gap is that Calendar,
  the screen a person would actually look on, is not one of them.
- **System Calendar Provider registration** (Decided wording, 2026-07-13 — replaces the earlier
  "default calendar app" phrasing, which isn't a real Android concept: there's no `RoleManager` role
  for calendar the way there is for browser/SMS/dialer). Tendril registers a Calendar Provider
  account, so its own events become visible/editable to any other calendar-aware app, widget, or
  watch face on the device via `CalendarContract` (native integration required — see §9.11). **Added
  2026-07-14**: Room (with the RRULE-based recurrence expansion from §4.1) is the single source of
  truth for Tendril's own UI at all times; the Provider write is one-directional (Tendril → Provider)
  and never read back for Tendril's own display logic. Two independent recurrence expanders both
  feeding the same screen — Tendril's local one and whatever `CalendarContract.Instances` computes —
  would be exactly the kind of duplicated, silently-divergent logic (DST, leap years, BYDAY edge
  cases) worth ruling out now rather than discovering later. **Corrected 2026-08-29**:
  "sync-adapter-backed account" originally implied a real `android.accounts.Account` registered via
  an `AbstractAccountAuthenticator` service plus a background `AbstractThreadedSyncAdapter` — the
  machinery an actual remote-syncing (CalDAV-style) calendar needs. Checking `CalendarContract`
  directly before implementing found the account never needs to exist at all:
  `CalendarContract.ACCOUNT_TYPE_LOCAL` plus a `CALLER_IS_SYNCADAPTER=true` write-time query
  parameter is Android's own documented mechanism for exactly this "local calendar, no server" case
  — no authenticator service, no sync-adapter service, no intent-filter registration for either. See
  §9.11 for the full mechanics.
- **Optional Google Calendar sync** (two-way, a separate integration from Provider registration
  above). **Decided (2026-07-13, direct decision — no trade-off frontier needed)**: stays
  independent. This wasn't a genuine multi-objective case for an optimizer — "independent" wins on
  both simplicity (no write-through sync code) and risk (no duplicate-event display if the real
  Google Calendar app is also installed), and only gives up system-wide Provider visibility
  specifically for Google-sourced events, which is a minor loss since Provider registration already
  delivers the actual goal (Tendril's own events being visible system-wide) regardless. A
  Connect/Disconnect control for this lives in Calendar's own settings (not main Settings) —
  **corrected 2026-08-29**: no client ID field, see §9.5 for why and for the OAuth setup details
  (production vs. testing mode, 7-day token expiry trap), and §9.5.1 for the sync engine's own scope
  (EVENT-only, `primary` calendar, incremental via `updatedMin`)
- **Quick Add**: fast, minimal single-line capture. *(**Amended 2026-09-12 (§0.8 step 5, B§6
  #3):** the line is read by `QuickAddParser` — English first: `today`/`tmr`/weekdays/`next
  monday`/`in 3 days`/`sep 20`/`20/9`/ISO for the date; `3pm`/`15:30`/`noon` for the time; a span
  `3-4pm` or `at 3pm for 1h30` for an event's end; `daily`/`every 2 weeks`/`every monday`/`every
  weekday` for recurrence, emitted as an RRULE on an event and a period on a task; `by friday`/`due
  sep 30` for a task's deadline, never its When; a trailing `!` for the flag. The surface picks the
  kind — Calendar makes an Event, Tasks a Task — a span forces Event, a leading `todo`/`task`/`event`
  forces either way, and a chip row under the field previews the reading before Enter: the kind chip
  flips it, a chip's × says "that was a word". Italian words are a later row; ISO dates and 24-hour
  times read in any language. Tasks' add dialog reads its title the same way and pre-fills its
  controls.)* Deliberately does **not** include the Reminders
  list (see §5.4) — that lives behind its own bell icon on a row, keeping Quick Add fast
  *(**corrected 2026-09-06**: "the fuller edit sheet" named a surface that was never built — see the
  Reminders bullet below)*
- **Show Habits** button — ~~**not built (recorded 2026-09-06)**~~ *(**built 2026-09-12 (§0.8 step
  6d)** as one of four **layers** on a chip row under the views: *Tasks* and *Events* (on, and they
  filter the rows), *Habits* (off; every habit with a time, drawn on every day at its time with its
  duration — the human layer shows presence, never an obligation, so the frequency is not turned
  into "due today"; **since 2026-09-21 a stroke under the blocks on the grids, never on the Month** — the amendment below) and *Database dates* (off; every stored `DATE` cell in every database, drawn on
  its day as the row's title and the column's name, and opening the page — Notion Calendar's layer,
  built from the rows the databases already hold. A bound date column is a live proxy of an Entry
  (§5.2.1) and that Entry is already a task on the Calendar, so nothing draws twice). Layer state is
  the session's; persisting it is §0.10 item 12.)* *(Superseded 2026-09-12: item 12 resolved, the layers persist as `calendar_layers`; and the habit layer below was built the same day — `CalendarViewModel` takes the `HabitDao`. The next two sentences are the record, not the state; noted 2026-09-24.)*. Still the intended design: a small
  toggle surfacing habit entries (with time+duration) inline in the calendar view. `CalendarViewModel`
  does not take a `HabitDao` at all, so habit rows never reach this screen — a genuinely unbuilt item
  rather than a rendering detail. Habits with a time are visible today only in Tasks & Habits' Merged
  view (§3.3), which is the same idea on the other page; whichever of the two is built second should
  share one composable rather than growing a second habit-row renderer.
- **Reminders** (Decided, §5.4): repeatable list per event/task, presets (1h / 2h / 4h / 8h / 1 day
  / 2 days / 1 week) or custom (number + unit), no cap on how many stack, ~~lives in the full edit
  sheet only~~ *(**corrected 2026-09-06:** reached from a bell icon on each row of Calendar's Day view
  and of the Tasks list, opening a sheet of its own. The "full edit sheet" it was supposed to live in
  was never built at all, so the constraint this clause was really expressing — **not** in Quick Add,
  which stays single-line — is the part that holds, and is what it should have said. Week and Month
  rows carry no bell; they navigate to Day. The same phrase is corrected in §5.4 the same day.)*
- Recurrence (Decided, §6.2): `None / Daily / Weekly / Monthly / Custom (interval + unit)`, anchored
  to the **original fixed schedule** — a missed occurrence does not shift subsequent ones
- **iCalendar (.ics)** *(**added 2026-09-12, §0.8 step 6e, B§6 #7)*: export every task and event —
  `VEVENT` for events with their `RRULE`, `VTODO` for tasks with `DUE`, `STATUS`, `PRIORITY` for the
  flag and `DURATION` for the estimate, a task's period written as an `RRULE`; a moved occurrence is
  its own component with `RECURRENCE-ID` (and `RELATED-TO` its base), a skipped one an `EXDATE`;
  `UID` = the Entry's uid. Import reads `VEVENT` and `VTODO` (Google's and Apple's `Z`, `TZID` and
  all-day forms, folded lines), matches on `UID` so the same file twice — or Tendril's own export —
  updates rather than duplicates, and writes every row through the coordinator *(true since 2026-09-24: a series was reported before its EXDATE skips existed, so the scheduler armed the skipped occurrences — audit 1.6)*. Reached from
  Settings › Calendar (.ics) on Android (the document picker) and from the Calendar's `···` on
  desktop (a file dialog), since desktop had no Settings then *(it has since — `DesktopSettingsScreen`; the placement stayed)*. The file goes where the person puts
  it, never into the sync folder — the same answer §0.10 item 6 wanted for JSON Canvas.

### 3.3 Tasks & Habits

*(Amended 2026-09-19 — §0.10 item 3: a counting habit's row wears a `+` disc where the checkbox stands and says what was done today (*2 cups today*) where a plain one says its cadence; the pane's *Counts* row and the sheet's amount sentences, `HabitCheck` the one composable for both kinds.)*

*(Amended 2026-09-18 — item 23's Lows, L·P5: the Postpone sheet's custom offset is a count and a `days ▾` unit menu on both platforms — every ground's form — where five chips wrapped beside the count on the phone and scrolled in the slide-over; the task pane's *When* and *Deadline* read as the rows do, *ven 25* (F·P4's desktop half).)*

*(Amended 2026-09-18 — the phone's second fixes, L·P2: the phone's Tasks tab wears the desktop's pane header — *Tasks · Habits · Merged* as 40 dp tabs and `This month ▾` — instead of two segmented rows; L·P1: every list row at the profile's 56 dp minimum.)*

*(Amended 2026-09-18 — the phone's fixes, P6: a task row's second line is `taskRowMeta` on both profiles — the When as the tray's *sab 12* (the month named only outside this month), the time, *due …*, the steps, the tracked segment — one line each for title and meta under Touch, the row 59 dp beside Todoist's 57 and TickTick's 46 measured on the phone; never an ISO date. P2: the Add task sheet, like every `TendrilSheet`, scrolls under the keyboard.)*

*(Amended 2026-09-18 — small things III, L14 / F12 / F10: the reminder presets are a list under *REMIND ME … BEFORE*, each row naming the offset and the moment it would fire (the start's time, else the all-day anchor, else midnight) — TickTick's reminder list measured live; *Custom…* the last row; the anchor presets wrap. The Someday filter reads *Show Someday · n* beside its switch, never as a section header.)*

*(Amended 2026-09-18 — L15, the Material frames PR: the Add task and Add habit dialogs are `TendrilSheet`s — a slide-over on a wide window (Ctrl+Shift+N opens it as before), a bottom sheet on the phone — with the edit sheet's frame: the title on the 36 dp field, the switches, the *Repeats* chips, *Cancel · Add* at the foot; the one modal that was neither a slide-over nor a picker is gone. `docs/critiques/material-frames-function.md`.)*

*(Amended 2026-09-17 — L5: on a wide window the two segmented rows fold into the list column's 52 dp header (`PaneHeader`, the tree header's row) — *Tasks · Habits · Merged* as tabs, `Today ▾` (Today / This week / This month; absent on Habits) at its right — and *new* joins the bar beside Review and Trash; the FAB is the phone's. §2.2's *one bar per tab*.)*

*(Amended 2026-09-17 — the audit's fixes: the habit pane leads with the habit's facts — Repeats · At · For · Streak — then §0.6.5's presence and the month of dots, then the row's verbs as chips: Check in today / Undo, Start timer / Stop, Move to Trash. No Reminders chip: the shared sheet is an Entry's and a habit fires at its own time.)* *(Amended 2026-09-20 — S10: **a habit is editable after creation** — *Edit…* on the row's `···`, as a chip on the pane and as a button at the foot of the phone's sheet opens the Add habit sheet with every field filled (*Edit habit*, *Save*); `Habit.edited` keeps the identity, the streak and its dates, drops a duration with its time and the amounts with *Counts something*; the alarm re-arms. `docs/critiques/edit-habit-function.md`.)*

Three views: **Tasks**, **Habits**, **Merged** — one page, kept from losing clarity despite covering
two different concerns.

*(**Amended 2026-09-16 — B§13.6 #7.** The bell on the desktop's rows (§2.2 *The notification area*); under a pointer the task and habit rows are one line at the profile's height, the meta at the title's right — measured beside Notion, 65 → 30 px.)*

*(**Amended 2026-09-16 — B§13.4 14f·1, the tab on a wide window.** From 840 dp (the shell's own
rule, read through `LocalShellLayout`) the tab is two panes: the list at 420 dp (resizable
300…600 with the tree's 12 dp handle, kept in `tasks_list_width`) beside a **pane** that reads
the chosen task in full — title, When, Deadline, Repeat, Importance (when Settings shows it),
Tracked today, the steps as their own list with *Add a step* at its end — and offers **the row's
verbs as chips, by name**: *Postpone…*, *Set deadline…*, *Important*, *Start timer*,
*Reminders…* (Android only), *Move to Trash*, each opening the sheet the row's `···` opens, a
slide-over here (decided 2026-09-16: nothing is edited inline; `docs/critiques/tasks-calendar-mock.md`
#4 struck the mock's *Someday* and *Flag*, verbs the app does not have; its pane labels measured
3.08:1 → `onSurfaceVariant`). A habit's pane is its detail sheet's content inline. A click or ↵
selects (`primaryContainer` — not 14e's keyboard ring, which stays a ring); switching tabs empties
the pane; a trashed subject empties it too. Below 840 dp the tab is exactly the phone's. **Rows
under a pointer** (14d's deferral): a task row's and a habit row's `···` fade in on hover under a
pointer profile and stay under Touch, right-click opens the menu at the pointer, long-press opens
it on the phone; the habit row's one-tap × is that menu's *Move to Trash* now — a destructive verb
one step in, as a task's is. The **Trash sheets are shared** (`ui/trash/`, taking `WorkbenchCore`),
so the desktop has its Trash button (§0.10 item 13); the bell stays Android's — no alarms on the
desktop. The Habits and Merged lists and the phone's Pages list take 14e's keyboard.
`docs/critiques/tasks-function.md` walked the build; the phone's half walked on the phone 2026-09-18 (`docs/critiques/phone-catch-up.md`, `phone-function-full.md`).)*
*(**14g·3, 2026-09-16:** the star is gone; a task row's urgency is a 4 dp stripe at its left edge,
the pane's *Urgency* row names the level (and *the deadline* when that set it) with a popup picker
on its chip, and the row menu's *Urgency ▸* lists the five levels — §0.6.4 as amended.)*

**Tasks** (GTD-style todo list):
- *(**Added 2026-09-12 (§0.8 step 5):** the add dialog's title is read as a Quick Add line — `Gym
  every monday 7am by friday` fills date, time, repeat and deadline, previewed as chips, all still
  editable; the title that is saved is the line with the tokens removed. Same parser as Calendar's.)*
- Entries are the same entries the Calendar uses. Deadlines sync both ways on add/edit (already true
  from the original spec, reinforced by §5/§6's database and recurrence design)
- Today / This week / This month filter bar (§2.2), default **Today**
- Undated tasks collapse into a toggle at the top, filtered consistently with whichever time-filter
  is active — **named "Someday" since 2026-09-11 (§0.6.4)**
- **Since 2026-09-11 (§0.6.4):** a task has a *When* (`startDate`, where Calendar draws it) and an
  optional *Deadline* (`dueDate`, shown as "due <date>"); checklist-style steps under a task, one
  level, undated, filtered with their parent; a **Postpone** sheet on the row's `···` that moves the
  When by minutes, hours, days, weeks or months and never the Deadline; a single *important* flag,
  hidden until enabled in Settings (§0.5.1)
- Recurring tasks supported (§6.2) — calendar-native recurrence, distinct from Habit streaks

**Habits** (adjacent but distinct — daily/periodic personal practices, not work):
- Optional time + duration (not required)
- ~~Habit sync folder picker lives in **Tasks & Habits' own settings** (not main Settings)~~ — written
  to via an SAF folder grant, read by the external Syncthing-fork app (§9.3, §9.4). *(**Corrected
  2026-09-06:** the premise this rested on — habits sync through a folder of their own, so the picker
  belongs on the habits page — stopped being true when §9.4 put entries, habits and pages into one
  snapshot folder together. There is exactly one SAF folder grant, it lives in main Settings (§3.5),
  and it is the sync folder for everything; Tasks & Habits has no settings surface of any kind.
  Recorded rather than quietly deleted, because §3.5's "page-specific settings live on the page" rule
  cites this bullet as one of its two precedents and only Calendar's Google connect control actually
  is one — and because §2.2's icon rule, corrected the same day, presupposes the same missing
  screen.)*
- ~~Streak-based~~ **Corrected 2026-09-11 (§0.6.6): log-based, presence shown.** A check-in is a
  `HabitCompletion` row; the streak is a derived number shown only when asked for in Settings, and
  the habit's own sheet says what was done and never what was not. Missing an instance still does
  **not** create backlog (§6.1 — this is the defining test that separates a Habit from a recurring
  Task), and now nothing on screen counts the miss either.
- **Decided (2026-07-13)**: Habits stay a simple, structurally separate top-level entity — not the
  database-driven Sync-to-Tasks pattern. §5.2's own reasoning against inferring Task-sync from
  schema shape ("a 'Cooked?' checkbox... should never silently become a Task") applies at least as
  strongly here, and Habit completion semantics (cadence + streak, no deadline) don't map onto the
  Done+deadline property bindings §5.2 defines. A future database-driven "Sync to Habits," mirroring
  §5.2's shape, is a plausible later extension — but as its own separate toggle/bindings, not a
  reuse of the Task mechanism.

**Merged**:
- A calendar-style view holding both Tasks and Habits together
- Habits **with a time** get a **delicate highlight** to distinguish them from Tasks in the
  same view; a habit with no time does not appear in Merged at all. *(**Corrected 2026-09-06:** "time
  + duration" reads as a joint condition and is not one — duration is optional (above), and gating on
  it would drop most habits, so the code gates on the time alone and has said so in its own comment
  for some time. The highlighted row shows a title and a time; duration is shown on the Habits tab
  and not here, which is a gap rather than a decision, since duration is exactly what tells a
  five-minute habit from an hour-long one in a day-shaped list.)*
- Undated tasks — **not carried into Merged at all (recorded 2026-09-06)**. This read "collapse into
  the same top toggle pattern as the Tasks view"; the Merged list filters to dated rows only and has
  no toggle and no parameter for one, so an undated task is invisible here rather than one tap away.
  Worth fixing rather than re-specifying: the toggle is the pattern §2.2 settled on precisely so that
  an undated item is never silently dropped, and Merged is the view most likely to be read as
  "everything today".

### 3.4 Road Map

*(Amended 2026-09-18 — small things III, F14: the force layout separates any two boxes that overlap every frame (8 dp of air, along the axis of least penetration), so the map never rests with one card over another; nothing is drawn until the layout has settled once, then it fades in — the shelf's 280 dp neighbourhood had shown its seed circle for a second.)*

*(Amended 2026-09-17 — L5: on a wide window the filter row is a `Filter ▾` check-menu in the bar — Journal, the three kinds (a kind refuses to be the last unchecked), *Any label* and the labels one at a time — the menu staying open while toggling; the edge legend sits at the canvas's foot. The phone keeps the row. §2.2's *one bar per tab*.)*

*(B§13.6 #3, 2026-09-16: a node previews its page on hover — the card of §3.1.1's amendment, the
map's canvas holding the state, so the shelf's neighbourhood previews too; a node's drag hides it.)*

*(14h·2: the map refreshes itself when a page or a relation changes — `PageDao.observeRootPages`
and `PageRelationDao.observeAll`, debounced 300 ms in the ViewModel; ↻ stays for mention edges,
which live in block text that no page flow announces.)*

Originally scoped as "Mind Map" — **renamed to Road Map** specifically to avoid collision with a
*separate* feature: a user-drawn mind-map surface inside Pages. Road Map and that surface must never
be conflated in naming or code.

**Corrected 2026-09-04 — that separate feature now exists, and this document said it didn't.** This
paragraph read "which does not exist yet and is intentionally out of scope for the current build
(§10)," and §10's Deferred list called it "entirely separate feature, not started." Both were false:
a **Canvas** feature is fully built and shipping — a third `PageKind.CANVAS` alongside `PAGE` and
`DATABASE`, three Room tables (`page_canvases`, `canvas_nodes`, `canvas_edges`) registered on the
database, their DAOs, a `CanvasScreen`/`CanvasViewModel`, snapshot sync through `PagesSyncEngine`,
and an entry in the "New from template" sheet. It is modelled on Obsidian Canvas: freely-positioned
text and page-embed cards joined by user-drawn, optionally-labelled, optionally-directional arrows.
It is its own page kind, not a block type nested in a page body.

The design record never recorded any of it — no Revision Log entry, no entity in §4's data model, no
mention in §1's "Five pages" — which also breaks §12's anti-drift rule, since the Canvas entities
live in `shared\` and so owed both spec files a same-day entry. The feature is now acknowledged
here; the reasoning behind its shape lives in `PageCanvas.kt`'s own doc comment, which is currently
the only written record of it. **Writing that reasoning up properly is an open item**, not something
this correction can reconstruct after the fact. The Claude-API-generated variant of the idea is
still not built and stays deferred (§10).

- Interactive map of relationships **between existing Pages**; a first tap selects a node and
  highlights its neighbourhood, dimming everything else, and a second tap on the already-selected
  node opens that page. *(**Corrected 2026-09-06:** written as "redirecting to each on tap", which is
  what the design record has said since 2026-07-13 and is not what shipped. Two-stage is the better
  behaviour on a dense force-directed graph — a single-tap-to-navigate map cannot be explored without
  leaving it — and it is worth recording as a decision rather than leaving as a discrepancy, because
  the phrase "redirecting to each on tap" is reused two bullets below as the argument against
  inferred, similarity-scored edges.)*
- Full-screen canvas (§2.2); "All Pages" as a collapsible bottom drawer, not a side panel
- **Decided (2026-07-13)**: edges are explicit, never inferred from content similarity (a fuzzy,
  scored-match feature that doesn't match "redirecting to each on tap"). Two sources feed the same
  edge set: (1) page-mention links created inline while editing a page's body (§3.1.1) — the primary
  source, no extra UI needed; (2) a manual "Relate to…" action for pages that are conceptually
  connected but don't reference each other in text. **Dependency**: this can't be fully built until
  §3.1.1's mention/link feature exists. Node layout uses a force-directed algorithm (repulsion
  between nodes, spring attraction along edges, centering, collision avoidance), hand-rolled
  natively (Compose Canvas + a small physics loop) rather than a ported library — conceptually
  similar to what an early, since-scrapped prototype used for a different, broader "every page" tree
  view; that prototype's scope (all pages, parent/child edges only) is explicitly not what Road Map
  is, only its layout-algorithm category is worth reusing.

**[Amended] 2026-09-13 (§0.8 step 8c, B§6 #14) — shared, filtered, reachable from a page.**
`ui/roadmap/` moved from the Android app to `shared/` unchanged in behaviour (everything it read
was already on `WorkbenchCore`; nothing in it was Android-specific), so the desktop draws the same
map; the `roadMapContent` slot it filled is retired and the scaffold renders the screen itself.
The graph's shape — the types, the depth walk, the filters — is pure in `domain/roadmap/`. **Three
filters**, always visible under the app bar: the **Journal** (root and day pages) hidden by default
— Logseq's answer, a day page mentions everything and says nothing about structure; each **page
kind** switchable (never all off); **one label** at a time. Applied *before* the focus walk, so
"depth 2" never routes through a hidden page; the Journal/kind half persists per device
(`roadmap_filter` in the `KeyValueStore`), the label id is per-device and does not. **Typed edges
tinted**: a mention keeps its arrowhead in the neutral ink, a manual "Relate to" line takes the
tertiary hue, with a legend at the row's end; Canvas nodes tinted apart from Databases. **"Show on
Road Map"** in an ordinary page's `···` opens the map focused on it at depth 1
(`WorkbenchNavState.showOnRoadMap`); depth is then set on the existing focus bar. Two layout
defects found on the desktop's first map and fixed in `shared/`: the loop declared the layout
settled on its first frame (a warm-up floor now), and repulsion was a px-space constant, ~7×
weaker at phone density than on the desktop, which is why phone nodes seeded on top of each
other stayed stacked (scaled by density³; positions clamped to the canvas). The constants are
still tuned by eye — §0.10 item 14's desktop pass owns the rest.

### 3.5 Settings

- **Appearance**: theme picker (§2.3), collapsed behind a disclosure toggle
- *(Amended 2026-09-17 — L6: the key's field is `TendrilField` — 36 dp, *API key* leading inside the hairline, *Reveal · Save · Clear* as 28 dp pills at its right — on both platforms; the desktop's sync passphrase the same; §2.2 *The desktop's field and card*.)*
- **Anthropic API key** field (for any Claude-API-assisted features, e.g. a future in-page mind-map
  generator). *(**2026-09-13, §0.6.15:** the feature that uses it exists — three verbs on a
  selection; the field is now the shared Claude section, key + model + what is sent, on both
  platforms.)* **Decided (2026-07-13)**: the app is fully local and makes zero network calls until
  this key (or Google OAuth, §3.2) is actually toggled on — no client/library is constructed at
  startup. On first toggle-on, the key is written to Android Keystore-backed encrypted storage
  (Jetpack Security `EncryptedSharedPreferences` or equivalent), never plain prefs/Room. **Corrected
  2026-08-29**: Google Calendar sync (§3.2) doesn't actually need this treatment — see §9.5, it
  never persists a refresh token or any other secret; the only local state is a plain, non-secret
  "connected" flag.
- **Sync folder permission** (SAF) — grants the one folder the continuous snapshot sync reads and
  writes: entries, habits *and* pages (§9.3, §9.4); replaces the earlier Shizuku-toggle plan.
  *(**Corrected 2026-09-06:** this read "the folder used for Habit sync and full export/import",
  which was the shape of the design before §9.4 put every record type in one folder, and it also
  collapsed two deliberately separate mechanisms. Export, Import and Restore do **not** use this
  grant — each opens its own document through the file picker, because a `.tendril` package is a
  one-off file that should be placeable anywhere (§9.4.1), while the sync folder is a standing grant
  to a directory Syncthing replicates. The shipped Settings screen already draws the same
  distinction in its own subtitle.)*
- **Full data import/export** across every page (all pages/projects/databases, tasks, habits,
  events) — **Restore from backup** (full replace) and full-app **Export**/**Import** (additive)
  live here as distinct actions; see §9.4.1 for the format and the merge-vs-replace design
- Every other agnostic, cross-cutting setting belongs here by default unless it's specific enough to
  a single page to live in that page's own settings (the pattern already established for Calendar's
  Google Calendar connect control and Tasks & Habits' sync-folder picker)
- *(**As built 2026-09-21.** The phone's screen, top to bottom: **Appearance** (the theme section,
  §2.3, collapsed behind its disclosure), **Sync folder** and **Sync folder encryption** (§9.3, §9.4.2),
  **Claude (opt-in)** (§0.6.15), **Tasks & Habits** (the two switches — urgency, streaks), **App Lock**
  (§3.6), **Backups** (Export · Import · Restore backup · Import from Notion, §9.4.1, §7) and **Export
  as Markdown** (§0.6.7). The desktop's pane (`DesktopSettingsScreen`): **Sync folder** with its
  passphrase, **Claude**, **Density** (§2.2), **Theme**, **Opens on** (the Calendar's first view, §3.2),
  **Tasks & Habits**, **Keyboard** (the F1 card's line, §2.2), **Notification area** (§2.2) and
  **Export as Markdown**; backups stay the phone's.)*

### 3.6 App Lock (Decided 2026-08-08)

A global on-launch (and optionally on-resume-after-backgrounding) lock gate — separate from, and
orthogonal to, §3.1.2's View-Only lock and checkbox-only mode. Those exist to prevent *accidental
edits* within an already-open app; this exists to keep the app's data private from anyone else
holding the unlocked phone, which nothing in the spec previously addressed despite the app holding
medical-appointment (§5.1) and financial (§5.2.2) data on a personal, unrooted device.

- **Mechanism**: `BiometricPrompt` (fingerprint/face, whatever the device already has enrolled) with
  the device's own PIN/pattern/password as the built-in fallback — no separate in-app PIN to build
  or store. Requires `USE_BIOMETRIC` only; no new sensitive permission beyond it.
- **Settings → App Lock**: off by default, matching the app's existing "opt-in, nothing surprising"
  pattern for the Anthropic key and Google OAuth (above). Two independent toggles once enabled:
  **lock on launch** (cold start or task-switcher return after the process was killed) and **lock on
  background** (re-prompt after any backgrounding, however brief) — the second is stricter and stays
  off by default even when App Lock itself is on, since re-prompting on every app-switch would be
  disruptive for a personal-productivity app used throughout the day.
- **Widgets stay visible; the one that writes is gated (Corrected 2026-09-04).** As written on
  2026-08-08 this bullet read "Widgets are unaffected — §8's home-screen widgets show only
  Date/Monthly-grid/Agenda summary data, **not editable content**, and remain visible regardless of
  App Lock state." That was true of the three widgets that existed then. §8.1.1 added a fourth three
  weeks later whose entire purpose is editing data from the home screen with no app launch, and
  neither section was reconciled — so the two together said App Lock keeps someone holding the
  unlocked phone out of the app while the Habits widget lets that same person rewrite
  `streak`/`lastCompletedDate` from the launcher. The premise, not the conclusion, is what was
  wrong. Resolved by splitting the claim:
  - **Reading stays unaffected.** All four widgets keep rendering regardless of App Lock state,
    matching how lock-screen widgets behave system-wide. Accepted and now stated rather than implied:
    Agenda and Monthly-grid render real Entry *titles*, and Habits real habit titles — not "summary
    data" in the strict sense. Anyone holding the phone can read them without unlocking. That is the
    same exposure any calendar widget carries, and the price of the widget being useful at a glance;
    it is a deliberate limit on what App Lock protects, not an oversight.
  - **Writing is gated.** With App Lock enabled, tapping a habit in the Habits widget opens the app
    through the lock gate instead of checking in. `CheckInHabitAction` also refuses to write if it
    is reached anyway — the same UI-gate-plus-guard defense-in-depth §3.1.2 uses for the View-Only
    lock, since a Glance `ActionCallback` is dispatched straight into the app process and the
    Activity's own lock branch cannot see it.
  - With App Lock off — the default — §8.1.1's one-tap check-in is unchanged.
  - **The overdue notification's own actions are not gated (recorded 2026-09-06 — the same hole, one
    surface over).** The Done/Skip actions on §9.7's overdue notification resolve the Entry through
    `ResolveEntryUseCase` with no App Lock check anywhere in the notification code. One tap from the
    keyguard logs a completion, advances a recurring TASK's `start_date`, re-arms its alarms and
    rewrites the Calendar Provider mirror — exactly the class of write the bullet above exists to
    stop, reached by a route the bullet above never considered. The Habits widget was reconciled with
    App Lock on 2026-09-04 and its code comment states the reasoning; the notification path was
    never reconciled with anything.
    *(**Closed 2026-09-22.** Both halves of the widget's pattern, and a third surface this bullet
    did not know about.*
    - *UI gate: `EntryActionReceiver.pendingIntent` returns an **Activity** PendingIntent while App
      Lock is on, so the action opens the app through the lock instead of broadcasting. Decided in
      that one factory rather than at each call site, so a future notification that adds a Done
      button cannot forget it; targeting the Activity also sidesteps the background-activity-start
      restriction a receiver launching one would hit.*
    - *Guard: `resolveFromNotification` refuses while the lock is on, for a notification posted
      before it was turned on or a PendingIntent that outlived the setting. A refusal deliberately
      **leaves the notification standing** — cancelling it would make "nothing happened" look
      exactly like "done", which is worse than the original defect, since the original at least did
      what it appeared to do.*
    - ***The third surface: the habit reminder's check-off action.*** *`HabitReminderAlarmReceiver`
      handles `ACTION_CHECK_OFF` by calling `CheckInHabitUseCase.checkIn` — the same write the
      widget bullet gates, by a route neither bullet considered. This section reconciled the widget
      and then recorded the entry notification, and still missed it; it is gated now on both halves.
      Posting the reminder stays ungated on purpose: reading a habit title on the lock screen is the
      exposure this section already accepts for widgets, and suppressing the reminder would break
      the feature to protect nothing new.*
    - *Pinned by `NotificationActionAppLockTest` (5 cases). The rule is lifted out of
      `onReceive` into `resolveFromNotification` precisely so it could be pinned: a
      `BroadcastReceiver` body only runs inside a broadcast dispatch, since `goAsync()` requires
      one, so the JVM suite could not reach it — and an instrumented test would run against the
      real database on the device. That unreachability is why the hole survived sixteen days.
      `tools/mutate.py` confirms the test bites: deleting the guard is **KILLED**.*
    - *No desktop twin: `Tendril windows` has no App Lock at all (§1), and its reminder surface is
      a tray toast with no action on it, so there is nothing to gate.)*
- **Interaction with checkbox-only mode (§3.1.2): App Lock wins (corrected 2026-09-06).**
  ~~Independent, no precedence rule needed — App Lock gates entering the app at all; checkbox-only
  governs one already-open page's behavior over the lockscreen. A person can reasonably want the app
  locked normally but still let one already-open checklist page survive the lockscreen, since the two
  gate different moments (before vs. after entry).~~ Withdrawn, and kept struck because the
  "different moments" reasoning is the only record of why this was thought to need no rule. What it
  misses is that checkbox-only mode does not merely *survive* the lockscreen — it draws the app over
  the keyguard with `setShowWhenLocked`/`setTurnScreenOn`, which is precisely the authentication step
  App Lock exists to impose. Honouring both is impossible in either enabling order, and honouring
  checkbox-only silently defeats App Lock. So checkbox-only refuses to activate while App Lock is on,
  and the caller explains the refusal. That was decided and built on 2026-09-05 and recorded in
  §3.1.2 and in the Revision Log; this section endorsed the one arrangement the code refuses for a
  further day, because nobody came back to it.
- **Interaction with the View-Only lock (§3.1.2)**: no rule needed, and stated here 2026-09-06 only
  because the other two pairs are. View-Only is a read-only rule *inside* an already-unlocked app —
  imperfectly enforced, see §3.1.2's correction of the same date — and asks nothing of the keyguard,
  so it neither overrides App Lock nor is overridden by it. All three pairs among the three locks are
  now written down, which they were not before: one was ruled, one was denied and wrong, and this one
  was simply absent.

### 3.7 Canvas (built long before this record — **written up 2026-09-07**)

*(**Amended 2026-09-19 — JSON Canvas export (§0.10 item 6, §0.6.7).** A Canvas page leaves the app as a **`.canvas` file** — JSON Canvas 1.0, the format Obsidian reads and writes — in the Markdown export's zip at the page's place in the tree, with no `.md` twin (a canvas has no blocks); a note that mentions a canvas links the `.canvas` file, so the zip opens in Obsidian as a vault whose canvases work. The mapping is the spec's (`domain/canvas/JsonCanvas.kt`, tested): a text card a `text` node, a page card a `file` node at the page's zip path (vault-relative, the spec's rule; a page the export does not carry becomes a `text` node with its title), a frame a `group` node with its label, an arrow an edge — `ONE_WAY` the spec's default, `TWO_WAY` adds `fromEnd: arrow`, `NONE` sets `toEnd: none` — with its label; the board's dp rounded to the integers the spec asks for; the rows' uids as ids, so a re-export writes the same ids. No colours (a card has none). Verified by Obsidian 1.13.7 opening the phone's and the desktop's exports. The desktop gained the Markdown export in Settings the same day (the exporter was already `jvmCommon`). Import is not built.)*

*(**Amended 2026-09-18 — frames (§0.10 item 15).** A **frame** is a node of its own (`CanvasNodeType.FRAME`) with a label and a box — `text`, `width`, `height`, the columns every node carries — drawn under the cards as a region: a 3 % tint of the text, a hairline on `faint` (Obsidian's group border measures 4.6 : 1; `border` read 1.4 on the mock — the critique's #1), radius 12, the label in a pill above its top-left corner. **Nothing points at a frame**: a node belongs to it while the node's whole box lies inside the frame's (`domain/canvas/nodesInside`; Obsidian's group measured on the desktop and the phone — the `.canvas` file moved the card with the group, no parent field; JSON Canvas writes the same node), so a card dragged out simply leaves and a frame wholly inside another moves with it. **The label is the handle**: dragging it moves the frame and what it holds (`moveFrame`, one write); a click selects it (the ring), a second opens the label editor; the frame's body reads no pointer — a press there pans the board, a double-click makes a card — so a frame larger than the phone's screen never swallows a pan. The delete disc lives on the label (a frame's verbs are its label's), the resize handle at the bottom-right corner (14 dp under a pointer, 20 on the phone; the floor 212 × 122 — one card and its margins); a new frame is 388 × 212 (two cards abreast) at the visible centre with its editor open, from `+ → Frame` on both platforms. Deleting a frame keeps its cards (*Delete this frame? Its cards stay.* — Obsidian's). *Fit* and the inert card in a page take a frame's box (`fitToBoxes`). **The phone's canvas gesture, decided 2026-09-18 on Obsidian mobile measured:** a long press lifts a card or a frame and a swipe pans — the tray's rule under Touch (B§13.6 #5); a tap opens a card or edits a frame's label; the desktop keeps press-and-move. Two defects older than the frames fell out of the walk: the board's pan had read `pan` captured at first composition (one delta per gesture, both platforms) and the arrow-tap detector consumed every drag; both fixed. Collapsible frames: §0.10 item 15's remaining line — **built 2026-09-21 (S12)**: a frame **collapses to a strip** at its own top-left — the label, *· n hidden*, a chevron; 48 dp, as wide as the label needs — and hides what it holds as a fold hides (positions kept): a hand-sized frame every node whose box lies inside its rectangle, a following frame its anchor and the whole subtree (its own state — the strip stands where the anchor stood and the branch from the parent ends on it; distinct from folding the node); a relationship to a hidden card is hidden with it; a nested frame goes with its contents. The toggle: the pill's chevron under a pointer, a click on the strip, *Collapse (n inside) / Expand (n hidden)* on the frame's menu and the phone's sheet; a right-click on a pill or a strip opens the frame's menu. `canvas_nodes.folded`, no schema. Grounds: Advanced Canvas's collapsible groups (its edge re-route not taken), draw.io's container. `docs/mockups/collapsible-frames.html`, `docs/critiques/collapsible-frames-mock.md`, `collapsible-frames-function.md`.)*

*(**Amended 2026-09-18 — one canvas at two sizes (§0.10 item 7).** The canvas block in a page (§0.6.3) draws the board's own layer: `ui/canvas/CanvasLayer` — the edges `Canvas` with arrowheads and labels, the `CanvasNodeCard`s with their text or page title, on one `graphicsLayer` — composed by `CanvasBoard` with a `CanvasInteraction` (move, tap, delete, connect, the edge tap, the link drag) and by `CanvasBlockCard` with none: inert, no gesture reads a pointer, no badge or hover ring, so the block list scrolls over it (the mind map's rule, §0.6.2 — `MapLayer` at two sizes). The block stands at `fitToCards`'s scale (≤ 1×, 16 dp of margin) and is as tall as the board's shape asks at the column's width, 160…320 dp (`canvasEmbedHeightDp`, `domain/canvas/`, tested) — Obsidian's canvas embed measured: a 250 × 60 card's embed 164 px in an 828 px column, the height following the content. A card's text is drawn while the fit is ≥ 0.5 (`CANVAS_CONTENT_MIN_SCALE`), the boxes alone below it — Obsidian hides a card's content past a zoom threshold the same way. The thumbnail renderer of 2026-09-11 (boxes and centre-to-centre lines, 200 dp) is gone; there is one canvas drawing in the app.)*

*(**Amended 2026-09-17 — the canvas's cards under a pointer (L9 + L10).** On a wide window the canvas's verbs are the bar's — *Fit* (pans and zooms so every card is inside the pane with 24 dp of margin, at most 1×: a single card is centred, never blown up; `domain/canvas/CanvasView.kt`, tested) and `+` with the *Text card · Page card* menu, both landing the card at the visible centre (`contentAtPaneCentre`); the FAB is the phone's. **A double-click on the ground makes a text card under the pointer with its editor open**, and a text card whose editor closes with nothing typed is discarded — the rule that made this the scored choice over the bar alone (B 4.55 vs A 4.30; Obsidian's floating toolbar 3.05; `docs/critiques/canvas-cards-mock.md`); a card typed into stays; the phone's FAB-made cards are not pending. The badges show on hover or on the selected card (§2.2 *Under a pointer*); the caption reads *Empty card — open* under a pointer, *tap to edit* under Touch, in `onSurface` (the critique's measured 3.45 : 1 on the tint); the wide empty state says the rule: *Nothing on this canvas yet — double-click the ground for a text card, or + for a page card*. Under View-Only `+` goes, *Fit* stays (it reads), the double-click is a no-op. Ground measured live: Obsidian's Canvas. `docs/critiques/canvas-cards-function.md`.)*

**This section exists because the feature did not have one.** §3.4's 2026-09-04 correction found a
fully shipped Canvas — a page kind, three Room tables, a screen and ViewModel, snapshot sync, an
entry in the New sheet — with no Revision Log row, no §4 entity, and no mention in §1. It
acknowledged the feature and then said, correctly, that reconstructing the reasoning was not
something a correction could do in passing. This is that write-up. It describes what is in the tree
on 2026-09-07 and says so where the tree and the intent differ; where the reasoning survives only in
`PageCanvas.kt`'s and `CanvasViewModel.kt`'s doc comments, it is repeated here rather than pointed
at, because a doc comment is deleted by the refactor that invalidates it and this file is not.

**Numbered §3.7 and not §3.1.8, and §1 still says five pages.** Canvas is not a sixth nav
destination, so it does not belong in §1's count; but it is also not a Pages sub-feature in the way
§3.1.1–§3.1.7 are, because it replaces the block editor rather than adding to it — a Canvas page
never renders a `Block`. §3.6 (App Lock) is the precedent for a §3.x that is not a nav tab. The
other candidate placement was a top-level section of its own, on the §5 Database precedent — the
other page kind that is "a Page plus a companion table" — and that was declined only because §5 is
that size for the to-do sync mechanism hanging off it, which Canvas has no equivalent of.

**Where it lives (2026-09-11).** In `shared/src/commonMain`, since §0.8's first step; both
platforms render it from the shared scaffold. Until that day it was Android-only.

**What it is.** A freeform board, modelled on Obsidian Canvas: text cards and page-embed cards at
arbitrary positions, joined by user-drawn arrows that are optionally labelled and optionally
directional. It is the surface §3.4 renamed *away* from — Road Map is the generated graph of
relationships between existing pages, Canvas is the one you draw yourself — and the two must stay
distinct in naming and in code for that reason.

**Decided: a page kind, not a block type.** `PageKind.CANVAS` sits beside `PAGE` and `DATABASE`, and
a canvas is never nested inside another page's body. *(**Amended 2026-09-11 (§0.6.3):** the
page kind stands, and a canvas can now *appear* in another page's body — as a `CANVAS` block that
points at the canvas page and draws it inert until tapped, whereupon this screen fills the
viewport. The gesture-conflict argument below is answered by arming rather than by nesting a
live board: see B§9.6.)* Two arguments, one external and one internal.
Obsidian's own model is a file, never embedded content in a note. And this app already had the shape:
a Database is a Page with a 1:1 companion row plus child rows (§5.1), so Canvas reuses a structure
the merge, the router and the Pages list already understood — `WorkbenchScaffold` branches on
`page.kind` alone to choose the screen, and Canvas cost that branch one line. The alternative — a
canvas block inside a page — would have put a pannable, zoomable, gesture-hungry surface inside a
vertically scrolling block list, which is a gesture conflict with no good resolution, and would have
made §3.1.1's "nestable one level" question apply to a two-dimensional thing.

**Reaching one.** Pages → New → "Canvas" (§3.1.3's creation sheet, which Canvas joined as a fourth
starting point). A canvas page shows in the Pages list under its own icon and the label "Canvas" —
the third of §3.1's three page-card kinds. Opening it routes on `kind` to `CanvasScreen`.

**The board.** Node positions and sizes are stored in content-space float units; `scale` and `pan`
transform that space onto the screen, applied once via a single `graphicsLayer` on one content layer
so cards and arrows cannot drift out of alignment at any zoom — the failure mode of transforming
each separately. Zoom is clamped 0.3×–2.5×; a new card is dropped by inverting the screen→content
transform, so it lands where the view is currently looking rather than at a fixed origin that the
person may have panned far away from. Pinch/pan is one gesture detector on the outer box; card
drags, the link drag, and the arrow hit-test are separate detectors on the layers beneath it.

**Nodes.** `TEXT` cards hold plain text with no formatting model of their own — deliberately, on
§3.1.1's own "obvious 80% subset" reasoning: a card is a sticky note, not a second block editor
nested inside the first. `PAGE_EMBED` cards show a target page's title and icon, and tapping one
opens that page. Tapping a text card opens an editor sheet instead, because a card renders at most a
few lines on the board.

**Edges.** Drag from a card onto another to connect them; a drag that ends on no card creates
nothing, and a self-connection is refused where the edge is made rather than by the drag. Direction
cycles `ONE_WAY → TWO_WAY → NONE`, `NONE` drawing a plain line for the case where two cards are
related without the relationship having a direction. Labels are optional and blank is stored as
null, so an emptied label is absent rather than an empty string the merge would have to treat as
content.

**View-Only (§3.1.2) — shipped 2026-09-07, and the reason it needed shipping.** Canvas had neither
half of the lock: no gate in the ViewModel and no mention of `viewOnly` in the screen, so the board
both offered edits and performed them while the person had been told the app was read-only. That is
a §9.4 problem and not only a broken promise, because every canvas write travels — a node rides
inside its page's snapshot and lands on every other device, where nothing distinguishes a write
nobody chose from one they did. What shipped:
- One gate, in `launchAndTouch`. Every node and edge mutation already funnelled through that helper
  (see the sync note below), so the gate is a property of *making a canvas write* rather than a line
  each future mutation has to remember to add.
- `updateTitle` carries its own gate, because it writes the `pages` row directly and a guard
  placed only in the funnel would have left the rename — and the `updatedAt` bump it carries —
  completely ungated. *(**Corrected 2026-09-22, spec trace.** This read "the one mutation outside
  the funnel". Two more have joined it since: `trashPage` (the phone's fix PR, P5 2026-09-18) and
  `saveAsTemplate` (§0.10 item 15). Both carry the same `if (locked()) return`, so the lock holds
  on all three — the count was stale, not the guarantee. Pinned by `ViewOnlySurfacesGuardTest`,
  which has a case for the rename and one for the trash; `saveAsTemplate` under the lock is
  gated and untested.)*
- Refusing before the write also refuses the bump. A bump under the lock would be a claim of
  authorship for an edit nobody made, which is enough on its own to outrank a real edit waiting on
  another device (§9.4).
- In the UI: the add FAB is hidden rather than disabled (matching the Pages hub — nothing else is on
  that control, so a greyed-out one would only advertise a refusal), the empty state drops the half
  of its message that invites a tap on a control that is gone, and the delete-card dialog re-checks
  the flag rather than trusting the hidden badge that opened it, since the toggle can be flipped
  from the Pages topbar while the dialog is already open.
- Both editor sheets stay *openable* and become readers. The board draws only a few lines of a card
  and never draws an arrow's label at all, so refusing to open them would hide content rather than
  protect it; what they lose is every control that writes.

**One decided exemption: the lazy `PageCanvas` shell.** Opening a canvas page with no companion row
creates one, ungated and untouched — the same exemption as a database's default view, for the same
two reasons. It is idempotent repair-on-open rather than an edit, and gating it would leave such a
page permanently unopenable-as-a-canvas for as long as View-Only is on; and it claims no authorship,
staying outside the bump so that merely *looking* at a board can never outrank a real edit made
elsewhere and not yet synced. Every device performs the same repair for itself.

**Sync (§9.4).** A canvas travels inside its own page's snapshot record, never as its own file, and
is exported only for `kind == CANVAS` pages. Three consequences worth stating, because each one is a
decision:
- **In the snapshot, nodes carry their `uid` and edges do not** — both tables have one in Room, but
  an edge's is never written out. An edge is referenced from nowhere else, so its identity only has
  to be stable *within* one canvas record, and the pair of node uids it connects already is that
  identity. The cost is that a re-merged edge is a new row locally; nothing reads an edge id, so
  nothing notices.
- **A page-embed target travels as the target's `uid`**, not a local row id, and resolves against
  the incoming batch on arrival. A target that has not arrived yet resolves to null: the card
  survives as an empty embed rather than the record failing.
- **A winning record replaces the whole node and edge set.** Blind delete-and-reinsert is safe here
  in a way it explicitly is not for Properties (§9.4), because nothing outside this subsystem
  references a node id — no cell, no tombstone, no other table. The delete runs only after every
  node type and arrow direction in the record has decoded, which is the ordering Milestone 0's
  quarantine policy generalised: a decode that fails after the delete has destroyed the local copy
  of the thing it was replacing.
- Because the merge gates all of this on `pages.updated_at`, every canvas mutation goes through
  `launchAndTouch`, which writes and bumps as one operation. A canvas edit that forgets the bump
  looks completely saved on the device that made it and simply never arrives on the other one —
  this is the §9.4 write-path defect, seen from the Canvas end.

**Not available on desktop.** `Tendril windows` renders `NotAvailableOnDesktop("Canvas")` for the
canvas slot; the screen and ViewModel live in `Tendril android`, not in `shared\`, unlike the block
editor Milestone 3 moved. The asymmetry to be aware of is that the *data* is shared even though the
surface is not: the entities, the DAOs and the merge pass are all in `shared/commonMain`, so a
desktop sync reads, merges and re-exports canvas nodes and edges it has no way to draw. That is the
correct behaviour — a client must not drop what it cannot render — but it means the desktop is a
full participant in canvas sync while showing a placeholder.

*(**Superseded 2026-09-22, spec trace — the paragraph above describes a state that no longer
exists, and is kept only so the drift is legible.** Canvas is on the desktop and has been since
the screen moved: `CanvasScreen` and `CanvasViewModel` are in
`shared/src/commonMain/kotlin/com/tendril/app/ui/canvas/`, not in `Tendril android`, and
`ui/nav/PageRoute.kt` routes `PageKind.CANVAS` straight to `CanvasScreen` for both callers. The
desktop reaches that route through `WorkbenchScaffold` (`Main.kt:326`) and through its pop-out
windows (`PopOutWindows.kt:224`). No `NotAvailableOnDesktop("Canvas")` stand-in remains anywhere
in `Tendril windows/src` — `WorkbenchScaffold`'s own comment records that the placeholders the
slot pattern was built for are gone. So the sync asymmetry this paragraph explains is not a
live consideration: the desktop draws what it merges.

The part worth keeping: **the companion spec was right and this one was not.** `Tendril
windows/tendril-windows-spec.md` has carried a Revision Log row reading "2026-09-11 (Canvas on
desktop) — `shared/` gains `ui/canvas/` … moved from the Android app" since the day it happened,
and records the stand-in being replaced. So the two documents contradicted each other for eleven
days and nothing noticed, because nothing reads them together. That is the same failure the
both-platforms rule was written for after row 1.17, one level up: the rule says to check the other
platform's *code* before writing a finding, and this says to check the other platform's *record*
before trusting this one. Found by tracing §3.7's claims against the tree; no code changed.)*

**Known open defects, recorded here rather than only in `docs/audit-2026-09-04.md`** (§1.1 and §1.2
there; both re-read against the tree on 2026-09-07 and both still present): the arrow hit-test is
implemented inside a drag detector, whose callback only fires after touch slop, so a *tap* on an
arrow never reaches it — arrows cannot be selected, relabelled or deleted from the board at all,
which also makes the View-Only note above ("tapping an arrow to read its label stays live")
describe an affordance that does not currently work. And the arrow editor operates on the captured
snapshot of the edge it was opened with, so direction never cycles past one step and the displayed
direction goes stale. Neither is a data-loss bug; both make a shipped feature partly unreachable.

**Both closed 2026-09-22 (spec trace), and the second was worse than this recorded.**

*The hit-test* had already been fixed before this trace, on Item 15's walk, and the paragraph above
simply never caught up: the tap is now an `awaitEachGesture` loop that takes the first down without
consuming it and acts on `waitForUpOrCancellation`, so a drag pans and a tap picks. The comment at
the call site records why the old `detectDragGestures` form had to go — it consumed every drag past
touch slop and cancelled the board's pan on both platforms, moving a 380 px swipe by 40.

*The stale editor* was still live, and the consequence recorded here — a direction that will not
cycle past one step — is the mild half. The sheet's label field calls `onSetLabel` on **every
keystroke**, and `setEdgeLabel` wrote `edge.copy(label = …)` on the snapshot the sheet was opened
with. So changing an arrow's direction and then typing in its label put the *opening* direction
back: a change that had landed was silently undone by typing, which is a data-loss bug, and this
section said there was none. Proved red before green by `CanvasStaleEdgeTest` (3 cases), which
drives the ViewModel the way the sheet does — one captured `CanvasEdge`, used more than once.

The fix follows §9.7's precedent rather than patching the two symptoms. Leaving correctness to
each call site holding fresh state is what produced four separate re-arm defects, so both edge
writes now re-read the row through the new `CanvasEdgeDao.getById` and take only `id` from their
argument; a stale snapshot is merely stale. `?: return` covers an edge deleted under an open sheet,
or replaced wholesale by a merge — §9.4's Pass 4 re-inserts, so the id genuinely goes. Separately
`CanvasScreen` resolves `editingEdge` against the live `edges` list, which is what makes the
sheet's "Direction: …" line *show* the new value; that half is pinned by the walk, not by a test.
A `@Query` adds no schema version, so the migration chain is untouched.

---

## 4. Data Model Sketch

**Status: reviewed 2026-07-13.** The Task/Event relationship (below), Habits architecture, and to-do
eligibility questions that were open when this section was first drafted are now resolved. Still a
starting point for the real schema work, not a byte-for-byte ratified design.

**This table is partial, and now says so (recorded 2026-09-06).** Seven entities registered on the
database have no row here at all — `PageCanvas`/`CanvasNode`/`CanvasEdge` (§3.4, which already notes
its own absence), `PageRelation`, `PurgedRecord` (§5.5.1.1), `PropertyValue`, and the FTS entry
(§3.1.1) — and until this pass no row named `uid`, the cross-device identity every merge in §9.4
turns on. *(**Partly closed 2026-09-07:** the three Canvas entities now have rows below, alongside
the functional record at §3.7 they were missing. **Four remain unwritten** — `PageRelation`,
`PurgedRecord`, `PropertyValue` and the FTS entry — and the sentence above is kept whole rather than
edited down to four, because the size of the original omission is the reason this paragraph exists.)*
*(**Closed 2026-09-21 — the register levelled with schema v24.** The four rows are written below, with
`HabitCompletion` and `TimeLog`, which had also gone unlisted; every row's key fields were read from the
entity classes, so `uid`, the later columns (`due_date`, `parent_entry_id`, `estimate`, the two provider
ids on Entry; `label_id`, `label_confirmed`, `last_reviewed_at`, `due_date_property_id` and `hue` on
Database; `FRAME` and `hue` on CanvasNode; `order` on Property and DatabaseView; `deleted_at` on Reminder)
and the block's own fields appear where they were missing. Twenty-two tables, twenty-two rows.)* That matters more than tidiness because of where this section is pointed at from:
`tendril-windows-spec.md` sends a desktop reader here first for what a `shared\` type or table
actually means, and §12 lists the Room schema among the things that stay in this file. A register
that omits a third of the schema without saying so sends that reader away with a wrong model. The
missing rows are not written in this pass — filling them in is real work rather than a correction —
but the omission is on the record instead of being inferred.

**Core entities:**

| Entity | Key fields | Notes |
|---|---|---|
| **Page** | id, uid, title, icon, kind, parent_id, database_id (nullable), is_template, deleted_at (nullable, added 2026-08-08 — Trash, §5.5.1), created/updated | `kind` distinguishes a plain page from a database. `parent_id` builds the page tree (also what Notion import needs to reconstruct, §7). Body content is a structured `Block` list (§3.1.1), not a blob field. **`category` removed 2026-08-08** — carried from an early draft with no behavior ever specified behind it; replaced by the `Tag`/`PageTag` entities below (§3.1.6). **Corrected 2026-09-06:** `kind` is three-valued — `PAGE` \| `DATABASE` \| `CANVAS` — the third having arrived with the Canvas feature §3.4 records, while this cell still described the two-valued version; and three key fields were missing from it. `uid` is the cross-device identity every merge in §9.4 is keyed on. `database_id` is listed here rather than only under **Row** because a row *is* a page (§5.1): the link physically lives on this table, and reading it as a Row-table column is exactly the mistake §5.1's row=page model exists to prevent. `is_template` (§3.1.3) is what keeps saved templates out of the Pages list and out of Road Map's edge set. |
| **Tag** *(code: `Label`, §0.6.9)* | id, uid, name, color | Global, freeform, reusable across all Pages (§3.1.6, added 2026-08-08) — resolves the removed `category` field. Flat, no nesting. |
| **PageTag** *(code: `PageLabel`, §0.6.9)* | page_id, tag_id | Many-to-many join table between Page and Tag (§3.1.6, added 2026-08-08). |
| **Block** | id, uid, page_id, type, order, parent_block_id (nullable), content, formatting spans, checked (to-do), code_language, callout_icon, callout_color, mentioned_page_id (nullable — PAGE_MENTION), referenced_block_uid (nullable, v17 — §0.6.12), toggle_expanded, mind_map (v12 — §0.6.2), image_path, created/updated | One row per content block inside a Page's (or Row's) body (§3.1.1) — paragraph, heading, list item, code, image, toggle, callout, page-mention, etc. Feeds the FTS index (§3.1.1). |
| **PageCanvas** *(added to this table 2026-09-07 — the entity has existed since the Canvas feature shipped, §3.7)* | id, uid, page_id (unique, FK → Page, cascade delete), created/updated, **structure** *(v23 — the board's, a `CanvasStructure` key, `free` by default)* | The 1:1 companion row that makes a `kind = CANVAS` Page a board — structurally the same move as **Database** below, which is why Canvas cost the router one branch. Created **lazily on first open**, not at page creation, and that write is deliberately exempt from both the View-Only gate and the `updated_at` bump (§3.7): it is repair-on-open, not an edit, and bumping it would let merely opening a board outrank a real edit made on another device (§9.4). Carries no content of its own; the board is its child rows. |
| **CanvasNode** *(added 2026-09-07, §3.7)* | id, uid, canvas_id (FK → PageCanvas, cascade), type (`TEXT` \| `PAGE_EMBED` \| `FRAME` — *the frame since item 15, 2026-09-18*), x, y, width, height, **parent_id, folded, structure** *(v23, the mind-map pass — the tree: one parent by construction, no FK; a frame with a parent follows its subtree)*, **hue** *(v24, S13 — 0–359 or null, §2.3's token map)*, text (nullable — TEXT only), embedded_page_id (nullable, FK → Page, cascade — PAGE_EMBED only), created/updated | One card on the board. Position and size are content-space floats, not pixels — the screen transform is applied once at render (§3.7), so the same board is the same board at any zoom or on any screen size. `text` is plain, with no `Block` model of its own: a card is a sticky note, not a second page editor (§3.1.1's "obvious 80% subset" reasoning). `embedded_page_id` cascades from Page, so deleting the embedded page removes the card — the one place a canvas is changed by an action taken outside it. In the snapshot the target travels as the page's `uid` and resolves on arrival; unresolvable means an empty card, never a rejected record. |
| **PageRevision** *(added 2026-09-13, §0.6.13)* | id, page_id (FK → Page, cascade), taken_at, reason (`EDIT` \| `MERGE` \| `RESTORE`), title, blocks_json, block_count | A page's kept body — this device's only, never synced or archived. |
| **CanvasEdge** *(added 2026-09-07, §3.7)* | id, uid, canvas_id (FK → PageCanvas, cascade), from_node_id, to_node_id (both FK → CanvasNode, cascade), direction (`NONE` \| `ONE_WAY` \| `TWO_WAY`), label (nullable) | One arrow. The `uid` exists in Room but is **not written to the snapshot** — an edge is referenced from nowhere else, so its identity only needs to be stable within one canvas record, and the node-uid pair it connects supplies that (§3.7). `direction = NONE` draws a plain line, for two cards that are related without the relation having a direction. A blank label is stored as null rather than `""`, so an emptied label is absence rather than content the merge has to carry. Both endpoint cascades are what makes deleting a card delete its arrows, with no application-level cleanup. |
| **Database** *(code: `PageDatabase`)* | id, uid, page_id — *(a Page with a schema)* — schema (ordered Property list), `sync_to_tasks` flag, `done_property_id`, `deadline_property_id`, `due_date_property_id` (v11 — §0.8 step 2b), `recurrence_property_id` (nullable), `blocked_by_property_id` (nullable, v19 — §0.6.14), `label_id` + `label_confirmed` (v13 — §0.6.8, the schema on a label), `last_reviewed_at` (v15 — §0.6.11), **`hue`** (v24, S13 — null for the title's hash), created/updated | The Sync-to-Tasks flag and the explicit property bindings are the mechanism from §5.2 — not inferred from schema shape, always deliberate. `recurrence_property_id` added 2026-07-16 — see §5.2 for the binding and §4's Property type note below for the `Interval` type it points at. |
| **DatabaseView** *(code: `PageDatabaseView`)* | id, uid, database_id, name, order, view_type (`TABLE` \| `BOARD` \| `GALLERY` \| `CALENDAR` \| `TIMELINE`), group_by_property_id (nullable, BOARD-only), date_property_id (nullable, CALENDAR's day / TIMELINE's start), end_date_property_id (nullable, TIMELINE-only, v19), visible_property_ids, filter (single condition, nullable), sort_property_id (nullable), sort_direction | Saved views over a Database's rows (§5.6, added 2026-08-08) — display configuration only, never alters stored row/property data. A Database always has at least one Table view (default, matches §5.1's existing behavior). |
| **Property** | id, uid, database_id, name, type, config (a JSON blob per type — a Select's options, a Relation's `targetDatabasePageUid` / `reversePropertyUid`, a Rollup's `relationPropertyUid` / `targetPropertyUid` / `aggregation`, a Formula's expression), order | Type list needs to cover at minimum: text, number, checkbox, select, multi-select, date, URL, email, phone — the set Notion CSV export can actually carry (§7). ~~Relation/rollup/formula were explicitly deferred (§7, §10)~~ — **reopened 2026-09-06, see §5.4**; they now land together as a single `COMPUTED` type plus a stored `RELATION` type. **Added 2026-07-16**: `Interval` (number + unit ∈ {day, week, month}) — a Tendril-native type, not part of the Notion CSV import set, used exclusively as the `recurrence_property_id` binding target (§5.2). Not offered as a general-purpose property type in the "New property" picker outside that binding context, to avoid a second, uglier way to represent a plain number. |
| **Row** | id, database_id, property values | A Row *is* a page (§5.1) — its free-form body beneath the properties is the same Block-based content as any Page (§3.1.1), matching Notion's actual row=page model. Tapping a row opens it. |
| **Entry** *(renamed from Task, 2026-07-13)* | id, uid, title, kind (`TASK` \| `EVENT`), start_date (nullable), start_time (nullable), end_date (nullable, EVENT-only — see below), end_time (nullable, EVENT-only), recurrence_rule (typed, see below), original_entry_id (nullable, FK to another Entry), original_occurrence_date (nullable), is_exception_skip (nullable), status (`PENDING` \| `DONE` \| `SKIPPED`, TASK-only), importance (0–4, **v20**, §0.6.4 as amended — replaces the v10 `important` flag), due_date (nullable — the Deadline, v10 §0.6.4), parent_entry_id (nullable — a step's task, v10 §0.6.4), estimate (minutes, nullable — v10 §0.6.5), source_row_id (nullable, FK to Row — **added 2026-07-16**, see below), deleted_at (nullable, **added 2026-08-08** — Trash, §5.5.1), source, google_event_id + provider_event_id (nullable — §9.5.1, §9.11), created/updated | Calendar queries `WHERE start_date IS NOT NULL` regardless of kind. Tasks view queries `WHERE kind = TASK`. Merged view (§3.3) also filters `kind = TASK`. Both queries also filter `WHERE deleted_at IS NULL`. `source_row_id` is the actual foreign key behind the Row↔Entry link §5.2 has described behaviorally since it was designed but never named as a real field — a small, previously-invisible gap surfaced while designing the rebind mechanism (§5.2). Full reasoning for the multi-day, recurrence-split, and exception design below the table (2026-07-14 case-scenario round 2). |
| **CheckIn** | id, uid, **date**, at, mood (1–5, nullable), energy (1–5, nullable), deleted_at — *added 2026-09-19, v22, §0.10 item 4: one tap on a Journal day, one scale per row; tombstoned like a habit completion* |
| **Habit** | id, uid, title, time (nullable), duration (nullable), frequency, **unit, amount_per_check_in, daily_amount** (nullable — *added 2026-09-19, v21, §0.10 item 3: set, the habit counts*), streak, **last_completed_date** (nullable — *listed here 2026-09-06; the field itself has always existed*), previous_streak, previous_completed_date (nullable, **added 2026-08-29** — undo-check-in, §8.1.1), deleted_at (nullable, **added 2026-08-08** — Trash, §5.5.1), created/updated | Structurally separate from Task (Decided 2026-07-13, §3.3) — does not follow the to-do database pattern. `last_completed_date` is the date the current streak run last extended: it is what "already checked in today" is computed from, and what a missed period resets against without ever surfacing backlog (§6.1). Leaving it out was not harmless — `previous_completed_date` sat here as the undo counterpart of a field this table never named, and §3.6 had to refer to it by its code name in order to describe what the Habits widget writes. |
| **Reminder** | id, uid (v9), entry_id, offset (a `ReminderOffset` — a preset or a custom number + unit), anchor_time (nullable), deleted_at (nullable — tombstoned for the sync) | Presets: 1h / 2h / 4h / 8h / 1 day / 2 days / 1 week; or custom (number + unit). No cap on count. Applies to either kind — a birthday reminder is just as valid as a deadline reminder. **Decided 2026-07-14**: when the day a reminder anchors to has no `start_time`/`end_time` (an all-day Event, or one of the all-day middle days of a multi-day span), the reminder needs a concrete time-of-day to actually fire at — prompted via presets (8:00 AM / 10:00 AM / 12:00 PM / 4:00 PM / custom), defaulting to midnight if none is chosen. Stored in `anchor_time`, unused when the Entry already has a real time on the relevant day. Separately, `kind = TASK` Entries also get an automatic **overdue notification** at `start_date`+`start_time` (or midnight if `start_time` is null) with inline Done/Skip actions — distinct from these pre-due reminders, and the trigger for the Skip-only-once-overdue rule above; folded into §9.7. **Device-local, and not synced (recorded 2026-09-06).** A Reminder appears in no snapshot record and in no `.tendril` package — the Entry snapshot has no reminders field and there is no `reminders.json` anywhere — so a reminder set on the phone does not exist on the desktop and never has. The loss is active rather than passive: `Reminder` cascades on `Entry`, and Restore-from-backup deletes every Entry before merging, so restoring a backup silently destroys every reminder on the device and restores none of them. §9.4.1's "one schema, reused everywhere" reads as though this table travelled with the rest of the model; it does not. Either it joins the snapshot format, or that omission belongs in §9.4's list of what travels as a decision rather than as an accident. **(Resolved 2026-09-09 — S2: it joined the snapshot format.)** `Reminder` and `EntryCompletion` both carry a `uid` as of schema v9, travel as `reminders.json` and `entry_completions.json` in the sync folder, and are written into and read back out of a `.tendril` package. The restore-destroys-every-reminder loss described above is fixed twice over: the archive now carries them, and `restoreFromBackup` clears the table explicitly rather than relying on the `Entry` cascade. **A reminder is now soft-deleted** — the merge unions records by uid, so a hard delete would have let a reminder deleted on one device return from another's copy and re-register its alarm; `Reminder.deletedAt` is the tombstone that prevents it, and the two per-entry reads filter on it so nothing that schedules alarms can see one. |
| **EntryCompletion** *(new, 2026-07-14 — resolves round 1's open history question)* | id, uid (v9), entry_id, occurrence_date, resolved_at, status (`DONE` \| `SKIPPED`) | Append-only log, written by the same centralized resolution step that advances a recurring Entry (below) — every TASK resolution gets logged uniformly, recurring or not, so a future "history" screen has one consistent source rather than recurring Tasks silently having no record (per round 1's finding) while one-off Tasks do. |
| **PropertyValue** *(written 2026-09-21)* | id, property_id (FK → Property, cascade), row_page_id (FK → Page, cascade), value (nullable text — one string per type: a date as ISO, a select as its option, a relation as row uids) | One cell. Unique on (property_id, row_page_id); indexed by row. A bound column (When, Deadline, Done) is read through the row's Entry, not here — §5.2's mechanism. |
| **PageRelation** *(written 2026-09-21)* | id, uid, from_page_id, to_page_id (both FK → Page, cascade), created_at | One manual *Relate to* edge on the Road Map (§3.4); mention edges are derived from blocks and never stored. Travels by the two pages' uids. |
| **HabitCompletion** *(written 2026-09-21)* | id, uid, habit_id (FK → Habit, cascade), date, checked_at, value (nullable — v21, a counting habit's amount per check-in, §0.10 item 3), deleted_at (nullable — the undo's tombstone) | The habit log (§0.6.6): one row per check-in, repeatable on a counting habit; presence and the month of dots read it. |
| **TimeLog** *(written 2026-09-21)* | id, uid, entry_id or habit_id (one of the two, nullable), started_at, ended_at (nullable while running), is_running, deleted_at, updated_at | §0.6.5's timer: one row per run of the clock on a task or a habit; *Tracked today* and the estimate's bar sum it. |
| **PurgedRecord** *(written 2026-09-21)* | kind (`PurgedKind`: `PAGE` \| `ENTRY` \| `HABIT` \| `PROPERTY`), uid, purged_at — keyed on (kind, uid) | §5.5.1.1's tombstone of a hard delete, so a purged record arriving from an older snapshot is refused rather than resurrected. |
| **BlockFts** *(v25, 2026-09-22)* | page_id, block_id, plain_text — an FTS4 virtual table (`block_fts`), with `BlockSearchHit(page_id, block_id, title, icon, snippet)` as the query's shape | §3.1.1's per-block index: one row per block with text, rebuilt with the page's `page_fts` row on every edit and healed on open (the first launch after v25 fills it). Never synced. |
| **PageFts** *(written 2026-09-21)* | page_id (the row id), plain_text — an FTS4 virtual table (`page_fts`), with `PageSearchHit(page_id, title, icon, snippet)` as the query's shape | §3.1.1's index: the title and the blocks' text (v16 emptied it so titles are indexed), rebuilt per page on every edit and healed on open. Never synced — each device indexes its own. |

**Decided (2026-07-13), corrected (2026-07-14):** Task and Event are the *same underlying table* —
Calendar and Tasks are two queries over one row, avoiding the dual-write/id-mapping/drift risk of
two synced tables — but they are **not the same *kind* of row**. The first pass of this decision
missed that: a birthday, a holiday, or a plain meeting has no meaningful done/not-done state, and
would have wrongly landed in the Tasks list with a checkbox under a single undifferentiated table.
The fix is the `kind: TASK | EVENT` discriminator on the renamed `Entry` entity above — one table
for the sync benefit, one extra column for the actionability distinction that was actually needed.
This still resolves the item that touched Calendar, Tasks, Habits' Merged view, and the widget
Agenda list simultaneously, now correctly.

*(**Amended 2026-09-20 — the mind-map pass.** The canvas gained **the tree** (`domain/canvas/Tree.kt`): a node with a `parentId` is a branch of that node — one parent by construction, never stored as an arrow — and the board and any node carry a **structure** (*Free · Map · Right · Down*; Xmind's structure-per-topic, measured) that **Tidy** applies to the subtree: the root stays, siblings stack in bands centred on their parent (each side its own stack under Map — the first half of a root's children in creation order to the right), Down stacks bands under the parent; a dragged node is free until the next Tidy (Freeplane's free node); a new child lands after its last sibling (`newChildPosition`). **The grammar by level** — the map structures: the root at `pageTitle` in a bordered box (220 × 64), a branch the strip, deeper nodes **text on the line** (their branch runs on as the underline); Down: the strip at every level (Mindomo's org chart); a page card always a strip; a parentless card with no children a strip. **Branches** are headless — a curve from the parent's side, an elbow under Down — in one colour per main branch (the register's fanned hues, three cycling), 2 dp at the first level, 1.5 deeper. **Relationships** are the `canvas_edges` rows as before, drawn dashed at 1.5 dp in `dim` with their head on the target's edge and their label on the line, **routed from the outer sides** — the side away from each node's parent — so they never cross the branch they relate (`relationRoute`). **Fold**: a node folds its subtree; the branch ends in a 20 dp badge with the hidden count (a click unfolds); a folded strip draws no link handle. **A frame with a parent follows that node's subtree** (Xmind's boundary, Freeplane's cloud): its box is the visible subtree's bounds plus 12 dp, its stroke `dim`, no resize handle; a hand-sized frame is unchanged. **The verbs**: the structure's check-menu in the bar (*Map ▾*, the Calendar's pattern) with *Tidy now*, and *Tidy*; a node's menu (a right-click; the phone's editor sheet under *Tree*) — Edit · Add child (Insert; Tab where the platform passes it — AWT keeps Tab for focus traversal) · Add sibling (Enter) · Fold / Unfold · Structure ▸ (Inherit or one of the four) · Frame this subtree · Detach from the tree · Delete — *Edit* and the key chips only under a pointer (the phone's sheet *is* the editor), a page card offers *Open page* in Edit's place, *Fold* only on a node with children (*Unfold (n hidden)* once folded), *Frame this subtree* only with children, *Detach* only with a parent, and Delete asks (*Its n children move up a level; any relationships go too.*); a card **dragged onto another card becomes its child** (the target lights with the accent ring); a node moves with its subtree; deleting a node lifts its children to its parent, never a cascade. The sync record carries `parentUid`, `folded`, `structure` per node and the board's `structure`; a peer from before reads a free board. The JSON Canvas export writes the tree as headless edges (`toEnd: none`, id `<uid>-tree`) and a following frame at its computed box. The outline's mind map (§0.6.2) is drawn by the same grammar and painters (`ui/canvas/TreeGrammar.kt`) — its structure is Right, its tree the outline's. The canvas block's fit (item 7) counts a frame's label pill — the 28 dp above its box. Not this PR: ~~Timeline and Fishbone (the list's last two, once Down proves the elbow)~~ *(S9, built 2026-09-21 — the amendment below)*, ~~sibling reorder by drag (order is creation order)~~ *(S8, built 2026-09-20 — see the amendment below)*, Xmind's summary and Freeplane's attributes (recorded). `docs/critiques/mind-map-grounds.md`, `docs/mockups/mind-map.html`, `docs/critiques/mind-map-mock.md`, `mind-map-function.md`.)*

*(**Amended 2026-09-21 — the spine structures (S9).** The list's last two: **Timeline** — a spine from the root's right edge at its centre (`dim`, 2 dp), the main topics along it **alternating above and below** (Xmind's off-axis timeline, measured live), each on a 8 dp dot with a 24 dp stem, its subtree **an org chart growing away from the spine** (Down's row, mirrored above), the strip at every level; a topic's slot is its band, the same side never overlaps, the two sides interleave with the anchors at least 48 dp apart. **Fishbone** — the head at the left (Xmind's default; the classic Ishikawa's right-hand head recorded, not taken), **ribs leaning forward at 60°** (60.2 measured) as long as their bones need (96 dp at least, 28 dp a bone), the main topic a strip at the rib's end, **level 2 as bones off the rib — text on the line**, evenly along it, deeper levels stacked right of their bone; a foot is placed after the same side's previous rib is clear (its strip and its bones brought back to the spine along the slope) and at least 48 dp past the last foot. A rib is derived from the topic's box alone (`ribOf`), so a dragged topic keeps a straight rib and a bone stays on it. **A side is the node's own** — its centre above or below the spine (S8's rule turned on its side); a new child takes the other side; a drop tidies at once. Both join the board's and the node's structure menus on both platforms; the outline's map keeps Map. `docs/mockups/timeline-fishbone.html` (generated by the layout rule), `docs/critiques/timeline-fishbone-mock.md`, `timeline-fishbone-function.md`.)*

*(**Amended 2026-09-20 — sibling order by drag (S8).** A sibling's order is **its position's** — y on a side, x under Down (no column); under Map a root's child is on **the side its own centre lies on**, so a drag across the root moves it over (Xmind's), and a new child goes to the side with fewer children, the right on a tie — the creation-order half split is gone. A tree node **dropped among its siblings** (not onto a card) under Map, Right or Down **tidies its tree at once** from the root (`tidyTreeOf`; the whole tree, since a band that grew moves its neighbours); only on a *Free* board does a dragged node stay where the hand left it. `docs/critiques/sibling-order-function.md`.)*

*(**Amended 2026-09-20 — the card's width.** The user: the boxes shorter by default, growing with the text, wrapping past a length — "to avoid overlap when there are many short text boxes" — and a 1 px border around the cards. The rule was **chosen by `meta-optimizer`** over the user's own texts against Xmind measured live (`docs/critiques/card-width-optimizer.md`): a card is **as wide as its text asks, between 60 and 220 dp, 8 dp of padding a side**, then it wraps (`cardWidth`; **the text's width is the font's own** since S14, 2026-09-21 — `domain/canvas/TextWidth.kt`, Inter's 95 ASCII advances at 14 sp read from the bundled file, summed with a 5 % margin, `wrappedLines` wrapping words on them; it had been a flat 8.5 dp a character, which cut *Weeds* and let *Lawn clippings* sit in air, and which hid `CANVAS_CARD_PAD` saying 8 while the card drew 12 — the constant is 12 now, what is drawn; a leaf reads the same table, `docs/critiques/leaf-width-function.md`; a drawing — the canvas, the mind map — keeps the desktop's typography under every profile (`DrawingType`, 2026-09-21), since its text is measured at 14 sp and zoomed by the person, not stepped by the chrome); fitness 0.847 against 0.466 for the fixed 200 strip (its overlap score 0.233 was the complaint, measured) and 0.761 for widths snapped to steps. Every strip takes it — free, branch and page cards (a page card is its title's width; `CanvasTree` carries the titles) and the outline's map; the root keeps its 220 × 64 box; a leaf its line. An empty card is its caption's width (*Empty card* — the card is the verb). **A hand may set a card's width** (Obsidian's): a handle straddling the **left** edge's middle on a selected card under a pointer — the right edge stays, the `width` column is written (read as a hand's only when it is neither 0 nor the entity's old 180 — `handWidth`), the text keeps deciding the height. Every strip wears **a 1 dp `outline` hairline**; the card is a `Box`, not a `Surface`, so its badges may **straddle the right edge** (the frame's handle straddles its corner the same way) instead of reserving a 28 dp column that was half of a short card. The phone's `···` gains *Tidy* and *Fit every card* (its bar holds only `···`; Fit had been missing on the phone).)*

*(**Amended 2026-09-20 — the canvas card.** A card is a strip that grows with its text: ~~200 dp wide~~ (its text's width since the card's-width amendment above), 48 dp tall with one line, 68 with two, 88 with three, the estimate the mind map uses (`cardLines` / `cardHeight` / `nodeBox` in `domain/canvas/Frames.kt`) so the board, the arrows, the embed's fit and the JSON Canvas export read one box — Obsidian's proportion (250 × 60 CSS px at rest, measured on L9), after the user found 180 × 90 dp around one line disproportionate on Garden plan. A page card is one line, its glyph beside the title. Frames keep their stored sizes; the handle resizes them. The card's badges stand in a column at its right edge (× over the link handle) and the text keeps a 28 dp end margin — the strip's spare width, never its height; the text never moves. `docs/critiques/desktop-type-grounds.md` § The cards.)*

### 4.1 Entry case-scenario review (2026-07-14, two rounds)

**Round 1** fixed: multi-day span (`end_date`/`end_time`), the TASK/EVENT recurrence split, `status`
as a tri-state, the all-day reminder time-anchor picker, and the overdue notification. **Round 2**,
testing cases that exercise two features at once rather than one feature in isolation, found four
more:

**Multi-day only applies to EVENT.** Confirmed 2026-07-14: a Task is never a span — the person sets
when it should start and resolves it (Done/Skipped) whenever they actually got to it, however late.
`end_date`/`end_time` are populated for `kind = EVENT` only; a `kind = TASK` row leaves them
permanently null, not just typically null.

**Recurring EVENT with a duration, and single-occurrence exceptions.** A recurring multi-day EVENT
("team offsite, 3 days, every quarter") needs each virtual occurrence to preserve the *duration*
(`end_date − start_date`), not repeat the original absolute `end_date` — stated explicitly now since
the field existing doesn't mean the interaction was ever written down. Separately, single-occurrence
editing/deleting for recurring Events is in scope (Decided 2026-07-14, matching Fossify Calendar's
own behavior — its CalDAV sync data shows exactly the standard iCalendar pattern: a skipped
occurrence is an EXDATE-style exclusion, an edited-just-this-once occurrence is a separate record
sharing the base event's identity but overriding that one instance's fields, RFC5545's
`RECURRENCE-ID` mechanism). Rather than a new table, an exception is just another `Entry` row:
`original_entry_id` set (pointing at the recurring base) plus `original_occurrence_date` (which
occurrence it overrides); if `is_exception_skip = true` it's a tombstone (skip that date, nothing
else on the row matters); otherwise it's a full override with its own independent
`start_date`/`start_time`/`end_date`/`end_time`/title. This deliberately mirrors
`CalendarContract.Events`' own `ORIGINAL_ID`/`ORIGINAL_INSTANCE_TIME` exception-event columns, so
writing Tendril's exceptions into the system Calendar Provider at Phase 3 (§9.9) is close to a direct
field mapping rather than a translation layer that has to be invented later.

**`recurrence_rule` splits format, not just meaning (Decided 2026-07-14).** EVENT and TASK
recurrence aren't the same *shape* of data — EVENT needs real calendar expressiveness (every Monday,
every 2nd Tuesday), TASK needs only a plain repeating interval. *(**Corrected 2026-09-04:** this
paragraph originally read "an elastic interval anchored to whenever it was last resolved," and the
`AlarmScheduler` note below elaborated it as "resolve 2 days late, the gap to next time is 7+2 days,
not a fixed 7." Both are withdrawn — they contradict §6.2's anchoring rule and, through it, §6.1's
dividing test. Intervals step from the original schedule; see §6.2, which carries the surviving rule
and explains why the `Elastic` type name is now a misnomer that is kept anyway.)*
Rather than one loosely-typed string column disambiguated by convention via `kind` (the same
implicit-coupling shape that caused the original TASK/EVENT bug), `recurrence_rule` should be a
Kotlin sealed type — `RecurrenceRule.Fixed(rrule: String)` storing a real RFC5545 RRULE string for
EVENT (matching `CalendarContract.Events.RRULE` and the Google Calendar API's own recurrence format
exactly — no translation layer at Phase 3 or for Google sync; use an existing RFC5545 library such
as `lib-recur` rather than hand-rolling a parser), and `RecurrenceRule.Elastic(period: Period)`
storing an ISO-8601 duration ("P7D") for TASK, parseable natively via `java.time.Period` with no
extra dependency. A Room `TypeConverter` handles serialization; the type system, not developer
memory, then enforces that a TASK row can never carry a `Fixed` rule or vice versa.

#### 4.1.1 Occurrence expansion (Implemented 2026-09-04 — the read side `Fixed` never had)

Everything above is about *storing* a recurrence rule, and until now that is all the app did with
one. `RecurrenceRule.Fixed` was written outward — into `CalendarContract` (§9.11) and the Google
Calendar API (§9.5.1) — and never read back, so every Tendril surface rendered one stored row on one
stored day. Three things followed, all with the same root cause:

- **A recurring EVENT appeared once.** A weekly meeting showed a single entry in Tendril's own
  Calendar while recurring properly in Google Calendar, in any watch face, and in the system
  calendar **Tendril itself publishes to** — §9.11 defines that mirror's scope as "whatever
  Tendril's own Calendar screen already shows," and the screen showed strictly less than the mirror
  did. §9.8 R3's "Room stays authoritative for Tendril's UI" held only vacuously: Room's
  un-expanded rows were what the UI showed.
- **Multi-day spans never rendered.** `end_date`/`end_time` (round 1 above) reached the Provider but
  no Tendril view consulted them, so a three-day offsite appeared on day one.
- **Exception rows were dead**, and two thirds of that is now fixed. `original_entry_id`/
  `original_occurrence_date`/`is_exception_skip` (round 3 above, §9.8 R5) were declared,
  round-tripped through snapshots, and never written, read or honoured by anything. *(**Added
  2026-09-06 — an omission, not a correction: the sentence above is true of what was found.** The
  expander below did fix the reading half, and properly — a skip tombstone removes an occurrence, an
  override replaces one, and an override whose base has vanished is still drawn rather than lost.
  **Nothing writes one.** No surface in the app creates a skip or a per-occurrence override, so
  §4.1's "single-occurrence editing/deleting for recurring Events is in scope (Decided 2026-07-14)"
  is a decision with no implementation behind it, and an exception row can currently only reach this
  device from another device's snapshot. Said plainly here so that the next reader does not infer
  from this section's "Implemented" heading that the round-3 design shipped whole.)*

**Decided/Implemented:** a shared `EntryOccurrences` expander in `shared/`, taking stored rows plus
a date range and returning one occurrence per covered day. Calendar's Day/Week/Month views, the
Agenda and Monthly-grid widgets, and `AlarmScheduler` all read through it. Nothing reads
`CalendarContract.Instances` — that would be the second, silently-divergent expander §9.8 R3 exists
to rule out; this expands Room's own rows, so R3 is satisfied properly rather than vacuously.

**Corrected: hand-rolled, not `lib-recur`.** The paragraph above says to "use an existing RFC5545
library such as `lib-recur` rather than hand-rolling a parser." That was written in 2026-07-14, when
the assumption was that Tendril would author arbitrary RRULEs itself. It doesn't: the *only* thing
that ever constructs a `Fixed` rule is `GoogleEvent.toEntry` (§9.5.1), so the rules this app has to
read come from one producer emitting a small, stable subset. Weighed against this codebase's
consistent call elsewhere — hand-rolled Notion Markdown and CSV parsers (§7.4), a hand-rolled nav
shell — a bounded grammar under our own control won. Supported: `FREQ` (DAILY/WEEKLY/MONTHLY/
YEARLY), `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY` (plain and ordinal), `BYMONTHDAY`, `BYMONTH`, `WKST`.

**The accepted limitation, stated rather than discovered later.** A rule using a part outside that
subset (`BYSETPOS`, `BYWEEKNO`, `BYYEARDAY`) is treated as unexpandable and shows its first
occurrence only. That is deliberate — the alternative, ignoring the unsupported part, *adds*
occurrences on days the event doesn't happen, and a phantom calendar entry is worse than a missing
one because a missing one is visibly missing. It does mean such a series can show fewer occurrences
in Tendril than in the system calendar Tendril published it to. That divergence is cosmetic, not a
data-integrity problem — the Provider write is still one-directional and Room is still the single
source of truth (§9.8 R3) — but it is a real, visible gap, and the honest fix if it ever bites is to
widen the subset, not to start reading `Instances` back.

**Amended 2026-09-22 (audit): an ordinal `BYDAY` that can only limit joins the unexpandable set.**
`BYDAY` is listed above as supported "plain and ordinal", and it is — but only where the ordinal
*expands*. Beside `BYMONTHDAY` the two parts intersect: `FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=1FR` asks
for a 13th that is also a first Friday, which no month has. The expander has no limiting path, so it
dropped the ordinal term and emitted **every** `BYMONTHDAY` date — all twelve 13ths of 2026, eleven
of them phantom. The same ignoring turned `FREQ=DAILY;BYDAY=1MO`, which RFC 5545 forbids outright (a
numeric `BYDAY` is meaningful only under MONTHLY or YEARLY), into every Monday. Both now fail
`parse()` and fall to the first occurrence alone, which is this section's own doctrine — a missing
occurrence is visibly missing, a phantom one is not. A plain `BYDAY` still limits `BYMONTHDAY`
(`BYMONTHDAY=13;BYDAY=FR` is Friday the 13th and expands correctly), and an ordinal alone still
expands. Reachable from any imported `.ics`, not only from the Google pull: `IcsImporter` (§3.2)
passes a component's RRULE through verbatim, which widens the producer set §4.1.1's "one producer"
argument above was written against.

**Alarm rescheduling must be event-driven, not schedule-ahead (Decided 2026-07-14; premise struck
2026-09-06).** ~~`AlarmManager` cannot know a TASK's next occurrence before it exists — elastic
recurrence means the interval itself depends on *when* the person resolves the current one (resolve 2
days late, the gap to next time is 7+2 days, not a fixed 7).~~

*This is the exact wording the 2026-09-04 correction in §4.1 above says it withdrew, and it
went on standing here verbatim underneath that correction — a withdrawn premise still circulating
under a **Decided** heading, which is worse than never having withdrawn it, because a reader who
lands on this paragraph alone has no way to know. Struck in place on 2026-09-06 rather than deleted,
since the pair of them is the record of how a correction can announce itself in one paragraph and
never reach the sentence it names. §9.7 carried a third copy, struck the same day.*

*The conclusion survives on a premise that does hold. A recurring TASK keeps exactly one live row,
and resolving it **moves** that row's `start_date` forward — in whole periods stepped from the
original schedule, never from the moment of resolution (§6.2). So there is no future occurrence for
`AlarmManager` to be handed, and the row it can be pointed at changes every time the task is
resolved, edited, trashed, restored or rebound.* This means only the currently-live occurrence's alarms should ever be
scheduled for a TASK; every write path that can move an Entry's `start_date` (create, manual edit,
or the resolve-and-advance step) must cancel that Entry's existing alarms and schedule fresh ones
against the new date, as one atomic step. MedTimer's own recent changelog confirms this is a real,
necessary pattern rather than over-engineering — their release notes show explicit work on *"not
cancel[ling] alarm at slot 0 when scheduling snooze or repeat"*, i.e. they manage alarm slots
explicitly tied to the confirm/dismiss action, the same shape being proposed here. See §9.7 for the
`AlarmScheduler` component this implies, and §9's new centralized-resolution note for where it gets
called from.

**History log confirmed (round 1's open item, resolved).** `EntryCompletion` (above) is written
every time a TASK resolves, `DONE` or `SKIPPED`, recurring or not — capturing which occurrence, when
it was actually resolved, and what happened. This is what makes "skipped" durable and queryable even
though the live `Entry` row itself resets to `PENDING` within moments for a recurring Task (round 1
flagged this as a silent scope mismatch if unaddressed — now addressed).

---

## 5. To-do Databases (Decided)

The mechanism that lets a Notion-style database inside Pages produce real, functional Tasks —
without every checklist needing page/database overhead, and without every database with a checkbox
accidentally flooding Tasks.

### 5.1 Row-as-page

*(**Corrected 2026-09-12 (§0.6.8):** a row is a page, as below — and since §0.6.8 a page can be
a row without living inside the database. A database's members are its *native* rows, whose
`databaseId` names it as home, plus every page carrying the label it has bound. A labelled member
is a full row in every view and carries the database's fields in its header, keeps its own place
in the tree, is listed once in the Pages hub — where it lives — and leaves the database by losing
the label, not by being trashed. Where this section says "row", read "member" unless it is about
creation, which still makes a native.)*

Every database row can hold free-form content beneath its properties, matching how Notion rows
actually work (a row *is* a page). Confirmed directly against a real use case: a
medical-appointments database with Tags, Date, Location, Means of transport as visible properties,
and a free-text "what to bring" section living in the row's body rather than crammed into a property
field.

**Default view for a to-do-oriented database shows properties as real, visible table columns** — not
hidden badges. The earlier instinct to hide properties behind a minimal checklist-only view for
*all* in-page to-dos was corrected: that minimal case is already what the standalone Tasks page is
for (§3.3). A three-item "buy milk, call mom, walk dog" list has no reason to live in Pages at all.
A database with several properties genuinely needs them visible to be useful for navigation and
assessment (e.g. seeing every appointment's date and location at a glance to judge timing).

### 5.2 Sync mechanism

*(**Corrected 2026-09-11 (§0.6.4):** the binding this section calls "deadline" —
`PageDatabase.deadlinePropertyId` — fills `Entry.startDate`, which is the day the task is
*planned for* and where Calendar draws it, not a deadline. The storage and snapshot names are
kept so a v9 peer's record keeps its meaning; the UI now says "Date (when)". A second, optional
binding to `Entry.dueDate` — the deadline proper — is `BindingRole.DUE_DATE`, labelled
"Deadline", since §0.8 step 2b landed the same day.)*

Explicitly **not** inferred from schema shape (a "Cooked?" checkbox on a Recipes database should
never silently become a Task). The mechanism is:

1. A **database-level "Sync to Tasks" toggle** — off by default, deliberate opt-in.
2. **Explicit property bindings**, set once when sync is turned on: which property means *Done*
   (checkbox), which property is the *deadline* (date), and, optionally, which property drives
   *recurrence* (§5.2.1, added 2026-07-16). Explicit rather than inferred because a database can
   have more than one checkbox or more than one date, and guessing wrong is worse than asking once.
3. Every row in a sync-enabled database becomes its own linked Task (a `kind = TASK` Entry, §4).
   Checking the box from either surface (the row in Pages, or the Task in Tasks/Calendar) updates
   the other — **bidirectional**, mirroring the Task↔Calendar sync rule already established for the
   app as a whole. **Corrected 2026-07-14**: since there's only ever one live Entry row per
   recurring Task (§4's sequential-recurrence fix — no separate materialized-occurrence table), this
   bidirectional link is unambiguous even for a recurring row: whichever surface resolves it (Done
   or Skipped), the Entry's `start_date` advances and `status` resets to `PENDING`, and the bound
   Row property (`done_property_id`, §4) resets to unchecked in lockstep — reflecting the new
   pending occurrence on both surfaces at once. The Row's own checkbox stays a simple boolean
   (checked/unchecked); "Skipped" as a distinct state is only exposed from the Task/Calendar
   surface, not from the Row's checkbox — a deliberate v1 scope limit, since a Notion-style database
   checkbox property is inherently boolean, worth confirming that's acceptable. **Added 2026-07-14
   (architecture review finding, see §9.8)**: this bidirectional link must be implemented as one
   centralized `ResolveEntryUseCase`, called by every surface that can resolve an Entry — the Row's
   checkbox, the Task list, Calendar, Merged, widget quick-actions, and the overdue notification's
   inline actions alike — rather than as two tables independently observing and writing to each
   other. Two independent observers reacting to each other's writes is a ping-pong risk; five UI
   surfaces each reimplementing the same multi-step resolution sequence is a drift risk. One
   function, one place both concerns are handled at once.

#### 5.2.1 Binding, retroactive seeding, and rebinding (Decided 2026-07-16, case-scenario round; UI implemented 2026-08-30)

*(**Corrected 2026-09-11:** "UI implemented" was true of rebind and unbind and not of the
post-hoc *bind* this section specifies. `DatabaseSyncManager.bindProperty` existed from the
start and no menu reached it — only the enable-sync sheet bound anything, so a to-do database,
whose sync is on from creation with Done alone, could never gain a date or recurrence binding
afterwards. Found on the phone while binding §0.6.4's deadline. A property's header menu now
offers "Bind as <role>" for each unfilled role its type can fill while sync is on; no confirm
dialog, since nothing bound is frozen or replaced.)*

Two real gaps, found by walking a populated (not empty) database through the binding mechanism: what
happens to values already sitting in a property at the moment it gets bound, and what happens when a
binding needs to be corrected later without destroying every linked Entry. Both resolve to the same
underlying pair of operations, applied at different moments:

- **`bindProperty(property, role)`** — the operation behind turning sync on, the retroactive per-row
  picker (§5.5), and the "new" half of a rebind alike. For each row: if the property already holds a
  real stored value (a database that's been running unsynced for months, now toggling sync on), that
  value **seeds** the newly-created linked Entry — a checked box seeds `status = DONE`, a populated
  date seeds `start_date`. **A seeded `DONE` also writes one `EntryCompletion` row** (`resolved_at`
  = the moment of binding, not the row's real historical completion time, which isn't recoverable —
  worth knowing the logged timestamp is approximate for pre-existing data specifically, not a
  limitation for anything resolved going forward). Skipping this would have silently broken the
  "every resolution gets logged uniformly" rule from §4.1 the same way round 1 originally missed it
  for recurring Tasks. After seeding, the property permanently stops being real stored data and
  becomes a live proxy read from the Entry via `ResolveEntryUseCase`, exactly as already specified
  for Done/Deadline.
- **`unbindProperty(property, role)`** — the reverse: the property crystallizes back into an
  ordinary stored column, frozen at whatever the Entry's state was at that instant, and stops
  proxying. This was already implicitly needed for full Sync-to-Tasks toggle-off (§5.5); now named
  as its own reusable operation. **Requires its own confirm dialog** (§5.5), stating plainly that
  the property freezes at its current value and stops updating automatically — the same
  confirm-and-commit-immediately pattern §5.5 already uses elsewhere, not a new mechanism.
- **Rebinding** (e.g., a Bills database where "Paid" was bound as Done by mistake instead of
  "Reconciled") is `unbindProperty(old, role)` immediately followed by `bindProperty(new, role)`, as
  one atomic action. **No Entry is ever deleted or recreated** — the fix over the only two levers
  that previously existed (toggle sync fully off, which deletes every linked recurring Entry per
  §5.5; or toggle it back on, which re-picks rows from scratch). A wrong binding becomes a one-field
  correction instead of a destructive rebuild.
- This depends on a real foreign key that the spec described behaviorally but never named:
  `Entry.source_row_id` (§4, added 2026-07-16) is what actually connects a specific Row to its
  specific linked Entry, and is what `bindProperty`/`unbindProperty` read and write against.

**Implementation note (2026-08-30):** `DatabaseSyncManager.rebindProperty`/`unbindProperty` were
fully built (§9.8-style domain logic) but unreachable from any screen — the only path that ever
touched them was property *deletion*. Closed by adding a "Change binding…" item to a bound property's
header-cell menu (`PropertyHeaderCell`, replacing "Edit property type," which a bound property never
offered anyway per §5.5) that opens a picker of same-type properties plus, for the optional
Deadline/Recurrence roles only, a "Remove binding" option — Done stays mandatory whenever sync is on,
so its own picker never offers "None." Picking either stages a `PendingRebind`, committed only after
its own confirm dialog per this section's "requires its own confirm dialog" rule, reusing the same
confirm-and-commit-immediately pattern as `DeletePropertyConfirm`/`ChangeTypeDialog`.

#### 5.2.2 Recurrence binding (Decided 2026-07-16, researched against Notion and Obsidian precedent)

Checked how two real database-driven apps solve the same problem rather than designing this from
scratch: Obsidian's own core database feature (Bases, a 2026 core plugin) has no task or recurrence
concept at all — confirmed via an open, unresolved community feature request asking for exactly this
— so third-party layers on top of it (the Tasks and TaskNotes community plugins) are where
recurrence actually lives, not the platform itself. Notion's own native answer (added ~Nov 2022) is
a bindable "Recur Interval" number property plus an automation that resets status and recalculates
the date on completion — structurally the same shape as Tendril's own resolve-and-advance mechanism
(§4.1), which is reassuring rather than coincidental, since it's the natural shape for this kind of
elastic recurrence regardless of app.

**Decided**: a third optional binding, `recurrence_property_id`, pointing at a property of the new
`Interval` type (§4 — a number plus a unit, day/week/month). *(**Built but unreachable, recorded
2026-09-06.** Every piece of the mechanism exists — the type, its `"n:UNIT"` encoding, the conversion
into a real calendar `Period`, the Enable-sync picker and the rebind picker — except a way to make an
`Interval` property in the first place. §4 excludes it from the general "New property" picker
deliberately, to avoid a second and uglier way to represent a plain number, on the understanding that
one is created *inside the recurrence-binding flow*; that flow only ever selects among properties
that already exist, and the creation step §4 assumes was never built. So the Recurrence picker is
always empty and this binding cannot be established from any surface, on either platform. The
decision below is untouched — only its reachability is at issue — and the fix is either a "create
one" step in the binding flow, or offering the type in that one context.)* Binding it wires that property's value
into the linked Entry's `RecurrenceRule.Elastic(period)` (§4.1), the exact same elastic mechanism
standalone recurring Tasks already use — database-driven Entries are always `kind = TASK`, never
`EVENT`, so `Fixed` (RRULE) recurrence is never relevant here. **Deliberately simpler than
standalone Tasks' own `None/Daily/Weekly/Monthly/Custom` Select-plus-Custom-field UI (§6.2)** — a
single `Interval` property is enough, without needing a separate "Custom" sub-mode, because any
(number, unit) pair already covers an arbitrary custom interval on its own; Custom was only ever a
distinct concept in §6.2's UI because Daily/Weekly/Monthly were presets standing in front of it.
This isn't a literal copy of Notion's own number-only property, though — Notion's raw day-count
would drift for a monthly-ish case (a 2-months-apart water bill, §6.1's own reference example, isn't
a fixed number of days; months vary 28–31 days). Storing `(number, unit)` instead of raw days
preserves true calendar-month semantics (`Period.ofMonths(n)`, the same `java.time.Period` machinery
§4.1 already committed to) at effectively the same one-property simplicity Notion's pattern offers.
Standalone Tasks' own Repeats UI (§6.2) is unaffected — this only applies to the database-binding
surface.

### 5.3 "To-do database" as a creation-time template

"New → To-do database" in the Pages action sheet is a convenience shortcut, not a separate data
type: it pre-fills a Done checkbox, flips the sync toggle on, and wires the bindings automatically.
Structurally identical to any other database underneath — a database created for something else can
have sync turned on later, consistent with the app's running pattern that nothing is a one-way,
permanent choice (themes, layouts, and now this).

**Decided (2026-07-13):** any database can toggle Sync-to-Tasks on at any time, not just ones
created via the "New → To-do database" shortcut — this was actually already implied by the paragraph
above ("nothing is a one-way, permanent choice"); the Open flag is removed as redundant rather than
as a new decision.

### 5.4 Reminders (not a computed property) — and, since 2026-09-06, computed properties themselves

An earlier idea — a computed "remind me N days before a date" *property* on a database row — was
explicitly dropped. The actual need (assessing timing across visible dates, e.g. when to schedule a
blood test before a follow-up appointment) is already satisfied by the visible-properties table view
(§5.1); no computed field was needed for that. Reminders themselves are a Calendar/Task feature, not
a database one (§3.2): a repeatable list per event/task, presets (1h/2h/4h/8h/1 day/2 days/1 week)
or custom (number+unit), ~~living in the full edit sheet~~ reached from a bell icon on the row itself
(not Quick Add — *corrected 2026-09-06; no full edit sheet was ever built, see §3.2*), no cap on how
many stack per item.

~~A general Notion-style formula language (arbitrary expressions referencing other properties) was
also explicitly scoped **out** — real feature, real complexity (closer to a small interpreter than a
database property), not planned for an early build.~~

**Reopened and reversed 2026-09-06**, on the same practice as §5.6's reversal of the table-only
database: the exclusion is struck rather than deleted, because the reasoning above is the only
record of what the cost was judged to be. What changed is not the estimate — a formula language
*is* a small interpreter — but the decision to pay it. Computed properties land as **relation,
rollup and formula together**, since rollup aggregates across a relation and cannot exist without
one, and a formula that cannot traverse a relation is a calculator over a single row.

The shape, and the reason it is one feature rather than three: a single `COMPUTED` property type
with **two authoring paths onto one evaluator**. A rollup is built from pickers — relation, target
property, aggregation, optional condition — and those pickers *write an expression*; a small `ƒ`
under the result reveals what they wrote, editable in place. Choosing the easy path therefore
teaches the language instead of capping the person who chose it. The alternative, two disconnected
subsystems, is Notion's own arrangement and the source of its sharpest limitation; Baserow shipped
the language first and added rollup fields afterwards purely for approachability, while Teable,
having no language, now carries four separate field types because in that model every new
requirement becomes another type.

Three properties are load-bearing and are the reason this is worth building rather than copying.
**Errors are caught when the formula is written, not when a cell renders** — Notion 2.0 permits
saving a broken formula and renders it as an empty cell, invisible to anyone not inside the editor.
**Composition is a real dependency graph with cycle rejection that names the path**, not an opaque
depth budget; Notion caps at 15 reference layers, and in the 7-layer era exceeding it produced
silently wrong values. And **a computed cell can explain itself** — tapping one shows the expression
with every reference resolved to this row's values, and for an aggregate the contributing rows and
their individual values. Spreadsheets have had trace-precedents for thirty years and no database app
in this category ships it; in a single-user local app it costs almost nothing.

Determinism is a contract, not an aspiration: the evaluator lives in `shared/commonMain`, reads no
default time zone and no locale-dependent collation, and a discrepancy between Android and desktop
is indistinguishable from a sync bug and will be reported as one. Computed **definitions** sync as
part of a database's schema; computed **values** never enter a snapshot file — they are derived
state, cached locally so they can still be sorted, grouped and filtered on, and rebuildable from
scratch.

**Non-negotiable precondition, recorded here because it is a sync-wide hazard rather than a feature
detail**: `PagesSyncEngine` decodes a property's type with a bare `PropertyType.valueOf`, which
throws on any name it does not know. That exception leaves `mergePages`, leaves `mergePagesDir`, and
aborts the entire sync pass *(**precision fix 2026-09-06:** this listed "pages, entries, habits and
purge tombstones alike". Inbound tombstones are the one thing already applied by the time the throw
happens, since they merge before any record file is read — but the throw is caught above and the
write pass never runs at all, so this device also publishes nothing, its own tombstones included. The
conclusion is unchanged; the four-item enumeration is dropped rather than qualified, because a
qualification here reads as an escape hatch and there is none)*. So a device on an
older build that receives a `COMPUTED` property does not degrade to an unreadable column; it stops
syncing anything at all. The tolerant decode (`runCatching { … }.getOrNull()`, the shape
`PurgedRecordSnapshot.toEntity` already uses two files away) must reach **every** device before any
new property type ships.

---

### 5.5 Property and Row lifecycle safety (Decided 2026-07-15, case-scenario round)

Four real gaps found by walking concrete cases through the schema rather than reviewing it in the
abstract, all resolved the same way: confirm, and explain the consequence in plain language before
it happens.

*(**Added 2026-09-12 (§0.6.8):** a member through a label is not the database's to delete. Its
row menu offers "Remove label from this page" instead of "Delete row"; the page stays where it
lives, its values for this database stay stored but unseen, and its linked Task — if the database
syncs — goes to Trash unless a native home or another bound label still makes it a task. The
first time a label bound to a syncing database is applied, a dialog says so, once per database.
Deleting the database forever purges the labelled pages' values with the schema, exactly as it
purges its native rows' (§5.5.1.1); the pages themselves are untouched.)*

**Updated 2026-07-16**: a fifth case, from the same family — **rebinding or unbinding a bound
property** (`done_property_id`/`deadline_property_id`/`recurrence_property_id`, §5.2.1) outside of a
full Sync-to-Tasks toggle-off. `unbindProperty` gets its own confirm dialog, same pattern as
everything below: stating plainly that the property will freeze at its current value and stop
updating automatically. Rebinding chains an unbind confirm and a new bind in one action; see §5.2.1
for the mechanism.

**Updated 2026-07-15 (same day, later pass):** the Editing-mode draft/Cancel mechanism these
originally leaned on for reversibility has since been withdrawn (§3.1.2) — Pages are instant-write,
like everything else in the app. Each item below now commits immediately on confirm; the confirm
dialog itself, stating the concrete consequence in plain words, is the entire safety net, not a
preview step ahead of a later Save. **Worth your confirmation**: this means there's no longer a way
to back out of a confirmed property-type conversion or deletion short of manually reconstructing the
data (re-adding a property, retyping values) — acceptable for a personal build, but a real behavior
change from the original "confirm now, still revertible until Save" design, so flagging it rather
than assuming it's fine.

- **Property type conversion** (e.g. select → number, multi-select → text) is destructive by default
  in the real app this is modeled on — Notion applies type changes instantly with no confirmation,
  and several conversions are silently lossy (multi-select → text collapses every row's tags into a
  comma-joined string that won't cleanly re-split; date → text loses the date semantics outright).
  That's in direct tension with this app's own "nothing is a one-way, permanent choice" philosophy
  (§5.3), so Tendril does the safer thing: opening "Edit property type" shows a preview of what would
  happen to existing values ("12 rows will lose their multi-select tags"), and the change commits
  immediately on confirm — not a bespoke flow, the same confirm-dialog pattern as the rest of this
  section. A bound property (Done/Deadline, §5.2) never enters this flow to begin with — there's no
  real schema-editable field there to convert.
- **Property deletion** requires an explicit confirm dialog. If the property is
  `done_property_id`-bound, deleting it is just one more trigger into the same
  Sync-to-Tasks-disable flow below — not a separate bespoke behavior — with the dialog stating
  plainly that confirming disables sync, and that re-adding a same-named property later begins a new
  sync relationship rather than resuming the old one (the property's stored values are genuinely
  gone, even though the sync *relationship* re-establishing might feel like resuming from the
  person's side). *(**Corrected 2026-09-06:** this read "`done_property_id`- or
  `deadline_property_id`-bound", and a Deadline-bound property does **not** take that path — nor
  should it. §5.2.1 gave the optional roles a non-destructive `unbindProperty`, so deleting a
  Deadline- or Recurrence-bound column crystallizes it and clears the role while sync keeps running;
  only Done is mandatory whenever sync is on, so only Done's deletion can end it. The routing is
  right and the **dialog copy is the defect** — it computes "bound" across all three roles and then
  warns, for all three, that deleting turns sync off and moves the linked Tasks to Trash. Deleting a
  Deadline column today promises a teardown that does not happen.)*
- **Row deletion and Sync-to-Tasks toggle-off** both require an explicit confirm dialog, stated in
  plain language: toggling sync off deletes the database's linked recurring Entries (a deliberate
  bulk-cleanup mechanism, not just a safety gate — it means the person never has to manually delete
  a database's worth of future Entries by hand when the whole thing is no longer needed).
  *(**Corrected 2026-09-06 — every linked Entry, not only the recurring ones.** Disabling sync walks
  every row of the database and trashes that row's Entry with no test on its recurrence rule at all;
  and a database with no Recurrence binding has no recurring Entries in the first place, so on most
  databases the qualifier describes an empty set while the action clears the lot. The shipped confirm
  dialog repeats the same wrong qualifier. Appended rather than edited into the sentence above,
  because §5.5.1's own correction below turns on that sentence still saying "deletes".)* Turning
  Sync-to-Tasks back on later (§5.3) prompts the person to pick which existing rows should generate
  Entries retroactively, with an "All" shortcut.

### 5.5.1 Trash / soft-delete (Decided 2026-08-08 — supersedes the "no way to back out" limitation noted above)

§5.5's confirm-dialog pattern remains the mechanism for *destructive-by-consequence* actions
(property type conversion, sync toggle-off) where the dialog's real job is explaining a non-obvious
consequence before it happens. Outright deletion — of a Page, a Row, a standalone Entry, or a Habit
— now goes to a Trash instead of being destroyed immediately, closing the exact gap §5.5 flagged
explicitly above ("no longer a way to back out... short of manually reconstructing the data").

- **Mechanism**: deletion sets `deleted_at` (a new nullable timestamp field, §4) rather than
  removing the row. A deleted Page/Row disappears from its normal list/tree/database-table location
  and from FTS/Road Map/the backlinks panel (§3.1.5) — invisible everywhere except Trash — while its
  data and block content remain fully intact underneath.
- **Trash**: one list, reachable from the Pages hub's "···" menu, showing everything with a non-null
  `deleted_at`, most-recent-first, noting the deleted item's former location (e.g., "was in:
  Groceries database") so restoring makes sense out of context. *(**Open as of 2026-09-04, and wider
  since — three lists, not one** (recounted 2026-09-06). Pages and Rows are in a sheet off the Pages
  hub as specified; Entries and Habits are in two further sheets off the Tasks & Habits tab, chosen
  by whichever tab happens to be showing — so a trashed Habit is reachable from the Habits tab only.
  They were briefly one sheet, merged 2026-09-04 and lost again in the 2026-09-05 integration for
  purging without recording a tombstone. Merging them is more than a move: a
  database Row and its linked Entry are trashed together (§5.2), but Page-Trash's Restore clears
  only the Row's `deleted_at`, so a restored row currently comes back with its Task still in the
  other Trash. Both are tracked together rather than the split being closed and that bug left
  behind it.)* Two actions per item: **Restore**
  (clears `deleted_at`, reappears exactly where it was — same `parent_id`/`database_id`, no
  re-creation) and **Delete forever** (the actual permanent removal, with its own confirm dialog,
  same pattern as §5.5's others).
- **Bulk actions (Decided 2026-08-08).** Every Trash item has a selection checkbox, plus a
  header-level "Select all" — a deliberate improvement over Notion, whose own Trash lacks this and
  makes batch cleanup a one-at-a-time chore. Selected items get the same Restore / Delete forever
  actions as a single item, applied to the whole selection at once; Delete forever's confirm dialog
  states a count ("Permanently delete 14 items?") rather than repeating per item.
- **No auto-purge (Corrected 2026-08-08, same day).** An earlier pass of this section specced a
  30-day automatic purge. Reconsidered and dropped: Trash retention is indefinite — an item sits
  there until the person explicitly chooses **Delete forever**, with no background sweep silently
  finalizing that decision on their behalf. This is a real behavior change worth being explicit
  about: Trash will accumulate without bound unless the person manually empties it, which is the
  trade-off being made here (no surprise data loss) against the trade-off the 30-day version made
  (bounded storage growth, but a decision made for the person rather than by them). See §9.4's note
  on what this means for the synced Entry/Habit files specifically, since those aren't per-record
  files the way Page/Row are.
- **What this changes from §5.5's existing items above**: Row deletion (third bullet above) now goes
  to Trash like everything else — restorable, not permanent. Sync-to-Tasks toggle-off's bulk
  deletion of linked Entries (also the third bullet above) is unchanged in **trigger** ~~and
  confirm-copy~~, but the Entries it removes now land in Trash too, restorable via the same mechanism,
  rather than being gone outright. *(**Corrected 2026-09-06:** the confirm copy did change, and could
  not have stayed — a dialog still saying "deleted" would be describing the behaviour this section
  replaced. It now says the linked Tasks move to Trash, restorable from there, not deleted outright.
  Only the trigger is untouched.)* Property type conversion and property deletion (first two bullets
  above) are **unaffected** — those aren't row-level deletions and stay exactly as specced
  (confirm-and-commit-immediately), since a property's stored values, once converted or dropped,
  have no natural "undo" shape the way a whole row does.
- **Entry and Habit** (§4, not Pages) get the same `deleted_at` field directly, with the same
  Trash/Restore behavior — a deleted standalone Task/Event or Habit is recoverable exactly like a
  Page. *(**Corrected 2026-09-04:** this bullet and the next one still read "auto-purge" and
  "recoverable for 30 days" — the 2026-08-08 correction three bullets above removed the 30-day purge
  and never reached them, leaving the section asserting both indefinite retention and a 30-day sweep.
  Indefinite retention is the decided rule.)*
  - **Implemented 2026-09-04, second half.** "Recoverable" is a claim about a surface, and for
    Habits there wasn't one. `HabitDao` had `observeTrash`/`restore`/`deleteForever` from the
    start and **nothing ever called any of the three** — so trashing a Habit set `deleted_at`,
    removed it from the habits list, the Merged view (§3.3) and the quick-check widget (§8.1.1),
    and left no way back at all. It was a permanent delete wearing a soft delete's field. The
    identical gap for Entries had already been closed; ~~the Tasks & Habits Trash sheet now lists
    both, with the same selection, bulk Restore / Delete forever and counted confirm this section
    requires, over one merged newest-first list rather than two stacked ones.~~ *(**Corrected
    2026-09-06 — the merged list did not survive the 2026-09-05 integration**, and this document's
    own Revision Log for that day records it as one of three deliberate losses, for a reason that
    still stands: the combined sheet purged without recording a tombstone, so keeping it would have
    stopped "Delete forever" propagating. What ships is two sheets, one over Entries and one over
    Habits, each carrying the selection, bulk actions and counted confirm this section requires, and
    chosen by whichever tab is showing — which means a trashed Habit is reachable from the Habits tab
    only, never from Tasks or Merged. The single list stays the goal; it now has to be rebuilt on top
    of the purge registry rather than beside it.)* Restoring an Entry
    still routes through `ResolveEntryUseCase` so a TASK comes back with its alarms rearmed
    (§9.7); ~~a Habit has no alarms, so the DAO call is the whole operation.~~ *(**Corrected
    2026-09-06 — a Habit does have an alarm, and this sentence is why its restore forgets it.**
    Written 2026-09-04, one day before habit reminders were built. A Habit with a time-of-day now has
    a scheduled reminder of its own; trashing it is what cancels that reminder, and
    `EntryScheduleCoordinator` gained an `onHabitRemoved` for exactly the reason its own comment
    gives — an alarm that outlives its row is a wakeup for nothing. Restore still calls the Habit DAO
    alone, so a restored Habit comes back unscheduled and stays that way until some unrelated write
    happens to re-arm it; the boot and app-open sweep re-arms Entries only (§9.7). This is the same
    defect for Habits that routing Entry restore through `ResolveEntryUseCase` was added to prevent
    for Tasks, and restore has to re-arm by the same rule. Recorded rather than quietly fixed because
    the reasoning that made it invisible was written here first and then copied verbatim into the
    Habit Trash sheet's own doc comment, where it will re-justify the bug to the next reader unless
    both are corrected together.)*
- **Interaction with sync (§9.4)**: `deleted_at` is just another field on an already-synced record —
  no new sync mechanism needed; it travels with the record's normal snapshot write and merges under
  the existing per-record LWW rule. A **Delete forever** — the only way an item leaves Trash — is a
  real hard-delete written to that device's own snapshot file on the next sync pass, same as any
  other mutation. *(~~Known gap, not yet closed: the merge on the receiving side is additive by
  design and never deletes a local record for being absent from a remote file, so a hard-delete does
  not currently propagate — the record is re-inserted from the folder's own copy on the next pass,
  on this device as much as another. Closing it needs a tombstone the merge can act on, which is a
  format change, not a fix in place.~~ **Closed 2026-09-04/05, and the format change is exactly what
  it took** — see §5.5.1.1. A `(kind, uid, purged_at)` tombstone travels in `purged_records.json`,
  covering Pages, Entries, Habits **and database Properties** — the fourth added 2026-09-05, when a
  property absent from a winning schema stopped meaning "deleted" and had to travel as a tombstone
  like everything else; *listed here 2026-09-06, having been left out of this enumeration when it
  shipped*. §9.4's additive rule is narrowed rather than abandoned: absence
  still never implies deletion, an explicit tombstone does, and a tombstone is compared against the
  record's own `updated_at` so a stale delete cannot destroy a newer edit. The tombstone is recorded
  *before* the row is dropped, so a crash between the two leaves a tombstone with no row — harmless
  and self-correcting — rather than a row with no tombstone, which resurrects.)*

#### 5.5.1.1 "Delete forever" and snapshot sync (Decided 2026-09-04; scope corrected same day)

*Problem found during the 2026-09-04 code audit.* §9.4's merge inserts any record whose `uid`
isn't already local, so "Delete forever" didn't stick. A Page has its own snapshot file
(`pages/<uid>.json`), which put it back on the very next pass **on a single device**, no second
device involved; an Entry lives in an array file another device rewrites, so it came back from
there. Pruning the file afterwards cannot fix either — the merge runs first and has already
restored the row. A purge has to be *recorded*, not inferred from absence.

*Decision:* `PurgeRegistry` records a `PurgedRecord` tombstone — `(kind, uid, purged_at)`, keyed
by kind because each record type's uids come from a separate space — in the same operation that drops
the row, never as two things a call site must remember to do in order. The merge declines a
tombstoned uid; the write pass drops that uid's page file. *(**Corrected 2026-09-06:** this read
"because Page and Entry uids come from separate spaces", which was true of the two kinds that existed
on 2026-09-04. There are four — `PAGE`, `ENTRY`, `HABIT` and `PROPERTY` — Habits added later the same
day and Properties on 2026-09-05. Three separate Revision Log rows name this section among the ones
they touched and none of them amended it, which is how a two-kind description survived over a
four-member enum. `PurgedRecord`'s own doc comment is stale in the same direction, still saying a
Habit has no such action yet, directly above an enum that lists one.)*

**Purges propagate (corrected).** The first cut kept tombstones local, on the grounds that §9.4
calls its merge additive and non-destructive. That was the wrong reading: the rule exists so that
*absence* is never mistaken for deletion, and a tombstone is precisely the explicit signal that
distinguishes the two. Keeping it local also made "Delete forever" a lie on any multi-device
setup — the Trash could never actually be emptied, since the next sync brought everything back
from whichever device hadn't purged. So the tombstones travel, in `purged_records.json` beside
the other snapshot files, and are the one signal that removes local data.

**A purge is a timestamped fact, not a veto.** For a given uid the folder can carry both a record
(`updated_at`) and a tombstone (`purged_at`); the later wins, the same last-write-wins rule §9.4
already applies everywhere else. A purge therefore removes the record on every device — unless
some device edited it *after* the purge, never having seen it, in which case that edit resurrects
it and the tombstone is dropped as superseded. The alternative, letting a stale delete always win,
silently destroys work someone was still doing.

**One kind is a veto, and deliberately so (added 2026-09-06).** `PROPERTY` has no supersede check and
cannot be given one: a `Property` carries no timestamp to compare against `purged_at`, and a re-added
column is minted with a fresh uid that no tombstone names. A property tombstone therefore refuses its
uid permanently. That is exactly right for the race it exists to stop — a device that had not yet seen
a new column re-exporting the database without it, taking the column and every row's value under it —
and it is worth stating, because it is the one exception to the rule immediately above and a reader
who applied that rule to all four kinds would look for a supersede path that does not exist.

*Ordering matters:* the tombstone file merges, and is applied to local rows, **before** any record
file is read. Otherwise a record and the tombstone that kills it cross within one pass and the
record survives by accident.

*Escape hatches,* matching §9.4.1's existing Import/Restore split: Restore-from-backup discards
this device's purge history and adopts the archive's, since Restore means "become exactly what
this archive says"; an additive Import adopts the archive's tombstones under the same
later-timestamp-wins rule as a folder sync, so an archive holding a page edited after it was
purged brings that page back, and one holding only an older copy does not.

*Known cost:* tombstones are never garbage-collected — a purge is permanent information, and
forgetting one lets the record return from any device that still has it. At personal scale this
is a few dozen bytes per deleted item; if it ever matters, the bound is "older than the oldest
device's last sync", which this app has no way to know today.

### 5.6 Database views (Decided 2026-08-08 — reopens and reverses the §10 "decided out of scope" table-only-database limitation)

*(The Month grid, 2026-09-17: under a pointer the **Calendar view is §3.2's `MonthGrid`** — Notion's database calendar is this grid — the rows whose Date property parses as chips in the database's tint (the third hue until the slider PR), a chip opening the row's page, a drag writing the cell through `setDateCell` (a bound When moves the task too), *No date · n* under the grid as the Timeline lists them; no ground click — a row needs a title, the Table's *+ New* is the way; the month shown is the earliest dated row's from today, else today's. Under Touch the grouped list stays.)*

*(14h·2: the Table's rows take the density profile's height (§2.2 *Submenus and captions*); the
footer counts *1 row* / *2 rows* (`rowsLabel`, also in the five conversion warnings) and leaves an
empty column blank rather than `—`. A date cell's `TextButton` keeps Material's 40 dp, so a row
at Compact sits at 45 dp, not 36 — `docs/critiques/small-things-function.md`.)*

**Correction, stated plainly** (same practice as the Task/Event and Editing/Viewing corrections
elsewhere in this doc): an earlier pass scoped Databases to a single table view, reasoning that
anything dated already surfaces on Calendar once it's a synced Task. That reasoning covers dated
items specifically but misses the actual point of a Notion/Anytype/AppFlowy-style database: the
*same rows*, viewed differently depending on what the person is doing with them right now — a Kanban
board of a project's tasks by status, a visual gallery of a recipe database by cover image, a
calendar laid out by any date property, not just the one bound to Sync-to-Tasks. A table-only
database is a real functional regression against every reference app this spec draws on, not a
reasonable simplification. Reopened and reversed.

**Scope (Notion-lite equivalent, matching §3.1.1's own scoping precedent):**
- **Table** (existing, §5.1) — unchanged, remains the default view for any new database.
- **Board**: groups rows into columns by one Select-type property's options (e.g., a Kanban of a
  project database grouped by a "Status" Select property); ~~drag-and-drop between columns~~ a
  "Move…" menu on the card writes the property value, same as editing it from the table. A database
  with no Select-type property yet shows a prompt to pick one or create it — no silent fallback.
  *(**Amended 2026-09-18 — the database views PR, F6:** a new Board never opens on that prompt: `addView` makes a Select *Status* (Not started · In progress · Done) when the database has none and groups by it, or binds the first Select that exists — Notion's, measured; a Calendar or Timeline gets a *Date* the same way. The prompt (`ViewNeedsPrompt`, centred, with *Add a … property* and *Configure view*) is the state a later deletion leaves. **A blank Select cell is unset, not a mismatch**: its rows sit in a named first column, *No Status*, from which a card moved takes its option and into which one moved is cleared; a value matching no option is still dropped — the *no silent fallback* rule is about mismatches. *Delete view…* asks first and the last view cannot be deleted; the active chip's ▾ opens *Configure view… · Delete view…*; the New view sheet takes a name (the 36 dp field, the kind's label as its placeholder), the layouts as chips and one line on the chosen kind. The Timeline opens with today three columns in and never past the day before its earliest bar (L13, small things III). `docs/critiques/database-views-mock.md`, `database-views-function.md`.)*
  *(**Corrected 2026-09-06 — a card moves by a tap-to-reassign menu, not a drag.** Same end
  capability, any card to any column, and the same single cell write. It is the same call already
  made for block reordering (§3.1.1's Move up / Move down over a drag handle) and made for the same
  reason: gesture-tracking on a 240dp card inside a horizontally-scrolling row is the risk, and the
  menu costs the board nothing it actually needs.)*
- **Gallery**: a card grid, one card per row, with a slot for a designated cover (the first Image
  block in the row's body, §3.1.1, or a blank placeholder) plus a small set of properties chosen
  per-view — matches Notion's/AppFlowy's own gallery pattern closely enough that a Notion import (§7)
  can carry a source gallery view forward directly. *(**Two corrections, 2026-09-06.** The cover is
  chosen and then thrown away: the ViewModel picks exactly the block this bullet specifies, and the
  card paints nothing for it, because no surface in the app renders an Image block at all — §3.1.1's
  own gap, surfacing here first. What was built is worse than the empty case it exists to improve: a
  row *without* a cover at least says "No image", while a row *with* one renders a blank box. The
  cost is charged to image rendering, not to this view. Second, the properties are **not yet
  per-view** — the card shows the database's first three, full stop, and the stored
  `visible_property_ids` is read by nothing. Recorded rather than quietly dropped, because the field
  exists and the choice is a small edit on top of it rather than a redesign.)*
- **Calendar**: rows plotted by any one Date-type property in the database, chosen per-view —
  deliberately more general than Sync-to-Tasks' binding (§5.2), which only ever plots the bound
  deadline property on the app's own Calendar page. This view is local to the database (e.g., see
  every row of a Trips database by "Departure date" without that property ever being bound to Tasks
  at all).
- **Timeline** *(added 2026-09-13, §0.6.14)*: rows as bars on day columns, from a start Date
  property to an optional end one; a bar dragged moves the row's date through the same write the
  Table's date cell makes, task included when the row is synced. Open "blocked by" rows are
  marked and their dependencies drawn.

**Was still explicitly out of scope, unchanged from §10's original reasoning** — *reopened 2026-09-06, see §5.4 and §10; the paragraph is struck rather than deleted because it is the record of what the cost was judged to be*: ~~Timeline/Gantt view and
formula/rollup-driven grouping — both are real added complexity (date-range bar rendering with
drag-resize; an interpreter for computed grouping keys) that didn't come up anywhere else in this
spec as an actual need, unlike Board/Gallery/Calendar which map directly onto cases already
described (§5.1's medical-appointments and Recipes examples, §5.4's blood-test timing example).~~

**Mechanism**: a database can have multiple saved views (matching Notion's own view-tab pattern),
each storing `view_type`, the grouping/date property it depends on, and its own visible-properties
selection (§4's new `DatabaseView` entity) *(**corrected 2026-09-06 — the third of those is stored,
synced, and read by nothing.** `view_type`, the grouping property and the date property are all
honoured; the per-view column chooser is designed and persisted and has no UI in either renderer,
since the Table draws every property and the Gallery the first three and neither consults the view.
Left in the schema deliberately: the storage and the sync mapping are the parts that would be
expensive to add later, and this is the record that the display half is still owed.)* — independent of the underlying schema, so switching or
adding a view never changes stored data, only how it's displayed. Filters and sorts are
**per-view**, not global to the database: a single-condition filter model (one property, one
comparison, one value — e.g., "Status is not Done"), no AND/OR-group logic for v1, matching the
spec's consistent bias toward the 80%-useful subset over full expressiveness (the same trade-off
already made for the block editor, §3.1.1, and the Repeats Custom field, §5.2.2). Deleting a view
only removes that display configuration; the underlying rows and properties are untouched —
consistent with §5.3's "nothing is a one-way, permanent choice" philosophy running through the rest
of the app.

**Import interaction (§7)**: Notion's CSV export (§7.1) only carries whichever view was active at
export time — already noted in §7.2's fidelity table as a real format limitation, not an importer
gap, and that doesn't change here. What does change: if the exported view was a
Board/Gallery/Calendar (detectable from Notion's export metadata where present), the importer can
pre-create a matching `DatabaseView` instead of leaving the person to rebuild it by hand — a
worthwhile but non-blocking improvement over always defaulting to Table on import.

---

## 6. Habits vs. Recurring Tasks (Decided)

### 6.1 The dividing test

**Does missing an instance create backlog, or not?**

- **Habit**: no. Skipping a day of bed-making doesn't mean yesterday is owed today — it just
  evaporates. Streaks track consistency, not debt.
- **Recurring Task**: yes. Missing the water bill doesn't erase it — it becomes overdue and sits
  there until handled.

This is sharper than "how often it repeats" — a Habit could in principle be weekly, a recurring Task
could in principle be daily; what makes them different is what happens when one is missed. Reference
example: making the bed (Habit) vs. paying the water & electricity bill roughly every two months
(recurring Task).

**The arithmetic behind that test, written down 2026-09-06 — it had only ever existed in the code.**
This section states the dividing rule and stops there, and §8.1.1 refers to "a weekly+ habit's grace
period" as though it had been defined somewhere. It had not been. Four constants are the whole habit
product. A period is `n` days for a DAY frequency, `n × 7` for WEEK and **`n × 30` for MONTH** — a
month is thirty days here, which the code concedes in its own comment, and which drifts against a
real calendar by up to three days a month. A streak **continues** while the gap since the last
check-in is at most `period + 1` days — a one-day grace period — and otherwise **resets to 1, not
0**, because the check-in just made is itself day one. A habit that has never been checked in is due
immediately. And if a habit's time-of-day has already passed today, its reminder moves a **whole
period** forward rather than to tomorrow (§9.7). None of the four is wrong, but all four are product
decisions a reader of this section would otherwise have to guess at, and the thirty-day month is the
one most likely to be judged differently once somebody notices it.

### 6.2 Mechanism

**Correction, stated plainly (2026-09-04) — this section and §4.1 asserted opposite rules for a
year, and §4.1's own architectural argument rested on the version this section rules out.** As
originally written (2026-07-13), §6.2 said Task recurrence used "calendar-native recurrence (the
same RRULE-style mechanism any competent calendar needs)" and that "a repeating Task is just that
entry carrying a recurrence rule. One mechanism, not two." §4.1's round-3 pass the following day
replaced exactly that with **two** mechanisms — `RecurrenceRule.Fixed(rrule)` for EVENT and
`RecurrenceRule.Elastic(period)` for TASK — precisely so a column's meaning would stop depending on
an adjacent column. §6.2 was never updated to match, so the document carried both claims at once.
The split in §4.1 is the decided one; the "one mechanism" sentence is withdrawn.

The anchoring rule below is the reverse case: §6.2 is the one that survives, and §4.1's gloss on it
is what is wrong. §4.1 describes `Elastic` as meaning "the interval itself depends on *when* the
person resolves the current one (resolve 2 days late, the gap to next time is 7+2 days, not a fixed
7)". That is resolution-anchored recurrence, and it contradicts §6.1's dividing test in exactly the
way this section's own bullet spells out. **The anchoring rule below stands; §4.1's sentence is
withdrawn**, and `RecurrenceRule.Elastic` is therefore a misnomer for what it stores — kept as a
name only because it is the persisted `"ELASTIC:"` tag in the Room converter and in every snapshot
file (§9.4), which a rename would break for no behavioural gain. Read it as "an interval rule,"
never as "elastic about when it is resolved."

One consequence §4.1 loses with that sentence: its stated reason for event-driven alarms —
"`AlarmManager` cannot know a TASK's next occurrence before it exists" — is not true of the rule
that was actually built, since the next occurrence is a deterministic function of the current one.
`AlarmScheduler` (§9.7) is still right, for the reasons that do hold: an edit, a Trash, a restore or
a rebind can move `start_date` at any time, so alarms must be re-derived from the row on every
write rather than laid down ahead.

- **Repeats** field on Task: `None / Daily / Weekly / Monthly / Custom` (custom = interval + unit,
  so "every 2 months" is interval=2, unit=month), stored as `RecurrenceRule.Elastic(Period)` (§4.1)
- No page or database required — a standalone recurring Task never has to touch Pages
- A Task generated from a synced database Row (§5) inherits the same Repeats field
- **Anchoring**: the next occurrence is generated from the **original fixed schedule**, not from
  whenever the previous instance was actually completed. If it anchored to completion, a missed
  instance would silently push the whole schedule out instead of becoming genuinely overdue — which
  would contradict the §6.1 test directly. A late payment shows up overdue exactly as expected; the
  next one still lands on the original schedule rather than drifting.
- **Anchoring, second clause (added 2026-09-04, closing a real bug the rule above left open):**
  the next occurrence is the first one on that schedule that **hasn't already gone by**, not
  literally "the resolved date plus one period." Both readings keep the same phase — every candidate
  is `original + n×period` — so this doesn't reintroduce drift. What it removes is the degenerate
  case: resolve a weekly task nineteen days late and `resolved + 7` is still eleven days in the
  past, so the task reappeared overdue the instant it was ticked off, and clearing it took one Done
  tap per missed period. That broke three things at once — §5.2 keeps exactly **one** live Entry row
  per recurring task, so those intermediate occurrences had nothing to resolve in the first place;
  each tap wrote an `EntryCompletion` (§4.1) claiming an occurrence was resolved that nobody
  performed, corrupting the one append-only record of what actually happened; and §9.7's "never
  schedule an alarm for a trigger time already past" meant every in-between state was silently
  unscheduled, so a long-neglected recurring task stopped notifying entirely. Backlog is still real
  and still visible — the occurrence in hand stays overdue until it is resolved, per §6.1 — the app
  just stops manufacturing backlog for occurrences it never tracked.
- **Anchoring, third clause (added 2026-09-06 — the one case the rule above does not cover):** the
  "original schedule" the phase is read off is the row's `start_date`. A recurring TASK with no date
  has none, and the resolve step falls back to the day of resolution — resolution-anchored
  recurrence, the exact thing this section rules out, arrived at through the absence of a date rather
  than through anybody's decision. Two routes produce that row. The standalone Add-Task dialog is the
  primary one and is a plain state-retention bug: the Repeats chips are nested under Has-date for
  *display* only, so picking "Weekly" and then switching Has-date back off still inserts the
  recurrence alongside a null date. §5.2's binding step is the second and is structural: Deadline and
  Recurrence are independently optional there, so a database bound for recurrence without a deadline
  yields the same row by design. The dialog should retain nothing it has hidden, and the binding step
  should either require a deadline alongside a recurrence or this section should state the fallback
  as accepted behaviour. It is currently neither.

Habits keep their own separate, simpler streak-based recurrence — not the calendar RRULE mechanism.

---

## 7. Notion Import (Implemented 2026-08-30)

Researched against Notion's actual current export mechanism (Markdown & CSV, the only one of
Notion's three export formats — PDF, HTML, Markdown & CSV — that constitutes a real data export).
The target explicitly is: **importing a real Notion export must not silently break or drop
content.**

### 7.1 What the export actually contains

A zip with: one `.md` file per page (named `Title 32-character-hex-ID.md`), one `.csv` file per
database (rows + whatever properties were visible in the exported view), a folder beside each parent
page holding its children (recursively, if "Include subpages" was checked — mirrors the page tree
faithfully), and an assets folder per page for uploaded images/files.

### 7.2 What survives, degrades, or is lost

| Fate | Elements |
|---|---|
| **Survives intact** | paragraphs, headings, ordered/unordered lists, quotes, code blocks (with language), dividers, images/files, the page tree itself |
| **Survives but degraded** | Toggles → the collapse becomes permanently open (content intact, collapsible behaviour lost). Callouts → exported as raw embedded HTML inside the `.md` (Markdown has no callout syntax). |
| **Duplicated, not lost** | Synced blocks → a static copy appears at every location it was placed; the sync relationship itself doesn't survive. Linked database views → rows can appear redundantly across multiple exported files. |
| **Data survives, "liveness" doesn't** | Relations → raw Notion UUIDs, not resolved titles. Rollups → a static string snapshot. Formulas → only the last computed value, not the formula definition. |
| **Gone permanently — format limitation, not an importer bug** | Database views/filters/sorts (CSV only holds whatever the *currently active view* showed at export time). Comments (unless HTML export's "include comments" was used). Page version history. |

### 7.3 What the importer must actually do

1. Parse `.md` files preserving heading levels, lists, quotes, code fences, tables, image/file
   references.
2. **Rewrite internal links.** Exported links point to local relative paths carrying the same
   32-character hex page ID; the importer must remap these to Tendril's own internal page IDs, or
   every internal link breaks on import — the single most common failure mode across every migration
   guide checked.
3. Detect embedded raw HTML from callouts and preserve it faithfully (fallback HTML block at
   minimum) rather than silently dropping it; heuristic reconstruction into a native callout block
   is a nice-to-have, not required for "doesn't break."
4. Parse CSVs into typed database rows — CSV cells are all strings natively, so property type
   (number / date / checkbox / select vs. plain text) needs inference or an explicit mapping step
   during import.
5. Cross-reference relation UUIDs across CSVs where multiple related databases are imported
   together, to resolve human-readable titles instead of leaving raw IDs, where possible.
6. Reuse the **same property-binding step from §5.2/§5.2.1** during import: ask once which column is
   Done, which is the deadline, and — optionally — which drives recurrence, for any database the
   user wants synced to Tasks. Not new surface area — the identical mechanism already built for
   locally-created to-do databases. Notion import always runs through the **additive Import path**
   (§9.4.1), never the replace-everything Restore path — it was never meant to overwrite a person's
   existing Tendril data, only add to it.
7. Communicate plainly, not silently, that views/filters/live formulas/comments cannot be recovered
   — this is what the export format itself omits, not something a better importer could fix.

**Deferred** (§10): a second, more powerful import path using Notion's own API (a user-supplied
integration token) would expose the full live block/database/view structure programmatically, unlike
the static zip export. Worth keeping as a future option, not required for v1.

### 7.4 Import architecture (Decided 2026-08-30, scored via `optimization-engines:meta-optimizer`, Harmony Search over four discrete dimensions)

§7.3 named *what* the importer must do; this is *how*. Framed as four independent discrete
choices, scored against build footprint / correctness-fidelity / fit with this codebase's existing
`Block`/`PropertyType` model / import UX / dev effort (weights 0.20/0.30/0.20/0.15/0.15 — fidelity
dominant, matching §7's own stated bar: "must not silently break or drop content"). Four whole-
architecture candidates were scored first (Nominal, a dependency-light Extreme-low, a
dependency-heavy Extreme-high, and a Contrarian that treats a generic Markdown AST as pure
translation overhead this app's own bespoke Block model doesn't need):

| Candidate | Build footprint | Fidelity | Fits `Block`/`PropertyType` | Import UX | Dev effort | Score |
|---|---|---|---|---|---|---|
| Nominal — CommonMark-style library + custom postprocess, hand-rolled CSV, two-pass linking, hybrid infer+confirm | 0.55 | 0.85 | 0.55 | 0.80 | 0.55 | 0.678 |
| Extreme-low — naive per-line regex, hand-rolled CSV, single-pass placeholder-then-patch linking, always-ask column types | 1.00 | 0.35 | 0.75 | 0.45 | 0.80 | 0.643 |
| Extreme-high — full CommonMark + OpenCSV libraries, two-pass linking, fully silent heuristic inference | 0.30 | 0.90 | 0.45 | 0.55 | 0.45 | 0.570 |
| Contrarian — hand-rolled streaming state machine, hand-rolled CSV, *lazy* on-the-fly link resolution, hybrid infer+confirm | 1.00 | 0.70 | 0.85 | 0.80 | 0.45 | 0.768 |

A Harmony-Search recombination pass (reflective, targeted at each candidate's diagnosed weak
point — the Nominal/Extreme-high's library-adapter tax, the Extreme-low's tables/nested-list
breakage, the Contrarian's lazy-resolution debt) produced a fifth candidate combining the
Contrarian's parser and codebase-fit strengths with the Nominal/Extreme-high's two-pass linking in
place of lazy resolution:

| Candidate | Build footprint | Fidelity | Fits `Block`/`PropertyType` | Import UX | Dev effort | Score |
|---|---|---|---|---|---|---|
| **Chosen — streaming state machine + hand-rolled CSV + two-pass linking + hybrid infer+confirm** | 1.00 | 0.78 | 0.90 | 0.80 | 0.65 | **0.832** |

A follow-up generation isolated the markdown-parser dimension alone, re-testing a CommonMark-style
library against this same now-converged link/CSV/inference combination — it scored 0.678, confirming
the library's fidelity edge is smaller than its build-footprint and adapter-layer cost once the
*bigger* fidelity risk (dangling links from bad ID remapping, not markdown syntax edge cases) is
already solved by two-pass linking. Optimization stopped there: the gain from testing that isolated
swap was ~0, and every other dimension had already converged identically across three of the four
initial candidates.

**Decided:**
- **Markdown → Block**: a hand-rolled, line-oriented streaming state machine — no generic Markdown
  AST library, no intermediate representation. It reads Notion's export line-by-line and emits
  `Block`/`BlockType`/`FormattingSpan` rows directly, the same way `PageContentRepository` and the
  in-app block editor already model content, so there's no separate schema to keep in sync. Embedded
  HTML callouts (§7.2) are ~~detected structurally (a line beginning `<`) and preserved as a raw HTML
  fallback per §7.3.3, not silently dropped.~~ *(**Superseded 2026-09-06 — the nice-to-have was
  built.** Notion's callout export turns out to be completely regular (`<aside>`, emoji, body,
  `</aside>`), regular enough that reconstructing it structurally cost less than the fallback's own
  hedging: the parser matches `<aside>` specifically, lifts the leading emoji into `calloutIcon`,
  parses the body's inline spans, and emits a native `BlockType.CALLOUT`. The raw-HTML fallback
  survives for every other tag, which is what it was really for.)*
- **CSV → rows**: a small hand-rolled RFC4180-compliant parser (quoted fields, embedded commas,
  embedded newlines, doubled-quote escaping) — the grammar is compact enough that a dependency for
  it isn't worth this app's zero-non-AndroidX-dependency posture (`AppContainer`'s own stated
  preference for skipping a framework's cost when a small hand-written mechanism covers the actual
  need, §4/domain layer generally).
- **Link remapping is two-pass, not lazy or patch-based**: pass 1 walks the entire zip creating every
  `Page` row up front (titles + tree structure only) and builds a Notion-hex-ID → Tendril-page-ID
  map; pass 2 parses each page's actual body against that already-complete map, so every internal
  link resolves at import time. Rejected lazy on-the-fly resolution specifically because it would
  have pushed "is this link resolved yet" into `PageMention` rendering, backlinks (§3.1.5), and Road
  Map's mention graph forever — the same "one mechanism, not N call sites reimplementing it"
  reasoning §9.8 R1 and §9.11 already established, applied here to reject a design that would have
  spread the same state-handling problem across three unrelated features.
- **Property-type inference is hybrid, not silent or always-ask**: a small heuristic battery samples
  each CSV column's values (boolean-like, ISO date, numeric) to pre-select a guessed type, ~~then
  reuses the *exact* property-binding confirmation step already built for §5.2/§5.2.1 — not new
  surface area, per §7.3.6 — letting the person confirm or override before anything commits.~~
  *(**Corrected 2026-09-06: as built, inference commits.** `NotionImporter` writes every `Property`
  with its guessed type and every `PropertyValue` during the import itself, before any UI appears; the
  step that runs afterwards is `EnableSyncSheet`, which binds Done/Deadline/Recurrence among columns
  **as already typed** and offers no way to retype one. Two different confirmations were conflated
  here — §7.3.6's *binding* confirmation, which genuinely is reused, and a *type* confirmation, which
  was never built. A wrong guess is still recoverable through §5.5's type-conversion dialog, so §7's
  opening bar of not silently breaking content is met — but by the recoverability, not by the flow.
  Either build the type-confirm pass this bullet describes, or restate the decision honestly as "infer
  silently, recoverable via §5.5". What must not stand is the document claiming a pass that does not
  exist.)* Rejected
  fully silent inference: a wrong guess on an ambiguous column (e.g., a Select property whose values
  happen to look numeric) would silently mis-type data, which is exactly the "must not silently
  break content" failure §7 opens by ruling out.

**Implementation note (2026-08-30):** built as `com.tendril.app.notionimport` (`NotionMarkdownParser`,
`NotionCsvParser`, `NotionPropertyTypeInference`, `NotionImporter`) plus a Settings section reusing
`EnableSyncSheet` (§5.2's existing binding UI, made ~~`internal`~~ **public** for cross-module
reuse rather than duplicated) *(**corrected 2026-09-06:** `internal` was accurate while both lived in
the app module; Milestone 3's move of the shared UI into `shared/` put a Gradle module boundary
between the sheet and its second caller, and `internal` does not cross one. The reasoning is unchanged
— one binding sheet, not two — only the modifier that expresses it)*. Two gaps found only while
implementing, not anticipated by §7.1-§7.3:

- **No native Table block exists.** The Block model (§3.1.1) has no `BlockType` for a Markdown pipe
  table. Resolved the same way Callouts already degrade (§7.2): a table imports as a Code block
  holding the raw pipe-table text verbatim — degraded, not lost, consistent with the section's own
  accepted trade-offs elsewhere.
- **Nesting is flattened, not preserved.** ~~The in-app block editor's own nested-block rendering was
  never actually built (`PageDetailScreen`'s own code comment: "kept out of this MVP render pass
  since no UI path creates toggle children yet") — every block still renders top-level
  (`parentBlockId == null`) only. Assigning `parentBlockId` to imported sub-list/toggle content would
  have made it silently invisible, which is worse than the format's own documented "toggle collapse
  becomes permanently open" degradation (§7.2). Every imported block is top-level, in source order;
  only the hierarchy is lost, not the content.~~
  **Superseded — nesting is now preserved, one level deep (§3.1.1).** The condition this decision
  rested on is gone: `outlineOf` draws children, so a `parentBlockId` no longer makes content
  invisible. Note where the flattening actually lived — not in the importer but in the *parser*,
  whose first statement was `rawLine.trimStart()`, so the indentation never reached a decision
  about `parentBlockId` at all. The promise underneath the original call is kept: anything indented
  deeper than one level still arrives at depth 1 rather than being dropped, and a page opening on an
  indented line imports every block. Structural blocks (headings, dividers, code, callouts, images,
  tables) ignore stray indentation, since §3.1.1's nesting is list items and toggle children.

**Verified**: a synthetic Notion export (nested pages, an internal link, bold/italic/inline-code
spans, a to-do list, a blockquote, a `<aside>` callout, a fenced code block, a divider, a pipe table,
and a CSV database with Yes/No, date, and category columns) round-tripped through `assembleDebug`
cleanly. A JVM unit test (`NotionImportParsingTest`, this project's first test file) exercises the
three pure-logic classes directly — it caught a real bug during development (internal links were
resolving to the *parent folder's* Notion id rather than the linked page's own, since a nested
export path carries both; fixed by taking the last, not first, 32-hex match in a link's href). Full
on-device UI exercise of the Settings import flow was attempted but not completed — the only
available device turned out to be in active personal use (another app kept regaining foreground
during automated taps), so driving its UI further risked interfering with real usage rather than
verifying the feature.

---

## 8. Widget System

Three home-screen widget types were designed and prototyped in `noema-widgets-audit.html`, following
the same Ink/Clay/Moss/Mauve/light/dark/Sans/Serif theming system as the main app (Workbench only),
plus widget-specific controls that don't apply in-app. A fourth, Habits, was added later (§8.1.1)
once none of the original three touched Habits at all.

### 8.1 Widget types

- **Date**: resizable from 1×1 up to 2×2+; the day number renders at the **same font size regardless
  of footprint** — a larger widget only adds tap area/breathing room, never distorts the number.
- **Monthly grid**: three density tiers depending on widget height *(**corrected 2026-09-04**: this
  read "four" while naming three; the code and `monthly_widget_info.xml`'s own comment both say
  three)* — `dots` (compact, 4×2, no room
  for real text), `one` (standard, 4×3.5, one truncated title + "+N"), `wrap` (tall, 4×5, one full
  entry title wrapped to two lines + "+N" — chosen deliberately over showing two truncated titles,
  so a user who dedicates that much space can actually read the one entry rather than skim two
  fragments).
- **Agenda list**: compact (4×2) and tall (4×4) variants, times and titles fully spelled out (this
  is the one that actually delivers "written out, not just a dot").
- **Picker labels (Decided/Implemented 2026-08-29)**: each `<receiver>` carries its own
  `android:label` (Date/Monthly Grid/Agenda/Habits) rather than inheriting the app's `android:label`
  — without it, every entry in the system widget picker reads "Tendril" with nothing distinguishing
  them, since Android falls back to the app label per widget provider when none is set. The picker
  still groups all four under the "Tendril" app heading; the per-receiver label is what shows
  underneath each individual entry.

#### 8.1.1 Habits quick-check widget (Decided/Implemented 2026-08-29)

Not in the original three, and not prototyped in `noema-widgets-audit.html` — added after
cross-referencing Loop/uhabits, whose entire design center of gravity is exactly this: checking a
habit off from the home screen without opening the app, not a secondary convenience layered onto an
in-app-first design.

- **Content**: every active (non-Trashed) habit, sized/themed like the Agenda list (§8.1) — same
  4×2/4×4 footprint, same Ink/Clay/Moss/Mauve + Shade/Hue theming (§8.3), same per-instance
  opacity/Shade/Hue config screen (§8.7). A ○/✓ marker shows whether today's check-in has already
  happened; the title dims once checked.
- **Interaction**: tapping an unchecked habit checks it in for today immediately, in place, with no
  app launch — the entire point, per the Loop/uhabits precedent above. Tapping an already-checked
  habit undoes today's check-in (Decided/Implemented 2026-08-29), reverting
  `streak`/`lastCompletedDate` to their pre-check-in values via a snapshot `CheckInHabitUseCase`
  stashes on every check-in (`previousStreak`/`previousCompletedDate` on `Habit`) — needed because a
  weekly+ habit's grace period means "just subtract a day" can't reconstruct what
  `lastCompletedDate` actually was. Wired into the in-app checkbox too, not just the widget, since
  both already share the one use case.
- **One streak-math implementation, not two.** The check-in logic (§6.1's "no backlog, grace period,
  streak reset" test) was extracted from the in-app Tasks & Habits screen into a standalone
  `CheckInHabitUseCase`, called by both the screen and the widget's background action — the same
  centralization principle as `ResolveEntryUseCase` (§9.8 R1) and `EntryScheduleCoordinator`
  (§9.11): a second surface reimplementing streak math independently was exactly the kind of drift
  risk worth ruling out on sight, not after it first diverges.
- **Mechanism**: Jetpack Glance's `ActionCallback`/`actionRunCallback` (§9.6) — the tap fires a
  background action that updates Room directly and then explicitly re-renders the widget, mirroring
  how §9.6 already documented that widget colours need an explicit refresh trigger rather than
  recomposing automatically.

### 8.2 Background opacity, and the contrast problem it creates

A background-opacity slider (0–100%, matching Fossify Calendar's reference pattern) sits alongside
the theme controls. This surfaced a real, thoroughly-tested finding worth preserving in full,
because it shapes exactly how widget colours are allowed to be built going forward:

**The adversarial worst-case (a wallpaper bracketed as literally pure white AND pure black
simultaneously) makes *any* fixed opaque text colour fail at some opacity level — mathematically,
not as a color-choice failure.** Verified: for a light-mode widget, the white-wallpaper bracket is
flat (white blended with white stays white regardless of opacity), but the black-wallpaper bracket
sweeps its own effective luminance from 0 to 1 as opacity goes 0→100%, and for any fixed text
luminance strictly between black and white, that sweep must cross the text's own luminance somewhere
— contrast against your own luminance is exactly 1:1. No hue, no lightness choice escapes this
against a truly adversarial bracket; only the *location* of the failure moves.

**The reframe that actually matters (Decided):** a real wallpaper isn't adversarial — it's one
specific luminance. Tested directly: for a single fixed wallpaper luminance, wide safe zones of
(text lightness × opacity) genuinely exist and are easily reachable — e.g. against an 80%-luminance
wallpaper, 238 of 624 tested (lightness, opacity) combinations pass 4.5:1 outright. **Lightness is
the lever that matters; hue is essentially free once lightness is in a safe band** — confirmed by
sweeping all 72 hues at 5° steps at one safe lightness against a real wallpaper: all 72 passed. A
hue-only slider (the original proposal) would not have delivered the intended benefit; lightness had
to be the primary control.

**Bonus finding, not yet acted on:** light-mode and dark-mode widgets have *opposite*
wallpaper-safety profiles — light mode is safe against bright wallpapers and struggles against dark
ones, dark mode is the mirror image. A future refinement could suggest widget mode based on
detected/declared wallpaper darkness. **Deferred.**

### 8.3 `accent2` and the Shade/Hue system (Decided, built)

A secondary accent colour, distinct from the app's primary accent, applied to three specific
elements: the Date widget's number, and the Agenda list's day label and time label.

*(**Corrected 2026-09-04:** this listed four elements, including "the Monthly grid's weekday header
letters" — which §8.4's own fix #2, further down this same section, then reassigns to `textDim`
because those letters are the only label in their row. The two statements are incompatible and the
build follows §8.4. §8.4 wins: it is the later finding, it is the one backed by a measured contrast
failure, and the weekday row is now deliberately outside the Shade/Hue controls' reach. The
prototype's CSS still colours it `--w-accent2`, i.e. the prototype predates fix #2 here.)*

- **Generation**: an Analogous hue rotation (±30°) off each theme's primary accent — chosen over a
  full complementary rotation to stay consistent with the app's restrained, cohesive visual
  identity. Rotation direction (+30° or −30°) is picked per theme for the strongest natural
  contrast, then kept identical between that theme's light and dark mode so the colour's character
  doesn't flip when switching modes. Ink, Clay and Moss rotate −30°; Mauve rotates +30°.
  **Two exceptions to "rotation preserves lightness" (recorded 2026-09-04 — real all along, never
  written down):** Moss-light's base lightness is 0.427 rather than the accent's own 0.490, and
  Mauve-light's is 0.514 rather than 0.551. Both were darkened because the pure rotation fails AA
  against a white background — Moss-light would sit at 3.61:1 and Mauve-light at 4.01:1, against a
  4.5:1 requirement; at the stored values they reach 4.59:1 and 4.62:1. Same "nudged darker, hue
  preserved" move §8.4's fix #3 records for `textFaint`, and the shipped values are correct; it was
  only the rule as stated here that claimed a pure rotation with no exception.
- **Shade** (0–100%): interpolates lightness from the original vivid tone toward a
  verified-safe extreme — L=0.12 in light mode, L=0.88 in dark mode — per theme's own exact base
  lightness (not a shared generic value; an early implementation bug used one generic lightness per
  mode instead of each theme's real value, caught and fixed before shipping).
- **Hue** (±90°): rotates around the theme's own accent2 base hue. Confirmed to cost almost
  nothing in contrast once Shade is high — exactly the "hue is free" finding from §8.2, now
  user-controllable rather than fixed.
- *(**Corrected 2026-09-06: neither is a ~~slider~~.** Both ranges shipped exactly as decided, but the
  control did not. Two flat gradient bars side by side read as near-identical rows, so Shade and Hue
  were folded into a single radial picker — radius is Shade, angle is Hue, −90° left, 0° up, +90°
  right — and one drag sets both. The colour model above is untouched; only the affordance changed.
  Worth correcting rather than leaving, because a section that names the control twice is the one a
  reader checks their build against.)*
- **Default state** (shade=0, hue=0) exactly reproduces the original fixed accent2 hex values —
  verified byte-for-byte across all 8 palettes before shipping, so nothing changes for anyone who
  doesn't touch the new sliders.
- The live contrast-audit readout (§8.4) reflects whichever combination of
  colour/mode/shade/hue/opacity is currently selected, computed live, not cached.

### 8.4 Live contrast-audit tool

Built directly into `noema-widgets-audit.html` rather than delivered as a static report,
specifically so it stays correct as the person keeps adjusting settings. Computes worst-case WCAG
contrast (against the pure-black/pure-white bracket, §8.2) for every text role at the currently
selected colour/mode/opacity/shade/hue, and shows a plain pass/fail readout with the specific
offending role and ratio when it fails.

Real bugs this process caught and fixed, worth keeping on record since they're the kind of thing
that would otherwise resurface as a mysterious rendering bug later:
1. **Duplicate HTML `style` attributes** were silently dropping every widget's width/height (the
   browser keeps the first `style="..."` on a tag and ignores a second one) — fixed by merging all
   inline styles into a single attribute at the source.
2. **Token reuse mistake**: `textFaint` (deliberately low-contrast in the main app, meant for
   secondary text sitting *beside* primary text) was reused in the widgets for content that was the
   *only* label in its row — weekday letters, agenda times, an overflow count. Promoted those
   specific usages to `textDim`, which was designed for that job.
3. A follow-on edge case from fix #2: the navigation chevron icons (the only remaining `textFaint`
   usage after the fix) sat at 2.82:1 against a fully opaque light background — just under the 3:1
   icon-contrast minimum. The four light-mode `textFaint` values were nudged slightly darker (hue
   preserved) to clear it.

### 8.5 Reference research: Fossify Calendar

The user's uploaded reference (`Calendar-main.zip`, an open-source Fossify Calendar project) was
checked directly at the code level (`WidgetMonthlyConfigureActivity.kt`) for how it handles this
same background-opacity-vs-text-legibility problem. Finding: **it doesn't.** Background alpha and
text colour are two entirely independent, manually user-picked values with no coupling, no contrast
computation, and no warning — confirming that even a mature, published app accepts this as an
inherent, unmitigated trade-off. Tendril's live readout already goes further than the reference by at
least surfacing the risk, even before the Shade/Hue system existed.

### 8.6 What the live readout still does *not* model

The readout tests the full adversarial bracket (§8.2), which no real wallpaper matches — it will not
show PASS at low opacity even with Shade maxed, because that was never the achievable target.
~~**Deferred, not decided:** whether to extend the tool to model a *specific* declared or sampled
wallpaper luminance instead of the adversarial bracket, which would let it report realistic
pass/fail rather than worst-case-only.~~ *[Amended] 2026-09-22 — **resolved, on the phone's own
wallpaper**: the configure screen reads the system's `WallpaperColors` (the primary colour and its
dark-text hint — no permission, API 27+) and shows a second readout, **On your wallpaper**, with
the same five roles on the widget background composited over that one colour, beside the bracket
that stays the worst case; and it names **the smallest opacity at which every role passes there**
as a tap (`suggestedOpacity`, a multiple of 5). The mode stays the app's (§8.7), so §8.2's
"mode auto-suggestion" is this opacity suggestion instead — the control the screen has. A system
that reports no colours (a live wallpaper) leaves the bracket as the guide. Walked 2026-09-22:
a red wallpaper (#AC0B23), 88 % → every role passes and 35 % offered; at 35 % the bracket fails all
five and the wallpaper passes all five (`docs/critiques/widget-wallpaper-function.md`).
`WallpaperAuditTest` — the widget module's first tests.*

### 8.7 Widget configuration flow (Decided 2026-08-26)

Three candidates, scored against §8.2/§8.3's own finding that opacity/Shade/Hue safety is
wallpaper-dependent (and a device's separate home screens can show entirely different wallpaper
regions behind different widget placements), consistency with the app's existing "theme is one
global choice" pattern (§2.3), fit with the Fossify Calendar precedent already studied first-hand
(§8.5), implementation effort, and the actual flexibility a person gets (weights
0.30/0.20/0.15/0.20/0.15):

| Option | Wallpaper-fit | Global-theme consistency | Fossify-precedent fit | Effort (inverted) | Flexibility | Score |
|---|---|---|---|---|---|---|
| Fully global (widgets always mirror in-app Settings) | 0.10 | 1.00 | 0.10 | 1.00 | 0.10 | 0.460 |
| Fully per-instance, Fossify-style (theme+mode+Shade+Hue+opacity all configurable per widget) | 1.00 | 0.50 | 1.00 | 0.50 | 1.00 | 0.800 |
| **Hybrid: opacity/Shade/Hue per-instance, theme/mode global (chosen)** | 0.95 | 0.90 | 0.60 | 0.75 | 0.75 | **0.8175** |

**Decided:** `AppWidgetConfigureActivity` asks only for the wallpaper-dependent controls — opacity,
Shade, Hue (§8.2, §8.3) — per placed widget instance, defaulting to shade=0 / hue=0, which by
construction *is* the current theme's own unmodified accent2 (§8.3's byte-for-byte default state),
and to a fixed 88% opacity. *(**Corrected 2026-09-06:** this read ~~"defaulting to whatever the
currently-selected in-app theme's values are at placement time"~~, which suggests all three defaults
are sampled from the live theme. Shade and Hue effectively are, because zero *means* the theme's own
value. Opacity is not and cannot be — no in-app surface carries an opacity, so there is nothing to
sample, and the 88% is a constant chosen for the widget alone.)* Theme (Ink/Clay/Moss/Mauve) and mode (Light/Dark) are
**not** offered in the widget config screen at all — they inherit live from Settings → Appearance
(§2.3), the same way every other themed surface in the app does, so a Settings theme change updates
every placed widget's base colors automatically rather than leaving old widgets stranded on a stale
palette. The fully-independent Fossify-style alternative scored close behind (0.800 vs. 0.8175)
precisely because it's real, already-studied prior art and is genuinely more flexible — but it
reopens the exact "a widget quietly drifts from the app's own palette" problem the shared theming
system (§2.3) exists to prevent everywhere else, for a personal build with no actual reason to run
mismatched themes across widgets on purpose.

**Acceptance:** `AppWidgetConfigureActivity` shows only opacity/Shade/Hue controls plus the live
contrast-audit readout (§8.4) computed against the values chosen; theme/mode fields are absent from
that screen entirely; a Settings-level theme change is reflected on next widget redraw without the
person having to reconfigure each placed instance.

---

## 9. Android Architecture & Build Plan

### 9.1 Architecture decision (Decided)

**Device preferences (2026-09-12, §0.10 item 12).** `shared/…/data/prefs/KeyValueStore` is the one
place a *device* preference lives on either platform — strings only, each consumer encoding its own
value; `MapKeyValueStore` is the shared body (a map behind one `StateFlow` per observed key) and
each platform supplies `persist`. Android's older `*Preferences` classes stay as they are; new
settings go here. Secrets never do (Android `SecretStore`; the desktop's key file, step 8g).

**Native Kotlin + Jetpack Compose**, not a WebView/hybrid wrapper around the HTML prototypes. Two
constraints force this regardless of preference: home-screen widgets cannot be WebViews (require
Jetpack Glance or legacy RemoteViews — a hard platform limitation), and "become the default calendar
app" requires native `CalendarContract` integration plus intent-filter registration. Once those two
pieces are necessarily native, splitting the rest of the app across a WebView shell *and* a native
side adds complexity without saving any — better to keep it one native codebase throughout.

**The HTML/CSS/JS prototypes are the design spec, not source to literally port.** Every colour,
spacing value, copy string, and interaction flow they encode is the ground truth for the Compose
implementation — this is the normal handoff pattern from a fast prototyping tool to real
engineering, not wasted work.

### 9.2 Toolchain (verified against current tooling, not assumed from training data)

- AGP is currently on the 9.x line (9.0 shipped January 2026, 9.1 and 9.2 since April), with real
  breaking DSL changes from the 8.x line.
- AGP's minimum required JDK is 17 — **JDK 21 is fine**, being newer than the floor.
- **Recommendation**: don't hand-pin exact AGP/Gradle/Kotlin/Compose version numbers now — let
  Android Studio's New Project wizard select current, mutually-compatible versions at scaffold time,
  since any numbers fixed today may already be stale by the time the project is actually created.
  *(**As built, read from the version catalogs 2026-09-21:** AGP 9.3.2, Gradle 9.7.1, Kotlin 2.4.10,
  KSP 2.3.11, Compose BOM 2026.08.00 on Android, Compose Multiplatform 1.12.0 for `shared` and the
  desktop, Room 2.8.4 (KMP), kotlinx-serialization 1.11.0; compileSdk 37, minSdk 30, targetSdk 36,
  Java 21; the desktop on Android Studio's JetBrains Runtime 21 for its custom title bar
  (`tendril-windows-spec.md`, 2026-09-17). Builds run `--offline` from the Gradle cache.)*
- minSdk 30 is well-supported by current tooling; compileSdk should simply be whatever "latest
  stable" the chosen AGP offers.
- Single-device personal use means: a locally-generated signing keystore is sufficient (no Play App
  Signing needed), and distribution is a direct `assembleRelease` + manual/`adb install` — no need
  for an Android App Bundle (`.aab`), which is a Play-Store-specific format.

#### 9.2.1 targetSdk 36 (Android 16) behavior changes (Decided 2026-07-13, verified current)

These apply the moment `targetSdk 36` is set, as OS-level enforcement — regardless of sideloaded
distribution, since they're platform behavior, not Play Store policy:
- **Edge-to-edge can no longer be opted out of** — the old `windowOptOutEdgeToEdgeEnforcement` flag
  is ignored. `WindowInsets` handling needs to be designed into the Workbench nav shell (bottom tab
  bar) from Phase 1, not retrofitted later.
- **`onBackPressed()` is never called** — all back-handling (Road Map's canvas, the Pages editor,
  custom sheets) must use `OnBackPressedDispatcher`/predictive back from the start.
- **16 KB page-size alignment** for any native `.so` libraries — almost certainly a non-issue for a
  pure-Kotlin/Compose stack; worth a one-time check now that Shizuku (a native-adjacent dependency)
  is no longer in the picture (§9.3).

### 9.3 Folder access: SAF, not Shizuku (Decided 2026-07-13 — supersedes the original Shizuku plan)

Shizuku was originally planned to work around scoped storage for the Habit-folder/export use case,
but that overstates the problem: `ACTION_OPEN_DOCUMENT_TREE` (Storage Access Framework) gives a
persistable read/write grant to a user-chosen folder — no root, no privileged API, survives reboots
via `takePersistableUriPermission()`. This is the standard scoped-storage-compliant mechanism for
exactly this case, and it's what the real Syncthing Android app itself uses for folder access. If
Tendril and the Syncthing-fork app each get their own SAF grant (or plain filesystem access) to the
same physical folder, both can read/write it with no coordination needed.

This removes an entire isolated, high-external-dependency build phase (Shizuku pairing,
reactivation-after-reboot on Android 11–12, wireless-ADB setup) — folder access is now a single
one-time system folder picker, foldable into Phase 1 rather than its own late phase (§9.9).

### 9.4 Syncthing-fork integration & multi-device backup safety (Decided 2026-07-13 — expanded scope)

Confirmed: Tendril does **not** embed or manage a Syncthing engine. An external Syncthing-fork app
already handles sync separately; Tendril only needs to read and write files inside the SAF-granted
folder (§9.3) that app is already syncing.

Scope now explicitly includes **two devices writing to the same synced folder virtually
concurrently** — a real, if limit-case, personal-multi-device scenario — not just the original
single-writer Habit-folder case:

- **The live Room database is never synced.** Only export snapshots are. Each device's Room DB is
  its own local source of truth; syncing raw `.db`/`.db-wal`/`.db-shm` files is a real corruption
  risk and contradicts local-first.
- **Snapshots are split by domain** (`entries_active.json`, `entries_archived.json`, `habits.json`,
  `pages/<uid>.json`, …) rather than one giant file, so an edit to one Page doesn't create sync
  tension with an unrelated Habit edit *(**corrected 2026-09-06:** this section wrote
  `pages/<page_id>.json` throughout, and the distinction is load-bearing rather than cosmetic —
  `Page.id` is a local Room row id that names a different page on every device, which is exactly the
  failure the `uid` column exists to prevent. The merge additionally refuses any record whose uid is
  not a UUID, so a hand-named file dropped into the folder is ignored rather than imported. Corrected
  here, at §9.4's debounce paragraph and at §9.4.1's file list; the Revision Log's own entries keep
  their original wording, being dated records of what was written at the time)* — a generalization of
  the single-file `anchor_snapshot.json`
  pattern an earlier, scrapped prototype used for habit-only sync. **Clarified 2026-07-16**: since a
  Row *is* a Page (§5.1), each Row gets its own `pages/<uid>.json` file too, same as any other
  Page — the page tree (including a Database's rows) is reconstructed from `parent_id` on read,
  mirroring exactly how Notion's own export structures a page's children (§7.1), rather than nesting
  Row data inside its parent Database's file.
- **Clarified 2026-08-08 (resolves the shared-array sync-tension question raised this pass) —
  Entries split into `entries_active.json` and `entries_archived.json` rather than one
  `tasks_events.json` array.** Scored against monthly-by-`start_date` sharding, a
  monthly-archive/active hybrid, a full per-record split matching Page/Row, and the original
  single-array design, across write-amplification, file-count overhead, implementation complexity,
  query/lookup ease, and growth-safety (weighted 0.30/0.25/0.20/0.15/0.10) — Active/Archived won
  clearly at 0.87 against 0.72 for the next-best hybrid.
  - **Active** = every Entry with `status = PENDING` (or an `EVENT` with no terminal state) and
    `deleted_at IS NULL` — the small, frequently-edited set Tasks/Calendar/Merged (§3.3) actually
    query against day to day.
  - **Archived** = everything else: `DONE`/`SKIPPED` one-offs, and anything with `deleted_at` set
    (Trash, §5.5.1) — ~~written once on the transition into that state, then read rarely (history,
    search, Trash) and essentially never rewritten again.~~
  - **Transition**: ~~resolving an Entry or trashing/restoring one (§5.5.1) moves its record between
    the two files as one added step inside those same operations — not a new mechanism.~~
  - *(**Both corrected 2026-09-06, and the second is better than what was specified.** The
    Active/Archived split is real and the file boundary is exactly where this section puts it, but the
    write pattern it was scored on is not what was built: `writeSnapshots` rewrites **both** files
    wholesale from `entryDao.getAll()` on every pass, so `entries_archived.json` is re-serialised on
    every launch and every backgrounding rather than once on transition. And no "move between files"
    step was ever added to resolve/trash/restore — because none is needed. File membership is not
    stored anywhere; it is recomputed from `Entry.isActive()` each time a pass writes, so the two
    files are consistent with Room by construction. That is the stronger design: there is no
    per-operation step anyone can forget, which is precisely the failure the 2026-09-05 `PageDao.touch`
    entry is about. It is simply not the design this bullet describes, and it is why the next bullet's
    write-amplification claim does not hold.)*
  - ~~This directly neutralizes the growth-safety risk of Trash having no auto-purge (§5.5.1, previous
    correction)~~ *(**corrected 2026-09-06 — it does not.** There is one write path and both files are
    on it, so a growing Trash inflates every pass's write. What the split does neutralise is the
    **read** side: Tasks, Calendar and the Merged view query Room, never these files. The 0.87-vs-0.72
    score stands on query-ease and growth-safety; its write-amplification component does not.)*: an
    unbounded, never-emptied Trash inflates only `entries_archived.json`, which sits
    outside the hot write path, rather than inflating the file every active-task edit has to
    rewrite.
  - **`habits.json` stays a single shared file, unaffected.** Habit rows are one-per-definition
    (§4), not one-per-occurrence — a personal habit list stays in the dozens at most, nowhere near
    the volume that motivated splitting Entries; the same complexity there wouldn't pay for itself.
- **Writes are atomic**: write to a temp file, then rename over the target, so a partially-written
  file is never what gets synced mid-write.
- **Merge rule**: per-record last-write-wins on an `updated_at` timestamp — same principle as that
  earlier prototype's habit-sync design.
- **Conflict files are actively handled, not just trusted to LWW.** When Syncthing detects genuinely
  concurrent edits to the same file from two devices, it creates a
  `<file>.sync-conflict-<date>-<deviceID>.json` sibling rather than silently overwriting. Tendril
  checks for these on resume/launch, merges them via the same per-record LWW rule, then deletes the
  conflict file — *(**refined 2026-09-06:** only once its content has actually merged. The deletion is
  conditional, deliberately: an undecryptable file and an empty one are indistinguishable at the merge
  layer, since `decryptText` returns "" for both, so deleting on a failed merge was silent data loss.
  A conflict file whose passphrase this device does not hold, or one truncated mid-sync, is left on
  disk for the next pass or for a person to inspect. The same reasoning is why a `.tendril-lost-` copy
  is never swept: it is evidence, not an input.)*
- **Accepted v1 limitation**: for Pages specifically, LWW applies at the whole-page snapshot level —
  concurrent edits to the *same page* on two devices before either syncs means one edit is lost.
  Acceptable for a personal, limit-case scenario; flagged explicitly rather than left implicit.
  *(**Softened 2026-09-13, §0.6.13:** the losing body is kept in the losing device's page History
  before the winner replaces it, so it is recoverable by hand; the merge rule itself is unchanged.
  `page_revisions` is the first table that stays home — never in the folder, never in the archive.)*
- **UI**: a "last synced at ·" indicator plus a manual "Sync now" action.
- **Decided (2026-08-04, resolves the 2026-07-15 reopening — Page-snapshot write timing):**
  debounce, not per-mutation. A Page's `pages/<uid>.json` snapshot is written 2 seconds after
  the last block/property mutation to that page, reset on every further edit — not on every
  keystroke, which would make Pages' snapshot-write volume wildly out of proportion to Entry/Habit
  data for no real benefit (nobody is reading the synced file mid-typing). Two flush triggers bypass
  the debounce and write immediately regardless of the timer: **navigating away from the page**, and
  **`onStop`/app backgrounding** — so the normal "close the page, go do something else" moment is
  never left waiting on a timer. This leaves exactly one edge case, and it's a narrow one: the
  process is killed (not backgrounded — killed, e.g. by the OS under memory pressure) inside the
  2-second window with no navigation having happened yet. Room already has the edit by then
  (§3.1.2's instant-write is a Room commit, unaffected by any of this), so nothing is lost — only
  the *synced-to-other-devices* copy is briefly stale, self-healing on next launch's flush or the
  periodic background sync pass already implied by §9.4's "last synced at" UI. Chosen over a pure
  per-mutation write specifically because Pages are the one domain where a single user action
  (typing a sentence) fans out into many rapid mutations, unlike Entry/Habit edits which are already
  one mutation per user action.

- **Implemented 2026-09-04 — the triggers, not the debounce.** Everything above described *when* a
  sync pass should run, and until now none of it did: the only caller of `readAndMerge`/
  `writeSnapshots` anywhere in the app was the Settings "Sync now" button. So a `.sync-conflict-*`
  file sat undetected until someone went looking for it ("Tendril checks for these on
  resume/launch"), an editing session reached the folder only if the person remembered to tap a
  button, and the "periodic background sync pass already implied by the 'last synced at' UI" did not
  exist. Now:
  - a new `SyncCoordinator` is the one place a pass runs from — the lifecycle triggers and the
    button both go through it, so they can't overlap and a failure has somewhere to be reported
    from even when nothing is on screen;
  - **launch/resume** (`onStart`) runs a pass, which is what performs the conflict sweep;
  - **`onStop`/backgrounding** runs one too, skipped on a configuration change — a rotation also
    calls `onStop` and is not a backgrounding;
  - a pass is always read-merge-**then**-write, never a bare write. That ordering matters:
    `writeSnapshots` rewrites each domain file wholesale from this device's rows, so writing
    without merging first would drop records only the other device has. They survive on that
    device and return on its next pass, but merging first keeps the window as small as this
    design allows;
  - the pass runs on an application-scoped, non-cancellable coroutine rather than the Activity's.
    That is load-bearing rather than tidy: the Android `SyncFileStore` write is **not** atomic
    (§9.4's "write to a temp file, then rename over the target" is approximated — it renames the
    existing file aside, moves the temp into place, then deletes the old one), so a write
    cancelled by the Activity going away can leave the folder with no copy of that file at all,
    *(**corrected 2026-09-06:** this describes the store as it was before the write-then-swap
    reordering. `AndroidSafSyncFileStore.writeAtomic` now writes a temp file, moves the existing file
    aside to `<name>.bak`, renames the temp into place, and restores the backup if that rename is
    refused — so the worst case leaves the previous snapshot recoverable under `.bak`, never nothing
    at all, which is what `SyncFileStore`'s own contract promises. `writeAtomic` is not a suspending
    function either, so cancellation cannot interrupt it partway)*,
    in a folder Syncthing is actively watching.

  **Still not implemented, and deliberately not faked: the 2-second per-page debounce itself.** The
  flush triggers above are ~~the half that has somewhere to live~~ *(**corrected 2026-09-06:** one of
  the two is. `onStop`/backgrounding was wired; **navigating away from a page was not**, and cannot be
  with what exists — the block editor lives in `shared/` and holds no reference to `SyncCoordinator`,
  which is `:app`-only, so the same missing per-page write path that blocks the debounce blocks this
  trigger too. The practical gap is therefore wider than the paragraph below states: an edit made and
  navigated away from reaches the folder at the next backgrounding or launch, and not at all if the
  process is killed first)*; the debounce is the half that
  doesn't yet. It is specified per *page* — "a Page's `pages/<uid>.json` snapshot" — and
  `writeSnapshots` has no per-page mode, it rewrites every domain file from scratch. Running that
  whole-database write on a 2-second timer while someone types would be far worse than the
  per-mutation write this decision already rejected, for exactly the reason it rejected it. Doing it
  properly needs a per-page write path in the orchestrator plus a way for the block editor — which
  lives in `shared/` and knows nothing about SAF or a desktop file store — to reach it. Left open
  rather than approximated. The practical gap is narrow and self-healing: edits are in Room
  immediately (§3.1.2), and reach the folder at the next backgrounding.

- **Desktop is still manual (2026-09-04).** `onStart`/`onStop` are Android lifecycle callbacks, and
  the desktop companion has a more specific reason to stay on its explicit "Sync now" button: its
  passphrase is session-only and typed into the sync bar, never persisted
  (`tendril-windows-spec.md` §7), so at launch there is nothing to decrypt an encrypted folder
  with. An automatic pass there would either do nothing or, without the §9.4.2 guard, do harm.

- **Fixed 2026-09-05 — the timestamp the merge runs on is now actually written.** Everything above
  assumes a page whose content changed exports with a newer `updated_at`. Nothing in the app made
  that true. The merge gates its per-page last-write-wins on `pages.updated_at` alone, and outside
  the sync engine the only writers of that column were `updateTitle`, `softDelete` and `restore`.
  Every other mutation wrote only the child row — a cell edit just `property_values`, a block edit
  just `blocks.updated_at`, a canvas drag just `canvas_nodes` — so `pages/<page_id>.json` went out
  carrying new content under the timestamp it already had. On the other device that record was not
  *newer*, so it did not win, so the passes that apply schema, canvas content, blocks, tags and cell
  values all skipped it.

  It failed silently at both ends. On the editing device the change is in Room and looks saved,
  because it is — it simply never leaves. On the receiving device an **equal** `updated_at` is the
  same version by definition, so the record was never treated as a loser either and no
  `<uid>.tendril-lost-<updatedAt>.json` was written beside the winner. The edit was dropped with
  nothing anywhere recording that it had existed. Worse, it was then destroyed on the editing device
  too, the moment the other device made any timestamp-moving edit of its own — a title change, a
  trash, a restore — because that record legitimately won and the merge replaces a winner's content
  wholesale.

  `PageDao.touch(id, at)` — a column-scoped `UPDATE pages SET updatedAt`, narrow like
  `updateParentAndDatabase` so a bump can never carry a stale copy of another field with it — now
  runs on every write that changes a page's synced payload without rewriting the page row.

  It is reached through a **launcher rather than a second call**, which is the part that matters for
  it staying fixed. A bump you have to remember to write after the DAO call is the bug this whole
  entry is about, and it would come back the same way; so each of the four sites that mutate page
  content now has one entry point that performs the write and the bump together, and nothing else to
  remember. `PageDetailViewModel` keeps `launchAndReindex` (already there, because the FTS index has
  exactly the same trigger) and gains `launchTouching` for the mutations with no index to rebuild;
  `PageDatabaseViewModel` gets `launchAndTouch(pageIdToBump)`; `CanvasViewModel` gets
  `launchAndTouch`; and `DatabaseSyncManager`'s four binding operations all exit through one
  `commit`, so writing the `PageDatabase` row and saying its page changed are a single operation.

  **Which** page is the part that is easy to get wrong, so it is a *required parameter* of the
  database screen's launcher rather than a default: a cell hangs off its **row's** page, a property
  or a view off the **database's**, and a binding change off both, since crystallizing rewrites
  every row's stored values. Bumping the database for a cell edit would propagate the schema and
  still lose the cell.

  *Amended 2026-09-25 (audit 5a.2).* A row **joining** a database that already syncs — a new row
  (`addRow`) or a labelled page (`LabelMembership.ensureTask`) — is not a binding change, and no
  longer goes through one. Both called `enableSync` with the one row's id, and its `commit` touched
  the database page and every row, so adding one row claimed an edit to all of them and beat any
  unsynced edit to another row on the other device. They call `DatabaseSyncManager.addRowToSync`
  now, which seeds the row's linked Entry and writes nothing else: the bindings are unchanged, the
  bound columns are already empty, and the Entry is its own record with its own timestamp.
  `WritePathSyncTest` pins it, red first.

  Two mutations stay outside a launcher and say so at their own site. `setChecked` is gated by the
  narrower `viewOnlyLocked` (§3.1.2 keeps a to-do tappable on an otherwise locked page), and
  `addTag`'s bump is conditional — picking a tag the page already carries changes nothing, and
  bumping anyway would claim authorship of an edit that did not happen, which is enough under LWW to
  beat a real edit sitting unsynced on another device.

  **Whole-page LWW is unchanged.** The accepted v1 limitation above stands exactly as written; this
  only makes the timestamp it depends on tell the truth about what happened.

  Two writes are deliberately excluded: `ensureDefaultView` and the Canvas page's lazy `PageCanvas`
  shell. Both do write a synced row, but both run on *open* rather than on an edit, and under
  last-write-wins a bump is a claim of authorship — a device that merely opened a database would
  then outrank one that had genuinely edited its schema moments earlier and not yet synced. Neither
  costs anything by staying local, because every device performs the same repair for itself.

  Why a merge suite that covers §9.4 closely could pass throughout is worth recording, since it is
  the reusable part. `PageMergeTest` builds every record it merges by hand
  (`pageRecord(uid, title, updatedAt = N)`), which makes it structurally unable to observe the one
  thing that was wrong: whether the app, having changed a page, produces a record whose timestamp
  says so. The new `WritePathSyncTest` refuses to build a snapshot record at all — it mutates
  through the real ViewModels, exports, and merges into a second device's store, which is the only
  arrangement in which the defect is visible.

- **Fixed 2026-09-05 (same day, second pass) — what the merge destroys on the way past.** Making
  the timestamp move above was necessary and is not in question, but it changed how often the
  merge's destructive half actually runs. Before it, a page whose *content* changed rarely won
  anything, so the pass that rebuilds a winner's blocks, tags and cell values seldom ran over a
  page that already existed here. Now every synced edit makes a record win on the peer, and three
  things that were latent became routine.

  **The local copy is preserved too, not only the arriving one.** `candidateLosers` collected
  incoming records that lost, and nothing else — so `<uid>.tendril-lost-<updatedAt>.json` was
  written for the *other* device's version and never for this one's. When an arriving record won,
  whatever this device held was overwritten and gone, with no file naming it on either side. The
  local export is now captured before Pass 1 (there is nothing left to capture afterwards) and
  returned alongside the incoming losers. Compared on content with the timestamp normalised, since
  the timestamps necessarily differ — that is *why* the remote won — and comparing the records
  whole would preserve a copy on every routine catch-up where nothing was lost at all.

  **`Block.imagePath` survives the rebuild.** It points into this device's app-private storage and
  is deliberately excluded from the snapshot, so an arriving record can never carry one; rebuilding
  from that record wrote null over it and unlinked the picture with the file still on disk. Held by
  uid across the delete-and-reinsert instead. The remote has nothing to say about where this device
  keeps its own copy.

  **A property absent from a schema is no longer deleted.** This pass was the single exception to
  the rule stated one file away in `SnapshotSyncOrchestrator.readAndMerge` — *"absence still never
  implies deletion… a real hard-delete propagates instead as an explicit tombstone"* — and the
  exception was encoded in a passing test, which is why it survived review. A device that had not
  yet seen a new column re-exported the database without it, and the column plus every row's value
  under it went with it, on an ordinary two-device schema race, silently and with no preserved
  copy. `PurgedKind.PROPERTY` supplies the signal absence could not: `PurgeRegistry.purgeProperty`
  records the tombstone and drops the column as one operation, the merge refuses to re-insert a
  tombstoned uid, and `PageDatabaseViewModel` deletes through the registry rather than the DAO.
  Unlike the other kinds there is no supersede check, and none is needed: a `Property` carries no
  timestamp to compare, and `Property.uid` is minted fresh, so re-adding a deleted column produces
  a *different* property that no tombstone names.

  Not fixed here, and named so it is not mistaken for done: a preserved lost version is still only
  a file in the folder — nothing in the app tells you one exists. 261 unit tests.

- **Open (recorded 2026-09-07 — stated, not fixed): a record this build reads *well enough* is
  republished with the fields it did not understand stripped out.** Milestone 0's quarantine policy
  (Revision Log, 2026-09-07) closed the case where an arriving record cannot be read at all. It
  left the opposite case untouched, and the asymmetry between the two is the whole reason this
  needs writing down rather than living in a commit message.

  `SnapshotSyncOrchestrator` configures its `Json` with `ignoreUnknownKeys = true`, and all 24
  `@Serializable` declarations across `SnapshotRecords.kt` and `PageSnapshotRecords.kt` are plain
  data classes with no catch-all — no `JsonObject` field, no leftover-property map, in either file.
  So a record written by a newer build that carries one *additional* field, and is in every other
  respect perfectly readable here, decodes with that field silently discarded. The merge then
  adopts the decoded object into Room; and the next write pass re-encodes that record **from Room
  rows** — `pagesSyncEngine.exportPages()`, and `entryDao.getAll()`/`habitDao` through
  `SnapshotMappers`, never from the bytes that arrived — so the copy that goes back into the folder
  no longer contains the field. Every other device, including the newer one that wrote it, then
  adopts *that*. The field is not merely stale on one device: it is destroyed folder-wide, on the
  ordinary sync pass that runs on every launch and every backgrounding (§9.4's `onStart`/`onStop`
  triggers).

  **The asymmetry, which is what makes it subtle.** A record this build *cannot* read is now the
  safe one. `mergePages` quarantines it; `HeldRecords` holds it as a raw `JsonElement` rather than
  as the typed record it used to hold, precisely so that nothing round-trips through a data class;
  and `publishArrayFile` appends it back byte-faithfully (pinned by `UnknownEnumQuarantineTest`'s
  *"a quarantined record is republished with the fields this build has no class for intact"*). It is
  exactly the records this build understands *well enough to adopt* that lose data — the failure
  gets quieter as the two builds grow closer, which is the reverse of the intuition, and there is no
  unreadable value anywhere in the file for a guard to trip on. Nothing reports it either, and no
  `.tendril-lost-` copy is kept: unlike a lost merge race, the field is gone before any comparison
  is made, so nothing on this device ever knew it had existed.

  **A second, smaller accepted loss on the same theme (recorded 2026-09-07).** `PurgedKind` is read
  by a Room converter that cannot return null, so an unrecognised tombstone kind falls back to
  `PROPERTY` — chosen because it is the narrowest of the four and, every uid being a random UUID,
  cannot realistically match a local column. At HEAD this line was a bare `valueOf`, and because
  `PurgeRegistry` loads every tombstone at the top of every merge pass, one unrecognised kind
  aborted **every** sync this device would ever run, against every peer. That was loud and destroyed
  nothing; the fallback is survivable and destroys a little. The mislabelled row re-exports to every
  peer as `PROPERTY`, so a PAGE or ENTRY delete instruction from a newer build stops propagating and
  the record it named can resurrect on a peer that had not yet applied it. The trade is still the
  right one, and the reach is narrow — verified, not assumed: the snapshot path can never insert an
  unknown kind into Room, so the only route is running a newer build here and then downgrading. It
  is recorded because an unrecorded cost is one a later reader assumes was never paid. The proper
  fix is the same as above — keep the unreadable kind string rather than folding it to a member —
  and it needs a schema change on `purged_records`, so it lands with §9.10's migration work.

  **Why it is deferred rather than fixed here.** No guard placed at a sync boundary can close it:
  by the time any boundary sees the record, the field is already gone, discarded by the decoder
  before the merge ever ran. It is a **format change** — every record carrying the raw
  `JsonObject` it arrived as alongside its typed fields, and merging the two on re-encode so
  unknown members survive the round trip. That rewrites `SnapshotRecords.kt` and
  `SnapshotMappers.kt` end to end — which is exactly what the backlog's SYNC lane
  (`.claude\workflows\complete-tendril.PLAN.md`, items S2/S3/S4) already does for Reminder and
  EntryCompletion sync, the restore fallback and images, and which that plan marks strictly serial
  with each other *because they are the same two files*. Doing this one separately means rewriting
  both files twice. So it belongs with S2–S4 and is sequenced there, not skipped.

  Said plainly, because a deferral that reads like a fix is worse than no record: **this is not
  fixed today.** It bites the first time two builds of different versions share a folder — which
  needs nobody to choose it, since the pass runs itself on every launch and every backgrounding, so
  whichever build happens to be installed reads and rewrites the folder unasked.


### 9.4.1 Portable export/import (Decided 2026-07-16)

Distinct from §9.4's continuous background sync feed between two of a person's own devices — this is
a one-off, user-initiated, shareable file: a full backup, a single Page/Database sent to someone
else, or a Notion export being brought in. §3.5's "full data import/export" previously pointed at
§9.4's mechanism directly, which conflated a live sync feed with a portable file and left import
behavior (merge vs. replace) completely unstated — the actual cause of the risk flagged for
busy-parent-style cases (two people's separate agendas, one import away from silently overwriting
the other's data).

- **One schema, reused everywhere.** The exact per-domain JSON shapes §9.4 already defines —
  `entries_active.json`, `entries_archived.json`, `habits.json`, one `pages/<uid>.json` per Page
  or Row — are the only serialization format; a portable export doesn't invent a second one, it just
  packages the same files differently. *(**Corrected 2026-09-06:** that list has been closed-ended
  since 2026-07-16 and the format has grown twice since. It is now `entries_active.json`,
  `entries_archived.json`, `habits.json`, `page_relations.json` (§3.4's relation edges),
  `purged_records.json` (§5.5.1.1's tombstones), and one `pages/<uid>.json` per Page or Row. One file
  is deliberately **not** portable: `sync_meta.json`, which carries a folder's salt and its
  "this folder is encrypted" marker — properties of one sync folder, meaningless inside a package
  meant to leave it. That asymmetry is exactly why a `.tendril` and a sync folder do not in fact
  encrypt identically; see §9.4.2's correction of the same date.)* *(**Corrected 2026-09-10 — the
  list has grown a third time, and this one is not JSON.** S4 added `images/<block uid>.<extension>`,
  the block pictures §3.1.1 allows, carried as raw bytes. It is the same directory name and the same
  naming rule the sync folder uses, because it is the same channel in a different container — which
  is what lets the import path reuse the folder fetch's own "which image belongs to which block"
  decision rather than restating it. The bullet's claim survives intact and is worth restating for
  it: a portable export still invents no second format, it packages what §9.4 already defines. What
  needs correcting is only the word "JSON" — an archive reader must now expect one directory whose
  entries must not be decoded as text. **The encryption asymmetry this bullet recorded is closed
  (2026-09-10).** A packaged picture is encrypted because this class encrypts per zip entry rather
  than per file type; for one item the same picture in the sync folder was not, since §9.4.2's
  scheme wrapped text payloads only. The folder half now seals images too — see §9.4.2's own
  entry below. The two containers still differ in one way, and deliberately: the folder gives an
  encrypted image the opaque name `<block uid>.tdrlimg`, while an archive keeps `images/<block
  uid>.<extension>`. Renaming inside an archive would buy nothing, because its entry list already
  names every page uid in the clear — `manifest.json` is readable by design.)*
- **Packaging**: a zip, given a dedicated extension so it behaves as one shareable file rather than
  a loose folder (the same trick `.docx`/`.epub` use) — **`.tendril`**, decided 2026-08-04 alongside
  the app name itself. Contains a `manifest.json` (app version, export timestamp, `full` or
  `partial`, and the exact list of domain/page files included) plus whichever domain files the
  export actually contains.
- **Two distinct actions, not one "Import" with hidden behavior:**
  - **Import** — ~~always additive, never replaces~~ **additive for *records*, and destructive for
    anything the package's tombstones name (corrected 2026-09-06)**. When this was decided on
    2026-07-16 absence never implied deletion and nothing in a package could remove data. §5.5.1.1's
    travelling tombstones changed that, §9.4's merge rule was narrowed to match, and this bullet was
    not: `purged_records.json` is adopted and applied *before* any record merges, so importing a
    package hard-deletes local Pages, Entries, Habits and Properties whose uids it declares purged —
    superseded only where the local record's `updatedAt` is strictly newer, and for a Property not
    superseded at all, since a Property carries no timestamp of its own. That is the right behaviour,
    because a "Delete forever" an import quietly undoes is not one; it is simply not what "never
    replaces" says, and the picker below is where a person should be able to decline it. Otherwise
    unchanged: it reuses §9.4's existing per-record last-write-wins merge rule (no new merge logic
    invented) and, for a Page, the same accepted whole-page-snapshot LWW limitation already in place
    for cross-device sync. Before merging, the manifest drives a
    picker showing exactly what's in the package, letting the person choose what to actually bring
    in rather than all-or-nothing — this is the direct fix for two people wanting to combine
    separate agendas without one silently overwriting the other. *(**Not built, recorded 2026-09-06.**
    The manifest is written with everything such a picker would need — `includedFiles`, `kind`, the
    export timestamp — and nothing reads it; Import merges the whole package. So the specific risk
    this bullet exists to fix is unmitigated, and is sharper now than when it was written, because an
    imported package also carries tombstones that delete. Until the screen exists, Import is
    all-or-nothing and should be described that way. Blocked on nothing but the screen: the data it
    would list is already in the manifest.)* Notion import (§7.3) is just one
    flavor of this same path — it was always additive-only in practice, never a full-app replace, so
    it never had a destructive-replace risk to begin with; only manually-created or
    manually-exported Tendril packages did.
  - **Restore from backup** — a separate, deliberately harder-to-reach action, worded unambiguously
    about what it does (e.g. requiring the person to confirm they understand current data will be
    erased, not a soft dialog matching §5.5's lighter pattern). Natural fit for a fresh/empty install
    or genuine disaster recovery; not reachable via the everyday Import path. *(**Corrected
    2026-09-06:** ~~full wipe-and-replace~~ describes the intent and the fresh-install case, not the
    code. It wipes Entries and Habits, but **merges** Pages: blocks, tags, canvas nodes and cell
    values are not bulk-deleted, they go through the same whole-page LWW merge Import uses, which
    behaves as a replace only when there is nothing local to lose. Restore onto a *populated* device
    therefore leaves any local page the archive does not carry, and lets a newer local page beat the
    archive's copy — which is not what "erased" implies, and the confirm dialog already states the
    narrower truth. Either this section matches the dialog or the wipe is extended to pages and the
    dialog reworded; recorded rather than silently fixed because §9.10 leans on this path as its
    last-resort migration recovery, and which of the two is true decides whether that reading
    holds.)*
- **Selective export** lives on each Page's and each Database's own "···" menu — exporting that
  item, with a prompt (mirroring Notion's own "include subpages" option, §7.1) for whether to bring
  sub-pages along. *(**Not built, recorded 2026-09-06.** Unimplemented at both ends: there is no "···"
  entry on a Page or a Database, and `PortableArchive.export` has no partial mode — it always writes
  `kind = "full"` and always packages every page. The manifest's `full`-versus-`partial` field and the
  per-page snapshot files it would select from both already exist, so what is missing is the selection
  UI and a filter on `exportPages()`, not the format. Worth stating plainly because §9.4.2's "send a
  single Page to someone else" consequence is written as though this shipped: today the only thing
  anyone can send is the whole database.)* A full-app export stays in Settings (§3.5) alongside the SAF sync-folder
  permission.

### 9.4.2 At-rest snapshot encryption (Decided 2026-08-08 — optional, off by default)

Syncthing (or its fork) encrypts data in transit between devices, but the snapshot JSON files above
sit as plaintext on-disk, on every device, for as long as they exist inside the SAF-granted folder —
a real exposure on a lost/stolen phone or a compromised sync-fork install, for data that includes
medical appointments (§5.1) and financial recurring bills (§5.2.2). Settings → Sync folder
permission (§3.5) gains an adjacent **optional passphrase field**; encryption is on exactly when a
passphrase is stored. *(**Corrected 2026-09-06:** no ~~toggle~~ was built, on purpose — a switch and a
passphrase are two pieces of state that can disagree, and "toggle on, no passphrase" has no meaning.
Read "when the toggle is on", used throughout this section, as "when a passphrase is set". The
plain-language warning this section asks for lives on the Save confirm dialog instead, and does name
the consequence: losing this passphrase makes the synced folder unreadable on any new device.)*

- **Off by default** — matches the app's consistent "opt-in, nothing surprising by default" pattern
  (Anthropic key, Google OAuth, App Lock §3.6).
- **On**: every snapshot file is encrypted before the atomic write above and decrypted after read,
  using a passphrase-derived key (Argon2id or PBKDF2 into AES-256-GCM — the exact choice left to
  implementation, not a spec-level decision). The same passphrase must be entered on every device
  sharing the sync folder, since Tendril has no account/identity system to distribute keys through.
  The passphrase itself is never written to disk or synced; only a Keystore-backed `SecretStore` — an
  `AndroidKeyStore` AES/GCM key wrapping the value inside ordinary `SharedPreferences` — holds it
  locally per device, re-entered once per install. *(**Corrected 2026-09-06:** this said
  ~~`EncryptedSharedPreferences`~~, which the app deliberately does not use: Jetpack Security
  deprecated it in 1.1.0 in favour of direct platform Keystore use, which is what was built, and which
  §9.5.1 already cites as this codebase's standing preference for platform APIs over an added
  abstraction. The security property the bullet depends on is unchanged and worth restating plainly:
  the wrapping key is hardware-bound to **this** device, so the stored passphrase cannot be carried to
  another one even by copying the app's data directory.)*
- **The PBKDF2 salt lives in the folder, not in the binary** (`sync_meta.json`, added 2026-09-05).
  *(**Corrected 2026-09-06 — the folder salt does not reach exports.** Everything in this bullet holds
  for the sync folder. It does not hold for `.tendril` packages, which the "same scheme" bullet below
  brings under the same promise: `PortableArchive.keyOrNull()` calls `deriveKey` with one argument and
  so takes `SnapshotEncryption.LEGACY_SALT`, the compile-time constant this bullet exists to retire.
  Both weaknesses named below therefore survive in the file **most** likely to leave the device — one
  precomputed table works against every Tendril export in existence, and two people choosing the same
  passphrase produce byte-identical keys. It is at least self-consistent, since import derives the same
  way and round-trips work, and it is not careless: an archive cannot carry a folder's salt without
  carrying the folder's identity, and `sync_meta.json` is deliberately not packaged. The fix is the
  same reasoning one level down — a per-archive random salt in the plaintext `manifest.json`, beside
  the `encrypted` flag, because a salt is not a secret and the manifest is the channel. Until then,
  "the same at-rest protection as continuous sync" overstates it, and the salt is the one thing the
  two do not share.)*
  The original reasoning above — that a random salt needs a channel to distribute it and Tendril
  has none — was wrong in one respect: the sync folder *is* the channel, the same one the
  snapshots travel through. A compile-time salt meant one precomputed table worked against every
  Tendril install in existence, and two people choosing the same passphrase got byte-identical
  keys. The meta file is deliberately never encrypted (a device without the key still has to read
  the salt in order to derive it; a salt is not a secret, it defeats precomputation in the open),
  and a folder written before it keeps its original salt so it stays readable. Two devices that
  enable encryption before either has synced both mint one — earliest `createdAt` wins, ties
  broken on the salt's Base64 text, so every device converges without negotiating. *(**Corrected
  2026-09-06:** the comparison runs over the encoded string as it appears in `sync_meta.json`, not the
  decoded ~~bytes~~. The convergence property claimed here is unaffected — the ordering is total and
  identical on every device either way — but the two orderings are not the same ordering, and a
  reimplementation that sorted decoded bytes could pick the other salt and lock itself out.)*
- **A folder that says it is encrypted does not accept plaintext** (added 2026-09-05). Until the
  meta file existed there was no way to tell an injected plaintext file from a folder that had not
  been encrypted yet, so any non-encrypted file was trusted — which made AES-GCM's authentication
  tag worth nothing at the system level, since nothing forced a file to be encrypted at all.
  Anyone who could write to the synced folder could inject records without the passphrase.
  *(**Extended 2026-09-10 to the image channel, which it did not previously cover.** The rule was
  implemented inside `decryptText`, so it guarded JSON and nothing else — which left §9.4's
  `images/` directory as the one path an attacker with folder write access could still inject
  through, and image bytes go to a platform image decoder rather than to a JSON parser. Refused as
  unreadable, never deleted, exactly as an undecryptable snapshot is.)*
- **Block images are encrypted too, and their names with them** (added 2026-09-10). §9.4's
  `images/` channel carried raw bytes under `<block uid>.<extension>` regardless of the
  passphrase, because this section'''s scheme wrapped text payloads and an image is the one payload
  that is not text — so an encrypted folder protected the note that mentioned a photograph and not
  the photograph. An encrypted folder now holds `images/<block uid>.tdrlimg`: the original file
  name and the bytes, sealed with the same key as every snapshot. **The uid stays in the clear on
  purpose** — it is what lets a fetch be driven by the folder listing rather than by whichever
  pass merged the page record, so an image and its record may arrive in either order; hiding it
  would cost that property and buy little beside an encrypted `pages/<uid>.json` naming the same
  uid. The *type* and *size* are what the opaque name withholds. A folder with no passphrase is
  unchanged and stays browsable, `<uid>.png` and raw bytes: not encrypting is the choice to leave
  the folder readable by anything, and renaming files would remove that while protecting nothing.
  Consequently a re-key must **rewrite** every image (snapshots re-encrypt themselves by being
  rewritten each pass; an image is written once and skipped thereafter, so it would otherwise stay
  sealed under a key nobody holds), and turning encryption on must **delete** the plaintext copy
  the sealed one replaces. A `.tendril` package encrypts the same images but keeps
  `images/<uid>.<extension>`, since its entry list already names every page uid in the clear.
- **Changing the passphrase and re-keying the folder are different acts** (added 2026-09-05).
  Saving a passphrase changes what *this device* uses to read the folder; it does not touch the
  folder, so if the two disagree, sync pauses rather than overwriting anything. Re-keying changes
  the folder itself, and is offered as its own action because it is destructive in a way the
  first is not: re-encrypting is decrypt-then-encrypt, so it can only preserve what the
  re-keying device can actually read. The precondition is therefore a *successful merge under
  the current passphrase*, not a warning — a device that cannot read the folder has no business
  rewriting it, however deliberate the person was. Other devices are not harmed by a successful
  re-key: they find the folder unreadable, refuse to write to it by the same rule, and once given
  the new passphrase they merge and republish their own rows. The unrecoverable case is a device
  that no longer exists whose rows lived only in the folder, and that is what the precondition
  protects. Re-keying *to* no encryption is deliberately not offered — turning encryption off
  over an encrypted folder is the cleartext downgrade this section already refuses.
- **Conflict files (§9.4) and portable export/import packages (§9.4.1 above) are encrypted under the
  same scheme when the toggle is on** — a `.tendril` export carries the same at-rest protection as
  continuous sync, not a separate case to design.
  - **Implemented 2026-09-04 for the export half.** Conflict files were encrypted from the
    start; `.tendril` packages were not. `PortableArchive` never referenced `SnapshotEncryption`
    at all, so with the toggle on, **Export was the plaintext way around it** — carrying exactly
    the medical (§5.1) and financial (§5.2.2) data this section names as its reason for
    existing, in a file explicitly meant to be moved off the device.
  - **Per zip entry, not one encrypted blob around the whole archive**, matching what the sync
    folder already does to the same JSON: same `TDRLENC1` magic, same AES-256-GCM, same
    fresh-IV-per-write. That also keeps a `.tendril` a real zip rather than an opaque payload.
  - **`manifest.json` stays plaintext, deliberately.** It carries no content — an app version,
    a timestamp, full-vs-partial, and a list of `pages/<uid>.json` names, which are uids rather
    than titles. Leaving it readable costs a page *count* and an export date to anyone holding
    the file; it buys an importer that can say "this is encrypted, check your passphrase"
    instead of "this file is unreadable", and keeps §9.4.1's manifest-driven picker possible
    for someone who has the passphrase but mistyped it. The manifest also now carries an
    explicit `encrypted` flag, defaulted false so archives written before this still decode.
  - **An archive that can't be decrypted is refused before anything changes.** This matters most
    on the Restore path, which wipes before it applies (§9.4.1): the existing readability guard
    would have caught it, but only as "this file doesn't contain any readable Tendril data" —
    which reads as *the file is wrong* when the truth is *the passphrase is*, and sends someone
    hunting for another backup instead of fixing the passphrase they still have.
  - **Consequence worth stating, because it cuts against §9.4.1.** That section's other use for
    an export is "a single Page/Database sent to someone else". With the toggle on, such a file
    is unreadable to the recipient unless they are also given the passphrase — which is the
    passphrase to the person's *entire* sync folder, not to that one export. This spec has no
    per-export key and no reason to invent one for a personal build, so the mitigation is
    disclosure rather than mechanism: the export confirmation now says the file is encrypted
    and that opening it elsewhere needs that passphrase. Revisit only if sharing single pages
    with other people becomes a real habit rather than a stated possibility.
- **Losing the passphrase** makes the synced snapshot folder unreadable on any new device — Room
  (the live local database, always unaffected by this toggle) is unaffected on devices that already
  have it, but re-establishing sync elsewhere requires either recovering the passphrase or wiping
  and re-exporting fresh. Worth a plain-language warning in the toggle's own confirm dialog,
  matching §5.5's pattern of stating the concrete consequence rather than assuming it's understood.

### 9.5 Google Calendar OAuth without Play Store

- Fully workable for personal/sideloaded use — no Play Store review needed.
- **Real trap to avoid**: if the OAuth consent screen is left in "Testing" mode in Google Cloud
  Console, every connection automatically expires after 7 days, forcing repeated re-authentication.
  Keeping the consent screen in **Production** (while remaining unverified) avoids the 7-day expiry
  — the only cost is clicking through a one-time "Google hasn't verified this app" warning during
  consent, which is an acceptable trade-off for a single-user personal app.
- **Corrected 2026-08-29 — no client ID field, and no stored refresh token.** The original plan
  called for a user-entered "Google OAuth client ID" field in Calendar's settings (§3.2) plus a
  Keystore-protected refresh token (§3.5), on the assumption Tendril would use the standard
  authorization-code-plus-refresh-token flow. Checking the actual Android `AuthorizationClient` API
  (`com.google.android.gms.auth.api.identity`) before building against it surfaced a real mismatch:
  obtaining a persistent refresh token requires `requestOfflineAccess()`, which issues a server auth
  code meant to be exchanged by a backend holding a **Web-type** OAuth client's secret — Tendril has
  no backend and was never going to have one (§9.1, local-first). The corrected mechanism instead
  requests the Calendar scope against an **Android**-type OAuth client, which Play Services resolves
  automatically from the app's own package name and signing-certificate SHA-1 — no client ID is
  entered or stored anywhere in the app. `AuthorizationClient.authorize()` is called again for a
  fresh short-lived access token before each sync pass; once the scope is granted once, Play
  Services returns it silently (no consent UI) as long as the grant stands, functioning as a
  standing authorization without Tendril ever holding a persistent secret. What the user still does
  themselves in Google Cloud Console is unchanged: enable the Calendar API, set the consent screen
  to Production (above), and register an Android-type OAuth client with the app's package name and
  SHA-1 fingerprint — self-registered, tied to the app's own signing certificate, same as originally
  planned; only the "enter a client ID in-app" step is gone.
- **Not yet implemented**: revoking the grant from inside the app
  (`AuthorizationClient.revokeAccess()`) — its exact request shape wasn't pinned down with
  confidence against the live SDK, so Disconnect currently only clears Tendril's local "connected"
  flag, not Google's own grant record. Full revoke requires the person's own Google Account settings
  (Security → Third-party apps & services) until this is verified and wired.

### 9.5.1 Google Calendar sync engine (Decided/Implemented 2026-08-29)

The actual push/pull mechanics, built on top of §9.5's OAuth mechanism, once an access token is
available:

- **EVENT-only.** Tasks are never pushed to or pulled from Google Calendar. Google's Event resource
  has no elastic/streak concept, and §4.1 designed `RecurrenceRule.Fixed`'s RRULE format to match
  this exact API specifically for EVENT recurrence (§4.1 R2); `RecurrenceRule.Elastic` (TASK) has no
  Google Calendar analog and was never meant to sync there.
- **`primary` calendar only** — no calendar picker for v1, matching the spec's running "obvious
  80%-subset" bias (§5.6's Board/Gallery/Calendar views, §5.2.2's single `Interval` property).
- **Incremental sync via `updatedMin` + `showDeleted=true`**, not Google's syncToken mechanism —
  simpler, avoids syncToken's own 410-invalidation-and-full-resync edge case, and sufficient for a
  single personal user's event volume. `singleEvents=false` keeps a recurring series as one master
  event carrying its RRULE, matching Tendril's own one-row-per-series model (§4.1) rather than
  expanding every occurrence into its own record.
- **Push runs before pull, each sync pass** — needed so pull's dedup-by-`googleEventId` lookup
  doesn't re-discover and double-insert an event push just created in the same pass. Accepted
  trade-off: a genuine concurrent edit to the same event, made on this device *and* directly in
  Google Calendar within one sync interval, is resolved by push unconditionally overwriting Google's
  copy rather than comparing timestamps first — the same category of accepted whole-record
  last-write-wins simplification §9.4 already makes for Pages, not a new risk class.
- **Trash interaction**: moving a synced EVENT to Trash (§5.5.1) deletes it from Google and clears
  `Entry.googleEventId` on the next push; restoring it from Trash later creates a fresh Google event
  rather than writing to a since-deleted id. A cancelled/deleted event on Google's side moves its
  local counterpart into Trash — never a hard delete — matching Trash's reversible philosophy; this
  app has no reason to trust a remote delete over a local one enough to destroy data outright.
- **Conflict resolution otherwise mirrors §9.4's per-record last-write-wins rule exactly**,
  comparing Google's `updated` timestamp against `Entry.updatedAt` — one consistent merge principle
  across both sync mechanisms this app has (Syncthing snapshot sync, and this).
- `Entry.googleEventId` (nullable) is the linkage field, added to the snapshot record (§9.4) too so
  the link survives cross-device Syncthing sync rather than a second device re-discovering and
  duplicating an event the first device already linked.

  *Amended 2026-09-25 (audit 5a.1).* Until this date it did not survive the sync. A push sets the
  id without moving `updatedAt`, and the snapshot merge carried entries record-wise on
  `updatedAt`, so a peer holding the same version never learned the id. Worse, a later edit on a
  peer that lacked the id won and wiped it here, and the next push inserted a duplicate event.
  Bumping on the push was rejected: it would claim an edit nobody made, and under LWW it would beat
  a real edit on the other device (the §9.4 false-authorship class). The merge now carries this one
  field **field-wise** (`SnapshotSyncOrchestrator.adoptedGoogleEventId`):
  - a known id always beats an absent one, whichever record is newer;
  - between two known ids, the newer record's id stands;
  - a trashed winner keeps its own id, because the push that trashes an event clears the id on
    purpose.

  This is sound because the id only ever goes from absent to known while an entry lives.
  `GoogleEventIdSyncTest` pins it: two of its tests failed first, and the third is a control.
- **Transport**: plain `HttpURLConnection` plus the existing `kotlinx.serialization` dependency, not
  a new HTTP library — consistent with this codebase's preference for direct platform APIs over
  added abstraction (`SecretStore`'s direct Keystore usage over `androidx.security` is the same
  call, §3.5).

### 9.6 Jetpack Glance (widgets)

- Stable at 1.1.x — the correct, modern, Kotlin-first framework for the widgets in §8, not legacy
  XML RemoteViews.
- **Specific gotcha directly relevant to §8.3**: Glance widget colours resolve *once*, at placement
  time, and do not automatically follow later theme changes unless the app explicitly triggers a
  widget update. The dynamic Ink/Clay/Moss/Mauve + Shade/Hue system built in the prototype will need
  an explicit "push a widget refresh whenever Settings changes" hook in the native implementation —
  it will not simply recompose live the way the CSS custom properties did in the browser prototype.

### 9.7 Notification reliability (Decided 2026-07-13 — scope confirmed, wasn't in the original spec)

Reminders (§3.2, §5.4) imply real scheduled notifications, which needs explicit permission/prompt
handling:
- `POST_NOTIFICATIONS` runtime prompt (Android 13+)
- Exact-alarm handling (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) — worth re-verifying exact current
  behavior at build time, since this has tightened release over release
- A battery-optimization-exemption prompt (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — fine for
  sideloaded personal use, would be discouraged for Play distribution, moot here
- Separate notification channels per source (Tasks/Events vs. Habits) for independent OS-level
  control
- **Added 2026-07-14, MedTimer-pattern**: a distinct **overdue notification** for `kind = TASK`
  Entries, firing at `start_date`+`start_time` (or midnight if undated-time), with inline Done/Skip
  actions — separate from the pre-due reminders above, and what actually drives
  sequential-recurrence advancement (§4) when the person isn't in the app to tap it manually.

**Added 2026-09-04 — alarms follow a recurring EVENT, not just its first occurrence.**
`AlarmScheduler` anchored every alarm to `entry.start_date`. For a recurring EVENT that is the
series' *first* occurrence, so once it had passed, the never-schedule-in-the-past rule below
suppressed everything after it and a weekly meeting reminded exactly once, ever. It now anchors to
the first occurrence whose start is still in the future, resolved through §4.1.1's expander and
honouring skip/override exception rows. Only that one occurrence is armed at a time: request codes
are deterministic in `(entry_id, reminder_id)` alone (below), so two occurrences of one series would
collide on the same `PendingIntent` and the second would silently replace the first. That is the
same "only the currently-live occurrence's alarms exist" model this section already settled on for
elastic TASK recurrence, and it leans on the same backstop — the reconciliation sweep below re-arms
everything on boot and on app open. A series whose next occurrence passes while the app is never
opened waits for that sweep; bounded and self-healing, against the previous permanent silence.

**Added 2026-07-14 — `AlarmScheduler`, a required architectural component, not just a permission
list.** Elastic TASK recurrence (§4.1) means `AlarmManager` can never be handed a whole future
sequence — only the currently-live occurrence's alarms exist at any time, and every write path that
can move an Entry's `start_date` (create, edit, or resolve-and-advance) must cancel that Entry's old
alarms and schedule fresh ones, atomically, through one shared component — not ad hoc from each call
site. Two more requirements this surfaces that weren't in the original permission list:
- `RECEIVE_BOOT_COMPLETED` — alarms do not survive a device reboot; without a boot receiver that
  re-schedules everything from Room, every reminder silently stops working after every restart until
  the app happens to be opened again.
- **Idempotent scheduling**: `PendingIntent` request codes must be deterministic (derived from
  `entry_id` + `reminder_id`), not auto-incrementing, so calling "reschedule this Entry's alarms"
  twice never creates a duplicate. A periodic reconciliation sweep (WorkManager, or an on-app-open
  check) comparing what Room says *should* be scheduled against `AlarmManager`'s actual state is a
  cheap self-healing backstop against Doze-mode losses or missed edge cases.

**Added 2026-07-16 — never schedule an alarm for a trigger time that's already past (fixes a real
bulk-import/retroactive-sync notification flood).** `AlarmManager` fires an alarm set for a past
trigger time almost immediately rather than dropping it silently — a real platform behavior, not a
theoretical one. Left unguarded, this means retroactively binding Sync-to-Tasks on a database with
existing overdue rows (§5.2.1), or importing a Notion export containing a year of already-past-due
to-do rows (§7.3), would fire one overdue notification per row, all within the same minute. Fix:
`AlarmScheduler` never schedules an alarm whose trigger time is at or before "now" at the moment of
scheduling — this applies uniformly wherever alarms get (re)scheduled, including the reconciliation
sweep above, so it's a property of the component, not something each caller has to remember. The
Entry itself is unaffected and still shows up correctly as overdue in Tasks/Calendar; only the
immediate-fire notification is suppressed. **Batched summary notification, added 2026-07-16 — *specified,
never built* (corrected 2026-09-06)**: after a bulk operation creates one or more already-overdue
Entries in one pass (retroactive sync-on, Notion import), a single summary notification fires
instead of silence — "12 tasks were imported already overdue" — the same pattern as a routine
"downloads finished" notification elsewhere on the OS, not a per-item alert. The reasoning is kept
because it still holds: the never-schedule-in-the-past guard turns a flood into silence, and silence
about twelve imported overdue tasks is its own failure. But only the guard shipped.
`AlarmScheduler`'s own doc comment deferred the summary on the grounds that "nothing in Phase 3
creates Entries in bulk yet" — and then Notion import (§7) and retroactive Sync-to-Tasks (§5.2.1)
both shipped without revisiting it, `DatabaseSyncManager.enableSync` still inserting one Entry per
row with nothing counting them. Open work, not behaviour to debug.

*(**Amended 2026-09-24 — the paragraph above reasons about a flood from a path that scheduled
nothing.** An invariant sweep of all 71 write calls that reach an Entry, reminder, completion or
habit found that `enableSync` and `bindProperty` never reached a scheduler: `DatabaseSyncManager`
held no coordinator and no caller re-armed after it. So retroactive Sync-to-Tasks could not flood
past-due alarms — it armed no alarm at all, future ones included, until the next cold start or
reboot on the phone and never on the desktop. `DatabaseSyncManager` now takes the
`EntryScheduleCoordinator` as a required parameter and reports every Entry it inserts or moves;
the never-schedule-in-the-past guard now does the job this paragraph credited it with. And an
exception row (§4.1: one occurrence moved or skipped) re-armed only itself on Android: the series,
whose next occurrence it changes, kept its alarm for the occurrence that no longer happens.
`AndroidEntryScheduleCoordinator` re-arms the series with its exceptions when handed one — one
place for the editor's "this one", ICS EXDATE/RECURRENCE-ID and a merged exception alike. The
desktop's `replan()` re-reads everything and was never affected. Recorded, not fixed (each needs a
device or an `androidTest`): a reminder deleted on the other device is never cancelled on the phone
(`AlarmScheduler.cancelAllFor` finds reminders through `getForEntry`, which skips deleted rows —
merge and archive import alike); an archive import that makes an entry DONE or dateless leaves its
armed alarm; merged and imported habits are not re-armed (build-order B5); a restored habit is not
re-armed (§5.5.1).)*

### 9.8 Architecture review (2026-07-14 — Review Mode, deep-review strategy)

Requested as a final audit of the Entry/recurrence/notification cluster before Phase 1. Mode:
**Review** (an existing design, not a blank slate). Strategy: **deep-review** over quick-audit —
committed directly rather than run through a separate selection pass, since the asymmetry is already
stated plainly enough to reason from: under-auditing something about to be built is costly,
over-auditing costs only some reading time.

**Structural analysis.** `Entry`'s high fan-in (Calendar, Tasks, Merged, widget Agenda, Reminder,
`EntryCompletion`, the to-do-database bridge, the Provider writer, and snapshot export all touch it)
is expected for a central domain table and not itself a problem. What *is* a real risk: the
~~five-step~~ resolution sequence (log completion → advance-or-finalize → reschedule alarms and the
Calendar Provider mirror) *(**corrected 2026-09-06:** this said "five-step" while listing four, and one
of the four — "reset the bound Row property" — was never built, deliberately: a bound Row's Done
property is a live proxy computed from the Entry at display time, not a second stored value, so there
is nothing to write back and nothing to drift. The step that did arrive later is the Provider mirror,
folded in behind `EntryScheduleCoordinator` rather than added as a fifth thing to remember)* is exactly
the shape of logic that quietly drifts if several UI surfaces
each reimplement it — the same category of bug as the birthday case and the stale-alarm case already
caught, just not yet manifested. That's an **anemic domain model** risk (business logic scattered
across the UI layer instead of owned by one domain operation) if left unaddressed.

**Recommendations, ranked by impact × reversibility ÷ migration cost:**

**[R1] Centralize Entry resolution — HIGH impact.**
*Problem:* the 5-step resolve sequence has no single owner yet.
*Change:* one `ResolveEntryUseCase`, called by every surface that can resolve an Entry (added to
§5.2 above). No call site touches `Entry`/`Reminder`/`EntryCompletion` directly.
*Migration path:* trivial now (nothing built yet); expensive once 5 independent call sites exist.
*Risk:* none — purely additive discipline.

**[R2] Type `recurrence_rule` as a sealed class, not a convention-typed string — HIGH impact.**
*Problem:* a column whose meaning depends on an adjacent column (`kind`) is the same
implicit-coupling shape that caused the original TASK/EVENT conflation.
*Change:* `RecurrenceRule.Fixed`/`RecurrenceRule.Elastic` (§4.1) — the compiler enforces the
correspondence instead of developer memory.
*Migration path:* trivial now; a Room `TypeConverter` refactor later.
*Risk:* minor, well-trodden pattern.

**[R3] One source of truth for recurrence expansion — MEDIUM-HIGH impact.**
*Problem:* once Calendar Provider registration exists (§3.2), Tendril's own local RRULE expansion and
`CalendarContract.Instances`' expansion could silently disagree on edge cases if both ever feed the
same screen — a missing-anti-corruption-layer risk between Tendril's model and the platform's.
*Change:* Room stays authoritative for Tendril's UI always; Provider writes are one-directional (added
to §3.2 above).
*Migration path:* free now (one sentence); expensive to un-teach later if Phase 3 code starts
reading `Instances` for convenience.
*Risk:* needs to actually be visible to Phase-3-you, not just decided here — it's in §3.2 now for
that reason.

**[R4] `AlarmScheduler` as a named component, with reboot recovery — MEDIUM impact.**
*Problem:* elastic recurrence requires event-driven (not schedule-ahead) alarm management, and
alarms don't survive reboot.
*Change:* centralized scheduler, `RECEIVE_BOOT_COMPLETED` + boot receiver, idempotent request codes,
periodic reconciliation (added to §9.7 above).
*Migration path:* additive, fills a gap rather than reworking anything.
*Risk:* low, standard pattern, grounded in MedTimer's own changelog behavior.

**[R5] Exception rows reuse `Entry` rather than a new table — confirmed design, not a defect.**
Stated for completeness: `original_entry_id`/`original_occurrence_date`/`is_exception_skip` on
`Entry` itself (§4.1) mirrors `CalendarContract`'s own exception-event shape, minimizing Phase 3
translation work — the "less rework later" goal driving this whole round.

**Not flagged, and deliberately so:** no microservice/service-boundary anti-patterns apply (single
local app, not a distributed system); the Row↔Entry link is the one intentionally bidirectional
relationship in the schema and is covered by R1, not a separate issue. Nothing here calls for
api-designer, debugging-guide, or the optimization-engines skills — there's no genuine
multi-candidate trade-off in this cluster, just gaps to close, which is a normal outcome of a review
and not a reason to force another skill in.

#### 9.8.1 Second pass: Page editing mode & draft system (2026-07-15 — Review Mode, quick-audit strategy)

**Superseded (2026-07-15, later the same day):** this pass reviewed the
Editing/Viewing/draft/Save/Cancel model, which §3.1.2 has since withdrawn in favor of View-Only lock
+ checkbox-only mode. Kept below as historical record rather than deleted, per this document's
existing practice (e.g. the Task/Event correction). R6 and R9 still carry forward conceptually
against the new design (see the notes appended to each); R7, R8, and the closing "positive finding"
were specific to the draft architecture and no longer apply as written.

Lighter pass than 9.8's — requested as a "see what it finds" check on the new
Editing/Viewing/draft/View-Only cluster (§3.1.2, §5.5), not a pre-Phase-1 gate, so quick-audit over
deep-review this time; the asymmetry that justified deep-review for the Entry cluster (about to be
built) doesn't apply the same way to a cluster still being actively decided.

**[R6] Proxied properties must be explicitly exempted from the draft/session model.** Without that
rule stated outright, Editing mode's "capture everything since entry, revert on Cancel" logic could
plausibly intercept a Done-checkbox tap meant for `ResolveEntryUseCase`, which needs to fire
immediately (completion log, recurrence advance, alarm reschedule) regardless of whether a draft
happens to be open. Resolved in §3.1.2 — proxied properties bypass drafting entirely, always live.
Same centralizing principle as R1 in the first pass, applied again rather than re-derived from
scratch, which is a good sign it generalizes rather than being a one-off patch.
*Carries forward:* moot as literally stated (there's no draft to bypass anymore), but the underlying
concern is now trivially satisfied rather than newly at risk — with Pages instant-write throughout,
proxied properties were never going to be captured into anything in the first place. No action
needed.

**[R7] Two sync-timing domains, not a conflict — but worth making structurally visible.** Page-draft
content (nothing syncs until Save) and Entry/Task data (syncs continuously, §9.4) are genuinely
different and both correct — the risk isn't the rule itself, it's the rule living only in this
document and not in the code's actual repository-layer boundaries, where it could quietly blur.
*Moot* — the "nothing syncs until Save" side of this no longer has a Save to anchor to; see the
reopened question in §9.4.

**[R8] Draft state should be a typed list of discrete pending operations, not one large mutable
page-copy.** A sealed class
(`BlockEdit`/`PropertyAdd`/`PropertyDelete`/`PropertyTypeChange`/`RowDelete`, …) keeps "what exactly
commits on Save" enumerable rather than an opaque diff, and doesn't foreclose a future step-by-step
undo if ever wanted later, even though whole-session revert is what's being built now.
*Moot* — no draft state exists to type.

**[R9] Cross-device conflict on an open draft is still genuinely open, not just deferred.** Flagged
in §3.1.2 as unresolved rather than assumed — recommend deciding before Phase 5 (Pages, §9.9) rather
than carrying it in as an implicit assumption.
*Carries forward, generalized:* "an open draft" no longer exists, but the underlying question
doesn't fully disappear — it folds into §9.4's existing whole-page-snapshot LWW limitation
(concurrent edits to the same page before either syncs means one edit is lost, already an accepted
v1 limitation). Worth confirming that's still an acceptable answer now that edits commit instantly
rather than being held in a draft first, since instant-write plausibly makes concurrent-edit windows
more common, not less.

**Positive finding, not just risk-hunting (historical):** memory-only drafts and whole-session
revert reinforced each other well — since nothing reached Room before Save, neither a Cancel nor a
crash could ever leave a partial commit. No longer applicable now that there's no draft layer;
instant-write sidesteps the partial-commit concern differently (each write is its own small commit,
not a batch).

### 9.9 Build sequence (Decided 2026-07-13, updated 2026-07-14 with the §9.8 findings)

Updated from the original proposal to reflect this pass's decisions: SAF replaces the isolated
Shizuku phase (much lower risk, foldable much earlier), notifications land with Calendar/Tasks
rather than as an afterthought, and the block editor gets its own scoping confirmation before Pages
work starts rather than during it.

**Decision gates (updated 2026-08-26):** each of §10's five former open items is scheduled as a gate
immediately before the first phase that would otherwise have to guess at it — not batched all up
front (wasted effort on a decision three phases won't touch yet) and not left dangling until it
blocks something. Nothing below is newly added scope; every gate below already has its full decision
recorded in the section cited.

0. **Gate — accessibility scope (§2.4) and first-run/empty-state pattern (§2.5)**, decided before
   Phase 1 begins, since both are cheapest to build into the initial nav shell and its first screens
   and expensive to retrofit once real layouts exist.
1. Project scaffold + Compose theme (§2.3 palette table) + five-tab navigation shell + SAF
   folder-picker permission flow — installable and navigable fast, validates the toolchain, and
   folds in what used to be the isolated Shizuku phase at much lower risk.
2. **Gate — Room schema migration policy (§9.10)**, decided immediately before this phase's Room
   data layer work starts. Settings + Room data layer + theme picker + encrypted-secret-storage
   scaffolding (§3.5) + App Lock (§3.6) — present but unused until Anthropic/Google integrations, or
   App Lock itself, are toggled on.
3. Tasks & Habits + Calendar as one phase, given Task/Event are now one table (§4) — local first,
   notifications (§9.7) wired in from the start, Trash/soft-delete for Entry and Habit (§5.5.1)
   included here since it's a field on these same tables, then Google sync and Calendar Provider
   registration (§3.2) as separable increments.
4. Snapshot-based export/import + sync-conflict merge logic (§9.4) + optional at-rest snapshot
   encryption (§9.4.2), now that SAF folder access from step 1 is in place.
5. **Gate — in-app search UX (§3.1.7)**, decided alongside the block editor scope confirmation
   below, since search is a Pages-topbar feature and belongs in the same pass as the rest of Pages'
   UI shell. Block editor scope confirmation (§3.1.1) → Pages/Notion-like system (§3.1, §5, §7),
   including the FTS schema (§3.1.1), Tags (§3.1.6), page templates (§3.1.3), daily journal
   (§3.1.4), the backlinks panel (§3.1.5), database views (§5.6), and Trash/soft-delete for Page/Row
   (§5.5.1), once the block model is settled.
6. Road Map (§3.4), contingent on Pages' mention/link feature existing.
7. **Gate — widget configuration flow (§8.7)**, decided immediately before this phase, since it
   determines what `AppWidgetConfigureActivity` actually needs to build. Widgets (Glance, §8) — once
   Task/Event/Habit models are stable, since widgets read from them.

### 9.10 Room schema migration policy (Decided 2026-08-26)

Five candidates, scored against build effort while the schema is still actively changing (Phases 1–7
haven't landed yet, and §4/§5 have already changed shape twice mid-spec), data safety once the app
is in real daily personal use post-v1, ongoing engineering overhead, and fit with an architectural
fact unique to this app — a schema-independent JSON snapshot of everything already exists as an
export/import format (§9.4, §9.4.1) (weights 0.30/0.30/0.20/0.20):

| Policy | Pre-v1 effort | Post-v1 data safety | Ongoing overhead (inverted) | Leverages existing snapshot format | Score |
|---|---|---|---|---|---|
| Pure destructive migration, forever | 1.00 | 0.10 | 1.00 | 0.00 | 0.560 |
| Hand-written `Migration` for every version bump, forever | 0.30 | 1.00 | 0.20 | 0.00 | 0.430 |
| `@AutoMigration` + manual fallback, from day one | 0.60 | 0.90 | 0.55 | 0.00 | 0.590 |
| **Phased: destructive pre-v1, then `@AutoMigration` + manual + snapshot-backed recovery post-v1 (chosen)** | 0.90 | 0.85 | 0.65 | 1.00 | **0.845** |

**Decided:** two policies, one clean switch-over point. **Pre-v1** (Phases 1–7 above):
`fallbackToDestructiveMigration()` — nothing real is stored yet that isn't trivially re-creatable,
the schema is still visibly moving (three of §4's own fields didn't exist a month before this
decision), and hand-writing `Migration` objects against a schema still finding its shape is pure
waste. **Post-v1** (the first release actually used daily): switch to Room `@AutoMigration` for the
overwhelmingly common case this schema actually produces — every change logged in this spec so far
has been additive (a new nullable column, a new join table, a new entity), never a rename or a drop,
so `@AutoMigration` covers most future changes with no hand-written SQL. For the rare structural
change `@AutoMigration` can't express, fall back to a hand-written `Migration`; for the rarer case
even that can't cover, reuse the existing Restore-from-backup path (§9.4.1) — ~~wipe Room, then replay
the most recent `.tendril`/snapshot-folder export~~, which is schema-independent JSON rather than a
byte-for-byte Room dump. *(**Corrected 2026-09-06:** two assumptions here are not true of the code
this points at. Restore does not wipe Room — it clears Entries and Habits and **merges** everything
page-shaped — and "snapshot-folder export" names a recovery path that does not exist, the folder being
read additively only, with no wipe-and-replay against it. For the post-v1 migration case this is
mostly harmless, because a destructive migration has already emptied Room by the time Restore runs,
which is the fresh-install case Restore was written for. It matters for the acceptance criterion
below, which asks for this path to be exercised once against a deliberately broken migration: that
test must run on a populated device, or it proves the easy case only.)* Gate that path behind the same plain-language confirm dialog §5.5 already
uses elsewhere ("this update needs to reset local data; your synced pages, tasks, and habits will be
restored from your last sync").

**Acceptance** *(restored here 2026-09-04 — this block was printed at the end of §9.11, so §9.10 had
no acceptance criteria and §9.11's acceptance-tested a different section; §9.11 now has its own)*:
every schema change from v1 onward ships with either an `@AutoMigration` entry or an explicit
`Migration`, never a silent `fallbackToDestructiveMigration()` left in place after the first
release; the snapshot-restore fallback is wired and manually tested at least once (a
deliberately-broken migration on a test device, confirming the restore path actually recovers a
populated Room DB) before it's relied on for a real one. *(**Wired 2026-09-09 — S3.** The first half of that criterion was
unmet in a way the wording hides: the fallback was not merely untested, it did not exist. Nothing
caught a failure opening the database — `AppContainer` and the desktop `main` both assigned
`buildTendrilDatabase(...)` straight into a field — and Room opens lazily, so a migration that threw
surfaced at the first DAO call and took the app down on every launch. `fallbackToDestructiveMigration`
does not cover that case, as this section itself says: it handles version *changes*, not a declared
migration that throws — and until v9 (S2) this app had no declared migration at all, so the gap only
became reachable the day the first one shipped. `openOrRecover` now builds the database, proves it
opens with a real query, and on failure closes it, **renames the unopenable file aside rather than
deleting it**, and starts from an empty one. The confirm dialog this section asks for cannot be
honoured as written — a dialog needs a running app and there is no database to run on — and setting
the file aside answers the same concern it was protecting, by the same rule `SnapshotSyncOrchestrator`
applies with its `.tendril-lost-` marker. The empty database is then repopulated by the ordinary
additive sync pass, which recovers everything the folder holds **and nothing that was never
published** — stated as a test rather than left to be discovered. **The manual run §9.10 asks for was carried out
2026-09-09** — a OnePlus 9 Pro, a populated database, a v9→v10 migration made to throw on purpose. The
app stayed up, the database was set aside with its `-wal`/`-shm` and still held every row when
opened afterwards, and the next sync refilled the fresh one completely with the reminder
tombstone intact. The folder was unchanged by the emptied device's own write pass. What this
run does **not** cover: file-level corruption that SQLite refuses before Room's migration
machinery runs, which `openOrRecover`'s probe would catch but which has not been exercised.)* *(**Corrected 2026-09-14 — the
uncovered case, exercised, and the probe did *not* catch it on Android.** Prompted by Lunar's
finding of the same shape (its `openOrRecover` never saw a corrupt file because Room's default
open-helper callback inherits the framework's `DefaultDatabaseErrorHandler`, which deletes and
reopens before any probe). Tendril has no open helper — `finishBuilding` sets a driver — but the
Android driver, `AndroidSQLiteDriver`, opens with `SQLiteDatabase.openOrCreateDatabase(name, null)`,
and that `null` is the same handler by another route: `SQLiteDatabase.open()` catches the
`SQLITE_NOTADB` its setup pragmas raise, deletes the file with its `-journal`/`-wal`/`-shm`, and
reopens empty, all before Room runs a statement of ours. The probe then found a pristine database
and reported nothing recovered — the "set aside, never deleted" rule was true of the helper and
false of the app. **`DatabaseFileTest`** (Robolectric, the first in the suite — it has to be the
real `android.database.sqlite`) writes plain text to `getDatabasePath("tendril.db")`, runs
`openTendrilDatabase`, and asserts recovery reported, a `.unopenable-` copy holding the original
bytes, and an empty database in the file's place; it failed on (a) before the fix. **The fix is
`KeepFileOnCorruptionDriver`** in `androidMain`: `AndroidSQLiteDriver` with a no-op
`DatabaseErrorHandler`, so the framework's one retry throws the corruption out of `open()`, Room
surfaces it at the probe, and `openOrRecover` closes, renames aside and rebuilds as written. The
desktop's `BundledSQLiteDriver` has no such handler and was never affected. **Run on the device
the same day** — the OnePlus 9 Pro on Android 14, both builds, the real `tendril.db` copied off
first (md5-checked) and put back after. `main`: 23 bytes of plain text in the file's place,
launch, and logcat says it in the framework's own words — `Database corruption detected in
open()` … `SQLITE_NOTADB … while compiling: PRAGMA journal_mode` … `DefaultDatabaseErrorHandler:
deleting the database file` … `DB wipe detected: reason=corruption` — a fresh 12 KB database, no
copy of the old bytes anywhere, the app none the wiser. The fix build, same 23 bytes: the
corruption is thrown out of `KeepFileOnCorruptionDriver.open` twice (the framework's one retry),
no handler line, no wipe line, and `databases/` holds `tendril.db.unopenable-1789400029110` at 23
bytes with the same md5 as the text written, beside a fresh `tendril.db`; `MainActivity` resumed
on top. The restored database showed *Errands*, *Journal* and *Trip* again. So both halves of the
2026-09-09 clause are now exercised on hardware: a throwing migration then, a file SQLite refuses
now.)* *(**Narrowed 2026-09-09 — S1b.** The destructive fallback is no
longer blanket: `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1..7)` confines it to
the pre-release schemas, which is the "destructive pre-v1" half of this section's own policy and
nothing more. From v9 — the first release with a declared migration — a forgotten migration, a
downgrade, and a moved schema hash all reach `openOrRecover` instead, which preserves the database
rather than dropping it. This is what the acceptance clause "never a silent
`fallbackToDestructiveMigration()` left in place after the first release" was asking for. v8 is
deliberately excluded: it has a declared path to v9, and Room rejects a version that is both
migrated-from and wiped-from. Verified on the same device by downgrading a populated v10 database
to the v9 build — previously wiped, now set aside intact.)* **Pre-v1 the version number still has to
move on every schema change**: Room compares a hash of the schema against the one stored in the
database and throws before migration runs when the hash moved but `version` didn't, so
`fallbackToDestructiveMigration` never gets the chance to recover — it only handles version
*changes*. Adding the Canvas tables (§3.4) without a bump is exactly how that was found.

### 9.11 System Calendar Provider registration (Decided/Implemented 2026-08-29)

The actual mechanics behind §3.2's Provider registration bullet, corrected and built together once
`CalendarContract` was checked directly rather than assumed:

- **`CalendarContract.ACCOUNT_TYPE_LOCAL`, not a real account.** No `android.accounts.Account`, no
  `AbstractAccountAuthenticator` service, no `AbstractThreadedSyncAdapter` service, no intent-filter
  registration for either. Every Provider write instead carries `CALLER_IS_SYNCADAPTER=true` plus a
  fixed account name/type as URI query parameters — the documented mechanism that unlocks the
  sync-adapter-only write path `Calendars`/`Events` require, against an account that never has to
  actually exist. This is strictly simpler than the original plan, not a scaled-down version of it.
- **Scope: both TASK and EVENT, whatever Tendril's own Calendar screen already shows** (§4's `WHERE
  start_date IS NOT NULL` rule) — broader than §9.5.1's Google Calendar sync, which is EVENT-only. A
  todo with a date is exactly the kind of thing a widget or watch face should be able to show
  alongside real events. *(**Note added 2026-09-04:** this sentence was aspirational until §4.1.1.
  The Provider expands the `RRULE` it is handed, so the mirror showed a recurring EVENT on every
  occurrence while Tendril's own screen showed it once — the mirror was strictly broader than the
  scope defining it. With expansion built, the two agree, except for a rule outside §4.1.1's
  supported subset, where Tendril shows fewer; that residual gap is recorded there.)*
- **Google-sourced Entries excluded** (`source = GOOGLE_CALENDAR`, §9.5.1) — those already reach the
  system's calendar surfaces through the device's own real Google account sync; mirroring them here
  would just duplicate them.
- **Recurring EVENT** (`RecurrenceRule.Fixed`) maps its RRULE directly, as already anticipated
  (§4.1). Recurring TASK (`RecurrenceRule.Elastic`) has no Provider equivalent, so it mirrors as a
  plain single event for its one currently-live occurrence, moved forward in place on each
  resolve-and-advance — the same "only the current occurrence really exists" model §9.7's
  `AlarmScheduler` already uses for elastic recurrence. `Events.update`/`insert` reject `DTEND`
  alongside `RRULE` — a recurring write uses `DURATION` (the occurrence's length) instead,
  discovered against the live Provider validation rather than assumed.
- **One-directional, write-only** — unchanged from the original decision (§3.2): Room stays the
  single source of truth for Tendril's own UI always; Provider rows are never read back.
- **`Entry.providerEventId`** (nullable, new column) is the linkage field — and, unlike
  `googleEventId` (§9.5.1), **deliberately excluded from the cross-device snapshot record** (§9.4).
  Each device's Calendar Provider is its own independent local database; a Provider row id from one
  phone means nothing on another, so a remote-wins snapshot or Google-pull merge must preserve
  whatever this device's own value already was rather than adopt one that doesn't exist locally.
- **Centralized through `EntryScheduleCoordinator`**, not nine separate call sites. Every write path
  that can change a schedulable Entry (`ResolveEntryUseCase`'s resolve/unresolve/trash/restore, plus
  `CalendarViewModel`, `TasksHabitsViewModel`, `PageDatabaseViewModel`, and `PageDetailViewModel`'s
  own edit paths) already had to call `AlarmScheduler.rescheduleFor`/`cancelAllFor` directly. Adding
  the Provider mirror as a second, independent call at each of those same nine sites would have
  repeated the exact "N surfaces reimplementing the same sequence" drift risk §9.8's R1 already
  fixed once for Entry resolution — so it's fixed the same way here: one coordinator, no call site
  touches `AlarmScheduler` or the Provider sync directly any more.
- **Permission requested contextually**, on first app launch (mirroring the existing
  `POST_NOTIFICATIONS` request), not gated behind a Settings toggle — Provider registration is
  inherent app behavior, not an opt-in integration like Google Calendar sync (§3.2). A self-healing
  backfill sweep rides alongside the existing boot-time alarm reconciliation (§9.7, §9.8 R4) for the
  case permission was granted after some Entries already existed.

**Acceptance:** every Provider write carries `CALLER_IS_SYNCADAPTER=true` with the fixed local
account name/type; `Entry.providerEventId` never appears in a snapshot record and is preserved
locally by every merge path (snapshot, Google pull, and `.tendril` import alike); no Entry with
`source = GOOGLE_CALENDAR` is mirrored; and a recurring write sends `DURATION`, never `DTEND`
alongside `RRULE`.

---

## 10. Open Questions (consolidated)

**Updated 2026-08-04** — most items below were resolved in a pre-build gap-analysis pass; each now
lives as a **Decided** note in its home section rather than here. Both items that reopened in later
rounds are now resolved (see below) — nothing is currently blocking Phase 1 of the build sequence
(§9.9).

**Still open, not blocking Phase 1 (§9.9):** *this line read "none" until 2026-09-06, when every
deferral in this document was reopened at once (see the Revision Log). It is no longer none, and this
section is no longer where they are tracked. A register of that size maintained inline goes stale
faster than it can be read — which is precisely what happened to the sentence this note replaces —
so §10 keeps its purpose of recording what was DECIDED and why, and stops pretending to be a
worklist. Where the worklist itself lives is deliberately not named here: any answer would be either
a path outside version control, which a reader of this repository cannot follow, or a second copy of
the same list, which is the duplication §9.8 R1 exists to refuse.* *(**Amended 2026-09-24:** that
dilemma had a third answer, and it was taken on 2026-09-08 — the worklist moved into version
control as `docs/build-order.md` (the build order; its per-item status is asserted, not measured)
and `docs/scope-decisions.md` (what is deliberately not built, which wins where the two disagree).
Neither copies §10; both say in their first paragraph that they exist because this note refused to
name them. The note is kept as the record of the reasoning; this amendment supersedes it.)*
The five items below were the last consolidated open items and are now resolved — see §9.9's decision-gate annotations for exactly when each was
scheduled relative to the build sequence.

**Resolved 2026-08-26** (kept here as a changelog trail; full reasoning lives in the linked
section):
- **Room schema migration strategy** — destructive pre-v1, `@AutoMigration`/manual with
  snapshot-backed recovery post-v1, chosen via a 4-criteria weighted score against three
  alternatives (§9.10).
- **Accessibility beyond color contrast** — Compose-default semantics plus targeted
  `contentDescription`/`semantics{}` on icon-only controls, Road Map's Canvas, and widgets, chosen
  via a 4-criteria weighted score against three alternatives (§2.4).
- **First-run / empty-state experience** — a single reusable `EmptyState` composable across every
  list-shaped screen, chosen via a 4-criteria weighted score against three alternatives (§2.5).
- **Widget configuration flow** — opacity/Shade/Hue configurable per placed widget instance,
  theme/mode inherited globally from Settings, chosen via a 5-criteria weighted score against two
  alternatives including the Fossify-style fully-independent option (§8.7).
- **In-app search UX** — a Pages-scoped full-screen overlay off the existing search icon, querying
  the existing page-level FTS index, Database rows included for free since a Row is a Page, chosen
  via a 4-criteria weighted score against three alternatives (§3.1.7).

**Resolved 2026-08-08** (kept here as a changelog trail; full reasoning lives in the linked
section):
- **Database views (Board/Gallery/Calendar) reopened and added** — reverses the "decided out of
  scope" table-only-database limitation below; a table-only database was a real functional
  regression against every reference app this spec draws on, not a reasonable simplification (§5.6).
- App Lock — biometric/PIN gate, separate from and orthogonal to the existing View-Only lock (§3.6).
- Trash / soft-delete — supersedes the "no way to back out" limitation §5.5 had explicitly flagged
  for row/Page/Entry/Habit deletion (§5.5.1).
- Page templates (§3.1.3), daily journal (§3.1.4), and a page-level backlinks panel (§3.1.5) — added
  to Pages.
- Tags — resolves the previously-undefined `category` field on Page (§3.1.6, §4).
- At-rest snapshot encryption — optional, off by default, closes a real plaintext-on-disk gap in the
  sync folder (§9.4.2).

**Resolved 2026-08-04** (kept here as a changelog trail; full reasoning lives in the linked
section):
- App name and export extension — **Tendril**, export extension `.tendril` (§9.4.1), chosen for the
  "ever reaching, branching out" resonance rather than the original "hybrid" framing this doc's
  naming pass started from. Scored 0.85 on a
  mood-alignment/pronounceability/memorability/collision/originality fitness pass against candidates
  like Ibrido (0.91), Alloy (0.88), and Ibridomni (0.88) — not the top score, but the one that
  stuck. One flag carried forward for awareness rather than as a blocker: Tendril Networks was a
  real (now largely faded) smart-home energy-software company, different industry, low practical
  collision risk for a personal build.
- Page-snapshot sync timing — 2-second debounce per page, with immediate flush on navigate-away or
  app backgrounding (§9.4).

**Resolved 2026-07-13** (kept here as a changelog trail; full reasoning lives in the linked
section):
- Block editor scope — Notion-lite, chosen via Pareto trade-off analysis across
  effort/completeness/import-fidelity (§3.1.1).
- Calendar Provider vs. Google Calendar sync relationship — stay independent; a dominance case, not
  a genuine trade-off (§3.2).
- Task/Event schema — one table (§4).
- Habits architecture — stays separate, not database-driven (§3.3, §4).
- To-do database eligibility — any database, any time (§5.3).
- Road Map's data model — explicit edges from page mentions + manual "Relate to" (§3.4).
- Calendar → Week view — added hour-grid layout option (§2.2).
- Build sequence — updated (§9.9).
- Folder access mechanism — SAF, not Shizuku (§9.3).
- Multi-device sync/backup safety — snapshot + atomic-write + conflict-merge design (§9.4).
- Notification reliability — permissions/prompts scoped (§9.7).
- Full-text search — confirmed necessary, schema tied to the block editor (§3.1.1).
- Secret storage — local-first until toggled, then Keystore-backed (§3.5).

**Deferred (not needed for v1, worth keeping on record):**
- ~~In-page mind-map block, **Claude-API-generated** (§3.4) — the reason Road Map was renamed away
  from "Mind Map" in the first place.~~ **Built 2026-09-22** as §0.6.15's fourth verb. *(**Corrected 2026-09-04:** this read "user-drawn or
  Claude-API-generated ... entirely separate feature, not started." The user-drawn half shipped as
  the Canvas page kind and is no longer deferred — see §3.4. Only the API-generated half remains
  unbuilt.)*
- Notion API-based import (using a user's own integration token) as a second, richer import path
  alongside the file-based Markdown/CSV importer (§7.3).
- ~~Extending the widget live-audit tool to model one specific/sampled wallpaper luminance instead of
  the adversarial black/white bracket, so it can report a realistic pass in typical conditions
  (§8.6).~~ **Built 2026-09-22** — the system's own wallpaper colour (§8.6's amendment).
- ~~Widget mode (light/dark) auto-suggestion based on detected wallpaper darkness (§8.2 bonus finding)
  — noted, not designed.~~ **Superseded 2026-09-22** — mode is the app's (§8.7); the suggestion is
  the smallest passing opacity on the detected wallpaper (§8.6's amendment).
- ~~Block-level (vs. page-level) full-text search granularity (§3.1.1) — reasonable later refinement
  if page-level feels too coarse.~~ **Built 2026-09-22** (v25; §3.1.1's amendment).
- ~~Syntax highlighting in code blocks (§3.1.1) — nice-to-have, not required for v1.~~ **Built 2026-09-22** (§3.1.1's amendment).
- ~~Desktop companion app (§12) — feasibility and platform strategy explored and scored (Kotlin
  Multiplatform + Compose Multiplatform), but not scheduled into §9.9; graduates into its own spec
  file only once it actually enters the build sequence.~~ **Superseded 2026-08-30 by Milestone 1**
  — shared KMP core plus a minimal desktop viewer (`tendril-windows-spec.md` §5) — which met this
  bullet's own graduation condition on its own terms: running code, not further scoring. Milestones
  2 (folder-sync-on-desktop, that file's §7) and 3 (Workbench UI port, its §8) landed the same day,
  so the companion has been three milestones past this deferral while the deferral sat in the one
  list a reader consults to learn what is *not* built — the same failure mode §10's own "Still open"
  note describes, reached from the opposite direction. Struck rather than deleted because the
  condition it sets is the reason `tendril-windows-spec.md` exists as a separate file at all (that
  file's §4 scored the split against exactly this trigger). What was still deferred on that day was not the
  companion but the four tabs it stubbed out — Calendar, Tasks & Habits, Road Map, Settings, plus the
  Canvas page kind (§3.7) — which rendered `NotAvailableOnDesktop` by design, per that file's §8
  scope boundary. *(**Superseded 2026-09-21:** none of those stand-ins exists — Canvas came to the
  desktop 2026-09-11, Calendar and Tasks & Habits 2026-09-12, Road Map 2026-09-13 (step 8c) and
  Settings 2026-09-13 (§0.6.15); every tab is shared code, and B§13's desktop pass (§0.10 item 14)
  then made each a desktop surface.)*

- A stable per-install **device id** (**considered and deferred 2026-09-06**). Raised while designing
  the merge-loss fixes of §9.4: several candidate designs wanted one, and the argument for adding it
  early is real — a device id is cheap to mint now and expensive to retrofit, since anything that has
  already written identifiers keyed on its absence has to be re-keyed. It is deferred anyway, on
  three grounds. **Nothing needs it.** The two designs that did — a conflict-free fractional order
  key for blocks, and a CRDT actor id — were both examined against this codebase and both declined
  (see below); with those gone, no code would read it. **It is not the small change it looks like.**
  There is no shared preferences abstraction: `AppLockPreferences`, `ThemePreferences`,
  `SyncStatusPreferences` and `SecretStore` all live in the Android module and the desktop has no
  counterpart, so a per-install value needs a new `expect`/`actual` pair, a new interface with two
  implementations, or a Room row — and a Room row means a schema bump, which §9.10's destructive
  policy makes the most expensive kind of change available. **And it is the exact generality this
  spec refuses elsewhere**: `PurgedKind`'s own note says speculative enum members "are the sort of
  dead generality this codebase avoids; add HABIT alongside the UI that needs it," and an unused
  identity facility is that, with a wider blast radius. Recorded rather than built so the reasoning
  survives: whichever feature first needs a device id should add it, and should expect the retrofit
  cost this entry declines to pay in advance.

**Decided out of scope** (unlikely to resurface, listed for completeness):
- ~~A general Notion-style formula language for database properties (§5.4)~~ — **reopened 2026-09-06**; relation, rollup and formula are now in scope as one feature, and are no longer out of scope. See §5.4.
- Full Notion-parity block editor (embeds, inline databases, nested-page canvases) — Notion-lite
  scope chosen instead (§3.1.1).
- ~~Timeline/Gantt database view and formula/rollup-driven view grouping (§5.6) — real added
  complexity with no case elsewhere in this spec that needs them~~, unlike Board/Gallery/Calendar,
  which are now in scope (§5.6, reopened 2026-08-08 — see the Resolved list above; database views
  generally are **not** out of scope anymore). **Both halves reopened 2026-09-06**: grouping by a
  computed value stops being a separate problem once §5.4's evaluator exists — it is the same code
  path as grouping by a Select — and Timeline/Gantt is reopened on its own merits rather than
  because anything changed about its cost. *(And built: Timeline on 2026-09-13, schema v19,
  §0.6.14 — noted 2026-09-24, since this list is where a reader looks to learn it is not.)*

---

## 11. Document Notes

This spec was consolidated from a single extended design conversation covering navigation/visual
design, the widget system, and the Pages/Tasks/Habits data model, then updated 2026-07-13 following
a pre-build gap-analysis pass (SAF vs. Shizuku, notification/FTS/secret-storage/backup-safety gaps,
the Pages block editor scope, and resolution of most §10 open questions). It is not a substitute for
the ~~three~~ **two** HTML prototype files listed at the top *(**corrected 2026-09-06:** a count left
over from before the 2026-09-04 reference-artifact pass established that
`noema-accent2-fallback-comparison.html` was never in the repository; two exist, the third is
historical)* — those remain the pixel-accurate reference for
anything visual; this document is the reference for *decisions, reasoning, and what's still open*.

**Suggested next steps:**
- ~~All consolidated open items are resolved as of 2026-08-26 (§10) — nothing currently blocks
  starting Phase 1 of the build sequence (§9.9).~~ **Both halves are out of date (corrected
  2026-09-06):** the build sequence is finished rather than waiting to start — every §9.9 phase is
  implemented and ~~261 unit tests~~ a unit-test suite runs against it *(**recount 2026-09-07** — the
  figure is in the bullet below rather than restated here, because a count carried inside a sentence
  about something else is exactly how the stale ones got in)* — and §10 is no longer empty, because
  the 2026-09-06 instruction reopened every deferral in this document at once. The live next step is
  that reopened backlog, ordered by dependency; it is not Phase 1.
- **The next step as of 2026-09-07: the staged backlog build, beginning with §9.10's Room
  destructive-migration switch.** What landed immediately before it was **Milestone 0**, which is
  hardening rather than a feature: a `locked()` write gate on the surfaces that had never adopted
  §3.1.2's View-Only lock — the Pages hub, Canvas (§3.7), Road Map, and Settings' import/restore
  paths, where the lock had been absent at *both* layers, so the screens neither refused a write nor
  hid the control that made one — plus a quarantine policy for unrecognised enum values at every
  sync boundary, so a record written by a newer build is set aside intact instead of being decoded
  halfway and then thrown, which used to take the local copy of whatever it was rebuilding with it.
  The migration switch leads the backlog on a dependency argument, not a size one: §9.10's
  acceptance criterion forbids leaving `fallbackToDestructiveMigration()` in place past the first
  release, and nearly every reopened item adds to the schema (§5.4's `RELATION`/`COMPUTED` property
  types, §5.6's Timeline view) — so each one built before the switch is one more migration to
  hand-write afterwards, against a schema that moved in the meantime.
- **The suite, counted rather than remembered (2026-09-07): 321 `@Test` methods across 29 unit-test
  classes** under `Tendril android\app\src\test`, none carrying `@Ignore` or `@Disabled`. That is a
  green-run tally, not merely a static count: the JUnit XML under `app/build/test-results` records
  321 tests, 0 failures, 0 errors and 0 skipped across all 29 classes. It supersedes the "313
  passing" figure recorded earlier the same day, which was correct until the last round of
  quarantine tests landed. *(**Corrected 2026-09-07, twice over.** This bullet first claimed a
  "static count, not a green-run tally" — true when written and false seventy-three seconds later,
  once the suite ran — and then attributed the 321-313 gap to a named file from memory rather than
  from anything in the repository. A paragraph whose entire subject is not copying counts forward
  got its own count wrong in two separate ways. Both are left on the record rather than quietly
  replaced, because that is precisely the failure it was written to warn about.)* Stated this way because counts are what this
  document keeps getting wrong by copying them forward instead of recounting — §11's own "three
  prototypes" (two exist) and §8.1's "four density tiers" (three are named) were both that. Recount
  before reusing this number; do not carry it into a sentence about something else.
- If this spec is handed to a fresh conversation or another engineer, it's worth testing it cold:
  paste it in and ask what's unclear or assumed.
- Continue updating this file directly as further decisions get made, rather than letting new
  context accumulate only in chat history.

**Tooling note (checked 2026-07-12):** no specialized native-Kotlin project-scaffolding skill was
available when §9 was written — it's built from general Android/Kotlin knowledge plus the targeted
current-state research cited inline (AGP, Glance, OAuth, and, as of the 2026-07-13 pass, the
SAF/Syncthing precedent and Android 16 behavior changes). Worth re-checking for a more specific
scaffolding skill before Phase 1 (§9.9) begins, since one may become available later.

See the **Revision Log** at the top of this document for the version history — each entry's full
reasoning lives in the section it touched, not here.

**Version control (Corrected 2026-09-04 — supersedes "No version control", decided 2026-08-30):**
this project is now under git, in a single repository at `haziaferi/tendril`, covering all three
sibling folders. The earlier decision — that the project used no git at all, that there was no git
account, and that readers should consult the Revision Log rather than `git log` — no longer holds and
should not be acted on.

One repository rather than three was itself a decision, and the reason is the folder layout below:
both `Tendril android\` and `Tendril windows\` resolve the shared core as `includeBuild("../shared")`,
a *relative sibling* path. Only a single repository reproduces that arrangement from one clone. A
submodule cannot — a submodule must live inside its superproject, which would nest `shared\` under a
consumer and break both build files. Keeping the three together also keeps cross-cutting changes
atomic: about two thirds of the Android app's source files import `shared` packages — 35 of the 55
under `app/src/main` at the time of writing — so a change routinely spans a DAO
query and its Android caller, or a use case and its test.

What this does *not* change: the Revision Log stays the index of decisions and reasoning, because a
commit history records what changed, not why it was chosen over the alternative. Read this file for
the reasoning and `git log` for the sequence — they answer different questions.

Build and setup instructions deliberately live outside this spec, in `README.md` at the repository
root. Setup steps are not decisions, and this document is a decisions record.

**Folder layout (2026-08-30):** the project lives across three sibling folders under
`...\Builds\Tendril\`, not one single project root:
- `Tendril android\` — the existing Android app (`app\`, single-module Gradle project). Builds the
  APK. *(This file lived here until 2026-09-18 — §0.10 item 9; it sits at the repository root now.)*
- `shared\` — a Kotlin Multiplatform module (`android` + `desktop` targets) holding the Room 2.8.4
  data/domain/sync-merge layer **and, since Milestone 3, the Workbench UI both clients render from**:
  theming, the five-tab nav shell, and the Pages/PageDetail/PageDatabase block editor with their
  ViewModels, on Compose Multiplatform *(**added 2026-09-06**, worth stating here rather than only in
  the windows spec because it widens what §12's anti-drift rule covers — a Compose change to the block
  editor is now a change to both apps)*. Consumed by both other folders via Gradle composite builds
  (`includeBuild("../shared")`). See §12.
- `Tendril windows\` — the desktop companion (Compose Multiplatform, JVM). Builds the Windows EXE.
  Has its own spec file, `tendril-windows-spec.md`, living in this folder — see §12.

---

## 12. Desktop Companion (graduated to its own file 2026-08-30 — see `Tendril windows\tendril-windows-spec.md`)

**Status:** all desktop-companion-specific content — feasibility, the platform-strategy scoring, the
open page-merge-granularity risk, Milestone 1's implementation, and the desktop build sequence — now
lives in `tendril-windows-spec.md`, in the `Tendril windows\` folder. The graduation trigger this
section itself once set ("split into its own file... when desktop work actually enters the build
sequence — not before") was met once Milestone 1 shipped real, running code; the full history and
reasoning of that decision moved with the content it decided about, into that file's own §4.

**Anti-drift rule, binding on both files (the actual mitigation for the drift risk a split always
creates — the file boundary itself doesn't prevent it):** whenever a change touches code in
`shared\`, both `tendril-windows-spec.md`'s Revision Log and this file's Revision Log get an entry
the same day — even if one of the two is just a one-line pointer to the other. Whichever file's
editor makes the `shared\` change is responsible for adding both entries.

**What stays here, not there:** the shared domain/data/sync-merge model itself — `Entry`,
`RecurrenceRule`, the Room schema (§4), the to-do database sync mechanism (§5.2), the JSON snapshot
format and whole-page LWW merge (§9.4), Room's migration policy (§9.10) — because it originated here
and Android remains the primary client. `tendril-windows-spec.md` references these section numbers
rather than redescribing them; if a future change to any of them affects desktop (it usually will,
since `shared\` is where this content actually lives in code), the anti-drift rule above applies.

---

