package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Insert
    suspend fun insert(block: Block): Long

    @Update
    suspend fun update(block: Block)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun delete(id: Long)

    /** §9.4 snapshot merge — a winning page record's blocks fully replace the local set
     * (whole-page LWW, §9.4's already-accepted "concurrent edits to the same page" limitation),
     * so the merge clears every existing block first rather than diffing. */
    @Query("DELETE FROM blocks WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: Long)

    @Query("SELECT * FROM blocks WHERE pageId = :pageId ORDER BY `order`")
    fun observeForPage(pageId: Long): Flow<List<Block>>

    @Query("SELECT * FROM blocks WHERE pageId = :pageId ORDER BY `order`")
    suspend fun getForPage(pageId: Long): List<Block>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun getById(id: Long): Block?

    @Query("SELECT MAX(`order`) FROM blocks WHERE pageId = :pageId")
    suspend fun maxOrder(pageId: Long): Int?

    /** Standalone PAGE_MENTION blocks pointing at [pageId] — the cheap half of the mention
     * edge set (SQL can filter this column directly). */
    @Query("SELECT * FROM blocks WHERE mentionedPageId = :pageId")
    suspend fun getStandaloneMentionsOf(pageId: Long): List<Block>

    /** Every standalone PAGE_MENTION block in the app, regardless of target — Road Map's
     * whole-graph edge scan (§3.4) needs every mention at once, not one target page's worth. */
    @Query("SELECT * FROM blocks WHERE mentionedPageId IS NOT NULL")
    suspend fun getAllStandaloneMentions(): List<Block>

    /** Every block carrying at least one inline `@Page` mention span — [Block.formattingSpans]
     * is opaque JSON to SQLite, so this fetches every block with any spans at all and the
     * caller (backlinks panel §3.1.5, Road Map §3.4) filters for [SpanStyle.PageMention] in
     * Kotlin. Personal-scale block counts make a full scan here cheap; a real per-mention
     * index isn't worth building for this app's scale. */
    @Query("SELECT * FROM blocks WHERE formattingSpans != '[]'")
    suspend fun getBlocksWithAnySpans(): List<Block>

    /** Gallery view cover (§5.6) — "the first Image block in the row's body". Ordered so the
     * caller (grouping by `pageId`, taking the first per group) gets the lowest-`order` image
     * per row without a second query per card. */
    @Query("SELECT * FROM blocks WHERE pageId IN (:pageIds) AND type = 'IMAGE' ORDER BY pageId, `order`")
    fun observeImagesForPages(pageIds: List<Long>): Flow<List<Block>>
}
