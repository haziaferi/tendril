# The fourth verb, the build walked — `Mind map`

*2026-09-22. §0.6.15's fourth verb built as planned from `docs/critiques/ai-mind-map-mock.md`:
`AiVerb.MIND_MAP` with its one instruction, `domain/ai/OutlineReply.kt` (`parseOutline`, pure,
6 cases), `PageDetailViewModel.insertOutline` (one recorded edit), the sheet's rows preview and
its two inserts, the verb row a `FlowRow`. No key is available in this session, so the walk
proves what the three older verbs' walk proved — the row, the request against the real endpoint
(a genuine 401 proves the headers, the body's shape and the failure parse), the sheet's failure
state on both devices — and the success path is pinned by tests rather than by a paid call.*

## The desktop (Ink dark, Compact, the dev window at 91,35 · 1007 × 885; native grabs)

| tried | observed |
|---|---|
| Settings → a dummy key → *Save* | `~/.tendril-desktop-dev/anthropic.key` written (334 bytes, DPAPI-wrapped); `prefs.properties` never holds it |
| *Escape test* → click into *Child two* → Ctrl+A | the floating toolbar with **Rewrite · Expand · Summarise · Mind map** on one line |
| *Mind map* | the slide-over titled *Mind map*; *Asking Claude…*; then **The key was rejected — check it in Settings**, *Try again* · *Cancel*, the caption — a real 401 from `api.anthropic.com` |
| *Cancel* → Settings → *Clear* | the key file gone; the row gone from the toolbar |

**A copy defect fixed on the walk:** the saved-key line read *Key saved — Rewrite, Expand and
Summarise appear on a selection*; it names the four now. **Recorded, pre-existing (Low):** the
floating toolbar's popup draws through the slide-over's scrim (the popup layer is above it; the
panel is above the popup) — the three older verbs have done the same since 14b/14d; a
`Popup` cannot sit under another `Popup`'s scrim without being closed first — an option for a
later pass (close the toolbar when a sheet opens).

## The phone (the OnePlus, 480 dpi × 0.85; dumps and screencaps)

| tried | observed |
|---|---|
| Settings → Claude → a dummy key → *Save* | *Key saved — Rewrite, Expand, Summarise and Mind map appear on a selection* (the second build; the first said three) |
| *Trip* → long-press *Packing* → the keyboard dismissed | **first build: the fourth button had 20 px** — *Mind map* wrapped letter by letter down the right edge, the inline `Row` being full at Touch's 16 sp |
| the same on the second build | the verbs as a **FlowRow**: *Rewrite · Expand · Summarise* on one line, *Mind map* whole on the next; rows 1467–1530 and 1618–1681 px — a 151 px pitch, ≈ 59 dp, the Touch row |
| *Mind map* | the bottom sheet *Mind map* → **The key was rejected — check it in Settings**, *Try again* alone (no *Cancel* under Touch — the critique's #3 as built), the caption |
| back → Settings → *Clear* | *No key — nothing is sent anywhere* |

**Fixed:** the verb row is a `FlowRow` on both platforms (the desktop's fits on one line and is
unchanged in effect); each label `maxLines = 1` with an ellipsis (rule 19).

## The success path — pinned, not walked

- `OutlineReplyTest` (6): two-space lists, `*` / numbered / `•` markers, tabs and four-space
  steps, a preamble dropped and a trailing colon trimmed, a depth jump clamped to the parent + 1,
  an indented first line as the root, no list → no rows.
- `WritePathSyncTest` gains *a generated outline inserted on one device reaches the other as a
  tree, and one undo removes it*: four rows after the selection's block at its order, the parent
  chain (`Inputs` → `Compost`, `Scraps` → `Inputs`, `Care` → `Compost`), bulleted, the flag on the
  root only; the same tree and flag on device B after a sync; `undo()` leaves the two original
  blocks.
- `AiVerbsTest`: every verb's request carries its instruction, the model and only the text; the
  body never holds a key.

## Cannot verify

- The preview's render with a real reply (its rows at the profile's height, the root at
  `heading`, the ellipsis) — the composable is the mock's geometry by construction; a walk with a
  real key would show it. The 401 path exercised the same sheet's frame and caption.
- Whether a given model returns a clean list — the parser is tolerant, and an empty parse is a
  named failure with *Try again*.

Tests 917 → 924 (`OutlineReplyTest` 6, `WritePathSyncTest` +1); audit PASS; both compiles.
