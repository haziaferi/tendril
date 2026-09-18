# Critique — the last Material frames, on the build (L6b, L15 + T3, T4, T5)

*2026-09-18 · `design-critique-plus`, the after-pass on the `material-frames` build — the desktop
walk at the user's 967 × 1100 window, Ink dark, Compact, native grabs through user32, pixel-read;
the code census re-run. Read-only; one finding (pre-existing, High) was fixed on the branch before
this pass closed.*

## Measured

| finding | before (`main`) | after (this build) | on the grab |
|---|---|---|---|
| **L6b** Material text fields | 19 in `shared/…/ui/` + 1 in the Android app, 56 dp at 16 sp | **0** — every field is a `TendrilField` (36 dp under a pointer, 48 under Touch, `body`); `readOnly`, `minLines` (the card editor grows from three lines inside the same frame) and the keyboard options joined the String form; **audit rule 18 *material field*** | the Add sheet's *Title* field: border rows 114 → 149, **36 px** |
| **L15** the Add task / Add habit dialogs | `AlertDialog`, 520 px | `TendrilSheet` — a **slide-over** on a wide window (Ctrl+Shift+N opens it as before), a bottom sheet on the phone; *Cancel · Add* at the foot | the sheet at the slide-over's 440 dp, the title *Add task*, the field, two switches, *Repeats* chips, *Has deadline* |
| **T3** the dialog's title | 24 sp | the sheet's title (T5's `pageTitle`, 18/600) | the title's text band **13 px** tall (an 18 sp cap at ≈ 1.01 px/dp; a 14 would be ≈ 10) |
| **T3** the date picker's headline | 32 sp display + a supporting title | `TendrilDatePicker` — *Pick a day* at `heading`, one line, no headline, no mode toggle; the nine `DatePickerDialog`s share it | the picker opened from *Has date*: the heading, a hairline, *settembre 2026 ▾*, the grid; the 12 dp corners from D7 |
| **T4** one-line texts without an ellipsis | 39 | **0** — `overflow = Ellipsis` on every `maxLines = 1` call; **audit rule 19 *clip*** reads the call, not the line (ten of the 39 spread their arguments over several lines) | — |
| **T5** the slide-over's header | `heading` 14/600 | **`pageTitle`** 18/600 on both sheet forms (the bottom sheet's title too — one title, one style); annotated `// type: BAR_TITLE` (a sheet's header is the panel's title bar; the classifier's SHEET_HEADER class keeps `heading` for the tray's, the hover card's and the F1 card's rows) | *Add task* at 13 px caps beside the bar's *Tasks* |

## Findings

1. **[High · observed, pre-existing, fixed before the pass]** **A slide-over's × sat exactly under
   Windows' ✕.** Since L5 the window's caption buttons are painted over the top-right corner of
   everything, and a full-height panel at the right edge put its own close control — or a
   titleless sheet's first row — under the app's close button: a miss by a few pixels hid the
   app (× to the tray) instead of closing the sheet. Seen on the Add sheet's first grab; every
   slide-over since 2026-09-17 had it. **Fix:** the panel keeps its height and its content starts
   under the title bar's row (`TOP_BAR_HEIGHT`) where a title-bar handle exists — Notion's side
   peek starts under its top bar the same way; on Android and a runtime without the API nothing
   moves. Verified: the Add sheet's header 52 dp down, its × clear of ✕.
2. **[Med · observed, fixed]** With a title alone, Material's `DatePicker` keeps its 120 dp header
   and leaves the headline's room blank (a 90 px hole between *Pick a day* and the month row on the
   first build). The heading rides in the headline slot instead (`title = null`); the header is the
   one line.
3. **[Low · recorded]** The shelf's graph header (*Around …*) still ellipsises early at the shelf's
   280 dp: its depth chips are already three 28 dp text chips (the report's fix), so the title's
   room is what the shelf's width leaves — `pages_shelf_width` is the person's; no change. The tray
   chip's *Keybo…* is the same arithmetic (the tray ≤ 30 % of the pane).
4. **[Low · judged]** The Add sheet's labels (*Has date*, *Repeats*) now declare their styles
   explicitly (`body`, `label`) — outside a dialog's slot the classifier reads them as titles and
   sub-sections, which is what they are; nothing moved on screen.

## What's working

- One field frame everywhere: the last twenty Material fields fell to one composable with three
  parameters added, and rule 18 keeps it so.
- The Add sheet reads as the edit sheet's sibling — the same panel, the same field, the same foot.

## Cannot verify

- The phone: the Add sheet as a bottom sheet, the fields at 48 dp under Touch, the passphrase
  field on Android — compiled, unrun (pending as every PR since #82).
- The nine date pickers beyond the Add sheet's — the same composable, not each opened.

## Disposition

#1 and #2 fixed on the branch; #3 and #4 recorded. Ship.
