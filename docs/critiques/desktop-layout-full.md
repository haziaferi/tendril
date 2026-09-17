# Critique — the desktop, full layout pass (1 of 3)

*2026-09-17 · `design-critique-plus`, pass 1 of the desktop audit (planned 2026-09-16, after the
tray merged: `main` at `57fc375`). Read-only. **Desktop**, the user's 967 × 1039 window beside
Notion (Windows at 125 %, Compact 0.85 → ≈ 1.03 device px per dp), every surface grabbed once in
**Ink dark** and the three panes, the Table, the Week, the Road Map, Settings and the F1 card again
in **Chalk light**; native `CopyFromScreen` grabs measured by pixel projection (the established PIL
band method — a text band is the run of rows that differ from the region's modal colour), 0.6-scale
screenshots for the judged half. Dimensions 1a first impression · 1b hierarchy · 1e layout and
spacing · 1f consistency · 1h interactivity. Measured and judged findings kept in their own voices.
**Phone: not walked** (testing paused). The grab set is in the session scratchpad (`audit/`, named
`<tab>-<state>-<ground>.png`), not committed.*

*One tool caveat, found on the walk and worth recording: the computer-use screenshot of a Compose
window can be **stale** — it showed Ink dark for a full minute after the register had re-solved
to Chalk light, while the native grab (and the eye) showed the light ground. Every number below is
from a native grab; the screenshots only steered the pointer. `tendril-never-open-installed-exe`'s
rule stands beside a new one: **measure natively, never from the tool's frame**.*

## Top priorities

1. **[High · measured]** **Every `DropdownMenu` row is 49 px** — the page's `···` menu, the
   database's, a task row's right-click menu, the Road Map's label menu, the New view sheet's
   type menu: item pitch 49 px (Material's 48 dp `DropdownMenuItem` minimum at 1.03) against
   Notion's 28–30 and the app's own rows at 30. A seven-item menu is 343 px tall on a 1039 px
   window. **Fix:** one shared `TendrilMenu`/`PointerMenu` that, under a pointer profile, provides
   `LocalMinimumInteractiveComponentSize = 28.dp` and `DropdownMenuItem(contentPadding =
   PaddingValues(horizontal = 12.dp, vertical = 0.dp))` inside a `heightIn(min = rowHeightDp)`
   — 30 px rows; Touch keeps 48. Every `DropdownMenu` site (9 files) goes through it, and the
   audit refuses a bare `DropdownMenuItem(` outside `ui/components/`.
2. **[High · measured]** **The Calendar's Day and Agenda rows are the phone's**: Day rows at
   **65 px** pitch (two lines — title, then `HH:mm` — and the ▶ / bell as 40 dp `IconButton`s),
   Agenda task rows 52–61 px, event rows 44; the Tasks list beside them is 30 (the tray PR's row
   brief, *one line, meta inline*). The Calendar list never got the brief. **Fix:** the same
   one-line rule under a pointer — title `body`, time as a `caption` at its right, the buttons
   `rowButtonModifier` 28 dp with 18 dp glyphs, `heightIn(min = rowHeightDp)`; Touch unchanged.
   `CalendarScreen`'s Day list and the Agenda share one row composable with `TaskRow`'s rules.
3. **[High · measured, carried from the tray PR]** **The Table's rows are 50 px** (hairlines every
   50, text bands 14): a cell's 40 dp `TextButton` (the Date) and the 32 dp `Blocked` chip plus
   padding hold the row above the profile's 29 dp. **Fix:** the Table under a pointer provides the
   same 28 dp interactive minimum around its cells, the Date cell's `TextButton` becomes a
   `body` text with the click on the cell, the chip 22 dp — rows at 30–32 px like the tree's.
4. **[High · judged]** **The Month view is the phone's dot grid**: 30 px cells with a dot under
   the number, no weekday header, no titles, the six rows taking the top quarter of the pane and
   three quarters left empty. Every desktop calendar (Notion Calendar, Fantastical, Outlook)
   fills the pane with titled cells. **Fix:** on a wide window the Month is a grid of
   `(height − chrome) / 6` cells, a weekday header row, each cell listing its occurrences as
   one-line chips (the Week's all-day chips) with *+n* past three; the dots PR that was planned
   after 14g folds into it (the dot survives as the phone's form).
5. **[Med · measured]** **The Calendar's chrome is 184 px tall** before content: the 52 dp bar,
   then the full-width `SegmentedButton` row (41 px, with 20 above and 40 below), then the layer
   chips (32 px) — 178 dp, 17 % of the user's window, before the first hour line. Notion
   Calendar keeps the view picker in the bar (a *Week ▾* menu) and has no chip row. **Fix:** the
   Day/Week/Month/Agenda picker as four chips in the bar's action slot (or a `Week ▾` menu), the
   layer chips into the tray's header row; the grid starts under the bar.
6. **[Med · measured]** **The switcher is a full-pane overlay with a 57 px field** (Material's
   56 dp `OutlinedTextField`), the result rows 40 px; it covers the tree and the page. Notion's
   is a 560 dp centred card with a 36 px field over a scrim, the page still readable behind it.
   **Fix:** the shortcuts card's frame (a centred `Popup` over the 32 % scrim, max 560 dp), the
   field a `BasicTextField` at `body` in a 36 dp row, rows at the profile's height.
7. **[Med · measured]** **The quick-add strip's field is 66 px tall** (a 2 dp accent outline
   `OutlinedTextField`) under a 44 dp find bar on the same tab family, the preview chips on a
   second row — the strip is ≈ 100 px where the find bar is 44. **Fix:** the find bar's field
   (`BasicTextField`, hairline, 6 dp radius, 28 dp tall) and the chips inline at its right
   when they fit; the popup's field the same, so the two quick-add homes match.
8. **[Med · measured]** **The tray at 360 dp squeezes the Week to 66 px lanes** at the user's
   window (chips clip to *Keybo…*, *Pane ta…*; the day header's second line clips to *Planned*
   with the minutes cut off — no ellipsis, the text just ends). The shelf clamps to 45 %; the
   tray has no clamp (`calendar_tray_width` 240…360 is absolute). **Fix:** `effectiveWidthDp =
   min(width, 0.30 × pane)`, and the header line hides its minutes below a 90 dp lane rather
   than clipping (the `Text` gets `overflow = Ellipsis` at least).
9. **[Med · judged]** **A FAB on the Tasks tab and on a canvas page** — the phone's `+` at the
   bottom right under a pointer, beside a bar that already has room for the verb. Nothing else
   on the desktop uses a FAB. **Fix:** *New task* and *New card* as 28 dp bar buttons on a wide
   window (Ctrl+Shift+N exists for the first); the FAB stays the phone's.
10. **[Med · judged]** **Canvas cards under a pointer**: the caption *Empty card — tap to edit*
    (`CanvasScreen.kt:517` never took 14h·2's `openVerb()`), the red delete disc always drawn on
    every card (a hover-revealed control by 14d's rule), a card clipped at the pane's right edge
    with no scroll affordance. **Fix:** the verb through `openVerb()`, the disc on hover or on
    the selected card only, the canvas panned so a new card lands inside the pane.
11. **[Med · measured]** **Submenus draw over their parent** when there is no room at the
    right: at the user's window *Show beside ▸* opened its two items on top of the page menu's
    *History* row (the right edge is 45 px away); with room (the page in the shelf) it sits
    beside. **Fix:** `SubmenuItem` measures the window and flips to the left (`offset = −(own
    width)`) when `itemRight + submenuWidth > windowWidth`.
12. **[Med · judged]** **Journal day pages read `journal/2026-09-13` under the Journal parent**
    in the tree, and the shelf's header shows *journal/2026-…* — the prefix is the storage name,
    repeated under a parent that already says Journal; the shelf's title has room for eleven
    characters and spends seven on the prefix. **Fix:** a tree row (and the shelf header, the
    hover card, the switcher) shows a Journal child as its date — *13 Sep 2026* — the title
    string untouched (`journalDayOf` already parses it).
13. **[Low · measured]** The Timeline opens with the earliest bar (*Call the library*, 15 Sep)
    clipped at the pane's left edge (the first column is the 16th); the range starts at *min
    start − 7 days* by rule but the initial scroll lands two days in. **Fix:** open scrolled to
    `max(0, todayColumn − 3)` and never past the first bar.
14. **[Low · judged]** The Reminders slide-over's preset row still scrolls off the panel (*Custom*
    hidden past *1 day*) — recorded in the tray PR, unchanged. **Fix:** `FlowRow`.
15. **[Low · judged]** The Add task **dialog** is the one modal that is neither a slide-over nor
    a picker: an `AlertDialog` with a 56 dp title field, three switches and a chip row, 520 px
    tall. The edit sheet for the same task is a slide-over. **Fix:** the Add sheet through
    `TendrilSheet` like every other sheet (it becomes a slide-over on a wide window for free).

## Measured — the numbers behind the rows (device px, Ink dark unless said)

| surface | measured | expected (Compact, 1.03 px/dp) | verdict |
|---|---|---|---|
| the app bar (Pages, Calendar, Tasks, the shelf's header) | 53 (79 → 132 hairline) | 52 dp → 53.6 | ✓ one height everywhere |
| the tree's rows | **30** pitch, 14 px text band | 29 dp → 30 | ✓ (Notion 30) |
| the Tasks list's rows | **30** | 30 | ✓ |
| the tray's chips | **32** | 29 dp row + 1 dp gap → 31 | ≈ ✓ |
| the Table's rows | **50** | 30 | ✗ #3 |
| the Calendar Day rows | **65** | 30 | ✗ #2 |
| Agenda rows (task / event) | **52–61 / 44** | 30 | ✗ #2 |
| History rows (two lines) | 53 | — | a two-line row; the second line is a `caption` — fine |
| `DropdownMenu` items (5 menus) | **49** | 30 | ✗ #1 |
| the switcher's field / rows | **57** / 40 | 36 / 30 | ✗ #6 |
| the quick-add strip's field | **66** | 28–36 | ✗ #7 |
| Settings' chip rows (`FilterChip` 32 dp + padding) | 41–45 | — | Material's chip; acceptable |
| the Calendar's chrome to the first content | **184** | ≈ 100 (bar + one row) | ✗ #5 |
| the segmented row (`SegmentedButton`) | 41 | 40 dp | Material's |
| the Week's lanes at the user's window with the 360 dp tray | **66** | ≥ 90 to read a title | ✗ #8 |
| the page title's band (`pageTitle` 18/600) | 18 | 19.1 px font | ✓ |
| the window's chrome (title bar) | 79 (0 → 78) | Windows' | — |

Chalk light: the same pitches (the profile is theme-blind); grounds `#FBFBFA` (measured 251, 251,
250) and Ink dark `#1B1D21` (27, 29, 33) — the solved values, exactly.

## Judged — first read, hierarchy, whitespace, the signature

- **Pages.** The first read is right: the tree's rows and the page's title carry the hierarchy;
  PAGES / the bar's title / the page title are three weights, not three sizes, as the type PR
  wanted. The folded mind map's frame is a 300 px box holding one 130 px card and *Mind map ·
  open* in the corner — the frame is sized to the unfolded map; folded, it should hug its card.
  The property strip's labels and values align on one baseline; the `#book` chip on the page
  and in the tree's filter row are the same object (✓ signature). The empty state's line is
  centred and quiet (✓).
- **The shelf.** The header is the bar's height (✓ 14h·1); the neighbourhood's nodes overlapped
  on first draw (*Call the library* over *Escape test* over *journal/*) and only spread after a
  second layout pass ~1 s later — the initial positions are the map's 160 dp layout squeezed into
  a 120 dp node frame; recorded for the function pass. The header's title *Around this page*
  ellipsises to *Arou…* because the three depth chips, the open-in-map button and × leave 40 px:
  the chips could drop to *1 · 2 · 3* text buttons, or the title could go (the glyph says graph).
- **Calendar.** The Week grid reads as a calendar (hour lines, today tinted, the now-line —
  ✓); the tray reads as the Tasks list's cousin (✓ chips, eyebrows). The grid opened at 00:00
  because the earliest block is 00:21 — the rule (*earliest − 1 h*) is right and the seed is
  odd; no finding. The Month and the Day are the phone's (#2, #4). The Agenda's day dividers are
  a hairline with the date as a `caption` — fine, once its rows are one line.
- **Tasks.** The list column is remembered at 575 dp (`tasks_list_width`), leaving the pane
  270 px — the pane's chips wrap two per row and *2026-09-17 · 15:00* wraps mid-value. The pane
  itself is a good object: the title with its checkbox, five label/value rows, the chips as the
  menu by name, STEPS as an eyebrow. The list's *Someday (2)* with a switch reads as a section
  header, not the filter it is (the copy pass). The Habits pane is a stub (the function pass).
- **Road Map.** The filter row reads as one line of chips with the legend at its end (✓). Two
  nodes overlap (*Call the library* on *Escape test*) at rest — the force layout's minimum
  distance is below a node's height. Focus dims the rest (✓).
- **Settings.** Sections by weight (✓ the type PR); the register swatches with a name under
  each are the page's one ornament and earn it. The AI key field is the same 56 dp Material
  field as the switcher's (#6's family): a `BasicTextField` in a 36 dp row would match the
  rest. The Sync folder row's *Passphrase (optional)* field, the same.
- **Menus and sheets.** Radii: the slide-over square (by design), the hover card 10, the popup
  10, the shortcuts card 12, menus 4 (Material), chips 8 — one family (4 · 8 · 10 · 12) with
  the card at 12 as the one stray; Low, folded into #6 if the switcher takes the card's frame
  (make both 10).
- **Hover and right-click.** The tree row's `···` fades in on hover (✓ 14d); a task row's on
  hover (✓); the canvas card's delete disc does not wait for a hover (#10). Right-click opens
  the menu at the pointer on rows and blocks (✓).

## What's working

- The rail, the tree, the Tasks list and the tray are at one row height (30 px) beside Notion's
  30 — the tray PR's brief holds across the three lists that took it.
- The bar is 52 dp on every tab and in the shelf; one hairline vocabulary under it.
- Chalk light re-solves every surface (the Week's tints, the label chip, the ladder's stripe,
  the outline of a canvas node) with nothing left dark — the token map holds on the second
  ground.
- The slide-over frames every sheet the same way (History, Trash, Reminders, Postpone, Calendar
  settings, the New sheet, the label sheet, Configure view); the one dialog is #15.

## Cannot verify

- Pixel measurements of the hover card's width and the popup's radius: both grabs had the card
  on a dark ground the projection could not separate from the card's own; judged only.
- 1200 × 800 grabs: the walk ran at the user's window (967 × 1039) beside Notion, as the tray
  PR did; the plan's second frame was not taken (the pitches are theme- and size-blind; the lane
  and pane widths at #8 and the Tasks pane's squeeze are window-specific and said so).
- The phone.

## Disposition

#1–#4 → the fix PR (*the audit's fixes*); #5–#15 → §0.10's small-things list with this file's
row numbers (L5…L15). The type pass is `desktop-type-full.md`, the function pass
`desktop-function-full.md`.
