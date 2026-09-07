# Build order

**Provenance and reading rule.** This is an imported planning artifact, not a verified
record. It was produced on 2026-09-07 by a multi-agent pass over the two spec files, the
audit, and the tree; its dependency edges and lane map have held up in use, but its
**per-item claims, sizes and "already done" list are `asserted`, not measured**. Do not
cite a row here as fact. Where `scope-decisions.md` rules on an item, that ruling wins.

It is committed because the alternative was worse: until 2026-09-08 this document existed
only in a session scratchpad outside version control, while `tendril-spec.md` §10 —
the one place a reader consults to learn what is not built — declined to say where it was.

**Known stale sections as of 2026-09-08:**

- Section 1's exclusion tables (C1–C6, X1–X6) are **superseded** by `scope-decisions.md`.
  Six of those items were ruled on and four reversed.
- Stage 0 and the quarantine half of Stage 2 are **done** — see commits `5d3d064` and
  `96eb02b`, and the Milestone 0 rows in both spec files.
- The test count in Stage 0's gate is stale. It said 261; the tree has 339.
- X6 (harden `tools/audit.py`) is scheduled **last**, in Stage 9. That is the wrong place.
  It is the detector for the entire class of drift this plan exists to clean up, its
  finding was confirmed by measurement on 2026-09-08, and it is sized S.

---

I read the inventory in full (212 raw items, six sources), then verified the load-bearing technical claims against the working tree, because the six agents disagree about the single fact the whole plan hangs on.

# TENDRIL BUILD ORDER

## 0. Three findings that change the plan's shape before anything else

**(a) The schema surface is roughly a sixth of what the brief assumes.** Inventory 0 lists 14 items as `needsSchemaChange:true`. I checked each against the entities. Room hashes *column types*, and every enum in this schema persists as its `.name` String through a `@TypeConverter` (`shared/src/commonMain/kotlin/com/tendril/app/data/Converters.kt:116-139`). Verified consequences:

- `PropertyType.RELATION` / `ROLLUP` / `FORMULA`, `ViewType.TIMELINE`, `BlockType.TABLE` — **zero schema cost.**
- `HabitFrequency` persists as `"count:unit"` (`Converters.kt:107-113`). The weekday/quota model is a string-encoding change with a back-compat parser — **zero schema cost.**
- `ViewFilter` and `visiblePropertyIds` are converter-serialized on `page_database_views` (`PageDatabaseView.kt:31-45`). AND/OR filter groups and the column chooser are **zero schema cost.**
- `Property.config` is already a free-form "type-specific config" string (`Property.kt:26-29`), and `PropertyValue.value` is a single string. A relation's edge list and a formula's expression both fit in columns that already exist — **zero schema cost**, no link table required.

The genuine schema movers, verified: `Reminder.uid` + `EntryCompletion.uid` (neither entity has one — `Reminder.kt:23-27`, `EntryCompletion.kt:17-23`, while `Entry`, `Habit`, `Block`, `Property`, `PageDatabase`, `PageDatabaseView` all do); `PageFtsEntry.blockId`; `PageDatabaseView.endDatePropertyId`; `Tag.parentTagId`; `Entry.googleCalendarId`; `PageDatabase` sync-to-habits binding columns + `Habit.sourceRowId`; a text-CRDT oplog table; Room 3.0. That is it.

**(b) So the answer to your question 4 is yes, but not for the batching reason.** The migration-policy switch must be step 1 — because it is the only way to add `Reminder.uid` and `EntryCompletion.uid` *without destroying the exact rows those columns exist to protect*. Under the current policy that item eats itself. And once the switch lands, batching stops being necessary: all five remaining additive columns are nullable and expressible as `@AutoMigration`, so each can ship its own version bump at no cost. **Batch-everything-into-one-migration is the correct strategy only if you keep `fallbackToDestructiveMigration`. Removing it is cheaper than planning around it.** Critically, the switch itself moves no hash: turning on `exportSchema` and deleting the fallback are both hash-neutral, and compiling with `exportSchema = true` emits `schemas/…/8.json` from the current entity set, which is exactly the shipped v8. It can land today, alone, at zero risk.

**(c) The universal file conflict is not code, it is the two spec files.** House convention gives every change a Revision Log row, and anything in `shared/` owes a same-day row in *both* `Tendril android/tendril-spec.md` and `Tendril windows/tendril-windows-spec.md`. Revision Logs are append-at-one-place tables. Two agents in two worktrees will conflict on the spec files even when their code does not overlap at all — which is precisely the merge nobody has designed. There are only two workable disciplines: each item writes its Revision Log row as the **last** step, on the integration branch after its code merges; or one integrator owns both spec files and writes every row. I recommend the first. Either way, **spec files are excluded from the file-conflict analysis below and handled by protocol, not by scheduling.**

One more constraint that shapes how items may be *split*: `tools/audit.py` check 3 fails on a top-level declaration nothing references, and check 2 fails on any `TODO`/`FIXME` in a comment. So no item may be split into "land the seam" then "land the caller" across two commits. The expect/actual preferences seam and the device id must each ship inside the same commit as their first real consumer.

---

## 1. NOT IN THE BUILD ORDER — you decide these per item

### (b) Contradicts a founding constraint

| # | Item | Why excluded | Constraint-respecting substitute |
|---|---|---|---|
| C1 | **Real-time collaboration** | Needs a server, accounts, and presence. §1 rules out the first two outright. Unanimous across all six agents. | None. This is a different product. |
| C2 | **Hosted template gallery** | A hosted catalogue needs a server and a publishing identity. | **Real and cheap:** export/import a single template as a `.tendril` file via `PortableArchive`, plus a bundled starter set. Needs no server. Sized S. I'd take the substitute. |
| C3 | **Embedded P2P / IPFS sync layer** | Reverses the founding "Syncthing replicates a folder" decision, and needs per-ABI native binaries. Also redundant — the existing design works. | None needed. |
| C4 | **Notion-style AI features** | *Contested.* Inv0, 1, 2, 4 all flag that the reasoning may not hold: the user's own key going directly to Anthropic needs no Tendril server and no Tendril account — the same shape as the already-shipped Google Calendar integration. The audit's sentence bundles it with (C1), which is where the tag came from. | I'd reclassify this as **deferred**, contingent on A1 landing first. Your call. |
| C5 | **Desktop home-screen widgets** | Windows has no surface a sideloaded JVM app can occupy. Tray popup / always-on-top window is new design, not a port. | Decline. |
| C6 | **Desktop system Calendar Provider registration** | No account-free Windows equivalent. | **Good degradation:** emit `.ics` into a folder. `RecurrenceSpec` already parses the RFC 5545 subset; only the emit direction is missing. ~M. |

### (c) Dependency problem — not a scope call

| # | Item | The actual blocker |
|---|---|---|
| X1 | **Character-level text CRDT** | Every mature implementation (Yjs, Automerge, Loro, diamond-types) is JS or Rust. A KMP consumer needs per-ABI native binaries for four Android ABIs plus a Windows JVM, under §9.2.1's 16 KB page-alignment requirement. Nothing in this project ships a native `.so`. **Do not let this block per-block merge (S10) — they are separate questions and all six agents that touched it said so.** |
| X2 | **Live embed blocks** | Acquired *after* the exclusion was written: desktop Milestone 3 moved `PageDetailScreen` into `shared/commonMain`, so every block type must now render on Android *and* desktop JVM. Compose Desktop has no WebView; JCEF/KCEF is the same per-ABI native problem. **A static preview card is buildable and small.** Decide which rendering you accept. |
| X3 | **Wallpaper *sampling* for widget contrast** | `WallpaperManager.getDrawable()` is restricted from Android 13 to the default launcher on `targetSdk 36`. Not buildable for a sideloaded app. **But `getWallpaperColors(FLAG_SYSTEM)` is API 27+, needs no permission, and returns `HINT_SUPPORTS_DARK_TEXT` — the platform literally answering the light/dark question.** Build the declared/hinted half (W1, W2 below); do not plan the sampled half. |
| X4 | **Desktop App Lock** | `BiometricPrompt` has no JVM equivalent. But Windows Hello via JNA is reachable and a passphrase gate is trivial. Inv3 pushed back on this being category (b) at all, and I agree — it is a dependency choice, not a constraint. |
| X5 | **Desktop Google Calendar** | Play Services' `AuthorizationClient` is Android-only. A desktop OAuth loopback flow works — **but it would produce a persistent refresh token, which is exactly what the Android design congratulates itself on never holding.** That is a deliberate decision, not a port side-effect. |

### Deliberately not items (seen, skipped — do not "build" these)

The `INTERVAL` property type stays out of the general New-Property picker by design (§4/§5.2.2). §5.4's "remind me N days before" computed property was dropped as already satisfied, not deferred. Building either is a regression. Also: `PropertyType`'s deferral of `INTERVAL` and §10's declined fractional/LexoRank order key — the latter matters for B13 below (`order` is an `Int`; sort by `(order, uid)`, do not reach for LexoRank).

### Already done — do not re-plan (five sources disagreed; I resolved against the code)

Nested block rendering (`BlockOutline.outlineOf` draws children, `PageDetailScreen.kt:257-268`, indent at `(24*depth).dp`) — the audit's #1 gap is **closed**, only stale comments remain. Self-relation guard (`excludePageId`). Recurrence filter/sort string form. Notion import notices. Plaintext trust. Per-folder PBKDF2 salt. Checkbox-only vs App Lock. `Habit.time`/`duration` wiring. Habit trash + `PurgedKind.HABIT`. Parent healing. Search debounce. Passphrase-loss warning. Widget live contrast readout.

**One conflict I resolved by reading the file:** inv0 claims the `EdgeEditor` stale-snapshot bug is fixed. It is not. `CanvasScreen.kt:185-193` passes callbacks, but `editingEdge` holds a captured `CanvasEdge` *value*, and every callback closes over it — so direction never cycles past one step. Inv4 and inv5 are correct. Kept as B2.

---

## 2. THE BUILD ORDER

Notation: **[XS]** <½d · **[S]** ½–1d · **[M]** 2–4d · **[L]** 1–2wk · **[XL]** 3wk+. "⇄" marks the file-lane an item locks.

### STAGE 0 — Truth-up · SERIAL, one agent, ~1 day

Every item here edits `tendril-spec.md`, `tendril-windows-spec.md` or `docs/audit-2026-09-04.md`. **They cannot be parallelised with each other or with anything else, because they are the same files everything else must append to.** Do them first: a build plan generated from a document asserting it has no open items will silently omit the items it has.

| ID | Item | Acceptance test |
|---|---|---|
| D1 [XS] | §10's "Still open … none" line is false — two dated contradictions sit under it | Grep the line; it now enumerates the open items or is deleted |
| D2 [XS] | The desktop-companion deferral shipped three milestones ago | The deferral bullet is struck through with a superseded-by date |
| D3 [XS] | The 2026-09-04 audit's top-ranked gap (nested blocks) is fixed and still listed open | Audit row 5.1 marked closed with the commit that closed it |
| D4 [XS] | `BlockOutline` KDoc still says the importer flattens nesting | Grep `BlockOutline.kt` for "flatten"; zero hits in the KDoc |
| D5 [XS] | `PurgedKind` KDoc says a Habit has no Delete-forever action; the enum on the next line has `HABIT` | Grep the KDoc; the claim is gone |
| D6 [XS] | §11's "Suggested next steps" and prototype count are stale | §11 names the current next step |
| D7 [S] | Canvas has no design record — no §4 data-model entry, not in §1's "Five pages" | A §3.x Canvas section exists in the house voice + a Revision Log row in both files |
| D8 [XS] | Decide `EntrySource.NOTION_IMPORT` (constructed nowhere) | Either the importer sets it, or the member is deleted, with the reasoning recorded |

---

### STAGE 1 — Migration policy · SERIAL, ALONE, nothing else in flight · ~3 days

| ID | Item | Depends on | Acceptance test |
|---|---|---|---|
| **S1** [M] | **Switch Room off destructive migration.** `exportSchema = true`, commit `schemas/…/8.json` as the baseline, delete `fallbackToDestructiveMigration(dropAllTables = true)`, add the §9.4.1 restore-from-snapshot fallback behind §5.5's confirm dialog | Stage 0 | `schemas/8.json` is committed; `TendrilDatabase.kt` has no `fallbackToDestructiveMigration`; app opens on an existing device with data intact; 261 tests still pass |

Why alone: any concurrent entity edit changes what `8.json` records, and the baseline would then describe a schema that never shipped. **No other work may touch `shared/…/data/` while this is in flight.** Zero user-visible change; zero risk; unblocks everything with a column in it.

---

### STAGE 2 — Data durability · mostly serial · ~1 week

The three items here are pure data-loss risk and outrank every feature in the backlog.

| ID | Item | Depends on | ⇄ Lane | Acceptance test |
|---|---|---|---|---|
| **S2** [M] | **Reminder + EntryCompletion reach the other device at all.** `uid` on both entities as **v9 via `@AutoMigration(8→9)`**, two new snapshot record types, mappers both directions, merge coverage, and both tables added to `PortableArchive` | S1 | SYNC | Create a reminder on device A, sync, open device B — the reminder is there and fires. `.tendril` export unzips with a `reminders` and `entry_completions` array |
| **S3** [S] | **Test the restore fallback.** It has never been wired or exercised | S2 | SYNC | A deliberately-broken migration on a test device recovers a populated DB from the newest snapshot |
| **S4** [M] | **Images are in no snapshot and no portable export.** Add an `images/` directory to the archive and an image file-kind to the snapshot folder | S2 (same files) | SYNC | Import a Notion page with an image on A, sync, open on B — the image renders (after P2) / the file exists in private storage |

**S2, S3, S4 all rewrite `SnapshotRecords.kt`, `SnapshotMappers.kt`, `SnapshotSyncOrchestrator.kt` and `PortableArchive.kt`. They are strictly serial with each other and with S9/E1/E2/E3 below.**

**Runs in parallel with Stage 2 (verified no file overlap):**

| Lane | Items | Files locked |
|---|---|---|
| **CANVAS** | B1 [S] canvas edge tap unreachable → B2 [XS] EdgeEditor stale snapshot → T14 [M] node resize | `ui/canvas/CanvasScreen.kt`, `CanvasViewModel.kt` |
| **NOTIF** | B4 [XS] stock notification icon | 3 receivers + `res/drawable/` + `tools/audit.py` |
| **DESKTOP-BUG** | B8 [XS] sync error rendered twice → B12 [XS] still on the M1 dev DB path | `Tendril windows/…/Main.kt` |
| **THEME** | B16 [S] `TendrilTheme` remaps only a ColorScheme subset, so NavigationBar shows Material purple | `ui/theme/Theme.kt`, `Palette.kt` |
| **NAV** | B10 [S] switching tabs drops the page pushed on the previous tab · B11 [XS] desktop can create a Canvas page it cannot open | `ui/nav/WorkbenchNavState.kt`, `WorkbenchScaffold.kt` |

B1 must precede B2 (nothing can reach `EdgeEditor` until a tap selects an edge, so B2 is unverifiable alone). B1+B2 must precede the desktop Canvas port (DK5), or the port duplicates both bugs into shared code.

**Acceptance tests:** B1 — tapping within threshold of an arrow opens EdgeEditor, and a one-finger drag on empty canvas still pans. B2 — cycling direction twice returns to the original, and setting a label does not revert the direction. B4 — the notification shade shows the app glyph, not the stock bell. B8 — a wrong-passphrase message appears once. B12 — the desktop DB is under `%LOCALAPPDATA%`, with a migration note. B16 — the nav bar renders in theme colour on both platforms. B10 — push a page on Pages, switch to Calendar and back; the page is still there. B11 — desktop's New sheet either offers Canvas and opens it, or does not offer it.

---

### STAGE 3 — The editor/database serial lane opens · ~1 week

**This is the plan's hard bottleneck and worth stating plainly: `PageDetailScreen.kt` and `PageDatabaseScreen.kt` are touched by roughly 25 items across the whole backlog. They are a single-threaded resource for the plan's entire duration.** Only one agent may hold them at a time. Everything below marked ⇄EDITOR or ⇄DB is in that one queue.

Start it with the fixes that every later item would otherwise have to work around:

| ID | Item | ⇄ | Acceptance test |
|---|---|---|---|
| **B3** [S] | Database cell and row-property text fields drop characters when a Room write lands mid-typing. The block field already has the `lastWrittenContent` guard; the two cell fields do not | EDITOR+DB | Type continuously into a cell for 5s while sync runs; no dropped characters |
| **B13** [M] | Block ordering has no deterministic tiebreak; two blocks can share an `order`. Sort by `(order, uid)` at all eight sort sites — **not** a fractional key (§10 declined it, `order` is an `Int`) | EDITOR | Two blocks forced to the same `order` render in the same sequence on both devices |
| **B7** [S] | Numbered list items show page position, not list position (`block.order + 1`) | EDITOR | A list under a heading starts at "1."; deleting item 2 renumbers 3→2 |
| **B6** [XS] | A bound Recurrence cell displays "P7D" while filtering on "1:WEEK" | DB | The cell reads "Every week" |

Parallel lanes still running: ENTRIES (see Stage 4), DESKTOP prep (DK1).

---

### STAGE 4 — Independent lanes, genuinely parallel · ~4 weeks, 3–4 agents

These four lanes have **no file overlap with each other** and none with the EDITOR/DB queue. This is the only stretch of the plan with real parallelism.

**Lane ENTRIES** ⇄ `ui/taskshabits/`, `ui/calendar/`, `notifications/`, `domain/HabitSchedule.kt`, `EntryOccurrences.kt`

| ID | Item | Depends on | Acceptance test |
|---|---|---|---|
| B5 [S] | **Every habit reminder dies at reboot and never comes back.** `reconcileAlarms` walks `entryDao.getAllSchedulable()` and never touches habits; `EntryScheduleCoordinator` has `onHabitRemoved` with no added/changed counterpart, so a habit arriving via merge is never armed | — | Set a habit reminder, reboot, wait — it fires. Create a habit on A, sync to B — it fires on B |
| T1 [M] | **Tasks, Events and Habits cannot be edited after creation.** The trunk for three others | B5 | Long-press a task → edit sheet → change title/date/repeat → it persists |
| T2 [M] | Per-occurrence exceptions (skip / override this one) — read everywhere, written nowhere | T1 | Skip one occurrence of a weekly task; the others survive and the skip syncs |
| T3 [M] | Timed, multi-day and repeating Events cannot be created in the app | T1 | Create a 3-day event with an RRULE from the Calendar; it spans correctly and repeats |
| T4 [XS] | `EntryStatus.SKIPPED` is only reachable from a notification | T1 | Skip is in the task action menu |
| T5 [S] | "Custom" (interval + unit) recurrence missing from the standalone Task dialog — §6.2's own reference case ("every two months") is unreachable | T1 | Add a task repeating every 2 months from the dialog |
| T6 [M] | Widen the RRULE subset (BYSETPOS, BYWEEKNO, BYYEARDAY); unsupported parts collapse a series to its first occurrence | — | New unit tests in `RecurrenceExpansionTest` for "last Friday of the month" pass |
| T7 [L] | **Habits cannot express "3 times a week" or specific weekdays.** Encoding-only (`"count:unit"` string) — **no schema change** — but the streak semantics are the real work: §6.1's "missing an instance creates no backlog" must still hold under a quota model | B5 | A 3×/week habit shows a streak after 3 check-ins in one week and does not break on a missed day |
| T8 [S] | Habit reminder has check-off but no snooze/dismiss | B5 | Snooze re-fires 10 min later without colliding in the request-code region |
| T9 [S] | `entry_completions` is write-only — no history screen | S2 | A task's detail shows its completion history, synced from the other device |
| T10 [M] | Calendar Week's Cards/Grid hour-grid toggle (persist in a DataStore preference, not Room) | — | Toggle to Grid; timed entries land at their hour; survives restart |
| T11 [S] | Calendar's "Show Habits" toggle — reuse `HabitSchedule`, don't write a second cadence evaluator | — | Toggle on; habits due today appear on the day cell |
| T12 [S] | Battery-optimization exemption prompt | — | Settings row opens the system dialog and reflects the granted state |
| T13 [S] | Batched summary notification after a bulk import creates overdue Entries (deferral expired — both bulk creators shipped) | — | Import 50 overdue rows; one summary notification, not 50 |
| G1 [S] | Disconnecting Google Calendar does not revoke the grant | — | Disconnect, then check the Google account permissions page — the grant is gone |
| B15 [S] | Push unconditionally overwrites Google's copy rather than comparing timestamps | — | Edit an event in Google, then sync — the Google edit survives |
| B14 [S] | The kind↔recurrence-rule invariant (`Fixed`⇒EVENT, `Elastic`⇒TASK) is a convention no code enforces | — | A unit test asserting the invariant at every construction site passes |

**Lane WIDGETS** ⇄ `widget/` (no overlap with anything)

| ID | Item | Acceptance test |
|---|---|---|
| W1 [S] | Contrast audit against a real wallpaper — **build the `getWallpaperColors` hinted variant, not the sampled one** (X3) | The config screen's contrast readout changes when the wallpaper changes |
| W2 [S] | Suggest widget light/dark from wallpaper darkness. **Must be a non-binding hint** — §8.7 decided with a scored comparison that theme/mode fields are "absent from that screen entirely"; a per-widget override reverses an argued decision | A hint chip appears; no theme/mode field is added to the config screen |
| W3 [S] | App Lock does not hide widget content | Lock the app; the agenda widget shows a placeholder, not entries |

W1 and W2 share one wallpaper-luminance source and touch the same two files — **build them as one pass, not two.** Note also that the audit tool exists twice (`noema-widgets-audit.html` and `WidgetColor.kt`, transcribed verbatim); change both together or they drift.

**Lane SYNC-INFRA** ⇄ `sync/` (serial *after* Stage 2, and serial within itself)

| ID | Item | Depends on | Acceptance test |
|---|---|---|---|
| S5 [M] | **Nothing in the app surfaces a preserved `.tendril-lost-` version.** This is the *instrument* that produces the evidence the windows spec's gate says must exist before per-block merge is judged | S2 | Force a conflict; Settings lists the lost version and can restore it |
| S6 [M] | The 2-second per-page snapshot debounce (§9.4: "still not implemented, and deliberately not faked"). Needs a per-page write mode on the orchestrator and a seam letting shared/ editor code request a write without knowing about SAF | S2 | Editing one page writes one page file, not a whole-database rewrite |
| S7 [S] | No periodic background sync — WorkManager is a dependency with zero usages. Ride alarm reconciliation on the same job | S2 | Sync happens without opening the app; alarms reconcile periodically |
| E1 [M] | Export is all-or-nothing — no per-Page/Database picker | S2 | Export one page; the archive contains that page and nothing else |
| E2 [M] | Manifest-driven picker on Import — choose what to bring in | E1 | Import shows a checklist of what the archive contains |
| E3 [S] | A per-export key, so sharing one page doesn't hand over the sync passphrase | E1 | An exported page opens with its own key; the sync passphrase does not unlock it |

**Lane DESKTOP-PREP** ⇄ `composeResources/strings.xml`, Android `strings.xml`

| ID | Item | Acceptance test |
|---|---|---|
| DK1 [M] | Move each unported screen's strings from Android `R.string` to Compose Multiplatform `Res.string`. **This gates all five screen ports and costs nothing else.** | Every string the five screens use resolves from `shared/…/composeResources` |

---

### STAGE 5 — Editor and database features · SERIAL queue, ~10 weeks

One agent, one queue, in this order. Everything here is ⇄EDITOR or ⇄DB.

| ID | Item | Depends on | Schema | Acceptance test |
|---|---|---|---|---|
| P1 [XS] | `Block.codeLanguage` is imported and stored, shown and settable nowhere | B3 | — | A code block shows its language and offers a picker |
| P2 [M] | **IMAGE blocks are offered, imported and stored — and never drawn.** §3.1.1 lists Image as a v1 block type; no picker sets `imagePath`, no render branch exists, and `GalleryBody` draws "No image" when there is none and *nothing* when there is | B3, S4 | — | Insert an image from the picker; it renders on Android and desktop, and syncs |
| P3 [XS] | `Block.calloutColor` and callout icon are stored, editable nowhere | B3 | — | A colour swatch row in the block action sheet changes the callout |
| P8 [S] | **Bold cannot be un-bolded** — the toolbar only ever appends spans | B3 | — | Select bold text, tap B, it becomes plain |
| P10 [S] | `Page.icon` can never be set | B3 | — | Set an emoji in the page title bar; it shows in the page list and syncs |
| P12 [S] | A search result opens the page but does not scroll or highlight the match — **§3.1.1 and §3.1.7 both assert it does** | B13 | — | Tap a result; the page opens scrolled to the match, highlighted |
| P9 [M] | Links and inline @mentions are styled but inert; links cannot be created | P8 | — | Tap a link, it opens; tap a mention, it navigates; a link can be created |
| P16 [S] | Unlinked mentions in the backlinks panel | P12 | — | A page mentioning this title without a link appears under "Unlinked" |
| P11 [M] | Sub-pages: `Page.parentId` exists, the tree does not | — | — | A page shows its children; the sidebar nests them |
| P4 [M] | A native Table block — imported tables degrade to a Code block | P6 | — | A Notion table imports as a table and renders as one |
| P5 [S] | Imported Notion callouts stay raw HTML | — | — | `NotionImportParsingTest` asserts a callout becomes `BlockType.CALLOUT` |
| P7 [M] | Drag-handle block reordering (spec) vs Move up/down (built) | B13 | — | Drag a block to a new position on a scrolling list |
| P17 [M] | Template variables / placeholders | — | — | A template with `{{date}}` expands on instantiation |
| P13 [M] | **Block-level FTS granularity** | P12 | **yes** — `blockId` on `PageFtsEntry`; FTS4 virtual-table redefinition needs a hand-written `Migration` that drops and rebuilds (content is derived from `blocks`, so it is safe) | A search hit names the block, and P12's scroll lands on it |
| P14 [M] | App-wide search folding in Entries and Habits | P13 | **yes** — a second FTS table | Searching finds a task by title |
| P15 [M] | Command palette / omnibox | P14 | — | Ctrl+K opens; typing runs an action, not just finds a page |
| P18 [S] | Tag hierarchy / nesting | — | **yes** — `parentTagId` on `tags`, `@AutoMigration` | A child tag nests under its parent and filters transitively |
| DB8 [S] | `visiblePropertyIds` — the column chooser that does not exist | B3 | — | Hide a column in a Table view; it stays hidden after restart and syncs |
| DB9 [S] | Per-database "find in table" | B3 | — | Typing filters rows in place |
| DB10 [S] | URL / EMAIL / PHONE property types behave exactly like TEXT | B3 | — | A URL cell offers a URL keyboard and tapping opens it |
| DB11 [S] | "Skipped" unreachable from a row's checkbox. **Must route through `ResolveEntryUseCase`** (§5.2's centralisation rule) — never write the Entry directly | T4 | — | A row checkbox offers Skipped, and the linked Entry shows SKIPPED |
| DB7 [M] | AND/OR filter groups on database views. Converter-serialized — **no schema change**, but the serialized shape must stay back-compatible | — | — | A view filtered on "A and (B or C)" persists and syncs to an older-format reader without crashing |
| DB6 [L] | Timeline / Gantt view | DB7 | **yes** — `endDatePropertyId`, `@AutoMigration` | Rows with start+end render as draggable bars on a time axis |
| DB14 [M] | A "Relate to…" edge is permanent once created. The delete must record a `PurgedKind` tombstone or it resurrects on merge | — | — | Delete a relation on A; it stays deleted on B after sync |
| **DB1** [L] | **Relation property type** — a `PropertyType.RELATION` member (zero schema), edges encoded in the existing `PropertyValue.value`, plus auto-creation of the reverse property so the link is genuinely two-way | DB14 | — | Relate row X to row Y; Y shows X in its reverse property; both sync |
| DB2 [M] | **Rollup** over a relation (count/sum/min/max/earliest/latest/show-original). **Compute on read — caching a result in a column is the one trap that would turn this into a schema change** | DB1 | — | A rollup column shows the sum of a related database's number column and updates when the source changes |
| DB3 [L] | **Formula language** — lexer, parser, evaluator, dependency graph, cycle detection. Expression lives in the existing `Property.config`. Almost all pure, testable logic | DB2 | — | `prop("A") + prop("B")` evaluates in a cell; a cycle is reported, not hung |
| DB4 [M] | Per-column Summary footer + "Explain this value" derivation trace | DB3 | — | A column footer shows sum/avg/empty; tapping a computed cell shows its inputs |
| DB5 [S] | Formula/rollup-driven view grouping | DB3 | — | A Board groups by a formula result |
| DB12 [L] | Database-driven "Sync to Habits" | T7, DB1 | **yes** — binding columns on `page_databases` + `Habit.sourceRowId`, `@AutoMigration` | Bind a database to Habits; a row creates a habit and unbinding leaves it |
| DB13 [M] | No way back out of a confirmed property-type conversion or deletion | DB1 | **yes** if a stash table is chosen (or extend Trash) | Convert TEXT→NUMBER, undo, the original strings are back |
| DB15 [S] | Notion import pre-creates a matching Board/Gallery/Calendar view — **verify a real export actually carries the metadata first** | — | — | Importing a Notion board arrives as a Board view, not a bare Table |
| B18 [M] | **One Trash list, and restoring a Row restores its linked Entry.** There are *three* trash sheets, not two (the spec's own note is wrong). A superseded branch had a merged sheet that was deliberately dropped in the PR #1 integration because it purged without recording a tombstone — **do not resurrect it; rebuild on `PurgeRegistry`** | — | — | One Trash list shows pages, rows, entries and habits; restoring a row restores its entry |
| P19 [M] | Inline databases inside a page body — mostly a refactor of `PageDatabaseScreen` into an embeddable form; `Block.mentionedPageId` already models the reference | DB8 | — | A database renders inside a page and is editable there |
| P20 [M] | Nested-page canvases. Needs `CanvasScreen` in `shared/` (DK5) first, and a spec entry reversing `PageCanvas.kt`'s recorded "a canvas is never embedded content" | DK5 | — | A canvas renders inside a page body |
| P22 [S] | Streak / gamification on the daily journal. **This is a design exclusion, not a time deferral** — confirm you want it | — | — | The journal shows a streak count |
| P23 [M] | Onboarding wizard and pre-seeded sample content | — | — | A fresh install shows sample content and a first-run walkthrough |

---

### STAGE 6 — Desktop parity · partly parallel · ~11 weeks

**Inv3's finding, verified by reading every import of all five unported screens, contradicts the spec's own stated reason for the boundary.** `RoadMapScreen` (579 lines) + VM, `CanvasScreen` (556) + VM, `TasksHabitsScreen` (365) + VM + `AddDialogs`, and both trash sheets contain **zero** `android.*` / `androidx.activity` / `androidx.core` / `androidx.biometric` imports. `CalendarScreen`'s only Android APIs are three lines in one bottom sheet. The real universal blocker is `androidx.compose.ui.res.stringResource(R.string.X)` — which is DK1. Also verified: `java.time` compiles from `commonMain` today (`HabitSchedule.kt`, `DatePickerDates.kt` and two ViewModels all import it and the project builds), so no kotlinx-datetime migration is needed.

**Hard scheduling rule: a desktop port moves an Android screen's file into `shared/`. It cannot run concurrently with feature work on that screen.** So DK6 (Tasks & Habits) must come *after* Lane ENTRIES finishes T1–T9, and DK5 (Canvas) *after* B1/B2/T14. Otherwise the port merges against a file that moved.

| ID | Item | Depends on | ⇄ | Acceptance test |
|---|---|---|---|---|
| DK4 [M] | Port Road Map | DK1 | windows/Main | Road Map opens on Windows and edits persist |
| DK5 [M] | Port Canvas | DK1, B1, B2, T14 | windows/Main, canvas | A canvas opens on Windows; pan/zoom/edge-edit work with a mouse |
| DK6 [L] | Port Tasks & Habits (~1060 lines; the alarm seam is ~10 lines of interface) | DK1, T1–T9 | windows/Main, taskshabits | Tasks & Habits opens on Windows and check-in works |
| DK7 [M] | Port Calendar (its Google half is separately gated) | DK1 | windows/Main, calendar | Calendar opens on Windows |
| **DK2** [M] | **A cross-platform preferences abstraction.** Desktop has no persisted settings at all. `AppLockPreferences`, `ThemePreferences`, `SyncStatusPreferences`, `SecretStore`, `CalendarProviderPreferences`, `GoogleCalendarPreferences` and `WidgetPrefs` are all Android-only. **Must ship in the same commit as its first consumer — `audit.py` check 3 fails on an unreferenced declaration** | DK1 | shared/storage | Desktop theme choice survives a restart |
| **DK3** [M] | **Secure secret storage on desktop.** The true bottleneck: auto-sync, re-key, at-rest encryption, the Anthropic key and encrypted export all sit behind it and behind nothing else | DK2 | shared/storage | The sync passphrase survives a restart, DPAPI-protected |
| DK9 [M] | Portable `.tendril` export/import on desktop | DK3 | sync | Export from Windows, import on Android |
| DK10 [M] | Notion import on desktop (~400 of ~800 lines move unchanged) | DK9 | notionimport | A Notion zip imports on Windows |
| DK11 [M] | **Automatic sync on desktop.** Contradicts a *recorded decision* (§9.4: the desktop passphrase is session-only, so an automatic pass "would either do nothing or … do harm") — a build step must **answer** that reason, not delete it: arm the pass only when the folder is unencrypted or a passphrase was entered this session. **Plan with S6, or the orchestrator gets reworked twice** | DK3, S6, S7 | sync, windows/Main | A change on Android appears on Windows without pressing Sync now |
| DK14 [M] | App Lock on desktop (see X4 — pick passphrase gate, or Windows Hello via JNA) | DK3 | windows | The app locks and unlocks on Windows |
| DK8 [L] | **Port Settings** (~640 lines) — the one screen where "Android-integration-heavy" is genuinely true | DK1, DK2, DK3, DK9, DK10, DK14 | windows/Main, settings | Every Settings section renders and works on Windows |
| DK12 [S] | Surface the already-shared re-key operation on desktop | DK3, DK8 | windows | Re-key from Windows; Android reads the folder with the new passphrase |
| DK13 [L] | Reminders and notifications on desktop — needs a real decision about tray/background residency | DK6 | windows, notifications | A reminder fires on Windows while the app is in the tray |
| DK18 [M] | Desktop window: no remembered size, no menu bar, no keyboard Back | DK2 | windows/Main | Window size survives a restart; Alt+← goes back |
| DK20 [M] | **Desktop has no automated verification of any kind.** Pick the source-set, write a first batch | DK2 | build | `./gradlew --offline :desktop:test` runs and passes |
| DK16 [M] | `.ics` emit as the Calendar Provider degradation (see C6) | DK7 | calendarprovider | Outlook subscribes to the emitted `.ics` and shows entries |
| DK19 [S] | **Windows packaging / installer** — packaging an app with five placeholder screens ships the placeholders, so this is genuinely last | DK4–DK8, B12 | build | A `.msi` installs and launches on a clean Windows box |

---

### STAGE 7 — Merge architecture · SERIAL, high risk · ~4 weeks

| ID | Item | Depends on | Acceptance test |
|---|---|---|---|
| S9 [S] | Purge tombstones are never garbage-collected | S8 | A tombstone older than the oldest device's last-sync is gone from the folder |
| S8 [S] | **A stable per-install device id.** §10 declined it on 2026-09-06 precisely because nothing needed one, and its own rule says whichever feature first needs it should add it. **Verified: it does not need a Room row** — an expect/actual preference is enough. **Must ship with its first consumer (S9 or S10), never alone** | DK2 | Two installs report different ids that survive restart |
| **S10** [L] | **Move page merge from whole-page LWW to per-block.** Verified cheaper than every source assumed: `BlockSnapshotRecord` already carries per-block `updatedAt`, so **no schema change and no snapshot-format change is required.** The prerequisites are real, not thematic: B13 because per-block merge makes duplicate `order` values routine rather than rare, and S5 because it is the instrument producing the evidence the windows-spec gate requires before this is built | B13, S5, S8 | Edit block 1 on device A and block 2 on device B offline; sync; both edits survive. `PageMergeTest` gains cases for it |

---

### STAGE 8 — Third-party integrations · parallel · ~4 weeks

**No HTTP library exists and none is needed** — `GoogleCalendarSyncEngine` uses raw `HttpURLConnection` + `kotlinx.serialization`, `INTERNET` is declared, and §7.4 records a deliberate zero-non-AndroidX-dependency posture. Follow that idiom; do not add OkHttp/Ktor.

| ID | Item | Depends on | Acceptance test |
|---|---|---|---|
| A1 [M] | **Claude-API-generated in-page mind map.** The Anthropic key path is fully built and has zero consumers: `SecretStore` encrypts `anthropic_api_key`, `SettingsScreen` renders `AnthropicKeySection`, and nothing reads it. **This is also the first test of §3.5's "zero network calls until this key is toggled on" promise.** Note the honest caveat from inv4: §3.5 deferred this key "to a future in-page mind-map generator", and that generator shipped as Canvas *with no AI in it* — so the feature needs a product decision about what it does before it can be estimated. Removing the Settings section is as legitimate an answer as building this | DK5 (for shared Canvas) | With a key set, a page generates a Canvas; with no key, zero network calls (verified by a proxy) |
| N1 [L] | Notion API import as a second, richer path. Uses the *user's own* integration token — same shape as Google Calendar, no Tendril account. **Lands Android-only unless deliberately moved** | DB1 (typed relations), DK10 (if desktop) | A Notion workspace imports with relations as RELATION properties, not text |
| G2 [M] | Google Calendar sync is EVENT-only and `primary`-only | S2 | **yes** — `Entry.googleCalendarId`, `@AutoMigration`; must ride the snapshot record the way `googleEventId` does | A second calendar can be picked and syncs |

---

### STAGE 9 — Decide, don't schedule

| ID | Item | Note |
|---|---|---|
| S11 [L] | Room 3.0 (`androidx.room3`) migration — mechanical but repo-wide; changes DAO signatures. Sequence either before all the desktop ports or well after, never during |
| P21 [M] | Formal accessibility audit — needs a real device; the spec's own stated payoff is near zero for a single-user unlisted build |
| P24 [XL] | Ledger / Deck / Atelier navigation paradigms — mutually exclusive with Workbench, and §2.3's theming is scoped to Workbench only. This is a product exploration, not a backlog item |
| X6 [S] | **Harden `tools/audit.py`.** Its dead-declaration check anchors at column 0, so no entity *field*, member function or enum member is ever checked — which is where every "declared but unused" finding in this backlog lives (`Block.calloutColor`, `CanvasNode.width/height`, `visiblePropertyIds`, `Page.icon`, `Entry.endDate/endTime/originalEntryId`). Its unused-DAO check also has a false negative: `EntryCompletionDao.observeForEntry` is masked by `ReminderDao.observeForEntry`. **A field-with-no-non-mapper-reader check is the highest-yield addition — but it must discount `SnapshotMappers`/`PagesSyncEngine`, which reference every dead field twice and make everything look live** |

---

## 3. File-conflict map — what is actually parallel-safe

Six lanes, defined by the files they lock. Two items may run concurrently **iff** they are in different lanes.

| Lane | Files it owns | Notes |
|---|---|---|
| **SYNC** | `sync/SnapshotRecords.kt`, `SnapshotMappers.kt`, `PagesSyncEngine.kt`, `PageSnapshotRecords.kt`, `SnapshotSyncOrchestrator.kt`, `PurgeRegistry.kt`, `PortableArchive.kt`, `TendrilDatabase.kt` | **Strictly serial internally.** S1→S2→S3/S4→S5/S6/S7→E1→E2/E3→S8→S9→S10. Nine items, one agent, the whole plan long |
| **EDITOR** | `ui/pages/PageDetailScreen.kt`, `PageDetailViewModel.kt`, `domain/BlockOutline.kt`, `data/page/Block.kt`, `SpanVisualTransformation.kt`, `PageFts.kt`, `PageContentRepository.kt` | **Strictly serial internally** — ~15 items |
| **DB** | `ui/pages/PageDatabaseScreen.kt`, `PageDatabaseViewModel.kt`, `pagedatabase/*`, `DatabaseSyncManager.kt` | **Strictly serial internally** — ~15 items. **Overlaps EDITOR on `PageDetailScreen.kt`** (B3, DB10, P19, row properties), so EDITOR and DB are *not* fully independent: they need a shared queue for those four items or a handoff protocol |
| **ENTRIES** | `ui/taskshabits/`, `ui/calendar/`, `notifications/`, `data/habit/`, `domain/HabitSchedule.kt`, `EntryOccurrences.kt`, `googlecalendar/` | Serial internally after B5→T1; T6/T10/T11/T12/G1/B15 are independent sub-lanes |
| **CANVAS** | `ui/canvas/`, `ui/roadmap/`, `data/page/PageRelation.kt`, `data/canvas/` | Small; B1→B2→T14, plus DB14 and B9 |
| **DESKTOP** | `Tendril windows/src/`, `shared/jvmCommon/`, `composeResources/strings.xml`, `ui/nav/WorkbenchScaffold.kt` | Serial internally after DK1; DK2→DK3 is the bottleneck. **Consumes ENTRIES and CANVAS files at port time — cannot overlap them** |

**Cross-lane collisions to schedule around, concretely:**
- `WorkbenchScaffold.kt` — B10, B11, DK4–DK8, DK18, P15. Six lanes want it. It should be owned by DESKTOP for the plan's duration, with other lanes filing requests.
- `Tendril windows/…/Main.kt` — B8, B11, B12, DK4–DK8, DK11, DK18, DK19. Same answer.
- `AlarmScheduler.kt` — B5, T8, T13, S7. Serial within ENTRIES.
- `notionimport/` — P5, DB15, N1, DK10. Serial.
- `TendrilDatabase.kt` — S1, S2, P13, P14, P18, DB6, DB12, G2, S11. Every schema item. **This file is the reason the SYNC lane must own all schema changes**, even ones whose feature lives in another lane: DB6's `endDatePropertyId` is a DB-lane feature but a SYNC-lane commit.
- **Both spec files** — every item. Handled by protocol (Revision Log row written last, on the integration branch).

**Genuinely parallel-safe stages: Stage 2's four bug lanes (5 concurrent agents), and Stage 4 (4 concurrent lanes).** Everything else is 2–3 lanes at best. Stage 1 and Stage 7 are strictly single-agent.

---

## 4. Honest size

**Roughly 250–270 engineer-days.** Not six months — closer to **12 months solo**, or **7–8 months with the parallelism the file topology actually permits**, which is about three concurrent agents for most of the plan and one for two of the stages.

The breakdown, by lane:

| Lane | Days |
|---|---|
| Docs + migration policy + data durability (Stages 0–2) | 12 |
| Bug batch (18 items) | 14 |
| Editor features (P1–P23) | 45 |
| Database features (DB1–DB15) | 52 — of which **DB1+DB2+DB3+DB4 alone are 30**, the largest single subsystem in the backlog |
| Entries / habits / calendar (T1–T14) | 32 |
| Sync infrastructure + export (S5–S10, E1–E3) | 30 |
| Widgets + Google + Notion + Anthropic | 22 |
| Desktop parity (DK1–DK20) | 55 |

The two things that would most change that number: **DB3 (formula language) is a compiler in miniature** — lexer, parser, evaluator, dependency graph, cycle detection, error surfacing — and it is the single item most worth asking whether you actually want, since §5.4's own words are "real feature, real complexity … not planned for an early build" and its downstream dependents (DB4, DB5) are small. And **desktop parity is a fifth of the total** for a companion app; if Windows only ever needs to read and edit Pages, DK6/DK7/DK8/DK13 alone give back ~35 days.

Nothing in the buildable list requires a server, an account, or a native binary. The three items that do are in section 1 and are not scheduled.