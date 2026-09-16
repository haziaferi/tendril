# Critique — the shelf, on the 14h·1 build

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14h·1. Read-only;
observable-level findings. **Desktop** at the remembered frame (≈ 1200 × 800), Ink dark, Windows in
dark mode: *Escape test* → `···` → *Show beside ▸ Today's Journal*; `⇄`; `×`; Ctrl+Shift+\;
*Show beside ▸ Road Map around this page* at depth 1 and 2; a click and a double-click on a node;
a mention inside the shelf's page; the tree row's right-click → *Open beside* on *Garden plan*
(a canvas); Ctrl+click on *Untitled*; the handle dragged past the clamp and back; a block typed
in the shelf's page; quit and relaunch. `prefs.properties` read after. **Phone: not walked** —
testing paused; nothing there moved (the openers live in the workspace's chrome, which the phone
never composes).*

## Top priorities

1. **[Med · seen, fixed before merge]** The graph header's title read *Aroun…* at 380 dp: the
   Road Map's own `FilterChip`s for the depth took ≈ 150 dp of a 380 dp bar. **Fix:** the shelf's
   depth control is three **28 dp** targets (`DepthChips`, the critique's floor), not the chips;
   the title now reads whole (*Around Escape test*).
2. **[Med · seen, fixed before merge]** A page in the shelf drew its title at `titleLarge`, so
   *journal/2026-09-16* was clipped after eight characters beside three 48 dp buttons. **Fix:**
   `PaneChrome.compact` — the shelf's bar sets the three screens' title field to `titleMedium`,
   and the shelf's own `⇄` / `×` are 32 dp targets with 20 dp glyphs (the page's `···` keeps
   its 48).
3. **[Low · judged]** *Show beside ▸* opens its two items as a second menu **under** the parent
   menu rather than beside it — the same placement `Urgency ▸` has
   (`urgency-ladder-function.md` #3). Readable; 14h·2 #10 moves both to the item's right edge.
4. **[Low · judged]** The tree marks the *main* page only; the shelf's page has no mark in the
   tree. With three panes a second, fainter mark on the shelf's row would say where things are.
   Recorded for the small things; not a blocker (the shelf's own bar names its page).

## Dimension by dimension

- **The header rule** — one bar height across the three panes: the tree's header, the page's bar
  and the shelf's (the page's own, or the graph's `ShellTopBar`) share the 52 dp and the one
  hairline. The kind's glyph sits where the back arrow would; `⇄` then `×` then the page's `···`.
- **The three contents** — *Today's Journal* opened with its Today strip live (*Pane task*,
  *Stretch · Every 1 day(s)*); the graph at depth 1 showed *Escape test*'s three neighbours and at
  2 the Journal root joined; a canvas (*Garden plan*) and a plain page (*Untitled*) opened beside
  through the two row paths.
- **The swap** — `⇄` put the Journal in the main pane and *Escape test* in the shelf; the tree's
  mark followed the main pane. A swapped-out Journal is stored as that day's page (`page:<id>`),
  which is what was on screen.
- **Beside itself** — a click on the *Escape test* mention inside the shelf's *Call the library*
  turned the shelf into *Escape test*'s graph (the page was the open one); opening *Books v12* in
  the main pane brought *Escape test* back as a page. The rule is "while it is the open page",
  which reads as a pin: the stored content never changes under you.
- **The graph's gestures** — the map's own: a click focuses (the rest dims), a click on the
  focused node opens it in the shelf, a double-click opens it in the main pane (the shelf then
  shows the graph around the new page). A single click now waits the double-tap timeout, which is
  perceptible but not annoying; the main map keeps its immediate tap.
- **The clamp** — dragged to the left edge of the window, the shelf stopped at ≈ 45 % of the
  workspace while `pages_shelf_width` stored 560; dragging back started from the drawn width, no
  dead zone. The tree's handle idiom mirrored (astride the left hairline, the hand cursor).
- **Remembered** — quit with the graph open: relaunch showed the graph beside the first page
  opened (`pages_shelf=graph`); the chord after `×` reopened the last (`pages_shelf_last`).
- **Editable** — a block typed in the shelf's *Untitled* saved (read back from `blocks`); the
  page's `···` still carries *Find in page*, *History*, *Move to Trash*, but not *Show beside*
  (the shelf's chrome offers nothing to put beside itself).

## What's working

- The shelf costs the page nothing it had: every kind of page is the same screen with two more
  buttons on its bar, and a link inside it stays inside it.
- The Journal as a *kind* rather than a page id: the shelf is still "today" after midnight and
  after a relaunch, and the same create-or-find path the Journal button uses answers it (refused
  under View-Only with the button's message, the shelf closing rather than sitting empty).

## Cannot verify

- **The phone** (paused): that the tab is pixel-identical — by construction the openers are in
  `PagesWorkspace`'s chrome and `PageRowMenuItems` hides *Open beside* when no opener is passed.
- **Below 840 dp** — the workspace, and the shelf with it, are the rail layout's; the window was
  not narrowed this walk (14c's fallback stands).
- **View-Only refusing the Journal's creation** on a day with no page — the path is the button's
  (`PagesViewModel.openJournal`'s `onRefused`); not exercised.

## Disposition

#1 and #2 fixed; #3 and #4 recorded for 14h·2. Nothing High; merge.
