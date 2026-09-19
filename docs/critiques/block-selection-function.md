# §0.10 item 19 — the block selection, the build walked

*2026-09-19 · the `block-selection` build on the desktop (the dev window at 1007 × 885, native
grabs; *journal/2026-09-13*, six blocks) and the phone (OnePlus, `uiautomator` dumps; *Trip*,
six blocks under a mind map). The mock and its critique: `docs/mockups/block-selection.html`,
`docs/critiques/block-selection-mock.md` — its three binding findings all landed (the ring on
`accentSoft`, the phone's second long press, the marquee's rule).*

## Walked — desktop

| step | observed |
|---|---|
| caret in *A tint, not a pastel* → Esc | the field loses focus; the row on the accent's soft with the 2 dp ring, no caret |
| Shift+↑ ×2 | the run grows upward by one row each — *See @Escape test*, then the block reference's card (a card row wears the same ring) |
| Delete | the three rows gone; the snackbar *3 blocks deleted · Undo* at the pane's foot |
| Ctrl+Z | the three back in place — the reference card, the mention span, the paragraph, in their order |
| a press on the ground below *Add block*, dragged up-left across two rows | the dashed marquee on the soft; on release the rows it crossed are selected, the marquee gone |
| a press on a selected row's text, moved up | the ghost *See @Escape test · 3 blocks* beside the pointer, the insertion line under the mention block; release → the run above the reference, still selected; Ctrl+Z → back |
| Ctrl+C, Ctrl+V | the run pasted after the selection's last and selected; the clipboard holds the Markdown (`See @Escape test / A tint, not a pastel / <aside>…</aside>`) |
| Delete on the pasted run | gone; the snackbar again |
| a click in a field, Ctrl+A, Ctrl+A | the first selects the text, the second every block (six rings, the empty paragraph and the mention block among them) |
| right-click on a selected row's text → *Block actions…* | the slide-over titled *6 blocks*: Move up · Move down · Cut · Indent · Turn into — no map, no language (single-block sections absent for a run) |
| View-Only on → a marquee → Delete | the selection still draws (a copy is a read); Delete does nothing; the eye back off |

## Walked — phone

| step | observed |
|---|---|
| a long press on *Packing*'s margin | the ring; the bar becomes *× · 1 selected · Copy · Delete · ···* (Cut and Paste below moved into the sheet — see below) |
| *···* → *Show as list* | the sheet for one block keeps its single-block rows; the map unfolds; the bar reads *4 selected* — the children came along with their parent |
| × · a long press on *Clothes*' margin · a tap on *Documents*' margin | *2 selected* then *3 selected* — *Clothes* brought *Jackets*; rings on all three |
| Delete | *3 blocks deleted · Undo* — dark on the light ground, above the canvas block |
| the canvas block: long press · Delete · Undo at once | the block back; the DB then pulled: six rows, orders 0–5, parents intact |

## What changed on the walk

- **The row's ground is the whole row.** The click and long-press handlers sat after the row's
  inner padding, so an indented block's margin (and the 8 dp before any block) reached nothing —
  a long press on *Clothes* selected nothing while *Packing*'s worked. Moved before the padding
  (`PageDetailScreen.kt`, the Row's chain); the old long-press-for-the-sheet had the same blind
  spot.
- **The ground's callbacks read the latest state.** `pointerInput` keeps its first block, so the
  marquee's `onMarquee` had captured the page's block list from the first frame — empty — and
  selected nothing on a fresh page (`visible=[]` in the log). `blockGround` is `@Composable` and
  reads its callbacks through `rememberUpdatedState` (`BlockGround.kt`).
- **The drag carries its own ids.** The press on a selected block's text focuses the field, and
  a field gaining focus clears the selection — so the first drag lifted *0 blocks*. The gesture
  reads the run at the press and hands it to every callback; the screen puts the selection back
  when the drag activates.
- **The phone's bar had no room.** *× · n selected · Copy · Cut · Paste · Delete · ···* left 49
  px for the count (*1 sel…*); Cut and Paste below moved into the sheet (`···`), the count sits
  at `heading`. Four controls, the mock's shape.
- **The snackbar under a canvas block.** On the phone the block's layer drew over it; the host
  takes `zIndex(1f)`.
- **The count is of rows on screen.** A folded subtree comes along with its map block (deleted
  or moved with it) but is not a row the person sees; the bar, the sheet's title and the ghost
  count the visible run.

## Recorded, not changed

- On the desktop a right-click inside a selected block's *text* opens the field's own menu
  (14d's rule) with *Block actions…* as its last item; the row's margin opens the sheet at once.
- On the phone a long press *in the text* is the platform's word selection; the block's long
  press is the row's margin and prefix — 16 dp before a paragraph, more before a list item. The
  gutter is thin for a thumb; a drag handle or a wider Touch gutter is a later pass (§0.10).
- *Turn into* on a run retypes every selected block, a mention block or a reference included,
  as the single-block sheet always did.
- The marquee selects rows that are laid out and does not scroll (`block-selection-mock.md` #3).
- The undo stack is the ViewModel's — a page closed and reopened starts empty; page history is
  the net under it (the walk's own *Packing* branch came back through History › Restore).

## Cannot verify

- Notion's, Logseq's and Craft's exact chords — [Assumed] in the mock; the build's are the ones
  the F1 card's *Editor* group lists.

## Disposition

Ship. §0.10 item 19 resolved: a block selection with two ways in and one undo stack under the
block operations, on both platforms.
