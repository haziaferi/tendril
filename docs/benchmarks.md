# Benchmarks — what the bar is, per surface

**Dated 2026-09-11.** Input to the objectives file's "the bar" section.
**§13 (desktop layout) added 2026-09-13**, with its own frame of reference; **§13.7 (themes and colour) and §13.8 (data colour) 2026-09-14.** Forty-eight names were
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
| 2 | **Habit presence** — a completion log, shown as presence and never as absence; the streak retired from the row. *Rewritten 2026-09-11 — see §10.3; the original "history with heat-map and score" is withdrawn.* | Daylio, Finch, Tiimo | one table; `streak` becomes derivable | A quiet **Habit detail**: "four times this month", "usually mornings", "last: Tuesday" | — | Whether a one-tap mood/energy check-in joins the layer, and when |
| 3 | **Natural-language Quick Add** — one line → Task *or* Event with date, time, span, recurrence, and (#1) priority | Fantastical, Todoist | none | Quick Add on Calendar *and* Tasks; a parse preview under the field | #1 for priority | Grammar scope: English-only first; which recurrence phrases |
| 4 | **Schema on a tag** — a `Tag` may own `Property` rows; a page carrying it is a row of that tag's database; a tag's own page *is* that database | Tana, Anytype, Capacities, Obsidian Bases | `Tag.schemaPageId` or a `TagSchema` table; reuse `Property`/`PropertyValue` | The tag chip opens a database view of everything tagged | — | The one genuinely reopened data-model question; decide *before* #5 or #8 |
| 5 | **Linked database views in a page** + **sub-page create/move** | Notion | a `DATABASE_VIEW` block type carrying `pageId + viewId` | The block; the `···` menu (already the blocker for two scope rows) | #4 if tags are databases | Whether a linked view is a block or an embed card (X2 chose static previews) |
| 6 | **Calendar edit path, drag-to-move, agenda view, calendar sets** | Fantastical, stock | an `Entry.color`/category, or reuse tags | Agenda as a fourth view; long-press-drag on Day/Week | #3 makes the sheet optional | Whether "sets" are tags (#1) or a new field |
| 7 | **Calendar layers** — every database's date properties, habits, ICS import/export | Notion Calendar, Fossify | ICS mapping only | Layer toggles in Calendar; `Show Habits` finally built | — | Which layers ship first; VTODO for tasks or VEVENT only |
| 8 | **Auto-scheduling ("Plan")** — flexible tasks with an estimate and a deadline are placed into free slots; re-flow on change | FlowSavvy, Akiflow, TickTick Arrange | `Entry.isFlexible`; the scheduled slot stored as `startDate/Time` | A **Plan** mode on Day view: timeline left, unscheduled tasks right, drag either way | #1 (estimate), #6 (drag) | Whether placement is automatic or drag-only at first |
| 9 | **Time tracking** — start/stop on any Entry or Habit; a `TimeLog` table; planned-vs-actual on the Day view and the Habit detail | Toggl, Super Productivity, Sunsama | `TimeLog(entityUid, start, end)` | A running-timer chip in the topbar; totals on #2's screen | #1 (estimate) for the *planned* half | Whether a timer survives process death via a notification (§9.7 already solves the reliability half) |
| 10 | **Review** — a weekly walk through every project/database with stale rows, à la OmniFocus; Sunsama's weekly summary | OmniFocus, Sunsama | `Page.lastReviewedAt` | A **Review** screen reachable from Tasks | #9 for time totals (optional) | Cadence per database, or one global |
| 11 | **Quick switcher + command palette** | Obsidian | none | An overlay off the search icon, mode-switched by prefix (`>` for commands) | — | Reopens §3.1.7's explicit deferral; the FTS title-not-indexed gap must be fixed first |
| 12 | **Page revision history** | Notion, BookStack | `BlockRevision` or page-level JSON snapshots on a cadence | A "History" entry in `···` | — | *Decided 2026-09-13 (§0.6.13, step 8e):* title + blocks; before an edit at most every ten minutes, before a merge when the body differs (the LWW loser kept), before a restore always; fifty per page; never synced or archived. |
| 13 | **Unlinked mentions, block references, deeper nesting** | Obsidian, Logseq | `parent_block_id` already exists; a `BLOCK_REFERENCE` span style | Backlinks panel grows a second section | — | *Decided 2026-09-13 (§0.6.12, step 8d):* a BLOCK_REFERENCE **block** (not a span — inline spans are not tappable in this editor), live text, cached words, uid by value in the snapshot; Unlinked mentions with *Link*. Depth: unlimited since step 3. ~~Nesting depth cap; whether block refs render inline or as cards~~ |
| 14 | **Road Map filters, colour, local depth, typed edges** | Obsidian, Anytype | none — edges already carry a kind | Filter chips over the canvas; a "from this page, depth N" entry in `···` | — | *Decided 2026-09-13 (§3.4 amended, step 8c):* Journal hidden by default, kinds, one label; edge kinds tinted; "Show on Road Map" from a page's `···`, depth on the map. Shared with the desktop. |
| 15 | **Journal shows today** — today's Entries and Habits at the top of the Journal page | Reflect | none | A strip above the blocks | — | *Decided 2026-09-13 (§3.1.4 amended, step 8b):* live, never blocks — the export and the FTS row stay the person's text; **checkable**, through the Tasks tab's own use cases; today's page only. |
| 16 | **Canvas depth** — nested canvases, frames/sections, mind-map layout, Canvas templates | Heptabase, Miro, Storyflow | `canvas_nodes.kind = CANVAS`, a `frame` node | Canvas toolbar | — | *Checked 2026-09-13 (step 8c):* a nested canvas **is** a page-embed of a Canvas page (no new entity); the mind-map layout is the outline block's (§0.6.2). Frames/sections and canvas templates → §0.10 item 15. |
| 17 | **Timeline/Gantt view + task relations** | Airtable, Vikunja, ClickUp | `ViewType.TIMELINE`; `Entry` relation reuses #1's `parentEntryId` or DB1's `RELATION` | Fifth database view | #1 | *Decided 2026-09-13 (§0.6.14, step 8f):* a bound RELATION **property** (`blockedByPropertyId`), not a relation kind; Timeline at one day scale, drag = the Table's date write. Not on standalone tasks. |
| 18 | **AI, opt-in** — selection verbs (rewrite/expand/summarise); an agent over the Markdown export with the person's key; a local-model option | Agent-Native Content, OpenKnowledge, AppFlowy | key already in Settings (§3.5) | A verb row on the selection toolbar | #37's export | *Decided 2026-09-13 (§0.6.15, step 8g):* the three verbs, key-gated, a result sheet (nothing written until chosen), only the selection sent; the agent and the local model → §0.10 item 17. |

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

---

## 8. A single page — structure and features

**Added 2026-09-11, second pass.** §1 scored Pages as a *surface* — the hub, the tree, search,
templates. This scores what happens inside *one open page*: its header, its editing model, what a
block can be, how deep they nest, what can be transcluded, and whether anything spatial can live in
the body. The frame is a **Notion Plus page**: icon, cover, a properties header when the page is a
row, blocks with columns, sub-pages as blocks, embeds, a table of contents, and history.

Tendril's page today, for the `Gap` column: icon and title; a properties header only when the page
is a database row (§5.1); tags (§3.1.6); the §3.1.1 block inventory rendered in one column; one
level of list nesting, enforced by `indentTargetFor` rather than by the schema — `Block.parentBlockId`
is unbounded and `BlockOutline` already computes a `depth`; page mentions inline; backlinks at the
foot; no columns, no sub-page block, no transclusion, no table block, no TOC, no history; nothing
spatial in the body by decision (§3.7).

| App | Editing model | Reach | Fit | Gap | Value | Take, for the page |
|---|---|---|---|---|---|---|
| **Notion** (frame) | Block WYSIWYG | 5 | 4 | 3 | 2.4 | Columns; **sub-page as a block** (the page tree grown from inside a page — the `···` blocker again); toggle headings; a simple table block; a TOC block; synced blocks; equation; bookmark card; page cover; full-width toggle. |
| **Obsidian** | Markdown source, live preview | 5 | 5 | 3 | 3.0 | **A properties header on *any* page**, not only rows (frontmatter — proposal #4's other half); an outline pane built from headings; **section transclusion** (`![[page#heading]]`); footnotes; math. Fully local. |
| **Logseq** | Outliner | 4 | 5 | 3 | 2.4 | **Every block nests to any depth** — Tendril's schema already allows it, one function forbids it; block references; properties *on a block*; collapse any bullet. |
| **Tana** | Outliner + supertags | 5 | 3 | 4 | 2.4 | Fields on any node; **a node's children viewed as list / table / cards / calendar** — the same block tree read as a database, which is §5.6's views turned inward. |
| **Anytype** | Block WYSIWYG + objects | 4 | 5 | 3 | 2.4 | A type decides the page's **layout** (note / task / profile / collection) and which relations sit in its header; relations rendered in-page. |
| **Coda** | Doc-as-app | 5 | 3 | 3 | 1.8 | Inline formula chips in prose (`=Tasks.Count()`); buttons; a page hierarchy in the sidebar. Packs excluded. |
| **Capacities** | Block + objects | 4 | 3 | 3 | 1.4 | An object header with its properties; media (image, PDF, link) as first-class objects rather than blocks. |
| **Heptabase** | Cards on whiteboards | 4 | 3 | 3 | 1.4 | A card *is* a page and may sit on many whiteboards (`PAGE_EMBED` already does this); a mind-map layout of cards — see §9. |
| **Miro** | Whiteboard | 5 | 2 | 3 | 1.2 | Frames; a real **mind-map object with auto-layout** — the reference for §9's M1; shapes; sticky colours. |
| **xTiles** | Tiles | 3 | 3 | 3 | 1.1 | **A page as a grid of tiles** — a two-dimensional layout of blocks *without* an infinite canvas: columns generalised, bounded, and scrollable. The least gesture-hostile spatial page on the list. |
| **OpenKnowledge** | Markdown IDE | 3 | 4 | 2 | 1.0 | Split source/preview; agent co-authoring over local files (C4). |
| **BookStack** | WYSIWYG / Markdown | 3 | 3 | 2 | 0.7 | Revisions (#12); a TOC sidebar from headings. |
| **Scrintal** | Cards on a board | 3 | 3 | 2 | 0.7 | A card's body previewed on the board — the `PAGE_EMBED` card showing a few lines of the page, not only its title. Small, and worth it. |
| **AppFlowy** | Block WYSIWYG | 3 | 5 | 1 | 0.6 | Nothing Tendril lacks. Its editor is Flutter; not an implementation reference for Compose. |
| **Reflect** | Outliner | 3 | 2 | 2 | 0.5 | Daily-note calendar strip (#15). |
| **Storyflow** | Docs on a canvas | 3 | 2 | 2 | 0.5 | A document that opens *from* a canvas card, at depth — Canvas's page-embed already; templates per canvas. |
| **Lexical** | Editor *framework* | 4 | 2 | 1 | 0.3 | Scored here, and only here, because it is a page-structure reference even though it is not an app: an immutable node tree with **decorator nodes** — opaque non-text nodes (image, embed, preview) in the text sequence. Tendril's `IMAGE` block already is one; §9's canvas-preview block would be another. Nothing else transfers from JavaScript to Compose. |
| **Agent-Native Content** | MDX | 2 | 3 | 1 | 0.2 | Interactive components inside a document — not for this app. |
| **XWiki / ClickUp / Upbase / Superlist** | Various | 2–3 | 2 | 1 | 0.2 | Nothing beyond the rows above; Superlist's tasks-inside-notes is the `TO_DO` block. |
| **Mem** | Prose + AI | 3 | 1 | 1 | 0.1 | Skip. |

**One unlisted candidate worth naming: Craft.** Not on the brief's list, but it is the best-regarded
*mobile* page editor in this category — sub-pages as cards, a block model close to Notion's with far
better touch ergonomics, local files with optional sync. If one more app were added for this
section it would be that one.

**What this table says.** Three things score high and cohere:

1. **Depth**: arbitrary nesting (Logseq), block references (Logseq/Obsidian), section transclusion
   (Obsidian). Proposal #13 already carries these; the schema needs nothing.
2. **A header on every page**: typed properties on a plain page, decided by a type or a tag
   (Obsidian, Anytype, Capacities, Tana) — proposal #4 seen from inside the page.
3. **Children as views** (Tana): a page's block tree read as a table or a board. It is the one
   idea here that is *not* on the §6 list yet, and it is where #4 and #5 meet — if a tag can be a
   schema, and a linked view can be a block, then "show this page's sub-pages as a board" is the same
   block with `pageId = this page`.

---

## 9. In-page mind map and infinite canvas — viability

### 9.1 What already exists

An infinite canvas is **shipped** — as a page kind, not a block (§3.7). Measured on the tree:
node positions and sizes are floats in an unbounded content space; one `graphicsLayer` applies
`scale` and `pan`, zoom clamped 0.3×–2.5×; `TEXT` and `PAGE_EMBED` nodes; edges with `NONE /
ONE_WAY / TWO_WAY` direction and an optional label; View-Only gated at the write funnel; every
node and edge rides in its page's snapshot, so sync and encryption are already paid for. The UI
is 821 lines in `Tendril android/app/.../ui/canvas/` — **Android only**; desktop has no canvas.

§3.7 also recorded *why* not a block: a pannable, zoomable, gesture-hungry surface inside a
vertically scrolling list of editable text fields is a gesture conflict with no good resolution
on a phone, and it would make "nestable one level" apply to a two-dimensional thing. That
reasoning is sound and this section does not reopen it; it asks what "in-page" can mean *without*
that conflict.

### 9.2 Infinite canvas — what the benchmarks have that Canvas lacks

| Capability | Obsidian Canvas | Heptabase | Miro | Cost here |
|---|---|---|---|---|
| Group / frame node that contains others | yes | sections | frames | one node type + a parent field |
| Card colour | yes | yes | yes | one nullable column, same as `calloutColor` |
| Image and link nodes | yes | yes | yes | `IMAGE` node reusing the §9.4 image channel |
| Resize handles | yes | yes | yes | UI only — `width`/`height` are already stored |
| Edge anchors on a card's side | yes | — | yes | UI only |
| Multi-select and group move | — | yes | yes | UI only |
| Nested canvas | — | **yes** | — | a `PAGE_EMBED` whose target is a Canvas page: **no new entity** |
| Mind-map layout | plugin | **yes** | **yes** | §9.4, M1 |
| Body preview on a page card | — | yes | — | read the first blocks; small |
| Minimap / viewport culling | — | — | yes | needed before hundreds of nodes; not before |
| **Open interchange format** | **JSON Canvas** (`.canvas`) | — | — | an exporter beside `MarkdownExporter`; Obsidian opens the result |

Everything in that column is additive to the existing tables. Nothing requires a second canvas
model. The last row is worth singling out: JSON Canvas is an open spec Obsidian published for
exactly this data shape (nodes with `x, y, width, height, color`, edges with `fromNode / toNode /
fromSide / toSide / label`), and Tendril's `CanvasNode`/`CanvasEdge` map onto it field for field.
A `.canvas` file in the Markdown export zip (#37) would make a Tendril board open in Obsidian
unchanged — the same "readable without this app" argument that justified the export.

### 9.3 Canvas *in* a page — three options

| | O1 — preview block | O2 — live inline canvas | O3 — leave as a kind |
|---|---|---|---|
| What the reader sees | A card in the block list: title, node count, a static thumbnail; tap opens the canvas full-screen | The board itself, pannable, inside the page | Nothing in the page; the canvas is a sibling page |
| New data | a `CANVAS_EMBED` block (`Block.mentionedPageId` already points at pages; a kind check suffices) | the same block, plus a height | none |
| Gesture conflict | none — the thumbnail does not scroll or zoom | **the conflict §3.7 describes**, on every phone; tolerable on desktop with a mouse | none |
| Precedent | X2 (embeds as static preview cards); Lexical's decorator node; Obsidian's `![[board.canvas]]` renders exactly this | Miro-in-Notion iframes, which nobody enjoys on a phone | Obsidian, Logseq, Heptabase all keep boards as files |
| Effort | small: one block type, one composable that draws nodes at thumbnail scale from data already loaded for Road Map | large, and then a second gesture-arbitration layer to maintain | zero |

**Verdict: O1.** It is the in-page canvas every local-first benchmark actually ships, it costs a
block type, and it is the same decision X2 already made for embeds. O2 is the thing §3.7 rejected,
and the rejection holds. O3 is what exists; O1 is what "in-page" adds to it.

### 9.4 A user-created mind map — three options

| | M1 — Canvas layout mode | M2 — the outline *is* the map | M3 — a mind-map entity |
|---|---|---|---|
| What it is | A `layout = MIND_MAP` on `PageCanvas` with a `rootNodeId`; positions are computed from the edge tree instead of stored; "add child" creates a node and a `ONE_WAY` edge | A nested-list block subtree rendered as a tree: each list item is a node, its children are its branches; opened full-screen from the list's `···`, edited in either place | New tables for nodes and branches, a new screen, a new snapshot record |
| Precedent | Heptabase, Miro's mind-map object, Obsidian canvas mind-map plugins | **Xmind's outline mode, markmap, Logseq's and Obsidian's markmap plugins**, Workflowy — every one of them proves an outline and a mind map are the same data | Standalone mind-map apps |
| New data | two columns on `page_canvases` | **none** — `parentBlockId` is the tree; only `indentTargetFor`'s one-level cap (proposal #13) stands in the way | three tables |
| Searchable, exportable, synced | nodes are canvas text: not in FTS; exported as JSON Canvas (§9.2) | **already**: the text is block content, in FTS, in the Markdown export as a nested list, in the snapshot | all three from scratch |
| In-page | no — it is a canvas | **yes** — it is a rendering of the page's own blocks; a page can hold several | as a block, with O2's gesture conflict |
| Editing | on the board | in the outline *or* on the map — a node tap edits that block with the editor that already exists | a third editor |
| Layout algorithm | tidy tree (Reingold–Tilford) or radial, pure Kotlin, ~150 lines, unit-testable with no UI | the same code | the same code |
| Rendering | reuse `CanvasScreen`'s pan/zoom layer | reuse it too: the map is a read of `BlockOutline` drawn on the canvas layer | new |
| Effort | small on top of Canvas | small on top of #13; the layout code is shared with M1 | large |

**Verdict: M2 is the in-page mind map, and M1 is a Canvas feature.** They are not competitors:
M2 answers "I want to think in a tree inside this page", M1 answers "I want this board to lay
itself out". They share the layout code and the pan/zoom layer, and neither needs a new entity.
M3 is what §3.7 argued against — a third content model — and nothing in the benchmark set does it
that way.

M2 fits this codebase unusually well, for a reason worth stating: §3.1.1 chose plain text + spans
so that *what is indexed is what is stored*. A mind map that is a view of blocks keeps that
promise — the map is never a second copy of anything — where a mind-map entity would break it.

### 9.5 What is viable, and what it depends on

| Proposal | Viable | Depends on | Decision needed |
|---|---|---|---|
| 19 · **Canvas block** (O1, then §9.6) | yes, small | #24 | *Decided 2026-09-11:* the inline card is the live board, inert until tapped, then armed and grown in place — no separate screen. Thumbnail-vs-live drawing while inert is the one open detail |
| 20 · **Outline mind map** (M2) | yes, small once #13 lands | **#13** — lift the one-level cap (`indentTargetFor`) | *Decided 2026-09-11:* depth unlimited; inline, inert until tapped, then armed and grown in place to edit (§9.6) |
| 21 · **Canvas mind-map layout** (M1) | yes, small | Canvas; shares #20's layout code | Whether the root is chosen per board or inferred (the node with no incoming edge) |
| 22 · **JSON Canvas export** | yes, small | #37 | Whether `.canvas` files go in the Markdown zip or beside `.tendril` |
| 23 · **Canvas depth** — colours, groups, image nodes, nested boards, body preview | yes, additive | — | Order; nested boards are free (a `PAGE_EMBED` of a Canvas page) and could go first |
| 24 · **Canvas UI to `shared/`** | yes, a move | — | Precondition for every row above reaching desktop (C7); Compose gestures are common code, so the move is mechanical |
| — · Live inline canvas (O2) | **not recommended** | — | §3.7's reasoning stands; revisit only for desktop, with a mouse, and only if #19 proves insufficient |
| — · Mind-map entity (M3) | **not recommended** | — | Duplicates content outside FTS, export and the span model for no capability M1 + M2 lack |

**Risks, named.** Compose draws every canvas node today; past a few hundred nodes both Canvas and
the mind map need viewport culling, which is a `filter` on the node list before drawing and not
an architecture change. Text measurement for node sizing on the map is the one cost that grows
with content; Xmind and markmap both cap the visible text per node and show the rest on tap, which
`TEXT` cards already do. The Android-only Canvas UI is the real debt: every row above lands on one
platform until #24.

### 9.6 The gesture conflict, resolved — arm and grow (Decided 2026-09-11)

§9.3 and §9.4 accepted §3.7's premise that a pannable surface cannot live inside a scrolling
block list on a phone, and routed around it: a static preview, a read-only inline map. The premise
is true only for a surface that is *always* live. The conflict is ambiguity — a one-finger drag
over the map could mean "scroll the page" or "pan the map" — and it is removed by removing the
ambiguity. Three ways exist:

| | Arm-to-interact | Finger-count split | Boundary handoff |
|---|---|---|---|
| How | Inline, the surface is inert and scrolls with the page; a tap *arms* it (visible border, an "Editing" chip) and only then do pan, zoom and node-drag consume touches; tap outside, Done or Back disarms | One finger scrolls the page, two fingers pan and zoom the surface; node drag is long-press-then-drag | The surface consumes drags until its content reaches an edge, then the page scrolls |
| Precedent | Notion's embedded Miro/Figma; Obsidian's canvas embed (hover-capture); Apple Notes' inline sketches | Google Maps embedded in scrolling pages ("use two fingers to move the map") | Nested-scroll lists |
| Modeless | no — and that is its virtue: an explicit, visible mode is the neurodiverse-friendly property (§10); a heuristic that has to be guessed is not | yes, but needs a hint overlay, and long-press-to-drag inside an editable list is the very thing §3.7 worried about | yes, and feels unpredictable: the same swipe does different things depending on where the content was |
| Applies to | mind map and canvas | mind map and canvas | **mind map only** — an infinite canvas has no edge |
| In Compose | a child `pointerInput` that consumes a drag wins over the parent `LazyColumn`, so arming is attaching the consuming detectors conditionally — not a custom arbiter | a pointer-count check in one detector | a `NestedScrollConnection` |
| Desktop | the same rule resolves the mouse-wheel ambiguity (page scroll vs pan): wheel pans only when armed | n/a | n/a |

**Decided: arm-to-interact, with one refinement — the full-screen surface *is* the armed inline
one, grown in place.** Tap to arm; it expands to fill the viewport (a shared-element transition,
the way Notes opens a sketch); edit; Back shrinks it and disarms. One component at two sizes, in
place of two renderers and a per-map option. The map composable takes `interactive: Boolean`:
inert, it carries only `detectTapGestures { arm() }`; armed, it gains `detectTransformGestures`
and the node-drag detectors `CanvasScreen` already layers ("one gesture detector on the outer box;
card drags, the link drag and the arrow hit-test on the layers beneath", §3.7), plus a
`BackHandler` that disarms.

**Consequences, both decided the same day:**

- **The mind map (#20) is editable inline.** The "inline read-only" and "two options per map"
  wordings recorded earlier today are superseded; there is one map, inert until tapped.
- **The canvas block (#19) is the live board, not a preview.** This is the infinite canvas *in a
  page* that §9.3 said could not be had — O2 without O2's cost, because the board is inert until
  armed. O1's static thumbnail survives only as what the inert card may draw for speed; whether it
  draws nodes live or a cached image is the one detail left open. §3.7's decision that a Canvas is a
  page kind stands — the block *embeds* a canvas page (`Block.mentionedPageId` → a `kind = CANVAS`
  page), it does not nest a second canvas model inside the body.
- **Depends on #24.** Both land on desktop only once the Canvas UI is in `shared/`, which is why
  #24 was ordered first.

**Decided 2026-09-11, after §9 was read:** nesting depth is **unlimited** (Logseq's answer);
the outline mind map is one component at two sizes — inline and inert until tapped, then **armed and grown in place** to fill the viewport for editing (§9.6, superseding the earlier "inline read-only" and "two options per map" wordings the same day); and
**#24 goes first** — the Canvas UI moves to `shared/` before any canvas or mind-map row is built,
so nothing above lands on one platform.

---

## 10. The human layer — Habits are not a tracker

**Stated by the author, 2026-09-11, and now a design constraint** on the same footing as
offline-first: Habits exist to put a *human* layer into a productivity-shaped app — to keep
meditation, self-care, rest, the things that are not work, obligations or projects, visible as
things that *are and should be* part of a life. The app is to be **neurodiverse-friendly** —
autism, ADHD, depression named explicitly. Any "did I do it?" and "when, and how many times?"
risks turning those practices into one more thing that needs strict upkeep, which is the
opposite of why the layer exists.

That reframes §3's Habits rows and proposal #2, and it changes what the benchmark for Habits is.

### 10.1 What this means for what already exists

`Habit` carries `streak`, `previousStreak`, `lastCompletedDate`. **A streak is the most
pressure-shaped number a habit can carry** — it is a chain that *breaks*, and every "don't break
the chain" app is built on that anxiety. §6.1's rule that a missed instance creates no backlog was
already pointing the right way; the streak counter on the row is the half that still points the
other way. The §8.1.1 quick-check widget is right: it asks nothing, it offers a tap.

A completion *log*, by contrast, is neutral data. Nothing about a row that says "meditated,
Tuesday" is a demand; the pressure lives entirely in the **framing** of what is shown. Proposal #2
as written — heat-map, score curve, best streak — was Loop's framing, and Loop is a tracker. It is
withdrawn in that form. What replaces it is in §10.3.

### 10.2 Benchmarks for a human layer

None of the forty-eight were chosen for this. These were, and one of them is a paid,
full-featured option and so becomes the frame:

| App | Reach | Fit | Gap | Value | Take |
|---|---|---|---|---|---|
| **Tiimo** (frame; paid) | 5 | 4 | 4 | 3.2 | Built *for* ADHD and autism: a **visual day** as a timeline of coloured blocks rather than a list; visual timers; a **focus mode** that shows one thing; routines as sequences of small steps; gentle notifications ("it's time for", never "you missed"); a widget that shows *now and next*. AI task breakdown is its one cloud feature and is opt-in. |
| **Finch** | 4 | 3 | 4 | 1.9 | Self-care framed as caring for something else (a bird); every check-in is a *gift*, missing one is *nothing*; energy level and mood as one tap; "small steps" as the unit of a goal. The framing, not the pet, is what transfers. |
| **Amazing Marvin** | 5 | 4 | 3 | 2.4 | ADHD-oriented by design: every feature is a **strategy you switch on**, the app ships nearly empty — the purest example of E12's progressive disclosure on the list. Its "Do it anyway" and "Day Planner" strategies are gentle by construction. |
| **Routinery** | 3 | 4 | 3 | 1.4 | Routines as timed step sequences with a running visual timer; morning/evening framing. |
| **Llama Life** | 3 | 4 | 3 | 1.4 | One list, each item with a time, a running timer for the current one; "a day you can actually see". A shape Merged (§3.3) could take. |
| **Daylio / Bearable** | 3 | 5 | 3 | 1.8 | A **one-tap mood or energy check-in** with no scoring; a month shown as colours, never as a percentage. Local, private. The right shape for any habit "history" here. |
| **Goblin.tools** | 3 | 2 | 3 | 0.7 | Breaking a task into steps, and a "how spicy is this" estimate — both AI, so C4-gated; the *questions* are worth keeping even without the model. |
| **Loop Habit Tracker** (rescored) | 3 | 5 | 2 | 1.2 | Its **score** — a decaying average that recovers quickly — is *gentler* than a streak and is the one number worth keeping if any number is shown. Its heat-map is not. |
| **Fabulous / Habitica / Streaks** | — | — | — | — | Not scored: gamified upkeep is the pattern this layer exists to avoid. |

### 10.3 Proposal #2, rewritten — and a principle for the rest

**#2 · Habit presence (replaces "Habit history").** Keep a completion log, because without one
no gentle view can exist either — "you last sat down to meditate on Tuesday" needs the Tuesday.
Then:

- **Show presence, never absence.** "Four times this month." "Usually mornings." "Last: Tuesday."
  No misses, no gaps, no red, no percentages, no chain. A month view, if any, is Daylio's:
  a colour where something happened and *nothing* — not an empty cell — where it did not.
- **Retire the streak from the row.** It stays as a computed number behind a disclosure for the
  person who wants it (E12), defaulting off. The `streak` columns become derivable and can go in
  a later migration; nothing needs to break now.
- **Drop the score, the heat-map and the best-streak** from the proposal. If a single number is
  ever shown, it is Loop's decaying score, opt-in, because it forgives.
- **Language.** A habit is *offered* ("Meditation is here if you want it"), not *due*. The
  quick-check widget already speaks this way; the Habits tab and any notification follow it.
- **A one-tap check-in** — mood or energy, Daylio-shaped — is the natural extension of the layer
  and is *not* a habit; it is a note about the person. Deferred, named here so it is not designed
  as a tracker later.

**The principle, generalised to Tasks.** The same author's reasoning applies, less absolutely,
to §6's task-model proposals: priority flags, overdue badges and deadline countdowns are pressure
mechanisms too. Things 3 is the frame for restraint — a task has a *When* (a plan, movable without
guilt), an optional *Deadline* (rare, real), and a *Someday* (kept without being scheduled). That
is the model §11 recommends for `Entry`, and it is why "priority" there is a single opt-in flag and
not a scale.

Tiimo's *visual day* and Llama Life's *one thing with a timer* are the shapes proposals #8 (Plan)
and #9 (time tracking) should take when they are designed — a timeline you can see, and a "now",
not a workload chart.

---

## 11. The five `Entry` fields, and what each one actually is

Proposal #1 named five fields. Measured against the tree, one of them is not what it looked like.

| Field | What it means | What exists today | Frame | Pressure? |
|---|---|---|---|---|
| **A second date** | Things separates *When* (the day you plan to do it; moving it is fine) from *Deadline* (the day it is late; rare). OmniFocus calls them defer and due. | `Entry.startDate` is the **only** date on a task. It is where Calendar draws it *and* what §5.2 binds as `deadlinePropertyId`. So today it is *both*. | Things | The deadline is; the When is not. |
| **`parentEntryId`** — sub-tasks | A task made of smaller tasks; the parent completes when its children do, or independently (Things: checklist inside a task; TickTick: real sub-tasks with their own dates). | Nothing. A to-do database row can hold `TO_DO` blocks, which are checklist items, not entries. | Things (checklist) is the lighter shape; TickTick the heavier. | No — breaking a thing into steps is the ADHD-friendly move (Goblin.tools exists for exactly this). |
| **`estimate`** | How long it is expected to take. | Nothing on `Entry`; `Habit.duration` exists. | Sunsama, Super Productivity | Mild, and only if compared against actuals. It is what #8 needs to place a task at all. |
| **`priority`** | An importance rank. TickTick/Todoist: four levels. Things: none — only a "This Evening" bucket and a star. | Nothing. | Things (none) | **Yes** — a scale invites triage guilt. |
| **tags** | Free labels on an entry, reusing `Tag`/`PageTag`'s shape. | `Tag` exists for pages only. | TickTick, Todoist | No. |

**Recommendation, given §10.**

1. **Keep `startDate` as *When*** — that is what it already does — **and add an optional
   `dueDate`.** The §5.2 binding's name is then wrong (it binds the When) and gets a second,
   optional binding for the deadline; the spec's word "deadline" is corrected to "date". Adding
   `deferDate` instead would keep `startDate` as the deadline and make every existing task a
   deadline retroactively, which is the pressure-shaped reading.
2. **`parentEntryId`** — yes, Things' checklist shape first: children have no dates of their own
   until someone asks for that.
3. **`estimate`** — yes, optional, shown nowhere until #8 or #9 uses it.
4. **`priority`** — **a single opt-in flag** ("important"), not a scale. Progressive disclosure:
   the field is hidden until enabled in Settings.
5. **tags on entries** — yes, but *after* §12 settles what a tag is, since it may become a schema.
6. **`Someday`** — not a field: it is the existing undated task, given a name and a section.

**Decided 2026-09-11 — all six as recommended**, plus one addition from the author: a
**Postpone** control on a task that moves its date forward by a chosen amount — minutes, hours,
days, months. Recorded as stated, with one question flagged rather than answered: the author named
`dueDate`, and under the When/Deadline split it is the *When* that a person postpones without
guilt (Things' "move to tomorrow"), while moving a deadline is a rarer, deliberate act. The
control should probably move the When by default and the deadline only when that is the date
being looked at; to be settled when #1 is built.

---

## 12. Schema on a tag — the discussion

### 12.0 Plainly, first

**Tags keep meaning what they mean.** A tag is a label; things with the same tag are related;
tapping the chip shows them together. Nothing here changes that, and most tags stay exactly that.

**The addition is one sentence:** *a tag may also bring fields with it.* Not every tag — only a
tag that a database has chosen to bind to. Tag a page with one of those, and the page gains that
database's fields in its header and appears in that database's views. Remove the tag, and it
loses them. The page never moves.

**Why that matters, with the app as it is.** Say there is a *Books* database with the fields
Author, Status and Rating, and elsewhere in the tree — under *Reading*, say — a plain page called
*Notes on Dune*. Today, for that page to have an Author or a Status it must **become a row**: be
created inside Books, or moved into it, because a row is a page whose `databaseId` says which one
database it belongs to. The page's place in the tree and its place in a database are the same
fact. Schema-on-a-tag separates them: *Notes on Dune* stays under *Reading*, is tagged `#book`,
and is now also a row of Books — in its table, on its board, with its three fields filled in at
the top of the page. Tag it `#2026-goals` as well, if that tag is bound to a Goals database, and it
carries both headers. Untag it and it is a plain page again, in the same place, with its body
untouched.

**What changes day to day, and what does not:**

- Creating a database, adding rows inside it, its views, Sync-to-Tasks — unchanged.
- The tag chip row, the tag editor on a page, the filter — unchanged. A bound tag looks like any
  other tag, with a small mark that it brings fields.
- A database's `···` menu gains one item: *Bind a tag*. Until someone uses it, the app is today's
  app (E12).
- A page's header can show fields without the page living inside a database. That is the new
  sight.
- Tagging a page into a to-do database (one with §5.2's binding) makes it a task, because that is
  what being a row of that database means. The first time a person applies such a tag, the app
  says so and asks once.

**Are the three shapes different mechanisms?** They are three answers to one question — *who
owns the fields?* — with the same outcome for the person: a page anywhere can carry structured
fields and be seen alongside its kind.

| | Who owns the fields | What the tag does | What is new |
|---|---|---|---|
| **A** | The database, as today | Is the doorway: carrying it makes a page a row of that database | One column on the database saying which tag is its doorway |
| **B** | A new thing called a *type*; every page has exactly one | Nothing — a type replaces the tag's role for this purpose | The type entity, a type picker on every page, and a second thing beside databases that also owns fields |
| **C** | Nobody: any page can have any field | Is one of the things a view can filter by | Fields without an owner, and views that are queries rather than a database's own |

A is the smallest change because the owner already exists and the doorway already exists; only
the link between them is added. B builds a second owner. C removes the owner. Same outcome,
different amount of the current app kept.

### 12.1 What it is

Tana's supertag, Anytype's type, Capacities' object, Obsidian's Bases: *attach a schema to a
label, and everything carrying the label is a row*. The page stays where it is in the tree and
keeps its body; it gains a properties header and appears in the schema's views. A page may carry
more than one such label and so be a row in more than one place.

### 12.2 What Tendril has, measured

- `Tag(id, name, color)` and `PageTag` — a many-to-many label, flat, on pages only.
- A database is a `kind = DATABASE` page plus a `PageDatabase` companion; its `Property` rows
  carry `databaseId`; a **row is a `Page` with `databaseId` set** — single-valued, so a page is a
  row of at most one database.
- `PropertyValue(propertyId, rowPageId)` is keyed by *page*, not by membership. **This is the
  hinge**: a value already lives on the page, so "row of two databases" needs nothing new here.

### 12.3 Three shapes

| | **A — a database can own a tag** | **B — Types** (Anytype) | **C — Bases** (Obsidian) |
|---|---|---|---|
| The idea | A database may be bound to one `Tag`. A page carrying that tag is a row of that database. Native rows (created inside the database) get the tag automatically. | A new `PageType` entity with properties; every page has exactly one type; a "Set" is a query by type. | Properties on any page with no owner; a "database" becomes a saved query (by tag, kind, property) plus a schema it *suggests*. |
| New data | `PageDatabase.tagId: Long?` — one nullable column. `PageTag` becomes the membership relation; `Page.databaseId` stays as the *home* (where the row was created and is listed by default). | `PageType`, `Page.typeId`, properties re-parented to types. | `Property.databaseId` becomes nullable (a global property pool); `PageDatabaseView` gains a query; migration of every database into "view + suggestion". |
| Multiple schemas on one page | yes — one tag per database, any number of tags per page | no — one type | yes |
| Reuse of what exists | everything: properties, values, views, bindings, the tag chip, the tag editor | properties and values; views need a second source | values; views and properties are restructured |
| Where it shows | the page's header gains one property group per schema-tag; the tag chip in the Pages hub opens that database's views; the database lists native rows *and* tagged pages | a type picker on every page; type pages | a query builder on every view |
| §5.2 Sync-to-Tasks | tagging a page into a to-do database makes it a task — Tana's `#task` behaviour; needs a one-time confirmation the first time a schema-tag with a binding is applied | same | same, but the binding has no owner to hang on |
| Untagging | the page stops being a row; its `PropertyValue`s stay (absence never implies deletion, §9.4) and are hidden, purged with the tag's own trash | type change: values for the old type orphaned | values stay; nothing owns them anyway |
| Effort | small: one column, the membership query, a header renderer, the confirmation | medium; a parallel concept beside databases | large; a reframe of §5 |
| Matches | Tana exactly; Capacities closely | Anytype | Obsidian |

### 12.4 Recommendation, and what needs deciding

**Shape A.** It is one nullable column plus a query, it reuses every existing surface, and it is
the Tana semantics — the one the objectives survey named as "exceed" material. B duplicates
databases under another name. C is the most flexible and the most expensive, and Obsidian only
gets it cheaply because its properties are freeform frontmatter with no owner to begin with.

**Progressive disclosure (E12):** a database does not get a tag by default. Its `···` menu offers
*"Bind a tag — pages tagged #name become rows here"*, and only then does anything change. A
person who never touches it has exactly today's app.

Decisions the author owns:

1. **A, B or C?** (Recommendation: A.)
2. **Opt-in per database** (recommended) **or automatic** — every database owns `#its-name` from
   creation?
3. **Untag = hide values, or delete values?** (Recommendation: hide; purge with the tag's trash —
   consistent with §5.5.1.)
4. **Where does a tagged, non-native row appear?** In the database's views (yes, that is the
   point); in the database's Pages-hub listing as a child (recommendation: no — it lives where it
   lives; the database *shows* it, does not *contain* it).
5. **One confirmation the first time a binding-carrying schema-tag is applied**, or a per-page
   prompt every time? (Recommendation: once per tag.)

### 12.5 Decided 2026-09-11 — A, opt-in, hide, show; and the two words that confused it

**Shape A, opt-in per database** — agreed by the author. The other three were decided on the
recommendation, after the following clarification, which is recorded because the confusion will
recur for anyone coming from Notion.

**Two different things are both called "tags".** Notion's *Tags* is a **property** — a Select or
Multi-select column that exists inside one database; its values are rows' values, and a page has
it only because it is a row there. Tendril's tags (§3.1.6) are **labels outside any database**,
the Joplin/Bear/Obsidian kind, applied to any page. Notion has no equivalent of the second, and
Tendril already has both: a database can have a Select property named anything, including "Tags",
and that is untouched by §12. Schema-on-a-tag concerns only the §3.1.6 label.

**Which values are hidden when a bound tag is removed.** Exactly the page's values for *that
database's* properties — *Notes on Dune*'s Author, Status and Rating, and nothing else. Its body,
its other tags, its place in the tree, and any values it carries for a *different* bound tag stay
as they are. **Decided: hide, not delete.** Re-applying the tag restores them; removing a tag by
accident is common and this makes it free. They are purged when the database itself is deleted
forever (§5.5.1.1), which is the same lifecycle their row-mates already have. Absence never implies
deletion (§9.4) is the same rule one level down.

**"Contain" versus "show".** In Notion a row lives *inside* its database — the database is its
only home, so deleting the database deletes the row. Tendril's native rows are the same, and stay
the same. A *tagged* page already has a home — *Notes on Dune* lives under *Reading* — so the
question was never whether it appears in the database (it does: **a full row in every view**,
fields editable in the table, a card on the board, opens the page on tap — never a title as plain
text or a dead link). The question was only two smaller things, both **decided as "show"**:

- **Deleting the Books database does not delete *Notes on Dune*.** It loses its three fields and
  stays under *Reading*. A native row, which has no other home, goes to the trash with its
  database as today.
- **The Pages-hub tree lists *Notes on Dune* once, under *Reading*** — not a second time under
  *Books*. Its membership is visible from the tag chip and from the database's views. Notion's own
  precedent is the linked view: rows that live elsewhere, shown in full.

So "show" does not mean a weaker kind of row. It means the page keeps its own home.

**Naming, decided the same day:** the §3.1.6 feature is called **Label** from here on — in the
UI, in the objectives file, and in the spec on its next pass — so that "tag" can keep meaning
what Notion users expect (a Select property inside a database). The code's `Tag`/`PageTag` rename
is mechanical and separate; until it lands, the word in the tree is the old one.

---

## 13. Desktop layout — the bar, and the pass (2026-09-13)

`tendril-spec.md` §0.10 item 14 asked for this: the desktop app draws the phone's layout in a
window driven by a keyboard and a mouse, and the question is what a desktop app of this purpose
looks like instead. This section scores the bar on six *layout* axes, reads the pattern off the
tables, and ends in a pass of ordered PRs whose decisions were answered the same day on an
interactive mock — `docs/mockups/desktop-shell.html`, a clickable window with the seven questions
as controls and a *Today* preset for the baseline. Scores are asserted in §7's sense: nothing was
measured against a running copy.

### 13.1 What the desktop does today — measured

From the code, not from memory. The shared `WorkbenchScaffold` draws a Material `NavigationBar`
of the five `WorkbenchDestination`s at the bottom of the window, with the `RunningTimerBar` above
it (`WorkbenchScaffold.kt`, `bottomBar`). Every modal is a `TendrilSheet` (29 call sites, all
fully expanded since the 2026-09-12 sheet frame) or an `AlertDialog`/`DatePickerDialog` (31);
there is no popover anywhere. `Main.kt` opens a `Window` with no `WindowState`: the default size
(about 460 px tall on Windows), no remembered position, the title *Tendril (desktop preview)*.
Rows are phone rows — 48–56 dp list rows, `CELL_WIDTH = 160.dp` table cells, `DAY_WIDTH = 36.dp`
timeline days — and the type scale is the phone's. Two shortcuts exist (Escape = back, Ctrl+K =
the switcher) and the switcher's `>` commands are the only command list. The theme is fixed
(Ink, Light, Sans); the picker is Android's. Every tab is shared — no stand-ins remain (windows
spec §8) — so this is a *layout* gap, not a feature gap. That is the `Gap` column's baseline.

### 13.2 The bar, per axis

Six axes, defined once. **Navigation**: where the top-level sections live. **Panes**: list +
detail, a collapsible tree, an inspector. **Modal input**: how a quick edit is asked for — a
popover at the pointer, inline, a centred dialog, a slide-over; never a bottom sheet. **Keyboard**:
palette, a fixed global set, arrow keys in lists, quick-add. **Density**: row height, type, hover
affordances. **Window**: remembered size and position, a minimum, multi-window. `R/F/G/V` is
Reach / Fit / Gap / Value as everywhere else in this file: Reach is how much of the desktop
surface the app covers (5 = the frame), Fit how much of its answer suits a shared Compose shell
under Tendril's constraints, Gap how far §13.1 is from it, Value the weight of what to take.

**The frame** — Notion for the shell, Obsidian for the keyboard, Things for the list.

| App | Navigation | Panes | Modal input | Keyboard | Density | Window | R/F/G/V | Take |
|---|---|---|---|---|---|---|---|---|
| **Notion** | Left sidebar holding the page tree; collapsible (Ctrl+\), resizable, hover-reveal | Tree + page; a page opens in place, or as a **side peek** / centre peek | Property edits in **popovers**; peeks for pages; dialogs only for destructive confirms | Ctrl+K search-and-command, Ctrl+N, Ctrl+Shift+N new window, Ctrl+[ ] history | Small-text and full-width per page; `···` and drag handles on hover | Remembered; multi-window; follows the OS theme | 5/4/4/1.0 | The sidebar's *collapse + resize*, popovers for properties, the side peek as the desktop form of a sheet. |
| **Obsidian** | Left **ribbon** of icons + a collapsible left pane (files, search) and right pane (backlinks, outline), both resizable | Tabs and split panes; every pane a view | Centred modals (palette, switcher, settings) | Ctrl+P palette, Ctrl+O switcher, every hotkey rebindable, `?`-style listing in Settings | Theme-driven; "readable line length" | Remembered; pop-out windows; follows the OS | 5/3/4/0.8 | The icon rail + collapsible tree is the exact shape for a five-tab app; the palette as the command list Tendril already has. |
| **Things 3** | Left sidebar: Inbox / Today / Upcoming / Anytime / Someday, then areas and projects; collapsible | List; a task **expands in place** rather than opening a pane | **Popovers at the pointer** for When, Deadline, Tags; Quick Entry is a floating window | Cmd+N, Cmd+K complete, Cmd+1–5 the lists, ↑↓ ↵, global Quick Entry | ~28 pt rows, checkbox on hover, one type size | Remembered; several windows; follows the OS | 5/4/5/0.9 | Dense rows with hover checkboxes; the date popover; the fixed small shortcut set with numbers for sections. |

**The rest** — scored more briefly; each adds one thing the frame does not.

| App | Navigation | Panes | Modal input | Keyboard | Density | Window | R/F/G/V | Take |
|---|---|---|---|---|---|---|---|---|
| Logseq | Left sidebar (Journals, Graph, All pages, favourites) + a **right shelf** for pages opened alongside | Journal stream + shelf | Popovers | Ctrl+K, chord shortcuts (`t l` etc.) | Theme-driven | Remembered; follows the OS | 4/3/4/0.5 | The right shelf: a page beside the one you are on. Later, if 14c's two panes want a third. |
| Anytype | Left sidebar of spaces and objects, collapsible and resizable | Object in the main pane; Sets as tables | Relation edits in popovers | Ctrl+K | One density | Remembered | 3/3/3/0.3 | Nothing beyond Notion's; confirms the sidebar shape. |
| Capacities | Left sidebar + a **right side panel** for the object under discussion | Tabs | Popovers | Ctrl+K | One density | Remembered | 3/3/3/0.3 | The side panel is Notion's peek by another name. |
| Todoist | Left sidebar (Inbox, Today, Upcoming, Filters & Labels, projects), collapsible | List; task detail as a **modal, side panel or full page — a user setting** | Quick Add as a floating field; dates in a popover | `q` add, `g t` today, global Quick Add, ↑↓ | Comfortable / compact toggle | Remembered; follows the OS | 4/4/4/0.6 | Detail-placement and density as *settings* — the precedent for decisions 4 and 6 below. |
| TickTick | Left sidebar of lists | The classic **three columns**: lists, tasks, detail | Detail pane inline; dates in a popover | Global Ctrl+Shift+A, Ctrl+N | One density | Remembered; follows the OS | 4/4/4/0.6 | List + detail pane for Tasks (14f) — the shape it shares with Tendril. |
| Fantastical | Left sidebar: calendar list + mini month; view switch in the toolbar | Day / Week / Month / Quarter / Year | Event edits in a **popover on the event**; natural-language field in the toolbar | Cmd+N quick-add, Cmd+1–5 the views, ↑↓ ← → the dates | Fixed; hour rows ~40 pt | Remembered; a menu-bar mini window; follows the OS | 4/4/4/0.7 | Opens on **Week** on a wide window; the quick-add field lives in the top bar. |
| Sunsama | Left channels/backlog, centre **day columns**, right calendar | Three panes, all visible | Modals for rituals; inline for tasks | Cmd+K | Fixed | Remembered | 3/2/3/0.3 | Week-as-columns is the planner's default; Tendril's Week view already draws it. |
| Akiflow | Left sidebar (collapses to icons), centre list, right calendar | Two or three panes | The **command bar** *is* the input | Cmd+K primary, global Cmd+Shift+A | Fixed, dense | Remembered | 3/3/3/0.3 | The rail that collapses to icons: Obsidian's ribbon reached from the other side. |
| Apple Notes / Reminders | Sidebar + list + note (three columns), sidebar collapsible | List + detail | Reminders' detail in an inline **ⓘ popover** | Cmd+N, ↑↓ ↵, Cmd+1–4 | List / gallery; one row size | Remembered; follows the OS | 4/4/4/0.5 | The three-column note shape is 14c exactly; the ⓘ popover is 14b's cheapest form for a row. |

### 13.3 What the tables say when read together

- **Sections live on the left, always.** Thirteen apps, thirteen left sidebars or rails; none
  uses a bottom bar on desktop. Two forms: a full sidebar carrying the tree (Notion, Logseq,
  Anytype) and an icon rail with the tree in a pane beside it (Obsidian, Akiflow collapsed).
- **List + detail is the default two-pane** (Things, Todoist, TickTick, Apple, Notion's tree +
  page). Where a third pane appears it is a shelf for "the other thing" (Logseq, Capacities).
- **Popovers and inline edits, never sheets.** A sheet is a phone's answer to "no room"; on a
  desktop there is room, so the edit appears where the pointer is (Things' dates, Notion's
  properties, Reminders' ⓘ) or beside the page (Notion's side peek).
- **A palette plus a small fixed set.** Every app has Ctrl+K or Cmd+P; the fixed set is short and
  the same across apps — new, search, go to section by number, today, back. Only Obsidian makes
  the set editable, and only Obsidian needs to.
- **Denser rows with hover-revealed controls** (Things' checkbox, Notion's handles). Two apps
  (Todoist, Notion) make density a *setting* rather than an opinion.
- **A remembered window** — every app — and **dark following the OS** — every app but Sunsama and
  Akiflow, which are web-first.

### 13.4 The pass — ordered PRs, each with its decision (answered 2026-09-13 on the mock)

Each PR follows the established shape: tests where there is logic (the breakpoint, window-state
persistence, the density and calendar settings' defaults), the local CI stand-in, a desktop walk
at 1200×800 **and at 700 px wide** (the narrow fallback is a promise, so it is proved every time),
a phone smoke test that nothing moved there, spec rows in both files.

| PR | What | Decided |
|---|---|---|
| **14a — the shell** — *Done 2026-09-14, reshaped*: the shell drawn as the mock draws it on both platforms (`ui/nav/Shell.kt`: the rail, the bottom bar, the timer strip and rail foot, the 52 dp top bar at every screen), not a Material `NavigationRail` inside Material chrome — the first walk showed that a rail alone changed nothing visible, since the plan had deferred every mocked element to 14b–14h. Breakpoint, remembered window and title as planned; desktop sync controls into Settings. | A left `NavigationRail` (icons + labels) on a wide window, the bottom bar kept under it — one `if (wide)` in `WorkbenchScaffold` driven by window width at **840 dp**, so a narrow desktop window falls back to the phone's shape rather than a broken one; `rememberWindowState` persisted in `KeyValueStore` (size, position; default 1200×800, minimum 800×600); the title *Tendril*; the `RunningTimerBar` at the rail's foot. | **Rail**, not a full sidebar. The sidebar would swallow the Pages tree into the shell (Notion) and make 14c's pane moot; the rail keeps the tree where the tab is, at 84 dp instead of 240. |
| **14b — sheets become slide-overs** — *Done 2026-09-14*: `SlideOver` behind `TendrilSheet` at the shell's own 840 dp rule; the mock's geometry; menus and dialogs untouched; the phone unchanged. | `TendrilSheet` already frames every sheet; on a wide window it renders as a **right slide-over** (~440 dp, full height, the same title slot) — one change, 29 sheets. Pickers anchored to a control (dates, labels, the relation menus) stay `DropdownMenu`s; confirms stay `AlertDialog`s. | **Slide-over**, not a centred dialog: the page stays readable beside it (Notion's side peek), and a sheet's content is already a column that expects height. |
| **14c — Pages as two panes** — *Done 2026-09-15*: the tree beside the page from 840 dp; chrome split per pane, chosen among three on `docs/mockups/pages-two-panes.html` (A Apple Notes / Ulysses, B Obsidian / Bear — taken, C Things / Craft); resizable, collapsible (Ctrl+\); `showPage` replaces, `openPage` pushes; children one level per chevron. | On a wide window the Pages tab shows the tree on the left (the existing list, ~280 dp) and the open page on the right; the route stack unchanged underneath (a page from the tree replaces the right pane; Escape closes it). Databases and canvases take the right pane too. | **Resizable** (a drag handle, width remembered with the window) **and collapsible** (Ctrl+\, a chevron in the tree's header). |
| **14c·0 — the scale** — *Done 2026-09-15, pulled ahead of 14c at the user's reminder that every measurement must be proportional on both platforms*: B§13.5 #4's `LocalDensity` multiplier (shorter side ÷ 800 dp clamped 0.85…1.25, × Compact 0.9 / Comfortable 1.0 / Touch 1.3), the phone fixed Touch, the desktop's profile in Settings; measured on both devices. What 14d still owes: the per-surface density pass (row heights, list type, hover-only controls, the floating toolbar). | The first half of 14d, built early so 14c's pane is proportional from its first line. |
| **14d — density under a pointer** — *Done 2026-09-15* (the scale shipped as 14c·0; this PR the pointer half): hover-revealed `···` on tree rows (28 dp, never laid out away), right-click = the phone's long-press on rows and blocks through shared `Pointer.kt` (no `ContextMenuArea` — in-window on both platforms; inside a text field the field's own menu wins and *Block actions…* is appended to it on the desktop), the toolbar floating above the selection under a pointer profile via `LocalDensityProfile`; three `design-critique-plus` passes in `docs/critiques/`. | One knob, not a profile per component: `LocalDensity` overridden once at the window's root, so every `dp` in the app — rows, `CELL_WIDTH`, `DAY_WIDTH`, icons, padding — scales together. **Scale = clamp(shorter side of the window ÷ 800 dp, 0.85…1.25) × profile**, the profile being *Compact* 0.9× / *Comfortable* 1.0× / *Touch* 1.3× (the earlier 36 / 40 / 52 dp rows, as proportions). The OS font scale stays multiplied on top and is never overridden; the clamp keeps a maximised 4K window from becoming a poster. Row controls (`···`) appear on hover; **right-click** on a row or block opens what a long-press opens on the phone (`ContextMenuArea`, in-window) — B§13.6 #1. | **Proportional to the screen on both platforms; the profile a Settings choice on desktop, default Compact; hover-only controls yes.** The phone runs the same rule with the profile fixed to Touch (Android's 48 dp minimum target is a floor, not a preference); at 360–430 dp wide the window term is near 1.0, so the rule is one rule whose effect is small where the screen is small. Todoist and Notion are the precedent for density as a setting. |
| **14e — the keyboard** — *Done 2026-09-16*: one table (`Shortcuts.kt`) and a card generated from it (F1); **Alt+← / Alt+→**, not Ctrl+[ ], and **F1**, not Ctrl+/ — those are AltGr+è and Shift+7 on an Italian layout, so the browsers' and Windows' keys stand in; forward list on the nav state; side buttons through `isBackPressed`; ↑↓↵→← and type-ahead in the tree and the Tasks list, shared; Ctrl+F shipped separately the same day (§0.10 item 19 — the bar under the page bar, the blocks' text, marks through the span transformation). Critiques in `docs/critiques/keyboard-*.md` and `find-in-page-*.md`. | A fixed set in `Main.kt`'s `onPreviewKeyEvent`: Ctrl+N new page, Ctrl+Shift+N quick-add task, Ctrl+T today's Journal, Ctrl+F find in page, Ctrl+1…5 the tabs, Ctrl+, Settings, Ctrl+\ the tree, **Ctrl+[ ] and the mouse's side buttons for back and forward** (the route stack gains a forward list — B§13.6 #2), **↑↓ ↵ and type-ahead in the tree and every list** (B§13.6 #8); a `?` overlay listing them. | **No Alt-mnemonics and no menu bar.** Mnemonics serve a menu bar; Tendril's actions live in each screen's `···` and in the palette's `>` commands, so a bar would be a third home for the same list. Ctrl+1…5 is the section shortcut (Things, Fantastical, Apple). |
| **14f — Tasks & Calendar as desktop surfaces** — *·1 Tasks done 2026-09-16*: list + pane from 840 dp, the pane's chips the row's menu by name (the mock's *Someday* / *Flag* struck by the critique), rows under a pointer, the Trash sheets shared (§0.10 item 13). *·2 Calendar done 2026-09-16*: a time grid of seven Plan lanes (date and time in one drag), the opening view a Settings choice (Week wide, Day on the phone — not Month), quick add as a strip under the bar with no chord. | Tasks: list left, the task's sheet content inline on the right (TickTick, Apple). Calendar: day columns wider, Quick Add in the top bar (Fantastical); the desktop Trash and the reminder bell (§0.10 item 13) folded in, since the sheets are slide-overs by then. | **The calendar's opening view is a Settings choice, default Week** on a wide window (Fantastical, Sunsama, Akiflow open on the week); the phone keeps Month. |
| **14g — theme on desktop** | The picker in the desktop Settings pane (mode, colour, typeface — all four colours and the Serif face are shared already), stored in `KeyValueStore`. | **System / Light / Dark in Settings, default System.** *·1 the model done 2026-09-16* (§13.7 built: ten registers solved to floors, the theme in the store on both platforms, one shared section; `tendril-spec.md` §2.3); *·2 the token map done 2026-09-16* (§13.8.3 built with its amendments below; `tendril-spec.md` §2.3 *The token map*); *·3 the ladder done 2026-09-16* (§13.8.1 built with its amendments; v20; `tendril-spec.md` §0.6.4). **14g done.** |
| **14h — the shelf** — *·1 done 2026-09-16*: as planned, with the openers decided on `docs/mockups/shelf.html` (the page's `···` → *Show beside ▸*, the tree row's *Open beside* and Ctrl+click; not the Road Map's nodes), the page's own bar as the shelf header, the graph over the full graph's neighbourhood on the shared canvas, the Journal stored as a kind, a 45 % clamp, Ctrl+Shift+\ (`tendril-spec.md` §3.1). *·2 the small things done 2026-09-16* — the thirteen items measured before and after (`docs/critiques/small-things-measured.md`, `small-things-function.md`). **The pass is done: 14a…14h, 2026-09-14 → 2026-09-16** (`tendril-spec.md` §0.10 item 14 closed). | A second pane beside the open page on a wide window: another page, the Road Map's local graph around this page, or today's Journal (B§13.6 #4). Opened from a page's `···` ("Open beside") and from the Road Map; closed with its own ×; width remembered like the tree's. | **Yes** (2026-09-13). Only Obsidian pairs graph and page; nobody pairs Journal-today with the page being written. |
| **After the pass** | **Pop-out windows** (#6 — *done 2026-09-16*: one `Window` per page with its own nav state, store and scale rule, remembered; `tendril-spec.md` §3.1; `docs/critiques/pop-out-mock.md`, `pop-out-function.md`); **drag between panes** (#5 — *done 2026-09-16*: the Calendar's task tray beside the week — Unscheduled + Overdue — dragged onto a day, an hour, or back to the tray; the Timeline's *No date* rows onto a day; on the phone a strip under the Week and the Month with a long-press drag; `tendril-spec.md` §3.2; `docs/critiques/drag-between-panes-mock.md`, `-function.md`); **hover previews** of mentions and block references (#3 — *done 2026-09-16*: a card under an `@mention`, a mention block, a block reference — its line in context, marked — or a Road Map node after 500 ms; up to six lines and a footer; desktop only; `tendril-spec.md` §3.1.1; `docs/critiques/hover-preview-mock.md`, `-function.md`); **the system tray** with desktop reminder notifications and a **global quick-add hotkey** (#7 — *done 2026-09-16*: the notification-area icon with three verbs, × hides to it, Windows toasts from `DesktopReminderScheduler` over the shared `domain/reminders/ReminderFirings.kt`, the bell on both platforms, the chord as a Settings pick list and its popup; `tendril-spec.md` §2.2; `docs/critiques/tray-mock.md`, `tray-function.md`). **The desktop audit** — *run 2026-09-17*: three full passes (layout, type, function) over every desktop surface in one document PR (`docs/critiques/desktop-layout-full.md`, `desktop-type-full.md`, `desktop-function-full.md`; `tendril-spec.md` §0.10 item 22); nine High findings go to the fix PR *the audit's fixes*, the Med/Low rows to item 22's list. **The fix PR shipped 2026-09-17** (`docs/critiques/desktop-audit-fixes.md`; the type classes in `tools/type_sites.py` and `tools/type_table/`). **The Month grid shipped 2026-09-17** (L4, the dots PR folded in — titled cells on a wide window, a *+n* popover, quick add from a cell, the database Calendar view on the same grid, the phone's dots per layer; `docs/critiques/month-grid-mock.md` with the feature scoring, `month-grid-function.md`). **L5 shipped 2026-09-17** (the Calendar's chrome — one 52 dp bar on every tab, the bar the window's title bar on Windows through the JetBrains Runtime, the time grid at Notion Calendar's measured 48 px hour / 60 px gutter; `docs/critiques/calendar-chrome-mock.md`, `calendar-chrome-function.md`). **L6 shipped 2026-09-17** (the switcher as a centred card — 560 dp at 20 % of the window, a 36 dp field, rows at the profile's height, Recents when empty, chords from the shortcuts table; `docs/critiques/quick-switcher-mock.md`, `quick-switcher-function.md`; the AI key and the desktop passphrase on the same 36 dp field). **L7 + L8 shipped 2026-09-17** (the strip 44 dp on the find bar's field, the chips inline; the popup on the 36 dp field; the tray ≤ 30 % of the pane; the header's minutes from a 90 dp lane; `docs/critiques/quick-add-strip-mock.md`, `quick-add-strip-function.md`). **L9 + L10 shipped 2026-09-17** (the canvas's cards under a pointer — *New card*'s home scored: the bar's `+` and a double-click on the ground with the empty-card discard rule; the badges on hover or on the selected card; new cards at the visible centre and a *Fit* button; the desktop's Escape dispatched once — the app's duplicate input removed; `docs/critiques/canvas-cards-mock.md`, `canvas-cards-function.md`). **The design layer shipped 2026-09-18** (D4–D8 — `surface2` at 6 %, the edges at full alpha, `accentStrong`/`thirdStrong` gone, the radius family 4 · 6 · 8 · 10 · 12 through Material's shapes, Tab's ring; `docs/critiques/design-layer-measured.md`, `design-layer-function.md`). **B the last Material frames shipped 2026-09-18** (L6b every field a `TendrilField` with rule 18; L15 + T3 the Add dialogs as sheets and the date picker's 32 sp headline gone; T4 the ellipsis rule 19; T5 a sheet's title at `pageTitle`; a slide-over's × out from under Windows' ✕; `docs/critiques/material-frames-measured.md`, `material-frames-function.md`). **C the database's views shipped 2026-09-18** (F4 a confirm; F5 the ▾; F6 the property made with the view — Notion's, measured — and *No Status* for unset rows; F7 the New view sheet; F8 one vocabulary; `docs/critiques/database-views-mock.md`, `database-views-function.md`). **D small things III shipped 2026-09-18** (L11 the submenu flip, L12 Journal days as dates, L13 the Timeline's first bar, L14/F12 the reminder list with fire moments — TickTick's, measured — F9 the label sheet lists all, F10 *Show Someday · n*, F13, F14; `docs/critiques/small-things-3-function.md`). **L7b shipped 2026-09-18** (the recognised runs tinted in the line on `findSoft`, the chips kept — Todoist's quick add measured live; `docs/critiques/quick-add-tokens-mock.md`, `quick-add-tokens-function.md`). **§0.10 item 22 is closed; the desktop audit's list is done.** **The phone catch-up ran 2026-09-18** (`docs/critiques/phone-catch-up.md`: the launch crash since 14h·2 fixed; the v19 → v20 rebuild verified on the phone; five findings for a fix PR). **The phone's fix PR shipped 2026-09-18** (`docs/critiques/phone-fixes-measured.md`: the four apps measured on the phone — Todoist's 57 dp rows, TickTick's 46, Notion's 48 / 45, its pushed-level sheet; every sheet scrolls, a Touch submenu pushes, the canvas's `···`, the rows at 59 dp, a Robolectric layout test). **Next: the phone audit beside the five apps on the phone (§0.10 item 23).** | **All yes** (2026-09-13), in that order, each its own PR. |

Two settings from this pass are per device by construction — `KeyValueStore` does not sync (§0.10
item 12) — which is right for them: density and the calendar's opening view are properties of the
screen in front of the person, not of the data.

### 13.5 The decisions, as answered

For the record, by number — the form the mock's "Your answers" box produces.

1. Navigation: **rail**.
2. Sheets on desktop: **right slide-over**.
3. Pages tree: **resizable, collapsible**.
4. Density: **proportional to the screen on both platforms** (one `LocalDensity` scale from the window's shorter side, clamped) **× a profile chosen in desktop Settings — Compact 0.9 / Comfortable 1.0 / Touch 1.3 — default Compact; hover-only row controls: yes.**
5. Alt-mnemonics: **no** (and no menu bar); reasoning under 14e.
6. Calendar opening view: **a Settings choice — Week / Month — default Week.**
7. Theme on desktop: **System / Light / Dark in Settings, default System.**

What stays open is not a decision but a measurement: the breakpoint. 840 dp is the Material
medium-width boundary and the mock's; the first PR proves it against the real rail and tree at
the widths a person actually drags a window to.

### 13.6 Other diffs with the bar — answered 2026-09-13

Layout-axis things the thirteen do that Tendril does not, each judged on whether it would
*improve* on them rather than copy, and on whether the offline build can do it. The last column
is the answer; where it says *folded*, the PR row in §13.4 carries the detail.

| # | Diff | Who | Tendril's angle | Cost | Decided |
|---|---|---|---|---|---|
| 1 | **Right-click menus** on rows and blocks — the pointer's long-press | every desktop app | Today a long-press opens a sheet; `ContextMenuArea` is in-window on Compose Desktop | cheap | **Done 2026-09-15 (14d)** — shared `onSecondaryClick`, not `ContextMenuArea`, so an Android mouse gets it |
| 2 | **Back / forward** — Ctrl+[ ], the mouse's side buttons | Notion, browsers | The route stack has back only | cheap | **Done 2026-09-16 (14e)** — as Alt+← / Alt+→ (Ctrl+[ ] is AltGr-bound on European layouts); side buttons implemented, unverified without a physical mouse |
| 3 | **Hover preview** of a mention or block reference — the target's first lines | Obsidian, Notion | Better than theirs: a block reference's preview is *live* text (§0.6.12) | cheap | **Yes — after 14e** *(done 2026-09-16; the reference previews its line in context)* |
| 4 | **A second page beside the first** — a shelf or split | Logseq, Obsidian | The shelf holds the Road Map's local graph or today's Journal next to the page being edited; only Obsidian pairs graph and page, no one pairs Journal-today with a page | medium | **Yes — 14h** *(done 2026-09-16, 14h·1)* |
| 5 | **Drag between panes** — a task onto a Calendar day, a page from the tree onto a Timeline day | Sunsama, Akiflow, Things | A bound date means the drop *is* the Table's cell write (§0.6.14) | medium | **Yes — after 14f; on the phone too** (long-press drag) *(done 2026-09-16)* |
| 6 | **Pop-out windows** — a page in its own window | Notion, Obsidian | Compose `Window`s are cheap; the ViewModel cache is keyed by page already | cheap–medium | **Yes — after 14c** *(done 2026-09-16)* |
| 7 | **System tray** — keep running closed; desktop notifications for reminders (`TrayState.sendNotification`) | Things, TickTick, Fantastical | Would give desktop the reminder bell the windows spec §12.1 says it lacks. The *global* quick-add hotkey needs JNA — and **`jna` + `jna-platform` 5.6.0 turn out to be in the Gradle cache** (not on the desktop classpath yet; a direct dependency resolves offline), so `User32.RegisterHotKey` is available | cheap | **Done 2026-09-16** — the icon, × hides, toasts from the shared firing arithmetic, the bell, the chord (Win+Alt+N; Win+Alt+T is Game Bar's) and its popup; `tendril-spec.md` §2.2; `docs/critiques/tray-mock.md`, `tray-function.md` |
| 8 | **Keyboard in the tree and lists** — arrows, type-ahead, Enter | Apple, Things | Rows are click-only today | cheap | **Done 2026-09-16 (14e)** — the tree and the Tasks list; the prefix underlined in the row; the rest of the lists with 14f |
| 9 | **Tabs** | Obsidian, Capacities | Rail + tree (+ a shelf, #4) covers it; tabs are a browser's answer | medium | **No, for now** |
| 10 | **Cross-block selection and undo** | Notion | A real gap, but the editor's — one `BasicTextField` per block — not the layout's | high | **To scope in the per-block editor pass** — `tendril-spec.md` §0.10 item 19 |

### 13.7 Themes, colour and the layout grammar — decided 2026-09-14

Read against the same thirteen apps on three axes — chrome colour, where colour goes, the grammar
that carries it — and settled over two days on two mocks (`docs/mockups/desktop-shell.html`, whose
*Register* menu applies each candidate to the whole UI in both modes, and
`docs/mockups/register-explorations.html`, twelve re-conceptions of four registers by other apps'
colour structures). The numbers below were computed with `design-optimization`'s `designkit`
(WCAG ratios, CIEDE2000, colour-vision simulation, Matsuda harmony fit), not asserted.

**The trend.** (1) *Monochrome chrome; colour spent on data* — the note apps' UI is grey plus one
accent, and every colour a person sees is something they made: a tag, a calendar, a type
(Capacities), a priority (Todoist). The only coloured chrome on the list is the task apps' theme
galleries, the weakest feature in the set. (2) *Light · Dark · System is universal; a palette
gallery is not* — a note app offers one accent hue (Obsidian) or the OS's (Apple), never four fixed
colourways. (3) *Dark is not black* — `#191919`, `#1E1E1E`, warm greys; true black only for OLED
(Things). (4) *Typography is the theme lever* in writing apps — Notion's fonts per page, Obsidian's
base size. (5) *The grammar is space and weight, not lines and cards* — hairlines, small radii
(6–8), fills only for selection and hover, shadows only on popovers.

**Tendril's diff (§2.3 as it stood).** Four colourways that tint the *surfaces* (`surface2` is
`#F1E9DC` in Clay, `#F1E7EC` in Mauve) — the gallery pattern applied to a note app. Ink alone matches
the trend (a slate accent on neutral white, dark `#1B1D21`). Data colour half there: `Label.color`
yes; databases, views, habits, calendar sources no. Typography strong and under-used (Serif is
global, not per page; no base-size control). The layout grammar is Material 3's defaults — 12–16 dp
radii, tonal `primaryContainer` fills, `NavigationBar` pills — the single biggest "Android app on a
desktop window" tell, and a `Theme.kt` change rather than a rewrite.

#### 13.7.1 The model — three knobs, shared by both platforms

A theme is **ground × hue × mode**, and a *register* is a named preset of the first two:

- **Ground**: *neutral* (Ink's `#FFFFFF` / `#1B1D21`) or *cold* (Console's `#FBFBFA` / `#101216`).
  Never warm. Warm grounds were built (Cozy, Kodachrome paper, Chalk sand, Playground cream) and
  scored, and were dropped for a physical reason: an hour of writing on a cream ground strains the
  eye — the person who will use this app said so, and the numbers agreed (§13.7.4, headroom).
  Warmth lives in the hue, never in the surface being read.
- **Hue**: one accent, solved to **8.0:1 (light) / 7.6:1 (dark)** against the ground — Console's
  contrast, the reference the user chose — with saturation capped at 55 % light / 50 % dark; at most
  one **second channel** hue where the use needs two meanings (Swiss's signal yellow, Playground's
  warm).
- **Mode**: System / Light / Dark, default System (§13.5 #7). Colour and register are the same on
  the phone and the desktop; only the *screen* differs, and that is a device setting (§13.7.3).

Adding a register later costs one hue and a contrast solve — no new palette, no new WCAG table —
which leaves Obsidian's "pick any accent" a colour wheel away without committing to it now.

#### 13.7.2 The seven registers

| Register | Use it was imagined for | Ground | Light hue | Dark hue | Second channel | Source |
|---|---|---|---|---|---|---|
| **Ink** | none in particular — the default | neutral | slate `#4A5568` | `#8792A6` | — | `Palette.kt`, unchanged |
| **Console** | coding projects, a process log | cold | violet `#4B3AA6` (8.3:1) | `#A99AF0` (7.6:1) | — | lexicon *Developer Tools · Console* |
| **Swiss** | company project management | cold `#F4F6F8` / `#0E1018` | navy `#1D3557` | `#7FA6D9` | signal yellow `#B8960A` / `#F4D03F` (fills and marks only) | lexicon *Swiss · pair 2* |
| **Playground** | groceries, kids' appointments | cold | teal `#1D5663` (7.9:1) | `#53B2C5` (7.6:1) | warm `#8A4523` / `#D18E61` (6.9:1) | lexicon *Kids/Education · Playground*, hue only |
| **Blush** | a personal diary | cold | pink 339° `#86324F` (7.9:1) | `#D793AB` (7.7:1) | — | from the user's `#EAA7BF`, deepened and muted (sat 46) |
| **Chalk** | a quieter diary | cold | sand `#574C38` (8.1:1) | `#B7A380` (7.6:1) | — | lexicon *Serene · Chalk*, hue only |
| **Kodachrome** | a travel journal | cold | rust `#7E3925` (8.1:1) | `#D29879` (7.6:1) | — | lexicon *Nostalgic · Kodachrome*, hue only |

Rejected on the way: *Cocoa* (too intensely warm), *Scholastic* (no need), the warm grounds of the
four above (eye strain), the earlier rose at 349° (too close to rust: CIEDE2000 13 → now 18 light /
21 dark, colour-vision worst case 13 / 17, both over the dE 10 floor). Clay, Moss and Mauve from
§2.3 are not registers: their surfaces are tinted. Their hues could return as registers under this
model if wanted — one solve each. *Decided 2026-09-16 (14g·1): they returned — **Clay, Moss,
Mauve** as registers eight to ten, their old accents on the cold ground, solved by lightness
(Clay light lands at `#68472D`, still clay). And Ink is solved like the rest: its dark accent
measured 5.4 where the model asks 7.6, so "`Palette.kt`, unchanged" is withdrawn.*

#### 13.7.3 Four rules the theme engine implements

1. **Derived tokens are solved to a floor, never mixed by a fixed percentage.** `dim`, `faint`,
   `soft`, `border` come from ground and text; a fixed mix put `dim` at 3.7–3.9:1 on darker grounds
   twice during this work. `Theme.kt` takes the smallest share of text that clears 4.5:1 (`dim`) and
   3:1 (`faint`). Every register then passes whatever ground it is given, and the AA claim in §2.3
   becomes a property of the code instead of a table. *Amended 2026-09-16 (14g·1, from
   `docs/critiques/registers-mock.md`): dim and faint are solved against the **worse of the ground
   and `surface2`** — on the ground alone they read 4.28–4.51 on the hover and sheet ground — and
   a second-channel mark is solved to 3:1 (Swiss's yellow was 2.6). The floors as built are
   `Floors` in `Registers.kt`: dim 4.6, faint 3.05, accent 8.0/7.6, on-accent and soft-text 4.6,
   mark 3.0.*
2. **The eye pass** (screen-, eye- and astigmatism-friendly): keep the temperature, cut saturation
   and brightness inside it; body text in a **9.5–13:1 band**, not maximal (17:1 black-on-white is
   the fatigue case) — *amended 2026-09-16: the engine solves the text itself into the band
   (~12:1), since the mock derived everything from a seed it never solved and every register
   measured 15–17.5;* in dark mode text below its brightest (halation blooms light text on dark for
   astigmatic readers); no thin weights — 400/500 only, **amended 2026-09-16 (the type PR): Inter the default at true 400/500/600, headers bold not large, seven styles (`tendril-spec.md` §2.3)** — and size through 14d's scale.
3. **OLED is a device setting, not a theme.** Astigmatism prefers a lifted dark ground (Ink's ~11 %
   lightness) and softer text; an OLED panel prefers 3–6 % for contrast and battery. No single
   ground serves both, and themes are shared across devices — so *Deeper blacks* is a phone-only
   toggle, off by default, that drops the register's dark ground to 4 % and re-solves the derived
   tokens. The desktop's LCD never shows it. Same shape as density (§13.5 #4): the theme owns the
   hue, the device owns its screen.
4. **Colour is never the only channel** — see §13.7.4 for why.

#### 13.7.4 Data colour — what the ground can carry, measured

Four structures were scored on every register: an **importance ladder** (3 colours, on
`Entry.importance` — new), **calendar layers** (4), **databases** (6, one each), **labels** (9).
Legibility as *headroom* (the share of hue × lightness space at saturation 50 that clears 4.6:1 on
the ground); tolerance as the **worst-case CIEDE2000 among the set plus the register's own accent,
across normal, deuteranopic and protanopic vision**; floor dE 10, the audit's, calibrated on
Okabe–Ito. Harmony fit was 0.63–0.71 for every register — clashing is not where they differ,
*disappearing* is.

| | Headroom L / D | Importance (3) | Layers (4) | Databases (6) | Labels (9) |
|---|---|---|---|---|---|
| Cold grounds (Console and the four) | 43 % / 53 % | 12–18 / 10–18 | 10–13 / 10–12 | 6–9 / 6–10 | 4–5 / 3–6 |
| Neutral ground (Ink) | 44 % / 50 % | 17 / 16 | 9 / 11 | 9 / 11 | 4 / 5 |
| Warm grounds (dropped) | 35–40 % / 50 % | 10–16 / 10–16 | **7.5–7.8** / 9–11 | 3–8 / 5–9 | 3–5 / 3–6 |

What it says: the **importance ladder passes everywhere**; **calendar layers** pass on cold and
neutral grounds and missed by 2–2.5 dE on the warm ones (one more reason they went); **six database
colours clear the floor on no ground** — six plus the accent is past what one channel carries, and
Capacities knows it (every type has an icon); **nine label colours** sit at 3–6 on every ground —
colour on a label is grouping, never identification, and labels carry their names. Two further
findings: sets of four or more separate better at **two lightness levels** than one (the search
chose "2 levels" in nearly every winning cell — solving every data colour to the same contrast puts
them at the same luminance, which colour-vision deficiency then cannot tell apart), and adding the
accent to the set is what pulls six under the floor, so the accent's hue is *reserved* — data hues
are fanned away from it.

Hence rule 4: layers differ by shape (a checkbox, a block, a habit mark), databases by icon, labels
by name, importance by its ladder — colour is the second channel, never the first.

**Decided the same day in §13.8:** which element carries which colour — and the importance ladder
turned out to have five steps, not three.
§2.3's two recorded defects (the hardcoded link blue; the mode tri-state) are answered by the model
above — links and mentions take the accent token, mode is System/Light/Dark with System default —
and land with 14g.

### 13.8 Data colour — the channel map, decided 2026-09-14

§13.7.4 ended with "which element carries which colour" open. The survey of every coloured element
the app has (a grep of `shared/`: Material roles used as meanings, `LabelColors`, the callout
pastels, two hardcoded blues, three `Color.Gray` canvas edges) and a third mock —
`docs/mockups/coloured-elements.html`, every element in each register's environment, light and
dark, each colour derived by a printed rule — settled it. Two pushbacks shaped the answer: *one
colour per task flattens a thing with five characteristics*, and *six colours for databases is stock
and repeats at once*. Both point at the same principle: **colour is assigned to a dimension, not to
a thing.**

#### 13.8.1 Tasks — urgency is the colour; everything else is a glyph or the chip

| Characteristic | Channel |
|---|---|
| **Urgency** — none / low / mid / high / urgent, *and* a nearing deadline | **colour: a five-step heat ladder** — dim → the register's signal family at three strengths → the error red. Level = max(priority, time pressure), so "increasingly urgent as time passes" is an input to the same ladder, never a second colour. Steps differ in lightness and saturation as well as hue, so colour-vision deficiency reads them. |
| Deadline / none | the ⚑ glyph and the date; overdue turns the date error-coloured |
| Repeating / one-time | the ↻ glyph |
| Category — self, work, a project | **its database's hue, else its first label's**, on the chip and as the calendar block's fill; neither → the surface with only the stripe |
| Done · Blocked | dim + struck · the error chip |

A task shows two colours at most, each meaning one thing. On the calendar a task block is
*category fill + urgency stripe*.

**The ladder itself — decided later the same day, on `docs/mockups/urgency-ladder.html`.** Seven
candidate ladders from the user were adapted to the four grounds and measured; three findings
fixed the shape. (1) *A one-hue ladder adapted to rising contrast is a lightness ramp*, capped
near 5–6 dE between adjacent steps on a light ground whatever hexes it starts from; hue drift
(amber → red) makes it *worse* for colour-deficient readers, since deuteranopia folds amber,
orange and red into one brown. (2) *The ladder is a mark, never text* — a 4 px stripe, a star, a
dot — so its floor is WCAG's 3:1 for UI components, not 4.5:1; letting *low* sit at 3:1 and
*urgent* reach 13:1 nearly doubles the spacing. (3) *On a dark ground the ladder runs the other
way*: pale-and-muted → saturated, so that urgent is the deepest step, not the palest. The result,
**R-wide+ in coral**, is **one family in every register** (hue 4–12°) — urgency looks the same
everywhere, and a register's second channel keeps its own meaning. Light (Ink): `#B9887E ·
#B84E3D · #9A2619 · #660C05` at 3.0 / 5.0 / 7.9 / 13.0:1; dark (Ink): `#E6D1CC · #E1A599 · #E97D6D
· #F83F2A` at 11.5 / 8.1 / 6.1 / 4.6:1; *none* is the ground's dim. Adjacent steps ≥ 11 dE for
everyone and ≥ 9 under colour-vision deficiency on every ground. Overdue *text* stays the error
colour at ≥ 4.5:1.

*Implementation note:* `Entry.important` is a Boolean (§0.6.4's flag). The ladder needs
`Entry.importance: Int` 0–4 with `important = true` migrating to 3 (high) and the flag's UI becoming
the ladder's; time pressure is derived from `deadline` at read time, never stored. A schema step for
the PR that builds this. *Built 2026-09-16 (14g·3, v20).*

*Amended 2026-09-16 (14g·3, from `docs/critiques/urgency-ladder-mock.md`):* (1) the ladder is a
**rule, not a table** — a hue/saturation step with its lightness solved to the contrast target on
the actual ground; it reproduces the hexes above at ΔE ≤ 0.4 and re-solves on the OLED ground.
(2) The **star retires** — a glyph in a text run measured 3.05:1 as text; the ladder is a stripe
and a disc, never a glyph. (3) ***None* draws nothing** on a row; the dim swatch is the legend's.
(4) **Time pressure**: within 7 days low, 3 mid, today or tomorrow high, past urgent — the level is
max(set, pressure); a done task has none. (5) The ladder is **on by default** behind one shared
switch (the flag was opt-in). (6) The row's overdue *date* keeps the row's one colour — the stripe
already says urgent; "overdue text stays the error colour" holds for the Blocked chip and the
Timeline's dependency line, which mean something else.

#### 13.8.2 Databases — any hue on the wheel, the icon mandatory

A database takes **any hue** (a hue slider; the lightness is solved to the register's ground),
defaulting from the title's hash over the whole circle kept ≥ 30° from the accent — not a fixed set
of six, which was the CVD-safe *simultaneous* answer to a question databases don't pose (they are
not all on screen at once). Because two databases may sit near each other in hue, **an icon is
mandatory** and is the second channel. A database date on the calendar wears its database's hue —
a database *is* a calendar (Fantastical's rule) — so the calendar has no "database date" layer
colour of its own. Eight hashed databases measure 3–8 dE apart on a ground; the icons carry the
rest.

#### 13.8.3 The rest, by rule

- **The accent is reserved**: selection, links, @mentions (the §2.3 hardcoded blues become this
  token), the today marker and the "now" line, the running timer. Data hues keep ≥ 30–40° from it.
- **Calendar layers**: *event* and *habit* are two hues fanned ≥ 60° from the accent (they clear
  dE 10 against it on twelve of fourteen register-modes; Playground dark and Kodachrome light rely
  on shape); a *task* is category fill + urgency stripe; a *database date* its database's hue; an
  external (Google) event is the event hue with a dashed edge — a source is a shape, not a colour.
  Month and Agenda get dots in the same hues, which they have never had (today every layer is one
  accent-coloured line with a "◦").
- **Habits**: the habit layer's hue on the streak mark and the calendar block; a habit may be given
  its own hue later by the database rule (a slider) if wanted — not now.
- **Labels**: the eight `LabelColors` hues and their *names* stay; the hues are re-solved onto each
  ground at two lightness levels instead of being one hex in both modes; the chip is a 14 % tint with
  the hue as text.
- **Callouts**: tints of data hues over the ground with the register's text on them — the fixed
  pastels (`CALLOUT_COLORS`, `PageDetailScreen.kt`) only ever worked in light mode.
- **Block references, related edges, canvas nodes**: today's `tertiary` role → the fan's third hue;
  **canvas edges** (`Color.Gray` ×3 in `CanvasScreen.kt`) → dim, as the Road Map's already are.
- **Error family** unchanged for blocked, overdue, delete, formula and AI failures.
- **Everything derived is solved to a floor** (4.6:1 read; dim 4.6; faint 3.05) — §13.7.3 rule 1.

Where a colour cannot be told apart by a colour-deficient reader, the channel beside it carries the
meaning: the checkbox, the block, the mark, the icon, the name, the glyph — §13.7.3 rule 4, now with
its assignments. What is built when: the token changes and the ladder land with 14g; the calendar
dots and the database hue slider are their own small PRs after it.

*Amended 2026-09-16 (14g·2, from `docs/critiques/coloured-elements-mock.md`):* (1) the three
data hues are **fanned from the accent by one rule** — event −120°, habit +60°, the third 180°,
saturation raised to ≥ 0.40 — so no register's third can drift to the mock's 14° from the accent
(Kodachrome dark); a second channel keeps the third slot. (2) A label's hue and the error red are
solved **against their own tint as well as the ground** — the mock's chips measured 4.09–4.25 and
4.1–4.3. (3) Callouts are a **20 % tint with the hue as a 3 px bar**: two 14 % tints were
near-identical in the render. (4) The **find mark is the third hue's tint**, the current match its
solid — ≥ 60° from the selection by construction. (5) For the dots PR: an uncategorised task's
Month dot is the ladder's *none* (dim), not the accent — *built 2026-09-17 in the Month grid PR* —, which rule 1 reserves. Built: `tendril-spec.md`
§2.3 *The token map*.
