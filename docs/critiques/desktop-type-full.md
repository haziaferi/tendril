# Critique — the desktop, full type pass (2 of 3)

*2026-09-17 · `design-critique-plus`, pass 2 of the desktop audit, over the same grab set as
`desktop-layout-full.md` (Ink dark, Chalk light; the user's 967 × 1039 window at 125 % and
Compact 0.85 → 1.0625 device px per sp) plus a read of every `MaterialTheme.typography.*` site
in `shared/…/ui/`. Dimension 1c, and 1g where the words themselves are the finding. **Measured**:
text bands by pixel projection against the scale's expected device sizes (11 · 12.5 · 14 · 16 ·
18 sp → 11.7 · 13.3 · 14.9 · 17 · 19.1 px; a band with ascender and descender ≈ 0.98 em, caps
only ≈ 0.73 em), weights by eye on both grounds, clipping in every title slot. **Judged**: the
assignment — the right style in the right place — and whether seven are enough. Phone not walked.*

## Top priorities

1. **[High · measured (the code)]** **Two spellings coexist.** The type PR promised *one
   spelling per kind of chrome text*; the count says otherwise — **113** sites read the
   vocabulary (`body` 57, `description` 16, `heading` 13, `caption` 13, `label` 7, `eyebrow` 4,
   `pageTitle` 3, `clock` 2) and **204** still read Material's raw roles (`bodyMedium` 58,
   `bodySmall` 47, `labelLarge` 34, `titleMedium` 21, `labelSmall` 19, `labelMedium` 14,
   `titleLarge` 6, `titleSmall` 3, `bodyLarge` 2). The sizes agree (the roles were mapped onto
   the same scale), so nothing is *visibly* wrong today — but `labelSmall` serves as an eyebrow
   (11/500) in one file and as a plain caption in another, `titleMedium` is a section heading in
   Settings and a card title in a hover card, and the next author has no rule to follow. The
   audit's *literal type* rule refuses `fontSize =`, not a role name. **Fix (mechanical, one
   PR):** the sweep — every `bodyMedium` → `body`, `bodySmall` → `description` (or `caption`
   where it is a count / a time / *edited 2 h ago*), `labelLarge` → `label`, `titleMedium` →
   `heading`, `titleLarge` → `pageTitle`, `labelSmall` → `eyebrow` where uppercase, else
   `caption`, `labelMedium` → `caption`, `titleSmall` → `label` — then **the audit rule**: a
   Material role name (`typography\.(body|label|title|headline|display)\w+`) in `shared/…/ui/`
   outside `ui/theme/` fails the run. The editor's three content sizes are already named in
   `Type.kt` and stay.
2. **[High · judged, 1g]** **Copy that is not the app's.** *(s)* plurals in six places
   (`HistorySheet.kt:85` *1 block(s)*, `JournalTodayStrip.kt:69` and `TasksHabitsScreen.kt:777`
   *Every 1 day(s)*, `HabitTrashSheet.kt:166`, `RoadMapScreen.kt:217` and
   `RoadMapNeighbourhood.kt:77` *hop(s)*, the recurrence unit buttons at
   `PageDatabaseScreen.kt:1374` / `PageDetailScreen.kt:1505` *day(s)*, the formula checker's
   *argument(s)*); **raw enum names** in the New view sheet — *Type: table* and a menu of
   *table · board · gallery · calendar · timeline* (`PageDatabaseScreen.kt:619–627`, and again at
   `:1439`), *"Read on" is currently date* (`:997`); **spec citations in the UI** — *(§0.6.14)*
   in the task pane's note (`TaskDetailPane.kt:142`) and *tendril-windows-spec.md §1* in the
   Calendar settings sheet (`Main.kt:363`). 14h·2 fixed *row(s)* and left the rest. **Fix:**
   `rowsLabel`'s shape for each (*1 block · 2 blocks*, *Every day · Every 3 days*, *1 hop*),
   `ViewType.label` / `PropertyType.label` (Title-case, `"Table"`), and the two citations struck
   — a spec reference belongs in the code's comment, never in a sentence a person reads.
3. **[Med · measured]** **Material's own type leaks through five components.** The
   `OutlinedTextField` sets its text and placeholder at `bodyLarge` **16 sp** (measured: the
   switcher's field, the quick-add strip's *Dentist fri 14:30 !*, the New sheet's *Title*, the
   API key field, the label sheet's *Label name* — 14 px caps bands, the 17 px font) beside
   rows at 14 — every field is a size louder than the list it sits in. The `AlertDialog` title
   (*Add task*) is `headlineSmall` **24 sp** (19 px band): a dialog's title bigger than the page
   title's 18. The `DatePickerDialog` headline (*17 set 2026*) is Material's 32 sp display (25 px
   band) with *Seleziona data* as its supporting text — the one place a 32 appears in the app.
   `SegmentedButton` and `FilterChip` labels are `labelLarge` = `label` 12.5/500 (✓ by the map).
   **Fix:** `TextStyle` on every field (`textStyle = MaterialTheme.typography.body`, the
   placeholder the same at `onSurfaceVariant`) — or the find bar's `BasicTextField` frame, which
   already takes `body` (L6/L7 in the layout pass make the switcher and the strip that frame);
   the Add task dialog's title `heading` (or the dialog becomes a slide-over, L15); the date
   picker's headline is Material's and stays — one 32 in one picker is a known cost, said here.
4. **[Med · measured]** **Titles clip instead of ellipsising, or ellipsise too early.** The
   Week header's second line — *Planned 45m* — is cut to *Planned* by the 66 px lane
   (`WeekGridView.kt:174`, `maxLines = 1`, no `overflow`), and reads as a label with no value;
   the shelf's header title ellipsises at four characters (*Arou…*, *journal/2026-…*) because the
   depth chips and two buttons take the row; a tray chip clips to *Keybo…* (✓ ellipsis, but at
   five characters). **Fix:** `overflow = TextOverflow.Ellipsis` on every one-line `Text` in a
   header or a chip (an audit rule: `maxLines = 1` without `overflow` in `ui/` fails); the
   header's minutes hidden below a 90 dp lane; the shelf's depth chips as text (*1 2 3*) so the
   title keeps ≥ 120 dp.
5. **[Low · judged]** **Is a `title` between `pageTitle` 18 and `heading` 14 wanted?** Read on
   the grabs: the slide-over's header at `heading` 14/600 (History, Trash, Reminders, Postpone)
   sits level with a page's section headings and a hair under the bar's 18 — it reads as a
   section of the page behind it, which a modal is not. Notion's side-peek title is its page
   title size. **Recommendation:** no eighth style; the slide-over's header takes `pageTitle`
   (it *is* the panel's title, and the panel is a page-sized surface), the hover card and the
   shortcuts card keep `heading` (they are cards). One line each in `SlideOver.kt` and §2.3's map.
6. **[Low · measured]** **The eyebrow is used four times** (UNSCHEDULED / OVERDUE, STEPS, the
   shortcuts' groups) and its tracking reads well at 11/500 on both grounds; Settings' section
   titles, the tree's *Pages* (`PagesWorkspace.kt:260`) and the tray's *Tasks* (`TaskTray.kt:98`)
   are all `heading` 14/600 — right by the map, and the three pane headers agree. The one
   eyebrow that is not an eyebrow: the Table's column header row (*Title · Author · Done · Read
   on*) reads as `caption` in `onSurfaceVariant` — Notion's column headers are the same, so no
   change; said here so the sweep does not "fix" it.

## Measured — bands on the scale (device px, Ink dark; Chalk light identical)

| text | style expected | expected font px | measured band | reads as |
|---|---|---|---|---|
| a tree row title (*Books v12*) | `body` 14/400 | 14.9 | 14 (asc + desc) | 14 ✓ |
| a Tasks row title | `body` | 14.9 | 12–14 | 14 ✓ |
| the page's title (*Escape test*) | `pageTitle` 18/600 | 19.1 | 18 | 18 ✓ |
| a menu item (*Find in page*) | `body` | 14.9 | 13 | 14 ✓ |
| the eyebrow (*UNSCHEDULED · 2*) | `eyebrow` 11/500 caps | 11.7 | 9 (caps) | 11.5 ✓ |
| a Settings chip label (*Compact*) | `label` 12.5/500 | 13.3 | 12 | 12.5 ✓ |
| a field's text (*Dentist fri 14:30 !*) | `body` wanted | 14.9 | 14 (caps, no desc) | **17 → 16 sp** ✗ #3 |
| the New sheet's *Title* placeholder | `body` wanted | 14.9 | 14 (caps) | **16 sp** ✗ #3 |
| the Add task dialog's title | `heading` wanted | 14.9 | 19 (asc) | **24 sp** ✗ #3 |
| the date picker's headline | — (Material) | — | 25 (caps + desc) | **32 sp** ✗ #3 |
| the Week's day header second line | `caption` 11 | 11.7 | 9 (caps) | 11 ✓ (clipped, #4) |

No band lands off the five sizes except the four Material defaults in #3. Regular / Medium /
SemiBold are three visibly different weights at 14 and at 12.5 on both grounds (Settings'
section titles against their descriptions; a chip's label against a row) — the type PR's true
instances hold. Multi-line `description` (Settings' explainers, the task pane's note) runs at
17 px line pitch — ✓ the 12.5/17 pair.

## Judged — the assignment, site by site

- **Right.** The tree (PAGES `heading`, rows `body`, the count and dates `caption`); the Tasks
  list (title `body`, meta `caption`); the task pane (title `pageTitle`, labels `label`, values
  `body`, unset values `description` italic, STEPS `eyebrow`, the note `description`); the tray
  (`heading`, `eyebrow`, `body`, `description`); the shortcuts card; the hover card (`heading`,
  `description`, `caption`); Settings (sections `heading`, explainers `description`, chips
  `label`); the F1 chips (`label` on `surfaceVariant`); the Trash rows (title `body`, *was in:
  Pages* `caption`); the History rows.
- **Wrong style, right size.** The Board's empty-state message at `body` top-left where the
  Table's *3 rows* footer is `caption` — an empty state is a `description`, centred, with its
  verb as a button (the function pass has the rest). The Calendar's *Planned 2h 15m* under the
  Day's date is `caption` (✓) but the Week header's is `labelSmall` (#1's family).
- **Missing.** Nothing wants an eighth style once #5 is decided. The editor's H1/H2/H3 (24 / 20 /
  16 SemiBold) are content and were not measured beside Notion again here (the type PR did).
- **Case and copy.** *Delete view*, *Move to Trash*, *Trash…*, *Add row*, *Add block*, *Add
  label*, *Add a step* — verb-first (✓ 1g); *Type: table* is not (#2). The Calendar settings
  sheet's first paragraph is 46 words with a file name in it (#2). The tray's chips carry *due
  dom 13* in the locale's short day — ✓ consistent with the Day view's *giovedì, settembre 17*.

## Cannot verify

- The eyebrow's tracking in px (the projection measures height, not letter-spacing) — read as
  correct by eye on both grounds.
- The phone's Touch registers (the same styles at 1.23) — not walked.

## Disposition

#1 and #2 → the fix PR (the sweep with its audit rule; the copy); #3–#6 → §0.10's small-things
list as T3…T6 (T3's field frame rides with the layout pass's L6/L7 if those are taken).
