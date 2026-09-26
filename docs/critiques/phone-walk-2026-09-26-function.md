# Phone walk, 2026-09-26 — the audit's device-bound rows

The walk settled audit rows 5.4 and 5.6 of `docs/audit-2026-09-24.md` on the phone (serial
`1129268e`, the debug build), and found 5.11 and 5.12 along the way. It was driven through
`uiautomator` dumps. The system calendar was read with `adb shell content query` against Tendril's
own calendars only, never another account's. Every fix has its own commit on
`phone-reminders-5-1-to-5-3`.

| # | Tried | Observed | Outcome |
|---|---|---|---|
| 5.6 | A task with a deadline, opened in the Calendar's edit sheet | *When* `sab 26`, *Deadline* `2026-09-26` | Fixed: both `dayLabel` |
| 5.6 | The same sheet switched to Event, *Ends* on | *Date* `sab 26`, *Ends* `2026-09-26` | Fixed |
| 5.6 | Re-walk after the fix | *When*, *Deadline*, *Date* and *Ends* all `sab 26` | Pass |
| 5.4 | A weekly all-day event, then its first occurrence dragged Sat → Fri in the Week view, **This one** | Tendril: Fri 25, then Saturdays (Agenda: *sabato 3 ott*). Provider: one event, `dtstart` moved to Fri 25, `FREQ=WEEKLY`, so every occurrence showed on **Fridays** | The prediction ("shows twice") was wrong, and the defect was worse: the override row carried the series' `providerEventId` |
| 5.4 | Relaunch after the fix (the repair path) | Provider: the series back on Saturdays with `EXDATE=20260926T000000Z`, and the moved occurrence its own event on Fri 25 | Pass. The pre-fix row was repaired |
| 5.11 | `content query` on the calendars | **Eight** visible calendars named Tendril (ids 4–11), two still holding events | Fixed: a lost preference now replaces them with one |
| 5.11 | Relaunch after the fix | One Tendril calendar; the walk's series is correct in it | Pass |
| 5.12 | Cleanup: the series deleted from its 3 Oct occurrence ("The whole series goes") | The moved occurrence stayed on Fri 25, in Tendril and in the provider | Fixed: exceptions go and return with their series |
| 5.12 | Re-walk: restore from Trash, delete the series again | Fri 25 empty, and the provider holds no event of it | Pass |

**Recorded, not changed:** the Tasks Trash sheet shows each item's deletion date as ISO
(`Event · 2026-09-26`), 5.6's class on another sheet.

The walk's two items are in Tendril's Trash (restorable), not deleted forever.
