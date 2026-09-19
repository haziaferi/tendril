package com.tendril.app.domain

import com.tendril.app.data.checkin.CheckIn
import com.tendril.app.domain.checkin.checkInLabel
import com.tendril.app.domain.checkin.checkInOffered
import com.tendril.app.domain.checkin.dayLine
import com.tendril.app.domain.checkin.latestOfDay
import com.tendril.app.domain.checkin.monthOfMoods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** §0.10 item 4 — the words a check-in gets, the latest of each scale, the month's discs. */
class CheckInsTest {

    private val day = LocalDate.of(2026, 9, 19)
    private fun at(h: Int, m: Int) = day.atTime(h, m).toInstant(ZoneOffset.UTC)
    private fun row(h: Int, m: Int, mood: Int? = null, energy: Int? = null, deleted: Boolean = false, on: LocalDate = day) =
        CheckIn(date = on, at = at(h, m), mood = mood, energy = energy, deletedAt = if (deleted) at(h, m + 1) else null)

    @Test
    fun `the line names the latest of each scale, mood first whatever the tap order`() {
        val rows = listOf(row(9, 0, energy = 2), row(14, 32, mood = 4), row(14, 35, energy = 4), row(8, 0, mood = 2))
        assertEquals("Good at 14:32 · energy high at 14:35", dayLine(rows, ZoneOffset.UTC))
        assertEquals(4 to 4, latestOfDay(rows))
    }

    @Test
    fun `an undone row does not count, and nothing logged is an empty line`() {
        assertEquals("", dayLine(emptyList()))
        val rows = listOf(row(14, 32, mood = 4, deleted = true), row(15, 0, energy = 3))
        assertEquals("energy steady at 15:00", dayLine(rows, ZoneOffset.UTC))
        assertEquals(null to 3, latestOfDay(rows))
    }

    @Test
    fun `a row says which scale, and one with neither has no words`() {
        assertEquals("Good", checkInLabel(row(1, 0, mood = 4)))
        assertEquals("Energy drained", checkInLabel(row(1, 0, energy = 1)))
        assertNull(checkInLabel(row(1, 0)))
        assertNull(checkInLabel(row(1, 0, mood = 9)))
    }

    @Test
    fun `the month colours a day by its latest mood, rings a day with energy alone, and skips the rest`() {
        val other = day.minusDays(1)
        val energyOnly = day.minusDays(2)
        val rows = listOf(
            row(9, 0, mood = 2), row(21, 0, mood = 4),
            row(9, 0, mood = 5, on = other), row(10, 0, energy = 3, on = other),
            row(9, 0, energy = 1, on = energyOnly),
            row(9, 0, mood = 1, on = day.minusDays(3), deleted = true),
        )
        val month = monthOfMoods(rows)
        assertEquals(4, month[day])
        assertEquals(5, month[other])
        assertEquals(0, month[energyOnly])
        assertEquals(3, month.size)
    }

    @Test
    fun `the row is offered today and on a past day, never a future one`() {
        assertTrue(checkInOffered(day, day))
        assertTrue(checkInOffered(day.minusDays(30), day))
        assertFalse(checkInOffered(day.plusDays(1), day))
    }
}
