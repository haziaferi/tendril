# Critique — the Tasks and Calendar frames of the shell mock, before 14f

*2026-09-16 · `design-critique-plus`, pre-build pass for B§13.4 14f. Read-only. On
`docs/mockups/desktop-shell.html` in the *proposed* preset at 1200×800: the Tasks tab
(`tasksTab`, two panes: a 420 px list, a 640 px detail) and the Calendar tab in Week
(`calendarTab`, the time grid). **Measured** by walking the rendered frames' computed styles;
**judged** on the frames and against the app as it is (`TasksHabitsScreen`, `CalendarScreen`).
The phone changes nothing in 14f except what the Trash sheets' move touches — no phone pass.*

## Top priorities — Tasks

1. **[High · measured]** The detail pane's labels — *When · Deadline · Importance · Repeat* —
   are 13.5 px at **3.08:1** (`--faint`), and the unset values (*none*, *does not repeat*) the
   same. The pane's whole skeleton is under AA. **Fix:** labels `onSurfaceVariant` (≥ 4.9:1),
   unset values the same colour in italics, never `outlineVariant`.
2. **[Med · measured]** A row's meta (*Tue · 09:00*) is 12 px at **2.55:1** on the current
   row's `accentSoft`. **Fix:** `onSurfaceVariant` everywhere the row's ground changes; the app's
   `TaskRow` subtitle already uses it — keep that, not the mock's.
3. **[Med · measured]** The row's checkbox is 16 × 16 px; the pointer floor is 24. The row's
   click opens the task, so the box is the only way to tick — a 24 dp target with the 16 dp
   glyph, as Compose's `Checkbox` already gives (48 dp min interactive). Keep the app's.
4. **[Med · judged]** The detail's action chips (*Postpone ▾ · Someday · Flag · ▶ Start
   timer*) duplicate the row's `···` (*Postpone… · Add a step · Set deadline… · Important ·
   Delete*) with different names: *Flag* vs *Important*, *Someday* (no such action in the app —
   a task with no date is "Someday" by the list's filter, not a verb). **Fix:** the pane's chips
   are the menu's items by name, plus *Start timer* (the row's ▶) and the reminder bell where
   reminders exist; *Add a step* belongs in the pane's steps list as its own row.
5. **[Low · judged]** The pane's *Blocked by* note (§0.6.14) is right — it exists only for a
   row-linked task — and is the one place the pane says something the row cannot.

## Top priorities — Calendar (Week)

6. **[High · measured]** The 24-hour grid is 768 px tall in a 665 px frame with `overflow:
   visible` — the mock's grid runs off the frame and nothing scrolls. **Fix:** the grid scrolls
   inside the tab (a `verticalScroll` column under a sticky day header and all-day row), and
   opens scrolled to 07:00 (Fantastical, Google), not 00:00.
7. **[Med · measured]** Task chips in the grid (`.ev.t`) read at **2.70:1**; the hour gutter's
   labels 11 px at **3.08:1**. **Fix:** the chips take the layer's tint with `onSurface` text,
   never a lightened text on a light tint; the gutter `onSurfaceVariant`.
8. **[Med · judged]** *Quick add — "Dentist Tue 14:30"* in the bar carries **Ctrl+⇧N**, which
   14e bound to *New task* (the Add sheet). One chord, one action. **Fix:** the field has no
   chord; it is the Calendar's own, clicked or reached by Tab. (The parser and its chip preview
   exist — `QuickAddParser`, `QuickAddPreview` — on the Day view today; 14f moves them to the
   bar on a wide window.)
9. **[Low · measured]** Day columns 149 px at 1200 px: seven columns fit; at the 800 px minimum
   window with the rail they would be ~95 px — the strip (the app's current Week) is the
   fallback below 840 dp anyway, so the grid never draws that narrow.
10. **[Low · judged]** The all-day row is 34 px, one line: two all-day items on a day stack out
    of it. **Fix:** the row grows to its content, capped at three lines with "+n".

## Dimension by dimension

- **First impression** — Tasks: TickTick's three-column shape minus the third (lists are the
  filter chips), which is right for a personal app. Calendar: a week grid, Fantastical's; the
  quick-add field in the bar is the one thing that says "this is where you type".
- **Hierarchy** — Tasks: the detail's 24/600 title leads; the list's 12.6 px rows (7.75:1)
  recede correctly; the *Habits due* header (12 px uppercase) separates without shouting.
  Calendar: today's column tint and the current-hour line (not mocked) are the two anchors.
- **Colour (measured)** — Tasks: rows 7.75:1, *Blocked* chip 7.17:1, the pane's note 5.43:1;
  #1–#2 are the failures. Calendar: headers 5.43:1, events 5.43:1; #7 the failures.
- **Layout** — Tasks: 420 / 640 split at 1200 px; the list's width should be the tree's kind
  of thing (remembered, resizable) — one `PagesTreeState`-shaped state. Calendar: #6, #9, #10.
- **Consistency** — the detail's chips (#4); the Calendar's segmented Day/Week/Month is the
  app's own `SingleChoiceSegmentedButtonRow` (Agenda is a fourth value the mock omits — keep
  it, it is the phone's list view).
- **Interactivity** — Tasks: `.row .more` at opacity 0 → the 14d rule (28 dp, hover, right-click
  = long-press) reaches task and habit rows here, as 14d deferred. Calendar: a chip's drag
  between days exists in the app's Week strip (`onMove`); the grid keeps it (a column drop =
  the date) and leaves the time-drop to the *drag between panes* PR.
- **Copy** — *Habits due*, *Quick add — "Dentist Tue 14:30"* (an example in the placeholder:
  right); *Someday* and *Flag* (#4).

## What's working

- The split itself: a task's sheet content as a pane, so the phone's sheets and the desktop's
  pane are one set of controls in two homes.
- The Calendar's opening view as a Settings choice (decided 2026-09-13): Week on a wide window,
  Month on the phone.

## Cannot verify

- The app's rendering; a drag in the grid; the current-hour line (not mocked); dark register.

## Disposition

#1, #2, #7 → the plan's colour rules; #3 keep Compose's targets; #4 → the pane is the menu by
name; #6 → the grid scrolls, opens at 07:00; #8 → no chord on quick add; #10 → the all-day row
grows. A function walk on each 14f build before its merge.

## Addendum (2026-09-16, before 14f·2) — the app's Day timeline and Week strip, judged

*The Calendar as built, on the desktop at 1200×800 (Compact), for what the week grid can reuse
and what it replaces. Read from the screen and `PlanView.kt` / `DayTimeline.kt`.*

11. **[Med · judged]** The Week on a wide window is seven cards with bulleted titles — no time
    axis, no durations, the same words seven times; a week's *overview*, not its shape. The grid
    replaces it above 840 dp; the strip stays the phone's (and the narrow window's) form.
12. **[Low · judged, reuse]** The Day's Plan mode already has the geometry the grid needs —
    56 dp per hour, a 44 dp gutter, a 24-hour canvas, the now-line, blocks sized by span or
    estimate (dashed when estimated), a drag that snaps to the quarter hour
    (`timelineBlocks`, `PlanView`). The grid is seven of these lanes under one scroll and one
    header; the drag gains a horizontal axis (the column = the date) at almost no cost, so
    time-drop and date-drop land in one gesture rather than the *drag between panes* PR.
13. **[Low · judged]** The Day view's header — ‹ *mercoledì, settembre 16* › with *Planned 30m*
    under it — is the header the grid wants per column: day and date, today tinted, the day's
    planned minutes as a quiet second line. The mock's header had only the number.
14. **[Low · judged]** Quick add is a full-width field under the date header on the Day view; the
    mock put it in the bar. On a wide window one home is enough: a *Quick add* button in the bar
    opening a strip under it (the find bar's pattern) with the same field and chip preview, on
    every view; the Day view keeps its inline field on the phone.
15. **[Low · judged]** Plan mode's *Unplanned · drag onto the day* rail is the Day's; the grid
    shows no unplanned rail (Tasks is where they live) — dragging a task onto a week day is the
    *drag between panes* PR (a task from the Tasks list onto the grid).
