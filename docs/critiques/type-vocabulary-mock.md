# Critique — the type vocabulary, on the mock before the build

*2026-09-16 · `design-critique-plus` + `typography-spacing-core`, the pre-build pass for the type
PR (Inter at true weights; headers bold, not large; one spelling per kind of text), on
`docs/mockups/type-vocabulary.html` — drawn for this pass: the vocabulary table, then Settings and
the tree drawn twice, *Now* (DM Sans as the desktop actually renders it — its 400 instance only)
and *Proposed* (Inter, weight carrying the hierarchy), Ink dark and light. `craftkit ui` (0
errors, 3 warnings, 2 notes — the sprawl and the heading skip are the mock's own) and `craftkit
render` at 1000 wide, read whole. `designkit typography --medium dashboard` was run and set
aside: its search buys hierarchy with size (a Perfect Fifth, 900 headings, a 113-px display),
which is the document medium's answer, not a dense app's; its arithmetic stands — the Major
Second fits the app's six sizes at 0.04 error, leading 1.5, contrast solved by the registers.*

## The inventory the pass started from (shared UI outside `ui/theme/`)

254 sites on nine Material roles; **45 literal `.sp` sizes in 11 files**; 31 explicit weights in
14 files (4 `Bold`); the eyebrow (11 sp, +0.7 sp tracking, uppercase) typed out three times. The
same thing — a pane's header, a section's title, a card's title — spelled differently per file.

## Top priorities

1. **[Med · judged]** The table names ten roles but draws seven styles: *paneTitle*,
   *sectionTitle* and *cardTitle* are all 14 SemiBold; *fieldLabel* and *chip* are both 12.5
   Medium. Ten names for seven styles is the inconsistency the pass exists to remove. **Rule:**
   **seven styles** — `pageTitle` 18/600 · `heading` 14/600 · `body` 14/400 · `label` 12.5/500 ·
   `description` 12.5/400 dim · `caption` 11/400 dim · `eyebrow` 11/500 uppercase +0.06 em — and
   the table's *Where* column is the map from element to style, kept in the spec.
2. **[Med · measured]** *Now*'s section titles at 18 and the page title at 24 are the only
   hierarchy the desktop has had: with DM Sans at its 400 instance, every `Medium` in the app
   rendered as Regular. The mock's *Now* column is drawn that way on purpose. **Fix in the build:**
   the `variationSettings` overload (both platforms, CMP 1.12) at 400 / 500 / 600 — the eye-pass
   rule becomes 400 / 500 / 600, never 700, never thin.
3. **[Low · judged]** The eyebrow's tracking: the build types `letterSpacing = 0.7.sp`, the mock
   `+0.06 em`. At 11 sp they are the same 0.66 sp; the em form scales with the size and is the
   one to keep, once, in the style.
4. **[Low · judged]** The row's *edited 2 h ago* at the tree's right is a caption in the mock; the
   build's tree has no such column (the phone's card does) — drawn to show the style, not to add
   the column.

## Dimension by dimension

- **Hierarchy** — in *Proposed* the eye finds Settings → Density → Register by weight alone; the
  page title is the one loud thing at 18. The *Now* frame has to say it with 24 / 18 / 14 and
  still reads flat, because the weights are not there.
- **Type** — Inter's tighter apertures and taller x-height read a step larger than DM Sans at the
  same size, which is why the sizes can step down and the measure stays.
- **Consistency** — one style per kind, enforced: the audit forbids a literal `fontSize` or
  `fontWeight` in shared UI outside `ui/theme/` (the `CLOCK` tabular style moves into the theme;
  the editor's span transformation keeps `Bold` — that is content, not chrome).
- **Fit** — the editor's content is not chrome: body 16, H1 24 / H2 20 / H3 16 SemiBold, so a
  page still reads as a page; the two vocabularies meet at 14 (`body` = a row, a dialog's copy).
- **Copy** — no change.

## What's working

- The seven styles cover all 45 literal sites and the 31 weights without a new one.
- Notion's rule, measured against Notion: its settings headers are 14 semibold and its sidebar
  rows 14 regular — the proposed `heading` and `body`.

## Cannot verify

- Inter's rendering in Skia at 12.5 and 11 sp on Windows (hinting); the build measures it.
- The phone: a 14/600 heading at Touch × the phone's factor is 17 px — read on the device when
  testing resumes.

## Disposition

#1 and #3 fold into the plan; #2 is the build's first step.
