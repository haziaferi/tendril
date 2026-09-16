package com.tendril.app.widget

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.theme.Register
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val WHEEL_BITMAP_WIDTH = 240
private const val WHEEL_BITMAP_HEIGHT = 120

/**
 * §8.3's Shade (0–100%, radius) + Hue (±90°, angle) combined into one radial picker instead of
 * two separate slider rows — two flat gradient bars read as visually near-identical "rows,"
 * where a single wheel makes both dimensions immediately legible as one gesture. Angle maps
 * directly onto hue offset (−90° left … 0° up … +90° right); radius maps onto shade (0% at
 * the center point, 100% at the arc's edge) — the same (angle, radius) → (hue, shade) mapping
 * used to build the wheel bitmap and to hit-test a touch back into those two values.
 */
@Composable
fun ShadeHueWheel(
    theme: Register,
    mode: Boolean,
    shade: Int,
    hueOffset: Int,
    onChange: (shade: Int, hueOffset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bitmap = remember(theme, mode) { buildWheelBitmap(theme, mode) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val resultRgb = remember(theme, mode, shade, hueOffset) { currentAccent2(theme, mode, shade, hueOffset) }
    val resultColor = Color(resultRgb.r, resultRgb.g, resultRgb.b)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val radiusPx = with(density) { maxWidth.toPx() } / 2f
            val heightDp = with(density) { radiusPx.toDp() }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .pointerInput(theme, mode) {
                        // A single low-level gesture loop rather than separate tap/drag
                        // detectors — any touch (a single tap or a drag) should update the
                        // picked value immediately, with no ambiguity to resolve between them.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            applyPick(down.position, radiusPx, onChange)
                            var pointer = down.id
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointer } ?: break
                                if (change.pressed) {
                                    applyPick(change.position, radiusPx, onChange)
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                drawImage(imageBitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))

                val angleRad = Math.toRadians(hueOffset.toDouble())
                val r = (shade / 100f) * radiusPx
                val puckX = radiusPx + r * kotlin.math.sin(angleRad).toFloat()
                val puckY = radiusPx - r * kotlin.math.cos(angleRad).toFloat()
                drawCircle(color = Color.White, radius = 7f, center = Offset(puckX, puckY))
                drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = 7f, center = Offset(puckX, puckY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(resultColor))
            Spacer(Modifier.width(8.dp))
            Text(resultRgb.toHex().lowercase(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private inline fun applyPick(position: Offset, radiusPx: Float, onChange: (Int, Int) -> Unit) {
    val dx = position.x - radiusPx
    val dy = (radiusPx - position.y).coerceAtLeast(0f)
    val radius = min(sqrt(dx * dx + dy * dy), radiusPx)
    val angleDeg = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).roundToInt().coerceIn(-90, 90)
    val shadePct = ((radius / radiusPx) * 100f).roundToInt().coerceIn(0, 100)
    onChange(shadePct, angleDeg)
}

private fun buildWheelBitmap(theme: Register, mode: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(WHEEL_BITMAP_WIDTH, WHEEL_BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
    val r = WHEEL_BITMAP_WIDTH / 2f
    for (py in 0 until WHEEL_BITMAP_HEIGHT) {
        for (px in 0 until WHEEL_BITMAP_WIDTH) {
            val dx = px - r
            val dy = r - py
            val radius = sqrt(dx * dx + dy * dy)
            if (dy < 0f || radius > r) {
                bmp.setPixel(px, py, 0x00000000)
                continue
            }
            val angleDeg = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).roundToInt().coerceIn(-90, 90)
            val shadePct = ((radius / r) * 100f).roundToInt().coerceIn(0, 100)
            val rgb = currentAccent2(theme, mode, shadePct, angleDeg)
            bmp.setPixel(px, py, android.graphics.Color.rgb(rgb.r, rgb.g, rgb.b))
        }
    }
    return bmp
}
