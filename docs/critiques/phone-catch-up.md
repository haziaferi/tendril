# The phone catch-up — the pending halves of #82–#106, walked

*2026-09-18 · the OnePlus 9 Pro (1080 × 2412 at 480 dpi — 3 px/dp; the phone's register Ink,
mode System → light, Inter), driven with `uiautomator dump` and `input`, screencaps only for
colour. The phone last ran a build from 2026-09-15 (schema v19); this walk installed `main`
(#106, v20) over its data — the user's choice: keep the phone's database. Every PR since 14f·1
stated its phone half as pending; this pass folds the 25 into one walk. Decided with the user:
catch-up first (this document), then a measured phone audit beside the five apps installed for
it (TickTick, Todoist, Obsidian, Notion, Notion Calendar); every High goes to one follow-up fix
PR; Med/Low to §0.10. Read-only apart from the one fix the walk could not start without.*

## Before anything: the launch

**[High · fixed in this PR, as the walk's prerequisite]** `main` crashed on every launch —
`IllegalArgumentException: Padding must be non-negative` from `LabelFilterRow`
(`PagesScreen.kt:370`): under Touch the chip row's bottom padding is `4 dp − listTopPadding
(8 dp)`, negative since 14h·2 (#88, 2026-09-16). Any store with one label — the phone's has
`errand` — died on the Pages tab before drawing it. Clamped at 0; the 48 dp target's own inset
is the air there. **The phone had been unlaunchable for two days of PRs**; nothing in the
stand-in runs a Compose layout, which is the lesson (recorded below).

## The migration — v19 → v20 in place (14g·3's rebuild)

Pulled before and after: `user_version` 19 → **20**; `entries.important` gone, `importance`
present; the two flagged rows (*Old task*, *Call bank*) read **3**; ids intact (the one
`time_logs` row still joins its entry); `index_entries_uid` recreated; `page_revisions`,
`page_database_views.endDatePropertyId`, `blocks.referencedBlockUid` all present (v17–v19 had
been applied on the 15th). **Verified.**

## The walk, by PR

| PR | the phone owed | observed | finding |
|---|---|---|---|
| #82 14f·1 | tap a task row does nothing; long-press → the menu; the Trash button; the Pages list's ↓↓↵ opens the third page | long-press → *Postpone… · Add a step · Set deadline… · Urgency: None ▸ · Move to Trash* at 48 dp rows (a step's menu rightly shorter); Trash sheet *Trash is empty*; **↓ from cold lands on the label chip**, ↵ toggled the `errand` filter | #4 |
| #83 14f·2 | the Week strip unchanged; the Day view's field; *Opens on* in the settings sheet | the strip's day rows (*• Gym*); the Day view opens first; *Opens on* with four chips — **Agenda wraps to two lines** | #7 |
| #84 14g·1 | the migrated choice; *Deeper blacks*; the widget follows | *Ink · System · Inter* in the Appearance row; the OLED line under it; the widget not placed | widget: cannot verify |
| #85 14g·2 | the page and chips coloured | the `errand` chip tinted, its hue dot in the label sheet (86,109,87 solved green) | — |
| #86 14g·3 | the migration; the stripe; the edit sheet's picker; the Settings row | above; a 13 px (4 dp) stripe on *Old task* (*Urgency: Urgent*); *Show urgency on tasks* and *Show habit streaks* under *Tasks & Habits* | — |
| #87 14h·1 · #89 pop-outs · #91 previews | nothing composed | the page menu is *Show on Road Map · Find in page · History · Save as template · Move to Trash* — no *Show beside*, no *Open in a window* | — |
| #88 14h·2 | *edited 2 h ago*; the chip row; *tap to open* | *edited 5 days ago* on every card; the chip row (the crash above); *Empty card — tap to open* on the canvas | #1 |
| #90 drag | the strip under Week and Month; a long-press drag onto a day; Day keeps its rail | *Unscheduled · 1  Overdue · 3* with the chips' stripes and *due sab 12*; a long-press drag (`input motionevent`) of *Untitled* onto *mer 16* → *• Untitled* on the 16th, *Overdue · 4* | — |
| #92/#93 type | the roles at Touch | 14 sp rows, 12.5 meta, 18/600 titles read as on the desktop | — |
| #94 tray | the bell as before; a removed reminder's alarm cancelled | the bell on every dated row → the shared sheet | the cancel: tested, not observed |
| #95–#97 audit · fixes | menus and rows unchanged under Touch; the habit sheet unchanged | menus 48 dp; task rows 67 dp (a two-line meta 86); the habit sheet *Once this month · Last · Streak · month dots* | #2 |
| Month grid | the dot grid with a weekday header and per-layer dots; the database Calendar list | *L M M G V S D*; grey task dots, green event dot on the 13th, today a disc | — |
| L5 chrome | the rows as before | the Calendar's segmented rows and layer chips as before | — |
| L6 switcher | the overlay + Recents | *RECENT* with *Errands · just now*, *13 Sep 2026 · 5 days ago* (L12's dates) | — |
| L7 + L8 | the Day view's field; the tray strip | both as before | #8 |
| L9 + L10 canvas | the FAB, the badges, *tap to open* | all three; **the canvas page has no `···`** on the phone | #5 |
| #102 design layer | dialogs' corners | the delete-view confirm and the date picker at the family's radius | — |
| #103 frames | fields 48 dp; the Add task sheet a bottom sheet; the date picker | fields 144–150 px; the sheet with the tinted line, chips, switches, *Repeats*, *Cancel · Add*; *Pick a day*; **with the keyboard up the sheet's rows overlap** | #3 |
| #104 views | the New view sheet; the delete confirm; *No Status* | *Name · Layout* chips · the blurb · *Cancel · Add*; the Board with *No Status* and *Not started*; *Delete view* asks | — |
| #105 small things III | the reminder list at 48 dp; the label sheet; *Show Someday · n*; Journal dates | seven rows at 56 dp with *sab 12 · 07:00*; `errand` listed before typing; *Show Someday · 1*; *13 Sep 2026* in the switcher | #6 |
| #106 L7b | the Day view field's tint | *tomorrow* and *14:30* on `findSoft` in the Day view, the Add sheet | — |

## Findings

1. **[High · fixed here]** The launch crash (above).
2. **[High]** **A `TendrilSheet` on the phone does not scroll**: with the keyboard up, the Add task
   sheet's column is squeezed — the *Repeats* chips measure 20 px and the *Has deadline* switch
   is drawn over them (reproduced twice; correct once the keyboard is down). `ModalBottomSheet`'s
   content `Column` needs `verticalScroll` (the desktop's `SlideOver` body scrolls already); every
   sheet with a field and rows below it has the defect.
3. **[Med]** **The Urgency submenu covers its parent** on a 360 dp window: L11's flip puts the 220 dp
   menu to the left of the item, which is where the parent is. Under Touch a submenu should open
   *below* its item (or replace the menu's contents), not beside.
4. **[Med]** **The Pages list's keyboard**: DPAD ↓ from cold lands on the label filter chip, not the
   list's cursor, and ↵ toggles the filter — the 14f·1 claim (*↓↓↵ opens the third page*) does not
   hold on a hardware keyboard; the list only owns the keys after a click, which on the phone
   opens a page.
5. **[Med]** **The canvas page has no `···` on the phone** — *Show on Road Map*, *Move to Trash*,
   *Save as template* exist for a page and a database, not a canvas (the desktop's canvas takes the
   workspace's chrome). F8's one vocabulary stops at the canvas on the phone.
6. **[Med]** **Task rows' meta is raw ISO** — *2026-09-12 · due 2026-09-11 · 0/1 steps*, wrapping to
   two lines (86 dp rows) where the tray's chips say *due sab 12* and the switcher *13 Sep 2026*.
   One date format for the phone's rows (the tray's).
7. **[Low]** The Calendar settings' *Opens on* row wraps *Agenda* to two lines (four 48 dp chips
   in 288 dp); *Restore backup* wraps too.
8. **[Low]** The Day view's preview chips start at the screen's edge (x = 0) while the field is
   inset 16 dp.
9. **[Low]** The find bar's buttons say *(Shift+Enter)*, *(Enter)*, *(Esc)* to a finger.
10. **[Low]** The Day header reads *venerdì, settembre 18* — the Italian order is *venerdì 18
    settembre* (a `DateTimeFormatter` pattern, not a locale style).
11. **[Process]** Nothing in the stand-in composes a layout: a negative padding under Touch, a
    squeezed sheet under an IME, a submenu off a 360 dp edge — none of these can fail a JVM test or
    `audit.py`. The phone audit that follows measures; the fix PR should add the one thing that would
    have caught #1: a Compose UI test of the Pages tab with a label in the store (Robolectric or the
    device's `connectedDebugAndroidTest`) — decided in that PR.

## Cannot verify

- The widget's register (not placed on the launcher); the reminder cancel through the coordinator
  (tested in the JVM); the Plan rail's drag under Touch (not tried).
- The phone's dark mode (System → light at the time of the walk).

## Leftovers on the phone (yours to delete)

A task *Catch-up test* (Fri 25 Sep 14:30, High); *Untitled* moved to Wed 16 Sep by the drag; a
*Status* Select on *Errands* (made by the Board view, which was deleted); the `errand` filter was
toggled and cleared.

## Disposition

#1 fixed here (the walk could not start without it); #2–#6 to the fix PR; #7–#10 recorded in
§0.10 item 23; #11 decided in the fix PR. Then the phone audit beside the five apps.
