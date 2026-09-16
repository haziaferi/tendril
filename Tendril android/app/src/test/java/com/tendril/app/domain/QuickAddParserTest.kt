package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.recurrence.EntryOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/**
 * §0.8 step 5 — the grammar, one line per case. `today` is a Saturday, 12 September 2026, so
 * every relative date below has one right answer.
 */
class QuickAddParserTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 12) // Saturday

    private fun event(line: String) = QuickAddParser.parse(line, today, EntryKind.EVENT)
    private fun task(line: String) = QuickAddParser.parse(line, today, EntryKind.TASK)

    // ------------------------------------------------------------------ dates

    @Test
    fun `relative dates`() {
        assertEquals(today, event("Dentist today").date)
        assertEquals(today.plusDays(1), event("Dentist tomorrow").date)
        assertEquals(today.plusDays(1), event("Dentist tmr").date)
        assertEquals(today.plusDays(3), event("Dentist in 3 days").date)
        assertEquals(today.plusWeeks(2), event("Dentist in 2 weeks").date)
        assertEquals("Dentist", event("Dentist in 3 days").title)
    }

    @Test
    fun `weekdays are the next such day, and next means the one after`() {
        assertEquals(LocalDate.of(2026, 9, 14), event("Call monday").date)
        assertEquals(LocalDate.of(2026, 9, 21), event("Call next monday").date)
        assertEquals(LocalDate.of(2026, 9, 19), event("Call sat").date) // a week on, never today
        assertEquals("Call", event("Call next monday").title)
    }

    @Test
    fun `absolute dates, day-first where ambiguous`() {
        assertEquals(LocalDate.of(2026, 9, 20), event("Party sep 20").date)
        assertEquals(LocalDate.of(2026, 9, 20), event("Party 20 sept").date)
        assertEquals(LocalDate.of(2026, 9, 20), event("Party 20/9").date)
        assertEquals(LocalDate.of(2027, 3, 1), event("Party 1 march").date) // already past this year
        assertEquals(LocalDate.of(2026, 12, 24), event("Party 2026-12-24").date)
        assertEquals(LocalDate.of(2027, 1, 5), event("Party 5/1/2027").date)
        assertEquals("Party", event("Party 20/9").title)
    }

    @Test
    fun `no date leaves the date null for the surface to fill`() {
        val p = event("Dentist")
        assertNull(p.date)
        assertEquals("Dentist", p.title)
    }

    // ------------------------------------------------------------------ times and spans

    @Test
    fun `times`() {
        assertEquals(LocalTime.of(15, 0), event("Dentist tmr 3pm").time)
        assertEquals(LocalTime.of(15, 30), event("Dentist at 3:30pm").time)
        assertEquals(LocalTime.of(15, 30), event("Dentist 15:30").time)
        assertEquals(LocalTime.of(0, 30), event("Dentist 12:30am").time)
        assertEquals(LocalTime.NOON, event("Lunch at noon").time)
        assertEquals("Dentist", event("Dentist tmr 3pm").title)
        // A bare number is not a time.
        assertNull(event("Chapter 3").time)
        assertEquals("Chapter 3", event("Chapter 3").title)
    }

    @Test
    fun `a span sets an end time and makes the line an event even on the Tasks surface`() {
        val p = task("Standup 9-9:30am tomorrow")
        assertEquals(EntryKind.EVENT, p.kind)
        assertEquals(LocalTime.of(9, 0), p.time)
        assertEquals(LocalTime.of(9, 30), p.endTime)
        assertEquals(today.plusDays(1), p.endDate)
        assertEquals("Standup", p.title)

        val q = event("Workshop 15:00-16:30")
        assertEquals(LocalTime.of(15, 0), q.time)
        assertEquals(LocalTime.of(16, 30), q.endTime)
    }

    @Test
    fun `for on an event is a span, on a task an estimate`() {
        val e = event("Call at 3pm for 1h30")
        assertEquals(LocalTime.of(15, 0), e.time)
        assertEquals(LocalTime.of(16, 30), e.endTime)
        assertNull(e.estimate)
        assertEquals("Call", e.title)

        val t = task("Write report for 45m")
        assertEquals(EntryKind.TASK, t.kind)
        assertEquals(Duration.ofMinutes(45), t.estimate)
        assertNull(t.endTime)
        assertEquals("Write report", t.title)

        // No unit on a minutes-only count: not a duration.
        assertNull(task("Book a table for 2 people").estimate)
        assertEquals("Book a table for 2 people", task("Book a table for 2 people").title)
    }

    // ------------------------------------------------------------------ recurrence

    @Test
    fun `recurrence takes the kind's own shape`() {
        assertEquals(RecurrenceRule.Elastic(Period.ofDays(1)), task("Stretch daily").recurrence)
        assertEquals(RecurrenceRule.Elastic(Period.ofWeeks(2)), task("Bins every 2 weeks").recurrence)
        assertEquals(RecurrenceRule.Fixed("FREQ=DAILY"), event("Standup every day").recurrence)
        assertEquals(RecurrenceRule.Fixed("FREQ=WEEKLY;INTERVAL=2"), event("Sync every 2 weeks").recurrence)
        assertEquals(RecurrenceRule.Fixed("FREQ=MONTHLY"), event("Rent monthly").recurrence)
        assertEquals(RecurrenceRule.Fixed("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"), event("Standup every weekday 9am").recurrence)
        assertEquals("Stretch", task("Stretch daily").title)
    }

    @Test
    fun `every monday anchors on the next Monday and is not read as a date`() {
        val t = task("Gym every monday 7am")
        assertEquals(RecurrenceRule.Elastic(Period.ofWeeks(1)), t.recurrence)
        assertEquals(LocalDate.of(2026, 9, 14), t.date)
        assertEquals(LocalTime.of(7, 0), t.time)
        assertEquals("Gym", t.title)

        val e = event("Yoga every monday")
        assertEquals(RecurrenceRule.Fixed("FREQ=WEEKLY;BYDAY=MO"), e.recurrence)
        assertEquals(LocalDate.of(2026, 9, 14), e.date)
    }

    @Test
    fun `a repeat with no date starts today`() {
        assertEquals(today, task("Stretch daily").date)
    }

    @Test
    fun `an emitted RRULE expands through the app's own expander`() {
        val p = event("Yoga every monday")
        val entry = Entry(
            title = p.title, kind = p.kind, startDate = p.date, startTime = null, endDate = null, endTime = null,
            recurrenceRule = p.recurrence, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        val dates = EntryOccurrences.expand(listOf(entry), today, today.plusWeeks(3)).map { it.startDate }
        assertEquals(listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)), dates)
    }

    // ------------------------------------------------------------------ deadline, importance, kind

    @Test
    fun `by and due set the deadline and never the When`() {
        val t = task("Call bank by friday")
        assertEquals(LocalDate.of(2026, 9, 18), t.deadline)
        assertNull(t.date)
        assertEquals("Call bank", t.title)

        val u = task("Taxes due sep 30 tomorrow")
        assertEquals(LocalDate.of(2026, 9, 30), u.deadline)
        assertEquals(today.plusDays(1), u.date)
        assertEquals("Taxes", u.title)

        // `by` as a preposition stays in the title.
        assertNull(task("Call by phone tomorrow").deadline)
        assertEquals("Call by phone", task("Call by phone tomorrow").title)
        // An event has no deadline.
        assertNull(event("Party by friday").deadline)
    }

    @Test
    fun `importance`() {
        // 14g·3 — `!` is what the flag was (high), `!!` and more urgent, the word high.
        assertEquals(3, task("Call bank by friday !").importance)
        assertEquals(4, task("Call bank by friday !!").importance)
        assertEquals(4, task("Call bank !!!").importance)
        assertEquals(3, task("important: renew passport").importance)
        assertEquals(0, task("Call bank by friday").importance)
        assertEquals("Call bank", task("Call bank by friday !").title)
        assertEquals("renew passport", task("important: renew passport").title)
    }

    @Test
    fun `the surface picks the kind and a prefix overrides it`() {
        assertEquals(EntryKind.EVENT, event("Dentist tmr").kind)
        assertEquals(EntryKind.TASK, task("Dentist tmr").kind)
        assertEquals(EntryKind.TASK, event("todo call bank").kind)
        assertEquals(EntryKind.TASK, event("Task: call bank").kind)
        assertEquals(EntryKind.EVENT, task("event team lunch").kind)
        assertEquals("call bank", event("todo call bank").title)
        assertEquals("team lunch", task("event team lunch").title)
    }

    @Test
    fun `a task never carries a span, and a forced task keeps its estimate`() {
        val t = event("todo Write report for 45m tmr 3pm")
        assertEquals(EntryKind.TASK, t.kind)
        assertEquals(LocalTime.of(15, 0), t.time)
        assertNull(t.endTime)
        assertEquals(Duration.ofMinutes(45), t.estimate)
    }

    @Test
    fun `spans cover exactly the recognised characters, in order`() {
        val p = task("Call bank tmr 3pm by friday !")
        assertEquals(listOf(TokenKind.DATE, TokenKind.TIME, TokenKind.DEADLINE, TokenKind.IMPORTANT), p.spans.map { it.kind })
        assertEquals("tmr", "Call bank tmr 3pm by friday !".substring(p.spans[0].start, p.spans[0].end))
        assertEquals("by friday", "Call bank tmr 3pm by friday !".substring(p.spans[2].start, p.spans[2].end))
    }
}
