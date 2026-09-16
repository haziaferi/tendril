# Critique — the coloured-elements mock, before 14g·2

*2026-09-16 · `design-critique-plus`, the pre-build pass for 14g·2 (the token map) on
`docs/mockups/coloured-elements.html` — fourteen register-modes (the seven registers the mock was
drawn for, light and dark; Clay, Moss and Mauve returned after it), four panels each. Read-only.
**Measured:** `craftkit ui` over the file, then every derived colour re-read from each
register-mode's variables and inline styles and checked against the mock's own rule 9 (4.6:1 read,
dim 4.6, faint 3.05, marks 3:1) and rule 1 (data hues ≥ 30–40° from the accent; event/habit
≥ 60°). The 14 % tints were mixed in sRGB (the mock mixes in OKLab — a difference of a few
hundredths). **Judged:** one render at 1560 px, light mode, Ink and Console.*

## Top priorities

1. **[High · measured]** The label chips fail their own floor: the hue as text on its 14 % tint
   measures **4.09–4.25:1** on every register-mode (Ink light `#B54A80` on `#F5E8EE` 4.09). The hue
   was solved to ~4.9 on the *ground*, and the tint costs the rest. **Fix:** solve each label hue
   against *both* the ground and its own tint — the `solveTo(against = …)` the 14g·1 engine already
   does for dim and faint on `surface2` — so the chip clears 4.6 wherever the hue lands.
2. **[High · measured]** In every **dark** register-mode the error red on its soft chip is
   **4.1–4.3:1** (the *Blocked* and *overdue* chips). Light modes measure 4.6 exactly. **Fix:** the
   same — solve the error hue against the ground and `errorSoft` together.
3. **[Med · measured]** The third hue is not a fanned hue in 14g·1: `third` is `mix(accent, text,
   0.55)` where no second channel exists, so it has the accent's hue and differs only in depth. The
   mock's `--tert` *is* a separate hue — but by no printed rule, and on Kodachrome dark it sits
   **14°** from the accent, on Chalk light **31°** (rule 1 asks ≥ 30–40°). Event and habit hold
   everywhere: **61–119°** from the accent and 179–180° from each other. **Fix:** derive all three
   data hues from the accent by one rule — event at +120°, habit at −60° (as the mock effectively
   has them), the third at 180° — with the register's second channel taking the third slot where
   one exists (Swiss, Playground). Then no register can drift under 60°.
4. **[Med · judged]** The two callouts (`#E2E7DF`, `#E2E6EE` on Ink light) are hard to tell apart
   and hard to tell from `surface2`: a 14 % tint is a chip's strength, not a card's. Seven callout
   tints at 14 % will read as one grey. **Fix:** 20 % for callouts (the emoji is already the second
   channel), or 14 % plus the hue as a 3 px left bar — the block reference's shape, which the mock
   draws right beside them.
5. **[Med · judged]** The Month row's *task* dot is drawn in the accent (`--task: #4A5568`) — a
   task with no category wears the reserved colour, against rule 1, and beside the today marker it
   reads as "selected". Not 14g·2's (the dots are their own PR after 14g) — recorded so that PR
   gives an uncategorised task's dot the ladder's *none* (dim), not the accent.
6. **[Low · measured, already answered]** Dark dim on `surface2` 4.32–4.35 and Console dark's
   accent 5.4 on the ground / 3.8 on `accentSoft` — the mock predates 14g·1's amendments (dim
   solved against the worse ground; the accent solved to 7.6; `accentSoftText` solved on the soft).
   The build inherits the fixes.
7. **[Low · judged]** The mention has a soft background in the mock (`.mn` on `--soft`) and the
   app's has none (SemiBold, coloured). A background makes an `@mention` look like the find mark and
   the selection. Keep the app's form: accent text, medium weight, no fill.

## Dimension by dimension

- **Colour** — event 5.4–5.6, habit 7.8–8.1, database-date 5.1–5.3, third 4.9–5.1, error
  5.5–5.6 on their grounds: every hue read as text clears 4.6 (measured). Month dots ≥ 5.1
  (marks need 3). Text on a 14 % category tint 11.8–14.5 — the calendar block's title never
  suffers. `faint` 3.10–3.24 on its ground.
- **Hierarchy** — the accent appears only where rule 1 says: the link, the mention, *Tue*, the
  timer, the ticked box. Data hues stay in the blocks and chips. The rule is visible in the render.
- **Consistency** — the block reference's left bar, the calendar block's stripe and the mock's
  callouts are three different 3 px bars (`tert`, ladder, none): #4 proposes the callout join them.
- **Copy** — the legends print the rule beside the colour ("→ mention (dim) · — related (fan hue
  3)"), the form the spec will keep.
- **Fit** — the mock is the whole channel map; 14g·2 takes its token half (rules 1, 4's hues, 6,
  7, 8, 9 and the third hue), 14g·3 the ladder (rules 2, 5), and the dots and the database hue
  slider their later PRs (rule 3, rule 4's Month).

## What's working

- One rule per colour, printed beside it; the light and dark panels are the same rules, not two
  hand-tuned sets.
- Event and habit at opposite sides of the wheel from each other and ≥ 60° from the accent on all
  fourteen — the calendar's three layers are legible at a glance in every register.
- The done row: dim, struck, on the surface — no colour spent on a finished thing.

## Cannot verify

- **Clay, Moss, Mauve** (returned to the set after the mock) — the same rules, unmeasured here;
  `RegisterSolveTest` will cover them.
- **A `find` mark** — not in the mock; `find-in-page-mock.md` #1 only asked that it stop sharing
  `accentSoft`. Defined in the plan, tested in the build.
- **OKLab vs sRGB** for the 14 % tints — a few hundredths; the build mixes in sRGB and solves to
  the floor, so the floor is what is tested, not the mix.
- **Colour-vision deficiency** for the eight labels once re-solved per ground — the original set's
  ≥ 12 dE under deuteranopia/protanopia was measured on fixed hexes; a lightness solve moves them.
  The label's name is the second channel (§13.7.3 rule 4), so this is polish, not a floor.

## Disposition

#1–#3 become rules in the 14g·2 plan; #4 and the `find` mark are decisions for the user; #5 is a
line for the dots PR; #6 is inherited; #7 stands as the app's current form.
