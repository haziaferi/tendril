package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.Query

/**
 * §3.1.1 — "a Room FTS4/5 virtual table indexing each page's concatenated block plain-text,
 * rebuilt on block write... Page-level granularity for v1, not per-block." One row per Page,
 * fully replaced (delete + insert) whenever that page's blocks change — see
 * [com.tendril.app.domain.PageContentRepository].
 */
@Fts4
@Entity(tableName = "page_fts")
data class PageFtsEntry(
    val pageId: Long,
    val plainText: String,
)

@Dao
interface PageFtsDao {
    @Insert
    suspend fun insert(entry: PageFtsEntry)

    @Query("DELETE FROM page_fts WHERE pageId = :pageId")
    suspend fun deleteForPage(pageId: Long)

    /** §3.1.7 — the search overlay's query, live at ~300ms debounce. Returns pageId plus a
     * snippet of the matching text for the results list. */
    @Query(
        "SELECT pageId, snippet(page_fts, -1, '', '', '…', 12) AS snippet " +
            "FROM page_fts WHERE page_fts MATCH :query LIMIT 50"
    )
    suspend fun search(query: String): List<PageSearchHit>
}

data class PageSearchHit(val pageId: Long, val snippet: String)
