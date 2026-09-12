package com.tendril.app.sync

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.page.PageDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §9.4 — a field written by a newer build survives a round trip through this one.
 *
 * The defect: `Json` is `ignoreUnknownKeys = true` and every record is a plain data class, so a
 * newer build's extra field is dropped on decode, the record is adopted into Room, and the next
 * write pass re-encodes it **from Room rows** — republishing it stripped, for every device to
 * adopt in turn. It gets quieter as two builds converge, and nothing unreadable exists anywhere
 * for a guard to trip on.
 *
 * [aClearedValueIsNotResurrected] is the one that matters most. The obvious implementation —
 * "copy any key the folder has that we don't" — is wrong, because kotlinx.serialization omits a
 * field equal to its default, so a value this build has legitimately cleared is *also* absent.
 * That version of the fix would silently restore deleted data, which is worse than the bug.
 */
class UnknownFieldPreservationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private class Device(habits: List<Habit> = emptyList()) {
        val habitDao = FakeHabitDao(habits)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = FakeEntryDao(),
            habitDao = habitDao,
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
        const val HABITS = "habits.json"
        val AT: Instant = Instant.ofEpochMilli(1_000L)

        fun habit(uid: String, title: String, at: Instant = AT, lastCompleted: LocalDate? = null) = Habit(
            id = 1,
            uid = uid,
            title = title,
            frequency = HabitFrequency(1, IntervalUnit.DAY),
            streak = 3,
            lastCompletedDate = lastCompleted,
            createdAt = AT,
            updatedAt = at,
        )
    }

    private fun habitsIn(store: InMemorySyncFileStore) =
        json.parseToJsonElement(String(runBlocking { store.readRoot(HABITS) }!!, Charsets.UTF_8))
            .jsonArray.single().jsonObject

    /** A habit as a *newer* build would have written it: everything this build knows, plus a field
     * it has no member for. */
    private fun newerBuildHabit(uid: String, title: String, updatedAt: Long, extra: String) = """
        [{"uid":"$uid","title":"$title","frequency":"1:DAY","streak":3,
          "createdAt":1000,"updatedAt":$updatedAt,$extra}]
    """.trimIndent()

    @Test
    fun `a field this build has no member for survives a merge and republish`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(HABITS, newerBuildHabit("h1", "Read", 5_000, """"moodTag":"calm""""))

        val b = Device()
        b.orchestrator.readAndMerge(store)
        b.orchestrator.writeSnapshots(store)

        val out = habitsIn(store)
        // The whole point: this build never had a `moodTag`, and did not erase one.
        assertEquals("calm", out["moodTag"]!!.jsonPrimitive.content)
        // And the fields it does understand still came from its own rows, not copied wholesale.
        assertEquals("Read", out["title"]!!.jsonPrimitive.content)
        assertEquals("h1", out["uid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a nested field inside a record this build understands also survives`() = runBlocking {
        val store = InMemorySyncFileStore()
        // Not a real Habit shape, but the merge is descriptor-driven: an undeclared *object* is
        // carried whole, since nothing in the schema claims it.
        store.putRoot(HABITS, newerBuildHabit("h1", "Read", 5_000, """"pacing":{"target":4,"unit":"WEEK"}"""))

        val b = Device()
        b.orchestrator.readAndMerge(store)
        b.orchestrator.writeSnapshots(store)

        val pacing = habitsIn(store)["pacing"]!!.jsonObject
        assertEquals(4, pacing["target"]!!.jsonPrimitive.content.toInt())
        assertEquals("WEEK", pacing["unit"]!!.jsonPrimitive.content)
    }

    @Test
    fun aClearedValueIsNotResurrected() = runBlocking {
        val store = InMemorySyncFileStore()
        // The folder's copy has a completion date. It is *declared* in this build's schema, so it
        // is not an unknown field -- and this device's own row, which is newer, has cleared it.
        store.putRoot(
            HABITS,
            newerBuildHabit("h1", "Read", 1_000, """"lastCompletedDate":"2026-09-01","moodTag":"calm""""),
        )

        val b = Device(habits = listOf(habit("h1", "Read", at = Instant.ofEpochMilli(9_000), lastCompleted = null)))
        b.orchestrator.readAndMerge(store)
        b.orchestrator.writeSnapshots(store)

        val out = habitsIn(store)
        // Cleared stays cleared. A "copy every key we don't have" merge would restore it here,
        // which is the bug this rule exists to avoid.
        assertNull("a declared field this build cleared must not come back", out["lastCompletedDate"])
        // ...while the genuinely unknown field beside it is still carried.
        assertEquals("calm", out["moodTag"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a record the folder does not hold is written without inventing anything`() = runBlocking {
        val store = InMemorySyncFileStore()
        val b = Device(habits = listOf(habit("brand-new", "Stretch")))
        b.orchestrator.writeSnapshots(store)

        val out = habitsIn(store)
        assertEquals("brand-new", out["uid"]!!.jsonPrimitive.content)
        assertFalse("nothing to carry, so nothing extra", out.keys.contains("moodTag"))
    }

    @Test
    fun `an unreadable folder file never stops the write`() = runBlocking {
        val store = InMemorySyncFileStore()
        // Not JSON at all. Preservation is best-effort by construction: the alternative -- letting
        // a damaged folder file stop this device publishing -- is a worse failure than the one
        // being fixed.
        store.putRoot(HABITS, "{ this is not an array")

        val b = Device(habits = listOf(habit("h1", "Read")))
        b.orchestrator.writeSnapshots(store)

        assertEquals("h1", habitsIn(store)["uid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an unknown field is carried across repeated passes, not lost on the second`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(HABITS, newerBuildHabit("h1", "Read", 5_000, """"moodTag":"calm""""))

        val b = Device()
        repeat(3) {
            b.orchestrator.readAndMerge(store)
            b.orchestrator.writeSnapshots(store)
        }

        // Once carried, the field is in the folder's copy again, so the next pass reads it back.
        // If preservation only worked on the first pass it would decay silently, which is the
        // same shape of failure as the original bug.
        assertTrue(habitsIn(store).containsKey("moodTag"))
        assertEquals("calm", habitsIn(store)["moodTag"]!!.jsonPrimitive.content)
    }
}
