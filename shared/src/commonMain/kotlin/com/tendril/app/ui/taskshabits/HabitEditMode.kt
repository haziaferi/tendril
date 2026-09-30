package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tendril.app.data.habit.Habit
import com.tendril.app.domain.plan.DayRow
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.MovePlan
import com.tendril.app.domain.plan.ScopeChoice
import com.tendril.app.domain.plan.timeFitsDay
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.plan_apply
import com.tendril.app.generated.resources.plan_choose_days
import com.tendril.app.generated.resources.plan_done
import com.tendril.app.generated.resources.plan_down
import com.tendril.app.generated.resources.plan_edit
import com.tendril.app.generated.resources.plan_err_time_format
import com.tendril.app.generated.resources.plan_err_time_outside
import com.tendril.app.generated.resources.plan_more
import com.tendril.app.generated.resources.plan_move_block
import com.tendril.app.generated.resources.plan_move_day
import com.tendril.app.generated.resources.plan_move_title
import com.tendril.app.generated.resources.plan_move_to
import com.tendril.app.generated.resources.plan_move_to_trash
import com.tendril.app.generated.resources.plan_move_weekday_from
import com.tendril.app.generated.resources.plan_put_back
import com.tendril.app.generated.resources.plan_remove_time
import com.tendril.app.generated.resources.plan_remove_time_body
import com.tendril.app.generated.resources.plan_remove_time_title
import com.tendril.app.generated.resources.plan_scope_day
import com.tendril.app.generated.resources.plan_scope_from
import com.tendril.app.generated.resources.plan_scope_occurrence
import com.tendril.app.generated.resources.plan_scope_title
import com.tendril.app.generated.resources.plan_scope_week
import com.tendril.app.generated.resources.plan_scope_weekdays_from
import com.tendril.app.generated.resources.plan_scope_weekdays_week
import com.tendril.app.generated.resources.plan_set_time
import com.tendril.app.generated.resources.plan_skip
import com.tendril.app.generated.resources.plan_pause
import com.tendril.app.generated.resources.plan_keys_move
import com.tendril.app.generated.resources.plan_key_move_to
import com.tendril.app.generated.resources.plan_key_edit
import com.tendril.app.generated.resources.plan_key_done
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import com.tendril.app.generated.resources.plan_time_body
import com.tendril.app.generated.resources.plan_time_field
import com.tendril.app.generated.resources.plan_time_hint
import com.tendril.app.generated.resources.plan_time_title
import com.tendril.app.generated.resources.plan_up
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.TendrilMenuItem
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * §6.3 (plan Phase 5b) — edit mode (V3): a tap selects an entry and docks the toolbar at the foot —
 * Up, Down, Move to…, Edit, More… (Skip this one…, Move to Trash), Done. Up and Down move the entry
 * to a pending place, drawn there; *Done* works out the net move and asks what it must — a time,
 * whether to remove one, the scope (D10) — then writes. Declining any question puts the entry back.
 * Every prompt is a `TendrilSheet`: a bottom sheet on the phone, the slide-over on a wide window.
 */

/** The question on screen, one at a time. */
private sealed interface EditPrompt {
    data class Time(val plan: MovePlan) : EditPrompt
    data class RemoveTime(val plan: MovePlan) : EditPrompt
    data class Scope(val choices: List<ScopeChoice>, val date: LocalDate, val apply: (ScopeChoice, Set<DayOfWeek>?) -> Unit) : EditPrompt
    data class MoveTo(val row: DayRow) : EditPrompt
    data class MoveRemoveTime(val row: DayRow, val target: LocalDate, val blockUid: String) : EditPrompt
    data class MoveWeekly(val row: DayRow, val target: LocalDate, val blockUid: String, val removeTime: Boolean) : EditPrompt
}

/**
 * §6.3 (5a, 5b) — the Day view with its edit mode. [wide]: on a wide window a tap also shows the
 * habit in the detail pane, where the phone keeps it for *Edit* (T4). [onTrash] is P1's whole-habit
 * Move to Trash.
 */
@Composable
internal fun HabitDayContent(
    viewModel: TasksHabitsViewModel,
    wide: Boolean,
    onOpen: (Habit) -> Unit,
    onTrash: (Habit) -> Unit,
    selectedHabitId: Long?,
    /** 5d — the Day view by area instead of by time; [labels] name its groups. */
    byArea: Boolean = false,
    labels: List<com.tendril.app.data.page.Label> = emptyList(),
) {
    val view by viewModel.dayView.collectAsState()
    val selection by viewModel.editSelection.collectAsState()
    val now by remember { com.tendril.app.domain.track.minuteTicker() }.collectAsState(initial = java.time.Instant.now())
    var prompt by remember { mutableStateOf<EditPrompt?>(null) }
    var pauseTarget by remember { mutableStateOf<Habit?>(null) }
    var editBlocks by remember { mutableStateOf(false) }
    val blocks by viewModel.blocks.collectAsState()
    var cursor by remember { mutableStateOf(-1) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val v = view ?: return
    val sel = selection
    val editing = sel?.let { s -> viewModel.selectedRow(v, s)?.let { it to s.pos } }
    val local = now.atZone(java.time.ZoneId.systemDefault())

    fun putBack() { prompt = null; viewModel.cancelEdit() }
    fun settle(plan: MovePlan, time: Int?) {
        val choices = viewModel.moveChoicesFor(plan, time)
        prompt = if (choices.size <= 1) {
            viewModel.commitMove(plan, time, choices.firstOrNull(), null); null
        } else EditPrompt.Scope(choices, v.date) { c, d -> prompt = null; viewModel.commitMove(plan, time, c, d) }
    }
    fun done() {
        val plan = viewModel.pendingMove() ?: return viewModel.cancelEdit()
        when {
            plan.askTime -> prompt = EditPrompt.Time(plan)
            plan.askRemoveTime -> prompt = EditPrompt.RemoveTime(plan)
            else -> settle(plan, null)
        }
    }
    fun weeklyOrOnce(row: DayRow, target: LocalDate, blockUid: String, removeTime: Boolean) {
        prompt = if (viewModel.canMoveSelectedWeekly(row)) EditPrompt.MoveWeekly(row, target, blockUid, removeTime)
        else { viewModel.commitMoveToDay(row, target, blockUid, removeTime, weekly = false); null }
    }

    if (byArea) {
        val sorted = labels.sortedBy { it.name.lowercase() }
        HabitAreaList(
            view = v, today = local.toLocalDate(),
            groups = com.tendril.app.domain.plan.byArea(v, sorted.map { it.id }), labelNames = sorted.associate { it.id to it.name },
            onShowDay = viewModel::showDay,
            onCheckIn = { viewModel.checkInOccurrence(it.habit.id, it.key) },
            onUndo = { viewModel.undoOccurrence(it.habit.id, it.key) },
            onOpen = onOpen,
            onEditBlocks = { editBlocks = true },
            selectedHabitId = selectedHabitId,
        )
        if (editBlocks) BlocksEditorSheet(blocks = blocks, onDismiss = { editBlocks = false }, onSaveTimes = viewModel::saveBlockTimes, onRename = viewModel::renameBlock, onOverrides = viewModel::setBlockOverrides)
        return
    }
    val drawn = drawnRows(v, editing)
    // 5d — the 14e keyboard model on the Day view: ↑ ↓ move the cursor and ↵ selects; in edit mode
    // ↑ ↓ move the entry, Ctrl+M is Move to…, ↵ Edit, Esc Done (a bare letter is the list's type-ahead).
    fun onKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || prompt != null) return false
        val key = e.key
        val row = editing?.first
        return when {
            row != null && key == androidx.compose.ui.input.key.Key.DirectionUp -> { viewModel.moveSelectedUp(); true }
            row != null && key == androidx.compose.ui.input.key.Key.DirectionDown -> { viewModel.moveSelectedDown(); true }
            row != null && key == androidx.compose.ui.input.key.Key.M && (e.isCtrlPressed || e.isMetaPressed) -> { prompt = EditPrompt.MoveTo(row); true }
            row != null && key == androidx.compose.ui.input.key.Key.Enter -> { onOpen(row.habit); true }
            row != null && key == androidx.compose.ui.input.key.Key.Escape -> { done(); true }
            row == null && key == androidx.compose.ui.input.key.Key.DirectionDown -> { cursor = (cursor + 1).coerceAtMost(drawn.size - 1); true }
            row == null && key == androidx.compose.ui.input.key.Key.DirectionUp -> { cursor = (cursor - 1).coerceAtLeast(0); true }
            row == null && key == androidx.compose.ui.input.key.Key.Enter && cursor in drawn.indices && !v.readOnly -> {
                viewModel.select(drawn[cursor]); if (wide) onOpen(drawn[cursor].habit); true
            }
            else -> false
        }
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent(::onKey)
            .focusable(),
    ) {
        HabitDayList(
            view = v,
            today = local.toLocalDate(),
            nowMinute = local.toLocalTime().toSecondOfDay() / 60,
            onShowDay = { viewModel.cancelEdit(); viewModel.showDay(it) },
            onCheckIn = { viewModel.checkInOccurrence(it.habit.id, it.key) },
            onUndo = { viewModel.undoOccurrence(it.habit.id, it.key) },
            onRowTap = { r ->
                when {
                    v.readOnly -> onOpen(r.habit)
                    editing != null -> done() // a tap on any entry while one is selected is *Done*
                    else -> { viewModel.select(r); cursor = drawn.indexOf(r); if (wide) onOpen(r.habit) }
                }
                runCatching { focus.requestFocus() }
            },
            selectedHabitId = selectedHabitId,
            editing = editing,
            onEditBlocks = { viewModel.cancelEdit(); editBlocks = true },
            cursor = if (editing == null) drawn.getOrNull(cursor) else null,
            modifier = Modifier.weight(1f),
        )
        editing?.let { (row, _) ->
            EditToolbar(
                row = row,
                onUp = viewModel::moveSelectedUp,
                onDown = viewModel::moveSelectedDown,
                onMoveTo = { prompt = EditPrompt.MoveTo(row) },
                onEdit = { onOpen(row.habit) },
                onSkip = viewModel.skipChoicesFor(row).takeIf { it.isNotEmpty() }?.let { choices ->
                    {
                        if (choices.size == 1) viewModel.commitSkip(row, choices.single(), null)
                        else prompt = EditPrompt.Scope(choices, v.date) { c, d -> prompt = null; viewModel.commitSkip(row, c, d) }
                    }
                },
                onPause = { viewModel.cancelEdit(); pauseTarget = row.habit },
                onTrash = { viewModel.cancelEdit(); onTrash(row.habit) },
                onDone = ::done,
            )
        }
    }

    when (val p = prompt) {
        is EditPrompt.Time -> TimePrompt(v, p.plan, target = sel?.pos?.group?.let(v.blocks::getOrNull), onPutBack = ::putBack, onSet = { t -> settle(p.plan, t) })
        is EditPrompt.RemoveTime -> RemoveTimePrompt(p.plan.row, onPutBack = ::putBack, onRemove = { settle(p.plan, null) })
        is EditPrompt.Scope -> ScopePrompt(p.choices, p.date, onPutBack = ::putBack, onApply = p.apply)
        is EditPrompt.MoveTo -> MoveToPrompt(v, p.row, local.toLocalDate(), onPutBack = ::putBack) { day, blockUid ->
            if (day == v.date) {
                prompt = null
                viewModel.placeSelectedAtEndOf(blockUid)
                done()
            } else {
                val t = p.row.time
                if (t != null && !viewModel.timeFitsBlockOn(day, blockUid, t)) prompt = EditPrompt.MoveRemoveTime(p.row, day, blockUid)
                else weeklyOrOnce(p.row, day, blockUid, removeTime = false)
            }
        }
        is EditPrompt.MoveRemoveTime -> RemoveTimePrompt(p.row, onPutBack = ::putBack, onRemove = { weeklyOrOnce(p.row, p.target, p.blockUid, removeTime = true) })
        is EditPrompt.MoveWeekly -> WeeklyPrompt(p.target, onPutBack = ::putBack) { weekly ->
            prompt = null
            viewModel.commitMoveToDay(p.row, p.target, p.blockUid, p.removeTime, weekly)
        }
        null -> Unit
    }
    pauseTarget?.let { h ->
        PauseSheet(h, today = local.toLocalDate(), onDismiss = { pauseTarget = null }) { from, until -> viewModel.pauseHabit(h.id, from, until) }
    }
    if (editBlocks) BlocksEditorSheet(
        blocks = blocks,
        onDismiss = { editBlocks = false },
        onSaveTimes = viewModel::saveBlockTimes,
        onRename = viewModel::renameBlock,
        onOverrides = viewModel::setBlockOverrides,
    )
}

/** V3 — docked at the foot; the check keeps its own hit area on the row. */
@Composable
private fun EditToolbar(row: DayRow, onUp: () -> Unit, onDown: () -> Unit, onMoveTo: () -> Unit, onEdit: () -> Unit, onSkip: (() -> Unit)?, onPause: () -> Unit, onTrash: () -> Unit, onDone: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val keys = com.tendril.app.ui.nav.LocalDensityProfile.current.pointer
                ToolButton(Icons.Filled.KeyboardArrowUp, Res.string.plan_up, onUp, if (keys) Res.string.plan_keys_move else null)
                ToolButton(Icons.Filled.KeyboardArrowDown, Res.string.plan_down, onDown, if (keys) Res.string.plan_keys_move else null)
                ToolButton(Icons.Filled.SwapHoriz, Res.string.plan_move_to, onMoveTo, if (keys) Res.string.plan_key_move_to else null)
                ToolButton(Icons.Filled.Edit, Res.string.plan_edit, onEdit, if (keys) Res.string.plan_key_edit else null)
                Box {
                    ToolButton(Icons.Outlined.MoreHoriz, Res.string.plan_more, onClick = { more = true })
                    TendrilMenu(expanded = more, onDismissRequest = { more = false }) {
                        if (onSkip != null) TendrilMenuItem(text = { Text(stringResource(Res.string.plan_skip)) }, onClick = { more = false; onSkip() })
                        TendrilMenuItem(text = { Text(stringResource(Res.string.plan_pause)) }, onClick = { more = false; onPause() })
                        TendrilMenuItem(text = { Text(stringResource(Res.string.plan_move_to_trash)) }, onClick = { more = false; onTrash() })
                    }
                }
                ToolButton(Icons.Filled.Check, Res.string.plan_done, onDone, if (keys) Res.string.plan_key_done else null)
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: StringResource, onClick: () -> Unit, hint: StringResource? = null) {
    val text = stringResource(label)
    Column(
        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = text, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.caption) // type: ICON_LABEL — the tool's name under its icon
        if (hint != null) Text(stringResource(hint), style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: ICON_LABEL — its key (the desktop, as drawn)
    }
}

/** Two buttons at a prompt's foot: the way out that puts the entry back, worded as that, and the act. */
@Composable
private fun PromptButtons(act: String, enabled: Boolean = true, onPutBack: () -> Unit, onAct: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onPutBack) { Text(stringResource(Res.string.plan_put_back)) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAct, enabled = enabled) { Text(act) }
    }
}

private val HHMM = Regex("(\\d{1,2}):?(\\d{2})")

/** The planner's `askTime`: no suggestion; the time must fall in one of the day's blocks. */
@Composable
private fun TimePrompt(view: HabitDayView, plan: MovePlan, target: com.tendril.app.domain.plan.DayBlock?, onPutBack: () -> Unit, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = view.blocks.minOfOrNull { it.start } ?: 0
    val last = view.blocks.maxOfOrNull { it.end } ?: 1440
    val errFormat = stringResource(Res.string.plan_err_time_format)
    val home = target ?: view.blocks.firstOrNull()
    TendrilSheet(onDismiss = onPutBack, title = stringResource(Res.string.plan_time_title, plan.row.habit.title)) {
        if (home != null) Text(stringResource(Res.string.plan_time_body, plan.row.habit.title, blockName(home.block), clock(home.start), clock(home.end)), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: EXPLAINER — why a time is asked
        Spacer(Modifier.size(12.dp))
        Text(stringResource(Res.string.plan_time_field), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the field's name
        TendrilField(value = text, onValueChange = { text = it; error = null }, placeholder = stringResource(Res.string.plan_time_hint), modifier = Modifier.fillMaxWidth())
        val outside = error
        if (outside != null) Text(outside, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error) // type: ERROR_LINE — the typed time refused
        val errOutsideTemplate = stringResource(Res.string.plan_err_time_outside, "%s", clock(first), clock(last))
        PromptButtons(stringResource(Res.string.plan_set_time), onPutBack = onPutBack) {
            val m = HHMM.matchEntire(text.trim())
            val minute = m?.let { val h = it.groupValues[1].toInt(); val mi = it.groupValues[2].toInt(); if (h < 24 && mi < 60) h * 60 + mi else null }
            when {
                minute == null -> error = errFormat
                !timeFitsDay(view, minute) -> error = errOutsideTemplate.replace("%s", clock(minute))
                else -> onSet(minute)
            }
        }
    }
}

@Composable
private fun RemoveTimePrompt(row: DayRow, onPutBack: () -> Unit, onRemove: () -> Unit) {
    val time = row.time?.let(::clock) ?: ""
    TendrilSheet(onDismiss = onPutBack, title = stringResource(Res.string.plan_remove_time_title, row.habit.title)) {
        Text(stringResource(Res.string.plan_remove_time_body, row.habit.title, time), style = MaterialTheme.typography.body) // type: BODY_LINE — what removing the time means
        PromptButtons(stringResource(Res.string.plan_remove_time, time), onPutBack = onPutBack, onAct = onRemove)
    }
}

private fun ScopeChoice.label(): StringResource = when (this) {
    ScopeChoice.ONLY_THIS_ENTRY -> Res.string.plan_scope_occurrence
    ScopeChoice.THIS_DAY -> Res.string.plan_scope_day
    ScopeChoice.THIS_WEEK -> Res.string.plan_scope_week
    ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK -> Res.string.plan_scope_weekdays_week
    ScopeChoice.FROM_NOW_ON -> Res.string.plan_scope_from
    ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON -> Res.string.plan_scope_weekdays_from
}

private val ScopeChoice.pickDays get() = this == ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK || this == ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON

/** D10 — "Apply this change to…", the identical choices already hidden; a weekday choice opens its weekday row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopePrompt(choices: List<ScopeChoice>, date: LocalDate, onPutBack: () -> Unit, onApply: (ScopeChoice, Set<DayOfWeek>?) -> Unit) {
    var chosen by remember { mutableStateOf(choices.first()) }
    var days by remember { mutableStateOf(setOf(date.dayOfWeek)) }
    var error by remember { mutableStateOf(false) }
    TendrilSheet(onDismiss = onPutBack, title = stringResource(Res.string.plan_scope_title)) {
        for (c in choices) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.RadioButton) { chosen = c; error = false }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = chosen == c, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(c.label()), style = MaterialTheme.typography.body) // type: BODY_LINE — a scope
            }
            if (c.pickDays && chosen == c) {
                FlowRow(modifier = Modifier.padding(start = 40.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in DayOfWeek.entries) {
                        // "this week" cannot reach a day already past; "from now on" can name any weekday
                        val inert = c == ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK && d < date.dayOfWeek
                        FilterChip(
                            selected = d in days,
                            enabled = !inert,
                            onClick = { days = if (d in days) days - d else days + d; error = false },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
            }
        }
        if (error) Text(stringResource(Res.string.plan_choose_days), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error) // type: ERROR_LINE — no weekday chosen
        PromptButtons(stringResource(Res.string.plan_apply), onPutBack = onPutBack) {
            if (chosen.pickDays && days.isEmpty()) error = true
            else onApply(chosen, if (chosen.pickDays) days else null)
        }
    }
}

/** *Move to…*: a day of this week not yet past (T2) for a calendar habit, and a block. An interval habit moves among blocks only — it falls due by its interval. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveToPrompt(view: HabitDayView, row: DayRow, today: LocalDate, onPutBack: () -> Unit, onApply: (LocalDate, String) -> Unit) {
    val monday = view.date.minusDays((view.date.dayOfWeek.value - 1).toLong())
    val days = (0L..6L).map { monday.plusDays(it) }.filter { !it.isBefore(today) }
    var day by remember { mutableStateOf(view.date) }
    val here = view.blocks.firstOrNull { b -> (b.timed + b.flexible).any { it == row } }?.block?.uid ?: view.blocks.firstOrNull()?.block?.uid
    var block by remember { mutableStateOf(here) }
    TendrilSheet(onDismiss = onPutBack, title = stringResource(Res.string.plan_move_title, row.habit.title)) {
        if (row.occurrence != null) {
            Text(stringResource(Res.string.plan_move_day), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the day choice
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                for (d in days) {
                    FilterChip(selected = d == day, onClick = { day = d }, label = { Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth) })
                }
            }
        }
        Text(stringResource(Res.string.plan_move_block), style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 8.dp)) // type: GROUP_HEADER — the block choice
        for (b in view.blocks) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.RadioButton) { block = b.block.uid }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = block == b.block.uid, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(blockName(b.block), style = MaterialTheme.typography.body) // type: BODY_LINE — a block
            }
        }
        PromptButtons(stringResource(Res.string.plan_apply), enabled = block != null, onPutBack = onPutBack) { block?.let { onApply(day, it) } }
    }
}

/** The planner's choice for a weekday rule moved to another day: this entry, or every week from now on. */
@Composable
private fun WeeklyPrompt(target: LocalDate, onPutBack: () -> Unit, onApply: (Boolean) -> Unit) {
    var weekly by remember { mutableStateOf(false) }
    TendrilSheet(onDismiss = onPutBack, title = stringResource(Res.string.plan_scope_title)) {
        for (w in listOf(false, true)) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.RadioButton) { weekly = w }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = weekly == w, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text( // type: BODY_LINE — a scope
                    if (w) stringResource(Res.string.plan_move_weekday_from, target.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())) else stringResource(Res.string.plan_scope_occurrence),
                    style = MaterialTheme.typography.body,
                )
            }
        }
        PromptButtons(stringResource(Res.string.plan_apply), onPutBack = onPutBack) { onApply(weekly) }
    }
}
