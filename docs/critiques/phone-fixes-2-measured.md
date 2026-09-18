# The phone's second fix PR — the audit's High and decided Meds, measured after

*2026-09-18 · the after-pass on the `phone-fixes-2` build, the OnePlus (3 px/dp), `uiautomator`
dumps as in `phone-layout-full.md` / `phone-type-full.md`. The user's decisions (all A): the Touch
type scale, one Touch row, the stacked chrome folded, Material's components on the app's row.*

## Measured — before → after

| finding | before (the audit) | after | ground |
|---|---|---|---|
| **T·P1** the Touch type scale | text bands 14 · 16 · 18 · 25 dp (caption 11 · description/label 12.5 · body 14 · pageTitle 18 sp) | **16 · 18 · 20 · 27** — caption 12.5 · description/label 14 · body 16 · heading 16 · pageTitle 20 (`touchTypography`, tested: every chrome role one step up the same scale, the editor's three untouched); provided by `WorkbenchEnvironment` where the profile is known, so sheets and menus inherit it | Todoist 17 / 14, TickTick 17 / 14, Notion 16 |
| **L·P1** one Touch row | Pages 54 · tasks 59 · habits 67 · switcher 67 · History 53 · New sheet 48 / 64 | the profile's **56 minimum** on every list row (`heightIn(min = rowHeightDp)`, 4 dp of padding); measured 59 on tasks and Pages cards (the 48 dp checkbox / two lines at the new scale plus padding) | Todoist 57 |
| **L·P2** the stacked chrome | Calendar: bar + view row + layer chips + day header ≈ **200 dp**; Tasks: bar + kind row + range row ≈ **150** | Calendar: the bar carries `Day ▾` on the phone too and its `···` is a menu — *Layers ▸* (a pushed level with the dots and checks) · *Plan the day* (Day) · *Calendar settings…*; the day header stays → **≈ 100 dp**. Tasks: the desktop's pane header (L5's C1) is the phone's — *Tasks · Habits · Merged* as 40 dp tabs, `This month ▾` at its right → **≈ 100 dp** | TickTick one bar; Notion Calendar a 40 dp bar + a day strip |
| **L·P3** targets under 48 | the find bar's buttons 36 × 50; the row page's values 50 × 31; the Month's cells 49 × 42; the bar's pills and pane tabs 28 tall | the find bar 56 dp with 48 dp buttons under Touch; the strip's rows 48 dp with the value's tap padded to 48; the Month's cells 48; every bar control and pane tab **40 dp** under Touch (`barControlHeight`) | Material's 48 |
| **T·P2** Material's bands | menu rows at Material's 15 / 17 dp text beside the app's | `TendrilMenuItem` is the app's row on both profiles — `body` text (16 under Touch), 12 dp sides, the 48 dp target kept (Notion's 45) | Notion 45 |
| **F·P1** the strip's values | read as dead 31 dp targets | the tap **did** open the cell's editor (a picker, a menu) — the finding was the target, fixed under L·P3; the verb was there | — |
| **F·P4** the edit sheet's ISO *When* | *2026-09-25* | *ven 25* (`dayLabel`); the Add sheet's *Date:* and *Deadline:* the same | the tray, the rows |

## Walked
Calendar → `Day ▾` → the four views; `···` → *Layers ▸* → the pushed level, *Habits* toggled on and off with the menu open, ← back; *Plan the day* on the Day; *Calendar settings…* opens the sheet. Tasks → the header's tabs switch the kind, `This month ▾` the range; the rows at 16 sp with *sab 12 · due ven 11 · 0/1 steps*. Pages → the cards at the new scale; a page's `···` menu rows at `body` 16.

## Recorded, not changed
- T·P3 `pageTitle` at two line heights (the sheet's title vs the bar's) — Low, open.
- The Postpone sheet's unit chips wrapping; the Timeline's English initials; the Day header's Italian order (P10) — Low, open.
- The Tasks rows at 59 against Todoist's 57: the difference is the 48 dp checkbox target's air; left.

## Cannot verify
- The phone's dark mode and landscape; the widgets; the desktop's slide-over at the Touch scale (a desktop never runs Touch).

## Disposition
Ship. §0.10 item 23's High and decided Meds closed; the Lows stay listed.
