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
import androidx.compose.material.icons.filled.Notifications
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
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.reminders.ReminderSheet
import com.tendril.app.ui.trash.EntryTrashSheet
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

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
                    container.resolveEntryUseCase,
                    container.entryScheduleCoordinator,
                    container.checkInHabitUseCase,
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
                TabSelection.TASKS -> TasksList(tasks, filter, showUndated, { showUndated = it }, viewModel) { reminderTarget = it }
                TabSelection.HABITS -> HabitsList(habits, viewModel)
                TabSelection.MERGED -> MergedList(tasks, habits, filter, viewModel) { reminderTarget = it }
            }
        }
    }

    if (showAddDialog) {
        if (tab == TabSelection.HABITS) {
            AddHabitDialog(onDismiss = { showAddDialog = false }, onAdd = viewModel::addHabit)
        } else {
            AddTaskDialog(onDismiss = { showAddDialog = false }, onAdd = viewModel::addTask)
        }
    }

    reminderTarget?.let { entry ->
        ReminderSheet(container = container, entry = entry, onDismiss = { reminderTarget = null })
    }

    if (showTrash) {
        EntryTrashSheet(container = container, onDismiss = { showTrash = false })
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
    onOpenReminders: (Entry) -> Unit,
) {
    val dated = tasks.filter { it.startDate != null && inFilterRange(it.startDate, filter) }
    val undated = tasks.filter { it.startDate == null }

    if (tasks.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Check,
            message = stringResource(R.string.empty_tasks_message),
            modifier = Modifier.fillMaxSize(),
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
                items(undated, key = { it.id }) { TaskRow(it, viewModel, onOpenReminders) }
            }
        }
        items(dated, key = { it.id }) { TaskRow(it, viewModel, onOpenReminders) }
    }
}

@Composable
private fun TaskRow(entry: Entry, viewModel: TasksHabitsViewModel, onOpenReminders: (Entry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = entry.status == EntryStatus.DONE,
            onCheckedChange = { checked -> viewModel.resolve(entry.id, if (checked) EntryStatus.DONE else EntryStatus.SKIPPED) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(entry.startDate?.toString(), entry.startTime?.toString()).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { onOpenReminders(entry) }) {
            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.reminders_open))
        }
        IconButton(onClick = { viewModel.trashTask(entry.id) }) {
            Icon(Icons.Filled.Close, contentDescription = "Delete")
        }
    }
}

@Composable
private fun HabitsList(habits: List<Habit>, viewModel: TasksHabitsViewModel) {
    if (habits.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.LocalFireDepartment,
            message = stringResource(R.string.empty_habits_message),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(habits, key = { it.id }) { habit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                        "Every ${habit.frequency.count} ${habit.frequency.unit.name.lowercase()}(s) · streak ${habit.streak}",
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
    onOpenReminders: (Entry) -> Unit,
) {
    val dated = tasks.filter { it.startDate != null && inFilterRange(it.startDate, filter) }
    if (dated.isEmpty() && habits.isEmpty()) {
        EmptyState(icon = Icons.Filled.Check, message = stringResource(R.string.empty_tasks_message), modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        items(dated, key = { "task_${it.id}" }) { TaskRow(it, viewModel, onOpenReminders) }
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
