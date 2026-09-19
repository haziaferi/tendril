package com.tendril.app.ui.pages

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import com.tendril.app.domain.blocks.Box

/**
 * §0.10 item 19 — the two pointer gestures a page's ground carries: a **marquee** from a press
 * outside every block's text, and a **group drag** from a press on a selected block. Both run on
 * the Initial pass so the list under them never scrolls and a field under the pointer never
 * focuses once the press has moved past the slop; a press that never moves is left alone, so a
 * click on a selected block's text still starts editing and a click on the ground still clears
 * focus. The run is read at the press — the field under it takes focus on the down and the
 * screen clears the selection for that, so the drag carries its own ids and puts them back.
 * Touch never reaches this — the phone selects with a long press and moves from the sheet.
 * Every rectangle is in root coordinates ([origin] is the ground's own root position).
 */
internal data class GroupDrag(val ids: Set<Long>, val pointer: Offset, val afterId: Long?, val lineY: Float?)

@Composable
internal fun Modifier.blockGround(
    origin: () -> Offset,
    rows: () -> Map<Long, Pair<Rect, Rect>>,
    selected: () -> Set<Long>,
    canDrag: Boolean,
    onMarquee: (Rect?) -> Unit,
    onDragChange: (GroupDrag?) -> Unit,
    onDrop: (ids: Set<Long>, afterId: Long?) -> Unit,
): Modifier {
    // The gesture outlives the composition that made these lambdas (`pointerInput` keeps its first
    // block), so each is read through the latest state — a callback that captured the page's
    // block list on the first frame would carry an empty one forever.
    val marquee by rememberUpdatedState(onMarquee)
    val dragChange by rememberUpdatedState(onDragChange)
    val drop by rememberUpdatedState(onDrop)
    return pointerInput(canDrag) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type == PointerType.Touch) return@awaitEachGesture
        val start = down.position + origin()
        val map = rows()
        val sel = selected()
        val onSelectedRow = map.any { (id, b) -> id in sel && b.first.contains(start) }
        val onText = map.values.any { it.second.contains(start) }
        val dragging = onSelectedRow
        if (!dragging && onText) return@awaitEachGesture
        if (dragging && !canDrag) return@awaitEachGesture
        var active = false
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val pos = change.position + origin()
            if (!change.pressed) {
                if (active) {
                    if (dragging) { drop(sel, dropTarget(map, sel, pos).first); dragChange(null) } else marquee(null)
                }
                break
            }
            if (!active && (pos - start).getDistance() > slop) active = true
            if (active) {
                change.consume()
                if (dragging) {
                    val (afterId, lineY) = dropTarget(map, sel, pos)
                    dragChange(GroupDrag(sel, pos, afterId, lineY))
                } else {
                    marquee(Rect(minOf(start.x, pos.x), minOf(start.y, pos.y), maxOf(start.x, pos.x), maxOf(start.y, pos.y)))
                }
            }
        }
    }
    }
}

/** The block a dragged run lands after — the last unselected row whose middle is above the
 * pointer — and the y of the insertion line (that row's bottom; the first row's top when none). */
private fun dropTarget(rows: Map<Long, Pair<Rect, Rect>>, selected: Set<Long>, pointer: Offset): Pair<Long?, Float?> {
    val candidates = rows.filterKeys { it !in selected }.entries.sortedBy { it.value.first.top }
    if (candidates.isEmpty()) return null to null
    val above = candidates.lastOrNull { it.value.first.center.y < pointer.y }
    return if (above == null) null to candidates.first().value.first.top else above.key to above.value.first.bottom
}

internal fun Rect.toBox() = Box(left, top, right, bottom)
