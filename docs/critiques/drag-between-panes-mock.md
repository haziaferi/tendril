# Critique — drag between panes, on the mock before the build

*2026-09-16 · `design-critique-plus`, the pre-build pass for B§13.6 #5, on
`docs/mockups/drag-between-panes.html` — drawn for this pass: the Calendar's task tray beside the
week with a chip mid-drag, the phone's tray under the Week strip, and a Timeline's *No date* row
over a day column. `craftkit ui` (0 errors, 5 warnings, 2 notes — the sprawl is the mock's; the
app's scale is settled) and `craftkit render` at 1300 wide, read whole. Measured and judged
findings labelled.*

## Top priorities

1. **[Med · judged]** The desktop frame lights **two targets at once** — Thursday's all-day cell
   and the 10:00 slot under the ghost. A drop has one target: the thing under the pointer. **Rule:**
   exactly one target lights — the all-day cell while the pointer is in the all-day row, the
   quarter-hour slot while it is on the grid, the tray while it is over the tray (a block coming
   back).
2. **[Med · judged]** The *Overdue* row's *due Mon 8* is set in the urgent red. 14g·3's decision
   stands: a row's text keeps one colour, the stripe says how urgent. **Fix:** the due date in
   `onSurfaceVariant`; the stripe is already the ladder's top step.
3. **[Med · judged]** The tray's hint *Drop a block here to unschedule it* names the gesture but
   not what it costs: a task's **When** goes, its Deadline stays (§0.6.4 — the two dates are two
   things). **Copy:** *Drop a block here to clear its When*. A series is refused with a snackbar
   (*A repeating task keeps its days — edit the series*), as its own drags ask *this one or all?*.
4. **[Med · judged]** The tray at 280 dp beside a 1200 px window leaves the seven lanes 116 dp
   each — legible, but a fixed 280 is the tree's mistake before 14c's handle. **Fix:** the tray is
   a `PaneWidthState` (240…360, default 280, `calendar_tray_width`), collapsible from its header
   (`calendar_tray_collapsed`), the same handle as the tree's and the shelf's.
5. **[Low · measured]** Chips 36 dp on the desktop, 40 on the phone, the all-day cells 34 dp, the
   Week strip's day cells 48: every drop target clears 28 dp under a pointer and 44 under a
   finger. The ghost's −1.5° tilt is decoration the app draws nowhere else — **a shadow, no tilt**.
6. **[Low · judged]** The phone's Day view already has the Plan rail (today's untimed and Someday
   tasks, dragged onto the hour grid). Two rails on one view would say the same thing twice:
   **the tray shows under Week and Month; the Day view keeps its rail** (which also places a time).

## Dimension by dimension

- **Hierarchy** — the tray is a list of chips with two section labels and a hint at its foot;
  the grid is unchanged. The ghost is the chip itself with the target named (*Thu 17 · 10:00*),
  which is what tells the person where the drop will land before it lands.
- **Copy** — *Unscheduled* and *Overdue* (Things' *Anytime*, Akiflow's *Overdue*); *Nothing
  unscheduled* when empty, as the Plan rail says. The target name on the ghost is the drop's
  contract.
- **Consistency** — the drag is the Plan view's (`PlanDrag`: root-relative positions, the grid's
  `minuteAt`, the quarter-hour snap) grown to a pane; the drop is the same `EntryEditor.move`
  the grid's own drag makes, so a series gets the same *this one or all?*. The Timeline's drop is
  `setDateCell`, the same write the bar's drag and the Table's cell make.
- **Fit** — under a pointer the drag starts on press-and-move (a long press on a mouse is a
  wait); under Touch on a long press (the rail's rule — a plain touch-drag would fight the scroll).
- **Interactivity** — the tray is also a target (a block back to unscheduled); the grid's own
  block drag already exists and keeps its long-press start under Touch.

## What's working

- Both homes already have half the mechanism: the Plan rail's drag, the grid's `GridDrag`, the
  Timeline's *No date* list. The PR is the tray as a pane, one geometry the tray's drag can ask,
  and the Timeline's rows made draggable.
- Nothing is placed for the person (§0.5.2): the tray offers, the drag decides.

## Cannot verify

- The feel of a press-and-move drag start beside a scrollable tray under a pointer (the chip's
  click vs drag threshold); the phone's long-press drag over the Week strip while the list under
  it scrolls.

## Disposition

#1–#4 and #6 fold into the plan; #5's tilt is dropped.
