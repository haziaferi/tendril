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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

private val DAY_WIDTH = 36.dp
private val LANE_HEIGHT = 40.dp
private val HEADER_HEIGHT = 44.dp

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
                style = MaterialTheme.typography.bodyMedium,
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
    val blockerInk = MaterialTheme.colorScheme.tertiary

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.horizontalScroll(hScroll)) {
            Column {
                DayHeader(range.start, dayCount, today)
                Box(modifier = Modifier.width(DAY_WIDTH * dayCount).height(LANE_HEIGHT * bars.size.coerceAtLeast(1))) {
                    // Week hairlines and today's column, under everything.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (i in 0 until dayCount) {
                            val day = range.start.plusDays(i.toLong())
                            if (day == today) drawRect(color = blockerInk.copy(alpha = 0.08f), topLeft = Offset(i * dayPx, 0f), size = androidx.compose.ui.geometry.Size(dayPx, size.height))
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
            Text("No date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            undated.forEach { row ->
                Text(row.page.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { onOpenPage(row.page.id) }.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(start: LocalDate, dayCount: Int, today: LocalDate) {
    Row(modifier = Modifier.height(HEADER_HEIGHT)) {
        for (i in 0 until dayCount) {
            val day = start.plusDays(i.toLong())
            val isToday = day == today
            Column(modifier = Modifier.width(DAY_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (day.dayOfMonth == 1 || i == 0) day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isToday) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    day.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One bar: positioned by its start, as wide as its days; dragged sideways it snaps to whole
 * days on release and reports the delta. A tap opens the row. */
@Composable
private fun TimelineBarRow(
    bar: TimelineBar<TableRow>,
    lane: Int,
    offsetDays: Long,
    dayPx: Float,
    isBlocked: Boolean,
    draggable: Boolean,
    onMove: (Long) -> Unit,
    onOpen: () -> Unit,
) {
    var dragPx by remember(bar.row.page.id, bar.start) { mutableStateOf(0f) }
    val container = if (isBlocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val ink = if (isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .offset { IntOffset((offsetDays * dayPx + dragPx).roundToInt(), (lane * LANE_HEIGHT.toPx()).roundToInt()) }
            .width(DAY_WIDTH * bar.days.toInt())
            .height(LANE_HEIGHT)
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
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
        Text(
            (if (isBlocked) "⏸ " else "") + bar.row.page.title,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
