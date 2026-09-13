package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.journal.journalDayOf
import com.tendril.app.domain.journal.todayHabits
import com.tendril.app.domain.journal.todayTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/** §3.1.4 / §0.8 step 8b — which page is today's Journal, and what it shows. */
class JournalTodayTest {

    private val at = Instant.EPOCH
    private val today: LocalDate = LocalDate.of(2026, 9, 13)
    private val root = Page(id = 1, title = "Journal", kind = PageKind.PAGE, createdAt = at, updatedAt = at)
    private fun page(title: String, parentId: Long? = root.id) =
        Page(id = 9, title = title, parentId = parentId, kind = PageKind.PAGE, createdAt = at, updatedAt = at)

    private fun task(id: Long, start: LocalDate?, repeat: Period? = null, status: EntryStatus = EntryStatus.PENDING, deleted: Boolean = false) = Entry(
        id = id, title = "Task $id", kind = EntryKind.TASK, startDate = start, startTime = null, endDate = null, endTime = null,
        recurrenceRule = repeat?.let { RecurrenceRule.Elastic(it) }, status = status,
        deletedAt = if (deleted) at else null, createdAt = at, updatedAt = at,
    )

    private fun habit(id: Long, everyDays: Int = 1, last: LocalDate? = null, time: LocalTime? = null, deleted: Boolean = false) = Habit(
        id = id, title = "Habit $id", time = time, frequency = HabitFrequency(everyDays, IntervalUnit.DAY), lastCompletedDate = last,
        deletedAt = if (deleted) at else null, createdAt = at, updatedAt = at,
    )

    @Test
    fun `a journal day is a dated child of the Journal root, nothing else`() {
        assertEquals(today, journalDayOf(page("journal/2026-09-13"), root))
        assertNull("wrong parent", journalDayOf(page("journal/2026-09-13", parentId = 77), root))
        assertNull("no root known", journalDayOf(page("journal/2026-09-13"), null))
        assertNull("not the journal title shape", journalDayOf(page("Notes from 2026-09-13"), root))
        assertNull("prefix but no date", journalDayOf(page("journal/today"), root))
    }

    @Test
    fun `today's tasks are what starts today, a recurring one by its next date, done kept, trashed and tomorrow's out`() {
        val tasks = listOf(
            task(1, today),
            task(2, today, repeat = Period.ofDays(1)),
            task(8, today.minusDays(7), repeat = Period.ofDays(1)),
            task(3, today.plusDays(1)),
            task(4, today, deleted = true),
            task(5, today, status = EntryStatus.DONE),
            task(6, today.minusDays(1)),
            task(7, null),
        )
        assertEquals(listOf(1L, 2L, 5L), todayTasks(tasks, today).map { it.entry.id }.sorted())
    }

    @Test
    fun `today's habits are the due ones and the ones already checked in today, timed first`() {
        val habits = listOf(
            habit(1, time = LocalTime.of(21, 0)),
            habit(2, everyDays = 3, last = today.minusDays(1)),
            habit(3, last = today),
            habit(4, deleted = true),
            habit(5, everyDays = 3, last = today.minusDays(3), time = LocalTime.of(7, 0)),
        )
        assertEquals(listOf(5L, 1L, 3L), todayHabits(habits, today).map { it.id })
    }
}
