# Measured — the design layer on `main` before the fix (D4–D8)

*2026-09-18 · the numbers behind `docs/critiques/desktop-design-layer.md`'s D4–D8, re-run on `main`
at `95bf8d9` through the palette engine itself (a temporary JUnit diagnostic over `paletteFor` for
every register × mode; not committed) and a census of the code. The after-pass is
`design-layer-function.md`. No mock: nothing here is a new surface.*

## D4 — the lifted ground on light

| register (light) | `surface2` vs ground | CIEDE2000 |
|---|---|---|
| Ink · Console · Swiss · Playground · Blush · Chalk · Kodachrome · Clay · Moss · Mauve | **1.07 : 1** | **1.60 – 1.66** |
| the same registers, dark (6 %) | 1.11 – 1.14 : 1 | 3.17 – 3.46 |

Under the 2.0 just-noticeable difference on every light ground: a hovered tree row, the rail pill at
rest and a menu's ground were invisible in light mode. The dark share (6 %) is fine.

## D5 — the Road Map's edges at `alpha = 0.6`

| edge | token (solved) | at 0.6 over the ground, light | dark |
|---|---|---|---|
| mention | `onSurfaceVariant` ≥ 4.6 | **2.50 : 1** | 3.14 – 3.46 |
| related | `tertiary` (the third hue) | **2.31 – 3.36 : 1** (Console 2.31, Swiss 2.31, Blush 2.32, Mauve 2.44) | 2.87 – 6.02 (Chalk 2.87, Moss 2.97) |

The report's 2.35–2.80 and 1.86 reproduced within the method's rounding (its 1.86 read the `third`
at MARK; `tertiary` was already mapped to the DATA-solved `thirdStrong` for the lines). Under the
3 : 1 graphics floor in every light register and in two dark ones.

## D6 — the idle and duplicate tokens

- `accentStrong` — **0 reads** in `shared/` and `Tendril windows/`; Material's `secondary` is mapped to
  it and `secondary` has 0 reads. The Android widget's contrast audit had a role for it
  (`ContrastRole.ACCENT_STRONG`) that only ever fed a diagnostic list.
- `third` (solved to MARK 3.0) and `thirdStrong` (solved to DATA 4.6) — **the same colour in 28 of
  30 palettes**; `third` is read as `tertiary` (edges, the block-reference bar, canvas outlines),
  `thirdStrong` by the database dot, the Day view's extras and the find mark's current match.
  `onThird` was solved against `thirdStrong` and measured **3.15 : 1** on `third` in Console light.
- `event` — read by `dotColour(DotKind.EVENT)` since the Month grid (2026-09-17); the report predates
  that PR. Not idle.

## D7 — the radius family

| radius | sites in `shared/…/ui/` |
|---|---|
| 4 dp | 7 |
| 5 dp | 1 (the rail's app mark) |
| 6 dp | 19 |
| 8 dp | 11 |
| 10 dp | 8 |
| 12 dp | 2 |
| Material's `extraLarge` **28 dp** | every `AlertDialog` (13 files), `DatePickerDialog`, `ModalBottomSheet` — through `MaterialTheme.shapes`, never set |

The report's 14 dp no longer exists (`CentredCard` is 10). The family the code draws is 4 · 6 · 8 ·
10 · 12 with 6 the workhorse — not the layout pass's "4 · 8 · 10 · 12".

## D8 — focus without a state

Eight `indication = null` click targets take keyboard focus and draw nothing when Tab lands on
them: the rail pill (`Shell.kt`), the register swatch (`ThemeSection.kt`), the tree row
(`PagesWorkspace.kt`), the task row, the habit row and the merged list's row (`TasksHabitsScreen.kt`),
plus the two scrims (`SlideOver`, `CentredCard`) — which are not Tab stops in practice. The 14e
cursor ring covers ↑↓ inside a list only.
