package com.tendril.app.domain.canvas

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType

/**
 * §0.10 item 15 (2026-09-18) — **frames** on a canvas, Obsidian's *group* measured on both
 * platforms (`docs/critiques/canvas-frames-mock.md`): a node of its own with a label and a box,
 * drawn under the cards, and **no parent field** — a node belongs to a frame while its whole
 * box lies inside the frame's, so dragging the frame's label moves whatever is inside it and a
 * card dragged out simply leaves. JSON Canvas (item 6) writes the same node. A card is the fixed
 * [CANVAS_NODE_W] × [CANVAS_NODE_H]; a frame has its own `width` / `height` (the columns every
 * node carries and cards ignore). Content in dp.
 */
const val CANVAS_NODE_W = 180f
const val CANVAS_NODE_H = 90f

/** A new frame holds two cards abreast with their margins; it cannot be shrunk under one. */
const val FRAME_DEFAULT_W = 388f
const val FRAME_DEFAULT_H = 212f
const val FRAME_MIN_W = 212f
const val FRAME_MIN_H = 122f
const val FRAME_DEFAULT_LABEL = "Frame"

/** The box a node occupies on the board — a card the fixed card size, a frame its own. */
data class NodeBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
}

fun nodeBox(node: CanvasNode, nodeW: Float = CANVAS_NODE_W, nodeH: Float = CANVAS_NODE_H): NodeBox =
    if (node.type == CanvasNodeType.FRAME) NodeBox(node.x, node.y, node.width, node.height) else NodeBox(node.x, node.y, nodeW, nodeH)

/** Every other node whose whole box lies inside [frame]'s — the ones a drag of the frame carries. */
fun nodesInside(frame: CanvasNode, nodes: List<CanvasNode>, nodeW: Float = CANVAS_NODE_W, nodeH: Float = CANVAS_NODE_H): List<CanvasNode> {
    val f = nodeBox(frame, nodeW, nodeH)
    return nodes.filter { other ->
        if (other.id == frame.id) return@filter false
        val b = nodeBox(other, nodeW, nodeH)
        b.x >= f.x && b.y >= f.y && b.right <= f.right && b.bottom <= f.bottom
    }
}

/** A frame's size after a resize, never under the minimum. */
fun clampFrameSize(width: Float, height: Float): Pair<Float, Float> =
    width.coerceAtLeast(FRAME_MIN_W) to height.coerceAtLeast(FRAME_MIN_H)
