# Habit planner in Tendril — plan for Claude Code

Status: proposed 2026-09-29. Run it only when every branch is merged and deleted (Phase 0 checks this and stops otherwise). **Done 2026-09-30**: Phases 1–5 merged as #151, Phase 6 as #152; Phase 7 found nothing to import, and the planner's weekly sync is the person's to remove where it runs. The reference implementation below is archived as `Downloads\Builds\Old\Habit planning system (retired 2026-09-30).zip`; see the decision record's Phase 7 section. Work phase by phase; **stop at the end of every phase** and wait for the person's review. Never commit or push: the person reviews and commits.

## 1. What this task is

The person built a standalone habit planner (outside Tendril) and wants its planning features inside Tendril's **Habits** tab, replacing the standalone version. The planner turns habits into daily and weekly plans arranged in **time blocks** (Morning, Midday, Afternoon, Evening, Night), with **set times** listed first, calendar-based repetition rules, and an edit mode for moving entries. Tendril already has habits (interval since last check-in, optional time and duration, check-ins, presence, counting habits, trash, sync, a Habits widget) and a Plan mode on the Day view. The work extends Tendril's Habit with a second kind of schedule and adds the planner's views and editing to the Habits tab, following Tendril's rules wherever the two disagree.

**The reference implementation** is in `C:\Users\User\Downloads\Builds\Habit planning system` (read-only for this task):

| Path | What it tells you |
|---|---|
| `planner/assets/engine.js` | The scheduling engine: every rule, set times, blocks, scoped edits, flags. The behaviour to port. |
| `golden/*.json` + `golden/README.md` | Ten reference cases produced by that engine: input model → expected blocks, occurrences and the two structural flags Tendril keeps, for two weeks. The Kotlin port must reproduce them. Regenerate with `python3 tests/make_golden.py golden` (needs Node.js; not needed to read them). |
| `planner/assets/app.js` | The editing flows: edit mode, ↑/↓ across blocks, the time / remove-time / scope prompts, block-boundary editing, add/edit form, pause. |
| `tests/ui/test_ui.py` | The 30 browser checks: the flows written as tried/expected steps. Use them as the walk script. |
| `planner/i18n_ui.py`, `planner/i18n.py` | English and Italian wording for every control and prompt. |
| `README.md`, `habits/_template.toml` | The rules and fields, in plain words. |

## 2. Decisions already taken (do not reopen)

Rule of thumb from the person: **where the planner and Tendril's habit features disagree, Tendril prevails.**

| # | Decision |
|---|---|
| D1 | **Extend `Habit`** (option A): a habit's schedule is either *since last check-in* (today's `HabitFrequency`, unchanged) or *on the calendar* (the planner's rules). One Habits list; no new kind of item. |
| D2 | Calendar habits get Tendril's **check-ins and presence** like every habit. §0.5.2 applies: presence, never absence. |
| D3 | **No priority on habits.** Order inside a block is manual (↑/↓). Priority stays in Tasks under Tendril's existing rule. The planner's priority prompt is dropped. |
| D4 | Flags: only the structural ones — **blocks overlapping** and **a set time outside every block** — shown as neutral notes, no red, no over/under colour. **No daily limit.** |
| D5 | "X times a week": the app **suggests days; the person confirms** (§0.6.5 "no automatic placement" stands). |
| D6 | **Time blocks hold habits only.** They show in the **Habits tab** and in the **Habits widgets**. The widgets will be designed afterwards (separate task). **Plan mode gets a mockup** in Phase 1; building it is not in this plan unless the person says so at that review. |
| D7 | The planner's screens (Day / Week, By time / By area) live **inside the Habits tab**. No sixth destination. |
| D8 | The planner's *areas* are **Tendril Labels**. |
| D9 | **One title per habit.** The whole app's language (English / Italian) is chosen in general **Settings** — a new app-wide setting. |
| D10 | The six scopes of "Apply this change to…" (only this entry · this day · this week only · chosen weekdays this week · every week from now on · chosen weekdays from now on; identical choices hidden) apply to **habits now**, tasks later. |
| D11 | **Keep the exports**: daily and weekly plan as Markdown first, A4 PDF second (daily portrait, weekly landscape). |
| D12 | Retire the standalone planner (its claude.ai page and its Sunday 19:00 sync) **only after** the Tendril version passes its gate and its walks, then import its data. |
| D13 | **Tendril's design system and order of work**: mockup → critique → build → walk, phone and desktop. |
| D14 | A **new branch** from an up-to-date `main`; the person commits. |

Carried over unchanged from the planner (the person specified them and nothing in Tendril contradicts them): editing opens with a **tap** (click on desktop; arrow keys, Enter and Esc work there); ↑ at the top of a block jumps to the bottom of the block above and ↓ at the bottom to the top of the block below; moving an entry among set times asks for a time (no suggestion); a timed entry leaving them asks first whether to remove its time; declining any prompt puts the entry back; a set time outside its block moves the entry to the block containing it; a block runs from its start up to, not including, its end (09:00 belongs to the block starting at 09:00); editing a block's end moves the next block's start, and pushing past the next block's end is refused; the first start and the last end are free, times stop at 24:00; pause asks "until I resume it" or "until a date"; past weeks are read-only, the current week editable.

### Derived decisions — confirm at the Phase 0 review

These follow from "Tendril prevails" but were not asked in so many words. Present them in the Phase 0 report and wait.

| # | Planner did | Proposed in Tendril |
|---|---|---|
| P1 | Delete = type DELETE, stops from today, past kept | Tendril's **Move to Trash** (recoverable, §5.5.1); purge follows Tendril's Trash. |
| P2 | Page-wide Undo / Redo, 5 steps | Tendril's existing undo patterns for habits (undo check-in §8.1.1, the snackbar/undo it already uses); no separate 5-step stack unless the person asks. |
| P3 | Several-times-a-day habits had no check-ins | **One check-in per occurrence**, built on the counting-habit precedent (several completions a day, §0.10 item 3); the day's presence counts occurrences done. |
| P4 | One area per habit | **At most one Label per habit** (a nullable reference), matching the planner; many-to-many only if the person asks. |
| P5 | Every-N-hours: removing one time applied to that entry only | Same (a regular series cannot lose one step going forward). |

## 3. Constraints from Tendril (read before Phase 0)

- `CLAUDE.md` in full, then `tendril-spec.md` §0 (Objectives), §3.3 Tasks & Habits, §6 Habits vs recurring tasks, §0.6.5 (time), §0.6.6 (presence), §0.10 item 3 (counting habits), §4 data model, §9.4 sync and merge, §9.4.1 archive, §9.10 migration policy, §8 widgets; `Tendril windows/tendril-windows-spec.md` for the desktop; `docs/build-order.md` for where this work sits; `docs/audit-2026-09-22.md` for known defects in the areas touched.
- **The gate** (all four, offline) and counting tests **from the XML**, never Gradle's summary — as `CLAUDE.md` gives them.
- **Both platforms, every change**: the Habits tab is shared UI (`shared/src/commonMain/.../ui/taskshabits/`), so it reaches desktop; widgets are Android-only; say what the desktop has for every finding.
- **A `shared/` change owes a Revision Log row in both spec files** plus the amendment in the home section.
- LF line endings everywhere; write files with `newline=""`; no heredocs for text containing `§`, arrows or backslashes (`CLAUDE.md`, Shells).
- A finding is a hypothesis until a command proves it. Never launch the installed `Tendril.exe`.
- One schema migration for this work (§9.10), verified on a **populated** database on both devices (the person runs the device part).

## 4. Skills

Load each skill's `SKILL.md` before using it. The repo's own `/tendril-audit` already runs the registry skills in-thread; do not duplicate its passes.

| When | Skill | Why |
|---|---|---|
| Start of every phase | `optimization-engines:de-optimizer` | Pick one approach before writing anything long (it skips itself when there is no real choice). |
| Phase 0, Phase 2 | `dev-engineering-suite:architecture-designer` | The schedule model, where scoped edits live, how occurrences get stable identity. |
| Phase 2 only, if two storage designs stay close after D-O | `optimization-engines:meta-optimizer` | The precedent: §7.4 scored the import architecture this way. |
| Phase 1 | `creative-design-suite:craft` | Mockups built from Tendril's existing tokens (`ui/theme`), not new ones. |
| Phase 1, 4 | `design-optimization:app-register-layout` | Row, header and toolbar density on the phone and desktop profiles. |
| Phase 1, 4 | `creative-design-suite:critique` | The written critique in `docs/critiques/` (the measurable half measured). |
| Phase 1, 4 | `creative-design-suite:accessibility-color-auditor` | Contrast of every new role, Ink dark included; neutral notes must stay legible without colour. |
| Phase 4 | `creative-design-suite:handoff` | Values extracted from the approved mockup, so the build matches it. |
| Phase 2, 3, 4, 6 | `dev-engineering-suite:test-writer` | Test-first: golden parity, migration on a populated DB, merge rules, UI state. |
| Phase 2–7, end of each | `dev-engineering-suite:code-verification-core` | Spec trace (`--generation` for code written in the session). |
| Phase 2–7, end of each | `dev-engineering-suite:code-reviewer` | Room migrations, two-device merge, Compose state; the same code on the other platform. |
| When something fails | `dev-engineering-suite:debugging-guide` | Reproduce, isolate, then fix. |
| Phase 3, 4 | `dev-engineering-suite:performance-profiler` | Only if the Habits list or occurrence expansion shows up in a trace. |
| Phase 3 | `dev-engineering-suite:dependency-auditor` | Only if a dependency is added (none is expected). |
| End of every phase | `/tendril-audit --diff` | The repo's gate-plus-audit; its five gate commands. |

Tools from the repo: `python tools/spec_trace.py`, `python tools/mutate.py --symbol <guard> --list` then without `--list` on the new guards (about four minutes each; choose), `python tools/facts.py`, `python tools/audit.py`.

## 5. Phases

Every phase ends the same way: run the gate, count tests from the XML, run `/tendril-audit --diff`, add the Revision Log rows (both specs) for any `shared/` change, write a short report (what changed, what was proved and by which command, what is a hypothesis, what the desktop has), and **stop for review**.

### Phase 0 — Preflight and decision record (no code)

1. `git fetch`; stop if any local or remote branch other than `main` exists, if `main` is behind `origin/main`, or if `git status` shows anything other than this plan file. The plan is written to `docs/plans/` before it is proposed, so it may be the one untracked file: ask the person to commit it to `main` first, or to let it travel on the new branch. Report and wait.
2. Run the gate on `main` as the baseline; record test counts from the XML.
3. Read the documents in §3 and the reference in §1. Run the reference cases mentally against Tendril's model: list which fields a calendar habit needs that `Habit` lacks.
4. Write `docs/decisions/2026-09-29-habit-planner.md`: D1–D14, P1–P5 as proposed, and the model sketch (below). Add the work to `docs/build-order.md` in the stage the person's rules put a migration-bearing feature (serial, nothing else in flight).
5. Create the branch `habit-planner` from `main`.
6. **Stop.** The person confirms P1–P5 and the model sketch.

Model sketch to propose (adjust after reading):

- `Habit` gains `scheduleKind` (`INTERVAL` | `CALENDAR`), `calendarRule` (one of: daily · once(date) · weekdays(days) · everyNDays(n, anchor) · everyNWeeks(n, anchor, days) · timesPerDay(n, slots: block id or time) · timesPerWeek(n, days, confirmedDays?) · everyNHours(n, from, until)), `blockId` (nullable; a set `time` chooses its block instead), `sortOrder`, `labelId` (nullable), `pauseFrom` / `pauseUntil`. `frequency` stays for `INTERVAL`.
- `habit_blocks`: id, uid, name, start, end, position; the defaults are the planner's five (06:30–09:00, 09:00–13:00, 13:00–18:00, 18:00–21:30, 21:30–23:30), editable.
- `habit_schedule_edits`: the planner's edit record — target (habit | block), ref uid, scope (kind, date, week, days, occurrence), the changed fields, created, a tombstone. Applied in created order; this is what "Apply this change to…" writes.
- `habit_completions` gains an occurrence key (for several a day, P3).
- All of it travels in the snapshot and the `.tendril` archive, with merge rules in the house style: tombstoned, deleted wins, else the later `updatedAt`; older peers ignore unknown fields (`UnknownFieldMerge`).

### Phase 1 — Mockups and critique (no production code)

1. With `craft` and `app-register-layout`, build `docs/mockups/habit-planner.html` from Tendril's tokens: the Habits tab's Day and Week views, each *By time of day* and *By area (Label)*; *At a set time* on a rail first, then *Any time in this block*; the edit mode toolbar (↑ ↓, Move to…, Edit, Skip, Pause, Move to Trash, Done) on phone and desktop; the time, remove-time and scope prompts (with the weekday row); block-boundary editing; the add/edit sheet's calendar schedule (including "suggested days — confirm"); the neutral notes. Phone and desktop profiles, Ink dark and light.
2. A separate mockup for **Plan mode** with habit blocks (`docs/mockups/habit-planner-plan-mode.html`), as D6 asks.
3. `critique` → `docs/critiques/habit-planner-mock.md`; `accessibility-color-auditor` on every new role.
4. **Stop.** The person chooses and amends; nothing is built from an unapproved mockup.

### Phase 2 — The schedule engine in `shared/` (pure Kotlin, no database, no UI)

1. Test-first. Copy the ten `golden/*.json` cases into `shared` test resources and write a parity test that reads each case's `model`, computes the two weeks and compares blocks, occurrences and structural flags. They fail first.
2. Port `engine.js` to pure Kotlin in `shared/src/commonMain/kotlin/com/tendril/app/domain/plan/` (e.g. `CalendarSchedule.kt`): rule expansion, the block containing a time (start inclusive, end exclusive), weekday overrides, scoped edits (occurrence, day, week ± days, from ± days, extra), series edits (every-N-hours start shift, times-per-day slots), pause, the two structural flags, and the times-per-week **suggestion** (the planner's evenly spaced, least-loaded rotation) as a function the UI offers rather than applies.
3. Leave out what D3, D4 and D9 drop: priority ordering, the daily-limit flag, per-habit bilingual names.
4. `mutate.py --list` on the new guards; mutate the ones that decide placement (block boundary, scope coverage).
5. **Stop.**

### Phase 3 — Data, migration and sync

1. One migration (`MIGRATION_26_27` or the next number at the time; read it from `TendrilDatabase.kt`) adding D1/P3/P4 fields and the two tables; export the schema JSON; `PopulatedMigrationTest` with existing interval habits, counting habits and completions surviving unchanged.
2. Snapshot records, mappers, the `.tendril` archive, merge rules, unknown-field handling; a two-device merge test for each new table (edit on both, delete on one).
3. Wire `HabitSchedule` / `isHabitDueOn` / reminders / `HabitStrokes` / Merged / the Habits widget's data so a `CALENDAR` habit is due on its occurrences and an `INTERVAL` habit behaves exactly as before (tests for both). Reminders stay as they are for interval habits; no new notifications (out of scope).
4. Ask the person to verify the migration in place on the phone and the desktop databases, as §0.6.6 records it was done before.
5. **Stop.**

### Phase 4 — App language setting

1. A general **Settings** choice: English / Italian (and following the system if Tendril's conventions prefer it — decide in the report). `composeResources/values-it/` with the Italian strings for everything this plan adds; take the wording from the planner's `i18n_ui.py`, adjusted to Tendril's voice. Dates already localise.
2. Say in the report which existing screens still lack Italian strings; translating them all is not part of this plan unless the person asks.
3. **Stop.**

### Phase 5 — The Habits tab

1. Build from the approved mockup (`handoff` for its values): Day / Week, By time / By area, the rail, edit mode and its prompts, block editing, the calendar schedule in the add/edit sheet with suggested days to confirm, the neutral notes, check-ins per occurrence with presence, Move to Trash, Pause, labels as areas.
2. Desktop: keyboard (↑/↓ in edit mode, Enter/Esc, the existing 14e keyboard model), the two-pane layout from 840 dp.
3. `tools/audit.py` design checks 11–19 clean; `accessibility-color-auditor` on the build.
4. The walk, phone and desktop, using `tests/ui/test_ui.py`'s steps as the script → `docs/critiques/habit-planner-function.md` (tried / observed table, `uiautomator` dumps on the phone).
5. **Stop.**

### Phase 6 — Exports

1. Markdown: the week and each day, by time (set times first) and by area, from the same engine output.
2. A4 PDF: daily portrait, weekly landscape (the planner's layout adapted to Tendril's theme), on both platforms; say what each platform uses to render it.
3. **Stop.**

### Phase 7 — Import and retirement

1. A one-off import of the standalone planner's data (`planner.toml` blocks and areas → habit blocks and Labels; `habits/*.toml` → calendar habits; `edits.toml` → schedule edits), as a `tools/` script producing a `.tendril` archive or through the existing import path — whichever the archive format supports without new app code. Dry-run first; show the person what will be created.
2. After the person confirms and the import is verified: the person (or a Claude session with access) removes the scheduled task "Habit Planner weekly sync" and the two planner pages on claude.ai (the planner and its demo), and archives the planner folder. Their links and the task's id are not written here, because this repository is public: find them by name in the person's scheduled tasks and artifact list, or ask the person. Claude Code on Windows may not have the tools for the first two; say so rather than work around it.
3. **Stop.** Final report.

## 6. Why this order

- **Decisions and mockups before code**: every open choice is settled while it costs a document, not a migration.
- **Engine before database, database before UI**: the engine is pure and tested in seconds against known answers; the one migration then carries a model that is already proved; the UI is built last on stable data.
- **One migration** instead of one per feature: §9.10's policy and the populated-DB verification happen once.
- **Golden cases instead of re-deriving behaviour**: the port is checked against ten cases produced by the engine the person already used, so "does it behave like the planner" is a test, not a reading.
- **The language setting before the UI**: new strings land in both languages the first time.
- **Retirement last**: the standalone planner keeps working until its replacement has passed its gate and its walks.

## 7. Risks

| Risk | Guard |
|---|---|
| The interval habits change behaviour | Tests for `INTERVAL` habits written before any change to `HabitSchedule`; the populated-DB migration test includes them. |
| Two devices merge scoped edits in different orders | Edits applied in `created` order with a stable tie-break; a two-device merge test. |
| Occurrence identity drifts when a rule changes | The occurrence key is (habit uid, date, index within the day) as in the planner; check-ins keep their own date so presence survives a rule change. |
| A calendar habit shows "absence" | §0.5.2 review in the critique: no misses, no gaps, no red anywhere the new views touch. |
| Scope creep into widgets, Plan mode, tasks | D6 and D10: mockup only for Plan mode; widgets and tasks are later plans. |

## 8. Out of scope

Notifications for calendar habits; widget redesign; Plan mode build (mockup only); scoped edits for tasks; translating Tendril's existing screens beyond what this plan adds.
