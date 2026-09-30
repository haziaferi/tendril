package com.tendril.app.ui.taskshabits

import com.tendril.app.data.habit.edited
import com.tendril.app.domain.plan.HabitCalendarSource
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.habitDay
import com.tendril.app.domain.plan.DayRow
import com.tendril.app.domain.plan.EditPos
import com.tendril.app.domain.plan.MovePlan
import com.tendril.app.domain.plan.PlanWrites
import com.tendril.app.domain.plan.ScheduleEdit
import com.tendril.app.domain.plan.ScopeChoice
import com.tendril.app.domain.plan.editGroups
import com.tendril.app.domain.plan.moveChoices
import com.tendril.app.domain.plan.moveToDayWrites
import com.tendril.app.domain.plan.moveWrites
import com.tendril.app.domain.plan.originOf
import com.tendril.app.domain.plan.planMove
import com.tendril.app.domain.plan.skipChoices
import com.tendril.app.domain.plan.skipWrites
import com.tendril.app.domain.plan.toPlanEdit
import com.tendril.app.domain.plan.toPlanHabit
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.domain.track.TimeTracker
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.track.loggedInMonth
import com.tendril.app.domain.track.minuteTicker
import com.tendril.app.domain.plan.loggedByEntry
import com.tendril.app.domain.plan.loggedByHabit
import com.tendril.app.domain.plan.minutesPerSession
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.HabitPresence
import com.tendril.app.domain.PostponeAmount
import com.tendril.app.domain.habitPresenceOf
import com.tendril.app.domain.postponed
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.ResolveEntryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The uid a habit being drafted in the add sheet goes by in the engine, for its suggestion (D5). */
private const val DRAFT_UID = "draft"

@OptIn(ExperimentalCoroutinesApi::class)
class TasksHabitsViewModel(
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
    private val checkInHabitUseCase: CheckInHabitUseCase,
    private val timeTracker: TimeTracker,
    /** §6.3 (5a) — the blocks, edits and check-ins the Day view is built from. */
    private val habitCalendarSource: HabitCalendarSource,
) : ViewModel() {
    val tasks: StateFlow<List<Entry>> =
        entryDao.observeTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val habits: StateFlow<List<Habit>> =
        habitDao.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val day = MutableStateFlow(LocalDate.now())

    /** §6.3 (5a) — the day the Day view shows; today until the person steps to another. */
    val shownDay: StateFlow<LocalDate> = day.asStateFlow()

    fun showDay(date: LocalDate) { day.value = date }

    /** The date as the clock has it, re-read each minute, so a view left open past midnight moves on. */
    private val todayFlow = minuteTicker().map { LocalDate.now() }.distinctUntilChanged()

    /**
     * §6.3 (5a) — the shown day, by time: every habit of both kinds placed in the day's blocks, and
     * what was done ([habitDay]). Null until the first read has arrived.
     */
    val dayView: StateFlow<HabitDayView?> = combine(
        habits,
        habitCalendarSource.observeRows(),
        day.flatMapLatest { d -> habitCalendarSource.observeLiveOn(d).map { d to it } },
        todayFlow,
    ) { hs, (blocks, edits), (d, checkIns), today -> habitDay(d, today, hs, blocks, edits, checkIns) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** §6.3 (5b) — the entry in edit mode: which one, where it was, where it is now (pending until *Done*). */
    data class EditSelection(val habitId: Long, val key: String?, val origin: EditPos, val pos: EditPos)

    private val selection = MutableStateFlow<EditSelection?>(null)
    val editSelection: StateFlow<EditSelection?> = selection.asStateFlow()

    /** The stored blocks and edits as rows, held for the prompts, which read another day's blocks. */
    private val rows = habitCalendarSource.observeRows().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<com.tendril.app.data.habit.HabitBlock>() to emptyList())

    /** §6.3 (5c) — the blocks as stored, for the add sheet's *Where* and the blocks editor. */
    val blocks: StateFlow<List<com.tendril.app.data.habit.HabitBlock>> = rows.map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The stored edits in the engine's terms — what a change on a day starts from (`effectiveOn`). */
    private val planEdits: StateFlow<List<ScheduleEdit>> = rows.map { (_, edits) -> edits.mapNotNull { it.toPlanEdit() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The selected entry as the current view has it, or null. */
    fun selectedRow(view: HabitDayView, sel: EditSelection): DayRow? =
        (view.blocks.flatMap { it.timed + it.flexible } + view.anyTime + view.outside).firstOrNull { it.habit.id == sel.habitId && it.key == sel.key }

    /** A tap on an entry of today or a later day (T2); a past day is read-only, so nothing is selected there. */
    fun select(row: DayRow) {
        val v = dayView.value ?: return
        if (v.readOnly) return
        val origin = originOf(v, row)
        selection.value = EditSelection(row.habit.id, row.key, origin, origin)
    }

    fun moveSelectedUp() = step { groups, pos -> com.tendril.app.domain.plan.moveUp(groups, pos) }
    fun moveSelectedDown() = step { groups, pos -> com.tendril.app.domain.plan.moveDown(groups, pos) }

    private fun step(f: (List<com.tendril.app.domain.plan.EditGroup>, EditPos) -> EditPos) {
        val v = dayView.value ?: return
        val sel = selection.value ?: return
        val row = selectedRow(v, sel) ?: return
        selection.value = sel.copy(pos = f(editGroups(v, row), sel.pos))
    }

    /** *Move to…* a block on the same day: the entry goes to the end of that block's rest, then *Done*'s questions. */
    fun placeSelectedAtEndOf(blockUid: String) {
        val v = dayView.value ?: return
        val sel = selection.value ?: return
        val row = selectedRow(v, sel) ?: return
        val groups = editGroups(v, row)
        val g = groups.indexOfFirst { it.blockUid == blockUid }.takeIf { it >= 0 } ?: return
        selection.value = sel.copy(pos = EditPos(g, timed = false, index = groups[g].flexible.size))
    }

    /** Declining any prompt puts the entry back (the planner's rule). */
    fun cancelEdit() { selection.value = null }

    /** What *Done* has to settle; null when the entry is back where it started. */
    fun pendingMove(): MovePlan? {
        val v = dayView.value ?: return null
        val sel = selection.value ?: return null
        val row = selectedRow(v, sel) ?: return null
        return planMove(v, row, sel.origin, sel.pos)
    }

    fun moveChoicesFor(plan: MovePlan, time: Int?): List<ScopeChoice> = dayView.value?.let { moveChoices(it, plan, time, planEdits.value) } ?: emptyList()

    fun commitMove(plan: MovePlan, time: Int?, choice: ScopeChoice?, days: Set<DayOfWeek>?) {
        val v = dayView.value ?: return
        val w = moveWrites(v, plan, time, choice, days, planEdits.value, Instant.now(), ::newUid)
        selection.value = null
        viewModelScope.launch { persist(w) }
    }

    fun skipChoicesFor(row: DayRow): List<ScopeChoice> = dayView.value?.let { skipChoices(it, row) } ?: emptyList()

    fun commitSkip(row: DayRow, choice: ScopeChoice, days: Set<DayOfWeek>?) {
        val v = dayView.value ?: return
        val e = skipWrites(v, row, choice, days, Instant.now(), ::newUid)
        selection.value = null
        viewModelScope.launch { habitCalendarSource.addEdits(listOf(e)) }
    }

    fun canMoveSelectedWeekly(row: DayRow): Boolean = dayView.value?.let { com.tendril.app.domain.plan.canMoveWeekly(row, planEdits.value, it.date) } ?: false

    /** Whether [minute] falls in [blockUid] as [date] has it — *Move to…* another day asks to remove a time that does not. */
    fun timeFitsBlockOn(date: LocalDate, blockUid: String, minute: Int): Boolean {
        val (blocks, edits) = rows.value
        val day = habitDay(date, date, emptyList(), blocks, edits, emptyList())
        return day.blocks.any { it.block.uid == blockUid && it.start <= minute && minute < it.end }
    }

    fun commitMoveToDay(row: DayRow, target: LocalDate, blockUid: String, removeTime: Boolean, weekly: Boolean) {
        val v = dayView.value ?: return
        val out = moveToDayWrites(v, row, target, blockUid, removeTime, weekly, planEdits.value, Instant.now(), ::newUid)
        selection.value = null
        viewModelScope.launch { habitCalendarSource.addEdits(out) }
    }

    private suspend fun persist(w: PlanWrites) {
        habitCalendarSource.addEdits(w.edits)
        for (h in w.habits) {
            // over the row as it is now, as `updateHabit` does: a check-in made meanwhile must survive
            val live = habitDao.getById(h.id) ?: continue
            habitDao.update(live.copy(time = h.time, blockUid = h.blockUid, sortOrder = h.sortOrder, updatedAt = h.updatedAt))
        }
    }

    private fun newUid() = UUID.randomUUID().toString()

    /**
     * §6.3 (5c) — the add and edit sheet's save, both kinds: a new habit, or [initial]'s fields laid
     * over the row as it is now (a check-in made while the sheet was open survives, as in
     * [updateHabit]); an untouched sheet writes nothing.
     */
    fun saveHabit(initial: Habit?, form: HabitForm) {
        if (form.title.isBlank()) return
        viewModelScope.launch {
            val now = Instant.now()
            fun Habit.withPlan() = copy(
                scheduleKind = form.scheduleKind, calendarRule = form.calendarRule, blockUid = form.blockUid,
                labelId = form.labelId, note = form.note, activeFrom = form.activeFrom, activeUntil = form.activeUntil,
            )
            if (initial == null) {
                val id = habitDao.insert(
                    Habit(
                        title = form.title.trim(), time = form.time, duration = form.duration, frequency = form.frequency,
                        unit = form.unit?.trim()?.takeIf { it.isNotEmpty() }, amountPerCheckIn = form.amountPerCheckIn, dailyAmount = form.dailyAmount,
                        createdAt = now, updatedAt = now,
                    ).withPlan(),
                )
                habitDao.getById(id)?.let { entryScheduleCoordinator.onHabitChanged(it) }
                return@launch
            }
            val live = habitDao.getById(initial.id) ?: return@launch
            val edited = live.edited(form.title, form.frequency, form.time, form.duration, form.unit, form.amountPerCheckIn, form.dailyAmount, now).withPlan()
            if (edited.copy(updatedAt = live.updatedAt) == live) return@launch
            habitDao.update(edited)
            rearm(initial.id)
        }
    }

    /** §6.3 (5c) — the planner's pause: from [from] until [until], or until resumed (null). Its check-ins stay. */
    fun pauseHabit(habitId: Long, from: LocalDate, until: LocalDate?) {
        viewModelScope.launch {
            val live = habitDao.getById(habitId) ?: return@launch
            habitDao.update(live.copy(pauseFrom = from, pauseUntil = until, updatedAt = Instant.now()))
            rearm(habitId)
        }
    }

    fun resumeHabit(habitId: Long) {
        viewModelScope.launch {
            val live = habitDao.getById(habitId) ?: return@launch
            if (live.pauseFrom == null && live.pauseUntil == null) return@launch
            habitDao.update(live.copy(pauseFrom = null, pauseUntil = null, updatedAt = Instant.now()))
            rearm(habitId)
        }
    }

    /** The blocks editor: the planner's rule, already checked by the sheet ([com.tendril.app.domain.plan.blockEditError]). */
    fun saveBlockTimes(uid: String, start: Int, end: Int) {
        val changed = com.tendril.app.domain.plan.editBlockTimes(rows.value.first, uid, start, end, Instant.now())
        viewModelScope.launch { habitCalendarSource.saveBlocks(changed) }
    }

    /** H1 — a name typed; null goes back to the default's, in the app's language. */
    fun renameBlock(uid: String, name: String?) {
        val b = rows.value.first.firstOrNull { it.uid == uid } ?: return
        viewModelScope.launch { habitCalendarSource.saveBlocks(listOf(b.copy(name = name, updatedAt = Instant.now()))) }
    }

    /** H2 — a block's weekday times, as `PlanCodec` writes them. */
    fun setBlockOverrides(uid: String, text: String?) {
        val b = rows.value.first.firstOrNull { it.uid == uid } ?: return
        viewModelScope.launch { habitCalendarSource.saveBlocks(listOf(b.copy(overrides = text, updatedAt = Instant.now()))) }
    }

    /** D5 — the days this week the engine would suggest for a "times a week" [rule] being drafted, beside the habits already there. */
    fun weekSuggestion(rule: com.tendril.app.domain.plan.CalendarRule, minutes: Int): List<LocalDate> {
        // The walk (5c): the engine spreads over the whole week, as the planner did, and suggested a
        // day already past for a habit made on a Wednesday. What the sheet shows is days still to come.
        val today = LocalDate.now()
        val weekly = rule as? com.tendril.app.domain.plan.CalendarRule.TimesPerWeek ?: return emptyList()
        val remaining = weekly.days.filter { d -> d >= today.dayOfWeek }.toSet().takeIf { it.isNotEmpty() } ?: return emptyList()
        val (blockRows, editRows) = rows.value
        val draft = Habit(uid = DRAFT_UID, title = "", frequency = HabitFrequency(1, com.tendril.app.data.entry.IntervalUnit.DAY), duration = java.time.Duration.ofMinutes(minutes.toLong()), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        return com.tendril.app.domain.plan.suggestedDays(DRAFT_UID, weekly.copy(days = remaining), habits.value + draft, blockRows, editRows, com.tendril.app.domain.plan.mondayOf(today))
    }

    private val week = MutableStateFlow(com.tendril.app.domain.plan.mondayOf(LocalDate.now()))

    /** §6.3 (5d) — the Monday of the week the Week view shows. */
    val shownWeek: StateFlow<LocalDate> = week.asStateFlow()

    fun showWeek(monday: LocalDate) { week.value = com.tendril.app.domain.plan.mondayOf(monday) }

    /** §6.3 (5d) — the shown week: seven days as the Day view builds them, and Q1's suggestions. */
    val weekView: StateFlow<com.tendril.app.domain.plan.HabitWeekView?> = combine(
        habits,
        habitCalendarSource.observeRows(),
        week.flatMapLatest { m -> habitCalendarSource.observeLiveBetween(m, m.plusDays(6)).map { m to it } },
        todayFlow,
    ) { hs, (blocks, edits), (m, checkIns), today -> com.tendril.app.domain.plan.habitWeek(m, today, hs, blocks, edits, checkIns) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** §6.3 (Phase 6) — any week as the Week view builds it, read once, for an export. */
    suspend fun planWeek(monday: LocalDate): com.tendril.app.domain.plan.HabitWeekView {
        val (blocks, edits) = habitCalendarSource.observeRows().first()
        val checkIns = habitCalendarSource.observeLiveBetween(monday, monday.plusDays(6)).first()
        // The DAO's own flow, not [habits]: a `WhileSubscribed` state can still hold its empty initial value.
        return com.tendril.app.domain.plan.habitWeek(monday, LocalDate.now(), habitDao.observeActive().first(), blocks, edits, checkIns)
    }

    /** Q1 — *Confirm*: the week's days for an "X times a week" habit, that week alone. */
    fun confirmWeek(habit: Habit, monday: LocalDate, days: Set<DayOfWeek>) {
        val e = com.tendril.app.domain.plan.confirmWeekDays(habit, monday, days, Instant.now(), newUid())
        viewModelScope.launch { habitCalendarSource.addEdits(listOf(e)) }
    }

    /** P3 — one occurrence of a calendar habit checked in; an interval habit's row has no key and checks in as ever. */
    fun checkInOccurrence(habitId: Long, key: String?) {
        viewModelScope.launch { checkInHabitUseCase.checkIn(habitId, occurrenceKey = key); rearm(habitId) }
    }

    fun undoOccurrence(habitId: Long, key: String?) {
        viewModelScope.launch { checkInHabitUseCase.undoCheckIn(habitId, occurrenceKey = key); rearm(habitId) }
    }

    /** §0.6.5 / step 7c — ▶/■ on a row; one timer at a time, see [TimeTracker.toggle]. */
    fun toggleTracking(target: TrackTarget) {
        viewModelScope.launch { timeTracker.toggle(target) }
    }

    /** §0.8 step 7d — minutes logged today per entry and per habit, ticking with the open log. */
    val loggedToday: StateFlow<Pair<Map<Long, Int>, Map<Long, Int>>> =
        combine(timeTracker.logsOn(LocalDate.now()), minuteTicker()) { logs, now -> loggedByEntry(logs, now) to loggedByHabit(logs, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap<Long, Int>() to emptyMap())

    /** §0.10 item 3 — today's logged amount per counting habit, for the rows' meta (*2 cups today*). */
    val amountsToday: StateFlow<Map<Long, Double>> = habitCompletionDao.observeLiveForDay(LocalDate.now())
        .map { rows -> rows.filter { it.value != null }.groupBy { it.habitId }.mapValues { (_, r) -> r.sumOf { it.value!! } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The habit's mean session, for the presence sheet's "about N min each". */
    fun habitMinutesPerSession(habitId: Long): Flow<Int?> = timeTracker.logsForHabit(habitId).map { minutesPerSession(it) }

    /** The habit's logged minutes this month, for the presence sheet — live, so it ticks. */
    fun habitLoggedThisMonth(habitId: Long): Flow<Int> =
        timeTracker.logsForHabit(habitId).map { loggedInMonth(it, YearMonth.now(), Instant.now()) }

    fun addTask(
        title: String,
        date: LocalDate?,
        time: LocalTime?,
        repeat: RecurrenceRule.Elastic?,
        deadline: LocalDate? = null,
        parentEntryId: Long? = null,
        estimate: Duration? = null,
        importance: Int = 0,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = Instant.now()
            val id = entryDao.insert(
                Entry(
                    title = title.trim(),
                    kind = EntryKind.TASK,
                    startDate = date,
                    startTime = time,
                    endDate = null,
                    endTime = null,
                    recurrenceRule = repeat,
                    status = EntryStatus.PENDING,
                    dueDate = deadline,
                    parentEntryId = parentEntryId,
                    estimate = estimate,
                    importance = importance.coerceIn(0, 4),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            entryDao.getById(id)?.let { entryScheduleCoordinator.onEntryChanged(it) }
        }
    }

    /** §0.6.4 — a checklist-style sub-task: no date of its own, so it lives under its parent
     * wherever the parent is filtered to, and never appears on Calendar by itself. */
    fun addSubtask(parentEntryId: Long, title: String) =
        addTask(title, date = null, time = null, repeat = null, parentEntryId = parentEntryId)

    /** §0.6.4's Postpone — moves the *When*; see [postponed] for why never the Deadline. Re-arms
     * alarms from the row as it now stands, as every write that moves a date does. */
    fun postpone(entryId: Long, by: PostponeAmount) {
        viewModelScope.launch {
            val entry = entryDao.getById(entryId) ?: return@launch
            val moved = entry.postponed(by, LocalDate.now(), LocalTime.now())
            entryDao.update(moved)
            entryScheduleCoordinator.onEntryChanged(moved)
        }
    }

    fun setDeadline(entryId: Long, deadline: LocalDate?) {
        viewModelScope.launch {
            val entry = entryDao.getById(entryId) ?: return@launch
            // Unchanged writes nothing: under §9.4 the stamp alone claims an edit (5a.6's class).
            if (entry.dueDate == deadline) return@launch
            entryDao.update(entry.copy(dueDate = deadline, updatedAt = Instant.now()))
        }
    }

    /** 14g·3 — the ladder's set half, 0–4; time pressure is read, never written. */
    fun setImportance(entryId: Long, level: Int) {
        viewModelScope.launch {
            val entry = entryDao.getById(entryId) ?: return@launch
            val importance = level.coerceIn(0, 4)
            if (entry.importance == importance) return@launch
            entryDao.update(entry.copy(importance = importance, updatedAt = Instant.now()))
        }
    }

    /** §0.6.6 — what the habit detail is allowed to say. Computed, never stored. */
    fun habitPresence(habitId: Long): Flow<HabitPresence> =
        habitCompletionDao.observeForHabit(habitId).map { habitPresenceOf(it, LocalDate.now()) }

    fun addHabit(title: String, frequency: HabitFrequency, time: LocalTime?, duration: Duration? = null, unit: String? = null, amountPerCheckIn: Double? = null, dailyAmount: Double? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = Instant.now()
            habitDao.insert(
                Habit(
                    title = title.trim(),
                    time = time,
                    duration = duration,
                    frequency = frequency,
                    unit = unit?.trim()?.takeIf { it.isNotEmpty() },
                    amountPerCheckIn = amountPerCheckIn,
                    dailyAmount = dailyAmount,
                    createdAt = now,
                    updatedAt = now,
                )
            ).let { id -> habitDao.getById(id)?.let { entryScheduleCoordinator.onHabitChanged(it) } }
        }
    }

    /** §9.8 R1 — the checked/unchecked decision lives in [ResolveEntryUseCase.setDone], not in
     * each surface's own ViewModel. */
    fun setDone(entryId: Long, done: Boolean) {
        viewModelScope.launch { resolveEntryUseCase.setDone(entryId, done) }
    }

    /** Unchecking is an undo, not a second resolution. [ResolveEntryUseCase.resolve] only
     * takes a terminal status, so routing "unchecked" to `SKIPPED` logged a *second*
     * [com.tendril.app.data.completion.EntryCompletion] for one real-world act and, on a
     * recurring task, advanced `startDate` by another period — the opposite of undoing it.
     * §5.2 exposes Skipped as its own state, not as the meaning of clearing the box. */
    fun unresolve(entryId: Long) {
        viewModelScope.launch { resolveEntryUseCase.unresolve(entryId) }
    }

    fun trashTask(entryId: Long) {
        viewModelScope.launch { resolveEntryUseCase.trash(entryId) }
    }

    /** §6.1's "no backlog" test — see [CheckInHabitUseCase], shared with the Habits widget
     * (§8) so both surfaces use the exact same streak math. */
    fun checkInHabit(habitId: Long, value: Double? = null) {
        viewModelScope.launch { checkInHabitUseCase.checkIn(habitId, value = value); rearm(habitId) }
    }

    /** §8.1.1 — reverts today's check-in via the same shared use case the widget's undo
     * action calls, so unchecking here and unchecking there behave identically. */
    fun undoCheckInHabit(habitId: Long) {
        viewModelScope.launch { checkInHabitUseCase.undoCheckIn(habitId); rearm(habitId) }
    }

    /**
     * S10 — the habit's fields edited after creation (`Habit.edited`); the alarm re-arms from the
     * row as it now is (§9.7).
     *
     * The sheet's fields go over the row as it is **now**, not over [habit], which is the sheet's
     * copy from when it opened: laying them over that copy wrote back its streak and completion
     * dates, undoing a check-in made while the sheet was open. An untouched sheet writes nothing,
     * since under §9.4 the stamp alone claims an edit (audit 5a.6, both).
     */
    fun updateHabit(habit: Habit, title: String, frequency: HabitFrequency, time: LocalTime?, duration: Duration?, unit: String?, amountPerCheckIn: Double?, dailyAmount: Double?) {
        viewModelScope.launch {
            val live = habitDao.getById(habit.id) ?: return@launch
            val edited = live.edited(title, frequency, time, duration, unit, amountPerCheckIn, dailyAmount, Instant.now())
            if (edited.copy(updatedAt = live.updatedAt) == live) return@launch
            habitDao.update(edited)
            rearm(habit.id)
        }
    }

    fun trashHabit(habitId: Long) {
        // rescheduleHabit clears the alarm rather than setting one for a trashed habit, so
        // trash and restore both route through the same single call.
        viewModelScope.launch { habitDao.softDelete(habitId, Instant.now()); rearm(habitId) }
    }

    /** §9.7 — every write that moves when a habit is next due re-arms from the row as it now
     * stands, rather than each call site working out the new trigger for itself. */
    private suspend fun rearm(habitId: Long) {
        habitDao.getById(habitId)?.let { entryScheduleCoordinator.onHabitChanged(it) }
    }
}
