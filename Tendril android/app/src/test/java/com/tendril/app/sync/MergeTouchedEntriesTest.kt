package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderOffset
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * §9.7 — what a merge reports back about the rows it wrote, so the caller can put each one
 * through the [com.tendril.app.domain.EntryScheduleCoordinator] instead of sweeping the table.
 *
 * The audit of 2026-09-22 closed the seam twice and got the cost wrong both times. First a merge
 * armed nothing at all, so a reminder set on the desktop never fired on the phone. Then the fix
 * called the whole launch-time sweep after every pass — and a pass runs on `onStart` *and*
 * `onStop`, so opening and closing the app rewrote every dated entry into the system calendar
 * cross-process, twice. [SnapshotMergeResult.touchedEntryIds] is what makes the third shape
 * possible: the work is whatever the pass changed, and usually that is nothing.
 *
 * The last test is the one that makes that claim true rather than hopeful. If the merge reported
 * every record it *read* instead of every record it *wrote*, the first three would still pass and
 * the cost would be back where it started.
 */
class MergeTouchedEntriesTest {

    private class Device(entries: List<Entry> = emptyList(), reminders: List<Reminder> = emptyList()) {
        val entryDao = FakeEntryDao(entries)
        val reminderDao = FakeReminderDao(reminders)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = FakeHabitDao(),
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = reminderDao,
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        val AT: Instant = Instant.ofEpochMilli(1_000L)
        val LATER: Instant = Instant.ofEpochMilli(9_000L)

        fun task(id: Long, uid: String, title: String = "Task $uid", updatedAt: Instant = AT) = Entry(
            id = id,
            uid = uid,
            title = title,
            kind = EntryKind.TASK,
            status = EntryStatus.PENDING,
            startDate = LocalDate.of(2026, 9, 22),
            startTime = null,
            endDate = null,
            endTime = null,
            recurrenceRule = null,
            createdAt = AT,
            updatedAt = updatedAt,
        )

        fun reminder(id: Long, uid: String, entryId: Long) = Reminder(
            id = id,
            uid = uid,
            entryId = entryId,
            offset = ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY),
            anchorTime = LocalTime.of(9, 0),
        )
    }

    @Test
    fun `an entry arriving from the other device is reported, under this device's own id`() = runBlocking {
        val store = InMemorySyncFileStore()
        Device(entries = listOf(task(1, "e1"))).orchestrator.writeSnapshots(store)

        val b = Device()
        val merge = b.orchestrator.readAndMerge(store)

        val landed = b.entryDao.getAll().single()
        assertEquals("e1", landed.uid)
        // The local id, not the peer's: the caller re-reads the row from this device's database.
        assertEquals(listOf(landed.id), merge.touchedEntryIds)
    }

    @Test
    fun `a reminder arriving is reported under its entry's id, not its own`() = runBlocking {
        val store = InMemorySyncFileStore()
        Device(
            entries = listOf(task(1, "e1")),
            reminders = listOf(reminder(1, "r1", entryId = 1)),
        ).orchestrator.writeSnapshots(store)

        // The same entry, already present here under a different local id, and unchanged — so
        // only the reminder is new and only the reminder pass can report anything.
        val b = Device(entries = listOf(task(7, "e1")))
        val merge = b.orchestrator.readAndMerge(store)

        assertEquals(1, b.reminderDao.getForEntry(7).size)
        // 7, the entry's local id — a reminder moves when its *entry* is next due, and the entry
        // is what the coordinator arms and publishes.
        assertEquals(listOf(7L), merge.touchedEntryIds)
    }

    @Test
    fun `an entry the pass rewrote is reported once, not once per file it appears in`() = runBlocking {
        val store = InMemorySyncFileStore()
        Device(
            entries = listOf(task(1, "e1", title = "Newer", updatedAt = LATER)),
            reminders = listOf(reminder(1, "r1", entryId = 1)),
        ).orchestrator.writeSnapshots(store)

        // Older locally, so the entry pass rewrites it *and* the reminder pass names it.
        val b = Device(entries = listOf(task(7, "e1", title = "Older", updatedAt = AT)))
        val merge = b.orchestrator.readAndMerge(store)

        assertEquals("Newer", b.entryDao.getAll().single().title)
        assertEquals("reported twice — the caller would do the work twice", listOf(7L), merge.touchedEntryIds)
    }

    @Test
    fun `a second merge of the same folder reports nothing`() = runBlocking {
        val store = InMemorySyncFileStore()
        Device(entries = listOf(task(1, "e1"))).orchestrator.writeSnapshots(store)

        val b = Device()
        assertTrue("the first pass wrote it", b.orchestrator.readAndMerge(store).touchedEntryIds.isNotEmpty())

        // Every pass re-reads files it has already seen — that is the ordinary case, not an edge
        // one. The last-write-wins guard skips a record that is not newer than the local row
        // before any write happens, so nothing is written and nothing is reported. Were this
        // list "records read" rather than "rows written", the whole database would come back on
        // every `onStart` and `onStop`, which is the cost this field exists to avoid.
        assertEquals(emptyList<Long>(), b.orchestrator.readAndMerge(store).touchedEntryIds)
    }
}
