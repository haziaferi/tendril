@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.AppContainer
import com.tendril.app.R
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.formatHabitDuration
import com.tendril.app.domain.TaskWithSubtasks
import com.tendril.app.domain.withSubtasks
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.reminders.ReminderSheet
import com.tendril.app.ui.trash.EntryTrashSheet
import com.tendril.app.ui.trash.HabitTrashSheet
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

private enum class TabSelection { TASKS, HABITS, MERGED }
private enum class TimeFilter { TODAY, WEEK, MONTH }

@Composable
fun TasksHabitsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: TasksHabitsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TasksHabitsViewModel(
                    container.database.entryDao(),
                    container.database.habitDao(),
                    container.database.habitCompletionDao(),
                    container.resolveEntryUseCase,
                    container.entryScheduleCoordinator,
                    container.checkInHabitUseCase,
                    container.alarmScheduler,
                )
            }
        }
    )

    var tab by remember { mutableStateOf(TabSelection.TASKS) }
    var filter by remember { mutableStateOf(TimeFilter.TODAY) }
    var showUndated by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    // §5.4 — the reminder list opens over whichever Entry was tapped; null means closed.
    var reminderTarget by remember { mutableStateOf<Entry?>(null) }
    // §5.5.1 — Entry Trash, the counterpart to Pages' own Trash sheet.
    var showTrash by remember { mutableStateOf(false) }
    // §0.6.4 — the three task sheets and §0.6.6's habit detail; null means closed.
    var postponeTarget by remember { mutableStateOf<Entry?>(null) }
    var subtaskParent by remember { mutableStateOf<Entry?>(null) }
    var deadlineTarget by remember { mutableStateOf<Entry?>(null) }
    var habitDetail by remember { mutableStateOf<Habit?>(null) }
    val showImportance by container.taskPreferences.showImportance.collectAsState()
    val showStreaks by container.taskPreferences.showHabitStreaks.collectAsState()
    val rowActions = remember(showImportance) {
        TaskRowActions(
            onPostpone = { postponeTarget = it },
            onAddSubtask = { subtaskParent = it },
            onSetDeadline = { deadlineTarget = it },
            showImportance = showImportance,
        )
    }

    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tasks_habits)) },
                actions = {
                    IconButton(onClick = { showTrash = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.trash_entries_open))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(
                    if (tab == TabSelection.HABITS) R.string.taskshabits_add_habit else R.string.taskshabits_add_task
                ))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TabSelection.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { tab = t },
                        shape = SegmentedButtonDefaults.itemShape(index, TabSelection.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (t) {
                                    TabSelection.TASKS -> R.string.taskshabits_tab_tasks
                                    TabSelection.HABITS -> R.string.taskshabits_tab_habits
                                    TabSelection.MERGED -> R.string.taskshabits_tab_merged
                                }
                            )
                        )
                    }
                }
            }

            if (tab != TabSelection.HABITS) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TimeFilter.entries.forEachIndexed { index, f ->
                        SegmentedButton(
                            selected = filter == f,
                            onClick = { filter = f },
                            shape = SegmentedButtonDefaults.itemShape(index, TimeFilter.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (f) {
                                        TimeFilter.TODAY -> R.string.taskshabits_filter_today
                                        TimeFilter.WEEK -> R.string.taskshabits_filter_week
                                        TimeFilter.MONTH -> R.string.taskshabits_filter_month
                                    }
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                TabSelection.TASKS -> TasksList(
                    tasks, filter, showUndated, { showUndated = it }, viewModel, rowActions,
                    onAdd = { showAddDialog = true },
                ) { reminderTarget = it }
                TabSelection.HABITS -> HabitsList(habits, viewModel, showStreaks, onOpen = { habitDetail = it }, onAdd = { showAddDialog = true })
                TabSelection.MERGED -> MergedList(tasks, habits, filter, viewModel, rowActions) { reminderTarget = it }
            }
        }
    }

    if (showAddDialog) {
        if (tab == TabSelection.HABITS) {
            AddHabitDialog(onDismiss = { showAddDialog = false }, onAdd = viewModel::addHabit)
        } else {
            AddTaskDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, date, time, repeat, deadline, estimate, important -> viewModel.addTask(title, date, time, repeat, deadline, estimate = estimate, important = important) },
            )
        }
    }

    reminderTarget?.let { entry ->
        ReminderSheet(container = container, entry = entry, onDismiss = { reminderTarget = null })
    }
    postponeTarget?.let { entry ->
        PostponeSheet(entry, onPostpone = { viewModel.postpone(entry.id, it) }, onDismiss = { postponeTarget = null })
    }
    subtaskParent?.let { parent ->
        SubtaskDialog(parent, onAdd = { viewModel.addSubtask(parent.id, it) }, onDismiss = { subtaskParent = null })
    }
    deadlineTarget?.let { entry ->
        DeadlineDialog(entry.dueDate, onSet = { viewModel.setDeadline(entry.id, it) }, onDismiss = { deadlineTarget = null })
    }
    habitDetail?.let { habit ->
        HabitDetailSheet(habit, viewModel, showStreak = showStreaks, onDismiss = { habitDetail = null })
    }

    if (showTrash) {
        // Which Trash the button opens follows the tab, exactly as the add button's label does
        // above: on Habits it has to be the Habit sheet, or trashed Habits stay unreachable.
        if (tab == TabSelection.HABITS) {
            HabitTrashSheet(container = container, onDismiss = { showTrash = false })
        } else {
            EntryTrashSheet(container = container, onDismiss = { showTrash = false })
        }
    }
}

private fun inFilterRange(date: LocalDate?, filter: TimeFilter): Boolean {
    if (date == null) return false
    val today = LocalDate.now()
    return when (filter) {
        TimeFilter.TODAY -> date == today
        TimeFilter.WEEK -> {
            val week = WeekFields.of(Locale.getDefault()).let { today.get(it.weekOfWeekBasedYear()) to today.get(it.weekBasedYear()) }
            val fields = WeekFields.of(Locale.getDefault())
            date.get(fields.weekOfWeekBasedYear()) == week.first && date.get(fields.weekBasedYear()) == week.second
        }
        TimeFilter.MONTH -> date.month == today.month && date.year == today.year
    }
}

@Composable
private fun TasksList(
    tasks: List<Entry>,
    filter: TimeFilter,
    showUndated: Boolean,
    onShowUndatedChange: (Boolean) -> Unit,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onAdd: () -> Unit,
    onOpenReminders: (Entry) -> Unit,
) {
    // §0.6.4 — grouped first, then filtered on the *parent's* date: a step under a task that is
    // due this week belongs in this week's list even though the step itself carries no date.
    val grouped = tasks.withSubtasks()
    val dated = grouped.filter { it.task.startDate != null && inFilterRange(it.task.startDate, filter) }
    val undated = grouped.filter { it.task.startDate == null }

    if (tasks.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Check,
            message = stringResource(R.string.empty_tasks_message),
            modifier = Modifier.fillMaxSize(),
            // §2.5 fixes the CTA here as part of the copy ("Nothing due — Add a task"), not
            // just the message. Both `empty_tasks_cta` and `empty_habits_cta` existed in
            // strings.xml with no Kotlin reference at all until this was wired up.
            ctaLabel = stringResource(R.string.empty_tasks_cta),
            onCta = onAdd,
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (undated.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onShowUndatedChange(!showUndated) }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${stringResource(R.string.taskshabits_undated_toggle)} (${undated.size})", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = showUndated, onCheckedChange = onShowUndatedChange)
                }
            }
            if (showUndated) {
                items(undated, key = { it.task.id }) { TaskWithSteps(it, viewModel, actions, onOpenReminders) }
            }
        }
        items(dated, key = { it.task.id }) { TaskWithSteps(it, viewModel, actions, onOpenReminders) }
    }
}

/** What a task row can open, handed down once rather than threaded as four lambdas. */
internal class TaskRowActions(
    val onPostpone: (Entry) -> Unit,
    val onAddSubtask: (Entry) -> Unit,
    val onSetDeadline: (Entry) -> Unit,
    /** §0.5.1 — the flag is drawn, and offered, only while Settings says so. */
    val showImportance: Boolean,
)

@Composable
private fun TaskWithSteps(
    group: TaskWithSubtasks,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onOpenReminders: (Entry) -> Unit,
) {
    Column {
        TaskRow(group.task, viewModel, actions, onOpenReminders, stepsDone = group.done, stepsTotal = group.subtasks.size)
        group.subtasks.forEach { step -> TaskRow(step, viewModel, actions, onOpenReminders, isStep = true) }
    }
}

@Composable
private fun TaskRow(
    entry: Entry,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onOpenReminders: (Entry) -> Unit,
    isStep: Boolean = false,
    stepsDone: Int = 0,
    stepsTotal: Int = 0,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (isStep) 40.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = entry.status == EntryStatus.DONE,
            onCheckedChange = { checked -> viewModel.setDone(entry.id, checked) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actions.showImportance && entry.important) {
                    Icon(Icons.Filled.Star, contentDescription = "Important", modifier = Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(entry.title, style = if (isStep) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
            }
            // The When, the Deadline and the steps, in that order and in one colour: a deadline
            // that has passed is information, not an alarm (§0.5.2).
            val subtitle = listOfNotNull(
                entry.startDate?.toString(),
                entry.startTime?.toString(),
                entry.dueDate?.let { "due $it" },
                if (stepsTotal > 0) "$stepsDone/$stepsTotal steps" else null,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!isStep) {
            IconButton(onClick = { onOpenReminders(entry) }) {
                Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.reminders_open))
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!isStep) {
                    DropdownMenuItem(text = { Text("Postpone…") }, onClick = { menuOpen = false; actions.onPostpone(entry) })
                    DropdownMenuItem(text = { Text("Add a step") }, onClick = { menuOpen = false; actions.onAddSubtask(entry) })
                    DropdownMenuItem(
                        text = { Text(if (entry.dueDate == null) "Set deadline…" else "Change deadline…") },
                        onClick = { menuOpen = false; actions.onSetDeadline(entry) },
                    )
                }
                if (actions.showImportance) {
                    DropdownMenuItem(
                        text = { Text(if (entry.important) "Not important" else "Important") },
                        leadingIcon = { Icon(if (entry.important) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = null) },
                        onClick = { menuOpen = false; viewModel.setImportant(entry.id, !entry.important) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    onClick = { menuOpen = false; viewModel.trashTask(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun HabitsList(habits: List<Habit>, viewModel: TasksHabitsViewModel, showStreaks: Boolean, onOpen: (Habit) -> Unit, onAdd: () -> Unit) {
    if (habits.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.LocalFireDepartment,
            message = stringResource(R.string.empty_habits_message),
            modifier = Modifier.fillMaxSize(),
            ctaLabel = stringResource(R.string.empty_habits_cta),
            onCta = onAdd,
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(habits, key = { it.id }) { habit ->
            Row(
                // §0.6.6 — the row opens the presence view; the checkbox stays its own target.
                modifier = Modifier.fillMaxWidth().clickable { onOpen(habit) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val checkedToday = habit.lastCompletedDate == LocalDate.now()
                Checkbox(
                    checked = checkedToday,
                    onCheckedChange = { checked ->
                        if (checked) viewModel.checkInHabit(habit.id) else viewModel.undoCheckInHabit(habit.id)
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        // §3.3 — time and duration are what distinguish a habit that sits at an
                        // hour from one that just needs doing sometime today, so both show when
                        // set and neither takes room when not.
                        listOfNotNull(
                            "Every ${habit.frequency.count} ${habit.frequency.unit.name.lowercase()}(s)",
                            habit.time?.toString(),
                            habit.duration?.let(::formatHabitDuration),
                            // §0.6.6 — retired from the row by default; a plain number when asked for.
                            if (showStreaks && habit.streak > 0) "streak ${habit.streak}" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.trashHabit(habit.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun MergedList(
    tasks: List<Entry>,
    habits: List<Habit>,
    filter: TimeFilter,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onOpenReminders: (Entry) -> Unit,
) {
    // Merged is day-shaped, so sub-tasks are folded into their parent's step count here rather
    // than listed: the steps are undated and would only lengthen a list meant to be read as a day.
    val dated = tasks.withSubtasks().filter { it.task.startDate != null && inFilterRange(it.task.startDate, filter) }
    if (dated.isEmpty() && habits.isEmpty()) {
        EmptyState(icon = Icons.Filled.Check, message = stringResource(R.string.empty_tasks_message), modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(dated, key = { "task_${it.task.id}" }) { TaskRow(it.task, viewModel, actions, onOpenReminders, stepsDone = it.done, stepsTotal = it.subtasks.size) }
        // Habits with a time get a delicate highlight to distinguish them (§3.3).
        items(habits.filter { it.time != null }, key = { "habit_${it.id}" }) { habit ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text(habit.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        habit.time?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
