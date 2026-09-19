package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.track.TimeLog
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * §0.6.5 / step 7c — tracked time between devices. A log is the one record that is both edited
 * (opened, then closed) and tombstoned, so the rule under test is the two others combined:
 * deleted on any device wins, else the later `updatedAt`. And the negative control every
 * folder-wide file has: a record whose owner has not merged here is republished, not erased.
 */
class TimeLogSyncTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Device(
        entries: List<Entry> = emptyList(),
        habits: List<Habit> = emptyList(),
        logs: List<TimeLog> = emptyList(),
    ) {
        val entryDao = FakeEntryDao(entries)
        val habitDao = FakeHabitDao(habits)
        val timeLogDao = FakeTimeLogDao(logs)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = habitDao,
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = timeLogDao,
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        const val FILE = "time_logs.json"
        val T0: Instant = Instant.ofEpochMilli(1_000L)
        val T1: Instant = Instant.ofEpochMilli(2_000L)
        val T2: Instant = Instant.ofEpochMilli(3_000L)

        fun task(id: Long, uid: String) = Entry(
            id = id, uid = uid, title = "Task $uid", kind = EntryKind.TASK, status = EntryStatus.PENDING,
            startDate = null, startTime = null, endDate = null, endTime = null, recurrenceRule = null,
            createdAt = T0, updatedAt = T0,
        )

        fun habit(id: Long, uid: String) = Habit(
            id = id, uid = uid, title = "Habit $uid", frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = T0, updatedAt = T0,
        )

        fun log(id: Long, uid: String, entryId: Long? = null, habitId: Long? = null, endedAt: Instant? = null, deletedAt: Instant? = null, updatedAt: Instant = T0) =
            TimeLog(id = id, uid = uid, entryId = entryId, habitId = habitId, startedAt = T0, endedAt = endedAt, deletedAt = deletedAt, updatedAt = updatedAt)
    }

    private fun publishedIn(store: InMemorySyncFileStore): List<TimeLogSnapshotRecord> =
        json.decodeFromString(String(runBlocking { store.readRoot(FILE) }!!, Charsets.UTF_8))

    @Test
    fun `a running timer reaches the other device, by owner uid, and runs there too`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(entries = listOf(task(1, "e1")), habits = listOf(habit(1, "h1")), logs = listOf(log(1, "l1", entryId = 1), log(2, "l2", habitId = 1, endedAt = T1)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(entries = listOf(task(9, "e1")), habits = listOf(habit(4, "h1")))
        b.orchestrator.readAndMerge(store)
        b.orchestrator.readAndMerge(store)

        assertEquals(listOf("l1"), b.timeLogDao.getRunning().map { it.uid })
        assertEquals(9L, b.timeLogDao.getByUid("l1")!!.entryId)
        assertEquals(4L, b.timeLogDao.getByUid("l2")!!.habitId)
        assertEquals(setOf("e1", "h1"), publishedIn(store).map { it.entryUid ?: it.habitUid }.toSet())
    }

    @Test
    fun `a stop on one device closes the log on the other, the later write winning`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(entries = listOf(task(1, "e1")), logs = listOf(log(1, "l1", entryId = 1, endedAt = T1, updatedAt = T1)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(entries = listOf(task(9, "e1")), logs = listOf(log(5, "l1", entryId = 9)))
        b.orchestrator.readAndMerge(store)
        assertEquals(T1, b.timeLogDao.getByUid("l1")!!.endedAt)
        assertEquals(5L, b.timeLogDao.getByUid("l1")!!.id)

        // The other way round: B's later close is not undone by A's earlier one.
        val c = Device(entries = listOf(task(9, "e1")), logs = listOf(log(5, "l1", entryId = 9, endedAt = T2, updatedAt = T2)))
        c.orchestrator.readAndMerge(store)
        assertEquals(T2, c.timeLogDao.getByUid("l1")!!.endedAt)
    }

    @Test
    fun `a deleted log stays deleted, whatever the other device did to it later`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(entries = listOf(task(1, "e1")), logs = listOf(log(1, "l1", entryId = 1, deletedAt = T1, updatedAt = T1)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(entries = listOf(task(9, "e1")), logs = listOf(log(5, "l1", entryId = 9, endedAt = T2, updatedAt = T2)))
        b.orchestrator.readAndMerge(store)
        assertEquals(T1, b.timeLogDao.getByUid("l1")!!.deletedAt)

        val onward = InMemorySyncFileStore()
        b.orchestrator.writeSnapshots(onward)
        assertEquals(T1.toEpochMilli(), publishedIn(onward).single().deletedAt)
    }

    @Test
    fun `a log whose owner has not merged here is republished, not deleted from the folder`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(
            FILE,
            json.encodeToString(listOf(TimeLogSnapshotRecord(uid = "l1", habitUid = "not-here", startedAt = 1_000L, updatedAt = 1_000L))),
        )

        val b = Device()
        b.orchestrator.readAndMerge(store)
        assertNull(b.timeLogDao.getByUid("l1"))

        b.orchestrator.writeSnapshots(store)
        assertEquals("l1", publishedIn(store).single().uid)
    }
}
