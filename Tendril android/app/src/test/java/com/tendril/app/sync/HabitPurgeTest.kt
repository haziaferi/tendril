package com.tendril.app.sync

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.domain.PurgeRegistry
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

/**
 * §5.5.1 / §5.5.1.1 — "Delete forever" for Habits.
 *
 * Pages and Entries both got tombstones; Habits were left out, and the omission was invisible
 * from the UI because `TasksHabitsScreen`'s trash button opened the *Entry* sheet on every tab.
 * So a trashed Habit was unreachable, and `mergeHabitContent` — which never consulted the
 * registry — would have resurrected it from another device's `habits.json` even once it was.
 *
 * These pin both halves: the tombstone is recorded and blocks the merge, and a Habit edited
 * after the purge still wins, by the same supersede rule Entries and Pages follow.
 */
class HabitPurgeTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun t(millis: Long) = Instant.ofEpochMilli(millis)

    private fun habit(uid: String, title: String, updatedAt: Long) = Habit(
        uid = uid,
        title = title,
        frequency = HabitFrequency(1, IntervalUnit.DAY),
        createdAt = t(updatedAt),
        updatedAt = t(updatedAt),
    )

    private fun habitRecord(uid: String, title: String, updatedAt: Long) = HabitSnapshotRecord(
        uid = uid,
        title = title,
        frequency = "1:DAY",
        streak = 0,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    /** A device whose registry and orchestrator share one habit store — the thing that makes
     * the tombstone and the merge see the same rows. */
    private class Device {
        val purgedDao = FakePurgedRecordDao()
        val habitDao = FakeHabitDao()
        val entryDao = FakeEntryDao()
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val registry = PurgeRegistry(
            purgedDao, pageDao, entryDao, habitDao, FakePropertyDao(store), RecordingEntryScheduleCoordinator(),
        )
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = habitDao,
            pageDao = pageDao,
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = registry,
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
            habitCompletionDao = FakeHabitCompletionDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    // ------------------------------------------------------------ recording the purge

    @Test
    fun `purgeHabit records a HABIT tombstone and drops the row`() = runBlocking {
        val d = Device()
        val id = d.habitDao.insert(habit("h-1", "Stretch", 1_000))

        d.registry.purgeHabit(id, t(5_000))

        assertNull("the row should be gone", d.habitDao.getById(id))
        val tombstones = d.registry.tombstones(PurgedKind.HABIT)
        assertEquals("a HABIT tombstone should exist for the uid", setOf("h-1"), tombstones.keys)
        assertEquals(t(5_000), tombstones["h-1"])
    }

    @Test
    fun `purging an unknown id records nothing`() = runBlocking {
        val d = Device()
        d.registry.purgeHabit(999L, t(5_000))
        assertTrue(d.registry.tombstones(PurgedKind.HABIT).isEmpty())
    }

    // ------------------------------------------------------- the resurrection regression

    @Test
    fun `a purged habit does not come back from another device's habits file`() = runBlocking {
        val d = Device()
        val id = d.habitDao.insert(habit("h-1", "Stretch", 1_000))
        d.registry.purgeHabit(id, t(5_000))

        // The other device has not seen the purge yet, so its habits.json still carries the row.
        val store = InMemorySyncFileStore().apply {
            putRoot("habits.json", json.encodeToString(listOf(habitRecord("h-1", "Stretch", 1_000))))
        }
        d.orchestrator.readAndMerge(store)

        assertNull("the purged habit walked back in", d.habitDao.getByUid("h-1"))
    }

    @Test
    fun `an unpurged habit from the same file still merges`() = runBlocking {
        val d = Device()
        val id = d.habitDao.insert(habit("h-1", "Stretch", 1_000))
        d.registry.purgeHabit(id, t(5_000))

        val store = InMemorySyncFileStore().apply {
            putRoot(
                "habits.json",
                json.encodeToString(
                    listOf(habitRecord("h-1", "Stretch", 1_000), habitRecord("h-2", "Read", 1_000))
                ),
            )
        }
        d.orchestrator.readAndMerge(store)

        // The tombstone must block exactly one record, not poison the whole file.
        assertNull(d.habitDao.getByUid("h-1"))
        assertNotNull("the untombstoned habit should have merged", d.habitDao.getByUid("h-2"))
    }

    // ------------------------------------------------------------ the far side of the purge

    @Test
    fun `applyToLocalRecords deletes a habit purged on another device`() = runBlocking {
        val d = Device()
        d.habitDao.insert(habit("h-1", "Stretch", 1_000))

        // A tombstone arriving from elsewhere, newer than the local row.
        d.registry.adopt(listOf(PurgedRecord(PurgedKind.HABIT, "h-1", t(5_000))))
        d.registry.applyToLocalRecords()

        assertNull(d.habitDao.getByUid("h-1"))
    }

    @Test
    fun `a habit edited after the purge supersedes the tombstone`() = runBlocking {
        val d = Device()
        // Restoring from Trash moves updatedAt forward, which is precisely how a restore beats
        // an in-flight purge from another device rather than losing to it.
        d.habitDao.insert(habit("h-1", "Stretch", 9_000))

        d.registry.adopt(listOf(PurgedRecord(PurgedKind.HABIT, "h-1", t(5_000))))
        d.registry.applyToLocalRecords()

        assertNotNull("the newer edit should have won", d.habitDao.getByUid("h-1"))
        assertTrue(
            "and the superseded tombstone should be dropped",
            d.registry.tombstones(PurgedKind.HABIT).isEmpty(),
        )
    }

    // ------------------------------------------------------------ the tombstone travels

    @Test
    fun `the habit tombstone is written into purged_records and read back`() = runBlocking {
        val a = Device()
        val id = a.habitDao.insert(habit("h-1", "Stretch", 1_000))
        a.registry.purgeHabit(id, t(5_000))

        val store = InMemorySyncFileStore()
        a.orchestrator.writeSnapshots(store)

        // A second device holding the same Habit picks the tombstone up and drops its own row.
        val b = Device()
        b.habitDao.insert(habit("h-1", "Stretch", 1_000))
        b.orchestrator.readAndMerge(store)

        assertNull("the purge should have propagated", b.habitDao.getByUid("h-1"))
        assertEquals(t(5_000), b.registry.tombstones(PurgedKind.HABIT)["h-1"])
    }
}
