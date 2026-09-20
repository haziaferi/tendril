# The desktop's text beside Notion and the Claude app — measured

*2026-09-20 · native grabs at 125 % display scaling, the cap height of each element in device
pixels (the height of a capital, which is what the eye compares across fonts; runs that carry a
descender are noted and the cap read from them). Tendril at Compact on the dev window
(1007 × 885); Notion's desktop app (a row page and a database page); the Claude desktop app.
The user's brief: after the rail, "look at the other text elements on the page and resize them
accordingly, on real-world examples".*

## The grounds

| app · element | cap px | what it is |
|---|---|---|
| Notion · sidebar items (*Home*, *Views ground*) | 10 | 14 px CSS |
| Notion · property labels (*Date*, *Tags*) | 10 | 14 px, dim |
| Notion · property values (*September 17, 2026*, *Empty*) | 10 | 14 px |
| Notion · view tabs (*Table*, *Board*), a group chip (*Not started*), *New* | 10 | 14 px |
| Notion · *Add a property*, *New page* | 10 | 14 px, dim |
| Notion · section headers (*Recents*, *Comments*) | 8–9 | 12 px |
| Notion · the top bar's page name | 10–11 | 14 px, Medium |
| Notion · the in-page title | ≈ 30 | 40 px |
| Claude · sidebar items (*Root directory*) | 10 | — |
| Claude · sidebar group headers (*Claude Code*) | 9 | — |
| Claude · meta lines (*Eseguito 4 comandi*, *Creato 3 file*) | 10 | dim |
| Claude · message body | 11–12 | — |
| Claude · the title bar's title | 11 | Medium |

The pattern is the same in both: **one size for every piece of chrome text** — labels, values,
chips, tabs, buttons, secondary lines — and hierarchy carried by **weight and colour**, with a
single smaller size reserved for section headers.

## Tendril, before and after

| element (style) | before | after | ground |
|---|---|---|---|
| the rail's labels (`caption` → `body`, Medium when current) | 7 | 10 | Notion 10 · Claude 10 |
| tree rows (`body`) | 10 | 10 | 10 |
| tree header *Pages* (`heading` 14/600) | 9–10 | 9–10 | Notion 8–9 · Claude 9 |
| the bar's title (`pageTitle` 18/600) | 13 | 13 | Notion top bar 10–11, in-page title 30; Claude 11 |
| label chips *book*, *Add label* (`label` 12.5 → 14 / 500) | 8 | 9 | 10 |
| property labels *Author* (`label`) | 8 | 9 | 10 |
| property values *Herbert* (`body`) | 10 | 10 | 10 |
| the membership line *Books v12 · #book* (`description` 12.5 → 14, dim) | 8 | 9 | 10 |
| a block's text (`editorBody` 16) | 12 | 12 | Claude 11–12 · Notion 11 at its zoom |
| captions *Mind map · open* (`caption` 11 → 12.5) | 8 | 9 | section headers 8–9 |

## The decision

Under a pointer profile the four small styles step **one size up the scale** and nothing else
moves (`pointerTypography`, `ui/theme/Type.kt`, the mirror of the phone's `touchTypography`):
`label` and `description` 12.5 → 14 — Medium and Regular-dim beside `body`'s Regular, Notion's
own separation — `caption` and `eyebrow` 11 → 12.5. `body`, `heading` and the editor's sizes
already sat at the grounds' heights and hold; `pageTitle` holds at 18 — it is the page's title
and the bar's at once, between Notion's 14 px top-bar name and its 40 px in-page title, and the
brief named the elements *on the page*. The phone is untouched (its own pass, item 23). A
measured 9 against the grounds' 10 is the antialiasing threshold on a Medium capital; the
Regular *Herbert* at the same 14 sp measures 10.

## Not changed, recorded

- The Table's cells, the calendar grids and the Timeline keep their dense classes (`GRID_DENSE`
  at `caption`, now 12.5 under a pointer through the same step).
- Notion's in-page title is a 40 px display line the page bar does not have; a display title
  inside the page is a later question, not a size.

## The size count (the same day, `/typography-spacing-core`)

`dk scale` (base 14, ratio 1.125 — a Major Second) holds every size in use: 11 · 12.5 · 14 · 16 ·
17.5 · 20 · 22.5 · 25; the bar's 18 was the one chrome size off it. Decided in one batch: **two
chrome sizes** on the desktop — 14 for everything, 12.5 for captions and eyebrows (Notion's and
the Claude app's shape); **the bar's title at 16 / 600**, the editor's size (the Claude app's title
bar measured 11 px of cap — 16 / 600's); **text inside scaled drawings is readable or absent** —
the canvas embed's and the mind-map card's threshold moves from a 0.5 fit to 12.5 ÷ 14 ≈ 0.89, the
fit under which a 14 sp label would fall below the smallest chrome size (Obsidian's rule, measured
on item 7); desktop only. The desktop's page now carries **12.5 · 14 · 16**, with 20 · 24 for H2 /
H1 alone; the embed in the walk's page draws its boxes bare (its fit is 0.5), the mind-map card
keeps its two lines at 14 (its fit is 1).

| after the count | cap px |
|---|---|
| the bar's title (16 / 600) | 10–11 (was 13) |
| chrome — rows, labels, values, chips, headings | 9–10 |
| captions, eyebrows | 9 |
| a block's text | 12 |
| a canvas embed's card text at a 0.5 fit | 9 — `caption` over the bare box (was ≈ 7; hidden for one build) |

**Amended the same day** — the user, on the build: "the text in the mind map disappears completely", and *Look at Garden plan* (the embed's boxes bare). Hiding was Obsidian's rule for a board, and wrong for a card whose boxes mean nothing without their words. Below the threshold the layer still draws the boxes alone, and `ReadableLabels` (`ui/pages/ReadableLabels.kt`) writes each box's words over it at `caption` — one to three lines by the box's scaled height, an ellipsis past that — for the canvas embed (a card's text, a page card's title, a frame's label) and the mind-map card (the root at Medium). The words never shrink and never vanish; the class `SCALED_LABEL` → `caption` in the type table.

## The cards (the same day)

The user, on Garden plan: the cards are "disproportionately big" — 180 × 90 dp around one line of
14 sp, six and a half times its text; Obsidian's card at rest measured 252 × 63 px on L9 (250 × 60
CSS), a strip about four times its text that grows when typed into. Decided: **a strip that grows
with its text — 200 × 48 dp, one line; 68 two; 88 three** (`cardLines` / `cardHeight` in
`domain/canvas/Frames.kt`, the mind map's estimate — characters per line, hard breaks counted —
so the board's hit-tests, the arrows' anchors and heads, the embed's fit and the JSON Canvas
export read one `nodeBox`); a page card is one line, its glyph beside its title; frames keep
their stored sizes (the handle resizes them — Beds stays as drawn). The badges moved from a
reserved row above the text to a reserved column at the strip's right edge (× over the link
handle; the text keeps a 28 dp end margin) — the width the strip has to spare, never its height;
the text still never moves. Measured on the board: the card 191 × 46 px at the board's 0.96 fit,
the text centred.
