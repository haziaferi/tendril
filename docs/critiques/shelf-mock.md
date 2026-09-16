# Critique — the shelf mock, before 14h·1

*2026-09-16 · `design-critique-plus`, the pre-build pass for 14h·1 on `docs/mockups/shelf.html` —
a mock drawn for this pass (no earlier mock had the shelf): the Pages workspace at 1200 × 760 on
Ink, light and dark, with today's Journal beside the page and with the Road Map's neighbourhood
beside it. Read-only. **Measured:** `craftkit ui` (contrast, targets, semantics). **Judged:** one
render at 1260 px.*

## Top priorities

1. **[Med · judged]** The shelf header is **44 px** beside a **52 px** page bar and a 52 px tree
   header: three bars on one row with two heights, and the hairline under the shelf's header
   lands 8 px above its neighbours'. The mock's own rule 1 says a page in the shelf keeps its bar
   *as* the header — so the header is the bar's height. **Fix:** 52 dp (the page bar's,
   `PaneChrome`'s), the hairline continuous across the handle.
2. **[Med · measured]** The graph header's depth control is three letter-spaced digits in dim at
   12 px with no button semantics — no target at all. **Fix:** the Road Map's own `FocusBar`
   chips (28 dp targets, a `FilterChip` each) — the same control the full map has, not a new one.
3. **[Med · judged]** At 1200 px, tree 260 + handle 8 + shelf 380 leaves the page **488 px** —
   under the Table's comfortable width, and a database in the main pane would scroll at once.
   **Fix:** the shelf's width clamps to **≤ 45 % of the workspace** (the space right of the rail
   and the tree), the main pane keeps ≥ 360 dp; the tree stays the user's to collapse (Ctrl+\)
   and the shelf never auto-collapses it.
4. **[Low · judged]** One `⇄` glyph carries two verbs — *swap the panes* for a page, *open the
   full Road Map here* for the graph. The tooltip says which; the glyph alone does not. **Fix:**
   keep the glyph for the page and Journal (a true swap: the panes exchange) and give the graph's
   header the Road Map's own icon-button (*Open in Road Map*) instead — one glyph, one verb.
5. **[Low · judged]** The graph legend's second half is an instruction ("click a node to open it
   here, double-click to open it in the main pane") — a legend, not a manual. **Fix:** the legend
   keeps the edge key only; the click rule lives in the node's tooltip and the spec.
6. **[Low · measured]** `craftkit` flags the 28 px `×` / `⇄` under a 44 px touch minimum — the
   desktop's pointer profile; the Touch profile scales them (14c·0), and below 840 dp there is no
   shelf.

## Dimension by dimension

- **Hierarchy** — the main pane's 20 px title against the shelf's 14 px keeps the shelf secondary
  in both frames; the rail's current item, the tree's current row and the shelf's centre node all
  wear the selection tint — one meaning, three places, correct.
- **Colour** — dark frame: text `#D8D8DA` on `#1B1D21`, the handle a `border` hairline; the
  graph's mention edge dim and the related edge the third hue, as the Road Map draws them.
- **Layout** — three columns and one handle; the handle's hairline reads as the shelf's edge.
  The graph's nodes sit low in the pane (the mock's placement, not a rule).
- **Copy** — *Around Escape test*; *journal/2026-09-16* is the page's own title. `Ctrl+Shift+\`
  beside `Ctrl+\` (the tree): the two panes' chords rhyme.
- **Fit** — a Journal beside a page for writing into, and a page's neighbourhood beside it for
  navigating from: the two uses B§13.6 #4 named; a third, another page, is the tree row's.

## What's working

- One header shape for three contents; the page's own bar *is* it, so a page beside a page shows
  two bars of one kind, not a bar and a caption.
- The workspace keeps its shape below the shelf: close it and the page has the width it had.

## Cannot verify

- The graph's force layout in a 380 dp pane with ten neighbours — the full map's constants
  (`NODE_WIDTH`, spring length) may crowd; the walk decides whether the shelf's canvas takes a
  smaller node.
- The two editors on one page's *neighbour* — a page open in both panes is refused by rule 2, but
  a Journal in the shelf and a link to it from the main pane is allowed and is two ViewModels
  keyed by one id (`viewModel(key = "page_$id")` — the same instance, by design).

## Disposition

#1–#3 become rules in the plan; #4, #5 taken; #6 stands.
