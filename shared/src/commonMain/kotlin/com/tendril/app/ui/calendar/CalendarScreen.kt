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

private enum class CalendarView { DAY, WEEK, MONTH }

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
    modifier: Modifier = Modifier,
) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(core.database.entryDao(), core.resolveEntryUseCase, core.entryScheduleCoordinator) }
        }
    )
    // Defaults to Day, not Month (§2.2).
    var view by remember { mutableStateOf(CalendarView.DAY) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val entries by viewModel.entries.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    // §5.4 — reminders open over the tapped Entry; null means closed.
    var reminderTarget by remember { mutableStateOf<Entry?>(null) }

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
                                }
                            )
                        )
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
            val occurrences = remember(entries, view, selectedDate) {
                when (view) {
                    CalendarView.DAY -> EntryOccurrences.onDay(entries, selectedDate)
                    CalendarView.WEEK -> EntryOccurrences.expand(entries, weekStart, weekStart.plusDays(6))
                    CalendarView.MONTH ->
                        EntryOccurrences.expand(entries, gridMonth.atDay(1), gridMonth.atEndOfMonth())
                }
            }

            when (view) {
                CalendarView.DAY -> DayView(
                    date = selectedDate,
                    occurrences = occurrences,
                    onPrev = { selectedDate = selectedDate.minusDays(1) },
                    onNext = { selectedDate = selectedDate.plusDays(1) },
                    onQuickAdd = { viewModel.quickAdd(it, selectedDate) },
                    onSetDone = viewModel::setDone,
                    onOpenReminders = if (reminderSheet != null) { { reminderTarget = it } } else null,
                )
                CalendarView.WEEK -> WeekStripView(
                    selectedDate = selectedDate,
                    occurrences = occurrences,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                )
                CalendarView.MONTH -> MonthGridView(
                    month = gridMonth,
                    occurrences = occurrences,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                    onMonthShift = { selectedDate = selectedDate.plusMonths(it.toLong()) },
                )
            }
        }
    }
}

@Composable
private fun DayView(
    date: LocalDate,
    occurrences: List<EntryOccurrence>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQuickAdd: (ParsedEntry) -> Unit,
    onSetDone: (Long, Boolean) -> Unit,
    onOpenReminders: ((Entry) -> Unit)?,
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

        if (occurrences.isEmpty()) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                occurrenceSubtitle(occurrence, date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (onOpenReminders != null) IconButton(onClick = { onOpenReminders(entry) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(Res.string.reminders_open))
                        }
                    }
                }
            }
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

/** Cards layout (§2.2) — the Grid/hour-grid alternative is a later refinement. */
@Composable
private fun WeekStripView(selectedDate: LocalDate, occurrences: List<EntryOccurrence>, onSelectDate: (LocalDate) -> Unit) {
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(days) { day ->
            val dayEntries = occurrences.filter { it.date == day }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                onClick = { onSelectDate(day) },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.dayOfMonth}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (dayEntries.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        dayEntries.take(3).forEach { Text("• ${it.entry.title}", style = MaterialTheme.typography.bodySmall) }
                        if (dayEntries.size > 3) Text("+${dayEntries.size - 3} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGridView(month: YearMonth, occurrences: List<EntryOccurrence>, onSelectDate: (LocalDate) -> Unit, onMonthShift: (Int) -> Unit) {
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
                    val count = occurrences.count { it.date == day }
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
