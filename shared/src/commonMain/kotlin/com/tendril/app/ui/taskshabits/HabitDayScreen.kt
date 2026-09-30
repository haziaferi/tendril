package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.plan.DayBlock
import com.tendril.app.domain.plan.DayRow
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.EditPos
import com.tendril.app.domain.plan.displayedGroups
import com.tendril.app.ui.components.keyboardCursorRing
import com.tendril.app.ui.components.keyboardCursorShown
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.plan_any_time
import com.tendril.app.generated.resources.plan_any_time_today
import com.tendril.app.generated.resources.plan_done_on_day
import com.tendril.app.generated.resources.plan_done_today
import com.tendril.app.generated.resources.plan_next_day
import com.tendril.app.generated.resources.plan_note_outside_after
import com.tendril.app.generated.resources.plan_note_outside_before
import com.tendril.app.generated.resources.plan_note_overlap
import com.tendril.app.generated.resources.plan_now
import com.tendril.app.generated.resources.plan_outside_blocks
import com.tendril.app.generated.resources.plan_previous_day
import com.tendril.app.generated.resources.plan_projected
import com.tendril.app.generated.resources.plan_today
import com.tendril.app.generated.resources.plan_no_area
import com.tendril.app.generated.resources.plan_and_more
import com.tendril.app.generated.resources.plan_and_more_day
import com.tendril.app.generated.resources.plan_more
import com.tendril.app.generated.resources.plan_edit_blocks
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.TendrilMenuItem
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.hueColours
import com.tendril.app.ui.theme.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * §6.3 (plan Phase 5a) — the Habits tab's Day view by time, direction A as approved (R1, R2):
 * each block a heading — its hue as a small mark beside the name (the one place a block's colour
 * appears), its range, and *· now* on the current one — then its entries, set times first; every
 * entry the app's habit row, one line: the check, the title, a set time as a dim number at the end.
 * An interval habit sits in its block like any other (V2). The two structural notes are neutral
 * lines at the foot (D4). Only today's entries can be checked: a past day is read (read-only), a
 * later one has not happened yet; an interval habit ahead is its expected day, in italics (T3).
 */
@Composable
fun HabitDayList(
    view: HabitDayView,
    today: LocalDate,
    nowMinute: Int,
    onShowDay: (LocalDate) -> Unit,
    onCheckIn: (DayRow) -> Unit,
    onUndo: (DayRow) -> Unit,
    onRowTap: (DayRow) -> Unit,
    selectedHabitId: Long?,
    modifier: Modifier = Modifier,
    /** 5b — the entry in edit mode and its pending place: drawn there, and nowhere else. */
    editing: Pair<DayRow, EditPos>? = null,
    /** 5c — the header's `···`: *Edit blocks* (the mockup's place for it). */
    onEditBlocks: () -> Unit = {},
    /** 5d — the keyboard's cursor on the desktop (14e's model): the row it rests on. */
    cursor: DayRow? = null,
) {
    val isToday = view.date == today
    val blockNames = view.blocks.associate { it.block.uid to blockName(it.block) }
    val groups = editing?.let { (row, pos) -> displayedGroups(view, row, pos) }
    val shownAnyTime = groups?.getOrNull(view.blocks.size)?.flexible ?: view.anyTime
    val shownOutside = groups?.getOrNull(view.blocks.size)?.timed ?: view.outside
    fun isSelected(r: DayRow) = if (editing != null) r == editing.first else r.habit.id == selectedHabitId
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "day_header") { DayHeader(view, isToday, onShowDay, today, onEditBlocks) }
        view.blocks.forEachIndexed { i, b ->
            item(key = "block_" + b.block.uid) { BlockHeading(b, blockNames.getValue(b.block.uid), now = isToday && nowMinute >= b.start && nowMinute < b.end) }
            val rows = groups?.get(i)?.let { it.timed + it.flexible } ?: (b.timed + b.flexible)
            items(rows, key = { "row_" + it.habit.id + "_" + it.key }) { row ->
                PlanRow(row, canCheck = isToday, selected = isSelected(row), onCheckIn, onUndo, onRowTap, keyFocused = row == cursor)
            }
        }
        if (shownAnyTime.isNotEmpty()) {
            item(key = "any_time") { GroupHeading(stringResource(if (isToday) Res.string.plan_any_time_today else Res.string.plan_any_time)) }
            items(shownAnyTime, key = { "row_" + it.habit.id + "_" + it.key }) { row ->
                PlanRow(row, canCheck = isToday, selected = isSelected(row), onCheckIn, onUndo, onRowTap, keyFocused = row == cursor)
            }
        }
        // D4 — an entry whose set time is in no block is kept, under its own heading (the walk: without
        // one it read as an *Any time* entry), with its reason beneath it.
        if (shownOutside.isNotEmpty()) item(key = "outside") { GroupHeading(stringResource(Res.string.plan_outside_blocks)) }
        items(shownOutside, key = { "row_" + it.habit.id + "_" + it.key }) { row ->
            Column {
                PlanRow(row, canCheck = isToday, selected = isSelected(row), onCheckIn, onUndo, onRowTap, keyFocused = row == cursor)
                OutsideNote(row, view.blocks, blockNames)
            }
        }
        items(view.overlaps, key = { "overlap_" + it.first + "_" + it.second }) { o ->
            val a = view.blocks.first { it.block.uid == o.first }
            val b = view.blocks.first { it.block.uid == o.second }
            NeutralNote(stringResource(Res.string.plan_note_overlap, blockNames.getValue(a.block.uid), span(a.start, a.end), blockNames.getValue(b.block.uid), span(b.start, b.end), duration(a.end - b.start)))
        }
    }
}

@Composable
internal fun DayHeader(view: HabitDayView, isToday: Boolean, onShowDay: (LocalDate) -> Unit, today: LocalDate, onEditBlocks: () -> Unit) {
    val title = view.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShowDay(view.date.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.plan_previous_day))
            }
            Text(title.replaceFirstChar { it.titlecase(Locale.getDefault()) }, style = MaterialTheme.typography.heading, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) // type: HEADER_DATE — the shown day
            IconButton(onClick = { onShowDay(view.date.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.plan_next_day))
            }
            Spacer(Modifier.weight(1f))
            if (!isToday) TextButton(onClick = { onShowDay(today) }) { Text(stringResource(Res.string.plan_today)) }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(Res.string.plan_more)) }
                TendrilMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    TendrilMenuItem(text = { Text(stringResource(Res.string.plan_edit_blocks)) }, onClick = { menu = false; onEditBlocks() })
                }
            }
        }
        // R3, as drawn — presence counted up; a day with nothing done says nothing rather than "0".
        if (view.doneCount > 0) {
            Text( // type: META — the day's presence
                if (isToday) pluralStringResource(Res.plurals.plan_done_today, view.doneCount, view.doneCount) else pluralStringResource(Res.plurals.plan_done_on_day, view.doneCount, view.doneCount),
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** R2 — the block's hue appears here and nowhere else: a small mark beside its name. */
@Composable
private fun BlockHeading(b: DayBlock, name: String, now: Boolean) {
    val palette = LocalTendrilPalette.current
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(b.block.hue?.let { hueColours(it, palette).hue } ?: MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.label) // type: GROUP_HEADER — the block's name
        Spacer(Modifier.width(8.dp))
        Text( // type: META — the block's range, and the current block marked
            span(b.start, b.end) + if (now) " · " + stringResource(Res.string.plan_now) else "",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(text, style = MaterialTheme.typography.label, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) // type: GROUP_HEADER — Any time
}

/** R1 — the app's own habit row: the check leading, the title, a set time dim at the end; one line. */
@Composable
private fun PlanRow(row: DayRow, canCheck: Boolean, selected: Boolean, onCheckIn: (DayRow) -> Unit, onUndo: (DayRow) -> Unit, onTap: (DayRow) -> Unit, keyFocused: Boolean = false) {
    val profile = LocalDensityProfile.current
    val height = if (profile.pointer) profile.rowHeightDp else profile.rowHeightDp - 8
    val time = row.time?.let(::clock)
    val projected = if (row.projected) stringResource(Res.string.plan_projected) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .clickable { onTap(row) }
            .heightIn(min = height.dp)
            .keyboardCursorRing(keyFocused && keyboardCursorShown(), 6)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = listOfNotNull(row.habit.title, time, projected).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HabitCheck(row.habit, doneToday = row.done, onCheckIn = { onCheckIn(row) }, onUndo = { onUndo(row) }, enabled = canCheck && !row.projected)
        Text(
            row.habit.title,
            style = MaterialTheme.typography.body.let { if (row.projected) it.copy(fontStyle = FontStyle.Italic) else it },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        ) // type: TITLE — the habit
        if (projected != null) Text(projected, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — an expected day (T3), said in words, not colour
        if (time != null) Text(time, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — the set time
    }
}

/** D4 — why an entry is in no block, and the two ways out; neutral, never red. */
@Composable
private fun OutsideNote(row: DayRow, blocks: List<DayBlock>, names: Map<String, String>) {
    val t = row.time ?: return
    val first = blocks.minByOrNull { it.start } ?: return
    val last = blocks.maxByOrNull { it.end } ?: return
    NeutralNote(
        if (t < first.start) stringResource(Res.string.plan_note_outside_before, row.habit.title, clock(t), names.getValue(first.block.uid), clock(first.start))
        else stringResource(Res.string.plan_note_outside_after, row.habit.title, clock(t), names.getValue(last.block.uid), clock(last.end)),
    )
}

@Composable
private fun NeutralNote(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: EXPLAINER — a structural note (D4)
    }
}

/** Minutes from midnight as the day's clock: `06:30`; 24:00 stays 24:00, the end of the last block. */
internal fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private fun span(start: Int, end: Int) = clock(start) + "–" + clock(end)

private fun duration(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

/** The day's rows in the order drawn — blocks (set times, then the rest), *Any time*, *Outside* — for the keyboard's cursor. */
internal fun drawnRows(view: HabitDayView, editing: Pair<DayRow, EditPos>?): List<DayRow> {
    val groups = editing?.let { (row, pos) -> displayedGroups(view, row, pos) }
    val blocks = view.blocks.indices.flatMap { i -> groups?.get(i)?.let { it.timed + it.flexible } ?: (view.blocks[i].timed + view.blocks[i].flexible) }
    val last = groups?.getOrNull(view.blocks.size)
    return blocks + (last?.flexible ?: view.anyTime) + (last?.timed ?: view.outside)
}

/**
 * §6.3 (5d) — the Day view by area (V1): its Labels as the planner's areas (P4), *No area* last; a
 * habit several times a day is one row — its next entry not done, *and N more today* (R3, as drawn).
 * The check checks in the entry the row stands for; a tap opens the habit (edit mode is by time).
 */
@Composable
fun HabitAreaList(
    view: HabitDayView,
    today: LocalDate,
    groups: List<com.tendril.app.domain.plan.AreaGroup>,
    labelNames: Map<Long, String>,
    onShowDay: (LocalDate) -> Unit,
    onCheckIn: (DayRow) -> Unit,
    onUndo: (DayRow) -> Unit,
    onOpen: (com.tendril.app.data.habit.Habit) -> Unit,
    onEditBlocks: () -> Unit,
    selectedHabitId: Long?,
    modifier: Modifier = Modifier,
) {
    val isToday = view.date == today
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "day_header") { DayHeader(view, isToday, onShowDay, today, onEditBlocks) }
        for (g in groups) {
            item(key = "area_" + g.labelId) { GroupHeading(g.labelId?.let { labelNames[it] } ?: stringResource(Res.string.plan_no_area)) }
            items(g.rows, key = { "area_row_" + it.habit.id }) { a ->
                val row = a.shown
                val more = if (a.more > 0) (if (isToday) pluralStringResource(Res.plurals.plan_and_more, a.more, a.more) else pluralStringResource(Res.plurals.plan_and_more_day, a.more, a.more)) else null
                val profile = LocalDensityProfile.current
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .then(if (row.habit.id == selectedHabitId) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable { onOpen(row.habit) }
                        .heightIn(min = (if (profile.pointer) profile.rowHeightDp else profile.rowHeightDp - 8).dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HabitCheck(row.habit, doneToday = row.done, onCheckIn = { onCheckIn(row) }, onUndo = { onUndo(row) }, enabled = isToday && !row.projected)
                    Text(row.habit.title, style = MaterialTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) // type: TITLE — the habit
                    val meta = listOfNotNull(row.time?.let(::clock), more).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — its next time, and how many more
                }
            }
        }
    }
}

