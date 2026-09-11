package com.tendril.app.domain

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.sync.FakeHabitCompletionDao
import com.tendril.app.sync.FakeHabitDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §0.6.6 — the check-in funnel writes the log, and the log has the shape the merge needs: an
 * undo tombstones, a redo inserts a new row rather than reviving the old one, and a repeated tap
 * on an already-checked day writes nothing.
 */
class CheckInHabitLogTest {

    private val habitDao = FakeHabitDao()
    private val logDao = FakeHabitCompletionDao()
    private val useCase = CheckInHabitUseCase(habitDao, logDao)
    private val today = LocalDate.of(2026, 9, 11)

    private fun seed(): Long = runBlocking {
        habitDao.insert(
            Habit(
                title = "Meditate",
                frequency = HabitFrequency(1, IntervalUnit.DAY),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

    @Test
    fun `a check-in writes one live row for the day`() = runBlocking {
        val id = seed()
        useCase.checkIn(id, today)

        val live = logDao.getLiveForDay(id, today)
        assertEquals(1, live.size)
        assertEquals(today, live.single().date)
        assertNull(live.single().deletedAt)
        // The streak columns still move, as a cache — nothing that reads them has changed yet.
        assertEquals(today, habitDao.getById(id)!!.lastCompletedDate)
    }

    @Test
    fun `a second tap on a checked day writes nothing`() = runBlocking {
        val id = seed()
        useCase.checkIn(id, today)
        useCase.checkIn(id, today)

        assertEquals(1, logDao.getAll().size)
    }

    @Test
    fun `undo tombstones the row, and a redo is a new row`() = runBlocking {
        val id = seed()
        useCase.checkIn(id, today)
        val first = logDao.getAll().single()

        useCase.undoCheckIn(id, today)
        assertTrue(logDao.getLiveForDay(id, today).isEmpty())
        assertNotNull(logDao.getByUid(first.uid)!!.deletedAt)

        useCase.checkIn(id, today)
        val rows = logDao.getAll()
        assertEquals(2, rows.size)
        // The tombstone stays a tombstone; the redo is the other row.
        assertNotNull(logDao.getByUid(first.uid)!!.deletedAt)
        assertEquals(1, logDao.getLiveForDay(id, today).size)
        assertTrue(logDao.getLiveForDay(id, today).single().uid != first.uid)
    }

    @Test
    fun `undo on a day that was never checked in writes nothing`() = runBlocking {
        val id = seed()
        useCase.undoCheckIn(id, today)
        assertTrue(logDao.getAll().isEmpty())
    }
}
