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
import com.tendril.app.generated.resources.trash_entries_title
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.ui.components.EmptyState
import kotlinx.coroutines.launch
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading

/**
 * §5.5.1 — Trash for Entries (Tasks and Events), the counterpart to the Page/Row Trash in
 * `PagesScreen`. Soft-delete has always written `deletedAt` and `EntryDao` has always had
 * `observeTrash`/`deleteForever`, but nothing ever listed them: a trashed Task could be
 * neither seen, restored, nor purged. §5.5.1's promise that Trash is "restorable from there"
 * had no *there* for Entries until this.
 *
 * Restore goes through [com.tendril.app.domain.ResolveEntryUseCase] rather than the DAO
 * directly, because a restored TASK needs its alarms rearmed (§9.7) — the raw `restore` query
 * would bring the row back silently unscheduled.
 */
@Composable
fun EntryTrashSheet(core: WorkbenchCore, onDismiss: () -> Unit) {
    val entries by core.database.entryDao().observeTrash().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeleteForever by remember { mutableStateOf<List<Entry>?>(null) }

    fun restore(ids: Collection<Long>) {
        scope.launch { ids.forEach { core.resolveEntryUseCase.restore(it) } }
    }

    TendrilSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.6f)) {
        // Same proportional height as the Page/Row Trash rather than a flat dp figure.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.trash_entries_title),
                    style = MaterialTheme.typography.heading,
                    modifier = Modifier.weight(1f),
                )
                if (entries.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == entries.size) emptySet() else entries.map { it.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == entries.size) "Select none" else "Select all")
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
                        pendingDeleteForever = entries.filter { it.id in selectedIds }
                    }) { Text("Delete forever (${selectedIds.size})") }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Close,
                    message = stringResource(Res.string.empty_trash_message),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = entry.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + entry.id else selectedIds - entry.id
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.body)
                                Text(
                                    entry.trashSubtitle(),
                                    style = MaterialTheme.typography.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { restore(listOf(entry.id)) }) { Text("Restore") }
                            TextButton(onClick = { pendingDeleteForever = listOf(entry) }) { Text("Delete forever") }
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
                        // §5.5.1.1 — the registry records the tombstone that makes this purge
                        // stick on the other devices too, and tears the schedule down on the way
                        // (which this call site used to have to remember to do itself).
                        targets.forEach { core.purgeRegistry.purgeEntry(it.id) }
                    }
                    selectedIds = selectedIds - targets.map { it.id }.toSet()
                    pendingDeleteForever = null
                }) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteForever = null }) { Text("Cancel") } },
        )
    }
}

/** §5.5.1's "note the deleted item's former location so restoring makes sense out of
 * context" — for an Entry that context is what kind it was and when it was due, which is what
 * tells two same-titled rows apart. Kept to one short line like the Page/Row sheet's
 * subtitle: the two actions sit on the same row, so a wrapping subtitle squeezes them. The
 * trashed timestamp is left out deliberately — `observeTrash` already orders newest-first, so
 * it earned less than the width it cost. */
private fun Entry.trashSubtitle(): String {
    val kindLabel = if (kind == EntryKind.TASK) "Task" else "Event"
    val due = startDate?.let { date -> listOfNotNull(date.toString(), startTime?.toString()).joinToString(" ") } ?: "No date"
    return "$kindLabel · $due"
}
