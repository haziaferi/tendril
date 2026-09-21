# Critique — `docs/mockups/hue.html` (S13: a hue for a database and a card, pre-build)

*2026-09-21. The token map's last open slot (B§13.8.2, decided 2026-09-14: a database takes any hue
on the wheel, solved to the ground, defaulting from the title's hash ≥ 30° from the accent; the icon
the second channel) and the canvas's card colours, on which JSON Canvas's `color` waited. Grounds
measured live: Obsidian's canvas colour row — *none* + six presets (red · orange · yellow · green ·
cyan · purple; 20 px discs at a 29 px pitch; JSON Canvas `"1"`–`"6"`) + a custom wheel; a coloured
node a tint fill with its border in the hue. Notion's databases have no colour (its pages have icons);
Google Calendar's per-calendar colours and Apple Reminders' twelve [Assumed]. Decided in one batch:
**one hue bar with the six as marks**, for a database and a card alike; **cards and frames in
Obsidian's form** (a 14 % tint, the border in the hue; a frame at 8 %); **the database's hue on the
map, the calendar and its glyph everywhere**; **the default from the title's hash**.*

## Top priorities

1. **[Med · measured] The six presets solved on Ink dark** clear the floor by construction — the
   hue at saturation 0.55 with its lightness walked to 4.6 : 1 on the ground *and* on its own 14 %
   tint (the labels' rule): red 4.65, orange 4.68, yellow 4.73, green 4.67, cyan 4.75, purple 4.69;
   the register's text on each tint ≥ 9.9. The build asserts the same over every register and the
   OLED ground (`HuesTest`), so no hue can be chosen that does not read.
2. **[Med · judged] The sheet's swatch must name the default** — with nothing chosen the mock's
   swatch read as blank; the build shows the *default's* solved colour and *Default · hue n°*, so a
   database's colour is legible before anyone picks (found on the walk, fixed).
3. **[Low · judged] A hue is stored, never a colour** — one integer on the row; the register solves
   it on each ground, so the same database is the same hue in Ink dark and Chalk light and the
   export writes the hue's own hex. The sync record carries the integer; a v23 peer reads null.

## Dimension by dimension

- **Hierarchy** — the accent stays reserved (B§13.8.3 rule 1): a database's hue is kept ≥ 30° from it by the default, and a chosen hue may sit anywhere — the person's choice is theirs.
- **Colour** — one solver for labels, callouts and hues; the calendar's *database date* layer loses its own tint (the third hue) for the database's — a database is a calendar.
- **Consistency** — the same sheet from a database's `···` and a card's or frame's menu; the same marks; *Default* / *None* is the one line that differs.

## Cannot verify

- The bar's knob under a finger (a 24 dp bar; the knob 28 dp) — the phone's walk.

## What the mock says about the code

Schema **v24**: `page_databases.hue`, `canvas_nodes.hue` (both `INTEGER`, null); the sync records'
`hue`; `domain/colour/Hues.kt` (the wheel, the six, `defaultDatabaseHue`, `jsonCanvasColour`);
`ui/theme/DataColours.kt` `hueColours` and `LocalDatabaseHues` (every database's hue, provided by
`WorkbenchEnvironment`); `ui/components/HueSheet.kt`; the sites — the tree row, the phone's card,
the Road Map's node, the Month's chip, the Day and Agenda rows, the phone's dot, the canvas card
and frame; JSON Canvas `color`.
