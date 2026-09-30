package com.tendril.app.sync

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitBlock
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleEdit
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.domain.PurgeRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §6.3 (v27, plan Phase 3 step 2) — the calendar habit's columns, the time blocks and the scoped
 * edits between two devices: each new table edited on both and deleted on one, the defaults each
 * device seeded on its own merging to five, the Label by name, and the two ways an older or newer
 * peer's data must survive — decoded as what it is, or held untouched.
 */
class HabitPlannerSyncTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class Device(
        habits: List<Habit> = emptyList(),
        completions: List<HabitCompletion> = emptyList(),
        blocks: List<HabitBlock> = DEFAULT_HABIT_BLOCKS,
        edits: List<HabitScheduleEdit> = emptyList(),
        val engine: com.tendril.app.sync.PagesSyncEngine = mockk(relaxed = true),
        purge: PurgeRegistry = mockk(relaxed = true),
    ) {
        val habitDao = FakeHabitDao(habits)
        val completionDao = FakeHabitCompletionDao(completions)
        val blockDao = FakeHabitBlockDao(blocks)
        val editDao = FakeHabitScheduleEditDao(edits)
        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = FakeEntryDao(),
            habitDao = habitDao,
            pageDao = mockk<PageDao>(relaxed = true),
            pagesSyncEngine = engine,
            purgeRegistry = purge,
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = completionDao,
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            habitBlockDao = blockDao,
            habitScheduleEditDao = editDao,
            localImages = InMemoryLocalImageStore(),
        )
    }

    private companion object {
        val AT: Instant = Instant.ofEpochMilli(1_000L)
        val LATER: Instant = Instant.ofEpochMilli(5_000L)
        val DAY: LocalDate = LocalDate.of(2026, 10, 1)

        fun gym(id: Long, labelId: Long? = null) = Habit(
            id = id, uid = "h1", title = "Gym", frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = AT, updatedAt = AT,
            scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = "TIMES_PER_DAY:2", blockUid = "block-evening", sortOrder = 2.5,
            labelId = labelId, pauseFrom = DAY, activeUntil = DAY.plusDays(90), note = "the long way",
        )

        fun edit(uid: String, deletedAt: Instant? = null) = HabitScheduleEdit(
            uid = uid, target = "HABIT", refUid = "h1", scope = "OCC:2026-10-01:2", changes = """{"time":1140}""", createdAt = AT, deletedAt = deletedAt,
        )

        fun block(uid: String, f: (HabitBlock) -> HabitBlock) = DEFAULT_HABIT_BLOCKS.map { if (it.uid == uid) f(it) else it }
    }

    @Test
    fun `a calendar habit, a renamed block, an edit and an occurrence's check-in reach the other device`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(
            habits = listOf(gym(1, labelId = 3)),
            completions = listOf(HabitCompletion(id = 1, uid = "c1", habitId = 1, date = DAY, checkedAt = AT, occurrenceKey = "2")),
            blocks = block("block-morning") { it.copy(name = "Dawn", updatedAt = LATER) },
            edits = listOf(edit("e1")),
        )
        coEvery { a.engine.labelNameOf(3) } returns "Health"
        a.orchestrator.writeSnapshots(store)

        val b = Device()
        coEvery { b.engine.labelIdFor("Health") } returns 9
        b.orchestrator.readAndMerge(store)
        b.orchestrator.readAndMerge(store) // a second pass adds nothing

        val h = b.habitDao.getByUid("h1")!!
        assertEquals(gym(h.id, labelId = 9), h)
        assertEquals("P4 — the Label found or created by its name on the far side", 9L, h.labelId)
        assertEquals("2", b.completionDao.getAll().single().occurrenceKey)
        assertEquals(listOf("e1"), b.editDao.getAll().map { it.uid })
        assertEquals("both devices seeded the five defaults, which merge to five", 5, b.blockDao.getAll().size)
        assertEquals("Dawn", b.blockDao.getByUid("block-morning")!!.name)
        assertNull("an untouched default keeps its language-following null name (H1)", b.blockDao.getByUid("block-midday")!!.name)
    }

    @Test
    fun `a block edited on both keeps the later edit, and a deletion is one more edit`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(blocks = block("block-evening") { it.copy(endMinute = 1320, updatedAt = LATER) }
            .map { if (it.uid == "block-night") it.copy(deletedAt = LATER, updatedAt = LATER) else it })
        val b = Device(blocks = block("block-evening") { it.copy(endMinute = 1300, updatedAt = LATER.plusSeconds(1)) })

        a.orchestrator.writeSnapshots(store)
        b.orchestrator.readAndMerge(store)
        b.orchestrator.writeSnapshots(store)
        a.orchestrator.readAndMerge(store)

        for (d in listOf(a, b)) {
            assertEquals(1300, d.blockDao.getByUid("block-evening")!!.endMinute)
            assertEquals(LATER, d.blockDao.getByUid("block-night")!!.deletedAt)
        }
    }

    @Test
    fun `an edit undone on one device stays undone on both, and travels as a tombstone`() = runBlocking {
        val store = InMemorySyncFileStore()
        val a = Device(habits = listOf(gym(1)), edits = listOf(edit("e1", deletedAt = LATER), edit("e2")))
        val b = Device(habits = listOf(gym(4)), edits = listOf(edit("e1")))

        a.orchestrator.writeSnapshots(store)
        b.orchestrator.readAndMerge(store)
        assertEquals(LATER, b.editDao.getByUid("e1")!!.deletedAt)
        assertNull(b.editDao.getByUid("e2")!!.deletedAt)

        val onward = InMemorySyncFileStore()
        b.orchestrator.writeSnapshots(onward)
        val published = json.parseToJsonElement(String(onward.readRoot("habit_schedule_edits.json")!!, Charsets.UTF_8)).jsonArray
        assertEquals(LATER.toEpochMilli().toString(), published.single { it.jsonObject["uid"]!!.jsonPrimitive.content == "e1" }.jsonObject["deletedAt"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an edit of a habit purged for good is neither published nor taken in`() = runBlocking {
        val purged = mockk<PurgeRegistry>(relaxed = true)
        coEvery { purged.tombstones(PurgedKind.HABIT) } returns mapOf("h1" to LATER)
        val store = InMemorySyncFileStore()
        Device(edits = listOf(edit("e1")), purge = purged).orchestrator.writeSnapshots(store)
        assertEquals("[]", String(store.readRoot("habit_schedule_edits.json")!!, Charsets.UTF_8).filterNot { it.isWhitespace() })

        val fromPeer = InMemorySyncFileStore()
        Device(edits = listOf(edit("e1"))).orchestrator.writeSnapshots(fromPeer)
        val b = Device(purge = purged)
        b.orchestrator.readAndMerge(fromPeer)
        assertTrue(b.editDao.getAll().isEmpty())
    }

    /** An older peer — one without v27 — writes no calendar fields; its habit is the interval habit it always was. */
    @Test
    fun `a habit record from before v27 decodes as an interval habit with nothing else set`() {
        val record = json.decodeFromString(
            HabitSnapshotRecord.serializer(),
            """{"uid":"h9","title":"Stretch","frequency":"1:DAY","streak":2,"createdAt":1000,"updatedAt":1000}""",
        )
        val h = record.toEntity(labelId = null)
        assertEquals(HabitScheduleKind.INTERVAL, h.scheduleKind)
        assertEquals(listOf(null, null, 0.0, null), listOf(h.calendarRule, h.blockUid, h.sortOrder, h.note))
    }

    /** A newer peer's kind is that build's habit: shown on no days here, and republished untouched. */
    @Test
    fun `a schedule kind this build does not know is quarantined and held for the next write`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(
            "habits.json",
            """[{"uid":"h9","title":"Tide","frequency":"1:DAY","streak":0,"createdAt":1000,"updatedAt":9000,"scheduleKind":"LUNAR"}]""",
        )
        val b = Device()
        val merge = b.orchestrator.readAndMerge(store)
        assertNull(b.habitDao.getByUid("h9"))
        assertEquals(listOf("h9"), merge.quarantinedRecords.map { it.uid })
        b.orchestrator.writeSnapshots(store)
        assertTrue(String(store.readRoot("habits.json")!!, Charsets.UTF_8).contains("LUNAR"))
    }

    /** §9.4's `mergeUnknownFields`, reaching the two new files through [FolderArrayFile] like every other. */
    @Test
    fun `a field a newer build added to a block survives this device's rewrite`() = runBlocking {
        val store = InMemorySyncFileStore()
        store.putRoot(
            "habit_blocks.json",
            """[{"uid":"block-morning","startMinute":390,"endMinute":540,"position":0,"updatedAt":0,"texture":"linen"}]""",
        )
        val b = Device()
        b.orchestrator.readAndMerge(store)
        b.orchestrator.writeSnapshots(store)
        val published = json.parseToJsonElement(String(store.readRoot("habit_blocks.json")!!, Charsets.UTF_8)).jsonArray
        assertEquals("linen", published.single { it.jsonObject["uid"]!!.jsonPrimitive.content == "block-morning" }.jsonObject["texture"]!!.jsonPrimitive.content)
    }
}
