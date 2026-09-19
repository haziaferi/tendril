# §0.10 item 4 — a one-tap check-in, the mock critiqued

*2026-09-19 · `docs/mockups/check-in.html` (`craftkit ui`, rendered at 1000 px). The pre-build
pass; the decisions it feeds are asked once, before the build. The measured and the judged kept
apart, as the skill asks.*

## Grounding

| app | what it settles | how known |
|---|---|---|
| **Daylio** | the shape: a *How are you?* row of five faces, one tap; an entry carries its time; several a day; the calendar colours a day; no score in the entry itself (its statistics — mood count, average mood, the line chart — are what B§10.3 refuses) | [Assumed] — not installed on the phone; the user was offered the install (2026-09-19) and said proceed |
| **Finch** | energy as a second one-tap scale beside mood; a check-in is a gift, a miss is nothing | [Assumed] |
| **Bearable** | a 0–10 slider and symptoms — the clinical form, not taken | [Assumed] |
| **Notion** (installed) | no check-in; the journal templates keep a *Mood* select on a database row — a setup before the first tap, and a value in a cell, not a moment with a time | [Verified] on the desktop, its Journal template |
| **TickTick · Todoist · Obsidian** (installed, both platforms) | none has one (Obsidian only through community plugins) | [Verified] |
| Tendril today | the Journal's day page opens on the *Today* strip (tasks and due habits, `JournalTodayStrip.kt`); the habit sheet's `MonthOfDots` (a dot where a check-in was, 18 dp cells) | [Verified] |

**What Tendril takes:** the five faces and the one tap, the time on the entry, several a day, the
month as colours. **Better than the ground:** the check-in sits on the day's own page, so *the
words are the page* — Daylio's note field is a second thing to fill; here the Journal is already
open under the row — and the month keeps only Daylio's calendar, none of its statistics. **Not
taken:** the activities grid, the average, the count, the chart, a photo.

## Measured

1. **The five hues on both grounds** — Ink: 6.4 · 7.4 · 7.5 · 8.4 · 9.7 : 1; Chalk: 4.9 · 4.3 ·
   4.5 · 4.1 · 3.8 : 1. All clear the 3.0 floor a mark needs; the numeral on a Chalk disc reads
   3.9–5.1 : 1 — the build solves the hue to `Floors.DATA` (4.6) where a numeral sits on it, as
   the labels are (`labelColours`' tint-aware solve).
2. **Targets** — the phone's faces and bars at 48 × 48 dp, the desktop's at 40 × 36 (28 is the
   pointer floor). ✓
3. **Type** — the phone's row title at `label` 12.5, the logged line at `body` 14 with the values
   at `text`; the desktop's logged line at `description` 12.5. On the scale. ✓ (`craftkit`'s
   sprawl warnings are the mock's own chrome — the page's `h1`/`.lead` — not the app's frames.)
4. **The energy glyph's first step** — one 4 px stroke: at 48 dp it is a dot and at the pointer's
   36 dp a speck. Measured on the render: the first bar occupies 3 × 4 px.
5. **The chosen face's disc** — 32 dp on the phone, 26 on the desktop, filled with the mood's hue
   and the glyph in `onAccent`'s rule (white or the ground, whichever clears 4.6).

## Judged

- **First read** — the row reads as a question, not a demand: *How are you? ›* in `label` with the
  chevron the only hint that more lives behind it. Right register for §10.3. The faces above the
  bars is the order the eye takes; the bars alone would not read as "energy" without the title —
  the logged line names it (*energy high*), and the sheet's rows do (#3 below).
- **Emoji vs. glyphs** — the mock's faces are the platform's colour emoji; the build draws the
  five Material *sentiment* outlines (`SentimentVeryDissatisfied … VerySatisfied`) in `dim`, the
  chosen one on its hue's disc. A monochrome row is quieter and the colour then means one thing —
  *this is what you logged*.
- **Two rows on the phone** (96 dp above the strip on every day page) is the cost of "either
  alone". Acceptable on today's page; on a past day the row is still offered (backdating, Daylio's),
  and never on a future day.
- **The sheet** — the month of discs at 32 dp (the habit sheet's are 18: a coloured disc needs
  room for its numeral); a ring where only energy was logged; nothing where nothing was. The list
  under it is the only "history": a line per check-in, its time in `dim`. No count of days, no
  average — the rule stands.
- **The desktop's one row** holds both scales with a hairline between; under 600 dp of pane it
  should wrap to two, as the phone's.

## Binding on the build

1. The energy glyph: five bars of 2 dp stroke rising **6 → 18 dp** in 3 dp steps (the first step
   a visible 6 dp), or the count of filled bars out of five — not a 4 px speck.
2. The chosen face's glyph on its disc takes `onAccent`'s rule against the hue; the disc's numeral
   in the sheet the same.
3. The sheet's list says which scale — *Good* · *Energy high* · *Good, energy high* — so a bar
   logged alone is never an unnamed number.
4. The row is composed only where `journalDayOf(page) != null && day <= today`; the Today strip's
   own rule (today only) is unchanged.

## Cannot verify

- Daylio's and Finch's exact geometry (face size, row height, the calendar's cell) — [Assumed]
  from knowledge, not measured; the after-pass measures Tendril's own.
- Whether five steps of energy earn their row against a three-step one — decided by use, recorded.
