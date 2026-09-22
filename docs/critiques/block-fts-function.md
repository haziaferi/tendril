# Block-level search (v25) — the build walked

*2026-09-22. §10's last search deferral: `block_fts`, one FTS4 row per block with text beside
`page_fts` (which keeps the title + body row the unlinked-mentions search reads), rebuilt with
the page's row on every write and healed on the first launch; the switcher's body hits are
block hits — the block's own snippet, at most three rows a page, none for a page already listed
by its title — and a block hit opens the page with the find bar on the query and the cursor on
that block (`WorkbenchNavState.openBlock` → `BlockJump` → the page's `findTarget`): the marks
and the scroll are the bar's own, so the promise §3.1.1 struck on 2026-09-06 ("opens the page
and scrolls/highlights the first match") is kept by the machinery that arrived since. No mock
(a rule over the switcher's existing rows). Schema **v25**; nothing synced.*

## The desktop (Ink dark, Compact; native grabs)

| tried | observed |
|---|---|
| the DB before the launch | `PRAGMA user_version` **24**, no `block_fts` |
| the launch | **25**; `block_fts` holds 10 rows over the 4 live pages with text (the heal, once); `MATCH 'hours*'` names the code block on *Call the library* |
| Ctrl+K → `hours` | one row: *Call the library — // the library's opening **hours*** (the block's snippet, the match at heading weight) |
| ↵ | the page opens with the find bar on *hours*, **1 of 3**, the cursor on the code block, the three marks over the syntax colours |
| Ctrl+K → `child` | **first build**: *Escape test — Root edited* — the block's text is three lines and the row's one line showed the first, the match past a newline; **fixed** — the snippet's whitespace collapses in the ranking: *Root edited **Child** one! **Child** two* (and the Journal day's copy of it as a second row) |

## The phone (the OnePlus; dumps and screencaps)

| tried | observed |
|---|---|
| the DB after the launch (pulled cleanly with its WAL; md5 matched) | **25**; 9 rows over the 3 live pages with text; `MATCH 'pack*'` → *Trip*'s *Packing* block |
| the Pages bar's search → `pack` | *PAGES — Trip · Packing* |
| tap | *Trip* opens with the bar on *pack*, **1 of 1**, the mark on *Packing* under the property strip |

## Cannot verify

- A page with more than three matching blocks in one query (the cap) — pinned by
  `QuickSwitcherTest` (three rows of five, the fourth one Ctrl+F away).
- The heal on a database with rows that predate the index but no text — nothing to heal by
  construction (`indexedPageIds` is DISTINCT over rows; a page with no text has none to miss).

Tests 934 → 935 (`QuickSwitcherTest` reshaped over block hits + the heal's block half,
`WorkbenchNavStateTest` +1); audit PASS (the FTS column allowed as `page_fts`'s is); both compiles;
`25.json` committed.
