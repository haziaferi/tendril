# Critique — the small things, measured again on the 14h·2 build

*2026-09-16 · `design-critique-plus`, the post-build pass for 14h·2, held to the numbers in
`small-things-measured.md`. Desktop at ≈ 1200 × 800, Ink dark, Compact (× 0.9), full-resolution
grabs measured with PIL at the same sites: the tree and its chip row, *Books v12*'s Table, the
mind map's caption, an `@mention` on `journal/2026-09-13`, a find for a word inside a folded
map (a child seeded under the mapped block), the Road Map after Ctrl+N, *Show beside ▸*, a
ground click. **Phone: not measured** (testing paused).*

## Measured — before → after

1. **Type scale.** Roles: five sizes → **six** (11 · 12.5 · 14 · 16 · 18 · 22, headlineSmall 24);
   `titleMedium` 16 → 18, no longer `bodyLarge`'s size. Literals: 27 on eleven values → all 27
   on the six (`tools/audit.py` rule 12 passes; `TypeScaleTest` holds the ratio ≥ 1.09 and the
   list). labelSmall stays 11 (9.9 px under Compact) — the smallest step is the scale's floor,
   not a defect.
2. **The Table's rows.** 58–60 px → **40.5 px** (≈ 45 dp; the tree row 27 px → unchanged): the
   ratio 2.15 → 1.5. The `Checkbox` now sits in a 28 dp target; what remains above the
   profile's 36 dp is the date cell's `TextButton` (Material's 40 dp minimum height), not the
   padding — a cell-level change for another day, recorded below.
3. **Captions.** *Mind map · open* under the pointer profile; contrast unchanged at 5.36:1.
4. **The chip row.** 14 / 20 px → **11 / 12 px** above and below the chip (12 dp each; the list's
   own 6 dp top padding subtracted); the chip's left edge 8 → **13 px**, on the row's content
   start (18 dp ≈ 16 px, less the chip's border).
5. **"2 rows"**, and no `—` in the footer.
6. **The phone card** — `edited …` from `relativeTime`; not measured on a device.

## Walked

- **`@mention`** — *@Escape test* on the accent's tint, Medium, the accent text on it: a chip in
  the line, not a highlight; the find mark beside it is the third hue's tint. Distinct.
- **Find inside a folded map** — `treasure` (in the seeded child) read *1 inside a mind map* with
  ↓ enabled; ↵ unfolded the map (the child drawn as a row), the bar *1 of 1*, the match current
  and marked. The map card returned on the next open of the page (session state only).
- **The Road Map** — Ctrl+N from the map's tab, back with Ctrl+4: the new *Untitled* node stood
  in the map without ↻ (`observeRootPages` + relations, debounced 300 ms).
- ***Show beside ▸*** — the two items open level with the item at the menu's right edge, not
  under the parent (`SubmenuItem`); *Urgency ▸* takes the same component.
- **A ground click** — a letter typed after clicking the page's ground went nowhere; the title
  kept its text.
- **The shelf's row** — *Untitled* (in the shelf) ringed in the tree with the split glyph at
  its end; *Books v12* (the main page) filled.

## Judged

- **[Low]** The Table's 45 dp at Compact is 9 dp over the profile's row height because a date
  cell is a `TextButton`; the remaining cells sit at 36. A `CompactTextButton` (a 28 dp
  `Text` with the button's ripple) would bring the row to the profile — one more small thing,
  not this PR's.
- **[Low]** `titleMedium` at 18 makes sheet titles and section labels a visible step above body
  everywhere at once; nothing overflowed in the walk, but the phone's sheets are unmeasured.

## Cannot verify

- **The phone**: the card's *edited* line, the chip row's Touch padding (4 dp, the 48 dp target
  kept), *· tap to open*, the sheets under the larger `titleMedium`.
- **The find scroll** (#11): needs a page longer than the window; the seed's pages fit.

## Disposition

Twelve of the thirteen land as planned; #2 lands at 45 dp with the residual named. Nothing
High; merge. **§0.10 item 14 closes** — the desktop pass is 14a…14h.
