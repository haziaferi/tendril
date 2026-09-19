package com.tendril.app.sync

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §0.6.6 — that a habit check-in reaches the other device, that an *undone* one does not come
 * back, and that a redo on the same day survives the undo's tombstone.
 *
 * The third is the case this table's shape exists for. A reminder is inserted once and deleted
 * once; a check-in is inserted, undone, and quite possibly done again the same afternoon. If the
 * redo revived the tombstoned row, "deleted on any device wins" would delete the redo the moment
 * the tombstone arrived from the peer. So the redo is a new uid, and [redoSurvivesTheUndo] is
 * the proof that the two rows, one dead and one live, merge to "checked in".
 */
class HabitCompletionSyncTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Device(
        habits: List<Habit> = emptyList(),
        completions: List<HabitCompletion> = emptyList(),
    ) {
        val habitDao = FakeHabitDao(habits)
        val completionDao = FakeHabitCompletionDao(completions)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = FakeEntryDao(),
            habitDao = habitDao,
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = completionDao,
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        const val FILE = "habit_completions.json"
        val AT: Instant = Instant.ofEpochMilli(1_000L)
        val DAY: LocalDate = LocalDate.of(2026, 9, 11)

        /** Different local ids on the two devices, so a merge keyed on `id` cannot pass by luck. */
        fun habit(id: Long, uid: String) = Habit(
            id = id,
            uid = uid,
            title = "Habit $uid",
            frequency = HabitFrequency(1, IntervalUnit.DAY),
            createdAt = AT,
            updatedAt = AT,
        )

        fun checkIn(id: Long, uid: String, habitId: Long, deletedAt: Instant? = null) = HabitCompletion(
            id = id,
            uid = uid,
            habitId = habitId,
            date = DAY,
            checkedAt = AT,
            deletedAt = deletedAt,
        )
    }

    private fun publishedIn(store: InMemorySyncFileStore): List<HabitCompletionSnapshotRecord> =
        json.decodeFromString(String(runBlocking { store.readRoot(FILE) }!!, Charsets.UTF_8))

    @Test
    fun `a check-in on one device reaches the other, by habit uid`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(habits = listOf(habit(1, "h1")), completions = listOf(checkIn(1, "c1", habitId = 1)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(habits = listOf(habit(7, "h1")))
        b.orchestrator.readAndMerge(store)
        b.orchestrator.readAndMerge(store) // the ordinary second pass must not duplicate it

        val live = b.completionDao.getLiveForDay(7, DAY)
        assertEquals(1, live.size)
        assertEquals("c1", live.single().uid)
        assertEquals(7L, live.single().habitId)
        assertEquals("h1", publishedIn(store).single().habitUid)
    }

    @Test
    fun `an undone check-in does not come back from a peer that still holds it live`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(habits = listOf(habit(1, "h1")), completions = listOf(checkIn(1, "c1", 1, deletedAt = AT)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(habits = listOf(habit(7, "h1")), completions = listOf(checkIn(3, "c1", 7)))
        b.orchestrator.readAndMerge(store)

        assertTrue(b.completionDao.getLiveForDay(7, DAY).isEmpty())
        val row = b.completionDao.getByUid("c1")
        assertNotNull(row)
        assertEquals(AT, row!!.deletedAt)

        // And B republishes the tombstone rather than going quiet about it.
        val onward = InMemorySyncFileStore()
        b.orchestrator.writeSnapshots(onward)
        assertEquals(AT.toEpochMilli(), publishedIn(onward).single { it.uid == "c1" }.deletedAt)
    }

    @Test
    fun redoSurvivesTheUndo() = runBlocking {
        val store = InMemorySyncFileStore()
        // A checked in (c1), undid it, and checked in again (c2) — two rows for one day.
        val a = Device(
            habits = listOf(habit(1, "h1")),
            completions = listOf(checkIn(1, "c1", 1, deletedAt = AT), checkIn(2, "c2", 1)),
        )
        a.orchestrator.writeSnapshots(store)

        // B still holds c1 live from before the undo reached it.
        val b = Device(habits = listOf(habit(7, "h1")), completions = listOf(checkIn(3, "c1", 7)))
        b.orchestrator.readAndMerge(store)

        // The day reads as checked in, through c2, and c1 is the tombstone it should be. Had the
        // redo revived c1 instead of inserting c2, this device would now hold a single dead row.
        val live = b.completionDao.getLiveForDay(7, DAY)
        assertEquals(listOf("c2"), live.map { it.uid })
        assertEquals(AT, b.completionDao.getByUid("c1")!!.deletedAt)
    }

    @Test
    fun `a check-in whose habit has not merged here is republished, not deleted from the folder`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(
            FILE,
            json.encodeToString(
                listOf(HabitCompletionSnapshotRecord(uid = "c1", habitUid = "not-here", date = "2026-09-11", checkedAt = 1_000L)),
            ),
        )

        val b = Device()
        b.orchestrator.readAndMerge(store)
        assertNull(b.completionDao.getByUid("c1"))

        b.orchestrator.writeSnapshots(store)
        assertEquals("c1", publishedIn(store).single().uid)
    }
}
