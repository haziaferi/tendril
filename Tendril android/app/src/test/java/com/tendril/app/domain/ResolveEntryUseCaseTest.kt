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
