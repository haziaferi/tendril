package com.tendril.app.domain

import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.sync.FakeEntryDao
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Regression test for the trash clobber — a delete that silently undid itself.
 *
 * [ResolveEntryUseCase.trash] soft-deletes the Entry, then asks the [EntryScheduleCoordinator]
 * to cancel its alarms and tear down its Calendar mirror. Both halves write to the same row.
 * The coordinator used to be handed the snapshot read *before* the delete, and
 * `CalendarProviderSync.removeEntry` wrote that snapshot straight back with a whole-row
 * `@Update` in order to clear `providerEventId` — restoring `deletedAt = null` and the
 * pre-delete `updatedAt` along with it.
 *
 * The visible symptom was a task that would not delete. The real damage was worse: the alarms
 * were cancelled before the row came back, so the Entry returned to the list live and silently
 * unscheduled — no overdue notification, no reminders. It only reproduced when the Entry had a
 * Provider mirror (`providerEventId != null`), and every app launch re-created that mirror,
 * which is what made it look intermittent rather than deterministic.
 *
 * The rule pinned here: **after `trash()`, the Entry stays trashed — whatever the coordinator
 * writes back.** [WholeRowMirrorCoordinator] below deliberately uses the whole-row form that
 * caused the original bug, so this test fails if `trash()` ever hands out a pre-delete snapshot
 * again, independently of how any one coordinator implementation happens to write.
 */
class TrashClobberRegressionTest {

    /**
     * Mirrors what `CalendarProviderSync.removeEntry` used to do: clear the mirror id by
     * writing back the Entry it was handed. The whole-row form is the point — a coordinator
     * that only touches its own column cannot demonstrate the bug, so it could not guard
     * against it either.
     */
    private class WholeRowMirrorCoordinator(private val entryDao: EntryDao) : EntryScheduleCoordinator {
        var removedWith: Entry? = null

        override suspend fun onEntryChanged(entry: Entry) = Unit

        override suspend fun onEntryRemoved(entry: Entry) {
            removedWith = entry
            entryDao.update(entry.copy(providerEventId = null))
        }
    }

    private val completionDao = mockk<EntryCompletionDao>(relaxed = true)

    private val createdAt = Instant.ofEpochMilli(1_000)

    /** A TASK that has been mirrored into the system Calendar Provider — the only shape the
     * original bug reproduced on. */
    private fun mirroredTask() = Entry(
        id = 1L,
        title = "Mirrored task",
        kind = EntryKind.TASK,
        startDate = LocalDate.of(2026, 9, 10),
        startTime = null,
        endDate = null,
        endTime = null,
        recurrenceRule = null,
        status = EntryStatus.PENDING,
        providerEventId = 189L,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun useCase(entryDao: EntryDao, coordinator: EntryScheduleCoordinator) =
        ResolveEntryUseCase(entryDao, completionDao, coordinator)

    @Test
    fun `trashed entry stays trashed when the coordinator writes the entry back`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(mirroredTask()))
        val coordinator = WholeRowMirrorCoordinator(entryDao)
        val now = Instant.ofEpochMilli(50_000)

        useCase(entryDao, coordinator).trash(1L, now)

        val after = entryDao.getById(1L)!!
        // The actual regression: this came back null, and the task reappeared in the list.
        assertNotNull("Entry was resurrected by the coordinator's write-back", after.deletedAt)
        assertEquals(now, after.deletedAt)
        assertEquals(now, after.updatedAt)
    }

    @Test
    fun `the coordinator still gets to clear the mirror id`() = runBlocking {
        // The fix must not work by simply denying the coordinator its write — tearing down the
        // Provider mirror is the whole reason trash() calls it.
        val entryDao = FakeEntryDao(listOf(mirroredTask()))
        val coordinator = WholeRowMirrorCoordinator(entryDao)

        useCase(entryDao, coordinator).trash(1L, Instant.ofEpochMilli(50_000))

        assertNull(entryDao.getById(1L)!!.providerEventId)
    }

    @Test
    fun `the coordinator is handed the post-delete state, not the pre-delete snapshot`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(mirroredTask()))
        val coordinator = WholeRowMirrorCoordinator(entryDao)
        val now = Instant.ofEpochMilli(50_000)

        useCase(entryDao, coordinator).trash(1L, now)

        // Stated directly, so a future change that reintroduces the stale read fails here with
        // the cause rather than only on the symptom above.
        assertEquals(now, coordinator.removedWith!!.deletedAt)
        // …and still carries the mirror id, or it would have nothing to tear down.
        assertEquals(189L, coordinator.removedWith!!.providerEventId)
    }

    @Test
    fun `trashing an entry that does not exist is a no-op`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(mirroredTask()))
        val coordinator = WholeRowMirrorCoordinator(entryDao)

        useCase(entryDao, coordinator).trash(404L, Instant.ofEpochMilli(50_000))

        assertNull(coordinator.removedWith)
        assertNull(entryDao.getById(1L)!!.deletedAt)
    }
}
