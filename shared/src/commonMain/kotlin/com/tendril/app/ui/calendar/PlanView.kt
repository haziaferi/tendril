package com.tendril.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.Entry
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.domain.plan.LoggedSpan
import com.tendril.app.domain.plan.TimelineBlock
import com.tendril.app.domain.plan.snapMinute
import com.tendril.app.domain.plan.timeOfMinute
import com.tendril.app.domain.recurrence.EntryOccurrence
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val HOUR_DP = 56
private const val GUTTER_DP = 44
private const val DAY_MINUTES = 24 * 60

/**
 * §0.6.5 / §0.8 step 7b — Plan mode: the day as a timeline. Tiimo's visible day, Llama Life's
 * "now": hours down the side, a block per timed thing sized by its span or estimate (estimated
 * ones dashed), the current minute as a line, and a rail of unplanned tasks below. Placing is
 * the person's — a rail item long-pressed and dragged onto the grid lands at the drop, snapped
 * to a quarter hour; a block dragged up or down moves the same way. Nothing is placed for them
 * (B§6 #8, drag-only first; §0.5.2).
 */
@Composable
internal fun PlanView(
    day: LocalDate,
    blocks: List<TimelineBlock>,
    logged: List<LoggedSpan>,
    allDay: List<EntryOccurrence>,
    unplanned: List<Entry>,
    onEdit: (Entry) -> Unit,
    onPlace: (Entry, LocalTime) -> Unit,
    onMoveBlock: (EntryOccurrence, LocalTime) -> Unit,
) {
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR_DP.dp.toPx() }
    val gutterPx = with(density) { GUTTER_DP.dp.toPx() }
    // The scroll viewport's bounds, not the grid's: a child's `onGloballyPositioned` does not
    // fire again as its parent scrolls, so the grid's top would be stale; the viewport stays put
    // and `scroll.value` says how far the grid has moved under it.
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf<PlanDrag?>(null) }
    val scroll = rememberScrollState()
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }
    // Open scrolled to a little before the first block, or the morning.
    LaunchedEffect(day) {
        val first = blocks.minOfOrNull { it.startMinute } ?: (8 * 60)
        scroll.scrollTo(((first - 60).coerceAtLeast(0) / 60f * hourPx).roundToInt())
    }

    fun minuteAt(rootY: Float): Int = (((rootY - gridBounds.top + scroll.value) / hourPx) * 60).roundToInt().coerceIn(0, DAY_MINUTES - 1)

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { boxOrigin = it.boundsInRoot().topLeft }) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (allDay.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allDay.forEach { o -> AssistChip(onClick = { onEdit(o.entry) }, label = { Text(o.entry.title, maxLines = 1) }) }
                }
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { gridBounds = it.boundsInRoot() }.verticalScroll(scroll)) {
                val widthPx = with(density) { maxWidth.toPx() }
                val laneAreaPx = widthPx - gutterPx - with(density) { 8.dp.toPx() }
                val lineColor = MaterialTheme.colorScheme.outlineVariant
                val nowColor = MaterialTheme.colorScheme.primary
                val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier.fillMaxWidth().height((HOUR_DP * 24).dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (h in 0..23) {
                            val y = h * hourPx
                            drawLine(lineColor, Offset(gutterPx, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                        if (day == LocalDate.now()) {
                            val y = now.toSecondOfDay() / 60f / 60f * hourPx
                            drawLine(nowColor, Offset(gutterPx - 6f, y), Offset(size.width, y), strokeWidth = 3f)
                            drawCircle(nowColor, radius = 6f, center = Offset(gutterPx - 6f, y))
                        }
                    }
                    for (h in 0..23) {
                        Text(
                            "%02d".format(h),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            modifier = Modifier.offset { IntOffset(8, (h * hourPx).roundToInt() - 6) },
                        )
                    }
                    // §0.6.5 — where the time actually went, as a wash *under* the plan: no border,
                    // no text, nothing to tap. The open log's span ends at now, so it grows.
                    val washColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    logged.forEach { span ->
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(gutterPx.roundToInt(), (span.startMinute / 60f * hourPx).roundToInt()) }
                                .width(with(density) { laneAreaPx.toDp() })
                                .height(with(density) { ((span.endMinute - span.startMinute) / 60f * hourPx).coerceAtLeast(2f).toDp() })
                                .background(washColor, RoundedCornerShape(4.dp)),
                        )
                    }
                    blocks.forEach { block ->
                        val laneWidth = laneAreaPx / block.lanes
                        val x = gutterPx + block.lane * laneWidth
                        val y = block.startMinute / 60f * hourPx
                        val h = block.minutes / 60f * hourPx
                        val moving = drag?.let { it.block?.key == block.key } == true
                        val tint = when (block.kind) {
                            BlockKind.EVENT -> MaterialTheme.colorScheme.secondaryContainer
                            BlockKind.TASK -> MaterialTheme.colorScheme.primaryContainer
                            BlockKind.HABIT -> MaterialTheme.colorScheme.tertiaryContainer
                            BlockKind.OTHER -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val entry = block.occurrence?.entry
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(x.roundToInt() + 2, y.roundToInt() + 1) }
                                .width(with(density) { (laneWidth - 4).toDp() })
                                .height(with(density) { (h - 2).coerceAtLeast(12f).toDp() })
                                .background(if (moving) tint.copy(alpha = 0.4f) else tint, RoundedCornerShape(6.dp))
                                .then(if (block.estimated) Modifier.dashedBorder(MaterialTheme.colorScheme.outline) else Modifier)
                                .then(if (entry != null) Modifier.clickable { onEdit(entry) } else Modifier)
                                .then(
                                    if (block.occurrence != null) Modifier.pointerInput(block.key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { start -> drag = PlanDrag(block = block, entry = null, position = Offset(gridBounds.left + x, gridBounds.top + y - scroll.value) + start, title = block.title) },
                                            onDrag = { change, delta -> change.consume(); drag = drag?.let { it.copy(position = it.position + delta) } },
                                            onDragEnd = {
                                                val d = drag; drag = null
                                                if (d?.block?.occurrence != null && gridBounds.contains(d.position)) {
                                                    onMoveBlock(d.block.occurrence, timeOfMinute(snapMinute(minuteAt(d.position.y))))
                                                }
                                            },
                                            onDragCancel = { drag = null },
                                        )
                                    } else Modifier,
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                block.title + (if (block.estimated && block.kind == BlockKind.TASK) "  ~${block.minutes}m" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = if (block.minutes >= 45) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // The rail: what is not placed yet. Long-press and drag one up onto the grid.
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        if (unplanned.isEmpty()) "Nothing unplanned" else "Unplanned · drag onto the day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        unplanned.forEach { task ->
                            var origin by remember(task.id) { mutableStateOf(Offset.Zero) }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                                    .pointerInput(task.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { start -> drag = PlanDrag(block = null, entry = task, position = origin + start, title = task.title) },
                                            onDrag = { change, delta -> change.consume(); drag = drag?.let { it.copy(position = it.position + delta) } },
                                            onDragEnd = {
                                                val d = drag; drag = null
                                                if (d?.entry != null && gridBounds.contains(d.position)) {
                                                    onPlace(d.entry, timeOfMinute(snapMinute(minuteAt(d.position.y))))
                                                }
                                            },
                                            onDragCancel = { drag = null },
                                        )
                                    },
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(task.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    task.estimate?.let { Text("  ${it.toMinutes()}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
        }
        drag?.let { d ->
            val minute = if (gridBounds.contains(d.position)) snapMinute(minuteAt(d.position.y)) else null
            Surface(
                modifier = Modifier.offset { IntOffset((d.position.x - boxOrigin.x).roundToInt() + 12, (d.position.y - boxOrigin.y).roundToInt() - 18) },
                tonalElevation = 6.dp, shadowElevation = 6.dp, shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    d.title + (minute?.let { "  → " + timeOfMinute(it).toString() } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** What is being dragged: a block off the grid, or a task off the rail. */
private data class PlanDrag(val block: TimelineBlock?, val entry: Entry?, val position: Offset, val title: String)

/** An estimated block's outline is dashed — the length is a guess, and the border says so. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    drawRoundRect(color, style = stroke, cornerRadius = CornerRadius(6.dp.toPx()))
}
