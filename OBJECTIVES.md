# Tendril — Objectives

**What this file is.** The statement of what Tendril is for, what it must never do, what it is
measured against, and the principles every later decision is filtered through. It sits *above*
the two specifications: when this file and a spec disagree, this file is right and the spec owes
an amendment (§10). It is short on purpose; the reasoning behind each verdict lives once, where
the pointer says.

**Related documents** — named here once, never scattered through the body:
`Tendril android/tendril-spec.md` (the functional and technical spec, Android-primary),
`Tendril windows/tendril-windows-spec.md` (the desktop companion),
`docs/benchmarks.md` (the scored benchmark, 2026-09-11, cited as B§n),
`docs/scope-decisions.md` (the register of what is deliberately not built).

## Revision Log

| Version | Summary | Sections touched |
|---|---|---|
| v0.1 | First draft, from the objectives survey (2026-09-10) and the benchmark (2026-09-11) | all |

---

## §0. Hard constraints

Non-negotiable. Everything below is filtered through these; a proposal that needs one relaxed is
out of scope by definition, not an open item.

| # | Constraint | Source |
|---|---|---|
| 0.1 | **Personal use only.** Not distributed, not sold, no store. One person's data on that person's devices. | survey B5 |
| 0.2 | **Offline-first.** Every feature works with no network. The app makes no network call unless the person has explicitly enabled one (0.6, Google Calendar). | survey D9; spec §3.5 |
| 0.3 | **No telemetry.** Nothing leaves the device that the person did not put there. | survey D9 |
| 0.4 | **No first-party sync.** Replication is an external Syncthing-fork over a folder of snapshot files the app reads and writes; the app never runs a server, an account or a relay. | survey D10; spec §9.4 |
| 0.5 | **No collaboration.** No presence, no comments-as-conversation, no public sharing, no web clipper, no projects-as-teams. A different product. | survey A3; scope C1 |
| 0.6 | **AI is opt-in, with the person's own key**, or a local model. Off, the app is whole. | survey A3; scope C4 |
| 0.7 | **Two platforms: Android (primary) and Windows desktop.** No others. True parity is the goal; desktop waits for the project's shape to settle (§7). | survey C7, C8 |
| 0.8 | **Unrooted, Storage Access Framework, no Play distribution.** | spec §1 |

---

## §1. Purpose, goals, non-goals

**Purpose.** One app for a single person's pages, databases, calendar, tasks, habits and the map
between them — built to **improve on Notion's features for one person**, not to integrate a
subset of them. Notion was the starting point, not the ceiling (survey A2).

**Goals**

1. **Exceed, including on the data model** (survey A4). The concrete meaning is §5.8: a schema
   can be attached to a label, so any page anywhere can be a row of its kind.
2. **A base as simple as possible on a structure as scalable as possible** (survey E11). One
   person tracks groceries and a company roadmap without the app feeling like two products
   (spec §1).
3. **Every power feature present, none obligatory** — progressive disclosure (survey E12; §4.1).
4. **A human layer.** Habits are not a tracker (§4.2).
5. **Durable data.** Everything the app stores can leave it in a form something else reads:
   Markdown, JSON Canvas, `.tendril`, ICS. The app is not a hostage-taker.
6. **True parity between the two platforms**, reached by sharing code, not by porting it twice.

**Non-goals** — not "later", but *not this app*: anything in §0; a plain-files vault
(Markdown is an export format, not the storage format — spec §3.1.1's span model stands, B§1.2);
gamified upkeep (§4.2); inferred relationships between pages (spec §3.4).

---

## §2. Blast radius of this file

What the 2026-09-11 pass changes, against the tree on that date. **[Verified]** against the code
where stated in B§.

| | |
|---|---|
| **New** | Time on tasks (estimate, planning, tracking — §5.5, §7); a completion log and a presence view for habits (§5.6); a second task date, sub-tasks, an opt-in importance flag, Postpone (§5.4); schema on a label (§5.8); an outline mind map (§5.2); a live canvas block (§5.3); natural-language entry (§7); calendar layers, agenda, ICS (§7). |
| **Changed** | Nesting depth unlimited (§5.1); the §3.1.6 feature renamed *Label* (§5.9); the streak retired from the habit row (§5.6); §3.2's "deadline" wording corrected to *When* (§5.4); the Canvas UI moves to `shared/` (§5.10). |
| **Untouched** | The block/span model (spec §3.1.1); databases, views, bindings and computed properties (spec §5); snapshot sync, merge, encryption (spec §9.4); Notion import (spec §7); Canvas as a page kind (spec §3.7); the five nav destinations (spec §1). |

---

## §3. The bar

Per surface, the frame is the full-featured *paid* option where one exists; its reach is the
ceiling scores are measured against. Scores are **[Assumed]** — product knowledge, not measured
against running copies — and the gaps are **[Verified]** against the tree. Full tables: B§1–4, 8.

| Surface | Frame | What it has that Tendril lacks, in one line |
|---|---|---|
| Pages | Notion Plus; data model: Tana / Anytype | Linked views in a page, sub-pages from inside a page, columns, history, transclusion; a schema on any page |
| Calendar | Fantastical Premium | Natural-language entry, an agenda, drag-to-move, an edit path at all (spec §3.2 as corrected) |
| Tasks & Habits | TickTick Premium; restraint: Things 3 | Sub-tasks, a second date, an estimate; for habits, any memory beyond a streak |
| Road Map | Obsidian's graph | Filters, colour by label, local depth, typed edges |
| The human layer | **Tiimo** | A visual day, a focus mode showing one thing, notifications that offer rather than demand |

Six ideas score high on every surface they touch (B§5): **time** (estimate → plan → track →
compare), **a schema on a label**, **natural-language entry**, **a task with When, Deadline and
Someday**, **habit presence**, **graph filters**. §5 and §7 are those six, in dependency order.

---

## §4. Principles

Each is a rule a future decision is checked against, with its home.

- **4.1 Progressive disclosure.** A person who never opens a power feature has today's app. A
  database gets a label only when asked; importance is a flag hidden until enabled; the streak is
  a number behind a disclosure. Amazing Marvin is the reference (B§10.2).
- **4.2 The human layer.** Habits exist to keep the things that are *not* work — rest, care,
  practice — visible as part of a life, for a neurodiverse person as much as anyone. **Show
  presence, never absence**: no misses, no gaps, no red, no chain, no percentage. Offer, don't
  demand. The rule leaks, deliberately, into Tasks: a *When* is a plan that moves without guilt;
  a *Deadline* is rare and real; there is no priority scale. Home: B§10.
- **4.3 What is indexed is what is stored.** No second copy of content. The mind map is a view of
  blocks; the export is a rendering; a schema's values live on the page. Home: spec §3.1.1, B§9.4.
- **4.4 A page keeps its own home.** Membership in a database is shown, not contained. Deleting
  a database never deletes a page that lived elsewhere. Home: B§12.5.
- **4.5 Arm to interact.** A pannable surface inside a scrolling page is inert until tapped, and
  grows in place when armed. Explicit modes over guessed gestures. Home: B§9.6.
- **4.6 Absence never implies deletion.** In sync, in merge, and now in schema values on untag.
  Home: spec §9.4.
- **4.7 Explicit over inferred.** Edges are drawn or written, never scored from similarity.
  Home: spec §3.4.
- **4.8 Shared code is the parity mechanism.** A feature reaches desktop by living in `shared/`,
  not by a second implementation. Home: spec §12, §5.10.

---

## §5. Decisions of 2026-09-11

Verdicts, each with the triad where something will be built against it. Reasoning lives at the
pointer; nothing is restated here.

**5.1 Nesting depth — unlimited.** Finding **[Verified]**: `Block.parentBlockId` is unbounded;
`indentTargetFor` alone enforces one level. Decision: lift it. Acceptance: a list nests to any
depth; export renders the depth; FTS content unchanged. (B§9.5)

**5.2 The in-page mind map is a rendering of a nested list.** Finding: an outline and a mind map
are the same data (Xmind, markmap). Decision: no mind-map entity; a subtree drawn as a tree,
inert inline until tapped, then armed and grown in place to edit; a page may hold several.
Acceptance: creating, editing and deleting a node is creating, editing and deleting a block; the
map has no table of its own; Markdown export shows the list. (B§9.4, 9.6)

**5.3 The canvas block is the live board.** Finding: Canvas is a shipped page kind with an
unbounded content space (spec §3.7). Decision: a block that embeds a Canvas page, inert until
armed, grown in place; no second canvas model. Acceptance: the block points at a `CANVAS` page by
id; arming captures pan/zoom/drag; Back disarms; the page list still scrolls when inert. (B§9.3, 9.6)

**5.4 A task has a When and an optional Deadline.** Finding **[Verified]**: `Entry.startDate` is a
task's only date and is both where Calendar draws it and what §5.2 binds as "deadline". Decision:
`startDate` stays the *When*; add optional `dueDate`; add `parentEntryId` (checklist-style
sub-tasks), hidden `estimate`, a single opt-in *important* flag, and a **Postpone** control that
moves a date forward by minutes/hours/days/months. "Someday" is the undated task, named. Labels on
entries after §5.8. Acceptance: an existing task gains no deadline by migration; the §5.2 binding
is renamed to "date" and a second, optional deadline binding exists. Open: which date Postpone
moves by default (§9). (B§11)

**5.5 Time is a first-class concern.** Decision in principle: estimate → plan → track → compare,
in that order (§7). Shape: Tiimo's visible day and Llama Life's "now", not a workload chart.
Nothing built yet; recorded so the estimate field (5.4) is not designed without its consumers.
(B§5, B§10.3)

**5.6 Habits keep a completion log and show presence.** Finding **[Verified]**: `Habit` holds
only `streak`, `previousStreak`, `lastCompletedDate`. Decision: add a completion log; the streak
leaves the row and becomes an opt-in derived number; the habit detail shows "four times this
month", "usually mornings", "last: Tuesday" and never a miss. Acceptance: no screen shows a gap,
a percentage or a broken chain by default. (B§10.3)

**5.7 Canvas grows additively.** Colours, groups, image nodes, nested boards as `PAGE_EMBED` of a
Canvas page, a mind-map layout mode sharing 5.2's layout code, JSON Canvas export. (B§9.2, 9.5)

**5.8 A schema on a label — shape A, opt-in.** Finding **[Verified]**: `PropertyValue` is keyed
by page, not by membership; a row is a page with a single `databaseId`. Decision: a database may
*bind a label*; a page carrying it is a full row in that database's views and gains its fields in
its header, while keeping its own home (§4.4). Untag hides the values; they purge with the
database's own trash. First application of a label bound to a to-do database asks once.
Acceptance: a plain page under any parent can be tagged into a database, edited in its table,
untagged and retagged without loss; deleting the database leaves the page. (B§12)

**5.9 The §3.1.6 feature is called *Label*.** So that *tag* keeps its Notion meaning — a Select
property inside one database, which Tendril also has. Code rename separate and mechanical.
(B§12.5)

**5.10 The Canvas UI moves to `shared/` first.** Finding **[Verified]**: 821 lines in
`Tendril android/…/ui/canvas/`, none in shared. Decision: the move precedes every spatial
feature. Acceptance: `CanvasScreen` compiles for both targets; desktop opens a canvas. (B§9.5)

---

## §6. Explicitly out of scope

Ruled out on purpose. Not to be reopened without amending §0 or §1.

- Everything §0 excludes: distribution, servers, accounts, presence, sharing, clipping, telemetry.
- A mind-map entity of its own (B§9.4, M3) and an always-live canvas inside a scrolling page
  (B§9.3, O2) — 5.2 and 5.3 are the forms that survive.
- Streak chains, heat-maps of misses, scores that punish (§4.2).
- A priority *scale* (§5.4).
- Inferred edges (§4.7). Character-level CRDT (scope X1). Embedded P2P sync (scope C3).
  Desktop home-screen widgets (scope C5).
- Markdown as the storage format (§1).

---

## §7. Order of work

By dependency, then by value (B§6, B§9.5). Each row is a PR or a short chain of them; the spec is
amended in the same pass (§10).

| Step | Work | Unblocks |
|---|---|---|
| 1 | **5.10** Canvas UI → `shared/` | every spatial row on desktop |
| 2 | **5.4** Entry fields + Postpone; **5.6** habit log + presence view | 3, 6, 7 |
| 3 | **5.1** depth, then **5.2** mind map, **5.3** canvas block, **5.7** as time allows | — |
| 4 | **5.8** schema on a label; **5.9** rename | labels on entries; linked views in a page |
| 5 | Natural-language Quick Add (B§6 #3) — creates a Task *or* an Event from one line | pays spec §3.2's debt |
| 6 | Calendar: edit path, drag-to-move, agenda, layers, "Show Habits", ICS (B§6 #6, #7) | 7 |
| 7 | Time: Plan mode, then tracking, then planned-vs-actual (B§6 #8, #9) | Review (B§6 #10) |
| 8 | The rest of B§6 by value: quick switcher (after the FTS title defect), history, transclusion, Road Map filters, Journal-shows-today, Timeline view, AI verbs | — |
| ∥ | **Spec refresh**, section by section, against this file; desktop parity tracked per row | — |

Desktop **[Assumed]**: four of five destinations are stubs and neither export reaches it; parity
is tracked per row above rather than as one milestone, so it never becomes "later".

---

## §8. Risks

- **Pressure creep.** Every task feature is one badge away from a tracker. §4.2 is the check; a
  reviewer asks "does this show absence?" of each new screen.
- **Two implementations.** The Canvas UI already diverged onto one platform once. §4.8 and step 1
  are the mitigation; a feature that lands Android-only is incomplete, not shipped.
- **Scale.** Canvas and the mind map draw every node; past a few hundred, viewport culling is a
  filter before drawing, not an architecture change (B§9.5). Images are already budgeted (#40).
- **Schema on a label meets Sync-to-Tasks.** A label bound to a to-do database turns pages into
  tasks. The once-per-label confirmation is the mitigation; the risk is a surprised person, not
  broken data.
- **The span model and transclusion.** Block references are a new span style; section embeds
  are not. If transclusion of whole sections is ever wanted, that is a real design, not a span.

---

## §9. Open items

Genuinely undecided — distinct from §6.

1. Which date **Postpone** moves by default — the When (recommended) or the Deadline (B§11).
2. Whether the inert canvas block draws nodes live or a cached thumbnail (B§9.6).
3. Whether habits become **measurable** (a value on the log entry) now or later (B§10.3).
4. Whether a one-tap **mood/energy check-in** joins the human layer, and when — it is not a habit.
5. **Command palette / quick switcher**: reopens spec §3.1.7's deferral; the FTS title-not-indexed
   defect (spec §3.1.1) is fixed first regardless.
6. Where **JSON Canvas** files go — in the Markdown zip or beside `.tendril`.
7. Whether the Canvas page kind and the block share one composable at two sizes exactly as the
   mind map does (recommended) or the page kind keeps its own screen.
8. Nesting **rendering** past a few levels on a phone width — indentation budget, or a fold.

---

## §10. Relationship to the specifications

- This file is **upstream**. The spec records *how*; this file records *what for* and *against
  what*. When they disagree, the spec is amended, tagged **[Amended]** at the section, with a
  pointer here.
- **Anti-drift, extended.** Spec §12's rule — a `shared/` change gets both revision logs the same
  day — gains a third line: a decision here gets its spec pointer in the same pass that builds it
  (§7's last row). Nothing in §5 is "done" until the spec section it touches says so.
- **Scope decisions** (`docs/scope-decisions.md`) remain the register of what is *not* built and
  why; §6 here is the short list, that file is the evidence.
- **Benchmarks** (`docs/benchmarks.md`) are dated and **[Assumed]** by nature; rescore before
  relying on any row older than a release of the app it describes.
