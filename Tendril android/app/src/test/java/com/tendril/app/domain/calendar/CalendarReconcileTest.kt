package com.tendril.app.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * §9.12's read-back table, one test per row, plus the cases the table's rows imply: a series'
 * exceptions are items of their own, and a lost link table (a reinstall) must not duplicate.
 *
 * R is Tendril's side, P the system calendar's, L the fingerprint both last agreed on.
 */
class CalendarReconcileTest {

    private val series = CalendarItemKey("uid-1", occurrence = null)
    private val moved = CalendarItemKey("uid-1", occurrence = LocalDate.of(2026, 10, 8))

    private fun fields(title: String = "Yoga", day: Int = 1, skip: Boolean = false) = CalendarItemFields(
        title = title, startDate = LocalDate.of(2026, 10, day), startTime = LocalTime.of(9, 0),
        endDate = LocalDate.of(2026, 10, day), endTime = LocalTime.of(10, 0), rrule = null, isSkip = skip,
    )

    private fun plan(r: Map<CalendarItemKey, CalendarItemFields>, p: Map<CalendarItemKey, CalendarItemFields>, l: Map<CalendarItemKey, String>) =
        planCalendarReconcile(tendril = r, provider = p, links = l).associateBy { it.key }

    @Test
    fun `nothing changed on either side does nothing`() {
        val f = fields()
        assertTrue(plan(mapOf(series to f), mapOf(series to f), mapOf(series to f.fingerprint())).isEmpty())
    }

    @Test
    fun `an edit made elsewhere is taken into Tendril`() {
        val before = fields(); val after = fields(title = "Yoga, later")
        val step = plan(mapOf(series to before), mapOf(series to after), mapOf(series to before.fingerprint()))[series]
        assertEquals(ReconcileStep.TakeFromProvider(series, after, conflict = false), step)
    }

    @Test
    fun `an edit made in Tendril is written to the calendar`() {
        val before = fields(); val after = fields(title = "Yoga, later")
        val step = plan(mapOf(series to after), mapOf(series to before), mapOf(series to before.fingerprint()))[series]
        assertEquals(ReconcileStep.WriteToProvider(series, after), step)
    }

    @Test
    fun `both sides changed - the calendar wins, and says it was a conflict`() {
        val before = fields(); val ours = fields(title = "Ours"); val theirs = fields(title = "Theirs")
        val step = plan(mapOf(series to ours), mapOf(series to theirs), mapOf(series to before.fingerprint()))[series]
        assertEquals(ReconcileStep.TakeFromProvider(series, theirs, conflict = true), step)
    }

    @Test
    fun `both sides changed to the same thing is agreement, only relinked`() {
        val before = fields(); val both = fields(title = "Same")
        val step = plan(mapOf(series to both), mapOf(series to both), mapOf(series to before.fingerprint()))[series]
        assertEquals(ReconcileStep.Relink(series, both.fingerprint()), step)
    }

    @Test
    fun `an event new in the calendar is created in Tendril`() {
        val f = fields()
        assertEquals(ReconcileStep.TakeFromProvider(series, f, conflict = false), plan(emptyMap(), mapOf(series to f), emptyMap())[series])
    }

    @Test
    fun `an event new in Tendril is inserted into the calendar`() {
        val f = fields()
        assertEquals(ReconcileStep.WriteToProvider(series, f), plan(mapOf(series to f), emptyMap(), emptyMap())[series])
    }

    @Test
    fun `an event gone from the calendar moves to Tendril's Trash`() {
        val f = fields()
        assertEquals(ReconcileStep.TrashInTendril(series), plan(mapOf(series to f), emptyMap(), mapOf(series to f.fingerprint()))[series])
    }

    @Test
    fun `an event gone from Tendril is deleted from the calendar`() {
        val f = fields()
        assertEquals(ReconcileStep.DeleteFromProvider(series), plan(emptyMap(), mapOf(series to f), mapOf(series to f.fingerprint()))[series])
    }

    /** Deleted in Tendril, edited elsewhere: the edit is newer information than the delete. */
    @Test
    fun `an event deleted in Tendril but edited elsewhere comes back`() {
        val before = fields(); val edited = fields(title = "Moved room")
        assertEquals(
            ReconcileStep.TakeFromProvider(series, edited, conflict = true),
            plan(emptyMap(), mapOf(series to edited), mapOf(series to before.fingerprint()))[series],
        )
    }

    /** A lost link table (reinstall, cleared data) sees the same event on both sides with no L. */
    @Test
    fun `the same event on both sides with no link is linked, not duplicated`() {
        val f = fields()
        assertEquals(ReconcileStep.Relink(series, f.fingerprint()), plan(mapOf(series to f), mapOf(series to f), emptyMap())[series])
    }

    @Test
    fun `different on both sides with no link - the calendar wins`() {
        val ours = fields(title = "Ours"); val theirs = fields(title = "Theirs")
        assertEquals(ReconcileStep.TakeFromProvider(series, theirs, conflict = true), plan(mapOf(series to ours), mapOf(series to theirs), emptyMap())[series])
    }

    @Test
    fun `gone from both sides forgets the link`() {
        assertEquals(ReconcileStep.Forget(series), plan(emptyMap(), emptyMap(), mapOf(series to fields().fingerprint()))[series])
    }

    @Test
    fun `a moved occurrence reconciles on its own, beside its series`() {
        val s = fields(); val m = fields(day = 9)
        val steps = plan(mapOf(series to s), mapOf(series to s, moved to m), mapOf(series to s.fingerprint()))
        assertEquals(setOf(moved), steps.keys)
        assertEquals(ReconcileStep.TakeFromProvider(moved, m, conflict = false), steps[moved])
    }

    @Test
    fun `the fingerprint sees every mapped field, and a skip`() {
        val base = fields()
        listOf(
            base.copy(title = "x"), base.copy(startDate = base.startDate!!.plusDays(1)), base.copy(startTime = null),
            base.copy(endDate = null), base.copy(endTime = LocalTime.of(11, 0)), base.copy(rrule = "FREQ=WEEKLY"), base.copy(isSkip = true),
        ).forEach { assertNotEquals(it.toString(), base.fingerprint(), it.fingerprint()) }
        assertEquals(base.fingerprint(), base.copy().fingerprint())
    }
}
