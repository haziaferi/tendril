package com.tendril.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The phone's fix PR (P6, 2026-09-18) — a task row's second line names days the tray's way, never as ISO. */
class TaskRowMetaTest {
    private val today = LocalDate.of(2026, 9, 18)
    private fun day(d: LocalDate, pattern: String) = d.format(DateTimeFormatter.ofPattern(pattern))

    @Test
    fun `the parts in order, joined by a middle dot`() {
        val start = LocalDate.of(2026, 9, 12); val due = LocalDate.of(2026, 9, 11)
        val expected = "${day(start, "EEE d")} · 08:00 · due ${day(due, "EEE d")} · 0/1 steps · 20m"
        assertEquals(expected, taskRowMeta(start, LocalTime.of(8, 0), due, 0, 1, "20m", today))
    }

    @Test
    fun `a day outside this month names its month, a bare task an empty line`() {
        val oct = LocalDate.of(2026, 10, 3)
        assertEquals("due " + day(oct, "EEE d MMM"), taskRowMeta(null, null, oct, 0, 0, null, today))
        assertEquals("", taskRowMeta(null, null, null, 0, 0, null, today))
        assertEquals("3/4 steps", taskRowMeta(null, null, null, 3, 4, null, today))
    }
}
