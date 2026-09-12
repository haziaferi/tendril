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

    /** Which pages have a row — what [com.tendril.app.domain.PageContentRepository.healIndex] diffs against. */
    @Query("SELECT pageId FROM page_fts")
    suspend fun indexedPageIds(): List<Long>

    /**
     * §3.1.7 — the search overlay's query. Returns everything a result row shows: "each
     * matching page's icon, title, and a highlighted snippet from the first matching block".
     *
     * The join is what keeps a trashed page out of the results. `page_fts` is a standalone
     * `@Fts4` table (no `contentEntity`), so it carries no `deletedAt` of its own and nothing
     * prunes it on delete — §5.5.1 requires a deleted Page to be "invisible everywhere except
     * Trash", FTS named explicitly. Filtering here covers both halves at once: a soft-deleted
     * page stops matching, and a **Delete forever**'d one can no longer return a `pageId` that
     * resolves to nothing.
     *
     * `snippet()`'s FTS4 signature is
     * `snippet(table, startMatch, endMatch, ellipses, columnNumber, tokens)`. The arguments
     * used to be `(-1, '', '', '…', 12)` — one position short, so `-1` landed in `startMatch`,
     * the match delimiters were empty strings, and `'…'` was read as the *column number*,
     * coercing to 0, which is `pageId` rather than `plainText`. Snippets were drawn from the
     * wrong column and nothing was ever highlighted. [SEARCH_HL_OPEN]/[SEARCH_HL_CLOSE] are
     * control characters so they cannot collide with a person's own note text; the overlay
     * splits on them to build the highlight.
     */
    @Query(
        "SELECT page_fts.pageId AS pageId, pages.title AS title, pages.icon AS icon, " +
            "snippet(page_fts, '\u0002', '\u0003', '…', 1, 12) AS snippet " +
            "FROM page_fts JOIN pages ON pages.id = page_fts.pageId " +
            "WHERE page_fts MATCH :query AND pages.deletedAt IS NULL LIMIT 50"
    )
    suspend fun search(query: String): List<PageSearchHit>
}

/** Delimiters SQLite wraps each match in — see [PageFtsDao.search]. */
const val SEARCH_HL_OPEN = '\u0002'
const val SEARCH_HL_CLOSE = '\u0003'

data class PageSearchHit(
    val pageId: Long,
    val title: String,
    val icon: String?,
    val snippet: String,
)

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
