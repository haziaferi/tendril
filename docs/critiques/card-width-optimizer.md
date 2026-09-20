# The canvas card's width — chosen by the optimizer, walked on both devices

*2026-09-20. The user: "Can the text boxes be shorter by default and adapt with the length of the
text, wrapping after a certain length? This is to avoid overlap when there are many short text
boxes" — and, mid-turn, a 1 px border around the cards. Decided in one batch: `meta-optimizer`
with a weighted deterministic scorer; every strip takes the rule, the root keeps its box; a hand
may also set a card's width (a handle — Obsidian's); the readable band from the grounds' measure.*

## The ground — Xmind, measured live

A new map (discarded unsaved); one main topic retyped at six lengths at its default 18 px text,
its box grabbed natively at 125 %:

| text | chars | box width | note |
|---|---|---|---|
| Water | 5 | 70 px | ≈ 3.9 em |
| Kitchen scraps | 14 | 121 | 6.7 em |
| Compost bins by the shed | 24 | 201 | 11.2 em |
| Turn the compost every second week | 34 | 258 | 14.3 em |
| … in spring | 44 | 303 | 16.8 em — at the cap |
| Plant the tomatoes … | 81 | 307, three lines | the panel's **Lunghezza 307 px** (17.1 em; the central topic's 342 at 30 px), then it wraps |

Xmind's rule: a topic is its text plus padding, capped at a per-topic max width, then wrapped.
Mindomo [Assumed] the same (its topics were measured for height in `mind-map-grounds.md`, not
width). Tendril's card text is 14 sp, so 17 em is 239 dp; the app's strip was a fixed 200.

## The search

**Corpus**: the user's own short texts from the dev DB — every card text, page, task and habit
title, and every block under 160 characters (36 texts, 2…60 characters; the file is not
committed). **Dimensions**: the policy (*fit* to the text · widths snapped to *steps* · *fixed*),
the minimum width 60…200, the wrap width 160…320, the padding 8…16 a side, the step 20…80.
**Fitness** (`scorers.py`, deterministic, no model turns):

| component | weight | what it measures |
|---|---|---|
| overlap | 0.35 | the short texts (≤ 24 chars) placed by hand at the pitch of what is seen — the text plus a 24 dp gap — and the share of neighbours whose boxes do not collide |
| readability | 0.25 | every line inside 12…46 characters (Xmind's cap), no text past three lines |
| grounds_fit | 0.20 | 1 − nRMSE between the rule's width in em and Xmind's curve, over the corpus' lengths |
| alignment | 0.10 | fewer distinct widths read as columns |
| compactness | 0.10 | area against the fixed 200 strip |

`opt validate` → fully computable, 0 turns. `opt run` → **converged after 13 generations**
(HS+DE; under 0.00 gain for 12): **fit · min 60 · wrap 220 · pad 8 — 0.847** (overlap 0.975,
readability 0.938, grounds 0.738, alignment 0.316, compactness 0.922); the runners-up the same
rule at wrap 210. Against it: today's fixed strip **0.466** (overlap 0.233 — the user's
complaint, measured), steps of 40 **0.761**, a hand-picked fit 80/240/10 **0.822**.

A second run with the card's *real* horizontal chrome (8 dp of padding plus the 24 dp badge
column then reserved at the right: 32 dp) fell to **0.788** (overlap 0.906, grounds 0.578) —
the reserved column itself was costing a third of a short card. So the badges moved.

## What was built

- `domain/canvas/Frames.kt`: `cardWidth(text, handWidth)` — the longest line × `CANVAS_CARD_CHAR`
  + 16, clamped 60…220; `cardChars(width)`, `cardLines(text, width)`, `cardHeight(text, nodeH, width)`;
  `CanvasNode.handWidth()` reads the `width` column only when a hand set it (the entity's default
  180, which no card ever had set by hand, and 0 read as *derived*); `nodeBox` takes a `titleOf`
  for page cards (their title is the width); an empty card is its caption's width (*Empty card*).
- `CanvasTree(nodes, structure, titleOf)` — the screen, the block, the ViewModel and the export
  pass the page titles; the outline's map (`MindMap.kt`) uses the same `cardWidth`.
- The card (`CanvasNodeCard`): a `Box`, not a `Surface` (a Surface clips to its shape); the text
  padded 8 dp a side; **a 1 dp `outline` hairline** on every strip (the user's ask); **the badges
  straddle the right edge** (the frame's handle straddles its corner the same way); **the width
  handle** straddles the left edge's middle on a selected card under a pointer — the right edge
  stays, `width` is written, the text keeps deciding the height (`CanvasViewModel.resizeCard`).
- The phone's `···` gains **Tidy** and **Fit every card** (its bar holds only `···`; Fit had been
  missing there — the S-list had struck it in error; corrected in §0.10 item 24).

## The walk

| tried | observed | finding |
|---|---|---|
| Garden plan at the first build | *Garden wa…*, *Coffee grou…*, *Em…* — cut | the 7.5 dp estimate a character left no slack: a word a hair over it wrapped into a one-line card and was ellipsised → **8**, then still *Garden wa…* → **8.5** (0.6 em); every card then holds its text |
| the empty cards | *Em…* at the 60 dp minimum | an empty card sizes to its caption; the caption lost its verb (*Empty card*, the card is the verb) |
| a selected card's badges | half of each disc cut at the edge | `Surface` clips its content — a `Box` with the same background and ring |
| the width handle at the right edge | not visible on a one-line card | the two badges fill a 48 dp card's height and covered it → the **left** edge |
| the first drag of the handle | the whole card moved | `detectDragGestures` waits for slop and the body consumed the first changes → the handle consumes its down at once (`awaitEachGesture` + `drag`) |
| the handle dragged 94 dp left | `width` 212, `x` 171 (from 265): the right edge stayed, the elbow re-anchored | — |
| the phone, *Map test* | the root box, *Child one* a fit strip with the hairline and straddling badges; `···` → *Fit every card* brought the tree into view | — |

Measured on the last grab (the board's own zoom, so ratios): *Garden waste* 98 px against
*LeavesKitchen scraps* 164 — 12 and 20 characters, 1 : 1.67 for 1 : 1.67 of text.

## Recorded, not changed

- The scorer's character estimate was 7.5 dp; the build's is 8.5. The winner does not move (the
  same rule, the same bounds; the score shifts by the padding term only) — re-run if the bounds
  are ever revisited.
- Under Touch the badges are always shown, so a card's straddling discs sit half outside on the
  phone; a wider Touch handle for the card's width is not built (the phone has no selection).
- A page card sizes to its title; without the title (an export missing the page) it is the old 200.
