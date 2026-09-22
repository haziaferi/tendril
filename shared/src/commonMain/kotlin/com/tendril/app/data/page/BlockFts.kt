package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.Query

/**
 * §3.1.1 / §10's "block-level FTS granularity" (v25, 2026-09-22) — one FTS4 row per block with
 * text, beside `page_fts` (which keeps the title + body row the unlinked-mentions search reads).
 * Rebuilt with the page's row on every block write ([com.tendril.app.domain.PageContentRepository]),
 * so a deleted block's row goes with the page's delete-and-reinsert. What it buys: a search hit
 * names *the block*, and the switcher opens the page scrolled to it with the find marks on.
 */
@Fts4
@Entity(tableName = "block_fts")
data class BlockFtsEntry(
    val pageId: Long,
    val blockId: Long,
    val plainText: String,
)

@Dao
interface BlockFtsDao {
    @Insert
    suspend fun insert(entry: BlockFtsEntry)

    @Query("DELETE FROM block_fts WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: Long)

    /** Which pages have any row — what the heal diffs against (a page with no text has none). */
    @Query("SELECT DISTINCT pageId FROM block_fts")
    suspend fun indexedPageIds(): List<Long>

    /**
     * The switcher's body search. The join keeps a trashed page out, as `page_fts`'s does; the
     * snippet is the block's own (column 2 is `plainText` — `pageId` 0, `blockId` 1); the
     * delimiters are [SEARCH_HL_OPEN] / [SEARCH_HL_CLOSE]. Ranking happens in the caller.
     */
    @Query(
        "SELECT block_fts.pageId AS pageId, block_fts.blockId AS blockId, pages.title AS title, pages.icon AS icon, " +
            "snippet(block_fts, '\u0002', '\u0003', '…', 2, 12) AS snippet " +
            "FROM block_fts JOIN pages ON pages.id = block_fts.pageId " +
            "WHERE block_fts MATCH :query AND pages.deletedAt IS NULL LIMIT 60"
    )
    suspend fun search(query: String): List<BlockSearchHit>
}

data class BlockSearchHit(
    val pageId: Long,
    val blockId: Long,
    val title: String,
    val icon: String?,
    val snippet: String,
)

/** The block search with the same safe MATCH expression as [PageFtsDao.searchPrefix]. */
suspend fun BlockFtsDao.searchPrefix(raw: String): List<BlockSearchHit> {
    val match = ftsPrefixExpression(raw)
    if (match.isEmpty()) return emptyList()
    return runCatching { search(match) }.getOrDefault(emptyList())
}
