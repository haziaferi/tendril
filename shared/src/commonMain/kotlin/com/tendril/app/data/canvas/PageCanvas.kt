package com.tendril.app.data.canvas

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.tendril.app.data.page.Page
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The in-page Canvas feature (§10's deferred "in-page mind-map block," brought into scope) —
 * modeled on Obsidian Canvas: freely-positioned text/page-embed cards connected by user-drawn,
 * optionally-labeled, optionally-directional arrows. A Canvas is its own [PageKind.CANVAS]
 * page (a sibling of [com.tendril.app.data.pagedatabase.PageDatabase], not a block type
 * nested inside a page's body) — matching both Obsidian's own model (a .canvas file is a file,
 * never embedded content in a note) and this app's own precedent for "a Page with extra
 * structure" (§5.2's Database is the same shape: one 1:1 companion table plus child rows).
 */
@Entity(
    tableName = "page_canvases",
    foreignKeys = [ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("uid", unique = true), Index("pageId", unique = true)],
)
data class PageCanvas(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val pageId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class CanvasNodeType { TEXT, PAGE_EMBED }

@Entity(
    tableName = "canvas_nodes",
    foreignKeys = [
        ForeignKey(entity = PageCanvas::class, parentColumns = ["id"], childColumns = ["canvasId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Page::class, parentColumns = ["id"], childColumns = ["embeddedPageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("canvasId"), Index("embeddedPageId")],
)
data class CanvasNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val canvasId: Long,
    val type: CanvasNodeType,
    val x: Float,
    val y: Float,
    val width: Float = 180f,
    val height: Float = 90f,
    /** TEXT nodes only — plain text, no block/formatting model of its own (Notion-lite's own
     * "obvious 80% subset" bias, §3.1.1 — a canvas card is a sticky note, not a second page
     * editor nested inside this one). */
    val text: String? = null,
    /** PAGE_EMBED nodes only — shows the target page's title/icon; tapping opens it. */
    val embeddedPageId: Long? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** NONE renders a plain line — used for the rare case someone wants to connect two cards
 * without implying a relationship direction, matching Canvas's own "no arrowhead" option. */
enum class CanvasArrowDirection { NONE, ONE_WAY, TWO_WAY }

@Entity(
    tableName = "canvas_edges",
    foreignKeys = [
        ForeignKey(entity = PageCanvas::class, parentColumns = ["id"], childColumns = ["canvasId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CanvasNode::class, parentColumns = ["id"], childColumns = ["fromNodeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CanvasNode::class, parentColumns = ["id"], childColumns = ["toNodeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("canvasId"), Index("fromNodeId"), Index("toNodeId")],
)
data class CanvasEdge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val canvasId: Long,
    val fromNodeId: Long,
    val toNodeId: Long,
    val direction: CanvasArrowDirection = CanvasArrowDirection.ONE_WAY,
    val label: String? = null,
)

@Dao
interface PageCanvasDao {
    @Insert
    suspend fun insert(canvas: PageCanvas): Long

    @Update
    suspend fun update(canvas: PageCanvas)

    @Query("SELECT * FROM page_canvases WHERE pageId = :pageId")
    suspend fun getByPageId(pageId: Long): PageCanvas?

    @Query("SELECT * FROM page_canvases WHERE pageId = :pageId")
    fun observeByPageId(pageId: Long): Flow<PageCanvas?>
}

@Dao
interface CanvasNodeDao {
    @Insert
    suspend fun insert(node: CanvasNode): Long

    @Update
    suspend fun update(node: CanvasNode)

    @Query("DELETE FROM canvas_nodes WHERE id = :id")
    suspend fun delete(id: Long)

    /** §9.4 snapshot merge — a winning Canvas record's node set fully replaces the local one. */
    @Query("DELETE FROM canvas_nodes WHERE canvasId = :canvasId")
    suspend fun deleteForCanvas(canvasId: Long)

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId")
    fun observeForCanvas(canvasId: Long): Flow<List<CanvasNode>>

    @Query("SELECT * FROM canvas_nodes WHERE canvasId = :canvasId")
    suspend fun getForCanvas(canvasId: Long): List<CanvasNode>
}

@Dao
interface CanvasEdgeDao {
    @Insert
    suspend fun insert(edge: CanvasEdge): Long

    @Update
    suspend fun update(edge: CanvasEdge)

    @Query("DELETE FROM canvas_edges WHERE id = :id")
    suspend fun delete(id: Long)

    /** §9.4 snapshot merge — a winning Canvas record's edge set fully replaces the local one. */
    @Query("DELETE FROM canvas_edges WHERE canvasId = :canvasId")
    suspend fun deleteForCanvas(canvasId: Long)

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId")
    fun observeForCanvas(canvasId: Long): Flow<List<CanvasEdge>>

    @Query("SELECT * FROM canvas_edges WHERE canvasId = :canvasId")
    suspend fun getForCanvas(canvasId: Long): List<CanvasEdge>
}
