@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.domain.TaskWithSubtasks
import com.tendril.app.domain.dayLabel
import com.tendril.app.domain.plan.loggedSegment
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.urgency.Urgency
import com.tendril.app.domain.urgency.urgencyOf
import com.tendril.app.ui.components.UrgencyDot
import com.tendril.app.ui.components.UrgencyPicker
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.Period
import com.tendril.app.ui.track.TrackButton
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.pageTitle
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.domain.label
import com.tendril.app.domain.plural
import com.tendril.app.data.habit.formatHabitDuration

/**
 * B§13.4 14f·1 — the right pane of the Tasks tab on a wide window: a task read in full, with
 * **the row's verbs as chips, by name** (`docs/critiques/tasks-calendar-mock.md` #4: the mock's
 * *Someday* and *Flag* were verbs the app does not have). Nothing is edited inline (decided
 * 2026-09-16): each chip opens the sheet the row's `···` opens — a slide-over on a wide window —
 * so the phone's sheets and the desktop's pane are one set of controls in two homes. Labels and
 * unset values are `onSurfaceVariant` (the mock's `faint` measured 3.08:1, #1).
 */
@Composable
internal fun TaskDetailPane(
    group: TaskWithSubtasks,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onOpenReminders: (() -> Unit)?,
    onTrash: () -> Unit,
) {
    val entry = group.task
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = entry.status == EntryStatus.DONE, onCheckedChange = { viewModel.setDone(entry.id, it) })
            Text(entry.title, style = MaterialTheme.typography.pageTitle, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        // F·P4's desktop half (item 23's Lows, 2026-09-18): the pane's dates as the rows' — *ven 25*, not ISO.
        val today = LocalDate.now()
        DetailRow("When", listOfNotNull(entry.startDate?.let { dayLabel(it, today) }, entry.startTime?.toString()).joinToString(" · ").ifEmpty { null })
        DetailRow("Deadline", entry.dueDate?.let { dayLabel(it, today) })
        DetailRow("Repeat", (entry.recurrenceRule as? RecurrenceRule.Elastic)?.period?.let(::periodWords))
        if (actions.showUrgency) {
            // 14g·3 — the level shown is the greater of the set level and the deadline's pressure;
            // when pressure is what raised it, the row says so.
            val set = Urgency.fromLevel(entry.importance)
            val shown = urgencyOf(entry, LocalDate.now())
            DetailRow("Urgency", if (shown == Urgency.NONE) null else shown.label + (if (shown.level > set.level) " (the deadline)" else ""), unsetWord = "none")
        }
        DetailRow("Tracked today", loggedSegment(actions.loggedToday[entry.id] ?: 0, entry.estimate), unsetWord = "nothing yet")
        Spacer(Modifier.height(14.dp))
        // The chips: the menu's items by name, plus the row's ▶ and its bell, and the Trash.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { actions.onPostpone(entry) }, label = { Text("Postpone…") })
            AssistChip(onClick = { actions.onSetDeadline(entry) }, label = { Text(if (entry.dueDate == null) "Set deadline…" else "Change deadline…") })
            if (actions.showUrgency) {
                var pick by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { pick = true },
                        label = { Text("Urgency…") },
                        leadingIcon = { UrgencyDot(Urgency.fromLevel(entry.importance)) },
                    )
                    TendrilMenu(expanded = pick, onDismissRequest = { pick = false }) {
                        UrgencyPicker(
                            value = Urgency.fromLevel(entry.importance),
                            onPick = { pick = false; viewModel.setImportance(entry.id, it.level) },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            val running = actions.runningTarget == TrackTarget.Entry(entry.id)
            AssistChip(
                onClick = { viewModel.toggleTracking(TrackTarget.Entry(entry.id)) },
                label = { Text(if (running) "Stop timer" else "Start timer") },
                leadingIcon = { Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null) },
            )
            if (onOpenReminders != null) AssistChip(onClick = onOpenReminders, label = { Text("Reminders…") }, leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) })
            AssistChip(onClick = onTrash, label = { Text("Move to Trash") })
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider()
        Text("Steps".uppercase(), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        group.subtasks.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = step.status == EntryStatus.DONE, onCheckedChange = { viewModel.setDone(step.id, it) })
                Text(step.title, style = MaterialTheme.typography.body, modifier = Modifier.weight(1f))
                TrackButton(TrackTarget.Entry(step.id), actions.runningTarget, viewModel::toggleTracking)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { actions.onAddSubtask(entry) }.padding(vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            Text("Add a step", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.body)
        }
        if (entry.sourceRowId != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "This task is a database row's — its When and Done are the row's cells; a \"Blocked by\" relation, where the database has one, lives on the row.",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A habit read in full: the presence sentences and the month of dots the sheet shows, inline. */
@Composable
internal fun HabitDetailPane(habit: Habit, viewModel: TasksHabitsViewModel, showStreak: Boolean, runningTarget: TrackTarget?, onTrash: () -> Unit) {
    // The audit's fixes (2026-09-17, F3): the pane read only §0.6.5's presence — *Here whenever
    // you want it.* on a habit with no completions — so it looked like a stub. The habit's own
    // facts come first, the presence and the month of dots stay, and the row's verbs are chips
    // by name, as the task pane's are. No Reminders chip: the shared sheet is an Entry's; a
    // habit fires at its own time (`habitFiring`).
    val doneToday = habit.lastCompletedDate == LocalDate.now()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = doneToday, onCheckedChange = { if (it) viewModel.checkInHabit(habit.id) else viewModel.undoCheckInHabit(habit.id) })
            Text(habit.title, style = MaterialTheme.typography.pageTitle, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        DetailRow("Repeats", habit.frequency.label())
        DetailRow("At", habit.time?.toString(), unsetWord = "any time")
        DetailRow("For", habit.duration?.let(::formatHabitDuration), unsetWord = "no length")
        if (showStreak) DetailRow("Streak", if (habit.streak > 0) plural(habit.streak, "day") else null)
        Spacer(Modifier.height(14.dp))
        HabitDetailContent(habit, viewModel, showStreak)
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
                onClick = { if (doneToday) viewModel.undoCheckInHabit(habit.id) else viewModel.checkInHabit(habit.id) },
                label = { Text(if (doneToday) "Undo today's check-in" else "Check in today") },
            )
            val running = runningTarget == TrackTarget.Habit(habit.id)
            AssistChip(
                onClick = { viewModel.toggleTracking(TrackTarget.Habit(habit.id)) },
                label = { Text(if (running) "Stop timer" else "Start timer") },
                leadingIcon = { Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null) },
            )
            AssistChip(onClick = onTrash, label = { Text("Move to Trash") })
        }
    }
}

/** The pane at the tab root, before anything is chosen — the Pages workspace's quiet line. */
@Composable
internal fun EmptyTaskPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Choose a task or a habit — or press Ctrl+Shift+N for a new task.",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String?, unsetWord: String = "none") {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        if (value != null) Text(value, style = MaterialTheme.typography.body)
        else Text(unsetWord, style = MaterialTheme.typography.description, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Every day", "Every 2 weeks" — the Add sheet's vocabulary for a task's repeat. */
private fun periodWords(p: Period): String {
    val (n, unit) = when {
        p.months > 0 -> p.months to "month"
        p.days > 0 && p.days % 7 == 0 -> p.days / 7 to "week"
        else -> p.days to "day"
    }
    return if (n == 1) "Every $unit" else "Every $n ${unit}s"
}
