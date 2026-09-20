package com.tendril.app.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 */
internal data class ScaledLabel(val x: Float, val y: Float, val w: Float, val h: Float, val text: String, val emphasis: Boolean = false)

@Composable
internal fun ReadableLabels(labels: List<ScaledLabel>, scale: Float, pan: Offset, density: Float, color: Color) {
    val style = MaterialTheme.typography.caption
    val strong = style.copy(fontWeight = MaterialTheme.typography.heading.fontWeight)
    val lineDp = style.lineHeight.value
    labels.forEach { l ->
        val hDp = l.h * scale
        val lines = ((hDp - 4f) / lineDp).toInt().coerceIn(1, 3)
        Box(
            modifier = Modifier
                .offset { IntOffset((pan.x + l.x * density * scale).roundToInt(), (pan.y + l.y * density * scale).roundToInt()) }
                .size((l.w * scale).dp, hDp.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                l.text,
                style = if (l.emphasis) strong else style,
                color = color,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
