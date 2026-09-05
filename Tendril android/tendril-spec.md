# Tendril — Product & Technical Specification

**Status:** living document, consolidated from design conversation. Sections are marked **Decided**,
**Open**, or **Deferred** — treat anything not marked Decided as unsettled, even if it reads
confidently. App name **decided 2026-08-04: Tendril** (previously carried as the "Noema" working
title). The 2026-08-04 Revision Log entry claimed the document was "retitled throughout"; it wasn't —
22 prose references to the old name survived until 2026-09-04, when they were finally replaced. The
two that remain, here and in that log row, are deliberate: they are *about* the rename. The
prototype **filenames** below still start `noema-`, because those are real files on disk.

**Reference artifacts** (interactive HTML prototypes, ground truth for pixel-level detail). They
live at the **repository root**, one level up from this file — paths below are relative to it.
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

---

## 1. Overview

Tendril is a personal productivity app — an Android app plus a Windows desktop companion (§12) over
a shared Kotlin Multiplatform core — combining a Notion-like page/database system, a
calendar capable of replacing the phone's default calendar, a combined tasks-and-habits tracker, a
relationship map between pages, and a themeable settings layer — all built to avoid the two failure
modes of this category: burying power-user features, and overwhelming casual use. One person should
be able to use it to track groceries and a company roadmap without the app feeling like two
different products.

**Five pages:** Pages, Calendar, Tasks & Habits, Road Map, Settings — see §3 for the full functional
spec of each.

**Target device / build environment** (Decided):
1. minSdk 30 (Android 11+), targetSdk 36+ (Android 16+) — see §9.2.1 for behavior changes this
   triggers regardless of distribution channel
2. Unrooted
3. Storage Access Framework (SAF) folder access for the sync folder — no Shizuku, no root (Decided
   2026-07-13, supersedes the original Shizuku plan; see §9.3)
4. An external Syncthing-fork app handles the actual sync; Tendril only reads/writes snapshot files
   inside a folder that app is already syncing — never the live database itself (see §9.4)
5. Personal use only — no Google Play distribution
6. Built on Windows 11, Android Studio, JDK 21

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

- **Page cards** (Pages tab): horizontal row layout (icon, title, meta stacked to the right of the
  icon) rather than the original stacked/vertical card — roughly half the height of the first
  version, so more pages/databases are visible without scrolling, while keeping icon and font size
  unchanged.
- **Page-level settings icon**: always the horizontal three-dot (⋯) icon on Calendar and Tasks &
  Habits' own settings entry points — never the gear icon, which is reserved exclusively for the
  main Settings tab, so the two are never visually confused.
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

**Decided**, scoped to Workbench only (the other three navigation explorations do not get this
theming system).

- Lives behind a **collapsed-by-default disclosure row** in Settings (icon, current summary e.g.
  "Ink · Light · Sans", chevron) — not always-expanded controls, to avoid accidental taps while
  scrolling Settings.
- **4 colour themes × 2 modes = 8 palettes.** Every text/background pairing is WCAG AA verified:
  4.5:1 for body/small text, 3:1 for large text (≥~24px or ≥~19px bold) and icon-only glyphs.
- **2 typefaces**, selected independently of colour: **Sans** (DM Sans) and **Serif** (Source Serif
  4) — one family for both headings and body in either case, chosen deliberately over a display+body
  split for lower layout risk.

**Exact palette values** (hex, from the live prototype's source):

| Theme | Mode | bg | surface2 | text | textDim | textFaint | accent | accentStrong | onAccent | accentSoft | accentSoftText | border |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Ink | Light | `#FFFFFF` | `#F1F0EC` | `#1B1B18` | `#6B6A63` | `#959389` | `#4A5568` | `#333D4D` | `#FFFFFF` | `#E7EAEE` | `#3D4759` | `#E4E2DA` |
| Ink | Dark | `#1B1D21` | `#24262B` | `#EDEDEF` | `#9B9DA3` | `#6A6C72` | `#8792A6` | `#A9B3C4` | `#12131A` | `#262A33` | `#9BAAC2` | `#2A2D33` |
| Clay | Light | `#FFFFFF` | `#F1E9DC` | `#2A2320` | `#7A6B5C` | `#A28F79` | `#A87249` | `#8A5D3B` | `#FFFFFF` | `#F3E4D3` | `#7A4E2E` | `#EBDFCC` |
| Clay | Dark | `#221C18` | `#2B241F` | `#F2EAE1` | `#B0A190` | `#776A5A` | `#D4A57C` | `#E8C39D` | `#1A1310` | `#33281F` | `#E0B98E` | `#362E27` |
| Moss | Light | `#FFFFFF` | `#EBF0E9` | `#1E241F` | `#5E6B5F` | `#889783` | `#6E8C70` | `#48624A` | `#FFFFFF` | `#E4ECE3` | `#3C5240` | `#E2E9E0` |
| Moss | Dark | `#1C211B` | `#232A22` | `#E9EFE7` | `#9DAA9A` | `#6B766A` | `#93AC93` | `#B3C7B1` | `#131A13` | `#253026` | `#A9C2A8` | `#2A322A` |
| Mauve | Light | `#FFFFFF` | `#F1E7EC` | `#241E22` | `#7A6B72` | `#A38C99` | `#A9708D` | `#7D4D64` | `#FFFFFF` | `#F0DEE6` | `#7A4A5E` | `#EBD9E1` |
| Mauve | Dark | `#211A23` | `#29212C` | `#F0E6EB` | `#AC9AA5` | `#77636D` | `#CC93AC` | `#E0AFC4` | `#18121A` | `#322730` | `#DBA9BE` | `#362B38` |

`surface` is white (light) / the theme's near-black-tinted dark tone (dark) in every case — see
prototype source for the exact `surface`/`surface-3` steps not tabulated above.

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
page to get started"), Trash ("Trash is empty"). Calendar needs no bespoke empty state, since Day
view always renders a date grid regardless of content. No onboarding wizard and no seeded sample
content: this is a single-developer, single-user build — a walkthrough only its own author would
ever see is pure sunk cost, and seed content is one more thing to delete rather than a genuine aid.

**Acceptance:** every list-shaped screen in §3 renders through the shared `EmptyState` composable
rather than a bespoke per-screen layout; each tab's copy and CTA are fixed here, not improvised ad
hoc mid-build.

---

## 3. Page-by-Page Functional Spec

### 3.1 Pages (Notion-like)

Capable of creating many pages, projects, and databases without burying quick settings, save/sync
status, or import/export behind menus. Must handle a large personal knowledge base without becoming
visually noisy.

- Import from Notion (Markdown & CSV export format — see §7 for the full fidelity spec)
- Full import/export of pages and projects
- Page cards show icon (page vs. database), title, and metadata; horizontal layout per §2.2

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
- Bulleted list item, numbered list item (nestable one level via indent — matches typical
  personal-notes depth, not arbitrary nesting)
- To-do (checkbox) — a **block-level checkbox for freeform checklist text inside a page**,
  structurally distinct from the Property-level Done checkbox that drives Sync-to-Tasks (§5.2).
  Checking one here never creates a Task.
- Quote
- Code block (with a language tag; no syntax highlighting required for v1 — a nice-to-have, not
  needed for import fidelity)
- Divider
- Image (local file, copied into app-private storage on insert — kept out of the SAF-synced snapshot
  folder, §9.4, to keep the sync payload small; worth confirming this split is acceptable)
- Toggle (actually collapses, unlike §7.2's degraded-on-import case, which is permanently open)
- Callout (icon + colored background — natively rendered; an *imported* callout starts as a raw-HTML
  fallback block, per §7.2, until manually converted)
- Page mention (`@Page Title`) — inline reference to another Page; the primary source of Road Map's
  edges (§3.4)

**Data model:** a `Block` entity per row — `id, page_id, type, order, parent_block_id (nullable, for
list/toggle nesting), content (typed per block type), created/updated`. Inline formatting
(bold/italic/strikethrough/inline code/links/page-mentions) is stored as `(start, end, style)` spans
over a block's plain-text content rather than embedded markup — keeps FTS indexing (below) simple,
since the indexed text is just the plain content with spans stripped.

**Interaction model:** a slash-command menu (`/`) to insert any block type at the cursor; a drag
handle on hover/long-press to reorder; a floating selection toolbar for inline formatting on text
selection.

**Full-text search (confirmed necessary):** a Room FTS4/5 virtual table indexing each page's
concatenated block plain-text, rebuilt on block write. Page-level granularity for v1, not per-block
— a search hit opens the page and scrolls/highlights the first match; per-block result granularity
is a reasonable later refinement (§10) if page-level feels too coarse.

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
defense-in-depth: every mutating ViewModel function also early-returns through the same flag (a
`locked()`/`contentLocked()` guard), not just the UI disabling the affordance, so a missed UI gate
can't silently let an edit through. Checkbox-only mode is built the same way — a second
`CheckboxOnlyState` (one active page at a time, since the window flags it drives are window-level)
combined with `LocalViewOnly` into a `LocalContentLocked` CompositionLocal that every control except
a to-do block's own checkbox respects. `WorkbenchScaffold` owns the "architecture note" concern
directly: a `LaunchedEffect` keyed on the active page id and the current nav back-stack entry calls
`Activity.setShowWhenLocked`/`setTurnScreenOn` only when they match, and deactivates the state
entirely the instant they don't (covering "navigates away" without needing an unlock). Turning it
back off while still on the page reuses the existing `showAppUnlockPrompt` (§3.6's `BiometricPrompt`
mechanism) rather than a second bespoke prompt.

### 3.1.3 Page templates (Decided 2026-08-08)

Any Page or Database can be saved as a template from its "···" menu — "Save as template." A template
captures the page's block structure (for a Notion-like page) or its schema plus default property
values (for a Database), not a specific content instance. Creating a new page offers "New from
template" alongside a blank page, listing the person's own saved templates plus a small built-in set
(blank page, blank database, to-do database — folding §5.3's existing shortcut in as the first
built-in template rather than a separate mechanism). Templates live as ordinary Pages internally,
flagged `is_template = true`, excluded from Road Map's edges (§3.4) and from the Pages hub's default
list — surfaced only via "New from template" — so they don't clutter the page tree.

Deliberately no template *variables/placeholders* (e.g., an auto-filled "created date" field) for v1
— real Notion feature, but nothing elsewhere in this spec needs it; a template here is a structural
starting point, filled in by hand like any other page.

### 3.1.4 Daily journal (Decided 2026-08-08)

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

### 3.1.5 Backlinks panel (Decided 2026-08-08)

A collapsed-by-default "Linked mentions" section at the bottom of every page, listing every other
Page whose body contains an `@Page Title` mention of this one (§3.1.1) — the same edge data Road Map
(§3.4) already computes, consumed here as a second, lighter-weight view rather than a second query
or a new data source. Each entry shows the linking page's title and the block containing the
mention; tapping opens that page.

No "unlinked mentions" (plain-text title occurrences that were never turned into a real `@mention`)
for v1 — surfacing those requires scanning all page content for substring matches, a real
FTS-adjacent feature of its own scope, not needed to satisfy "what links here."

### 3.1.6 Tags (Decided 2026-08-08 — resolves the previously-undefined `category` field)

§4's Page entity carried a `category` field from an early draft with no behavior ever specified
behind it — flagged during this pass as a genuine spec gap rather than a deliberate decision.
Resolved by replacing it with a proper tag system: a global, reusable, freeform set of tags
(create-on-type, matching Joplin's own pattern), applied many-to-many to any Page — not just
Database rows, which already have Select/multi-select properties for this purpose within a database
(§4). A `Tag` entity (`id, name, color`) plus a `PageTag` join table connect the two (§4).

Tags surface as a filter-chip row in the Pages hub (filter the page list by one or more tags) and a
small tag editor on each page itself, using the same type-to-search-or-create interaction already
established for page mentions (§3.1.1). Deliberately flat — no tag hierarchy/nesting; Joplin's own
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

**Acceptance:** the search icon opens a dedicated full-screen search surface, not an inline
dropdown; an empty query shows nothing; a query with no matches uses the §2.5 `EmptyState`
composable ("No pages match '…'").

### 3.2 Calendar

- Day, Week, Month views; **week starts Monday**; defaults to **Day** view on open. All three draw
  *occurrences*, not stored rows (§4.1.1) — a recurring EVENT appears on every occurrence in view
  and a multi-day one on every day it covers, each labelled "day N of M"
- Create and edit events and tasks directly
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
- **Quick Add**: fast, minimal single-line capture. Deliberately does **not** include the Reminders
  list (see §5.4) — that lives in the fuller edit sheet, keeping Quick Add fast
- **Show Habits** button: small toggle surfacing habit entries (with time+duration) inline in the
  calendar view
- **Reminders** (Decided, §5.4): repeatable list per event/task, presets (1h / 2h / 4h / 8h / 1 day
  / 2 days / 1 week) or custom (number + unit), no cap on how many stack, lives in the full edit
  sheet only
- Recurrence (Decided, §6.2): `None / Daily / Weekly / Monthly / Custom (interval + unit)`, anchored
  to the **original fixed schedule** — a missed occurrence does not shift subsequent ones

### 3.3 Tasks & Habits

Three views: **Tasks**, **Habits**, **Merged** — one page, kept from losing clarity despite covering
two different concerns.

**Tasks** (GTD-style todo list):
- Entries are the same entries the Calendar uses. Deadlines sync both ways on add/edit (already true
  from the original spec, reinforced by §5/§6's database and recurrence design)
- Today / This week / This month filter bar (§2.2), default **Today**
- Undated tasks collapse into a toggle at the top, filtered consistently with whichever time-filter
  is active
- Recurring tasks supported (§6.2) — calendar-native recurrence, distinct from Habit streaks

**Habits** (adjacent but distinct — daily/periodic personal practices, not work):
- Optional time + duration (not required)
- Habit sync folder picker lives in **Tasks & Habits' own settings** (not main Settings) — written
  to via an SAF folder grant, read by the external Syncthing-fork app (§9.3, §9.4)
- Streak-based; missing an instance does **not** create backlog (§6.1 — this is the defining test
  that separates a Habit from a recurring Task)
- **Decided (2026-07-13)**: Habits stay a simple, structurally separate top-level entity — not the
  database-driven Sync-to-Tasks pattern. §5.2's own reasoning against inferring Task-sync from
  schema shape ("a 'Cooked?' checkbox... should never silently become a Task") applies at least as
  strongly here, and Habit completion semantics (cadence + streak, no deadline) don't map onto the
  Done+deadline property bindings §5.2 defines. A future database-driven "Sync to Habits," mirroring
  §5.2's shape, is a plausible later extension — but as its own separate toggle/bindings, not a
  reuse of the Task mechanism.

**Merged**:
- A calendar-style view holding both Tasks and Habits together
- Habits with time + duration get a **delicate highlight** to distinguish them from Tasks in the
  same view
- Undated tasks collapse into the same top toggle pattern as the Tasks view

### 3.4 Road Map

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

- Interactive map of relationships **between existing Pages**, redirecting to each on tap
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

### 3.5 Settings

- **Appearance**: theme picker (§2.3), collapsed behind a disclosure toggle
- **Anthropic API key** field (for any Claude-API-assisted features, e.g. a future in-page mind-map
  generator). **Decided (2026-07-13)**: the app is fully local and makes zero network calls until
  this key (or Google OAuth, §3.2) is actually toggled on — no client/library is constructed at
  startup. On first toggle-on, the key is written to Android Keystore-backed encrypted storage
  (Jetpack Security `EncryptedSharedPreferences` or equivalent), never plain prefs/Room. **Corrected
  2026-08-29**: Google Calendar sync (§3.2) doesn't actually need this treatment — see §9.5, it
  never persists a refresh token or any other secret; the only local state is a plain, non-secret
  "connected" flag.
- **Sync folder permission** (SAF) — grants the folder used for Habit sync and full export/import
  (§9.3, §9.4); replaces the earlier Shizuku-toggle plan
- **Full data import/export** across every page (all pages/projects/databases, tasks, habits,
  events) — **Restore from backup** (full replace) and full-app **Export**/**Import** (additive)
  live here as distinct actions; see §9.4.1 for the format and the merge-vs-replace design
- Every other agnostic, cross-cutting setting belongs here by default unless it's specific enough to
  a single page to live in that page's own settings (the pattern already established for Calendar's
  Google Calendar connect control and Tasks & Habits' sync-folder picker)

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
- **Interaction with checkbox-only mode (§3.1.2)**: independent, no precedence rule needed — App
  Lock gates entering the app at all; checkbox-only governs one already-open page's behavior over
  the lockscreen. A person can reasonably want the app locked normally but still let one
  already-open checklist page survive the lockscreen, since the two gate different moments (before
  vs. after entry).

---

## 4. Data Model Sketch

**Status: reviewed 2026-07-13.** The Task/Event relationship (below), Habits architecture, and to-do
eligibility questions that were open when this section was first drafted are now resolved. Still a
starting point for the real schema work, not a byte-for-byte ratified design.

**Core entities:**

| Entity | Key fields | Notes |
|---|---|---|
| **Page** | id, title, icon/kind, parent_id, deleted_at (nullable, added 2026-08-08 — Trash, §5.5.1), created/updated | `kind` distinguishes a plain page from a database. `parent_id` builds the page tree (also what Notion import needs to reconstruct, §7). Body content is a structured `Block` list (§3.1.1), not a blob field. **`category` removed 2026-08-08** — carried from an early draft with no behavior ever specified behind it; replaced by the `Tag`/`PageTag` entities below (§3.1.6). |
| **Tag** | id, name, color | Global, freeform, reusable across all Pages (§3.1.6, added 2026-08-08) — resolves the removed `category` field. Flat, no nesting. |
| **PageTag** | page_id, tag_id | Many-to-many join table between Page and Tag (§3.1.6, added 2026-08-08). |
| **Block** | id, page_id, type, order, parent_block_id (nullable), content, formatting spans, created/updated | One row per content block inside a Page's (or Row's) body (§3.1.1) — paragraph, heading, list item, code, image, toggle, callout, page-mention, etc. Feeds the FTS index (§3.1.1). |
| **Database** | *(a Page with a schema)* — schema (ordered Property list), `sync_to_tasks` flag, `done_property_id`, `deadline_property_id`, `recurrence_property_id` (nullable) | The Sync-to-Tasks flag and the explicit property bindings are the mechanism from §5.2 — not inferred from schema shape, always deliberate. `recurrence_property_id` added 2026-07-16 — see §5.2 for the binding and §4's Property type note below for the `Interval` type it points at. |
| **DatabaseView** | id, database_id, name, view_type (`TABLE` \| `BOARD` \| `GALLERY` \| `CALENDAR`), group_by_property_id (nullable, BOARD-only), date_property_id (nullable, CALENDAR-only), visible_property_ids, filter (single condition, nullable), sort_property_id (nullable), sort_direction | Saved views over a Database's rows (§5.6, added 2026-08-08) — display configuration only, never alters stored row/property data. A Database always has at least one Table view (default, matches §5.1's existing behavior). |
| **Property** | id, database_id, name, type, config | Type list needs to cover at minimum: text, number, checkbox, select, multi-select, date, URL, email, phone — the set Notion CSV export can actually carry (§7). Relation/rollup/formula are explicitly deferred (§7, §10). **Added 2026-07-16**: `Interval` (number + unit ∈ {day, week, month}) — a Tendril-native type, not part of the Notion CSV import set, used exclusively as the `recurrence_property_id` binding target (§5.2). Not offered as a general-purpose property type in the "New property" picker outside that binding context, to avoid a second, uglier way to represent a plain number. |
| **Row** | id, database_id, property values | A Row *is* a page (§5.1) — its free-form body beneath the properties is the same Block-based content as any Page (§3.1.1), matching Notion's actual row=page model. Tapping a row opens it. |
| **Entry** *(renamed from Task, 2026-07-13)* | id, title, kind (`TASK` \| `EVENT`), start_date (nullable), start_time (nullable), end_date (nullable, EVENT-only — see below), end_time (nullable, EVENT-only), recurrence_rule (typed, see below), original_entry_id (nullable, FK to another Entry), original_occurrence_date (nullable), is_exception_skip (nullable), status (`PENDING` \| `DONE` \| `SKIPPED`, TASK-only), source_row_id (nullable, FK to Row — **added 2026-07-16**, see below), deleted_at (nullable, **added 2026-08-08** — Trash, §5.5.1), source | Calendar queries `WHERE start_date IS NOT NULL` regardless of kind. Tasks view queries `WHERE kind = TASK`. Merged view (§3.3) also filters `kind = TASK`. Both queries also filter `WHERE deleted_at IS NULL`. `source_row_id` is the actual foreign key behind the Row↔Entry link §5.2 has described behaviorally since it was designed but never named as a real field — a small, previously-invisible gap surfaced while designing the rebind mechanism (§5.2). Full reasoning for the multi-day, recurrence-split, and exception design below the table (2026-07-14 case-scenario round 2). |
| **Habit** | id, title, time (nullable), duration (nullable), frequency, streak, previous_streak, previous_completed_date (nullable, **added 2026-08-29** — undo-check-in, §8.1.1), deleted_at (nullable, **added 2026-08-08** — Trash, §5.5.1) | Structurally separate from Task (Decided 2026-07-13, §3.3) — does not follow the to-do database pattern. |
| **Reminder** | id, owning Entry id, offset-or-preset, anchor_time (nullable) | Presets: 1h / 2h / 4h / 8h / 1 day / 2 days / 1 week; or custom (number + unit). No cap on count. Applies to either kind — a birthday reminder is just as valid as a deadline reminder. **Decided 2026-07-14**: when the day a reminder anchors to has no `start_time`/`end_time` (an all-day Event, or one of the all-day middle days of a multi-day span), the reminder needs a concrete time-of-day to actually fire at — prompted via presets (8:00 AM / 10:00 AM / 12:00 PM / 4:00 PM / custom), defaulting to midnight if none is chosen. Stored in `anchor_time`, unused when the Entry already has a real time on the relevant day. Separately, `kind = TASK` Entries also get an automatic **overdue notification** at `start_date`+`start_time` (or midnight if `start_time` is null) with inline Done/Skip actions — distinct from these pre-due reminders, and the trigger for the Skip-only-once-overdue rule above; folded into §9.7. |
| **EntryCompletion** *(new, 2026-07-14 — resolves round 1's open history question)* | id, entry_id, occurrence_date, resolved_at, status (`DONE` \| `SKIPPED`) | Append-only log, written by the same centralized resolution step that advances a recurring Entry (below) — every TASK resolution gets logged uniformly, recurring or not, so a future "history" screen has one consistent source rather than recurring Tasks silently having no record (per round 1's finding) while one-off Tasks do. |

**Decided (2026-07-13), corrected (2026-07-14):** Task and Event are the *same underlying table* —
Calendar and Tasks are two queries over one row, avoiding the dual-write/id-mapping/drift risk of
two synced tables — but they are **not the same *kind* of row**. The first pass of this decision
missed that: a birthday, a holiday, or a plain meeting has no meaningful done/not-done state, and
would have wrongly landed in the Tasks list with a checkbox under a single undifferentiated table.
The fix is the `kind: TASK | EVENT` discriminator on the renamed `Entry` entity above — one table
for the sync benefit, one extra column for the actionability distinction that was actually needed.
This still resolves the item that touched Calendar, Tasks, Habits' Merged view, and the widget
Agenda list simultaneously, now correctly.

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
- **Exception rows were dead.** `original_entry_id`/`original_occurrence_date`/`is_exception_skip`
  (round 3 above, §9.8 R5) were declared, round-tripped through snapshots, and never written, read
  or honoured by anything.

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

**Alarm rescheduling must be event-driven, not schedule-ahead (Decided 2026-07-14).** `AlarmManager`
cannot know a TASK's next occurrence before it exists — elastic recurrence means the interval itself
depends on *when* the person resolves the current one (resolve 2 days late, the gap to next time is
7+2 days, not a fixed 7). This means only the currently-live occurrence's alarms should ever be
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
`Interval` type (§4 — a number plus a unit, day/week/month). Binding it wires that property's value
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

### 5.4 Reminders (not a computed property)

An earlier idea — a computed "remind me N days before a date" *property* on a database row — was
explicitly dropped. The actual need (assessing timing across visible dates, e.g. when to schedule a
blood test before a follow-up appointment) is already satisfied by the visible-properties table view
(§5.1); no computed field was needed for that. Reminders themselves are a Calendar/Task feature, not
a database one (§3.2): a repeatable list per event/task, presets (1h/2h/4h/8h/1 day/2 days/1 week)
or custom (number+unit), living in the full edit sheet (not Quick Add), no cap on how many stack per
item.

A general Notion-style formula language (arbitrary expressions referencing other properties) was
also explicitly scoped **out** — real feature, real complexity (closer to a small interpreter than a
database property), not planned for an early build.

---

### 5.5 Property and Row lifecycle safety (Decided 2026-07-15, case-scenario round)

Four real gaps found by walking concrete cases through the schema rather than reviewing it in the
abstract, all resolved the same way: confirm, and explain the consequence in plain language before
it happens.

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
- **Property deletion** requires an explicit confirm dialog. If the property is `done_property_id`-
  or `deadline_property_id`-bound, deleting it is just one more trigger into the same
  Sync-to-Tasks-disable flow below — not a separate bespoke behavior — with the dialog stating
  plainly that confirming disables sync, and that re-adding a same-named property later begins a new
  sync relationship rather than resuming the old one (the property's stored values are genuinely
  gone, even though the sync *relationship* re-establishing might feel like resuming from the
  person's side).
- **Row deletion and Sync-to-Tasks toggle-off** both require an explicit confirm dialog, stated in
  plain language: toggling sync off deletes the database's linked recurring Entries (a deliberate
  bulk-cleanup mechanism, not just a safety gate — it means the person never has to manually delete
  a database's worth of future Entries by hand when the whole thing is no longer needed). Turning
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
  Groceries database") so restoring makes sense out of context. *(**Open as of 2026-09-04 — still
  two lists, not one.** Pages and Rows are in a sheet off the Pages hub as specified; Entries and
  Habits are in a second sheet off the Tasks & Habits tab. Merging them is more than a move: a
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
  deletion of linked recurring Entries (also the third bullet above) is unchanged in trigger and
  confirm-copy, but the Entries it removes now land in Trash too, restorable via the same mechanism,
  rather than being gone outright. Property type conversion and property deletion (first two bullets
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
    identical gap for Entries had already been closed; the Tasks & Habits Trash sheet now lists
    both, with the same selection, bulk Restore / Delete forever and counted confirm this section
    requires, over one merged newest-first list rather than two stacked ones. Restoring an Entry
    still routes through `ResolveEntryUseCase` so a TASK comes back with its alarms rearmed
    (§9.7); a Habit has no alarms, so the DAO call is the whole operation.
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
  covering Pages, Entries and Habits. §9.4's additive rule is narrowed rather than abandoned: absence
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
by kind because Page and Entry uids come from separate spaces — in the same operation that drops
the row, never as two things a call site must remember to do in order. The merge declines a
tombstoned uid; the write pass drops that uid's page file.

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
  project database grouped by a "Status" Select property); drag-and-drop between columns writes the
  property value, same as editing it from the table. A database with no Select-type property yet
  shows a prompt to pick one or create it — no silent fallback.
- **Gallery**: a card grid, one card per row, showing a designated cover (the first Image block in
  the row's body, §3.1.1, or a blank placeholder) plus a small set of properties chosen per-view —
  matches Notion's/AppFlowy's own gallery pattern closely enough that a Notion import (§7) can carry
  a source gallery view forward directly.
- **Calendar**: rows plotted by any one Date-type property in the database, chosen per-view —
  deliberately more general than Sync-to-Tasks' binding (§5.2), which only ever plots the bound
  deadline property on the app's own Calendar page. This view is local to the database (e.g., see
  every row of a Trips database by "Departure date" without that property ever being bound to Tasks
  at all).

**Still explicitly out of scope, unchanged from §10's original reasoning**: Timeline/Gantt view and
formula/rollup-driven grouping — both are real added complexity (date-range bar rendering with
drag-resize; an interpreter for computed grouping keys) that didn't come up anywhere else in this
spec as an actual need, unlike Board/Gallery/Calendar which map directly onto cases already
described (§5.1's medical-appointments and Recipes examples, §5.4's blood-test timing example).

**Mechanism**: a database can have multiple saved views (matching Notion's own view-tab pattern),
each storing `view_type`, the grouping/date property it depends on, and its own visible-properties
selection (§4's new `DatabaseView` entity) — independent of the underlying schema, so switching or
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
  HTML callouts (§7.2) are detected structurally (a line beginning `<`) and preserved as a raw HTML
  fallback per §7.3.3, not silently dropped.
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
  each CSV column's values (boolean-like, ISO date, numeric) to pre-select a guessed type, then
  reuses the *exact* property-binding confirmation step already built for §5.2/§5.2.1 — not new
  surface area, per §7.3.6 — letting the person confirm or override before anything commits. Rejected
  fully silent inference: a wrong guess on an ambiguous column (e.g., a Select property whose values
  happen to look numeric) would silently mis-type data, which is exactly the "must not silently
  break content" failure §7 opens by ruling out.

**Implementation note (2026-08-30):** built as `com.tendril.app.notionimport` (`NotionMarkdownParser`,
`NotionCsvParser`, `NotionPropertyTypeInference`, `NotionImporter`) plus a Settings section reusing
`EnableSyncSheet` (§5.2's existing binding UI, changed from `private` to `internal` for cross-package
reuse rather than duplicated). Two gaps found only while implementing, not anticipated by §7.1-§7.3:

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
- **Shade slider** (0–100%): interpolates lightness from the original vivid tone toward a
  verified-safe extreme — L=0.12 in light mode, L=0.88 in dark mode — per theme's own exact base
  lightness (not a shared generic value; an early implementation bug used one generic lightness per
  mode instead of each theme's real value, caught and fixed before shipping).
- **Hue slider** (±90°): rotates around the theme's own accent2 base hue. Confirmed to cost almost
  nothing in contrast once Shade is high — exactly the "hue is free" finding from §8.2, now
  user-controllable rather than fixed.
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
**Deferred, not decided:** whether to extend the tool to model a *specific* declared or sampled
wallpaper luminance instead of the adversarial bracket, which would let it report realistic
pass/fail rather than worst-case-only.

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
Shade, Hue (§8.2, §8.3) — per placed widget instance, defaulting to whatever the currently-selected
in-app theme's values are at placement time. Theme (Ink/Clay/Moss/Mauve) and mode (Light/Dark) are
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
  `pages/<page_id>.json`, …) rather than one giant file, so an edit to one Page doesn't create sync
  tension with an unrelated Habit edit — a generalization of the single-file `anchor_snapshot.json`
  pattern an earlier, scrapped prototype used for habit-only sync. **Clarified 2026-07-16**: since a
  Row *is* a Page (§5.1), each Row gets its own `pages/<row_id>.json` file too, same as any other
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
    (Trash, §5.5.1) — written once on the transition into that state, then read rarely (history,
    search, Trash) and essentially never rewritten again.
  - **Transition**: resolving an Entry or trashing/restoring one (§5.5.1) moves its record between
    the two files as one added step inside those same operations — not a new mechanism.
  - This directly neutralizes the growth-safety risk of Trash having no auto-purge (§5.5.1, previous
    correction): an unbounded, never-emptied Trash inflates only `entries_archived.json`, which sits
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
  conflict file.
- **Accepted v1 limitation**: for Pages specifically, LWW applies at the whole-page snapshot level —
  concurrent edits to the *same page* on two devices before either syncs means one edit is lost.
  Acceptable for a personal, limit-case scenario; flagged explicitly rather than left implicit.
- **UI**: a "last synced at ·" indicator plus a manual "Sync now" action.
- **Decided (2026-08-04, resolves the 2026-07-15 reopening — Page-snapshot write timing):**
  debounce, not per-mutation. A Page's `pages/<page_id>.json` snapshot is written 2 seconds after
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
    in a folder Syncthing is actively watching.

  **Still not implemented, and deliberately not faked: the 2-second per-page debounce itself.** The
  flush triggers above are the half that has somewhere to live; the debounce is the half that
  doesn't yet. It is specified per *page* — "a Page's `pages/<page_id>.json` snapshot" — and
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

### 9.4.1 Portable export/import (Decided 2026-07-16)

Distinct from §9.4's continuous background sync feed between two of a person's own devices — this is
a one-off, user-initiated, shareable file: a full backup, a single Page/Database sent to someone
else, or a Notion export being brought in. §3.5's "full data import/export" previously pointed at
§9.4's mechanism directly, which conflated a live sync feed with a portable file and left import
behavior (merge vs. replace) completely unstated — the actual cause of the risk flagged for
busy-parent-style cases (two people's separate agendas, one import away from silently overwriting
the other's data).

- **One schema, reused everywhere.** The exact per-domain JSON shapes §9.4 already defines —
  `entries_active.json`, `entries_archived.json`, `habits.json`, one `pages/<page_id>.json` per Page
  or Row — are the only serialization format; a portable export doesn't invent a second one, it just
  packages the same files differently.
- **Packaging**: a zip, given a dedicated extension so it behaves as one shareable file rather than
  a loose folder (the same trick `.docx`/`.epub` use) — **`.tendril`**, decided 2026-08-04 alongside
  the app name itself. Contains a `manifest.json` (app version, export timestamp, `full` or
  `partial`, and the exact list of domain/page files included) plus whichever domain files the
  export actually contains.
- **Two distinct actions, not one "Import" with hidden behavior:**
  - **Import** — always additive, never replaces. Reuses §9.4's existing per-record last-write-wins
    merge rule (no new merge logic invented) and, for a Page, the same accepted whole-page-snapshot
    LWW limitation already in place for cross-device sync. Before merging, the manifest drives a
    picker showing exactly what's in the package, letting the person choose what to actually bring
    in rather than all-or-nothing — this is the direct fix for two people wanting to combine
    separate agendas without one silently overwriting the other. Notion import (§7.3) is just one
    flavor of this same path — it was always additive-only in practice, never a full-app replace, so
    it never had a destructive-replace risk to begin with; only manually-created or
    manually-exported Tendril packages did.
  - **Restore from backup** — a separate, deliberately harder-to-reach, full wipe-and-replace
    action, worded unambiguously about what it does (e.g. requiring the person to confirm they
    understand current data will be erased, not a soft dialog matching §5.5's lighter pattern).
    Natural fit for a fresh/empty install or genuine disaster recovery; not reachable via the
    everyday Import path.
- **Selective export** lives on each Page's and each Database's own "···" menu — exporting that
  item, with a prompt (mirroring Notion's own "include subpages" option, §7.1) for whether to bring
  sub-pages along. A full-app export stays in Settings (§3.5) alongside the SAF sync-folder
  permission.

### 9.4.2 At-rest snapshot encryption (Decided 2026-08-08 — optional, off by default)

Syncthing (or its fork) encrypts data in transit between devices, but the snapshot JSON files above
sit as plaintext on-disk, on every device, for as long as they exist inside the SAF-granted folder —
a real exposure on a lost/stolen phone or a compromised sync-fork install, for data that includes
medical appointments (§5.1) and financial recurring bills (§5.2.2). Settings → Sync folder
permission (§3.5) gains an adjacent **optional passphrase** toggle:

- **Off by default** — matches the app's consistent "opt-in, nothing surprising by default" pattern
  (Anthropic key, Google OAuth, App Lock §3.6).
- **On**: every snapshot file is encrypted before the atomic write above and decrypted after read,
  using a passphrase-derived key (Argon2id or PBKDF2 into AES-256-GCM — the exact choice left to
  implementation, not a spec-level decision). The same passphrase must be entered on every device
  sharing the sync folder, since Tendril has no account/identity system to distribute keys through.
  The passphrase itself is never written to disk or synced; only Keystore-backed
  `EncryptedSharedPreferences` (§3.5's existing mechanism) holds it locally per device, re-entered
  once per install.
- **The PBKDF2 salt lives in the folder, not in the binary** (`sync_meta.json`, added 2026-09-05).
  The original reasoning above — that a random salt needs a channel to distribute it and Tendril
  has none — was wrong in one respect: the sync folder *is* the channel, the same one the
  snapshots travel through. A compile-time salt meant one precomputed table worked against every
  Tendril install in existence, and two people choosing the same passphrase got byte-identical
  keys. The meta file is deliberately never encrypted (a device without the key still has to read
  the salt in order to derive it; a salt is not a secret, it defeats precomputation in the open),
  and a folder written before it keeps its original salt so it stays readable. Two devices that
  enable encryption before either has synced both mint one — earliest `createdAt` wins, ties
  broken on the salt bytes, so every device converges without negotiating.
- **A folder that says it is encrypted does not accept plaintext** (added 2026-09-05). Until the
  meta file existed there was no way to tell an injected plaintext file from a folder that had not
  been encrypted yet, so any non-encrypted file was trusted — which made AES-GCM's authentication
  tag worth nothing at the system level, since nothing forced a file to be encrypted at all.
  Anyone who could write to the synced folder could inject records without the passphrase.
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
immediate-fire notification is suppressed. **Batched summary notification, added 2026-07-16**: after
a bulk operation creates one or more already-overdue Entries in one pass (retroactive sync-on,
Notion import), a single summary notification fires instead of silence — "12 tasks were imported
already overdue" — the same pattern as a routine "downloads finished" notification elsewhere on the
OS, not a per-item alert.

### 9.8 Architecture review (2026-07-14 — Review Mode, deep-review strategy)

Requested as a final audit of the Entry/recurrence/notification cluster before Phase 1. Mode:
**Review** (an existing design, not a blank slate). Strategy: **deep-review** over quick-audit —
committed directly rather than run through a separate selection pass, since the asymmetry is already
stated plainly enough to reason from: under-auditing something about to be built is costly,
over-auditing costs only some reading time.

**Structural analysis.** `Entry`'s high fan-in (Calendar, Tasks, Merged, widget Agenda, Reminder,
`EntryCompletion`, the to-do-database bridge, the Provider writer, and snapshot export all touch it)
is expected for a central domain table and not itself a problem. What *is* a real risk: the
five-step resolution sequence (log completion → advance-or-finalize → reset the bound Row property →
reschedule alarms) is exactly the shape of logic that quietly drifts if five different UI surfaces
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
even that can't cover, reuse the existing Restore-from-backup path (§9.4.1) — wipe Room, then replay
the most recent `.tendril`/snapshot-folder export, which is schema-independent JSON rather than a
byte-for-byte Room dump. Gate that path behind the same plain-language confirm dialog §5.5 already
uses elsewhere ("this update needs to reset local data; your synced pages, tasks, and habits will be
restored from your last sync").

**Acceptance** *(restored here 2026-09-04 — this block was printed at the end of §9.11, so §9.10 had
no acceptance criteria and §9.11's acceptance-tested a different section; §9.11 now has its own)*:
every schema change from v1 onward ships with either an `@AutoMigration` entry or an explicit
`Migration`, never a silent `fallbackToDestructiveMigration()` left in place after the first
release; the snapshot-restore fallback is wired and manually tested at least once (a
deliberately-broken migration on a test device, confirming the restore path actually recovers a
populated Room DB) before it's relied on for a real one. **Pre-v1 the version number still has to
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

**Still open, not blocking Phase 1 (§9.9):** none. The five items below were the last consolidated
open items and are now resolved — see §9.9's decision-gate annotations for exactly when each was
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
- In-page mind-map block, **Claude-API-generated** (§3.4) — the reason Road Map was renamed away
  from "Mind Map" in the first place. *(**Corrected 2026-09-04:** this read "user-drawn or
  Claude-API-generated ... entirely separate feature, not started." The user-drawn half shipped as
  the Canvas page kind and is no longer deferred — see §3.4. Only the API-generated half remains
  unbuilt.)*
- Notion API-based import (using a user's own integration token) as a second, richer import path
  alongside the file-based Markdown/CSV importer (§7.3).
- Extending the widget live-audit tool to model one specific/sampled wallpaper luminance instead of
  the adversarial black/white bracket, so it can report a realistic pass in typical conditions
  (§8.6).
- Widget mode (light/dark) auto-suggestion based on detected wallpaper darkness (§8.2 bonus finding)
  — noted, not designed.
- Block-level (vs. page-level) full-text search granularity (§3.1.1) — reasonable later refinement
  if page-level feels too coarse.
- Syntax highlighting in code blocks (§3.1.1) — nice-to-have, not required for v1.
- Desktop companion app (§12) — feasibility and platform strategy explored and scored (Kotlin
  Multiplatform + Compose Multiplatform), but not scheduled into §9.9; graduates into its own spec
  file only once it actually enters the build sequence.

**Decided out of scope** (unlikely to resurface, listed for completeness):
- A general Notion-style formula language for database properties (§5.4).
- Full Notion-parity block editor (embeds, inline databases, nested-page canvases) — Notion-lite
  scope chosen instead (§3.1.1).
- Timeline/Gantt database view and formula/rollup-driven view grouping (§5.6) — real added
  complexity with no case elsewhere in this spec that needs them, unlike Board/Gallery/Calendar,
  which are now in scope (§5.6, reopened 2026-08-08 — see the Resolved list above; database views
  generally are **not** out of scope anymore).

---

## 11. Document Notes

This spec was consolidated from a single extended design conversation covering navigation/visual
design, the widget system, and the Pages/Tasks/Habits data model, then updated 2026-07-13 following
a pre-build gap-analysis pass (SAF vs. Shizuku, notification/FTS/secret-storage/backup-safety gaps,
the Pages block editor scope, and resolution of most §10 open questions). It is not a substitute for
the three HTML prototype files listed at the top — those remain the pixel-accurate reference for
anything visual; this document is the reference for *decisions, reasoning, and what's still open*.

**Suggested next steps:**
- All consolidated open items are resolved as of 2026-08-26 (§10) — nothing currently blocks
  starting Phase 1 of the build sequence (§9.9).
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
atomic: 33 of 52 Android source files import `shared` packages, so a change routinely spans a DAO
query and its Android caller, or a use case and its test.

What this does *not* change: the Revision Log stays the index of decisions and reasoning, because a
commit history records what changed, not why it was chosen over the alternative. Read this file for
the reasoning and `git log` for the sequence — they answer different questions.

Build and setup instructions deliberately live outside this spec, in `README.md` at the repository
root. Setup steps are not decisions, and this document is a decisions record.

**Folder layout (2026-08-30):** the project lives across three sibling folders under
`...\Builds\Tendril\`, not one single project root:
- `Tendril android\` — this file, and the existing Android app (`app\`, single-module Gradle
  project). Builds the APK.
- `shared\` — a Kotlin Multiplatform module (Room 2.8.4 data/domain/sync-merge layer, `android` +
  `desktop` targets), consumed by both other folders via Gradle composite builds
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

