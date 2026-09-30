package com.tendril.app.ui.taskshabits

import androidx.compose.material.icons.outlined.MoreHoriz
import com.tendril.app.generated.resources.plan_export
import com.tendril.app.generated.resources.plan_more
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.plan.DayRow
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.WeekSuggestionCard
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.plan_any_time
import com.tendril.app.generated.resources.plan_apply
import com.tendril.app.generated.resources.plan_choose_days
import com.tendril.app.generated.resources.plan_choose_days_button
import com.tendril.app.generated.resources.plan_confirm_day
import com.tendril.app.generated.resources.plan_next_week
import com.tendril.app.generated.resources.plan_outside_blocks
import com.tendril.app.generated.resources.plan_previous_week
import com.tendril.app.generated.resources.plan_projected
import com.tendril.app.generated.resources.plan_put_back
import com.tendril.app.generated.resources.plan_times_this_week
import com.tendril.app.generated.resources.plan_today
import com.tendril.app.generated.resources.plan_today_marker
import com.tendril.app.generated.resources.plan_week_label
import com.tendril.app.generated.resources.plan_week_suggestion
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.hueColours
import com.tendril.app.ui.theme.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * §6.3 (plan Phase 5d) — the Week view, as approved: each day its blocks as run-in labels, a block's
 * hue beside its name only (R2); a done entry ticked, an entry not done simply listed (§0.5.2); a
 * past day's heading quieter, never its entries; today marked. An "X times a week" habit not yet
 * confirmed places nothing and shows as one neutral card naming the suggestion (Q1): *Confirm*, or
 * *Choose days*. On a wide window the days run across, on the phone down; a tap on a day opens it.
 */
@Composable
internal fun HabitWeekContent(viewModel: TasksHabitsViewModel, wide: Boolean, onOpenDay: (LocalDate) -> Unit, labels: List<com.tendril.app.data.page.Label> = emptyList()) {
    val view by viewModel.weekView.collectAsState()
    val export = rememberPlanExport(viewModel, labels)
    val v = view ?: return
    val today = LocalDate.now()
    var choosing by remember { mutableStateOf<WeekSuggestionCard?>(null) }
    Column(modifier = Modifier.fillMaxSize().then(if (wide) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
        WeekHeader(v.monday, today, onShow = viewModel::showWeek, onExport = { export(v.monday) })
        for (card in v.suggestions) SuggestionCard(card, onConfirm = { viewModel.confirmWeek(card.habit, v.monday, card.days.map { it.dayOfWeek }.toSet()) }, onChoose = { choosing = card })
        if (wide) {
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                for (d in v.days) Box(Modifier.width(150.dp).verticalScroll(rememberScrollState())) { DayOfWeekColumn(d, today, onOpenDay) }
            }
        } else {
            for (d in v.days) DayOfWeekColumn(d, today, onOpenDay)
            Spacer(Modifier.size(96.dp))
        }
    }
    choosing?.let { card ->
        ChooseDaysSheet(card, v.monday, today, onDismiss = { choosing = null }) { days -> choosing = null; viewModel.confirmWeek(card.habit, v.monday, days) }
    }
}

@Composable
private fun WeekHeader(monday: LocalDate, today: LocalDate, onShow: (LocalDate) -> Unit, onExport: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShow(monday.minusWeeks(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.plan_previous_week)) }
        Text( // type: HEADER_DATE — the shown week
            stringResource(Res.string.plan_week_label, monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), monday.format(fmt), monday.plusDays(6).format(fmt)),
            style = MaterialTheme.typography.heading,
        )
        IconButton(onClick = { onShow(monday.plusWeeks(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.plan_next_week)) }
        Spacer(Modifier.weight(1f))
        if (today.isBefore(monday) || today.isAfter(monday.plusDays(6))) TextButton(onClick = { onShow(today) }) { Text(stringResource(Res.string.plan_today)) }
        // Phase 6 — the Day header's `···`, here holding only *Export…*: blocks are edited from a day.
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(Res.string.plan_more)) }
            com.tendril.app.ui.components.TendrilMenu(expanded = menu, onDismissRequest = { menu = false }) {
                com.tendril.app.ui.components.TendrilMenuItem(text = { Text(stringResource(Res.string.plan_export)) }, onClick = { menu = false; onExport() })
            }
        }
    }
}

private fun dayNames(days: List<LocalDate>) = days.joinToString(", ") { it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }

/** Q1 — one neutral card; the days it suggests said in words, never only by colour. */
@Composable
private fun SuggestionCard(card: WeekSuggestionCard, onConfirm: () -> Unit, onChoose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
    ) {
        Text( // type: BODY_LINE — the suggestion, placing nothing
            stringResource(Res.string.plan_week_suggestion, card.habit.title, pluralStringResource(Res.plurals.plan_times_this_week, card.n, card.n), dayNames(card.days)),
            style = MaterialTheme.typography.body,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onChoose) { Text(stringResource(Res.string.plan_choose_days_button)) }
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.plan_confirm_day, dayNames(card.days))) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChooseDaysSheet(card: WeekSuggestionCard, monday: LocalDate, today: LocalDate, onDismiss: () -> Unit, onApply: (Set<DayOfWeek>) -> Unit) {
    var days by remember { mutableStateOf(card.days.map { it.dayOfWeek }.toSet()) }
    TendrilSheet(onDismiss = onDismiss, title = card.habit.title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0L..6L) {
                val d = monday.plusDays(i)
                FilterChip(
                    selected = d.dayOfWeek in days,
                    enabled = !d.isBefore(today), // a day already past is not offered
                    onClick = { days = if (d.dayOfWeek in days) days - d.dayOfWeek else days + d.dayOfWeek },
                    label = { Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth) },
                )
            }
        }
        if (days.isEmpty()) Text(stringResource(Res.string.plan_choose_days), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error) // type: ERROR_LINE — no day chosen
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.plan_put_back)) }
            TextButton(onClick = { onApply(days) }, enabled = days.isNotEmpty()) { Text(stringResource(Res.string.plan_apply)) }
        }
    }
}

/** A day of the week: its heading, then each block as a run-in label and its entries. */
@Composable
private fun DayOfWeekColumn(day: HabitDayView, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val palette = LocalTendrilPalette.current
    val isToday = day.date == today
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDay(day.date) }
            .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text( // type: GROUP_HEADER — a day of the week over its entries; a past one quieter
            day.date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault())).replaceFirstChar { it.titlecase(Locale.getDefault()) } +
                if (isToday) " · " + stringResource(Res.string.plan_today_marker) else "",
            style = MaterialTheme.typography.label,
            color = if (day.readOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        for (b in day.blocks) {
            val rows = b.timed + b.flexible
            if (rows.isEmpty()) continue
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).background(b.block.hue?.let { hueColours(it, palette).hue } ?: MaterialTheme.colorScheme.outline, CircleShape))
                Spacer(Modifier.width(6.dp))
                WeekEntries(blockName(b.block), rows)
            }
        }
        if (day.anyTime.isNotEmpty()) WeekEntries(stringResource(Res.string.plan_any_time), day.anyTime, Modifier.padding(start = 12.dp, top = 4.dp))
        if (day.outside.isNotEmpty()) WeekEntries(stringResource(Res.string.plan_outside_blocks), day.outside, Modifier.padding(start = 12.dp, top = 4.dp))
    }
}

/** A block's entries as one run of text: *Morning* 07:00 Water ✓, Meds ✓, Stretch. */
@Composable
private fun WeekEntries(name: String, rows: List<DayRow>, modifier: Modifier = Modifier) {
    val expected = stringResource(Res.string.plan_projected)
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — the block, run in
        for (r in rows) {
            Text( // type: BODY_LINE — an entry; a done one ticked, an expected one said so
                listOfNotNull(r.time?.let(::clock), r.habit.title).joinToString(" ") + (if (r.done) " ✓" else "") + (if (r.projected) " · $expected" else ""),
                style = MaterialTheme.typography.body.let { if (r.projected) it.copy(fontStyle = FontStyle.Italic) else it },
            )
        }
    }
}
