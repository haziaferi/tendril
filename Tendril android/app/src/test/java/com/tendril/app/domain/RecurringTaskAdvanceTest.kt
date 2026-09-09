package com.tendril.app.domain

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.sync.FakeEntryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Pins how a recurring TASK advances when it is resolved — the behaviour §6.2 and §4.1 described
 * in opposite terms, and which no test covered.
 *
 * §6.2 is the one implemented: occurrences step from the **original schedule**, never from the
 * moment of resolution, so handling one late doesn't drag the series later. What was missing is
 * that a single `+ period` step only lands in the future when the task was resolved roughly on
 * time; resolved long enough after the fact, it landed in the *past*, and the task came back
 * overdue needing one Done tap per missed period — each of which logged an [EntryCompletion] for
 * an occurrence nobody actually resolved, while §9.7's "never schedule a past alarm" rule left
 * every in-between state silently unscheduled.
 */
class RecurringTaskAdvanceTest {

    /** Records what was logged, so a test can assert the history log isn't invented. */
    private class RecordingCompletionDao : EntryCompletionDao {
        val inserted = mutableListOf<EntryCompletion>()
        override suspend fun insert(completion: EntryCompletion): Long {
            inserted += completion
            return inserted.size.toLong()
        }
        override fun observeForEntry(entryId: Long): Flow<List<EntryCompletion>> =
            flowOf(inserted.filter { it.entryId == entryId })
        /** §9.4 / S2 — the snapshot write pass republishes the whole table. */
        override suspend fun getAll(): List<EntryCompletion> = inserted.toList()

        override suspend fun getByUid(uid: String): EntryCompletion? =
            inserted.firstOrNull { it.uid == uid }
    }

    private val zone: ZoneId = ZoneId.systemDefault()
    private val completionDao = RecordingCompletionDao()
    private val noOpCoordinator = object : EntryScheduleCoordinator {
        override suspend fun onEntryChanged(entry: Entry) = Unit
        override suspend fun onEntryRemoved(entry: Entry) = Unit
    }

    private fun at(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant().plusSeconds(12 * 3600)

    private fun weeklyTask(due: LocalDate) = Entry(
        id = 1L,
        title = "Water the plants",
        kind = EntryKind.TASK,
        startDate = due,
        startTime = null,
        endDate = null,
        endTime = null,
        recurrenceRule = RecurrenceRule.Elastic(Period.ofDays(7)),
        status = EntryStatus.PENDING,
        createdAt = at(due),
        updatedAt = at(due),
    )

    private suspend fun resolveOnTime(entry: Entry, resolvedOn: LocalDate): Entry {
        val dao = FakeEntryDao(listOf(entry))
        ResolveEntryUseCase(dao, completionDao, noOpCoordinator)
            .resolve(1L, EntryStatus.DONE, at(resolvedOn))
        return dao.getById(1L)!!
    }

    @Test
    fun `resolved on time, the next occurrence is exactly one period on`() = runBlocking {
        val due = LocalDate.of(2026, 9, 1)

        val after = resolveOnTime(weeklyTask(due), resolvedOn = due)

        assertEquals(due.plusDays(7), after.startDate)
        assertEquals(EntryStatus.PENDING, after.status)
    }

    @Test
    fun `resolved late, the next occurrence keeps the schedule's phase but lands in the future`() = runBlocking {
        // Due Sep 1, weekly, resolved Sep 20 — nineteen days late. A single +7 step gives
        // Sep 8, still in the past. Phase is preserved (every candidate is Sep 1 + 7n), so
        // §6.2's "lands on the original schedule rather than drifting" still holds; what
        // changes is that the one chosen is the first that hasn't already gone by.
        val due = LocalDate.of(2026, 9, 1)
        val resolvedOn = LocalDate.of(2026, 9, 20)

        val after = resolveOnTime(weeklyTask(due), resolvedOn)

        assertTrue("next occurrence must be in the future, was ${after.startDate}", after.startDate!!.isAfter(resolvedOn))
        assertEquals(LocalDate.of(2026, 9, 22), after.startDate)
        assertEquals(0, java.time.temporal.ChronoUnit.DAYS.between(due, after.startDate).toInt() % 7)
    }

    @Test
    fun `resolving once logs exactly one completion, for the occurrence actually resolved`() = runBlocking {
        val due = LocalDate.of(2026, 9, 1)

        resolveOnTime(weeklyTask(due), resolvedOn = LocalDate.of(2026, 9, 20))

        assertEquals(1, completionDao.inserted.size)
        assertEquals(due, completionDao.inserted.single().occurrenceDate)
        assertEquals(EntryStatus.DONE, completionDao.inserted.single().status)
    }

    @Test
    fun `a monthly interval keeps calendar-month semantics rather than a fixed day count`() = runBlocking {
        // §5.2.2's reason for storing (number, unit) instead of Notion's raw day count: months
        // vary 28-31 days, and a bill due on the 31st must not drift off month-end.
        val due = LocalDate.of(2026, 1, 31)
        val entry = weeklyTask(due).copy(recurrenceRule = RecurrenceRule.Elastic(Period.ofMonths(1)))

        val after = resolveOnTime(entry, resolvedOn = due)

        assertEquals(LocalDate.of(2026, 2, 28), after.startDate)
    }

    @Test
    fun `a non-recurring task simply finishes`() = runBlocking {
        val entry = weeklyTask(LocalDate.of(2026, 9, 1)).copy(recurrenceRule = null)

        val after = resolveOnTime(entry, resolvedOn = LocalDate.of(2026, 9, 1))

        assertEquals(EntryStatus.DONE, after.status)
        assertEquals(LocalDate.of(2026, 9, 1), after.startDate)
    }
}
