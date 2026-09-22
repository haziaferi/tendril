package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.BlockFtsDao
import com.tendril.app.data.page.BlockFtsEntry
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
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val pageFtsDao: PageFtsDao,
    private val blockFtsDao: BlockFtsDao,
) {
    /** §3.1.1 — "a Room FTS4/5 virtual table indexing each page's concatenated block
     * plain-text, rebuilt on block write." Full delete+insert per page rather than an
     * incremental diff — page-level granularity (not per-block) makes this cheap enough
     * not to need anything cleverer.
     *
     * **The title is indexed too** (2026-09-12, §0.8 step 8a). It never was — the defect §3.1.1
     * and §0.10 item 5 recorded — so a page could not be found by its own name, only by its
     * body. It goes first in the text, so a snippet on a title match shows the title. Every
     * title change calls this as a block change does; [healIndex] covers the rows that predate
     * the fix. */
    suspend fun rebuildFtsForPage(pageId: Long) {
        val title = pageDao.getById(pageId)?.title.orEmpty()
        val blocks = blockDao.getForPage(pageId)
        val plainText = (listOf(title) + blocks.map { it.content }).filter { it.isNotBlank() }.joinToString(" ")
        pageFtsDao.deleteForPage(pageId)
        if (plainText.isNotBlank()) {
            pageFtsDao.insert(PageFtsEntry(pageId = pageId, plainText = plainText))
        }
        // v25 — one row per block with text, beside the page's; the same delete-and-reinsert.
        blockFtsDao.deleteForPage(pageId)
        blocks.filter { it.content.isNotBlank() }.forEach { b ->
            blockFtsDao.insert(BlockFtsEntry(pageId = pageId, blockId = b.id, plainText = b.content))
        }
    }

    /**
     * Indexes every live page that has no FTS row. Idempotent and cheap on an indexed database
     * (one query, nothing to do), so both apps run it at start. Its one real job is the launch
     * after `MIGRATION_15_16`, which empties `page_fts` so the titles get in: that first run
     * re-indexes everything and every later run finds nothing missing. Returns how many it did.
     */
    suspend fun healIndex(): Int {
        val inPages = pageFtsDao.indexedPageIds().toSet()
        val inBlocks = blockFtsDao.indexedPageIds().toSet()
        // v25 — `block_fts` starts empty on the first launch after MIGRATION_24_25: a live page
        // with a text block and no block row is re-indexed once; a page with no text has no
        // row to miss.
        val missing = pageDao.getAll().filter { page ->
            page.deletedAt == null && (page.id !in inPages || (page.id !in inBlocks && blockDao.getForPage(page.id).any { it.content.isNotBlank() }))
        }
        missing.forEach { rebuildFtsForPage(it.id) }
        return missing.size
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
