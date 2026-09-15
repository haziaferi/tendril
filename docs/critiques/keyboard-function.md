# Critique — the keyboard on the 14e build, before its merge

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14e. Read-only; every
finding at the observable level. **Desktop** at 1200×800, Compact: every chord once; F1's card;
Alt+← / Alt+→ around a pushed page; the tree — click, ↓, type `jo`, →, ↓, ↵, ←, Esc, Esc; the
Tasks list — click, ↵; Settings' *Keyboard shortcuts…* line. **Phone** (OnePlus, dark): the
Pages list pixel-diffed against the 14d build; the Tasks list driven with `adb shell input
keyevent` (ENTER, DPAD_DOWN) after a tap.*

## Top priorities

1. **[High · fixed before merge]** The plan's chords were not typable on the keyboard the app
   is written on: on an Italian layout `/` is Shift+7 and `[` `]` are AltGr+è / AltGr++, so
   Ctrl+/ (the overlay) and Ctrl+[ ] (back / forward) never fired — the first press of the walk
   found it. A chord that needs AltGr or Shift on a common European layout is no chord. **Fix
   taken:** the overlay on **F1** (Windows' own help key), back / forward on **Alt+← / Alt+→**
   (the browsers'), both layout-independent; `Chord` gained `ctrl`/`alt` flags so the table can
   say so, and the test holds every chord unique. Notion's and Slack's chords were the
   benchmark's — a US-layout assumption the benchmark did not state.
2. **[Med · fixed]** The overlay's card sized itself to its content and the labels wrapped to
   three lines in two narrow columns; it now fills to its 560 dp maximum. The chips are
   `onSurface` on `surfaceVariant` and read at a glance — the mock's 3.08:1 did not recur.
3. **[Med · fixed]** On the phone a tap on a task row drew the keyboard's ring and a ripple —
   a focus ring after a touch, which Android never shows. **Fix taken:** the ring draws only
   while `LocalInputModeManager` says Keyboard (a hardware key flips it; verified: no ring after
   the tap, a ring after `DPAD_DOWN`), and the row's tap has no indication.
4. **[Low · recorded]** Ctrl+N and Ctrl+T from a tab other than Pages open the page stacked
   over that tab with a back arrow (what the switcher's commands do), not in the Pages
   workspace beside the tree. Consistent with the switcher; §0.10 item 14's list — "a page opened
   from elsewhere lands in the workspace" is one `switchTab(PAGES)` before the push, once the
   tree pane's own focus rule (14h) is settled.
5. **[Low · by design]** Esc in a list clears the cursor first and goes back second — two
   presses to leave a page when a cursor is set. The alternative (one Esc does both) would
   close a page the person only meant to un-focus. Kept.

## Dimension by dimension

- **Affordance** — the cursor ring is a 2 dp `outline` inside the row's shape: distinct from
  the current row's tint and from the hover's, so *focused*, *current* and *hovered* read as
  three states. The typed prefix underlines in the title (`Jo` on *Journal*) — the feedback is
  where the eye already is.
- **Reach** — every action in the overlay was pressed and did what its row says; the card is
  generated from the table, and `ShortcutsTest` holds the two together. The Settings line is
  the second door.
- **Fit for purpose** — ↵ on a task opens its `···` menu (a task has no detail screen); ↵ on a
  tree row shows the page; → expands, ← collapses or climbs. The Tasks list on the phone answers
  a hardware keyboard through the same code.
- **Copy** — *Back · forward · the mouse's side buttons*, *Jump to a row by its first letters*,
  *Clear the cursor*: each row says what happens.

## What's working

- One table, one overlay, one test between them.
- Alt+← / Alt+→ around a switcher-pushed page: back to the page, forward to the pushed one.
- The tree: click → ↓ → `jo` → → → ↓ → ↵ opened the journal day; ← climbed to *Journal*;
  Esc cleared the ring; Esc closed the page.

## Cannot verify

- **The mouse's side buttons.** Implemented on Compose's `isBackPressed` / `isForwardPressed`;
  a synthetic `mouse_event` XBUTTON press from PowerShell never reached the window — nor did a
  synthetic primary click sent the same way, so the test proves nothing about the code. Needs a
  physical mouse with side buttons.
- Home / End in a list; type-ahead on the phone's hardware keyboard (the same code path as
  DPAD, which worked).
- Ctrl+\ was verified in 14c and not re-pressed here (its line moved into the table; the table's
  test covers the chord).

## Disposition

#1–#3 fixed in this PR. #4 → §0.10 item 14. #5 stands. The side buttons stay a *Cannot verify*
in the PR body.
