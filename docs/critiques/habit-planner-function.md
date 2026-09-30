# Habit planner in the Habits tab — function walks (plan Phase 5)

One section per slice (T1). Phone: OnePlus 6T, `uiautomator` dumps. Desktop: the dev build
(`gradlew run`, never the installed `Tendril.exe`), native window captures. The data on both is
test data; five calendar habits (`Walk5a_…`) were written into each database directly for 5a,
because the sheet that creates them is slice 5c.

## 5a — the Day view by time, with check-ins (2026-09-30)

| Tried | Observed |
|---|---|
| Habits → *Day* (the default) on the phone | The header *‹ Wednesday 30 September ›*, *2 done today*; the five blocks in order, each a hue mark, its name and range; *Midday 09:00–13:00 · now* at 09:13 |
| Where each entry sits | Water ×3 (slots 07:30, Evening, 20:30) in Morning's set times, Evening's set times and Evening's list; the interval habit Stretch at 09:00 in **Midday** (a block starts at its start); Gym (Mon, Wed) in Afternoon; Journal in Night; Plants and the counting Water (its + disc) under *Any time today*; Call home at 05:30 with the note *…is before Morning starts (06:30), so it is in no block…* |
| Check Water's 07:30 occurrence | That row alone ticked; *3 done today*; one `habit_completions` row, `occurrenceKey` *1* |
| › to Thursday | *Thursday 1 October*, a *Today* button; the two interval habits in italics with *expected* (T3); no box can be checked |
| ‹ ‹ to Tuesday | *Tuesday 29 September*; every check box disabled (read `enabled="false"` from the dump); the interval habit not checked that day absent (presence, never a gap) |
| *Today* | Back to Wednesday, every box enabled |
| The desktop, Ctrl+3 → Habits | The same day at the pointer's row height; *Day ▾* in the pane header; the hue marks beside the names only (R2) |
| Click Stretch | Selected, the detail pane shows it — as before 5a |
| **Found:** Call home 05:30 directly under *Any time today* | It read as an *Any time* entry. **Fixed:** outside entries have their own heading, *Outside the blocks* |
| **Found, for 5c:** the pane and the sheet describe a calendar habit by its unread interval (*Repeats Every day*) | The calendar schedule reaches the sheet in 5c |

Not in 5a, by T1: tap-to-select with the docked toolbar (5b; a tap still opens the sheet or the pane);
the desktop's ↑ ↓ ↵ cursor on the Day view (5d; the Month list keeps it).

## 5b — edit mode and its prompts (2026-09-30)

| Tried | Observed |
|---|---|
| Tap Gym (Afternoon) on the phone | Selected; the toolbar docks at the foot — *Up · Down · Move to… · Edit · More… · Done*; the *+* button hidden while it is there |
| Up | Gym drawn at the end of Midday's entries (a block with no rest of its own: the end of the block above), nowhere else |
| Done | *Apply this change to…* with five choices — *This day* hidden, the same entry for a habit once a day (D10) |
| *Every week from now on* → Apply | Gym in Midday; one edit stored: `FROM:2026-09-30`, `{"block":"block-midday","order":10.0}` |
| Journal (Night): Up, Up, Done | Its place was the top of Evening's rest — no time asked, only the scope; **Put it back**: Journal in Night, nothing written |
| Journal: Up ×3, Done | Among Evening's set times: *Moving Walk5a_Journal among set times*, *No time is suggested. Evening runs 18:00–21:30…* |
| Type 25:00, Set time | *Write the time as HH:MM, for example 14:30.* |
| Type 05:00, Set time | *05:00 falls outside every block. Choose a time bet…* (the dump cuts the line there) |
| Type 19:15, Set time → *Only this entry* | Journal at 19:15 in Evening today, before Water 20:30; tomorrow in Night as before |
| Stretch (an interval habit, 09:00): Down, Done | *Moving MigrationCheck_Stretch out of set times … Remove 09:00*; → Remove: no scope asked; the habit row itself now `time` null, `block-midday`, order 10; Gym, its new neighbour, renumbered by an edit from today |
| Plants: More… | *Skip this one…* and *Move to Trash* |
| *Skip this one…* → *Only this entry* | Plants gone from today |
| Water 20:30: Move to… | *Move Walk5a_Water*: the week's days from today (*Wed 30 … Sun 4*) and the five blocks |
| Thu 1, Night → Apply | *Moving Walk5a_Water out of set times … Remove 20:30* (20:30 is not in Night); → Remove: no every-week choice (a several-a-day slot); today's 20:30 gone, tomorrow's Night holds the added entry |
| The desktop: click Gym | Selected, the detail pane shows it, the toolbar docks along the list pane's foot |
| Up, Done | The scope question as the slide-over; Gym drawn in Midday; *Put it back* → Afternoon again, the toolbar gone |

The prompts are `TendrilSheet`s — a bottom sheet on the phone, the slide-over on the desktop where
the mockup drew a dialog: the house component. Not in 5b, by T1: *Pause…* (5c), the keys (5d).

## 5c — the sheet's calendar schedule, the blocks editor, pause (2026-09-30)

| Tried | Observed |
|---|---|
| The phone's *+* | *Add habit*: *Schedule — Since last check-in · On the calendar*; *Every* as before; *Where — Any time, the five blocks, At a set time…*; *Counts something*; no *Area* (the phone has no Label); *Note · Optional*; *More options* |
| *On the calendar* | *Every day · On weekdays · Every few days · Every few weeks · Times a day · Times a week · Every few hours · Once* |
| *Times a week* | *How many*, *On any of* Mon…Sun, and *This week the app suggests Tuesday, Saturday…* — **Found:** Tuesday already past (the engine spreads over the whole week, as the planner did). **Fixed** in what the sheet shows: days from today on — *…suggests Thursday, Saturday…* |
| *Evening*, then *Add* | Stored `CALENDAR`, `TIMES_PER_WEEK:2:MON,…,SUN`, **no block** — the walk's own error: the tap landed with the keyboard over the chip. A chip's choice reads `checked` on its parent node in the dump; checked that way, a second habit took its block |
| A *Once* habit in *Night* | Stored `ONCE:2026-09-30`, `block-night`; in Night today |
| Select it, *Edit* | Its sheet: *Once · Sep 30, 2026*, *Start timer* (T4), *Pause…*, *Edit…* |
| *Pause…* | *Pause · Walk5c_Dentist*, *Paused, it leaves the plan; its check-ins stay.*, *Until I resume it* / *Until a date…*, *Don't pause* / *Pause* |
| *Pause* (until resumed) | Gone from the Day view. **Found:** the sheet still offered *Pause…* — it held the copy it opened with. **Fixed:** the sheet reads the live row, as the pane did (and the Calendar's) |
| *Month*, the habit | *Paused until you resume it*, *Resume* |
| *Resume* | *Pause…* again; back in Night |
| Header `···` → *Edit blocks* | Each block: *Name* (blank: the default's, H1), *Starts*, *Ends*, *Weekday times*, *Save* |
| Morning ends 13:00 → *Save* | *Midday ends at 13:00, so Morning can end at 12:59…* |
| Morning ends 09:30 → *Save* | Morning 06:30–09:30, Midday 09:30–13:00 · now — the next block's start moved with it |
| Name *Dawn*; *Weekday times* Sat, Sun 08:00–10:30 | Stored `name` *Dawn*, `overrides` `SAT,SUN@480-630`; listed *Sat, Sun · 08:00–10:30* with *Remove* |
| The desktop's *+* on Habits | The same sheet as the slide-over; *Area — None · book*; *On the calendar* shows the eight choices; the Day header's `···` |

## 5d — the Week view, the Day view by area, the desktop's keys (2026-09-30)

| Tried | Observed |
|---|---|
| The phone: *Week* on Habits | The week header with *Previous week* / *Next week*; the days as a list, Monday first, *Wednesday 30 · today* marked; each day's entries under its blocks |
| A *Times a week* habit (Yoga, 2), not yet confirmed | A card above the days: *Yoga, 2 times a week — suggested Thursday, Saturday.*; *Confirm* / *Choose days…*; nothing placed on any day (Q1, D5) |
| *Confirm* | One edit stored: `HABIT`, `WEEK:2026-09-28`, `{"weekDays":"THU,SAT"}`; the card gone |
| Tap Thursday | The Day view on Thursday 1, Yoga listed. *Next week* asking again is pinned by `HabitWeekTest`, not walked |
| The desktop, Habits, *Day*: ↓ ↓ Enter | The cursor ring on the second row, then Gym selected; the pane *Mon, Wed*; the toolbar's key hints under a pointer |
| Enter on it, ↑, then Esc | The entry one up, then the scope question; *Put it back* restores it — nothing written |
| `···` → *By area* | *No area* last; Water collapsed to one row: *07:30 · and 2 more today* (R3, as drawn) |
| *Week* in a wide window | Columns of 150dp — Monday 28 … Thursday 1 in the window's width, the rest scrolled — *Wednesday 30 · today* highlighted, *07:30 Stretch expected* in italics (T3), the renamed block *Dawn* beside *Morning* |
| **Found, in the build:** a week day's heading on the desktop in the group-header style | `tools/audit.py`'s type check caught it; fixed before the gate |

Not in 5d: the Week *by area* (the Day view has it; a week of areas is seven of them and the
mockup did not draw one); the desktop's Week is columns, not the mockup's full hour grid; the
Tasks tab's *Merged* view is unchanged; pop-outs.
