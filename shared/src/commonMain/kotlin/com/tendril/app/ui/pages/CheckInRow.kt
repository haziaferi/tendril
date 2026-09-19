package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.tendril.app.ui.components.barControlHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.data.checkin.CheckIn
import com.tendril.app.domain.checkin.Energy
import com.tendril.app.domain.checkin.Mood
import com.tendril.app.domain.checkin.checkInLabel
import com.tendril.app.domain.checkin.dayLine
import com.tendril.app.domain.checkin.latestOfDay
import com.tendril.app.domain.checkin.monthOfMoods
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.TendrilPalette
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.theme.labelColours
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * §0.10 item 4 (2026-09-19) — *How are you?* on a Journal day: five words for mood, five for
 * energy, either alone, above the Today strip. A tap logs one check-in at that moment; a tap on
 * the chosen word again undoes it. Words, not faces (the user's call), and the words are the
 * page under the row — there is no note field. The title opens the month (`CheckInSheet`).
 * No score anywhere: the line under the chips says what and when, nothing more (B§10.3).
 */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.checkInItems(day: LocalDate, rows: List<CheckIn>, viewModel: PageDetailViewModel, onOpenSheet: () -> Unit) {
    item(key = "check_in_title") {
        Row(
            modifier = Modifier.clickable(onClick = onOpenSheet).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How are you?", style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "How you've been", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
    item(key = "check_in_chips") {
        val palette = LocalTendrilPalette.current
        val (mood, energy) = latestOfDay(rows)
        val latestMoodRow = rows.lastOrNull { it.mood != null }
        val latestEnergyRow = rows.lastOrNull { it.energy != null }
        // Two rows, never mixed: each scale is one line where it fits (a 360 dp phone at the
        // pill's 10 dp padding), and a wrap stays inside its own scale.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mood.entries.forEach { m ->
                    val c = moodColours(m, palette)
                    ScalePill(
                        word = m.label, selected = mood == m.level, hue = c.hue, onHue = c.onHue,
                        onClick = { if (mood == m.level) latestMoodRow?.let { viewModel.undoCheckIn(it.id) } else viewModel.checkIn(mood = m.level) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Energy.entries.forEach { e ->
                    ScalePill(
                        word = e.label, selected = energy == e.level, hue = MaterialTheme.colorScheme.primary, onHue = MaterialTheme.colorScheme.onPrimary,
                        onClick = { if (energy == e.level) latestEnergyRow?.let { viewModel.undoCheckIn(it.id) } else viewModel.checkIn(energy = e.level) },
                    )
                }
            }
        }
    }
    val line = dayLine(rows)
    if (line.isNotEmpty()) {
        item(key = "check_in_line") {
            Text(
                line,
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/** A mood's stored hue rendered by the register — the label chip's solve, so the word on the
 * chosen chip and the numeral on the month's disc both read. */
fun moodColours(mood: Mood, palette: TendrilPalette) = labelColours(mood.hex, palette)

/** The bar's pill (`BarPillButton`'s frame — 28 dp under a pointer, 40 under Touch, inside the
 * 48 dp minimum target), filled with the scale's hue when it is the latest answer. */
@Composable
private fun ScalePill(word: String, selected: Boolean, hue: Color, onHue: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .height(barControlHeight())
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) hue else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (selected) hue else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .semantics { role = Role.Checkbox; this.selected = selected }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                word,
                style = MaterialTheme.typography.label,
                color = if (selected) onHue else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayOfMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")

/**
 * *How you've been* — the month as colours: a disc in the day's latest mood, a ring where only
 * energy was logged, nothing where nothing was (Daylio's calendar, none of its statistics); then
 * the month's check-ins newest first, each saying which scale. ‹ › move by a month.
 */
@Composable
fun CheckInSheet(viewModel: PageDetailViewModel, day: LocalDate, onDismiss: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(day)) }
    val rows by remember(month) { viewModel.checkInsIn(month) }.collectAsState(emptyList())
    val palette = LocalTendrilPalette.current
    TendrilSheet(onDismiss = onDismiss, title = "How you've been", scrolls = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.heading,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(LocalDate.now())) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        MonthOfMoods(monthOfMoods(rows), month, palette)
        Spacer(Modifier.height(12.dp))
        val listed = rows.filter { checkInLabel(it) != null }.sortedByDescending { it.at }
        if (listed.isEmpty()) {
            Text("Nothing logged this month", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(listed, key = { it.id }) { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val m = row.mood?.let { Mood.fromLevel(it) }
                    Box(
                        modifier = Modifier.size(10.dp).let {
                            if (m != null) it.background(moodColours(m, palette).hue, CircleShape)
                            else it.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(checkInLabel(row) ?: "", style = MaterialTheme.typography.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        row.date.format(dayOfMonth) + " · " + row.at.atZone(ZoneId.systemDefault()).format(hourMinute),
                        style = MaterialTheme.typography.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Seven columns of 32 dp: a numeral in the mood's disc, a ring for energy alone, plain where nothing. */
@Composable
private fun MonthOfMoods(days: Map<LocalDate, Int>, month: YearMonth, palette: TendrilPalette) {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value + 6) % 7 // Monday-first, matching §3.2
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d ->
                    val level = d?.let { days[it] }
                    val mood = level?.takeIf { it > 0 }?.let { Mood.fromLevel(it) }
                    val colours = mood?.let { moodColours(it, palette) }
                    Box(
                        modifier = Modifier.size(32.dp).let {
                            when {
                                colours != null -> it.background(colours.hue, CircleShape)
                                level == 0 -> it.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                else -> it
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d != null) {
                            Text(
                                d.dayOfMonth.toString(),
                                style = MaterialTheme.typography.label,
                                color = colours?.onHue ?: if (level == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.size(32.dp)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
