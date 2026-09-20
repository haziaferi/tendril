# The mind-map pass — the grounds

*2026-09-20. The user, on three zooms of Garden plan after #120: "consider the structure of some
mind-mapping apps to smooth out this part of the Tendril design." Decided in one batch: its own
PR after #120's merge; **both** a structured mode and the visual grammar; **one grammar** for the
Canvas page and the outline's mind-map block; grounds Xmind, Freeplane, Miro or Whimsical, Obsidian
(measured on items 7 and 15), and the open-source projects the user listed, read for their
structure. This file is the grounds; the mock and its critique follow.*

## What Tendril has today

Two drawings that do not share a grammar:

| | the Canvas page (`ui/canvas/CanvasScreen.kt`) | the outline's mind map (`ui/pages/MindMap.kt`) |
|---|---|---|
| node | a 200 × 48 strip (text, page) or a frame with its own size | a box per outline block, sized by its text |
| edge | a straight line centre to centre, dim, an arrowhead one way or two, a label read in a sheet | a curve from parent to child, no head |
| hierarchy | none — a free graph; a frame groups by position | the outline's, one parent each, the root marked |
| layout | by hand; *Fit* | automatic (`RoadMapLayout`-style force), by hand after |
| fold | none | the map folds its subtree out of the page |
| storage | `canvas_nodes` / `canvas_edges`, exported as JSON Canvas | the outline's blocks; positions on the block |

The grabs showed the seams: an edge between box centres says nothing about direction until its
head, and its head sat under the strip (fixed); a frame is a box with a name, not a branch; a
card's placeholder is treated as content by the overlay; nothing marks a root.

## The models in the projects the user listed **[Verified — the READMEs, 2026-09-20]**

| project | node | connection | layout | group / fold | storage |
|---|---|---|---|---|---|
| inoichi | a titled node with a Markdown document | **exactly one parent**; cross links "decoration rather than a second hierarchy", drawn from a dot on the node's left edge | on the server, "the whole tree is tidied around the change — one layout implementation"; a *Tidy* verb; drags are temporary | collapse a branch (`/`) | one JSON per map; cycles rejected |
| obsidian-ideascape | an item of a nested list under a heading | the nesting only | radial — "branches spread either side of the centre"; or placed by hand, *Tidy* puts it back | fold a branch (⌘.); *focus*: one branch forward, the rest dimmed | Markdown + a `%%` comment `{"v":1,"pos":{…}}` |
| markmap-forge | a heading / bullet | the outline's | markmap's horizontal tree (d3) | expand/collapse per depth | JSON with a Markdown field → one HTML |
| Thinkingspace | a topic card (many Q&A rounds inside) | parent–child only — "no manual arbitrary linking"; synthesis references drawn as a second line kind | `@xyflow/react` + `lib/layout.ts`, automatic | subtree folding, hide/restore | `.thinkingspace.json` |
| mind-world-map | a 2–7-character label; every root-to-leaf path a proposition | the tree | XMind's | XMind's `"branch": "folded"` under a subtree budget of 50 | `.xmind` (`content.json`) |
| Opensource-Xmind | a topic: central → main → sub | the tree **plus** "floating connections between any two nodes — labeled arrows" | free drag | fold/unfold | `.xmind`, JSON, Markdown |
| claudemm | a folder as a branch, a chat as a leaf; own nodes added | the tree; chats dragged into nodes | pan/zoom | fold; dimming by age; status by colour | small text files |
| zotero-linked-mindmaps | a Zotero item or note | **typed, named, optionally directed links** — a vocabulary, not a fixed list; parallel links offset | positions by drag, persisted; unplaced nodes on a grid | "node grouping, for marking a cluster without inventing a link between every pair" | a Zotero note |
| draw.io (the *mindmaps* template) | a rounded container (`arcSize=50`), the root an ellipse 108 × 53 at stroke 2 | hierarchy edges **curved, undirected, no head** (`entityRelationEdgeStyle`, main branches stroke 2, sub-branches 1); the one cross-link the only edge with a head | by hand; a `containerType=tree` swimlane, dashed | `treeFolding=1` — a fold button on every subtree | mxGraph XML |

Three models, and the line between them is the one Tendril's two drawings sit on either side of:

1. **A tree from a root** (inoichi, Ideascape, markmap, Thinkingspace, mind-world-map): one parent, automatic layout with a *Tidy* to return to it, fold per branch, the hierarchy edge **undirected and headless** — direction is the tree's, not the line's.
2. **A tree plus relationships** (Xmind, draw.io's template, claudemm): the same tree, and a second kind of edge — labelled, with a head — for what the tree cannot say.
3. **A free graph with groups** (zotero-linked-mindmaps, Obsidian Canvas, Tendril's canvas): every edge typed or labelled and drawn as such; a group marks a cluster "without inventing a link between every pair".

The Canvas page is model 3; the outline's map is model 1. The pass's brief — both a structured
mode and one grammar — is model 2 drawn once: **a hierarchy edge is a headless curve; a
relationship is a labelled line with a head; a frame is a group.**

## Live grounds **[Verified — native grabs, 125 % display, 100 % zoom, 2026-09-20]**

| app | measured | what it settles |
|---|---|---|
| **Xmind** (a new *Mappa mentale*) | the central topic 30 ExtraBold in a white box with a border; a main topic 18 Medium, filled, **41 px** tall; a level-3 topic 14 Regular in a tinted box with a border, **27 px**; level 4 bare text on the line with a small hollow dot at the join; branches **thin curves, 2 px**, one colour per main branch, *no head*; a boundary (*Contorno*) a dashed rounded rectangle with a grey fill, ≈ 10 px around its topics; a relationship (*Relazione*) a dashed curve **with** a head and a 13 Regular label, two bezier handles when selected; the selected topic shows a `+` at its right (child) and below (sibling) and a floating toolbar above; the Style panel names the **structure per topic** — *Senso orario* on the centre, *Da sinistra a destra* on a main topic | three levels; headless branches; the relationship's form; structure as a property of a subtree |
| **Mindomo** (a new *Mappa mentale*, maximized) | the centre a pill **70 px** tall, 3 px border, ≈ 22 px text; main topics pills **43 px**, 2 px border in the branch's colour, white fill; branches **3 px**, straight from the centre's side; from level 3 on **the text sits on its line** — the branch is the underline; the new-diagram picker lists *mind map · concept map · org chart · fishbone · timeline · outline · Gantt* | text on the line from level 3; the template list read as one model drawn several ways |
| **Freeplane** (its *freeplaneFunctions* document) | nodes as underlined text (fork) or bubbles; **the outline pane and the map are one document**; free-positioned nodes that stay in the tree; clouds following a subtree; a fold marker at the branch's end; summary nodes; attributes (a key–value table under a node); details under the core; arrow-links dashed with a head and a label; styles per level — the chrome is 1997's, the structure is not | what to take: the outline ↔ map identity, free nodes, clouds, fold, level styles; not taken: attributes, summaries (recorded) |
| Obsidian Canvas | items 7 and 15: card 252 × 63 px at rest, group border 4.6 : 1, label at the group's top-left, edges over groups | the strip, the group |
| draw.io (browser, no sign-in) | the *mindmaps* template read from its XML (above) | the Buzan grammar; fold on every subtree |
| MindMup · Miro · Whimsical | a sign-in each — not entered, not measured | — |

## Decided (2026-09-20, one batch after the measurements)

The user's question first — *which option is the most versatile; consider Mindomo's templates;
could Tendril have a similar list without it being a fixed option?* Answered: Mindomo's list is
one model drawn several ways (mind map · org chart · fishbone · timeline are layouts of a tree
with relationships; the concept map is the same tree with the layout off — today's canvas; the
outline is the tree as text — the outline block; Gantt is a database's Timeline view), and Xmind
makes the structure a property of a *topic*, switchable any time. So:

1. **A parent link on the node** (v23: `canvas_nodes.parentId`, `folded`) — one parent by
   construction; a hierarchy edge is drawn from the link, never stored as an arrow; today's
   arrows become *relationships*. **A structure on the board (`page_canvases.structure`) and,
   optionally, on any node's subtree** — *Free · Map · Right · Down · Timeline · Fishbone* —
   applied by **Tidy**; the same nodes whichever is chosen. The outline's mind-map block is the
   same renderer over the outline's tree. JSON Canvas export writes the tree as headless edges.
2. **The grammar per structure:** the map structures draw three levels — the root at
   `pageTitle` in a bordered box, main topics the 200 × 48 strip, deeper nodes as text on the
   line; Down and Timeline draw the strip at every level (Mindomo's org chart); a free
   (parentless) card is always a strip.
3. **A frame around a tree node follows its subtree**, hand-sized around free nodes — one frame
   kind, two behaviours by what it holds; **subtrees fold** with the hidden count at the
   branch's end.
4. **Branches curved** in the map structures, **elbowed** in Down / Timeline, headless, one
   colour per main branch from the register's fanned hues; relationships dashed with a head and
   a label; **Tidy on demand** (a bar button; a new child is placed by the layout), a dragged
   node free until the next Tidy (Freeplane's free node).

The mock: `docs/mockups/mind-map.html`; its critique: `mind-map-mock.md` (two binding fixes: a
relationship's outer-side route, the following frame's stroke at `dim`).
