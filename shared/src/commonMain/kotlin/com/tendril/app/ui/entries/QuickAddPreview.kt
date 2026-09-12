package com.tendril.app.ui.entries

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.domain.TokenKind
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * §0.8 step 5 — what the line was read as, under the field, before anything is written. One chip
 * per recognised token; × on a chip means "that was a word, not a date", and the kind chip flips
 * Task ↔ Event. Shared by Calendar's Quick Add and Tasks' add dialog, so the two surfaces read a
 * line the same way and show it the same way.
 */
@Composable
fun QuickAddPreview(
    parsed: ParsedEntry,
    onFlipKind: () -> Unit,
    onDrop: (TokenKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = parsed.spans.map { it.kind }.toSet()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InputChip(
            selected = true,
            onClick = onFlipKind,
            label = { Text(if (parsed.kind == EntryKind.TASK) "Task" else "Event") },
        )
        parsed.date?.let { PreviewChip(dateLabel(it), if (TokenKind.DATE in kinds) { { onDrop(TokenKind.DATE) } } else null) }
        parsed.time?.let { time ->
            val label = parsed.endTime?.let { "${timeLabel(time)}–${timeLabel(it)}" } ?: timeLabel(time)
            PreviewChip(label) { onDrop(if (parsed.endTime != null && TokenKind.SPAN in kinds) TokenKind.SPAN else TokenKind.TIME) }
        }
        parsed.estimate?.let { PreviewChip("for ${durationLabel(it)}") { onDrop(TokenKind.SPAN) } }
        parsed.recurrence?.let { PreviewChip(repeatLabel(it)) { onDrop(TokenKind.REPEAT) } }
        parsed.deadline?.let { PreviewChip("by ${dateLabel(it)}") { onDrop(TokenKind.DEADLINE) } }
        if (parsed.important) PreviewChip("Important") { onDrop(TokenKind.IMPORTANT) }
    }
}

@Composable
private fun PreviewChip(label: String, onDrop: (() -> Unit)?) {
    InputChip(
        selected = false,
        onClick = { onDrop?.invoke() },
        enabled = onDrop != null,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        trailingIcon = if (onDrop != null) {
            { Icon(Icons.Filled.Close, contentDescription = "Not that", modifier = Modifier.size(14.dp)) }
        } else null,
    )
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DATE_FORMAT)
    }
}

private fun timeLabel(time: LocalTime): String = time.format(TIME_FORMAT)

private fun durationLabel(d: Duration): String {
    val h = d.toHours(); val m = d.toMinutesPart()
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun repeatLabel(rule: RecurrenceRule): String = when (rule) {
    is RecurrenceRule.Elastic -> {
        // `Period.ofWeeks(n)` is stored as `n * 7` days — read the weeks back out.
        val p = rule.period
        when {
            p.months == 1 -> "Monthly"
            p.months > 1 -> "Every ${p.months} months"
            p.days == 1 -> "Daily"
            p.days == 7 -> "Weekly"
            p.days > 0 && p.days % 7 == 0 -> "Every ${p.days / 7} weeks"
            p.days > 1 -> "Every ${p.days} days"
            else -> "Repeats"
        }
    }
    is RecurrenceRule.Fixed -> {
        val parts = rule.rrule.split(";").associate { it.substringBefore("=") to it.substringAfter("=", "") }
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val byDay = parts["BYDAY"]
        when (parts["FREQ"]) {
            "DAILY" -> if (byDay == "MO,TU,WE,TH,FR") "Weekdays" else if (interval > 1) "Every $interval days" else "Daily"
            "WEEKLY" -> (if (interval > 1) "Every $interval weeks" else "Weekly") + (byDay?.let { " on $it" } ?: "")
            "MONTHLY" -> if (interval > 1) "Every $interval months" else "Monthly"
            else -> "Repeats"
        }
    }
}
