# Habit planner in the Habits tab — decision record

**Status: Phase 0 of `docs/plans/2026-09-29-habit-planner.md`, written 2026-09-29. D1–D14 decided
by the person before the plan; P1–P5 confirmed by the person on 2026-09-29, at the start of Phase 0,
as proposed. The model sketch and the answers to Q1–Q5 were approved by the person at the Phase 0
review, 2026-09-29, as proposed below; the "proposed" headings are kept as they were reviewed.
No code is written.** Every claim about the tree names the file it was read from; every claim about the
reference planner names the file under `C:\Users\User\Downloads\Builds\Habit planning system`
(read-only here, "the reference" below).

## Baseline (the gate on `main` at 1e2c60c, branch `habit-planner` cut from it)

| Command | Result |
|---|---|
| `./gradlew --offline :app:assembleDebug :app:testDebugUnitTest` | green; `testDebugUnitTest` **FROM-CACHE** — it did not execute. The XML: 1057 tests in 135 files, 0 failures, stamped 20:35. #149 and #150 changed documents only, so the cached result is this tree's. |
| `shared: ./gradlew --offline build` | green (no test XML of its own — `shared/` is tested from the app module) |
| `Tendril windows: ./gradlew --offline build` | green; XML 12 tests in 3 files, 0 failures |
| `python tools/audit.py && python -m pytest -q tools/tests` | PASS, no findings; 88 passed |

## Decisions (D1–D14, from the plan, not reopened)

| # | Decision |
|---|---|
| D1 | Extend `Habit`: a schedule is *since last check-in* (today's `HabitFrequency`) or *on the calendar* (the planner's rules). One Habits list. |
| D2 | Calendar habits get check-ins and presence; §0.5.2 — presence, never absence. |
| D3 | No priority on habits; order inside a block is manual (↑/↓). |
| D4 | Only the two structural flags — blocks overlapping, a set time outside every block — as neutral notes. No daily limit. |
| D5 | "X times a week": the app suggests days, the person confirms (§0.6.5). |
| D6 | Time blocks hold habits only; shown in the Habits tab and the Habits widgets (widgets designed later). Plan mode: mockup only. |
| D7 | The planner's screens live inside the Habits tab. |
| D8 | Areas are Labels. |
| D9 | One title per habit; app language (English / Italian) is a new general Setting. |
| D10 | The six scopes of "Apply this change to…" for habits now, tasks later. |
| D11 | Exports: Markdown first, A4 PDF second. |
| D12 | The standalone planner retires only after the gate and the walks pass, then its data is imported. |
| D13 | Mockup → critique → build → walk, phone and desktop. |
| D14 | A new branch from an up-to-date `main`; the person commits. |

## Derived decisions (P1–P5) — confirmed 2026-09-29

| # | Confirmed |
|---|---|
| P1 | Deleting a habit is **Move to Trash** (§5.5.1); purge follows Tendril's Trash. The planner's "type DELETE" is dropped. A *scoped* removal ("remove this entry", "from now on") is a schedule edit, not a Trash — see the sketch. |
| P2 | Tendril's existing undo patterns for habits (§8.1.1 undo check-in, the snackbar); no page-wide 5-step undo/redo. |
| P3 | **One check-in per occurrence** for a habit several times a day, on the counting-habit precedent (§0.10 item 3); the day's presence counts occurrences done. |
| P4 | **At most one Label per habit** — a nullable reference, not a join table. |
| P5 | Removing one time of an every-N-hours series changes that entry only. |

## What the reading found that the plan's sketch did not have

Read against the reference's `planner/assets/engine.js`, `habits/_template.toml`, `planner.toml`
and all ten `golden/*.json` models (field counts from a script over the models, not by eye):

1. **Weekday block overrides are in every golden case.** Each model carries two
   (`days [sat, sun]`: Morning 08:00–10:30, Midday 10:30–13:00). `effectiveBlocks` applies base →
   overrides → block edits, in that order. The sketch had no place for them.
2. **Occurrence keys are not always integers.** An occurrence is keyed `habit|date|n`, where `n` is
   its number within the day — except an occurrence added by "Move to another day" (scope
   `extra`), whose `n` is `x:<edit id>`. P3's key on a completion therefore has to be a string.
3. **Edits set `skip` and `deleted`**, and `pause` — `edit-skip-delete-pause.json` sets
   `deleted = true` from 2026-10-02 and a `pause` from/until. These are *scoped* removals and must
   stay edits; P1's Trash is the whole habit.
4. **An item has an active range** (`start`, `end` dates) and a `note`. No golden case exercises
   the item range (all `null`); the template documents both.
5. **Blocks carry `icon` and `color`** from fixed vocabularies (`sunrise…star`, `peach…mint`).
6. **Rule aliases.** `weekly(day)`, `alternate_days`, `alternate_weeks` are `weekdays`,
   `every_n_days(2)`, `every_n_weeks(2)`; a rule may carry a display `label` ("weekly"). The port
   stores the canonical rule only.
7. **Priority does reach `times_per_week` placement**: `expandWeek` places weekly items
   priority-first, then by id. In the golden models the two weekly items (`run`, `yoga`) are both
   priority 2, so the id order alone reproduces them. Without priority (D3) the Kotlin port orders
   by `sortOrder`, then uid; the golden parity test maps each item's id to its uid so that order is
   the reference's.
8. **The golden cases place "X times a week" automatically**, where D5 asks for a suggestion the
   person confirms. The parity test therefore pins the *suggestion* function — which days it
   offers — not the week's occurrences before confirmation.

## Model sketch — proposed for review

Room schema v26 → v27 (`TendrilDatabase.kt:99`, `MIGRATION_25_26` the last in `Migrations.kt`),
one hand-written migration, additive only.

**`habits`** gains:

| Column | Type | Notes |
|---|---|---|
| `scheduleKind` | TEXT not null, default `INTERVAL` | `INTERVAL` \| `CALENDAR`. Every existing row becomes `INTERVAL`; `frequency` keeps its meaning and is ignored for `CALENDAR`. |
| `calendarRule` | TEXT null | Canonical rule, one tagged string through `Converters`, as `RecurrenceRule` is (`"FIXED:"`/`"ELASTIC:"`, `Converters.kt:140`): `DAILY` · `ONCE:<date>` · `WEEKDAYS:<days>` · `EVERY_N_DAYS:<n>:<anchor>` · `EVERY_N_WEEKS:<n>:<anchor>:<days>` · `TIMES_PER_DAY:<n>:<slots>` (a slot is a block uid or `HH:MM`; none = spread) · `TIMES_PER_WEEK:<n>:<days>` · `EVERY_N_HOURS:<n>:<from>:<until>`. An unparseable value reads as *not due* and is kept untouched, so a newer peer's rule survives an older one. |
| `blockUid` | TEXT null | The block a flexible habit sits in. A set `time` (the existing column) chooses its block instead, by the start-inclusive, end-exclusive rule. By uid, not id, so a snapshot needs no remapping. |
| `sortOrder` | REAL not null, default 0 | Manual order inside a block (↑/↓); REAL so a move writes one row. |
| `labelId` | INTEGER null, FK `tags.id` ON DELETE SET NULL | P4; travels as the label's uid in the snapshot. (`tags` is the Label table, §3.1.6.) |
| `pauseFrom`, `pauseUntil` | TEXT (date) null | "Until I resume it" is `pauseFrom` with `pauseUntil` null. |
| `activeFrom`, `activeUntil` | TEXT (date) null | Finding 4. Cheap, and `ONCE` / scoped "from now on" read cleanly with it. |
| `note` | TEXT null | Finding 4 — **question Q3**. |

`time` and `duration` keep their meaning (the planner's `time` and `minutes`).

**`habit_blocks`** (new): `id`, `uid`, `name`, `start`, `end` (minutes of the day, end ≤ 1440),
`position`, `icon`, `hue` (**Q2**), `updatedAt`, `deletedAt`. Seeded with the planner's five
(06:30–09:00, 09:00–13:00, 13:00–18:00, 18:00–21:30, 21:30–23:30), editable.

**`habit_block_overrides`** (new, finding 1): `id`, `uid`, `blockUid`, `days` (weekday set),
`start`, `end`, `updatedAt`, `deletedAt`. Alternative: express an override as a block edit with
scope *from the beginning, these weekdays*. Rejected in this sketch because block settings show
overrides as settings, and the engine applies them before every edit whatever their dates — an
edit with an early `created` would reproduce that by accident rather than by rule.

**`habit_schedule_edits`** (new): `id`, `uid`, `target` (`HABIT` \| `BLOCK`), `refUid`, scope
(`kind` — `OCCURRENCE` \| `DAY` \| `WEEK` \| `FROM` \| `EXTRA`; `date`; `isoWeek`; `days`;
`occurrence`), `changes` (a JSON object of the fields set — `block`, `time`, `minutes`, `rule`
partial, `skip`, `deleted`, `pause`, `order`, and for blocks `start`/`end`), `createdAt`,
`updatedAt`, `deletedAt`. Applied in `createdAt` order, tie-broken by uid (the reference's
`byCreated`: created, then id). This is what "Apply this change to…" writes; an undo tombstones
the edit.

**`habit_completions`** gains `occurrenceKey` TEXT null (P3, finding 2): `null` for an interval
habit and for every existing row; for a calendar habit the within-day number, or `x:<edit uid>`.
The existing "is there a live row for this date" reads keep working for interval habits
unchanged; a calendar habit's day asks per key. The row keeps its own `date`, so presence survives
a rule change even when a key stops matching an occurrence (plan §7, risk 3).

**Sync and archive.** `HabitSnapshotRecord` gains the columns above; `HabitBlockSnapshotRecord`,
`HabitBlockOverrideSnapshotRecord`, `HabitScheduleEditSnapshotRecord` are new; all ride the
`.tendril` archive. Merge in the house style: deleted on any device wins, else the later
`updatedAt`; an older peer keeps what it does not know through `mergeUnknownFields`
(`sync/UnknownFieldMerge.kt:44`). Two devices writing scoped edits offline produce two rows, both
kept, applied in `createdAt` order — the same on every device, which is the convergence property
(plan §7, risk 2).

**Where the logic lives.** Pure Kotlin in `shared/src/commonMain/kotlin/com/tendril/app/domain/plan/`
(Phase 2): the expansion, block placement, scoped edits, the two flags and the times-a-week
suggestion, with no Room types, so the golden parity test runs in milliseconds. `HabitSchedule.kt`
dispatches on `scheduleKind`: `INTERVAL` goes to today's code untouched; `CALENDAR` asks the
engine whether the date has an occurrence.

## Questions for the Phase 0 review

| # | Question | Proposed |
|---|---|---|
| Q1 | **Where does a confirmed "X times a week" live, and what shows before confirmation?** | Confirmation writes a schedule edit, scope `WEEK`, setting that week's days. Until then the week shows one neutral line ("Run, 3 times this week — suggested Mon, Wed, Fri · Confirm"), no occurrences, nothing in the widgets. §0.6.5 holds: nothing is placed until the person places it. |
| Q2 | Block icon and colour | Keep `icon` (the planner's six, drawn with Tendril's icon set); map the planner's colours onto Tendril's existing hue keys (§2.3's token map), not new tokens. |
| Q3 | A habit `note`? | Add it (nullable, one line under the title), since the planner has it and the import would otherwise drop data. Declining it means the import drops notes. |
| Q4 | Streak on a calendar habit | None shown: a streak reads a gap as a break, which is absence (§0.5.2). Presence only — "done 4 of 5 this week" is also a fraction, so the wording is left to the Phase 1 critique. `streak` stays 0 for `CALENDAR`. |
| Q5 | Reminders for calendar habits | Out of scope (plan §8). An *interval* habit's reminder is unchanged; a calendar habit with a `time` arms nothing. Stated so it is not mistaken for a bug in the walk. |

## Phase 1 decisions (the person, 2026-09-29, before the mockups)

| # | Decided |
|---|---|
| V1 | In the Habits tab, **Today becomes the Day view and Week the Week view**, each with By time / By area; Month keeps the plain list. No separate Plan switch. |
| V2 | An **interval habit** due that day sits in its block (or under its set time) if it has one, otherwise in an **"Any time today"** group after the blocks. `blockUid` therefore applies to both kinds. |
| V3 | **A tap selects the entry and shows the edit toolbar**, docked at the bottom; its Edit opens the habit sheet. Chosen for the fewest taps and the least harm from a misclick, the person's stated concern: a stray tap changes nothing, a move is two taps, no long-press. Pause and Move to Trash sit under *More…*; the check control keeps its own hit area. Open to reversal at the Phase 1 review. |
| V4 | Two alternative directions for the **Day view by time**; one direction for every other screen. |

## Phase 1 review (the person, 2026-09-29)

| # | Decided |
|---|---|
| R1 | **Direction A** for Day by time, as revised after the first look: the app's own habit row — check leading (`HabitCheck`, the accent when done), title, a set time as a dim number at the end; one line, 48 dp on the phone; set times first in each block, no sub-labels. Direction B is not taken. |
| R2 | **A block's colour appears only beside its name**, as a small mark: the Day and Week headings, the Blocks editor, the add sheet's *Where* choices, Plan mode's band names. Everything else is neutral — no tinted rows, bands or row edges. |
| R3 | **Every other open point is deferred**, not decided: *By area*'s collapsed row for a habit several times a day, the day header's *N done today*, whether Plan mode is built (it stays a mockup, D6), and the *Ctrl+M* key for Move to…. The mockup's current drawing of each stands as the working assumption until the person reopens it. |

## Phase 3 decisions (the person, 2026-09-29, before the migration)

| # | Decided |
|---|---|
| H1 | **The five default blocks follow the app language** until renamed: a default block stores no name (`name` null) and is shown from the string resources, so Phase 4's Italian reaches them. A rename stores the text. |
| H2 | **Weekday times are a column on the block** (`overrides`, one encoded field), not their own table. Amends the sketch: `habit_block_overrides` is not created; one table, one sync file and one merge path fewer. An override travels with its block and merges with it (the later `updatedAt`). |
| H3 | **Outside the Habits tab** (the Journal's Today strip, the Habits widget) a calendar habit is **one row**; a tap checks in the next occurrence not yet done today, and the row reads done once every occurrence today is. Until the widget redesign. |
| H4 | **The Calendar's grids** draw a stroke for each occurrence **with a set time**, only on its days; an occurrence that sits only in a block stays off the grid (today's rule for a habit with no time). The month and agenda habit chips follow the same rule. |

Taken without asking, each following a precedent in the code (listed so the review can overturn any):

- **A Label travels by name**, not uid: `labelName` in the habit's snapshot record, found or
  created on merge — `PageDatabaseSnapshotRecord.labelName` and a page's `tags` do exactly this
  (`PagesSyncEngine.kt:599`, `:673`). The sketch said uid; the code's precedent wins. Locally
  `labelId` is a plain `INTEGER`, as `page_databases.labelId` is (v13), not a declared foreign key.
- **Default blocks have fixed uids** (`block-morning` … `block-night`) and `updatedAt` 0, inserted
  `OR IGNORE` each time the database opens. Two devices seeding on their own therefore write the
  same five rows, which merge to five, not ten; any real edit or deletion is newer and wins.
- **A schedule edit is inserted once and tombstoned once**, like a habit check-in: no `updatedAt`,
  "deleted on any device wins". An edit is never rewritten; a change is a new edit.
- **An edit is stored as three strings** — `scope` (`OCC:<date>:<key>`, `DAY:<date>`,
  `WEEK:<monday>[:<days>]`, `FROM:<date>[:<days>]`, `EXTRA:<date>`) and `changes` (a JSON object of
  the fields set; a key present with `null` is "set to nothing") — kept verbatim, so a key a newer
  build adds survives an older one untouched. An unreadable rule, scope or changes value makes
  that habit or edit do nothing, never a crash.
- **A calendar habit's `frequency`** is written as `1:DAY`, unread. An older build that has not
  taken v27 sees a calendar habit as a daily habit until it updates; both devices should update
  together.
- **Dates are `INTEGER` columns** (epoch days), not the sketch's `TEXT`: every `LocalDate` in the
  schema goes through the one converter, and the snapshot carries them as ISO text as it always has.
- **Streak** stays 0 on a calendar habit (Q4); `lastCompletedDate` still records the last day
  anything was checked, which the widget, the strip and the calendar read.
- **A habit purged for good takes its schedule edits out of circulation**: the write pass leaves
  out, and the merge skips, an edit whose habit has a tombstone, so they do not walk back in from
  the other device. The local rows stay (they name a habit that is gone, so they do nothing);
  deleting them would have meant a new dependency in `PurgeRegistry` and its fifteen call sites.

**Migration verified on the phone** (2026-09-30): a v26 database with two habits made through the
v26 build's own UI (a timed habit checked in, a counting habit with two cups) reached v27 through
`adb install -r` with every v26 habit and check-in column byte-identical, both habits `INTERVAL`,
the five blocks, Room's identity hash equal to `27.json`'s, `integrity_check` ok and no foreign-key
violation; a second open seeded nothing.

**Verified on the desktop too** (2026-09-30): the dev database (`~/.tendril-desktop-dev/tendril.db`, at v25, last opened 22 September, backed up and md5-checked) opened by the dev build (`gradlew run`, never the installed exe) through `MIGRATION_25_26` and `MIGRATION_26_27`: `user_version` 27, identity hash equal to `27.json`'s, all 21 tables it had — 2 habits, 6 check-ins, 25 entries, 14 pages, 17 canvas nodes, 15 revisions among them — row-for-row identical on their v25 columns, both habits `INTERVAL` with nothing new set, the five blocks, `integrity_check` ok, no foreign-key violation, nothing in the run log, no set-aside file.

## Phase 4 decisions (the person, 2026-09-30)

| # | Decided |
|---|---|
| L1 | Settings offers **English or Italian, English by default**; no *follow the system* until the app is translated throughout (the phone's system is Italian). |
| L2 | **Dates, weekday and month names follow the app's language**, not the device's. The device's region is kept (24-hour times, day-first dates). |
| L3 | **Italian only for what this plan adds**: the language row, the default block names, the Habits tab's wording. The rest of the app stays English in either language. |
| L4 | **Stop after Phase 4** for review, as the plan says. |

Taken without asking: the language is the process's default locale (the one lever Compose
Multiplatform's resources and every date formatter share, on both platforms), applied before the
first window and after an Android configuration change, with the root keyed on it so a change
takes effect at once; the Habits tab's strings are written now, from the approved mockup and the
planner's Italian, so Phase 5 builds on strings that already exist in both languages; Italian
wording follows Tendril's voice where the planner's differs (a block is a *fascia*, Trash the
*Cestino*, a check-in a *spunta*).

## Phase 5 decisions (the person, 2026-09-30)

| # | Decided |
|---|---|
| T1 | **Four slices, each gated and walked**, one stop at the end of Phase 5: 5a the Day view by time with check-ins; 5b edit mode and its prompts; 5c the add and edit sheet's calendar schedule, the blocks editor and pause; 5d the Week, By area and the desktop keyboard. |
| T2 | **The current week and later weeks are editable**; past weeks are read-only. |
| T3 | **An interval habit is projected ahead**: on a later day it shows where it would fall due if checked in on time, in italics and named *expected* — a guess said as one, never a plan. On a past day it shows only where it was checked in. |
| T4 | **The row's timer and menu move to the toolbar and the sheet** (R1's one-line row): ▶ *Start timer* and the presence details sit in the habit's sheet, and in the detail pane on the desktop. |

Taken without asking, as the mockup drew them (R3's working assumptions): *N done today* in the day header (presence, counted up; nothing said on a day with none); By area's collapsed row for a habit several a day; Ctrl+M for *Move to…*. And: an interval habit enters the engine as a habit once on the shown day, so both kinds are placed by one set of rules (V2); only today's entries can be checked — a past day is read, a later one has not happened.

## Phase 6 decisions (the person, 2026-09-30)

Asked once, before any code; Phase 6 waited for #151 to merge (the person's choice), then began
from `main` on `habit-planner-exports`.

| # | Decision |
|---|---|
| E1 | **The PDF writer is Tendril's own, in `shared/`**: one file on the phone and the desktop, read back by the unit suite, no dependency (no PDF library is in the offline cache; the standalone planner printed HTML through a headless browser). PDF's built-in Helvetica, WinAnsi characters: Latin, Italian included; anything else prints as `?`. |
| E2 | **An entry shows its time, name and duration when the habit has one, and each day its total.** No block load against capacity — the over/under framing D4 dropped. |
| E3 | **A plan sheet: no check-ins marked**, as the standalone planner's; nothing is ever marked missed (§0.5.2). |

## Phase 7 — import and retirement (the person, 2026-09-30)

Begun from `main` at `7f6e826`, after #152 merged. Before asking anything, every place the
standalone planner keeps data was read:

| Source | What it holds |
|---|---|
| `planner.toml` | The five default blocks (06:30–09:00, 09:00–13:00, 13:00–18:00, 18:00–21:30, 21:30–23:30); no `[[override]]`, no `[[area]]` |
| `habits/` | `_template.toml` only |
| `edits.toml`, `habits/added.toml` | Absent: `sync.py` never ran with anything to fold in |
| The published page's model | `items`, `areas`, `overrides`, `edits` all `[]` |
| The page's database (`items`, `edits` collections) | Empty |
| `habit-planner.zip` (2026-09-29) | A copy of the same empty project |
| This machine's scheduled tasks | None: the weekly sync is not scheduled here |

Tendril already seeds the same five blocks, with the same icons and hues
(`DEFAULT_HABIT_BLOCKS`, `data/habit/HabitBlock.kt`), so there was nothing to import.

| # | Decision |
|---|---|
| R1 | **No import code.** What was checked is recorded above; habits are added in Tendril directly. |
| R2 | **Retire the weekly sync only.** The person removes the scheduled task where it runs (not on this machine); the two pages on claude.ai, the planner and its demo, stay. |
| R3 | **The planner folder is archived** as `Downloads\Builds\Old\Habit planning system (retired 2026-09-30).zip`: 53 files, every one read back from the zip byte-equal to the folder. The person deletes the original. Nothing in the tree reads it: the golden cases were copied into `Tendril android/app/src/test/resources/habit-planner-golden/` in Phase 2, and the paths quoted above and in the plan now point into that archive. |

## Other findings from Phase 0

- **`docs/build-order.md` T7** ("Habits cannot express 3 times a week or specific weekdays") is
  what `CALENDAR` habits deliver, by a schema change rather than the encoding change T7 proposed.
  Marked superseded there; **DB12** ("Sync to Habits") depended on T7 and now depends on this work.
- **The plan and `CLAUDE.md` cite `docs/audit-2026-09-22.md`; the current audit is
  `docs/audit-2026-09-24.md`** (`python tools/facts.py`). Its open row **5.15** — the widget's undo
  does not re-arm the habit alarm on the phone, red on `main` too — is in the area Phase 3 touches
  (`HabitsWidget.kt`). Not fixed here; Phase 3 must not be blamed for it or silently absorb it.
- **Both platforms.** The Habits tab is shared (`shared/…/ui/taskshabits/`), so Phases 5–6 reach
  the desktop. Habit due-ness is read in `shared/` by `HabitSchedule.kt`, `JournalToday.kt` and
  `reminders/ReminderFirings.kt`; on Android also by `AlarmScheduler`,
  `HabitReminderAlarmReceiver` and `HabitsWidget`. The widgets have no desktop twin. Phase 3's
  wiring covers the three `shared/` readers, which the desktop uses too: `Tendril windows/…/
  DesktopReminders.kt` calls `habitFiring` (`ReminderFirings.kt:81`), so Q5's "a calendar habit
  arms nothing" must hold there as well as in `AlarmScheduler`.
