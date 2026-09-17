# Critique — the canvas's cards under a pointer, on the mock

*2026-09-17 · `design-critique-plus`, the pre-build pass over `docs/mockups/canvas-cards.html`
(L9's canvas half and L10 of the desktop audit — §0.10 item 22). `craftkit ui` on the file,
`craftkit render` at 1000 × 4400, the contrast pairs computed from the registers' own hexes;
the ground measured natively this evening at 125 % — Obsidian's Canvas (its Italian name
*Lavagna*; a throwaway canvas made in the vault, nothing existing touched). Read-only; the two
measured findings were applied to the mock before it was committed.*

## Grounding — measured today (device px at 125 %; CSS ≈ px ÷ 1.25)

| app | what | measured | what it settles |
|---|---|---|---|
| **Obsidian Canvas** | a card at rest | a bare bordered box, **252 × 63 px**; no control on it | badges are not worn at rest |
| | a selected card | a click selects: an accent ring; a **36 px** toolbar floats above it — delete · colour · zoom-to · edit (145 px, four 36 px buttons) | the selection reveals the verbs |
| | creating a card | **a double-click on the ground** makes a card where you clicked and opens its editor; a bottom-centre toolbar (card · note · media, 48 px) to drag from; the empty state says so (*Trascina dal basso o fai doppio clic · Spazio + trascinamento · Ctrl + scorrimento*) | B's idiom; C's toolbar; the empty-state hint |
| | the view | a right-side column: settings · zoom + · reset · **zoom to fit** · zoom − · undo · redo · help | *Fit* |
| | hover | no handle showed to a synthetic pointer (`SetCursorPos` moves the cursor without a move event in Electron) — the edge handles on hover are [Assumed] | |
| FigJam · Miro · Freeform | [Assumed]: a selection toolbar over the card, a double-click to create, zoom-to-fit | the same three patterns |
| **Tendril today** (`ui/canvas/CanvasScreen.kt`) | the phone's FAB at the bottom right under a pointer; every card wears its red delete disc and blue link handle at rest (a reserved 20 dp row); *Empty card — tap to edit*; a new card at screen (200, 200) whatever the pan; no Fit; no double-click | L9, L10 |

**Feature scoring** for *New card*'s home — the user's brief: quick-add ease and misclick-proofness, and whether more variables matter. Three do: consistency with the desktop's chrome (L5 put every verb in the bar), fit in a small pane (a canvas in the 280 dp shelf or a pop-out), and the lock (View-Only removes, never greys). Keyboard is equal (no option offers a chord) and the phone is untouched (the FAB), so neither scores. Weights .30 · .30 · .20 · .15 · .05.

| option | ease | misclick | consistency | small pane | lock | score |
|---|---|---|---|---|---|---|
| **A** — a `+` in the bar with the Text / Page menu | 3 (two clicks, the pointer travels to the bar, the card at the visible centre) | 5 (a menu never misfires) | 5 (L5's bar) | 5 | 5 | **4.30** |
| **B** — A, and a double-click on the ground makes a text card where you clicked | 5 (one gesture, the card under the pointer) | 4 (a stray double-click leaves an empty card — the rule below makes it leave nothing) | 4 (the bar plus the canvas's own idiom) | 5 | 5 (a no-op under the lock) | **4.55** |
| **C** — Obsidian's floating toolbar at the board's foot | 4 (one click, the pointer travels to the foot) | 3 (sits where a pan ends; over a card in a small pane) | 2 (a second chrome row over content, nowhere else on the desktop) | 2 | 4 | **3.05** |

**Taken: B**, with the rule that lifts its misclick score: *a text card whose editor closes with nothing typed is discarded* — a stray double-click leaves no card behind (Obsidian keeps the empty card). A is one deleted branch if the double-click is unwanted.

## Findings

1. **[Med · measured, applied to the mock]** The card's caption *Empty card — open* in `dim` on the card's `soft` measures **3.45 : 1** on Ink (4.15 Chalk) — the same tint case as L5's #1, L6's #1 and the Month chip: the register solves `dim` against the ground and `surface2`. The mock's card text is now `text` (7.64 : 1). **Binding on the build**: the caption in `onSurface`; on a wide window it appears only for a page card without a title or a phone-made empty card (B's discard rule).
2. **[Low · measured, applied to the mock]** Frame B carried a permanent hint line at the board's foot, drawn over a card. The rule belongs to the empty state (Obsidian's), not to a populated board; the line is gone from B and stays in B′.
3. **[Low · measured]** The delete disc: `error` on `errorSoft` measures 3.58 : 1 on Ink (3.79 Chalk) — a 20 dp glyph, above the 3 : 1 graphics floor, under the text floor; unchanged (14g·2's solve). The link handle's `onAccent` on `accent` 7.65.
4. **[Low · judged]** The badges' reserved row stays even when the badges are hidden, so a card's text never moves between rest and hover — 14d's *never laid out away* rule, at the cost of 20 dp of card height at rest. Kept; the alternative (overlaying the badges on hover) reintroduces the overlap the reserved row was made to end.
5. **[Low · judged]** *Fit* pans and zooms so every card is inside the pane with 24 dp of margin, at most 1×: a single card is centred at 1×, not blown up. Obsidian's zoom-to-fit does the same.

## What's working

- The bar takes the canvas's verbs as it took the Calendar's, Tasks' and the Road Map's — one chrome pattern across the five tabs and every page kind.
- The double-click gives the canvas the one gesture every ground app shares, with a discard rule none of them has.

## Cannot verify

- Obsidian's hover handles (synthetic pointer); the after-pass reads Tendril's own hover with real input.
- The Fit arithmetic at the shelf's 280 dp and in a pop-out — measured on the build.
- The phone: the FAB, the badges always shown, *tap to edit* — unchanged, not run.

## Disposition

#1 and #2 applied to the mock, #1 binding on the build; #3–#5 recorded. Build.
