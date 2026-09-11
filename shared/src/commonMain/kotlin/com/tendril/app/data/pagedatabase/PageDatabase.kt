package com.tendril.app.data.pagedatabase

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.page.Page
import java.time.Instant
import java.util.UUID

/**
 * §4 (Database row) / §5.2 — "a Page with a schema." Named `PageDatabase`, not `Database`,
 * to avoid colliding with both `androidx.room.Database` (the annotation) and
 * [com.tendril.app.data.TendrilDatabase] (the Room database class) in the same codebase.
 * One-to-one with a `kind = DATABASE` [Page] — kept as its own table rather than nullable
 * columns bolted onto every Page, since only database pages ever populate them.
 */
@Entity(
    tableName = "page_databases",
    foreignKeys = [ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("uid", unique = true), Index("pageId", unique = true)],
)
data class PageDatabase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val pageId: Long,
    /** Off by default — deliberate opt-in (§5.2), never inferred from schema shape. */
    val syncToTasks: Boolean = false,
    val donePropertyId: Long? = null,
    /** Binds `Entry.startDate` — the *When*. Named for what §5.2 called it when built and kept
     * so a v9 peer's `deadlinePropertyUid` keeps its meaning (§0.6.4). */
    val deadlinePropertyId: Long? = null,
    /** §0.8 step 2b — binds `Entry.dueDate`, the deadline proper. Optional, `DATE`-typed. */
    val dueDatePropertyId: Long? = null,
    /** §5.2.2 — optional; only ever binds an `Interval`-type Property. */
    val recurrencePropertyId: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
