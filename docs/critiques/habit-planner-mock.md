# Critique — the habit planner in the Habits tab, on the mock

*2026-09-29 · `design-critique-plus`, the pre-build pass over `docs/mockups/habit-planner.html` and
`docs/mockups/habit-planner-plan-mode.html` (Phase 1 of `docs/plans/2026-09-29-habit-planner.md`).
`craftkit ui` on both files; the render looked at in the Browser pane; every new colour pair computed
from Ink's own hexes, which a throwaway JVM test printed from `paletteFor(Register.INK, dark)` and
`hueColours` (the test was deleted after use; the values are in the mockups' `:root` comments).
Heights measured in the render with `getBoundingClientRect`. The mockups were not otherwise edited
by this pass; the five findings marked **fixed in the mock** were applied to them before commit, as
`calendar-chrome-mock.md` did.*

Each finding is **measured** (a number from a command) or **judgement** (a reading, arguable).

## Revision after the person's review (2026-09-29)

The person, on the first mock: **Day by time carried too much colour, and the entries looked
scattered.** Both were right, and two of the causes were the mock departing from the app:

| cause | measured on the first mock | revised |
|---|---|---|
| colour | five block hues as ticks, the habit purple in every done check, a filled grey *Now* chip: six hues on one phone screen | block headings in text; *· now* in the dim colour; done checks in the accent, **as the app's `HabitCheck` already draws them** (`colorScheme.primary`); one hue on the screen |
| the check's side | at the row's end | leading, as `HabitRow` has it |
| left edges for titles | two — after the 44 dp time column, and indented under *Any time* | one |
| row heights | 56 dp, one or two lines depending on a cadence sub-line | one line, 48 dp — the app's timed-habit row (`rowHeightDp - 8`, `TasksHabitsScreen.kt`) |
| extra labels | an *ANY TIME* label in every block | none; set times still come first, in time order |
| the phone day's length | 1789 dp | **1494 dp** (direction B: 1222) |

Direction B's bands are now neutral (alternating `surface2`, a faint edge). The block hues remain
defined, and are still drawn in the Week grid's row edges, the Blocks editor, the add sheet's
*Where* chips and Plan mode's bands — whether those should go neutral too is a question for the
review, not a change made here. The contrast table below is unchanged by the revision except that
the done check is now `on` on `accent` (7.65 dark, 8.02 light) instead of `on` on `habit`.

Finding 4's figures are the first mock's; findings 1 and 6 about direction B still hold, and the
revision strengthens 1: A's rows are now the app's own, B's chips are still a new kind of row.

## Outcome of the review (2026-09-29)

Direction A is chosen; B is marked *not taken* in the mock. The person's colour rule — a block's
hue only beside its name — is applied: the hue marks came back beside the Day and Week headings
(in the Week grid replacing the 3 dp row edges), and Plan mode's bands went neutral with the mark
beside each name (`dim` on `surface2` for the names: 6.30 dark, 5.11 light, from the same role pair
`RegisterSolveTest` pins). Plan mode's event green and habit purple are its existing entry colours,
not block colours, and are unchanged. Every other open point is deferred (decision record, R3).

## The top three

1. **[High · judgement] Take direction A for Day by time, on the phone and the desktop.** The
   person's stated concern (V3) is misclicks and taps per change. A gives every entry a full-width
   row with its check as a separate 48 dp target at the row's end, and a tap anywhere else on the
   row only selects it. In B the flexible habits are 44 dp chips that wrap next to each other,
   each with its check inside it, so a chip, its neighbour and a check sit within a thumb's width:
   the arrangement that produces the misclicks V3 is meant to avoid. B's gain is length (below,
   finding 4) and a visible shape to the day; the length can be won back in A by collapsing blocks
   that are all done, which costs nothing in taps. **Proposed:** A everywhere; B's tinted bands
   kept only in Plan mode, where the hour grid gives them a meaning.
2. **[High · measured, fixed in the mock] Plan mode's flexible-habit chips collided with set
   times.** Rendered, Morning's four chips wrapped to a second line and ran into *07:00 Water*
   (the band starts at 06:30, 24 dp above the 07:00 line; two lines of 24 dp chips need 52). Night
   did the same over *22:00 Water*. Any block whose first set time is within about 30 minutes of its
   start has room for one line of chips at most. Fixed by replacing the row with one pill under the
   block name, *4 any time*, which lists them on a tap. A first version, *4 any time · 3 done*,
   measured wider than the 96 dp name column and overlapped the timed entries in turn, so the count
   of done ones moved into the list the pill opens.
3. **[Med · measured, fixed in the mock] Two text pairs under 4.5 : 1 on Ink light.** Plan mode's
   habit length meta (`dim` on `habitSoft`) at **4.46**; the add sheet's placeholders (`faint` on
   `bg`) at **3.75**. The second was the mock's error, not the app's: `TendrilField` draws a
   placeholder in `onSurfaceVariant`, which is `textDim` (`TendrilField.kt:114`, `Theme.kt:45`).
   Both now use the app's own roles: `text` on the tint, **9.12** (7.61 dark); `dim` for placeholders, **5.67** (7.20 dark).

## What works

- **§0.5.2 holds.** Measured by grep over both files for *missed*, *behind*, *overdue*, *streak*,
  *N of M*, *N / M* and a percentage in copy: every hit is CSS (`100%`, `50%`) or copy that says the
  opposite (*never "missed"*; *drawn behind the grid*). A done entry is ticked in the habit hue; an
  entry not done is plain; past days dim their heading, never their entries. *5 done today* counts
  up with no denominator. *(measured, grep)*
- **One column of titles.** In A, the rail puts set times in a fixed 44 dp column and indents
  *Any time* entries to the same line, so titles align down the whole day whether timed or not.
  *(judgement)*
- **The neutral notes read as information.** A glyph, the dim colour, the fact, the way to change
  it, and the entry that would have been lost kept visible in the note. The reference engine drops
  an entry outside every block; the mock keeps it. *(judgement)*
- **Every prompt's way out says what it does.** *Put it back*, *Don't pause*, never a bare
  *Cancel* where declining restores the entry. *(judgement)*
- **The tokens are the app's.** No hand-picked colour: Ink's roles and the hue wheel's solved values.
  All 33 new pairs × 2 modes computed; 64 of 66 passed as first drawn, 66 of 66 after finding 3.
  *(measured)*

## Findings

4. **[Med · measured] The phone day is long in A.** On this sample day (21 entries, five blocks
   plus *Any time today*) the phone frame measures **1789 dp**, 197 of it chrome down to the day
   header: about three screens of a 780 dp phone. B measures **1222 dp**. The mock's own note
   claimed "two thumb-scrolls" and is corrected. If A is taken, a block whose entries are all done
   could fold to its header line (*Morning · 6 done*) without hiding anything that still needs
   doing. On this sample day no block is all done at 11:20 (Morning still has Tidy), so the fold
   saves nothing here; it pays in the evening, when the day's first blocks are finished.
   *(the length is measured; the fold is judgement)*
5. **[Med · measured, fixed in the mock] Bare *M* for Move to… collides with type-ahead.** The
   desktop list already treats a typed letter as a jump to the matching title
   (`ListKeyboard.kt:48`, `typeAheadTarget`), so *M* would never reach the toolbar. The mock now
   shows *Ctrl+M*. Whether Tendril's key model has a better home for it (the row menu's key, or
   *Enter* opening a menu that holds Move to…) is for Phase 5 to settle against the 14e model;
   *Ctrl+M* is a placeholder that at least does not collide. *(measured, the code; the choice is
   judgement)*
6. **[Low · measured] B's gutter wraps *Afternoon*.** At 13 px semibold in a 56 dp gutter the
   label breaks onto two lines (38 dp against 19 for the others), held together by a soft hyphen.
   Italian *Pomeriggio* is the same length (Phase 4). Another reason A's full-width header is safer.
   *(measured)*
7. **[Low · judgement] *By area* collapses a several-a-day habit to one row** (*Water, and 5 more
   today*). Proposed because Water alone would otherwise take six of Health's rows; the cost is
   that its later occurrences can be checked in only from *By time*. Left open for the review.
8. **[Low · judgement] The prompt sheets are drawn over an empty phone.** They read clearly, but a
   reviewer cannot see what the scrim covers. Cosmetic; not changed.
9. **[Info · measured] Tap targets.** Phone: row checks 48 dp, toolbar buttons 56 dp on a 64 dp bar,
   menu items 48 dp, B's chips 44 dp with a 44 dp check (raised from 40 after `craftkit ui` flagged
   it — **fixed in the mock**). Desktop: 28 px checks and 29 px rows, the app's existing desktop
   profile under a pointer (`craftkit ui` flags these against the 44 px touch minimum; they are not
   touch targets). *(measured)*
10. **[Info · measured] The prompt titles skipped a heading level** (h2 → h4); now h3. **Fixed in
    the mock.** `craftkit ui` otherwise reports 0 errors on both files; its colour-, radius- and
    spacing-sprawl warnings count two registers and five block hues drawn side by side, and the
    radii are the app's own (2 tick, 4 key cap, 6 control, 8/12 frames, 16 sheet).

## Not verified here

- **Compose rendering.** The mock is HTML at 1 px = 1 dp with Inter from Google Fonts; the app's
  type metrics, the Material ripple and the real row heights come from `LocalDensityProfile`. The
  1789 dp figure is the mock's, and would shift with the phone profile's actual row height.
- **Italian strings.** Every label here is English; Phase 4 adds Italian. B's gutter is the tightest
  place (finding 6); the toolbar's six labels at 12 px in 56 dp are the next.
- **The suggestion's days.** *Monday, Wednesday, Friday* for Run and *Tuesday, Thursday* for Yoga
  are what the golden `base-rules` case places; the mock does not run the engine.
- **Plan mode's desktop day columns.** The mock draws the phone only; whether the block names crowd
  the desktop's narrower columns is listed as open in the mock itself.

## Contrast table — the new pairs

From `pairs.py` in the session scratchpad (WCAG 2.1 relative luminance), after the fixes:

| pair | where | Ink dark | Ink light |
|---|---|---|---|
| text on bg | row titles | 11.86 | 11.59 |
| dim on bg | meta, rail times, *Any time*, day count, placeholders, empty check ring | 7.20 | 5.67 |
| dim on soft | meta on a selected row, today's week column | 4.64 | 4.71 |
| soft-text on soft | *Now* tag | 5.65 | 7.09 |
| text on s2 | toolbar labels, *More* menu | 10.37 | 10.44 |
| accent on s2 | toolbar *Done* | 6.69 | 7.23 |
| on on habit | the tick in a done check | 6.67 | 9.54 |
| habit on bg | a done check's fill; the week grid's tick | 6.67 | 9.54 |
| block hue on bg (non-text, 3.0) | Morning · Midday · Afternoon · Evening · Night ticks | 5.73 · 6.99 · 7.62 · 5.75 · 5.80 | 5.63 · 5.63 · 5.69 · 6.68 · 5.65 |
| text on Morning tint | B's titles on a band | 10.65 | 10.42 |
| dim on each block tint | B's times on bands | 6.27–6.46 | 5.02–5.10 |
| block hue on its tint | Plan mode's block names | 5.15–6.64 | 5.06–5.91 |
| text on habitSoft | Plan mode's timed habits and their length | 7.61 | 9.12 |
| text on eventSoft | Plan mode's events | 6.73 | 9.63 |
| accent on bg | the now line, B's *Now 11:20*, (on on accent) primary buttons | 7.65 | 8.02 |

The tightest pair is `dim` on `soft` (4.64 dark, 4.71 light): the meta line on a selected row and
today's week column. It passes, with the least margin of any pair here, and it is already pinned:
`RegisterSolveTest` holds `textDim` on `accentSoft` to `Floors.DIM` in every register and mode
(`RegisterSolveTest.kt:51`), so the build inherits the guarantee rather than needing a new one.
