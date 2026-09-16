# Critique — pop-out windows, on the mock before the build

*2026-09-16 · `design-critique-plus`, the pre-build pass for B§13.6 #6 (pop-out windows), on
`docs/mockups/pop-out.html` — drawn for this pass: the main window with one pop-out over it, the
pop-out's `···`, and two pop-outs cascaded on Ink dark. `craftkit ui` (274 elements, 0 errors,
5 warnings, 3 notes) and `craftkit render` at 1460 wide; the render read whole. The type and
spacing sprawl the tool reports is the mock's own (nine sizes, twelve spacings) — the app's scale
is six sizes since 14h·2 and is not at issue here. Measured and judged findings labelled.*

## Top priorities

1. **[Med · judged]** Two titles stacked: the OS title bar says *Untitled — Tendril* and the page
   bar under it says *Untitled* again, 32 + 52 px of chrome before the first block on a window
   that is 520 px tall. Compose cannot draw into the OS bar without an undecorated window (which
   costs the OS's own move/resize/snap), so the page bar stays — it holds the editable title and
   the `···` — and **the OS title mirrors the page's title as it is typed**, so the two never
   disagree. **Fix:** the pop-out's default frame grows to **720 × 600** so the chrome is a
   smaller share; its minimum 480 × 400 (the editor's floor, not the shell's 800 × 600).
2. **[Med · judged]** *Escape at the root closes the window* (rule 1): a stray Escape while
   editing first drops the focus, a second one would close a window the person may have placed
   carefully. Nothing is lost — the page is saved — but Obsidian, Notion and the browsers do not
   close a window on Escape. **Fix:** Escape in a pop-out is the pop-out's back (a pushed page,
   the find bar, a focused block) and **stops at the root**; Ctrl+W and the OS `×` close.
3. **[Med · judged, from the code]** The shell's scale is a function of the *window's* shorter
   side (`shellScaleFor`): a 600 px pop-out beside a 1200 × 800 main window would score lower and
   draw its text smaller than the same page in the main window. **Fix:** a pop-out takes the
   **main window's scale** — one scale per app, the pop-out's frame does not change what 14 sp
   means.
4. **[Low · judged]** *Show on Road Map · in the main window* — an action in one window that
   acts in another needs the other window to come forward, or it looks like nothing happened.
   **Fix:** every cross-window verb (Road Map, *Open in the main window*) fronts the main window
   (`window.toFront()`), and the pop-out that handed off its page closes.
5. **[Low · measured]** The tree row's ⧉ at 13 px sits where the shelf's ring-and-glyph already
   sits; a page both in the shelf and popped out shows two glyphs at the row's end. Acceptable
   at 14 dp each with 4 dp between; the ring stays the shelf's.

## Dimension by dimension

- **Hierarchy** — the pop-out is the page and nothing else: no rail, no tree, no timer, no chip
  row. Right: a second window exists to hold *one thing* beside the first.
- **Copy** — *Open in a window* / *Open in the main window* / *Close window · Ctrl+W* say what
  happens; the menu keeps the page's own verbs first and the window's two after a rule, as the
  workspace chrome appends *Trash…*.
- **Consistency** — the page bar is the same bar (52 dp, the kind's glyph in the leading slot as
  the shelf has it, the title at `titleMedium`'s size in a narrow window — `PaneChrome.compact`
  again); the cascade is the OS convention (32 px).
- **Fit** — a database pops out as its Table, a canvas as its board: `PageRoute` again, so the
  three kinds cost nothing extra. Two windows on one page are two ViewModels over one Room —
  edits flow both ways through the same tables; the History sheet captures once per window's
  edit burst, which is right (each window is an editor).
- **Interactivity** — a pop-out's keys: Escape (back), Ctrl+F, Ctrl+W, Alt+← / Alt+→; everything
  else — the tabs, Ctrl+K, Ctrl+N, F1 — belongs to the main window. The F1 card gains Ctrl+W
  under *Navigate* with a *pop-out* note, generated from the table as before.

## What's working

- One page and its history in a window, opened from the same three places the shelf is opened
  from (`···`, the row's menu, a modifier-click) — the vocabulary is already learned.
- Remembering the open pop-outs and their frames across a relaunch (`popout_pages`,
  `popout_frame_<pageId>`) is what makes a second window worth placing.

## Cannot verify

- Focus and IME behaviour across two Compose windows (a `DropdownMenu` open in one, a click in
  the other); the ViewModel stores' lifetimes (one owner per window, cleared on close).
- The scale rule under a maximised pop-out.

## Disposition

#1, #2, #3 and #4 fold into the plan; #5 stands.
