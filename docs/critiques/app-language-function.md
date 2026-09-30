# The app's language — function walk (plan Phase 4, 2026-09-30)

The habit planner's Phase 4 (`docs/plans/2026-09-29-habit-planner.md`): a Settings choice of English
or Italian, English by default (L1); the words and the dates follow it, the device's region kept
(L2); Italian only for what the plan adds (L3). Walked on the phone (OnePlus 6T, Android, system
language Italian) through `uiautomator` dumps, and on the desktop's dev build (`gradlew run`, never
the installed `Tendril.exe`) through native window captures. Decisions in
`docs/decisions/2026-09-29-habit-planner.md` (*Phase 4 decisions*); the spec's home is §3.5.

## Phone

| Tried | Observed |
|---|---|
| Open Settings on a fresh install of the build | *Language* under *Appearance*; *English* selected; the note *The app’s words and its dates…* |
| Tap *Italiano* (first build) | The row read *Lingua*, but the app **fell back to Pages**: the whole content is keyed on the language and the phone's navigation state was remembered inside it. **Defect, fixed in this phase:** `MainActivity` now holds the `WorkbenchNavState` above `WithAppLanguage` and passes it to `AndroidWorkbenchScaffold` (the desktop already held its own outside). |
| Relaunch after the fix | Still Italian — the choice is stored; *Lingua* and the Italian note; *Settings* and every other string still English (L3) |
| Tap *English*, then *Italiano*, on the fixed build | Each switch in place; the screen stays on Settings; the row and its note change language, nothing else |
| Calendar, Italian | The Day header reads *mercoledì 30 settembre* |
| Calendar, English | *Wednesday 30 September* — the dates follow the app, not the Italian phone (L2) |
| Leave the phone on English | Done: the default |

## Desktop

| Tried | Observed |
|---|---|
| Ctrl+5 on the dev build, English stored | *Language* after *Theme*: *English* ✓ · *Italiano*, and the note. The control spanned the pane's full width where *Mode* and *Typeface* are compact; **made compact** in this phase. |
| Click *Italiano* | In place, still on Settings: *Lingua*, *Italiano* ✓, *Le parole e le date dell’app…* |
| Ctrl+2, the Week | *28 set – 4 ott 2026*; the columns *lun 28 … dom 4*; the tray's *due dom 13*. *Planned*, *due*, *Today*, *Week* stay English — screens this plan does not translate (L3) |
| Click *English*, stop the dev build | `prefs.properties` holds `app_language=en`; no Tendril process left |

## Not walked

Pop-outs and the Quick Add window (they share the process's locale and open in it; one already open
follows on its next opening); the Habits widget and notifications, whose strings are all English
today, so the language reaches only their dates.
