package com.tendril.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.Entry
import com.tendril.app.domain.plan.TrayTasks
import com.tendril.app.domain.urgency.urgencyOf
import com.tendril.app.ui.components.UrgencyStripe
import com.tendril.app.ui.components.dragSource
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.nav.TOP_BAR_HEIGHT
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label

/**
 * B§13.6 #5 — the Calendar's task tray: the Plan rail grown into a pane beside the week
 * (Sunsama's, Akiflow's backlog; decided 2026-09-16 on `docs/mockups/drag-between-panes.html`).
 * Two sections, *Unscheduled* and *Overdue* (`trayTasks`), one-line chips (the profile's row height) wearing the urgency
 * stripe, a hint at the foot. A chip dragged out is the screen's drag ([onDragStart] with the
 * chip's root position); the chip stays, faded, until the drop lands. The tray is also a
 * target — a grid block over it lights the ground ([dropHere]) and a drop clears its When.
 * Under a pointer the drag starts on press-and-move, under Touch on a long press (`dragSource`).
 */
@Composable
internal fun TaskTray(
    tasks: TrayTasks,
    today: LocalDate,
    showUrgency: Boolean,
    draggingId: Long?,
    dropHere: Boolean,
    onDragStart: (Entry, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onOpen: (Entry) -> Unit,
    onCollapse: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ground = if (dropHere) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(ground)
            .then(if (dropHere) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tasks", style = MaterialTheme.typography.heading, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Hide the tray", modifier = Modifier.size(18.dp)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            SectionLabel("Unscheduled · ${tasks.unscheduled.size}")
            if (tasks.unscheduled.isEmpty()) {
                Text("Nothing unscheduled", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }
            tasks.unscheduled.forEach { entry ->
                TrayChip(entry, today, showUrgency, dragging = entry.id == draggingId, dueLabel = null, onDragStart, onDrag, onDragEnd, onDragCancel, onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp))
            }
            if (tasks.overdue.isNotEmpty()) {
                SectionLabel("Overdue · ${tasks.overdue.size}")
                tasks.overdue.forEach { entry ->
                    // The date in the dim colour: the stripe already says how urgent (14g·3).
                    TrayChip(entry, today, showUrgency, dragging = entry.id == draggingId, dueLabel = "due " + entry.startDate!!.format(DAY_FORMAT), onDragStart, onDrag, onDragEnd, onDragCancel, onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            "Drag a task onto a day, or onto an hour. Drop a block here to clear its When.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * The tray under Touch: a collapsible strip under the Week strip and the Month grid (the Day
 * view keeps its Plan rail, which also places a time). One horizontally scrolling row of 40 dp
 * chips — Unscheduled, then Overdue after a divider; the header line alone when collapsed.
 */
@Composable
internal fun TaskTrayStrip(
    tasks: TrayTasks,
    today: LocalDate,
    showUrgency: Boolean,
    draggingId: Long?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onDragStart: (Entry, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onOpen: (Entry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Unscheduled · ${tasks.unscheduled.size}" + if (tasks.overdue.isNotEmpty()) "   Overdue · ${tasks.overdue.size}" else "",
                style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            Icon(if (collapsed) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (collapsed) "Show the tray" else "Hide the tray", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!collapsed) {
            if (tasks.isEmpty) {
                Text("Nothing unscheduled", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tasks.unscheduled.forEach { entry ->
                        TrayChip(entry, today, showUrgency, dragging = entry.id == draggingId, dueLabel = null, onDragStart, onDrag, onDragEnd, onDragCancel, onOpen, height = 40.dp)
                    }
                    if (tasks.unscheduled.isNotEmpty() && tasks.overdue.isNotEmpty()) {
                        Box(modifier = Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
                    }
                    tasks.overdue.forEach { entry ->
                        TrayChip(entry, today, showUrgency, dragging = entry.id == draggingId, dueLabel = "due " + entry.startDate!!.format(DAY_FORMAT), onDragStart, onDrag, onDragEnd, onDragCancel, onOpen, height = 40.dp)
                    }
                }
            }
        }
    }
}

/** One tray card; the drag source. A plain click opens the task (its edit sheet), as a block's does. */
@Composable
private fun TrayChip(
    entry: Entry,
    today: LocalDate,
    showUrgency: Boolean,
    dragging: Boolean,
    dueLabel: String?,
    onDragStart: (Entry, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onOpen: (Entry) -> Unit,
    modifier: Modifier = Modifier,
    // The profile's one-line row (Compact 32) since the tray PR; the Touch strip passes its own 40.
    height: androidx.compose.ui.unit.Dp = LocalDensityProfile.current.rowHeightDp.dp,
) {
    val pointer = LocalDensityProfile.current.pointer
    var origin by remember(entry.id) { mutableStateOf(Offset.Zero) }
    // The gesture detector is keyed on the entry, not on the lambdas (re-keying would cancel a
    // drag mid-way), so the callbacks it holds must read the latest ones.
    val start = rememberUpdatedState(onDragStart)
    val move = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onDragEnd)
    val cancel = rememberUpdatedState(onDragCancel)
    Row(
        modifier = modifier
            .height(height)
            .alpha(if (dragging) 0.35f else 1f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clickable { onOpen(entry) }
            .dragSource(pointer, entry.id, onStart = { start.value(entry, origin + it) }, onDrag = { move.value(it) }, onEnd = { end.value() }, onCancel = { cancel.value() })
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showUrgency) UrgencyStripe(urgencyOf(entry, today), height = 20.dp)
        Text(entry.title, style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = if (height > 36.dp) Modifier else Modifier.weight(1f))
        if (dueLabel != null) {
            Text(dueLabel, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** The ghost that follows a tray or Timeline drag: the chip's shape on `surface`, a shadow, the target named. */
@Composable
fun DragGhost(title: String, target: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .height(36.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (target != null) Text(target, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp))
}

/** The overdue row's date and the ghost's target, one format. */
internal val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
