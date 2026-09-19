package com.tendril.app.sync

import com.tendril.app.data.checkin.CheckIn
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §0.10 item 4 — a check-in reaches the other device as its own file, an undone one does not
 * come back, and a record this build cannot read is held rather than dropped. The rule is the
 * habit completions' ([HabitCompletionSyncTest]) with no owner to resolve.
 */
class CheckInSyncTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Device(checkIns: List<CheckIn> = emptyList()) {
        val checkInDao = FakeCheckInDao(checkIns)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = FakeEntryDao(),
            habitDao = FakeHabitDao(),
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            purgeRegistry = mockk(relaxed = true),
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = checkInDao,
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        const val FILE = "check_ins.json"
        val AT: Instant = Instant.ofEpochMilli(1_000L)
        val DAY: LocalDate = LocalDate.of(2026, 9, 19)

        fun row(id: Long, uid: String, mood: Int? = 4, energy: Int? = null, deletedAt: Instant? = null) =
            CheckIn(id = id, uid = uid, date = DAY, at = AT, mood = mood, energy = energy, deletedAt = deletedAt)
    }

    private fun publishedIn(store: InMemorySyncFileStore): List<CheckInSnapshotRecord> =
        json.decodeFromString(String(runBlocking { store.readRoot(FILE) }!!, Charsets.UTF_8))

    @Test
    fun `a check-in on one device reaches the other, once`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(listOf(row(1, "c1"), row(2, "c2", mood = null, energy = 5)))
        a.orchestrator.writeSnapshots(store)

        val b = Device()
        b.orchestrator.readAndMerge(store)
        b.orchestrator.readAndMerge(store)

        val live = b.checkInDao.observeForDay(DAY).first()
        assertEquals(listOf("c1", "c2"), live.map { it.uid })
        assertEquals(4, live[0].mood)
        assertEquals(5, live[1].energy)
        assertEquals(2, publishedIn(store).size)
    }

    @Test
    fun `an undone check-in does not come back from a peer that still holds it live`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(listOf(row(1, "c1", deletedAt = AT)))
        a.orchestrator.writeSnapshots(store)

        val b = Device(listOf(row(3, "c1")))
        b.orchestrator.readAndMerge(store)

        assertTrue(b.checkInDao.observeForDay(DAY).first().isEmpty())
        val kept = b.checkInDao.getByUid("c1")
        assertNotNull(kept)
        assertEquals(AT, kept!!.deletedAt)

        val onward = InMemorySyncFileStore()
        b.orchestrator.writeSnapshots(onward)
        assertEquals(AT.toEpochMilli(), publishedIn(onward).single().deletedAt)
    }

    @Test
    fun `a record with a date this build cannot read is held, not dropped, and the readable one lands`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(listOf(row(1, "c1")))
        a.orchestrator.writeSnapshots(store)
        val text = String(store.readRoot(FILE)!!, Charsets.UTF_8)
        val broken = text.trimEnd().removeSuffix("]").trimEnd().removeSuffix(",") +
            ",\n  {\"uid\": \"c9\", \"date\": \"someday\", \"at\": 1000, \"mood\": 2}\n]"
        store.writeRoot(FILE, broken.toByteArray(Charsets.UTF_8))

        val b = Device()
        b.orchestrator.readAndMerge(store)
        assertEquals(listOf("c1"), b.checkInDao.observeForDay(DAY).first().map { it.uid })

        // B's write pass carries the unreadable element forward instead of publishing over it.
        b.orchestrator.writeSnapshots(store)
        assertTrue(String(store.readRoot(FILE)!!, Charsets.UTF_8).contains("\"c9\""))
    }
}
