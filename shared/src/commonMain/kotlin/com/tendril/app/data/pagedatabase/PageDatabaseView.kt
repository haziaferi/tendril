package com.tendril.app.data.pagedatabase

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

enum class ViewType { TABLE, BOARD, GALLERY, CALENDAR }
enum class SortDirection { ASC, DESC }

/** Single-condition filter (§5.6) — "one property, one comparison, one value... no AND/OR-group
 * logic for v1," matching the spec's consistent 80%-useful-subset bias. */
@Serializable
data class ViewFilter(val propertyId: Long, val comparator: Comparator, val value: String) {
    enum class Comparator { EQUALS, NOT_EQUALS, CONTAINS, IS_EMPTY, IS_NOT_EMPTY }
}

/**
 * §5.6 — a saved display configuration over a [PageDatabase]'s rows; never alters stored
 * row/property data. A database always has at least one Table view (default).
 */
@Entity(
    tableName = "page_database_views",
    foreignKeys = [ForeignKey(entity = PageDatabase::class, parentColumns = ["id"], childColumns = ["databaseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("uid", unique = true), Index("databaseId")],
)
data class PageDatabaseView(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val databaseId: Long,
    val name: String,
    val viewType: ViewType,
    /** BOARD only — must be a SELECT-type property. */
    val groupByPropertyId: Long? = null,
    /** CALENDAR only — must be a DATE-type property. */
    val datePropertyId: Long? = null,
    /** Empty means "all properties" — TABLE/GALLERY's column/field chooser. */
    val visiblePropertyIds: List<Long> = emptyList(),
    val filter: ViewFilter? = null,
    val sortPropertyId: Long? = null,
    val sortDirection: SortDirection? = null,
    val order: Int = 0,
)
