package com.tendril.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.plan.HABIT_STROKE_SHORT_DP
import com.tendril.app.domain.plan.HabitStroke
import com.tendril.app.domain.plan.strokeHeightDp
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.caption
import kotlin.math.roundToInt

/**
 * The wash's share of the habit hue — 12 %: 1.22 / 1.19 : 1 against the two grounds, a tint under
 * the day and not a required graphic (under the 3 : 1 non-text floor by design, as `surface2`'s
 * 6 % is), with the name in the hue at 5.5 : 1 on it. `dim` would not clear AA on any share
 * (4.4 / 4.2 measured), so the name is in the habit's own hue and the softening is its weight
 * and case (`docs/critiques/habit-strokes-mock.md`, the addendum).
 */
const val HABIT_STROKE_SHARE = 0.12f

/**
 * §3.2 (2026-09-21) — one habit stroke on a grid: the full lane, the wash feathered 6 dp at each
 * end (4 on a short stroke), the name in `caption` at Regular in the hue along its start — no
 * time, the grid says when — centred when the stroke is under 30 dp. One state: the check-in
 * changes no wash; on today's lane a 5 dp dot before the name is presence. Drawn *before* the
 * blocks, so a block over the span covers it — the point. Inert to drags and drops (the grids'
 * drop arithmetic never sees it); a click opens the habit's sheet.
 */
@Composable
internal fun HabitStrokeBox(stroke: HabitStroke, xPx: Float, widthPx: Float, hourPx: Float, hourDp: Float, onClick: () -> Unit) {
    val hue = LocalTendrilPalette.current.habit
    val density = LocalDensity.current
    val heightDp = strokeHeightDp(stroke.minutes, hourDp)
    val short = heightDp < HABIT_STROKE_SHORT_DP
    val featherDp = if (short) 4f else 6f
    val yPx = stroke.startMinute / 60f * hourPx
    val wash = hue.copy(alpha = HABIT_STROKE_SHARE)
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .height(heightDp.dp)
            .drawBehind {
                val f = (featherDp.dp.toPx() / size.height).coerceAtMost(0.45f)
                drawRect(Brush.verticalGradient(0f to Color.Transparent, f to wash, 1f - f to wash, 1f to Color.Transparent))
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = if (short) Alignment.CenterStart else Alignment.TopStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = if (short) Modifier else Modifier.padding(top = 3.dp)) {
            if (stroke.checkedIn) {
                Box(Modifier.width(5.dp).height(5.dp).background(hue, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(stroke.habit.title, style = MaterialTheme.typography.caption, color = hue, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
