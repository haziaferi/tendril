# §0.10 item 6 — canvases in the Markdown export as JSON Canvas, verified in Obsidian

*2026-09-19 · the ground is the format's own specification (`obsidianmd/jsoncanvas`, spec 1.0 —
the org's repository the user pointed at) and the reader that matters: Obsidian 1.13.7 opening
the exported files on the desktop. No mock — no new surface; the Settings row's sentence changes.*

## The mapping (the spec's, nothing invented)

| Tendril | JSON Canvas | checked |
|---|---|---|
| a Canvas page | a `.canvas` file at the page's place in the zip's tree, no `.md` twin | Obsidian lists it as CANVAS and opens it |
| a text card | `{"type":"text","text":…}`, 180 × 90 | opens |
| a page card | `{"type":"file","file":"<the page's zip path>"}` — vault-relative, the spec's rule; a page the export does not carry becomes a `text` node with the title | unit-tested; Obsidian [Assumed] resolves vault paths as its own do |
| a frame (item 15) | `{"type":"group","label":…}` with the frame's own box | Obsidian draws the group with its label |
| an arrow | an edge: `ONE_WAY` the spec's default (`toEnd: arrow` implied), `TWO_WAY` adds `fromEnd: arrow`, `NONE` sets `toEnd: none`; the label carried | Obsidian draws the arrow, choosing the sides |
| coordinates | the board's dp rounded to integers; ids the rows' uids (a re-export writes the same ids) | `x: 10.4 → 10`, `y: 20.6 → 21` (tested) |
| a mention of a canvas in a note | `[Canvas: Route ideas](Trip/Route%20ideas.canvas)` | Obsidian's graph shows *Trip → Route ideas.canvas* |

## Verified

- **Phone**: Settings → *Export Markdown* → the zip pulled: `Trip/Route ideas.canvas` (a text node and the *Stops* group), `Trip.md` linking it, *Exported 6 pages, 1 canvas and 0 pictures*. The two files dropped into the desktop vault (a new folder, `Tendril export/`): Obsidian opened the canvas — the group *Stops* with the card inside, as the phone draws it.
- **Desktop** (new this PR — the exporter was `jvmCommon` already; Settings gained the section and a save dialog): `Garden plan.canvas` and `Beds board.canvas` in the zip; *Garden plan* opened in Obsidian with its three cards, the *Beds* group and the arrow between the two cards, *Exported 8 pages, 2 canvases and 0 pictures to desk-md.zip*.
- The unit tests: a canvas page as a `.canvas` file and no `.md`, the four node kinds, the rounded integers, a two-way arrow with its label, the mention's link; a trashed page's card as a text node.

## Where the files go — the item's own question
In the Markdown zip, at the page's place in the tree: the zip is then an Obsidian vault whose canvases work, which is what "a form something else reads" (§1's goal 5) means for a canvas. Not beside `.tendril` (the archive is Tendril's own format and carries the board already), not the sync folder (the `.ics` precedent: a file the person picks).

## Recorded, not changed
- Import of `.canvas` files — not asked; the Notion import (§7) is the only importer of pages. One line for a later item.
- No colours: Tendril's cards have none (the token map has no card hue yet).
- The desktop's *Quick add from anywhere* section read *Ctrl+Shift+Space is taken by another app* on this walk — the chord the user set is held elsewhere on this machine now; the section says so as designed.

## Cannot verify
- A `file` node's rendering in Obsidian (the walked boards carried text cards only); the spec's path rule is followed.

## Disposition
Ship. §0.10 item 6 resolved; §0.6.7's "JSON Canvas export" built.
