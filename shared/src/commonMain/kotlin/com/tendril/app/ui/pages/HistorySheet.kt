package com.tendril.app.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.PageRevision
import com.tendril.app.data.page.RevisionReason
import com.tendril.app.sync.BlockSnapshotRecord
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.components.TendrilSheet
import androidx.compose.material.icons.outlined.History
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.tendril.app.domain.time.relativeTime
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

/**
 * §0.6.13 — the page's kept bodies, newest first; a row opens a read-only preview with
 * *Restore*. Restoring keeps the current body first, so the sheet is never a one-way door.
 */
@Composable
internal fun HistorySheet(viewModel: PageDetailViewModel, contentLocked: Boolean, onDismiss: () -> Unit) {
    val revisions by viewModel.revisions.collectAsState(initial = emptyList())
    var open by remember { mutableStateOf<PageRevision?>(null) }
    var confirm by remember { mutableStateOf(false) }

    val shown = open
    TendrilSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.7f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shown != null) {
                    IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the list") }
                }
                Text(if (shown == null) "History" else describe(shown), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (shown != null && !contentLocked) {
                    Button(onClick = { confirm = true }) { Text("Restore") }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            if (shown == null) {
                if (revisions.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        message = "No versions yet — one is kept before each edit, at most every ten minutes",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                } else {
                    LazyColumn {
                        items(revisions, key = { it.id }) { revision ->
                            Column(modifier = Modifier.fillMaxWidth().clickable { open = revision }.padding(vertical = 10.dp)) {
                                Text(describe(revision), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    revision.title.ifBlank { "Untitled" } + " · " + revision.blockCount + " block(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                RevisionPreview(shown)
            }
        }
    }

    if (confirm && shown != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Restore this version?") },
            text = { Text("The page's title and text become this version's. What they are now is kept in History.") },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.restore(shown.id); onDismiss() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** The revision's title and the text of its blocks, indented by nesting — read-only. */
@Composable
private fun RevisionPreview(revision: PageRevision) {
    val blocks = remember(revision.id) {
        runCatching { json.decodeFromString(ListSerializer(BlockSnapshotRecord.serializer()), revision.blocksJson) }.getOrDefault(emptyList())
    }
    val depthOf = remember(blocks) { depths(blocks) }
    LazyColumn {
        item { Text(revision.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }
        items(blocks, key = { it.uid }) { block ->
            Text(
                block.content.ifBlank { "·" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = (16 * (depthOf[block.uid] ?: 0)).dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

private fun depths(blocks: List<BlockSnapshotRecord>): Map<String, Int> {
    val parentOf = blocks.associate { it.uid to it.parentBlockUid }
    fun depth(uid: String, seen: Int = 0): Int {
        val parent = parentOf[uid] ?: return 0
        return if (seen > 32) 0 else 1 + depth(parent, seen + 1)
    }
    return blocks.associate { it.uid to depth(it.uid) }
}

private fun describe(revision: PageRevision): String {
    val reason = when (revision.reason) {
        RevisionReason.EDIT -> "before an edit"
        RevisionReason.MERGE -> "replaced by a sync"
        RevisionReason.RESTORE -> "before a restore"
    }
    return relativeTime(revision.takenAt) + " · " + reason
}
