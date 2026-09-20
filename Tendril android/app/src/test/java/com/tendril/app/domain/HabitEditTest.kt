package com.tendril.app.domain

import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.edited
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** S10 (2026-09-20): a habit edited after creation — `Habit.edited`. */
class HabitEditTest {
    private val t0 = Instant.EPOCH
    private val habit = Habit(
        id = 7, uid = "u7", title = "Water", frequency = HabitFrequency(1, IntervalUnit.DAY), time = LocalTime.of(9, 0),
        duration = Duration.ofMinutes(10), streak = 4, lastCompletedDate = LocalDate.of(2026, 9, 19), previousStreak = 3,
        unit = "cups", amountPerCheckIn = 1.0, dailyAmount = 8.0, createdAt = t0, updatedAt = t0,
    )

    @Test
    fun `the fields change, the identity and the record stay`() {
        val e = habit.edited("Drink water", HabitFrequency(2, IntervalUnit.WEEK), LocalTime.of(18, 30), Duration.ofMinutes(5), "glasses", 2.0, 6.0, Instant.ofEpochSecond(99))
        assertEquals(7L, e.id); assertEquals("u7", e.uid); assertEquals(4, e.streak); assertEquals(LocalDate.of(2026, 9, 19), e.lastCompletedDate); assertEquals(3, e.previousStreak)
        assertEquals("Drink water", e.title); assertEquals(HabitFrequency(2, IntervalUnit.WEEK), e.frequency); assertEquals(LocalTime.of(18, 30), e.time)
        assertEquals(Duration.ofMinutes(5), e.duration); assertEquals("glasses", e.unit); assertEquals(2.0, e.amountPerCheckIn!!, 0.0); assertEquals(6.0, e.dailyAmount!!, 0.0)
        assertEquals(Instant.ofEpochSecond(99), e.updatedAt); assertEquals(t0, e.createdAt)
    }

    @Test
    fun `a blank title keeps the old, no time drops the duration, not counting drops the amounts`() {
        val e = habit.edited("  ", habit.frequency, null, Duration.ofMinutes(5), "cups", null, 8.0, t0)
        assertEquals("Water", e.title); assertNull(e.time); assertNull(e.duration); assertNull(e.unit); assertNull(e.amountPerCheckIn); assertNull(e.dailyAmount)
    }
}
