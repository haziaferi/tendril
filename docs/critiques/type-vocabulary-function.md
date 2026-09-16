# Critique — the type vocabulary, on the build

*2026-09-16 · `design-critique-plus`, the post-build walk for the type PR (Inter at true weights;
seven styles; headers bold, not large). Read-only; observable-level findings, measured where a
number exists. **Desktop**, Ink dark, the user's 967 × 1039 window beside Notion's Preferences
(Windows at 125 %, Compact 0.85): Settings, the tree, *Escape test* (the editor), the Calendar's
Week with the tray, Typeface switched to DM Sans and to Serif and back. **Phone: not walked**
(testing paused).*

## Measured, beside Notion (device px)

| | before (#92's build) | after | Notion |
|---|---|---|---|
| Settings' page title | 19 (Regular) | **18 SemiBold** | 33 (*Preferences*, 24 CSS) |
| a section title (*Sync folder* / *Density*) | 19–20 Regular | **12–14 SemiBold** | 15 SemiBold (*Appearance*) |
| a description line | 10 | 10 | 12 (Notion's body) |
| a chip's label (*Compact*) | 15 | 13 Medium | 10 (*Use system setting*) |
| the tree's row pitch | 33 | 33 | 29–30 |

The hierarchy is now carried the way Notion carries it — by weight at the body's size — and the
sizes sit at or under Notion's. The row pitch is unchanged (a `dp`, proportional): at 33 px for
14-px text against Notion's 29–30 for 14-CSS-px text it is the one number still above Notion's
settings sidebar, by ~10 %; recorded, not changed here (the tree's 32 dp row is 14h·2's, chosen
against the Table's; a step to 30 dp is a one-line follow-up if the eye asks for it).

## Top priorities

1. **[High · measured, fixed before merge]** The desktop had been drawing every `Medium` as
   Regular: DM Sans was loaded at its default instance only, and Compose synthesises bold but not
   Medium. The build loads each family at 400 / 500 / 600 through `variationSettings` — switching
   Typeface to DM Sans now shows *Density* and *Theme* in a real SemiBold, which the app never had.
2. **[Med · seen, fixed before merge]** 40 chrome sites drew rows and toggles at `bodyLarge` 16 —
   task rows, page cards, the switcher's rows, Settings' toggle labels, the Table's cells — while
   the tree drew its rows at 14. All of them are `body` (14) now; `bodyLarge` is the editor's
   alone (`editorBody`).
3. **[Low · judged]** The eyebrow's tracking moved from `0.7.sp` to `0.06 em` inside the style;
   at 11 sp the two are the same 0.66 sp, so nothing moved on screen — the spelling did.

## Walked

- **Settings** — *Settings* 18/600; *Sync folder*, *Claude (opt-in)*, *Density*, *Theme*, *Tasks
  & Habits*, *Keyboard* all one style (heading); *Register* / *Mode* / *Typeface* labels 12.5
  Medium; descriptions 12.5 dim; the chips 12.5 Medium — beside Notion's Preferences the two
  columns read as one family of decisions.
- **The tree** — *Pages* a heading; rows 14 Regular; the empty pane's line `body` in the outline
  colour.
- **The editor** — the page bar's title 18/600; the body 16; a `Bold` span still bold (content,
  `SpanVisualTransformation.kt` exempt from the audit); the property labels dim at 12.5, the
  values 14.
- **The Calendar** — *Tasks* a heading; UNSCHEDULED · 2 / OVERDUE · 2 eyebrows; chips 14 with the
  due date a caption; the grid's all-day chips captions; today's header SemiBold.
- **Typeface** — Inter → DM Sans → Serif → Inter: each at true weights; a stored `sans` keeps DM
  Sans; a phone that never chose takes Inter (tested).
- **The audit** — rule 12 *literal type* passes with zero findings over the 45 sizes and 31
  weights that were there; a re-introduced `fontSize = 13.sp` fails the run.

## Cannot verify

- **The phone**: the seven styles at Touch (14/600 × 1.23 × the phone's factor); Inter's hinting
  at 11 sp on a 420-dp screen.
- **Inter at 11 sp on Windows in Skia**: legible on this display at 125 %; a 100 % display is
  unmeasured.

## Disposition

#1–#2 fixed; #3 recorded. Nothing open; merge.
