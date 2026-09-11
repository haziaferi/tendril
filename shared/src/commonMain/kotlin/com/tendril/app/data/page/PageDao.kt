package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface PageDao {
    @Insert
    suspend fun insert(page: Page): Long

    @Update
    suspend fun update(page: Page)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getById(id: Long): Page?

    /** Canvas's page-embed nodes (§10's deferred in-page mind-map, brought into scope) need
     * every embedded target's title/kind at once to render, not one query per node. */
    @Query("SELECT * FROM pages WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observeById(id: Long): Flow<Page?>

    @Query("SELECT * FROM pages WHERE uid = :uid")
    suspend fun getByUid(uid: String): Page?

    @Query("SELECT * FROM pages")
    suspend fun getAll(): List<Page>

    /** The "relate to" database picker (§5.4/DB1) — every database in the app, by its own
     * defining page. `:kind` binds through the existing [PageKind] converter, so this stays a
     * parameterized query rather than a raw enum-name literal that would silently drift if the
     * converter's format ever changed. */
    @Query("SELECT * FROM pages WHERE kind = :kind AND deletedAt IS NULL ORDER BY title")
    suspend fun getByKind(kind: PageKind): List<Page>

    /** Pages hub default list (§3.1) — root-level, non-template, undeleted pages. */
    @Query("SELECT * FROM pages WHERE parentId IS NULL AND isTemplate = 0 AND databaseId IS NULL AND deletedAt IS NULL ORDER BY title")
    fun observeRootPages(): Flow<List<Page>>

    /** Rows of a Database (§5.1) — a Row is a Page with `databaseId` set. */
    @Query("SELECT * FROM pages WHERE databaseId = :databaseId AND deletedAt IS NULL ORDER BY id")
    fun observeRowsOf(databaseId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE databaseId = :databaseId AND deletedAt IS NULL ORDER BY id")
    suspend fun getRowsOf(databaseId: Long): List<Page>

    @Query("SELECT * FROM pages WHERE isTemplate = 1 AND deletedAt IS NULL ORDER BY title")
    fun observeTemplates(): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Page>>

    /** §9.4 snapshot merge (see [com.tendril.app.sync.PagesSyncEngine]) — a page's tree
     * position is resolved in a second pass once every page in the same sync batch has a
     * local id, so this updates just the two FK columns rather than the whole row. */
    @Query("UPDATE pages SET parentId = :parentId, databaseId = :databaseId WHERE id = :id")
    suspend fun updateParentAndDatabase(id: Long, parentId: Long?, databaseId: Long?)

    /** §9.4 snapshot merge — a page's synced payload is far larger than its own row (its
     * blocks, labels, cell values, database schema, canvas content), but the merge's
     * last-write-wins gate reads `pages.updatedAt` and nothing else. So any write that changes
     * that payload without rewriting the row has to move this column as well, or the record
     * arrives at a peer no newer than the copy already there, loses, and every pass that would
     * have applied it is skipped. Silently: an *equal* timestamp is the same version by
     * definition, so it is not recorded as a conflict either and no `.tendril-lost` copy is
     * written. Column-scoped like [updateParentAndDatabase] rather than a whole-row `update`,
     * so bumping a timestamp can never carry a stale copy of some other field along with it. */
    @Query("UPDATE pages SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Instant)

    @Query("UPDATE pages SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant)

    @Query("UPDATE pages SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restore(id: Long, restoredAt: Instant)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deleteForever(id: Long)

    /** Journal root lookup (§3.1.4) — by title under the tree, cheap enough at personal scale
     * not to need a dedicated `isJournalRoot` flag. */
    @Query("SELECT * FROM pages WHERE title = :title AND parentId IS NULL AND deletedAt IS NULL LIMIT 1")
    suspend fun findRootByTitle(title: String): Page?

    @Query("SELECT * FROM pages WHERE parentId = :parentId AND title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun findChildByTitle(parentId: Long, title: String): Page?
}
