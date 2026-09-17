package com.tendril.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tendril.app.domain.plan.cellCapacity
import com.tendril.app.domain.plan.dayLabel
import com.tendril.app.domain.plan.monthGridDays
import com.tendril.app.domain.plan.visibleInCell
import com.tendril.app.ui.components.UnderAnchor
import com.tendril.app.ui.components.dragSource
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.description
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One line in a cell: what the grid draws, and the caller's own object behind it. */
data class MonthItem(
    val key: Any,
    val title: String,
    val time: LocalTime?,
    val tint: Color,
    val stripe: Color?,
    /** False for what the grid must not lift — a habit's day is its rule's, a database date the Table's cell (the Calendar tab's extras). */
    val draggable: Boolean,
    val payload: Any,
)

private const val NUMBER_ROW_DP = 22
private const val CELL_INSET_DP = 4
private const val POPOVER_WIDTH_DP = 280

/**
 * The Month as a grid of titled cells — the desktop audit's L4 (2026-09-17), one composable with
 * two homes: the Calendar tab's Month and a database's Calendar view (Notion's is this grid;
 * `tools/audit.py` refuses a third). Rows are the weeks the month spans, equal, filling the
 * height ([monthGridDays]); a cell holds as many [OccurrenceChip]s as its height allows
 * ([cellCapacity]) and gives the last slot to *+n* ([visibleInCell]), which opens a popover
 * listing the day (the hover card's frame — Google's and Outlook's *more*, where Notion's grows
 * the row). The number is top-left at the Week header's alignment (Notion's is top-right — the
 * one convention not taken), today a filled disc (Notion's 22 px); adjacent-month days are dim
 * numbers with their items at full strength (a chip at 60 % measured 3.2–4.0:1). Clicks: the
 * number → [onDayClick]; a chip → [onItemClick]; the empty ground → [onGroundClick] when given
 * (the Calendar tab seeds its quick-add strip with the day; a database has no title to add) — a
 * `+` shows at the number's right on hover, as Notion's does; a double-click on the ground opens
 * the day too. A draggable item lifts on press-and-move ([dragSource]) and lands on the cell
 * under the pointer through [onItemMove] — the caller's own write, the Week strip's series
 * prompt included. Composed only under a pointer profile: the phone keeps its dot grid.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    itemsByDay: Map<LocalDate, List<MonthItem>>,
    today: LocalDate,
    onItemClick: (MonthItem) -> Unit,
    onItemMove: ((MonthItem, LocalDate) -> Unit)?,
    onDayClick: (LocalDate) -> Unit,
    onGroundClick: ((LocalDate) -> Unit)?,
    onMonthShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** B§13.6 #5 — the cells' root bounds for the tray's drag, and the cell it would land on. */
    onCellBounds: (LocalDate, Rect) -> Unit = { _, _ -> },
    highlightDay: LocalDate? = null,
    firstDay: DayOfWeek = DayOfWeek.MONDAY,
) {
    val days = remember(month, firstDay) { monthGridDays(month, firstDay) }
    val rows = days.size / 7
    val cellBounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    var popoverDay by remember { mutableStateOf<LocalDate?>(null) }
    var popoverAnchor by remember { mutableStateOf<Rect?>(null) }
    // A chip being dragged: root coordinates, the cell under the pointer lights, the ghost follows.
    var drag by remember { mutableStateOf<MonthDrag?>(null) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    val dragTarget: LocalDate? = drag?.let { d -> cellBounds.entries.firstOrNull { it.value.contains(d.position) }?.key?.takeIf { it != d.from } }
    val density = LocalDensity.current
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val targetOutline = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxSize().onGloballyPositioned { gridOrigin = it.boundsInRoot().topLeft }) {
        // The month's own bar: ‹ September 2026 › — the Week's pattern.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthShift(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month") }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${month.year}",
                style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onMonthShift(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month") }
        }
        // The weekday header: seven eyebrows, the first day the grid's.
        Row(modifier = Modifier.fillMaxWidth().height(NUMBER_ROW_DP.dp)) {
            (0..6).forEach { i ->
                val dow = firstDay.plus(i.toLong())
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
            }
        }
        HorizontalDivider(color = hairline)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val cellHeight = maxHeight / rows
            val capacity = remember(cellHeight, density) {
                with(density) { cellCapacity(cellHeight.toPx(), NUMBER_ROW_DP.dp.toPx(), (CHIP_HEIGHT_DP + CHIP_GAP_DP).dp.toPx(), (2 * CELL_INSET_DP).dp.toPx()) }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().height(cellHeight)) {
                        for (c in 0 until 7) {
                            val day = days[r * 7 + c]
                            MonthDay(
                                day = day, month = month, today = today, items = itemsByDay[day].orEmpty(), capacity = capacity,
                                lastColumn = c == 6, lastRow = r == rows - 1, hairline = hairline,
                                lit = highlightDay == day || dragTarget == day, litOutline = targetOutline,
                                onBounds = { cellBounds[day] = it; onCellBounds(day, it) },
                                onItemClick = onItemClick,
                                onDayClick = { onDayClick(day) },
                                onGroundClick = onGroundClick?.let { f -> { f(day) } },
                                onMore = { anchor -> popoverAnchor = anchor; popoverDay = day },
                                dragStart = if (onItemMove != null) { item, pos -> drag = MonthDrag(item, day, pos) } else null,
                                dragMove = { delta -> drag = drag?.let { it.copy(position = it.position + delta) } },
                                dragEnd = {
                                    // Read the live state here: `dragSource`'s pointer block keeps the lambdas it
                                    // started with, so a value captured at composition would be the pre-drag one.
                                    val d = drag
                                    drag = null
                                    val target = d?.let { cellBounds.entries.firstOrNull { e -> e.value.contains(it.position) }?.key?.takeIf { day -> day != it.from } }
                                    if (d != null && target != null && onItemMove != null) onItemMove(d.item, target)
                                },
                                dragCancel = { drag = null },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            // The ghost: the tray's, over everything, naming the cell it would land on.
            drag?.let { d ->
                DragGhost(
                    title = d.item.title, target = dragTarget?.let { "→ " + it.format(GHOST_DAY) },
                    modifier = Modifier.offset { IntOffset((d.position.x - gridOrigin.x).roundToInt() + 12, (d.position.y - gridOrigin.y).roundToInt() - 18) },
                )
            }
        }
    }

    // The *+n* popover: the day's every item, the hover card's frame, anchored under the cell's number row.
    val day = popoverDay
    val anchor = popoverAnchor
    if (day != null && anchor != null) {
        val gapPx = with(density) { 4.dp.roundToPx() }
        val provider = remember(anchor) { UnderAnchor(anchor, gapPx) }
        val close = { popoverDay = null }
        Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = true), onDismissRequest = close) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(POPOVER_WIDTH_DP.dp),
            ) {
                val items = itemsByDay[day].orEmpty()
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text(day.format(POPOVER_DAY), style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f))
                        Text("${items.size}", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items.forEach { item ->
                        OccurrenceChip(title = item.title, tint = item.tint, stripe = item.stripe, time = item.time, onClick = { close(); onItemClick(item) })
                    }
                    HorizontalDivider(color = hairline, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        "Open the day ›", style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).clickable { close(); onDayClick(day) }.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDay(
    day: LocalDate,
    month: YearMonth,
    today: LocalDate,
    items: List<MonthItem>,
    capacity: Int,
    lastColumn: Boolean,
    lastRow: Boolean,
    hairline: Color,
    lit: Boolean,
    litOutline: Color,
    onBounds: (Rect) -> Unit,
    onItemClick: (MonthItem) -> Unit,
    onDayClick: () -> Unit,
    onGroundClick: (() -> Unit)?,
    onMore: (Rect) -> Unit,
    dragStart: ((MonthItem, Offset) -> Unit)?,
    dragMove: (Offset) -> Unit,
    dragEnd: () -> Unit,
    dragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outOfMonth = YearMonth.from(day) != month
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var cellRect by remember { mutableStateOf(Rect.Zero) }
    val (shown, more) = visibleInCell(items, capacity)
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }
    val numberRowPx = with(LocalDensity.current) { (NUMBER_ROW_DP + CELL_INSET_DP).dp.toPx() }
    Box(
        modifier = modifier
            .onGloballyPositioned { cellRect = it.boundsInRoot(); onBounds(cellRect) }
            .drawBehind {
                if (!lastColumn) drawLine(hairline, Offset(size.width, 0f), Offset(size.width, size.height), strokePx)
                if (!lastRow) drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), strokePx)
            }
            .then(if (lit) Modifier.background(MaterialTheme.colorScheme.primaryContainer).border(2.dp, litOutline) else Modifier)
            .hoverable(interaction)
            .pointerInput(onGroundClick, onDayClick) {
                detectTapGestures(onTap = { onGroundClick?.invoke() }, onDoubleTap = { onDayClick() })
            }
            .padding(CELL_INSET_DP.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CHIP_GAP_DP.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(NUMBER_ROW_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                val isToday = day == today
                // Today's disc is Notion's 22 px; the number is the Week header's `body`, left-aligned with the chips.
                Text(
                    dayLabel(day),
                    style = MaterialTheme.typography.body,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        outOfMonth -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.Unspecified
                    },
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .then(if (isToday) Modifier.size(NUMBER_ROW_DP.dp).background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier.height(NUMBER_ROW_DP.dp))
                        .clickable(onClick = onDayClick)
                        .padding(horizontal = if (isToday) 0.dp else 4.dp)
                        .wrapContentSize(Alignment.Center),
                )
                if (hovered && onGroundClick != null) {
                    Text("+", style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
            shown.forEach { item ->
                var chipOrigin by remember(item.key) { mutableStateOf(Offset.Zero) }
                OccurrenceChip(
                    title = item.title, tint = item.tint, stripe = item.stripe, time = item.time,
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .onGloballyPositioned { chipOrigin = it.boundsInRoot().topLeft }
                        .then(
                            if (dragStart != null && item.draggable) Modifier.dragSource(
                                pointer = true, key = item.key,
                                onStart = { dragStart(item, chipOrigin + it) }, onDrag = dragMove, onEnd = dragEnd, onCancel = dragCancel,
                            ) else Modifier
                        ),
                )
            }
            if (more > 0) {
                Text( // type: GRID_DENSE — the Week's "+n", a cell's last line
                    "+$more", style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = CHIP_HEIGHT_DP.dp).clickable { onMore(Rect(cellRect.left, cellRect.top, cellRect.right, cellRect.top + numberRowPx)) }.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** A chip lifted off its cell, in root coordinates. */
private data class MonthDrag(val item: MonthItem, val from: LocalDate, val position: Offset)

private val GHOST_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
private val POPOVER_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
