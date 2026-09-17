# Critique — the Calendar's chrome and the borderless window, on the mock

*2026-09-17 · `design-critique-plus`, the pre-build pass over `docs/mockups/calendar-chrome.html`
(L5 of the desktop audit — §0.10 item 22 — widened by the user's brief to every tab's header and
to a borderless window). `craftkit ui` on the file, `craftkit render` at 1300 × 5400, the contrast
pairs computed from the registers' own hexes; the grounds measured natively this morning at 125 %.
Read-only; the two measured findings were applied to the mock before it was committed.*

## Grounding — measured today (device px at 125 %)

| app | what | measured | what it settles |
|---|---|---|---|
| **Notion Calendar** (signed in, maximized 1920 × 1040, dark, Italian) | the toolbar **is** the title bar | **46 px** tall (buttons 30 px at 8–37); Windows' — ☐ ✕ overlaid at its right end, centred on the same line | the bar-as-title-bar pattern; the caption buttons' home |
| | chrome above the grid | title + day header end at **106 px**; all-day row **24 px** (grows); first hour line at **130 px** | Tendril's grid opens at 378 px from the screen's top (355 under its title bar) |
| | the time grid | **48 px per hour**; gutter **60 px**; columns 190; the now-line red | hour 45 dp, gutter 56 dp, the all-day row at the 22 dp chip pitch |
| | the view picker | `Settimana ▾` in the toolbar's actions — Giorno *1/G* · Settimana *0/S* · Mese *M* · *Numero di giorni ▸* · *Visualizza impostazioni ▸*; *Oggi* beside it | a menu with keys, `Today` next to it |
| | the layers | the left sidebar lists every calendar with a colour dot and a toggle (249 px pane) | L2's shape |
| | the Month | a scrolling month whose rows are the viewport's height ÷ the weeks shown (one week on this window's setting) | not taken — the Month grid shipped on Notion's database calendar's grounds |
| **Notion**, **Obsidian** | borderless with the OS caption overlay; the app's first row is the drag area | seen on the earlier grabs (`audit/notion-*.png`); not re-measured today | the pattern is the norm among the installed apps |
| **Tendril today** (maximized, Ink, Compact) | OS title bar 23 px; bar **55 px** (52 dp); Calendar: segmented row to 142, chips row to ~208, the grid's own ‹ › row, day header, all-day → **first hour line at 378 px**; Tasks: two segmented rows (**≈ 190 px** of chrome); Road Map: bar + chip row (**≈ 103**); Pages, Settings: the bar alone (**55**) — the tree header shares the bar's row | the audit's L5; the user's "all like Pages'" |
| Google Calendar · Apple Calendar · Fantastical | [Assumed] Google: `‹ › Today · September 2026` and `Week ▾` in one toolbar; Apple: a segmented Day/Week/Month/Year in the toolbar; Fantastical: a calendar-set menu | C1's title row, C2's form, L1's menu |

**Feature scoring** (Reach = how many of the grounds do it · Fit = fits Tendril's constraints · Gap = how far today's build is · Value = what the person gains; 1–5):

| feature | Reach | Fit | Gap | Value | take |
|---|---|---|---|---|---|
| The bar as the window's title bar (caption overlay) | 5 (every installed app) | 4 (needs the JetBrains Runtime; falls back) | 5 | 4 (23 px back, the app reads as one surface) | **yes — the 52 dp bar, not a strip** |
| `‹ › Month · Today` in the bar's title slot | 4 (Google, Apple, Fantastical; Notion Calendar spends a row) | 5 | 4 | 4 (one row fewer than Notion Calendar) | **yes** |
| The view as a `Week ▾` menu with keys | 4 | 5 | 5 | 3 | **C1** |
| The view as a segmented control in the bar | 2 (Apple) | 2 (the bar overflows at the user's 967 px window — see #3) | 5 | 3 | C2, not taken |
| Layers as a check-menu in the bar | 2 (Fantastical; Google's are in the sidebar) | 5 (one home on every view) | 5 | 4 | **L1** |
| Layers in the tray's header | 2 (Notion Calendar, Google) | 3 (the tray exists on Week only; Day has the Plan rail) | 5 | 4 while the tray shows, 0 when it does not | L2, not taken |
| Layers inside `···` | 1 | 5 | 5 | 2 | L3, not taken |
| Hour 45 dp · gutter 56 · all-day 22 | 1 measured (Notion Calendar); Google ≈ 48 CSS px [Assumed] | 5 | 3 | 5 (19 hours on the user's window, not 11) | **yes — decided** |
| Tasks' kind as the list pane's header tabs | 3 (TickTick's list header, Things' sidebar, Todoist's page title) | 5 (the tree header's pattern on Pages) | 4 | 3 | **C1** |
| Road Map's filters as a `Filter ▾` check-menu | 3 (Obsidian's graph filters are a popover panel) | 5 | 4 | 3 | **C1** |

## Findings

1. **[Med · measured, fixed in the mock]** The day header's *Planned 1h 45m* line on today's tinted cell: `dim` on `soft` measures **3.45 : 1** on Ink (4.15 on Chalk) — the register solves `dim` against the ground and `surface2`, not against the accent's soft. The mock now draws it in `text` (7.64 : 1) — the Month chip's time-prefix rule again. **Binding on the build**: `WeekGridView`'s today cell draws its second line in `onSurface`.
2. **[Low · judged, fixed in the mock]** The hour labels sat under their lines; Notion Calendar centres them on the line and hides the first (its *CEST* takes the header's gutter). The mock now does the same; the build's `PlanView`/`WeekGridView` gutter is checked on the after-pass.
3. **[Med · measured by arithmetic]** C2's Calendar bar at 1200 dp: title 420 + segmented 300 + actions 170 + caption 138 = **1028 of the 1116 dp** left of the rail — 88 dp of slack; at the user's 967 px window (≈ 910 dp) it **overflows by ~30 dp** before the caption inset is even applied. C1's bar spends 420 + 230 + 138 = 788. A segmented control in the title bar is Apple's, and Apple's toolbar has no caption buttons in it. C2 is not recommended.
4. **[Low · judged]** L3 makes the layers a setting: a person who cannot see their habits has to think of `···`. L1 keeps them one click away on every view, and the menu stays open while toggling — Fantastical's behaviour. L2 is Notion Calendar's and Google's, but Tendril's tray is a *task* pane that the Day view does not show; carrying the toggles on it would make the pane the layers' home on Week and nowhere else, or force the tray onto every view. **L1 recommended.**
5. **[Low · judged]** C3 (`Tasks ▾` as the title) hides the kind switch in a menu that today is three visible segments; the tree header on Pages shows what the pane holds without a click, and C1's tabs do the same. C3's one saved row is the list pane's header, which the Pages tab already spends. **C1 recommended.**
6. **[Low · measured]** `craftkit ui`: 0 errors; the warnings (37 colours, 12 spacing values, 6 radii) are the document's — two registers' tokens in one file, as every mock here — not the shell's, which uses radius 6 (controls), 8 (the window, Windows 11's), 4 (chips). The caption glyphs in `text` measure 11.86 : 1 (Ink) and 11.72 (Chalk) on the ground.

## What works

- The window reads as one surface: the mark over the rail, the bar across, the caption buttons on the same line — the grid's first hour line at **126 dp** from the window's top edge against today's 355 px under the OS title bar.
- The tray's 52 dp header, the list pane's header (C1) and the tree header are the same row on every tab that has a pane — Pages' rule, kept.

## Cannot verify

- The JetBrains Runtime's caption inset at 125 % (read at runtime through `CustomTitleBar.rightInset`); the mock assumes 3 × 46 dp.
- Google Calendar's, Apple Calendar's and Fantastical's toolbars — cited from knowledge, not installed.
- Whether Windows 11's snap-layout flyout appears on the hover of the JBR-drawn maximize button (it does for IntelliJ, the same runtime; [Assumed] for Tendril).

## Disposition

Findings #1 and #2 applied to the mock. Recommended: **L1** (a `Layers ▾` check-menu in the bar) and **C1** (menus in the bar; Tasks' kind as the list pane's header tabs; the Road Map's `Filter ▾`). The two decided items (the JBR title bar as the 52 dp bar; Notion Calendar's grid proportions) are drawn in frame A.
