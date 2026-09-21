package com.tendril.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.colour.HUE_PRESETS
import com.tendril.app.domain.colour.normalHue
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.contrast
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.hslToSrgb
import com.tendril.app.ui.theme.hueColours
import com.tendril.app.ui.theme.toSrgb
import kotlin.math.roundToInt

/**
 * S13 (B§13.8.2) — the one control for a hue: a database's, a card's, a frame's. A bar of the wheel
 * (0–360°) with a knob, Obsidian's six presets as marks under it (measured live: 20 px discs at a
 * 29 px pitch — 28 dp targets here), a *Default* / *None* row above that returns null, the swatch
 * reading the solved colour and its contrast on this ground (the phone's walk: *on this ground* ellipsised at Touch's 16 sp — the ratio alone). Stores a hue, never a colour — the
 * register solves it on each ground, as it solves a label's.
 */
@Composable
fun HueSheet(title: String, current: Int?, unsetLabel: String, onDone: (Int?) -> Unit, onDismiss: () -> Unit, /** What null means on screen — a database's hashed default; null where none is none. */ defaultHue: Int? = null) {
    var hue by remember(current) { mutableStateOf(current) }
    val palette = LocalTendrilPalette.current
    val effective = hue ?: defaultHue
    val solved = effective?.let { hueColours(it, palette) }
    TendrilSheet(title = title, onDismiss = onDismiss) {
        // The swatch row: what the hue is on this ground.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(36.dp)) {
            Box(
                modifier = Modifier.width(20.dp).height(20.dp).background(solved?.hue ?: MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.5.dp, if (solved == null) MaterialTheme.colorScheme.outline else Color.Transparent, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (solved != null && effective != null) (if (hue == null) "Default · " else "") + "hue ${effective}° · ${"%.1f".format(java.util.Locale.ROOT, contrast(solved.hue.toSrgb(), palette.bg.toSrgb()))} : 1" else unsetLabel,
                style = MaterialTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // The bar: the wheel itself, a knob at the hue; a tap or a drag sets it.
        var barWidthPx by remember { mutableStateOf(1f) }
        val stops = remember { (0..12).map { hslToSrgb(it * 30.0, 0.75, 0.55).toColor() } }
        Box(
            modifier = Modifier.fillMaxWidth().height(24.dp).padding(vertical = 0.dp)
                .background(Brush.horizontalGradient(stops), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pos -> hue = normalHue((pos.x / size.width * 360f).roundToInt()) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> change.consume(); hue = normalHue((change.position.x / size.width * 360f).roundToInt().coerceIn(0, 359)) }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                barWidthPx = size.width
                hue?.let { h ->
                    val x = h / 360f * size.width
                    drawCircle(palette.bg, radius = 14.dp.toPx(), center = Offset(x, size.height / 2f))
                    drawCircle(solved?.hue ?: palette.textDim, radius = 11.dp.toPx(), center = Offset(x, size.height / 2f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // The six presets as marks: 28 dp targets, the disc 20 dp, in their solved colours.
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            HUE_PRESETS.forEach { p ->
                val c = hueColours(p, palette)
                Box(modifier = Modifier.width(28.dp).height(28.dp).clickable { hue = p }, contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.width(20.dp).height(20.dp).background(c.hue, CircleShape)
                            .then(if (hue == p) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Default / None — the row that returns null.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(36.dp).clickable { hue = null }) {
            Box(
                modifier = Modifier.width(20.dp).height(20.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (hue == null) Box(Modifier.width(10.dp).height(10.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape)) }
            Spacer(Modifier.width(10.dp))
            Text(unsetLabel, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onDone(hue) }) { Text("Done") }
        }
    }
}
