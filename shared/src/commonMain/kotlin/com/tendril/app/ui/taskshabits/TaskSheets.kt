@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tendril.app.ui.taskshabits

import com.tendril.app.ui.pages.LocalViewOnly
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.tendril.app.domain.plural
import com.tendril.app.domain.amountLabel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.TendrilMenuItem
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.MenuCheck
import com.tendril.app.ui.components.BarMenuButton
import com.tendril.app.ui.components.TendrilDatePicker
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.habit.Habit
import com.tendril.app.domain.track.formatMinutes
import com.tendril.app.domain.PostponeAmount
import com.tendril.app.domain.PostponeUnit
import com.tendril.app.domain.TimeOfDay
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

/**
 * §0.6.4's Postpone — the presets from [PostponeAmount.PRESETS] as one tap each, then a
 * custom amount. Moves the *When*; the sheet says so, because a person looking at a task with a
 * deadline deserves to know which of its two dates is about to change.
 */
@Composable
internal fun PostponeSheet(entry: Entry, onPostpone: (PostponeAmount) -> Unit, onDismiss: () -> Unit) {
    var customAmount by remember { mutableStateOf("2") }
    var customUnit by remember { mutableStateOf(PostponeUnit.DAY) }

    TendrilSheet(title = "Postpone", onDismiss = onDismiss) {
        Column {
            Text(
                if (entry.startDate == null) "This will give it a date — from today."
                else "Moves when you plan to do it. A deadline, if there is one, stays where it is.",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PostponeAmount.PRESETS.forEach { preset ->
                    AssistChip(onClick = { onPostpone(preset); onDismiss() }, label = { Text(preset.label()) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Custom", style = MaterialTheme.typography.label)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TendrilField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.width(80.dp),
                )
                // L·P5 (item 23's Lows, 2026-09-18) — five unit chips wrapped beside the count on the phone and
                // scrolled on the desktop; every ground's custom offset is a number and a unit menu (Google
                // Calendar's, Todoist's, TickTick's). The bar's menu button, so it is 40 dp under Touch.
                var unitOpen by remember { mutableStateOf(false) }
                Box {
                    BarMenuButton(label = customUnit.name.lowercase() + "s", open = unitOpen, onClick = { unitOpen = true })
                    TendrilMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                        PostponeUnit.entries.forEach { unit ->
                            TendrilMenuItem(text = { Text(unit.name.lowercase() + "s") }, trailingIcon = { MenuCheck(customUnit == unit) }, onClick = { customUnit = unit; unitOpen = false })
                        }
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
                Text("Under “${parent.title}”", style = MaterialTheme.typography.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TendrilField(value = title, onValueChange = { title = it }, placeholder = "Step", modifier = Modifier.fillMaxWidth())
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
    ) { TendrilDatePicker(state = state) }
}

/**
 * §0.6.6 / §0.5.2 — the habit detail: presence, never absence. Three sentences and a month of
 * dots. A day with a check-in is a filled dot; a day without one is *nothing* — not an empty
 * ring, not a lighter dot, nothing — so the eye reads "these are the days I did it" and cannot
 * read "these are the days I failed". The streak appears only when the person switched it on in
 * Settings, and then as a plain number with no adjective.
 */
@Composable
internal fun HabitDetailSheet(habit: Habit, viewModel: TasksHabitsViewModel, showStreak: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit = {}) {
    TendrilSheet(title = habit.title, onDismiss = onDismiss) {
        HabitDetailContent(habit, viewModel, showStreak)
        // S10 — the phone's way to every field (the desktop's pane has the chip).
        if (!LocalViewOnly.current) Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            TextButton(onClick = onEdit) { Text("Edit…") }
        }
    }
}

/** The sheet's body, also the desktop pane's (14f·1): the presence sentences and the month of dots. */
@Composable
internal fun HabitDetailContent(habit: Habit, viewModel: TasksHabitsViewModel, showStreak: Boolean) {
    val presence by viewModel.habitPresence(habit.id).collectAsState(initial = null)
    // §0.6.5 — the logged minutes join the presence sentences; zero says nothing, like the rest.
    val loggedMinutes by viewModel.habitLoggedThisMonth(habit.id).collectAsState(initial = 0)
    val perSession by viewModel.habitMinutesPerSession(habit.id).collectAsState(initial = null)
    run {
        Column {
            val p = presence
            if (p == null || (p.lastDate == null && p.timesThisMonth == 0 && loggedMinutes == 0)) {
                Text(
                    "Here whenever you want it.",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val lines = buildList {
                    // §0.10 item 3 — a counting habit's presence is its amounts: *2 cups today*, *41 cups this
                    // month, on 12 days*. A sum of things done, never a share of a number.
                    if (habit.counts && p.amountToday != null) add(amountLabel(p.amountToday, habit.unit) + " today")
                    if (habit.counts && p.amountThisMonth != null) add(amountLabel(p.amountThisMonth, habit.unit) + " this month, on " + plural(p.timesThisMonth, "day"))
                    else if (p.timesThisMonth > 0) add(
                        if (p.timesThisMonth == 1) "Once this month" else "${p.timesThisMonth} times this month",
                    )
                    p.usualTime?.let { add("Usually ${it.word()}") }
                    p.lastDate?.let { add("Last: ${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}") }
                    if (showStreak && habit.streak > 0) add("Streak: ${habit.streak}")
                    if (loggedMinutes > 0) add("${formatMinutes(loggedMinutes)} logged this month")
                    perSession?.let { add("About ${formatMinutes(it)} each") }
                }
                lines.forEach { Text(it, style = MaterialTheme.typography.body) }
                // The number the person set, once and last, as a sentence in the dim colour — never a
                // fraction, a bar or a rate (§0.6.6; the critique's #3 puts it after the count).
                habit.dailyAmount?.let {
                    Text(amountLabel(it, habit.unit) + " a day is what you set", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                MonthOfDots(days = p.daysThisMonth, month = p.month)
            }
            if (habit.counts) CountingHabitVerbs(habit, viewModel)
        }
    }
}

/**
 * §0.10 item 3 — a counting habit's verbs, on the sheet and the pane: *+ 1 cup* (the row's tap),
 * *Undo the last cup*, and the manual entry the user asked for — **collapsed by default** behind
 * *Log an amount…*, a number typed and logged as one check-in of that value.
 */
@Composable
private fun CountingHabitVerbs(habit: Habit, viewModel: TasksHabitsViewModel) {
    var logOpen by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val unit = habit.unit?.takeIf { it.isNotBlank() }
    val doneToday = habit.lastCompletedDate == LocalDate.now()
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The unit is the person's word as typed (*cups*), so the chips carry the number alone — *+ 1*,
        // *Undo the last one* — and the sentences above carry the unit where the number fits it.
        AssistChip(onClick = { viewModel.checkInHabit(habit.id) }, label = { Text("+ " + amountLabel(habit.amountPerCheckIn ?: 1.0, null)) })
        if (doneToday) AssistChip(onClick = { viewModel.undoCheckInHabit(habit.id) }, label = { Text("Undo the last one") })
        AssistChip(onClick = { logOpen = !logOpen }, label = { Text(if (logOpen) "Log an amount ▴" else "Log an amount…") })
    }
    if (logOpen) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TendrilField(value = typed, onValueChange = { typed = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) }, placeholder = unit ?: "amount", modifier = Modifier.width(120.dp))
            val value = typed.replace(',', '.').toDoubleOrNull()
            TextButton(enabled = value != null && value > 0, onClick = { viewModel.checkInHabit(habit.id, value); typed = ""; logOpen = false }) { Text("Log") }
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
        style = MaterialTheme.typography.label,
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
