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
