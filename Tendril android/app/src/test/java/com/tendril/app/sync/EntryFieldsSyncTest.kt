package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * §0.6.4 — the four v10 fields on a task reach the other device, with the parent link resolved
 * to *that* device's local id; and a record from a v9 peer, which has none of them, reads as a
 * task with none of them rather than failing to read at all.
 */
class EntryFieldsSyncTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Device(entries: List<Entry> = emptyList()) {
        val entryDao = FakeEntryDao(entries)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = FakeHabitDao(),
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        const val ACTIVE = "entries_active.json"
        val AT: Instant = Instant.ofEpochMilli(1_000L)

        fun task(
            id: Long,
            uid: String,
            dueDate: LocalDate? = null,
            parentEntryId: Long? = null,
            estimate: Duration? = null,
            importance: Int = 0,
        ) = Entry(
            id = id,
            uid = uid,
            title = "Task $uid",
            kind = EntryKind.TASK,
            status = EntryStatus.PENDING,
            startDate = LocalDate.of(2026, 9, 11),
            startTime = null,
            endDate = null,
            endTime = null,
            recurrenceRule = null,
            dueDate = dueDate,
            parentEntryId = parentEntryId,
            estimate = estimate,
            importance = importance,
            createdAt = AT,
            updatedAt = AT,
        )
    }

    @Test
    fun `deadline, parent, estimate and the flag reach the other device`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(
            entries = listOf(
                task(1, "parent"),
                task(2, "child", dueDate = LocalDate.of(2026, 9, 20), parentEntryId = 1, estimate = Duration.ofMinutes(45), importance = 2),
            ),
        )
        a.orchestrator.writeSnapshots(store)

        // B already holds the parent under a different local id, so the link must be re-resolved.
        val b = Device(entries = listOf(task(9, "parent")))
        b.orchestrator.readAndMerge(store)

        val child = b.entryDao.getAll().single { it.uid == "child" }
        assertEquals(LocalDate.of(2026, 9, 20), child.dueDate)
        assertEquals(9L, child.parentEntryId)
        assertEquals(Duration.ofMinutes(45), child.estimate)
        assertEquals(2, child.importance)
        // The When is untouched by the deadline's arrival: two fields, two facts.
        assertEquals(LocalDate.of(2026, 9, 11), child.startDate)
    }

    @Test
    fun `a record from a build without these fields reads as a task without them`() = runBlocking {
        val store = InMemorySyncFileStore()
        // Exactly what a v9 peer writes: no dueDate, parentEntryUid, estimateSeconds or important.
        store.putRoot(
            ACTIVE,
            json.encodeToString(
                listOf(
                    EntrySnapshotRecord(
                        uid = "old",
                        title = "From v9",
                        kind = "TASK",
                        status = "PENDING",
                        startDate = "2026-09-11",
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                    ),
                ),
            ),
        )

        val b = Device()
        val result = b.orchestrator.readAndMerge(store)

        val landed = b.entryDao.getAll().single { it.uid == "old" }
        assertNull(landed.dueDate)
        assertNull(landed.parentEntryId)
        assertNull(landed.estimate)
        assertEquals(0, landed.importance)
        assertTrue("nothing about a v9 record is unreadable", result.quarantinedRecords.isEmpty())
    }

    /** 14g·3 — the flag still travels for a v19 peer, and a v19 record's flag lands on high. */
    @Test
    fun `importance travels beside the old flag and a v19 flag reads as high`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(entries = listOf(task(1, "urgent", importance = 4), task(2, "plain")))
        a.orchestrator.writeSnapshots(store)
        val written = json.decodeFromString<List<EntrySnapshotRecord>>(String(store.readRoot(ACTIVE)!!, Charsets.UTF_8))
        val urgent = written.single { it.uid == "urgent" }
        assertTrue("the v19 flag is written for urgent", urgent.important)
        assertEquals("and the level", 4, urgent.importance)
        assertFalse("a plain task carries no flag", written.single { it.uid == "plain" }.important)

        // A v19 peer's record: the flag, no level.
        store.putRoot(
            ACTIVE,
            json.encodeToString(
                listOf(
                    EntrySnapshotRecord(uid = "flagged", title = "From v19", kind = "TASK", status = "PENDING", startDate = "2026-09-11", important = true, createdAt = 1_000L, updatedAt = 1_000L),
                    EntrySnapshotRecord(uid = "level", title = "From v20", kind = "TASK", status = "PENDING", startDate = "2026-09-11", important = false, importance = 1, createdAt = 1_000L, updatedAt = 1_000L),
                ),
            ),
        )
        val b = Device()
        b.orchestrator.readAndMerge(store)
        assertEquals(3, b.entryDao.getAll().single { it.uid == "flagged" }.importance)
        assertEquals(1, b.entryDao.getAll().single { it.uid == "level" }.importance)
    }
}
