package com.tendril.app.domain

import com.tendril.app.data.page.Page
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

/** One thing the palette can do; [keywords] widen the match beyond the title. */
data class SwitcherCommand(val title: String, val keywords: String = "", val run: () -> Unit)

fun filterCommands(commands: List<SwitcherCommand>, query: String): List<SwitcherCommand> =
    if (query.isBlank()) commands
    else commands.filter { query.lowercase() in (it.title + " " + it.keywords).lowercase() }

/**
 * Pages whose **title** starts with the query come first, alphabetically; then the FTS hits
 * (which find the query anywhere, titles included since §0.8 step 8a) in the order the index
 * returned them. A page is listed once, as its higher entry. A blank query lists nothing —
 * the field is not a page list.
 */
fun rankPageHits(query: String, livePages: List<Page>, ftsHits: List<PageSearchHit>): List<PageSearchHit> {
    if (query.isBlank()) return emptyList()
    val q = query.lowercase()
    val byTitle = livePages
        .filter { it.deletedAt == null && it.title.lowercase().startsWith(q) }
        .sortedBy { it.title.lowercase() }
        .map { PageSearchHit(pageId = it.id, title = it.title, icon = it.icon, snippet = "") }
    val seen = byTitle.mapTo(mutableSetOf()) { it.pageId }
    return byTitle + ftsHits.filter { seen.add(it.pageId) }
}
