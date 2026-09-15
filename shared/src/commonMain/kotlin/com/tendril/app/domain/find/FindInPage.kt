package com.tendril.app.domain.find

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType

/**
 * §0.10 item 19 — find in page, the pure half: every occurrence of [query] in the given blocks'
 * text, in the order given (the caller passes the outline's visible blocks, so the order is the
 * page's), case-insensitive. **The blocks' text only** (decided 2026-09-16): not the title, which
 * is on screen, and not a row's property values, which the Table's filter owns. Kinds without
 * searchable text are skipped; a block reference is searched on its cached text, since that is
 * what the page shows.
 */
data class FindMatch(val blockId: Long, val start: Int, val end: Int) {
    val range: IntRange get() = start until end
}

private val UNSEARCHABLE = setOf(BlockType.DIVIDER, BlockType.IMAGE, BlockType.PAGE_MENTION, BlockType.CANVAS)

fun findMatches(blocks: List<Block>, query: String): List<FindMatch> {
    if (query.isBlank()) return emptyList()
    val needle = query.lowercase()
    return buildList {
        for (block in blocks) {
            if (block.type in UNSEARCHABLE) continue
            val text = block.content.lowercase()
            var from = 0
            while (true) {
                val at = text.indexOf(needle, from)
                if (at < 0) break
                add(FindMatch(block.id, at, at + needle.length))
                from = at + maxOf(needle.length, 1)
            }
        }
    }
}

/** The next cursor after ↵ (forward) or Shift+↵ (backward), wrapping at either end; null when there is nothing. */
fun nextIndex(current: Int?, count: Int, forward: Boolean): Int? {
    if (count <= 0) return null
    val cur = current ?: return if (forward) 0 else count - 1
    return if (forward) (cur + 1) % count else (cur - 1 + count) % count
}
