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

    /** Audit 2026-09-24 5a.6 — a sheet saved with nothing changed claimed an edit (§9.4). */
    @Test
    fun `saving an unchanged entry writes nothing`() = runBlocking {
        val e = event("Dentist", monday, LocalTime.of(15, 0))
        editor.save(e, at.plusSeconds(60))
        assertEquals(at, entryDao.getById(e.id)!!.updatedAt)
    }

    /**
     * Audit 2026-09-24 5a.8. The edit sheet hands back the entry as it was when the sheet opened,
     * with its own fields laid over it. Everything else in that copy — status, trash, the Google
     * id — is stale, and `save` wrote it all back: a tick, a trash or a push landing while the
     * sheet was open was undone by saving it.
     */
    @Test
    fun `a save keeps what changed outside the sheet while it was open`() = runBlocking {
        val sheet = task("Call bank", monday)
        entryDao.update(entryDao.getById(sheet.id)!!.copy(status = EntryStatus.DONE, googleEventId = "g-1", updatedAt = at.plusSeconds(30)))

        editor.save(sheet.copy(title = "Call the bank"), at.plusSeconds(60))

        val saved = entryDao.getById(sheet.id)!!
        assertEquals("the sheet's edit lands", "Call the bank", saved.title)
        assertEquals("the tick made meanwhile survives", EntryStatus.DONE, saved.status)
        assertEquals("and so does the Google id", "g-1", saved.googleEventId)
    }

    @Test
    fun `a save does not restore an entry trashed while the sheet was open`() = runBlocking {
        val sheet = event("Dentist", monday, LocalTime.of(15, 0))
        entryDao.update(entryDao.getById(sheet.id)!!.copy(deletedAt = at.plusSeconds(30), updatedAt = at.plusSeconds(30)))

        editor.save(sheet.copy(title = "Dentist, moved"), at.plusSeconds(60))

        assertEquals(at.plusSeconds(30), entryDao.getById(sheet.id)!!.deletedAt)
    }

    /**
     * Audit 2026-09-24 5.4, as the 2026-09-26 walk found it. The occurrence moved alone was a copy
     * of its series, external ids included: its `providerEventId` was the series' own row in the
     * system calendar, so mirroring it *updated the series* to the moved date — on the phone every
     * later occurrence then showed on the wrong weekday. Its `googleEventId` was the series' Google
     * event, which the next push would have overwritten with the one occurrence.
     */
    @Test
    fun `an occurrence moved alone does not carry its series' external ids`() = runBlocking {
        val base = event("Yoga", monday, LocalTime.of(7, 0), LocalTime.of(8, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY"))
        entryDao.update(entryDao.getById(base.id)!!.copy(providerEventId = 221L, googleEventId = "g-series"))
        val series = entryDao.getById(base.id)!!

        val moved = editor.move(series, monday.plusWeeks(1), monday.plusWeeks(1).plusDays(1), scope = MoveScope.THIS_ONE, now = at)

        assertNull("the system calendar row is the series', not this occurrence's", moved.providerEventId)
        assertNull("and so is the Google event", moved.googleEventId)
        assertEquals("the series keeps both", 221L, entryDao.getById(base.id)!!.providerEventId)
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
        val t = task("Call bank", monday).copy(dueDate = monday.plusDays(4), importance = 3, recurrenceRule = RecurrenceRule.Elastic(Period.ofWeeks(1)))
        val e = editor.save(t.copy(kind = EntryKind.EVENT), at)
        assertNull(e.status); assertNull(e.dueDate); assertEquals(0, e.importance)
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
    fun `an all-day entry moved to another day stays all-day, and a time places an untimed task`() = runBlocking {
        val e = event("Holiday", monday)
        val moved = editor.move(e, monday, monday.plusDays(1), toTime = null, now = at)
        assertNull(moved.startTime)

        // Plan mode's drop: a task with no time lands at the hour it was dropped on.
        val t = task("Call bank", monday)
        val placed = editor.move(t, null, monday, toTime = LocalTime.of(10, 15), now = at)
        assertEquals(LocalTime.of(10, 15), placed.startTime)
        assertEquals(monday, placed.startDate)
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

    @Test
    fun `clearWhen drops the date and time and keeps the deadline`() = runBlocking {
        val t = task("Call the library", monday, LocalTime.of(10, 0)).copy(dueDate = monday.plusDays(3))
        entryDao.update(t)
        val cleared = editor.clearWhen(entryDao.getById(t.id)!!, at)!!
        assertNull(cleared.startDate)
        assertNull(cleared.startTime)
        assertEquals(monday.plusDays(3), cleared.dueDate)
        assertEquals(monday.plusDays(3), entryDao.getById(t.id)!!.dueDate)
        assertTrue(changed.any { it.id == t.id })
    }

    @Test
    fun `clearWhen refuses a series and clears an override of one`() = runBlocking {
        val series = task("Bins", monday).copy(recurrenceRule = RecurrenceRule.Elastic(Period.ofWeeks(2)))
        entryDao.update(series)
        assertNull(editor.clearWhen(entryDao.getById(series.id)!!, at))
        assertEquals(monday, entryDao.getById(series.id)!!.startDate)
        val override = stored(task("Bins", monday.plusDays(1)).copy(originalEntryId = series.id, originalOccurrenceDate = monday))
        assertNull(editor.clearWhen(override, at)!!.startDate)
    }
}
