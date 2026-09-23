package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.recurrence.EntryOccurrences
import com.tendril.app.domain.recurrence.RecurrenceSpec
import com.tendril.app.domain.recurrence.occurrenceDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/**
 * §4.1's recurring-EVENT expansion, multi-day spans, and single-occurrence exceptions.
 *
 * All three were stored, synced and written outward to `CalendarContract` (§9.11) and Google
 * (§9.5.1) but never read back, so Tendril's own Calendar drew one row on one day: a weekly
 * meeting appeared once, a three-day offsite appeared on day one, and an exception row did
 * nothing at all. These pin the read side.
 */
class RecurrenceExpansionTest {

    private val epoch: Instant = Instant.ofEpochMilli(1_000)

    private fun event(
        id: Long,
        start: LocalDate,
        rrule: String? = null,
        end: LocalDate? = null,
        time: LocalTime? = null,
    ) = Entry(
        id = id,
        title = "Event $id",
        kind = EntryKind.EVENT,
        startDate = start,
        startTime = time,
        endDate = end,
        endTime = null,
        recurrenceRule = rrule?.let { RecurrenceRule.Fixed(it) },
        createdAt = epoch,
        updatedAt = epoch,
    )

    private fun exceptionOf(
        id: Long,
        base: Entry,
        occurrence: LocalDate,
        skip: Boolean = false,
        movedTo: LocalDate? = null,
    ) = Entry(
        id = id,
        title = "Moved ${base.title}",
        kind = EntryKind.EVENT,
        startDate = movedTo ?: occurrence,
        startTime = null,
        endDate = null,
        endTime = null,
        recurrenceRule = null,
        originalEntryId = base.id,
        originalOccurrenceDate = occurrence,
        isExceptionSkip = if (skip) true else null,
        createdAt = epoch,
        updatedAt = epoch,
    )

    private fun datesOf(entries: List<Entry>, from: LocalDate, to: LocalDate) =
        EntryOccurrences.expand(entries, from, to).map { it.date }

    // ------------------------------------------------------------------ the parser

    @Test fun `weekly BYDAY expands to each named weekday`() {
        val spec = RecurrenceSpec.parse("FREQ=WEEKLY;BYDAY=MO,WE")!!
        // 2026-09-07 is a Monday.
        val dates = spec.occurrenceDates(
            anchor = LocalDate.of(2026, 9, 7),
            rangeStart = LocalDate.of(2026, 9, 7),
            rangeEnd = LocalDate.of(2026, 9, 20),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16),
            ),
            dates,
        )
    }

    @Test fun `INTERVAL skips weeks`() {
        val spec = RecurrenceSpec.parse("FREQ=WEEKLY;INTERVAL=2")!!
        val dates = spec.occurrenceDates(
            LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 6),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 5)),
            dates,
        )
    }

    @Test fun `COUNT is counted from the start of the series, not from the window`() {
        // The whole reason expansion walks from the anchor rather than from rangeStart: a
        // COUNT=3 series is over long before this window, and must contribute nothing.
        val spec = RecurrenceSpec.parse("FREQ=DAILY;COUNT=3")!!
        val anchor = LocalDate.of(2026, 1, 1)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)),
            spec.occurrenceDates(anchor, anchor, LocalDate.of(2026, 12, 31)),
        )
        assertEquals(
            emptyList<LocalDate>(),
            spec.occurrenceDates(anchor, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
        )
    }

    @Test fun `UNTIL bounds the series inclusively`() {
        val spec = RecurrenceSpec.parse("FREQ=DAILY;UNTIL=20260103T000000Z")!!
        val anchor = LocalDate.of(2026, 1, 1)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)),
            spec.occurrenceDates(anchor, anchor, LocalDate.of(2026, 2, 1)),
        )
    }

    @Test fun `monthly BYMONTHDAY 31 skips months that have no 31st`() {
        // RFC5545 skips an impossible date rather than clamping it — clamping would silently
        // move the occurrence to the 28th.
        val spec = RecurrenceSpec.parse("FREQ=MONTHLY;BYMONTHDAY=31")!!
        val dates = spec.occurrenceDates(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)),
            dates,
        )
    }

    @Test fun `monthly BYDAY with a negative ordinal is the last such weekday`() {
        val spec = RecurrenceSpec.parse("FREQ=MONTHLY;BYDAY=-1FR")!!
        val dates = spec.occurrenceDates(
            LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30),
        )
        // Last Fridays: 25 Sep, 30 Oct, 27 Nov 2026.
        assertEquals(
            listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 27)),
            dates,
        )
    }

    @Test fun `an unsupported rule part makes the whole rule unparseable`() {
        // Strict on purpose: ignoring a limiter would add occurrences that don't exist, and a
        // phantom calendar entry is worse than a missing one.
        assertNull(RecurrenceSpec.parse("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=-1"))
        assertNull(RecurrenceSpec.parse("FREQ=YEARLY;BYWEEKNO=20"))
        assertNull(RecurrenceSpec.parse("FREQ=HOURLY"))
        assertNull(RecurrenceSpec.parse(""))
        // COUNT and UNTIL together is malformed per RFC5545, not something to pick between.
        assertNull(RecurrenceSpec.parse("FREQ=DAILY;COUNT=3;UNTIL=20260101"))
        assertNotNull(RecurrenceSpec.parse("RRULE:FREQ=DAILY"))
    }

    @Test fun `an ordinal BYDAY that cannot limit makes the rule unparseable`() {
        // The same doctrine, one combination further. An ordinal BYDAY term beside BYMONTHDAY has
        // nothing to expand -- BYMONTHDAY already chose the days -- and this expander has no way to
        // apply "the first Friday" as a *limiter*, so it dropped the term and emitted every
        // BYMONTHDAY date. `FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=1FR` describes a 13th that is also a
        // first Friday, which no month has; it produced all twelve 13ths of 2026 -- eleven phantom
        // occurrences, the exact failure the test above refuses. Reachable from any imported .ics
        // (`IcsImporter` passes a component's RRULE through verbatim), not only from Google.
        assertNull(RecurrenceSpec.parse("FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=1FR"))
        assertNull(RecurrenceSpec.parse("FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=13;BYDAY=1FR"))
        // RFC5545: a numeric BYDAY is only meaningful under MONTHLY or YEARLY. Under DAILY or
        // WEEKLY the ordinal was ignored, turning an invalid rule into every Monday.
        assertNull(RecurrenceSpec.parse("FREQ=DAILY;BYDAY=1MO"))
        assertNull(RecurrenceSpec.parse("FREQ=WEEKLY;BYDAY=2TU"))
        // Unchanged: a plain BYDAY still limits BYMONTHDAY, and an ordinal alone still expands.
        assertNotNull(RecurrenceSpec.parse("FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=FR"))
        assertNotNull(RecurrenceSpec.parse("FREQ=MONTHLY;BYDAY=1FR"))
        assertNotNull(RecurrenceSpec.parse("FREQ=WEEKLY;BYDAY=TU"))
    }

    @Test fun `a Friday-the-13th rule puts nothing on a 13th that is not a Friday`() {
        // 2026's Friday the 13ths: February, March, November.
        val friday13 = RecurrenceSpec.parse("FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=FR")!!
        assertEquals(
            listOf(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 3, 13), LocalDate.of(2026, 11, 13)),
            friday13.occurrenceDates(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
        )
        // The ordinal spelling is unparseable, so the EVENT falls back to its own first day --
        // missing, never phantom.
        val base = event(1, LocalDate.of(2026, 2, 13), rrule = "FREQ=MONTHLY;BYMONTHDAY=13;BYDAY=1FR")
        assertEquals(
            listOf(LocalDate.of(2026, 2, 13)),
            datesOf(listOf(base), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
        )
    }

    // ------------------------------------------------------- expansion over real Entries

    @Test fun `a weekly EVENT appears on every occurrence in the window`() {
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY")
        val dates = datesOf(listOf(base), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28),
            ),
            dates,
        )
    }

    @Test fun `an unparseable rule falls back to the first occurrence only`() {
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY;BYSETPOS=2")
        assertEquals(
            listOf(LocalDate.of(2026, 9, 7)),
            datesOf(listOf(base), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
        )
    }

    @Test fun `a multi-day EVENT covers every day it spans`() {
        val base = event(1, LocalDate.of(2026, 9, 10), end = LocalDate.of(2026, 9, 12))
        assertEquals(
            listOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12)),
            datesOf(listOf(base), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
        )
    }

    @Test fun `a span that starts before the window still covers days inside it`() {
        val base = event(1, LocalDate.of(2026, 9, 10), end = LocalDate.of(2026, 9, 12))
        assertEquals(
            listOf(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12)),
            datesOf(listOf(base), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 30)),
        )
    }

    @Test fun `a recurring span repeats its duration, never the base row's absolute end date`() {
        // §4.1 round 3, stated explicitly there because the field existing didn't mean the
        // interaction was ever written down: a quarterly 3-day offsite is 3 days *each time*.
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY", end = LocalDate.of(2026, 9, 9))
        val second = EntryOccurrences
            .expand(listOf(base), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20))
        assertEquals(LocalDate.of(2026, 9, 14), second.first().startDate)
        assertEquals(LocalDate.of(2026, 9, 16), second.first().endDate)
        assertEquals(3, second.size)
    }

    @Test fun `a skip tombstone removes exactly its own occurrence`() {
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY")
        val skip = exceptionOf(2, base, occurrence = LocalDate.of(2026, 9, 14), skip = true)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)),
            datesOf(listOf(base, skip), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
        )
    }

    @Test fun `an override moves one occurrence and acts on the override row`() {
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY")
        val moved = exceptionOf(2, base, occurrence = LocalDate.of(2026, 9, 14), movedTo = LocalDate.of(2026, 9, 15))
        val occurrences = EntryOccurrences.expand(listOf(base, moved), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28),
            ),
            occurrences.map { it.date },
        )
        // Tapping the moved one must write to the override row, not the series' base row.
        assertEquals(2L, occurrences.single { it.date == LocalDate.of(2026, 9, 15) }.entry.id)
    }

    @Test fun `an exception whose base no longer produces that date still shows up`() {
        // Never silently drop a row: a stale override rendered as an ordinary entry is
        // recoverable, one that vanished is not.
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY")
        val orphan = exceptionOf(2, base, occurrence = LocalDate.of(2026, 9, 15), movedTo = LocalDate.of(2026, 9, 16))
        val dates = datesOf(listOf(base, orphan), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertTrue("the orphaned override is missing from $dates", LocalDate.of(2026, 9, 16) in dates)
    }

    @Test fun `a recurring TASK is never expanded`() {
        // §5.2/§6.2 — exactly one live Entry row exists per recurring task at any moment, and
        // resolve-and-advance moves it. Expanding would invent occurrences nothing can resolve.
        val task = Entry(
            id = 1,
            title = "Water bill",
            kind = EntryKind.TASK,
            startDate = LocalDate.of(2026, 9, 1),
            startTime = null,
            endDate = null,
            endTime = null,
            recurrenceRule = RecurrenceRule.Elastic(Period.ofDays(7)),
            status = EntryStatus.PENDING,
            createdAt = epoch,
            updatedAt = epoch,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 1)),
            datesOf(listOf(task), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
        )
    }

    @Test fun `a plain single-day entry expands to exactly itself`() {
        // The no-regression case: every pre-existing surface filtered `startDate == day`, and
        // must keep behaving identically for the rows that are not recurring or spanned.
        val base = event(1, LocalDate.of(2026, 9, 10), time = LocalTime.of(9, 0))
        val occurrences = EntryOccurrences.onDay(listOf(base), LocalDate.of(2026, 9, 10))
        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2026, 9, 10), occurrences.single().date)
        assertTrue(occurrences.single().isFirstDay)
        assertTrue(!occurrences.single().isMultiDay)
        assertTrue(!occurrences.single().isRecurrenceInstance)
        assertEquals(emptyList<LocalDate>(), datesOf(listOf(base), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 30)))
    }

    @Test fun `occurrences are ordered by day, then by time with untimed last`() {
        val untimed = event(1, LocalDate.of(2026, 9, 10))
        val nine = event(2, LocalDate.of(2026, 9, 10), time = LocalTime.of(9, 0))
        val eight = event(3, LocalDate.of(2026, 9, 10), time = LocalTime.of(8, 0))
        val tomorrow = event(4, LocalDate.of(2026, 9, 11), time = LocalTime.of(1, 0))

        val ids = EntryOccurrences
            .expand(listOf(nine, tomorrow, untimed, eight), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11))
            .map { it.entry.id }
        assertEquals(listOf(3L, 2L, 1L, 4L), ids)
    }

    @Test fun `the next alarm anchor skips a tombstoned occurrence`() {
        val base = event(1, LocalDate.of(2026, 9, 7), rrule = "FREQ=WEEKLY")
        val skip = exceptionOf(2, base, occurrence = LocalDate.of(2026, 9, 14), skip = true)

        val next = EntryOccurrences.nextOccurrenceOnOrAfter(base, listOf(skip), from = LocalDate.of(2026, 9, 8))
        assertEquals(LocalDate.of(2026, 9, 21), next?.startDate)
    }
}
