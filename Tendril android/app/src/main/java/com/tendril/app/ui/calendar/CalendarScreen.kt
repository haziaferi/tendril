@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.AppContainer
import com.tendril.app.R
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.googlecalendar.GoogleCalendarAuthManager
import com.tendril.app.googlecalendar.GoogleCalendarSyncEngine
import com.tendril.app.googlecalendar.SyncOutcome
import com.tendril.app.storage.GoogleCalendarPreferences
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.reminders.ReminderSheet
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarView { DAY, WEEK, MONTH }

@Composable
fun CalendarScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CalendarViewModel(container.database.entryDao(), container.resolveEntryUseCase, container.entryScheduleCoordinator) }
        }
    )
    // Defaults to Day, not Month (§2.2).
    var view by remember { mutableStateOf(CalendarView.DAY) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val entries by viewModel.entries.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    // §5.4 — reminders open over the tapped Entry; null means closed.
    var reminderTarget by remember { mutableStateOf<Entry?>(null) }

    if (showSettings) {
        CalendarSettingsSheet(
            authManager = container.googleCalendarAuthManager,
            syncEngine = container.googleCalendarSyncEngine,
            preferences = container.googleCalendarPreferences,
            onDismiss = { showSettings = false },
        )
    }

    reminderTarget?.let { entry ->
        ReminderSheet(container = container, entry = entry, onDismiss = { reminderTarget = null })
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_calendar)) },
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
                                    CalendarView.DAY -> R.string.calendar_view_day
                                    CalendarView.WEEK -> R.string.calendar_view_week
                                    CalendarView.MONTH -> R.string.calendar_view_month
                                }
                            )
                        )
                    }
                }
            }

            when (view) {
                CalendarView.DAY -> DayView(
                    date = selectedDate,
                    entries = entries.filter { it.startDate == selectedDate },
                    onPrev = { selectedDate = selectedDate.minusDays(1) },
                    onNext = { selectedDate = selectedDate.plusDays(1) },
                    onQuickAdd = { viewModel.quickAdd(it, selectedDate) },
                    onResolve = viewModel::resolve,
                    onOpenReminders = { reminderTarget = it },
                )
                CalendarView.WEEK -> WeekStripView(
                    selectedDate = selectedDate,
                    entries = entries,
                    onSelectDate = { selectedDate = it; view = CalendarView.DAY },
                )
                CalendarView.MONTH -> MonthGridView(
                    month = YearMonth.from(selectedDate),
                    entries = entries,
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
    entries: List<Entry>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQuickAdd: (String) -> Unit,
    onResolve: (Long, EntryStatus) -> Unit,
    onOpenReminders: (Entry) -> Unit,
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

        var quickAddText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = quickAddText,
            onValueChange = { quickAddText = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text(stringResource(R.string.calendar_quick_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onQuickAdd(quickAddText)
                quickAddText = ""
            }),
        )
        HorizontalDivider()

        if (entries.isEmpty()) {
            EmptyState(icon = Icons.Filled.ChevronRight, message = "Nothing scheduled", modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(entries.sortedWith(compareBy({ it.startTime == null }, { it.startTime })), key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.status != null) {
                            Checkbox(
                                checked = entry.status == EntryStatus.DONE,
                                onCheckedChange = { checked -> onResolve(entry.id, if (checked) EntryStatus.DONE else EntryStatus.SKIPPED) },
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                entry.startTime?.toString() ?: "All day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onOpenReminders(entry) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.reminders_open))
                        }
                    }
                }
            }
        }
    }
}

/** Cards layout (§2.2) — the Grid/hour-grid alternative is a later refinement. */
@Composable
private fun WeekStripView(selectedDate: LocalDate, entries: List<Entry>, onSelectDate: (LocalDate) -> Unit) {
    val monday = selectedDate.minusDays((selectedDate.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(days) { day ->
            val dayEntries = entries.filter { it.startDate == day }
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
                        dayEntries.take(3).forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodySmall) }
                        if (dayEntries.size > 3) Text("+${dayEntries.size - 3} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGridView(month: YearMonth, entries: List<Entry>, onSelectDate: (LocalDate) -> Unit, onMonthShift: (Int) -> Unit) {
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
                    val count = entries.count { it.startDate == day }
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

/**
 * Google Calendar sync connect/disconnect (§3.2, §9.5) — lives in Calendar's own settings,
 * not main Settings, matching the pattern already established for Tasks & Habits' sync-folder
 * picker. No client-ID field: see [GoogleCalendarAuthManager]'s class doc for why.
 */
@Composable
private fun CalendarSettingsSheet(
    authManager: GoogleCalendarAuthManager,
    syncEngine: GoogleCalendarSyncEngine,
    preferences: GoogleCalendarPreferences,
    onDismiss: () -> Unit,
) {
    val isConnected by preferences.isConnected.collectAsState()
    val lastSyncedAt by preferences.lastSyncedAt.collectAsState()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }

    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { activityResult ->
            runCatching { authManager.resultFromIntent(activityResult.data) }
                .onSuccess { authManager.onAuthorized(it) }
                .onFailure { errorMessage = it.message ?: "Google Calendar connection failed" }
        },
    )

    fun runSync() {
        syncing = true
        scope.launch {
            when (val outcome = syncEngine.sync()) {
                is SyncOutcome.Success -> errorMessage = null
                is SyncOutcome.Failed -> errorMessage = outcome.message
                is SyncOutcome.NeedsResolution -> {
                    // Grant was revoked/expired since Connect — re-launch consent; the person
                    // taps Sync now again once resolutionLauncher's onResult reconnects.
                    errorMessage = "Google Calendar needs to be reconnected — tap Sync now again after granting access"
                    outcome.result.pendingIntent?.let {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                    }
                }
            }
            syncing = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Calendar settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text("Google Calendar sync", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isConnected) "Connected" else "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            if (isConnected) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    val lastSyncedText = lastSyncedAt?.let {
                        "Last synced ${DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(java.time.ZoneId.systemDefault()).format(it)}"
                    } ?: "Never synced"
                    Text(lastSyncedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(enabled = !syncing, onClick = { runSync() }) { Text(if (syncing) "Syncing…" else "Sync now") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { authManager.disconnect() }) { Text("Disconnect") }
            } else {
                Button(onClick = {
                    scope.launch {
                        runCatching { authManager.authorize() }
                            .onSuccess { result ->
                                if (result.hasResolution()) {
                                    result.pendingIntent?.let {
                                        resolutionLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build())
                                    }
                                } else {
                                    authManager.onAuthorized(result)
                                }
                            }
                            .onFailure { errorMessage = it.message ?: "Google Calendar connection failed" }
                    }
                }) { Text("Connect Google Calendar") }
            }
        }
    }
}
