package com.tendril.app.domain.plan

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.data.habit.HabitBlock
import com.tendril.app.data.habit.HabitBlockDao
import com.tendril.app.data.habit.HabitScheduleEdit
import com.tendril.app.data.habit.HabitScheduleEditDao
import com.tendril.app.data.habit.HabitScheduleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * §6.3 (v27, plan Phase 3) — the stored blocks and edits, read once into the engine's terms, so
 * that everything outside the Habits tab — is it due today, which strokes on the grid, which
 * occurrence a widget tap checks in — asks [planWeek] the same question the tab does, and no
 * reader re-derives a calendar habit's days on its own (audit 2.2's drift, avoided up front).
 *
 * Built from the live rows: a tombstoned block is gone from the day (its habits fall to *Any
 * time*), a tombstoned edit is undone, and a row whose text this build cannot read is skipped.
 */
class HabitCalendar(blocks: List<HabitBlock>, edits: List<HabitScheduleEdit>) {
    private val live = blocks.filter { it.deletedAt == null }.sortedWith(compareBy({ it.position }, { it.uid }))
    private val planBlocks = live.map { PlanBlock(it.uid, it.startMinute, it.endMinute) }
    private val overrides = live.flatMap { decodeOverrides(it.uid, it.overrides) }
    private val planEdits = edits.filter { it.deletedAt == null }.mapNotNull { it.toPlanEdit() }
    /** Keyed by the habit's schedule, not the row: a check-in rewrites the row and changes no day. One thread per instance
     * (a composition, one collector, one read), as every holder uses it. */
    private val weeks = HashMap<Pair<PlanHabit, LocalDate>, List<PlanDay>>()

    /**
     * [habit]'s occurrences on [date], in the order of their keys ("1", "2", …, then the ones added
     * by a move) — the order a tap that checks in "the next one" walks. Empty for an interval
     * habit, a trashed one, and one whose rule this build cannot read.
     */
    fun occurrences(habit: Habit, date: LocalDate): List<PlanOccurrence> {
        if (habit.scheduleKind != HabitScheduleKind.CALENDAR || habit.deletedAt != null) return emptyList()
        val plan = habit.toPlanHabit() ?: return emptyList()
        val days = weeks.getOrPut(plan to mondayOf(date)) {
            val model = PlanModel(planBlocks, overrides, listOf(plan), planEdits.filter { it.target == EditTarget.BLOCK || it.refUid == habit.uid })
            planWeek(model, date).days
        }
        val day = days.first { it.date == date }
        return (day.blocks.flatMap { it.timed + it.flexible } + day.outside + day.anyTime)
            .sortedWith(compareBy({ it.key.toIntOrNull() ?: Int.MAX_VALUE }, { it.key }))
    }

    companion object {
        /** One read of the two tables, for a caller outside a Flow (a receiver, the widget, a use case). */
        suspend fun load(blocks: HabitBlockDao, edits: HabitScheduleEditDao): HabitCalendar = HabitCalendar(blocks.getAll(), edits.getAll())
    }
}

/**
 * §6.3 — the three tables a calendar habit's day is read from, for the readers outside the Habits
 * tab. One object passed around rather than three DAOs, so a reader cannot take the blocks and
 * forget the edits.
 */
class HabitCalendarSource(private val blocks: HabitBlockDao, private val edits: HabitScheduleEditDao, private val completions: HabitCompletionDao) {
    fun observe(): Flow<HabitCalendar> = combine(blocks.observeLive(), edits.observeLive(), ::HabitCalendar)
    /** The rows themselves, for the Habits tab, which draws the blocks (their names, hues) as well as placing into them. */
    fun observeRows(): Flow<Pair<List<HabitBlock>, List<HabitScheduleEdit>>> = combine(blocks.observeLive(), edits.observeLive()) { b, e -> b to e }
    suspend fun load(): HabitCalendar = HabitCalendar.load(blocks, edits)
    /** §6.3 (5b) — what "Apply this change to…" wrote, stored as the rows the sync carries (`PlanCodec`'s texts). */
    suspend fun addEdits(written: List<ScheduleEdit>) {
        for (e in written) {
            edits.insert(HabitScheduleEdit(uid = e.uid, target = e.target.name, refUid = e.refUid, scope = encodeScope(e.scope), changes = encodeChanges(e.changes), createdAt = e.createdAt))
        }
    }

    /** §6.3 (5c) — the blocks editor's changes: a block's times with its neighbour's, a name, weekday times. */
    suspend fun saveBlocks(changed: List<HabitBlock>) {
        for (b in changed) blocks.update(b)
    }

    /** Every live check-in on [date], every habit's — the done half of [habitDoneOn]. */
    fun observeLiveOn(date: LocalDate): Flow<List<HabitCompletion>> = completions.observeLiveForDay(date)
    /** A week's, for the Week view (5d). */
    fun observeLiveBetween(from: LocalDate, until: LocalDate): Flow<List<HabitCompletion>> = completions.observeLiveBetween(from, until)
}

/**
 * H3 — whether [habit]'s row reads done on [date], for the surfaces that show a habit as one row
 * (the Journal's strip, the widget). An interval habit: checked in that day. A calendar habit:
 * every one of that day's occurrences has a live check-in in [live]; a day with none is not done.
 */
fun habitDoneOn(habit: Habit, date: LocalDate, calendar: HabitCalendar, live: List<HabitCompletion>): Boolean {
    if (habit.scheduleKind != HabitScheduleKind.CALENDAR) return habit.lastCompletedDate == date
    val keys = calendar.occurrences(habit, date).map { it.key }
    val done = live.filter { it.habitId == habit.id && it.date == date && it.deletedAt == null }.mapNotNull { it.occurrenceKey }.toSet()
    return keys.isNotEmpty() && done.containsAll(keys)
}

/** §6.3 — a calendar habit as the engine reads it; null when its rule is missing or unreadable. */
fun Habit.toPlanHabit(): PlanHabit? {
    val rule = decodeRule(this.calendarRule) ?: return null
    return PlanHabit(
        uid = uid,
        rule = rule,
        minutes = duration?.toMinutes()?.toInt() ?: 0,
        blockUid = blockUid,
        time = time?.let { it.toSecondOfDay() / 60 },
        sortOrder = sortOrder,
        activeFrom = activeFrom,
        activeUntil = activeUntil,
        pause = if (this.pauseFrom == null && this.pauseUntil == null) null else PauseSpan(this.pauseFrom, this.pauseUntil),
    )
}

/** §6.3 — a stored edit in the engine's terms; null when any of its three texts is unreadable. */
fun HabitScheduleEdit.toPlanEdit(): ScheduleEdit? {
    val t = when (target) { "HABIT" -> EditTarget.HABIT; "BLOCK" -> EditTarget.BLOCK; else -> return null }
    return ScheduleEdit(uid, t, refUid, decodeScope(scope) ?: return null, decodeChanges(changes) ?: return null, createdAt)
}
