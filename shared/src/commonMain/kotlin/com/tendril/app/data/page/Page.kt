package com.tendril.app.data.page

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

enum class PageKind { PAGE, DATABASE, CANVAS }

/**
 * §4 (Page row) / §5.1 — a plain page, a database, or a database *row* (a row is
 * structurally an ordinary page — "matching Notion's actual row=page model" — tagged with
 * which database it belongs to via [databaseId], not a separate entity). `kind` only
 * distinguishes a plain page from a database *definition* page; a row is `kind = PAGE`
 * with [databaseId] set. The database's own schema/binding metadata lives in the separate
 * [com.tendril.app.data.pagedatabase.PageDatabase] table (§5.2), one-to-one with a
 * `kind = DATABASE` Page — kept separate rather than a wide table of nullable
 * database-only columns on every plain page.
 */
@Entity(tableName = "pages", indices = [Index("uid", unique = true), Index("parentId"), Index("databaseId")])
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    /** Emoji or short icon identifier — free-form, matching the page-card icon (§2.2). */
    val icon: String? = null,
    val kind: PageKind = PageKind.PAGE,
    /** Builds the page tree (§4) — null for a root-level page. */
    val parentId: Long? = null,
    /** Set when this Page is a Database row (§5.1) — which [com.tendril.app.data.pagedatabase.PageDatabase] it belongs to. */
    val databaseId: Long? = null,
    /** §3.1.3 — a saved template's block structure/schema, excluded from Road Map edges and the default Pages list. */
    val isTemplate: Boolean = false,
    /** Soft-delete (§5.5.1). */
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
