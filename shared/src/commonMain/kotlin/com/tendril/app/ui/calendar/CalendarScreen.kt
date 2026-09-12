@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.calendar

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.ui.track.TrackButton
import com.tendril.app.ui.track.runningTargetState
import com.tendril.app.domain.plan.unplannedTasks
import com.tendril.app.domain.plan.timelineBlocks
import com.tendril.app.domain.plan.TimelineExtra
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.generated.resources.calendar_view_agenda
import java.time.LocalTime
import com.tendril.app.data.habit.Habit
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import kotlin.math.roundToInt
import com.tendril.app.ui.entries.EntryEditSheet
import com.tendril.app.domain.MoveScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.domain.recurrence.EntryOccurrence
import com.tendril.app.domain.recurrence.EntryOccurrences
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.EmptyState
import org.jetbrains.compose.resources.stringResource
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.calendar_quick_add_hint
import com.tendril.app.generated.resources.calendar_view_day
import com.tendril.app.generated.resources.calendar_view_month
import com.tendril.app.generated.resources.calendar_view_week
import com.tendril.app.generated.resources.nav_calendar
import com.tendril.app.generated.resources.reminders_open
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.domain.QuickAddParser
import com.tendril.app.domain.TokenKind
import com.tendril.app.ui.entries.QuickAddPreview
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class CalendarView { DAY, WEEK, MONTH, AGENDA }

/**
 * §0.8 step 6d — a row on the Calendar that is not an Entry: a habit at its time (§3.2's "Show
 * Habits"), or a database row's DATE cell. Drawn beside the occurrences, never edited or dragged
 * here — a habit is Tasks & Habits' to change, a cell is its database's.
 */
private sealed class CalendarExtra {
    abstract val date: LocalDate
    abstract val title: String
    abstract val subtitle: String

    data class HabitAt(val habit: Habit, override val date: LocalDate) : CalendarExtra() {
        override val title get() = habit.title
        override val subtitle get() = habit.time?.toString().orEmpty() + (habit.duration?.let { " · ${it.toMinutes()} min" } ?: "")
    }

    data class RowDate(val cell: DatedCell) : CalendarExtra() {
        override val date get() = cell.date
        override val title get() = cell.title
        override val subtitle get() = cell.propertyName
    }
}

/** Habits on every day of the range, and the date cells that fall inside it. */
private fun extrasIn(from: LocalDate, to: LocalDate, layers: CalendarLayers, habits: List<Habit>, cells: List<DatedCell>): List<CalendarExtra> {
    val out = mutableListOf<CalendarExtra>()
    if (layers.habits) {
        var day = from
        while (!day.isAfter(to)) {
            habits.forEach { out += CalendarExtra.HabitAt(it, day) }
            day = day.plusDays(1)
        }
    }
    if (layers.databaseDates) cells.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.forEach { out += CalendarExtra.RowDate(it) }
    return out.sortedWith(compareBy({ it.date }, { (it as? CalendarExtra.HabitAt)?.habit?.time ?: LocalTime.MAX }, { it.title }))
}

/**
 * §3.2, in `shared/` since §0.8 step 6a (the same move step 1 made for the Canvas). The two
 * surfaces that only exist on Android arrive as slots: [settingsSheet] is the Google Calendar
 * connect/disconnect sheet (Play Services, §9.5) and [reminderSheet] the alarms-backed
 * Reminders sheet (§5.4) — null hides the bell, which is desktop's case, where nothing fires.
 */
@Composable
fun CalendarScreen(
    core: WorkbenchCore,
    settingsSheet: @Composable (onDismiss: () -> Unit) -> Unit,
    reminderSheet: (@Composable (entry: Entry, onDismiss: () -> Unit) -> Unit)?,
    /** §0.8 step 6d — a database row's date opens its page. */
    onOpenPage: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** §0.6.4 — the Settings switch that shows the importance flag; desktop has no Settings yet. */
    showImportant: Boolean = false,
) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(core.database.entryDao(), core.resolveEntryUseCase, core.entryScheduleCoordinator, core.entryEditor, core.database.habitDao(), core.database.propertyValueDao(), core.timeTracker) }
        }
    )
    // Defaults to Day, not Month (§2.2).
    var view by remember { mutableStateOf(CalendarView.DAY) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val allEntries by viewModel.entries.collectAsState()
    val layers by viewModel.layers.collectAsState()
    val timedHabits by viewModel.timedHabits.collectAsState()
    val dateCells by viewModel.dateCells.collectAsState()
    // §0.8 step 6d — the Tasks and Events layers filter the rows before expansion.
    val entries = remember(allEntries, layers) { allEntries.filter { if (it.kind == EntryKind.TASK) layers.tasks else layers.events } }
    var showSettings by remember { mutableStateOf(false) }
    // §5.4 — reminders open over the tapped Entry; null means closed.
    var reminderTarget by remember { mutableStateOf<Entry?>(null) }
    // §0.8 step 6b — the edit sheet over a tapped row, and a dragged occurrence's "this one or
    // all?" once it has been dropped on another day.
    var editTarget by remember { mutableStateOf<Entry?>(null) }
    var pendingMove by remember { mutableStateOf<PendingMove?>(null) }
    // §0.8 step 7b — Plan mode on the Day view, and a block's "this one or all?" for a series.
    var planMode by remember { mutableStateOf(false) }
    var pendingTimeMove by remember { mutableStateOf<PendingTimeMove?>(null) }
    val allTasks by viewModel.tasks.collectAsState()
    val runningTarget by core.timeTracker.runningTargetState()

    editTarget?.let { entry ->
        EntryEditSheet(
            entry = entry,
            showImportant = showImportant,
            onSave = { viewModel.save(it); editTarget = null },
            onDelete = { viewModel.trash(entry.id); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }
    pendingTimeMove?.let { move ->
        AlertDialog(
            onDismissRequest = { pendingTimeMove = null },
            title = { Text("Move \"${move.occurrence.entry.title}\" to ${move.time}?") },
            text = { Text("This repeats. Move only this occurrence, or the whole series?") },
            confirmButton = { TextButton(onClick = { viewModel.moveTo(move.occurrence.entry, move.occurrence.startDate, move.time, MoveScope.THIS_ONE); pendingTimeMove = null }) { Text("This one") } },
            dismissButton = { TextButton(onClick = { viewModel.moveTo(move.occurrence.entry, move.occurrence.startDate, move.time, MoveScope.ALL); pendingTimeMove = null }) { Text("All") } },
        )
    }
    pendingMove?.let { move ->
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("Move \"${move.occurrence.entry.title}\"?") },
            text = { Text("This repeats. Move only this occurrence, or the whole series?") },
            confirmButton = { TextButton(onClick = { viewModel.move(move.occurrence.entry, move.occurrence.startDate, move.toDate, MoveScope.THIS_ONE); pendingMove = null }) { Text("This one") } },
            dismissButton = { TextButton(onClick = { viewModel.move(move.occurrence.entry, move.occurrence.startDate, move.toDate, MoveScope.ALL); pendingMove = null }) { Text("All") } },
        )
    }

    if (showSettings) settingsSheet { showSettings = false }

    reminderTarget?.let { entry ->
        reminderSheet?.invoke(entry) { reminderTarget = null }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.nav_calendar)) },
                // Horizontal three-dot icon (§2.2) — never the gear, which is reserved for
                // the main Settings tab.
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Calendar settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                CalendarView.entries.forEachIndexed { index, v ->
                    SegmentedButton(
                        selected = view == v,
                        onClick = { view = v },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarView.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (v) {
                                    CalendarView.DAY -> Res.string.calendar_view_day
                                    CalendarView.WEEK -> Res.string.calendar_view_week
                                    CalendarView.MONTH -> Res.string.calendar_view_month
                                    CalendarView.AGENDA -> Res.string.calendar_view_agenda
                                }
                            )
                        )
                    }
                }
            }
            // §0.8 step 6d — layers. Tasks and Events on by default; Habits and Database dates
            // opt-in, so the Calendar is today's until someone asks for more on it (E12).
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = layers.tasks, onClick = { viewModel.setLayers(layers.copy(tasks = !layers.tasks)) }, label = { Text("Tasks") })
                FilterChip(selected = layers.events, onClick = { viewModel.setLayers(layers.copy(events = !layers.events)) }, label = { Text("Events") })
                FilterChip(selected = layers.habits, onClick = { viewModel.setLayers(layers.copy(habits = !layers.habits)) }, label = { Text("Habits") })
                FilterChip(selected = layers.databaseDates, onClick = { viewModel.setLayers(layers.copy(databaseDates = !layers.databaseDates)) }, label = { Text("Database dates") })
                if (view == CalendarView.DAY) {
                    // §0.6.5 — the day as a timeline, with a rail of what is not placed yet.
                    FilterChip(selected = planMode, onClick = { planMode = !planMode }, label = { Text("Plan") })
                }
            }

            // §4.1 — the stored rows are expanded into occurrences before anything is drawn:
            // a recurring EVENT contributes one per occurrence in the window, and a multi-day
            // one contributes a row on every day it covers. Filtering `it.startDate == day`
            // straight off the rows, as this used to, showed a weekly series exactly once and
            // a three-day event on day one only. `remember`ed per range so panning a month
            // doesn't re-expand on every recomposition.
            val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
            val gridMonth = YearMonth.from(selectedDate)
            val agendaFrom = LocalDate.now()
            val agendaTo = agendaFrom.plusDays(AGENDA_DAYS - 1L)
            val range = when (view) {
                CalendarView.DAY -> selectedDate to selectedDate
                CalendarView.WEEK -> weekStart to weekStart.plusDays(6)
                CalendarView.MONTH -> gridMonth.atDay(1) to gridMonth.atEndOfMonth()
                CalendarView.AGENDA -> agendaFrom to agendaTo
            }
            val occurrences = remember(entries, view, selectedDate) {
                if (view == CalendarView.DAY) EntryOccurrences.onDay(entries, selectedDate)
                else EntryOccurrences.expand(entries, range.first, range.second)
            }
            val extras = remember(layers, timedHabits, dateCells, range) { extrasIn(range.first, range.second, layers, timedHabits, dateCells) }

            when (view) {
                CalendarView.DAY -> DayView(
                    date = selectedDate,
                    occurrences = occurrences,
                    extras = extras,
                    onOpenPage = onOpenPage,
                    planMode = planMode,
                    unplanned = remember(allTasks, selectedDate) { unplannedTasks(allTasks, selectedDate) },
                    onPlace = { entry, time -> viewModel.place(entry, selectedDate, time) },
                    onMoveBlock = { occurrence, time ->
                        val entry = occurrence.entry
                        if (entry.recurrenceRule != null && entry.originalEntryId == null) pendingTimeMove = PendingTimeMove(occurrence, time)
                        else viewModel.moveTo(entry, occurrence.startDate, time, MoveScope.ALL)
                    },
                    runningTarget = runningTarget,
                    onToggleTracking = viewModel::toggleTracking,
                    onPrev = { selectedDate = selectedDate.minusDays(1) },
                    onNext = { selectedDate = selectedDate.plusDays(1) },
                    onQuickAdd = { viewModel.quickAdd(it, selectedDate) },
                    onSetDone = viewModel::setDone,
                    onOpenReminders = if (reminderSheet != null) { { reminderTarget = it } } else null,
                    onEdit = { editTarget = it },
                )
                CalendarView.WEEK -> WeekStripView(
                    selectedDate = selectedDate,
                    occurrences = occurrences,
                    extras = extras,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                    onMove = { occurrence, toDate ->
                        val entry = occurrence.entry
                        if (entry.recurrenceRule != null && entry.originalEntryId == null) pendingMove = PendingMove(occurrence, toDate)
                        else viewModel.move(entry, occurrence.startDate, toDate, MoveScope.ALL)
                    },
                )
                CalendarView.MONTH -> MonthGridView(
                    month = gridMonth,
                    occurrences = occurrences,
                    extras = extras,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                    onMonthShift = { selectedDate = selectedDate.plusMonths(it.toLong()) },
                )
                CalendarView.AGENDA -> AgendaView(
                    from = agendaFrom,
                    to = agendaTo,
                    occurrences = occurrences,
                    extras = extras,
                    onSetDone = viewModel::setDone,
                    onEdit = { editTarget = it },
                    onOpenPage = onOpenPage,
                )
            }
        }
    }
}

@Composable
private fun DayView(
    date: LocalDate,
    occurrences: List<EntryOccurrence>,
    extras: List<CalendarExtra>,
    onOpenPage: (Long) -> Unit,
    planMode: Boolean,
    unplanned: List<Entry>,
    onPlace: (Entry, LocalTime) -> Unit,
    onMoveBlock: (EntryOccurrence, LocalTime) -> Unit,
    runningTarget: TrackTarget?,
    onToggleTracking: (TrackTarget) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQuickAdd: (ParsedEntry) -> Unit,
    onSetDone: (Long, Boolean) -> Unit,
    onOpenReminders: ((Entry) -> Unit)?,
    onEdit: (Entry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day") }
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next day") }
        }

        // §0.8 step 5 — the line is read as it is typed and previewed as chips; a chip's × says
        // "that was a word", the kind chip flips Task ↔ Event. Enter writes what the preview shows.
        var quickAddText by remember { mutableStateOf("") }
        var ignored by remember { mutableStateOf(emptySet<TokenKind>()) }
        var kindOverride by remember { mutableStateOf<EntryKind?>(null) }
        val parsed = remember(quickAddText, ignored, kindOverride) {
            QuickAddParser.parse(quickAddText, LocalDate.now(), EntryKind.EVENT, ignored, kindOverride)
        }
        OutlinedTextField(
            value = quickAddText,
            onValueChange = { quickAddText = it; ignored = emptySet(); kindOverride = null },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(Res.string.calendar_quick_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onQuickAdd(parsed)
                quickAddText = ""; ignored = emptySet(); kindOverride = null
            }),
        )
        if (quickAddText.isNotBlank()) {
            QuickAddPreview(
                parsed = parsed,
                onFlipKind = { kindOverride = if (parsed.kind == EntryKind.TASK) EntryKind.EVENT else EntryKind.TASK },
                onDrop = { ignored = ignored + it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        HorizontalDivider()

        if (planMode) {
            val habitExtras = extras.filterIsInstance<CalendarExtra.HabitAt>()
                .map { TimelineExtra("habit_${it.habit.id}", it.habit.title, it.habit.time!!, it.habit.duration, BlockKind.HABIT) }
            PlanView(
                day = date,
                blocks = remember(occurrences, habitExtras) { timelineBlocks(occurrences, habitExtras) },
                allDay = occurrences.filter { it.startTime == null || !it.isFirstDay },
                unplanned = unplanned,
                onEdit = onEdit,
                onPlace = onPlace,
                onMoveBlock = onMoveBlock,
            )
        } else if (occurrences.isEmpty() && extras.isEmpty()) {
            EmptyState(icon = Icons.Filled.ChevronRight, message = "Nothing scheduled", modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                // Already ordered by EntryOccurrences.expand (by time, untimed last, then
                // title) — the order this list was sorting into by hand. The key carries the
                // occurrence's start date as well as the row id: one series contributes many
                // occurrences, all sharing the base row's id.
                items(occurrences, key = { "${it.entry.id}:${it.startDate}" }) { occurrence ->
                    val entry = occurrence.entry
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.status != null) {
                            Checkbox(
                                checked = entry.status == EntryStatus.DONE,
                                onCheckedChange = { checked -> onSetDone(entry.id, checked) },
                            )
                        }
                        // §3.2 — the row itself opens the edit sheet (it had no tap target before 6b).
                        Column(modifier = Modifier.weight(1f).clickable { onEdit(entry) }) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                occurrenceSubtitle(occurrence, date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // §0.6.5 — start/stop here too: the Day view is where the day is worked from.
                        TrackButton(TrackTarget.Entry(entry.id), runningTarget, onToggleTracking)
                        if (onOpenReminders != null) IconButton(onClick = { onOpenReminders(entry) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(Res.string.reminders_open))
                        }
                    }
                }
                items(extras, key = { extraKey(it) }) { ExtraRow(it, onOpenPage) }
            }
        }
    }
}

private fun extraKey(extra: CalendarExtra): String = when (extra) {
    is CalendarExtra.HabitAt -> "habit_${extra.habit.id}:${extra.date}"
    is CalendarExtra.RowDate -> "cell_${extra.cell.pageId}:${extra.cell.propertyName}:${extra.date}"
}

/** A habit at its time, or a row's date — the same row shape as an occurrence, tinted. A row
 * date opens its page; a habit is read here and changed on Tasks & Habits. */
@Composable
private fun ExtraRow(extra: CalendarExtra, onOpenPage: (Long) -> Unit) {
    val open: (() -> Unit)? = (extra as? CalendarExtra.RowDate)?.let { { onOpenPage(it.cell.pageId) } }
    Row(
        modifier = Modifier.fillMaxWidth().then(if (open != null) Modifier.clickable { open() } else Modifier).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (extra is CalendarExtra.HabitAt) Icons.Filled.LocalFireDepartment else Icons.Filled.TableChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(extra.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(extra.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val AGENDA_DAYS = 30

/** §0.8 step 6c — the next [AGENDA_DAYS] days as one list, grouped by day, empty days skipped. */
@Composable
private fun AgendaView(
    from: LocalDate,
    to: LocalDate,
    occurrences: List<EntryOccurrence>,
    extras: List<CalendarExtra>,
    onSetDone: (Long, Boolean) -> Unit,
    onEdit: (Entry) -> Unit,
    onOpenPage: (Long) -> Unit,
) {
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
        .map { day -> day to (occurrences.filter { it.date == day } to extras.filter { it.date == day }) }
        .filter { (_, lists) -> lists.first.isNotEmpty() || lists.second.isNotEmpty() }
        .toList()
    if (days.isEmpty()) {
        EmptyState(icon = Icons.Filled.ChevronRight, message = "Nothing in the next $AGENDA_DAYS days", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        days.forEach { (day, lists) ->
            item(key = "day_$day") {
                Text(
                    if (day == LocalDate.now()) "Today · " + day.format(DateTimeFormatter.ofPattern("EEE d MMM")) else day.format(DateTimeFormatter.ofPattern("EEEE d MMM")),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(lists.first, key = { "occ_${it.entry.id}:${it.startDate}:$day" }) { occurrence ->
                val entry = occurrence.entry
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.status != null) Checkbox(checked = entry.status == EntryStatus.DONE, onCheckedChange = { onSetDone(entry.id, it) })
                    Column(modifier = Modifier.weight(1f).clickable { onEdit(entry) }) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                        Text(occurrenceSubtitle(occurrence, day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(lists.second, key = { extraKey(it) }) { ExtraRow(it, onOpenPage) }
            item(key = "div_$day") { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

/**
 * The line under an occurrence's title. A multi-day span (§4.1) says which day of it this is,
 * since otherwise the same title appears on three consecutive days with nothing to tell them
 * apart, and the middle and last days of a span have no start time of their own to show.
 */
private fun occurrenceSubtitle(occurrence: EntryOccurrence, day: LocalDate): String {
    val base = (if (occurrence.isFirstDay) occurrence.startTime?.toString() else null) ?: "All day"
    if (!occurrence.isMultiDay) return base
    val total = ChronoUnit.DAYS.between(occurrence.startDate, occurrence.endDate) + 1
    val index = ChronoUnit.DAYS.between(occurrence.startDate, day) + 1
    return "$base · day $index of $total"
}

/** A dragged occurrence dropped on a day, waiting for "this one or all?". */
private data class PendingMove(val occurrence: EntryOccurrence, val toDate: LocalDate)

/** A block dragged to another hour on Plan mode's grid, waiting for the same question. */
private data class PendingTimeMove(val occurrence: EntryOccurrence, val time: LocalTime)

/**
 * Cards layout (§2.2) — the Grid/hour-grid alternative is a later refinement. §0.8 step 6b —
 * an occurrence line can be long-pressed and dragged onto another day's card; the drop is a move
 * (or, on a series, the question). The cards report their bounds so the drop knows which day is
 * under the finger; a ghost of the title follows it.
 */
@Composable
private fun WeekStripView(
    selectedDate: LocalDate,
    occurrences: List<EntryOccurrence>,
    extras: List<CalendarExtra>,
    onSelectDate: (LocalDate) -> Unit,
    onMove: (EntryOccurrence, LocalDate) -> Unit,
) {
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val cardBounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    var dragging by remember { mutableStateOf<EntryOccurrence?>(null) }
    // Root coordinates throughout: the cards report `boundsInRoot`, the ghost subtracts the
    // Box's own origin when it draws.
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    val hoverDay = dragging?.let { d -> cardBounds.entries.firstOrNull { it.value.contains(dragPosition) }?.key?.takeIf { it != d.date } }

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { boxOrigin = it.boundsInRoot().topLeft }) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(days) { day ->
                val dayEntries = occurrences.filter { it.date == day }
                val dayExtras = extras.filter { it.date == day }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .onGloballyPositioned { cardBounds[day] = it.boundsInRoot() },
                    tonalElevation = if (hoverDay == day) 4.dp else 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    onClick = { onSelectDate(day) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.dayOfMonth}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (dayEntries.isEmpty() && dayExtras.isEmpty()) {
                            Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            dayEntries.take(3).forEach { occurrence ->
                                var lineOrigin by remember(occurrence) { mutableStateOf(Offset.Zero) }
                                Text(
                                    "• ${occurrence.entry.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.onGloballyPositioned { lineOrigin = it.boundsInRoot().topLeft }.pointerInput(occurrence) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { start ->
                                                dragging = occurrence
                                                dragPosition = lineOrigin + start
                                            },
                                            onDrag = { change, delta -> change.consume(); dragPosition += delta },
                                            onDragEnd = {
                                                val target = cardBounds.entries.firstOrNull { it.value.contains(dragPosition) }?.key
                                                val moved = dragging
                                                dragging = null
                                                if (moved != null && target != null && target != moved.date) onMove(moved, target)
                                            },
                                            onDragCancel = { dragging = null },
                                        )
                                    },
                                )
                            }
                            if (dayEntries.size > 3) Text("+${dayEntries.size - 3} more", style = MaterialTheme.typography.bodySmall)
                            dayExtras.take(3).forEach { Text("◦ ${it.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            if (dayExtras.size > 3) Text("+${dayExtras.size - 3} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        dragging?.let { d ->
            Surface(
                modifier = Modifier.offset { IntOffset((dragPosition.x - boxOrigin.x).roundToInt() + 16, (dragPosition.y - boxOrigin.y).roundToInt() - 16) },
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(d.entry.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun MonthGridView(month: YearMonth, occurrences: List<EntryOccurrence>, extras: List<CalendarExtra>, onSelectDate: (LocalDate) -> Unit, onMonthShift: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onMonthShift(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month") }
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${month.year}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onMonthShift(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month") }
        }

        val firstOfMonth = month.atDay(1)
        val leadingBlanks = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val totalDays = month.lengthOfMonth()
        val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..totalDays).map { month.atDay(it) }

        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(cells) { day ->
                if (day == null) {
                    Box(modifier = Modifier.size(40.dp))
                } else {
                    val count = occurrences.count { it.date == day } + extras.count { it.date == day }
                    Column(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelectDate(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${day.dayOfMonth}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (count > 0) {
                            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
