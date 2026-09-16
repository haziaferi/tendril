package com.tendril.app.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.domain.journal.JournalToday
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.tendril.app.ui.theme.body

private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * §3.1.4 (amended, §0.8 step 8b / B§6 #15) — today's tasks and due habits at the top of today's
 * Journal page. Live rows, never blocks: the page's text, its FTS row and its Markdown export
 * stay what the person wrote. The boxes are real — a task ticks through the same
 * `ResolveEntryUseCase` the Tasks tab uses and a habit through `CheckInHabitUseCase` — so the
 * Journal is a place to work the day, not only to look at it.
 */
fun LazyListScope.journalTodayItems(today: JournalToday, date: LocalDate, viewModel: PageDetailViewModel) {
    item(key = "journal_today_header") {
        Text(
            "Today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    if (today.tasks.isEmpty() && today.habits.isEmpty()) {
        // Unlike the backlinks panel (§3.1.5), an empty day says so: a Journal reader wants to
        // know the day is clear, and a strip that only ever appears with content is not found.
        item(key = "journal_today_empty") {
            Text(
                "Nothing due today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    items(today.tasks, key = { "journal_task_" + it.entry.id + "_" + it.date }) { occurrence ->
        val entry = occurrence.entry
        StripRow(
            checked = entry.status == EntryStatus.DONE,
            onCheckedChange = { viewModel.setTaskDone(entry.id, it) },
            title = entry.title,
            detail = occurrence.startTime?.format(hourMinute),
        )
    }
    items(today.habits, key = { "journal_habit_" + it.id }) { habit ->
        StripRow(
            checked = habit.lastCompletedDate == date,
            onCheckedChange = { if (it) viewModel.checkInHabit(habit.id) else viewModel.undoCheckInHabit(habit.id) },
            title = habit.title,
            detail = listOfNotNull(
                habit.time?.format(hourMinute),
                "Every " + habit.frequency.count + " " + habit.frequency.unit.name.lowercase() + "(s)",
            ).joinToString(" · "),
        )
    }
    item(key = "journal_today_end") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
}

@Composable
private fun StripRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, detail: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column {
            Text(title, style = MaterialTheme.typography.body)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
