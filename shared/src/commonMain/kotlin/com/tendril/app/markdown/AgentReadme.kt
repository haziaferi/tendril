package com.tendril.app.markdown

/**
 * `docs/agent-over-export.md` (§0.10 item 17), the one buildable remainder (2026-09-22): the
 * Markdown zip is the interface an agent reads, so the zip explains itself at its root — a
 * `CLAUDE.md` Claude Code loads on its own, and any other agent or person can read. It says what
 * the folder is, how it is laid out, and the three rules that follow from its being a copy.
 * Counts are the export's own; nothing about the person is in it.
 */
fun agentReadme(pages: Int, canvases: Int, images: Int): String = """
# These notes

A Markdown export from Tendril, a personal notes app — $pages page${if (pages == 1) "" else "s"}, $canvases canvas${if (canvases == 1) "" else "es"} and $images image${if (images == 1) "" else "s"}, laid out as the app's page tree. Every `.md` file is one page: its title as the H1, then its blocks. A page's children sit in a folder of its name. Links between pages are relative links inside this folder; a `> quote` ending in a *source* link is a reference to a block on another page; `assets/` holds the images. A `.canvas` file is a JSON Canvas board (Obsidian's format): its `text` nodes are cards, its `file` nodes point at pages here, its `group` nodes are frames. A database's rows are pages in its folder; the table around them (its columns and views) is not here.

## Three rules

1. **Read-only towards the app.** Tendril does not re-import its own Markdown. An edit to a file here is an edit to a copy; a result goes back by hand — pasted into a page, or into the app's quick add.
2. **A snapshot.** These files are the notes as they were when exported. Ask about yesterday's export and you get yesterday's answer.
3. **Where you run is where the notes go.** An agent reading this folder sends what it reads to its own model's service, under its own account — the person chose that scope by exporting.

Answer in the folder's terms: page titles, the tree, the dates in the Journal folder (`journal/YYYY-MM-DD.md` is one day).
""".trimStart()
