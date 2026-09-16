# Critique — hover previews, on the build

*2026-09-16 · `design-critique-plus`, the post-build function walk for B§13.6 #3. Read-only;
observable-level findings. **Desktop**, Ink dark, 1400 × 900 then the user's 967 × 1039 beside
Notion: *journal/2026-09-13*'s inline `@Escape test`, its mention block *Call the library*, its
block reference to *Escape test*; the Road Map's five nodes (*Books v12* a database, *Garden
plan* a canvas, *Reading log* dragged to the window's foot); the shelf's neighbourhood; a pop-out
of *Call the library*; typing into the block whose mention the pointer rests on. **Phone: not
walked** (testing paused) — nothing composes under Touch (`LocalDensityProfile.pointer`).*

## Top priorities

1. **[Med · seen, fixed before merge]** The first build previewed *Escape test* as *Mind map ·
   0 nodes* and nothing else: the seed's one block is a map root with no children, and the rule
   folded the root's own words into the count. **Fix:** a map's root keeps its line (it is a block
   with words); the subtree is one counted line, and a map of no nodes says nothing more. A block
   written over several lines reads as one line with a middle dot (*Root edited · Child one! ·
   Child two*), not run together.
2. **[Med · seen, fixed before merge]** *Books v12* read *Table · 1 row* against a Table of three:
   the rows were asked with `labelId = null`, so the label-bound members were missed. **Fix:** the
   database's own `labelId`. The same pass gave a canvas its card (*Canvas · 2 cards · 1 link* and
   the first cards' text — the mock's rule 4, which the plan had dropped to a glyph).
3. **[Med · seen, fixed before merge]** Typing while the pointer rested on the block's own mention
   still opened the card ~500 ms in — a press cancels the wait, a keystroke did not. **Fix:** a
   key event on the field cancels the wait and drops the card (`onPreviewKeyEvent`, not consumed);
   the walk's *" and"* typed with the card up left the field, the caret and the text untouched.
4. **[Med · the user, fixed before merge]** *"The font size in compact density is a bit large"*,
   with Notion beside it at the same 728-px window. Measured (Windows at 125 %): Tendril's body
   13.4 px against Notion's 17.5 — smaller — but its section titles (21 px) and Medium labels sat
   above Notion's 14-px semibold headers and 12-px secondary labels. Decided: **Compact 0.9 →
   0.85, Comfortable and Touch by the same ratio (0.95, 1.23)**; the tree's rows then measure
   33 px against Notion's settings sidebar's 29–30, at a text band of 12 px against 10 — the rows
   are proportionally tighter than Notion's, the type is what is larger. The rest of the note —
   *a better font, headers bold rather than large* — is its own PR (decided: Inter bundled;
   Notion's rule, weight carries the hierarchy and sizes step down), because the desktop was found
   to load DM Sans at its default instance only, so Medium was rendering as 400.

## Walked

- **The inline span** — the pointer held on *@Escape test*: after ~500 ms the card under the
  line, left-aligned to the span, the span in a 1.5 dp accent outline; *▤ Escape test · edited
  1 h ago*, its lines; off the span → gone within the grace; onto the card → stays; a click →
  the page opens and the card goes.
- **The mention block** — *Call the library* → *▤ Call the library · → Escape test* (its one
  block is a mention block).
- **The block reference** — the card with the referenced line marked on the third hue's tint
  with its 3 dp bar and the footer *the referenced line · click to open*; the seed's source page
  has one block, so the context is the line alone (the critique's #5: what there is, never a
  blank).
- **The Road Map** — *Books v12* → *Table · 3 rows · Author, Done, Read on, Blocked…* and the
  three row titles; *Garden plan* → *Canvas · 2 cards · 1 link*; a node's drag hides the card and
  a settle after the drag brings it back under the moved node; *Reading log* at the window's foot
  → the card opens **above** the node.
- **The shelf** — *Show beside ▸ Road Map around this page*: the neighbourhood's nodes preview;
  a card at the shelf's right edge is clamped inside the window, not cut.
- **A pop-out** — Shift+click *Call the library*: its mention block previews in its own window
  (its own state; Ctrl+W closes).
- **Placement** — under the target's line with the gap, never over it; clamped to the window's
  width; flipped above near the foot.

## Judged

- **[Low]** After a node drag the card returns when the pointer settles on the moved node — a
  hover, by the rule; acceptable, recorded.
- **[Low]** A database's column list is ellipsised at the card's width when a Table has many
  properties (*Read on, Blocked…*); the row titles under it say more than the columns do.

## Cannot verify

- **The phone**: nothing to see — the modifier returns unchanged under Touch; an Android mouse
  (a tablet) would get the previews, unwalked.
- **A reference whose source is on another device**: `getByUid` finds nothing → the reference's
  page from `mentionedPageId`, the cached line unmarked; not seeded.

## Disposition

#1–#4 fixed. Nothing open; merge. The type note continues as its own PR.
