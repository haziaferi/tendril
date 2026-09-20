package com.tendril.app.domain.canvas

/**
 * L9 + L10 (2026-09-17) — where the canvas looks. Content is in dp; the board draws it at
 * `screen_px = content_dp × density × scale + pan` (`CanvasScreen`), so both rules here invert
 * that. [fitToCards] is the bar's *Fit* (Obsidian's zoom-to-fit): every card inside the pane
 * with a margin, at most 1× — one card is centred at 1×, never blown up. [contentAtPaneCentre]
 * is where a new card's top-left goes so it sits centred in the view at the current zoom,
 * whatever the pan (the menu's cards; the double-click places at the pointer instead).
 */
data class CanvasFit(val scale: Float, val panX: Float, val panY: Float)

fun fitToCards(
    cards: List<Pair<Float, Float>>,
    nodeW: Float,
    nodeH: Float,
    paneWidthPx: Float,
    paneHeightPx: Float,
    density: Float,
    marginDp: Float = 24f,
    maxScale: Float = 1f,
): CanvasFit {
    return fitToBoxes(cards.map { NodeBox(it.first, it.second, nodeW, nodeH) }, paneWidthPx, paneHeightPx, density, marginDp, maxScale)
}

/** [fitToCards] over boxes of any size — a frame's own (item 15), a card's fixed. */
fun fitToBoxes(
    boxes: List<NodeBox>,
    paneWidthPx: Float,
    paneHeightPx: Float,
    density: Float,
    marginDp: Float = 24f,
    maxScale: Float = 1f,
): CanvasFit {
    if (boxes.isEmpty() || paneWidthPx <= 0f || paneHeightPx <= 0f) return CanvasFit(1f, 0f, 0f)
    val left = boxes.minOf { it.x }; val top = boxes.minOf { it.y }
    val right = boxes.maxOf { it.right }; val bottom = boxes.maxOf { it.bottom }
    val boxW = (right - left) * density; val boxH = (bottom - top) * density
    val margin = marginDp * density
    val scale = minOf(maxScale, (paneWidthPx - 2 * margin) / boxW, (paneHeightPx - 2 * margin) / boxH).coerceAtLeast(0.05f)
    val panX = (paneWidthPx - boxW * scale) / 2f - left * density * scale
    val panY = (paneHeightPx - boxH * scale) / 2f - top * density * scale
    return CanvasFit(scale, panX, panY)
}

fun contentAtPaneCentre(
    paneWidthPx: Float,
    paneHeightPx: Float,
    scale: Float,
    panX: Float,
    panY: Float,
    density: Float,
    nodeW: Float,
    nodeH: Float,
): Pair<Float, Float> {
    val cx = ((paneWidthPx / 2f - panX) / scale) / density
    val cy = ((paneHeightPx / 2f - panY) / scale) / density
    return (cx - nodeW / 2f) to (cy - nodeH / 2f)
}

/**
 * §0.10 item 7 (2026-09-18) — the inert canvas card in a page draws the same layer the board
 * draws, at [fitToCards]'s scale. Its height follows the board's shape at the column's width
 * (Obsidian's canvas embed, measured: a 250 × 60 card's embed stood 164 px in an 828 px column),
 * clamped so a tall board cannot take the page and a flat one cannot vanish. Content in dp.
 */
fun canvasEmbedHeightDp(
    cards: List<Pair<Float, Float>>,
    nodeW: Float,
    nodeH: Float,
    columnWidthDp: Float,
    marginDp: Float = 24f,
    minDp: Float = 160f,
    maxDp: Float = 320f,
): Float {
    return embedHeightForBoxes(cards.map { NodeBox(it.first, it.second, nodeW, nodeH) }, columnWidthDp, marginDp, minDp, maxDp)
}

/** [canvasEmbedHeightDp] over boxes of any size. */
fun embedHeightForBoxes(boxes: List<NodeBox>, columnWidthDp: Float, marginDp: Float = 24f, minDp: Float = 160f, maxDp: Float = 320f): Float {
    if (boxes.isEmpty() || columnWidthDp <= 0f) return minDp
    val boxW = boxes.maxOf { it.right } - boxes.minOf { it.x }
    val boxH = boxes.maxOf { it.bottom } - boxes.minOf { it.y }
    val inner = (columnWidthDp - 2 * marginDp).coerceAtLeast(1f)
    return (inner * boxH / boxW + 2 * marginDp).coerceIn(minDp, maxDp)
}

/** Below this fit a card's text would fall under the smallest chrome size (12.5 sp of a 14 sp
 * `body`), so the layer draws the boxes alone — Obsidian's zoom threshold, and the type pass's
 * rule (2026-09-20): text is readable or absent, never shrunk. The mind-map card reads it too. */
const val CANVAS_CONTENT_MIN_SCALE = 12.5f / 14f

/**
 * How far a line through a box's centre travels before it leaves the box: the arrowhead's tip
 * sits there, on the target's edge, whichever side the line arrives from. The canvas card
 * (2026-09-20) made this matter: a 200 x 48 strip is met from the side far more often than
 * from above, and a pull-back of half its *height* left the tip under the card.
 * `dx`/`dy` is the line's direction (any length); `halfW`/`halfH` the box's half-extents.
 */
fun boxEdgeDistance(dx: Float, dy: Float, halfW: Float, halfH: Float): Float {
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len < 1e-6f) return 0f
    val ux = kotlin.math.abs(dx / len)
    val uy = kotlin.math.abs(dy / len)
    val tx = if (ux < 1e-6f) Float.POSITIVE_INFINITY else halfW / ux
    val ty = if (uy < 1e-6f) Float.POSITIVE_INFINITY else halfH / uy
    return minOf(tx, ty)
}
