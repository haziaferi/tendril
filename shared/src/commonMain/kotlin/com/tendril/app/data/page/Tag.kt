package com.tendril.app.data.page

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** §3.1.6 — global, freeform, reusable, flat (no nesting). Resolves the removed `category` field. */
@Entity(tableName = "tags", indices = [Index("uid", unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = TagColors.forName(name),
)

/** The spec (§3.1.6, §4) leaves per-tag color assignment unspecified beyond the `color`
 * field's existence — create-on-type tags need one immediately, with no picker step called
 * for anywhere. Assigned deterministically from a small fixed palette so re-creating a tag
 * with the same name is stable across devices without needing to sync a color choice. */
object TagColors {
    private val palette = listOf(
        // Eight is load-bearing: forName indexes by hash modulo size, so the count
        // has to stay put or every existing tag re-colours. Within that, these are
        // the smallest moves off the original set that keep every pair at least 12
        // CIEDE2000 apart under normal vision, deuteranopia and protanopia -- the
        // previous set had two pairs at 1.6 and 3.6 under dichromacy, and one pair
        // at 6.7 that was near-identical for everyone. Okabe-Ito, the reference
        // colour-vision-safe set, manages 11.1 with seven colours.
        "#A9708D", "#98693F", "#6E8C70", "#4A5568", "#B68E51", "#879FBA", "#9669BC", "#C6A790",
    )

    fun forName(name: String): String = palette[Math.floorMod(name.hashCode(), palette.size)]
}

/** Many-to-many join between [Page] and [Tag] (§3.1.6, §4). */
@Entity(
    tableName = "page_tags",
    primaryKeys = ["pageId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("pageId"), Index("tagId")],
)
data class PageTag(val pageId: Long, val tagId: Long)
