# Critique — the Pages tab on a wide window (desktop), before 14d

*2026-09-15 · `design-critique-plus` pass 1 of three (plan: critique passes around 14d). Read-only.
**Measured** on `docs/mockups/pages-two-panes.html`, option B at 1200×800 (scale 0.900 = 1.000 ×
Compact 0.9), by walking the rendered frame's computed styles — `craftkit ui` audits only the
mock's own chrome, and `craftkit render` has no rasteriser on this machine. **Judged** on the
desktop app at `main` (`9fa14f2`) in four states: tree + empty, tree + a page (*Escape test*),
tree + a database (*Books v12*), tree collapsed. Measured and judged findings are labelled.*

## Top priorities

1. **[High · judged]** Row-as-page property strip, *Blocked by* — a RELATION value renders as its
   raw uid (`57dbf97c-3ba7-…`) in an editable text field: `RowUnboundEditor`
   (`PageDetailScreen.kt:1295`) has no `RELATION` branch, so the value falls into the text editor.
   Unreadable, and a keystroke corrupts the relation. **Fix:** a `RELATION` branch — the related
   rows' titles, read-only, "—" when empty; editing stays in the Table where the picker lives.
2. **[High · measured]** The row's hover `···` (`.row .more`) is 14.4 px = **16 dp**, the expand
   chevron 14 × 12 px; at the 0.85 minimum scale 13.6 px — under the 24 px pointer floor. 14d's
   plan says 24 dp, which is 20.4 px at 0.85, still under. **Fix:** a 28 dp target with an 18 dp
   glyph (23.8 px at the minimum), and the whole row as the right-click target so the glyph is a
   hint, not the only door.
3. **[Med · judged]** Two bar heights meet at the divider: the tree header is 44 dp, the page bar
   52 dp, so their hairlines miss by 8 dp in every state with a page open. **Fix:** the tree header
   at the page bar's height, so one hairline runs across both panes (Obsidian aligns them).
4. **[Med · judged]** The `···` glyph changes with the state: horizontal `•••` on the empty-state
   bar, vertical `⋮` (Material `MoreVert`) on the page's and the database's bars. **Fix:** one
   glyph on the tab — `MoreHoriz`, the mock's.
5. **[Med · judged]** Icon weight: the journal icon is a filled book among outlined glyphs, on the
   tree header here and on the phone's bar. **Fix:** the outlined variant.
6. **[Med · measured]** Seven text sizes within 5 px — 10.3 / 10.8 / 11.3 / 11.7 / 12.2 / 12.6 /
   15.3 px — `craftkit` reads the scale as a minor second; only the page title separates from the
   rest. → **14g** (the registers own type); not touched here.
7. **[Med · judged]** Density differs across the divider: tree rows are 32 dp, the Table's rows
   ~64 dp under the same Compact profile — the table still wears its touch height. → **14f**.
8. **[Low · measured]** The canvas caption *Mind map · tap to open* is `#959389` on `#f3f4f6` =
   **2.80:1** at 9.4 px (needs 4.5), and says "tap" on a desktop. **Fix:** `onSurfaceVariant`,
   "Mind map · open".
9. **[Low · judged]** Rhythm above the rows: the label chip floats — ~16 dp above it, ~28 dp below
   before the first row; header, chip and row start at 14 / 12 / 18 dp. **Fix:** equal padding
   round the chips and the chip's left edge on the row icon's.
10. **[Low · judged]** The Table's count row *2 row(s)* with "—" under every column reads as a data
    row. **Fix:** "2 rows", no dashes. (Same on the phone.)

## Dimension by dimension

- **First impression** — a notes app with a sidebar: tree left, page right, rail far left. The
  purpose reads without text. Register: quiet, Bear-like; matches §2.2.
- **Visual hierarchy** — the page title (15.3 px / 500) is the one clear entry point; the tree's
  *Pages* header (12.2 px / 500, `#1b1b18` in the mock) sits at the rows' size and reads as a
  row, not a pane name — acceptable in option B, where the header is a toolbar, but the app draws
  it in `onSurfaceVariant`, weaker than the mock. The selected row's `accentSoft` is the tree's
  only accent — right.
- **Colour and contrast (measured)** — 11 colours in the frame. Every text pairing passes AA
  except the canvas caption (#8): rail labels 4.93:1 (unselected) / 15.7:1, chips 5.43–7.75:1,
  rows 17.3:1, dim copy 5.43:1.
- **Typography (measured)** — see #6. Weights: 400 body, 500 for titles and the header; tracking
  none. Line-height not measured (Compose).
- **Layout and spacing** — the split is on the mock's geometry (tree 252 px = 280 dp at 0.9); the
  hairline mismatch (#3) and the chip rhythm (#9) are the two grid breaks.
- **Consistency** — #4, #5; radii not measured on the app.
- **Copy** — the empty state names both ways in (*Choose a page from the tree — or press Ctrl+K
  to find one*): right. #8's "tap", #10's "row(s)".
- **Interactivity** — hover-revealed `···` is a pointer-only affordance by design (B§13.4 14d's
  stated trade-off; a touch-screen laptop never sees it) — so the row itself must carry the
  right-click, and the phone's long-press the same menu (14d does both). The resize handle
  (7.2 px in the mock, 12 dp astride the divider in the app since 14c) changes no cursor in the
  mock; the app sets none either (`PointerIcon` — 14d adds the resize cursor). No focus ring on
  rows (keyboard is 14e).
- **Fit for purpose** — the Obsidian/Bear split holds: nothing on the tree acts on the page and
  vice versa; the collapse chevron sits beside the divider it collapses.

## What's working

- One owner per control, as chosen: the tree's `+`, search, journal, chevron on the tree; eye and
  `···` on the page.
- The empty state's copy; Escape closing to it; the tree's current row as the sole accent.
- Contrast: every running-text pairing passes with room.

## Cannot verify

- The app's own pixel values (colours, sizes) — measured on the mock, judged on the app; the
  app's row and bar heights are read from the code (14c's numbers), not the screen.
- Hover and focus states (none exist yet — 14d builds them).
- The light theme only; the desktop has no dark theme until 14g.

## Disposition

#1 fixed in 14d (a defect the pass found; small). #2 folded into 14d's design (28 dp target).
#3, #4, #5 are one-line changes on lines 14d edits anyway (the tree header, the bars' `···`, the
journal icon) — folded in and said so in the PR. #6 → 14g, #7 → 14f, #8–#10 → §0.10 item 14's
"small things" list, to close with 14h.
