# Critique — the database's views, on the build (F4–F8)

*2026-09-18 · `design-critique-plus`, the after-pass on the `database-views` build — the desktop walk
at the user's 967 × 1100 window on *Books v12*, Ink dark, Compact, native grabs through user32; the
dev database read with sqlite3 after each write. Ground: Notion's database views as
`database-views-mock.md` measured them this morning. Read-only; nothing was left to fix.*

## Measured

| finding | before | after (this build) | on the grab / in the database |
|---|---|---|---|
| **F5** the active chip | mute; a second click opened the configure sheet | a **▾** on the selected chip; the click opens the view's two verbs — *Configure view…* · *Delete view…* (the last view's *Delete* disabled, Notion's rule); *Configure this view…* also in the `···` | the menu under *Table ▾* at the 29 dp row pitch (Notion's 29–30 px) |
| **F4** *Delete view* | at once | a confirm — *Delete the "Board" view? Its filters, sorts and column choices go with it. The rows stay.* — *Cancel · Delete view* in the error colour | the dialog **456 × 174 px** at D7's 12 dp corners (Notion's 326 × 141 stacks its buttons); *Delete view* → view 5 gone from `page_database_views`, the rows and the *Status* property untouched |
| **F7** the New view sheet | *Type: table* + *Add* | the name on the 36 dp field (the type's label as its placeholder), *Layout* as five chips with the chosen one on the tint, one line under it on what the kind needs and what the sheet adds, *Cancel · Add* at the foot — the Add task sheet's sibling | the slide-over at 440 dp; *Board* chosen → *Cards in columns, grouped by a Select property — a Status property is added when the database has none.* |
| **F6** a Board with nothing to group by | a sentence at the top-left, no verb | **never**, for a new view: *Add* on *Books v12* (a TEXT *Status*, no Select) made a Select *Status* (Not started · In progress · Done) and grouped by it — Notion's, measured; a Calendar or Timeline gets a *Date* the same way, or binds the first that exists (tested, 2 cases). The two-button prompt (`ViewNeedsPrompt`) remains for the state a later deletion leaves | `properties` 6 = *Status* SELECT `Not started,In progress,Done`; view 5's `groupByPropertyId` = 6 |
| **F6′** the rows on that first Board | — (they would have vanished: a blank cell matched no column) | a named first column, **No Status**, holds the rows whose cell is blank; a card moved out takes its option, moved in is cleared (`BoardColumn.key` ""); a value matching no option stays dropped, as §5.6 decided | *No Status · Not started · In progress · Done*, the three rows in the first (tested) |
| **F8** the two `···` menus | *Move to Trash* vs *Trash…*; no *Show on Road Map* or *Move to Trash* on a database | one vocabulary — the database's: *Show on Road Map · Save as template* │ *Configure this view… · Add property · Bound to #book… · Blocked by… · Turn off Sync to Tasks* │ *Move to Trash* + the chrome's *Show beside ▸ · Open in a window* │ *Open Trash…*; the page's keeps its verbs and ends the same way; *Trash…* → *Open Trash…* on both | the menu grabbed: twelve rows, three dividers, 29 dp each |

## Findings

1. **[Med · observed, fixed before the pass]** The first Board on an existing database showed
   **no rows** — every cell of the freshly made *Status* was blank, and a blank cell matched no
   column, so three rows vanished from view. Not the plan's rule (§5.6's *no silent fallback* is
   about a value that matches no option); an unset value is a state of its own. The named first
   column *No Status* (F6′ above) is the fix — Notion's *No Status* group is the same answer.
2. **[Low · judged]** *Move to Trash* sits before the chrome's *Show beside ▸ · Open in a window*
   on both kinds (the screen's items come first, the chrome's after) rather than beside *Open
   Trash…* as the mock drew it. The two menus agree with each other, which was F8's point; the
   mock's grouping would need the chrome to split its items around the screen's. Kept as built.
3. **[Low · not walked]** The fallback prompt (`ViewNeedsPrompt`) for a Calendar or Timeline whose
   Date property was deleted after the view was made — the same composable as the Board's, not
   reached on *Books v12* (both views have a Date). By construction.

## What's working

- A new view is never empty — the property it plots by is made with it, and the rows it did not
  yet sort are shown, named, where a person can drag them.
- The view's verbs have a home a person can see (the ▾) and a second one in the `···`.

## Cannot verify

- The phone: the New view sheet as a bottom sheet, the ▾ under Touch, the confirm — unrun.
- The Board's *Move…* menu with the *No Status* column under Touch.
- A leftover on the dev database: *Books v12* now carries the *Status* Select the walk made (the
  user's to delete or keep).

## Disposition

#1 fixed on the branch; #2–#3 recorded. Ship.
