@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.entries

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.tendril.app.ui.components.TendrilDatePicker
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.domain.urgency.Urgency
import com.tendril.app.ui.components.UrgencyPicker
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.Duration
import com.tendril.app.domain.dayLabel
import java.time.LocalDate
import java.time.LocalTime
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

/**
 * §0.8 step 6b / §3.2 — the edit sheet that was never built. Every field an Entry has, in the
 * shape its kind allows: flipping Task ↔ Event hides the fields the other kind cannot hold, and
 * `EntryEditor.save` enforces the same on the way to the row. Edits to a recurring entry apply
 * to the whole series — moving one occurrence alone is the drag's question, not this sheet's.
 * [showUrgency] is the Settings switch (14g·3): the ladder's picker is shown only when it is on.
 */
@Composable
fun EntryEditSheet(
    entry: Entry,
    showUrgency: Boolean,
    onSave: (Entry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(entry.title) }
    var kind by remember { mutableStateOf(entry.kind) }
    var date by remember { mutableStateOf(entry.startDate) }
    var time by remember { mutableStateOf(entry.startTime) }
    var endDate by remember { mutableStateOf(entry.endDate) }
    var endTime by remember { mutableStateOf(entry.endTime) }
    var deadline by remember { mutableStateOf(entry.dueDate) }
    var estimateMinutes by remember { mutableStateOf(entry.estimate?.toMinutes()?.toString().orEmpty()) }
    var importance by remember { mutableStateOf(entry.importance) }
    var repeat by remember { mutableStateOf(RepeatChoice.of(entry.recurrenceRule)) }
    // A stored rule richer than the presets (`every weekday`, `every 2 weeks`) is kept as it is
    // unless the person picks a preset; the chips only show the nearest one.
    var repeatTouched by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Fully expanded from the start: on desktop a half-open sheet's buttons sit below the
    // window with no gesture to reach them (tendril-spec.md §0.10 item 11).
    TendrilSheet(onDismiss = onDismiss) {
        Column {
            TendrilField(value = title, onValueChange = { title = it }, placeholder = "Title", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EntryKind.entries.forEachIndexed { index, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(index, EntryKind.entries.size),
                    ) { Text(if (k == EntryKind.TASK) "Task" else "Event") }
                }
            }
            if (entry.recurrenceRule != null && entry.originalEntryId == null) {
                Text(
                    "This repeats — changes here apply to every occurrence.",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // When — the day, and optionally the time. A task with no day is a Someday task.
            LabelledRow(if (kind == EntryKind.TASK) "When" else "Date") {
                Switch(checked = date != null, onCheckedChange = { on -> date = if (on) (date ?: LocalDate.now()) else null; if (!on) { time = null; endDate = null; endTime = null } })
            }
            if (date != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // F·P4 (the phone's second fix PR): the last ISO date on the phone — the tray's day form (`dayLabel`), the month outside this one.
                    TextButton(onClick = { picker = Picker.DATE }) { Text(dayLabel(date ?: LocalDate.now(), LocalDate.now())) }
                    TextButton(onClick = { picker = Picker.TIME }) { Text(time?.toString() ?: "Add time") }
                    if (time != null) TextButton(onClick = { time = null; endTime = null }) { Text("All day") }
                }
                if (kind == EntryKind.EVENT) {
                    LabelledRow("Ends") {
                        Switch(checked = endDate != null || endTime != null, onCheckedChange = { on ->
                            if (on) { endDate = date; endTime = time?.plusHours(1) } else { endDate = null; endTime = null }
                        })
                    }
                    if (endDate != null || endTime != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { picker = Picker.END_DATE }) { Text(dayLabel(endDate ?: date ?: LocalDate.now(), LocalDate.now())) }
                            if (time != null) TextButton(onClick = { picker = Picker.END_TIME }) { Text(endTime?.toString() ?: "End time") }
                        }
                    }
                }
                Text("Repeats", style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatChoice.entries.forEach { choice ->
                        FilterChip(selected = repeat == choice, onClick = { repeat = choice; repeatTouched = true }, label = { Text(choice.label) })
                    }
                }
            }

            if (kind == EntryKind.TASK) {
                LabelledRow("Deadline") {
                    Switch(checked = deadline != null, onCheckedChange = { on -> deadline = if (on) (deadline ?: date ?: LocalDate.now()) else null })
                }
                // Audit 5.6 — the day form the *When* above uses (F·P4), not ISO; walked 2026-09-26.
                // A local val: `deadline` is a delegated state property, which does not smart-cast.
                val shownDeadline = deadline
                if (shownDeadline != null) TextButton(onClick = { picker = Picker.DEADLINE }) { Text(dayLabel(shownDeadline, LocalDate.now())) }
                TendrilField(
                    value = estimateMinutes,
                    onValueChange = { if (it.all(Char::isDigit)) estimateMinutes = it },
                    placeholder = "Estimate (minutes)",
                    modifier = Modifier.width(200.dp).padding(top = 8.dp),
                )
                if (showUrgency) {
                    LabelledRow("Urgency") { UrgencyPicker(Urgency.fromLevel(importance), onPick = { importance = it.level }) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Row {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = {
                            // An end that fell before the start (the start was moved past it)
                            // keeps the span's old length rather than going backwards.
                            val safeEndTime = endTime?.let { end ->
                                val start = time
                                if (start != null && (endDate ?: date) == date && !end.isAfter(start)) {
                                    val span = if (entry.startTime != null && entry.endTime != null && entry.endTime!!.isAfter(entry.startTime!!)) Duration.between(entry.startTime, entry.endTime) else Duration.ofHours(1)
                                    start.plus(span)
                                } else end
                            }
                            onSave(
                                entry.copy(
                                    title = title.trim(),
                                    kind = kind,
                                    startDate = date,
                                    startTime = time,
                                    endDate = if (kind == EntryKind.EVENT) endDate else null,
                                    endTime = if (kind == EntryKind.EVENT) safeEndTime else null,
                                    recurrenceRule = when {
                                        date == null -> null
                                        !repeatTouched && kind == entry.kind -> entry.recurrenceRule
                                        else -> repeat.rule(kind)
                                    },
                                    dueDate = if (kind == EntryKind.TASK) deadline else null,
                                    estimate = if (kind == EntryKind.TASK) estimateMinutes.toLongOrNull()?.takeIf { it > 0 }?.let(Duration::ofMinutes) else null,
                                    importance = if (kind == EntryKind.TASK) importance else 0,
                                ),
                            )
                        },
                    ) { Text("Save") }
                }
            }
        }
    }

    when (picker) {
        Picker.DATE -> DateDialog(date ?: LocalDate.now(), onPick = { date = it; if (endDate != null && endDate!! < it) endDate = it }, onDismiss = { picker = null })
        Picker.END_DATE -> DateDialog(endDate ?: date ?: LocalDate.now(), onPick = { endDate = it }, onDismiss = { picker = null })
        Picker.DEADLINE -> DateDialog(deadline ?: LocalDate.now(), onPick = { deadline = it }, onDismiss = { picker = null })
        Picker.TIME -> TimeDialog(time ?: LocalTime.of(9, 0), onPick = { time = it }, onDismiss = { picker = null })
        Picker.END_TIME -> TimeDialog(endTime ?: (time ?: LocalTime.of(9, 0)).plusHours(1), onPick = { endTime = it }, onDismiss = { picker = null })
        null -> Unit
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move \"${entry.title}\" to Trash?") },
            text = { Text("Restorable from Trash, not deleted outright." + if (entry.recurrenceRule != null) " The whole series goes." else "") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private enum class Picker { DATE, END_DATE, DEADLINE, TIME, END_TIME }

/** The presets both kinds share; `rule` gives each kind its own shape (§4.1). */
private enum class RepeatChoice(val label: String) {
    NONE("None"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly");

    fun rule(kind: EntryKind): RecurrenceRule? = when (this) {
        NONE -> null
        DAILY -> if (kind == EntryKind.TASK) RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.DAY)) else RecurrenceRule.Fixed("FREQ=DAILY")
        WEEKLY -> if (kind == EntryKind.TASK) RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.WEEK)) else RecurrenceRule.Fixed("FREQ=WEEKLY")
        MONTHLY -> if (kind == EntryKind.TASK) RecurrenceRule.Elastic(intervalToPeriod(1, IntervalUnit.MONTH)) else RecurrenceRule.Fixed("FREQ=MONTHLY")
    }

    companion object {
        /** The nearest preset to a stored rule; a custom RRULE or interval reads as its frequency. */
        fun of(rule: RecurrenceRule?): RepeatChoice = when (rule) {
            null -> NONE
            is RecurrenceRule.Elastic -> when {
                rule.period.months >= 1 -> MONTHLY
                rule.period.days == 1 -> DAILY
                rule.period.days >= 7 -> WEEKLY
                else -> DAILY
            }
            is RecurrenceRule.Fixed -> when {
                "FREQ=MONTHLY" in rule.rrule -> MONTHLY
                "FREQ=WEEKLY" in rule.rrule -> WEEKLY
                "FREQ=DAILY" in rule.rrule -> DAILY
                else -> NONE
            }
        }
    }
}

@Composable
private fun LabelledRow(label: String, control: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.body)
        control()
    }
}

@Composable
private fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toDatePickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(datePickerMillisToLocalDate(it)) }; onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { TendrilDatePicker(state = state) }
}

@Composable
private fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
