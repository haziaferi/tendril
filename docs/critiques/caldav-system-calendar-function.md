# CalDAV through the system calendar (§9.12): the walk, 2026-09-29

§9.12 walked on the phone (a OnePlus 6T on Android 11, serial `8ad59cbb`, the debug build from
`caldav-system-calendar`). The calendar was DAVx5's copy of a Google CalDAV calendar that the person
named for the walk (calendar id 9). Tendril was driven through `uiautomator` dumps. Both sides were
read with `adb shell content query` against calendars 9 and 10 only (10 is Tendril's LOCAL
calendar), and Tendril's database was read from a pulled copy. Server-side steps (DAVx5's sync, the
web edit, the checks on calendar.google.com) were the person's. Account names that appear in the
picker are not recorded here.

| # | Tried | Observed | Outcome |
|---|---|---|---|
| 1 | Calendar settings: the picker | Calendars grouped by account under their sync app's name ("Google", "DAVx5"), read-only ones marked; Tendril's LOCAL calendar not offered | Pass |
| 2 | Tick the DAVx5 calendar (empty) | The pass ran, "Up to date", nothing written | Pass |
| 3 | Quick Add `Walktest one tmr 10-11am` | Created *This phone only*: the provider holds it in calendar 10 at 10:00 CEST | Pass |
| 4 | Its sheet: *Calendar* → the DAVx5 calendar, Save | Provider: the event in calendar 9 with `uid2445` = the entry's uid and `dirty=1`; the calendar-10 copy gone | Pass |
| 5 | Quick Add `Walktest series every thursday 9-10am` | Went straight to calendar 9, the remembered choice: `FREQ=WEEKLY;BYDAY=TH`, `duration=PT3600S`, no `dtend` | Pass |
| 6 | Plan mode on 8 Oct: drag the block, **This one** | Provider: an exception with `original_id` = the series, `originalInstanceTime` = 8 Oct 09:00 CEST, no `dtend` written. It landed on 11:15 because the drag was imprecise | Pass |
| 7 | 15 s later | The same three rows, `dirty` unchanged: Tendril's own writes wake the observer and the next pass writes nothing | Pass |
| 8 | DAVx5 sync (the person) | All three rows `dirty=0`, so they were uploaded. The rename in 9 shows the event reached the server | Pass (upload) |
| 9 | Rename to *Walktest web* on the web, DAVx5 sync | Tendril's Day view shows *Walktest web* | Pass |
| 10 | The same sync, 8 Oct | **Tendril showed the moved occurrence at 09:15.** DAVx5 re-created the exception row (778 → 779) stamped `eventTimezone=UTC`; `rowsToItems` read timed rows in the stamped zone | **Defect.** Fixed test-first (`SystemCalendarRowsTest` +1, red first): timed rows read in the phone's zone, all-day rows in UTC |
| 11 | Install the fix over the app, launch | Tendril's exception 11:15–12:15, its link re-fingerprinted; every provider row still `dirty=0`, so nothing was written back | Pass: healed by the next pass |
| 12 | *Walktest web*: sheet → Delete → Delete ("Restorable from Trash") | Provider row `deleted=1, dirty=1`, which DAVx5 turns into a server delete | Pass |
| 13 | DAVx5 sync (the person), then calendar.google.com | Gone from the web (the person); the provider row purged; Tendril's entry in Trash and its link removed. The series untouched: 8 Oct still 11:15, the exception keeping its `_id` this time | Pass |
| 14 | The series deleted on calendar.google.com (the person), DAVx5 sync | Calendar 9 empty; Tendril's series and its moved occurrence in Trash with one `deletedAt`, both links removed | Pass: a server delete is a move to Trash, never a hard delete |

**Not walked:** restore from Trash (it should re-insert), a conflict (edited in both places), un-ticking and re-ticking (relink without
writing), and the phone's own Google-account sync instead of DAVx5 (§9.12's *Unverified*). Each
is a unit test row of `CalendarReconcileTest`/`CalendarReadBackTest`, and none has been run
against a server.
