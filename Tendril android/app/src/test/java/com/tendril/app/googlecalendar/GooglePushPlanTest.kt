package com.tendril.app.googlecalendar

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Audit 5.4's Google half, recorded open on 2026-09-26 and found wider than recorded. The engine
 * pushed every EVENT row as an event of its own and knew nothing of a series' exception rows:
 *
 *  - a skipped occurrence (a skip row) was pushed as an event, so the skip *appeared* on Google;
 *  - a moved occurrence was pushed as a standalone event while Google's series, sent with no
 *    EXDATE, still held the original date — the occurrence twice;
 *  - an override made before 2026-09-26 carries its series' `googleEventId`, so its push
 *    overwrote the whole Google series with one date, and trashing it deleted the series.
 *
 * [planGooglePush] is the push's decisions without the network, so each is an assertion.
 */
class GooglePushPlanTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val t0: Instant = Instant.parse("2026-09-26T08:00:00Z")
    private val since: Instant = t0.plusSeconds(60)
    private val later: Instant = t0.plusSeconds(120)

    private val series = Entry(
        id = 1, uid = "series", title = "Yoga", kind = EntryKind.EVENT,
        startDate = LocalDate.of(2026, 9, 26), startTime = LocalTime.of(9, 0), endDate = LocalDate.of(2026, 9, 26), endTime = LocalTime.of(10, 0),
        recurrenceRule = RecurrenceRule.Fixed("FREQ=WEEKLY"), googleEventId = "g-series", createdAt = t0, updatedAt = t0,
    )

    private fun exception(id: Long, occurrence: LocalDate, skip: Boolean, googleEventId: String? = null) = series.copy(
        id = id, uid = "ex$id", recurrenceRule = null, startDate = if (skip) occurrence else occurrence.minusDays(1),
        endDate = if (skip) occurrence else occurrence.minusDays(1),
        originalEntryId = 1, originalOccurrenceDate = occurrence, isExceptionSkip = skip,
        googleEventId = googleEventId, updatedAt = later,
    )

    private fun upserts(plan: List<GooglePushStep>) = plan.filterIsInstance<GooglePushStep.Upsert>().associateBy { it.entry.id }

    @Test
    fun `a skipped occurrence is never pushed as an event, and its series carries the EXDATE`() {
        val plan = planGooglePush(listOf(series, exception(2, LocalDate.of(2026, 10, 3), skip = true)), since, zone)

        val byId = upserts(plan)
        assertEquals("the skip row itself is not an event", setOf(1L), byId.keys)
        assertEquals(
            listOf("RRULE:FREQ=WEEKLY", "EXDATE;TZID=Europe/Rome:20261003T090000"),
            byId.getValue(1).event.recurrence,
        )
    }

    @Test
    fun `a moved occurrence is its own event, and the series stops holding its old date`() {
        val plan = planGooglePush(listOf(series, exception(2, LocalDate.of(2026, 10, 3), skip = false)), since, zone)

        val byId = upserts(plan)
        assertEquals(null, byId.getValue(2).existingId)
        assertEquals(null, byId.getValue(2).event.recurrence)
        assertEquals(listOf("RRULE:FREQ=WEEKLY", "EXDATE;TZID=Europe/Rome:20261003T090000"), byId.getValue(1).event.recurrence)
    }

    @Test
    fun `an override carrying its series' Google id gets an event of its own, never the series'`() {
        val plan = planGooglePush(listOf(series, exception(2, LocalDate.of(2026, 10, 3), skip = false, googleEventId = "g-series")), since, zone)

        assertEquals("inserted fresh, not written over the series", null, upserts(plan).getValue(2).existingId)
    }

    @Test
    fun `trashing an override that carries its series' id unlinks it without deleting the series`() {
        val trashed = exception(2, LocalDate.of(2026, 10, 3), skip = false, googleEventId = "g-series").copy(deletedAt = later)
        val plan = planGooglePush(listOf(series, trashed), since, zone)

        val delete = plan.filterIsInstance<GooglePushStep.Unlink>().single()
        assertEquals(2L, delete.entry.id)
        assertEquals("the series' event is not the override's to delete", null, delete.remoteToDelete)
        assertEquals("and the series goes back without the EXDATE", listOf("RRULE:FREQ=WEEKLY"), upserts(plan).getValue(1).event.recurrence)
    }

    @Test
    fun `an all-day series' EXDATE is a date`() {
        val allDay = series.copy(startTime = null, endTime = null)
        val skip = exception(2, LocalDate.of(2026, 10, 3), skip = true).copy(startTime = null, endTime = null)
        val plan = planGooglePush(listOf(allDay, skip), since, zone)

        assertEquals(listOf("RRULE:FREQ=WEEKLY", "EXDATE;VALUE=DATE:20261003"), upserts(plan).getValue(1).event.recurrence)
    }

    /**
     * The pull half. A moved occurrence pushed as its own event comes back in the same pass,
     * newer (§9.5.1), as a plain Google event: stored wholesale it stopped being an occurrence of
     * its series, and the series showed the original date again beside it.
     */
    @Test
    fun `a pulled copy of a moved occurrence stays an occurrence of its series`() {
        val moved = exception(2, LocalDate.of(2026, 10, 3), skip = false, googleEventId = "g-moved")
        val remote = GoogleEvent(
            id = "g-moved", summary = "Yoga, later",
            start = GoogleEventDateTime(dateTime = "2026-10-02T11:00:00+02:00"), end = GoogleEventDateTime(dateTime = "2026-10-02T12:00:00+02:00"),
            updated = "2026-09-26T09:00:00Z",
        ).toEntry("g-moved")

        val stored = pulledOver(moved, remote)

        assertEquals("Yoga, later", stored.title)
        assertEquals(1L, stored.originalEntryId)
        assertEquals(LocalDate.of(2026, 10, 3), stored.originalOccurrenceDate)
        assertEquals(false, stored.isExceptionSkip)
    }

    /** Control: with nothing changed since the last push, nothing is sent. */
    @Test
    fun `an unchanged series is not pushed`() {
        assertTrue(planGooglePush(listOf(series), since, zone).isEmpty())
    }
}
