@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.backhandler.BackHandler
import com.tendril.app.ui.components.ListKeyState
import com.tendril.app.ui.components.TypeAheadReset
import com.tendril.app.ui.components.listKeyboard
import com.tendril.app.ui.components.keyedTitle
import com.tendril.app.ui.components.keyboardCursorShown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tendril.app.ui.components.PaneHandle
import com.tendril.app.ui.components.PaneWidthState
import com.tendril.app.ui.components.PointerMenu
import com.tendril.app.ui.components.onSecondaryClick
import com.tendril.app.ui.nav.DensityProfile
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.nav.LocalShellLayout
import com.tendril.app.ui.nav.ShellLayout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Checkbox
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.urgency.Urgency
import com.tendril.app.domain.urgency.urgencyOf
import com.tendril.app.ui.components.UrgencyDot
import com.tendril.app.ui.components.SubmenuItem
import com.tendril.app.ui.components.UrgencyStripe
import com.tendril.app.domain.track.formatMinutes
import com.tendril.app.domain.plan.loggedSegment
import com.tendril.app.ui.track.TrackButton
import com.tendril.app.ui.track.runningTargetState
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
import com.tendril.app.ui.nav.ShellTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tendril.app.ui.WorkbenchCore
import org.jetbrains.compose.resources.stringResource
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.empty_habits_cta
import com.tendril.app.generated.resources.empty_habits_message
import com.tendril.app.generated.resources.empty_tasks_cta
import com.tendril.app.generated.resources.empty_tasks_message
import com.tendril.app.generated.resources.nav_tasks_habits
import com.tendril.app.generated.resources.reminders_open
import com.tendril.app.generated.resources.taskshabits_add_habit
import com.tendril.app.generated.resources.taskshabits_add_task
import com.tendril.app.generated.resources.taskshabits_filter_month
import com.tendril.app.generated.resources.taskshabits_filter_today
import com.tendril.app.generated.resources.taskshabits_filter_week
import com.tendril.app.generated.resources.taskshabits_tab_habits
import com.tendril.app.generated.resources.taskshabits_tab_merged
import com.tendril.app.generated.resources.taskshabits_tab_tasks
import com.tendril.app.generated.resources.taskshabits_undated_toggle
import com.tendril.app.generated.resources.trash_entries_open
import com.tendril.app.generated.resources.review_open
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.caption
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.formatHabitDuration
import com.tendril.app.domain.TaskWithSubtasks
import com.tendril.app.domain.withSubtasks
import com.tendril.app.ui.components.EmptyState
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.material3.DropdownMenuItem
import com.tendril.app.ui.theme.body

private enum class TabSelection { TASKS, HABITS, MERGED }
private enum class TimeFilter { TODAY, WEEK, MONTH }

/**
 * §3.3, in `shared/` since §0.8 step 7a (as the Calendar since 6a). The Android-only surfaces
 * arrive as slots: [reminderSheet] (§5.4, alarms; null hides the bell), and the two Trash sheets
 * (§5.5.1; null hides the Trash button — desktop's case until they move too). [showUrgency]
 * and [showStreaks] are the Settings switches (14g·3, §0.6.6), shared on both platforms.
 */
@Composable
fun TasksHabitsScreen(
    core: WorkbenchCore,
    onOpenReview: () -> Unit,
    showUrgency: Boolean,
    showStreaks: Boolean,
    reminderSheet: (@Composable (entry: Entry, onDismiss: () -> Unit) -> Unit)?,
    entryTrashSheet: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    habitTrashSheet: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
    /** 14e — Ctrl+Shift+N's intent (`WorkbenchNavState.requestQuickAdd`): the Tasks tab with its Add sheet open. */
    quickAddRequested: Boolean = false,
    onQuickAddConsumed: () -> Unit = {},
) {
    val viewModel: TasksHabitsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TasksHabitsViewModel(
                    core.database.entryDao(),
                    core.database.habitDao(),
                    core.database.habitCompletionDao(),
                    core.resolveEntryUseCase,
                    core.entryScheduleCoordinator,
                    core.checkInHabitUseCase,
                    core.timeTracker,
                )
            }
        }
    )

    var tab by remember { mutableStateOf(TabSelection.TASKS) }
    var filter by remember { mutableStateOf(TimeFilter.TODAY) }
    androidx.compose.runtime.CompositionLocalProvider(LocalRemindersAvailable provides (reminderSheet != null)) {
        TasksHabitsBody(
            viewModel, core, onOpenReview, showUrgency, showStreaks, reminderSheet, entryTrashSheet, habitTrashSheet, modifier,
            tab, { tab = it }, filter, { filter = it },
            quickAddRequested, onQuickAddConsumed,
        )
    }
}

/** Whether a Reminders sheet was supplied — rows show the bell only then. */
private val LocalRemindersAvailable = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
private fun TasksHabitsBody(
    viewModel: TasksHabitsViewModel,
    core: WorkbenchCore,
    onOpenReview: () -> Unit,
    showUrgency: Boolean,
    showStreaks: Boolean,
    reminderSheet: (@Composable (entry: Entry, onDismiss: () -> Unit) -> Unit)?,
    entryTrashSheet: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    habitTrashSheet: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    modifier: Modifier,
    tab: TabSelection,
    setTab: (TabSelection) -> Unit,
    filter: TimeFilter,
    setFilter: (TimeFilter) -> Unit,
    quickAddRequested: Boolean,
    onQuickAddConsumed: () -> Unit,
) {
    var showUndated by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    LaunchedEffect(quickAddRequested) {
        if (quickAddRequested) { setTab(TabSelection.TASKS); showAddDialog = true; onQuickAddConsumed() }
    }
    // §5.4 — the reminder list opens over whichever Entry was tapped; null means closed.
    var reminderTarget by remember { mutableStateOf<Entry?>(null) }
    // §5.5.1 — Entry Trash, the counterpart to Pages' own Trash sheet.
    var showTrash by remember { mutableStateOf(false) }
    // §0.6.4 — the three task sheets and §0.6.6's habit detail; null means closed.
    var postponeTarget by remember { mutableStateOf<Entry?>(null) }
    var subtaskParent by remember { mutableStateOf<Entry?>(null) }
    var deadlineTarget by remember { mutableStateOf<Entry?>(null) }
    var habitDetail by remember { mutableStateOf<Habit?>(null) }
    val runningTarget by core.timeTracker.runningTargetState()
    val loggedToday by viewModel.loggedToday.collectAsState()
    // 14f·1 — on a wide window the tab is two panes: a click or ↵ selects a task or a habit for
    // the pane on the right; the phone's click keeps today's meaning (nothing for a task, the
    // detail sheet for a habit). The selection is not the keyboard cursor (14e's ring).
    val wide = LocalShellLayout.current == ShellLayout.RAIL
    var selected by remember { mutableStateOf<Selected?>(null) }
    // A subject belongs to the list it was chosen from: switching tabs empties the pane rather
    // than leaving a task beside the Habits list.
    LaunchedEffect(tab) { selected = null }
    val listWidth = remember { PaneWidthState(core.keyValueStore, TASKS_LIST_WIDTH_KEY, 420, 300, 600) }
    val rowActions = remember(showUrgency, runningTarget, loggedToday, wide, selected) {
        TaskRowActions(
            onPostpone = { postponeTarget = it },
            onAddSubtask = { subtaskParent = it },
            onSetDeadline = { deadlineTarget = it },
            showUrgency = showUrgency,
            runningTarget = runningTarget,
            loggedToday = loggedToday.first,
            onSelect = if (wide) ({ selected = Selected.Task(it.id) }) else null,
            selectedId = (selected as? Selected.Task)?.id,
        )
    }
    val openHabit: (Habit) -> Unit = { if (wide) selected = Selected.Habit(it.id) else habitDetail = it }

    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    // §0.6.11 — a dot when the walk has something, and no number: the review says what (§0.5.2).
    // Re-asked whenever the tasks change, which is also every return from the Review route.
    var reviewDue by remember { mutableStateOf(false) }
    LaunchedEffect(tasks) { reviewDue = core.review.isDue() }

    Scaffold(
        modifier = modifier,
        topBar = {
            ShellTopBar(
                title = { Text(stringResource(Res.string.nav_tasks_habits)) },
                actions = {
                    IconButton(onClick = onOpenReview) {
                        BadgedBox(badge = { if (reviewDue) Badge() }) {
                            Icon(Icons.Outlined.Checklist, contentDescription = stringResource(Res.string.review_open))
                        }
                    }
                    if (entryTrashSheet != null || habitTrashSheet != null) IconButton(onClick = { showTrash = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.trash_entries_open))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(
                    if (tab == TabSelection.HABITS) Res.string.taskshabits_add_habit else Res.string.taskshabits_add_task
                ))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 14f·2 (·1's walk, #2) — the two filter rows belong to the list: on a wide window
            // they sit over the list column, so the pane starts at the bar.
            val filters: @Composable () -> Unit = {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    TabSelection.entries.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { setTab(t) },
                            shape = SegmentedButtonDefaults.itemShape(index, TabSelection.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (t) {
                                        TabSelection.TASKS -> Res.string.taskshabits_tab_tasks
                                        TabSelection.HABITS -> Res.string.taskshabits_tab_habits
                                        TabSelection.MERGED -> Res.string.taskshabits_tab_merged
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
                                onClick = { setFilter(f) },
                                shape = SegmentedButtonDefaults.itemShape(index, TimeFilter.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        when (f) {
                                            TimeFilter.TODAY -> Res.string.taskshabits_filter_today
                                            TimeFilter.WEEK -> Res.string.taskshabits_filter_week
                                            TimeFilter.MONTH -> Res.string.taskshabits_filter_month
                                        }
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (!wide) filters()
            val listsInner: @Composable () -> Unit = {
                when (tab) {
                    TabSelection.TASKS -> TasksList(
                        tasks, filter, showUndated, { showUndated = it }, viewModel, rowActions,
                        onAdd = { showAddDialog = true },
                    ) { reminderTarget = it }
                    TabSelection.HABITS -> HabitsList(habits, viewModel, showStreaks, runningTarget, loggedToday.second, onOpen = openHabit, onAdd = { showAddDialog = true }, selectedId = (selected as? Selected.Habit)?.id)
                    TabSelection.MERGED -> MergedList(tasks, habits, filter, viewModel, rowActions, { reminderTarget = it }, onOpenHabit = openHabit, selectedHabitId = (selected as? Selected.Habit)?.id)
                }
            }
            // The tray PR (2026-09-16) — under a pointer the rows are one line at the profile's
            // height, and the `Checkbox`/`IconButton` 48 dp minimum yields to the list's (14h·2's
            // rule for the Table): a task row measured 65 px beside Notion's one-line 30.
            val listMin = LocalDensityProfile.current.listInteractiveMinDp.dp
            val lists: @Composable () -> Unit = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides listMin) { listsInner() } }
            if (!wide) lists() else Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(listWidth.widthDp.dp).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxSize()) { filters(); lists() }
                    PaneHandle(listWidth, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // The pane resolves its subject from the live lists, so an edit shows at once
                    // and a trashed subject empties the pane rather than showing a ghost.
                    val groups = remember(tasks) { tasks.withSubtasks() }
                    val subject = selected
                    val taskGroup = (subject as? Selected.Task)?.let { sel ->
                        groups.firstOrNull { it.task.id == sel.id }
                            ?: groups.asSequence().flatMap { it.subtasks.asSequence() }.firstOrNull { it.id == sel.id }?.let { TaskWithSubtasks(it, emptyList()) }
                    }
                    val habit = (subject as? Selected.Habit)?.let { sel -> habits.firstOrNull { it.id == sel.id } }
                    when {
                        taskGroup != null -> TaskDetailPane(
                            group = taskGroup, viewModel = viewModel, actions = rowActions,
                            onOpenReminders = if (reminderSheet != null) ({ reminderTarget = taskGroup.task }) else null,
                            onTrash = { viewModel.trashTask(taskGroup.task.id); selected = null },
                        )
                        habit != null -> HabitDetailPane(habit, viewModel, showStreak = showStreaks, onTrash = { viewModel.trashHabit(habit.id); selected = null })
                        else -> EmptyTaskPane()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        if (tab == TabSelection.HABITS) {
            AddHabitDialog(onDismiss = { showAddDialog = false }, onAdd = viewModel::addHabit)
        } else {
            AddTaskDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, date, time, repeat, deadline, estimate, importance -> viewModel.addTask(title, date, time, repeat, deadline, estimate = estimate, importance = importance) },
            )
        }
    }

    reminderTarget?.let { entry ->
        reminderSheet?.invoke(entry) { reminderTarget = null }
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
            habitTrashSheet?.invoke { showTrash = false }
        } else {
            entryTrashSheet?.invoke { showTrash = false }
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

/** 14f·1 — what the wide window's pane shows. */
private sealed class Selected {
    data class Task(val id: Long) : Selected()
    data class Habit(val id: Long) : Selected()
}

/** The list pane's width on a wide window, a device preference like the tree's. */
private const val TASKS_LIST_WIDTH_KEY = "tasks_list_width"

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
    // 14e (B§13.6 #8) — the rows as the keyboard sees them, in the order drawn: undated (when
    // shown) then dated, each task followed by its steps. ↵ selects on a wide window (14f·1)
    // and opens the row's menu on the phone — what its `···` does; a task has no detail screen.
    val keyState = remember { ListKeyState() }
    val flat: List<Entry> = remember(undated, dated, showUndated) {
        buildList {
            if (showUndated) undated.forEach { add(it.task); addAll(it.subtasks) }
            dated.forEach { add(it.task); addAll(it.subtasks) }
        }
    }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    TypeAheadReset(keyState)
    BackHandler(enabled = keyState.hasSomethingToClear) { keyState.clear() }
    val indexOf: (Entry) -> Int = { e -> flat.indexOfFirst { it.id == e.id } }
    val rowKeys = RowKeys(keyState, indexOf, menuFor, { menuFor = null })

    if (tasks.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Check,
            message = stringResource(Res.string.empty_tasks_message),
            modifier = Modifier.fillMaxSize(),
            // §2.5 fixes the CTA here as part of the copy ("Nothing due — Add a task"), not
            // just the message. Both `empty_tasks_cta` and `empty_habits_cta` existed in
            // strings.xml with no Kotlin reference at all until this was wired up.
            ctaLabel = stringResource(Res.string.empty_tasks_cta),
            onCta = onAdd,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().listKeyboard(
            state = keyState,
            count = { flat.size },
            titles = { flat.map { it.title } },
            onOpen = { i -> flat.getOrNull(i)?.let { e -> actions.onSelect?.invoke(e) ?: run { menuFor = e.id } } },
        ),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (undated.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onShowUndatedChange(!showUndated) }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${stringResource(Res.string.taskshabits_undated_toggle)} (${undated.size})", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = showUndated, onCheckedChange = onShowUndatedChange)
                }
            }
            if (showUndated) {
                items(undated, key = { it.task.id }) { TaskWithSteps(it, viewModel, actions, onOpenReminders, rowKeys) }
            }
        }
        items(dated, key = { it.task.id }) { TaskWithSteps(it, viewModel, actions, onOpenReminders, rowKeys) }
    }
}

/** 14e — what a row needs to draw its keyboard state and answer ↵: the cursor, its own index, the pending menu request. */
internal class RowKeys(
    val state: ListKeyState,
    val indexOf: (Entry) -> Int,
    val menuFor: Long?,
    val onMenuShown: () -> Unit,
)

/** What a task row can open, handed down once rather than threaded as four lambdas. */
internal class TaskRowActions(
    val onPostpone: (Entry) -> Unit,
    val onAddSubtask: (Entry) -> Unit,
    val onSetDeadline: (Entry) -> Unit,
    /** 14g·3 — the ladder's stripe is drawn, and the level offered, while Settings says so (on by default). */
    val showUrgency: Boolean,
    /** §0.6.5 — what runs now, so each row knows whether it draws ▶ or ■. */
    val runningTarget: TrackTarget?,
    /** §0.8 step 7d — minutes logged today by entry id; absent means nothing to say. */
    val loggedToday: Map<Long, Int>,
    /** 14f·1 — on a wide window a click or ↵ selects the row for the pane; null on the phone. */
    val onSelect: ((Entry) -> Unit)? = null,
    val selectedId: Long? = null,
)

@Composable
private fun TaskWithSteps(
    group: TaskWithSubtasks,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onOpenReminders: (Entry) -> Unit,
    rowKeys: RowKeys? = null,
) {
    Column {
        TaskRow(group.task, viewModel, actions, onOpenReminders, stepsDone = group.done, stepsTotal = group.subtasks.size, rowKeys = rowKeys)
        group.subtasks.forEach { step -> TaskRow(step, viewModel, actions, onOpenReminders, isStep = true, rowKeys = rowKeys) }
    }
}

/**
 * One task row. 14d's rule reaches it in 14f·1: the `···` fades in on hover under a pointer
 * profile (always shown under Touch), right-click opens the same menu at the pointer, the
 * phone's long-press opens it too. On a wide window a click selects the row for the pane and
 * paints it `primaryContainer` — distinct from 14e's keyboard ring.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    entry: Entry,
    viewModel: TasksHabitsViewModel,
    actions: TaskRowActions,
    onOpenReminders: (Entry) -> Unit,
    isStep: Boolean = false,
    stepsDone: Int = 0,
    stepsTotal: Int = 0,
    rowKeys: RowKeys? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pointer = LocalDensityProfile.current != DensityProfile.TOUCH
    // 14e — the keyboard cursor: a 2 dp ring inside the row, distinct from any selection tint;
    // ↵ on the cursor opens this row's menu, and a click puts the cursor here.
    val index = rowKeys?.indexOf?.invoke(entry) ?: -1
    val keyFocused = rowKeys != null && index >= 0 && rowKeys.state.focused == index && keyboardCursorShown()
    val selected = actions.selectedId == entry.id
    LaunchedEffect(rowKeys?.menuFor) {
        if (rowKeys != null && rowKeys.menuFor == entry.id) { menuOpen = true; rowKeys.onMenuShown() }
    }
    val moreEndPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowSize = it }) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .combinedClickable(
                interactionSource = interaction, indication = null,
                onClick = { if (index >= 0) rowKeys?.state?.clickedRow(index); actions.onSelect?.invoke(entry) },
                onLongClick = { menuAt = null; menuOpen = true },
            )
            .onSecondaryClick { menuAt = it; menuOpen = true }
            .then(if (pointer) Modifier.heightIn(min = LocalDensityProfile.current.rowHeightDp.dp).padding(start = if (isStep) 32.dp else 8.dp, end = 16.dp)
                  else Modifier.padding(start = if (isStep) 32.dp else 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
            .then(if (keyFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // B§13.8.1 (14g·3) — urgency is the row's colour: a 4 dp stripe, the level the greater of
        // what was set and the deadline's pressure today; none draws nothing but keeps the width.
        if (actions.showUrgency) UrgencyStripe(urgencyOf(entry, LocalDate.now()), height = 22.dp) else Spacer(Modifier.width(4.dp))
        Spacer(Modifier.width(4.dp))
        Checkbox(
            checked = entry.status == EntryStatus.DONE,
            onCheckedChange = { checked -> viewModel.setDone(entry.id, checked) },
        )
        // The When, the Deadline and the steps, in that order and in one colour: a deadline
        // that has passed is information, not an alarm (§0.5.2). Under a pointer the meta sits
        // at the title's right as a caption (Things' date tag, Notion's list view — the tray PR,
        // 2026-09-16: one line, Notion's row); under Touch it stays the second line.
        val subtitle = listOfNotNull(
            entry.startDate?.toString(),
            entry.startTime?.toString(),
            entry.dueDate?.let { "due $it" },
            if (stepsTotal > 0) "$stepsDone/$stepsTotal steps" else null,
            loggedSegment(actions.loggedToday[entry.id] ?: 0, entry.estimate),
        ).joinToString(" · ")
        val title = keyedTitle(entry.title, rowKeys?.state?.typed.orEmpty(), keyFocused)
        if (pointer) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.body)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Under a pointer the buttons are the find bar's 28 dp with 18 dp glyphs: Material's
        // 40 dp `IconButton` would hold the one-line row at 44 dp (the tray PR, measured).
        val button = rowButtonModifier(pointer)
        val glyph = rowGlyphModifier(pointer)
        // §0.6.5 — start/stop, on steps too: a step is a task.
        TrackButton(TrackTarget.Entry(entry.id), actions.runningTarget, viewModel::toggleTracking, modifier = button, iconModifier = glyph)
        // No bell where nothing can fire (desktop, §0.8 step 7a): the sheet's absence is the signal.
        if (!isStep && LocalRemindersAvailable.current) {
            IconButton(onClick = { onOpenReminders(entry) }, modifier = button) {
                Icon(Icons.Filled.Notifications, contentDescription = stringResource(Res.string.reminders_open), modifier = glyph)
            }
        }
        IconButton(
            onClick = { menuAt = null; menuOpen = true },
            modifier = button.alpha(if (!pointer || hovered || menuOpen) 1f else 0f),
        ) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", modifier = glyph)
        }
    }
    PointerMenu(expanded = menuOpen, at = menuAt, fallback = IntOffset(maxOf(0, rowSize.width - moreEndPx), rowSize.height), onDismiss = { menuOpen = false }) {
        TaskMenuItems(entry, isStep, actions, viewModel) { menuOpen = false }
    }
    }
}

/** The row's menu — also the pane's chips, by name (14f·1). */
@Composable
private fun TaskMenuItems(entry: Entry, isStep: Boolean, actions: TaskRowActions, viewModel: TasksHabitsViewModel, close: () -> Unit) {
    if (!isStep) {
        DropdownMenuItem(text = { Text("Postpone…") }, onClick = { close(); actions.onPostpone(entry) })
        DropdownMenuItem(text = { Text("Add a step") }, onClick = { close(); actions.onAddSubtask(entry) })
        DropdownMenuItem(
            text = { Text(if (entry.dueDate == null) "Set deadline…" else "Change deadline…") },
            onClick = { close(); actions.onSetDeadline(entry) },
        )
    }
    if (actions.showUrgency) {
        // 14g·3 — the level as a second menu anchored to the item: five rows, a dot each.
        val set = Urgency.fromLevel(entry.importance)
        SubmenuItem(text = { Text("Urgency: " + set.label) }, leadingIcon = { UrgencyDot(set) }) { closeLevels ->
            Urgency.entries.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.label) },
                    leadingIcon = { UrgencyDot(u) },
                    trailingIcon = if (u == set) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    onClick = { closeLevels(); close(); viewModel.setImportance(entry.id, u.level) },
                )
            }
        }
    }
    DropdownMenuItem(
        text = { Text("Move to Trash") },
        leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
        onClick = { close(); viewModel.trashTask(entry.id) },
    )
}

@Composable
private fun HabitsList(
    habits: List<Habit>,
    viewModel: TasksHabitsViewModel,
    showStreaks: Boolean,
    runningTarget: TrackTarget?,
    loggedToday: Map<Long, Int>,
    onOpen: (Habit) -> Unit,
    onAdd: () -> Unit,
    selectedId: Long? = null,
) {
    if (habits.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.LocalFireDepartment,
            message = stringResource(Res.string.empty_habits_message),
            modifier = Modifier.fillMaxSize(),
            ctaLabel = stringResource(Res.string.empty_habits_cta),
            onCta = onAdd,
        )
        return
    }
    // 14f·1 — the keyboard on the habits list too: ↑ ↓ ↵ opens (the phone's sheet, the desktop's pane).
    val keyState = remember { ListKeyState() }
    TypeAheadReset(keyState)
    BackHandler(enabled = keyState.hasSomethingToClear) { keyState.clear() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().listKeyboard(
            state = keyState,
            count = { habits.size },
            titles = { habits.map { it.title } },
            onOpen = { i -> habits.getOrNull(i)?.let(onOpen) },
        ),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        itemsIndexed(habits, key = { _, it -> it.id }) { index, habit ->
            HabitRow(
                habit, viewModel, showStreaks, runningTarget, loggedToday,
                onOpen = { keyState.clickedRow(index); onOpen(habit) },
                keyFocused = keyState.focused == index && keyboardCursorShown(),
                typed = keyState.typed,
                selected = selectedId == habit.id,
            )
        }
    }
}

/**
 * One habit row. §0.6.6 — the row opens the presence view; the checkbox stays its own target.
 * 14f·1: the trailing × that trashed a habit in one tap is a `···` menu now (hover-revealed
 * under a pointer, right-click and long-press open it), so a destructive verb is one step in,
 * as a task's is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(
    habit: Habit,
    viewModel: TasksHabitsViewModel,
    showStreaks: Boolean,
    runningTarget: TrackTarget?,
    loggedToday: Map<Long, Int>,
    onOpen: () -> Unit,
    keyFocused: Boolean = false,
    typed: String = "",
    selected: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pointer = LocalDensityProfile.current != DensityProfile.TOUCH
    val moreEndPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowSize = it }) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onOpen, onLongClick = { menuAt = null; menuOpen = true })
            .onSecondaryClick { menuAt = it; menuOpen = true }
            .then(if (pointer) Modifier.heightIn(min = LocalDensityProfile.current.rowHeightDp.dp).padding(horizontal = 16.dp) else Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            .then(if (keyFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val checkedToday = habit.lastCompletedDate == LocalDate.now()
        Checkbox(
            checked = checkedToday,
            onCheckedChange = { checked ->
                if (checked) viewModel.checkInHabit(habit.id) else viewModel.undoCheckInHabit(habit.id)
            },
        )
        // §3.3 — time and duration are what distinguish a habit that sits at an hour from one
        // that just needs doing sometime today, so both show when set and neither takes room
        // when not. Under a pointer the line sits at the title's right (one row, the tray PR);
        // under Touch it is the second line.
        val meta = listOfNotNull(
            "Every ${habit.frequency.count} ${habit.frequency.unit.name.lowercase()}(s)",
            habit.time?.toString(),
            habit.duration?.let(::formatHabitDuration),
            // §0.6.6 — retired from the row by default; a plain number when asked for.
            if (showStreaks && habit.streak > 0) "streak ${habit.streak}" else null,
            loggedToday[habit.id]?.let { formatMinutes(it) + " today" },
        ).joinToString(" · ")
        if (pointer) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(keyedTitle(habit.title, typed, keyFocused), style = MaterialTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(meta, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(keyedTitle(habit.title, typed, keyFocused), style = MaterialTheme.typography.body)
                Text(meta, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val button = rowButtonModifier(pointer)
        val glyph = rowGlyphModifier(pointer)
        TrackButton(TrackTarget.Habit(habit.id), runningTarget, viewModel::toggleTracking, modifier = button, iconModifier = glyph)
        IconButton(onClick = { menuAt = null; menuOpen = true }, modifier = button.alpha(if (!pointer || hovered || menuOpen) 1f else 0f)) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", modifier = glyph)
        }
    }
    PointerMenu(expanded = menuOpen, at = menuAt, fallback = IntOffset(maxOf(0, rowSize.width - moreEndPx), rowSize.height), onDismiss = { menuOpen = false }) {
        DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
        DropdownMenuItem(text = { Text("Move to Trash") }, leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) }, onClick = { menuOpen = false; viewModel.trashHabit(habit.id) })
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
    onOpenHabit: (Habit) -> Unit,
    selectedHabitId: Long? = null,
) {
    // Merged is day-shaped, so sub-tasks are folded into their parent's step count here rather
    // than listed: the steps are undated and would only lengthen a list meant to be read as a day.
    val dated = tasks.withSubtasks().filter { it.task.startDate != null && inFilterRange(it.task.startDate, filter) }
    val timedHabits = habits.filter { it.time != null }
    if (dated.isEmpty() && habits.isEmpty()) {
        EmptyState(icon = Icons.Filled.Check, message = stringResource(Res.string.empty_tasks_message), modifier = Modifier.fillMaxSize())
        return
    }
    // 14f·1 — one cursor over both kinds of row: the tasks, then the timed habits.
    val keyState = remember { ListKeyState() }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    TypeAheadReset(keyState)
    BackHandler(enabled = keyState.hasSomethingToClear) { keyState.clear() }
    val taskEntries = dated.map { it.task }
    val indexOf: (Entry) -> Int = { e -> taskEntries.indexOfFirst { it.id == e.id } }
    val rowKeys = RowKeys(keyState, indexOf, menuFor, { menuFor = null })
    val count = taskEntries.size + timedHabits.size
    LazyColumn(
        modifier = Modifier.fillMaxSize().listKeyboard(
            state = keyState,
            count = { count },
            titles = { taskEntries.map { it.title } + timedHabits.map { it.title } },
            onOpen = { i ->
                if (i < taskEntries.size) taskEntries[i].let { e -> actions.onSelect?.invoke(e) ?: run { menuFor = e.id } }
                else timedHabits.getOrNull(i - taskEntries.size)?.let(onOpenHabit)
            },
        ),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(dated, key = { "task_${it.task.id}" }) { TaskRow(it.task, viewModel, actions, onOpenReminders, stepsDone = it.done, stepsTotal = it.subtasks.size, rowKeys = rowKeys) }
        // Habits with a time get a delicate highlight to distinguish them (§3.3).
        itemsIndexed(timedHabits, key = { _, it -> "habit_${it.id}" }) { i, habit ->
            val index = taskEntries.size + i
            val keyFocused = keyState.focused == index && keyboardCursorShown()
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (selectedHabitId == habit.id) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { keyState.clickedRow(index); onOpenHabit(habit) }
                        .padding(8.dp)
                        .then(if (keyFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(start = 8.dp))
                    Column {
                        Text(keyedTitle(habit.title, keyState.typed, keyFocused), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        habit.time?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

/** A row's button under a pointer: the find bar's 28 dp; Material's 40 dp under Touch. */
private fun rowButtonModifier(pointer: Boolean): Modifier = if (pointer) Modifier.size(28.dp) else Modifier
/** Its glyph: 18 dp under a pointer, Material's 24 under Touch. */
private fun rowGlyphModifier(pointer: Boolean): Modifier = if (pointer) Modifier.size(18.dp) else Modifier
