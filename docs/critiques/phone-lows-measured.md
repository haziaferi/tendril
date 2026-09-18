# Item 23's Lows — the phone audit's twelve, measured after

*2026-09-18 · the after-pass on the `phone-lows` build, the OnePlus (3 px/dp), `uiautomator`
dumps as in `phone-fixes-2-measured.md`; the desktop's three shared changes grabbed natively at
the user's 967 × 931 window. Closes §0.10 item 23 — the catch-up's P7–P10 and the audit's
T·P3–T·P5, L·P4–L·P6, F·P2, F·P3, F·P5. No mock: every surface exists; the grounds are the
audit's, one re-read live (Notion's phone app, for L·P4 — see the row).*

## The one finding that was not a one-liner — T·P3

The audit measured `pageTitle` at **25 dp in the top bar and 22 in a sheet's title** and called
it a line-height defect. Two causes, both general, neither a site:

1. **A style without a `LineHeightStyle` trims its first and last lines.** `typographyFor`
   built raw `TextStyle`s, so a one-line `Text` measured the font's own height (~1.21 em: 22 dp
   at 18 sp, 24 at 20) while a text field's line took the declared height. Material's roles
   carry `LineHeightStyle(Center, Trim.None)`; `chromeStyle` does now, with Material's platform
   style (no font padding). A line is its style's line height on both platforms — the sheet's
   title went 24 → 26 dp on the phone at once.
2. **Android's windows do not inherit the shell's scale.** `WorkbenchEnvironment` multiplies
   `LocalDensity` by `shellScaleFor` (0.85 × 1.23 = **1.046** on a phone); a `ModalBottomSheet`,
   a `DropdownMenu` and every `AlertDialog` are Android windows that start from the platform's
   density — so every sheet, menu and dialog on the phone drew **4.6 % smaller than the page
   behind it** (a menu row 48 dp against the list's 50; a sheet's 20 sp title 78 px against the
   bar's 82). Fixed at the root: `MainActivity.attachBaseContext` applies the same scale to the
   activity's `densityDpi`, so every window it opens is scaled alike, and the environment scales
   nothing on Android (`platformScaled`). The desktop's windows inherit the composition's
   density and keep scaling in the shell. A tablet (shorter side ≥ 800 dp → ×1.23) would have
   shown the gap at 23 %; nothing measured it.

## Measured — before → after

| finding | before (the audit) | after | ground |
|---|---|---|---|
| **T·P3** `pageTitle` at two heights | bar 25 dp · sheet 22 (at 18 sp); on #110's build bar 82 px · sheet 72 · a menu row's text 72 vs a card's 76 | the bar's title **82 px**, the sheet's title **82**, a menu item's text **76** = a card's **76**: one style, one height, in and out of a window (26 / 24 dp × 1.046) | Material's rule |
| **T·P4 / F·P3** the Timeline's initials | *S S M T W T F* (the enum's English) beside the Month header's *L M M G V S D* | **S D L M M G V S D L** for 12–21 Sep — `weekdayInitial(day, locale)`, `NARROW` upper-cased, the Month header on the same function; tested in Italian and English | the Month header |
| **P10** the Day header's order | *venerdì, settembre 18* (a hand-written `EEEE, MMMM d`) | ***venerdì 18 settembre*** — the header is `rangeTitle(DAY)`, the desktop bar's title, tested in Italian | the locale |
| **L·P5** the Postpone sheet's unit chips | five `FilterChip`s wrapping beside the 80 dp count field (*weeks* on a second row); the desktop's scrolling row (`small-things-3-function.md` #3) | a `days ▾` menu button beside the count (`BarMenuButton` + `TendrilMenu`, 40 dp under Touch, 28 under a pointer) with the five units and a check — one line on the phone (*2 · days ▾*) and in the slide-over; the desktop's #3 closes with it | Google Calendar's, Todoist's, TickTick's custom offsets are a number and a unit menu |
| **L·P6 / P8** the Day view's preview chips | the chip row at x = 0 while the field sits at 16 dp | the row inset 16 dp: *Event* at **x 88 px**, the field's own left | the field |
| **F·P2** a long-press wrote a revision | a long-press on a block's text (a word selected) → History gained *just now · before an edit* | History unchanged after a long-press on *Trip*'s *Packing* (the newest still *1 h ago*): the field's `onValueChange` writes only when the **text** changed — a selection alone reported the same text, and the write captured a revision, re-stamped the page and rebuilt its FTS row, on both platforms (a double-click on the desktop selected the same way) | — |
| **P7** *Opens on* and *Restore backup* wrap | four 48 dp chips in 288 dp — *Agenda* broke inside its chip; *Restore backup* broke inside its button | `FlowRow`s: *Day · Week · Month* then *Agenda* whole on a second row; *Export · Import* then *Restore backup* whole | — |
| **P9** the find bar's hints | *Previous (Shift+Enter)*, *Next (Enter)*, *Close (Esc)* to a finger | **Previous · Next · Close** under Touch; the keys named under a pointer only | — |
| **L·P4** the row page's strip | "a loose list — the value 20 dp in"; Notion's mobile strip [Assumed] 45 dp rows | already L·P3's: every property a **48 dp** row (150 px scaled), the label column 16 → 120 dp, the value beside it at the same x on every row; Notion's phone row page was not reached in three tries (its calendar view opens the item's title for editing) — its form is asserted: name left in muted text, value beside. No further change; closed by L·P3's measurement | [Assumed] |
| **T·P5** | = F·P4, fixed in #110 (the sheets' *ven 25*) | the pane's *When* and *Deadline* on the desktop were still ISO (*2026-09-16*) — `dayLabel` there too, the same rule | the rows |
| **F·P5** a swipe on the Road Map's filter row set a focus | could not be reproduced on purpose in the audit | read, not changed: the filter row sits above the canvas in the bar's `Column`, not over it; a node's tap selects it (a ring), a focus comes only from *All Pages* and *Show on Road Map*. Recorded as *cannot reproduce*; nothing to fix by reading | — |

## Walked
The phone: the bar, a card, a menu, the Postpone sheet measured (the T·P3 row); Calendar → the Day header, `Dentist fri 14:30` → the chips at the field's edge, cleared; `···` → *Calendar settings…* → *Opens on* on two rows; Settings → the three backup buttons; Pages → *Errands* → Timeline → the initials; *Trip* → History (two revisions) → a long-press on *Packing* → History (the same two) → *Find in page* → the three labels. The desktop (the shared changes): Tasks → *Pane task* → *Postpone…* → the slide-over with *2 · days ▾* → the menu of five; rows 28 px at this 931-px-tall window (29 dp × 0.79 × 1.25 — the scale's own number, not a change: the earlier 30 px were at 1039).

## Recorded, not changed
- F·P5 — cannot reproduce; the code offers no path.
- The desktop's text bands are their line heights now (body 20 dp, caption 16, pageTitle 24) where they were the font's natural height; every measured pitch sits on a `heightIn(min)` floor and did not move (rows 28 px at 931 tall, menus 29).

## Cannot verify
- A tablet at ×1.23 (none); the widgets (a separate context — untouched); the phone's IME toolbar on the long-press (Compose's text toolbar is not in a `uiautomator` dump — the field was focused, History did not grow).

## Disposition
Ship. §0.10 item 23 closes.
