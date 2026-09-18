# §0.10 item 3 — measurable habits, the build walked

*2026-09-19 · the `measurable-habits` build on the phone (OnePlus, `uiautomator` dumps, the DB pulled)
and the desktop (the user's 967 × 931 window, native grabs). The decisions: the tap adds a fixed
amount and a manual entry sits collapsed behind *Log an amount…*; the number set for a day is a
sentence shown once; the `+` disc in the checkbox's slot; schema v21 as listed. The mock and its
critique: `docs/mockups/measurable-habits.html`, `docs/critiques/measurable-habits-mock.md`.*

## Walked — phone

| step | observed |
|---|---|
| launch on the v20 database | `user_version` 21; `habits` gained `unit`, `amountPerCheckIn`, `dailyAmount`, `habit_completions` gained `value`; *Meditate* and *Stretch* read null on all four and behave as before |
| Habits → + → *Water*, *Counts something* on: unit *cups*, each check-in 1, a day 3 → Add | the row *Water* with the `+` disc (48 dp target, 24 dp disc) in the checkbox's slot, titles aligned with the plain rows |
| the disc, twice | the disc filled; the meta *2 cups today · streak 1* (the phone's Settings shows streaks); the DB: two rows with `value 1.0` on the day, `lastCompletedDate` today, `streak 1` |
| the row → the sheet | *2 cups today* · *2 cups this month, on 1 day* · *Last: sabato, 19 set* · *Streak: 1* · **3 cups a day is what you set** in the dim colour, last; the month of dots with today's dot; the chips *+ 1* · *Undo the last one* · *Log an amount…* |
| *Log an amount…* → 2.5 → *Log* | *4.5 cups today*; the DB: a third row with `value 2.5` |
| *Undo the last one* | *2 cups today*; the 2.5 row tombstoned, the day still standing |

## Walked — desktop

Ctrl+3 → Habits → `+` → *Pages read*, *Counts something*: the three fields in the slide-over (the unit's placeholder reads *as you would write it after a number — cups, km, pages*); the row at 29 dp with the 28 dp disc; two taps → *20 pages today*; the pane: *Counts · pages, 10 a check-in*, the presence lines, *+ 10* · *Undo the last one* · *Log an amount…*. The desktop's database read `user_version 21` with the columns on launch.

## Copy settled on the walk
The chips carry the number alone — *+ 1*, *Undo the last one* — because the unit is the person's word as typed (*cups*) and *+ 1 cups* read wrong; the sentences carry the unit where the number fits it (*2 cups today*). The unit field's placeholder says how to write it.

## Recorded, not changed
- The Journal's Today strip and the Merged list check a counting habit with their plain checkbox: a tap still adds one amount (the use case is the same), but no `+` disc there yet — one composable to swap in each, after this ships.
- A habit's fields are not editable after creation (there is no edit-habit sheet today for any field); recorded with the habit's other fields, not new here.
- No Loop Habit Tracker ground (not installed); TickTick's desktop form of the row [Assumed] the phone's.

## Cannot verify
- Sync of the four new fields between the two devices (they share no folder); the snapshot records carry them as nullable defaults and the mappers copy them (compiled, not merged live).

## Disposition
Ship. §0.10 item 3 resolved: habits are measurable when the person says so, on presence's terms.
