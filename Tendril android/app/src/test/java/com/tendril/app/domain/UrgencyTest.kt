package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.domain.urgency.Urgency
import com.tendril.app.domain.urgency.timePressure
import com.tendril.app.domain.urgency.urgencyOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 14g·3 (B§13.8.1) — the level is the greater of what was set and the deadline's pressure today. */
class UrgencyTest {
    private val today = LocalDate.of(2026, 9, 16)
    private fun task(importance: Int = 0, due: LocalDate? = null, status: EntryStatus = EntryStatus.PENDING) = Entry(
        title = "t", kind = EntryKind.TASK, startDate = today, startTime = null, endDate = null, endTime = null, recurrenceRule = null,
        status = status, dueDate = due, importance = importance, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun `time pressure by the horizons`() {
        assertEquals(Urgency.NONE, timePressure(null, today))
        assertEquals(Urgency.NONE, timePressure(today.plusDays(8), today))
        assertEquals(Urgency.LOW, timePressure(today.plusDays(7), today))
        assertEquals(Urgency.LOW, timePressure(today.plusDays(4), today))
        assertEquals(Urgency.MID, timePressure(today.plusDays(3), today))
        assertEquals(Urgency.MID, timePressure(today.plusDays(2), today))
        assertEquals(Urgency.HIGH, timePressure(today.plusDays(1), today))
        assertEquals(Urgency.HIGH, timePressure(today, today))
        assertEquals(Urgency.URGENT, timePressure(today.minusDays(1), today))
    }

    @Test
    fun `the level is the greater of the set level and the pressure, and a done task has none`() {
        assertEquals(Urgency.HIGH, urgencyOf(task(importance = 3), today))
        assertEquals(Urgency.HIGH, urgencyOf(task(importance = 1, due = today), today))
        assertEquals(Urgency.URGENT, urgencyOf(task(importance = 4, due = today.plusDays(30)), today))
        assertEquals(Urgency.NONE, urgencyOf(task(importance = 4, due = today.minusDays(1), status = EntryStatus.DONE), today))
        assertEquals(Urgency.NONE, urgencyOf(task(), today))
    }

    @Test
    fun `a level outside the ladder is clamped`() {
        assertEquals(Urgency.URGENT, Urgency.fromLevel(9))
        assertEquals(Urgency.NONE, Urgency.fromLevel(-2))
        assertEquals(Urgency.MID, Urgency.fromLevel(2))
    }
}
