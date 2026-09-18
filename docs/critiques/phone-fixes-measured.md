# The phone's fix PR — P2–P6 and P11, measured beside the apps on the phone

*2026-09-18 · the OnePlus 9 Pro (480 dpi, 3 px/dp), `uiautomator` dumps for every number. The
catch-up (`phone-catch-up.md`) left five findings and a process gap; the user asked that the
desktop's size discipline — grounds measured live, proportions over fixed numbers — apply here,
where a screen is smaller. So the grounds first: the four apps that run on the phone
(**TickTick, Todoist, Notion, Notion Calendar**; Obsidian installed, not measured this pass),
each dumped once on its own list, its add surface and its menu.*

## Grounds (native px at 480 dpi → dp)

| app · surface | measured | dp |
|---|---|---|
| **Todoist** · a task row (title + date line) | 170 px pitch; title box 69 px (≈ 17 sp), meta 60 px (≈ 14 sp, *Domani 14:30 ⏰ 1*); the check a 28 dp circle | **57** |
| Todoist · quick add (a bottom sheet) | the sheet 614 px; the title line 81 px, no border; the parsed run tinted **inline** (the same form as L7b); chips 108 px; the Add button 144 | 205 · 27 · **36** · 48 |
| **TickTick** · a task row (one line) | 138 px; title box 65 px (≈ 17 sp); *Oggi* 14 sp at the right | **46** |
| TickTick · the add sheet | the title line 65 px; an icon row (date · flag · tag · list · ···) of 24 dp glyphs; no inline parse (recognition off on the account) | — |
| TickTick / Todoist · long-press on a row | **selection**, not a menu — actions live in the detail screen and in swipes | — |
| **Notion** · the page list | 144 px pitch, ≈ 16 sp titles | **48** |
| Notion · a database's table rows | 135 px | **45** |
| Notion · a page's `···` | a full-height bottom sheet *Azioni*: grouped rows of 135 px, a leading icon, the destructive in red, *Duplica ›* **pushes a level inside the sheet** | rows **45** |
| **Notion Calendar** · the 3-day grid | an hour 300 px, the gutter 155, the all-day row 96 | **100 · 52 · 32** |
| Tendril before (`phone-catch-up.md`) | task rows 67 dp (a wrapped meta 86); menus 48; the Add sheet's rows squashed under the keyboard; the submenu beside its item | |

## What changed, measured after

| finding | before | after | ground |
|---|---|---|---|
| **P2** a `TendrilSheet` squashes under the keyboard | the *Repeats* chips 20 px, the *Has deadline* switch over them | the frame **scrolls** (`verticalScroll` on the bottom sheet's and the slide-over's column); the chips 144 px, *Has deadline* and *Cancel · Add* reached by a scroll with the keyboard up; twelve sheets whose content is a lazy list pass `scrolls = false` and keep the bounded height a `LazyColumn` needs — **audit rule 20 *sheet scroll*** pairs the two (a lazy list inside a scrolling frame is a crash; a plain column in a bounded one is the squash); the reminder, edit-entry and insert-block sheets drop the scroll they had added by hand | Todoist's and Notion's sheets scroll |
| **P3** the submenu beside its item | *Urgency ▸* opened over the parent's rows (L11's flip on a 360 dp window) | under Touch a `SubmenuItem` **pushes a level** into its `TendrilMenu`: a back row *← Urgency: None ○* then the five levels, the parent's rows back on ← (`MenuLevel`, `LocalMenuLevel`); under a pointer the side menu is unchanged | Notion's *Duplica ›* pushes |
| **P4** the Pages list's DPAD | ↓ from cold lands on the label chip | **recorded, not built**: no app on the phone navigates a list by hardware keys; the chip row is the first focusable by order and a tap on a row opens the page, so the list cannot take focus by a click as the desktop's does — the 14f·1 claim is withdrawn for the phone | — |
| **P5** the canvas page's `···` | none on the phone | *Show on Road Map · Move to Trash* (the page's and the database's two verbs; the trash asks, refused under the lock — a guard test) | Notion: every page has *Azioni* |
| **P6** the rows' meta | *2026-09-12 · due 2026-09-11 · 0/1 steps* wrapping; rows 67 / 86 dp | `taskRowMeta` (tested): *sab 12 · due ven 11 · 0/1 steps*, the month named outside this month; one line each for title and meta under Touch; the row's 8 dp of padding 4 → **59 dp** | Todoist's 57; TickTick's 46 one-line |
| **P11** nothing composes a layout | — | `PhoneLayoutTest` (Robolectric + `ui-test-junit4`): the label filter row composed under Touch with a label in the store — **fails on the old padding, passes on the clamp** (proved by reverting once); `isIncludeAndroidResources` for the merged manifest, a debug manifest declaring the rule's activity, `DatabaseFileTest` kept on a plain `Application` | — |

## Recorded (Med/Low, §0.10 item 23)

- The phone's task row at 59 dp against Todoist's 57 and TickTick's 46 — the 48 dp checkbox sets the floor; a 40 dp check would bring a one-line row to ≈ 48. Not changed here (the touch target is Material's floor; a decision for the phone audit).
- The Touch profile's menu rows at 48 against Notion's 45 — Material's `DropdownMenuItem`; left.
- P7–P10 as recorded in the catch-up.

## Cannot verify

- Obsidian's rows and menus on the phone (installed, not dumped — the audit's pass).
- The pushed level's placement: Compose re-anchors the menu when its height changes (it moved to the top of the screen once) — read as Material's own placement, not a defect.

## Disposition

P2, P3, P5, P6, P11 built and walked; P4 withdrawn with the reason. Next: the phone audit beside the five apps.
