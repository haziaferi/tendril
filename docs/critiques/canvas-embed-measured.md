# §0.10 item 7 — one canvas at two sizes, measured

*2026-09-18 · the ground natively at 125 % on the desktop (Obsidian 1.13.7, a new note
`Canvas embed ground.md` in the vault embedding the throwaway `Senza nome.canvas`; nothing
existing touched), then the build on the desktop (the user's 967 × 931 window) and the OnePlus.
No mock — the surface exists (the canvas block in a page); what changed is what it draws.*

## Ground — Obsidian's canvas embed (`![[x.canvas]]`)

| | measured |
|---|---|
| the embed | a title row (the canvas glyph + the name, bold — a link to the canvas) above the board, drawn by **the canvas renderer itself** at a zoom that fits the board's bounding box to the column |
| its height | **164 px** for a 250 × 60 card in an **828 px** column: the height follows the board's shape at the column's width (a fixed 400 px is Obsidian's ceiling for embeds) |
| the card | its own rounded frame, scaled with the zoom (radius included); its text **not drawn** at rest — Obsidian hides a card's content past a zoom threshold and the embed sits past it here |
| interaction | the embed pans and zooms inside the note (Obsidian's); no hover affordance reached with a synthetic pointer |

## What Tendril drew, and draws now

| | before | after |
|---|---|---|
| the block's drawing | `CanvasThumbnail`: a second renderer — boxes and straight centre-to-centre lines, no text, no page glyph, no arrowhead, no label — in a **200 dp** card | **`CanvasLayer`, the board's own layer** (the edges `Canvas` with arrowheads and the `CanvasNodeCard`s with their text or page title) at `fitToCards`'s scale, inert (`interactive = null`: no gesture, no badge, no hover) — one canvas at two sizes, the mind map's rule (§0.6.2) |
| the block's height | 200 dp fixed | `canvasEmbedHeightDp`: the board's shape at the column's width, 160…320 dp (Obsidian's rule, clamped) — tested: one card in a 328 dp column → 188; two side by side → the 160 floor; a tall column → the 320 ceiling |
| a card's text | never | drawn while the fit ≥ **0.5** (`CANVAS_CONTENT_MIN_SCALE`); below it the boxes alone — Obsidian's threshold rule |
| the board | unchanged in effect: `CanvasBoard` composes the same `CanvasLayer` with a `CanvasInteraction` (the callbacks it had as parameters) | |

Measured after — **desktop**, *Escape test* with *Garden plan* (three cards, one arrow) as a block:
the frame **326 px** tall (the 320 dp ceiling + the 6 dp paddings at this window's 0.99 px/dp),
the three cards with *Empty card — open* / *Compost bins* at a ≈ 0.55 fit, the arrow with its
head, *Garden plan · open* at the foot; a click arms the board and the same three cards stand
where the block showed them. **Phone**, *Trip* with *Route ideas* (one card): the card at 1×
with *Empty card — tap to open*, the frame ≈ 180 dp (one card at the phone's 312 dp column),
*Route ideas · tap to open*.

## Walked
Desktop: *Escape test* → `+ Add block` → `/` → *Canvas* → *Or an existing one → Garden plan* →
the block; scroll; click → the board; Esc. The stray paragraph the walk left was deleted through
its block actions. Phone: *Trip* scrolled to the block (two swipes that began in the band above the bottom bar
did nothing — recorded below; one over the cards scrolled).

## Recorded, not changed (new Lows — §0.10 item 24)

*Assessed 2026-09-20 against the build — kept or struck in `tendril-spec.md` §0.10 item 24.*
- **S1** the row page's strip shows a Date cell's value as ISO (*Read on 2026-09-19*) on both platforms — the Table's cell format; F·P4's rule (*ven 25*) has not reached the database's date cells.
- **S2** the *Insert block* and *Block actions* slide-overs list their rows at ≈ 47 px on the desktop, not the profile's 29 (Material `ListItem`/`TextButton` rows, outside the type-class audit's reach).
- **S3** a text field's right-click on the desktop opens Compose's default context menu — white on a dark register, the system's Italian labels (*Taglia · Copia · Incolla · Seleziona tutto*) — with *Block actions…* appended; the menu wants the register's colours.
- **S4** on the phone a swipe that starts in the ≈ 40 dp band between the page's last item and the bottom bar does not scroll the page (the band sits outside the list — an inset's padding); a swipe over the cards scrolls as it should. Cosmetic; the band could be the list's.
- The block's card text at a fit near 0.5 is ~7 px on the desktop: readable as a shape, not as words — the threshold is a rule, not a promise.

## Cannot verify
- Obsidian's hover affordances (synthetic pointer); a phone canvas with many cards (none seeded).

## Disposition
Ship. §0.10 item 7 resolved: one composable at two sizes.
