package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Tag?

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name")
    suspend fun search(query: String): List<Tag>

    @Query("SELECT t.* FROM tags t INNER JOIN page_tags pt ON pt.tagId = t.id WHERE pt.pageId = :pageId")
    fun observeForPage(pageId: Long): Flow<List<Tag>>

    /** §9.4 snapshot export — one-shot counterpart to [observeForPage]. */
    @Query("SELECT t.* FROM tags t INNER JOIN page_tags pt ON pt.tagId = t.id WHERE pt.pageId = :pageId")
    suspend fun getForPage(pageId: Long): List<Tag>

    @Insert
    suspend fun addToPage(pageTag: PageTag)

    @Query("DELETE FROM page_tags WHERE pageId = :pageId AND tagId = :tagId")
    suspend fun removeFromPage(pageId: Long, tagId: Long)

    /** §9.4 snapshot merge — a winning page record's tag set fully replaces the local one. */
    @Query("DELETE FROM page_tags WHERE pageId = :pageId")
    suspend fun clearForPage(pageId: Long)


    /** §3.1.6 filter-chip row — OR semantics (a page matching *any* selected tag shows),
     * matching the spec's consistent bias toward the simplest useful filter model rather
     * than AND/group logic (the same call already made for §5.6's single-condition view filter). */
    @Query("SELECT DISTINCT pageId FROM page_tags WHERE tagId IN (:tagIds)")
    fun observePageIdsForTags(tagIds: List<Long>): Flow<List<Long>>
}
