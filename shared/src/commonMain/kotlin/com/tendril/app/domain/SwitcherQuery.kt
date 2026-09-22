package com.tendril.app.domain

import com.tendril.app.data.page.Page
import com.tendril.app.data.page.BlockSearchHit
import com.tendril.app.data.page.PageSearchHit

/**
 * §3.1.7 / B§6 #11 — what the quick switcher's one field means. Obsidian's rule: text finds
 * pages; a leading `>` finds commands. Pure, so the ranking is tested without a database.
 */
sealed interface SwitcherMode {
    data class Pages(val query: String) : SwitcherMode
    data class Commands(val query: String) : SwitcherMode
}

fun parseSwitcherInput(text: String): SwitcherMode =
    if (text.startsWith(">")) SwitcherMode.Commands(text.drop(1).trim()) else SwitcherMode.Pages(text.trim())

/** One thing the palette can do; [keywords] widen the match beyond the title; [chord] is the
 * key it is also bound to (`Ctrl+2`), looked up by the scaffold from the shortcuts table so the
 * card and the F1 card cannot disagree — null when the command has no key. */
data class SwitcherCommand(val title: String, val keywords: String = "", val chord: String? = null, val run: () -> Unit)

fun filterCommands(commands: List<SwitcherCommand>, query: String): List<SwitcherCommand> =
    if (query.isBlank()) commands
    else commands.filter { query.lowercase() in (it.title + " " + it.keywords).lowercase() }

/**
 * Pages whose **title** starts with the query come first, alphabetically; then the block hits
 * (v25 — `block_fts`, one row per matching block, its own snippet, in the index's order) with
 * at most [perPage] rows for one page — the rest are one Ctrl+F away once the page is open — and
 * none for a page already listed by its title. A blank query lists nothing — the field is not
 * a page list.
 */
private val WHITESPACE = Regex("""\s+""")

fun rankPageHits(query: String, livePages: List<Page>, blockHits: List<BlockSearchHit>, perPage: Int = 3): List<PageSearchHit> {
    if (query.isBlank()) return emptyList()
    val q = query.lowercase()
    val byTitle = livePages
        .filter { it.deletedAt == null && it.title.lowercase().startsWith(q) }
        .sortedBy { it.title.lowercase() }
        .map { PageSearchHit(pageId = it.id, title = it.title, icon = it.icon, snippet = "") }
    val titled = byTitle.mapTo(mutableSetOf()) { it.pageId }
    val perPageCount = mutableMapOf<Long, Int>()
    val blocks = blockHits.mapNotNull { hit ->
        if (hit.pageId in titled) return@mapNotNull null
        val n = perPageCount[hit.pageId] ?: 0
        if (n >= perPage) return@mapNotNull null
        perPageCount[hit.pageId] = n + 1
        // A multi-line block's snippet on the row's one line — the match must not sit past a newline.
        PageSearchHit(pageId = hit.pageId, title = hit.title, icon = hit.icon, snippet = hit.snippet.replace(WHITESPACE, " ").trim(), blockId = hit.blockId)
    }
    return byTitle + blocks
}

/**
 * L6 (2026-09-17) — what the card lists, in sections. A blank query shows *Recent* (the pages
 * most recently edited, each with its age — Notion's Recents); a query shows *Pages* (the
 * ranked hits) and then, when any match, up to [commandTail] *Commands* under their own label
 * (the plan's assumption A1); `>` shows *Commands* alone and every match. Empty sections are
 * dropped, so a query with no hit and no command yields nothing — the card's own empty line.
 */
sealed interface SwitcherItem {
    data class Hit(val hit: PageSearchHit, val meta: String? = null) : SwitcherItem
    data class Cmd(val command: SwitcherCommand) : SwitcherItem
}

data class SwitcherSection(val label: String, val items: List<SwitcherItem>)

fun switcherSections(
    mode: SwitcherMode,
    hits: List<PageSearchHit>,
    recents: List<Pair<PageSearchHit, String>>,
    commands: List<SwitcherCommand>,
    commandTail: Int = 3,
): List<SwitcherSection> {
    val sections = when (mode) {
        is SwitcherMode.Commands -> listOf(SwitcherSection("Commands", filterCommands(commands, mode.query).map { SwitcherItem.Cmd(it) }))
        is SwitcherMode.Pages ->
            if (mode.query.isBlank()) listOf(SwitcherSection("Recent", recents.map { (hit, age) -> SwitcherItem.Hit(hit, age) }))
            else listOf(
                SwitcherSection("Pages", hits.map { SwitcherItem.Hit(it) }),
                SwitcherSection("Commands", filterCommands(commands, mode.query).take(commandTail).map { SwitcherItem.Cmd(it) }),
            )
    }
    return sections.filter { it.items.isNotEmpty() }
}

/** The sections' items in order — what ↑↓ walk and ↵ runs. */
fun List<SwitcherSection>.flat(): List<SwitcherItem> = flatMap { it.items }

/** Where the typed letters sit at the start of [title] (Obsidian's bold prefix), case-insensitively; null when the title does not start with them or the query is blank. */
fun prefixRange(title: String, query: String): IntRange? {
    val q = query.trim()
    if (q.isEmpty() || !title.startsWith(q, ignoreCase = true)) return null
    return 0 until q.length
}
