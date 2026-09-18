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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import org.jetbrains.compose.resources.stringResource
import com.tendril.app.ui.components.TendrilDatePicker
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.taskshabits_add_habit
import com.tendril.app.generated.resources.taskshabits_add_task
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.Duration
import androidx.compose.runtime.LaunchedEffect
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.QuickAddParser
import com.tendril.app.domain.TokenKind
import com.tendril.app.ui.entries.QuickAddPreview
import com.tendril.app.ui.entries.quickAddTokensTransformation
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.domain.dayLabel
import java.time.LocalDate
import java.time.LocalTime
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.label

private enum class RepeatOption(val label: String) {
    NONE("None"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly")
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, date: LocalDate?, time: LocalTime?, repeat: RecurrenceRule.Elastic?, deadline: LocalDate?, estimate: Duration?, importance: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    // §0.8 step 5 — the title is read as a line (`Gym every monday 7am by friday`) and its
    // tokens pre-fill the controls below, which stay editable; the chips say what was read.
    var ignored by remember { mutableStateOf(emptySet<TokenKind>()) }
    val parsed = remember(title, ignored) { QuickAddParser.parse(title, LocalDate.now(), EntryKind.TASK, ignored, EntryKind.TASK) }
    // §0.6.4 — the Deadline is a second, optional date, off by default: most tasks have none.
    var hasDeadline by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf(LocalDate.now()) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var hasDate by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hasTime by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(DEFAULT_TIME_OF_DAY) }
    var showTimePicker by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RepeatOption.NONE) }

    // L15 + T3 (PR B, 2026-09-18): the one modal that was neither a slide-over nor a picker is a
    // `TendrilSheet` now — a slide-over on a wide window, a bottom sheet on the phone — with the
    // edit sheet's frame, the 36 dp field and the sheet's title style.
    TendrilSheet(onDismiss = onDismiss, title = stringResource(Res.string.taskshabits_add_task)) {
        Column {
            // L7b — the recognised runs tinted in the line, the chips under it unchanged; the field
            // takes focus when the sheet opens (the walk's Ctrl+Shift+N typed into nothing).
            val tint = LocalTendrilPalette.current.findSoft
            val titleFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
            TendrilField(
                value = title, onValueChange = { title = it; ignored = emptySet() }, placeholder = "Title", modifier = Modifier.fillMaxWidth(),
                visualTransformation = remember(parsed.spans, tint) { quickAddTokensTransformation(parsed.spans, tint) },
                focusRequester = titleFocus,
            )
            if (parsed.spans.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                QuickAddPreview(parsed = parsed, onFlipKind = {}, onDrop = { ignored = ignored + it })
            }
            // Each parsed field lands on its control once, when it appears or changes; the
            // person can still change the control afterwards without the line fighting back.
            LaunchedEffect(parsed.date) { parsed.date?.let { hasDate = true; date = it } }
            LaunchedEffect(parsed.time) { parsed.time?.let { hasTime = true; time = it } }
            LaunchedEffect(parsed.deadline) { parsed.deadline?.let { hasDeadline = true; deadline = it } }
            LaunchedEffect(parsed.recurrence) {
                (parsed.recurrence as? RecurrenceRule.Elastic)?.period?.let { p ->
                    repeat = when {
                        p.days == 1 -> RepeatOption.DAILY
                        p.days == 7 -> RepeatOption.WEEKLY
                        p.months == 1 -> RepeatOption.MONTHLY
                        else -> repeat
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Has date", style = MaterialTheme.typography.body, modifier = Modifier.fillMaxWidth().weight(1f))
                Switch(checked = hasDate, onCheckedChange = { hasDate = it })
            }
            if (hasDate) {
                TextButton(onClick = { showDatePicker = true }) { Text("Date: " + dayLabel(date, LocalDate.now())) }
                // Nested under `hasDate`: AlarmScheduler anchors an Entry's alarms to
                // start_date + start_time, so a time without a date has nothing to fire on.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has time", style = MaterialTheme.typography.body, modifier = Modifier.fillMaxWidth().weight(1f))
                    Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                }
                if (hasTime) {
                    TextButton(onClick = { showTimePicker = true }) { Text("Time: $time") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Repeats", style = MaterialTheme.typography.label)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RepeatOption.entries.forEach { option ->
                        FilterChip(
                            selected = repeat == option,
                            onClick = { repeat = option },
                            label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Outside the `hasDate` block on purpose: a Someday task may carry a deadline.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Has deadline", style = MaterialTheme.typography.body, modifier = Modifier.fillMaxWidth().weight(1f))
                Switch(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
            }
            if (hasDeadline) {
                TextButton(onClick = { showDeadlinePicker = true }) { Text("Deadline: " + dayLabel(deadline, LocalDate.now())) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = {
                val recurrence = when (repeat) {
                    RepeatOption.NONE -> null
                    RepeatOption.DAILY -> RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.DAY))
                    RepeatOption.WEEKLY -> RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.WEEK))
                    RepeatOption.MONTHLY -> RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.MONTH))
                }
                onAdd(parsed.title, if (hasDate) date else null, if (hasDate && hasTime) time else null, recurrence, if (hasDeadline) deadline else null, parsed.estimate, parsed.importance)
                onDismiss()
            }) { Text("Add") }
        }
    }

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
            ) { TendrilDatePicker(state = state) }
        }

        if (showTimePicker) {
            TimeOfDayDialog(
                initial = time,
                onDismiss = { showTimePicker = false },
                onConfirm = { time = it; showTimePicker = false },
            )
        }

        if (showDeadlinePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = deadline.toDatePickerMillis())
            DatePickerDialog(
                onDismissRequest = { showDeadlinePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { deadline = datePickerMillisToLocalDate(it) }
                        showDeadlinePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDeadlinePicker = false }) { Text("Cancel") } },
            ) { TendrilDatePicker(state = state) }
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

        // L15 + T3 (PR B, 2026-09-18): the one modal that was neither a slide-over nor a picker is a
        // `TendrilSheet` now — a slide-over on a wide window, a bottom sheet on the phone — with the
        // edit sheet's frame, the 36 dp field and the sheet's title style.
        TendrilSheet(onDismiss = onDismiss, title = stringResource(Res.string.taskshabits_add_habit)) {
            Column {
                TendrilField(value = title, onValueChange = { title = it }, placeholder = "Title", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Every", style = MaterialTheme.typography.body)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TendrilField(
                        value = count,
                        onValueChange = { if (it.all(Char::isDigit)) count = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IntervalUnit.entries.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.name.lowercase(), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                // A habit's time is what places it in the day — the Merged tab lists exactly
                // the habits that have one (§3.3). Optional, since a habit with no particular
                // hour is still a habit.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Has time", style = MaterialTheme.typography.body, modifier = Modifier.fillMaxWidth().weight(1f))
                    Switch(checked = hasTime, onCheckedChange = { hasTime = it })
                }
                if (hasTime) {
                    TextButton(onClick = { showTimePicker = true }) { Text("Time: $time") }
                    Spacer(Modifier.height(8.dp))
                    // §3.3 — "optional time + duration (not required)". Offered only alongside a
                    // time, because a length with no start is not something any surface can place:
                    // the calendar overlay (§5.3) and the Merged tab both position a habit by its
                    // time and would have nowhere to draw a duration without one.
                    TendrilField(
                        value = durationMinutes,
                        onValueChange = { if (it.all(Char::isDigit)) durationMinutes = it },
                        placeholder = "Duration (minutes, optional)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
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
        }
    }

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
