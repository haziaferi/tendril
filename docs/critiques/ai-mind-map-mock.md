# Critique — `docs/mockups/ai-mind-map.html` (the fourth verb, pre-build)

*2026-09-22. §10's last deferral in the mind-map line — the Claude-generated map — as a fourth
verb in §0.6.15's row: the selection goes out with one instruction, a nested list comes back,
and the sheet offers to insert it as a mind map (§0.6.2's flag on the root) or as a list.
Decided with the user before the mock: the selected text is what is sent (the verbs' rule);
the sheet is the one surface mocked. Measured with `craftkit ui` and the contrast arithmetic;
judged on the render. Grounds [Assumed] — Notion AI, Xmind Copilot and Whimsical's AI map were
not running here; each lands a generated outline as insertable content below the prompt, none
takes the page's own text as the input, which is the constraint Tendril keeps.*

## Top priorities

1. **[Med] The root's accent dot** — the mock marks the outline's root with a dot in the
   accent. §2.3's token map reserves the accent for selection, links, mentions, today and the
   timer; a bullet in it reads as a link. *Fix:* the root at Medium weight with the ordinary
   bullet; no accent in the preview.
2. **[Med] `white-space: nowrap` on the preview rows** — a long generated line would run under
   the box's edge. *Fix:* one line, ellipsised (audit rule 19 already refuses a `maxLines = 1`
   without an overflow); the inserted block keeps the whole text.
3. **[Low] The phone's third button wraps alone** — *Cancel* lands on its own row under the two
   inserts (measured on the 390 dp frame). *Fix:* the phone's sheet drops *Cancel* — the sheet's
   own dismiss (a drag, the scrim, back) is the cancel, as the Add task sheet does; the desktop
   slide-over keeps it (Esc and × exist, but the row is the existing verbs' and stays whole).

## Dimension-by-dimension

**Visual hierarchy** — the sheet reads title → outline → the primary → the caption; the outline's
indent (18 dp a level) is the tree's, so the nesting is read at once. The primary button is the
only filled control in the panel, as in the three existing verbs' sheet.

**Colour and contrast** (measured) — the caption `dim` on Ink dark 5.36 : 1, on Chalk 5.01; the
failure red 6.65 on Ink dark; the primary's text on the accent 7.65 (Ink) and 8.41 (Chalk);
every pairing over 4.6. The accent dot (#1) is a reserved-use finding, not a contrast one.

**Typography** — the panel's types are the seven styles (pageTitle 18/600 for the header,
body 14 for rows, description 14 for the caption under a pointer; the phone's step to 16 for
rows). `craftkit` reports the page's document scale at a minor second, which is the mock's own
prose, not the sheet.

**Layout and spacing** — the sheet's paddings are the slide-over's (4/20/20); the outline box's
rows at 24 dp are the switcher's pitch; the hairline box separates the preview from the page's
text behind the scrim. `spacing-sprawl` (14 values) and `radius-sprawl` are the mock page's
chrome (the phone's 24 dp corner, the toolbar's 5 dp) — the build draws only the app's family.

**Copy** — *Insert as a mind map* / *Insert as a list* name the two writes; the caption is the
existing sentence, unchanged, and still true (only the selection left). The busy line *Asking
Claude…* is the existing one.

**Consistency** — the fourth verb sits in the existing row at the existing weight; the pressed
state is the soft, the toolbar's own. The sheet's three states mirror the three verbs' sheet.

**Interactivity** — the two inserts are the only writes; nothing is written when the sheet
opens. The failure state keeps *Try again* and *Cancel* as today's.

## What's working

- One instruction, one parse: the reply is a Markdown list, which is what §0.6.2 says a mind map
  *is* — no new format, no second model of the map.
- The preview is honest — it shows the rows the page will get, and the map is the page's own
  rendering of them, so what is previewed is what is inserted.

## Cannot verify

- The grounds (Notion AI, Xmind Copilot, Whimsical AI) — not installed or not running; asserted.
- Whether Claude returns a clean list every time — the parser is tolerant of `-` / `*` / `•` /
  numbered markers and tabs, and a reply with no list lines is a failure the sheet names.
