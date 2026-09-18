@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.theme.LocalTendrilPalette
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
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.ui.backhandler.BackHandler
import com.tendril.app.ui.nav.LocalShellLayout
import com.tendril.app.domain.plan.trayDrawnWidthDp
import com.tendril.app.domain.plan.trayTasks
import com.tendril.app.ui.components.PaneHandle
import com.tendril.app.ui.components.PaneWidthState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.tendril.app.ui.nav.ShellLayout
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.plan.loggedByEntry
import com.tendril.app.domain.plan.loggedSegment
import com.tendril.app.domain.plan.loggedSpans
import com.tendril.app.domain.plan.plannedMinutes
import com.tendril.app.domain.track.formatMinutes
import com.tendril.app.domain.track.loggedMinutes
import java.time.Instant
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.ui.track.TrackButton
import com.tendril.app.ui.track.runningTargetState
import com.tendril.app.domain.plan.unplannedTasks
import com.tendril.app.domain.plan.timelineBlocks
import com.tendril.app.domain.plan.TimelineExtra
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.domain.plan.DotKind
import com.tendril.app.ui.components.TendrilMenuItem
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.MenuCheck
import com.tendril.app.ui.components.BarPillButton
import com.tendril.app.ui.components.BarMenuButton
import com.tendril.app.domain.plan.monthDots
import com.tendril.app.domain.plan.monthGridDays
import com.tendril.app.domain.plan.monthRange
import com.tendril.app.domain.plan.dayLabel
import com.tendril.app.ui.theme.eyebrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.Color
import com.tendril.app.generated.resources.calendar_view_agenda
import java.time.LocalTime
import com.tendril.app.data.habit.Habit
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.clip
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
import com.tendril.app.ui.entries.quickAddTokensTransformation
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.components.ListInteractiveMinimum
import com.tendril.app.ui.components.TitleAndMeta
import com.tendril.app.ui.components.listRow
import com.tendril.app.ui.components.rowButtonModifier
import com.tendril.app.ui.components.rowGlyphModifier
import com.tendril.app.ui.nav.LocalDensityProfile

enum class CalendarView { DAY, WEEK, MONTH, AGENDA }

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
    /** 14g·3 — the Settings switch that shows the urgency ladder (on by default, both platforms). */
    showUrgency: Boolean = true,
) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(core.database.entryDao(), core.resolveEntryUseCase, core.entryScheduleCoordinator, core.entryEditor, core.database.habitDao(), core.database.propertyValueDao(), core.timeTracker, core.keyValueStore) }
        }
    )
    // 14f·2 — the opening view is a Settings choice (`calendar_default_view`); with nothing
    // stored, Week on a wide window and Day otherwise (§2.2's phone default, not Month).
    val wide = LocalShellLayout.current == ShellLayout.RAIL
    var view by remember {
        mutableStateOf(
            when (defaultCalendarView(core.keyValueStore.get(CALENDAR_DEFAULT_VIEW_KEY), wide)) {
                CalendarViewKey.DAY -> CalendarView.DAY
                CalendarViewKey.WEEK -> CalendarView.WEEK
                CalendarViewKey.MONTH -> CalendarView.MONTH
                CalendarViewKey.AGENDA -> CalendarView.AGENDA
            }
        )
    }
    // 14f·2 — quick add's home on a wide window: a strip under the bar, opened from it; Esc closes.
    var quickAddOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = quickAddOpen) { quickAddOpen = false }
    // 14f·2 — a block dragged in the week grid: date and time in one gesture; a series asks first.
    var pendingGridMove by remember { mutableStateOf<PendingGridMove?>(null) }
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
    // B§13.6 #5 — the task tray beside the week (a strip under the Week strip and the Month on
    // the phone) and the drag off it: the screen owns the drag, draws the ghost, and asks the
    // grid's geometry or the day cells where it would land. Root coordinates throughout.
    val today = LocalDate.now()
    val tray = remember(allTasks, today) { trayTasks(allTasks, today) }
    val trayWidth = remember { PaneWidthState(core.keyValueStore, CALENDAR_TRAY_WIDTH_KEY, 280, 240, 360) }
    var trayCollapsed by remember { mutableStateOf(core.keyValueStore.getBoolean(CALENDAR_TRAY_COLLAPSED_KEY, false)) }
    fun setTrayCollapsed(v: Boolean) { trayCollapsed = v; core.keyValueStore.putBoolean(CALENDAR_TRAY_COLLAPSED_KEY, v) }
    var trayDrag by remember { mutableStateOf<TrayDrag?>(null) }
    var trayBounds by remember { mutableStateOf(Rect.Zero) }
    var gridGeometry by remember { mutableStateOf<WeekGeometry?>(null) }
    var gridDragPosition by remember { mutableStateOf<Offset?>(null) }
    var gridDragTitle by remember { mutableStateOf<String?>(null) }
    val dayCells = remember { mutableStateMapOf<LocalDate, Rect>() }
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val trayShown = if (wide) view == CalendarView.WEEK else (view == CalendarView.WEEK || view == CalendarView.MONTH)
    // Where the tray's drag would land: the grid's target on a wide window, a day cell otherwise.
    val trayGridTarget: WeekDrop? = trayDrag?.let { d -> if (wide) gridGeometry?.let { weekDropAt(d.position, it) } else null }
    val trayCellTarget: LocalDate? = trayDrag?.let { d -> if (!wide) dayCellAt(d.position, dayCells) else null }
    val dropOnTray = gridDragPosition?.let { trayBounds.contains(it) } == true
    fun dropTray(entry: Entry) {
        when (val t = trayGridTarget) {
            is WeekDrop.Slot -> viewModel.place(entry, t.day, t.time)
            is WeekDrop.AllDay -> viewModel.move(entry, entry.startDate ?: t.day, t.day, MoveScope.ALL)
            null -> trayCellTarget?.let { day -> viewModel.move(entry, entry.startDate ?: day, day, MoveScope.ALL) }
        }
    }
    val runningTarget by core.timeTracker.runningTargetState()
    // §0.8 step 7d — the selected day's logs and a ticking now, for Planned · Logged.
    val dayLogsNow by remember(selectedDate) { viewModel.logsOn(selectedDate) }.collectAsState(initial = emptyList<TimeLog>() to Instant.now())

    editTarget?.let { entry ->
        EntryEditSheet(
            entry = entry,
            showUrgency = showUrgency,
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
    pendingGridMove?.let { move ->
        AlertDialog(
            onDismissRequest = { pendingGridMove = null },
            title = { Text("Move \"${move.occurrence.entry.title}\" to ${move.toDate} ${move.time}?") },
            text = { Text("This repeats. Move only this occurrence, or the whole series?") },
            confirmButton = { TextButton(onClick = { viewModel.moveBlock(move.occurrence.entry, move.occurrence.startDate, move.toDate, move.time, MoveScope.THIS_ONE); pendingGridMove = null }) { Text("This one") } },
            dismissButton = { TextButton(onClick = { viewModel.moveBlock(move.occurrence.entry, move.occurrence.startDate, move.toDate, move.time, MoveScope.ALL); pendingGridMove = null }) { Text("All") } },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ShellTopBar(
                // L5 — on a wide window the bar is the Calendar's whole chrome (Pages' rule): the
                // range and its ‹ › and *Today* in the title slot, the view and the layers as
                // menus in the actions; the grids draw no ‹ › row of their own there.
                title = {
                    if (wide) CalendarBarTitle(view, selectedDate, onShift = { selectedDate = shiftedAnchor(view, selectedDate, it) }, onToday = { selectedDate = LocalDate.now() })
                    else Text(stringResource(Res.string.nav_calendar))
                },
                navigationIcon = {
                    if (wide && trayShown && trayCollapsed) {
                        IconButton(onClick = { setTrayCollapsed(false) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Show the tray") }
                    }
                },
                // Horizontal three-dot icon (§2.2) — never the gear, which is reserved for
                // the main Settings tab.
                actions = {
                    if (wide) {
                        ViewMenuButton(view, onView = { view = it }, planMode = planMode, onPlanMode = { planMode = it })
                        LayersMenuButton(layers, onLayers = viewModel::setLayers)
                    }
                    if (wide) IconButton(onClick = { quickAddOpen = !quickAddOpen }) {
                        Icon(Icons.Outlined.AddCircle, contentDescription = "Quick add")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Calendar settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).onGloballyPositioned { layerOrigin = it.boundsInRoot().topLeft }) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (wide && quickAddOpen) {
                QuickAddBar(today = selectedDate, onQuickAdd = { viewModel.quickAdd(it, selectedDate) }, onClose = { quickAddOpen = false })
            }
            // L5 — the segmented row and the layer chips are the phone's form; a wide window's
            // bar holds both (`ViewMenuButton`, `LayersMenuButton`).
            if (!wide) {
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
                CalendarView.MONTH -> monthRange(gridMonth)   // L4 — the grid's 35 or 42 days, so adjacent-month cells carry their items
                CalendarView.AGENDA -> agendaFrom to agendaTo
            }
            val occurrences = remember(entries, view, selectedDate) {
                if (view == CalendarView.DAY) EntryOccurrences.onDay(entries, selectedDate)
                else EntryOccurrences.expand(entries, range.first, range.second)
            }
            val extras = remember(layers, timedHabits, dateCells, range) { extrasIn(range.first, range.second, layers, timedHabits, dateCells) }

            val trayDragStart: (Entry, Offset) -> Unit = { entry, pos -> trayDrag = TrayDrag(entry, pos) }
            val trayDragMove: (Offset) -> Unit = { delta -> trayDrag = trayDrag?.let { it.copy(position = it.position + delta) } }
            val trayDragEnd: () -> Unit = { trayDrag?.let { dropTray(it.entry) }; trayDrag = null }
            val trayDragCancel: () -> Unit = { trayDrag = null }
            // L8 — the tray draws at most 30 % of the pane (`trayDrawnWidthDp`); the handle still remembers 240–360.
            var paneWidthDp by remember { mutableStateOf(0f) }
            val density = LocalDensity.current
            Row(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { paneWidthDp = with(density) { it.width.toDp().value } }) {
            if (wide && trayShown && !trayCollapsed) {
                Box(modifier = Modifier.fillMaxHeight().zIndex(1f)) {
                    TaskTray(
                        tasks = tray, today = today, showUrgency = showUrgency, draggingId = trayDrag?.entry?.id, dropHere = dropOnTray,
                        onDragStart = trayDragStart, onDrag = trayDragMove, onDragEnd = trayDragEnd, onDragCancel = trayDragCancel,
                        onOpen = { editTarget = it }, onCollapse = { setTrayCollapsed(true) }, onBounds = { trayBounds = it },
                        modifier = Modifier.width((if (paneWidthDp > 0f) trayDrawnWidthDp(trayWidth.widthDp, paneWidthDp) else trayWidth.widthDp).dp),
                    )
                    PaneHandle(trayWidth, Modifier.align(Alignment.CenterEnd))
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    dayLogs = dayLogsNow.first,
                    now = dayLogsNow.second,
                    onPrev = { selectedDate = selectedDate.minusDays(1) },
                    onNext = { selectedDate = selectedDate.plusDays(1) },
                    onQuickAdd = { viewModel.quickAdd(it, selectedDate) },
                    showQuickAdd = !wide,
                    showNav = !wide,
                    onSetDone = viewModel::setDone,
                    onOpenReminders = if (reminderSheet != null) { { reminderTarget = it } } else null,
                    onEdit = { editTarget = it },
                    showUrgency = showUrgency,
                )
                CalendarView.WEEK -> if (wide) WeekGridView(
                    weekStart = weekStart,
                    occurrences = occurrences,
                    onGeometry = { gridGeometry = it },
                    externalTarget = trayGridTarget,
                    onDragPosition = { pos, title -> gridDragPosition = pos; gridDragTitle = title },
                    outsideTargetLabel = { pos -> if (trayBounds.contains(pos)) "→ Clear When" else null },
                    onDropOutside = { occurrence, pos ->
                        if (trayBounds.contains(pos)) viewModel.unschedule(occurrence.entry) {
                            scope.launch { snackbarHostState.showSnackbar("A series keeps its days — change it from its sheet") }
                        }
                    },
                    habitExtras = { day ->
                        extras.filterIsInstance<CalendarExtra.HabitAt>().filter { it.date == day }
                            .map { TimelineExtra("habit_${it.habit.id}", it.habit.title, it.habit.time!!, it.habit.duration, BlockKind.HABIT) }
                    },
                    onEdit = { editTarget = it },
                    showUrgency = showUrgency,
                    onMove = { occurrence, toDate, time ->
                        val entry = occurrence.entry
                        if (entry.recurrenceRule != null && entry.originalEntryId == null) pendingGridMove = PendingGridMove(occurrence, toDate, time)
                        else viewModel.moveBlock(entry, occurrence.startDate, toDate, time, MoveScope.ALL)
                    },
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                    onShiftWeek = { selectedDate = selectedDate.plusWeeks(it.toLong()) },
                    showNav = false,
                ) else WeekStripView(
                    selectedDate = selectedDate,
                    occurrences = occurrences,
                    extras = extras,
                    onCardBounds = { day, rect -> dayCells[day] = rect },
                    highlightDay = trayCellTarget,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                    onMove = { occurrence, toDate ->
                        val entry = occurrence.entry
                        if (entry.recurrenceRule != null && entry.originalEntryId == null) pendingMove = PendingMove(occurrence, toDate)
                        else viewModel.move(entry, occurrence.startDate, toDate, MoveScope.ALL)
                    },
                )
                CalendarView.MONTH -> if (wide) {
                    // L4 — titled cells on a wide window: occurrences and extras as the Week's chips; a
                    // chip drags to another cell through the strip's write (the series prompt included),
                    // an extra never lifts; the empty ground seeds the quick-add strip with the day.
                    val palette = LocalTendrilPalette.current
                    val taskTint = layerTint(BlockKind.TASK)
                    val eventTint = layerTint(BlockKind.EVENT)
                    val itemsByDay = remember(occurrences, extras, showUrgency, today, palette) {
                        val out = mutableMapOf<LocalDate, MutableList<MonthItem>>()
                        occurrences.forEach { o ->
                            val stripe = if (showUrgency && o.entry.kind == EntryKind.TASK) palette.urgencyColour(com.tendril.app.domain.urgency.urgencyOf(o.entry, today).level) else null
                            out.getOrPut(o.date) { mutableListOf() } += MonthItem(
                                key = "o_${o.entry.id}_${o.date}", title = o.entry.title, time = o.startTime?.takeIf { o.date == o.startDate },
                                tint = if (o.entry.kind == EntryKind.TASK) taskTint else eventTint, stripe = stripe, draggable = true, payload = o,
                            )
                        }
                        extras.forEach { x ->
                            out.getOrPut(x.date) { mutableListOf() } += when (x) {
                                is CalendarExtra.HabitAt -> MonthItem("h_${x.habit.id}_${x.date}", x.title, x.habit.time, palette.habitSoft, null, false, x)
                                is CalendarExtra.RowDate -> MonthItem("d_${x.cell.pageId}_${x.date}", x.title, null, palette.thirdSoft, null, false, x)
                            }
                        }
                        out.values.forEach { list -> list.sortWith(compareBy({ it.time == null }, { it.time }, { it.title })) }
                        out
                    }
                    MonthGrid(
                        month = gridMonth,
                        itemsByDay = itemsByDay,
                        today = today,
                        onItemClick = { item ->
                            when (val x = item.payload) {
                                is EntryOccurrence -> editTarget = x.entry
                                is CalendarExtra.RowDate -> onOpenPage(x.cell.pageId)
                                is CalendarExtra.HabitAt -> { selectedDate = x.date; view = CalendarView.DAY }
                            }
                        },
                        onItemMove = { item, toDate ->
                            (item.payload as? EntryOccurrence)?.let { occurrence ->
                                val entry = occurrence.entry
                                if (entry.recurrenceRule != null && entry.originalEntryId == null) pendingMove = PendingMove(occurrence, toDate)
                                else viewModel.move(entry, occurrence.startDate, toDate, MoveScope.ALL)
                            }
                        },
                        onDayClick = { selectedDate = it; view = CalendarView.DAY },
                        onGroundClick = { selectedDate = it; quickAddOpen = true },
                        onMonthShift = { selectedDate = selectedDate.plusMonths(it.toLong()) },
                        onCellBounds = { day, rect -> dayCells[day] = rect },
                        highlightDay = trayCellTarget,
                        showNav = false,
                    )
                } else MonthGridView(
                    month = gridMonth,
                    occurrences = occurrences,
                    extras = extras,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                    onMonthShift = { selectedDate = selectedDate.plusMonths(it.toLong()) },
                    onCellBounds = { day, rect -> dayCells[day] = rect },
                    highlightDay = trayCellTarget,
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
            if (!wide && trayShown) {
                TaskTrayStrip(
                    tasks = tray, today = today, showUrgency = showUrgency, draggingId = trayDrag?.entry?.id,
                    collapsed = trayCollapsed, onToggle = { setTrayCollapsed(!trayCollapsed) },
                    onDragStart = trayDragStart, onDrag = trayDragMove, onDragEnd = trayDragEnd, onDragCancel = trayDragCancel,
                    onOpen = { editTarget = it },
                )
            }
            }
            }
        }
        // A grid block over the tray: its ghost drawn here, above the tray, naming the drop.
        if (dropOnTray) gridDragPosition?.let { pos ->
            DragGhost(
                title = gridDragTitle ?: "", target = "→ Clear When",
                modifier = Modifier.offset { IntOffset((pos.x - layerOrigin.x).roundToInt() + 12, (pos.y - layerOrigin.y).roundToInt() - 18) },
            )
        }
        // The ghost: the chip itself, the target named (*Thu 17 · 10:00*, *Thu 17*), over everything.
        trayDrag?.let { d ->
            val label = when (val t = trayGridTarget) {
                is WeekDrop.Slot -> t.day.format(DAY_FORMAT) + " · " + t.time
                is WeekDrop.AllDay -> t.day.format(DAY_FORMAT)
                null -> trayCellTarget?.format(DAY_FORMAT)
            }
            DragGhost(
                title = d.entry.title, target = label?.let { "→ $it" },
                modifier = Modifier.offset { IntOffset((d.position.x - layerOrigin.x).roundToInt() + 12, (d.position.y - layerOrigin.y).roundToInt() - 18) },
            )
        }
        }
    }
}

/** B§13.6 #5 — a task dragged off the tray, in root coordinates. */
private data class TrayDrag(val entry: Entry, val position: Offset)

private const val CALENDAR_TRAY_WIDTH_KEY = "calendar_tray_width"
private const val CALENDAR_TRAY_COLLAPSED_KEY = "calendar_tray_collapsed"

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
    dayLogs: List<TimeLog>,
    now: Instant,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQuickAdd: (ParsedEntry) -> Unit,
    /** 14f·2 — false on a wide window, where the strip under the bar is quick add's one home. */
    showQuickAdd: Boolean = true,
    /** L5 — false on a wide window, where the bar carries ‹ the day ›. */
    showNav: Boolean = true,
    onSetDone: (Long, Boolean) -> Unit,
    onOpenReminders: ((Entry) -> Unit)?,
    onEdit: (Entry) -> Unit,
    showUrgency: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showNav) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day") }
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), style = MaterialTheme.typography.heading)
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next day") }
        }
        // §0.6.5 — compare: two numbers side by side, no score and no colour (§0.5.2). Absent
        // when the day holds nothing planned and nothing logged.
        val habitExtras = extras.filterIsInstance<CalendarExtra.HabitAt>()
            .map { TimelineExtra("habit_${it.habit.id}", it.habit.title, it.habit.time!!, it.habit.duration, BlockKind.HABIT) }
        val blocks = remember(occurrences, habitExtras) { timelineBlocks(occurrences, habitExtras) }
        val planned = remember(blocks, occurrences) { plannedMinutes(blocks, occurrences.filter { it.startTime == null }.map { it.entry }) }
        val logged = remember(dayLogs, now) { loggedMinutes(dayLogs, Instant.MIN, Instant.MAX, now) }
        val loggedPerEntry = remember(dayLogs, now) { loggedByEntry(dayLogs, now) }
        if (planned > 0 || logged > 0) {
            Text(
                listOfNotNull(
                    if (planned > 0) "Planned " + formatMinutes(planned) else null,
                    if (logged > 0) "Logged " + formatMinutes(logged) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
            )
        }

        // §0.8 step 5 — the line is read as it is typed and previewed as chips; a chip's × says
        // "that was a word", the kind chip flips Task ↔ Event. Enter writes what the preview shows.
        var quickAddText by remember { mutableStateOf("") }
        var ignored by remember { mutableStateOf(emptySet<TokenKind>()) }
        var kindOverride by remember { mutableStateOf<EntryKind?>(null) }
        val parsed = remember(quickAddText, ignored, kindOverride) {
            QuickAddParser.parse(quickAddText, LocalDate.now(), EntryKind.EVENT, ignored, kindOverride)
        }
        val tokenTint = LocalTendrilPalette.current.findSoft
        if (showQuickAdd) TendrilField(
            value = quickAddText,
            onValueChange = { quickAddText = it; ignored = emptySet(); kindOverride = null },
            // L7b — the recognised runs tinted in the line (the strip's and the popup's rule).
            visualTransformation = remember(parsed.spans, tokenTint) { quickAddTokensTransformation(parsed.spans, tokenTint) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = stringResource(Res.string.calendar_quick_add_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onQuickAdd(parsed)
                quickAddText = ""; ignored = emptySet(); kindOverride = null
            }),
        )
        if (showQuickAdd && quickAddText.isNotBlank()) {
            QuickAddPreview(
                parsed = parsed,
                onFlipKind = { kindOverride = if (parsed.kind == EntryKind.TASK) EntryKind.EVENT else EntryKind.TASK },
                onDrop = { ignored = ignored + it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        HorizontalDivider()

        if (planMode) {
            PlanView(
                day = date,
                blocks = blocks,
                logged = remember(dayLogs, now) { loggedSpans(dayLogs, date, now) },
                allDay = occurrences.filter { it.startTime == null || !it.isFirstDay },
                unplanned = unplanned,
                onEdit = onEdit,
                onPlace = onPlace,
                onMoveBlock = onMoveBlock,
                showUrgency = showUrgency,
            )
        } else if (occurrences.isEmpty() && extras.isEmpty()) {
            EmptyState(icon = Icons.Filled.ChevronRight, message = "Nothing scheduled", modifier = Modifier.fillMaxSize())
        } else {
            // The audit's fixes (L2, 2026-09-17): the tray PR's one-line row — measured 65 px here
            // against the Tasks list's 30 — through `ui/components/RowControls.kt`.
            val profile = LocalDensityProfile.current
            ListInteractiveMinimum { LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                // Already ordered by EntryOccurrences.expand (by time, untimed last, then
                // title) — the order this list was sorting into by hand. The key carries the
                // occurrence's start date as well as the row id: one series contributes many
                // occurrences, all sharing the base row's id.
                items(occurrences, key = { "${it.entry.id}:${it.startDate}" }) { occurrence ->
                    val entry = occurrence.entry
                    val button = rowButtonModifier(profile.pointer)
                    val glyph = rowGlyphModifier(profile.pointer)
                    Row(
                        modifier = Modifier.fillMaxWidth().listRow(profile),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.status != null) {
                            Checkbox(
                                checked = entry.status == EntryStatus.DONE,
                                onCheckedChange = { checked -> onSetDone(entry.id, checked) },
                            )
                        }
                        // §3.2 — the row itself opens the edit sheet (it had no tap target before 6b).
                        TitleAndMeta(
                            entry.title,
                            listOfNotNull(occurrenceSubtitle(occurrence, date), loggedSegment(loggedPerEntry[entry.id] ?: 0, entry.estimate)).joinToString(" · "),
                            modifier = Modifier.weight(1f).clickable { onEdit(entry) },
                        )
                        // §0.6.5 — start/stop here too: the Day view is where the day is worked from.
                        TrackButton(TrackTarget.Entry(entry.id), runningTarget, onToggleTracking, modifier = button, iconModifier = glyph)
                        if (onOpenReminders != null) IconButton(onClick = { onOpenReminders(entry) }, modifier = button) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(Res.string.reminders_open), modifier = glyph)
                        }
                    }
                }
                items(extras, key = { extraKey(it) }) { ExtraRow(it, onOpenPage) }
            } }
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
    val profile = LocalDensityProfile.current
    Row(
        modifier = Modifier.fillMaxWidth().then(if (open != null) Modifier.clickable { open() } else Modifier).listRow(profile),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (extra is CalendarExtra.HabitAt) Icons.Filled.LocalFireDepartment else Icons.Filled.TableChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = rowGlyphModifier(profile.pointer),
        )
        Spacer(Modifier.width(12.dp))
        TitleAndMeta(extra.title, extra.subtitle, modifier = Modifier.weight(1f), titleColor = MaterialTheme.colorScheme.primary)
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
    val profile = LocalDensityProfile.current
    ListInteractiveMinimum { LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        days.forEach { (day, lists) ->
            item(key = "day_$day") {
                Text(
                    if (day == LocalDate.now()) "Today · " + day.format(DateTimeFormatter.ofPattern("EEE d MMM")) else day.format(DateTimeFormatter.ofPattern("EEEE d MMM")),
                    style = MaterialTheme.typography.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(lists.first, key = { "occ_${it.entry.id}:${it.startDate}:$day" }) { occurrence ->
                val entry = occurrence.entry
                Row(modifier = Modifier.fillMaxWidth().listRow(profile), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.status != null) Checkbox(checked = entry.status == EntryStatus.DONE, onCheckedChange = { onSetDone(entry.id, it) })
                    TitleAndMeta(entry.title, occurrenceSubtitle(occurrence, day), modifier = Modifier.weight(1f).clickable { onEdit(entry) })
                }
            }
            items(lists.second, key = { extraKey(it) }) { ExtraRow(it, onOpenPage) }
            item(key = "div_$day") { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        }
    } }
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
/** 14f·2 — a grid drag on a series, waiting for "this one or all?" (both axes). */
private data class PendingGridMove(val occurrence: EntryOccurrence, val toDate: LocalDate, val time: LocalTime)

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
    /** B§13.6 #5 — the cards' root bounds for the tray's drag, and the card it would land on. */
    onCardBounds: (LocalDate, Rect) -> Unit = { _, _ -> },
    highlightDay: LocalDate? = null,
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
                        .onGloballyPositioned { cardBounds[day] = it.boundsInRoot(); onCardBounds(day, it.boundsInRoot()) },
                    tonalElevation = if (hoverDay == day || highlightDay == day) 4.dp else 1.dp,
                    color = if (highlightDay == day) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (highlightDay == day) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = MaterialTheme.shapes.medium,
                    onClick = { onSelectDate(day) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.dayOfMonth}",
                            style = if (day == LocalDate.now()) MaterialTheme.typography.heading else MaterialTheme.typography.body,
                        )
                        if (dayEntries.isEmpty() && dayExtras.isEmpty()) {
                            Text("—", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            dayEntries.take(3).forEach { occurrence ->
                                var lineOrigin by remember(occurrence) { mutableStateOf(Offset.Zero) }
                                Text(
                                    "• ${occurrence.entry.title}",
                                    style = MaterialTheme.typography.caption,
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
                            if (dayEntries.size > 3) Text("+${dayEntries.size - 3} more", style = MaterialTheme.typography.caption)
                            // 14g·2 — a layer wears its own hue (B§13.8.3): a habit the habit hue, a
                            // database date the third; the accent is reserved for today and selection.
                            val palette = LocalTendrilPalette.current
                            dayExtras.take(3).forEach {
                                Text("◦ ${it.title}", style = MaterialTheme.typography.caption, color = if (it is CalendarExtra.HabitAt) palette.habit else palette.third)
                            }
                            if (dayExtras.size > 3) Text("+${dayExtras.size - 3} more", style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(d.entry.title, style = MaterialTheme.typography.caption, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun MonthGridView(
    month: YearMonth, occurrences: List<EntryOccurrence>, extras: List<CalendarExtra>, onSelectDate: (LocalDate) -> Unit, onMonthShift: (Int) -> Unit,
    /** B§13.6 #5 — the cells' root bounds for the tray's drag, and the cell it would land on. */
    onCellBounds: (LocalDate, Rect) -> Unit = { _, _ -> },
    highlightDay: LocalDate? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onMonthShift(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month") }
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${month.year}", style = MaterialTheme.typography.heading)
            IconButton(onClick = { onMonthShift(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month") }
        }

        // L4 (2026-09-17) — the phone's form: the grid's days (adjacent-month ones dim), a weekday
        // header, and under each number one dot per layer present that day (at most three, by
        // precedence — `monthDots`), in the layer's hue; the accent stays today's.
        val cells = remember(month) { monthGridDays(month) }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            (0..6).forEach { i ->
                Text(
                    DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(cells) { day ->
                val outOfMonth = YearMonth.from(day) != month
                val kinds = remember(occurrences, extras, day) {
                    monthDots(
                        occurrences.filter { it.date == day }.map { if (it.entry.kind == EntryKind.TASK) DotKind.TASK else DotKind.EVENT } +
                            extras.filter { it.date == day }.map { if (it is CalendarExtra.HabitAt) DotKind.HABIT else DotKind.DATABASE }
                    )
                }
                val isToday = day == LocalDate.now()
                Column(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (highlightDay == day) Modifier.background(MaterialTheme.colorScheme.primaryContainer).border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                        .onGloballyPositioned { onCellBounds(day, it.boundsInRoot()) }
                        .clickable { onSelectDate(day) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        style = MaterialTheme.typography.body,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            outOfMonth -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> Color.Unspecified
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.size(24.dp).then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier).wrapContentSize(Alignment.Center),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                        kinds.forEach { kind -> Box(modifier = Modifier.size(5.dp).background(dotColour(kind), CircleShape)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// L5 — the bar's Calendar controls on a wide window (`docs/critiques/calendar-chrome-mock.md`,
// L1 + C1): the range with ‹ › and *Today* in the title slot; `Week ▾` and `Layers ▾` as menus.

/** `‹ › September 2026 · Today` — Google's row; the Agenda has no ‹ › (it runs from today). */
@Composable
private fun CalendarBarTitle(view: CalendarView, anchor: LocalDate, onShift: (Int) -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (view.steps()) {
            IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = { onShift(1) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next", modifier = Modifier.size(18.dp)) }
        }
        Text( // type: SLOT_BAR_TITLE — the bar's title slot provides `pageTitle`; this Row is that slot's content
            rangeTitle(view, anchor, agendaDays = AGENDA_DAYS),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp).weight(1f, fill = false),
        )
        BarPillButton("Today", onClick = onToday)
    }
}

/** `Week ▾`: the four views; on the Day, *Plan the day* (§0.6.5) at the menu's foot as a check item. */
@Composable
private fun ViewMenuButton(view: CalendarView, onView: (CalendarView) -> Unit, planMode: Boolean, onPlanMode: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun label(v: CalendarView) = when (v) {
        CalendarView.DAY -> Res.string.calendar_view_day
        CalendarView.WEEK -> Res.string.calendar_view_week
        CalendarView.MONTH -> Res.string.calendar_view_month
        CalendarView.AGENDA -> Res.string.calendar_view_agenda
    }
    Box {
        BarMenuButton(stringResource(label(view)), open = open, onClick = { open = true })
        TendrilMenu(expanded = open, onDismissRequest = { open = false }) {
            CalendarView.entries.forEach { v ->
                TendrilMenuItem(text = { Text(stringResource(label(v))) }, trailingIcon = { MenuCheck(view == v) }, onClick = { open = false; onView(v) })
            }
            if (view == CalendarView.DAY) {
                HorizontalDivider()
                TendrilMenuItem(text = { Text("Plan the day") }, trailingIcon = { MenuCheck(planMode) }, onClick = { onPlanMode(!planMode) })
            }
        }
    }
}

/** `Layers ▾`: one check item per layer with its colour dot (`dotColour`); the menu stays open while toggling. */
@Composable
private fun LayersMenuButton(layers: CalendarLayers, onLayers: (CalendarLayers) -> Unit) {
    var open by remember { mutableStateOf(false) }
    @Composable fun dot(kind: DotKind) { Box(modifier = Modifier.size(8.dp).background(dotColour(kind), CircleShape)) }
    Box {
        BarMenuButton("Layers", open = open, onClick = { open = true })
        TendrilMenu(expanded = open, onDismissRequest = { open = false }) {
            TendrilMenuItem(text = { Text("Tasks") }, leadingIcon = { dot(DotKind.TASK) }, trailingIcon = { MenuCheck(layers.tasks) }, onClick = { onLayers(layers.copy(tasks = !layers.tasks)) })
            TendrilMenuItem(text = { Text("Events") }, leadingIcon = { dot(DotKind.EVENT) }, trailingIcon = { MenuCheck(layers.events) }, onClick = { onLayers(layers.copy(events = !layers.events)) })
            TendrilMenuItem(text = { Text("Habits") }, leadingIcon = { dot(DotKind.HABIT) }, trailingIcon = { MenuCheck(layers.habits) }, onClick = { onLayers(layers.copy(habits = !layers.habits)) })
            TendrilMenuItem(text = { Text("Database dates") }, leadingIcon = { dot(DotKind.DATABASE) }, trailingIcon = { MenuCheck(layers.databaseDates) }, onClick = { onLayers(layers.copy(databaseDates = !layers.databaseDates)) })
        }
    }
}
