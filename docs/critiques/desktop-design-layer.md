# Critique — the design layer (the `design-auditor` pre-pass)

*2026-09-17 · `creative-design-suite:design-auditor` (read-only, its own context) over the whole
design layer — `shared/…/ui/theme/` and every screen under `shared/…/ui/` — as the pre-pass of
the audit's fixes, decided with the user the same day. Measured claims are numbers from the code
or from a Python port of `ColorSolve.kt` / `Registers.kt` (checked against the spec's published
floors: text 11.59–11.99, dim ≥ 4.63, accent ≥ 7.62, error on its soft ≥ 4.61, in all 30
register × mode palettes); judged claims are labelled. The three desktop passes
(`desktop-layout-full.md`, `desktop-type-full.md`, `desktop-function-full.md`) were read first and
are not repeated. **D1–D3 were fixed in the same PR as the eight High findings; D4–D8 are §0.10
item 22's.***

**Verdict.** Colour is fully tokenised — zero literal colours in shared UI, every token solved —
but the type vocabulary sat at **37 % adoption** (121 vocabulary reads against 202 raw Material
role reads — now 0 after the sweep), and the solver's floors stopped at the two grounds: **five
text sites failed AA 4.5:1 in every palette** and **three non-text elements failed 3:1**, the
keyboard focus ring (1.21–1.42:1) foremost. Several state colours (focus, hover, selected-row
captions) were drawn in tokens whose own KDoc says they are not meant to be read.

## Top priorities

1. **[High · measured · fixed]** **The keyboard focus ring was drawn in the "meaningless" hairline
   token.** `ListKeyboard.kt` documents a 2 dp `outline` ring; `Theme.kt:56` maps `outline =
   p.border`, and `Palette.kt:32` describes `border` as "~1.3:1, meaningless by design". Measured:
   1.21:1 (Swiss light) to 1.42:1 (Ink dark) against the ground. WCAG 2.4.11/1.4.11 want 3:1.
   Five sites (`PagesWorkspace.kt`, `PagesScreen.kt`, `TasksHabitsScreen.kt` ×3). **Fixed:** one
   `Modifier.keyboardCursorRing(shown, radius)` in `ListKeyboard.kt`, in `outlineVariant` (=
   `textFaint`, 3.28–3.56 on bg, 3.06–3.11 on `surface2`); the tree's *in shelf* ring the same
   token.
2. **[High · measured · fixed]** **Five text sites failed AA in all 30 palettes.** The tab roots'
   *Choose a page…* / *Choose a task…* lines (`PagesWorkspace.kt:478`, `TaskDetailPane.kt:169`)
   in `outlineVariant`: **3.28:1** Ink light, 3.56 max anywhere — `HoverPreview.kt`'s own comment
   says "text, so onSurfaceVariant, never the faint token". A selected row's meta
   (`TasksHabitsScreen.kt:616, :787, :866`): `onSurfaceVariant` on `primaryContainer`
   (`accentSoft`) measured **3.45:1** (Ink dark) to 4.17 — `textDim` was solved on bg and
   `surface2` only. **Fixed:** the two lines take `onSurfaceVariant`; **dim and faint are now
   solved against `accentSoft` too** (`Registers.kt`, the `grounds` list; `RegisterSolveTest`
   asserts it in every palette) — every meta on a selected row clears 4.6 by construction.
3. **[High · measured · fixed]** **Two hidden styles existed outside the vocabulary.**
   `ThemeSection.kt:149` built `eyebrow.copy(letterSpacing = caption.letterSpacing)` = 11/500
   untracked, `TendrilType.kt:46` built `clockSmall` = 11/500 tabular, and `FindBar.kt:110` built
   `description.copy(fontFeatureSettings = "tnum")` — an eighth style (11/500 plain) invented
   twice and a clock at three sizes. **Fixed:** one `TextStyle.tabular()` derivation (`clock =
   body.tabular()`, `clockSmall = caption.tabular()`, the find bar's count
   `description.tabular()`); the swatch name is `caption` in both states (the ring marks the
   choice). With the sweep, 121 → every site on the seven styles; `TendrilTypeTest` pins the
   aliases.
4. **[Med · measured]** **Hover is imperceptible in light mode.** `surface2` = text mixed 4 % into
   the ground: **1.07:1, CIEDE2000 1.60–1.66** against bg in all ten light palettes — under the
   2.0 just-noticeable difference. It is the hover fill of the tree row and the rail pill's rest
   state. Dark mode is fine (ΔE 3.2–3.5). **Fix:** 6 % in light (`Registers.kt`; Notion's hover
   is ≈ 8 % of its text); dim and faint re-solve against it automatically.
5. **[Med · measured]** **Alpha undoes the solve on the Road Map.** `RoadMapScreen.kt:501` draws
   edges at `alpha = 0.6f`: the mention edge (`onSurfaceVariant`, solved to 4.6) measures
   **2.35–2.80:1**; the related edge (`tertiary`, solved to 3.0) **1.86:1** in Console and Swiss
   light. 17 `.copy(alpha =)` derivations exist in 9 files; this is the one where a required
   graphic falls under 3:1. **Fix:** edges at full token alpha, a thinner stroke; the 0.28 dim for
   de-emphasis is fine.
6. **[Med · measured]** **Two tokens, one value — `third` ≡ `thirdStrong` in 28/30 palettes**
   (only Swiss light and Console light differ). `accentStrong` ≈ `accentSoftText` (ΔE 0.64–1.85,
   24/30) and `accentStrong` is **never read** (`secondary` has 0 reads); `event` is solved and
   idle (only `eventSoft` is drawn). Latent: `onTertiary = onThird` is solved against
   `thirdStrong`, measures 3.15:1 on `third` (Console light) — nothing draws it. **Fix:** drop
   `thirdStrong` or give `third` a ceiling; delete `accentStrong` and `event` or read them; map
   `tertiary = thirdStrong` or note it.
7. **[Med · measured]** **Material's default radius family leaks through 26 `AlertDialog`s and 3
   `MaterialTheme.shapes` reads** (28 dp), while the app's own surfaces use 6 (20 sites), 8 (11),
   10 (6), 12 (2), 14 (1 — the shortcuts card). The layout pass's family "4 · 8 · 10 · 12" is not
   what the code draws: 6 is the most-used radius. **Fix:** `MaterialTheme(shapes = Shapes(4, 6,
   10, 12, 12))` in `Theme.kt`, the 14 → 12; the family documented as 4 · 6 · 8 · 10 · 12.
8. **[Med · judged, code-confirmed]** **Eight controls take keyboard focus with no visible
   state**: `indication = null` on the rail pill, the register swatch, the tree row and the three
   task/habit rows — Tab lands on them and nothing draws (the cursor ring covers only ↑↓ inside a
   list). **Fix:** `onFocusChanged` + `keyboardCursorRing` when focused from the keyboard; the
   swatch and pill `primary` at 2 dp.

## 1. Token adoption

**Colour — measured.** 0 literal colours in shared UI outside `ui/theme/` (the audit's rule).
Outside the rule's reach: the desktop app's two Composable files (14 raw role reads — now
renamed) and the Android widget's `Color.White`/`Black` puck (a bitmap over every hue; exempt).
17 alpha derivations in 9 files (`TimelineView.kt` ×4, `RoadMapScreen.kt` ×3, `WeekGridView.kt`
×2, `PlanView.kt` ×2, the label-chip borders at `hue × 0.4`, `MindMap.kt`, the two 32 % scrims)
— every alpha is a second, unsolved colour; #5 is the one that fails. Palette fields never read
outside the theme: `accentStrong`/`secondary`/`onSecondary`, `event`, `inversePrimary`,
`surfaceTint`, `inverseSurface`, `scrim` (an alias layer by design, except `event` and
`accentStrong`).

**Type — measured.** Before the sweep: vocabulary 121, raw 202 (`bodyMedium` 58, `bodySmall` 47,
`labelLarge` 34, `titleMedium` 21, `labelSmall` 19, `labelMedium` 14, `titleLarge` 6, `titleSmall`
3), ten files on raw roles only; 339 `Text(` calls with no `style` — all but two inside a
Material slot and inheriting its role by design; the two bare ones (`CanvasScreen.kt:579`,
`PageDetailScreen.kt:1044`) fell to `LocalTextStyle` = `bodyLarge` 16 sp. **After:** 0 raw
roles, every site classified (`tools/type_sites.py`), the two bare ones styled.

## 2. Naming coherence and near-duplicates

- `titleSmall` ≡ `labelLarge` ≡ `label` (12.5/500/17) by construction — folded by the sweep.
- `clock` 14/400 vs `clockSmall` 11/**500**: the name said size, the definition also changed
  weight — #3, fixed.
- `onAccent` ≡ `onThird` ≡ `onError` in 30/30 (≡ `bg` in 29/30): three role names for "the ground,
  or white"; deliberate, but `Palette.kt:40` promises a solve that never diverges — say so.
- `accent` ≈ `accentSoftText` ΔE < 2 in five light palettes (Swiss 1.18): `onPrimaryContainer`
  and `primary` are interchangeable to the eye; a future author will guess.
- Colour-vision: `CALLOUT_COLORS` `#BFDBFE`/`#FBCFE8` collapse under protanopia (ΔE 7.2),
  `#BBF7D0`/`#FED7AA` (7.1), `#FBCFE8`/`#E5E7EB` under deuteranopia (2.1). Low: the bar carries
  meaning only by choice, and the icon is mandatory.

## 3. Component state gaps (hover / keyboard focus / pressed / disabled / selected)

| component | gap |
|---|---|
| TreeRow | hover `surfaceVariant` invisible in light (#4); Tab focus none (#8); the chevron on the selected row `outlineVariant` at 2.27–2.75:1; the *in shelf* ring was in `outline` (fixed with #1) |
| TaskRow / HabitRow | the ring was applied after `.padding`, inset 8/16 dp and crossing the checkbox — two ring geometries against the tree's (one helper now; the geometry stays the row's) |
| Merged habit row `:855–866` | never took the one-line brief: `padding(8)` + two lines, title in `primary` — **Med, item 22** |
| TrayChip | the only row-like control *with* a ripple — judged inconsistent |
| Rail pill | rest state `surfaceVariant` on a `surface2` rail — the unselected pill is the ground's colour (by the mock) |
| UrgencyPicker | 26 dp discs by plain `clickable`; `LocalMinimumInteractiveComponentSize` does not reach non-Material clickables — under Touch 32 dp, not 48 |
| SlideOver | enters animated, exits without (`visibleState` never set false) — judged |
| SubmenuItem | opens on click only; no → to open; the arrow's `contentDescription` null |
| every `IconButton` / FAB | a `contentDescription` ✓; 36 decorative `null`s each beside text ✓ |

`collectIsPressedAsState`: 0 uses — pressed feedback exists only where Material's ripple survives;
the app's own rows give none (Notion-like, unstated in §2.2).

## 4. Spacing, radii, row heights

**Spacing — measured** (245 `padding` values): 8 ×83, 16 ×42, 12 ×33, 4 ×21, **6 ×20, 10 ×15, 14
×9**, 24 ×8, 20 ×4, 2 ×3, 3 ×2, 5 ×2, 11, 26, 32 ×1. Two grids coexist — the 4-grid (187 sites)
and a 2-offset family (6/10/14: 44 sites) the tray, the Week header and the shortcuts card use
for "compact"; that is a pattern, not strays. True strays: 3 (`CanvasScreen.kt:482, :510`), 5
(`ShortcutsOverlay.kt:131`, `PageDatabaseScreen.kt:1636`), 11 (`PageDetailScreen.kt:803`), 26
(`ShortcutsOverlay.kt:73`). No spacing token exists; every value is a literal.

**Radii — measured:** 2 ×2, 4 ×7, **6 ×20**, 8 ×11, 10 ×6, 12 ×2, 14 ×1 + Material's 28 through
the dialogs (#7).

**Interactive minimum:** provided in the Tasks lists and the Table; `PagesScreen.kt:362` a literal
32 dp; the tree, the Calendar lists (now — `ListInteractiveMinimum`) and the tray none. Buttons 28
dp everywhere but `ShelfPane.kt:213` (32 — one stray). The tree is a fixed 29 dp
(`TREE_ROW_HEIGHT`) beside task rows at the profile's — under Comfortable 29 vs 36, by the spec.

## 5. Per-screen defects visible from the code

- **[Med]** the Merged view's timed-habit row (two lines, `padding(8)`, title in `primary`) → route
  it through `HabitRow`.
- **[Med]** 30 `Text`s with `maxLines = 1` and no `overflow` (the type pass's T4 rule).
- **[Low]** title case in a sentence-case app: *All Pages* (`RoadMapScreen.kt:190, :670`,
  `RoadMapViewModel.kt:68`).
- **[Low]** `PageDatabaseScreen.kt:700` — a "●" prefix inside the sort label instead of a
  `leadingIcon`/`selected` state; a screen reader hears "black circle Ascending".
- **[Low]** `WeekGridView.kt`'s dashed border for an *estimated* block in the 1.2:1 token.
- **[Low]** `SlideOver.kt:78`'s empty `clickable` makes the panel a Tab-reachable node with no role.

## What's working

- Every colour in shared UI is a solved token, the audit enforces it, and the port reproduces the
  spec's floors in all 30 palettes.
- Naming is coherent: lower-case keys, one `Floors` object, one `Ladder`, a documented alias layer.
- No icon-only control lacks a description; `UrgencyPicker` and the swatches carry radio semantics.
- The tree, the task rows and the tray share one row height and one 28 dp button under a pointer;
  the hover-revealed `···` is laid out at alpha 0 so widths do not shift.
- The type scale itself is clean: seven sizes, three true weights, no literal `sp` outside the theme.

## Cannot verify

- The contrast numbers are from a Python port of the solver (the JVM arithmetic is
  `RegisterSolveTest`'s; the three pairs found here — `textDim`/`accentSoft`,
  `textFaint`/`accentSoft`, `border` as a ring — the first two are now in that test).
- Whether `surface2`'s ΔE 1.6 hover reads on the user's monitor — measured below the JND, not walked.
- The phone: the Touch profile's 48 dp around the 26 dp urgency discs, read not run.
- The Glance widgets: their own renderer and `sp` literals; outside this frame.
