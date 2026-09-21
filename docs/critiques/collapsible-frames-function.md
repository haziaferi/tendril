# S12 — collapsible frames, the build walked

*2026-09-21. Built on the mock's four decisions (`collapsible-frames-mock.md`). The desktop at the
dev window, native grabs; the phone by dump and screencap.*

## What was built

- `Tree.kt`: `frameContents(frame)` — a following frame its anchor and the anchor's subtree, a
  hand-sized frame every node whose box lies inside its rectangle (a nested hand-sized frame by
  its own rectangle; a following frame goes with its anchor); `hidden` hides a collapsed frame's
  contents and any frame following a hidden anchor — never the collapsed frame itself;
  `hiddenCount` counts a frame's cards; `box(frame)` when folded is the strip
  (`cardWidth(label) + 120` × 48 dp) at the frame's top-left, or where the anchor stood; `tidy`
  leaves hidden nodes alone whichever way they are hidden (`CollapsibleFramesTest`, 4).
- `CanvasScreen`: the strip — the label at `heading`, `· n hidden` in `description`, a `›`; a
  click expands, the strip drags (a hand-sized frame carries its hidden cards along); the pill's
  chevron collapses under a pointer; *Collapse (n inside) / Expand (n hidden)* on the frame's
  menu and the phone's sheet; a right-click on the pill or the strip opens the frame's menu
  (Rename · Collapse · Delete) — frames had no right-click menu on the desktop before; the branch
  from a hidden anchor's parent ends on a collapsed following frame's strip.

## The walk

| device | tried | observed |
|---|---|---|
| desktop | Garden plan → hover *Beds* → its pill's `⌄` | the strip *Beds · 3 hidden ›* at the frame's top-left; the three cards inside by geometry hidden (*Garden waste* overhangs the frame's left edge by 9 dp and stays — the rule's); the following frame *Compost* hidden with its anchor; the relationship from a hidden card hidden |
| desktop | quit and relaunch | the strip as left — `folded` is stored |
| desktop | a click on the strip | expanded, everything back |
| desktop | right-click *Compost*'s pill → *Collapse (6 inside)* | the strip *Compost · 6 hidden ›* where the root stood, the whole subtree hidden, the *Beds* frame round it as before; a click expands |
| phone | *Map testx* → *Root*'s sheet → *Frame this subtree* → the frame's sheet → *Collapse (2 inside)* | the strip *Frame · 2 hidden ›*; a tap expands; the frame deleted afterwards (the board as before) |

## Defects fixed on the walk

1. **The strip's click did nothing** — drawn in the body pass, it sat under the board's ground
   (the layer that takes the down for the double-click and the tap-to-clear). The strip is drawn
   in the label pass, over the cards, as the pill is.
2. **The strip lost its label** — the label had a weight beside the spacer's, and the strip's
   width (72 dp of extra) left it nothing; the label is unweighted and the extra is 120.
3. **A collapsed following frame hid itself** — a frame following a hidden anchor is hidden, and
   the collapsed frame's anchor is hidden by it; the collapsed frame is excepted.

## Findings

- **[Low · judged] A hand-sized frame drawn across a tree** hides by geometry — a card that
  overhangs the edge stays and its branch to a hidden parent is not drawn. The rule is
  Obsidian's (containment by rectangle) and stays; a following frame is the tree's own frame.

Tests 909 → 913.

## The phone (2026-09-21)

*Route ideas* → the *Stops* pill → the frame's sheet → *Collapse (1 inside)*: the strip — *Stops · 1 hidden*
and the chevron (content-description *Expand*), the card gone; a tap on the strip → the frame and *Empty
card* back.
