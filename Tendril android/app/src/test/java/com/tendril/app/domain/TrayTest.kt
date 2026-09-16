package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.plan.trayTasks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/** B§13.6 #5 — what the Calendar's task tray offers: the unscheduled and the overdue, never a step, a done task or a series. */
class TrayTest {
    private val at = Instant.ofEpochMilli(1_000L)
    private val today = LocalDate.of(2026, 9, 16)
    private fun task(title: String, date: LocalDate?, status: EntryStatus = EntryStatus.PENDING, parent: Long? = null, rule: RecurrenceRule? = null, kind: EntryKind = EntryKind.TASK, id: Long = title.hashCode().toLong()) =
        Entry(id = id, title = title, kind = kind, startDate = date, startTime = null, endDate = null, endTime = null, status = if (kind == EntryKind.TASK) status else null, parentEntryId = parent, recurrenceRule = rule, createdAt = at, updatedAt = at)

    @Test
    fun `unscheduled are the undated pending tasks, by title`() {
        val tray = trayTasks(listOf(task("Water the plants", null), task("Call the library", null), task("Read", today)), today)
        assertEquals(listOf("Call the library", "Water the plants"), tray.unscheduled.map { it.title })
    }

    @Test
    fun `a step, a done task, an event and a series are never offered`() {
        val tray = trayTasks(listOf(
            task("Step", null, parent = 7),
            task("Done", null, status = EntryStatus.DONE),
            task("Event", null, kind = EntryKind.EVENT),
            task("Bins", today.minusDays(3), rule = RecurrenceRule.Elastic(Period.ofWeeks(2))),
        ), today)
        assertEquals(emptyList<String>(), tray.unscheduled.map { it.title })
        assertEquals(emptyList<String>(), tray.overdue.map { it.title })
    }

    @Test
    fun `overdue is a day past, oldest first, and today is not overdue`() {
        val tray = trayTasks(listOf(task("Today", today), task("Fine", today.minusDays(8)), task("Yesterday", today.minusDays(1))), today)
        assertEquals(listOf("Fine", "Yesterday"), tray.overdue.map { it.title })
        assertEquals(emptyList<String>(), tray.unscheduled.map { it.title })
    }
}
