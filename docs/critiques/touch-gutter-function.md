# S11 — the block selection's Touch gutter, the build walked

*2026-09-21. Item 19's walk had recorded it: on the phone the block's long press lives on the
row's margin and prefix — 16 dp before a paragraph — thin for a thumb. `docs/mockups/touch-gutter.html`
drew today's zone and three ways out; the user asked which conflicts least with the other
gestures, and took that one.*

## The options, and why A

| option | what it changes | what it meets |
|---|---|---|
| **A — a wider Touch gutter** (taken) | the margin before the prefix 8 → 24 dp under Touch; with the prefix's 8 dp gap **32 dp** before a paragraph's text | **nothing** — the margin's long press already exists beside the list's scroll and the field's gestures; the cost is 16 dp of measure on a 360 dp phone |
| D — the block's long press is the block's | an Initial-pass gesture layer over an *unfocused* field: a hold selects the block, a tap places the caret by hand (`getOffsetForPosition`) and focuses | three seams with the platform: the caret placement re-implemented, the double-tap word selection on an unfocused block lost until the caret is in, the keyboard's opening on a programmatic focus (version-dependent; unverified). Recorded, not taken |
| C — a `⋮⋮` per row while a selection is up | a 24 dp handle in a 32 dp gutter: a tap toggles, a drag moves the run | nothing at rest, but it does not widen the way *in*; the sheet's *Move up / down* carries the phone. Recorded as its own small pass |

Grounds [Assumed]: Notion's phone app selects a block on a long press of the block and gives
the text's selection once the caret is in (Craft the same); Logseq's bullet is the handle;
Obsidian has no block. A synthetic long press over adb did not register in Notion's app, so
nothing was measured there.

## The build

`PageDetailScreen.BlockRow`: the row's inner start padding is `TOUCH_BLOCK_GUTTER` (24 dp) under
Touch and 8 dp under a pointer; nothing else moves — the outer 8 dp, the indent per depth, the
prefix and its 8 dp gap are as they were. The desktop is untouched.

## The walk

| device | tried | observed |
|---|---|---|
| phone | *Trip* → a long press 38 dp from the screen's edge beside *Packing* (a heading) | *1 selected*, the row ringed; the zone measured from the dump: the row's clickable from 16 dp to the text at 47.6 dp — **31.6 dp** (was 16) |
| phone | a tap in the text | the caret, as before; the platform's word selection on a long press in the text, as before |
| desktop | a page's blocks | the 16 dp margin as before; the right-click and the ground click unchanged |

No test: a constant, no logic. Tests 909.

## The phone (2026-09-21)

*Trip* → the first paragraph's ink starts 80 px from the pane's edge at 480 dpi × 0.85 — **31.4 dp** (the
rule's 32; the desktop measured 31.6). The property strip's labels start at 54 px, so the block's text sits
26 px (≈ 10 dp) inside the strip's — the gutter reads as the blocks' own margin, not a misalignment.
