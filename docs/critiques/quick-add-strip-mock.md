# Critique — the quick-add strip's field and the tray's clamp, on the mock

*2026-09-17 · `design-critique-plus`, the pre-build pass over `docs/mockups/quick-add-strip.html`
(L7 and L8 of the desktop audit — §0.10 item 22). `craftkit ui` on the file, `craftkit render`
at 1100 × 3600, the contrast pairs computed from the registers' own hexes; the grounds measured
natively this evening at 125 % (TickTick's add bar; the side panes of Todoist, TickTick, Notion
Calendar, Obsidian). Read-only; the one measured finding was applied to the mock before it was
committed.*

## Grounding — measured today (device px at 125 %; CSS ≈ px ÷ 1.25)

| app | what | measured | what it settles |
|---|---|---|---|
| **TickTick** (the Inbox list) | the add bar at the top of the list | **36 px** at rest (a filled row, no border), **38** focused (an accent outline), ≈ 30 CSS px — the row's height; 568 px wide (the pane); a date glyph and a chevron inside its right end; the typed date parsed inline, no chip row; Esc after clearing left nothing behind | a one-row field is the norm; the parsed tokens live *with* the line |
| **Todoist** | *Add task* | its task editor would not open on this machine (offline; the section editor opened instead and was cancelled) — [Assumed] an inline card: a title line, a description line, date / priority / reminder chips, *Add task · Cancel* | the chips-beside-the-line pattern, in a card |
| **Notion Calendar** | quick add | none — an event is created by a click on the grid, the title parsed for a time | the strip's placement is Tendril's (14f·2); nothing here |
| **Todoist · TickTick · Notion Calendar · Obsidian** | the side pane's share of the window | **21 %** (288 of 1390) · **21 %** (the list pane) · **13 %** (249 of 1920) · 34 % (the file explorer, user-resized) | a 30 % cap is above every default and under Obsidian's user-set pane |
| **Tendril today** | the strip | a 44 dp bar holding Material's 66 px `OutlinedTextField` (a 2 dp accent outline) and a chip row under it: **≈ 100 px**; the popup's field the same 56 dp | L7 |
| | the tray | 240–360 dp absolute (`calendar_tray_width`); at the user's 967 px window 360 dp leaves **66 px** lanes; the header's *Planned 30m* clips to *Planned* with no ellipsis | L8 |

**Feature scoring** (Reach · Fit · Gap · Value, 1–5):

| feature | Reach | Fit | Gap | Value | take |
|---|---|---|---|---|---|
| A one-row field for quick add (28 dp in the 44 dp strip) | 1 measured (TickTick 30 CSS) + Fantastical / Things [Assumed] | 5 (`TendrilField(height = 28.dp)` — the find bar's) | 5 | 4 (56 px back on every view the strip is open on) | **yes — decided** |
| The chips inline at the field's right, wrapping under only when they do not fit | 0 (TickTick parses inline; Todoist's chips sit under [Assumed]) | 4 (`FlowRow`) | 4 | 3 | **yes — decided** |
| Tokens highlighted inline, no chips | 2 (TickTick; Fantastical [Assumed]) | 3 (the parser's spans exist; the drop affordance moves into the text) | 5 | 3 | not taken (offered; a later PR if wanted) |
| The popup's field on the switcher's 36 dp frame | — | 5 | 5 | 3 (the two quick-add homes match) | **yes — the audit's note** |
| The tray clamped to 30 % of the pane | the grounds' panes 13–21 % of the window | 5 (the shelf's 45 % rule, `effectiveWidthDp`) | 5 | 4 | **yes — decided** |
| The header's minutes hidden below a 90 dp lane, the word kept | — (no ground draws a planned line) | 5 | 5 | 3 (never clips; the word still marks the day) | **yes — decided** |
| A permanent add bar above the grid (TickTick) | 1 | 3 (44 dp of every view) | — | 2 | not taken (offered) |

## Findings

1. **[Med · measured, applied to the mock]** The plan's number was wrong: at the user's window a 30 % tray leaves **82 dp** lanes, not 90 (the pane is 903 dp at the 0.98 scale; 30 % = 271; the grid 632 − 56 gutter = 576 ÷ 7). Frame B first drew *Planned 1h 45m* in an 80 dp lane — the exact clip the rule forbids. The mock now draws *Planned* alone there and says where the number returns (a 1200 dp window: ≈ 104 dp lanes; or the tray collapsed). **Binding on the build**: the rule is the lane's measured width against 90 dp, not the tray's state — `WeekGridView` already knows `laneWidthState`.
2. **[Low · measured]** The strip's placeholder in `dim` on `surface2` (the strip's ground) measures **4.69 : 1** on Ink and 4.67 on Chalk — above 4.5, unlike the L5/L6 tint cases (the register solves `dim` against `surface2` by design). Nothing to change; recorded because the strip is the one place a placeholder sits on `surface2` rather than `surface`.
3. **[Low · judged]** Four chips (*Task · Fri 18 · 14:30 · High*) take ≈ 300 dp; with the field's minimum of 200 dp the one-line form holds from ≈ 560 dp of strip width — every wide window. The parser can yield six chips (kind, date, time, estimate, repeat, deadline, urgency = seven at most); the wrap is the safety, not the norm. The build measures the widest case on the walk (*Standup mon 9:00 for 30m every week by fri !!*).
4. **[Low · judged]** The popup keeps its chips under the field (frame C): it is 560 dp wide and its foot line sits under the chips already; a one-line form there would push the foot to the right of the field, where Things keeps nothing. Not folded in.
5. **[Low · measured]** `craftkit ui`: the radii 4 · 5 · 6 · 8 · 10 are the L5 shell's plus the chips' 8 (Material's `InputChip`) — the audit's family; the strip adds none.

## What's working

- The strip becomes the find bar's twin on the same tab family: 44 dp, a 28 dp field, 28 dp buttons — one row where there were three.
- The clamp is a share of the pane, the header's rule a share of the lane: both scale with the window, nothing fixed.
- The popup and the strip stop being the two remaining Material fields on the desktop; with L6, every desktop text field is `TendrilField`.

## Cannot verify

- The wrap's row height with real `InputChip`s (the mock draws 26 dp chips; Material's are 32) — measured on the build.
- The lane width the build reports at 30 % (`laneWidthState` at the user's window) — the after-pass's first number.
- The phone: the strip is the wide window's; the Day view's inline field and the tray strip are untouched.

## Disposition

Finding #1 applied to the mock and binding on the build; #2–#5 recorded. Build.
