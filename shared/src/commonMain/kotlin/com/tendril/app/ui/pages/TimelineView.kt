package com.tendril.app.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.tendril.app.ui.calendar.DragGhost
import com.tendril.app.ui.components.dragSource
import com.tendril.app.ui.nav.LocalDensityProfile
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tendril.app.data.pagedatabase.PageDatabaseView
import com.tendril.app.domain.timeline.TimelineBar
import com.tendril.app.domain.timeline.timelineBars
import com.tendril.app.domain.timeline.timelineRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

private val DAY_WIDTH = 36.dp
private val LANE_HEIGHT = 40.dp
private val HEADER_HEIGHT = 54.dp

/**
 * §0.6.14 / B§6 #17 — the fifth database view: a day per column, a row per lane, a bar from
 * the row's start date to its end (one day when the view has no end column or the cell is
 * empty). Drag a bar and it moves by whole days through the same date write the Table makes
 * — a synced row's task moves with it. Open blockers (§0.6.14, "blocked by") draw a line into
 * the bar they hold up. One scale; the grid scrolls sideways and starts at today.
 */
@Composable
internal fun TimelineBody(rows: List<TableRow>, view: PageDatabaseView?, viewModel: PageDatabaseViewModel, onOpenPage: (Long) -> Unit) {
    val startId = view?.datePropertyId
    if (view == null || startId == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Timeline needs a Date property for the bars' start — configure this view to pick one.",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val endId = view.endDatePropertyId
    val today = LocalDate.now()
    val bars = timelineBars(rows, { viewModel.dateForCell(it, startId) }, { r -> endId?.let { viewModel.dateForCell(r, it) } })
    val undated = rows.filter { viewModel.dateForCell(it, startId) == null }
    val range = timelineRange(bars, today)
    val dayCount = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
    val blocked by viewModel.blockedStates.collectAsState()
    val viewOnly = LocalViewOnly.current

    val density = LocalDensity.current
    val dayPx = with(density) { DAY_WIDTH.toPx() }
    val lanePx = with(density) { LANE_HEIGHT.toPx() }
    val headerPx = with(density) { HEADER_HEIGHT.toPx() }
    val hScroll = rememberScrollState()
    // Open on today, a day of margin to the left, once per range.
    LaunchedEffect(range.start) {
        hScroll.scrollTo(((ChronoUnit.DAYS.between(range.start, today) - 1).coerceAtLeast(0) * dayPx).roundToInt())
    }
    val laneOf = bars.withIndex().associate { (i, bar) -> bar.row.page.id to i }
    // 14g·2 — a dependency line is the error family (blocked), not the third hue.
    val blockerInk = MaterialTheme.colorScheme.error
    // B§13.6 #5 — a *No date* row dragged onto a day column takes it as its start (`setDateCell`,
    // the bar drag's write). Root coordinates: the scrolling box's bounds plus its scroll give
    // the day under the pointer; the column lights; the ghost names the property and the day.
    val properties by viewModel.properties.collectAsState()
    val startName = properties.firstOrNull { it.id == startId }?.name ?: "Date"
    val pointer = LocalDensityProfile.current.pointer
    var scrollBounds by remember { mutableStateOf(Rect.Zero) }
    var rowDrag by remember { mutableStateOf<UndatedDrag?>(null) }
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    val targetDay: LocalDate? = rowDrag?.let { d ->
        if (!scrollBounds.contains(d.position)) null
        else range.start.plusDays(((d.position.x - scrollBounds.left + hScroll.value) / dayPx).toLong().coerceIn(0L, (dayCount - 1).toLong()))
    }
    val targetTint = MaterialTheme.colorScheme.primaryContainer
    val targetInk = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.boundsInRoot().topLeft }) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.horizontalScroll(hScroll).onGloballyPositioned { scrollBounds = it.boundsInRoot() }) {
            Column {
                DayHeader(range.start, dayCount, today)
                Box(modifier = Modifier.width(DAY_WIDTH * dayCount).height(LANE_HEIGHT * bars.size.coerceAtLeast(1))) {
                    // Week hairlines and today's column, under everything.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (i in 0 until dayCount) {
                            val day = range.start.plusDays(i.toLong())
                            if (day == today) drawRect(color = blockerInk.copy(alpha = 0.08f), topLeft = Offset(i * dayPx, 0f), size = androidx.compose.ui.geometry.Size(dayPx, size.height))
                            if (day == targetDay) {
                                drawRect(color = targetTint, topLeft = Offset(i * dayPx, 0f), size = androidx.compose.ui.geometry.Size(dayPx, size.height))
                                drawRect(color = targetInk, topLeft = Offset(i * dayPx, 0f), size = androidx.compose.ui.geometry.Size(dayPx, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                            }
                            if (day.dayOfWeek == DayOfWeek.MONDAY) drawLine(color = blockerInk.copy(alpha = 0.25f), start = Offset(i * dayPx, 0f), end = Offset(i * dayPx, size.height), strokeWidth = 1f)
                        }
                        // Dependencies: from the end of each open blocker's bar to the start of the blocked bar.
                        bars.forEachIndexed { lane, bar ->
                            val open = blocked[bar.row.page.id]?.open.orEmpty()
                            for (blocker in open) {
                                val fromLane = laneOf[blocker.id] ?: continue
                                val fromBar = bars[fromLane]
                                val x1 = (ChronoUnit.DAYS.between(range.start, fromBar.end) + 1) * dayPx
                                val y1 = fromLane * lanePx + lanePx / 2f
                                val x2 = ChronoUnit.DAYS.between(range.start, bar.start) * dayPx
                                val y2 = lane * lanePx + lanePx / 2f
                                drawLine(color = blockerInk.copy(alpha = 0.7f), start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 2f)
                            }
                        }
                    }
                    bars.forEachIndexed { lane, bar ->
                        TimelineBarRow(
                            bar = bar,
                            lane = lane,
                            offsetDays = ChronoUnit.DAYS.between(range.start, bar.start),
                            dayPx = dayPx,
                            dayCount = dayCount,
                            isBlocked = blocked[bar.row.page.id]?.isBlocked == true,
                            draggable = !viewOnly,
                            onMove = { days -> viewModel.moveBar(view, bar, days) },
                            onOpen = { onOpenPage(bar.row.page.id) },
                        )
                    }
                }
            }
        }
        if (undated.isNotEmpty()) {
            Text(
                if (viewOnly) "No date" else "No date · " + (if (pointer) "drag onto a day" else "hold and drag onto a day"),
                style = MaterialTheme.typography.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            undated.forEach { row ->
                var origin by remember(row.page.id) { mutableStateOf(Offset.Zero) }
                val target = rememberUpdatedState(targetDay)
                Text(
                    row.page.title, style = MaterialTheme.typography.body,
                    modifier = Modifier.fillMaxWidth()
                        .alpha(if (rowDrag?.row?.page?.id == row.page.id) 0.35f else 1f)
                        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                        .clickable { onOpenPage(row.page.id) }
                        .then(
                            if (viewOnly) Modifier else Modifier.dragSource(
                                pointer, row.page.id,
                                onStart = { rowDrag = UndatedDrag(row, origin + it) },
                                onDrag = { delta -> rowDrag = rowDrag?.let { it.copy(position = it.position + delta) } },
                                onEnd = { val d = rowDrag; rowDrag = null; val day = target.value; if (d != null && day != null) viewModel.setDateCell(d.row, startId, day) },
                                onCancel = { rowDrag = null },
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
    rowDrag?.let { d ->
        DragGhost(
            title = d.row.page.title, target = targetDay?.let { "$startName → " + it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) },
            modifier = Modifier.offset { IntOffset((d.position.x - layerOrigin.x).roundToInt() + 12, (d.position.y - layerOrigin.y).roundToInt() - 18) },
        )
    }
    }
}

/** B§13.6 #5 — a *No date* row dragged toward a day, in root coordinates. */
private data class UndatedDrag(val row: TableRow, val position: Offset)

@Composable
private fun DayHeader(start: LocalDate, dayCount: Int, today: LocalDate) {
    Row(modifier = Modifier.height(HEADER_HEIGHT)) {
        for (i in 0 until dayCount) {
            val day = start.plusDays(i.toLong())
            val isToday = day == today
            Column(modifier = Modifier.width(DAY_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.dayOfMonth == 1 || i == 0) day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } else "",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.caption,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    day.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One bar: positioned by its start, as wide as its days; dragged sideways it snaps to whole
 * days on release and reports the delta. A tap opens the row. */
/**
 * One bar. Notion's rule (the user, 2026-09-16, three notes the same day): **the colour envelops
 * the whole title** — the bar is as wide as its days or as wide as its title with 16 dp of air,
 * whichever is more — so a one-day row is not an ellipsis (its first form), and its label does
 * not sit on the ground looking like the next day's (its second). The days themselves are said
 * by a 2 dp line in the ink along the bar's foot over the true span, so a title-wide bar still
 * reads as one day. The whole bar clicks and drags; the label runs as far as the timeline's end.
 */
@Composable
private fun TimelineBarRow(
    bar: TimelineBar<TableRow>,
    lane: Int,
    offsetDays: Long,
    dayPx: Float,
    dayCount: Int,
    isBlocked: Boolean,
    draggable: Boolean,
    onMove: (Long) -> Unit,
    onOpen: () -> Unit,
) {
    var dragPx by remember(bar.row.page.id, bar.start) { mutableStateOf(0f) }
    val container = if (isBlocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val ink = if (isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val style = MaterialTheme.typography.caption
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spanWidth = DAY_WIDTH * bar.days.toInt()
    val titleWidthPx = remember(bar.row.page.title, style) { measurer.measure(bar.row.page.title, style, maxLines = 1).size.width }
    val titleWidth = with(density) { titleWidthPx.toDp() } + 16.dp
    val remaining = with(density) { ((dayCount - offsetDays).coerceAtLeast(1L) * dayPx).toDp() }
    val barWidth = maxOf(spanWidth, titleWidth).coerceAtMost(remaining)
    val spanPx = with(density) { spanWidth.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset((offsetDays * dayPx + dragPx).roundToInt(), (lane * LANE_HEIGHT.toPx()).roundToInt()) }
            .width(barWidth)
            .height(LANE_HEIGHT)
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            // The true span, under the label: the bar's foot in the ink where the days are.
            .drawBehind {
                if (barWidth > spanWidth) drawRect(ink.copy(alpha = 0.55f), topLeft = Offset(0f, size.height - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size((spanPx - 4.dp.toPx()).coerceAtLeast(6f), 2.dp.toPx()))
            }
            .clickable(onClick = onOpen)
            .pointerInput(bar.row.page.id, bar.start, draggable) {
                if (!draggable) return@pointerInput
                detectDragGestures(
                    onDrag = { change, amount -> change.consume(); dragPx += amount.x },
                    onDragEnd = {
                        val days = (dragPx / dayPx).roundToLong()
                        dragPx = 0f
                        if (days != 0L) onMove(days)
                    },
                    onDragCancel = { dragPx = 0f },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(bar.row.page.title, style = style, color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
    }
}
