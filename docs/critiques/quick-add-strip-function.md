# Critique — the quick-add strip's field and the tray's clamp, on the build

*2026-09-17 · `design-critique-plus`, the after-pass for the L7 + L8 PR (§0.10 item 22). Read-only;
native grabs at the user's 967 × 1100 window (Ink dark, Compact, the shell scale 0.98), the PIL
line probe beside this evening's TickTick grabs at the same 125 %; the window driven by user32
input. The walk is the plan's verification list, each step once. **Phone: not walked** — the
Day view's inline field and the tray strip are untouched.*

## Measured — beside the grounds (device px at 125 %)

| | Tendril before | Tendril after | ground | note |
|---|---|---|---|---|
| the strip | ≈ 100 px (a 66 px Material field + a chip row) | **44 px** (the find bar's), the field **28**, the chips on the same line | TickTick's add bar 36 / 38 (≈ 30 CSS) | a wrapped chip row → **80 px** (one row, never more) |
| the popup's field | 56 dp Material | **35 px** (36 dp), the `+` inside the hairline, the chips under | — | the switcher's frame |
| the tray at the user's window | 360 dp (the remembered width) → **66 px** lanes | **263 px** = 30 % of the pane → lanes **78–79 px** (≈ 80 dp) | Todoist's and TickTick's side panes 21 %, Notion Calendar's 13 % | the plan said 82; the pane is 4 dp narrower than estimated |
| the header's second line | *Planned 30m* clipped to *Planned* (no ellipsis) | *Planned* by rule below 90 dp; with the tray collapsed (lanes **115 px** ≈ 117 dp) *Planned 30m · 1h 00m · 1h 45m · 2h 00m* | — | `plannedLabel`, tested at 89.9 / 90 |

## Walked

- **The strip** — the bar's ⊕ opens it: 44 px, the `+` glyph, the 28 dp field with *Quick add…*,
  the ×. `Dentist fri 14:30 !` → *Event · Tomorrow · 14:30 · High* at the field's right on one
  line. `Standup mon 9:00 for 30m every week by fri !!` → *Event · lun 21 set · 09:00–09:30 ·
  Weekly* on the line and *Urgent* + the × wrapped under it, the strip 80 px. Enter on the short
  line → one *Dentist* event on Fri 18 at 14:30 in the database (ids 10 and 12 there are earlier
  walks'), the field cleared to its placeholder. Esc closes it.
- **The popup** — Ctrl+Shift+Space (this profile's chord): the 36 dp field with the `+` inside
  it, *Task · Tomorrow · 14:30 · High* under it, *Enter adds · Esc closes*; Esc closed it with
  the draft typed (the typed-stays rule is for focus loss, not Esc).
- **The tray** — drawn at 263 px with the remembered 360 dp; the handle still drags the
  remembered width; collapse (the header's ‹) → the lanes 115 px and the minutes back; the
  bar's chevron re-expands it at 263.

## Findings

1. **[Low · observed]** A `!` on an *event* line previews *High* but writes importance 0 —
   the ladder is the tasks' (§0.6.4) and `quickAddEntry` carries the level for tasks only. The
   chip is honest about the parse and silent about the write. Pre-existing (the tray PR's
   popup, 14f·2's strip); recorded for the parser: the urgency chip could hide when the kind is
   EVENT. Not this PR's.
2. **[Low · observed]** The strip's kind chip (*Event*) is Material's selected `InputChip`
   without a border while the token chips carry one — the phone's chips, unchanged; on the one
   line they read as one family because the kind chip is first. Recorded.
3. **[Low · measured]** The lane at 30 % is 80 dp at this window, 2 dp under the plan's 82 —
   the pane's edge insets. Nothing rests on the exact number: the header rule reads the lane.

## Cannot verify

- The phone: the Day view's inline field is still `QuickAddField` in its Column form (48 dp
  under Touch, Material's chips under) — not run.
- The Month cell's ground click seeding the strip's date — the code path is 14f·2's, unchanged.
- The tray's clamp on a 1200 dp window (the tray at its remembered 360 there; the minutes shown)
  — the window was not resized; the arithmetic is the test's.

## Disposition

No High. #1–#3 recorded. Merge.
