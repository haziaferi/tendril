package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Why a revision was taken — what the History list says about it. */
enum class RevisionReason {
    /** The body as it stood when an editing window opened (§0.6.13: at most one per ten minutes). */
    EDIT,
    /** The body a sync merge replaced — §9.4's LWW loser, kept instead of lost. */
    MERGE,
    /** The body a restore replaced, so a restore is itself undoable. */
    RESTORE,
}

/**
 * §0.6.13 — one kept version of a page's body: its title and its blocks as the sync's own
 * `BlockSnapshotRecord` JSON, so a restore rebuilds them with the merge's proven block rebuild.
 * **Stays home**: not in the sync folder and not in the `.tendril` archive — history is this
 * device's memory of this device's edits (and of what a sync overwrote here), and the page row
 * itself is what travels. Cascades with the page.
 */
@Entity(
    tableName = "page_revisions",
    foreignKeys = [ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("pageId")],
)
data class PageRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val takenAt: Instant,
    val reason: RevisionReason,
    val title: String,
    val blocksJson: String,
    /** How many blocks the body held — the list shows it without decoding the JSON. */
    val blockCount: Int,
)

@Dao
interface PageRevisionDao {
    @Insert
    suspend fun insert(revision: PageRevision): Long

    @Query("SELECT * FROM page_revisions WHERE pageId = :pageId ORDER BY takenAt DESC, id DESC")
    fun observeForPage(pageId: Long): Flow<List<PageRevision>>

    @Query("SELECT * FROM page_revisions WHERE id = :id")
    suspend fun getById(id: Long): PageRevision?

    /** The newest — what the edit throttle and the merge dedupe compare against. */
    @Query("SELECT * FROM page_revisions WHERE pageId = :pageId ORDER BY takenAt DESC, id DESC LIMIT 1")
    suspend fun latestForPage(pageId: Long): PageRevision?

    /** Keeps the newest [keep] rows of the page and drops the rest. */
    @Query(
        "DELETE FROM page_revisions WHERE pageId = :pageId AND id NOT IN " +
            "(SELECT id FROM page_revisions WHERE pageId = :pageId ORDER BY takenAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun pruneBeyond(pageId: Long, keep: Int)
}
