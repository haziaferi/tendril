package com.tendril.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** L5 — the bar's range title and its ‹ › step per view. */
class CalendarTitlesTest {
    private val thu = LocalDate.of(2026, 9, 17)

    @Test
    fun `each view names its range`() {
        assertEquals("Thursday 17 September", rangeTitle(CalendarView.DAY, thu, Locale.ENGLISH))
        assertEquals("14 – 20 September 2026", rangeTitle(CalendarView.WEEK, thu, Locale.ENGLISH))
        assertEquals("September 2026", rangeTitle(CalendarView.MONTH, thu, Locale.ENGLISH))
        assertEquals("Next 30 days", rangeTitle(CalendarView.AGENDA, thu, Locale.ENGLISH, agendaDays = 30))
    }

    @Test
    fun `a week across a month boundary names both months`() {
        assertEquals("28 Sep – 4 Oct 2026", rangeTitle(CalendarView.WEEK, LocalDate.of(2026, 9, 30), Locale.ENGLISH))
    }

    @Test
    fun `the arrows step by the view's unit and the Agenda stays`() {
        assertEquals(LocalDate.of(2026, 9, 18), shiftedAnchor(CalendarView.DAY, thu, 1))
        assertEquals(LocalDate.of(2026, 9, 10), shiftedAnchor(CalendarView.WEEK, thu, -1))
        assertEquals(LocalDate.of(2026, 10, 17), shiftedAnchor(CalendarView.MONTH, thu, 1))
        assertEquals(thu, shiftedAnchor(CalendarView.AGENDA, thu, 1))
        assertEquals(false, CalendarView.AGENDA.steps())
    }

    @Test
    fun `the week starts on Monday`() {
        assertEquals(LocalDate.of(2026, 9, 14), weekStartOf(thu))
        assertEquals(LocalDate.of(2026, 9, 14), weekStartOf(LocalDate.of(2026, 9, 20)))
    }
}
