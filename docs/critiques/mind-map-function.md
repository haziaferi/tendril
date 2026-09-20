# The mind-map pass — the build, walked

*2026-09-20. The desktop at the dev window (1007 × 885, Compact, Ink dark), native grabs; the
phone (OnePlus) from `screencap`. Each behaviour tried once, observable-level; the mock's binding
fixes checked. Grounds: `mind-map-grounds.md` (Xmind, Mindomo, Freeplane measured).*

## Desktop — Garden plan

| feature | tried | observed | finding |
|---|---|---|---|
| The board opens | from the tree | *Free ▾* in the bar; the two cards, the *Beds* frame; the old arrow now a **relationship** — dashed, `dim`, from the first card's right side to the second's left with its head on the edge | — |
| A node's menu | right-click *Compost bins* | the menu at the pointer: Edit · Add child `Insert` · Add sibling `Enter` · Structure ▸ · Delete | — |
| Add child | the menu | the editor opens; *Kitchen scraps* saved → *Compost bins* redraws as the **root box** (`pageTitle`, the accent's border), the child a strip on a green branch beside it | — |
| Add sibling by key | a strip selected, Enter | a second child's editor; *Garden waste* on a second hue (rose) | — |
| Add child by key | Tab | **nothing** — AWT keeps Tab as a focus-traversal key before Compose sees it; **Insert** (Freeplane's key) added and taken, the chip in the menu reads *Insert* under a pointer | fixed on the walk |
| Insert | the strip selected | *Coffee grounds* as **text on its line** — the third level, the branch running on as its underline in the branch's colour | — |
| The structure | *Free ▾* → Map | the two children split — one right, one left — centred on the root's middle; the bar reads *Map ▾*; *Tidy* appears beside it once the board has a tree | — |
| Fold | the root's menu → Fold | the subtree gone, a **badge with 3** at the root's right; a click on it unfolds | — |
| Frame this subtree | the root's menu, the label typed *Compost* | a frame **following the subtree** — its box the subtree's bounds + 12 dp, its stroke at `dim` (the mock critique's #2); the hand-sized *Beds* keeps `faint` beside it | — |
| Down | *Map ▾* → Down | **elbows** from the root's bottom centre to each child's top centre with one shared knee; *Coffee grounds* now a **strip** (the strip at every level); the following frame grew round the new shape; *Beds* stayed where it was drawn | — |
| Drag onto a card | the loose *Empty card* dragged over *Garden waste* | the target **rings in the accent** while the card is over it; on release the card is *Garden waste*'s child, placed under it (Down), the frame grew, the relationship re-routed from the child's new outer side (its bottom) | — |
| Fit | the bar | every visible node in view at ≤ 1×, the following frame included | — |
| The embed | *Escape test*'s Garden plan block | the same tree drawn at the fit; the frame's label pill was **clipped** at the embed's top — the fit did not count the 28 dp the pill sits above the frame | fixed on the walk |
| The outline's map | *Escape test*'s mind-map block | the root box at `pageTitle`, its children strips on coloured branches (the same painters) | — |

**Measured** (the board's own zoom after a Fit, so ratios not absolutes): the root box 54 px
tall against the strip's 41 — 1.32, the grammar's 64 / 48 = 1.33; the strip 41 px, Xmind's main
topic 41 at 100 %; branches 2 px at the first level.

**Cannot verify here**: Tidy's balance on a tree deeper than three (the walk's had two levels
under the root); a relationship between nodes of two different trees (the route's outer sides
are each tree's own).

## Phone — a new canvas *Map test*

| feature | tried | observed | finding |
|---|---|---|---|
| The migration | the APK over v22 | v23 in place: `parentId`, `folded`, `structure` on `canvas_nodes`, `structure` on `page_canvases`; the phone's 8 pages and 2 nodes intact (read back with the WAL) | — |
| A card's sheet | tap the card | *Edit card* with a **Tree** section under the field: Edit · Add child `Tab` · Add sibling `Enter` · Structure ▸ … — the key chips and *Edit* mean nothing inside the editor on a phone | fixed: no chips under Touch; *Edit* only under a pointer (the sheet *is* the editor) |
| Add child | the sheet's verb | the child's editor; *Child one* saved → *Root* the root box (its link disc always on, the phone's rule), the child a strip on a green branch to its right | — |
| The board's structure | the `···` → *Structure ▸* | the four and *Tidy now* as a pushed level (the phone's submenu) | not tried past opening |

## What the walk changed

- **Insert** for a child on the desktop (Tab never arrives — AWT's focus traversal); the menu's
  chip says so.
- The embed's fit counts a frame's label pill (28 dp above the frame).
- The phone's sheet shows no key chips and no *Edit*.

## Recorded, not fixed

*Assessed 2026-09-20 against the build — kept or struck in `tendril-spec.md` §0.10 item 24.*

- Sibling order is creation order; dragging a sibling above another does not reorder (Xmind
  does). A later PR.
- The relationship between a Down child and a card above it crosses the tree on its way up —
  the route's outer sides are honoured, the crossing is the geometry's.
- Timeline and Fishbone: the list's last two, once Down has been used for a while.
