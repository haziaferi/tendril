@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.reminders

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.reminders_add
import com.tendril.app.generated.resources.reminders_empty
import com.tendril.app.generated.resources.reminders_title
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.reminder.AllDayAnchorPreset
import com.tendril.app.data.reminder.ReminderOffset
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/**
 * §5.4 — the reminder list for one Entry, in a full edit surface rather than Quick Add
 * (§3.2 keeps capture to a single line). Presets plus a custom (number, unit) fallback, and
 * no cap on how many stack, so this is a list you append to rather than one picker.
 *
 * Offered for either kind (§4): a birthday reminder on an EVENT is as valid as a deadline
 * reminder on a TASK. Shared since B§13.6 #7 (the tray): the desktop fires these as Windows
 * notifications, so the bell is on both platforms and every write goes through
 * [com.tendril.app.domain.EntryScheduleCoordinator] alone.
 */
@Composable
fun ReminderSheet(core: WorkbenchCore, entry: Entry, onDismiss: () -> Unit) {
    val viewModel: ReminderViewModel = viewModel(
        key = "reminders_${entry.id}",
        factory = viewModelFactory {
            initializer {
                ReminderViewModel(
                    entry.id,
                    core.database.entryDao(),
                    core.database.reminderDao(),
                    core.entryScheduleCoordinator,
                )
            }
        }
    )
    val reminders by viewModel.reminders.collectAsState()

    // §4 — an Entry with no time of day gives a reminder nothing to count back from, so the
    // reminder carries its own anchor. Irrelevant (and stored as null) when there is a real
    // start time to anchor to.
    val needsAnchor = entry.startTime == null

    var useCustomOffset by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(ReminderOffset.Preset.ONE_HOUR) }
    var customCount by remember { mutableStateOf("1") }
    var customUnit by remember { mutableStateOf(IntervalUnit.DAY) }
    var anchor by remember { mutableStateOf<LocalTime?>(null) }
    var showAnchorPicker by remember { mutableStateOf(false) }

    // Scrolls: with reminders listed, the offset chips, the all-day anchor row and the
    // Add button together run past the sheet's height, and an unscrollable Column just
    // clips the button off the bottom.
    TendrilSheet(title = stringResource(Res.string.reminders_title), onDismiss = onDismiss, modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column {
            Text(entry.title, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (reminders.isEmpty()) {
                Text(
                    stringResource(Res.string.reminders_empty),
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                reminders.forEach { reminder ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reminder.offset.label(), style = MaterialTheme.typography.body)
                            // Only meaningful on an all-day Entry; on a timed one the Entry's
                            // own start time is what the offset counts back from.
                            if (needsAnchor) {
                                Text(
                                    "Fires at ${(reminder.anchorTime ?: LocalTime.MIDNIGHT).format(TIME_FORMAT)}",
                                    style = MaterialTheme.typography.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.remove(reminder.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove reminder")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Remind me", style = MaterialTheme.typography.label)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReminderOffset.Preset.entries.forEach { option ->
                    FilterChip(
                        selected = !useCustomOffset && preset == option,
                        onClick = { useCustomOffset = false; preset = option },
                        label = { Text(option.label(), maxLines = 1) },
                    )
                }
                FilterChip(
                    selected = useCustomOffset,
                    onClick = { useCustomOffset = true },
                    label = { Text("Custom", maxLines = 1) },
                )
            }

            if (useCustomOffset) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customCount,
                        onValueChange = { if (it.all(Char::isDigit)) customCount = it },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IntervalUnit.entries.forEach { u ->
                            FilterChip(
                                selected = customUnit == u,
                                onClick = { customUnit = u },
                                label = { Text(u.name.lowercase(), maxLines = 1) },
                            )
                        }
                    }
                }
            }

            if (needsAnchor) {
                Spacer(Modifier.height(16.dp))
                Text("Fires at", style = MaterialTheme.typography.label)
                Text(
                    "This one has no time of day, so a reminder needs a time to count back from.",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AllDayAnchorPreset.entries.forEach { option ->
                        val time = LocalTime.of(option.hour, option.minute)
                        FilterChip(
                            selected = anchor == time,
                            onClick = { anchor = time },
                            label = { Text(time.format(TIME_FORMAT), maxLines = 1) },
                        )
                    }
                    FilterChip(
                        selected = anchor != null && AllDayAnchorPreset.entries.none {
                            LocalTime.of(it.hour, it.minute) == anchor
                        },
                        onClick = { showAnchorPicker = true },
                        label = { Text("Custom", maxLines = 1) },
                    )
                }
                if (anchor == null) {
                    Spacer(Modifier.height(4.dp))
                    // §4 says an unpicked anchor falls back to midnight — say so out loud
                    // rather than letting it read as an unfinished form.
                    Text(
                        "Defaults to midnight if none is picked.",
                        style = MaterialTheme.typography.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val offset = if (useCustomOffset) {
                        ReminderOffset.Custom(customCount.toIntOrNull() ?: 1, customUnit)
                    } else {
                        ReminderOffset.FromPreset(preset)
                    }
                    viewModel.add(offset, if (needsAnchor) anchor else null)
                },
                enabled = !useCustomOffset || (customCount.toIntOrNull() ?: 0) > 0,
            ) { Text(stringResource(Res.string.reminders_add)) }
        }
    }

    if (showAnchorPicker) {
        val current = anchor ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute)
        AlertDialog(
            onDismissRequest = { showAnchorPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    anchor = LocalTime.of(state.hour, state.minute)
                    showAnchorPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showAnchorPicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

private fun ReminderOffset.Preset.label(): String = when (this) {
    ReminderOffset.Preset.ONE_HOUR -> "1 hour"
    ReminderOffset.Preset.TWO_HOURS -> "2 hours"
    ReminderOffset.Preset.FOUR_HOURS -> "4 hours"
    ReminderOffset.Preset.EIGHT_HOURS -> "8 hours"
    ReminderOffset.Preset.ONE_DAY -> "1 day"
    ReminderOffset.Preset.TWO_DAYS -> "2 days"
    ReminderOffset.Preset.ONE_WEEK -> "1 week"
}

private fun ReminderOffset.label(): String = when (this) {
    is ReminderOffset.FromPreset -> "${preset.label()} before"
    is ReminderOffset.Custom -> "$count ${unit.name.lowercase()}${if (count == 1) "" else "s"} before"
}
