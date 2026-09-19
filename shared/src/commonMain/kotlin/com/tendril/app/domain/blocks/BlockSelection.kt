package com.tendril.app.domain.blocks

import com.tendril.app.domain.OutlineBlock

/**
 * §0.10 item 19 — the block selection's arithmetic. A selection is a set of block ids over the
 * outline as it is drawn (`visibleIds`, reading order); every rule here is over that list, so a
 * mapped-away subtree or a collapsed toggle is exactly as selectable as it is visible.
 */

/** The contiguous run from [anchor] to [focus] in reading order, inclusive, whichever way round
 * they are. An id not in the list yields the other one alone; neither → empty. */
fun runBetween(visibleIds: List<Long>, anchor: Long, focus: Long): List<Long> {
    val a = visibleIds.indexOf(anchor)
    val f = visibleIds.indexOf(focus)
    if (a < 0 && f < 0) return emptyList()
    if (a < 0) return listOf(focus)
    if (f < 0) return listOf(anchor)
    return visibleIds.subList(minOf(a, f), maxOf(a, f) + 1)
}

/** A selected parent takes the descendants the outline draws under it (frame b of the mock):
 * moving or deleting a heading moves or deletes its branch, as the outline's own verbs do. */
fun withSubtrees(ids: Set<Long>, outline: List<OutlineBlock>): Set<Long> {
    val out = ids.toMutableSet()
    var i = 0
    while (i < outline.size) {
        val entry = outline[i]
        if (entry.block.id in ids) {
            var j = i + 1
            while (j < outline.size && outline[j].depth > entry.depth) { out += outline[j].block.id; j++ }
        }
        i++
    }
    return out
}

/** Every block whose row intersects the marquee. Selects what is laid out — a row that is not
 * composed has no bounds and is not hit (`block-selection-mock.md` #3). */
fun marqueeHits(bounds: Map<Long, Box>, marquee: Box): Set<Long> =
    bounds.filterValues { it.overlaps(marquee) }.keys

/** A rectangle in the page's own pixels — the domain's, so the tests need no Compose. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    companion object {
        /** A box from two corners in any order — a marquee dragged up-left is still a box. */
        fun of(x1: Float, y1: Float, x2: Float, y2: Float) = Box(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
    }
}

/** The selection after ↑/↓ (a single block, the neighbour of the focus) or Shift+↑/↓ (the run
 * from the anchor extended one row). Returns the new selection and the new focus; at either
 * end nothing moves. */
fun stepSelection(visibleIds: List<Long>, anchor: Long, focus: Long, direction: Int, extend: Boolean): Pair<List<Long>, Long> {
    val at = visibleIds.indexOf(focus)
    if (at < 0) return listOf(focus) to focus
    val next = (at + direction).coerceIn(0, visibleIds.size - 1)
    val newFocus = visibleIds[next]
    return if (extend) runBetween(visibleIds, anchor, newFocus) to newFocus else listOf(newFocus) to newFocus
}

/** The id the run should land after when moved: the block before the run's first in reading
 * order that is not itself in the run, or null for the top. */
fun blockBefore(visibleIds: List<Long>, run: Set<Long>): Long? {
    val first = visibleIds.indexOfFirst { it in run }
    if (first <= 0) return null
    return visibleIds.subList(0, first).lastOrNull { it !in run }
}
