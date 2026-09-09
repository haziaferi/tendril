package com.tendril.app.sync

import com.tendril.app.data.completion.EntryCompletion
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

/**
 * §9.10 — what "snapshot-backed recovery" actually recovers, once a database has been set aside
 * and replaced by an empty one ([com.tendril.app.data.openOrRecover]).
 *
 * `scope-decisions.md` records the objection this file has to answer: a device emptied by a
 * destructive migration and a fresh install are indistinguishable from inside, so "empty device
 * merges folder, data appears" proves the easy case only. The two assertions that are *not* the
 * easy case are here. [emptyDeviceDoesNotPublishItsEmptiness] is the one that matters — every
 * device rewrites each folder-wide file wholesale from its own rows, so a device that wrote
 * before it merged would erase the folder for everyone at exactly the moment it had nothing left
 * to lose. And [whatWasNeverPublishedDoesNotComeBack] states the limit plainly, because §9.10's
 * own 2026-09-06 correction says the folder is read additively with no wipe-and-replay: recovery
 * restores what the folder holds and nothing else, and a criterion that did not say so would be
 * read as promising more than it does.
 */
class MigrationRecoveryTest {

    private class Device(
        entries: List<Entry> = emptyList(),
        reminders: List<Reminder> = emptyList(),
        completions: List<EntryCompletion> = emptyList(),
    ) {
        val entryDao = FakeEntryDao(entries)
        val reminderDao = FakeReminderDao(reminders)
        val completionDao = FakeEntryCompletionDao(completions)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = FakeHabitDao(),
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = reminderDao,
            entryCompletionDao = completionDao,
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        val AT: Instant = Instant.ofEpochMilli(1_000L)

        fun task(id: Long, uid: String) = Entry(
            id = id, uid = uid, title = "Task $uid", kind = EntryKind.TASK,
            status = EntryStatus.PENDING,
            startDate = null, startTime = null, endDate = null, endTime = null,
            recurrenceRule = null, createdAt = AT, updatedAt = AT,
        )

        fun reminder(id: Long, uid: String, entryId: Long) = Reminder(
            id = id, uid = uid, entryId = entryId,
            offset = ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY),
        )

        fun completion(id: Long, uid: String, entryId: Long) = EntryCompletion(
            id = id, uid = uid, entryId = entryId,
            occurrenceDate = LocalDate.of(2026, 9, 9), resolvedAt = AT, status = EntryStatus.DONE,
        )
    }

    /** A folder as a healthy device left it. */
    private fun populatedFolder(): InMemorySyncFileStore {
        val store = InMemorySyncFileStore()
        val healthy = Device(
            entries = listOf(task(1, "e1"), task(2, "e2")),
            reminders = listOf(reminder(1, "r1", entryId = 1)),
            completions = listOf(completion(1, "c1", entryId = 1)),
        )
        runBlocking { healthy.orchestrator.writeSnapshots(store) }
        return store
    }

    @Test
    fun `a database replaced by an empty one is repopulated from the folder`() = runBlocking {
        val store = populatedFolder()

        // Exactly the state openOrRecover leaves behind: the app running, every table empty.
        val recovered = Device()
        recovered.orchestrator.readAndMerge(store)

        assertEquals(setOf("e1", "e2"), recovered.entryDao.getAll().map { it.uid }.toSet())
        assertEquals(listOf("r1"), recovered.reminderDao.getAll().map { it.uid })
        assertEquals(listOf("c1"), recovered.completionDao.getAll().map { it.uid })
        // The reminder is attached to *this* device's row for e1, not to the id it had elsewhere.
        val e1 = recovered.entryDao.getAll().single { it.uid == "e1" }
        assertEquals(listOf("r1"), recovered.reminderDao.getForEntry(e1.id).map { it.uid })
    }

    @Test
    fun emptyDeviceDoesNotPublishItsEmptiness() = runBlocking {
        val store = populatedFolder()
        val before = store.rootNames()

        val recovered = Device()
        // A full pass, in the order SyncCoordinator runs it. If the write half ran first — or if
        // the merge silently failed and the write went ahead anyway — this folder would be
        // stripped of every record, on every device syncing it, by the one device least able to
        // put them back.
        recovered.orchestrator.readAndMerge(store)
        recovered.orchestrator.writeSnapshots(store)

        assertEquals("no folder file may be lost by a recovering device", before, store.rootNames())
        val after = Device()
        after.orchestrator.readAndMerge(store)
        assertEquals(setOf("e1", "e2"), after.entryDao.getAll().map { it.uid }.toSet())
        assertEquals(listOf("r1"), after.reminderDao.getAll().map { it.uid })
        assertEquals(listOf("c1"), after.completionDao.getAll().map { it.uid })
    }

    @Test
    fun whatWasNeverPublishedDoesNotComeBack() = runBlocking {
        val store = InMemorySyncFileStore()
        // This device wrote nothing to the folder before it was emptied — the offline case.
        val neverSynced = Device(entries = listOf(task(1, "only-here")))
        assertTrue(store.rootNames().isEmpty())

        val recovered = Device()
        recovered.orchestrator.readAndMerge(store)

        // Recovery is bounded by what the folder holds. Stated as a test rather than left to be
        // discovered: §9.10's criterion reads as "the restore path recovers a populated Room DB",
        // and this is the sentence that keeps that from being read as "recovers everything".
        assertTrue(recovered.entryDao.getAll().isEmpty())
        assertEquals(listOf("only-here"), neverSynced.entryDao.getAll().map { it.uid })
    }
}
