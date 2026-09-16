# Critique — the urgency ladder, on the 14g·3 build

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14g·3. Read-only;
observable-level findings. **Desktop** at the remembered frame, Windows in dark mode, on the real
desktop database migrated v19 → v20 in place with one task flagged first: Tasks → the stripe; the
pane's *Urgency* row and its popup picker; the row's right-click menu and its level list; *Set
deadline…* to yesterday; Ctrl+Shift+N with `Dentist fri !!`; Calendar Week (timed blocks and the
all-day chip), then the week before; Settings → the switch off and on, Light and back to System.
The database read before and after (`PRAGMA user_version`, the columns, `time_logs` joining).
**Phone: not walked** — testing paused.*

## Top priorities

1. **[High · seen, fixed before merge]** The user, mid-walk: *"the calendar entries' text is too
   close to the left and the colour bar to the side overlaps."* The stripe was drawn behind the
   block and the title kept its 4–6 dp start padding, so the two met. **Fix:** a block or chip
   with a stripe starts its text at 12 dp — 4 dp of bar and 8 dp of air (`PlanView`,
   `WeekGridView`, the all-day chips); one without keeps 6 dp.
2. **[Med · judged]** The row's *due 2026-09-15* subtitle stays in the row's one colour after the
   deadline passes. B§13.8.1 said "overdue text stays the error colour"; the row's own rule
   (§0.5.2: a passed deadline is information, not an alarm) and B§13.8.1's *two colours per task,
   each meaning one thing* pull the other way — and the stripe is already urgent for that task.
   **Kept one colour**; the spec now says so. The Table's *Blocked* chip and the Timeline's
   dependency line keep the error family, as they mean a different thing.
3. **[Low · judged]** The row menu's *Urgency ▸* list opens as a second menu anchored to the
   item, which Compose places over the parent's upper rows rather than beside it. Readable, five
   rows, a check on the current level; a submenu that opens beside would need a custom popup.
   Recorded for the small-things list.

## Dimension by dimension

- **Colour** — Ink dark: the ladder pale → saturated with urgent the deepest step; Ink light: the
  flagged task's stripe the deep red at urgent. The picker's five discs read as a ladder in both.
- **Migration** — `user_version` 19 → 20; `entries.important` gone, `importance` present; the
  flagged task (`important = 1`) opened at *High*; ids kept — `time_logs` still join their entries.
- **Fit** — the pane's row names the level and, when the deadline set it, says so: *Urgent (the
  deadline)*. Quick add: `!!` previews as *Urgent* and lands at 4. The switch off removes every
  stripe; on restores them; the choice survives a relaunch (`show_urgency` in `prefs.properties`).
- **Consistency** — one 4 dp stripe on rows, timed blocks and all-day chips; the disc in the
  picker, the chip and the menu.

## What's working

- The migration is a rebuild and nothing noticed: the app opened on the old database as it always
  does, the flag read as high, the logs and completions still pointed at their tasks.
- The level is the greater of the two inputs in both directions: a low task with yesterday's
  deadline is urgent; an urgent task a month out stays urgent.

## Cannot verify

- **The phone** (paused): the migration on the real `tendril.db`, the stripe at the phone's row
  height, the edit sheet's picker under a keyboard, the Settings row.
- **The edit sheet's picker** on the desktop — the Calendar's `EntryEditSheet` shares the
  `UrgencyPicker` the pane's popup showed; not opened separately.
- **A v19 peer reading a v20 folder** — the record carries both fields (`EntryFieldsSyncTest`),
  no second device on a folder in this session.

## Disposition

#1 fixed. #2 decided (one colour), #3 recorded. Nothing else High; merge.
