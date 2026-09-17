# Critique — the audit's fixes, on the build

*2026-09-17 · `design-critique-plus`, the after-pass for the fix PR that answers the desktop
audit's High findings (`desktop-layout-full.md` L1–L3, `desktop-type-full.md` T1–T2,
`desktop-function-full.md` F1–F3) and the design-layer pre-pass's D1–D3
(`desktop-design-layer.md`). Read-only, the same sites measured the same way — native grabs at
the user's 967 × 1039 window, Ink dark, the PIL band projection — and, for the first time, **four
apps measured live on this machine at the same 125 %** as the ground: Notion, Obsidian, TickTick
and Todoist (installed for this pass; a habit and nothing else was created in TickTick). L4, the
Month grid, is its own PR. **Phone: not walked.***

## What changed, and by what rule

The brief for this PR was *as few judgement calls by hand as possible*. Three things carry it:

1. **The rename moved no pixel.** The seven styles are aliases of Material's roles
   (`TendrilTypeTest` pins each), so the 196 raw-role sites in shared UI and 36 in the two apps'
   own screens were renamed by script; `tools/audit.py`'s *material type role* rule refuses the
   old spelling from now on.
2. **A kind of text has one style, decided once.** `tools/type_sites.py` reads every text site's
   features from the code — its slot, its colour token, its content, its position among its
   block's Texts, the block's other calls — and assigns one of **31 classes** by rules in a fixed
   order (ten fixtures in `tools/tests/`). The class→style table
   (`tools/type_table/table.json`) was **chosen by `meta-optimizer`** over a deterministic scorer
   — hierarchy (a later line never louder than its block's first), one style per (colour,
   position), preservation of what is built, the title-to-meta ratio Notion draws (12 : 14),
   headers at Medium or above, ≤ 4 styles a screen, ≤ 5 sizes — 16 open classes × 7 styles, 24
   candidates, converged at generation 12. The best (0.7400) and the runner-up (0.7392) were a
   near-tie under 0.001 differing only in `CARD_TITLE` (heading vs pageTitle) and `GRID_DENSE`
   (label vs caption); the one that changes fewer sites won (preservation 0.703 vs 0.666 —
   de-optimizer's near-tie rule, stated before the run). `META = caption` — the tray PR's choice
   for the task rows' meta — scored 0.687 and lost to `description` on the ground ratio (12.5/14
   = 0.89 against Notion's 0.86; 11/14 = 0.79) and on preservation. **83 sites moved beyond the
   rename, every one by its class**; **3 sites** carry an explicit `// type:` comment (the mind
   map's node text, the neighbourhood's depth chips, the register swatch's name) — the
   judgements, written where the audit reads them. The *type class* rule now re-classifies every
   site on every run and fails a style that is not its class's.
3. **Every menu and every one-line row go through one composable.** `TendrilMenu` /
   `TendrilMenuItem` (the *bare menu* rule refuses Material's directly), `RowControls.kt`'s
   `listRow`, `TitleAndMeta`, `rowButtonModifier`, `ListInteractiveMinimum`.

## Measured — before → after, beside the grounds (device px at 125 %)

| site | before | **after** | Notion | Obsidian | Todoist | TickTick | Win11 flyout |
|---|---|---|---|---|---|---|---|
| a menu row (the page's `···`, the row menu, the New view types) | 49 | **30** | 29 | 25 | 32 | 36 | ≈ 32 |
| the Calendar's Day rows | 65 | **30** | — | — | — | — | — |
| the Agenda's task / event rows | 52–61 / 44 | **30** | — | — | — | — | — |
| the Table's rows | 50 | **34** | 36 | — | — | — | — |
| the tree's rows (unchanged) | 30 | 30 | 30 | — | 34 (sidebar) | 38 (sidebar) | — |
| raw Material role names in `ui/` | 196 (+ 36 in the apps) | **0** | — | — | — | — | — |
| sites off their class's style | — | **0** (3 annotated) | | | | | |
| text sites, classified | — | 666, 0 UNKNOWN | | | | | |

The Day rows: the title one line with the time as `description` at its right, ▶ and the bell at
28 dp; the Agenda the same; the Table's floor was `RowMenu`'s 40 dp button, now 28. The page menu:
eight items at 30 px = 240 px (from 343). The task row menu: five labels at one x, the urgency dot
in the text, no leading icons.

## Walked

- **F1** — Ctrl+F on *Escape test*, `child`, Esc; *Books v12* by the tree; *Call the library* by
  the tree → **no find bar** on either. (`FindRequestGate`, 3 tests.)
- **F2** — F1 → *Close or reopen the shelf · Ctrl+Shift+\* under Navigate; every other row as
  before; `ShortcutsTest` asserts every bound chord is on the card.
- **F3** — Habits → *Stretch*: the title with its checkbox, *Repeats · Every day*, *At · any
  time*, *For · no length*, the presence line, chips *Check in today · ▶ Start timer · Move to
  Trash*; *Check in today* → the row ticks, the presence reads *Once this month · Last: giovedì,
  17 set*, the month of dots appears, the chip reads *Undo today's check-in*; *Undo* → back.
  **Ground:** TickTick's habit detail (grabbed) leads with the title, four stat cards (monthly,
  total, rate, streak), a month calendar, a log — the check-in on the row; its Create Habit
  dialog orders Frequency · Goal · Start date · Goal days · Section · Reminder. Tendril's order
  (Repeats · At · For · Streak) is the same shape, with the verbs as chips where TickTick keeps
  them on the row.
- **T2** — History: *1 block*; the Journal strip and the habit row: *Every day*; the Road Map's
  empty focus: *1 hop*; the New view sheet: *View type* as five chips with a line under the chosen
  one (*Rows and columns; every property a cell.*), a Name field with the kind as placeholder,
  *Add view* as the button; the property-type menu names *Multi-select*, *Formula*; the task
  pane's note and the Calendar sheet without their citations.
- **D1–D3** — the keyboard ring in `outlineVariant` (3.3–3.6:1); the two tab-root lines in
  `onSurfaceVariant`; `textDim`/`textFaint` solved on `accentSoft` too (`RegisterSolveTest`, 30
  palettes); one `tabular()` derivation.
- **Chalk light**: not re-grabbed — the pitches are theme-blind (the audit's rule) and no colour
  moved except the ring's and the two lines'.

## Grounds, measured live

| app | menu row | sidebar / list row | notes |
|---|---|---|---|
| Notion (desktop, dark) | 29 | 30 sidebar · 36 table | the page's `···` menu; the Slowburn table |
| Obsidian 1.11.7 (dark) | 25 | — | a file's context menu (Italian) |
| Todoist (light) | 32 | 34 | the Inbox's `···` menu; Search / Inbox / Today rows |
| TickTick (dark) | 36 | 38 | the Inbox's context menu; Today / Next 7 days |
| Windows 11 (WinUI 3 `MenuFlyoutItem`) | ≈ 32 | — | [Assumed] from the design guidance, not grabbed |

Tendril's 30 (the profile's 29 dp) sits inside the field; the grounds range 25–36. **One tool note
again:** a native grab captures whatever window is on top — the Claude desktop window covered the
right third, so Todoist was moved left before its grab; each grab's ground pixel was checked.

## Left open (item 22 keeps them)

D4 the invisible light-mode hover (`surface2` at 4 %), D5 the Road Map's edge alpha, D6 the
duplicate `third`/`thirdStrong` and the unread `accentStrong`/`event`, D7 Material's 28 dp dialog
radius, D8 eight focusable controls with no focus state; the Merged view's habit row; the 30
`maxLines = 1` without `overflow`; and every Med/Low row of the three passes, by id.

## Cannot verify

- The phone: menus and rows under Touch keep Material's defaults by construction (`TendrilMenuItem`
  and `listRow` branch on `profile.pointer`); not run.
- Todoist's task-detail pane: the task-add flow did not take the typed text on this account; the
  pane was not grabbed.
- Whether the scorer's weights are the right ones — they are stated in `tools/type_table/spec.json`
  and the run is reproducible (`seed 11`); a different weighting is a re-run, not a judgement.

## Disposition

The nine High (eight here, L4 next) and D1–D3 fixed; item 22 lists the rest. Merge.
