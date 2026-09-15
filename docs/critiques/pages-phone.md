# Critique — the Pages tab on the phone, before 14d

*2026-09-15 · `design-critique-plus` pass 2 of three. Read-only. **Judged** on device screenshots
(OnePlus 9 Pro, Android 14, dark theme, the debug build of `main` `9fa14f2`) in six states: the
list, a page (*Trip*), a database (*Errands*), the New sheet, the journal menu (tap and
long-press both open it). **Not measured:** the mock's phone shape (`pages-two-panes.html`,
under 840 px) is a sketch drawn at the desktop's Compact profile (scale 0.7875, no chips, no bar
actions), so its numbers describe nothing on the phone; a screenshot has no underlying values.
Sizes below are read from the code and from `uiautomator` bounds where the box is the element.*

## Top priorities

1. **[Med · judged]** Each card's second line repeats its icon — *Page* under a page glyph,
   *Database* under a table glyph — a line of type carrying no information on every row. Bear and
   Apple Notes put the last edit or the first line there. **Fix:** a relative *edited* time
   (`updatedAt`) or the first block's text; or drop the line and let the row be 48 dp.
2. **[Med · judged]** The `···` glyph changes between screens: horizontal on the list's bar,
   vertical `⋮` on the page's and the database's — the desktop pass's #4. **Fix:** one glyph.
3. **[Med · judged]** Icon weight on the bar: the journal book is filled, search / eye / more are
   outlined — the desktop pass's #5. **Fix:** the outlined book.
4. **[Low · judged]** The label chip floats between the bar and the first card (~12 dp above,
   ~24 dp below). **Fix:** equal padding round the chip row.
5. **[Low · judged]** *2 row(s)* with a "—" under *Done* in the Table's count row (the desktop
   pass's #10). **Fix:** "2 rows", no dashes.
6. **[Low · judged]** The New sheet's *Title* field precedes the kind rows; a tap on *Blank
   database* with the field empty names it *Untitled* (the Table shows an *Untitled* row
   already). Fine as a flow; the field could say what it is for — *Title (optional)*.

## Dimension by dimension

- **First impression** — a list of pages with a bar of four actions and a FAB; the FAB reads as
  "new page" (its label says so). Register: plain Material 3 dark — matches the phone's §2.2.
- **Visual hierarchy** — the card title leads; the kind line competes for nothing but costs
  12 dp per row (#1). The selected bottom-bar pill is the only accent on the list — right.
- **Colour and contrast** — cannot measure; by eye the kind line (grey on near-black) is the
  weakest pairing and looks above 4.5:1. The chip's outline reads; the pill's tint reads.
- **Typography** — three visible sizes on the list (bar title, card title, kind line) — clearer
  steps than the desktop's seven, because the phone has fewer registers on one screen.
- **Layout and spacing** — 60 dp card pitch (200 px at 3.0 × 1.105 — read from the dump), the FAB
  clear of the bar, the sheet's rows at 48 dp; #4 is the one break.
- **Consistency** — #2, #3; the page and database bars are the same bar (back · title · `⋮`).
- **Copy** — the New sheet's four kinds are nouns (*Blank page*, *To-do database*, *Canvas*) —
  right; the eye's description *Turn on View-Only* says the effect, not the icon — right; #5.
- **Interactivity** — the journal icon opens a two-item menu on tap **and** long-press (so
  long-press adds nothing there — fine, it is the same menu); cards have no long-press today —
  14d adds *Open · Show on Road Map · Move to Trash*, the phone's half of "right-click is the
  pointer's long-press". The FAB's touch target and the bar icons' 48 dp minimum are Compose's;
  `uiautomator` reports the 24 dp icon boxes, not the targets.
- **Fit for purpose** — a phone list with a FAB is what Notion, Bear and Apple Notes ship; the
  chips over the list are the one Tendril-specific element and they earn their place.

## What's working

- The bar's four actions are the four things the tab does; nothing is buried.
- The New sheet: title, then four kinds with icons — one screen, no wizard.
- The journal menu anchored to its icon (a popover, not a sheet — the right form for two items).

## Cannot verify

- Any pixel value: colours, font sizes, touch targets, line-height — screenshots only.
- The light theme (the device is in dark); landscape (the rail appears there from 840 dp).
- Whether the *Title* field on the New sheet takes focus and opens the keyboard.

## Disposition

#2 and #3 are the desktop pass's one-liners, folded into 14d. #1 → §0.10 item 14 (a card that
says something — with 14f's Tasks rows, which face the same question). #4–#6 → the same list.
