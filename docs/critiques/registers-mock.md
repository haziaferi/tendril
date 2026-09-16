# Critique — the theme model's derived tokens, before 14g

*2026-09-16 · `design-critique-plus`, pre-build pass for 14g. Read-only. The subject is not a
screen but the **derivation rules** B§13.7.3 fixed and `docs/mockups/desktop-shell.html`'s
`applyRegister` implements (dim and faint *solved* to floors by an sRGB mix; `surface2`, border,
the accent's soft tint and its text, the third hue *mixed* in OKLab at fixed shares). **Measured**
by recomputing those rules for the seven registers in both modes (the mock's arithmetic ported to
a script, since the pane's JS classifier was unavailable) and reading every pairing's WCAG
ratio; **judged** against B§13.7.3's own four rules. The phone and the desktop share the model,
so one pass serves both; the ladder (B§13.8.1) has its own numbers already and is not re-measured.*

| register / mode | text on bg | text on s2 | dim on bg | **dim on s2** | faint | accent | on-accent | soft-text on soft | soft vs bg | border vs bg | third hue |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Console L | **17.2** | 15.7 | 4.93 | **4.51** | 3.17 | 8.3 | 8.6 | 7.72 | 1.22 | 1.29 | 12.0 |
| Console D | **15.0** | 13.6 | 4.74 | **4.31** | 3.10 | 7.6 | 7.6 | 6.88 | 1.38 | 1.31 | 10.6 |
| Playground L / D | 17.2 / 15.0 | 15.7 / 13.6 | 4.93 / 4.74 | **4.51 / 4.31** | 3.17 / 3.10 | 7.9 / 7.6 | 8.2 / 7.6 | 7.46 / 6.89 | 1.22 / 1.37 | 1.29 / 1.31 | 6.9 / 6.9 |
| Blush L / D | 17.2 / 15.0 | 15.7 / 13.6 | 4.93 / 4.74 | **4.51 / 4.31** | 3.17 / 3.10 | 7.9 / 7.7 | 8.1 / 7.7 | 7.43 / 6.89 | 1.22 / 1.38 | 1.29 / 1.31 | 11.8 / 10.5 |
| Chalk L / D | 17.2 / 15.0 | 15.7 / 13.6 | 4.93 / 4.74 | **4.51 / 4.31** | 3.17 / 3.10 | 8.1 / 7.6 | 8.4 / 7.6 | 7.55 / 6.87 | 1.23 / 1.38 | 1.29 / 1.31 | 12.0 / 10.5 |
| Kodachrome L / D | 17.2 / 15.0 | 15.7 / 13.6 | 4.93 / 4.74 | **4.51 / 4.31** | 3.17 / 3.10 | 8.1 / 7.6 | 8.4 / 7.6 | 7.56 / 6.86 | 1.23 / 1.38 | 1.29 / 1.31 | 12.0 / 10.5 |
| Swiss L / D | **17.5** / 16.4 | 15.9 / 14.9 | 4.83 / 4.76 | **4.38 / 4.31** | 3.10 / 3.24 | 11.4 / 7.6 | 12.4 / 7.6 | 9.84 / 7.15 | 1.26 / 1.36 | 1.31 / 1.31 | **2.6** / 12.6 |
| Ink L / D (`Palette.kt`) | 17.3 / 14.4 | 15.8 / 12.8 | 4.82 / 4.83 | **4.42 / 4.28** | 3.12 / 3.21 | 7.5 / **5.4** | 7.5 / 5.4 | 7.12 / 5.54 | 1.22 / 1.35 | 1.28 / 1.39 | 11.5 / 8.7 |

## Top priorities

1. **[High · measured]** Body text sits at **15–17.5:1** on every register — outside the eye
   pass's own **9.5–13:1 band** (B§13.7.3 rule 2 names 17:1 black-on-white as the fatigue case),
   because the mock derives everything *from* `text` and never solves `text` itself. **Fix:** the
   engine solves the text colour too — the ground's text lifted (light) or dimmed (dark) until
   it reads at ~12:1, the band's upper third — and derives dim and faint from *that*. One line in
   the same solver; every register then honours the rule it was written under.
2. **[Med · measured]** `dim` is solved to 4.6:1 on the ground and lands at **4.28–4.51** on
   `surface2` — the hover, selected and sheet ground — under AA in every dark register and in
   Swiss light. **Fix:** solve dim (and faint) against the *worse* of `bg` and `surface2`, so the
   floor holds wherever the text is drawn. Same for the accent's soft-text on soft (all pass
   today at 5.5–9.8; keep the solve, it costs nothing).
3. **[Med · measured]** Swiss light's second channel, signal yellow `#B8960A`, reads at **2.6:1**
   on its ground — under the 3:1 floor for a non-text mark it is meant to be (fills and marks
   only, B§13.7.2). **Fix:** a mark that is a fill gets a 1 px edge in the hue's solved text
   colour, or the yellow is deepened to 3.0:1 (`#A6860A`-ish) — the solver's job, one floor.
4. **[Low · measured]** Ink dark's accent is **5.4:1** where the model asks 7.6 (Console's, the
   reference the user chose); `Palette.kt` is "unchanged" by decision, so Ink is the one register
   the solver does not touch. Worth one re-solve in 14g's PR so Ink is not the exception to its
   own rule — or the decision restated.
5. **[Low · judged]** The selection tint (`soft`) sits 1.22–1.38:1 from its ground: a selected
   row is a whisper, as Bear's and Obsidian's are. Acceptable because selection never rides on
   colour alone here (the keyboard ring, the pane, the bold title) — rule 4 applied to chrome.
6. **[Low · judged]** Hairlines at 1.28–1.39:1 are Notion's weight (~1.2) — fine for borders
   that carry no meaning; a border that *does* (the find bar's field, a chip's edge) should take
   `faint` (3:1) instead. The token set has both; the sites must choose.

## What the numbers confirm

- **Rule 1 holds where it is applied**: dim and faint clear 4.6 / 3.05 on the ground in all
  fourteen register-modes, by construction.
- **The accents land where the user set them**: 7.5–8.3 light, 7.6 dark (Swiss light 11.4 —
  navy is darker than needed; the solver could lift it to 8.0 for a lighter navy, or leave it).
- **On-accent text** at 7.5–12.4 everywhere: white on the hue, the ground on the hue in dark.
- **The third hue** (mixed from the accent when there is no second channel) at 8.7–12.6 as text
  — reserved for edges and block references, well above their 3:1.

## Cannot verify

- Anything OKLab-mixed is reproduced here by the same formula, not by the browser; a 0.1
  difference is possible. The ladder's numbers (B§13.8.1) are the ladder mock's, not re-run.
- Colour-vision separation of the second channels from the accents — designkit's CIEDE2000 runs
  were the benchmark's; this pass is WCAG only.

## Disposition

#1–#3 → the solver's floors in 14g·1 (text to a band, dim/faint against the worse ground, marks
to 3:1); #4 → Ink re-solved, decision recorded; #5, #6 → the token sites' rule in the spec.
