# Critique — the theme model, on the 14g·1 build

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14g·1. Read-only;
observable-level findings. **Desktop** at the remembered frame, Compact, Windows in dark mode:
launch on a fresh store (Ink · System → dark); Settings → the ten swatches; Console → Light →
the chrome; Blush → Pages, a page, a right-click menu; Dark → Tasks, Pages; back to Ink · System;
`prefs.properties` read after each write. Pixel colours read from the screen where a screenshot
was ambiguous. **Phone: not walked** — testing paused; the migration, *Deeper blacks* and the
widgets' re-resolve are unit-tested and pending a device.*

## Top priorities

1. **[High · measured, fixed before merge]** With a dark register the desktop's Settings pane
   showed a **light grey `#F0F0F0`** between a dark rail and a dark top bar — the user saw it
   mid-walk as *"in light mode the rail and the title bar stay dark"*. The grey is no palette
   colour: it is the AWT window's own background, showing through a route that paints no
   ground (the Settings column) inside a scaffold that painted none either. Invisible on the
   old always-white desktop. **Fix taken:** the shared scaffold's content sits on one `Surface`
   in `background` — every route, both platforms.
2. **[High · measured, fixed before merge]** Once the ground was painted, the pane's unstyled
   labels (*Sync folder*, *Density*, *Register*…) vanished: they take `LocalContentColor`, which
   is black unless a `Surface` provides otherwise, and the desktop pane had none. The same
   `Surface` provides `onBackground`.
3. **[Med · measured, fixed]** Swatch names clipped at the 56 dp column: *Playgroun*, *Kodachro*.
   **Fix:** the column is as wide as its name, at least the square.
4. **[Med · judged, fixed]** Material's `surfaceContainer` family (menus, dialogs, the switcher)
   was unmapped and would have shown Material's purple-grey through a dark register. Mapped onto
   the two grounds; the tree's right-click menu verified on Blush light.
5. **[Low · judged]** In dark mode the rail and the tree pane, both on `surface2`, read as one
   band beside the page — as the shell mock draws it. Kept.
6. **[Low · judged]** Text at ~12:1 in dark (`#D8D8DA` on Ink's ground, `#CCCFD3` on the cold
   one) is visibly softer than the old `#EDEDEF`; B§13.7.3 rule 2's halation point, by design.
7. **[Low · deferred → 14g·2]** `tertiary` is still Material's: the mind map's node fill and
   the block-reference bar keep their old hue until the token map assigns `third`.

## Dimension by dimension

- **Affordance** — a swatch is its register's ground with the accent as a disc, in the mode being
  shown; the chosen one has a 2 dp accent ring and a medium-weight name; the caption under the
  row says what the register was imagined for.
- **Reach** — ten swatches, three modes, two typefaces in one section on both platforms; the
  phone adds *Deeper blacks*. System follows Windows (dark here) without a click.
- **Fit for purpose** — a change re-solves the whole window at once: rail, bars, chips, the
  page, the menu. Nothing hand-typed remains; `RegisterSolveTest` walks 30 palettes per run.
- **Colour** — Console light's accent violet on the *Choose folder…* link and the selected
  chip; Blush's pink on the `#book` label chip and the FAB; dim labels legible on the tree pane.
- **Copy** — *Register* (not *Colour theme*), *Mode*, *Typeface*, *Deeper blacks*; the desktop
  pane's closing line no longer claims theme is Android-only.

## What's working

- The default: a fresh store opens dark under a dark Windows — System, with no tri-state flag.
- `prefs.properties` holds `theme_register` and `theme_mode` after the walk; the typeface key is
  absent until touched (a missing key is the default, never an error).
- The old names resolve: a stored `CLAY` is the `clay` register.

## Cannot verify

- **The phone** (paused): the one-time migration on a real `tendril_theme_prefs` file, *Deeper
  blacks*, the widgets' `accent2` after the rule change, the Appearance disclosure's summary.
- A Windows theme change *while the app runs* — whether Compose Desktop's `isSystemInDarkTheme`
  re-reads it live; the walk flipped the app's own mode, not the OS's.
- Every register in every tab: Console, Blush and Ink were walked; the other seven are the
  same solver on the same grounds (the test's claim, not the eye's).

## Disposition

#1–#4 fixed. #5, #6 stand. #7 → 14g·2. The phone walk → the PR's open item.
