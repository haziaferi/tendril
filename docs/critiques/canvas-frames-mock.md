# Critique — `docs/mockups/canvas-frames.html` (§0.10 item 15, pre-build)

*2026-09-18 · `craftkit ui` + `render` on the mock; the ground measured live the same evening
(Obsidian 1.13.7's Canvas at 125 %, the throwaway canvas: a group created from the ground's
context menu, named, a card dragged into it, the group dragged by its label — the `.canvas` file
read before and after each step). Measured and judged kept apart.*

## Ground (Obsidian's group — measured)

| | measured |
|---|---|
| the node | `{"type":"group","x":-139,"y":410,"width":400,"height":400,"label":"Ground"}` — a node of its own, **no parent field**; the default 400 × 400 at the click |
| containment | a card dragged inside (its box wholly within the group's) moved with the group: both +199 units in the file after one drag of the label — geometry alone |
| the drawing | under the cards; a **1 px border at (126,126,126) on the (28,28,28) ground — 4.6 : 1**; a fill 7/255 over the ground (**1.09 : 1**); the label in a pill above the top-left corner, ≈ 18 px, muted, editable in place on creation (*Gruppo senza titolo* selected) |
| the verbs | the ground's context menu: *Crea gruppo*; the selection toolbar over a group: delete · colour · zoom · align · edit · background; deleting a group keeps its cards |
| creation | Ctrl+G is Obsidian's *graph view*, not *group* — the menu is the way; a group from a selection needs two or more cards selected |

## Top priorities

1. **[High · measured] The frame's hairline is a region boundary at 1.42 : 1.** The mock draws it on `border` (#35373B on #1B1D21); Obsidian's group border measures 4.6 : 1 on its ground. A frame is a region a reader must see the edge of. **Fix:** the frame's stroke on `faint` (the register's 3.05 floor — the design-layer rule for an edge that means something), the fill kept at 3 % of `text` (1.07 : 1 — Obsidian's 1.09).
2. **[Med · judged] The label's delete disc sits in the label pill, the cards' in a badge row.** Two homes for one verb. The pill is the right home for a frame (its body is not its own — it carries cards), so the rule is *a frame's verbs live on its label*: delete on hover / selection there, and the resize handle at the corner. Said in the spec, not changed in the mock.
3. **[Low · judged] A frame at the board's top edge loses its label above the pane.** Obsidian's does the same; *Fit* brings it back with 24 dp of margin, which covers the 26 dp label. Left.

## Dimension-by-dimension

**Hierarchy** — the cards read first (their fill on `soft`), the frame second (its 3 % fill and hairline), the label third (`dim`) — right for a region: it holds, it does not compete. The arrow inside the frame is unchanged and legible over the fill.
**Colour and contrast** — label `dim` on the ground 5.36 : 1, on the fill 4.99 ✓; the hairline 1.42 ✗ (#1); no colour per frame (Obsidian offers six) — Tendril's cards have none either; a frame with a colour would be the first coloured region in the app and needs the token map's rule first. Not now.
**Typography** — the label at `label` 12.5/500 (the FocusBar's chip style) in a 22 dp pill; the phone's 14/500 in 26. On the scale.
**Layout** — radius 12 for the frame beside the cards' 8: a region's corner is the family's largest (4 · 6 · 8 · 10 · 12). The default 388 × 212 (two cards abreast with margins) against Obsidian's 400 × 400: a canvas card here is 180 × 90, a frame is sized for cards. The minimum 212 × 122 (one card and its margins).
**Interactivity** — the label is the handle: drag moves the frame and what lies wholly inside it, tap selects (desktop) or edits (phone), the body pans — a large frame never swallows a pan, the reason the body is not a handle. The handle at the corner resizes; the ring on selection (L10's). Delete / Backspace with a frame selected → the confirm, the cards staying.
**Copy** — *Frame* in the `+` menu beside *Text card · Page card* (a divider before it: not a card); *Delete this frame? Its cards stay.*; the template row's meta *canvas · 3 frames · 7 cards*.

## What's working
- No schema: `FRAME` on the existing `width` / `height` / `text` columns; a v20 peer on the folder quarantines the unknown type as every enum is quarantined.
- One rule for containment (geometry, Obsidian's) that item 6's JSON Canvas export writes and reads without a mapping.
- The phone gets the same frame with the handle and the label's × always shown — the cards' badge rule.

## Cannot verify
- The hover states (a synthetic pointer raises none); Obsidian's resize on the phone (its Android canvas is read-mostly).

## Decisions for the user (in one batch, before the build)
1. Containment — geometry (Obsidian's; no schema) or an explicit parent column.
2. The handle — the label alone (the body pans) or the whole body (Obsidian's; a large frame blocks panning on the phone).
3. Deleting a frame — the cards stay (Obsidian's) or go with it.
4. A canvas template's page cards — keep pointing at their pages, or become text cards with the title.
