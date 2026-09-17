# Critique — the quick switcher as a centred card, on the mock

*2026-09-17 · `design-critique-plus`, the pre-build pass over `docs/mockups/quick-switcher.html`
(L6 of the desktop audit — §0.10 item 22 — with the Settings' two Material fields folded in as
the audit's #6 family). `craftkit ui` on the file, `craftkit render` at 1300 × 5200, the contrast
pairs computed from the registers' own hexes; the grounds measured natively this afternoon at
125 % from the five installed apps, driven by user32 input (the computer-use capture is stale on
these Electron windows as it is on the Compose one). Read-only; the two measured findings were
applied to the mock before it was committed.*

## Grounding — measured today (device px at 125 %; CSS ≈ px ÷ 1.25)

| app | what | measured | what it settles |
|---|---|---|---|
| **Notion Calendar** (Ctrl+K, the command menu) | a centred card over a dimmed window | **678 px** wide (544 CSS); its top **≈ 21 %** down the window; field row **46** (37 CSS); rows **36 px** pitch (29 CSS — the same row as Notion's menus and Tendril's Compact profile); section labels ≈ 11 CSS px; the chord at each row's right; a footer **32 px** with *↑↓ Navigate · ↵ Select · Esc Close*; the selected row's tint inset 8 px from the card's edge | the card's placement, width, row, footer, the chords |
| **Obsidian** (Ctrl+O the switcher, Ctrl+P the palette) | a card hanging from the window's top edge, no scrim | **699 px** (560 CSS); top 70 px under the title bar; field **48** (38) with a divider; rows **32** (26) at a 12 px list inset; the typed letters bold in each title; the palette's chords at the right; a footer of key hints | the width's other reading (560 = the F1 card's), the bold prefix, hints |
| **Notion** (Ctrl+K, search) | a search dialog with a preview pane | **1008 px** (806); field 48; a filter-chip row; two-line rows **54** at a 56 pitch; *Recents* when empty; a footer 41 with *Ctrl+↵ · Ctrl+L · Shift+Ctrl+K* | the empty state (Recents); not the frame — a search surface, not a switcher |
| **TickTick** | a popover beside the rail's search icon | 557 × 458; field 50 with an accent underline; scope chips *Task · List* | not taken |
| **Todoist** | its search would not open on this machine (offline) | — | [Assumed] a centred dialog; nothing rests on it |
| **Tendril today** (`ui/switcher/QuickSwitcher.kt`) | a full-pane overlay on `background` | Material's 56 dp `OutlinedTextField` → **57 px**; rows **40** (16 dp padding); nothing when empty; the pane-filling `EmptyState` on no match; the tree and the page covered | the audit's L6 |
| **The F1 card** (`ShortcutsOverlay.kt`) | a centred `Popup` over the 32 % scrim | max 560 dp, radius 12, the key chips on `surfaceVariant` | the frame the card reuses (radius → 10, the hover card's — the audit's radius family) |

**Feature scoring** (Reach = how many grounds do it · Fit = fits Tendril's constraints · Gap = how far today's build is · Value = what the person gains; 1–5):

| feature | Reach | Fit | Gap | Value | take |
|---|---|---|---|---|---|
| A centred card over a scrim, top at ≈ 20 % | 2 measured (Notion Calendar; Notion's dialog is centred too) + Raycast / Spotlight [Assumed] | 5 (the F1 card's `Popup`) | 5 | 4 (the page stays readable; the eye lands on the field) | **yes — decided** |
| A card at the window's top edge, no scrim | 1 (Obsidian) | 4 | 5 | 3 | not taken |
| 560 dp wide, clamped to 60 % of the window | Obsidian 560, Notion Calendar 544 | 5 | 5 | 3 | **yes** — by the grounds |
| A 36 dp field | Notion Calendar 37, Obsidian 38 (CSS) | 5 | 5 (57 today) | 4 | **yes** |
| Rows at the profile's height (29 dp Compact) | Notion Calendar 29, Obsidian 26 | 5 (`rowHeightDp`, the menus' row) | 4 (40 today) | 4 | **yes** |
| Recents when empty | 1 (Notion) | 5 (`updatedAt` — no new state) | 5 | 4 | **yes — decided: recently edited** |
| Chords on command rows | 2 (Notion Calendar, Obsidian's palette) | 5 (generated from `SHORTCUTS`, the F1 card's way) | 5 | 3 | **yes — decided** |
| A footer of key hints | 3 (all three) | 5 | 5 | 2 | **yes — decided** |
| Section eyebrows | 1 (Notion Calendar) | 5 | 5 | 3 (they earn their place once a plain query lists commands too) | **yes — decided** |
| The matched prefix bold | 1 (Obsidian) | 5 (the snippet already does it) | 3 | 2 | **yes — decided** |
| A plain query also listing matching commands | 2 (Notion Calendar's menu is commands-only; Todoist's Ctrl+K mixes [Assumed]) | 4 (the `>` rule stays; the plain list gains a capped tail) | 5 | 3 | **assumption A1** — see finding 3 |
| A preview pane (Notion) | 1 | 2 (the hover card already previews a page) | 5 | 2 | not taken |
| The Settings' fields in the same 36 dp frame | — | 5 | 5 (56 dp Material) | 3 | **yes — decided** |

## Findings

1. **[Med · measured, fixed in the mock]** The selected row's secondary text — *edited just now*, a snippet — in `dim` on the `soft` tint measures **3.45 : 1** on Ink (4.15 on Chalk): L5's #1 and the Month chip's rule again — the register solves `dim` against the ground and `surface2`, not against the accent's soft. The mock now draws a selected row's meta and snippet in `text` (7.64 : 1). **Binding on the build**: the selected row's secondary text takes `onSurface`; the unselected rows keep `onSurfaceVariant` (5.36 : 1 on the ground).
2. **[Low · measured, fixed in the mock]** *New canvas* wore a `Ctrl N` chord that belongs to *New page*. The build cannot make this mistake — the chord is looked up from `SHORTCUTS` by the command's action, and a command without an action shows none — but the mock is the reference and was wrong.
3. **[Med · judged]** Frame B lists two commands under a plain query (*ca* → *New canvas*, *Go to Calendar*). §3.1.7 says text finds pages and `>` finds commands. The *Commands* eyebrow the user chose only earns its place if a plain query can show both kinds, so the plan takes **assumption A1**: a plain query lists the pages first and then **up to three** matching commands under their eyebrow; `>` still lists commands alone and every command. Notion Calendar's menu is commands-only and Obsidian keeps two palettes — neither ground does this; Todoist's and Raycast's mixed lists are [Assumed]. If the user would rather keep the surfaces apart, the eyebrows appear only in the Recents state (*Recent*) and the `>` state (*Commands*), and A1 is one deleted branch in `rankMixed`.
4. **[Low · judged]** The Recents rows' ages (*edited 2 h ago · yesterday · 12 Sep*) are `relativeEdited`'s strings — the phone card's. On a 560 dp card at `description` size they read as one column at the right; they are the only thing that distinguishes a Recents row from a hit, and the *Recent* eyebrow already does that. Kept: Notion's Recents carry the same ages, and the age is what tells *Escape test* (just now) from *Trip* (6 Sep) when both titles are equally familiar.
5. **[Low · measured]** `craftkit ui`: 6 radii (3 · 4 · 5 · 6 · 8 · 10) and 16 spacing values in the document — the mock draws the whole window (the L5 shell) as context, so most are the shell's. The card's own set is one family: radius 10 (the card), 6 (the selected row, the field's buttons), 4 (the key chips); 4 · 8 · 12 dp gutters. The F1 card's radius 12 → 10 with this PR, as the audit's #6 recorded.
6. **[Low · judged]** The card's top at 20 % of the window (160 dp of 800; Notion Calendar's 21 %) puts the field at the eye's resting height without hiding the page's title behind the scrim on a 1200 × 800 window; on the user's 967 × 1039 window it is 208 px down. Fixed as a share, not a dp — the standing rule.

## What's working

- The row is the menu's row (29 dp) and the field is under it by a third — the card is 12 rows of content in the height today's overlay spends on three.
- The chords come from the one table the F1 card reads, so the two surfaces cannot disagree.
- The Settings' two fields stop being the only Material text fields on the desktop — the switcher's frame becomes the app's field.

## Cannot verify

- The scrim's read over a real page (the mock's page is three lines of placeholder text); the after-pass measures the card against Notion Calendar's on the build.
- Focus on open (the mock is static): the build's `FocusRequester` on the field, and Esc through the `Popup`'s `onDismissRequest` — the F1 card's path, already walked.
- The phone: unchanged (the full-screen overlay stays under `!wide`); not drawn.

## Disposition

Findings #1 and #2 applied to the mock; #3 stands as the plan's assumption A1 unless the user says otherwise; #4–#6 recorded. Build.
