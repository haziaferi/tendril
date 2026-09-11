# Benchmarks — what the bar is, per surface

**Dated 2026-09-11.** Input to the objectives file's "the bar" section. Forty-eight names were
put forward; this scores the ones that are products, says why the rest are not, and proposes what
to take. It is a *decision document*: every table ends in a "Take" column and §6 turns those into
features and screens with the decisions they need.

**Constraints applied to every score** (from the objectives survey, 2026-09-10):
offline-first, no telemetry, no first-party sync, no collaboration, no public sharing, no web
clipper, AI strictly opt-in with a personal API key. Personal use only.

**Frame of reference.** Per the brief: *whenever a full-featured paid option is present, that is
the frame*. Its `Reach` is 5 by definition.

| Surface | Frame (paid tier) | Why this one |
|---|---|---|
| Pages | **Notion, Plus** | The starting point, and still the deepest block+database product. The data-model frame is separate: **Tana** (see §1.1). |
| Calendar | **Fantastical, Premium** | The deepest single-user calendar; its best parts are local logic. |
| Tasks & Habits | **TickTick, Premium** | The only mainstream app that does tasks + habits + calendar in one product — Tendril's own shape. |
| Road Map | **Obsidian, core graph** | No paid product owns this surface; Obsidian's graph is what every other one copies. |

**Columns.** `Reach` 0–5: how far the app goes on this surface (frame = 5). `Fit` 0–5: how much
of that survives the constraints above — 5 means it is all local, single-user logic; 0 means it is
all server, collaboration or AI. `Gap` 0–5: how much of the fit-surviving part Tendril lacks,
measured against the tree and the spec on the date above, present *and* planned — 5 means almost
none of it exists here. `Value` = Reach × Fit × Gap ÷ 25, so 0–5; it is a *reading aid*, not a
verdict. `Take` is the verdict.

**Grounding.** `Gap` was measured against: §3.1.1 block inventory, §3.1.3–3.1.7 (templates,
Journal, backlinks — built, `PageDetailScreen.kt:891` — tags, page-level FTS), §3.2 as corrected
2026-09-06 (Calendar creates events only, edits nothing), §3.3, §3.4, §3.7 Canvas, §5 databases
with `TABLE / BOARD / GALLERY / CALENDAR` views and `RELATION / COMPUTED` properties (DB1–DB5),
§6 habits, and `docs/scope-decisions.md`. Entity facts that drive several scores: `Entry` has no
priority, no sub-tasks, no estimate/duration, no tags, no defer date; `Habit` has
`HabitFrequency(count, unit)`, `streak`, `lastCompletedDate` and **no completion history**.

---

## 1. Pages

| App | Reach | Fit | Gap | Value | Take |
|---|---|---|---|---|---|
| **Notion** (frame) | 5 | 4 | 3 | 2.4 | Linked/inline database views inside a page; sub-page create + move from a page; column layout; synced blocks; page history; Timeline view; per-database templates. Collaboration, web publishing and Notion AI are excluded by constraint. |
| **Obsidian** | 5 | 5 | 3 | 3.0 | Quick switcher; command palette (reopens §3.1.7's deferral); unlinked mentions; outgoing-links panel; block references (`^id`); *Bases* — a table over pages built from their properties, which is Tana's idea in plain files. Fully local: nothing is lost to the constraints. |
| **Anytype** | 4 | 5 | 4 | 3.2 | **Object types with relations, and Sets** — a live query of every object of a type. The closest architectural match to what Tendril already is (local-first, typed rows, optional sync you do not run). |
| **Tana** | 5 | 3 | 4 | 2.4 | **Supertags**: a tag that carries a schema, so tagging a node makes it a row. Live-search nodes. Cloud-only and AI-heavy, hence Fit 3, but the *idea* is entirely local. |
| **Logseq** | 4 | 5 | 3 | 2.4 | Arbitrary outliner nesting (§3.1.1 stops at one level); block references and block embeds; `TODO` keyword turning a block into a task; simple queries. |
| **Capacities** | 4 | 3 | 4 | 1.9 | Object-based notes: every note is an object of a type with properties; media as objects; an object calendar. Same family as Anytype/Tana. |
| **Coda** | 5 | 3 | 3 | 1.8 | Inline formulas in body text that read from tables; buttons that mutate rows. Packs and automations are server-side. |
| **OpenKnowledge** (inkeep) | 3 | 4 | 3 | 1.4 | The pattern for C4: an agent operating over a *local Markdown* knowledge base with the person's own key. Tendril already produces that Markdown (#37). |
| **Heptabase** | 4 | 3 | 3 | 1.4 | For Canvas (§3.7): nested whiteboards, sections, a mind-map layout mode, a card library. |
| **Airtable** | 5 | 3 | 2 | 1.2 | For databases: multi-condition filters and grouping (§5.6 is single-condition); attachment property; lookup; Timeline view. |
| **Miro** | 5 | 2 | 3 | 1.2 | For Canvas: frames, sticky-note colours, shape and image nodes. Collaboration-first, so most of it does not transfer. |
| **BookStack** | 3 | 3 | 2 | 0.7 | One thing: **page revision history**, which Tendril has none of. |
| **Scrintal** | 3 | 3 | 2 | 0.7 | Cards-as-notes on a board — Canvas's page-embed cards already are this. |
| **xTiles** | 3 | 3 | 2 | 0.7 | Tile/column layout inside a page. Overlaps Notion's columns. |
| **AppFlowy** | 3 | 5 | 1 | 0.6 | Nothing Tendril lacks except its **local-model AI mode** (Ollama), worth noting as a C4 option beside a remote key. |
| **XWiki** | 4 | 2 | 2 | 0.6 | Versioning. Otherwise a server wiki. |
| **ClickUp** | 4 | 2 | 2 | 0.6 | Docs are its weakest part; a team product. Skip on this surface. |
| **Storyflow** | 3 | 2 | 2 | 0.5 | Canvas *templates* (beat sheets) — a template whose body is a Canvas layout. Small, and §3.1.3 templates could carry it. |
| **Reflect** | 3 | 2 | 2 | 0.5 | One thing: **today's calendar events inside the daily note.** Journal (§3.1.4) + Entries makes this cheap. |
| **Agent-Native Content** | 2 | 3 | 2 | 0.5 | The C4 verbs — rewrite / expand / summarise / change tone on a selection. Everything else is MDX and React. |
| **Mem** | 3 | 1 | 2 | 0.2 | AI-inferred relations, which §3.4 ruled out. Skip. |
| **Upbase** | 2 | 2 | 1 | 0.2 | Simple docs inside a team tool. Skip. |

### 1.1 The data-model frame

Notion is the frame for *blocks*. For the data model — which A4 said "exceed" extends to — the
frame is the **object-model family**: Tana's supertags, Anytype's types + Sets, Capacities'
objects, Obsidian's Bases. They are one idea with four names: *a schema can be attached to any
page, and any page with that schema is a row.* Tendril has both halves and does not join them:
§3.1.6 Tags are flat labels, §5 databases own their rows. Joining them (§6, proposal 4) is the
single move that would put Tendril's model past Notion's rather than beside it.

### 1.2 Not products, or not this surface

| Name | What it is | Why it is not scored |
|---|---|---|
| **Lexical** | Meta's JavaScript rich-text editor framework | A library, not an app, and a JS one; §3.1.1's span model is the equivalent decision here and is not reopened. |
| **Calendar.com** | Scheduling-link / meeting-booking service | Its whole purpose needs a server and other people. |
| **TimeTree** | Shared family calendar | Sharing *is* the product. The one local idea — a memo on an event — is a row-as-page (§5.1). |

---

## 2. Calendar

| App | Reach | Fit | Gap | Value | Take |
|---|---|---|---|---|---|
| **Fantastical** (frame) | 5 | 4 | 4 | 3.2 | **Natural-language entry** ("lunch with Ana tue 1pm /work"); agenda list; calendar sets (colour-coded categories toggled together); event templates; drag-to-reschedule; time zones. Openings, proposals and weather are excluded. |
| **FlowSavvy** | 4 | 4 | 5 | 3.2 | **Auto-scheduling**: a task with a duration and a deadline is placed into free time and re-flowed when anything moves. The scheduler is pure local logic. Tendril has nothing of the kind. (Motion and Reclaim are the paid cloud versions of the same idea.) |
| **Toggl Track** | 4 | 4 | 5 | 3.2 | **Time tracking**: start/stop on anything, a calendar of what was actually done, totals. Tendril tracks no time at all. |
| **Super Productivity** | 3 | 5 | 4 | 2.4 | Timeboxing and tracking, open source, fully local — the reference *implementation* for the two rows above. |
| **Routine** | 4 | 4 | 3 | 1.9 | A unified daily planner (calendar + tasks + notes in one column) and a "console" quick-capture. The nearest all-in-one to Tendril's shape; offline-first by design. |
| **TickTick** | 4 | 4 | 3 | 1.9 | The "Arrange" panel — drag unscheduled tasks onto the calendar; habits shown in the calendar (§3.2's unbuilt "Show Habits"). |
| **Notion Calendar** | 4 | 3 | 3 | 1.4 | **Every database's date properties as toggleable layers on one calendar.** Tendril has a calendar view *per* database (§5.6) and no global overlay. |
| **Stock Android (Google/AOSP)** | 3 | 5 | 2 | 1.2 | Schedule (agenda) view; platform conventions for Provider behaviour (§9.11 already follows them). |
| **Fossify Calendar** | 3 | 5 | 2 | 1.2 | **ICS import/export**; event types with colours; year view. Open source and already cited (§8.5) — read it before writing any of those. |
| **Akiflow** | 4 | 2 | 3 | 1.0 | Drag-to-time-block; daily planning and shutdown rituals; a command bar. Its aggregation of cloud services is excluded. |
| **Sunsama** | 4 | 2 | 3 | 1.0 | **Planned vs actual** time per task; a weekly review; workload per day. Cloud-only, ideas local. |
| **Any.do** | 3 | 3 | 2 | 0.7 | "My Day": a daily prompt to pick today's tasks. |
| **Tweek** | 2 | 4 | 2 | 0.6 | A week as seven columns of to-dos plus "someday" — a paper-planner layout. Small and pleasant. |
| **Vikunja / ClickUp / Upbase** | 3 | 2 | 2 | 0.5 | Team calendars over tasks; nothing beyond the rows above. |

**What the corrected §3.2 already owes** — an edit path from Calendar, task creation from
Calendar, "Show Habits", undated tasks in Merged — sits underneath every row here. Natural-language
entry (Fantastical/Todoist) pays that debt in one feature: a parser that produces a Task *or* an
Event with a time, span and recurrence, from one line.

---

## 3. Tasks & Habits

| App | Reach | Fit | Gap | Value | Take |
|---|---|---|---|---|---|
| **TickTick** (frame) | 5 | 4 | 3 | 2.4 | Sub-tasks; priority; lists and folders; tags on tasks; natural-language input; Pomodoro; **habit statistics**; Eisenhower matrix; saved filters. Location reminders are a platform feature Tendril could add via geofencing but nothing else here needs. |
| **Super Productivity** | 4 | 5 | 4 | 3.2 | Open source, local: time tracking with a daily summary, Pomodoro, break reminders, attachments on tasks, "today" planning with estimates. The reference implementation for time on tasks. |
| **Toggl Track** | 4 | 4 | 5 | 3.2 | Same row as §2 — the tracking half of planned-vs-actual. |
| **FlowSavvy** | 4 | 4 | 5 | 3.2 | Same row as §2 — the planning half. |
| **OmniFocus** | 5 | 5 | 3 | 3.0 | **Defer vs due** (a task has a date it *becomes available* and a date it is *late*); **Perspectives** — saved custom views over every task; a **Review** mode that walks each project on a cadence; sequential projects; Forecast view (tasks and calendar together, which Merged already approaches). Local database, optional sync. The deepest task *model* on the list. |
| **Things 3** | 4 | 5 | 3 | 2.4 | Today / Upcoming / Anytime / **Someday**; "When" vs "Deadline" (OmniFocus's defer/due, made simple); headings inside a project; checklists inside a task; Evening section; quick entry. Local, elegant, the reference for *restraint*. |
| **Todoist** | 5 | 4 | 3 | 2.4 | The best natural-language date parser in the category ("every 2nd tuesday at 9", "in 3 weeks"); a filter query language; sections; labels. Karma and collaboration excluded. |
| **Loop Habit Tracker** | 3 | 5 | 4 | 2.4 | **Completion history** (Tendril has only `streak` + `lastCompletedDate`); calendar heat-map; a decaying *score* rather than a binary streak; **measurable habits** ("drink 2 L", "run 5 km"); "3 times per week" frequencies (`HabitFrequency` already models this); per-habit reminders. Open source Android — read it. |
| **Tasks.org** | 4 | 5 | 3 | 2.4 | Open source Android, offline-first: sub-tasks, priority, tags, **CalDAV / VTODO** sync, location reminders, widgets. The reference for how a task app behaves on Android without a server. |
| **Vikunja** | 4 | 3 | 3 | 1.4 | **Task relations** (blocked-by, subtask-of, related); Gantt; kanban; CalDAV. Self-hosted server. |
| **ClickUp** | 5 | 2 | 3 | 1.2 | Dependencies; custom fields; everything else is team scale. Skip except the dependency model, which Vikunja has in simpler form. |
| **Planka / Planka Pro** | 3 | 4 | 2 | 1.0 | Kanban with lists, labels, due dates — §5.6's Board view already is this. Nothing further to take. |
| **Kan.bn** | 3 | 4 | 2 | 1.0 | Same as Planka; board templates. |
| **Akiflow / Sunsama / Routine** | 4 | 2–4 | 3 | 1.0–1.9 | Covered in §2: time-blocking, rituals, planned-vs-actual, the unified daily planner. |
| **Notion** | 3 | 4 | 2 | 1.0 | Tasks are databases — §5 already mirrors it. |
| **Any.do** | 3 | 3 | 2 | 0.7 | My Day. |
| **Coda / Airtable** | 3 | 3 | 1–2 | 0.4–0.7 | Tables as task lists — covered by §5. |
| **Tweek** | 2 | 4 | 2 | 0.6 | Week-columns planner (§2). |
| **Superlist** | 3 | 2 | 2 | 0.5 | Notes inside tasks — row-as-page (§5.1) already. Collaboration-first. |
| **AppFlowy** | 2 | 5 | 1 | 0.4 | Kanban — §5.6. |
| **Upbase / xTiles** | 2 | 2–3 | 1 | 0.2 | Nothing beyond §5. |

**Where Tendril is weakest on this surface**, from the entity dump rather than from the apps: an
`Entry` is a title, a kind, a date range and a recurrence. Every frame above adds at least
priority, sub-tasks and an estimate; the two deepest (OmniFocus, Things) add *when* vs *due*; and
`Habit` cannot answer "how did I do last month".

---

## 4. Road Map

The frame is Obsidian's core graph. No app on the list sells a graph as its product; several draw
one as a side view, and several others (Heptabase, Scrintal, Miro, xTiles, Storyflow) are *manual*
spatial maps, which is Canvas (§3.7), not Road Map.

| App | Reach | Fit | Gap | Value | Take |
|---|---|---|---|---|---|
| **Obsidian** (frame) | 5 | 5 | 3 | 3.0 | **Filters** (by tag, by path, orphans on/off); **groups** — colour nodes by a query; **local graph** at depth *N* from the open page; node size by degree; time-lapse. Road Map has neighbourhood-highlight and "All Pages", nothing to filter or colour by. |
| **Logseq** | 4 | 5 | 2 | 1.6 | Same as Obsidian's; journal pages excluded from the graph by default — Journal (§3.1.4) pages will flood Road Map the same way and the same toggle is the answer. |
| **Anytype** | 3 | 5 | 2 | 1.2 | **Typed edges**: an edge is labelled with the relation it came from. Tendril has three edge kinds already — mention, "Relate to…", and since DB1 a `RELATION` property — and draws them all the same. |
| **Capacities** | 3 | 3 | 2 | 0.7 | Colour by object type — the same as "colour by tag/kind" above. |
| **Reflect** | 3 | 2 | 1 | 0.2 | A plain graph. Nothing further. |
| **Mem** | 2 | 1 | 1 | 0.1 | Inferred edges — ruled out by §3.4, and the reason stands. |
| **Heptabase / Scrintal / Miro / xTiles / Storyflow** | — | — | — | — | Manual maps: scored under Pages as input to Canvas. |
| **Tana** | — | — | — | — | Has no graph view. |

---

## 5. What the tables say when read together

Reading across the four surfaces, the same handful of ideas score high everywhere they appear:

1. **Time** — an estimate on a task, a scheduler that places it, a timer that records what happened,
   and the planned-vs-actual view that closes the loop (FlowSavvy, Toggl, Super Productivity,
   Sunsama, Akiflow). Value 3.2 in two sections; Gap 5 — Tendril has *no* notion of time spent or
   time needed. This is the largest absent capability on the list.
2. **A schema on a tag** (Tana, Anytype, Capacities, Obsidian Bases). Value 2.4–3.2; the one idea
   that changes the data model rather than adding to it.
3. **Natural-language entry** (Fantastical, Todoist, TickTick). Pays §3.2's corrected debt — Calendar
   creates only events — as a side effect.
4. **A task model with priority, sub-tasks, and *when* vs *due*** (OmniFocus, Things, TickTick,
   Tasks.org). Every task frame has it; `Entry` has none of it.
5. **Habit history** (Loop). Gap 4 and cheap: one table, one screen.
6. **Graph filters and typed edges** (Obsidian, Anytype). Small, and Road Map's data already carries
   the edge kinds.

Things that scored high on Reach and low on Fit — Coda's packs, Miro's boards, ClickUp's everything,
Mem's inference, TimeTree, Calendar.com — are the constraint working as intended: they are what
"no collaboration, no server" costs, and the survey accepted that cost knowingly.

---

## 6. Proposed features and screens

Ordered by dependency, then by Value. Each names what it needs decided. Nothing here is decided by
this document.

| # | Proposal | From | New data | New surface | Depends on | Decision needed |
|---|---|---|---|---|---|---|
| 1 | **Task model depth** — `priority`, `parentEntryId` (sub-tasks), `deferDate` (Things' *When*; OmniFocus' defer), `estimate: Duration`, `tags` (reuse the `Tag`/`PageTag` shape) | OmniFocus, Things, TickTick | `Entry` columns; one migration | Task row and add sheet grow; a **Someday** section beside the undated toggle | — | Which of the five fields; whether *defer* replaces or joins `startDate`'s semantics |
| 2 | **Habit history** — `HabitCompletion(habitId, date, value?)`; streak becomes derived | Loop | one table; `streak` kept as a cache or dropped | **Habit detail screen**: heat-map, score curve, best streak; optional numeric target | — | Whether habits become measurable (a value column) now or later |
| 3 | **Natural-language Quick Add** — one line → Task *or* Event with date, time, span, recurrence, and (#1) priority | Fantastical, Todoist | none | Quick Add on Calendar *and* Tasks; a parse preview under the field | #1 for priority | Grammar scope: English-only first; which recurrence phrases |
| 4 | **Schema on a tag** — a `Tag` may own `Property` rows; a page carrying it is a row of that tag's database; a tag's own page *is* that database | Tana, Anytype, Capacities, Obsidian Bases | `Tag.schemaPageId` or a `TagSchema` table; reuse `Property`/`PropertyValue` | The tag chip opens a database view of everything tagged | — | The one genuinely reopened data-model question; decide *before* #5 or #8 |
| 5 | **Linked database views in a page** + **sub-page create/move** | Notion | a `DATABASE_VIEW` block type carrying `pageId + viewId` | The block; the `···` menu (already the blocker for two scope rows) | #4 if tags are databases | Whether a linked view is a block or an embed card (X2 chose static previews) |
| 6 | **Calendar edit path, drag-to-move, agenda view, calendar sets** | Fantastical, stock | an `Entry.color`/category, or reuse tags | Agenda as a fourth view; long-press-drag on Day/Week | #3 makes the sheet optional | Whether "sets" are tags (#1) or a new field |
| 7 | **Calendar layers** — every database's date properties, habits, ICS import/export | Notion Calendar, Fossify | ICS mapping only | Layer toggles in Calendar; `Show Habits` finally built | — | Which layers ship first; VTODO for tasks or VEVENT only |
| 8 | **Auto-scheduling ("Plan")** — flexible tasks with an estimate and a deadline are placed into free slots; re-flow on change | FlowSavvy, Akiflow, TickTick Arrange | `Entry.isFlexible`; the scheduled slot stored as `startDate/Time` | A **Plan** mode on Day view: timeline left, unscheduled tasks right, drag either way | #1 (estimate), #6 (drag) | Whether placement is automatic or drag-only at first |
| 9 | **Time tracking** — start/stop on any Entry or Habit; a `TimeLog` table; planned-vs-actual on the Day view and the Habit detail | Toggl, Super Productivity, Sunsama | `TimeLog(entityUid, start, end)` | A running-timer chip in the topbar; totals on #2's screen | #1 (estimate) for the *planned* half | Whether a timer survives process death via a notification (§9.7 already solves the reliability half) |
| 10 | **Review** — a weekly walk through every project/database with stale rows, à la OmniFocus; Sunsama's weekly summary | OmniFocus, Sunsama | `Page.lastReviewedAt` | A **Review** screen reachable from Tasks | #9 for time totals (optional) | Cadence per database, or one global |
| 11 | **Quick switcher + command palette** | Obsidian | none | An overlay off the search icon, mode-switched by prefix (`>` for commands) | — | Reopens §3.1.7's explicit deferral; the FTS title-not-indexed gap must be fixed first |
| 12 | **Page revision history** | Notion, BookStack | `BlockRevision` or page-level JSON snapshots on a cadence | A "History" entry in `···` | — | Storage budget: per-save, or per-N-minutes; whether revisions sync (they should not) |
| 13 | **Unlinked mentions, block references, deeper nesting** | Obsidian, Logseq | `parent_block_id` already exists; a `BLOCK_REFERENCE` span style | Backlinks panel grows a second section | — | Nesting depth cap; whether block refs render inline or as cards |
| 14 | **Road Map filters, colour, local depth, typed edges** | Obsidian, Anytype | none — edges already carry a kind | Filter chips over the canvas; a "from this page, depth N" entry in `···` | — | Journal pages excluded by default (Logseq's answer) |
| 15 | **Journal shows today** — today's Entries and Habits at the top of the Journal page | Reflect | none | A read-only strip above the blocks | — | Whether it is live or a snapshot inserted as blocks |
| 16 | **Canvas depth** — nested canvases, frames/sections, mind-map layout, Canvas templates | Heptabase, Miro, Storyflow | `canvas_nodes.kind = CANVAS`, a `frame` node | Canvas toolbar | — | Whether a nested canvas is a page-embed card of kind CANVAS (probably yes — no new entity) |
| 17 | **Timeline/Gantt view + task relations** | Airtable, Vikunja, ClickUp | `ViewType.TIMELINE`; `Entry` relation reuses #1's `parentEntryId` or DB1's `RELATION` | Fifth database view | #1 | Whether "blocked-by" is a property or a relation kind |
| 18 | **AI, opt-in** — selection verbs (rewrite/expand/summarise); an agent over the Markdown export with the person's key; a local-model option | Agent-Native Content, OpenKnowledge, AppFlowy | key already in Settings (§3.5) | A verb row on the selection toolbar | #37's export | C4 is already "build"; this only picks the first verbs |

**Screens this adds**, in total: Habit detail (#2), Agenda view (#6), Plan mode (#8), Review (#10),
Quick switcher (#11), History (#12), Timeline view (#17). Everything else lands on an existing
screen.

**Order.** #1 and #2 are pure schema work with no design ambiguity and unblock #3, #8, #9 and #17;
they are the natural first two. #4 is the one to *discuss* rather than build, because #5 and #8's
data shape changes depending on the answer. #11's precondition — titles are not in the FTS index —
is a defect regardless and can be fixed now.

---

## 7. Sources verified this session

Three list entries were not identifiable from prior knowledge and were checked:
[Agent-Native Content](https://www.agent-native.com/apps/content/),
[OpenKnowledge](https://github.com/inkeep/open-knowledge),
[Storyflow](https://storyflow.so/novel-planner). Every other score is from product knowledge
current to spring 2026 and should be treated as *asserted* in the sense `docs/scope-decisions.md`
uses the word: nothing was measured against a running copy of any of these apps.
