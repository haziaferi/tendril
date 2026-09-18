# The phone audit — layout, every surface

*2026-09-18 · `design-critique-plus` pass 1 of 3 over every phone surface of `main` (#108) on
the OnePlus 9 Pro (1080 × 2412 at 480 dpi, 3 px/dp; Ink, System → light, Inter, the Touch
profile). **Measured** from `uiautomator` dumps — every number is a node's bounds — beside the
apps installed on the same phone for this pass: **TickTick, Todoist, Notion, Notion Calendar**
dumped on their lists, add surfaces, menus and details; **Obsidian** is a web view (no nodes; an
empty vault) and gave only its frame from a screencap. **Judged** from screencaps. The two
voices are kept apart. Read-only; nothing fixed here — High goes to the phone's second fix PR.*

## The grounds (dp)

| | TickTick | Todoist | Notion | Notion Calendar | Obsidian |
|---|---|---|---|---|---|
| top bar | 56 (a ☰, the title 27 sp, ⋮) | 56 | a 40 dp row of round buttons | 40 | 48 dp round buttons over the content |
| list row | **46** one-line (17 sp title; the date 14 sp at the right) | **57** two-line (17 sp / 14 sp; a 28 dp check) | **48** (16 sp) · table rows 45 | — | — |
| menu / sheet rows | — (long-press selects) | detail sheet rows **50**; verb chips 36 | the `···` sheet's rows **45**, grouped, icons leading, red for the destructive | — | — |
| add surface | a bottom sheet: the title line 22 dp, an icon row of 24 dp glyphs, a send button | a bottom sheet: the title line 27, chips 36, Add 48; the parsed run tinted inline | — | — | — |
| calendar | — | — | — | hour **100**, gutter 52, all-day row 32, a 3-day grid | — |
| bottom chrome | a tab bar | a 4-tab bar + a FAB | a search · AI · compose pill | a sheet handle | a 50 dp pill toolbar |

## Measured — Tendril

| surface | measured | against the ground | reads |
|---|---|---|---|
| the top bar | 52 dp (the bar's rule), title 18 sp | TickTick 56 / Notion 40 | in the family |
| **row pitches** | Pages cards **54** · task rows **59** · habit rows **67** · the switcher's **67** · the reminder list **56** · History **53** · the New sheet **48 / 64** · the Week strip's day rows 66–68 (92 with items) · the Table's rows 71 | Todoist 57 · TickTick 46 · Notion 48 / 45 | **six different pitches for "a row" under Touch** where the profile names one (56) — the checkbox's 48 dp target and per-site padding decide it, not the profile |
| the label chip row | 50 dp chips (the 48 dp target); the row's air 4 + 8 (the P1 clamp) | — | fine |
| the find bar | 50 dp; its three buttons **36 × 50** targets | — | under 48 wide |
| the row page's property strip | values *—* as **50 × 31** targets; labels at x 17, values at x 37 | Notion's property rows 45 with the value on its own line | targets under 48; the column unaligned |
| the Month grid | cells 49 × **42** | — | 42 dp tall targets (Low) |
| the Calendar's chrome | 52 bar + 50 view row + 50 layer chips + 50 day header = **≈ 200 dp before content** (25 % of an 800 dp screen) | TickTick one bar + a week strip; Notion Calendar a 40 dp bar + a 3-day strip | the desktop's L5 finding, on the phone |
| the Tasks tab's chrome | 52 + 50 (kind) + 50 (range) + the Someday line 59 = **≈ 210 dp** | TickTick one bar | the same family |
| sheets | the frame's 20 dp sides, 24 dp bottom room; rows inside at Material's 48 | Notion's rows 45, Todoist's 50 | in the family |
| menus | 48 dp rows (Material's under Touch) | Notion 45 | in the family |
| the canvas card | 180 × 90 dp card, 36 dp badges | — | — |
| the tray strip | 40 dp chips | — | — |
| the Timeline | 36 dp day columns, 14 sp initials | — | works; cramped, one bar clipped at the left edge |

## Judged

1. **[Med]** A phone screen spends a quarter of its height on chrome before the Calendar's first
   row and the Tasks' first task — two segmented rows and a chip row stacked under the bar. The
   desktop folded the same rows into one bar (L5); the phone's answer is a `···`-menu for the
   layers and the range (TickTick keeps one bar and puts the view under a menu), or a second row at
   most. **Decision needed** (below).
2. **[Med]** Six row pitches under Touch (53–71) where the desktop has one per profile. The
   grounds agree on two: ≈ 46–48 one-line, ≈ 57 two-line. The 48 dp checkbox sets the phone's floor
   at 59 for a task row; a 40 dp check (TickTick's is 24, Todoist's 28 on a 48 target) brings it to
   ≈ 52. **Decision needed.**
3. **[Med]** Targets under 48 dp: the find bar's three buttons (36 wide), the row page's property
   values (31 tall), the Month's cells (42 tall). Material's minimum is the phone's rule everywhere
   else in the app.
4. **[Low]** The row page's property strip reads as a loose list — labels and values in one
   column with the value 20 dp in; Notion's mobile strip gives each property a 45 dp row with the
   value right-aligned. Cosmetic until the values are editable there.
5. **[Low]** The Postpone sheet's custom-unit chips wrap to two rows beside the count field (the
   desktop's #3 in small things III, the same on the phone).
6. **[Low]** The Day view's quick-add preview chips start at the screen's edge (P8, recorded).

## What's working

- One frame for every sheet (drag handle, title, 20 dp sides), one menu, one field at 48 dp — the
  Material-frame PRs hold on the phone as on the desktop.
- The Month grid's dots and header, the tray strip's drag, the tinted quick-add line — each in the
  family of its ground.

## Cannot verify

- The phone's dark mode (light at the walk); landscape; a tablet width (the rail at 840 dp).
- Obsidian's rows (an empty vault, a web view).

## Decisions needed (with a recommendation)

1. **The Calendar's and Tasks' stacked rows** — A (recommended): the view and range pickers into
   the bar as menus (`Week ▾`, `This month ▾`) and the layers into the Calendar's `···`, as the
   desktop did in L5; B: keep the rows, drop the Someday line into the range menu.
2. **One Touch row** — A (recommended): the profile's 56 for every list row, the checkbox at 40 dp
   inside a 48 target (Material allows a smaller visual in a full target); B: leave the pitches to
   their content.
3. **The find bar's buttons and the property values** — A (recommended): 48 dp targets (the bar's
   buttons at 48 × 50, the values as 48 dp rows); no B.
