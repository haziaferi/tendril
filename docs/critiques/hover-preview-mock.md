# Critique — hover previews, on the mock before the build

*2026-09-16 · `design-critique-plus`, the pre-build pass for B§13.6 #3 (hover previews), on
`docs/mockups/hover-preview.html` — drawn for this pass: an `@mention` hovered (a page's first
lines), a block reference hovered (its source line in context), and a database previewed from a
Road Map node, Ink dark. `craftkit ui` (0 errors, 4 warnings, 3 notes — the sprawl is the mock's)
and `craftkit render` at 1000 wide, read whole. Measured and judged findings labelled.*

## Top priorities

1. **[Med · measured]** The card's *edited 2 h ago* is `#929290` on white — **2.8:1**, the
   `faint` token, under the 3:1 the eye pass allows for a mark and well under 4.5 for text. It
   is text. **Fix:** `onSurfaceVariant` (`textDim`, ≥ 4.6 since 14g·1), 11 sp.
2. **[Med · judged]** The card sits **over the line it previews** in frame 1 (its top edge
   crosses the mention block below the hovered span). A preview that covers its own neighbourhood
   hides what the person was reading. **Rule:** the card opens **below** the target's line with
   8 dp of air, flipped above when the window's bottom is nearer (`Popup` with an offset from the
   target's bounds, not the pointer), never over the target itself.
3. **[Med · judged]** Six lines of body plus a header and a footer make a 200 px card for a
   page with two blocks (frame 2 shows three lines and looks right). **Rule:** the card is as
   tall as its lines — up to six, no minimum — and the footer appears only when lines were left
   out (*3 more blocks*) or the target is a reference (*the referenced line*); *click to open* is
   said once, in the footer, never alone.
4. **[Low · judged]** The hovered mention's 1.5 dp accent outline is a second accent on a span
   that already wears `accentSoft` (14h·2); the outline says *this one* well enough — keep it,
   but only while the card is up, and drop it from the block card and the reference card, whose
   hover state is the surface's own lift.
5. **[Low · judged]** A block reference's context shows one line before and one after: on a
   page whose reference is the first block there is no line before — the card shows what there
   is (one or two lines), never a blank.

## Dimension by dimension

- **Hierarchy** — glyph · title · edited on one row, then the lines: the title is the card's
  one loud thing; the lines are body at 13 sp. A heading block bold, a checkbox as a glyph, a
  mind map or canvas as a count: the page's shape at a glance, not a rendering of it.
- **Copy** — *edited 2 h ago* is `relativeTime` (14h·2), shared with the History sheet and the
  phone's cards; *3 more blocks*; *Table · 3 rows · Read on, Author, Done* for a database. No
  *Preview* label — the card is the preview.
- **Consistency** — the `PointerMenu`'s popup and the shortcuts card's ground; 10 dp radius as
  the menus; the shelf's page glyph. The block reference's marked line uses the reference bar's
  own colour (the third hue) so the card echoes the block it came from.
- **Fit** — desktop only (`LocalDensityProfile.pointer`); 500 ms after the pointer settles, gone
  when it leaves the target *or the card* (the card is hoverable so it can be read; a click on it
  opens). Never while a drag is in progress.
- **Interactivity** — a click on the card opens the page (Ctrl+click beside, Shift+click in a
  window — the tree's chords, since the openers are shared).

## What's working

- The block reference's preview *in context* is the benchmark's "better than theirs": Obsidian
  previews the referenced block alone; the line with its neighbours says where it lives.
- One card for four sites: the same `PagePreview` for a mention span, a mention block, a node
  and (with the marked line) a reference.

## Cannot verify

- The hover-over-a-span mechanics: the offset under the pointer from `TextLayoutResult` inside a
  `BasicTextField` that is also the editor — the build's walk decides whether hover and caret
  fight.
- The 500 ms: the mock is static.

## Disposition

#1–#3 and #5 fold into the plan; #4 halves the outline's use.
