package com.tendril.app.ui.trash

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.dayLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Recorded on the 2026-09-26 phone walk: the Tasks Trash sheet read `Event · 2026-09-26` — ISO,
 * 5.6's class on another sheet, where every other date the phone shows is the tray's day form.
 */
class EntryTrashSubtitleTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val at = Instant.parse("2026-09-26T09:00:00Z")

    private fun entry(kind: EntryKind, date: LocalDate?, time: LocalTime?) = Entry(
        title = "Yoga", kind = kind, startDate = date, startTime = time, endDate = null, endTime = null,
        recurrenceRule = null, createdAt = at, updatedAt = at,
    )

    @Test
    fun `a trashed entry's date is the day form, with its clock time`() {
        val date = LocalDate.of(2026, 9, 26)
        assertEquals("Event · ${dayLabel(date, today)} 09:00", entry(EntryKind.EVENT, date, LocalTime.of(9, 0)).trashSubtitle(today))
    }

    @Test
    fun `a date outside this month names its month`() {
        val date = LocalDate.of(2026, 10, 3)
        assertEquals("Task · ${dayLabel(date, today)}", entry(EntryKind.TASK, date, null).trashSubtitle(today))
    }

    @Test
    fun `a dateless entry says so`() {
        assertEquals("Task · No date", entry(EntryKind.TASK, null, null).trashSubtitle(today))
    }
}
