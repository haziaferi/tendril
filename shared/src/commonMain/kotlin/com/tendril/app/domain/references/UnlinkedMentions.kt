package com.tendril.app.domain.references

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.SpanStyle

/**
 * §3.1.5 (amended, §0.6.12 / B§6 #13) — a page that names this one in plain text without
 * linking to it. [range] is the first occurrence in [block]'s content, what [linkMention] spans.
 */
data class UnlinkedMention(val page: Page, val block: Block, val range: IntRange)

/** Titles too short to mean anything as plain text ("a", "To") are never searched for. */
const val MIN_UNLINKED_TITLE_LENGTH = 3

/**
 * The blocks on *other* live, non-template pages whose text contains [title] — minus the
 * pages in [linkedPageIds] (they already link, they belong to Linked mentions) and an
 * occurrence that already sits under a mention span (that *is* a link). Journal day pages
 * (`journal/…`) are never a title worth finding. One row per block.
 */
fun unlinkedMentions(
    title: String,
    pageId: Long,
    blocks: List<Block>,
    pages: Map<Long, Page>,
    linkedPageIds: Set<Long>,
): List<UnlinkedMention> {
    val needle = title.trim()
    if (needle.length < MIN_UNLINKED_TITLE_LENGTH || needle.startsWith("journal/")) return emptyList()
    return blocks.mapNotNull { block ->
        if (block.pageId == pageId || block.pageId in linkedPageIds) return@mapNotNull null
        if (block.type == BlockType.BLOCK_REFERENCE || block.type == BlockType.PAGE_MENTION) return@mapNotNull null
        val page = pages[block.pageId] ?: return@mapNotNull null
        if (page.deletedAt != null || page.isTemplate) return@mapNotNull null
        val range = firstUnlinkedOccurrence(block, needle) ?: return@mapNotNull null
        UnlinkedMention(page, block, range)
    }
}

private fun firstUnlinkedOccurrence(block: Block, needle: String): IntRange? {
    var from = 0
    while (true) {
        val at = block.content.indexOf(needle, startIndex = from, ignoreCase = true)
        if (at < 0) return null
        val range = at until at + needle.length
        val alreadyLinked = block.formattingSpans.any { it.style is SpanStyle.PageMention && it.start <= range.first && it.end >= range.last + 1 }
        if (!alreadyLinked) return range
        from = at + 1
    }
}

/** The block with a mention of [pageId] over [range]; the text is left exactly as typed. */
fun linkMention(block: Block, range: IntRange, pageId: Long): Block =
    block.copy(formattingSpans = block.formattingSpans + FormattingSpan(range.first, range.last + 1, SpanStyle.PageMention(pageId)))
