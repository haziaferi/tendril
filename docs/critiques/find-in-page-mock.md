# Critique — the find bar on its mock, before the find-in-page PR

*2026-09-16 · `design-critique-plus`, pre-build pass for §0.10 item 19. Read-only. On
`docs/mockups/find-in-page.html`: two homes for the bar on a wide window (A under the page bar,
B floating top-right) and the phone's bar, drawn at the desktop's Compact scale (0.9) and the
phone's Touch (1.105). **Measured** by walking the rendered frames' computed styles (`craftkit
ui` sees the page chrome — its one error is the mock's own caption at 2.85:1); **judged** on the
frames. Decided on it (2026-09-16): **A on both platforms**; the match set is **the blocks' text
only**.*

## Top priorities

1. **[Med · judged]** A found word and a selected row wear the same colour: the marks are
   `accentSoft` (`--soft` on `--soft-text`, 7.75:1 — legible), which is also the tree's current
   row and the switcher's cursor. "Found" and "chosen" should not share a tint. **Fix:** the
   registers (14g) give find its own token — a warm mark, the browsers' yellow family, in each
   register; until then the marks stay on `accentSoft` and the *current* match on `accent` with
   the surface colour (7.53:1), which already separates the one that matters. Recorded, not
   fixed in this PR.
2. **[Med · measured]** B's floating card (340 × 40 px at 0.9) covers the first line of the
   page and sits under the page bar's `···` — the two things a person opening a page looks at.
   **Resolved by the decision:** A.
3. **[Low · measured]** The bar's buttons are 28 dp = 25.2 px at 0.9 and 23.8 px at the 0.85
   floor — the same target as the tree's `···` (14d #2), so the same floor, acceptable; on the
   phone 31 px in a 48 dp Compose touch target.
4. **[Low · measured]** The count *2 of 3* is 12.5 sp at 4.93:1 (`onSurfaceVariant` on
   `surfaceVariant`) — AA, not AAA. The *No matches* state must not drop to `--faint` (2.85:1 on
   this ground); it keeps the count's colour.
5. **[Low · judged]** The mock's ↑ ↓ × are glyph characters; the app uses `KeyboardArrowUp`
   / `KeyboardArrowDown` / `Close` icons at 18 dp, matching the page bar's.

## Dimension by dimension

- **First impression** — A reads as the browser's find bar: field, count, arrows, close. No
  learning. B reads as an editor's — fine on the desktop, homeless on the phone.
- **Hierarchy** — the current match on `accent` is the one high-contrast object on the page
  while the bar is open; the other marks recede; the count sits beside the field where the eye
  returns after each ↵.
- **Layout** — A pushes the page 44 dp (40.6 px at 0.9) while open — the browsers' precedent;
  the bar's field is full-width, so a long query is readable. B: 340 px card, top 53 px.
- **Colour (measured)** — mark 7.75:1, current 7.53:1, count 4.93:1, icons 4.93:1. All pass.
- **Interactivity** — ↵ next, Shift+↵ previous, wrapping; Esc closes and leaves the cursor on
  the current match; the bar opens with the selection as the query (the browsers' habit).
  Typing in a block while the bar is open re-runs the search on the live text — marks survive
  editing because they are computed from the block's content each frame.
- **Copy** — *2 of 3*, *No matches*; the button descriptions *Previous (Shift+Enter)*, *Next
  (Enter)*, *Close (Esc)* name the keys.

## What's working

- One form on both platforms; the phone's reached from `···` → *Find in page*.
- Marks inside the text through the span transformation — no second rendering of the block.

## Cannot verify

- The app's rendering; a match inside a formatted span (bold + mark) — composed styles, to be
  checked on the build; the LazyColumn scroll landing the current match in view.

## Disposition

#1 → 14g (a `find` token per register), noted in the plan. #2 resolved (A). #3–#5 shape the
plan. A function walk on the build follows before its merge.
