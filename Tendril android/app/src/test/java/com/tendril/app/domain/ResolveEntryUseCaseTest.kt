package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.RecordingEntryScheduleCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ResolveEntryUseCaseTest {

    private val at: Instant = Instant.parse("2026-09-26T09:00:00Z")
    private val entryDao = FakeEntryDao()
    private val completionDao = FakeEntryCompletionDao()
    private val resolve = ResolveEntryUseCase(entryDao, completionDao, RecordingEntryScheduleCoordinator())

    private fun task(): Long = runBlocking {
        entryDao.insert(
            Entry(title = "Call bank", kind = EntryKind.TASK, startDate = LocalDate.of(2026, 9, 25), startTime = null,
                endDate = null, endTime = null, recurrenceRule = null, status = EntryStatus.PENDING, createdAt = at, updatedAt = at),
        )
    }

    /**
     * Audit 2026-09-24 5.2, second half. A task already done — by an import, the other device, or
     * a second tap — still posted its overdue notification from an alarm armed before, and that
     * notification's Done resolved it again: a second completion row for one piece of work.
     */
    @Test
    fun `resolving a task already done logs nothing more`() = runBlocking {
        val id = task()
        resolve.resolve(id, EntryStatus.DONE, at)
        resolve.resolve(id, EntryStatus.DONE, at.plusSeconds(60))

        assertEquals(1, completionDao.getAll().size)
        assertEquals("and the first resolution's time stands", at, entryDao.getById(id)!!.updatedAt)
    }

    private fun series(): Pair<Long, Long> = runBlocking {
        val base = entryDao.insert(
            Entry(title = "Yoga", kind = EntryKind.EVENT, startDate = LocalDate.of(2026, 9, 26), startTime = null, endDate = null,
                endTime = null, recurrenceRule = com.tendril.app.data.entry.RecurrenceRule.Fixed("FREQ=WEEKLY"), createdAt = at, updatedAt = at),
        )
        val moved = entryDao.insert(
            entryDao.getById(base)!!.copy(id = 0, uid = "moved", startDate = LocalDate.of(2026, 10, 2), recurrenceRule = null,
                originalEntryId = base, originalOccurrenceDate = LocalDate.of(2026, 10, 3), isExceptionSkip = false),
        )
        base to moved
    }

    /**
     * Audit 5.12, found on the 2026-09-26 walk. The confirmation reads "The whole series goes", and
     * the series went — but its moved occurrence stayed, in Tendril and in the phone's calendar, an
     * orphan of a series in Trash.
     */
    @Test
    fun `trashing a series trashes its moved occurrences with it`() = runBlocking {
        val coordinator = RecordingEntryScheduleCoordinator()
        val resolve = ResolveEntryUseCase(entryDao, completionDao, coordinator)
        val (base, moved) = series()

        resolve.trash(base, at)

        assertEquals("the moved occurrence goes with it", at, entryDao.getById(moved)!!.deletedAt)
        assertEquals("and its mirror and alarms are torn down", setOf(base, moved), coordinator.removed.map { it.id }.toSet())
    }

    /** And back: restoring the series restores what went with it — not one trashed on its own before. */
    @Test
    fun `restoring a series restores the occurrences trashed with it`() = runBlocking {
        val (base, moved) = series()
        val earlier = entryDao.insert(
            entryDao.getById(moved)!!.copy(id = 0, uid = "earlier", originalOccurrenceDate = LocalDate.of(2026, 10, 10)),
        )
        resolve.trash(earlier, at.minusSeconds(3600))
        resolve.trash(base, at)

        resolve.restore(base, at.plusSeconds(60))

        assertEquals(null, entryDao.getById(moved)!!.deletedAt)
        assertEquals("trashed on its own, it stays in Trash", at.minusSeconds(3600), entryDao.getById(earlier)!!.deletedAt)
    }

    /** Control: a skipped task can still be marked done — a different outcome is a real change. */
    @Test
    fun `a skipped task can still be marked done`() = runBlocking {
        val id = task()
        resolve.resolve(id, EntryStatus.SKIPPED, at)
        resolve.resolve(id, EntryStatus.DONE, at.plusSeconds(60))

        assertEquals(EntryStatus.DONE, entryDao.getById(id)!!.status)
        assertEquals(2, completionDao.getAll().size)
    }
}
