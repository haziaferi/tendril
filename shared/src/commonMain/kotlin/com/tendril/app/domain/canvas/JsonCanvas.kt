package com.tendril.app.domain.canvas

import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * §0.10 item 6 (2026-09-18) — a canvas as a **JSON Canvas** file (`obsidianmd/jsoncanvas`, spec
 * 1.0): the format Obsidian reads and writes as `.canvas`, so the Markdown export's zip opens
 * as a vault whose canvases work. The mapping is the spec's, nothing invented:
 *
 * | Tendril | JSON Canvas |
 * |---|---|
 * | a text card | a `text` node, its text as Markdown |
 * | a page card | a `file` node whose `file` is the page's path in the zip (vault-relative, the spec's rule); a page the export does not carry (trashed) becomes a `text` node with its title |
 * | a frame (item 15) | a `group` node with its `label` — the same node Obsidian's group is |
 * | an arrow | an edge; `ONE_WAY` is the spec's default (`toEnd: arrow`), `TWO_WAY` adds `fromEnd: arrow`, `NONE` sets `toEnd: none` |
 *
 * Coordinates are the board's dp rounded to the integers the spec asks for; a card takes the
 * fixed [CANVAS_NODE_W] × [CANVAS_NODE_H], a frame its own box. Ids are the rows' uids, so a
 * re-export of the same board writes the same ids. No colours: Tendril's cards have none.
 * Import is not here — the item asked where the files go.
 */
@Serializable
data class JsonCanvasNode(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String? = null,
    val file: String? = null,
    val label: String? = null,
)

@Serializable
data class JsonCanvasEdge(
    val id: String,
    val fromNode: String,
    val toNode: String,
    val fromEnd: String? = null,
    val toEnd: String? = null,
    val label: String? = null,
)

@Serializable
data class JsonCanvasDocument(val nodes: List<JsonCanvasNode>, val edges: List<JsonCanvasEdge>)

object JsonCanvas {
    private val json = Json { prettyPrint = true; explicitNulls = false }

    fun document(
        nodes: List<CanvasNode>,
        edges: List<CanvasEdge>,
        /** The zip path of an embedded page, or null when the export does not carry it. */
        filePathFor: (Long) -> String?,
        /** The title to fall back on for a page card whose page is not in the export. */
        titleFor: (Long) -> String?,
    ): JsonCanvasDocument {
        val ids = nodes.associate { it.id to it.uid }
        val outNodes = nodes.map { node ->
            val box = nodeBox(node)
            val base = JsonCanvasNode(id = node.uid, type = "text", x = box.x.roundToInt(), y = box.y.roundToInt(), width = box.w.roundToInt(), height = box.h.roundToInt())
            when (node.type) {
                CanvasNodeType.TEXT -> base.copy(text = node.text.orEmpty())
                CanvasNodeType.FRAME -> base.copy(type = "group", label = node.text?.takeIf { it.isNotBlank() })
                CanvasNodeType.PAGE_EMBED -> {
                    val pageId = node.embeddedPageId
                    val path = pageId?.let(filePathFor)
                    if (path != null) base.copy(type = "file", file = path)
                    else base.copy(text = pageId?.let(titleFor).orEmpty().ifBlank { "Untitled" })
                }
            }
        }
        val outEdges = edges.mapNotNull { edge ->
            val from = ids[edge.fromNodeId] ?: return@mapNotNull null
            val to = ids[edge.toNodeId] ?: return@mapNotNull null
            JsonCanvasEdge(
                id = edge.uid, fromNode = from, toNode = to,
                fromEnd = if (edge.direction == CanvasArrowDirection.TWO_WAY) "arrow" else null,
                toEnd = if (edge.direction == CanvasArrowDirection.NONE) "none" else null,
                label = edge.label?.takeIf { it.isNotBlank() },
            )
        }
        return JsonCanvasDocument(outNodes, outEdges)
    }

    fun write(document: JsonCanvasDocument): String = json.encodeToString(document) + "\n"
}
