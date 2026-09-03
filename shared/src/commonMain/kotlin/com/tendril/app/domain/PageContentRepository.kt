package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.PageFtsDao
import com.tendril.app.data.page.PageFtsEntry
import com.tendril.app.data.page.SpanStyle

/**
 * Bridges block edits to the two things that read them without owning content themselves:
 * the FTS index (§3.1.1) and the mention edge set (§3.1.5 backlinks, §3.4 Road Map). Every
 * surface that mutates a page's blocks calls [rebuildFtsForPage] as part of that same write
 * — the block editor (§9.9 phase 5C) is the only caller today.
 */
class PageContentRepository(
    private val blockDao: BlockDao,
    private val pageFtsDao: PageFtsDao,
) {
    /** §3.1.1 — "a Room FTS4/5 virtual table indexing each page's concatenated block
     * plain-text, rebuilt on block write." Full delete+insert per page rather than an
     * incremental diff — page-level granularity (not per-block) makes this cheap enough
     * not to need anything cleverer. */
    suspend fun rebuildFtsForPage(pageId: Long) {
        val blocks = blockDao.getForPage(pageId)
        val plainText = blocks.joinToString(" ") { it.content }
        pageFtsDao.deleteForPage(pageId)
        if (plainText.isNotBlank()) {
            pageFtsDao.insert(PageFtsEntry(pageId = pageId, plainText = plainText))
        }
    }

    /** Every block anywhere that mentions [pageId] — standalone PAGE_MENTION blocks plus
     * inline `@mention` spans — the shared edge source for both the backlinks panel and
     * Road Map (§3.1.5: "the same edge data Road Map already computes, consumed here as a
     * second, lighter-weight view rather than a second query or a new data source"). */
    suspend fun mentionsOf(pageId: Long): List<Block> {
        val standalone = blockDao.getStandaloneMentionsOf(pageId)
        val inline = blockDao.getBlocksWithAnySpans().filter { block ->
            block.formattingSpans.any { it.style.let { style -> style is SpanStyle.PageMention && style.pageId == pageId } }
        }
        return (standalone + inline).distinctBy { it.id }
    }

    /** §3.4 Road Map's primary edge source — "no extra UI needed beyond typing @" — every
     * (mentioning page → mentioned page) pair in the app, computed live off the same block
     * data [mentionsOf] reads for one page at a time. No cached/derived table: a full scan
     * over blocks-with-mentions is cheap at personal scale and avoids the two-tables-drift
     * risk this codebase already ruled out for Sync-to-Tasks (§5.2). */
    suspend fun allMentionEdges(): List<Pair<Long, Long>> {
        val standalone = blockDao.getAllStandaloneMentions().map { it.pageId to it.mentionedPageId!! }
        val inline = blockDao.getBlocksWithAnySpans().flatMap { block ->
            block.formattingSpans.mapNotNull { span -> (span.style as? SpanStyle.PageMention)?.let { block.pageId to it.pageId } }
        }
        return (standalone + inline).distinct()
    }
}
