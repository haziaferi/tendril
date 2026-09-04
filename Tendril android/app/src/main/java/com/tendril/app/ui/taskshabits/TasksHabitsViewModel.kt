package com.tendril.app.ui.taskshabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.ResolveEntryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class TasksHabitsViewModel(
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
    private val checkInHabitUseCase: CheckInHabitUseCase,
) : ViewModel() {
    val tasks: StateFlow<List<Entry>> =
        entryDao.observeTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val habits: StateFlow<List<Habit>> =
        habitDao.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTask(title: String, date: LocalDate?, time: LocalTime?, repeat: RecurrenceRule.Elastic?) {
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
                    createdAt = now,
                    updatedAt = now,
                )
            )
            entryDao.getById(id)?.let { entryScheduleCoordinator.onEntryChanged(it) }
        }
    }

    fun addHabit(title: String, frequency: HabitFrequency, time: LocalTime?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = Instant.now()
            habitDao.insert(
                Habit(title = title.trim(), time = time, frequency = frequency, createdAt = now, updatedAt = now)
            )
        }
    }

    fun resolve(entryId: Long, status: EntryStatus) {
        viewModelScope.launch { resolveEntryUseCase.resolve(entryId, status) }
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
    fun checkInHabit(habitId: Long) {
        viewModelScope.launch { checkInHabitUseCase.checkIn(habitId) }
    }

    /** §8.1.1 — reverts today's check-in via the same shared use case the widget's undo
     * action calls, so unchecking here and unchecking there behave identically. */
    fun undoCheckInHabit(habitId: Long) {
        viewModelScope.launch { checkInHabitUseCase.undoCheckIn(habitId) }
    }

    fun trashHabit(habitId: Long) {
        viewModelScope.launch { habitDao.softDelete(habitId, Instant.now()) }
    }
}
