# S9 — Timeline and Fishbone, the build walked

*2026-09-21. Built on the mock's four decisions (`docs/critiques/timeline-fishbone-mock.md`).
The desktop at the dev window, native grabs; the phone by dump and screencap.*

## What was built

- `CanvasStructure.TIMELINE` and `FISHBONE` (`spine = true`, `curved = false`); `TreeSide.UP`;
  `isSpineRoot` / `spineRootOf` / `isRibTopic`; the spine geometry pure — `spineY`, `spineEnd`,
  `stemAnchors`, `ribOf` and `Rib.xAt`, `ribLength` — read by `tidy` and by the painter alike, so
  a dragged topic keeps a straight rib and a bone stays on it (`SpineStructuresTest`, 4).
- `tidy`: a timeline's topics along the spine in order, each on its own side, a slot as wide as
  its org chart's band; the same side never overlaps, the two sides interleave, anchors at least
  48 dp apart; a fishbone's ribs at 60° leaning forward, as long as their bones need, a foot
  placed after the same side is clear (its strip, its bones brought back to the spine along the
  slope); Down's row mirrored above the spine.
- A side is the node's own (S8's rule turned on its side): a topic's centre above or below the
  spine; a new child takes the other side; a drop among siblings tidies at once, as under Down.
- The painter (`CanvasScreen`): the spine per spine root in `dim` at 2 dp; a timeline topic's
  8 dp dot on the spine and 24 dp stem; a fishbone's rib; a bone from the rib to the word at
  its baseline, the underline carrying on. `outerSide` gains UP, so a relationship from a node
  above its parent leaves from its top.
- The two join the board's `Structure ▾` and the node's *Structure ▸* by the enum, on both
  platforms; the outline's mind-map block is untouched (Map is its only structure).

## The walk

| device | tried | observed |
|---|---|---|
| desktop | Garden plan (Down) → `Down ▾` → *Timeline* → *Tidy* → *Fit* | a spine from *Compost bins'* right; both topics below (both sat below the root's centre under Down — the side is the node's own), dots and stems, each org chart under its topic; the following frame *Compost* follows |
| desktop | *Garden waste* dragged above the spine | the drop tidied: *Garden waste* above with its child over it, *Leaves* below — the two sides interleaved, the anchors 48 dp apart |
| desktop | → *Fishbone* → *Tidy* → *Fit* | two ribs at 60° (59.9 measured), *Garden waste* above, *Leaves* below with *Coffee grounds* as a bone — text on the line — the empty card as a bone reads *…* (the leaf grammar's) |
| desktop | *Leaves* → right-click → *Add child* → *Shredded paper* | the new bone on the rib at its next place; *Tidy* → the rib grows for two bones, evenly along it |
| desktop | back to *Down* → *Tidy* | as before, the new card a strip in the row |
| phone | *Map testx* → `···` → *Structure* → *Timeline* → *Tidy* → *Fit* | *Root* → *Child one* on a stem above the spine; the menu lists the two; put back to *Free* |

## Measured (the desktop at Compact, ×0.85)

- The rib **59.9°** from the spine (Xmind 60.2); the spine `dim` at 2 dp from the root's right edge.
- Two bones on a 124 dp rib: **30 px** apart vertically = 41 dp along the rib (the rib's length
  spread evenly: `ribLength(2)` = 124, bones at its thirds — never under 28 dp).
- A timeline's anchors 48 dp apart where the sides interleave; the stem 24 dp.

## Findings

1. **[Low · measured] A wide word on a leaf is cut** — *Shredded paper* ellipsised at
   `CANVAS_LEAF_CHAR` 7.5 dp per character while *Coffee grounds* (the same 14 characters) fits.
   The strips took 8.5 in the card-width pass; the leaf did not. Older than S9; recorded as S14.
2. **[Low · judged] A rib stays its length until Tidy** — a new bone lands at the rib's next
   place on the rib as it is, so two bones on a 96 dp rib sit 16 dp apart until *Tidy* (a new
   child under Down does the same in its row). Recorded, not changed: Tidy is on demand by design.

Tests 905 → 909.
