package com.tendril.app.data.page

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * §3.1.6 — global, freeform, reusable, flat (no nesting). Resolves the removed `category` field.
 *
 * Called *Label* since §0.6.9 (2026-09-12), so that *tag* keeps its Notion meaning — a Select
 * property inside one database, which this app also has. The rename is in name only: the table
 * stays `tags`, the join stays `page_tags` with its `tagId` column, and the snapshot key stays
 * `tags`. The key has to, for every peer already on the folder; the tables could move but only
 * through a migration whose one effect would be a nicer word in a place no one reads.
 */
@Entity(tableName = "tags", indices = [Index("uid", unique = true)])
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = LabelColors.forName(name),
)

/** The spec (§3.1.6, §4) leaves per-label color assignment unspecified beyond the `color`
 * field's existence — create-on-type labels need one immediately, with no picker step called
 * for anywhere. Assigned deterministically from a small fixed palette so re-creating a label
 * with the same name is stable across devices without needing to sync a color choice. */
object LabelColors {
    val palette = listOf(
        // Eight is load-bearing: forName indexes by hash modulo size, so the count
        // has to stay put or every existing label re-colours. Within that, these are
        // the smallest moves off the original set that keep every pair at least 12
        // CIEDE2000 apart under normal vision, deuteranopia and protanopia -- the
        // previous set had two pairs at 1.6 and 3.6 under dichromacy, and one pair
        // at 6.7 that was near-identical for everyone. Okabe-Ito, the reference
        // colour-vision-safe set, manages 11.1 with seven colours.
        "#A9708D", "#98693F", "#6E8C70", "#4A5568", "#B68E51", "#879FBA", "#9669BC", "#C6A790",
    )

    fun forName(name: String): String = palette[Math.floorMod(name.hashCode(), palette.size)]
}

/** Many-to-many join between [Page] and [Label] (§3.1.6, §4). */
@Entity(
    tableName = "page_tags",
    primaryKeys = ["pageId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("pageId"), Index("tagId")],
)
data class PageLabel(val pageId: Long, val tagId: Long)
