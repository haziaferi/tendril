package com.tendril.app.domain.canvas

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType

/**
 * §0.10 item 15 (2026-09-18) — **frames** on a canvas, Obsidian's *group* measured on both
 * platforms (`docs/critiques/canvas-frames-mock.md`): a node of its own with a label and a box,
 * drawn under the cards, and **no parent field** — a node belongs to a frame while its whole
 * box lies inside the frame's, so dragging the frame's label moves whatever is inside it and a
 * card dragged out simply leaves. JSON Canvas (item 6) writes the same node. A card is the fixed
 * [CANVAS_NODE_W] wide and [CANVAS_NODE_H] tall per line of its text (`cardHeight`); a frame has its own `width` / `height` (the columns every
 * node carries and cards ignore). Content in dp.
 */
const val CANVAS_NODE_W = 200f
/** A card's height with one line; two and three lines add [CANVAS_CARD_LINE] each (48 · 68 · 88). */
const val CANVAS_NODE_H = 48f
const val CANVAS_CARD_LINE = 20f
const val CANVAS_CARD_MAX_LINES = 3
/** The characters a line of `description` holds across the card's text width (200 − 2 × 8 dp at ≈ 7.5 dp a character). */
const val CANVAS_CARD_CHARS = 24

/**
 * The size count (2026-09-20, the user: the cards were "disproportionately big" — 180 × 90 dp
 * around one line): a card is **a strip that grows with its text**, Obsidian's proportion
 * (250 × 60 CSS px at rest, measured on L9; its cards grow when typed into). One line sits in
 * 48 dp, two in 68, three in 88 — the estimate the mind map uses (characters per line, hard
 * breaks counted), so the board's hit-tests, the arrows' anchors, the embed's fit and the JSON
 * Canvas export all see one box. A page card is one line (its title). Frames keep their own.
 */
fun cardLines(text: String?): Int {
    val shown = text.orEmpty().ifBlank { " " }
    val lines = shown.split('\n').sumOf { line -> ((line.length + CANVAS_CARD_CHARS - 1) / CANVAS_CARD_CHARS).coerceAtLeast(1) }
    return lines.coerceIn(1, CANVAS_CARD_MAX_LINES)
}

fun cardHeight(text: String?, nodeH: Float = CANVAS_NODE_H): Float = nodeH + (cardLines(text) - 1) * CANVAS_CARD_LINE

/** A new frame holds two cards abreast with their margins; it cannot be shrunk under one. */
const val FRAME_DEFAULT_W = 388f
const val FRAME_DEFAULT_H = 212f
const val FRAME_MIN_W = 212f
const val FRAME_MIN_H = 122f
const val FRAME_DEFAULT_LABEL = "Frame"

/** The box a node occupies on the board — a text card the strip its lines ask for, a page card one line, a frame its own. */
data class NodeBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
}

fun nodeBox(node: CanvasNode, nodeW: Float = CANVAS_NODE_W, nodeH: Float = CANVAS_NODE_H): NodeBox =
    when (node.type) {
        CanvasNodeType.FRAME -> NodeBox(node.x, node.y, node.width, node.height)
        CanvasNodeType.TEXT -> NodeBox(node.x, node.y, nodeW, cardHeight(node.text, nodeH))
        CanvasNodeType.PAGE_EMBED -> NodeBox(node.x, node.y, nodeW, nodeH)
    }

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
