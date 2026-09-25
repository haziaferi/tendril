package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Audit 2026-09-24 5a.1 — §9.5.1 exports `googleEventId` so that a peer does not push the same
 * event to Google a second time. The phone sets it after a push with no `updatedAt` bump (a bump
 * would be a false claim of authorship, the class 5a is about), so the merge's last-write-wins
 * gate never carried it: a peer at the same timestamp never learned it, and a peer's later edit —
 * made without it — won and wiped it here, and the next push created a duplicate event.
 *
 * The id only ever goes from absent to known, so the merge adopts it field-wise, whichever
 * record is newer: absent loses to known, and between two known ids the newer record's stands.
 */
class GoogleEventIdSyncTest {

    private class Device(entries: List<Entry>) {
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
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )

        suspend fun entry() = entryDao.getAll().single { it.uid == UID }
    }

    private companion object {
        const val UID = "standup"
        val AT: Instant = Instant.ofEpochMilli(1_000L)

        fun event(title: String = "Stand-up", updatedAt: Instant = AT, googleEventId: String? = null) = Entry(
            id = 1,
            uid = UID,
            title = title,
            kind = EntryKind.EVENT,
            startDate = LocalDate.of(2026, 9, 25),
            startTime = null,
            endDate = null,
            endTime = null,
            recurrenceRule = null,
            googleEventId = googleEventId,
            createdAt = AT,
            updatedAt = updatedAt,
        )
    }

    /** Sends [from]'s snapshot to [to], the way the folder sync does. */
    private suspend fun sync(from: Device, to: Device) {
        val store = InMemorySyncFileStore()
        from.orchestrator.writeSnapshots(store)
        to.orchestrator.readAndMerge(store)
    }

    @Test
    fun `an id set after a push reaches a peer holding the same version`() = runBlocking {
        val phone = Device(listOf(event(googleEventId = "g-1")))
        val desktop = Device(listOf(event()))

        sync(phone, desktop)

        assertEquals("g-1", desktop.entry().googleEventId)
    }

    @Test
    fun `a newer edit made without the id keeps the id it did not know about`() = runBlocking {
        val phone = Device(listOf(event(googleEventId = "g-1")))
        val desktop = Device(listOf(event(title = "Stand-up, moved", updatedAt = AT.plusSeconds(60))))

        sync(desktop, phone)

        val merged = phone.entry()
        assertEquals("the edit itself still wins", "Stand-up, moved", merged.title)
        assertEquals("and the phone still knows which Google event it is", "g-1", merged.googleEventId)
    }

    /** Control: two known ids are an ordinary conflict, and the newer record's id stands. */
    @Test
    fun `between two known ids the newer record's stands`() = runBlocking {
        val phone = Device(listOf(event(googleEventId = "g-old")))
        val other = Device(listOf(event(updatedAt = AT.plusSeconds(60), googleEventId = "g-new")))

        sync(other, phone)

        assertEquals("g-new", phone.entry().googleEventId)
    }
}
