# Critique — the token map, on the 14g·2 build

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14g·2. Read-only;
observable-level findings. **Desktop** at the remembered frame, Windows in dark mode: Ink · System
(dark) → a page with Ctrl+F; Ink · Light → the page strip and filter chips, an `@mention` typed
through the picker, a callout inserted from the slash sheet and recoloured through its action
sheet, the Calendar's Week, Day and Month, the Road Map and its label menu, a canvas with two
cards and a drawn edge; Console · Dark → the same page, the Week, the Road Map with a canvas node.
Pixel colours read from the screen for the marks, the chips and the mention. **Phone: not
walked** — testing paused; the chips, the callout and the marks are the same shared code.*

## Top priorities

1. **[Med · judged]** On **Ink light** an `@mention` in the accent (`#465163`, measured) sits
   beside body text at `#393936`: two dark greys, told apart by weight alone. Ink's accent is a
   slate by design (B§13.7.2) and the link keeps its underline, so the rule holds — but the
   mention's only signal in Ink is *Medium*. Not changed: the accent is the register's choice and
   nine other registers carry it plainly. Recorded for the shelf's "small things" as a candidate
   for a faint underline on mentions, if the eye wants it.
2. **[Low · judged, pre-existing]** The Road Map needed its ↻ before a page created during the
   session appeared — not 14g·2's; a line for the small-things list.
3. **[Low · judged]** The Day view lists a database date as a row with the table icon, so the "◦
   title" lines the plan expected to recolour there are the Month/Agenda's; the code change stands
   (habit → habit hue, database date → third) and is the dots PR's to draw as dots.

## Dimension by dimension

- **Colour** — measured on screen: Ink dark's current find match `#D1BC94` (the third) with the
  ground on it, its other marks on `#5D564A` with the text; Console dark's `#E1F09A` / `#535940`
  the same; the `#book` chip `#895E3A` on `#EEE7E2` (Ink light) and `#B67F4E` on `#27211E` (Console
  dark) — the stored `#98693F` rendered by each ground. The mention `#465163` = the accent. The
  seven callout dots in the action sheet are seven distinct hues on both grounds; the pink one
  applied gives a pink tint and a pink bar with the register's text.
- **Consistency** — the Road Map's related edge, the block-reference bar, the database node's
  fill, the canvas node's outline and the find mark are all one hue per register (amber on Ink,
  yellow-green on Console): the third slot reads as one family across four surfaces.
- **Hierarchy** — the Week grid: events in the event tint, all-day tasks in the selection tint,
  today's header in the accent; the Month's dots dim. Nothing but today and the selected row wears
  the accent.
- **Affordance** — the canvas: the edge being drawn follows the pointer in the accent; the drawn
  edge and its arrowhead settle in dim.
- **Copy** — unchanged; the picker's "Color" label stands.

## What's working

- The fan reads as a set: on Ink, green events, purple habits (by code — no timed habit in the
  seed), amber third; on Console, green / magenta / yellow-green. Data never looks like chrome.
- A label is coloured for the first time and stays the same label on both grounds — the hue kept,
  the lightness the ground's.
- `tools/audit.py`'s new rule passes on the tree: no literal colour left in shared UI.

## Cannot verify

- **The phone** (paused): the chips, the callout, the marks on a touch screen.
- **A timed habit on the calendar** — none in the seed; `habitSoft` is exercised by the test, not
  the eye.
- **Swiss's yellow third** in the find mark by eye (its tint is solved to carry the text — tested).
- **Colour-vision deficiency** for the re-solved label hues, as the mock critique said.

## Disposition

#1 recorded (small things). #2, #3 stand. Nothing High; merge.
