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
import androidx.compose.material3.rememberDatePickerState
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
                onAdd(title, if (hasDate) date else null, null, recurrence)
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
}

@Composable
fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (title: String, frequency: HabitFrequency, time: LocalTime?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(IntervalUnit.DAY) }

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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(title, HabitFrequency(count.toIntOrNull() ?: 1, unit), null)
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
