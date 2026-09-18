# The phone audit — type, every surface

*2026-09-18 · pass 2 of 3 (dimension 1c) over the same dumps and grabs as `phone-layout-full.md`.
**Measured**: a text node's height is its line box, so the bands below are the styles as the
phone renders them (3 px/dp); the grounds' bands the same way. **Judged** from the grabs.*

## Measured — the bands

| band (dp) | the style it is | where | count across the dumps |
|---|---|---|---|
| **14** | `caption` / `eyebrow` (11 sp) | the rail's labels, *REMIND ME … BEFORE*, the Timeline's initials, the Month's weekday letters | ≈ 5 on every screen |
| **16** | `description` / `label` (12.5 sp) | rows' second lines, chip labels, *edited 5 days ago*, the row page's property labels | 1–10 |
| **18** | `body` (14 sp) | every row title, the segmented buttons, dialog text | 1–43 |
| **25** | `pageTitle` (18 sp) | the top bar's title | 1 |
| **15 · 17 · 22** | Material's own line boxes | menu rows (17), sheet rows and chips (15 / 17), the sheet's title (22 — the same `pageTitle` as the bar's 25) | every sheet and menu |
| 31 · 34 · 36 · 39 · 44–52 | fields and multi-line texts | `TendrilField` 48–50, the passphrase, explainers | — |

| ground · primary text | band | ≈ sp | the second line |
|---|---|---|---|
| Todoist's row title | 23 | **17** | 20 dp ≈ 14 sp |
| TickTick's row title | 22 | **17** | 14 sp at the right |
| Notion's list title | 24 | **16** | — |
| Notion's menu row | 22 | 16 | — |
| Todoist's detail sheet rows | 24 | 16–17 | — |
| **Tendril's row title** | **18** | **14** | 16 dp ≈ 12.5 sp |

## Findings

1. **[High · measured]** **The phone's primary text is two to three points under every ground.**
   A row title is `body` 14 sp (an 18 dp box) where Todoist and TickTick set 17 and Notion 16;
   a row's second line 12.5 where the grounds set 14. The seven-style vocabulary was cut against
   Notion's desktop (14 / 12.5 measured there) and the Touch profile inherits it unchanged — the
   desktop's density lever (`DensityProfile.factor`) scales *sizes*, not type, and the phone's
   factor is 1. Material's own list body is 16. **Decision needed**: A (recommended) — the Touch
   profile scales the vocabulary by ≈ 1.14 (body 16 · description 14 · caption 12.5 · label 14 ·
   heading 16 · pageTitle 20; the editor's 16 / 24 / 20 stays), one number in `Type.kt` read from
   the profile, the audit's *literal type* rule untouched; B — keep 14 / 12.5 and accept the
   phone reads smaller than every app beside it.
2. **[Med · measured]** **Two type families on one screen.** The app's rows read at 14 / 16 / 18 dp
   bands; every Material component under Touch — `DropdownMenuItem`, `FilterChip`, `Switch`
   labels, the sheet's own rows — reads at 15 / 17 dp bands (Material's 14 sp on a 20 sp line
   with its own padding). Side by side in the Tasks tab: the segmented buttons at 18, the rows at
   18, the long-press menu at 17. Under a pointer the audit's fixes gave menus the app's row;
   under Touch they are Material's. A 1 dp difference the eye reads as *slightly off*. Folds
   into decision 1 if the Touch scale lands on Material's 16.
3. **[Low · measured]** The same `pageTitle` renders at 25 dp in the top bar and 22 in a sheet's
   title — two line heights for one style (the sheet's `Text` sits in a different `ProvideTextStyle`
   context). One line height.
4. **[Low · judged]** The Timeline's weekday initials are English (*S S M T W T F*) on an Italian
   phone whose Month header reads *L M M G V S D*; the Day header reads *venerdì, settembre 18*
   (P10) — three spellings of a weekday on one tab.
5. **[Low · judged]** The edit sheet's *When* reads *2026-09-25* — the one ISO date left after P6
   (the tray, the rows and the switcher say *ven 25*).

## What's working

- One weight ladder (400 / 500 / 600) on the phone as on the desktop; nothing bold; the eyebrow
  tracked; the ellipsis rule (T4) holds on every one-line title measured.
- The type-class audit keeps every site on a named style — the phone inherits the desktop's
  discipline for free; the size is the only question.

## Cannot verify

- Contrast on the phone's dark mode and on the other nine registers (the JVM test covers the
  floors; the grab was Ink light).

## Decisions needed

1. **The Touch type scale** — A (recommended) ×1.14 as above; B keep.
2. Whether Material's components under Touch take the app's row (menus at 56 / the profile's
   height with `body` text, as under a pointer) — A (recommended): yes, one `TendrilMenuItem` on both
   profiles once the Touch scale is set; B: leave Material's.
