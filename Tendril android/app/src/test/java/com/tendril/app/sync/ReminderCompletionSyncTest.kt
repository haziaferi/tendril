package com.tendril.app.sync

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderOffset
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
import java.time.LocalTime

/**
 * S2 — that a reminder and a completion reach the other device, and that a *deleted* reminder
 * does not come back.
 *
 * The resurrection case is the one this file exists for. Every device rewrites `reminders.json`
 * in full from its own rows, so before S2 there was no reminder record in the folder at all; the
 * naive way to add one — publish the live rows, merge by uid — would have meant a reminder
 * deleted on device A walking straight back in from device B's copy on the next pass. That is not
 * a stale row on a screen: `AlarmScheduler` reads the same rows, so a resurrected reminder
 * re-registers an alarm and fires. `Reminder.deletedAt` is the tombstone that stops it, and
 * [tombstoneDoesNotResurrect] and [tombstoneTravelsOnward] are the two halves of proving it —
 * the second matters as much as the first, because a device that merged a delete but published
 * its own live copy again would simply move the resurrection one hop down the folder.
 */
class ReminderCompletionSyncTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** One device: its own local ids, sharing only the folder with any other fixture. */
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
            habitCompletionDao = FakeHabitCompletionDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        const val REMINDERS = "reminders.json"
        const val COMPLETIONS = "entry_completions.json"
        val AT: Instant = Instant.ofEpochMilli(1_000L)

        /** Deliberately different local ids on the two devices: a merge that keyed on `id`
         * rather than `uid` would pass every assertion here by accident otherwise. */
        fun task(id: Long, uid: String) = Entry(
            id = id,
            uid = uid,
            title = "Task $uid",
            kind = EntryKind.TASK,
            status = EntryStatus.PENDING,
            startDate = null,
            startTime = null,
            endDate = null,
            endTime = null,
            recurrenceRule = null,
            createdAt = AT,
            updatedAt = AT,
        )

        fun reminder(id: Long, uid: String, entryId: Long, deletedAt: Instant? = null) = Reminder(
            id = id,
            uid = uid,
            entryId = entryId,
            offset = ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY),
            anchorTime = LocalTime.of(9, 0),
            deletedAt = deletedAt,
        )

        fun completion(id: Long, uid: String, entryId: Long) = EntryCompletion(
            id = id,
            uid = uid,
            entryId = entryId,
            occurrenceDate = LocalDate.of(2026, 9, 9),
            resolvedAt = AT,
            status = EntryStatus.DONE,
        )
    }

    private fun remindersIn(store: InMemorySyncFileStore): List<ReminderSnapshotRecord> =
        json.decodeFromString(String(runBlocking { store.readRoot(REMINDERS) }!!, Charsets.UTF_8))

    private fun completionsIn(store: InMemorySyncFileStore): List<EntryCompletionSnapshotRecord> =
        json.decodeFromString(String(runBlocking { store.readRoot(COMPLETIONS) }!!, Charsets.UTF_8))

    // ------------------------------------------------------------------ it travels at all

    @Test
    fun `a reminder created on one device reaches the other`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(entries = listOf(task(1, "e1")), reminders = listOf(reminder(1, "r1", entryId = 1)))
        a.orchestrator.writeSnapshots(store)

        // Local id 7, not 1: the Entry is the same record, the row is not.
        val b = Device(entries = listOf(task(7, "e1")))
        b.orchestrator.readAndMerge(store)

        val landed = b.reminderDao.getForEntry(7)
        assertEquals(1, landed.size)
        assertEquals("r1", landed.single().uid)
        assertEquals(7L, landed.single().entryId)
        assertEquals(ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY), landed.single().offset)
        assertEquals(LocalTime.of(9, 0), landed.single().anchorTime)
    }

    @Test
    fun `a completion reaches the other device, and a second merge does not duplicate it`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(entries = listOf(task(1, "e1")), completions = listOf(completion(1, "c1", entryId = 1)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(entries = listOf(task(7, "e1")))
        b.orchestrator.readAndMerge(store)
        // Merging the same folder twice is the ordinary case, not an edge one — every sync pass
        // re-reads files it has already seen. A union that inserted unconditionally would grow
        // the table on every pass, and nothing else in the app would notice.
        b.orchestrator.readAndMerge(store)

        assertEquals(1, b.completionDao.getAll().size)
        assertEquals("c1", b.completionDao.getAll().single().uid)
        assertEquals(EntryStatus.DONE, b.completionDao.getAll().single().status)

        // And what A published names the Entry by uid, not by its local id 1 — the write pass
        // resolving `entryId` through `idToUid` is the half that makes B's lookup possible at all.
        assertEquals("e1", completionsIn(store).single().entryUid)
        assertEquals("c1", completionsIn(store).single().uid)
    }

    // ------------------------------------------------------------------ it stays deleted

    @Test
    fun tombstoneDoesNotResurrect() = runBlocking {
        val store = InMemorySyncFileStore()
        // A deleted it; B still holds it live. This is the state that produced the bug.
        val a = Device(
            entries = listOf(task(1, "e1")),
            reminders = listOf(reminder(1, "r1", entryId = 1, deletedAt = AT)),
        )
        a.orchestrator.writeSnapshots(store)

        val b = Device(entries = listOf(task(7, "e1")), reminders = listOf(reminder(3, "r1", entryId = 7)))
        b.orchestrator.readAndMerge(store)

        // Gone from every read the app makes — which is what keeps it away from AlarmScheduler...
        assertTrue(b.reminderDao.getForEntry(7).isEmpty())
        // ...but still a row, carrying the tombstone rather than having been removed.
        val row = b.reminderDao.getByUid("r1")
        assertNotNull(row)
        assertEquals(AT, row!!.deletedAt)
    }

    @Test
    fun tombstoneTravelsOnward() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(
            entries = listOf(task(1, "e1")),
            reminders = listOf(reminder(1, "r1", entryId = 1, deletedAt = AT)),
        )
        a.orchestrator.writeSnapshots(store)

        val b = Device(entries = listOf(task(7, "e1")), reminders = listOf(reminder(3, "r1", entryId = 7)))
        b.orchestrator.readAndMerge(store)

        // B now republishes. If `getAll` filtered tombstones the way the two per-entry reads do,
        // this file would carry no r1 at all — and device C, which still holds it live, would
        // publish its live copy back and undo the delete for everyone.
        val onward = InMemorySyncFileStore()
        b.orchestrator.writeSnapshots(onward)

        val published = remindersIn(onward).single { it.uid == "r1" }
        assertEquals(AT.toEpochMilli(), published.deletedAt)
        assertEquals("e1", published.entryUid)
    }

    @Test
    fun `a live copy arriving after a local delete does not undo it`() = runBlocking {
        val store = InMemorySyncFileStore()
        // The other direction: the peer's record is the *live* one, and this device deleted it.
        val a = Device(entries = listOf(task(1, "e1")), reminders = listOf(reminder(1, "r1", entryId = 1)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(
            entries = listOf(task(7, "e1")),
            reminders = listOf(reminder(3, "r1", entryId = 7, deletedAt = AT)),
        )
        b.orchestrator.readAndMerge(store)

        // Monotonic: delete wins regardless of which side is "newer", because there is no newer —
        // a reminder is never edited, so the only transition is live to deleted.
        assertEquals(AT, b.reminderDao.getByUid("r1")!!.deletedAt)
        assertTrue(b.reminderDao.getForEntry(7).isEmpty())
    }

    // ------------------------------------------------------------------ quarantine

    @Test
    fun `a reminder whose entry has not merged here is republished, not deleted from the folder`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(
            REMINDERS,
            json.encodeToString(
                listOf(
                    ReminderSnapshotRecord(uid = "r1", entryUid = "not-here", offset = "PRESET:ONE_DAY"),
                )
            ),
        )

        // This device holds no Entry at all, so `entryUid` resolves to nothing.
        val b = Device()
        b.orchestrator.readAndMerge(store)
        assertNull(b.reminderDao.getByUid("r1"))

        // The write pass must not now erase it. Dropping rather than holding would delete the
        // peer's reminder from the folder for every device, and the record that would have healed
        // it once the Entry arrived would be gone with it.
        b.orchestrator.writeSnapshots(store)
        assertEquals("r1", remindersIn(store).single().uid)
    }

    @Test
    fun `an offset this build cannot read costs that reminder, not the file`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(
            REMINDERS,
            json.encodeToString(
                listOf(
                    ReminderSnapshotRecord(uid = "bad", entryUid = "e1", offset = "PRESET:FROM_A_LATER_BUILD"),
                    ReminderSnapshotRecord(uid = "good", entryUid = "e1", offset = "CUSTOM:3:DAY"),
                )
            ),
        )

        val b = Device(entries = listOf(task(7, "e1")))
        val result = b.orchestrator.readAndMerge(store)

        // The readable one still lands — the whole point of quarantining per record rather than
        // failing the file, which is what an enum `valueOf` throwing mid-loop used to do.
        val landed = b.reminderDao.getForEntry(7)
        assertEquals(1, landed.size)
        assertEquals("good", landed.single().uid)
        assertEquals(ReminderOffset.Custom(3, IntervalUnit.DAY), landed.single().offset)

        // And the unreadable one is named, not swallowed.
        assertTrue(result.quarantinedRecords.any { it.uid == "bad" && it.kind == QuarantinedRecord.REMINDER })
        assertNull(b.reminderDao.getByUid("bad"))
    }
}
