# Small things IV — S1–S7, the build walked

*2026-09-20. The desktop at the dev window (1007 × 885, Compact, Ink dark), native grabs; the
phone (OnePlus) from `screencap` and `uiautomator` dumps. Each item once, observable-level.*

| item | site | before | after | evidence |
|---|---|---|---|---|
| **S1** a Date cell on the row page as ISO | `RowUnboundEditor`'s DATE branch | *Read on 2026-09-19* | *Read on **sab 19*** — `dayLabel(day, today)`, F·P4's rule, the locale's short weekday | *Escape test*'s strip |
| **S2** the *Insert block* and *Block actions* rows | the slash sheet's `TextButton`s; `SheetActionRow` | ≈ 47 px and ≈ 60 px rows | `TendrilMenuItem` — **25 px pitch** at the window's scale (the profile's 29 dp × 0.85), 48 dp under Touch | the slash sheet: *Paragraph* 104 → *Heading 1* 129 |
| **S3** the field's context menu | Compose Desktop's `LocalContextMenuRepresentation` default (light; the OS locale's labels) | white on Ink dark, *Taglia · Copia · Incolla · Seleziona tutto* | the register's `surface` / `onSurface` / `surfaceVariant`, **Cut · Copy · Paste · Select all** (disabled ones dimmed), *Block actions…* appended as before — `PlatformTextMenus`, an expect/actual provided by `WorkbenchEnvironment` (nothing on Android) | the menu over a block |
| **S4** the phone's dead band above the bar | every screen's `Scaffold` padded for the navigation bar the shell's bottom bar already sits on | ≈ 40 dp (132 px) that did not scroll | the shell's content consumes `WindowInsets.navigationBars` while the bar is drawn — the page's box ends at the bar's top (**262 … 2026**, the bar 2026 …); while the keyguard is bypassed (no bar) the inset stays the screen's | the dump of *Trip* |
| **S5** a Journal day's bar title | the page bar's title field | *journal/2026-09-19* | ***19 Sep 2026*** through `displayTitle` (L12's function); a day's stored name is its link, so the bar shows it as text and does not edit it | the desktop's bar; the phone's *20 Sep 2026* |
| **S6** the Today strip's habit check | `StripRow`'s plain `Checkbox` | a checkbox on *Water* (a counting habit) | `HabitCheck` — the **`+` disc** on *Water*, the checkbox kept on *Stretch* and *Meditate*; every tap adds one amount, as the Habits and Merged rows | the phone's Journal |
| **S7** (D9) Tab on an invisible `···` | the tree row's hover-revealed button | a Tab stop with nothing to ring | `focusProperties { canFocus = hovered || menuOpen }` — skipped while invisible | by construction (Tab was not walked) |

## Also seen

- The desktop's `Delete` from a script reaches Compose as the numpad's `.` unless sent as an
  extended key (`keybd_event` with the extended flag) — a tooling note; a keyboard's Delete is
  extended already.

## Recorded, not changed

*Assessed 2026-09-20 against the build — kept or struck in `tendril-spec.md` §0.10 item 24.*

- A Journal day's title cannot be renamed from the bar any more (it could before, which broke
  the day's link — `journalDayOf` parses the stored name). The tree row's *Rename* is untouched.
