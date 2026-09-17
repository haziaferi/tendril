package com.tendril.app.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.Entry
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.domain.plan.TimelineBlock
import com.tendril.app.domain.plan.TimelineExtra
import com.tendril.app.domain.plan.laneAt
import com.tendril.app.domain.plan.openScrollMinute
import com.tendril.app.domain.plan.plannedMinutes
import com.tendril.app.domain.plan.snapMinute
import com.tendril.app.domain.plan.timeOfMinute
import com.tendril.app.domain.plan.timelineBlocks
import com.tendril.app.domain.plan.visibleAllDay
import com.tendril.app.domain.recurrence.EntryOccurrence
import com.tendril.app.domain.track.formatMinutes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label

private const val HOUR_DP = 56
private const val GUTTER_DP = 44
private const val DAY_MINUTES = 24 * 60

/**
 * B§13.4 14f·2 — the Week as a time grid on a wide window (decided 2026-09-16): seven of the
 * Day view's Plan lanes under one scroll and one header — the same 56 dp hour, 44 dp gutter,
 * `timelineBlocks`, now-line and quarter-hour snap (`PlanView`), so the week has the day's shape
 * rather than the strip's seven bulleted cards. The header is the Day view's per column: day and
 * date, today tinted, *Planned Nm* as a quiet second line; a click opens the Day. The all-day
 * row holds the untimed and the multi-day continuations, three then "+n". A block dragged lands
 * on the column under it (the date) at the quarter-hour under it (the time) — one gesture, both
 * axes; a series asks the screen *this one or all?* as every move does. Opens scrolled an hour
 * before the week's first block, else 07:00 (`docs/critiques/tasks-calendar-mock.md` #6: the
 * mock's grid ran off its frame). Chips wear the layer's tint with `onSurface` text (#7).
 */
@Composable
internal fun WeekGridView(
    weekStart: LocalDate,
    occurrences: List<EntryOccurrence>,
    habitExtras: (LocalDate) -> List<TimelineExtra>,
    onEdit: (Entry) -> Unit,
    onMove: (EntryOccurrence, LocalDate, LocalTime) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onShiftWeek: (Int) -> Unit,
    /** 14g·3 — the task blocks' urgency stripe; off hides it. */
    showUrgency: Boolean = true,
    /** B§13.6 #5 — the grid's geometry for the screen's own drags (the tray's), root coordinates. */
    onGeometry: (WeekGeometry) -> Unit = {},
    /** A drag from outside over the grid: the target the grid lights. */
    externalTarget: WeekDrop? = null,
    /** The grid's own block drag — its position and title — reported so the tray can light when the block is over it and the screen can draw the ghost there (the grid's is under the tray). */
    onDragPosition: (Offset?, String?) -> Unit = { _, _ -> },
    /** What the ghost says when the block is outside the grid (*Clear When* over the tray), else null. */
    outsideTargetLabel: (Offset) -> String? = { null },
    /** The block dropped outside the grid — the screen decides (the tray clears its When). */
    onDropOutside: (EntryOccurrence, Offset) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val hourPx = with(density) { HOUR_DP.dp.toPx() }
    val gutterPx = with(density) { GUTTER_DP.dp.toPx() }
    val days = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val today = LocalDate.now()
    // Per day: the timed blocks and the all-day items, from the occurrences the screen expanded.
    val perDay = remember(occurrences, days) {
        days.map { day ->
            val dayOcc = occurrences.filter { it.date == day }
            val blocks = timelineBlocks(dayOcc, habitExtras(day))
            val allDay = dayOcc.filter { it.startTime == null || !it.isFirstDay }
            DayColumn(day, blocks, allDay, plannedMinutes(blocks, allDay.map { it.entry }))
        }
    }
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var headerBounds by remember { mutableStateOf(Rect.Zero) }
    var allDayBounds by remember { mutableStateOf<Rect?>(null) }
    var laneWidthState by remember { mutableStateOf(0f) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf<GridDrag?>(null) }
    val scroll = rememberScrollState()
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }
    // Keyed on whether the week has blocks as well as on the week: the occurrences arrive a
    // frame after the first composition, and the scroll should land at the first block, not
    // at the morning the empty first pass computed.
    val hasBlocks = perDay.any { it.blocks.isNotEmpty() }
    LaunchedEffect(weekStart, hasBlocks) {
        scroll.scrollTo((openScrollMinute(perDay.flatMap { it.blocks }) / 60f * hourPx).roundToInt())
    }
    fun minuteAt(rootY: Float): Int = (((rootY - gridBounds.top + scroll.value) / hourPx) * 60).roundToInt().coerceIn(0, DAY_MINUTES - 1)
    val geometry = WeekGeometry(gridBounds, headerBounds, allDayBounds, gutterPx, laneWidthState, hourPx, scroll.value.toFloat(), weekStart)
    LaunchedEffect(geometry) { if (laneWidthState > 0f) onGeometry(geometry) }
    val hasAllDayRow = perDay.any { it.allDay.isNotEmpty() }
    LaunchedEffect(hasAllDayRow) { if (!hasAllDayRow) allDayBounds = null }

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { boxOrigin = it.boundsInRoot().topLeft }) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The week's own bar: ‹ 14 – 20 September 2026 ›
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShiftWeek(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week") }
                Text(weekLabel(days.first(), days.last()), style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { onShiftWeek(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next week") }
            }
            // Day headers: the Day view's per column. B§13.6 #5 — a drop target too (the day, no time).
            val targetOutline = MaterialTheme.colorScheme.primary
            Row(modifier = Modifier.fillMaxWidth().onGloballyPositioned { headerBounds = it.boundsInRoot() }) {
                Box(modifier = Modifier.width(GUTTER_DP.dp))
                perDay.forEach { col ->
                    val isToday = col.day == today
                    val targeted = externalTarget is WeekDrop.AllDay && externalTarget.day == col.day && allDayBounds == null
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isToday || targeted) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)) else Modifier)
                            .then(if (targeted) Modifier.border(2.dp, targetOutline, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { onSelectDate(col.day) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    ) {
                        Text(col.day.format(DateTimeFormatter.ofPattern("EEE d")), style = MaterialTheme.typography.label, maxLines = 1)
                        Text(
                            if (col.planned > 0) "Planned ${formatMinutes(col.planned)}" else " ",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            // The all-day row: three, then "+n" opening the day.
            if (hasAllDayRow) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp).onGloballyPositioned { allDayBounds = it.boundsInRoot() }) {
                    Box(modifier = Modifier.width(GUTTER_DP.dp))
                    perDay.forEach { col ->
                        val targeted = externalTarget is WeekDrop.AllDay && externalTarget.day == col.day
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                                .then(if (targeted) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)).border(2.dp, targetOutline, RoundedCornerShape(4.dp)) else Modifier),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val (shown, more) = visibleAllDay(col.allDay)
                            shown.forEach { o ->
                                // 14g·3 — an all-day task chip wears its urgency stripe like a timed block; the chip is the Month grid's (L4).
                                OccurrenceChip(
                                    title = o.entry.title,
                                    tint = layerTint(if (o.entry.kind == com.tendril.app.data.entry.EntryKind.TASK) BlockKind.TASK else BlockKind.EVENT),
                                    stripe = blockStripe(o.entry, showUrgency, today),
                                    onClick = { onEdit(o.entry) },
                                )
                            }
                            if (more > 0) {
                                Text("+$more", style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onSelectDate(col.day) }.padding(horizontal = 6.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { gridBounds = it.boundsInRoot() }.verticalScroll(scroll)) {
                val widthPx = with(density) { maxWidth.toPx() }
                val laneWidthPx = (widthPx - gutterPx) / 7f
                laneWidthState = laneWidthPx
                val slotTint = MaterialTheme.colorScheme.primaryContainer
                val lineColor = MaterialTheme.colorScheme.outlineVariant
                val nowColor = MaterialTheme.colorScheme.primary
                val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                val todayTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                Box(modifier = Modifier.fillMaxWidth().height((HOUR_DP * 24).dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        perDay.forEachIndexed { i, col ->
                            val x = gutterPx + i * laneWidthPx
                            if (col.day == today) drawRect(todayTint, Offset(x, 0f), androidx.compose.ui.geometry.Size(laneWidthPx, size.height))
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        }
                        for (h in 0..23) {
                            val y = h * hourPx
                            drawLine(lineColor, Offset(gutterPx, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                        val todayIndex = perDay.indexOfFirst { it.day == today }
                        if (todayIndex >= 0) {
                            val y = now.toSecondOfDay() / 60f / 60f * hourPx
                            val x0 = gutterPx + todayIndex * laneWidthPx
                            drawLine(nowColor, Offset(x0, y), Offset(x0 + laneWidthPx, y), strokeWidth = 3f)
                            drawCircle(nowColor, radius = 5f, center = Offset(x0, y))
                        }
                        // B§13.6 #5 — the quarter hour a drag from outside would land on.
                        (externalTarget as? WeekDrop.Slot)?.let { slot ->
                            val lane = perDay.indexOfFirst { it.day == slot.day }
                            if (lane >= 0) {
                                val sx = gutterPx + lane * laneWidthPx + 2f
                                val sy = slot.time.toSecondOfDay() / 3600f * hourPx
                                val slotSize = androidx.compose.ui.geometry.Size(laneWidthPx - 4f, hourPx / 4f)
                                drawRoundRect(slotTint, Offset(sx, sy), slotSize, CornerRadius(6.dp.toPx()))
                                drawRoundRect(nowColor, Offset(sx, sy), slotSize, CornerRadius(6.dp.toPx()), style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                            }
                        }
                    }
                    for (h in 0..23) {
                        Text("%02d".format(h), style = MaterialTheme.typography.caption, color = labelColor, modifier = Modifier.offset { IntOffset(8, (h * hourPx).roundToInt() - 6) })
                    }
                    perDay.forEachIndexed { i, col ->
                        val laneX = gutterPx + i * laneWidthPx
                        col.blocks.forEach { block ->
                            val subWidth = (laneWidthPx - 4f) / block.lanes
                            val x = laneX + 2f + block.lane * subWidth
                            val y = block.startMinute / 60f * hourPx
                            val h = block.minutes / 60f * hourPx
                            val moving = drag?.block?.key == block.key
                            val tint = layerTint(block.kind)
                            val entry = block.occurrence?.entry
                                val stripe = blockStripe(entry, showUrgency, today)
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(x.roundToInt(), y.roundToInt() + 1) }
                                    .width(with(density) { (subWidth - 2f).coerceAtLeast(8f).toDp() })
                                    .height(with(density) { (h - 2).coerceAtLeast(12f).toDp() })
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (moving) tint.copy(alpha = 0.4f) else tint)
                                    // 14g·3 — a task block's left edge is its urgency stripe (B§13.8.1).
                                    .drawBehind { stripe?.let { drawRect(it, size = Size(4.dp.toPx(), size.height)) } }
                                    .then(if (block.estimated) Modifier.dashedBorder(MaterialTheme.colorScheme.outline) else Modifier)
                                    .then(if (entry != null) Modifier.clickable { onEdit(entry) } else Modifier)
                                    .then(
                                        if (block.occurrence != null) Modifier.pointerInput(block.key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { start -> drag = GridDrag(block, Offset(gridBounds.left + x, gridBounds.top + y - scroll.value) + start) },
                                                onDrag = { change, delta -> change.consume(); drag = drag?.let { it.copy(position = it.position + delta) }; onDragPosition(drag?.position, drag?.block?.title) },
                                                onDragEnd = {
                                                    val d = drag; drag = null; onDragPosition(null, null)
                                                    if (d?.block?.occurrence != null) {
                                                        if (gridBounds.contains(d.position)) {
                                                            val lane = laneAt(d.position.x - gridBounds.left, gutterPx, laneWidthPx)
                                                            onMove(d.block.occurrence, days[lane], timeOfMinute(snapMinute(minuteAt(d.position.y))))
                                                        } else {
                                                            // B§13.6 #5 — off the grid: the screen decides (the tray clears its When).
                                                            onDropOutside(d.block.occurrence, d.position)
                                                        }
                                                    }
                                                },
                                                onDragCancel = { drag = null; onDragPosition(null, null) },
                                            )
                                        } else Modifier,
                                    )
                                    // The text clears the stripe: 4 dp of bar and 4 dp of air before the title.
                                    .padding(start = if (stripe != null) 12.dp else 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    block.title,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = if (block.minutes >= 45) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        drag?.let { d ->
            val inside = gridBounds.contains(d.position)
            val target = if (inside) {
                val laneWidthPx = (gridBounds.width - gutterPx) / 7f
                days[laneAt(d.position.x - gridBounds.left, gutterPx, laneWidthPx)].format(DateTimeFormatter.ofPattern("EEE d")) + " " + timeOfMinute(snapMinute(minuteAt(d.position.y)))
            } else outsideTargetLabel(d.position)
            // Off the grid over a target the screen owns (the tray), the screen draws the ghost above that pane.
            if (inside || target == null) Surface(
                modifier = Modifier.offset { IntOffset((d.position.x - boxOrigin.x).roundToInt() + 12, (d.position.y - boxOrigin.y).roundToInt() - 18) },
                tonalElevation = 6.dp, shadowElevation = 6.dp, shape = RoundedCornerShape(6.dp),
            ) {
                Text(d.block.title + (target?.let { "  → $it" } ?: ""), style = MaterialTheme.typography.caption, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

/** One column of the grid: the day's timed blocks, its all-day items, its planned minutes. */
private class DayColumn(val day: LocalDate, val blocks: List<TimelineBlock>, val allDay: List<EntryOccurrence>, val planned: Int)

private data class GridDrag(val block: TimelineBlock, val position: Offset)

private fun weekLabel(first: LocalDate, last: LocalDate): String =
    if (first.month == last.month) "${first.dayOfMonth} – ${last.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}"
    else "${first.format(DateTimeFormatter.ofPattern("d MMM"))} – ${last.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"

private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    drawRoundRect(color, style = stroke, cornerRadius = CornerRadius(6.dp.toPx()))
}
