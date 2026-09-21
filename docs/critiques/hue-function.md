# S13 — a hue for a database and a card, the build walked

*2026-09-21. Built on the mock's four decisions (`hue-mock.md`). The desktop at the dev window,
native grabs; the phone had the build installed and then dropped off USB before its walk — recorded
below as pending.*

## What was built

- Schema **v24** (`MIGRATION_23_24`, `24.json`): `page_databases.hue`, `canvas_nodes.hue`; the
  records carry the integer; verified in place on the desktop's database (23 → 24, both columns).
- `domain/colour/Hues.kt` (pure): `normalHue`, `hueDistance`, the six presets and `huePreset`
  (± 10°), `hueHex` (the wheel's own lightness, for a file), `jsonCanvasColour` (`"1"`–`"6"` or a
  hex), `defaultDatabaseHue` (the title's hash, moved into the band 30–60° past the accent when
  it lands closer). `ui/theme/DataColours.kt` `hueColours` — the hue at saturation 0.55 solved to
  4.6 : 1 on the ground and on its own 14 % tint; the tint, the frame's 8 %, what reads on the
  solid. `HuesTest` (3): the arithmetic, the default's distance from five accents over eighteen
  titles, every preset and four more hues on every register × mode × OLED.
- `LocalDatabaseHues` from `WorkbenchEnvironment` (`PageDatabaseDao.observeAll`), read by the
  tree row's glyph, the phone card's glyph, the Road Map's database node (fill in the tint, border
  in the hue), the Month's database-date chips (`DateCell` carries its database), the Day and
  Agenda rows' glyph and title, the phone's database dot.
- `HueSheet`: the swatch (the effective hue — the default's when none is chosen — and its
  contrast), the bar with a knob (a tap or a drag), the six presets as 28 dp targets, *Default* /
  *None*, Cancel · Done. From a database's `···` → *Colour…* (`setHue`) and a text card's or a
  frame's menu → *Colour…* (`setNodeHue`); a page card keeps its own tint.
- The canvas: a coloured card a 14 % tint with its border in the hue; a frame an 8 % tint, its
  strip at 14 %; JSON Canvas export writes `color`.

## The walk

| device | tried | observed |
|---|---|---|
| desktop | the tree | *Books v12*'s glyph in its hashed default (hue 270°, purple) — no database is grey any more |
| desktop | *Books v12* → `···` → *Colour…* | the sheet: *Default · hue 270° — 5,8 : 1*, the bar's knob at 270, the six marks, the *Default* row |
| desktop | the cyan mark → *Done* | the tree's glyph cyan at once (the local's flow); Road Map → *Books v12* filled in the cyan tint with a cyan border, *Beds board* (a canvas) still the third hue's outline; Calendar → Month → the *Read on* chips (*Call the library*, *Escape test*, *Reading log*) in the cyan tint |
| desktop | Garden plan → *Garden waste* → *Colour…* → orange | the strip on an orange 14 % tint with an orange border; the tree branch unchanged |
| desktop | *Compost*'s pill → *Colour…* → green | the following frame on a green 8 % tint with a green border |
| desktop | the migration | `PRAGMA user_version` 23 → 24 in place; `hue` on both tables |
| phone | — | the v24 build installed (`adb install` Success), the phone dropped off USB before the walk: the sheet under a finger, the glyph on the Pages list and the dot pending |

## Defects fixed on the walk

1. The sheet's swatch read the unset label twice — it shows the default's solved colour and
   *Default · hue n°* now (`defaultHue` on the sheet).
2. Recompiling under the running app broke its lazily-loaded lambda classes
   (`NoClassDefFoundError`, logged to `crash.log` by item 21's handler; the window stayed) — not
   a defect of the build; noted so the next walk relaunches after every compile.

## Findings

- **[Low · judged] The contrast in the sheet reads `5,8` on an Italian locale** — `"%.1f".format`
  without a locale. Fixed: `Locale.ROOT`.
- **[Low · judged] The phone's month dot takes the day's first database's hue** where a day holds
  two databases' dates — one dot per kind is the rule (L4); a dot per database is not.

Tests 913 → 916.

## The phone (2026-09-21, the v24 build after #134; dumps and screencaps at 480 dpi × 0.85)

| tried | observed |
|---|---|
| the Pages list | *Errands*' glyph in its hashed default — #6139C6, hue 257° (the sheet names the same) |
| *Errands* → `···` → *Colour…* | the sheet under the finger: the swatch *Default · hue 257° — 7.2 : 1 …*, the bar, the six marks, *Default — from the title*, Cancel · Done |
| a drag along the bar, 300 → 600 px | the knob follows; *hue 203°* at 600 px — (600 − 63) / 954 × 360 = 202.6 |
| the cyan mark → *Done* → back | the glyph teal at once (35, 113, 121 — hue 185 solved on the light ground); `page_databases.hue` = 185 |
| a Date property *Read ons* on *Errands*, the 24th on a row; Calendar → Month, *Database dates* on | the 24th's dot in the same teal (35, 113, 121) — the day's first database's hue |
| the migration | `PRAGMA user_version` 24 on the phone's database; `hue` on both tables |

Found and fixed: **the swatch line ellipsised** at Touch's 16 sp (*Default · hue 257° — 7.2 : 1 on this…*) — the
line is *hue n° · 7.2 : 1* now, the ground being the sheet's own. The *Database dates* layer had been off on
the phone (its persisted layers), which is why the first Month showed no dot — a setting, not a defect.
