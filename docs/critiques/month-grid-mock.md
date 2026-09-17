# Critique — the Month grid, on the mock

*2026-09-17 · `design-critique-plus`, the pre-build pass over `docs/mockups/month-grid.html` — drawn
for this pass: the Calendar tab's Month on a wide window, the *+n* popover, the quick-add strip
seeded from a cell, the database Calendar view on the same grid, Chalk light, and the phone's dot
grid with one dot per layer. Measured with `craftkit ui` (1200 elements; 0 errors, 4 warnings, 2
notes — the sprawl warnings count the page's own chrome and six frames' tokens together, as on
every mock) and by hand for the pairings inside the frames; judged on the render at 1000 px.
Read-only. The Ink and Chalk values are the 14g·2 prototype's solves (`proto14g2.palette2`), not
picked by eye.*

## Grounding

Measured live on this machine at 125 % (native grabs, `audit/ground-notion-month.png`); the rest
[Assumed] from knowledge, as `docs/benchmarks.md` marks its unmeasured rows.

| app | rows | cell number | items | overflow | create from the grid | adjacent days | today | status |
|---|---|---|---|---|---|---|---|---|
| Notion — database Calendar view (dark) | **124 px** empty; a row with five chips **grows to 204** | top-right, *Sep 1* / *Oct 1* on the 1st | chips **28 px** at a **34 px pitch**, inset 5 px, ≈ 12 px CSS text, drag between days | none — the row grows, the grid scrolls | hover `+` at the cell's top-left → a new page on that date | dim, with their items | a **22 px** filled disc | **measured** (a new page *Month grid ground*; nothing existing touched) |
| TickTick | six equal rows | top-left | — | — | — | shown | — | Month view is **premium** (paywall dialog); geometry seen behind it only |
| Todoist | — | — | — | — | — | — | — | calendar layout is **Pro** (locked in Display → Layout) |
| Notion Calendar | equal rows filling the pane | top-left | one line per event, time first | *+n more* → a popover | click creates | dim | a disc | [Assumed] — the app sits on a blank *Log in* window (a Google sign-in) |
| Google Calendar · Outlook · Fantastical | equal rows filling the pane (Google: the weeks the month spans) | top-left (Apple Calendar: top-right) | one line per event, time first, coloured by calendar | *+n more* → a popover | click (Google) / double-click (Fantastical) creates | dim | a disc | [Assumed] |
| Tendril today (`audit/cal-month-ink.png`) | six rows of 40 dp in the top quarter of the pane | centred | one `•` | — | — | blanks | `heading` weight | the audit's L4 |

**Feature scoring** — Reach (how many of the grounds do it) / Fit (Tendril's constraints: proportional
to the window, one composable per kind, the pointer/Touch split, nothing placed for the person) /
Gap (distance from what is built) / Value, each 1–5; the *Take* is the mock's.

| feature | option | Reach | Fit | Gap | Value | Take |
|---|---|---|---|---|---|---|
| row height | equal rows filling the pane | 5 (all but Notion DB) | 5 (the audit's finding was the unfilled pane) | 2 | **5** | ✔ rows = the weeks the month spans (5 or 6), `cellH = pane / rows` |
| | content-sized rows, the grid scrolls (Notion DB) | 1 | 2 | 2 | 2 | — |
| overflow | *+n* → a popover listing the day | 4 | 5 (the month stays; the hover card's frame exists) | 3 | **5** | ✔ (decided) |
| | *+n* → the Day view (the Week's all-day rule) | 1 | 4 | 1 | 3 | — |
| | the row grows (Notion DB) | 1 | 1 | 2 | 1 | — |
| items | the Week's all-day chip (tint, stripe, one line) | 5 | 5 (one composable, by construction) | 1 | **5** | ✔ `OccurrenceChip` extracted; a tabular time prefix |
| visible count | computed from the cell's height | 5 | 5 (proportional — the standing rule) | 2 | **5** | ✔ `cellCapacity`; `visibleAllDay` becomes `visibleInCell(items, 3)` |
| | a fixed three (the Week's) | 1 | 2 | 0 | 2 | — |
| create from the grid | a click on the ground → the quick-add strip seeded with the day | 4 | 5 (the strip and the parser exist; `selectedDate` is its base) | 2 | **5** | ✔ (decided) |
| | click → the Day view (today's) | 0 | 3 | 0 | 2 | — |
| | select only, double-click opens | 1 | 3 | 1 | 2 | — |
| the number's corner | top-left | 4 | 5 (the Week header's `EEE d` is left-aligned — one edge for number and chips) | 1 | **5** | ✔ |
| | top-right (Notion DB, Apple) | 2 | 3 | 1 | 3 | — |
| adjacent-month days | shown dim, with their items | 5 | 4 (the range widens to the grid) | 2 | **4** | ✔ `monthRange(grid)` |
| the 1st's label | *Sep 1* / *Oct 1* | 3 | 5 | 1 | 4 | ✔ |
| drag between cells | yes, the same write as the Week strip's `onMove` | 4 | 5 (the series prompt exists) | 2 | **4** | ✔ extras do not lift |
| the database Calendar view | the same grid (Notion's is) | 1 (only Notion has one) | 5 (one grid, two homes; the audit refuses a second) | 3 | **5** | ✔ (decided) |
| the phone | the dot grid, one dot per layer | — | 5 (a 40 dp cell holds no chip) | 1 | 4 | ✔ + the weekday header |

Where the mock improves on the grounds: the visible count is arithmetic on the cell (every ground
fixes it or lets the row grow); the *+n* popover is the hover card's frame (Notion's database
calendar has no overflow); the create path runs through the parser's chips rather than a blank
page; the database calendar and the tab's calendar are one composable.

## Top priorities

1. **[High · measured]** **The time prefix in `dim` fails on every tint.** The mock's first draft
   drew *09:00* in `--dim` on the chip: **3.04–3.45 : 1** on Ink dark's `soft` / `eventSoft` /
   `habitSoft` / `thirdSoft`, **3.91–4.15** on Chalk light — under 4.5 at 11 px. The register
   solves `dim` on the ground and `surface2` only, never on a tint; `text` is the one colour
   proven on the tints (`RegisterSolveTest`: ≥ 4.6 on every soft). **Fix (applied to the mock):**
   the time in `text`, tabular, Regular; the title at Medium — weight carries the hierarchy, as
   the type PR's rule says. Measured after: 6.7–7.6 dark, 9.2–9.7 light.
2. **[High · measured]** **Adjacent-month chips at 60 % opacity fail**: `text` over `soft`, both
   composited at 0.6 on the ground, measures **3.97** dark and **3.19** light. Google and Notion
   dim the *number* only. **Fix (applied):** chips at full opacity everywhere; the number in
   `onSurfaceVariant` is the "not this month" signal.
3. **[Med · measured]** **The chip is 20 px tall** in the first draft against the profile's 28 dp
   list minimum — but Notion's chips measure **28 px ≈ 22 dp** at the same zoom, and a 28 dp hit
   area on a 20 dp chip overlaps its neighbour's in a stack. **Fix (applied):** the chip at
   **22 dp** (Notion's), pitch 24, no invisible minimum — a stated deviation from
   `listInteractiveMinDp`, with the ground beside it; the popover's *Open the day* row at 28 dp.
4. **[Med · judged]** **The popover's way out was a caption** (*6 · open the day ›* at 11 px in
   `dim`) — an action in the smallest, quietest style. **Fix (applied):** a foot row *Open the
   day ›* in `body` on the accent, 28 dp, under a hairline; the count stays a caption in the
   header.
5. **[Med · judged]** **The Month still sits under 184 px of chrome** (the audit's L5: the bar,
   the full-width segmented row with its 16 dp padding, the layer chips) and then its own
   ‹ month › row — four bands before the first cell. Not this PR's (L5 is Med, its own line in
   item 22), but the mock shows the cost: at the user's window the grid keeps ≈ 770 px of 1039.
   Recorded, not fixed.
6. **[Low · judged]** **The phone's dots at 5 dp** read at Touch scale (≈ 13 px) but three in a
   row under a two-digit number are 21 dp wide against a 40 dp cell — fine; four layers would not
   fit, and the mock caps at three (task, event, habit, database date are four kinds). **Fix:**
   the cap stays three, the fourth folds into the third's slot by precedence task > event >
   habit > database — said in the spec; or the cell's dots row wraps to two. The first.

## Dimension-by-dimension

**First impression** — a calendar: the weekday eyebrow, the numbers, the tinted lines. Thursday's
disc is the first thing the eye lands on, then the coral stripe on *Dentist*; the second read is
the rhythm of Standup down the weekdays. Nothing competes with today.

**Hierarchy** — number (`label`, Medium) → chips (`caption`, title Medium, time Regular) → *+n*
(`caption`, dim) → the popover's `heading`. One weight step separates the number from a chip
title; the same size separates a chip from its time by weight, not colour (after #1).

**Type** — five sizes on the page, all on the scale (11 · 12.5 · 14 · 15 (the lead only) · 18);
inside the frames four: 11 (chips, eyebrow, captions), 12.5 (numbers, the strip's chips), 14 (the
month title, the popover's heading, the strip's field), 18 (the database page's title). The
eyebrow's tracking at 0.06 em on the weekday row is the tray's.

**Colour** — after #1 and #2 every text sits ≥ 4.6:1 on its ground; the chips' tints are the token
map's (`accentSoft`, `eventSoft`, `habitSoft`, `thirdSoft`); the stripe is the ladder's; the
today disc is `primary` / `onPrimary` at 7.65 dark, 8.41 light; the drag/click highlight is
`accentSoft` + a 2 dp `primary` outline, the strip's own. No colour does no work; weekends carry
none.

**Layout** — 900 × 702 in the frame: 40 + 22 + 5 × 128; at the user's window the rows come out
at ≈ 150 px (five in ≈ 770 px of pane after the chrome), which holds five chips or four and a
*+n*; a six-row month holds four. Cells inset 4 dp (Notion 5 px), chips 2 dp apart (Notion 6 —
Tendril's Compact density, stated). The hairlines are `outlineVariant`, one family with the Week
grid's hour lines.

**Copy** — *+3*, *Open the day ›*, *No date · 1*, *Enter adds · Esc closes* (the popup's); the
month title is the phone's ‹ September 2026 ›. One thing to decide at build: the popover's day
label reads *Thursday 17 September* — the Day view's header format, reused.

**Consistency** — the chip is the Week's (extracted, one composable); the popover is the hover
card's frame; the highlight is the drag target's; the strip is the Calendar's; the dots' hues are
the token map's; the database page's bar is 14c's. The one deliberate difference from Notion's
own calendar is the number's corner.

**Interactivity** — the mock shows the ground click's result (C) and the *+n* result (B); it
cannot show hover. At build: a cell's ground shows a `+` glyph in `onSurfaceVariant` on hover at
the number's right (Notion's affordance, hidden on Touch); chips lift on press-and-move under a
pointer with the drag PR's ghost; the number is the Day's link and gets the pointer's hand.

## What's working

- The count a cell shows is arithmetic on its height — the standing rule (proportional, never
  fixed) made into a function with a test, and the same function fixes the Week's *three*.
- One grid, two homes: the database Calendar view stops being a list without a second layout.
- The layer hues are already there (14g·2); the Month is the first surface that shows all four
  side by side.

## Cannot verify

- Notion Calendar's, Google's and Fantastical's rows are cited, not measured; the numbers in the
  Grounding table for them are conventions, not pixels.
- TickTick's and Todoist's month views are behind paid tiers on this account.
- The hover `+`, the drag ghost and the popover's dismissal are behaviour — the function pass on
  the build measures them.
- The phone's dots at Touch scale: not rendered at 375 × 812 with the app's density; the mock's
  frame is CSS px.

## Disposition

The four High/Med measured findings are applied to the mock (time in `text`, no opacity on
adjacent-month chips, chips at 22 dp, the popover's foot row); #5 stays recorded under item 22
(L5); #6 is a spec sentence. Build on the mock as it stands.
