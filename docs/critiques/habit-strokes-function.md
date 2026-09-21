# Habits as brush-strokes — the build walked

*2026-09-21. Built on the mock's four decisions and the same day's four softening ones
(`docs/critiques/habit-strokes-mock.md` and its addendum). The desktop at the dev window, native
grabs; the phone by dump and screencap.*

## What was built

- `domain/plan/HabitStrokes.kt` (pure, 4 tests): `habitStrokes(habits, day, today)` — every
  active habit with a time, its span the time and the duration (30 min without one), clipped at
  midnight, `checkedIn` only when the day is today and the habit was checked in today;
  `strokeHeightDp` with the 18 dp floor.
- `ui/calendar/HabitStrokeLayer.kt`: one painter — the full lane, the habit hue at 12 %
  (`HABIT_STROKE_SHARE`) feathered 6 dp at each end (4 on a short stroke), the name in `caption`
  at Regular in the hue, no time, centred under 30 dp; a 5 dp dot before the name on today's
  lane after the check-in; a click opens the habit's sheet. Composed **before** the blocks in
  `WeekGridView` and `PlanView`, so a block over the span covers it.
- A habit is no block: `timelineBlocks` no longer takes habits, `BlockKind.HABIT` is gone, and
  *Planned* no longer counts a habit's span — reserved, not booked.
- The Month: no habit chip on the grid, no habit dot on the phone's cells. The Agenda and the
  Day's list keep their habit rows; the Layers menu's *Habits* governs the strokes.
- The sheet from the Calendar: `HabitDetailSheet` on a `TasksHabitsViewModel` of the Calendar's
  own, with *Edit…* → the Add sheet on the habit (S10) — a habit's time and length can be changed
  from where its stroke is.

## The walk

| device | tried | observed |
|---|---|---|
| desktop | Calendar → Week, *Stretch* 07:30 (no duration) and *Pages read* 21:00 for 1 h seeded | a 30 min stroke at 07:30 and an hour's at 21:00 on every day, the name at the start in the hue, no time; Saturday's *Standup* at 21:00 sits over *Pages read* and hides its name — the block wins; the header's *Planned 30m* counts *Standup* alone |
| desktop | a click on Monday's *Stretch* stroke; *Edit…* → the Add sheet on it → duration 60 → *Save* | the habit's sheet (*Here whenever you want it.*, *Edit…*); every day's *Stretch* stroke grows to the hour at once |
| desktop | Tasks → Habits → *Stretch* checked in → Calendar | Monday's stroke carries the dot before its name; Tuesday to Sunday none; the wash unchanged; undone afterwards |
| desktop | `Layers ▾` → *Habits* off / on | the strokes go and return, the menu stays open |
| desktop | `Month ▾` | no habit anywhere, the other chips as before |
| desktop | `Day ▾` → *Plan the day* on / off | the stroke across the one lane with its dot, *Planned 30m*; off → the list keeps its two habit rows (*Stretch 07:30 · 60 min*, *Pages read 21:00 · 60 min*) |
| phone | Calendar → `···` → *Layers* → *Habits* on → the Day | the list's habit row (*Stretch 09:00*) as before |
| phone | `···` → *Plan the day* | a 30 min stroke at 09:00 on the light register, the name in its hue; Tasks → *Stretch* checked in → the Day → the dot before the name (undone afterwards) |
| phone | `Day ▾` → *Month* | no habit dot on any cell (every day carried one before) |

## Measured

- Desktop (Compact, ×0.85): a 60 min stroke **38 px** = 45 dp; the feather **5 px** at each end (6 dp);
  the wash on a plain lane **(47, 43, 54)** over the ground **(27, 29, 33)** — exactly 12 % of the
  hue, **1.22 : 1** against the ground; the name (the habit hue) **5.46 : 1** on it; on today's
  tinted lane **4.93**. The 08:00 hour line shows through the wash — a tint under the day.
- Phone (the light register): the wash **(235, 230, 238)** on white, **1.23 : 1**; the name
  **7.76 : 1** on it; a 30 min stroke at 56 dp an hour is 28 dp — short, the name centred.

## Findings

1. **[Low · judged] The Week's `Planned` no longer counts a habit** — by the rule (reserved, not
   booked); said in §3.2. A person who plans their day around habits reads the number smaller
   than before. Recorded, not changed.
2. **[Low · judged] Every active timed habit is on every day**, as the chips were — a weekly habit
   reserves its hour daily. A period-aware rule needs the log's rhythm, not the frequency alone
   (§0.6.6's *not turned into "due today"*). Recorded in §3.2.
3. Nothing else: the stroke under a block, the click through bare wash only, the Layers switch, the
   Month without a habit, the dot on today's lane alone — all as the mock said.
