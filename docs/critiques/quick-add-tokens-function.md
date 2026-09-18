# Critique — the quick-add tokens highlighted inline (L7b), on the build

*2026-09-18 · `design-critique-plus`, the after-pass on the `quick-add-tokens` build — the last
row of §0.10 item 22. The mock and its critique (`quick-add-tokens-mock.md`) went first; the
ground is **Todoist's quick add measured live** (a tinted background behind the recognised
runs, the chips beneath). The desktop walk at the user's 967 × 1100 window, Ink dark, native
grabs; the three desktop homes (the Calendar's strip, the chord's popup, the Add task sheet) and
the drop. Two defects found on the walk and fixed before the pass closed. Read-only.*

## Measured

| | Todoist (native, 125 %) | the build (native, ≈ 1.01 px/dp) | reads |
|---|---|---|---|
| the tint | `#6F2625` on `#282828` (1.39:1) | **`#5D564A`** = `findSoft` on `#1B1D21` (2.33:1); `text` on it 5.10 (Chalk 6.85; ≥ 4.6 in every register by test) | the mock's #1 held: the find mark's tint, never selection's |
| the box | 23 px on 11 px caps, 8 px of side padding, radius 3 | **17 px** (the 14 sp line) in the **26 px** field — 4–5 px of air above and below; flat, no padding | proportionally the same reading; the pill is the stated deviation (mock #2) |
| the runs | *14:30 every week* · *p1* | *fri* · *14:30* · *!* — three boxes, the spaces bare (`tokenTintRanges` trims the whitespace the `!` and kind patterns swallow) | one box per token |
| the chips | *Friday 14:30 ↻ ✕ · P1 ✕* beneath | *Event · ven 25 set ✕ · 14:30 ✕ · High ✕* at the field's right (L7), unchanged | the tint says *read*, the chip *as what* |
| six tokens | — | *Standup mon 9:00 for 30m every week by fri !!* → six runs tinted; the chips on a second row (L7's rule) | — |
| the drop | ✕ on the chip | ✕ on *14:30* → its words lose the tint on the same recomposition | the chip and the line agree |
| the homes | one | the strip (28 dp field), the popup (36), the Add task sheet (36); the phone's Day view field carries it too, unrun | one transformation, `findSoft` passed in |

## Findings

1. **[Med · observed, fixed before the pass]** An **event** line's *by friday* was tinted with no
   chip: the parser recorded the DEADLINE span (the words left the title) and then discarded the
   deadline because an event has none — a silent loss the chips never showed and the tint made
   visible. Fixed in the parser: an event drops the DEADLINE span, so the words stay in the
   title, untinted and unchipped (tested: *Party by friday* → the title *Party by friday*). A
   task's *by friday* is a deadline as before, tinted and chipped.
2. **[Med · observed, fixed before the pass]** The Add task sheet's title field did not take
   focus on open — Ctrl+Shift+N then typing went nowhere (the strip and the popup already
   focused). A `FocusRequester` on the field, requested once when the sheet appears.
3. **[Low · observed, recorded]** In the strip, when the chips wrap to a second row, the leading
   `+` glyph centres against both rows rather than the field's line — L7's layout, unchanged
   here; the popup and the sheet have no such glyph beside a wrapping row.
4. **[Low · judged, recorded]** *Party by friday 20:00* as an event keeps *by friday* in the
   title and takes **no date** — *friday* was inside the deadline phrase when the date pass ran.
   Flipping the kind chip to Task re-reads the line and finds the deadline. Right by the rule (a
   deadline is a task's, §0.6.4); noted so the behaviour is written down.

## Cannot verify

- The phone's Day view field (the fourth home) and the bottom-sheet form of the Add task sheet —
  unrun, as every PR since #82.
- Chalk light on the build — not re-walked; `text` on `findSoft` is 6.85:1 there by the palette
  dump, and ≥ 4.6 on every register by `RegisterSolveTest`.
- TickTick's form (recognition off in the user's install) and Fantastical (macOS).

## Disposition

#1 and #2 fixed; #3 and #4 recorded. Ship — **§0.10 item 22 is closed** (F15 stays unverified).
