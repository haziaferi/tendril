@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tendril.app.R
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

private enum class RepeatOption(val label: String) {
    NONE("None"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly")
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, date: LocalDate?, time: LocalTime?, repeat: RecurrenceRule.Elastic?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var hasDate by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hasTime by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(DEFAULT_TIME_OF_DAY) }
    var showTimePicker by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatOption.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.taskshabits_add_task)) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has date", modifier = Modifier.fillMaxWidth().weight(1f))
                    Switch(checked = hasDate, onCheckedChange = { hasDate = it })
                }
                if (hasDate) {
                    TextButton(onClick = { showDatePicker = true }) { Text("Date: $date") }
                    // Nested under `hasDate`: AlarmScheduler anchors an Entry's alarms to
                    // start_date + start_time, so a time without a date has nothing to fire on.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Has time", modifier = Modifier.fillMaxWidth().weight(1f))
                        Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                    }
                    if (hasTime) {
                        TextButton(onClick = { showTimePicker = true }) { Text("Time: $time") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Repeats", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RepeatOption.entries.forEach { option ->
                            FilterChip(
                                selected = repeat == option,
                                onClick = { repeat = option },
                                label = { Text(option.label, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val recurrence = when (repeat) {
                    RepeatOption.NONE -> null
                    RepeatOption.DAILY -> RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.DAY))
                    RepeatOption.WEEKLY -> RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.WEEK))
                    RepeatOption.MONTHLY -> RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.MONTH))
                }
                onAdd(title, if (hasDate) date else null, if (hasDate && hasTime) time else null, recurrence)
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toDatePickerMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = datePickerMillisToLocalDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        TimeOfDayDialog(
            initial = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { time = it; showTimePicker = false },
        )
    }
}

@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, frequency: HabitFrequency, time: LocalTime?, duration: Duration?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(IntervalUnit.DAY) }
    var hasTime by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(DEFAULT_TIME_OF_DAY) }
    var showTimePicker by remember { mutableStateOf(false) }
    var durationMinutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.taskshabits_add_habit)) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("Every", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { if (it.all(Char::isDigit)) count = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntervalUnit.entries.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.name.lowercase(), maxLines = 1) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                // A habit's time is what places it in the day — the Merged tab lists exactly
                // the habits that have one (§3.3). Optional, since a habit with no particular
                // hour is still a habit.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has time", modifier = Modifier.fillMaxWidth().weight(1f))
                    Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                }
                if (hasTime) {
                    TextButton(onClick = { showTimePicker = true }) { Text("Time: $time") }
                    Spacer(Modifier.height(8.dp))
                    // §3.3 — "optional time + duration (not required)". Offered only alongside a
                    // time, because a length with no start is not something any surface can place:
                    // the calendar overlay (§5.3) and the Merged tab both position a habit by its
                    // time and would have nowhere to draw a duration without one.
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { if (it.all(Char::isDigit)) durationMinutes = it },
                        label = { Text("Duration (minutes, optional)") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    title,
                    HabitFrequency(count.toIntOrNull() ?: 1, unit),
                    if (hasTime) time else null,
                    // A blank or zero box means "no duration", not a zero-length habit.
                    durationMinutes.toLongOrNull()?.takeIf { hasTime && it > 0 }?.let(Duration::ofMinutes),
                )
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showTimePicker) {
        TimeOfDayDialog(
            initial = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { time = it; showTimePicker = false },
        )
    }
}

/** 9am, matching the default [com.tendril.app.ui.reminders.ReminderSheet] offers for an
 * all-day reminder's anchor — the same "a sensible hour to mean by default" choice. */
private val DEFAULT_TIME_OF_DAY: LocalTime = LocalTime.of(9, 0)

/** Both dialogs pick a time the same way, so the wrapper lives here rather than being spelled
 * out twice. Mirrors the shape ReminderSheet's own anchor picker already uses. */
@Composable
private fun TimeOfDayDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
