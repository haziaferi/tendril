# Critique — the urgency-ladder mock, before 14g·3

*2026-09-16 · `design-critique-plus`, the pre-build pass for 14g·3 (the ladder) on
`docs/mockups/urgency-ladder.html` — the decided R-wide+ ladder beside its two rejected widths,
on the four grounds, in the three forms (a 4 px stripe on a row, a star, the stripe on a calendar
block). Read-only. **Measured:** `craftkit ui`; the ladder re-derived by rule (a hue/saturation
table per step, lightness solved to a contrast target) and compared with the mock's hexes on all
30 palettes, adjacent steps in CIEDE2000, colour-deficiency by Machado's matrices. **Judged:** one
render at 1400 px.*

## Top priorities

1. **[High · measured]** The **star form fails as text**: `craftkit` flags the *low* star at
   **3.05–3.19:1** on both light grounds (`#B9887E`, `#B8857A`). The mock's own rule allows 3:1
   because the ladder is a mark — but a `★` is a glyph in a text run, and a screen reader, a
   text-scale bump or a caption beside it reads it as text. **Fix:** the ladder is drawn as a
   **stripe** (rows, calendar blocks) and as a **disc** (the picker, the detail pane) — shapes,
   never a glyph; the star retires with the flag.
2. **[Med · judged]** The mock draws *none* as a **dim stripe on every row**. On a list where most
   tasks have no urgency (the common case, §0.5.2), that is a grey bar down the whole list — chrome
   pretending to be data. **Fix:** *none* draws nothing; the ladder starts at *low*. The legend
   keeps dim as the ground's *none* swatch.
3. **[Med · measured]** The ladder is **rule-derivable**, so it need not be a table: hue 10→4°
   with saturation 0.30→0.91 on light (targets 3.0 / 5.0 / 7.9 / 13.0), hue 12→6° with
   0.34→0.94 on dark (11.5 / 8.1 / 6.1 / 4.6), lightness solved to the target on the actual ground.
   Re-derived, it matches the mock's hexes at **dE 0.0–0.4** on every light and dark ground and
   extends to the OLED ground (dE 1.7–5.6 from the plain-dark hexes — re-solved, as every token
   is). Adjacent steps **≥ 10.8 dE** normal, **≥ 8.5** under protanopia/deuteranopia (the mock said
   ≥ 11 / ≥ 9 with its own CVD model). The build takes the rule, not the hexes.
4. **[Low · judged]** On a dark ground the top step (`#F83F2A`) and the error family (`#E09B95`
   on Ink dark) are two reds. They are told apart by form — the stripe versus a chip or the
   overdue *text* — and by depth; a row that is both overdue and urgent shows both, each in its
   place. No change; the spec names the pair.
5. **[Low · measured, informational]** The rejected widths *R* (adjacent 5.2 dE) and *R-wide*
   (9.2) are on the page as the record of why R-wide+ won; the page's `h1 → h3` skip and missing
   viewport are the mock's, not the app's.

## Dimension by dimension

- **Colour** — one family on every ground; the rule's steps clear their targets within 0.1 on 29
  of 30 palettes (Swiss light's *urgent* 12.8 against 13.0 — its cooler white).
- **Hierarchy** — the render: the stripe reads first, the checkbox second, the title third; the
  star competes with the checkbox at the same size and weight (#1).
- **Consistency** — the calendar block's 3 px stripe and the row's 4 px stripe differ by a pixel;
  the build uses one width (4 dp) in both places.
- **Fit** — a mark that is scanned, never read: the build binds it to the row and the block, with
  the level's name in the detail pane and the picker for anyone who wants the word.

## What's working

- The decided ladder holds on every ground including OLED once re-solved; the reversal on dark
  (pale → saturated, urgent the deepest) survives the rule.
- A five-step scale that a colour-deficient reader keeps as five steps.

## Cannot verify

- The stripe at the phone's row height and the calendar block's 12 sp text beside it.
- The level's *name* in a screen reader (the build gives the stripe a content description).

## Disposition

#1 and #2 become rules in the plan; #3 is the engine's shape; #4, #5 stand.
