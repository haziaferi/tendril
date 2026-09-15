# Critique — find in page on the build, before its merge

*2026-09-16 · `design-critique-plus`, the post-build function walk for §0.10 item 19. Read-only;
observable-level findings. **Desktop** at 1200×800, Compact, on *Escape test*: Ctrl+F, `child`,
↵ ↵ (wrap), Shift+↵, a no-hit query, Esc; a double-clicked word then Ctrl+F; `···` → *Find in
page*; Ctrl+F on *Books v12* (a Database page); F1's card. **Phone: not walked** — phone
testing paused at the user's request mid-turn; the phone's half (`···` → the bar under the top
bar, the keyboard, back closing it, the Pages list unchanged) is an open item on the PR.*

## Top priorities

1. **[Low · by design]** The query survives Esc: reopening from `···` showed *two · 1 of 1*
   from the earlier seed. Browsers keep the last query too, and a re-search costs one Ctrl+A.
   Kept; a fresh selection still replaces it (the seed wins only when the bar is closed).
2. **[Low · judged]** The bar's arrows grey out at *No matches* — right — but the count's
   *No matches* sits where *1 of 2* sat, so the eye finds the state where it expects the
   number. Nothing to change; noted as the reason the count is not centred.
3. **[Low · recorded]** The mind-map card's text (*Root edited · Child one!*) is not searched —
   it is the outline's own blocks drawn again, so the same words are found in their rows above;
   a page whose only copy of a word is inside a mapped-away subtree would report *No matches*
   for it. Rare (a mapped subtree's rows are hidden by choice); §0.10 item 14's list.
4. **[Low · judged]** Scrolling to the current match is by block row (`animateScrollToItem`),
   which lands the row at the top of the list; a match already on screen still scrolls. A
   `layoutInfo` check for visibility would avoid the jump — with 14f's list pass.

## Dimension by dimension

- **Affordance** — the current match reads as the one dark object on the page; the others
  recede on `accentSoft` (the mock's #1 stands: the tint is selection's until 14g). The field
  takes focus on open, on both doors; the arrows' descriptions name the keys.
- **Reach** — Ctrl+F (the table, listed on F1's card), `···` → *Find in page*; ↵ / Shift+↵
  wrap; Esc closes through the same back dispatch as every sheet; a Database page ignores the
  chord, as decided.
- **Fit for purpose** — a match inside bold text keeps its weight and takes the mark (the
  transformation composes: *Root edite**d*** was bold from an earlier walk and the seed's mark
  drew over *two* without touching the bold run). Marks follow edits: recomputed from the
  live blocks.
- **Copy** — *Find in page* (the field's placeholder and the menu item agree), *1 of 2*, *No
  matches*, *Previous (Shift+Enter)* / *Next (Enter)* / *Close (Esc)*.

## What's working

- Option A as mocked: the page shifts 44 dp, nothing covered; the field full-width.
- The seed: double-click a word, Ctrl+F → the word is the query, *1 of 1*, marked.
- The Database page's silence on Ctrl+F.

## Cannot verify

- **The phone** (paused): the bar under the phone's top bar with the keyboard up; back closing
  it; the Pages list unchanged. The code is shared and the desktop walk exercised it, but the
  phone's keyboard-and-inset behaviour is its own.
- A page with a journal strip or several membership strips above the blocks — the scroll index
  arithmetic assumes the blocks are the list's last run (they are; the arithmetic was exercised
  on a page with one membership strip).

## Disposition

Nothing High. #3 and #4 → §0.10 item 14's list. The phone walk → the PR's open item.
