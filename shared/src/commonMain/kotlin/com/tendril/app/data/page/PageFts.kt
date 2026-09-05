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

/** Matches a run of letters or digits — everything else is punctuation as far as search is
 * concerned. */
private val SEARCH_TOKEN = Regex("[\\p{L}\\p{N}]+")

/**
 * The only safe way to call [PageFtsDao.search], and it lives here so no future caller has to
 * know that.
 *
 * `MATCH` takes a query *expression*, not a literal: a quote, parenthesis or bare `*` in what
 * someone typed is an FTS4 syntax error, and interpolating raw input straight into `"$query*"`
 * turned an ordinary keystroke into a crash. Taking only alphanumeric runs leaves bare terms,
 * which are always valid, and each gets FTS4's `*` prefix operator so results still narrow as
 * the person types. Multiple terms are ANDed, FTS4's default.
 *
 * Returns empty for input with nothing searchable in it ("", "  ", "???") rather than running a
 * query that would match everything. The `runCatching` is deliberate belt-and-braces on a
 * keystroke path: the tokens above are already valid, but nothing here should be able to take
 * the app down.
 */
suspend fun PageFtsDao.searchPrefix(raw: String): List<PageSearchHit> {
    val match = SEARCH_TOKEN.findAll(raw).joinToString(" ") { "${it.value}*" }
    if (match.isEmpty()) return emptyList()
    return runCatching { search(match) }.getOrDefault(emptyList())
}
