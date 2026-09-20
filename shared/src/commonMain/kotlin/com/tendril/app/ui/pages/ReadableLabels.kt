package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.heading
import kotlin.math.roundToInt

/**
 * The size count (2026-09-20) — a scaled drawing's words at a readable size. A canvas embed and
 * a mind-map card draw their boxes through one `graphicsLayer(scale)`, which would scale their
 * text with them; below `CANVAS_CONTENT_MIN_SCALE` the layer draws the boxes bare and this
 * overlay writes each box's words over it at `caption` — the smallest chrome size — clipped to
 * the box's scaled outline with an ellipsis. The words never shrink and never vanish: a board's
 * shapes say something without them, a map's boxes say nothing.
 *
 * A `pill` label is a frame's: the board draws it as a chip *above* the frame's top-left edge
 * (`CanvasFrameBox`), never inside the frame where a card may sit, and the overlay places it the
 * same way — at the chip's own height, one line, left-aligned, on `surface`, at `heading` (the
 * board's section heading; never scaled). (The user, 2026-09-20, on Garden plan at the embed's
 * fit: *Beds* written over a card's words, then "very small, blends in".)
 */
internal data class ScaledLabel(val x: Float, val y: Float, val w: Float, val h: Float, val text: String, val emphasis: Boolean = false, val pill: Boolean = false, val underline: Boolean = false)

@Composable
internal fun ReadableLabels(labels: List<ScaledLabel>, scale: Float, pan: Offset, density: Float, color: Color) {
    val style = MaterialTheme.typography.caption
    val strong = style.copy(fontWeight = MaterialTheme.typography.heading.fontWeight)
    val lineDp = style.lineHeight.value
    val pillStyle = MaterialTheme.typography.heading
    val pillDp = pillStyle.lineHeight.value + 4f
    val pillGround = MaterialTheme.colorScheme.surface
    labels.forEach { l ->
        if (l.pill) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((pan.x + l.x * density * scale).roundToInt(), (pan.y + l.y * density * scale - (pillDp + 4f) * density).roundToInt()) }
                    .height(pillDp.dp)
                    .widthIn(max = (l.w * scale).dp)
                    .background(pillGround, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(l.text, style = pillStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) // type: SECTION_HEADING — a frame's name, as the board draws it
            }
            return@forEach
        }
        val hDp = l.h * scale
        val lines = ((hDp - 4f) / lineDp).toInt().coerceIn(1, 3)
        Box(
            modifier = Modifier
                .offset { IntOffset((pan.x + l.x * density * scale).roundToInt(), (pan.y + l.y * density * scale).roundToInt()) }
                .size((l.w * scale).dp, hDp.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A leaf on the line (the mind-map pass): the word keeps the rule that says what it is.
            Text(
                l.text,
                style = if (l.emphasis) strong else if (l.underline) style.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline) else style,
                color = color,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (l.underline) TextAlign.Start else TextAlign.Center,
            )
        }
    }
}
