# S10 — the edit-habit sheet, the build walked

*2026-09-20. No field of a habit was editable after creation — the largest gap on the kept list.
No mock: the Add sheet is the form, and it is what edits now.*

- `AddHabitDialog(initial = habit)`: the same `TendrilSheet` with every field filled — the title,
  *Every n day/week/month*, the time and its duration, *Counts something* with the unit, the
  amount a check-in adds and the day's number — titled *Edit habit*, its button *Save*.
- `Habit.edited(…)` (`data/habit/Habit.kt`, tested, 2): the fields change, the identity and the
  record stay — id, uid, the streak and its dates; a blank title keeps the old; a time removed
  takes the duration with it; a habit that stops counting drops its unit and amounts.
- `TasksHabitsViewModel.updateHabit` writes it and re-arms the alarm from the row (§9.7).
- The way in: the habit row's `···` → *Edit…* (beside *Open* and *Move to Trash*); the desktop
  pane's *Edit…* chip beside *Start timer*; the phone's sheet gets an *Edit…* button at its foot
  (hidden under View-Only).

| device | tried | observed |
|---|---|---|
| desktop | *Stretch* → the pane's *Edit…* → *Has time* on, 10 minutes → *Save* | the row reads *Every day · 09:00 · 10m*, the pane *At 09:00 · For 10m*; the DB row: time 09:00, duration 600 s, the streak untouched; put back to no time the same way |
| phone | Habits → *Water*'s sheet → *Edit…* | *Edit habit* with *cups*, *1*, *3* and the day's sentence filled in; *Cancel · Save* at the foot |

Tests 899 → 901.
