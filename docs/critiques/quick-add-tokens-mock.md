# Critique — the quick-add tokens highlighted inline (L7b), on the mock

*2026-09-18 · `design-critique-plus`, the pre-build pass on `docs/mockups/quick-add-tokens.html`
— L7b of §0.10 item 22, the option recorded at L7 (2026-09-17) as not taken then. Ground
**measured live**: Todoist's quick add (Windows, native at 125 %) — a line typed as *Dentist
tomorrow 14:30 every week p1*: the recognised runs (*14:30 every week*, *p1*) carry a tinted
background `#6F2625` on the card's `#282828` (1.39:1 — a tint, not a text colour), the text kept
white on it (10.6:1); the box 23 px tall on 11 px caps, 8 px of side padding, a 3 px radius; the
chips beneath (*Inbox · Friday 14:30 ↻ ✕ · P1 ✕ · At time of task*) name each token and carry the
✕ that drops it. **TickTick**'s add bar parsed nothing in the user's install (smart recognition
off; a setting on the user's account — not toggled): [Assumed] an accent tint on the date words.
**Fantastical** [Assumed — macOS]: coloured words, no background. `craftkit ui` on the mock; the
contrasts below from the palette dump (`paletteFor`, Ink dark and Chalk light). Read-only.*

## Measured

| pair | value | floor | reads |
|---|---|---|---|
| `text` on `findSoft` — Ink dark | #D8D8DA on #5D564A · **5.10:1** | 4.6 (DATA, tested in `RegisterSolveTest`) | pass |
| `text` on `findSoft` — Chalk light | #343638 on #BDC3CD · **6.85:1** | 4.6 | pass |
| `findSoft` on `bg` — Ink / Chalk | 2.33:1 / 1.71:1 | none (a tint; Todoist's is 1.39) | a tint that is seen without shouting |
| the tint box | 20 dp (the 14/20 line) in the 28 dp field; 4 dp of air above and below | Todoist 23 px on a ~48 px row | proportionally taller, same reading |
| `third` on `bg` (frame D, the coloured-words branch) | 9.11 / 10.15 | 4.6 | pass, not taken |
| the mock's own chrome | `craftkit`: no error; the mock page's grey on grey 5.97 (AA) — the page, not the app | | — |
| radii on the page | 4 · 6 · 8 · 10 | the family | pass |

## Findings

1. **[Med · measured, binding] The tint is `findSoft`, never `accentSoft`.** The accent's tint is
   selection's (14g·2, rule 1; `find-in-page-mock.md` #1 refused the same overlap): a selected
   token and a parsed one in the same field would be one colour. `findSoft` already means
   *recognised text* (the find mark), and text on it clears 4.6 in every register by test. The
   build passes `palette.findSoft` and nothing else.
2. **[Low · judged, stated deviation] Compose's span background is flat.** A `SpanStyle(background)`
   paints the glyph run's line box — no side padding, no radius — where Todoist's pill has 8 px
   and a 3 px radius. Frame D's right half shows the pill through a second drawing path
   (`drawBehind` over the layout); it eats the space before the next word and doubles the
   mechanism. Taken as is: one transformation, the box at the line height. Recorded, not fixed.
3. **[Low · judged] The words coloured (frame D, left) is the weaker form.** A two-word token
   (*every week*) reads as two emphasised words, not one run; Medium inside a Regular line moves
   the caret's rhythm as you type. Todoist and TickTick both paint a background. Not taken.
4. **[Low · judged] The space between two adjacent tokens stays bare** (*fri* · *14:30* · *!*):
   three separate boxes with a 4 dp gap, as Todoist's pills break at the space. Right — a run is a
   token; the gap says where one ends.
5. **[Low · judged] The chips do not change.** The tint says *read*; the chip says *as what* and
   carries the ✕. Todoist's split is the same. Nothing on the chips moves in this PR.
6. **[Low · measured] Three homes, one rule.** The strip (28 dp field), the popup (36) and the Add
   task sheet's title field all parse through `QuickAddParser`; the transformation is built from
   `parsed.spans`, so a dropped token (B″) loses its tint on the same recomposition that removes
   its chip. The build passes the transformation from `QuickAddField` (two homes) and from the
   Add task sheet's field (the third).

## What's working

- The line and the chips now agree in the eye: *fri* tinted in the line, *Fri 18* in the chip.
- Nothing new to learn — the ✕ and the kind flip are where they were (L7).

## Cannot verify

- The rendered box in Compose (the line-height rule, the caret over a tinted run) — measured on
  the build in `quick-add-tokens-function.md`.
- TickTick's form (its recognition is off on this install); Fantastical (macOS).

## Disposition

Build B with #1 binding and #2 stated; D's two halves are the deleted branches.
