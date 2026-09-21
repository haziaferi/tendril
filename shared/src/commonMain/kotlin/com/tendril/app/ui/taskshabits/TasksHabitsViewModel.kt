package com.tendril.app.ui.taskshabits

import com.tendril.app.data.habit.edited
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

class TasksHabitsViewModel(
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
    private val checkInHabitUseCase: CheckInHabitUseCase,
    private val timeTracker: TimeTracker,
) : ViewModel() {
    val tasks: StateFlow<List<Entry>> =
        entryDao.observeTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val habits: StateFlow<List<Habit>> =
        habitDao.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            entryDao.update(entry.copy(dueDate = deadline, updatedAt = Instant.now()))
        }
    }

    /** 14g·3 — the ladder's set half, 0–4; time pressure is read, never written. */
    fun setImportance(entryId: Long, level: Int) {
        viewModelScope.launch {
            val entry = entryDao.getById(entryId) ?: return@launch
            entryDao.update(entry.copy(importance = level.coerceIn(0, 4), updatedAt = Instant.now()))
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

    /** S10 — the habit's fields edited after creation (`Habit.edited`); the alarm re-arms from the row as it now is (§9.7). */
    fun updateHabit(habit: Habit, title: String, frequency: HabitFrequency, time: LocalTime?, duration: Duration?, unit: String?, amountPerCheckIn: Double?, dailyAmount: Double?) {
        viewModelScope.launch { habitDao.update(habit.edited(title, frequency, time, duration, unit, amountPerCheckIn, dailyAmount, Instant.now())); rearm(habit.id) }
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
