@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.trash

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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tendril.app.AppContainer
import com.tendril.app.R
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * One trashed thing from the Tasks & Habits tab — an Entry (Task or Event) or a Habit.
 *
 * The two are separate tables with separate DAOs (§3.3 keeps Habits structurally apart from
 * Entries), but §5.5.1 gives them the same Trash: "**Entry and Habit** get the same
 * `deleted_at` field directly, with the same Trash/Restore behavior." Wrapping them in one type
 * is what lets the selection set, the bulk actions and the counted confirm dialog below be
 * written once rather than twice — and two parallel implementations of "restore the selection"
 * is exactly the drift §9.8 R1 keeps ruling out elsewhere.
 *
 * [key] rather than the raw row id: Entry 3 and Habit 3 are different things, and a
 * `Set<Long>` of selected ids would treat them as the same one.
 */
private sealed interface TrashItem {
    val key: String
    val title: String
    val deletedAt: Instant?

    /** §5.5.1's "note the deleted item's former location so restoring makes sense out of
     * context." Kept to one short line: the per-item actions sit on the same row, so a
     * wrapping subtitle squeezes them. The trashed timestamp is left out deliberately — the
     * list is already ordered newest-first, so it earned less than the width it cost. */
    val subtitle: String

    data class TrashedEntry(val entry: Entry) : TrashItem {
        override val key = "entry:${entry.id}"
        override val title = entry.title
        override val deletedAt = entry.deletedAt
        override val subtitle: String
            get() {
                val kindLabel = if (entry.kind == EntryKind.TASK) "Task" else "Event"
                val due = entry.startDate
                    ?.let { date -> listOfNotNull(date.toString(), entry.startTime?.toString()).joinToString(" ") }
                    ?: "No date"
                return "$kindLabel · $due"
            }
    }

    data class TrashedHabit(val habit: Habit) : TrashItem {
        override val key = "habit:${habit.id}"
        override val title = habit.title
        override val deletedAt = habit.deletedAt
        // Cadence rather than a date: a Habit has no due date to tell two same-titled rows
        // apart (§6.1 — no backlog, so nothing is ever "due"), and cadence is what identifies it.
        override val subtitle: String
            get() {
                val (count, unit) = habit.frequency
                val noun = when (unit) {
                    IntervalUnit.DAY -> "day"
                    IntervalUnit.WEEK -> "week"
                    IntervalUnit.MONTH -> "month"
                }
                val cadence = if (count == 1) "every $noun" else "every $count ${noun}s"
                return "Habit · $cadence"
            }
    }
}

/**
 * §5.5.1 — Trash for everything the Tasks & Habits tab can delete: Entries (Tasks and Events)
 * and Habits alike.
 *
 * Both halves were dead code before anything listed them. `EntryDao` gained
 * `observeTrash`/`restore`/`deleteForever` and nothing called them, so a trashed Task could be
 * neither seen, restored, nor purged; that was closed first. `HabitDao` has the identical three
 * queries and they stayed uncalled — so trashing a Habit set `deletedAt`, removed it from the
 * habits list, the Merged view and the widget, and left **no way back at all**. §5.5.1's
 * promise that a deleted Habit is "recoverable" had no surface behind it. This closes the
 * second half against the first.
 *
 * Restoring an Entry goes through [com.tendril.app.domain.ResolveEntryUseCase] rather than the
 * DAO directly, because a restored TASK needs its alarms rearmed (§9.7) — the raw `restore`
 * query would bring the row back silently unscheduled. A Habit has no alarms (§9.7's scheduler
 * only ever handles Entries), so its own DAO call is the whole operation.
 *
 * **Still two Trash lists, not one.** §5.5.1 asks for "one list ... showing everything with a
 * non-null `deleted_at`", and Pages/Rows keep their own sheet in `PagesScreen`. Merging them is
 * a larger change than this — it also has to fix restoring a database Row without its linked
 * Entry coming back — and is tracked separately rather than half-done here.
 */
@Composable
fun TasksHabitsTrashSheet(container: AppContainer, onDismiss: () -> Unit) {
    val entries by container.database.entryDao().observeTrash().collectAsState(initial = emptyList())
    val habits by container.database.habitDao().observeTrash().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleteForever by remember { mutableStateOf<List<TrashItem>?>(null) }

    // Each DAO query is already newest-first; interleaving them needs one more sort. A missing
    // timestamp (only reachable from hand-edited data) folds to the epoch so it sorts last
    // rather than heading the list.
    val trashed: List<TrashItem> = remember(entries, habits) {
        (entries.map { TrashItem.TrashedEntry(it) } + habits.map { TrashItem.TrashedHabit(it) })
            .sortedByDescending { it.deletedAt ?: Instant.EPOCH }
    }

    fun restore(targets: List<TrashItem>) {
        scope.launch {
            targets.forEach { item ->
                when (item) {
                    is TrashItem.TrashedEntry -> container.resolveEntryUseCase.restore(item.entry.id)
                    is TrashItem.TrashedHabit ->
                        container.database.habitDao().restore(item.habit.id, Instant.now())
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Same proportional height as the Page/Row Trash rather than a flat dp figure.
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.trash_entries_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (trashed.isNotEmpty()) {
                    val allSelected = selectedKeys.size == trashed.size
                    TextButton(onClick = {
                        selectedKeys = if (allSelected) emptySet() else trashed.map { it.key }.toSet()
                    }) {
                        Text(if (allSelected) "Select none" else "Select all")
                    }
                }
            }

            // §5.5.1 — a selection gets the same two actions as a single item, applied at once.
            if (selectedKeys.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    TextButton(onClick = {
                        restore(trashed.filter { it.key in selectedKeys })
                        selectedKeys = emptySet()
                    }) { Text("Restore (${selectedKeys.size})") }
                    TextButton(onClick = {
                        pendingDeleteForever = trashed.filter { it.key in selectedKeys }
                    }) { Text("Delete forever (${selectedKeys.size})") }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (trashed.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Close,
                    message = stringResource(R.string.empty_trash_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn {
                    items(trashed, key = { it.key }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.key in selectedKeys,
                                onCheckedChange = { checked ->
                                    selectedKeys = if (checked) selectedKeys + item.key else selectedKeys - item.key
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { restore(listOf(item)) }) { Text("Restore") }
                            TextButton(onClick = { pendingDeleteForever = listOf(item) }) { Text("Delete forever") }
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
                Text(if (targets.size == 1) "Delete forever?" else "Permanently delete ${targets.size} items?")
            },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        targets.forEach { item ->
                            when (item) {
                                is TrashItem.TrashedEntry -> {
                                    // Tear the schedule down before the row goes. Trashing
                                    // normally does this already, but an Entry can also reach
                                    // Trash from GoogleCalendarSyncEngine's pull, which
                                    // soft-deletes without going through the coordinator — so
                                    // its alarms may still be live, and an alarm outliving its
                                    // row is a wakeup for nothing.
                                    container.entryScheduleCoordinator.onEntryRemoved(item.entry)
                                    container.database.entryDao().deleteForever(item.entry.id)
                                }
                                is TrashItem.TrashedHabit ->
                                    container.database.habitDao().deleteForever(item.habit.id)
                            }
                        }
                    }
                    selectedKeys = selectedKeys - targets.map { it.key }.toSet()
                    pendingDeleteForever = null
                }) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteForever = null }) { Text("Cancel") } },
        )
    }
}
