package com.tendril.app.sync

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.page.PageDao
import com.tendril.app.domain.restoreHabitsFromTrash
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/**
 * Audit 2026-09-24 5.3. The phone arms one habit at a time, through
 * `EntryScheduleCoordinator.onHabitChanged`, and two paths that change a habit never called it:
 * a habit merged in by the folder sync, and a habit restored from Trash. Its reminder stayed unset
 * until the next check-in or edit. (The desktop's `replan()` reads every habit, so it was never
 * affected; the reboot and import sweep is `reconcileAlarms`, pinned on the phone.)
 */
class HabitRearmTest {

    private val at: Instant = Instant.ofEpochMilli(1_000L)
    private val daily = HabitFrequency(1, IntervalUnit.DAY)

    private fun habit(uid: String, updatedAt: Instant = at, deletedAt: Instant? = null) =
        Habit(uid = uid, title = "Walk", frequency = daily, time = LocalTime.of(8, 0), createdAt = at, updatedAt = updatedAt, deletedAt = deletedAt)

    private fun orchestrator(habitDao: FakeHabitDao) = SnapshotSyncOrchestrator(
        entryDao = FakeEntryDao(),
        habitDao = habitDao,
        pageDao = mockk<PageDao>(relaxed = true),
        pagesSyncEngine = mockk(relaxed = true),
        purgeRegistry = mockk(relaxed = true),
        reminderDao = FakeReminderDao(),
        entryCompletionDao = FakeEntryCompletionDao(),
        habitCompletionDao = FakeHabitCompletionDao(),
        checkInDao = FakeCheckInDao(),
        timeLogDao = FakeTimeLogDao(),
        localImages = InMemoryLocalImageStore(),
    )

    @Test
    fun `a habit the merge brings in or changes is named for re-arming`() = runBlocking {
        val store = InMemorySyncFileStore()
        val peer = FakeHabitDao()
        peer.insert(habit("new"))
        peer.insert(habit("moved", updatedAt = at.plusSeconds(60)).copy(time = LocalTime.of(9, 0)))
        orchestrator(peer).writeSnapshots(store)

        val here = FakeHabitDao()
        val movedHere = here.insert(habit("moved"))
        val result = orchestrator(here).readAndMerge(store)

        val newHere = here.getByUid("new")!!.id
        assertEquals(setOf(newHere, movedHere), result.touchedHabitIds.toSet())
    }

    @Test
    fun `a habit restored from Trash is re-armed`() = runBlocking {
        val habitDao = FakeHabitDao()
        val id = habitDao.insert(habit("walk", deletedAt = at))
        val coordinator = RecordingEntryScheduleCoordinator()

        restoreHabitsFromTrash(habitDao, coordinator, listOf(id), at.plusSeconds(60))

        assertEquals(listOf(id), coordinator.habits.map { it.id })
    }
}
