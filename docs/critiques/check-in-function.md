# §0.10 item 4 — the check-in, the build walked

*2026-09-19 · the `check-in` build on the phone (OnePlus, `uiautomator` dumps, the DB pulled) and
the desktop (the user's 967 × 931 window, native grabs). The decisions, in two rounds: the home
is the Journal's day page above the Today strip, past days too, never future; **mood and energy,
five steps each, either alone — as words, no emoji** (the second round's amendment to mock A);
the month as a sheet from the row's title; schema v22 with `check_ins.json` in the sync folder.
The mock and its critique: `docs/mockups/check-in.html`, `check-in-options.html`,
`docs/critiques/check-in-mock.md`.*

## What changed from the mock, and why

- **Words, not faces.** The user's call on the second round. Each scale is a row of five pills in
  the bar's frame (`BarPillButton`'s: 28 dp under a pointer, 40 under Touch inside a 48 dp
  target, radius 6, the `label` style); the latest answer fills with its hue — the mood's own
  (`Mood.hex` through `labelColours`), the accent for energy. The critique's judged finding
  ("the bars alone would not read as energy") is answered by the words themselves:
  *Drained · Low · Steady · High · Full* need no legend. Its binding note #1 (the energy glyph's
  first step) is moot.
- **Two lines on both platforms.** The mock's desktop frame packed both scales on one line; the
  build keeps one line per scale everywhere so a wrap can never mix them. On the phone each
  scale fits its line at 360 dp (mood 922 px of 1004 available, energy 966); on the desktop the
  second line costs 32 dp of a pane that has it.

## Walked — phone

| step | observed |
|---|---|
| launch on the v21 database | `user_version` 22; `check_ins` with its seven columns and two indices; nothing else touched |
| Pages → Journal → *Today's journal* | *How are you? ›* at `label` above the chips; two rows of five pills, 48 dp targets (`[38,556][217,706]` …), each row on one line; *Today* and the strip under, unchanged |
| *Good*, then *High* | both pills filled (teal, the accent); the line *Good at 14:33 · energy high at 14:33* in `description`; the DB: two rows, `mood 4` and `energy 4`, dated the page's day (epoch day 20715), stamped at the tap |
| the title → the sheet | *How you've been* · ‹ *settembre 2026* › (› disabled at the current month) · the month at 32 dp cells: the 19th a teal disc with its numeral in the on-hue, every other day plain; then *Energy high* (a ring) and *Good* (a teal dot), each with *sab 19 · 14:33* |
| *Good* again | the pill clears, the line reads *energy high at 14:33*; the DB: the mood row tombstoned (`deletedAt` set), the energy row standing |

## Walked — desktop

Ctrl+T → today's Journal in the workspace pane: the row under *Add label*, the pills at the
pointer's 28 dp (measured 29 px with the hairline, the bar's own), two lines; *Good* + *High* →
the fills and the line; the title → the slide-over with the month and the two rows; Esc closes.
The desktop's database read `user_version 22` on launch with the two rows.

## Recorded, not changed

- The desktop's page bar shows the Journal day's raw title (*journal/2026-09-19*) where the tree,
  the shelf and the switcher show *19 Sep 2026* (L12's list did not include the page bar) — one
  line for the small-things list, not this PR's.
- A past Journal day was not walked on either device (the date picker's flow); `checkInOffered`
  is tested, and the row composes from `journalDay`, not from the strip's today-only flow.
- The mood hues at the phone's Ink dark: the `Good` disc read `(39,116,99)` — the register's
  solve of `#2E8A76` on the dark ground.

## Cannot verify

- Sync of `check_ins.json` between the two devices (they share no folder) — `CheckInSyncTest`
  covers insert, tombstone-wins and a held unreadable record.

## Disposition

Ship. §0.10 item 4 resolved: a one-tap check-in on the Journal's day, in words, with a month of
colours and none of Daylio's statistics.
