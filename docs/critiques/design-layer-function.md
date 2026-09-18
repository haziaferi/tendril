# Critique — the design layer, on the build (D4–D8)

*2026-09-18 · `design-critique-plus`, the after-pass on the `design-layer` build — the same numbers
as `design-layer-measured.md` re-run through the engine, and the desktop walk at the user's
967 × 1100 window on Chalk light (the register where the light-mode findings live; Ink dark for the
unchanged half), native grabs through user32, pixel-read. Read-only; one finding was fixed on the
branch before this pass closed.*

## Measured

| finding | before (`main`) | after (this build) | on the grab |
|---|---|---|---|
| **D4** `surface2` on the ten light grounds | 1.07 : 1, ΔE 1.60 – 1.66 | 1.11 : 1, **ΔE 2.42 – 2.55** (dark unchanged, 3.17 – 3.46) | a hovered tree row on Chalk light: fill (239, 239, 238) on (251, 251, 250) — **ΔE 2.46**, 1.11 : 1 |
| **D5** the mention edge | 2.50 : 1 light at α 0.6 | `onSurfaceVariant` at full alpha — **5.63 – 5.80** light, 6.75 – 7.20 dark; 1 dp stroke | the darkest pixel on the *Call the library → Escape test* arrow: (100, 101, 103) on Chalk — **5.63 : 1** |
| **D5** the related edge | 2.31 – 3.36 light | `third` at full alpha — **4.61 – 10.15** light, 5.87 – 15.29 dark | the *Books v12 — Escape test* line: (43, 63, 100) — **10.15 : 1** |
| **D6** `third` | MARK-solved, ≡ `thirdStrong` in 28/30 | one token at DATA: **4.61 – 15.29** in every palette; `onThird` on it **4.77 – 15.29** (Console light 4.83, was 3.15 on `third`) | — |
| **D6** `accentStrong` | 0 reads | deleted; `secondary = accent` (0 reads) | — |
| **D7** a dialog's corner | Material's 28 dp | `TendrilShapes` 4 · 6 · 10 · 12 · 12 → the delete dialog's corner arc **12 px** (inset 12 at the top row, 0 by row 12 — 12 dp at this window's ≈ 1.01 px/dp) | the *Delete this card?* dialog on Chalk light, bbox 283 × 190 |
| **D7** the 5 dp mark | 1 site | 4 dp; rule 17 PASS over `ui/` | — |
| **D8** Tab on a register swatch | nothing | a 2 dp `primary` ring travels Chalk → Kodachrome → Clay; the click that started it drew none | three grabs |
| **D8** Tab on a tree row | nothing | see #1 below — the cursor ring, one ring | three grabs |

## Findings

1. **[Med · observed, fixed before the pass]** The first build drew **two rings on the tree**: the
   14e keyboard cursor (set on the clicked row and shown once the input mode flips to the keyboard)
   and the new Tab focus ring on the row Tab reached — two rows read as selected. Fixed: a *list's*
   row does not take the focus ring; Tab into a row **moves the list's cursor there**
   (`Modifier.cursorOnFocus`), so the cursor ring is the one ring and ↑↓ continue from where Tab
   landed. Verified: Tab → Books v12 (the cursor), Tab → its `···` (Material's own focus circle;
   the row keeps the cursor), Tab → *Call the library* (the cursor moved, one ring). The rail pill
   and the register swatch keep `keyboardFocusRing` — they are not lists.
2. **[Low · observed]** Tab stops on a tree row's hover-revealed `···` while it is at alpha 0 — a
   focus circle appears on a control that is otherwise invisible. Pre-existing (14d made the button
   hover-revealed; Tab never visited it before this pass because nothing showed). Recorded for
   item 22's list: the hidden `···` should be skipped by Tab (`focusProperties { canFocus = hovered }`)
   or shown while focused.
3. **[Low · judged]** The related edge on Chalk light is now 10 : 1 — a navy line on the near-white
   ground, heavier than the mention edge's grey at 5.6, where before both sat under 3. Two weights for
   two meanings (the legend says so); the 1 dp stroke keeps it a line, not a bar. Kept.

## What's working

- One hover share for both modes and one third token: fewer rules, and the test file now holds the
  ΔE floor the report measured by hand.
- Material's 28 dp is gone from every dialog without touching a dialog — the shapes were the one
  place to set it.

## Cannot verify

- The rail pill's Tab ring — the same modifier as the swatch's, not walked (Tab order reaches the
  rail after every control on the tab).
- The Android widget's contrast audit list without its `ACCENT_STRONG` row — compiles; the phone
  walk is pending.
- Notion's hover share (a synthetic pointer raises no hover in Electron) — [Assumed] ≈ 8 %.

## Disposition

#1 fixed on the branch; #2 recorded (item 22's list); #3 kept. Ship.
