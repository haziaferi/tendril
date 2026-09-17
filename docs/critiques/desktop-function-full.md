# Critique — the desktop, full function pass (3 of 3)

*2026-09-17 · `design-critique-plus`, pass 3 of the desktop audit: a walk of every feature once,
ordered by the spec's §3 sections and the after-the-pass PRs, on `main` at `57fc375` (the dev
build, the user's 967 × 1039 window beside Notion). Dimensions 1g copy · 1h interactivity · 1i
fit. Each row is *feature · what was tried · observed · finding*; findings are observable-level
(a screen has no underlying values; what a code read then confirmed is marked **[confirmed]**
with its site). Surfaces walked in the tray PR's function pass (the icon's menu, the toasts, the
chord's failure line, the switch on and off) were repeated in brief and not re-recorded unless
changed. **Phone: not walked.** The grabs are the layout pass's set.*

## Top priorities

1. **[High · seen twice, confirmed]** **The find bar reopens, empty, on every page opened after
   one Ctrl+F.** Ctrl+F on *Escape test* → `child` → Esc closed it; the switcher opened *Books
   v12*; the tree opened *Escape test* again → the bar was back with an empty field; then
   *journal/2026-09-13* → the bar again. `PageDetailScreen.kt:260` runs
   `LaunchedEffect(findRequest) { if (findRequest > 0) findOpen = true }` — the nav state's
   counter is never consumed, so a freshly composed page sees the old count and opens its bar.
   **Fix:** `var seenFindRequest by remember { mutableStateOf(navState.findRequested) }` and
   act only when `findRequest != seenFindRequest` (then store it) — the `quickAddRequested`
   pattern with its `clear`; one test on the pure half if it moves to the nav state
   (`consumeFindRequest()`).
2. **[High · seen, confirmed]** **The F1 card omits the shelf's chord.** Ctrl+Shift+\ is bound
   (`Shortcuts.kt:72`, `TOGGLE_SHELF`, wired at `WorkbenchScaffold.kt:127`) and works; the card
   lists *Hide or show the tree · Ctrl+\* and nothing for the shelf. 14e's invariant was *the
   overlay lists what is bound, generated from the table*; `shortcutRows()`
   (`ShortcutsOverlay.kt:101`) hand-writes the Navigate group and never iterates `SHORTCUTS`.
   **Fix:** the rows generated — every `ShortcutAction` in the table appears once, grouped by
   its `group`, the static rows (Esc, Ctrl+W, ↑↓↵, type) appended — and `ShortcutsTest` gains
   the invariant: `SHORTCUTS.map { it.first }.all { action -> rows.any { it.action == action } }`.
   The card's *This list · F1* is right (F1 replaced Ctrl+/ at some point; the table says F1).
3. **[High · seen]** **The habit pane is a stub.** Habits → *Stretch* → the pane reads *Stretch
   — Here whenever you want it. — Move to Trash*: no cadence (*Every day*), no time, no streak,
   no *Check in* / *Undo*, no *Start timer* — the row beside it has the ▶ and a tick; the phone's
   `HabitDetailSheet` has the check-in and the streak. 14f·1 said *HabitDetailPane =
   HabitDetailSheet's content inline*; what is inline is a placeholder. **Fix:** the sheet's
   content, as planned: cadence and time as label/value rows, the streak (when the switch is on),
   chips *Check in today* / *Undo* / *Start timer* / *Reminders…* / *Move to Trash*.
4. **[Med · seen]** **Delete view has no confirmation** — Books v12 → the Board chip twice →
   Configure "Board" → *Delete view* → gone at once. A row's trash asks; a page's trash asks; a
   view (with its filters, sorts, column choices) does not. **Fix:** the same `AlertDialog`
   confirm the others use (*Delete the "Board" view? Its filters and sorts go with it.*), or an
   undo snackbar.
5. **[Med · seen]** **A second click on the active view chip is the only way to Configure /
   Delete a view** — and nothing says so (the chip has no ▾, the database's `···` has no
   *Configure view*). Found by accident. **Fix:** a ▾ trailing glyph on the selected chip, and
   *Configure this view…* in the database's `···`.
6. **[Med · seen]** **The Board's empty state is a sentence at the top-left** — *Board needs a
   Select property, or a formula property, to group by. Add one, then configure this view.* —
   with no button to do either; the Calendar view's empty state (14f) has the same shape.
   **Fix:** the tab root's centred `description` with two buttons — *Add a Select property* (the
   Add property sheet) and *Configure view*.
7. **[Med · seen]** **The New view sheet is two controls** — *Type: table* (a text button
   opening a menu of lowercase enum names) and *Add*; no name field, no line on what each type
   needs. **Fix:** a name field (default the type's name), the five types as a chip row with a
   one-line description under the chosen one (*Board groups rows by a Select property*), *Add*
   as the sheet's primary button; the copy through `ViewType.label` (type pass #2).
8. **[Med · seen]** **Two menus, two vocabularies.** A page's `···`: *Show on Road Map · Find
   in page · History · Save as template · Move to Trash · Show beside ▸ · Open in a window ·
   Trash…* — *Move to Trash* and *Trash…* are two verbs on one word (the second opens the Trash
   sheet). A database's `···`: *Turn off Sync to Tasks · Add property · Bound to #book… ·
   Blocked by… · Save as template · Show beside ▸ · Open in a window · Trash…* — no *Move to
   Trash* (a database is trashed how? from the tree row's menu, which has it) and no *Show on
   Road Map*. **Fix:** *Trash…* → *Open Trash…* on both; *Move to Trash* and *Show on Road Map*
   on the database's; the order the same on both (the page's verbs, then the kind's).
9. **[Med · seen]** **The Add label sheet lists nothing until typed.** *Add label* → an empty
   sheet with *Label name*; typing `b` → *Create "b"* and *book*. Notion and Bear list the
   existing labels first. **Fix:** every label as a row under the field, filtered by the text,
   *Create "…"* at the top only when no exact match; the 14g·2 hue dot on each row (it is not
   visible at this size — verify it renders: the *book* row showed none).
10. **[Med · seen]** **"Someday (2)" with a switch reads as a section header** on the Tasks
    list, above rows that are today's, not Someday's. It is the *include Someday* filter with
    its count. **Fix:** *Show Someday · 2* as a `label` beside the switch, right-aligned with
    the filter rows, not a section title over the list.
11. **[Med · seen]** **The hover card joins the page's lines on one line** — *Root edited · Child
    one! · Child two* — where the plan (B§13.6 #3) said one line per block, up to six; the card
    was 240 px wide, not 320 dp. Possibly the mind map's fold (the three blocks are its nodes)
    collapsing to one `Mind map · n nodes` line that then rendered the node texts instead;
    observable only. **Fix:** verify `pagePreview` on a page whose blocks are a folded map; the
    card's width fixed at 320 dp regardless.
12. **[Med · seen, carried]** **The Reminders slide-over's preset row scrolls off** (*Custom*
    past *1 day*); the presets are unlabelled radio chips (*1 hour* selected by default with
    nothing saying *before*). **Fix:** `FlowRow`; the eyebrow *REMIND ME … BEFORE*.
13. **[Low · seen]** **The row menu's labels misalign**: *Postpone… · Add a step · Set
    deadline…* have no leading icon, *○ Urgency: None ▸* and *✕ Move to Trash* do, so the text
    starts at two x positions. **Fix:** icons on all five or on none (the page menu has none).
14. **[Low · seen]** **The shelf's neighbourhood overlaps its nodes on first draw** (*Call the
    library* over *Escape test* over *journal/*) and spreads ~1 s later; the Road Map tab's own
    layout overlaps *Call the library* and *Escape test* at rest. **Fix:** the force layout's
    minimum distance ≥ node height + 8 dp; the shelf's first frame after the layout settles.
15. **[Low · seen once]** **Switching to Agenda painted a black frame for over a second** in the
    tool's capture (the whole window black, the last row at the bottom) before the list drew;
    not reproduced on the second switch. The computer-use capture is known stale (the layout
    pass's caveat), so this is filed under *Cannot verify*, not as a defect.

## Walked — feature by feature

### Pages (§3.1)
| feature | tried | observed | finding |
|---|---|---|---|
| tree + empty state | open the tab | *Choose a page from the tree — or press Ctrl+K to find one.* centred | ✓ |
| open a page from the tree | click *Escape test* | the page beside the tree, row highlighted, `···` on hover | ✓ |
| the page's `···` | click | eight items (see #8) | #8 |
| History | `···` → History | the slide-over: five revisions, *yesterday · before an edit · 1 block(s)* | type #2 |
| Find | Ctrl+F, `child` | *1 of 3*, marks in the third hue, the current solid | ✓ — then #1 |
| the switcher | Ctrl+K, `bo`, ↵ | *Books v12* first, opens | ✓ (layout L6) |
| hover card | pointer on `@Escape test` 500 ms | the card under the line, *edited yesterday* | #11 |
| the shelf: graph | `···` → Show beside ▸ Road Map | the neighbourhood at depth 1, chips 1 2 3 | #14, layout L11 |
| the shelf: Journal | Show beside ▸ Today's Journal | today's page with the Today strip (two tasks, one habit) | ✓; type #2 (*day(s)*) |
| close the shelf | × | closed | ✓ |
| the tree: expand | Journal's chevron | three day pages | layout L12 |
| the New sheet | tree `+` | Title, four kinds | ✓ (type #3) |
| the label sheet | *Add label* | empty until typed | #9 |
| Trash | `···` → Trash… | two pages, Restore / Delete forever, Select all | ✓ |
| a Journal page's blocks | open 2026-09-13 | mention block, block reference (marked source), inline mention, callout with bar + tint | ✓ 14g·2 |
| a canvas page | *Garden plan* | two cards, FAB, delete discs | layout L9, L10 |
| a database: Table | *Books v12* | three rows, Blocked chip, *3 rows* footer | layout L3 |
| Timeline | chip | bars and the blocker line | layout L13 |
| Add view | *+ Add view* | the sheet (#7) → Board → empty state (#6) | #6, #7 |
| Configure / Delete view | click the active chip | the Configure slide-over: columns, sort, filter, Delete view | #4, #5 |
| the database's `···` | click | eight items (#8) | #8 |
| pop-outs, back/forward, templates, the slash menu, `((`, images, code, the canvas block, the label filter row | not re-walked | their own function passes stand (pop-out-function, find-in-page-function, shelf-function, hover-preview-function, small-things-function) | — |

### Calendar (§3.2)
| feature | tried | observed | finding |
|---|---|---|---|
| Week (the default) | open the tab | the grid from 00:00 (earliest block 00:21), today tinted, the now-line, the tray | layout L5, L8 |
| Day | segment | the list with ▶ and bell per row, *Planned 2h 15m* | layout L2 |
| Month | segment | the dot grid | layout L4 |
| Agenda | segment | grouped by day, tasks with boxes, events with times | layout L2; #15 |
| quick add (the strip) | `+`, `Dentist fri 14:30 !` | the field, chips *Event · Tomorrow · 14:30 · High*; Esc closed without adding | ✓ parse (fri = tomorrow ✓); layout L7 |
| the settings sheet | `···` | the sync sentence, Calendar (.ics) export/import | type #2 (the citation) |
| the tray, drags, layers, .ics, the bell | not re-walked | drag-between-panes-function, tray-function stand | — |

### Tasks & Habits (§3.3)
| feature | tried | observed | finding |
|---|---|---|---|
| the list + pane | click *Call the library* | the pane: When, Deadline, Repeat, Urgency, Tracked today; six chips; STEPS; the row-page note | ✓; type #2 (§ citation) |
| the row menu | right-click | five items at the pointer | #13 |
| Habits | tab, click *Stretch* | the stub pane | #3 |
| Merged | tab | the two tasks; the habit absent | *Cannot verify* (the habit may not be due) |
| Add (the FAB) | click | the Add task dialog: title, three switches, Repeats chips, Cancel / Add | layout L15, type #3 |
| Postpone | the pane's chip | the slide-over: eight presets, a custom row, Cancel / Postpone | ✓ |
| Set deadline | chip | Material's date picker (Italian) | type #3 |
| Reminders | chip | the slide-over | #12 |
| Trash | the bar's bin | *Trash is empty* centred with an × glyph | ✓ |
| Review | the bar's icon | *Last 7 days · 38m logged · 2 tasks done*, *1 of 3*, the database card with Open / Reviewed / Skip | ✓ |
| urgency menu, steps, the timer, ↑↓↵, the edit sheet | not re-walked | urgency-ladder-function, tasks-function stand | — |

### Road Map (§3.4)
| feature | tried | observed | finding |
|---|---|---|---|
| the tab | open | five nodes, one related edge, the filter row, the legend | #14 |
| the label menu | *Any label ▾* | *Any label · #book* | ✓ |
| focus | click a node | the rest dimmed | ✓ |
| filters, Relate to, ↻, drag | not re-walked | roadmap passes stand | — |

### Settings, the keyboard, the tray
| feature | tried | observed | finding |
|---|---|---|---|
| the register and mode | Chalk + Light, Ink + Light, Ink + System | the app re-solves live on every pick (native grabs: `#FBFBFA`, `#FFFFFF`, `#1B1D21`); `prefs.properties` follows | ✓ — the tool's stale capture said otherwise for a minute; recorded in the layout pass |
| density, opens-on, the task switches, the AI key, the sync folder, the notification area | read | present, the copy as shipped | ✓ (type #3 for the field) |
| F1 | key | the card | #2 |
| every chord | Ctrl+K, Ctrl+F, Ctrl+1…5 (by the rail), Esc | as bound | ✓ |
| the popup | Ctrl+Shift+Space (the stored chord) from Notion in front | the popup, the field focused, the two-verb foot; Esc closed | ✓ |
| the icon, the toasts, × to the tray | not re-walked | tray-function stands (DND; the overflow — the user's check) | — |
| the AI result sheet (a 401) | not walked | 8g's function pass stands; no key entered on this machine | — |

## Cannot verify

- #15 (the black frame): the tool's capture, not a native grab; not reproduced.
- Why the habit is absent from Merged (its due-today state is the domain's; the screen only
  shows the list).
- The seed's *Standup (team)Standup* title (three events) and the two *Dentist* events on the
  18th are the dev database's contents, not rendering — confirmed by a read of `entries`; the
  tray PR's "duplicate deleted" left one pair (ids 10 and 12).
- The phone's halves of everything above.

## Disposition

#1–#3 → the fix PR (*the audit's fixes*, with the layout pass's #1–#4 and the type pass's
#1–#2); #4–#15 → §0.10's small-things list as F4…F15.
