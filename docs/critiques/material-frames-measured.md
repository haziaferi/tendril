# Measured — the last Material frames on `main` before the fix (L6b, L15 + T3, T4, T5)

*2026-09-18 · the numbers behind `desktop-type-full.md` #3–#5 and `desktop-layout-full.md` #15,
re-counted on `main` at `a608c5c` (a code census; the report's grabs stand for the pixels). The
after-pass is `material-frames-function.md`. No mock: nothing here is a new surface — a field, a
sheet and a picker take frames the app already has.*

## L6b + T3 — Material's text field

| where | sites | what Material draws |
|---|---|---|
| `OutlinedTextField` in `shared/…/ui/` | **19** (the New sheet's *Title*, the Add task / Add habit dialogs' four, the edit sheet's title and estimate, the label and step fields, the canvas card editor and its edge label, three search/filter fields, the Reminders and Postpone counts, the phone's switcher and Day-view quick add, the canvas block's title, the mind-map node) | a **56 dp** frame with a floating label, its text and placeholder at `bodyLarge` **16 sp** — a size louder than the 14 sp rows around it (the report measured 14 px caps against 11.7) |
| `OutlinedTextField` in the Android app | **1** (the sync passphrase) | the same |
| `TendrilField` sites already | the switcher (wide), the find bar, the AI key, the desktop passphrase, the quick-add strip and popup | 36 dp / 28 dp, `body` |

## L15 + T3 — the Add task dialog

An `AlertDialog` **520 px** tall on the report's grab: a 56 dp title field, a 24 sp `headlineSmall`
title (the fixes PR put dialog titles at `heading` — the Add dialog's slot still carried Material's
padding), three switches, the *Repeats* chip row, *Cancel · Add*. The edit sheet for the same task
is a slide-over (`TendrilSheet`); the Add habit dialog has the same frame.

## T3 — the date picker

`DatePickerDialog` (9 sites in 6 files) with Material's header: *Seleziona data* as the supporting
title and the chosen day (*17 set 2026*) as a **32 sp display headline** — a 25 px cap band, the
one 32 in the app.

## T4 — clipping

**39** `Text(… maxLines = 1)` calls in `shared/…/ui/` name no `overflow` (the report's *Planned 45m*
→ *Planned*, the shelf's *Arou…*, the tray chip's *Keybo…* among them); Material's default is
`Clip`. The Week header's minutes were already ruled by L8 (hidden below a 90 dp lane) — the
ellipsis is the belt.

## T5 — the slide-over's header

`SlideOver.kt` draws the title at `heading` (14/600) — level with a page's section headings and a
hair under the bar's 18; `TendrilSheet`'s bottom-sheet form (the phone) the same.
