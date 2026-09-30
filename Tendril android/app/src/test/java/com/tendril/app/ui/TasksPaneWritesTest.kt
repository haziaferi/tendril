package com.tendril.app.ui

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.track.TimeTracker
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.FakeHabitCompletionDao
import com.tendril.app.sync.FakeHabitDao
import com.tendril.app.sync.FakeTimeLogDao
import com.tendril.app.ui.taskshabits.TasksHabitsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Two writes 5a.6's sweep did not list: the task pane's Deadline and Urgency. Each stamped
 * `updatedAt` whatever it wrote, so picking the urgency a task already had, or confirming its
 * deadline unchanged, claimed an edit — under §9.4's last-write-wins, one that beats a real edit
 * on the other device.
 */
class TasksPaneWritesTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val at: Instant = Instant.ofEpochMilli(1_000L)
    private val entryDao = FakeEntryDao()
    private val coordinator = object : EntryScheduleCoordinator {
        override suspend fun onEntryChanged(entry: Entry) = Unit
        override suspend fun onEntryRemoved(entry: Entry) = Unit
    }

    @Before fun installMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(): TasksHabitsViewModel {
        val habitDao = FakeHabitDao()
        val habitCompletionDao = FakeHabitCompletionDao()
        return TasksHabitsViewModel(
            entryDao, habitDao, habitCompletionDao,
            ResolveEntryUseCase(entryDao, FakeEntryCompletionDao(), coordinator), coordinator,
            CheckInHabitUseCase(habitDao, habitCompletionDao, com.tendril.app.domain.plan.HabitCalendarSource(com.tendril.app.sync.FakeHabitBlockDao(), com.tendril.app.sync.FakeHabitScheduleEditDao(), habitCompletionDao)), TimeTracker(FakeTimeLogDao()),
            com.tendril.app.domain.plan.HabitCalendarSource(com.tendril.app.sync.FakeHabitBlockDao(), com.tendril.app.sync.FakeHabitScheduleEditDao(), habitCompletionDao),
        )
    }

    private suspend fun task(): Long = entryDao.insert(
        Entry(title = "Call bank", kind = EntryKind.TASK, startDate = null, startTime = null, endDate = null, endTime = null,
            recurrenceRule = null, status = EntryStatus.PENDING, dueDate = LocalDate.of(2026, 9, 30), importance = 2,
            createdAt = at, updatedAt = at),
    )

    @Test
    fun `picking the urgency a task already has moves no timestamp`() = runTest(dispatcher) {
        val id = task()
        viewModel().setImportance(id, 2)
        assertEquals(at, entryDao.getById(id)!!.updatedAt)
    }

    @Test
    fun `setting the deadline a task already has moves no timestamp`() = runTest(dispatcher) {
        val id = task()
        viewModel().setDeadline(id, LocalDate.of(2026, 9, 30))
        assertEquals(at, entryDao.getById(id)!!.updatedAt)
    }

    /** Control: a real change still lands and is stamped. */
    @Test
    fun `a new urgency lands`() = runTest(dispatcher) {
        val id = task()
        viewModel().setImportance(id, 4)
        val stored = entryDao.getById(id)!!
        assertEquals(4, stored.importance)
        assertEquals(true, stored.updatedAt.isAfter(at))
    }
}
