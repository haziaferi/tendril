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

## Live grounds

| app | status |
|---|---|
| Obsidian Canvas | measured on items 7 and 15: card 252 × 63 px at rest, group border 4.6 : 1, label at the group's top-left, edges over groups |
| draw.io (browser, no sign-in) | the template read from its XML (above); the rendering seen at 100 % |
| MindMup (the user's link) | a sign-in page — not entered, not measured |
| Miro · Whimsical | a sign-in — not entered |
| Xmind · Freeplane | **to measure once installed** (the user installs; free tiers): topic sizes at each level, the branch connector's shape and width per level, the fold button, the root's form, the boundary/cloud (the frame's cousin), the relationship line |

*The mock waits for Xmind's and Freeplane's numbers; the rest of this file is what it draws from.*
