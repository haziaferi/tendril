# Critique — Tasks as a desktop surface, on the 14f·1 build

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14f·1. Read-only;
observable-level findings. **Desktop** at 1200×800, Compact: Ctrl+3; Ctrl+Shift+N → *Pane
task*; click → the pane; *Add a step* → the step in list and pane; *Postpone…* → the slide-over;
*Start timer* / *Stop timer*; hover → `···`; right-click → the menu at the pointer; the Trash
button → the Entry Trash slide-over; Habits → click *Stretch* → the habit pane; the handle
dragged ~100 px. **Phone: not walked** — phone testing paused at the user's request; the
phone's half (a tap on a task row changing nothing, long-press → the menu, the habit row's `···`
replacing its ×, the Pages list's keyboard) is an open item on the PR.*

## Top priorities

1. **[Med · fixed before merge]** The pane kept its subject across tabs: a task stayed beside
   the Habits list. **Fix taken:** switching tabs empties the pane (`LaunchedEffect(tab)`).
2. **[Med · judged]** The filter rows (Tasks · Habits · Merged, Today · This week · This month)
   span both panes above the split, so the detail pane sits under filters that do not filter it.
   The mock drew them the same way. **Fix:** keep them above the list only, the pane's column
   starting at the bar — a layout move for 14f·2's pass over the tab (the Calendar's bar gets the
   same treatment), not this PR. Recorded.
3. **[Low · judged]** The pane's *Repeat* reads *none* for a task with no rule and *Every day*
   for one with it — right — but a task's repeat cannot be changed from the pane or the row (the
   Add sheet sets it once). Not new; §0.10 item 14's list.
4. **[Low · judged]** The habit pane with no presence yet says *Here whenever you want it.* under
   the title — the sheet's line, now beside an empty column. Fine, and the month of dots
   appears with the first check-in.
5. **[Low · by design]** A step selected in the list shows as its own task in the pane (no
   steps, its own chips) — a step is a task (§0.6.4).

## Dimension by dimension

- **Affordance** — the selected row's `primaryContainer` and the keyboard ring are two states,
  visibly; the `···` fades in on hover and stays while its menu is open; the chips are the
  menu's names, so a person who learned the row learns nothing new.
- **Reach** — every chip reached its sheet; the Trash button opened the shared slide-over with
  *Restore* / *Delete forever* (empty here); the handle resized and the width is stored.
- **Fit for purpose** — TickTick's list + detail without a third column: the filters are the
  lists. The pane reads a task in full at a glance — the row's subtitle unfolded.
- **Copy** — *Choose a task or a habit — or press Ctrl+Shift+N for a new task*; *nothing yet*
  for tracked time; *not marked* for importance; *Move to Trash* (the row's *Delete* renamed to
  what it does — the Trash sheet is the other half).

## What's working

- The split at the shell's own rule (`LocalShellLayout`), so a 900 dp window is one thing to
  the shell and the tab.
- The pane resolving its subject from the live lists: a step added, a timer started, a
  postponement — each showed without a refresh.

## Cannot verify

- **The phone** (paused): the row's long-press, the habit row's `···`, the Pages list's keys.
- The Merged list's keyboard and ↵ (the cursor over tasks then timed habits) — not exercised.
- A width beyond the window: the clamp is unit-tested, the handle not dragged to its ends.

## Disposition

#1 fixed. #2, #3 → §0.10 item 14's list (#2 with 14f·2). The phone walk → the PR's open item.
