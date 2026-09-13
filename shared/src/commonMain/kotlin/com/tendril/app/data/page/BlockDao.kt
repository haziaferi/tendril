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

    /** §9.4 / S4 — the merge key, for attaching an image that arrived from another device to the
     * block it belongs to. Blocks are rebuilt wholesale by uid on merge, so `id` is not stable
     * across one and cannot be used here. */
    @Query("SELECT * FROM blocks WHERE uid = :uid")
    suspend fun getByUid(uid: String): Block?

    /** §0.6.12 — the live text behind a BLOCK_REFERENCE card; null while the source is not here. */
    @Query("SELECT * FROM blocks WHERE uid = :uid")
    fun observeByUid(uid: String): Flow<Block?>

    /** §0.6.12 — the block-reference picker's search: text blocks on live, non-template pages.
     * A substring scan rather than the page-level FTS, which cannot say *which* block matched. */
    @Query(
        "SELECT blocks.* FROM blocks JOIN pages ON pages.id = blocks.pageId " +
            "WHERE pages.deletedAt IS NULL AND pages.isTemplate = 0 " +
            "AND blocks.type NOT IN ('BLOCK_REFERENCE', 'DIVIDER', 'IMAGE', 'CANVAS', 'PAGE_MENTION') " +
            "AND blocks.content LIKE '%' || :query || '%' ORDER BY blocks.updatedAt DESC LIMIT 30"
    )
    suspend fun searchContent(query: String): List<Block>

    /** §9.4 / S4 — every block this device holds an image file for, so the write pass can put
     * those files in the folder. Not filtered on `type = 'IMAGE'`: the path is what says a file
     * exists, and a block whose type was changed out from under an attached image should still
     * have that image published rather than silently orphaned. */
    @Query("SELECT * FROM blocks WHERE imagePath IS NOT NULL")
    suspend fun getWithLocalImage(): List<Block>

    /** §9.4 / S4 — column-scoped, like `PageDao.touch`: an image arriving from a peer must not
     * carry a whole stale copy of the block back into the row with it. */
    @Query("UPDATE blocks SET imagePath = :path WHERE uid = :uid")
    suspend fun attachImagePath(uid: String, path: String)

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
