# Critique — `docs/mockups/mind-map.html` (the mind-map pass, pre-build)

*2026-09-20. `craftkit ui` over the mock, its render read at 1180 px, contrast of the branch hues
and the edge inks computed from the sRGB formula; the grounds in `mind-map-grounds.md`. Measured
and judged kept apart. The mock draws the decisions of the same day's batch: a parent link on the
node, a structure on the board and per subtree applied by Tidy, three levels in the map
structures, frames following subtrees, folding, curved headless branches, one renderer for the
Canvas page and the outline's map.*

## Top priorities

1. **[High] The relationship's route** (Map frame, *Water → feeds → Beds*) — drawn as a free
   curve it crosses the *Beds* subtree's leaves and ends inside the frame; on a board where
   Tidy places nodes, a relationship's path is the one thing nothing lays out. *Fix (binding):*
   a relationship is routed from the source's outer side to the target's outer side — the side
   away from the parent — and its label sits at the curve's midpoint on the ground, never over a
   node; where the straight route crosses a node, the curve bows outward (the same test as the
   drag's hit-test). The head stays on the target's edge (`boxEdgeDistance`).
2. **[High] The frame's border on the board** — `faint` on the board's ground measures
   **3.56 : 1**, on a strip's tint 2.29 : 1; a frame that *follows a subtree* is a drawn
   structure, not decoration, and B§13.8.3's floor for a required graphic is 3 : 1 on every
   ground it meets. *Fix (binding):* the frame's stroke at `dim` (5.36 : 1 on the ground) when
   it follows a subtree; the hand-sized frame keeps `faint` (item 15's measured Obsidian ground,
   4.6 : 1 there). The label pill is already `heading` on `surface` (#120).
3. **[Med] The fold badge** — a 20 dp disc at the branch's end holding a count; at the strip's
   right edge it sits where the link handle's column is on hover. *Fix:* the badge takes the
   handle column's slot (the strip's reserved 28 dp end margin) and the link handle is not drawn
   on a folded node — its subtree is what the handle would link from.
4. **[Med] Level-3 text on the line** — 14 sp in `onSurface` with a 1.5 dp branch under it
   reads well at 100 %; at the embed's fit (≈ 0.5) the line is under 1 px and the overlay writes
   the word at `caption` with no line. *Fix:* `ReadableLabels` draws the underline for a
   text-on-line label (a 1 dp rule under the word in the branch's colour); the word never loses
   the thing that says what it is.
5. **[Low] Colour count** — `craftkit` counts 19 colours: the four branch hues added to the
   register's twelve tokens. The four are not new tokens: they are the register's fanned data
   hues (`event`, `habit`, `third`, and a fourth at −120°) and must come from `paletteFor`, never
   from a list — the audit's *hardcoded-colour* rule holds the build to it.

## Dimension by dimension

**Measured.** Type: 11 · 12.5 · 14 · 16 on a Major Second (fit error 0.032) — the desktop's
three sizes plus the eyebrow; the root at 16/600 is the bar's title size, one step above the
strip's 14. Branch hues on the ground: blue 6.83, green 7.39, third 8.34, rose 6.83 (all ≥ 4.6,
`Floors.DATA`); on a strip's tint 4.40 · 4.76 · 5.38 · 4.40 — the branch enters the strip's
edge, so its last pixels are on the tint; ≥ 3 : 1 there, and the build's fanned hues are solved
on the ground, so this is the mock's approximation. The relationship in `dim`: 5.36 on the
ground. The root's border in `accent`: 7.65. Text on a strip 7.64, on the ground 11.86. Radii
4 · 6 · 8 · 10 · 12 (the family; the 5 is the mock's `.rel` and goes). Spacing off-grid in six
values — the mock's, not the build's (the build reads `Frames.kt`'s constants).

**Hierarchy (judged).** The root's box, the strips, the words on their lines: three weights of
node read as three depths without counting — the mock's strongest point, and Xmind's and
Mindomo's shared answer. The bar carries the structure's name (*Map ▾*) where the Calendar's
carries its view's — one vocabulary. The frame's label above the frame's edge (#120) holds in
the map: *This season* reads as a group's name, not a node.

**Layout (judged).** Both-sides layout balances by count, not by height: the right side's three
strips against the left's two leaves the root visually right of centre. Tidy should balance
by subtree height (Ideascape's "either side of the centre" does), and the mock's placement is
by hand. Down: the elbow's knee at the midpoint between rows reads as an org chart at once; a
page card keeps its tint inside the tree (the third hue's) — one more thing the strip carries.

**Consistency (judged).** The Free structure is today's board unchanged — the same strips, the
same frame, the same arrows now called relationships — so nothing a person built is redrawn by
the merge; only a node given a parent changes. The outline's embed is the same grammar at the
embed's fit; its caption keeps *Mind map · open*. The node menu's keys (*Tab* child, *Enter*
sibling) are Xmind's, Mindomo's and Freeplane's alike.

**Copy (judged).** *Tidy* is inoichi's and Ideascape's word and says what it does; *Detach from
the tree* is a plain sentence for Xmind's "convert to floating topic"; *Structure* over
Xmind's *Struttura* and Mindomo's *Layout* — *Layout* would collide with the Calendar's and the
database's *view*. *Fold* with the hidden count in the menu (*3 hidden*) says more than an
icon.

**Interactivity (judged).** The fold badge, the *+* handles of Xmind (a child at the right, a
sibling below on the selected node) are not in the mock — the keys and the menu are the ways
in; the hover *+* is a later option, recorded. A dragged node becoming free is invisible until
Tidy — the function pass should check whether a free node inside a tree needs a mark (Freeplane
draws none).

## What's working

- One data model under six structures — the user's "a list, but not a fixed option" — and the
  Free structure being exactly today's board.
- The three-level grammar: the strip we just measured is the middle level, unchanged.
- The relationship kept visibly distinct from the branch (dashed, headed, labelled) — every
  ground agrees, and Tendril's existing arrows become it without a migration of meaning.

## The user's look (the same day)

*Map*, *The structure* and *The outline's mind map* accepted; *Down* had branches out of
alignment (the elbows left the root ten pixels off its centre) and a frame over strips it did
not hold. Redrawn: every elbow leaves the parent's bottom centre and enters the child's top
centre with one shared knee; the frame follows the *Beds* subtree alone and Tidy spaces the
siblings around it — **a frame never overlaps a node it does not hold** is now a rule of the
layout, not a hope of the mock; the fold badge sits under a folded strip in Down (at the
branch's end, as in Map). A mock defect too: the page card's class collided with the page
frame's and drew it 700 px wide. A second look: still not centred — the root's centre (498), its
children's (520) and the board's (508) were three numbers; the frame is now placed from one
`CX` by arithmetic, which is what the build does: **Tidy puts the root over its children's
centre**, and the mock is an approximation of the app's look (its tokens, its sizes) with
positions typed by hand — never a render of the app.

## Cannot verify

- Tidy's balance on real boards (the mock is placed by hand); the frame's auto-fit padding
  (the mock uses 12 dp; Xmind's boundary measured ≈ 10 px at 125 %).
- The phone: the tree's keys become sheet verbs; measured on the walk.
- Timeline and Fishbone: not drawn; the same nodes on a spine / as ribs once Down proves the
  elbow.
