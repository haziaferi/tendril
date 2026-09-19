# Critique — `docs/mockups/block-selection.html` (§0.10 item 19, pre-build)

*2026-09-19 · `craftkit ui` + `render` on the mock; the grounds [Assumed] — Notion, Logseq and
Craft have a block selection mode beside the text selection (Esc, Shift+↑↓, Shift+click, a
marquee, Ctrl+A twice; Delete, Ctrl+C/X/V, Ctrl+D, a drag; Ctrl+Z over block operations),
Obsidian gets all of it from its one text field. Notion was not running for a live measure; a
build-or-skip decision did not need one, and the build measures the app itself.*

## Top priorities

1. **[High · measured] The tint alone does not carry the selection — the ring is required.** The
   selected row's tint against the ground measures **1.55:1** on Ink dark and **1.21:1** on
   Chalk light, both under the 3:1 non-text floor; the 2 dp accent ring measures **4.93 / 6.74**
   on the tint and 7.65 / 8.13 on the ground. *Fix, binding on the build:* a selected block always
   draws the ring; the tint is the accent's soft (`accentSoft`, the token 14g·2 reserved for
   selection — the mock's note says `surface2`, which is the hover's and would make a selected row
   read as hovered), so a text selection and a block selection are one hue family and the ring
   tells them apart.
2. **[Med · judged] Frame f changes what a long press means on the phone.** Today it opens the
   block action sheet; the mock makes it select the block and moves the sheet behind the bar's
   `···`. One more tap for a single-block *Turn into*, one fewer for everything a run wants.
   Notion mobile does exactly this [Assumed]. *Fix:* keep it, and make the sheet reachable from
   the selection bar's `···` **and** from a second long press on the selected block (a person
   who long-presses expecting the old sheet gets it on the next press, not a dead end).
3. **[Med · judged] The marquee over a `LazyColumn` selects what is composed.** Notion's marquee
   auto-scrolls at the window's edge and selects off-screen blocks. A first build selects the
   rows laid out; dragging past the edge does not scroll. *Fix:* say so in the spec as the rule,
   not a defect; Shift+↓ reaches what the marquee cannot.
4. **[Low · judged] Backspace on a selection deletes a run with one key.** Notion's behaviour;
   the undo entry and the snackbar make it recoverable in one tap. Kept.

## Dimension-by-dimension

**Hierarchy** — the selection is the only accent on the page besides the caret, and the caret is
gone while it is up (frame a) — one thing lit at a time. The ghost (frame e) names the first line
and the count rather than drawing the blocks at half opacity: a run of twenty reads as *20
blocks*, not a smear. **Colour** — every text pairing measured: `text` on the tint 7.64 / 9.72,
`dim` on the ground 5.36 / 5.01, the snackbar's inverted pair 11.9 / 11.7. **Type** — the mock
sits on the scale (11 · 12.5 · 14 · 16 · 18, major second, fit 0.008); the bar's *n selected* is
`heading` weight at the phone's bar size. **Layout** — the phone's selection bar replaces the page
bar at the same 52 dp (the tree's rule, one bar); the sheet's rows at 48 (Touch). **Copy** — *3
blocks deleted · Undo* through `plural()`; *2 selected* / *2 blocks* (the bar counts, the sheet
names). **Consistency** — the ring is the canvas card's (`border(2.dp, primary, 8.dp)`, L9), the
snackbar the app's, the ghost the drag PR's. **Interactivity** — Esc's three meanings in order
(select the block · clear the selection · the page's back) are stated on frame a's foot; the phone's
back does the middle one through a `BackHandler`.

## What's working

- One selection model on both platforms (a set of block ids, contiguous in reading order) with
  two ways in — the keyboard / marquee under a pointer, the long press under Touch.
- The undo stack holds *block operations*, not keystrokes: the field keeps its own undo, page
  history keeps the ten-minute net, and the stack sits between them with entries that are
  inverses replayed through the same DAO writes — nothing new to sync.
- The sheet the phone already has becomes the selection's verbs without a second surface.

## Cannot verify

- Notion's, Logseq's and Craft's exact chords and the marquee's auto-scroll — [Assumed]; the
  measured half of the report is the mock's own pixels.
- Whether Ctrl+A in a `BasicTextField` on the desktop reports the whole text selected before the
  second press reaches the block — the build reads `fieldValue.selection` on the preview key.
