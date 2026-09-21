# S14 — a text's width from the font's own advances, the build walked

*2026-09-21. Item 24's S14: a leaf's width at 7.5 dp per character cut *Shredded paper* where the
card's 8.5 fit. Measured before deciding: the bundled Inter Regular at 14 sp (`inter_variable.ttf`,
opsz 14 / wght 400, unhinted) runs 6.6–9.2 dp a character over the seed's words (median 7.6) —
7.5 cut 19 of 31, 8.5 still cut *Weeds*, *Wormery*, *Compost*; DM Sans within one per cent, Source
Serif narrower. Decided in one question: **the font's own advances**, shared by the leaf and the
card. No mock — a rule, not a surface.*

## What was built

- `domain/canvas/TextWidth.kt` (pure): the 95 printable-ASCII advances at 14 sp as a table,
  `charWidth`, `textWidth` (the sum × a 5 % margin), `wrappedLines` (words greedily on spaces, a
  word wider than the line on its own and broken by characters — what `Text` does with
  `softWrap`). `leafWidth` = the text's width + 12; `cardWidth` = the widest line + 2 × the
  padding, clamped to 60…220; `cardLines` wraps by the same advances. `CANVAS_LEAF_CHAR`,
  `CANVAS_CARD_CHAR` and `cardChars` are gone.
- **A defect the estimate had hidden**: `CANVAS_CARD_PAD` was 8 while the card drew 12 (4 dp
  around the badges' box + 8 inside), so every card's text had 8 dp less than its box promised —
  the flat 8.5 covered it; the exact width showed every card ellipsised on the first run. The
  constant now says what the card draws (12).
- The mind-map block (`MindMap.kt`) and the JSON Canvas export read the same functions — one box.

## The walk (Garden plan, the dev window, native grabs at 0.832 px/dp)

| structure | tried | observed |
|---|---|---|
| Down | every strip | *Garden waste*, *Coffee grounds*, *Shredded paper*, *Empty card* whole — all four were ellipsised on the first run (the padding defect), none after |
| Right | *Tidy*, then *Fit* | the two leaves as text on the line: *Shredded paper*'s ink **108 dp** (PIL's advances say 106.9), its line **127 dp** (`leafWidth` 124.3 + the stroke) — 8 dp of slack over the two 6 dp pads; *Coffee grounds* the same |
| — | the tree's rows as a control | *Call the library* 80 px = 95.7 dp against the table's 95.4 — Compose's Inter and the table agree to 0.3 % |

`\tLeavesKitchen scraps` is a node's literal text (a tab and two words typed into one card on an
earlier walk), not a layout defect; left as data.

## Findings

- None open. The 5 % margin is what the leaf's 6 dp pads no longer had to be: a word measured to
  0.3 % needs the margin only for the other two typefaces (DM Sans +1 %) and the rounding.

Tests 916 → 917 (`FramesTest` gains the advances' case; its fixtures now derive from `cardWidth`).

## The phone (2026-09-21) — and a defect the first phone run found

| tried | observed |
|---|---|
| *Map testx* + two cards (*Shredded paper* under *Child one*, *Weeds* under *Root*), Right, Tidy, Fit | **first run: every card and leaf cut** (*Shredded pa…*) — the Touch profile's one-step-up type (T·P1, `body` 14 → 16 sp) reached the board's text while the boxes were sized for 14 |
| the same after the fix | every word whole; the cards as wide as their text; *Shredded paper* a leaf on its line |

**Fixed — `DrawingType`** (`ui/theme/DrawingType.kt`): a canvas and a mind map are *drawings*, laid out in
content units from the advances at 14 sp and zoomed by the person, so the chrome's Touch step has no place in
them; `WorkbenchEnvironment` provides the desktop's typography as `LocalDrawingTypography`, and `CanvasLayer`,
`MindMapCard` and `MindMapFullScreen` wrap their content in it — the same drawing on both platforms.
**Also fixed**: the phone's always-on badges straddled the right edge 10 dp into the 12 dp pad, kissing the
text's last letter once the width was exact — under Touch they sit 16 dp out (4 in).

**A defect of #134's own**: `tools/make_textwidth.py` was run to prove the generator *after* the float-edge
tolerance had been edited into `TextWidth.kt` by hand — the regeneration dropped it, two `FramesTest` cases
failed on `main`, and #134's *917 / 0* was true of the file before that run, not after. The tolerance lives in
the generator now; 917 / 0 on this branch.
