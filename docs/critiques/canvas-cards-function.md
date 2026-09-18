# Critique — the canvas's cards under a pointer, on the build

*2026-09-17/18 · `design-critique-plus`, the function pass over the `canvas-cards` build (L9's canvas
half and L10 of §0.10 item 22) — the desktop walk at the user's 967 × 1100 window, Ink dark, Compact,
native grabs through user32 (computer-use's capture is stale for the Compose window), the dev database
read with sqlite3 after each write. Grounds as the mock's critique measured them (Obsidian's Canvas at
125 %). Observable-level findings; the two defects were fixed on the branch before this pass closed.*

## Measured — the card's three states, Fit, the double-click

| what | Tendril, this build (device px, 125 %, shell scale ≈ 0.81) | Obsidian Canvas (measured) | note |
|---|---|---|---|
| a card at rest | **182 × 91 px**, bare — no disc, no handle | 252 × 63, bare | the badges are not worn at rest (L10 fixed); the 180 × 90 dp proportion is the phone's, kept (`NODE_W`/`NODE_H` unchanged by the plan) |
| hover | the delete disc and the link handle fade in at the card's corners; the text does not move (the reserved row) | no handle shown to a synthetic pointer ([Assumed] on hover) | |
| selected (a click) | a **3 px** accent ring (2 dp) on the card's rounded shape; the badges stay | an accent ring and a 36 px toolbar above | Tendril's verbs live on the card, not in a toolbar; Delete / Backspace on the keyboard opens the delete dialog (§ below) |
| the ground clicked | the ring and the badges go; the text unmoved | the selection clears | |
| *Fit* | both empty cards inside the pane at 1× (the bounding box centred; a single card is never blown up) | zoom-to-fit | `fitToCards`, tested |
| a double-click on the ground | a text card **under the pointer** with the editor open and focused | the same | `contentAtPaneCentre` for `+`; `screenToContent(tap) − half a card` for the double-click |
| Esc with nothing typed | no card — `canvas_nodes` unchanged (read after each of four cycles) | Obsidian keeps the empty card | the discard rule, the scoring's pointer |
| typed *Compost bins* · Save | the card stays where the double-click landed — node 23 at content (105, 654) | | |
| Delete with a selection | *Delete this card?* — the badge's dialog; Esc closes it, the page and the selection stay | Obsidian's toolbar has a delete | added on the walk (the plan's line, missed in the first build) |
| the bar | *Fit · + · eye · ···*; no FAB on a wide window | | the phone keeps its FAB (`!wide`), unrun |
| empty state (wide, pointer) | *Nothing on this canvas yet — double-click the ground for a text card, or + for a page card* | Obsidian's states the double-click | |

## Findings

1. **[High · observed, fixed before the pass]** Every Escape on the desktop went **back twice**. The
   walk's editor closed on Esc *and* the page popped to the empty state whenever a frame fell between
   the two dispatches; on a two-deep stack one Esc could pop both levels. Instrumented with prints:
   Compose Multiplatform 1.12 already dispatches the Escape key-*down* to the enabled `BackHandler`s
   through the window's `NavigationEventDispatcher`, and the app's own `EscapeBackInput` — added
   2026-09-12 on the key-*up*, when nothing fed the dispatcher — had become a second input. Not an
   L9 defect: it dated from the sheet-as-slide-over and pop-out PRs and showed on every sheet, card and
   stack; L10's double-click/Esc cycle was the first walk to press Esc with a fresh sheet in front of
   a page whose route could pop. **Fix:** the key-up dispatch removed from `Main.kt` and
   `PopOutWindows.kt`, `EscapeBackInput` deleted; `dismissOnBackPress = false` on `SlideOver`'s and
   `CentredCard`'s `Popup`s so the popup's own back handling cannot become a third path. **Verified:**
   four double-click → Esc cycles leave the page open and no node behind; *Escape test* pushed over
   *Garden plan* pops one level per Esc (the first Esc clears the page's editor focus, the second
   pops); a pop-out (*Books v12*, opened with Shift+click) survives two Escapes at its seed and closes
   on Ctrl+W; the delete dialog closes on one Esc with the page and the selection kept.
2. **[Med · observed, fixed]** The card editor opened **without focus** — the first double-click's
   typing went nowhere and Save wrote an empty card, which the discard rule then removed. A
   `FocusRequester` on `TextNodeEditor`'s field, requested on the node id, so the double-click's
   editor takes the keyboard at once (Obsidian's).
3. **[Med · observed, fixed]** Delete / Backspace with a selected card did nothing in the first
   build (the plan's line, not carried into the code). The board takes focus when a card is
   selected (`focusable` + a `FocusRequester` fired by the selection) and Delete / Backspace open
   the badge's dialog through the same `nodeMenuFor` state, so View-Only's guard covers the key
   exactly as it covers the disc. A finger never selects, so the phone never focuses the board.
4. **[Low · measured]** The card is 182 × 91 px beside Obsidian's 252 × 63 — narrower and taller;
   the plan kept 180 × 90 dp (the phone's card). Recorded, not changed: a wider card is a
   proportion decision for both platforms, not a desktop fix.
5. **[Low · judged]** The ring reads as the selection; the badges' reveal on hover carries the
   verbs without Obsidian's floating toolbar — one fewer chrome layer over the board, at the cost
   of the badges being the only on-card verbs (edit is the second click, delete the disc or the
   key, link the handle). Consistent with 14d's hover rule; kept.

## What's working

- The bar holds the canvas's verbs as it holds every tab's — *Fit* and `+` beside the eye and `···`.
- The discard rule does what the scoring promised: a stray double-click leaves nothing (four cycles,
  the table unchanged), while a typed card lands where the pointer was.
- The Esc fix is app-wide: every sheet, card and pop-out now goes back once per press.

## Cannot verify

- The phone: the FAB, the always-on badges, *tap to edit* — unchanged in code, unrun (pending as
  every PR since #82).
- Obsidian's hover handles (a synthetic pointer moves the cursor without a move event in Electron).
- The badges' disc size on the grab (≈ 20 dp by construction; the pixel scan caught the caption).
- The canvas in the shelf at 280 dp — the same composable; *Fit* reads the shelf's pane size through
  `onSizeChanged`, not walked here.

## Disposition

#1–#3 fixed on the branch (the Esc dispatch as a High that predates L9); #4 and #5 recorded.
Ship.
