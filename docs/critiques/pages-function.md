# Critique — the Pages tab's pointer behaviours, on the 14d build

*2026-09-15 · `design-critique-plus` pass 3 of three, before the 14d merge. Read-only; every
finding is at the observable level (screenshots of the running app, no underlying values).
**Desktop** at 1200×800, Compact: hover a row; click the `···`; right-click a row; *Move to
Trash* on a throwaway page; right-click inside a block's text and on its margin; select text;
Bold; click another block; View-Only on. **Phone** (OnePlus, dark): long-press a card; the
menu's *Show on Road Map*.*

## Top priorities

1. **[Med · resolved in 14d]** Right-click *inside* a block's text opened the desktop's own
   text menu (Taglia / Copia / Incolla) and never the block sheet — the row's handler only had
   the 16 dp margin, because the field consumes the press and it should (its long-press does on
   the phone). **Fix taken:** the block's actions ride on the field's menu as one appended item,
   *Block actions…* (`TextContextMenuExtras`, desktop `ContextMenuDataProvider`; the phone's
   platform toolbar is not extensible, its margin long-press stays the way in). Verified: the
   item opens the same slide-over.
2. **[Med · resolved in 14d]** The floating toolbar stayed over the line above after the field
   lost focus (a selection survives blur), which the inline toolbar could get away with and a
   popup cannot. **Fix taken:** the floating form follows focus (`onFocusChanged`); the inline
   form on Touch is untouched. Verified: Bold keeps focus and the toolbar; clicking another block
   drops it.
3. **[Low · recorded]** A click on the page's empty ground clears nothing — Compose focuses only
   focusables — so a selection's toolbar lingers until another field is clicked. A
   `focusManager.clearFocus()` on the page's ground would settle it; §0.10 item 14's list.
4. **[Low · recorded]** The floating toolbar covers the line above the block (the first block's
   covers the last property row). A popup has to cover something; above is the convention (the
   mock, Notion, Bear) and the flip-below rule handles the window's top edge. Left as is.
5. **[Low · by design]** The `···` click and the phone's long-press hang the menu off the row's
   bottom-left (no pointer to place it at); a right-click places it at the pointer. Two anchors,
   one menu — consistent with what each input can know.

## Dimension by dimension

- **Affordance (1h)** — the `···` fades in on hover and stays while its menu is open, so the
  control never vanishes under the pointer that is using it; absent on the phone, where the
  long-press is the affordance and the menu's arrival teaches it. The resize handle now shows
  the hand cursor. No focus ring on rows (14e).
- **State** — *Move to Trash* is greyed, not removed, under View-Only (the lock is visible);
  `+` on the tree header is hidden under it, as the phone's FAB is — one control shown-disabled,
  one hidden: defensible (the menu is a list, the header is a toolbar) and worth one line in
  §3.1.2 someday.
- **Fit for purpose (1i)** — right-click on a row = the phone's long-press = the same three
  items; right-click in text = the desktop's text menu + the block item; right-click on the
  margin = the block sheet. The rule "right-click is the pointer's long-press" holds at every
  site that had a long-press, with the text field the one place both inputs already had their
  own owner.
- **Copy** — *Block actions…* with an ellipsis (it opens a sheet); *Move to Trash* names the
  destination; *Show on Road Map* the verb Road Map uses.

## What's working

- The menu at the pointer after a right-click, in-window, on both platforms' code path.
- *Move to Trash* → gone from the tree, present in the Trash slide-over with Restore.
- The floating toolbar's Bold applies to the selection and the toolbar stays for a second verb.
- The phone's long-press → *Show on Road Map* → the Road Map focused on the page.

## Cannot verify

- A mouse on Android (no device with one): the right-click path is the same code, unexercised.
- A touch-screen laptop: the hover `···` never appears there — the row's right-click and the
  phone's long-press are the fallbacks, untested on such hardware.
- Keyboard reach of the row menu (14e).

## Disposition

#1, #2 fixed in this PR. #3, #4 → §0.10 item 14's list. #5 stands.
