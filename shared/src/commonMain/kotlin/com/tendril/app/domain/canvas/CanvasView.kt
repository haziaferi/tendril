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
    if (cards.isEmpty() || paneWidthPx <= 0f || paneHeightPx <= 0f) return CanvasFit(1f, 0f, 0f)
    val left = cards.minOf { it.first }; val top = cards.minOf { it.second }
    val right = cards.maxOf { it.first } + nodeW; val bottom = cards.maxOf { it.second } + nodeH
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
    if (cards.isEmpty() || columnWidthDp <= 0f) return minDp
    val boxW = cards.maxOf { it.first } + nodeW - cards.minOf { it.first }
    val boxH = cards.maxOf { it.second } + nodeH - cards.minOf { it.second }
    val inner = (columnWidthDp - 2 * marginDp).coerceAtLeast(1f)
    return (inner * boxH / boxW + 2 * marginDp).coerceIn(minDp, maxDp)
}

/** Below this fit a card's text would be unreadable; the layer draws the boxes alone (Obsidian's zoom threshold). */
const val CANVAS_CONTENT_MIN_SCALE = 0.5f
