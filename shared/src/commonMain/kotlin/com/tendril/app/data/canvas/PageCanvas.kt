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
    /** The mind-map pass (v23, 2026-09-20) — the board's structure, a [com.tendril.app.domain.canvas.CanvasStructure] key
     * (`free` · `map` · `right` · `down`); a node's own `structure` overrides it for its subtree (Xmind's per-topic structure). */
    val structure: String = "free",
)

/** §0.10 item 15 (2026-09-18) — [FRAME]: a labelled region under the cards, its `width` / `height`
 * its own and its `text` the label; nothing points at it — a node is inside a frame while its box
 * is (`domain/canvas/Frames.kt`). A peer from before quarantines the unknown name as it does every enum. */
enum class CanvasNodeType { TEXT, PAGE_EMBED, FRAME }

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
    /** The mind-map pass (v23, 2026-09-20) — **the tree**: a node with a parent is a branch of it; one
     * parent by construction (inoichi's, Ideascape's), so a hierarchy edge is drawn from this link and
     * never stored as an arrow — `canvas_edges` are *relationships*. A FRAME with a parent follows that
     * node's subtree (Xmind's boundary) and ignores its own `width` / `height`. No foreign key: deleting a
     * node lifts its children to its parent (`CanvasViewModel.deleteNode`), never cascades. */
    val parentId: Long? = null,
    /** The subtree under this node is hidden; the count shows at the branch's end. */
    val folded: Boolean = false,
    /** A [com.tendril.app.domain.canvas.CanvasStructure] key for this node's subtree, null to inherit. */
    val structure: String? = null,
    /** S13 (v24) — a card's or a frame's hue on the wheel, 0–359, null for none (Obsidian's canvas colour). */
    val hue: Int? = null,
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

    /** v23 — the merge sets the links after every node of the record has an id. */
    @Query("UPDATE canvas_nodes SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)
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

    /** §3.7 — the arrow editor's writes re-read through this rather than trusting the
     * [CanvasEdge] the sheet was opened with. An edge is a small row edited by two controls at
     * once (direction and label), and a whole-row `copy()` from a stale snapshot puts the other
     * control's value back; re-reading makes that impossible instead of asking each caller to
     * hold fresh state. */
    @Query("SELECT * FROM canvas_edges WHERE id = :id")
    suspend fun getById(id: Long): CanvasEdge?
}
