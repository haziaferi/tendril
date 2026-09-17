# Critique — the quick switcher as a centred card, on the build

*2026-09-17 · `design-critique-plus`, the after-pass for the L6 PR (§0.10 item 22's L6, with the
audit's #6 field family). Read-only; native grabs at the user's 967 × 1100 window (Ink dark,
Compact — the shell scale 0.98 there), the PIL line probe beside the same afternoon's grabs of
Notion Calendar's Ctrl+K and Obsidian's Ctrl+O / Ctrl+P at the same 125 %; the window driven by
user32 input and by computer-use (the JetBrains Runtime's `java.exe` is granted; the arrows need
real input — `keybd_event` without the extended-key flag never reached the field). The walk is
the plan's verification list, each step once. **Phone: not walked** — the phone's overlay keeps
its form and gains the Recents rows.*

## Measured — beside the grounds (device px at 125 %)

| | Tendril (Compact, 0.98 scale) | Notion Calendar (Ctrl+K) | Obsidian (Ctrl+O) | note |
|---|---|---|---|---|
| frame | a centred card over the scrim, **top at 220 px of 1100 = 20 %** | centred, top ≈ 21 % | at the top edge | the page readable behind it (the scrim lightens on a dark register — the F1 card's and the slide-over's rule) |
| card width | **558** (560 dp) | 678 | 699 | the F1 card's 560, Obsidian's 560 CSS |
| field | **≈ 35** (36 dp; its top border shares the card's) | 46 | 48 | was Material's 57 |
| row pitch | **29** (`rowHeightDp`) | 36 (= 29 CSS) | 32 | was 40 |
| footer | 26 dp under a hairline, `↑↓ Navigate · ↵ Open · Esc Close` | 32 px, the same three | ≈ 44 | |
| the F1 card | the same frame, radius 10 (was 14), two columns at the user's window | — | — | |

## Walked

- **Ctrl+K** → the card at 20 % of the window, the field focused, *RECENT* with eight rows
  (*Reading log · 7 h ago* … *journal/2026-09-15 · 2 days ago*), the first selected on
  `surfaceVariant`, the hint row *Type to find a page · `>` for a command*, the footer.
- **`ca`** → *PAGES*: *Call the library* with *Ca* at heading weight, *Journal* and
  *journal/2026-09-13* with their FTS snippets after the title on one line; *COMMANDS*: *New
  canvas* (no chord), *Go to Calendar* `Ctrl` `2`, *Start timer: Call the library* — three, the
  cap; the rest of the matching commands not listed.
- **`> go`** → *COMMANDS* alone: the five *Go to* rows with `Ctrl` `1` … `5`; ↓ ↓ moved the
  selection to the third; ↵ closed the card and switched to Tasks.
- **`zq`** → *No pages match ‘zq’* inside the card, the footer under it.
- **Esc** closed it; a click on the scrim closed it (the page pixel back from the scrim's
  (87, 89, 92) to the ground's (27, 29, 33)).
- **Settings** → the API key in the 36 dp field with *API key* leading and *Reveal* at its
  right (*Save* appears on a change, *Clear* with a stored key); the sync passphrase the same
  frame beside *Choose folder…*.
- **F1** → the shortcuts card on the shared frame, centred, two columns; its key chips are the
  switcher's.

## Findings

1. **[Med · observed, fixed before this pass]** *Go to Tasks habits* and *Go to Road map*: the
   command titles were spelled from the enum (`TASKS_HABITS`); they now take the shortcut
   table's labels (*Tasks*, *Road Map*) — the F1 card's names, one source.
2. **[Low · observed]** The Recents list a Journal day as *journal/2026-09-17* — the title as
   stored; the hover card and the tree show such a page as its date. The audit's Med/Low list
   already records the title's spelling (the layout pass's #… on the Journal child); the
   switcher inherits whatever that fix decides. Not this PR's.
3. **[Low · judged]** The scrim lightens a dark register (`onSurface` at 32 %): the page behind
   reads as a lit veil rather than a dimmed one. Established by 14b's slide-over and the F1
   card, so consistent; a `scrim` token per register (dark on both grounds) is one line in
   `Registers.kt` if ever wanted — recorded, not changed here.
4. **[Low · measured]** The field's top hairline coincides with the card's top border (both
   1 dp at the same y), so the field reads as the card's head with one line under it — the
   mock's drawing. Its left and right borders are hidden the same way. Intended; noted so the
   next reader does not hunt for a missing border.

## Cannot verify

- The phone: the overlay's Recents rows and the sections' eyebrows in its 16 dp rows — not run.
- The window below 840 dp: the window's minimum (800 × 600) is still a wide window at this
  scale, so the `!wide` branch was not reached on the desktop.
- The Save / Clear pills' appearance on a change — the code path, not walked with a real key.

## Disposition

Finding #1 fixed and compiled; #2–#4 recorded. Merge.
