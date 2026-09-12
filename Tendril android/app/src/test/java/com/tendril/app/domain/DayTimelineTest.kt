package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.domain.plan.TimelineExtra
import com.tendril.app.domain.plan.timeOfMinute
import com.tendril.app.domain.plan.snapMinute
import com.tendril.app.domain.plan.timelineBlocks
import com.tendril.app.domain.plan.unplannedTasks
import com.tendril.app.domain.recurrence.EntryOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** §0.8 step 7b — the day as blocks: spans, estimates, the default, lanes, snapping, the rail. */
class DayTimelineTest {

    private val day = LocalDate.of(2026, 9, 14)
    private val at = Instant.EPOCH
    private var nextId = 1L

    private fun event(title: String, start: LocalTime, end: LocalTime?, endDate: LocalDate? = null) = Entry(
        id = nextId++, title = title, kind = EntryKind.EVENT, startDate = day, startTime = start,
        endDate = endDate ?: if (end != null) day else null, endTime = end, recurrenceRule = null, createdAt = at, updatedAt = at,
    )

    private fun task(title: String, date: LocalDate? = day, time: LocalTime? = null, estimate: Duration? = null, parent: Long? = null) = Entry(
        id = nextId++, title = title, kind = EntryKind.TASK, startDate = date, startTime = time, endDate = null, endTime = null,
        recurrenceRule = null, status = EntryStatus.PENDING, estimate = estimate, parentEntryId = parent, createdAt = at, updatedAt = at,
    )

    private fun blocks(vararg entries: Entry, extras: List<TimelineExtra> = emptyList()) =
        timelineBlocks(EntryOccurrences.onDay(entries.toList(), day), extras)

    @Test
    fun `an event is its span, a task its estimate, an unestimated task the default`() {
        val b = blocks(
            event("Dentist", LocalTime.of(15, 0), LocalTime.of(15, 45)),
            task("Write", time = LocalTime.of(9, 0), estimate = Duration.ofMinutes(90)),
            task("Call", time = LocalTime.of(11, 0)),
        ).associateBy { it.title }
        assertEquals(45, b.getValue("Dentist").minutes); assertTrue(!b.getValue("Dentist").estimated)
        assertEquals(90, b.getValue("Write").minutes); assertTrue(b.getValue("Write").estimated)
        assertEquals(30, b.getValue("Call").minutes); assertTrue(b.getValue("Call").estimated)
        assertEquals(BlockKind.EVENT, b.getValue("Dentist").kind); assertEquals(BlockKind.TASK, b.getValue("Call").kind)
    }

    @Test
    fun `untimed items are not blocks`() {
        assertTrue(blocks(task("Someday"), event("All day", LocalTime.MIDNIGHT, null).copy(startTime = null)).isEmpty())
    }

    @Test
    fun `overlapping blocks share lanes, non-overlapping ones do not`() {
        val b = blocks(
            event("A", LocalTime.of(9, 0), LocalTime.of(10, 0)),
            event("B", LocalTime.of(9, 30), LocalTime.of(10, 30)),
            event("C", LocalTime.of(14, 0), LocalTime.of(15, 0)),
        ).associateBy { it.title }
        assertEquals(2, b.getValue("A").lanes); assertEquals(0, b.getValue("A").lane)
        assertEquals(2, b.getValue("B").lanes); assertEquals(1, b.getValue("B").lane)
        assertEquals(1, b.getValue("C").lanes); assertEquals(0, b.getValue("C").lane)
    }

    @Test
    fun `a habit at its time is a block too, with its duration or the default`() {
        val b = blocks(extras = listOf(TimelineExtra("h1", "Stretch", LocalTime.of(7, 0), Duration.ofMinutes(10), BlockKind.HABIT))).single()
        assertEquals(BlockKind.HABIT, b.kind)
        assertEquals("a ten-minute habit still gets a tappable block", 15, b.minutes)
    }

    @Test
    fun `snapping`() {
        assertEquals(600, snapMinute(607)); assertEquals(615, snapMinute(608))
        assertEquals(0, snapMinute(-5)); assertEquals(24 * 60 - 15, snapMinute(24 * 60 + 40))
        assertEquals(LocalTime.of(10, 15), timeOfMinute(615))
    }

    @Test
    fun `the rail offers today's untimed tasks and Someday, never steps or timed ones`() {
        val parent = task("Parent")
        val rail = unplannedTasks(
            listOf(parent, task("Step", parent = parent.id), task("Someday", date = null), task("Timed", time = LocalTime.of(9, 0)), task("Elsewhere", date = day.plusDays(1))),
            day,
        )
        assertEquals(listOf("Parent", "Someday"), rail.map { it.title })
    }
}
