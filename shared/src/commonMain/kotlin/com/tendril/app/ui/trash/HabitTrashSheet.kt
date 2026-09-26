@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.trash

import com.tendril.app.domain.restoreHabitsFromTrash
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.empty_trash_message
import com.tendril.app.generated.resources.trash_habits_title
import com.tendril.app.data.habit.Habit
import com.tendril.app.ui.components.EmptyState
import kotlinx.coroutines.launch
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.domain.label

/**
 * §5.5.1 — Trash for Habits, the third of the three and the last one still missing a *there*.
 *
 * `HabitDao` has had `observeTrash`, `restore` and `deleteForever` all along, and
 * `TasksHabitsScreen` has had a working "Delete" button that calls `trashHabit` — but the
 * screen's trash affordance opened [EntryTrashSheet] whatever tab was showing, so a trashed
 * Habit could be neither seen, restored, nor purged. Exactly the gap [EntryTrashSheet]'s own
 * KDoc describes for Entries, one entity over, and it outlived that fix because the button
 * that should have surfaced it looked like it already did.
 *
 * Restore goes straight to the DAO here, unlike the Entry sheet: a Habit carries no alarms and
 * no Calendar Provider mirror, so there is no schedule to rearm and nothing for a use case to
 * coordinate. `HabitDao.restore` also moves `updatedAt` forward, which is what lets a restore
 * supersede an in-flight tombstone from another device (§5.5.1.1) rather than losing to it.
 */
@Composable
fun HabitTrashSheet(core: WorkbenchCore, onDismiss: () -> Unit) {
    val habits by core.database.habitDao().observeTrash().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteForever by remember { mutableStateOf<List<Habit>?>(null) }

    fun restore(ids: Collection<Long>) {
        scope.launch { restoreHabitsFromTrash(core.database.habitDao(), core.entryScheduleCoordinator, ids) }
    }

    TendrilSheet(scrolls = false, onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.6f)) {
        // Same proportional height as the Entry and Page/Row sheets.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.trash_habits_title),
                    style = MaterialTheme.typography.heading,
                    modifier = Modifier.weight(1f),
                )
                if (habits.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == habits.size) emptySet() else habits.map { it.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == habits.size) "Select none" else "Select all")
                    }
                }
            }

            // §5.5.1 — a selection gets the same two actions as a single item, applied at once.
            if (selectedIds.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    TextButton(onClick = {
                        restore(selectedIds)
                        selectedIds = emptySet()
                    }) { Text("Restore (${selectedIds.size})") }
                    TextButton(onClick = {
                        pendingDeleteForever = habits.filter { it.id in selectedIds }
                    }) { Text("Delete forever (${selectedIds.size})") }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (habits.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Close,
                    message = stringResource(Res.string.empty_trash_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn {
                    items(habits, key = { it.id }) { habit ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = habit.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + habit.id else selectedIds - habit.id
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(habit.title, style = MaterialTheme.typography.body)
                                Text(
                                    habit.trashSubtitle(),
                                    style = MaterialTheme.typography.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { restore(listOf(habit.id)) }) { Text("Restore") }
                            TextButton(onClick = { pendingDeleteForever = listOf(habit) }) { Text("Delete forever") }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteForever?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingDeleteForever = null },
            title = {
                Text(if (targets.size == 1) "Delete forever?" else "Permanently delete ${targets.size} habits?")
            },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        // §5.5.1.1 — the tombstone is what makes this purge stick on the other
                        // devices; without it the Habit walks back in from their habits.json.
                        targets.forEach { core.purgeRegistry.purgeHabit(it.id) }
                    }
                    selectedIds = selectedIds - targets.map { it.id }.toSet()
                    pendingDeleteForever = null
                }) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteForever = null }) { Text("Cancel") } },
        )
    }
}

/** §5.5.1's "note the deleted item's former location so restoring makes sense out of context".
 * For a Habit that context is its cadence and the streak being given up — the same phrasing the
 * live list uses, so a row reads identically either side of the Trash. */
private fun Habit.trashSubtitle(): String =
    frequency.label() + " · streak $streak"
