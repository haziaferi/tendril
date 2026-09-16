# Critique — the tray, on the build

*2026-09-16/17 · `design-critique-plus`, the post-build walk for B§13.6 #7 (the notification-area
icon, desktop reminder toasts, the global quick-add chord and its popup, the desktop bell, §0.10
item 21). Read-only; observable-level findings, measured where a number exists. **Desktop**, Ink
dark, the user's 967 × 1039 window beside Notion's Preferences (Windows at 125 %, Compact 0.85).
Walked: the chord from Notion in front, the popup's three states and its focus rules, the
Calendar's Week and the Tasks list after each add, the bell → the shared Reminders slide-over,
two firings by the clock, Settings › Notification area, the switch on and off with the ×, the
F1 card, the Calendar settings sheet. **Phone: not walked** (testing paused). Mid-walk the user
added a second brief — *the vertical space between rows is still too large; measure against the
Notion window open* — taken into this PR and measured below.*

## Top priorities

1. **[High · seen, fixed before merge]** The planned default chord **Win+Alt+T** registered as
   *taken by another app* on this machine — probed from PowerShell: `RegisterHotKey` fails with
   1409 for Win+Alt+T, Ctrl+Alt+Space and Win+Shift+T; Windows' Game Bar holds Win+Alt+T (its
   recording timer, with Win+Alt+R/G/B/M/W). **Fix:** the default is **Win+Alt+N**, Win+Alt+Q
   (Todoist's) is offered second; the failure line worked exactly as designed while the wrong
   default stood, which is the one good thing about finding it.
2. **[High · measured, fixed before merge]** *The vertical space between rows* (the user, on
   the Tasks list beside Notion): a task row measured **65 px** pitch (title 12–14 px band, a
   date band 16 px under it, then 40 px of nothing) against Notion's settings-sidebar rows at
   **30 px** and the tree's 33 — the `Checkbox`'s and `IconButton`'s 48/40 dp minimums were the
   floor, not the type. **Decided with the user:** task and habit rows are **one line under a
   pointer** — the meta (date · time · due · steps · logged) as a `caption` at the title's right
   (Things' date tag, Notion's list view), the row at the profile's height, the buttons at the
   find bar's 28 dp with 18 dp glyphs, the list's interactive minimum 28 (14h·2's rule for the
   Table); every one-line row moves to Notion's measure: `DensityProfile.rowHeightDp` Compact
   36 → **29**, Comfortable 44 → 36, the tree 32 → **29**, the tray chip the profile's row with a
   1 dp gap. Under Touch the rows keep their second line and Material's 48. **Measured after,
   the same grabs:** task rows **30 px** (from 65), the tree **30** (from 33), the tray chips 34
   → ≈ 32, Notion's 30. The Table's rows stay at **50 px** — a cell (the Date's `TextButton`,
   the Blocked chip) holds them at 40 dp; recorded for the audit's layout pass, not changed here.
3. **[Med · seen, fixed before merge]** Two *Dentist* events after one Enter in the popup (the
   first typed line, pasted by the tool + Return). Not reproduced by plain typing (*Dup*, *Toast
   test*, *Focus test* — one each) nor by the Calendar's strip, which shares the field; guarded
   anyway: **one write per open** — a second Enter during the *Added* flash is ignored. The
   duplicate was deleted from the Week.
4. **[Med · judged, fixed before merge]** A line with no kind token became an **Event** in the
   popup (the strip's default — the Calendar's context). A chord pressed anywhere is Todoist's
   and Things' *add a task*; **the popup's default is TASK** (`QuickAddField.defaultKind`).
5. **[Low · seen]** The very first open after launch dropped the typed line once (*Toast test
   23:20* never arrived; the same chord + type 0.6 s later, and every open since, took it). Not
   reproduced in three further tries, including one immediately after a relaunch; recorded.

## Measured, beside Notion (device px)

| | before | after | Notion |
|---|---|---|---|
| a task row (one line now) | 65 | **30** | 30 (settings sidebar) · 66 (a two-line Preferences row) |
| the tree's rows | 33 | **30** | 30 |
| the tray's chips | 34 | ≈ 32 | — |
| the Table's rows | 50 | 50 (recorded) | — |

## Walked

- **The chord** — Ctrl+Shift+Space (while the default was being fixed) from Notion in front: the
  popup at the screen's top third, the accent's `+`, the placeholder, the two-verb foot; the
  same with the main window **hidden** (× with the switch on: the process stays, the window
  gone, the popup still answers). A failed chord shows the failure line and the picker's other
  chips re-register live (`quick_add_chord` in `prefs.properties`).
- **The popup** — `Dentist fri 14:30 !!` → the chips *ven 18 set · 14:30 · Urgent* → Enter →
  *✓ Added · Dentist · ven 18 set 14:30* → gone; the Week shows it Friday at 14:30 with the
  stripe. Esc closes; a click elsewhere with the line blank closes; with *draft* typed the
  popup stays, always on top, and Esc after a click on it closes. The locale's day names
  (Italian) are the system's, as every other date in the app.
- **The bell** — on the Tasks rows and the Calendar's blocks: the shared Reminders slide-over
  (its preset row scrolls horizontally past the panel's width; *Custom* is off-screen until
  scrolled — the Android sheet's own behaviour, now visible on a 440 dp panel; recorded);
  *1 hour before* on a task at 00:21 → the scheduler's log at **23:21:00**: `toast In 1 h ·
  Reminder test · 00:21`; a task at 23:23 → **23:23:00** `Due now · Toast test · Today 23:23`;
  another at 23:26 the same. **The Windows toast itself did not show on screen**: this
  machine's notification centre is in **Do not disturb** (the bell-with-zZ at the taskbar's
  corner in the native grab) — the toasts are queued in the notification centre, where the user
  can read them; the walk cannot turn DND off. The firings, their titles and their times are
  the log's.
- **Settings › Notification area** — the switch (on by default), the description with the
  overflow clause, five chord chips, the failure line in the error colour when a chord is
  taken; the switch off → × quits (no `java` process left); the switch on → × hides.
- **F1** — *Quick add from anywhere — set in Settings · Ctrl+Shift+Space* under *Create*, the
  stored chord, not the default.
- **The Calendar's settings sheet** — the new sentence (sync Android-only; reminders as
  Windows notifications).
- **The icon** — sits behind the taskbar's `^` (as the critique's #4 said it would); the walk
  cannot open the taskbar (the shell is outside computer-use's grant), so the icon, its menu
  (*Open Tendril · Quick add… · Quit*) and *onAction* (a toast's click, a double-click) are the
  user's to check: drag the icon out of the overflow once.
- **§0.10 item 21** — the handlers are installed; no crash occurred during the walk and no
  `crash.log` exists. The clipboard reproduction was not attempted (a second process holding
  the clipboard open); recorded under *Cannot verify*.

## Cannot verify

- The Windows toast's look, its click-through to `onAction`, and the tray menu — DND and the
  taskbar's grant; the user's check.
- The uncaught-exception handler under a real fault (item 21) — installed, untested by a fault.
- A habit firing by the clock — the arithmetic is `ReminderFiringsTest`'s; not waited for.
- The phone: the shared `ReminderSheet` on Android, `onReminderRemoved` cancelling a real alarm,
  the two-line rows under Touch unchanged.

## Disposition

#1–#4 fixed; #5 recorded; the Table's row and the preset row's overflow to the audit. Merge.
