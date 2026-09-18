# Critique — small things III, on the build (L11–L14, F9–F14)

*2026-09-18 · `design-critique-plus`, the after-pass on the `small-things-3` build — PR D of the
four. Grounds measured live this afternoon: **TickTick's reminder list** (a throwaway task
*Reminder ground*, the user's to delete): *On the day (9:00 AM) · 1 day early (9:00 AM) · 2 days
early · 3 days early · 1 week early · Custom* — a vertical list at a ≈ 34 px pitch, each row naming
the offset **and the moment it fires**; Notion's and Obsidian's label pickers list the existing
labels before a letter is typed [Assumed — the function pass's note, not re-opened]. The desktop
walk at the user's 967 × 1100 window, Ink dark, native grabs. No mock (the label sheet's form is
the sheet the app has; the reminder list is the ground's). Read-only; one finding fixed before
the pass closed.*

## Measured

| item | before | after (this build) | on the grab |
|---|---|---|---|
| **L11** a submenu at the window's edge | *Show beside ▸* opened over its own parent's *History* row | `submenuSide` (pure, tested): when the item's right edge + 220 dp would cross the window, the submenu opens **to the left**, level with its item | *Escape test*'s `···` at the user's window: the two items left of the parent menu, its right edge on the parent's left |
| **L12** a Journal day's title | `journal/2026-09-13` in the tree, the shelf's graph header, the hover card, the switcher, the map | `displayTitle` (pure, tested): *13 Sep 2026* everywhere a title is *shown*; the page bar keeps the storage name — it is the editable title | the tree under *Journal*: *13 Sep 2026 · 15 Sep 2026 · 17 Sep 2026* |
| **L13** the Timeline's first scroll | the earliest bar clipped at the pane's left edge | `timelineInitialColumn` (pure, tested): today three columns in, never past the day before the first bar | *Books v12* → Timeline opens on the 14th; *Call the library* (15th) whole, with its margin (the first build opened on the 15th and clipped the label — fixed: a day of margin) |
| **L14 / F12** the Reminders presets | a chip row that scrolled off the panel (*Custom* past *1 day*); no *before* | a **list** under the eyebrow *REMIND ME … BEFORE*: seven rows at the profile's 29 dp with a check on the chosen one, each with the moment it would fire (*Thu 17 · 08:00*, `reminderFiresAt`, tested — the start's time, else the anchor, else midnight); *Custom…* the last row; the *Fires at* anchor presets wrap (`FlowRow`) — the walk found them cut off too | the sheet on *Escape test* (a Someday task): the seven rows, no moments (no date — by rule), the anchor chips wrapping |
| **F9** the Add label sheet | empty until typed | every label the page lacks, with its hue dot, before a letter is typed; the field on `TendrilField`; *Create "…"* only when nothing matches | on *13 Sep 2026*: *● book*; on *Escape test* (which has *book*, the only label): nothing to list — correct |
| **F10** *Someday (2)* | a `label` over a switch, read as a section header | *Show Someday · 2* in `label` on `onSurfaceVariant`, right-aligned beside its switch | the Tasks list's filter line |
| **F11** the hover card | 240 px wide, lines joined | `CARD_WIDTH` is 320 dp in code; the folded map's one counted line is tested (`PagePreviewTest`); the report's 240 px is read as the window-edge clamp | not re-walked (the mention on the Journal page was not reached this pass) |
| **F13** the row menus' icons | the habit row's *Move to Trash* wore a ✕ | no icons on the habit row's menu; the task row's *Urgency ▸* keeps its dots (they are the levels, not decoration) | — |
| **F14** the neighbourhood's first draw | nodes on top of each other for ~1 s; the tab's *Call the library* over *Escape test* at rest | a hard separation each frame (boxes that overlap are pushed apart along the axis of least penetration, 8 dp of air); nothing is drawn until the layout has settled once (a fade-in) | the shelf's graph at 0.25 s, 1.75 s and 3 s: four nodes apart in every frame |

## Findings

1. **[Low · observed, fixed before the pass]** The first build's Timeline opened *on* the first bar's
   column, so a title drawn from the bar leftwards still clipped. A day of margin before the first
   bar (`firstBarColumn − 1`).
2. **[Low · observed]** The shelf's graph header at 280 dp shows no title at all — the depth chips
   and three buttons take the row (`material-frames-function.md` #3, unchanged). Recorded again.
3. **[Low · recorded]** The custom offset's unit chips (*minute · hour · day · week*) still sit in a
   scrolling row beside the count field; four short chips fit the slide-over — left.

## Cannot verify

- The phone: the reminder list under Touch (48 dp rows), the label sheet as a bottom sheet, the
  Someday line, the Journal dates in the phone's list — unrun.
- F11's hover card width at the window's edge — the card was not opened this pass.
- Leftovers on other apps: TickTick's *Reminder ground* task (the user's to delete).

## Disposition

#1 fixed; #2–#3 recorded. Ship — item 22's Med/Low list is closed with this PR (L7b stays its own).
