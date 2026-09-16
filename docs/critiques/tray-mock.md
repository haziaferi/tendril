# Critique — the tray, on the mock

*2026-09-16 · `design-critique-plus`, the pre-build pass over `docs/mockups/tray.html` — drawn for
this pass: the notification-area icon with its Win32 menu, a Windows 11 reminder toast, the
quick-add popup in three states, and Settings › Notification area, on Ink dark and Chalk light.
Measured with `craftkit ui` (125 elements; 0 errors, 4 warnings, 2 notes) and by hand for the
pairings the tool does not see inside the frames; judged on the render at 1280 px. Read-only.
Two of the four frames are the OS's — the menu is what AWT's `PopupMenu` draws, the toast is
Windows' own card — so their type and colour are not Tendril's to fix; only their words are.*

## Grounding

| app | global quick add | where the chord is set | the popup | residency |
|---|---|---|---|---|
| Todoist (Windows) | Ctrl+Space, or Win+Alt+Q | Settings → Desktop, customisable | a floating bar with natural-language chips | runs in the tray |
| TickTick (Windows) | Shift+Alt+A (global) · Ctrl+Shift+A (in-app bar) | fixed | the in-app bar | runs in the tray |
| Things (Mac) | Ctrl+Space Quick Entry | System settings | a small floating window; Enter saves, Esc discards | a helper app |
| Notion (Windows) | — | — | — | no tray |
| Microsoft's Win32 UX guide | — | — | — | *Minimize*, not *Close*, should send an app to the notification area |

Tendril takes Todoist's shape (a pick list, a chip-previewed line, tray residency) with Things'
popup discipline (Enter/Esc, nothing else on it). Microsoft's Minimize rule is recorded in the
spec and not followed: a reminder needs the process alive, and every app in the table closes to
the tray. Sources: Todoist's shortcut article, TickTick's desktop-shortcuts article, Cultured
Code's Quick Entry article, Microsoft Learn *Notification Area* (Win32 UX guide) — linked in the
plan.

## Top priorities

1. **[High · judged]** The empty popup (C, first) is a bare field on a dark card: nothing on it
   says *Tendril* or *task*. A chord pressed in another app should land on something
   recognisable — Things puts its window chrome around Quick Entry, Todoist its mark. **Fix:** a
   20 dp `+`-in-a-circle glyph in the accent inside the field's left (the Calendar bar's quick-add
   button glyph, so the two homes share one sign); the card keeps the hover card's hairline and
   nothing else. The placeholder stays a real example (*Dentist fri 14:30 !*), which is the second
   half of the identity.
2. **[Med · measured]** Radii: the popup is 12, the hover card 10, the field 8, the chips 6 —
   `craftkit` counts six radii across the document (the OS frames add 5 and 4). Tendril's floating
   surfaces are the hover card's **10**; a second value for one more floating surface is the shape
   language drifting by construction. **Fix:** the popup at 10 dp; the field at 8 and the chips at
   6 are the app's existing two.
3. **[Med · judged]** The foot line *Enter adds · Esc closes · Win+Alt+T from anywhere* names the
   chord that just opened the window. Redundant there; useful only when the popup came from the
   tray menu, whose item already carries the chord. **Fix:** the foot reads *Enter adds · Esc
   closes*; the chord lives in the tray item, Settings and the F1 card.
4. **[Med · judged]** Windows 11 hides a new notification-area icon in the overflow flyout (`^`)
   until the person drags it out; the toast still arrives, the icon does not show. The Settings
   description promises *Reminders arrive as Windows notifications while it runs* and should say
   where the icon went. **Fix:** the description gains one clause — *the icon may sit behind the
   taskbar's ^ until you drag it out*. Under `gradlew run` the toast's app line reads *Java(TM)
   Platform SE binary* (the packaged exe gives it *Tendril*); recorded in the windows spec, not
   fixable in code.
5. **[Low · measured]** The mock's Chalk placeholder is 4.42:1 on the field (`#727270` on
   `#F5F5F5`) — the mock's hand-typed tokens, not the app's: `textDim` is solved to ≥ 4.6 on the
   worse of the ground and `surface2` (14g·1), and `OutlinedTextField`'s placeholder reads
   `onSurfaceVariant` = that token. Nothing to change; noted so the number is not mistaken for a
   defect on the build.
6. **[Low · judged]** The Win32 menu's *Quick add…* wrapped in the mock (a 176 px box); AWT sizes
   its menu to its longest item, so the build will not wrap. The item's label puts the chord after
   two spaces — AWT `MenuItem` has no shortcut column on Windows without a `MenuShortcut`, which
   would register a *second* accelerator. Keep the two-space spelling.

## Dimension-by-dimension

- **First impression** — C (typed) reads at once: a line, four chips, a quiet foot. A reads as
  Windows, which is right — the icon is one of six and the menu is the OS's. D reads as the
  Settings pane it sits in: the section title, a switch row, a label, chips.
- **Hierarchy** — in C the field (16, the editor's size — the line is content) sits above the
  chips (12.5 Medium) above the foot (11 dim): three steps, no competition. The *Added* state
  swaps the foot for a `description` line with the accent's check; the chips stay so the eye is
  not asked to re-read the card — Todoist's *Task added* pattern. In D the switch's label at
  `body` and its explainer at `description` are the pane's existing pair (Tasks & Habits' rows).
- **Type** — the seven styles, nothing else in Tendril's frames: `pageTitle` 18/600 (Settings),
  `heading` 14/600 (the sections), `label` 12.5/500 (*Quick add from anywhere*), `body` 14 (the
  switch's line, the F1 line), `description` 12.5 dim (the explainer, the failure line, *Added*),
  `caption` 11 dim (the foot), the field at the editor's 16. The OS frames use Segoe UI at the
  system's sizes; the audit's literal-type rule never sees them.
- **Colour and contrast** — measured: the foot 6.22:1 on the dark ground, the placeholder 5.32:1
  on the field, the failure line 6.52:1 (`#E08A80`), *Added*'s accent 7.65:1; light: the dim
  4.82:1, the failure 6.31:1, the link 8.02:1. The urgency chip's stripe 4.28:1 dark / 3.95:1
  light — a mark, ≥ 3:1 (14g·3's rule). The toast's body 9.18:1 and the menu's key hint 7.08:1
  are the OS's. Accent used twice in Tendril's frames (the glyph to come, *Added*'s check) — spare.
- **Layout and spacing** — the popup's 12 / 16 / 10 padding, the field 44, the chips row 8 under
  the field, the foot 10 under the chips; `craftkit` flags 14 spacing values over the whole
  document, eight of them the OS frames' (2, 3, 5, 14, 30). Tendril's frames hold 8 / 12 / 16.
  The popup is 560 wide at 22 % of the screen's height — Spotlight's band, Things' top-third.
- **Consistency** — the chips are `QuickAddPreview`'s, the field the strip's `QuickAddField`, the
  switch row Settings' own; the one drift is the radius (#2). The section sits between *Tasks &
  Habits* and *Keyboard* — beside the other desktop-only section, not before Theme.
- **Copy** — *Keep Tendril running in the notification area when the window closes* names the
  OS's word for the place (Microsoft's, not *system tray*); *Quit from the icon's menu* says where
  the exit went. The failure line names the chord and the verb. The tray items are verbs
  (*Open Tendril*, *Quick add…*, *Quit*). The toast's title leads with the time (*In 10 min ·
  Dentist*; *Due now · Dentist*; *Habit · Stretch*) so a glance ranks it.
- **Interactivity** — the popup's focus rules (blank → focus loss closes; typed → stays) are
  stated on the mock, not drawable; the chip *Task ⇄* is the strip's flip and reads as a control.
  The switch and chips are 20 × 36 and 27 px tall in the mock — the build's `Switch` and
  `FilterChip` are Material's 32 dp; no finding.
- **Fit for purpose** — a person mid-something else presses a chord, types one line, presses
  Enter, is back where they were: the popup asks nothing it cannot infer and confirms in 700 ms.
  The tray is three verbs and a separator.

## What's working

- The OS's parts are left to the OS — no attempt to restyle a Win32 menu or a Windows toast.
- One quick-add field on both homes (the Calendar strip and the popup) through `QuickAddField`.
- The failure line is an ordinary `description` in the error colour, beside the thing that failed.

## Cannot verify

- How AWT's `TrayIcon.displayMessage` renders on this Windows 11 build (a toast in the
  notification centre is expected; the balloon's click-through to `onAction` is documented for
  Windows) — the build's walk.
- Whether `RegisterHotKey` with `MOD_WIN | MOD_ALT` on `T` is free on this machine — the picker's
  failure line is the fallback. *(It was not: Windows' Game Bar holds Win+Alt+T — the build's
  default is Win+Alt+N, `tray-function.md` #1; the mock was regenerated with the final list.)*
- The popup's transparency + undecorated corners on Windows (a `transparent = true` window with
  a rounded `Surface`): Compose Desktop supports it; the shadow's edge is what the walk checks.

## Disposition

#1–#4 taken into the plan (the glyph, radius 10, the foot's two verbs, the description's
clause); #5–#6 recorded. Build.
