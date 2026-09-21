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
    /** S13 (v24, B§13.8.2) — the database's hue on the wheel, 0–359, null for the default hashed from its
     * title (`defaultDatabaseHue`). A hue, never a colour: the register solves it on each ground. */
    val hue: Int? = null,
    /** §0.6.14 (v19) — which RELATION property (pointing at this database itself) means
     * "blocked by". A pointer only: unlike the bindings above nothing is proxied through an
     * Entry and the relation values stay stored, so it is not a [com.tendril.app.domain.BindingRole]. */
    val blockedByPropertyId: Long? = null,
    /**
     * §0.6.8 (v13) — the *doorway*: a `Label` this database has bound. A page carrying it is a
     * member — a full row in every view, this database's fields in its header — while keeping
     * its own place in the tree; membership is the union `Page.databaseId == id` ∪ carries the
     * label ([com.tendril.app.data.page.PageDao.getMembersOf]). Null until someone binds one,
     * which is what keeps this the same app for anyone who never does (E12). No foreign key: a
     * label is never deleted today, and a dangling id would simply match nothing.
     */
    val labelId: Long? = null,
    /** §0.6.8 — set once the person has seen "pages carrying this label become tasks", which is
     * asked the first time the label is applied while [syncToTasks] is on. A column rather than
     * a device preference so the answer travels with the database. */
    val labelConfirmed: Boolean = false,
    /** §0.6.11 (v15) — when the person last walked this database in Review. Null means never,
     * which is "due now". A column rather than a device preference so a review done on one
     * device is done on both; the cadence it is measured against is global for now. */
    val lastReviewedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
