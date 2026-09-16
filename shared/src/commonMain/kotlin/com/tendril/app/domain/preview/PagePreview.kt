package com.tendril.app.domain.preview

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.OutlineBlock
import com.tendril.app.domain.outlineOf
import java.time.Instant

/**
 * B§13.6 #3 — what a hover preview shows of a page: its shape at a glance, not a rendering of
 * it. One line per block in the outline's order — a heading bold, a to-do as its box, a mind map
 * or a canvas as a count — cut to [LINE_CHARS], at most `max` of them, and how many blocks were
 * left out so the card can say *3 more blocks* (and say nothing for a page of two). Pure over
 * the page's rows so the four hover sites (an inline `@mention`, a mention block, a block
 * reference, a Road Map node) share one card and one test.
 */
enum class LineKind { TEXT, HEADING, TODO_OPEN, TODO_DONE, COUNT }

data class PreviewLine(val text: String, val kind: LineKind = LineKind.TEXT, val marked: Boolean = false)

data class PagePreview(val title: String, val kind: PageKind, val editedAt: Instant, val lines: List<PreviewLine>, val more: Int)

const val LINE_CHARS = 90
const val PREVIEW_LINES = 6

/** A block's line, or null when the block has no line to give (a divider, a picture, an empty paragraph). */
private fun lineOf(entry: OutlineBlock): PreviewLine? {
    val b = entry.block
    return when (b.type) {
        BlockType.DIVIDER, BlockType.IMAGE -> null
        BlockType.CANVAS -> PreviewLine("Canvas" + b.content.takeIf { it.isNotBlank() }?.let { " · " + cut(it) }.orEmpty(), LineKind.COUNT)
        BlockType.PAGE_MENTION -> b.content.takeIf { it.isNotBlank() }?.let { PreviewLine("→ " + cut(it)) }
        BlockType.HEADING_1, BlockType.HEADING_2, BlockType.HEADING_3 -> b.content.takeIf { it.isNotBlank() }?.let { PreviewLine(cut(it), LineKind.HEADING) }
        BlockType.TODO -> b.content.takeIf { it.isNotBlank() }?.let { PreviewLine(cut(it), if (b.checked == true) LineKind.TODO_DONE else LineKind.TODO_OPEN) }
        BlockType.CODE -> b.content.lineSequence().firstOrNull { it.isNotBlank() }?.let { PreviewLine(cut(it.trim())) }
        else -> b.content.takeIf { it.isNotBlank() }?.let { PreviewLine(cut(it)) }
    }
}

private fun cut(s: String): String {
    // A block written over several lines reads as one line with a middle dot between them.
    val one = s.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
    return if (one.length <= LINE_CHARS) one else one.take(LINE_CHARS - 1).trimEnd() + "…"
}

/** Every line the page has, with the block it came from; a folded map's subtree skipped unless [foldMaps] is false. */
private fun allLines(blocks: List<Block>, foldMaps: Boolean): List<Pair<Block, PreviewLine>> {
    val outline = outlineOf(blocks)
    val out = mutableListOf<Pair<Block, PreviewLine>>()
    var i = 0
    while (i < outline.size) {
        val entry = outline[i]
        lineOf(entry)?.let { out += entry.block to it }
        if (foldMaps && entry.block.mindMap) {
            // The map's root keeps its own line (it is a block with words); its subtree is one
            // counted line, and a map of no nodes yet says nothing more.
            var j = i + 1
            while (j < outline.size && outline[j].depth > entry.depth) j++
            val n = j - i - 1
            if (n > 0) out += entry.block to PreviewLine("Mind map · " + (if (n == 1) "1 node" else "$n nodes"), LineKind.COUNT)
            i = j
        } else i++
    }
    return out
}

/** The page's first [max] lines and the count left out. */
fun pagePreview(page: Page, blocks: List<Block>, max: Int = PREVIEW_LINES): PagePreview {
    val lines = allLines(blocks, foldMaps = true)
    return PagePreview(page.title, page.kind, page.updatedAt, lines.take(max).map { it.second }, (lines.size - max).coerceAtLeast(0))
}

/**
 * A block reference's card: the referenced line marked, the line before and the line after when
 * they exist (a first block shows what there is, never a blank). Maps are not folded here — a
 * referenced block inside one is still the line the person pointed at. An unknown uid falls back
 * to the page's own preview: the cached text is what the reference card already shows.
 */
fun referencePreview(sourcePage: Page, blocks: List<Block>, uid: String): PagePreview {
    val lines = allLines(blocks, foldMaps = false)
    val i = lines.indexOfFirst { it.first.uid == uid }
    if (i < 0) return pagePreview(sourcePage, blocks)
    val slice = ((i - 1).coerceAtLeast(0)..(i + 1).coerceAtMost(lines.size - 1)).map { k -> lines[k].second.copy(marked = k == i) }
    return PagePreview(sourcePage.title, sourcePage.kind, sourcePage.updatedAt, slice, 0)
}

/** A canvas's card: *Canvas · 4 cards · 2 links* and the first cards' text (an embedded page by its title). */
fun canvasPreview(page: Page, cards: List<String>, links: Int, max: Int = 3): PagePreview {
    val head = "Canvas · " + (if (cards.size == 1) "1 card" else "${cards.size} cards") + (if (links > 0) " · " + (if (links == 1) "1 link" else "$links links") else "")
    val lines = listOf(PreviewLine(head, LineKind.COUNT)) + cards.take(max).map { PreviewLine(cut(it.ifBlank { "(empty card)" })) }
    return PagePreview(page.title, page.kind, page.updatedAt, lines, (cards.size - max).coerceAtLeast(0))
}

/**
 * A database's card: *Table · 3 rows · Read on, Author, Done* — the first view's kind, the row
 * count, the property names — then the first rows' titles; `more` is the rows left. Cells are
 * the Table's business.
 */
fun databasePreview(page: Page, viewLabel: String?, columns: List<String>, rows: List<Page>, max: Int = 3): PagePreview {
    val head = buildString {
        append(viewLabel ?: "Database")
        append(" · ").append(if (rows.size == 1) "1 row" else "${rows.size} rows")
        if (columns.isNotEmpty()) append(" · ").append(cut(columns.joinToString(", ")))
    }
    val lines = listOf(PreviewLine(head, LineKind.COUNT)) + rows.take(max).map { PreviewLine(cut(it.title.ifBlank { "Untitled" })) }
    return PagePreview(page.title, page.kind, page.updatedAt, lines, (rows.size - max).coerceAtLeast(0))
}
