# Critique — the small things, measured on the build before 14h·2

*2026-09-16 · `design-critique-plus`, the pre-build pass for 14h·2. 14h·2 has no mock — its
items are fixes on the build — so this pass **measures the build** where the items live, to
give each one a before-number the post-build pass can hold it to. Desktop at ≈ 1200 × 800, Ink
dark, Compact profile (scale 0.9), full-resolution screen grabs measured in pixels with PIL; the
type scale read from `Type.kt` and a grep of every literal `sp`. **Measured** and **judged**
findings are labelled. Phone: not measured (testing paused).*

## Measured

1. **The type scale** (`pages-desktop.md` #6). `Type.kt` takes Material 3's default scale
   unchanged: the roles in use sit on **five sizes** — labelSmall 11 · labelMedium 12 = bodySmall
   12 · labelLarge 14 = bodyMedium 14 = titleSmall 14 · bodyLarge 16 = titleMedium 16 ·
   titleLarge 22 · headlineSmall 24 — nine roles, seven of them inside 11…16 sp. Steps: 1.09 ·
   1.17 · 1.14 · **1.375** · 1.09. `titleMedium` (22 sites: section titles, sheet titles) and
   `bodyLarge` (42 sites) are the **same 16 sp**, told apart by weight alone. On top of the roles,
   **27 literal sizes in 11 files**: 10.5 · 11 · 11.5 · 12 · 12.5 · 13 · 13.5 · 14 · 17 · 18 · 24
   — eight distinct sizes inside 10.5…14, the "seven sizes within 5 px" the earlier pass saw,
   now with the literals counted. Under Compact (× 0.9) labelSmall renders at **9.9 px**.
2. **The Table's row height** (#7). Tree row pitch **27 px** (32 dp × 0.9 = 28.8, rounded);
   Table row pitch **58–60 px** — 2.15 × the tree row, ≈ 64 dp at scale 1. The driver is not the
   padding (10 dp above and below) but the `Checkbox`'s 48 dp minimum interactive size: a row is
   48 + 20 whatever its text. The "Comfortable 44" the plan named is unreachable without lowering
   `LocalMinimumInteractiveComponentSize` inside the Table.
3. **The captions** (#8). *Mind map · tap to open* measures **5.36:1** at its anti-aliased peak
   on Ink dark (`onSurfaceVariant` = `textDim`, solved ≥ 4.6 since 14g·1) — the 2.8:1 the earlier
   pass read is gone; **only the wording stands**. Its size is labelSmall, 9.9 px under Compact
   (item 1's tail).
4. **The chip row's rhythm** (#9, phone #4). On the tree: hairline → chip **14 px** above, chip →
   first row **20 px** below (8 dp padding plus the chip's own inner height); the chip's left
   edge at **8 px** from the pane, the row's icon at **35 px** and the row's chevron slot at
   ≈ 16 px. Not equal, not aligned.
5. **"2 row(s)"** (#10, phone #5) and four `—` placeholders in the footer, as read.
6. **The shelf's page has no mark in the tree** (`shelf-function.md` #4): the tree marks the
   main page only — observed, not a number.

## Judged

- **A ratio, not a list.** With the roles at 11 · 12.5 · 14 · 16 · 18 · 22 (1.125, titleLarge
  skipping one step) every neighbour pair reads as a step and `titleMedium` leaves `bodyLarge`.
  The 27 literals fold onto the same six values (10.5 · 11 · 11.5 → 11; 12 · 12.5 · 13 · 13.5 →
  12.5 or 14 by role; 17 · 18 → 18; 24 stays as headlineSmall) — otherwise the scale is regular
  in `Type.kt` and irregular on screen.
- **The Table under a pointer** should read as a list, not as a form: rows at the profile's
  height (Compact 36 · Comfortable 44 · Touch 56), the checkbox inside a 28 dp target on a
  pointer profile and its 48 dp on Touch.
- **`@mention` on `accentSoft`** (coloured-elements-function #1): the mock's form, rejected in
  14g·2 while the find mark shared the tint; the find mark is the third hue's now, so the only
  thing an `accentSoft` span could be mistaken for is a selection — and a selection is the
  field's, drawn by the platform, not a span style.

## Cannot verify

- The phone's card list (#6) and its chip row (#4): the same code, not measured on a device.
- Find inside a mapped-away subtree (#12) and the find scroll (#11): behaviours, not
  measurements — the post-build pass walks them.

## Disposition

Item 3's colour half is already met (14g·1) and drops; item 2 changes its mechanism (the
interactive minimum, not the padding); item 1 gains the literals; a thirteenth item (the shelf's
tree mark) joins. The plan section is amended accordingly.
