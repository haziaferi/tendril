# Critique — the keyboard's two visible states on the shell mock, before 14e

*2026-09-15 · `design-critique-plus`, pre-build pass for 14e (the keyboard). Read-only. 14e is
mostly invisible — bindings — so this pass covers the two things it draws: the **shortcuts
overlay** (`help()` in `docs/mockups/desktop-shell.html`) and the **keyboard cursor in a list**
(`.row.kb` plus the `.kbhint` type-ahead caption). **Measured** by walking the rendered frame's
computed styles at 1200×800 (no rasteriser; `craftkit ui` sees only the page chrome); **judged**
on the frame's composition. The phone changes nothing visible in 14e — no phone pass; a hardware
keyboard on the phone runs the same shared list code and is exercised in the function walk.*

## Top priorities

1. **[High · measured]** The overlay's `kbd` chips — the shortcuts themselves — are 11 px at
   **3.08:1** on white (`--faint` on `#fff`; need 4.5). The one thing the overlay exists to show
   is its least legible text. **Fix:** `onSurface` on `surfaceVariant` for the chip, 12 sp.
2. **[Med · judged]** The list is not the set. It carries *Find in page · Ctrl+F* (decided
   2026-09-15: its own PR after 14e), *Settings · Ctrl+,* beside *Ctrl+1…5* (Settings is
   Ctrl+5 — one binding, two rows), *This list · ?* (decided: **Ctrl+/**), and omits **Ctrl+[ ]
   back / forward** (B§13.6 #2, in 14e). **Fix:** the overlay is generated from the binding
   table, never hand-written — what is bound is what is listed.
3. **[Med · judged]** The type-ahead caption sits at the window's bottom-right
   (`position:absolute; bottom:24px`), 600 px from the tree it describes. **Fix:** a caption at
   the tree's foot (or, cheaper, the matched prefix underlined in the focused row's title — the
   Finder's way, no extra element).
4. **[Low · measured]** The overlay's footnote is 11.5 px at 3.08:1 — mock-only text, but the
   same `--faint` habit as #1; the app's overlay gets no footnote.
5. **[Low · judged]** Twelve rows in two 270 px columns read as one table, but the columns are
   not grouped: *Navigate* (tabs, back, tree), *Create* (page, task, journal), *Find* (switcher),
   *Lists* (arrows, type, Enter, Esc) would let the eye stop at the group it wants. Notion groups;
   Things does not. Worth the four labels.

## Dimension by dimension

- **First impression** — a centred card over a 32 % scrim: a reference sheet, not a task, so a
  centred dialog is right where 14b made *tasks* slide-overs. Reads as "shortcuts" in the first
  second.
- **Hierarchy** — 18/500 title, 13.5 labels at 17:1, chips at 3:1 (#1): the hierarchy is
  inverted — the labels lead and the keys recede.
- **Colour** — scrim `rgba(27,27,24,.32)`, card white, radius 14, shadow `0 30px 60px -30px`:
  the slide-over's family; consistent with 14b.
- **Layout** — 620 px card, 22/26 px padding, 30.6 px rows: comfortable; two columns fit
  twelve rows without a scroll. At 700 px wide the card should become one column (not mocked).
- **The focus row** — a 2 px solid `#4a5568` outline, transparent fill: visible against the
  tree's `accentSoft` current row, so *focused* and *current* stay distinguishable (Apple Notes
  conflates them; the mock does not). The app's rows have no focus ring today
  (`pages-desktop.md`, Interactivity) — this is the shape to build.
- **Copy** — *Move · open in a list*, *Jump to a row by its first letters*: plain and right.
  *Quick add task* → *New task* (the sheet is titled that).
- **Interactivity** — `?` in the mock fires from anywhere, which is why the app takes Ctrl+/.
  Esc closes the overlay; a click on the scrim closes it; Enter in the overlay does nothing —
  fine, it is a list.

## What's working

- The overlay as a generated table with a chip per key; the focus ring distinct from the
  selection tint; the arrows-type-Enter trio written as three rows, not one.

## Cannot verify

- The app's rendering (nothing exists yet); the phone (nothing changes); how a focus ring reads
  in 14g's dark register.

## Disposition

#1, #2, #3 shape the 14e plan (chip colours; the overlay generated from the binding table; the
prefix underlined in the focused row instead of a caption). #4 does not apply to the app. #5
taken: four group labels. A function walk on the 14e build follows before its merge.
