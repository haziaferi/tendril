# Critique — pop-out windows, on the build

*2026-09-16 · `design-critique-plus`, the post-build function walk for B§13.6 #6. Read-only;
observable-level findings. **Desktop**, Ink dark, the main window at ≈ 1200 × 800: `···` → *Open
in a window*; the row menu's *Open in a window*; Shift+click; a block typed in the pop-out and
read in the main pane; Ctrl+F in the pop-out; a backlink pushed inside it, Escape twice; `···` →
*Open in the main window*; *Show on Road Map* from a pop-out; a page trashed from the tree while
popped out; Ctrl+W; quit and relaunch with two pop-outs open; the text size compared between the
two windows on the same block. **Phone: not walked** (testing paused) — no opener is composed
there by construction.*

## Top priorities

1. **[High · process, not the build]** Mid-walk the dev database was **wiped**: a
   `computer-use` `open_application("Tendril")` resolved to an installed 2026-09-08 preview
   `Tendril.exe` (schema v8, `fallbackToDestructiveMigration`) sharing `~/.tendril-desktop-dev/`,
   which recreated the v20 file at v8. Recovered from the main file's last checkpoint (v19, every
   page; the WAL with the destruction set aside in `wiped-2026-09-16/`); the dev app re-ran
   19 → 20. Lost: the day's post-checkpoint edits (a shelf block, a seeded child, a scratch page).
   Recorded in memory; the fix is outside the app (uninstall the preview exe; front windows with
   `user32`, never by app name). Also: a killed instance whose *pop-out* held the process's
   main title survived `taskkill /FI "WINDOWTITLE eq Tendril"` — two instances ran at once for a
   while, which briefly looked like windows that would not close.
2. **[Med · seen, fixed before merge]** *Open in a window* did not appear in the page's `···`
   on the first build: the workspace's chrome is remembered across pages and the lambda had
   captured the tab root's null page id. **Fix:** the item reads the open page from the nav
   state at click time.
3. **[Med · seen, fixed before merge]** A restored pop-out came back **800 px wide**: `WindowFrame.
   decode` raised every stored size to the *main* window's 800 × 600 minimum. **Fix:** `decode`
   takes the minimum to apply; the registry passes the pop-out's 480 × 400.
4. **[Low · seen, fixed before merge]** The OS title followed the pop-out's *seed* page, not the
   page pushed inside it (*Escape test — Tendril* while showing *Call the library*). **Fix:** the
   title observes the page on top of the pop-out's stack.
5. **[Low · seen, fixed before merge]** The shelf kept showing a page trashed from the tree — an
   older gap the pop-out's trash test exposed. **Fix:** the shelf closes when its page's
   `deletedAt` is set.

## Walked

- **The three openers** — the page's `···`, the row's menu and Shift+click each opened a
  720 × 600 window titled *〈page〉 — Tendril*; the second and third cascaded 32 dp from the last;
  a second request for an open page fronted it (no second window). The tree marked each with ⧉.
- **Two windows, one page** — a block typed in the pop-out appeared in the main pane as it was
  typed; Ctrl+Z in the pop-out took it back in both. Two ViewModels over one Room, as planned.
- **The pop-out's keys** — Ctrl+F opened its own find bar (*1 of 2*), the main window's stayed
  shut; a backlink pushed *Call the library* with a back arrow, Escape popped, a second Escape
  did nothing (decided: stops at the root); Ctrl+W closed it.
- **Cross-window verbs** — *Open in the main window* showed the page in the main pane, fronted
  it and closed the pop-out; *Show on Road Map* opened the map focused on the page, fronted the
  main window, closed the pop-out.
- **Trashed while out** — *Move to Trash* on the row closed the page's window.
- **Remembered** — two pop-outs open at quit came back where they were (`popout_pages=9,11`,
  `popout_frame_<id>`); a trashed page's id was dropped on restore.
- **The scale** — the same block in both windows, the same crop, pixel-identical line positions:
  the pop-out draws at the main window's scale.

## Judged

- **[Low]** A first pop-out with no remembered frame is placed by the platform (top-left over
  the main window) rather than beside it; cascading from the *main* window's frame + 64 would
  land it clear of the page. One line for a later pass.
- **[Low]** Ctrl+1…5 in a pop-out do nothing (by design — the tabs are the main window's), and
  the F1 card is the main window's too; a pop-out has no card. Acceptable: the pop-out's four
  keys are Escape, Ctrl+F, Ctrl+W and the back/forward arrows.

## Cannot verify

- **Focus across windows** beyond the walk: a `DropdownMenu` open in one window and a click in
  the other; IME behaviour.
- **A maximised pop-out**'s frame is not stored (floating only, as the main window's).
- **The phone**: nothing to walk — `popOuts` is null there.

## Disposition

#2–#5 fixed; #1 is a process finding, recorded in memory and here. Nothing High in the build;
merge.
