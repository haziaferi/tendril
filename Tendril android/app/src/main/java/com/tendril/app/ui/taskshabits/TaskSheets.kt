@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.habit.Habit
import com.tendril.app.domain.PostponeAmount
import com.tendril.app.domain.PostponeUnit
import com.tendril.app.domain.TimeOfDay
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * §0.6.4's Postpone — the presets from [PostponeAmount.PRESETS] as one tap each, then a
 * custom amount. Moves the *When*; the sheet says so, because a person looking at a task with a
 * deadline deserves to know which of its two dates is about to change.
 */
@Composable
internal fun PostponeSheet(entry: Entry, onPostpone: (PostponeAmount) -> Unit, onDismiss: () -> Unit) {
    var customAmount by remember { mutableStateOf("2") }
    var customUnit by remember { mutableStateOf(PostponeUnit.DAY) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Postpone", style = MaterialTheme.typography.titleMedium)
            Text(
                if (entry.startDate == null) "This will give it a date — from today."
                else "Moves when you plan to do it. A deadline, if there is one, stays where it is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PostponeAmount.PRESETS.forEach { preset ->
                    AssistChip(onClick = { onPostpone(preset); onDismiss() }, label = { Text(preset.label()) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Custom", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PostponeUnit.entries.forEach { unit ->
                        FilterChip(selected = customUnit == unit, onClick = { customUnit = unit }, label = { Text(unit.name.lowercase() + "s") })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    enabled = (customAmount.toIntOrNull() ?: 0) > 0,
                    onClick = { onPostpone(PostponeAmount(customAmount.toInt(), customUnit)); onDismiss() },
                ) { Text("Postpone") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun PostponeAmount.label(): String {
    val unitWord = unit.name.lowercase()
    return "+$amount ${if (amount == 1) unitWord else unitWord + "s"}"
}

@Composable
internal fun SubtaskDialog(parent: Entry, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a step") },
        text = {
            Column {
                Text("Under “${parent.title}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Step") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title); onDismiss() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** §0.6.4 — the Deadline, set or cleared. Clearing is a first-class action, not "pick a far
 * date": a deadline is rare and real, and removing one is as ordinary as adding one. */
@Composable
internal fun DeadlineDialog(current: LocalDate?, onSet: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (current ?: LocalDate.now()).toDatePickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSet(datePickerMillisToLocalDate(it)) }
                onDismiss()
            }) { Text("Set deadline") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSet(null); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) { DatePicker(state = state) }
}

/**
 * §0.6.6 / §0.5.2 — the habit detail: presence, never absence. Three sentences and a month of
 * dots. A day with a check-in is a filled dot; a day without one is *nothing* — not an empty
 * ring, not a lighter dot, nothing — so the eye reads "these are the days I did it" and cannot
 * read "these are the days I failed". The streak appears only when the person switched it on in
 * Settings, and then as a plain number with no adjective.
 */
@Composable
internal fun HabitDetailSheet(habit: Habit, viewModel: TasksHabitsViewModel, showStreak: Boolean, onDismiss: () -> Unit) {
    val presence by viewModel.habitPresence(habit.id).collectAsState(initial = null)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(habit.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            val p = presence
            if (p == null || (p.lastDate == null && p.timesThisMonth == 0)) {
                Text(
                    "Here whenever you want it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val lines = buildList {
                    if (p.timesThisMonth > 0) add(
                        if (p.timesThisMonth == 1) "Once this month" else "${p.timesThisMonth} times this month",
                    )
                    p.usualTime?.let { add("Usually ${it.word()}") }
                    p.lastDate?.let { add("Last: ${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}") }
                    if (showStreak && habit.streak > 0) add("Streak: ${habit.streak}")
                }
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Spacer(Modifier.height(16.dp))
                MonthOfDots(days = p.daysThisMonth, month = p.month)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun TimeOfDay.word(): String = when (this) {
    TimeOfDay.MORNING -> "mornings"
    TimeOfDay.AFTERNOON -> "afternoons"
    TimeOfDay.EVENING -> "evenings"
    TimeOfDay.NIGHT -> "at night"
}

/** Seven columns, a dot where a check-in happened, empty space where none did. */
@Composable
private fun MonthOfDots(days: Set<LocalDate>, month: java.time.YearMonth) {
    Text(
        month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value + 6) % 7 // Monday-first, matching §3.2
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    FlowRow(maxItemsInEachRow = 7, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEach { day ->
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (day != null && day in days) {
                    Box(modifier = Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        }
    }
}
