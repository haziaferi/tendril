# The phone audit — function, every feature once

*2026-09-18 · pass 3 of 3 (dimensions 1g / 1h / 1i): a walk of every phone feature, driven by
`uiautomator` dumps and `input` (taps found by text, never by coordinate — the first scripted
walk, on fixed coordinates, left Tendril after one missed tap and ran on inside Todoist and
Obsidian, which is why `walk3.py` checks the foreground package before every step). Findings
stay observable-level. What the catch-up (`phone-catch-up.md`) and the fix PR already walked
is not repeated here; the rows say what was new to this pass.*

## Pages

| feature | tried | observed | finding |
|---|---|---|---|
| a row page (*Trip*, a row of *Errands*) | open from the list | the label chip and *Add label*; the property strip *Errands · #errand · Done ☐ · Deadline — · Status —*; the mind map card *Mind map · tap to open* | #1 (the strip's values are 31 dp targets — layout #3) |
| a block's long-press | on the map card's node *Packing* | nothing opened; History afterwards showed *just now · before an edit* | #2 |
| History | `···` → History | four revisions, *Trip · 6 blocks*, newest first, 53 dp rows | — |
| the database's `···` | on *Errands* | *Show on Road Map · Save as template · Configure this view… · Add property · Bound to #errand… · Blocked by… · Turn off Sync to Tasks · Move to Trash* — F8's vocabulary | — |
| the Timeline | the chip | day columns at 36 dp with English initials, *Untitled* as a bar (clipped at the left edge), *No date · hold and drag onto a day* with *Trip* under it | #3 |
| the canvas | (the fix PR) | `···` with the two verbs | — |
| find in page, the label sheet, the switcher, the New sheet, templates | (the catch-up) | — | — |

## Calendar

| feature | tried | observed | finding |
|---|---|---|---|
| Day | the chip | the layer chips, the header *venerdì, settembre 18*, the 48 dp *Quick add…* field, *Nothing scheduled* | P10 |
| Agenda → an occurrence | tap *Catch-up test* | the edit sheet: the title field, *Task \| Event*, *When* on with *2026-09-25 · 14:30 · All day*, *Repeats* chips, *Deadline* off, *Estimate (minutes)*, the urgency discs, *Delete · Cancel · Save* | #4 (the ISO date) |
| Week, Month, the tray strip's drag, the settings sheet | (the catch-up) | — | — |

## Tasks & Habits

| feature | tried | observed | finding |
|---|---|---|---|
| Postpone | the row menu | a sheet: the explainer, six chips (*+15 minutes … +1 month*), *Custom* with a count field and unit chips wrapping to two rows, *Cancel · Postpone* | layout #5 |
| Set deadline | the row menu | the date picker *Pick a day* with *Cancel · Set deadline* | — |
| Review | the bar's ☑ | *Last 7 days · 2m logged · 1 check-in* and *Nothing left to review* | — |
| Merged | the tab | *Stretch · 09:00* today | — |
| the Add sheet, the row menu and its pushed level, the reminder list, the habit sheet, the Trash | (the catch-up, the fix PR) | — | — |

## Road Map · Settings

| feature | tried | observed | finding |
|---|---|---|---|
| the filter row | a horizontal swipe across it | the row scrolled to *Canvases · Any label* and the legend *→ mention — related*; **a focus was set** (the depth chips *1 2 3* appeared) | #5 |
| Settings | (the catch-up) | every section | — |

## Findings

1. **[Med · observed]** The row page's property strip is read-only on the phone and its values
   are 31 dp targets that do nothing on tap; a property row should open its cell's editor (the
   Table's) or read as plain text. Layout #3/#4 carry the size; this carries the verb.
2. **[Low · observed]** A long-press on a mind-map node opened nothing and left a revision
   (*just now · before an edit*) — the press reached the editor as an edit. The block action sheet
   is the long-press's meaning everywhere else on the page.
3. **[Low · observed]** The Timeline's day initials are English on an Italian phone (type #4).
4. **[Med · observed]** The edit sheet's *When* is an ISO date — the last one on the phone (type #5).
5. **[Low · observed, cannot reproduce on purpose]** A swipe that began on the Road Map's filter
   row set a focus: the gesture's down reached the canvas under the row, or the row's first chip.

## Cannot verify

- The widgets (not placed); reminders firing (the phone's alarms, not walked); Google Calendar
  sync (not connected); App Lock (a biometric); the phone's dark mode and landscape.

## Disposition

With the layout and type passes: **High** — type #1 (the Touch type scale) → the phone's second
fix PR, with its decision; **Med** — layout #1–#3, type #2, function #1, #4; **Low** — the rest.
All recorded in `tendril-spec.md` §0.10 item 23 by id (L·P, T·P, F·P); the decisions listed once
at the end of each pass.
