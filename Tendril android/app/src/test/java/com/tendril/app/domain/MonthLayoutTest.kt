package com.tendril.app.domain

import com.tendril.app.domain.plan.DotKind
import com.tendril.app.domain.plan.cellCapacity
import com.tendril.app.domain.plan.dayLabel
import com.tendril.app.domain.plan.monthDots
import com.tendril.app.domain.plan.monthGridDays
import com.tendril.app.domain.plan.monthRange
import com.tendril.app.domain.plan.visibleInCell
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The Month grid's arithmetic (L4): the rows a month spans, a cell's capacity, the +n slot, the 1st's label, the phone's dots. */
class MonthLayoutTest {
    @Test
    fun `the grid is exactly the weeks the month spans`() {
        val sep = monthGridDays(YearMonth.of(2026, 9))                 // Tue 1 … Wed 30 → Mon 31 Aug … Sun 4 Oct
        assertEquals(35, sep.size)
        assertEquals(LocalDate.of(2026, 8, 31), sep.first())
        assertEquals(LocalDate.of(2026, 10, 4), sep.last())
        val aug = monthGridDays(YearMonth.of(2026, 8))                 // Sat 1 … Mon 31 → six rows
        assertEquals(42, aug.size)
        assertEquals(LocalDate.of(2026, 7, 27), aug.first())
        val feb = monthGridDays(YearMonth.of(2027, 2))                 // Mon 1 … Sun 28 → four rows, no padding
        assertEquals(28, feb.size)
        assertEquals(LocalDate.of(2027, 2, 1), feb.first())
        assertEquals(LocalDate.of(2027, 2, 28), feb.last())
    }

    @Test
    fun `a Sunday-first grid shifts the edges`() {
        val sep = monthGridDays(YearMonth.of(2026, 9), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 8, 30), sep.first())
        assertEquals(LocalDate.of(2026, 10, 3), sep.last())
        assertEquals(LocalDate.of(2026, 8, 30) to LocalDate.of(2026, 10, 3), monthRange(YearMonth.of(2026, 9), DayOfWeek.SUNDAY))
    }

    @Test
    fun `capacity follows the cell's height`() {
        assertEquals(5, cellCapacity(153f, 22f, 24f, 4f))     // the user's window, five rows
        assertEquals(4, cellCapacity(128f, 22f, 24f, 4f))     // the mock's frame, or six rows
        assertEquals(1, cellCapacity(40f, 22f, 24f, 4f))      // never zero
    }

    @Test
    fun `the last slot goes to +n only when the day overflows`() {
        assertEquals(listOf(1, 2, 3, 4) to 0, visibleInCell(listOf(1, 2, 3, 4), 4))
        assertEquals(listOf(1, 2, 3) to 3, visibleInCell(listOf(1, 2, 3, 4, 5, 6), 4))
        assertEquals(emptyList<Int>() to 2, visibleInCell(listOf(1, 2), 1))
        assertEquals(listOf(1) to 0, visibleInCell(listOf(1), 0))
    }

    @Test
    fun `the first of a month carries its name`() {
        assertEquals("Sep 1", dayLabel(LocalDate.of(2026, 9, 1), Locale.ENGLISH))
        assertEquals("Oct 1", dayLabel(LocalDate.of(2026, 10, 1), Locale.ENGLISH))
        assertEquals("17", dayLabel(LocalDate.of(2026, 9, 17), Locale.ENGLISH))
    }

    @Test
    fun `dots cap at three by precedence`() {
        assertEquals(listOf(DotKind.TASK, DotKind.EVENT, DotKind.HABIT), monthDots(listOf(DotKind.DATABASE, DotKind.HABIT, DotKind.EVENT, DotKind.TASK, DotKind.TASK)))
        assertEquals(listOf(DotKind.EVENT, DotKind.DATABASE), monthDots(listOf(DotKind.DATABASE, DotKind.EVENT)))
        assertEquals(emptyList<DotKind>(), monthDots(emptyList()))
    }
}
