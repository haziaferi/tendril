# Critique — the database's views, on the mock

*2026-09-18 · `design-critique-plus`, the pre-build pass over `docs/mockups/database-views.html`
(F4–F8 of the desktop audit — §0.10 item 22; PR C of the four). `craftkit ui` on the file, `craftkit
render` at 1000 × 4200, the contrast pairs computed from the registers' own hexes; the ground
measured natively this morning at 125 % — Notion's database views on a throwaway page *Views
ground* (nothing existing touched; the page is the user's to delete). Read-only; one layout slip
was fixed in the mock before it was committed.*

## Grounding — measured today (device px at 125 %)

| Notion | measured | what it settles |
|---|---|---|
| the active view's tab, clicked | a **223 px** menu at a **29–30 px** row pitch: *Rename · Display as ▸ · Edit view · Source · Copy link to view · Duplicate view · Delete view*; the only view has no *Delete view* | **F5** — the view's verbs live on its tab; Notion draws no ▾ (the click is the affordance); Tendril draws one, because the audit found the second click by accident. The last view keeps no *Delete* |
| *Delete view* | **326 × 141 px** confirm — *Delete this view?*, a red full-width *Delete view* (285 × 33), *Cancel* under it | **F4** — a confirm, as decided; Tendril's takes the app's dialog frame (D7's 12 dp corners; the buttons at the foot, the error colour on the verb) |
| `+` (add a view) | a **389 × 273 px** panel of **87 × 65 px** icon tiles, eleven layouts, no name field; the view is created at once and a **273 px** *New view* side panel opens: a **27 px** name field (*View name*), the layouts as **72 × 54** tiles with the chosen one outlined in the accent, then the view's options (Group by · Card size …) | **F7** — the name first, the layouts as chips with the chosen one on the tint, the sheet's *Add* as the verb; Tendril's one line per type is its own (Notion needs none — next row) |
| a Board on a database with no Select | **never an empty state**: a *Status* Select (Not started · In progress · Done) is created and grouped by at once | **F6** — taken as the ground, on the user's standing rule (improve on Notion, not integrate parts): a Board with no Select gets a *Status* Select made for it, a Calendar or Timeline with no Date a *Date*; the two-button state stays for the case a later deletion leaves |
| the page's `···` vs a database's | one menu for both kinds [Assumed — the top-right `···` is the page's on every kind; not opened] | **F8** |
| Tendril today | the Board's empty state a sentence at the top-left with no verb; the New view sheet two controls (*Type: table*, *Add*, lowercase enum names); the active chip mute; *Delete view* at once; two `···` vocabularies | F4–F8 |

## Findings

1. **[Med · measured, applied to the mock]** The F6 fallback's empty state rendered its sentence
   and its two buttons on one line (the flex ground had no column direction) — the mock's own slip;
   stacked now: the sentence, the type's line, the two 28 dp pills. **Binding on the build:** the
   empty state is the tab root's centred `description` with two `BarPillButton`s, never a
   top-left sentence.
2. **[Low · measured]** Every pair clears its floor on both registers: the ▾ on the selected chip
   (`dim` on `surface2`) 4.69 : 1 Ink / ≥ 4.6 Chalk — at the 11 px glyph the 3 : 1 graphics floor is
   what applies; *Delete view* in the error colour on the dialog's ground 5.41 Ink / 5.35 Chalk;
   the empty state's `dim` on the ground 5.36 / 5.01. Nothing to solve.
3. **[Low · judged]** The New view sheet's line under the chips says two things — what the type
   needs and what the sheet will add (*A Status property is added when the database has none*).
   Notion's side panel says neither; the line is the one place a person learns the rule before the
   property appears in their Table. Kept, one sentence, `description`.
4. **[Low · judged]** The F8 menus are long on a database (eleven rows, three dividers). The order
   — the page's verbs, the panes, the kind's, then Trash — makes the shared half read the same on
   both kinds; a shorter database menu would hide *Add property* or *Blocked by…*, which the audit
   did not ask for. Kept; measured on the build against the 29 dp row rule.
5. **[Low · judged]** The dialog's verb is *Delete view* in the error colour, as Notion's is red;
   the app's other confirms (*Delete*, *Move to Trash*) already use the error family for the
   destructive verb (14g·2). Consistent.

## What's working

- One rule closes F6 on the ground instead of a sentence: the property the view needs is made with
  the view, so the Board and the Calendar view never open empty.
- F7's sheet is the Add task sheet's sibling (PR B): the same frame, the same field, the same foot.

## Cannot verify

- Notion's page-vs-database `···` (not opened; [Assumed] one menu).
- The Table's chip row at the shelf's 280 dp with the ▾ added — measured on the build.
- The phone: the New view sheet as a bottom sheet, the chip's ▾ under Touch — unrun (pending as
  every PR since #82).

## Disposition

#1 applied to the mock and binding; #2–#5 recorded. Build.
