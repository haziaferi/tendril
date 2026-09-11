package com.tendril.app.domain

import com.tendril.app.data.page.Block

/** One node of a laid-out map: the block, its box in content units, and its parent for the edge. */
data class MapNode(
    val block: Block,
    val depth: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val parentId: Long?,
) {
    val centerY: Float get() = y + height / 2f
    val right: Float get() = x + width
}

data class MindMapLayout(val nodes: List<MapNode>, val width: Float, val height: Float) {
    fun nodeFor(id: Long): MapNode? = nodes.firstOrNull { it.block.id == id }
}

/**
 * §0.6.2 — the mind map is a *drawing* of a block subtree, so this takes the outline the editor
 * already computes and returns positions; it stores nothing and knows nothing about pixels.
 *
 * Left-to-right tidy tree, the markmap shape: the root at the left, each depth in its own
 * column, a node vertically centred on the band its subtree occupies. A leaf's band is its own
 * height; a parent's is the sum of its children's bands plus [vGap] between them, or its own
 * height if that is taller. Columns are as wide as the widest node in them, plus [hGap]. That
 * is the whole algorithm — Reingold–Tilford's contour-merging exists to pack *siblings' subtrees*
 * into each other's gaps, which a personal outline never needs and a reader finds harder to
 * follow than plain bands.
 *
 * [subtree] is the root followed by its descendants in outline order, which is what fixes the
 * children's top-to-bottom order; [measure] returns a node's (width, height) for its text, so a
 * screen can measure with real fonts and a test with arithmetic.
 */
fun layoutMindMap(
    subtree: List<OutlineBlock>,
    measure: (Block) -> Pair<Float, Float>,
    hGap: Float = 48f,
    vGap: Float = 12f,
): MindMapLayout {
    if (subtree.isEmpty()) return MindMapLayout(emptyList(), 0f, 0f)
    val rootDepth = subtree.first().depth
    val ids = subtree.map { it.block.id }.toSet()

    // Children by parent, in outline order. A descendant whose parent is not in the subtree
    // (a cycle the outline broke, say) hangs off the root so that it is still drawn.
    val rootId = subtree.first().block.id
    val children = LinkedHashMap<Long, MutableList<OutlineBlock>>()
    for (entry in subtree.drop(1)) {
        val parent = entry.block.parentBlockId?.takeIf { it in ids } ?: rootId
        children.getOrPut(parent) { mutableListOf() } += entry
    }

    val size = subtree.associate { it.block.id to measure(it.block) }
    val band = HashMap<Long, Float>()
    fun bandOf(id: Long): Float = band.getOrPut(id) {
        val own = size.getValue(id).second
        val kids = children[id].orEmpty()
        if (kids.isEmpty()) own
        else maxOf(own, kids.sumOf { bandOf(it.block.id).toDouble() }.toFloat() + vGap * (kids.size - 1))
    }

    val columnWidth = HashMap<Int, Float>()
    for (entry in subtree) {
        val depth = entry.depth - rootDepth
        columnWidth[depth] = maxOf(columnWidth[depth] ?: 0f, size.getValue(entry.block.id).first)
    }
    val columnX = HashMap<Int, Float>()
    var x = 0f
    for (depth in 0..(columnWidth.keys.maxOrNull() ?: 0)) {
        columnX[depth] = x
        x += (columnWidth[depth] ?: 0f) + hGap
    }

    val nodes = mutableListOf<MapNode>()
    fun place(entry: OutlineBlock, top: Float, parentId: Long?) {
        val id = entry.block.id
        val (w, h) = size.getValue(id)
        val myBand = bandOf(id)
        val depth = entry.depth - rootDepth
        nodes += MapNode(entry.block, depth, columnX.getValue(depth), top + (myBand - h) / 2f, w, h, parentId)
        var childTop = top
        val kids = children[id].orEmpty()
        val kidsHeight = kids.sumOf { bandOf(it.block.id).toDouble() }.toFloat() + vGap * (kids.size - 1).coerceAtLeast(0)
        childTop += (myBand - kidsHeight) / 2f
        for (kid in kids) {
            place(kid, childTop, id)
            childTop += bandOf(kid.block.id) + vGap
        }
    }
    place(subtree.first(), 0f, null)

    val width = nodes.maxOf { it.right }
    val height = nodes.maxOf { it.y + it.height }
    return MindMapLayout(nodes, width, height)
}

/**
 * A node's box from its text alone, for the inert card and for tests: a fixed character width,
 * wrapped at [maxChars] per line, with padding. The armed map measures with real fonts; the
 * card only has to be proportionate, since it is scaled to fit anyway.
 */
fun estimateNodeSize(text: String, charWidth: Float = 7f, lineHeight: Float = 18f, maxChars: Int = 24, padding: Float = 16f): Pair<Float, Float> {
    val shown = text.ifBlank { "…" }
    val lines = (shown.length + maxChars - 1) / maxChars
    val widest = if (lines == 1) shown.length else maxChars
    return (widest * charWidth + padding) to (lines.coerceAtLeast(1) * lineHeight + padding)
}
