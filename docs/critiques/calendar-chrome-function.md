# Critique — the Calendar's chrome and the borderless window, on the build

*2026-09-17 · `design-critique-plus`, the after-pass for the L5 PR (§0.10 item 22; every tab's
header; the borderless window). Read-only; native grabs at the user's 967 × 1039 window and
maximized at 1920 × 1040, Ink dark and Chalk light; the PIL line probe (`lines.py`) beside the
Notion Calendar grabs of the same morning at the same 125 %; the window driven by user32 input
and by computer-use once the JetBrains Runtime's `java.exe` was granted. The walk is the plan's
verification list, each step once. **Phone: not walked** — the phone's form (`!wide`) is
untouched by this PR.*

## Measured — beside Notion Calendar (device px at 125 %, both maximized)

| | Tendril (Compact) | Notion Calendar | note |
|---|---|---|---|
| title bar | the 52 dp bar, **55 px**; — ☐ ✕ painted by Windows over its right end | the toolbar, 46 px, the same overlay | the OS title bar (23 px) is gone |
| caption block | **141 px** (3 × 47), read from the runtime; the bar's actions end before it | ≈ 3 × 47 | `···` ends at 1753, the — button starts at 1779 |
| chrome to the first hour line | **168** with a three-line all-day row (Wed's *Dup · Ex · +3*); ≈ **132** with one line | **130** (one-line all-day) | today 378 |
| hour pitch | **48** | **48** | 45 dp; 45.5 px at the 967 window (its scale 0.81) |
| gutter | **59** | 60 | 56 dp |
| all-day row | 22 dp chips at a 24 px pitch | 24 | |
| Tasks · Road Map · Pages · Settings chrome | **55** each (the Tasks list column's 52 dp header beside the bar, like the tree's) | — | was ≈ 190 · ≈ 103 · 55 · 55 |

## Walked

- **The window** — no OS title bar; the mark over the rail; a press-and-move on the bar's ground
  moved the window by exactly (200, 150); a double-click maximized it (`IsZoomed` true) and
  restored it; the View-only eye, `Week ▾`, `Layers ▾`, `···` still work as buttons; the
  ☐ button restores; Chalk light turns the caption glyphs dark (`controls.dark` follows the
  register — the Chalk grab).
- **Calendar** — the bar reads `‹ › 14 – 20 settembre 2026 · Today · Week ▾ · Layers ▾ · + · ···`;
  the segmented and chip rows are gone; the Week grid has no ‹ › row of its own. `Week ▾` lists
  Day / Week / Month / Agenda with a check on the current; Month → the grid without its ‹ › row,
  `‹` → *agosto 2026*, *Today* → back; Day → `giovedì 17 settembre`, and the menu gains *Plan the
  day* (checked → the Plan timeline, the menu stays open); Agenda → *Next 30 days* with no ‹ ›.
  `Layers ▾`: a click on *Habits* unticked it and the menu stayed open; a second click ticked it
  back. The quick-add `+` and `···` as before.
- **Tasks** — the bar `Tasks · Review · Trash · +`, no FAB; the list column's 52 dp header with
  *Tasks · Habits · Merged* tabs and `Today ▾` (Today / This week / This month — *This week*
  applied); Habits hides the range menu; the pane starts at the bar.
- **Road Map** — `Filter ▾` in the bar: *Journal* toggled on (the menu stayed open) and off; the
  kinds and the labels as check items; the legend at the canvas's foot.
- **Pages** — the tree header, the empty detail's bar and an open page's bar share the top row;
  the page bar's ground (right of the title field) drags the window; the tree header's ground too.
- **A pop-out** — *Escape test* Shift-clicked into a window: its page bar is its title bar (the
  caption block after `···`); a press-and-move moved it by (−101, +105); `···` opens its menu;
  Ctrl+W closes it.
- **The fallback** — the same build run on Temurin (an init script pointing `javaHome` at it):
  the OS title bar returns, the rail has no mark row, `···` sits at the edge, no exception.

## Findings

1. **[High · observed, fixed before this pass]** A pop-out could not be dragged: its page bar's
   title is a text field that filled the whole slot, leaving no ground. Under a title bar the
   field now takes its text's width (min 160 dp); the rest of the slot drags — on the main
   window's page bar too.
2. **[Med · observed, fixed before this pass]** Pop-outs had no custom title bar at all: the
   install lived in the scaffold, which a pop-out does not pass through. It now lives in
   `WorkbenchEnvironment`, the one place every window's content goes through.
3. **[Low · measured]** At the 967 px window the Week header's *Planned 30m* truncates to
   *Planned* (lanes ≈ 64 px with the tray open) — item 22's L8 already records "the header's
   minutes hidden below 90 dp lanes"; not this PR's.
4. **[Low · judged]** The `Today` pill sits in the title slot after the range, so its x moves
   with the title's length (Month's is shorter than Week's). Google's *Today* sits before the
   range at a fixed x. Recorded; a fixed order (`‹ › Today · range`) is one line if wanted.
5. **[Low · observed]** The debug run showed the title bar installed twice at start (a 44 px
   height before the density profile loaded, then 52.5) — harmless (`remember` keys), recorded.

## Cannot verify

- The phone: nothing changed under `!wide`, not run.
- The snap-layout flyout on hovering ☐ (Windows 11's): not grabbed — the hover is the OS's, the
  grab tool captures the app's window only.
- A JBR-less packaged app: the MSI bundles the toolchain's JBR by construction, not built here.

## Disposition

Findings #1 and #2 fixed and re-walked; #3–#5 recorded. Merge.
