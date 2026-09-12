package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.recurrence.EntryOccurrences
import com.tendril.app.sync.FakeEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/** §0.8 step 6b — the one edit path: kind invariants on save, spans kept on move, and a
 * recurring occurrence moved alone or as a series. */
class EntryEditorTest {

    private val entryDao = FakeEntryDao()
    private val changed = mutableListOf<Entry>()
    private val coordinator = object : EntryScheduleCoordinator {
        override suspend fun onEntryChanged(entry: Entry) { changed += entry }
        override suspend fun onEntryRemoved(entry: Entry) = Unit
    }
    private val editor = EntryEditor(entryDao, coordinator)
    private val at = Instant.ofEpochMilli(1_000L)
    private val monday = LocalDate.of(2026, 9, 14)

    private fun stored(entry: Entry): Entry = runBlocking { entryDao.getById(entryDao.insert(entry))!! }

    private fun event(title: String, date: LocalDate, start: LocalTime? = null, end: LocalTime? = null, endDate: LocalDate? = null, rule: RecurrenceRule? = null) = stored(
        Entry(title = title, kind = EntryKind.EVENT, startDate = date, startTime = start, endDate = endDate ?: if (end != null) date else null, endTime = end, recurrenceRule = rule, createdAt = at, updatedAt = at),
    )

    private fun task(title: String, date: LocalDate?, time: LocalTime? = null) = stored(
        Entry(title = title, kind = EntryKind.TASK, startDate = date, startTime = time, endDate = null, endTime = null, recurrenceRule = null, status = EntryStatus.PENDING, createdAt = at, updatedAt = at),
    )

    @Test
    fun `save writes the row and tells the coordinator`() = runBlocking {
        val e = event("Dentist", monday, LocalTime.of(15, 0))
        editor.save(e.copy(title = "Dentist (moved)"), at)
        assertEquals("Dentist (moved)", entryDao.getById(e.id)!!.title)
        assertEquals(listOf(e.id), changed.map { it.id })
    }

    @Test
    fun `switching an event to a task drops the span and gains a status`() = runBlocking {
        val e = event("Lunch", monday, LocalTime.of(12, 0), LocalTime.of(13, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY"))
        val t = editor.save(e.copy(kind = EntryKind.TASK), at)
        assertNull(t.endTime); assertNull(t.endDate)
        assertEquals(EntryStatus.PENDING, t.status)
        assertNull("an RRULE is not a task's recurrence", t.recurrenceRule)
        assertEquals(LocalTime.of(12, 0), t.startTime)
    }

    @Test
    fun `switching a task to an event drops the task-only fields`() = runBlocking {
        val t = task("Call bank", monday).copy(dueDate = monday.plusDays(4), important = true, recurrenceRule = RecurrenceRule.Elastic(Period.ofWeeks(1)))
        val e = editor.save(t.copy(kind = EntryKind.EVENT), at)
        assertNull(e.status); assertNull(e.dueDate); assertTrue(!e.important)
        assertNull("a period is not an event's recurrence", e.recurrenceRule)
    }

    @Test
    fun `a task that loses its date loses its time too`() = runBlocking {
        val t = task("Call bank", monday, LocalTime.of(9, 0))
        val someday = editor.save(t.copy(startDate = null), at)
        assertNull(someday.startTime)
    }

    @Test
    fun `move keeps a span's length in days and minutes`() = runBlocking {
        val e = event("Trip", monday, LocalTime.of(10, 0), LocalTime.of(11, 30), endDate = monday.plusDays(2))
        val moved = editor.move(e, occurrenceDate = monday, toDate = monday.plusDays(7), toTime = LocalTime.of(14, 0), now = at)
        assertEquals(monday.plusDays(7), moved.startDate)
        assertEquals(monday.plusDays(9), moved.endDate)
        assertEquals(LocalTime.of(14, 0), moved.startTime)
        assertEquals(LocalTime.of(15, 30), moved.endTime)
        assertEquals("the same row", e.id, moved.id)
    }

    @Test
    fun `an all-day entry moved to a time stays all-day`() = runBlocking {
        val e = event("Holiday", monday)
        val moved = editor.move(e, monday, monday.plusDays(1), toTime = LocalTime.of(9, 0), now = at)
        assertNull(moved.startTime)
    }

    @Test
    fun `moving one occurrence of a series makes an override the expander honours`() = runBlocking {
        val series = event("Yoga", monday, LocalTime.of(7, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY;BYDAY=MO"))
        val second = monday.plusWeeks(1)

        val override = editor.move(series, occurrenceDate = second, toDate = second.plusDays(1), scope = MoveScope.THIS_ONE, now = at)

        assertTrue(override.id != series.id)
        assertEquals(series.id, override.originalEntryId)
        assertEquals(second, override.originalOccurrenceDate)
        assertNull(override.recurrenceRule)
        assertEquals("the base is untouched", monday, entryDao.getById(series.id)!!.startDate)

        val dates = EntryOccurrences.expand(entryDao.getAll(), monday, monday.plusWeeks(2)).map { it.startDate }
        assertEquals(listOf(monday, second.plusDays(1), monday.plusWeeks(2)), dates)
    }

    @Test
    fun `moving all of a series moves its anchor`() = runBlocking {
        val series = event("Yoga", monday, LocalTime.of(7, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY"))
        val moved = editor.move(series, occurrenceDate = monday.plusWeeks(1), toDate = monday.plusDays(1), scope = MoveScope.ALL, now = at)
        assertEquals(series.id, moved.id)
        assertEquals(monday.plusDays(1), moved.startDate)
        assertEquals(1, entryDao.getAll().size)
    }
}
