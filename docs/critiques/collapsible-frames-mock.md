# Critique — `docs/mockups/collapsible-frames.html` (S12, pre-build)

*2026-09-21. Item 15 built frames and recorded collapse as *not now*. Grounds: Obsidian's stock
group has no collapse; **Advanced Canvas** (830 k installs — the frames pass's registry ground)
collapses a group to its header with a chevron and re-routes edges to it; draw.io collapses a
container to its title bar with a ± button; Xmind's boundary does not collapse (a topic folds).
The state exists already — `canvas_nodes.folded` (v23) is on every node, a frame included — so
no schema. Decided in one batch after the mock: a collapsed frame is **a strip at its top-left**;
a relationship to a hidden card is **hidden with it** (the fold's rule, not Advanced Canvas's
re-route); a following frame's collapse is **its own state — the anchor folds into the strip
too**; the toggle is **the pill's chevron and the menu**.*

## Top priorities

1. **[Med · judged] The mock's first draft collapsed a frame drawn across a tree** — *Beds* held
   the root by geometry, so its collapse orphaned the branches below it. Redrawn round free
   cards. The rule stands as it is (a hand-sized frame hides what lies inside its rectangle,
   whatever its parents are); the walk shows what that looks like on Garden plan.
2. **[Low · judged] A collapsed strip is a card's height and shape** — 48 dp, the frame's own
   hairline and 12 dp radius, the label at `heading`: it reads as a frame, not a card, by its
   radius and the count. The chevron is the only new glyph.

## Dimension by dimension

- **Hierarchy** — the strip stands where the frame's top-left was, so the board's reading order holds; a following frame's strip stands where its anchor stood and the branch from the parent ends on it.
- **Colour** — the frame's own tokens (`faint` hand-sized, `dim` following); the count in `dim`.
- **Consistency** — the strip is the handle, as the pill is; a hand-sized frame carries its hidden cards along when dragged (`moveFrame` reads the rectangle, not the strip). Tidy leaves hidden nodes alone whichever way they are hidden.

## Cannot verify

- The chevron's hit at 16 dp under a pointer beside the × — the walk decides.

## What the mock says about the code

`CanvasTree.frameContents(frame)`, `hidden` extended to collapsed frames (a frame following a
hidden anchor goes with it; the collapsed frame itself stays), `hiddenCount` for a frame,
`box(frame)` when folded = the strip (`cardWidth(label) + FRAME_COLLAPSED_EXTRA` × 48); the
painter's strip and the branch to a collapsed following frame; *Collapse (n inside) / Expand (n
hidden)* on the frame's menu and the phone's sheet; the pill's chevron under a pointer.
