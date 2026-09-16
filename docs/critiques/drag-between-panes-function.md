# Critique — drag between panes, on the build

*2026-09-16 · `design-critique-plus`, the post-build function walk for B§13.6 #5. Read-only;
observable-level findings. **Desktop**, Ink dark, ≈ 1200 × 800, Calendar → Week: the tray
(Unscheduled · 0, Overdue · 4 on the seed); *Call the library* dragged onto Thursday's header;
*Escape test* onto Friday 17:30; *Escape test*'s block dragged back onto the tray; the *Standup*
series dragged onto the tray; collapse and expand; the handle; Pages → *Books v12* → Timeline: a
*No date* row (*Reading log*, added) dragged onto the 22nd. The user watched the Timeline and
sent three notes mid-walk. **Phone: not walked** (testing paused) — the strip is composed under
Touch only.*

## Top priorities

1. **[High · seen, fixed before merge]** The first build's drag started **230 px right of the
   pointer** and dropped nothing: `detectDragGestures`' start offset was not the pointer's local
   position (it reported the chip's far edge), and the gesture detector — keyed on the entry so
   a recomposition does not cancel a drag — held the **first composition's** drop lambda, whose
   target was null. **Fix:** `dragSource` is hand-rolled over `awaitEachGesture` (the down's own
   position; slop or long press; `drag`), and the callbacks it holds are `rememberUpdatedState`'s.
2. **[High · the user, fixed before merge]** *"In the timeline view, all entries are immediately
   cut off and unreadable"* — a one-day bar is 36 dp and its title was an ellipsis. Two forms
   were tried and corrected on the user's notes the same hour: the title beside the bar on the
   ground (*"looks like it falls on the next day"*), then in a quarter-strength run of the bar's
   colour; **the rule that stands is Notion's — the colour envelops the whole title**: the bar is
   as wide as its days or its title, whichever is more, and a 2 dp line in the ink along its foot
   marks the true span.
3. **[Med · seen, fixed before merge]** A grid block dragged over the tray drew its ghost
   **under** the tray (the tray's `zIndex` for its handle). **Fix:** the grid hides its ghost off
   the grid over a target the screen owns, and the screen draws *〈title〉 → Clear When* above
   the tray.
4. **[Low · seen, fixed before merge]** The tray chip's title was squeezed to *Call the li…* by
   the due label; the title takes the row, the label sits at its end. The refusal snackbar said
   *task* for what was an event series → *A series keeps its days — change it from its sheet*.

## Walked

- **Onto a day** — *Call the library* (due Sat 12, 15:00) dropped on Thursday's header: the
  ghost read *→ gio 17*, the task moved to Thursday and kept its 15:00 (`move`'s rule); Overdue
  went 4 → 3.
- **Onto an hour** — *Escape test* over Friday: the quarter-hour slot lit (a dashed rounded rect
  in the accent), the ghost read *→ ven 18 · 17:30*, the drop placed the block at 17:30 and the
  header's *Planned* grew.
- **Back to the tray** — the block long-press-dragged over the tray: the tray took `accentSoft`
  and the accent outline, the ghost read *→ Clear When*, the drop moved the task to
  *Unscheduled · 1*, its block gone.
- **A series** — *Standup* dropped on the tray: the snackbar; the series untouched.
- **The pane** — collapse → the chevron in the Calendar bar; expand; the handle to 360
  (`calendar_tray_width=360`, `calendar_tray_collapsed=false` in `prefs.properties`).
- **The Timeline** — *Reading log* (no date) dragged: the 22nd's column lit, the ghost read
  *Read on → 22 set*, the drop wrote the cell and drew the bar; the *No date* list emptied.

## Judged

- **[Low]** A day-header drop's highlight is hidden under the ghost when the pointer is over the
  header itself (the ghost sits 12 dp right, 18 up); the all-day cell shows. Not worth moving the
  ghost — the ghost names the day.
- **[Low]** The Timeline's dependency line crosses a title now that bars are title-wide (as
  Notion's do). Recorded.

## Cannot verify

- **The phone**: the strip under Week and Month, the long-press start, the strip's day cards and
  the Month's cells as targets, the Day view's rail untouched.
- **Overdue → a series' occurrence**: never listed by rule.

## Disposition

#1–#4 fixed. Nothing open; merge.
