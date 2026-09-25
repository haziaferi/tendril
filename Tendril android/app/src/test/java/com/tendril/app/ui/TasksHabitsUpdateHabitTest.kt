package com.tendril.app.ui

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
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
 * Audit 2026-09-24 5a.6, `updateHabit`. The edit sheet hands back the habit as it was when the
 * sheet opened, and the write was that snapshot with the sheet's fields laid over it — so a
 * check-in made while the sheet was open (the row's own tap, the widget, a merge) was written
 * back out of existence, streak and all. And saving an untouched sheet still stamped `updatedAt`,
 * a claim of an edit that under §9.4's last-write-wins beats a real one on the other device.
 */
class TasksHabitsUpdateHabitTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val at: Instant = Instant.ofEpochMilli(1_000L)
    private val daily = HabitFrequency(1, IntervalUnit.DAY)

    private val habitDao = FakeHabitDao()
    private val habitCompletionDao = FakeHabitCompletionDao()
    private val checkIn = CheckInHabitUseCase(habitDao, habitCompletionDao)
    private val coordinator = object : EntryScheduleCoordinator {
        override suspend fun onEntryChanged(entry: Entry) = Unit
        override suspend fun onEntryRemoved(entry: Entry) = Unit
    }

    @Before fun installMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(): TasksHabitsViewModel {
        val entryDao = FakeEntryDao()
        return TasksHabitsViewModel(
            entryDao, habitDao, habitCompletionDao,
            ResolveEntryUseCase(entryDao, FakeEntryCompletionDao(), coordinator), coordinator, checkIn, TimeTracker(FakeTimeLogDao()),
        )
    }

    private suspend fun seeded(): Habit =
        habitDao.getById(habitDao.insert(Habit(title = "Water", frequency = daily, createdAt = at, updatedAt = at)))!!

    @Test
    fun `a check-in made while the sheet was open survives the sheet's save`() = runTest(dispatcher) {
        val sheet = seeded()
        checkIn.checkIn(sheet.id, LocalDate.of(2026, 9, 25))
        val checkedIn = habitDao.getById(sheet.id)!!

        viewModel().updateHabit(sheet, "Water, 2 l", daily, null, null, null, null, null)

        val saved = habitDao.getById(sheet.id)!!
        assertEquals("the sheet's edit lands", "Water, 2 l", saved.title)
        assertEquals("and the check-in it never saw is still there", checkedIn.streak, saved.streak)
        assertEquals(checkedIn.lastCompletedDate, saved.lastCompletedDate)
    }

    @Test
    fun `saving an untouched sheet moves no timestamp`() = runTest(dispatcher) {
        val sheet = seeded()

        viewModel().updateHabit(sheet, sheet.title, sheet.frequency, sheet.time, sheet.duration, sheet.unit, sheet.amountPerCheckIn, sheet.dailyAmount)

        assertEquals(at, habitDao.getById(sheet.id)!!.updatedAt)
    }
}
