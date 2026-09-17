# Critique — the Month grid, on the build

*2026-09-17 · `design-critique-plus`, the after-pass for the Month grid PR (L4 of the desktop
audit; the dots PR folded in). Read-only; native grabs at the user's 967 × 1039 window, Ink dark
and Chalk light, the PIL line probe (`lines.py`) beside the Notion database calendar grabbed the
same morning at the same 125 %. The walk is the plan's verification list, each step once.
**Phone: not walked** — the phone's form was checked on the desktop window below 840 dp.*

## Measured — beside Notion (device px at 125 %)

| | Tendril (Compact, five-row month) | Notion database calendar | note |
|---|---|---|---|
| row pitch | **149** (= the pane's 745 / 5, ± 1) | 124 empty · 204 with five chips | Tendril's rows are equal and fill the pane; Notion's grow |
| column pitch | 125 | 99 | the pane's width / 7 either way |
| chip height | **22** | 28 | 22 dp = Notion's 28 px at this zoom (the mock critique's #3) |
| chip pitch | **24** | 34 | 2 dp gap against Notion's 6 — Compact's density, as stated |
| today's disc | ≈ 22 | 22 | |
| chips a cell shows | **4** (three and *+n* on overflow) | all | `cellCapacity(149, 22.7, 24.7, 8.2)` = 4 |
| weekday header | 22 dp, eyebrows in capitals | 51 px block | |

The mock's arithmetic held: at the user's window the five rows are 149 px each, and a cell shows
four chips — the plan's estimate was "≈ 150, four and a *+n*".

## Walked

- **The grid** — Calendar → Month: five rows filling the pane, LUN … DOM, *set 1* and *ott 1*
  (the locale's month names, as `dayLabel` reads them), the 31st and Oct 2–4 dim with their items
  at full strength, today's disc on the 17th, Standup on every weekday with its time first in
  tabular figures, the urgency stripe on *Read chapter 3*.
- **Overflow** — three items added to the 16th (seven in all) → the cell shows three and *+4*;
  *+4* → the popover **over the cell under its number row** (the first build opened it under the
  whole cell, covering the next row — fixed before this pass), *mercoledì 16 settembre · 7*, the
  seven chips, *Open the day ›* → the Day view on the 16th; Esc closes it.
- **Ground click** — a click on the 16th's empty ground: the strip opens, its base the 16th;
  *Extra one* ↵ → the chip appears in the cell; the hover `+` shows at the number's right.
- **Drag** — *Keyboard task* dragged from the 15th to the 21st: the ghost reads *Keyboard task →
  lun 21*, the 21st lights, release → moved (grabbed natively; the first build did not move it —
  `dragSource`'s pointer block keeps the lambdas it started with, so the drop read a target
  captured before the drag; the drop now reads the live state). A *Standup* occurrence dragged to
  the 27th → *Move "Standup (team)Standup"? · All / This one* — the Week strip's prompt; *This
  one* → the 22nd's chip is gone, the 27th has it. An exception occurrence (the 20:15 Standup)
  moves without the prompt, as the rule says.
- **The database Calendar view** — *Books v12* → *+ Add view* → Calendar → the empty state, the
  view chip → *Plot by* → *Read on* → the grid: *Call the library* on the 15th, *Escape test* on
  the 19th, *Reading log* on the 22nd in the third tint; *Reading log* dragged to the 25th → the
  Table's *Read on* reads 2026-09-25. No hover `+`, no ground click (by design).
- **Chalk light** — the same grid re-grabbed: numbers, chips and the disc read; nothing moved.
- **Below 840 dp** — the window at 820 × 1039: the bar layout, Month → the phone's dot grid with
  the weekday header (L M M G V S D), today's disc, a dim dot under a task's day, an event's
  green, the third hue on the 25th; a tap opens the Day.

## Findings

1. **[Med · measured, fixed before this pass]** The popover anchored under the whole cell. Now
   `Rect(cell.left, cell.top, cell.right, cell.top + numberRow)` — over the cell.
2. **[High · observed, fixed before this pass]** The drop did nothing: the target was a value
   captured when the pointer block started. The drop now computes it from the live state.
3. **[Low · judged]** The chip's title is Regular, not the mock's Medium: the vocabulary has no
   11 sp Medium mixed-case style (`eyebrow` is capitals), and a new style for one chip is not
   worth its audit row. Position carries the hierarchy — time first, title after — and the
   register's `text` on both. Recorded, not fixed.
4. **[Low · observed]** Typing a second line into the strip **at automation speed** (the next
   character within ~100 ms of ↵) lost characters twice (*Extra two* → *Ex*; *Extra four* ↵
   *Extra five* → one item *Extra fourExt*); at a human pace (2 s between) both lines arrived
   intact (*Extra six*, *15:00 Extra seven*). The field now holds a `TextFieldValue` rather than a
   String — the usual cause of a reset racing the next keystroke — but the fast case was not
   re-run, so this is a note, not a claim. The strip's, not the Month's.
5. **[Low · judged]** The Calendar tab forgets the Month when left and re-entered (the *opens on*
   default wins) — the tab's rule since 14f·2, not the grid's. Recorded.

## Cannot verify

- The phone: the dot grid was checked on a narrow desktop window under the pointer profile's
  scale, not on the device at Touch.
- Notion Calendar's, Google's and Fantastical's grids: cited, not measured (the mock critique's
  Grounding table).
- Finding #4's fast case after the `TextFieldValue` change.

## Disposition

The two build defects fixed and re-walked; #3–#5 recorded. Merge.
