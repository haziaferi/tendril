package com.tendril.app.ui.taskshabits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePickerDialog
import com.tendril.app.generated.resources.plan_paused_open
import com.tendril.app.generated.resources.plan_paused_until
import com.tendril.app.generated.resources.plan_pause
import com.tendril.app.generated.resources.plan_resume
import com.tendril.app.domain.plan.pausedOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitBlock
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.label
import com.tendril.app.domain.plan.BlockEditError
import com.tendril.app.domain.plan.BlockOverride
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.DraftError
import com.tendril.app.domain.plan.RepeatKind
import com.tendril.app.domain.plan.ScheduleDraft
import com.tendril.app.domain.plan.blockEditError
import com.tendril.app.domain.plan.decodeOverrides
import com.tendril.app.domain.plan.decodeRule
import com.tendril.app.domain.plan.encodeOverrides
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.plan_active_from
import com.tendril.app.generated.resources.plan_active_until
import com.tendril.app.generated.resources.plan_add_weekday_times
import com.tendril.app.generated.resources.plan_block_end
import com.tendril.app.generated.resources.plan_block_name
import com.tendril.app.generated.resources.plan_block_start
import com.tendril.app.generated.resources.plan_blocks
import com.tendril.app.generated.resources.plan_choose_days
import com.tendril.app.generated.resources.plan_date
import com.tendril.app.generated.resources.plan_dont_pause
import com.tendril.app.generated.resources.plan_err_block_next
import com.tendril.app.generated.resources.plan_err_block_order
import com.tendril.app.generated.resources.plan_err_block_prev
import com.tendril.app.generated.resources.plan_err_n
import com.tendril.app.generated.resources.plan_err_time_format
import com.tendril.app.generated.resources.plan_err_times_count
import com.tendril.app.generated.resources.plan_err_until
import com.tendril.app.generated.resources.plan_every_how_many
import com.tendril.app.generated.resources.plan_every_n_days_n
import com.tendril.app.generated.resources.plan_every_n_weeks_n
import com.tendril.app.generated.resources.plan_from
import com.tendril.app.generated.resources.plan_how_many
import com.tendril.app.generated.resources.plan_on_any_of
import com.tendril.app.generated.resources.plan_pause_body
import com.tendril.app.generated.resources.plan_pause_open
import com.tendril.app.generated.resources.plan_pause_title
import com.tendril.app.generated.resources.plan_pause_until
import com.tendril.app.generated.resources.plan_put_back
import com.tendril.app.generated.resources.plan_remove
import com.tendril.app.generated.resources.plan_rule_daily
import com.tendril.app.generated.resources.plan_rule_every_n_days
import com.tendril.app.generated.resources.plan_rule_every_n_hours
import com.tendril.app.generated.resources.plan_rule_every_n_weeks
import com.tendril.app.generated.resources.plan_rule_once
import com.tendril.app.generated.resources.plan_rule_times_per_day
import com.tendril.app.generated.resources.plan_rule_times_per_week
import com.tendril.app.generated.resources.plan_rule_weekdays
import com.tendril.app.generated.resources.plan_save
import com.tendril.app.generated.resources.plan_step_hours
import com.tendril.app.generated.resources.plan_suggest_note
import com.tendril.app.generated.resources.plan_times_a_day_n
import com.tendril.app.generated.resources.plan_times_a_week_n
import com.tendril.app.generated.resources.plan_times_optional
import com.tendril.app.generated.resources.plan_until
import com.tendril.app.ui.components.TendrilDatePicker
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * §6.3 (plan Phase 5c) — what the add and edit sheet hands over: every field of a habit it asks
 * for, of both kinds. One value rather than fourteen arguments, so a field added later cannot be
 * passed in the wrong place.
 */
data class HabitForm(
    val title: String,
    val frequency: HabitFrequency,
    val time: LocalTime?,
    val duration: Duration?,
    val unit: String?,
    val amountPerCheckIn: Double?,
    val dailyAmount: Double?,
    val scheduleKind: HabitScheduleKind = HabitScheduleKind.INTERVAL,
    /** The rule as `PlanCodec` stores it; null for an interval habit. */
    val calendarRule: String? = null,
    val blockUid: String? = null,
    val labelId: Long? = null,
    val note: String? = null,
    val activeFrom: LocalDate? = null,
    val activeUntil: LocalDate? = null,
)

internal fun RepeatKind.label(): StringResource = when (this) {
    RepeatKind.DAILY -> Res.string.plan_rule_daily
    RepeatKind.WEEKDAYS -> Res.string.plan_rule_weekdays
    RepeatKind.EVERY_N_DAYS -> Res.string.plan_rule_every_n_days
    RepeatKind.EVERY_N_WEEKS -> Res.string.plan_rule_every_n_weeks
    RepeatKind.TIMES_PER_DAY -> Res.string.plan_rule_times_per_day
    RepeatKind.TIMES_PER_WEEK -> Res.string.plan_rule_times_per_week
    RepeatKind.EVERY_N_HOURS -> Res.string.plan_rule_every_n_hours
    RepeatKind.ONCE -> Res.string.plan_rule_once
}

private fun days(set: Set<DayOfWeek>) = set.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

private fun shortDate(d: LocalDate) = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

/** §6.3 (5c) — a habit's schedule in words, for the sheet and the pane: an interval habit's as before, a calendar one's from its rule. */
@Composable
internal fun scheduleText(habit: Habit): String {
    if (habit.scheduleKind != HabitScheduleKind.CALENDAR) return habit.frequency.label()
    return when (val r = decodeRule(habit.calendarRule)) {
        null -> ""
        CalendarRule.Daily -> stringResource(Res.string.plan_rule_daily)
        is CalendarRule.Once -> stringResource(Res.string.plan_rule_once) + " · " + shortDate(r.date)
        is CalendarRule.Weekdays -> days(r.days)
        is CalendarRule.EveryNDays -> stringResource(Res.string.plan_every_n_days_n, r.n)
        is CalendarRule.EveryNWeeks -> stringResource(Res.string.plan_every_n_weeks_n, r.n) + " · " + days(r.days)
        is CalendarRule.TimesPerDay -> stringResource(Res.string.plan_times_a_day_n, r.n)
        is CalendarRule.TimesPerWeek -> stringResource(Res.string.plan_times_a_week_n, r.n) + if (r.days.size < 7) " · " + days(r.days) else ""
        is CalendarRule.EveryNHours -> stringResource(Res.string.plan_step_hours, r.n, clock(r.from), clock(r.until))
    }
}

/** The app's date picker, as the Entry sheet's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toDatePickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(datePickerMillisToLocalDate(it)) }; onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.plan_put_back)) } },
    ) { TendrilDatePicker(state = state) }
}

private val HHMM = Regex("(\\d{1,2}):?(\\d{2})")

/** `08:00` or `8:00` or `0800` as minutes; null for anything else. */
internal fun parseClock(text: String): Int? {
    val m = HHMM.matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val mi = m.groupValues[2].toInt()
    return if (h < 24 && mi < 60) h * 60 + mi else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        for (d in DayOfWeek.entries) {
            FilterChip(selected = d in selected, onClick = { onChange(if (d in selected) selected - d else selected + d) }, label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) })
        }
    }
}

/**
 * §6.3 (5c) — the add sheet's calendar schedule: *Repeats* and the fields each choice asks for.
 * [error] is shown under the fields; [suggestion] is this week's suggested days for "times a week"
 * (D5 — the app suggests, the person confirms each week, nothing is placed until they do).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarScheduleFields(draft: ScheduleDraft, onDraft: (ScheduleDraft) -> Unit, error: DraftError?, suggestion: List<LocalDate>) {
    var nText by remember(draft.kind) { mutableStateOf(draft.n.toString()) }
    var timesText by remember { mutableStateOf(draft.times.joinToString(", ") { clock(it) }) }
    var fromText by remember { mutableStateOf(clock(draft.from)) }
    var untilText by remember { mutableStateOf(clock(draft.until)) }
    var pickDate by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in RepeatKind.entries) FilterChip(selected = draft.kind == k, onClick = { onDraft(draft.copy(kind = k)) }, label = { Text(stringResource(k.label())) })
    }
    Spacer(Modifier.size(8.dp))
    fun number(label: StringResource) = @Composable {
        Text(stringResource(label), style = MaterialTheme.typography.label) // type: GROUP_HEADER — a field's name
        TendrilField(value = nText, onValueChange = { t -> if (t.all(Char::isDigit) && t.length <= 3) { nText = t; t.toIntOrNull()?.let { onDraft(draft.copy(n = it)) } } }, modifier = Modifier.fillMaxWidth())
    }
    when (draft.kind) {
        RepeatKind.DAILY -> Unit
        RepeatKind.WEEKDAYS -> WeekdayChips(draft.days) { onDraft(draft.copy(days = it)) }
        RepeatKind.EVERY_N_DAYS -> number(Res.string.plan_every_how_many)()
        RepeatKind.EVERY_N_WEEKS -> { number(Res.string.plan_every_how_many)(); WeekdayChips(draft.days) { onDraft(draft.copy(days = it)) } }
        RepeatKind.TIMES_PER_DAY -> {
            number(Res.string.plan_how_many)()
            Spacer(Modifier.size(6.dp))
            Text(stringResource(Res.string.plan_times_optional), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the times field
            TendrilField(value = timesText, onValueChange = { t ->
                timesText = t
                val parts = t.split(',', ' ').filter { it.isNotBlank() }
                val parsed = parts.map(::parseClock)
                onDraft(draft.copy(times = if (parsed.all { it != null }) parsed.filterNotNull() else draft.times))
            }, modifier = Modifier.fillMaxWidth())
        }
        RepeatKind.TIMES_PER_WEEK -> {
            number(Res.string.plan_how_many)()
            Text(stringResource(Res.string.plan_on_any_of), style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 6.dp)) // type: GROUP_HEADER — the days it may fall on
            WeekdayChips(draft.days) { onDraft(draft.copy(days = it)) }
            if (suggestion.isNotEmpty()) Text( // type: EXPLAINER — D5, suggested, confirmed each week
                stringResource(Res.string.plan_suggest_note, suggestion.joinToString(", ") { it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }),
                style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RepeatKind.EVERY_N_HOURS -> {
            number(Res.string.plan_every_how_many)()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.plan_from), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the first time
                    TendrilField(value = fromText, onValueChange = { t -> fromText = t; parseClock(t)?.let { onDraft(draft.copy(from = it)) } }, modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.plan_until), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the last time
                    TendrilField(value = untilText, onValueChange = { t -> untilText = t; parseClock(t)?.let { onDraft(draft.copy(until = it)) } }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        RepeatKind.ONCE -> {
            Text(stringResource(Res.string.plan_date), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the date field
            TextButton(onClick = { pickDate = true }) { Text(shortDate(draft.date)) }
        }
    }
    val message = when (error) {
        null -> null
        DraftError.NoDays -> stringResource(Res.string.plan_choose_days)
        is DraftError.NumberRange -> stringResource(Res.string.plan_err_n, error.lo, error.hi)
        is DraftError.TimesCount -> stringResource(Res.string.plan_err_times_count, error.n)
        DraftError.Until -> stringResource(Res.string.plan_err_until)
    }
    if (message != null) Text(message, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) // type: ERROR_LINE — why the schedule makes no rule
    if (pickDate) PlanDateDialog(draft.date, onPick = { onDraft(draft.copy(date = it)) }, onDismiss = { pickDate = false })
}

/** The planner's `actPause`: until I resume it, or until a date; *Don't pause* puts it back. */
@Composable
internal fun PauseSheet(habit: Habit, today: LocalDate, onDismiss: () -> Unit, onPause: (from: LocalDate, until: LocalDate?) -> Unit) {
    var untilDate by remember { mutableStateOf<LocalDate?>(null) }
    var pick by remember { mutableStateOf(false) }
    TendrilSheet(onDismiss = onDismiss, title = stringResource(Res.string.plan_pause_title) + " · " + habit.title) {
        Text(stringResource(Res.string.plan_pause_body), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: EXPLAINER — what pausing does
        Spacer(Modifier.size(8.dp))
        for (open in listOf(true, false)) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.RadioButton) { if (open) untilDate = null else pick = true }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = (untilDate == null) == open, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(if (open) stringResource(Res.string.plan_pause_open) else stringResource(Res.string.plan_pause_until) + (untilDate?.let { " " + shortDate(it) } ?: ""), style = MaterialTheme.typography.body) // type: BODY_LINE — a pause's length
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.plan_dont_pause)) }
            TextButton(onClick = { onPause(today, untilDate); onDismiss() }) { Text(stringResource(Res.string.plan_pause_title)) }
        }
    }
    if (pick) PlanDateDialog(today.plusDays(7), onPick = { untilDate = it.coerceAtLeast(today) }, onDismiss = { pick = false })
}

/**
 * The approved mockup's *Blocks* editor (5c): each block's name, start and end, and its weekday
 * times beneath it. A name left blank is the default's, in the app's language (H1). Times follow
 * the planner's rule ([blockEditError]); a refusal says why and the limit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlocksEditorSheet(blocks: List<HabitBlock>, onDismiss: () -> Unit, onSaveTimes: (uid: String, start: Int, end: Int) -> Unit, onRename: (uid: String, name: String?) -> Unit, onOverrides: (uid: String, text: String?) -> Unit) {
    val live = blocks.filter { it.deletedAt == null }.sortedWith(compareBy({ it.position }, { it.uid }))
    TendrilSheet(onDismiss = onDismiss, title = stringResource(Res.string.plan_blocks)) {
        for (b in live) {
            androidx.compose.runtime.key(b.uid) { BlockEditorRow(b, live, onSaveTimes, onRename, onOverrides) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorRow(b: HabitBlock, all: List<HabitBlock>, onSaveTimes: (String, Int, Int) -> Unit, onRename: (String, String?) -> Unit, onOverrides: (String, String?) -> Unit) {
    var name by remember(b.name) { mutableStateOf(b.name ?: "") }
    var start by remember(b.startMinute) { mutableStateOf(clock(b.startMinute)) }
    var end by remember(b.endMinute) { mutableStateOf(clock(b.endMinute)) }
    var error by remember { mutableStateOf<String?>(null) }
    val defaultName = blockName(b.copy(name = null))
    val names = all.associate { it.uid to blockName(it) }
    val errFormat = stringResource(Res.string.plan_err_time_format)
    val errOrder = stringResource(Res.string.plan_err_block_order)
    val nextTemplate = stringResource(Res.string.plan_err_block_next, "{a}", "{b}", "{c}", "{d}")
    val prevTemplate = stringResource(Res.string.plan_err_block_prev, "{a}", "{b}", "{c}", "{d}")
    Text(names.getValue(b.uid), style = MaterialTheme.typography.label) // type: GROUP_HEADER — the block
    Text(stringResource(Res.string.plan_block_name), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — a field's name
    TendrilField(value = name, onValueChange = { name = it }, placeholder = defaultName, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.plan_block_start), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — a field's name
            TendrilField(value = start, onValueChange = { start = it; error = null }, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.plan_block_end), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant) // type: META — a field's name
            TendrilField(value = end, onValueChange = { end = it; error = null }, modifier = Modifier.fillMaxWidth())
        }
    }
    val shown = error
    if (shown != null) Text(shown, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.error) // type: ERROR_LINE — the times refused, with the limit
    // weekday times, under the block they change
    val overrides = decodeOverrides(b.uid, b.overrides)
    for (o in overrides) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(days(o.days) + " · " + clock(o.start) + "–" + clock(o.end), style = MaterialTheme.typography.body, modifier = Modifier.weight(1f)) // type: BODY_LINE — a weekday change
            TextButton(onClick = { onOverrides(b.uid, (overrides - o).takeIf { it.isNotEmpty() }?.let(::encodeOverrides)) }) { Text(stringResource(Res.string.plan_remove)) }
        }
    }
    var adding by remember { mutableStateOf(false) }
    if (adding) WeekdayTimesEditor(b, onCancel = { adding = false }) { o -> adding = false; onOverrides(b.uid, encodeOverrides(overrides + o)) }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        if (!adding) TextButton(onClick = { adding = true }) { Text(stringResource(Res.string.plan_add_weekday_times)) }
        TextButton(onClick = {
            val s = parseClock(start)
            val e = if (end.trim() == "24:00") 1440 else parseClock(end)
            if (s == null || e == null) { error = errFormat; return@TextButton }
            fun fill(t: String, a: String, bb: String, c: String, d: String) = t.replace("{a}", a).replace("{b}", bb).replace("{c}", c).replace("{d}", d)
            error = when (val refusal = blockEditError(all, b.uid, s, e)) {
                null -> null
                BlockEditError.Order -> errOrder
                is BlockEditError.PastNext -> fill(nextTemplate, names.getValue(refusal.next.uid), clock(refusal.end), names.getValue(b.uid), clock(refusal.end - 1))
                is BlockEditError.BeforePrev -> fill(prevTemplate, names.getValue(refusal.prev.uid), clock(refusal.start), names.getValue(b.uid), clock(refusal.start + 1))
            }
            if (error == null) {
                if (s != b.startMinute || e != b.endMinute) onSaveTimes(b.uid, s, e)
                val typed = name.trim().takeIf { it.isNotEmpty() && it != defaultName }
                if (typed != b.name) onRename(b.uid, typed)
            }
        }) { Text(stringResource(Res.string.plan_save)) }
    }
}

@Composable
private fun WeekdayTimesEditor(b: HabitBlock, onCancel: () -> Unit, onAdd: (BlockOverride) -> Unit) {
    var days by remember { mutableStateOf(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) }
    var start by remember { mutableStateOf(clock(b.startMinute)) }
    var end by remember { mutableStateOf(clock(b.endMinute)) }
    WeekdayChips(days) { days = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TendrilField(value = start, onValueChange = { start = it }, modifier = Modifier.weight(1f))
        TendrilField(value = end, onValueChange = { end = it }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.plan_put_back)) }
        TextButton(onClick = {
            val s = parseClock(start)
            val e = if (end.trim() == "24:00") 1440 else parseClock(end)
            if (s != null && e != null && e > s && days.isNotEmpty()) onAdd(BlockOverride(b.uid, days, s, e))
        }) { Text(stringResource(Res.string.plan_save)) }
    }
}

/** The dates a habit runs between (Q3's neighbours, under *More options*). */
@Composable
internal fun ActiveRangeFields(from: LocalDate?, until: LocalDate?, today: LocalDate, onFrom: (LocalDate?) -> Unit, onUntil: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: from, false: until
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picking = true }) { Text(stringResource(Res.string.plan_active_from) + (from?.let { " " + shortDate(it) } ?: "…")) }
        if (from != null) TextButton(onClick = { onFrom(null) }) { Text(stringResource(Res.string.plan_remove)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.plan_active_until) + (until?.let { " " + shortDate(it) } ?: "…")) }
        if (until != null) TextButton(onClick = { onUntil(null) }) { Text(stringResource(Res.string.plan_remove)) }
    }
    when (picking) {
        true -> PlanDateDialog(from ?: today, onPick = onFrom, onDismiss = { picking = null })
        false -> PlanDateDialog(until ?: today, onPick = onUntil, onDismiss = { picking = null })
        null -> Unit
    }
}

/** A pause in force today, or one that starts later: what the sheet and the pane speak of. */
private fun Habit.pauseShown(today: LocalDate) = pausedOn(today) || pauseFrom?.isAfter(today) == true

/** §6.3 (5c) — *Pause…* on a habit that is not paused, *Resume* on one that is; the pause sheet behind the first. */
@Composable
internal fun PauseChip(habit: Habit, viewModel: TasksHabitsViewModel) {
    var open by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    if (habit.pauseShown(today)) AssistChip(onClick = { viewModel.resumeHabit(habit.id) }, label = { Text(stringResource(Res.string.plan_resume)) })
    else AssistChip(onClick = { open = true }, label = { Text(stringResource(Res.string.plan_pause)) })
    if (open) PauseSheet(habit, today, onDismiss = { open = false }) { from, until -> viewModel.pauseHabit(habit.id, from, until) }
}

/** The pause, said once: *Paused until 12 Oct*, or *Paused until you resume it*; nothing when there is none. */
@Composable
internal fun PausedLine(habit: Habit) {
    if (!habit.pauseShown(LocalDate.now())) return
    Text( // type: META — the habit's pause
        habit.pauseUntil?.let { stringResource(Res.string.plan_paused_until, shortDate(it)) } ?: stringResource(Res.string.plan_paused_open),
        style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp),
    )
}

