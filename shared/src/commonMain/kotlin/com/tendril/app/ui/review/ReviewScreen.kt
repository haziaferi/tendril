@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.Entry
import com.tendril.app.domain.review.ReviewItem
import com.tendril.app.domain.review.WeekSummary
import com.tendril.app.domain.track.formatMinutes
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * §0.6.11 — the weekly walk (B§6 #10): OmniFocus's review mode on a cadence, Sunsama's summary
 * as three numbers at the top. One card at a time — a database that has gone a week without a
 * look, a task parked in Someday, a task whose When passed a week ago — each with the few
 * answers a review needs and nothing to scroll past. The list is re-taken after every answer,
 * so what was just handled is gone and the count is honest. §0.5.2 sets the tone: nothing here
 * is late or missed; it is offered again.
 */
@Composable
fun ReviewScreen(core: WorkbenchCore, onBack: () -> Unit, onOpenPage: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ReviewItem>?>(null) }
    var week by remember { mutableStateOf<WeekSummary?>(null) }
    var skipped by remember { mutableStateOf(setOf<String>()) }
    suspend fun reload() {
        items = core.review.items()
        week = core.review.week()
    }
    LaunchedEffect(Unit) { reload() }
    fun answer(action: suspend () -> Unit) { scope.launch { action(); reload() } }

    val remaining = items?.filterNot { keyOf(it) in skipped }.orEmpty()
    val current = remaining.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            week?.takeIf { !it.isEmpty }?.let { w ->
                Text(
                    listOfNotNull(
                        "Last 7 days",
                        if (w.loggedMinutes > 0) formatMinutes(w.loggedMinutes) + " logged" else null,
                        if (w.tasksDone > 0) "${w.tasksDone} " + (if (w.tasksDone == 1) "task done" else "tasks done") else null,
                        if (w.habitCheckIns > 0) "${w.habitCheckIns} " + (if (w.habitCheckIns == 1) "check-in" else "check-ins") else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            when {
                items == null -> Unit
                current == null -> EmptyState(icon = Icons.Outlined.Checklist, message = "Nothing left to review", modifier = Modifier.fillMaxSize())
                else -> {
                    Text(
                        "${items!!.size - remaining.size + 1} of ${items!!.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ReviewCard(
                        item = current,
                        onSkip = { skipped = skipped + keyOf(current) },
                        onOpenPage = onOpenPage,
                        onReviewed = { answer { core.review.markReviewed(it) } },
                        onToday = { answer { core.review.today(it) } },
                        onSomeday = { answer { core.review.someday(it) } },
                        onDone = { answer { core.review.done(it) } },
                        onTrash = { answer { core.review.trash(it) } },
                    )
                }
            }
        }
    }
}

private fun keyOf(item: ReviewItem): String = when (item) {
    is ReviewItem.DatabaseDue -> "db:${item.review.database.id}"
    is ReviewItem.SomedayTask -> "someday:${item.entry.id}"
    is ReviewItem.PastTask -> "past:${item.entry.id}"
}

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun ReviewCard(
    item: ReviewItem,
    onSkip: () -> Unit,
    onOpenPage: (Long) -> Unit,
    onReviewed: (com.tendril.app.data.pagedatabase.PageDatabase) -> Unit,
    onToday: (Entry) -> Unit,
    onSomeday: (Entry) -> Unit,
    onDone: (Entry) -> Unit,
    onTrash: (Entry) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            when (item) {
                is ReviewItem.DatabaseDue -> {
                    val r = item.review
                    Text(r.page.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            "${r.rowCount} " + (if (r.rowCount == 1) "row" else "rows"),
                            if (r.staleRows > 0) "${r.staleRows} untouched " + (r.database.lastReviewedAt?.let { "since last review" } ?: "so far") else null,
                            if (r.openTasks > 0) "${r.openTasks} open " + (if (r.openTasks == 1) "task" else "tasks") else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onOpenPage(r.page.id) }) { Text("Open") }
                        Button(onClick = { onReviewed(r.database) }) { Text("Reviewed") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }
                }
                is ReviewItem.SomedayTask -> TaskCard(
                    entry = item.entry,
                    line = "Someday since " + item.entry.createdAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dayFormat),
                    onSkip = onSkip, onToday = onToday, onSomeday = null, onDone = onDone, onTrash = onTrash,
                )
                is ReviewItem.PastTask -> TaskCard(
                    entry = item.entry,
                    line = "Planned for " + item.entry.startDate!!.format(dayFormat),
                    onSkip = onSkip, onToday = onToday, onSomeday = onSomeday, onDone = onDone, onTrash = onTrash,
                )
            }
        }
    }
}

/** The task answers, the same on both task cards: Keep is just the next card. */
@Composable
private fun TaskCard(
    entry: Entry,
    line: String,
    onSkip: () -> Unit,
    onToday: (Entry) -> Unit,
    onSomeday: ((Entry) -> Unit)?,
    onDone: (Entry) -> Unit,
    onTrash: (Entry) -> Unit,
) {
    Text(entry.title, style = MaterialTheme.typography.titleLarge)
    Text(
        listOfNotNull(line, entry.dueDate?.let { "due " + it.format(dayFormat) }).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onSkip) { Text("Keep") }
        OutlinedButton(onClick = { onToday(entry) }) { Text("Today") }
        if (onSomeday != null) OutlinedButton(onClick = { onSomeday(entry) }) { Text("Someday") }
        OutlinedButton(onClick = { onDone(entry) }) { Text("Done") }
        TextButton(onClick = { onTrash(entry) }) { Text("Trash") }
    }
}
