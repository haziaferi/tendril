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
| 2026-09-05 (integration) | Anti-drift entry (§0): the recurrence branch and the audit branch were merged, and the resolution changed `shared\` again. `PagesSyncEngine` gains recurrence-aware queries and loses `getInRange`/`observeOnDate` (a date-window query never returns a recurring series, anchored as it is at its first occurrence). `SnapshotSyncOrchestrator`'s `readAndMerge` returns a `SnapshotMergeResult` again — the count of files it could not decrypt — with the tally carried on the same value as the folder key rather than threaded separately; the desktop's "Sync now" reads it to refuse a write over a folder it could not read, alongside the write guard already there. `PageSearchHit` widens to carry title and icon. **Room schema is v8** (see `tendril-spec.md` for why neither branch's number survived). Desktop behaviour is otherwise unchanged; `Tendril windows` compiles and CI now assembles all three projects. | §1, §6, §7 |
| 2026-09-05 | Anti-drift entry (§0): a large pass over `shared\` landed on `main` — full reasoning in `tendril-spec.md`'s Revision Log, summarised here because the shared core changed under both consumers. `SnapshotSyncOrchestrator` and `SnapshotEncryption` (both `shared/jvmCommon`) gained a per-folder PBKDF2 salt in `sync_meta.json`, a refusal to trust plaintext inside a folder that declares itself encrypted, and a refusal to overwrite an encrypted folder the supplied key cannot open. `PagesSyncEngine` (`shared/commonMain`) stopped orphaning pages whose parent arrives in a later batch, and now returns the records that *lost* a last-write-wins race so the orchestrator can preserve them beside the winner rather than discarding them. `PurgeRegistry` gained `PurgedKind.HABIT` and a `HabitDao`; `EntryScheduleCoordinator` gained a defaulted `onHabitRemoved` (a no-op on desktop, which has no alarms — §1). New shared domain files: `BlockOutline.kt` (one level of block nesting, drawn on both platforms) and `HabitSchedule.kt`. `CheckboxOnlyState` now takes an injected `appLockEnabled` predicate, defaulted false for desktop. `DesktopAppContainer` updated for the `PurgeRegistry` signature; no desktop behaviour changes otherwise. | §1, §6, §7 |
| 2026-08-30 (later still) | Milestone 3 (Workbench UI port), first slice implemented: theming, the 5-tab nav shell, and Pages/PageDetail/PageDatabase (the actual block editor) moved into `shared/src/commonMain` and now render on both Android and desktop from one implementation, not two. Calendar/Tasks & Habits/Road Map/Settings and the Canvas page kind stay Android-only this pass (out of scope — see §8). §3's gate is now genuinely triggered: desktop originates edits for the first time. Per §0's anti-drift rule, `tendril-spec.md`'s Revision Log has a matching entry. See §8. | §3, §6, §8 |
| 2026-09-04 | Anti-drift-rule entry — a consistency audit of the whole app against both specs. Summary only, since it touches `shared\`; full detail in `tendril-spec.md`'s entry of the same date. What lands in `shared\`: Room's `@Database` `version` bumped 5→6 (three Canvas tables had been added to `entities` without it, and Room throws on an identity-hash mismatch *before* `fallbackToDestructiveMigration` can act, so it crashed on open rather than recreating); `ResolveEntryUseCase` now advances a recurring TASK to the first occurrence **not already past**, keeping §6.2's phase but ending the case where resolving one late left it still overdue, logged a fabricated `EntryCompletion` per catch-up tap, and — via §9.7's past-alarm rule — silently unscheduled it; `SnapshotSyncOrchestrator.readAndMerge` returns a `SnapshotMergeResult` so a caller can refuse to write when the folder holds snapshots it cannot decrypt (it previously merged nothing and then overwrote the folder's only copy under the wrong key — §9.4.2 promises "unreadable," not "destroyed"); `PageFtsDao.search` joins `pages` to exclude trashed rows and had `snippet()`'s arguments off by one position; `HabitSnapshotRecord` carries the undo stash; `EntryDao.getAllSchedulableTasks` is now `getAllSchedulable` and covers EVENTs. **Desktop-side changes**: `Main.kt`'s Sync now honours the new merge result, wraps the call in try/finally per `SyncFileStore`'s documented throwing contract, surfaces the error in the sync bar, and masks the passphrase field. §3's block-level-merge gate is untouched and still open. | §1, §3, §7, §8 |
| 2026-09-04 (later same day) | Anti-drift-rule entry — recurring EVENT expansion. Full reasoning in `tendril-spec.md` §4.1.1 and its Revision Log entry of the same date; summary only, since it adds to `shared\`. New `com.tendril.app.domain.recurrence` package in `shared/src/commonMain`: `RecurrenceSpec` (a hand-rolled RFC5545 subset parser) and `EntryOccurrences` (stored rows plus a date range → one occurrence per covered day, honouring multi-day spans and §4.1's skip/override exception rows). `EntryDao` gains `getAllDated`, `getExceptionsOf` and `getAllExceptions`. **No desktop-side change**: Calendar is one of the four tabs still showing `NotAvailableOnDesktop` (§8's scope boundary), so nothing in `Tendril windows` consumes the expander yet — it is there for when Calendar is ported, and the desktop build only needs to keep compiling against the widened `EntryDao`. §3's block-level-merge gate is unaffected. | §1, §6, §8 |
| 2026-09-04 (last of the day) | Sync now runs from the Android lifecycle rather than only from the Settings button — new `SyncCoordinator`, full detail in `tendril-spec.md` §9.4. **No `shared\` change**, so §0's rule doesn't compel this entry; it is here because the change carries a decision *about desktop*: desktop stays on its explicit "Sync now" button, and the reason is not just the missing lifecycle callbacks but the session-only passphrase (§7) — at launch there is nothing to decrypt an encrypted folder with. Recorded in §1. | §1, §7 |
| 2026-09-04 (last, really) | Anti-drift-rule entry — `.tendril` exports now honour §9.4.2's encryption toggle; full detail in `tendril-spec.md` §9.4.2 and its Revision Log entry of the same date. The `shared\` part is one field: `TendrilManifest` gains `encrypted`, defaulted false so archives written before this still decode. Everything else is in `Tendril android`'s `PortableArchive`, which desktop has no counterpart to — desktop reads and writes the sync folder (§7) but has no portable export/import UI at all. When it gets one it inherits the same constraint noted for automatic sync in §1: the passphrase is session-only, so an export would be unencrypted unless one had been typed that session. | §1, §7 |

---

## 0. Relationship to `tendril-spec.md` — the anti-drift rule

**This file does not duplicate the shared domain/data/sync-merge model.** `Entry`, `RecurrenceRule`,
the Room schema, the to-do database sync mechanism, the JSON snapshot format, and
`PagesSyncEngine`'s whole-page LWW merge algorithm are documented once, in `tendril-spec.md`
(§4 Data Model, §5 To-do Databases, §9.4 Sync, §9.10 migration policy) — because that model
originated there and Android remains the primary client. This file references those section numbers
rather than re-explaining them. If you're reading this file looking for what a `shared\` type or
table actually means, go there first.

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
(`tendril-spec.md` §9.7), Jetpack Glance widgets (§8, §9.6), `BiometricPrompt` App Lock (§3.6), and
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
formula language out of scope elsewhere (`tendril-spec.md` §5.6, §10). A shared-core-plus-per-platform
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
  interface in `shared` (two methods, `onEntryChanged`/`onEntryRemoved`) with the existing Android
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
item under build-sequence step 3 (§6).

- **`WorkbenchCore`** (new, `shared`): a plain grouping class — `TendrilDatabase`,
  `DatabaseSyncManager`, `TemplateManager`, `ViewLockState`, `CheckboxOnlyState`,
  `ResolveEntryUseCase`, `EntryScheduleCoordinator`, `PageContentRepository` — the slice of
  Android's `AppContainer` the ported screens actually depend on. `AppContainer` itself stays
  Android-only (it also builds Context-only services with no desktop equivalent) and now just
  holds one `WorkbenchCore` instance; `Tendril windows`'s new `DesktopAppContainer` builds an
  equivalent one from its own database, with a no-op `EntryScheduleCoordinator` (no
  alarms/Calendar Provider on desktop, §1).
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
