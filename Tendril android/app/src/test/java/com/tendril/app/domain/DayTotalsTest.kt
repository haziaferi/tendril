package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.plan.loggedByEntry
import com.tendril.app.domain.plan.loggedSegment
import com.tendril.app.domain.plan.loggedSpans
import com.tendril.app.domain.plan.minutesPerSession
import com.tendril.app.domain.plan.plannedMinutes
import com.tendril.app.domain.plan.timelineBlocks
import com.tendril.app.domain.recurrence.EntryOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** §0.8 step 7d — planned against logged: the sums, the per-row map, the wash, the habit's mean. */
class DayTotalsTest {

    private val day = LocalDate.of(2026, 9, 12)
    private val zone = ZoneOffset.UTC
    private val dayStart: Instant = day.atStartOfDay(zone).toInstant()
    private var nextId = 1L

    private fun task(title: String, time: LocalTime? = null, estimate: Duration? = null) = Entry(
        id = nextId++, title = title, kind = EntryKind.TASK, startDate = day, startTime = time, endDate = null, endTime = null,
        recurrenceRule = null, status = EntryStatus.PENDING, estimate = estimate, createdAt = dayStart, updatedAt = dayStart,
    )

    private fun log(uid: String, entryId: Long, startMin: Int, endMin: Int?) = TimeLog(
        uid = uid, entryId = entryId, startedAt = dayStart.plusSeconds(startMin * 60L),
        endedAt = endMin?.let { dayStart.plusSeconds(it * 60L) }, updatedAt = dayStart,
    )

    @Test
    fun `planned is the blocks plus the untimed estimates, a timed task counted once`() {
        val timed = task("Write", time = LocalTime.of(9, 0), estimate = Duration.ofMinutes(90))
        val untimed = task("Call", estimate = Duration.ofMinutes(15))
        val guess = task("Tidy")
        val all = listOf(timed, untimed, guess)
        val blocks = timelineBlocks(EntryOccurrences.onDay(all, day))
        assertEquals(90 + 15, plannedMinutes(blocks, all.filter { it.startTime == null }))
        assertEquals("the belt: a timed task handed in as untimed is ignored", 90 + 15, plannedMinutes(blocks, all))
    }

    @Test
    fun `logged per entry counts an open log to now and omits entries with nothing`() {
        val now = dayStart.plusSeconds(10 * 3600)
        val logs = listOf(log("a", 1, 540, 570), log("b", 1, 590, null), log("c", 2, 600, 600))
        assertEquals(mapOf(1L to 40), loggedByEntry(logs, now))
    }

    @Test
    fun `spans are the day's minutes, clipped, and a midnight crossing belongs to its start day`() {
        val now = dayStart.plusSeconds(23 * 3600)
        val spans = loggedSpans(listOf(log("a", 1, 23 * 60 + 30, 25 * 60), log("b", 1, 22 * 60, null)), day, now, zone)
        assertEquals(listOf(23 * 60 + 30 to 24 * 60, 22 * 60 to 23 * 60), spans.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `a session mean needs two closed logs`() {
        assertNull(minutesPerSession(listOf(log("a", 1, 0, 30))))
        assertNull(minutesPerSession(listOf(log("a", 1, 0, 30), log("b", 1, 60, null))))
        assertEquals(20, minutesPerSession(listOf(log("a", 1, 0, 30), log("b", 1, 60, 70))))
    }

    @Test
    fun `the row segment`() {
        assertNull(loggedSegment(0, Duration.ofMinutes(45)))
        assertEquals("20m of ~45m", loggedSegment(20, Duration.ofMinutes(45)))
        assertEquals("1h 05m logged", loggedSegment(65, null))
    }
}
