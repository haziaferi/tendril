# Critique — `docs/mockups/habit-strokes.html` (habits as brush-strokes, pre-build)

*2026-09-21. **Softened the same day — see the addendum at the end; the numbers below are the first mock's.** The user: a habit should not have a task's shape and never appear in the Month; on
the grids it is a brush-stroke across its span of time, which tasks may overlap — a reminder to
keep that time for oneself. Decided in one batch: the span is the habit's time + duration (no
duration → 30 min; no time → not on the grid); a soft-edged wash in the habit hue, the name along
it in small caps, filling after the check-in; Month never, the phone's dots lose the habit dot,
Agenda and the Day's untimed list keep habit rows; inert to drags and drops, a click opens the
habit's sheet. Rendered with `craftkit render` at 1200 px; contrasts computed on the tokens.*

## Grounds

| app | what it does with a habit / routine on a calendar | take |
|---|---|---|
| **Reclaim.ai** [Assumed] | a *Habit* is a flexible block the scheduler places inside a window; a meeting booked over it pushes it, and it is drawn lighter than an event | the stroke yields to blocks; drawn lighter |
| **Google Calendar's Goals** (retired) [Assumed] | a goal's session was a lighter, auto-scheduled block; declining it moved it | lighter than a booking |
| **Structured** [Assumed] | routines are ordinary timeline blocks, the same shape as tasks | what the user does *not* want |
| **Tendril today** | a habit with a time is a block in the habit tint on Day and Week, a chip in the Month, a dot on the phone's month | the shape that goes |

None of the three draws a habit as a wash *under* the day; Tendril's stroke is its own reading of
"reserved, not booked".

## Top priorities

1. **[High · measured] A short habit cannot hold its name** — a 20 min stroke is 15 px at 45 dp an
   hour; the name's line is 18. *Fix (in the mock):* a stroke is never drawn under **18 dp** (a
   brush has a minimum width) and a short stroke centres its name; a stroke ≥ 30 dp tops it. The
   build keeps the rule: `max(minutes × 0.75, 18)`.
2. **[Med · measured] The name clears AA on both registers, the wash does not have to** — Ink dark:
   the habit hue on the 18 % wash **4.88 : 1**, `text` on the 36 % wash after the check-in **5.94**;
   Chalk light **5.07** and **6.71**. The wash against the ground is **1.37 / 1.30** — under the 3 : 1
   non-text floor by design: it is a tint under the day, not a required graphic; the name is what
   carries the habit. Said in the spec, as `surface2`'s 6 % is.
3. **[Med · judged] A stroke under a block is invisible for that span, and that is the point** —
   Wednesday's *Bike to work* over Stretch, Tuesday's lunch over Meditate: the block wins and the
   wash shows around it (the feathered ends peek above and below a block that does not cover the
   whole span). Nothing else should try to show through a block.
4. **[Low · judged] The check-in's fill is quiet** — 18 → 36 % with a ✓ before the name; on a busy
   day the difference is the check more than the tint. Kept: the stroke is not a scoreboard (§0.6.5's
   presence), the tick is enough.

## Dimension by dimension

- **Hierarchy** — blocks first, strokes under; the gutter, the now line and the day header untouched. The Week reads as before with a softer layer beneath it.
- **Colour** — the habit hue only (the fanned hue of the register); the wash at 18 / 36 %; the name in the hue, `text` once done. No new token: the shares are constants beside `LayerColours`.
- **Type** — the name in `eyebrow` (11 · 500 · +0.06 em uppercase) — the one place the app writes a name in small caps; the stroke's start shows the time (*STRETCH · 07:30*) until the check-in, then the check.
- **Layout** — the stroke is the full lane minus the hairline; 6 dp feathers at each end (4 on a short stroke); the name inset 8 dp.
- **Consistency** — the Layers menu keeps *Habits* and governs the strokes; the phone's Day and Week keep the same painter at Touch; the all-day row never shows a habit (no time → not on the grid).
- **Month** — no habit, no dot; the desktop grid and the phone's dots keep task · event · database date.

## What's working

- A habit reads as time kept, not time booked; a task dropped onto the span is allowed and covers it.
- One rule for the span (the habit's own numbers, 30 when it has none) keeps the grid honest.

## Cannot verify

- The feathered edge at the app's density (a gradient of 6 dp on a 45 dp hour); the real grab decides whether 6 or 4.
- The Day view under Touch (the phone's Plan view is 56 dp an hour, so the 20 min stroke is 18.7 px there — the minimum rule still applies).

## What the mock says about the code

`domain/plan/HabitStrokes.kt` (pure, tested): `habitStrokes(habits, day, defaultMinutes = 30)` → `(habit, startMinute, minutes)` for habits due that day with a time; `strokeHeightDp(minutes, hourDp)` with the 18 dp floor. `WeekGridView` and `PlanView` paint the strokes before the blocks (`drawStroke` in `ui/calendar/`) — **one state**: the hue at 12 % (`HABIT_STROKE_SHARE`), feathered 6 dp, the name in `caption` at Regular in the hue, no time; a 5 dp dot before the name when `lastCompletedDate == today` **and the lane is today** — and `timelineBlocks` stops receiving habits; `weekDropAt` ignores strokes; a click on bare stroke → `HabitDetailSheet`. `MonthGrid` and the phone's `monthDots` drop the habit kind; the Agenda and the Day's untimed list keep their rows. §3.2 amended; the Layers menu unchanged.

## Addendum — softened (2026-09-21, the same day)

*The user, on the first mock: a softer look, less "something owed" like a task, closer to the non-judgemental register (§0.6.6: presence, never absence; offer, don't demand). Four readings drawn in `docs/mockups/habit-strokes-soft.html` — two lanes each (Tuesday unchecked, Monday checked in) on both grounds — and decided in one batch.*

What read as owed in the first mock, each on its own axis: the **label** — small caps with a time (*STRETCH · 07:30*) is an appointment's voice; the **two states** — 18 % before the check-in, 36 % and a ✓ after — make the pale one *not yet*, the absence §0.6.6 refuses; the **shape** — a full-lane rectangle is a block's even feathered.

| axis | offered | decided |
|---|---|---|
| shape | V1 the quiet wash (full lane, feathered ends) · V2 the cloud (feathered on every side) · V3 a hairline hatch (drafting's *reserved area*; Outlook's *tentative* [Assumed]) | **V1** — a stroke across the span, the softest ground under the blocks |
| the check-in on the grid | nothing · a 5 dp dot before the name, today only · the hatch settling / the wash filling | **the dot, today only** — presence on today's stroke; a past day never shows one, so a missing dot is never an absence |
| the name | mixed case Regular, no time · italic · small caps without the time | **mixed case, Regular, no time** — the grid already says when |
| the share | 12 % · 8 % · 18 % | **12 %** — 1.22 / 1.19 : 1 against the ground, visible on both |

**[Measured]** The name cannot be in `dim`: 4.40 : 1 on a 12 % wash (Ink), 4.21 (Chalk), 4.47 at 8 % on Chalk — under AA on every share. In the habit hue it clears **5.48 / 5.55** (12 %), 5.23 / 5.40 (14 %); so the name stays in the hue in every variant and the softening is the weight and the case, not the colour. Priority 2's numbers become 5.5 : 1 on both grounds and the wash 1.22 / 1.19 against the ground (still under the 3 : 1 non-text floor by design). Priority 4 is void: there is no second tint.

Not taken, recorded: the hatch (a real convention for *not firm*, but busier than a wash, and a tentative appointment is still an appointment); strokes drawn only on today and the days ahead (removes every absence reading but leaves Monday to Wednesday bare on a Thursday — reads as a defect before it reads as a rule).
