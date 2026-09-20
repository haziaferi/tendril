package com.tendril.app.domain.canvas

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType

/**
 * §0.10 item 15 (2026-09-18) — **frames** on a canvas, Obsidian's *group* measured on both
 * platforms (`docs/critiques/canvas-frames-mock.md`): a node of its own with a label and a box,
 * drawn under the cards, and **no parent field** — a node belongs to a frame while its whole
 * box lies inside the frame's, so dragging the frame's label moves whatever is inside it and a
 * card dragged out simply leaves. JSON Canvas (item 6) writes the same node. A card is as wide as
 * its text asks between [CANVAS_CARD_MIN_W] and [CANVAS_CARD_WRAP_W] (`cardWidth`) and [CANVAS_NODE_H]
 * tall per line (`cardHeight`); a frame has its own `width` / `height`; a card's `width` column is
 * read only when a hand set it (`handWidth`). Content in dp.
 */
/** A page card's width when its title is not known to the caller (the export without the page). */
const val CANVAS_NODE_W = 200f
/** A card's height with one line; two and three lines add [CANVAS_CARD_LINE] each (48 · 68 · 88). */
const val CANVAS_NODE_H = 48f
const val CANVAS_CARD_LINE = 20f
const val CANVAS_CARD_MAX_LINES = 3

/**
 * The card's width (2026-09-20, the user: shorter by default, growing with the text, wrapping past
 * a length — "many short text boxes" overlapped at a fixed 200). Chosen by `meta-optimizer` over the
 * user's own texts (`docs/critiques/card-width-optimizer.md`): **fit to the text between 60 and
 * 220 dp, 8 dp of padding a side** — 0.847 against 0.466 for the fixed strip and 0.761 for widths
 * snapped to steps; Xmind's rule measured live (a topic is its text plus padding, capped at a
 * "Lunghezza" then wrapped — 307 px at 18 px, 17 em; Tendril's 220 at 14 sp is 15.7 em). A
 * character is estimated at [CANVAS_CARD_CHAR] dp (the mind map's estimate), so the board's
 * hit-tests, the arrows' anchors, Tidy, the embed's fit and the JSON Canvas export all see one box.
 */
const val CANVAS_CARD_MIN_W = 60f
const val CANVAS_CARD_WRAP_W = 220f
const val CANVAS_CARD_PAD = 8f
const val CANVAS_CARD_CHAR = 8.5f   // 0.6 em of the 14 sp text: 7.5 and 8 left no slack, and a word a hair over the estimate wrapped and was cut (the walk's first two grabs)
/** What an empty card shows, and the width it takes (the caption's own); the desktop discards an empty card whose editor closes. */
const val CANVAS_EMPTY_CARD_TEXT = "Empty card"

/** The entity's default `width`, which no hand ever set on a card (cards had no handle before the fit) — read as *derived*. */
const val CANVAS_LEGACY_CARD_W = 180f

/** A card's `width` when a hand set it (the corner handle; Obsidian's rule), else null — the text decides. */
fun CanvasNode.handWidth(): Float? = width.takeIf { type != CanvasNodeType.FRAME && it > 0f && it != CANVAS_LEGACY_CARD_W }

/** The width a card takes for [text]: its longest line's estimate plus the padding, clamped; a [handWidth] wins, never under the minimum. */
fun cardWidth(text: String?, handWidth: Float? = null): Float {
    handWidth?.let { return it.coerceAtLeast(CANVAS_CARD_MIN_W) }
    val longest = text.orEmpty().ifBlank { CANVAS_EMPTY_CARD_TEXT }.split('\n').maxOf { it.length }
    return (longest * CANVAS_CARD_CHAR + 2 * CANVAS_CARD_PAD).coerceIn(CANVAS_CARD_MIN_W, CANVAS_CARD_WRAP_W)
}

/** The characters one line holds in a card [width] wide. */
fun cardChars(width: Float): Int = ((width - 2 * CANVAS_CARD_PAD) / CANVAS_CARD_CHAR).toInt().coerceAtLeast(1)

/**
 * The size count (2026-09-20, the user: the cards were "disproportionately big" — 180 × 90 dp
 * around one line): a card is **a strip that grows with its text**, Obsidian's proportion
 * (250 × 60 CSS px at rest, measured on L9; its cards grow when typed into). One line sits in
 * 48 dp, two in 68, three in 88 — the estimate the mind map uses (characters per line, hard
 * breaks counted), so the board's hit-tests, the arrows' anchors, the embed's fit and the JSON
 * Canvas export all see one box. A page card is one line (its title). Frames keep their own.
 */
fun cardLines(text: String?, width: Float = cardWidth(text)): Int {
    val shown = text.orEmpty().ifBlank { " " }
    val chars = cardChars(width)
    val lines = shown.split('\n').sumOf { line -> ((line.length + chars - 1) / chars).coerceAtLeast(1) }
    return lines.coerceIn(1, CANVAS_CARD_MAX_LINES)
}

fun cardHeight(text: String?, nodeH: Float = CANVAS_NODE_H, width: Float = cardWidth(text)): Float = nodeH + (cardLines(text, width) - 1) * CANVAS_CARD_LINE

/** A new frame holds two cards abreast with their margins; it cannot be shrunk under one. */
const val FRAME_DEFAULT_W = 388f
const val FRAME_DEFAULT_H = 212f
const val FRAME_MIN_W = 212f
const val FRAME_MIN_H = 122f
const val FRAME_DEFAULT_LABEL = "Frame"

/** The box a node occupies on the board — a text card the strip its lines ask for, a page card one line, a frame its own.
 * **A free node's box**: with a parent, the level's grammar decides (`CanvasTree.box`, the mind-map pass). */
data class NodeBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
}

fun nodeBox(node: CanvasNode, nodeH: Float = CANVAS_NODE_H, titleOf: (Long) -> String? = { null }): NodeBox =
    when (node.type) {
        CanvasNodeType.FRAME -> NodeBox(node.x, node.y, node.width, node.height)
        CanvasNodeType.TEXT -> cardWidth(node.text, node.handWidth()).let { w -> NodeBox(node.x, node.y, w, cardHeight(node.text, nodeH, w)) }
        // A page card is one line of its title; a caller without the title (the export missing the page) gets the old strip.
        CanvasNodeType.PAGE_EMBED -> {
            val title = node.text.orEmpty().ifBlank { node.embeddedPageId?.let(titleOf) }
            NodeBox(node.x, node.y, node.handWidth() ?: title?.let { cardWidth(it) } ?: CANVAS_NODE_W, nodeH)
        }
    }

/** Every other node whose whole box lies inside [frame]'s — the ones a drag of the frame carries. */
fun nodesInside(frame: CanvasNode, nodes: List<CanvasNode>, nodeH: Float = CANVAS_NODE_H): List<CanvasNode> {
    val f = nodeBox(frame, nodeH)
    return nodes.filter { other ->
        if (other.id == frame.id) return@filter false
        val b = nodeBox(other, nodeH)
        b.x >= f.x && b.y >= f.y && b.right <= f.right && b.bottom <= f.bottom
    }
}

/** A frame's size after a resize, never under the minimum. */
fun clampFrameSize(width: Float, height: Float): Pair<Float, Float> =
    width.coerceAtLeast(FRAME_MIN_W) to height.coerceAtLeast(FRAME_MIN_H)
