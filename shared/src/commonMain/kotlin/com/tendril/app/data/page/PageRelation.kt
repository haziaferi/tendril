package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant
import java.util.UUID

/**
 * §3.4 — the manual "Relate to…" edge source, for pages that are conceptually connected but
 * don't reference each other in text. A small dedicated join entity (matching [PageLabel]'s own
 * shape), not a reuse of Block/Label — a relation isn't page content (§3.1.1's block inventory
 * is fixed and deliberate) and isn't a label (§3.1.6's labels are freeform, not opaque markers).
 * Undirected in practice: [fromPageId]/[toPageId] only record *who created it*, every query
 * reads both directions.
 */
@Entity(
    tableName = "page_relations",
    foreignKeys = [
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["fromPageId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["toPageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("fromPageId"), Index("toPageId")],
)
data class PageRelation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val fromPageId: Long,
    val toPageId: Long,
    val createdAt: Instant,
)

@Dao
interface PageRelationDao {
    @Insert
    suspend fun insert(relation: PageRelation): Long

    @Query("SELECT * FROM page_relations")
    suspend fun getAll(): List<PageRelation>

    @Query(
        "SELECT COUNT(*) FROM page_relations WHERE (fromPageId = :pageIdA AND toPageId = :pageIdB) " +
            "OR (fromPageId = :pageIdB AND toPageId = :pageIdA)"
    )
    suspend fun countBetween(pageIdA: Long, pageIdB: Long): Int
}

/** Dedupes both directions before inserting — "Relate to" from either page should never
 * produce two redundant edges on the graph. */
suspend fun PageRelationDao.addRelation(fromPageId: Long, toPageId: Long, now: Instant = Instant.now()) {
    if (fromPageId == toPageId) return
    if (countBetween(fromPageId, toPageId) > 0) return
    insert(PageRelation(fromPageId = fromPageId, toPageId = toPageId, createdAt = now))
}
