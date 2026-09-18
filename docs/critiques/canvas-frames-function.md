# §0.10 item 15 — frames and canvas templates, the build walked

*2026-09-18 · the `canvas-frames` build on the desktop (the user's 967 × 931 window, native grabs)
and on the OnePlus (`uiautomator` dumps, the DB pulled after each gesture). The decisions:
geometry containment, the label as the handle, a long press lifts on the phone, collapse not
now; deleting keeps the cards and a template keeps its page links (stated, not asked). The mock
and its critique: `docs/mockups/canvas-frames.html`, `docs/critiques/canvas-frames-mock.md`.*

## Walked — desktop, *Garden plan*

| step | observed |
|---|---|
| `+` → *Text card · Page card · ─ · Frame* | the menu with the divider before *Frame* |
| *Frame* | a 388 × 212 frame at the visible centre (383 × 210 px at this window's 0.99 px/dp), its editor open at once — **Edit frame**, the label field one line — *Beds* typed, saved; the label in its pill above the top-left corner, the hairline on `faint`, the 3 % fill |
| drag *Compost bins* into the frame, then the label right by 150 px | the card moved with the frame (both +150): geometry, no parent |
| click the label | the 2 dp ring, the handle at the bottom-right corner |
| *Fit* | the frame's own box counted in the fit (`fitToBoxes`) — the whole frame and its cards in view |
| drag the handle | the frame grew by the drag; the floor 212 × 122 holds (the domain test) |
| Backspace with the frame selected | *Delete this frame? Its cards stay.* — cancelled |
| a second click on the selected label | the editor (a first click selects, a second opens — the cards' rule) |
| `···` → *Save as template* | the New sheet lists *Garden plan* under *From template* with the canvas glyph; *Beds board* made from it: the frame, the three cards and the arrow, rejoined |

## Walked — phone, *Route ideas*

| step | observed |
|---|---|
| FAB → *Frame* | *Edit frame* sheet, *Stops* typed; the frame with the 20 dp handle and the label's delete disc always shown (the phone's badge rule) |
| a swipe on the card | **the board pans; the card stays** (the DB unchanged) |
| a long press on the card, then a drag | the card moved (y 67 → 197 dp) — into the frame |
| a long press on the label, then a drag right | the frame x −22 → 26 and the card 67 → 114 with it |

## Two defects found on the walk, both older than this PR

1. **The board did not pan.** `CanvasBoard`'s `pointerInput(Unit)` captured `pan` and `scale` at first composition and never restarted, so every move set the board to that pan plus one delta: a 400 px swipe moved the board 26–40 px, on both platforms, since the board was built (the desktop walks had dragged cards, never the board). Fixed: `rememberUpdatedState` for both.
2. **The edge tap consumed drags.** The tap-on-an-arrow detector was a `detectDragGestures` "so it composes with the pan" — it consumed every drag past touch slop and cancelled the pan where a drag began over the 4000 dp edge layer (everywhere). Fixed: a non-consuming wait for the up; a drag pans, a tap picks the arrow.

And one of this PR's own: the phone's tap detector on a card used `detectTapGestures`, which consumes the down; replaced by the same non-consuming wait. The cards' stale-`node` capture in their drag loop (the same pattern as #1) is read through `rememberUpdatedState` now too.

## Measured after
Desktop: the frame 383 × 210 px at rest (388 × 212 dp), the label pill 22 dp, the handle 14 dp, the ring 2 dp; the fill 3 % of `text` on the ground (Obsidian's 1.09 : 1), the hairline `faint`. Phone: the pill 26 dp with the 20 dp delete disc, the handle 20 dp; the frame 388 dp on a 360 dp screen (its left edge off-screen when centred — *Fit* is the bar's, the FAB's menu has no Fit on the phone: recorded).

## Recorded, not changed
- Collapsible frames (Advanced Canvas's) — decided *not now*; after item 6, when the export's shape is settled. §0.10 item 15 keeps the line.
- The phone has no *Fit*: a frame made at the centre of a 360 dp screen is wider than the screen. A Fit in the phone's `···` is one line — folded into item 24.
- The New sheet's template row shows the kind's glyph only, not the mock's *canvas · 3 frames · 7 cards* meta (a query per template for a label; not worth it).
- Nested frames: a frame wholly inside another moves with it (the domain test); nothing draws them differently.

## Cannot verify
- A real finger's pan (synthetic swipes reach the transform detector, but not a fling's velocity); Obsidian's resize on the phone.

## Disposition
Ship. §0.10 item 15 resolved (frames and canvas templates; collapse recorded).
