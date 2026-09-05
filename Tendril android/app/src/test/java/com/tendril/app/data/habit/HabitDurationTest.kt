package com.tendril.app.data.habit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * audit §3 — `Habit.duration` had no producer and no consumer.
 *
 * It round-tripped through the Room converter (`durationToSeconds`/`secondsToDuration`) and the
 * snapshot mappers (`durationSeconds`) and was rendered nowhere, which is how a field stays
 * fully plumbed and entirely dead. §3.3 specifies it — "optional time + duration (not
 * required)" — and §5.3 draws habits with one on the calendar, so the answer was to wire it, the
 * same way its sibling `Habit.time` was fixed, rather than to remove it.
 *
 * The formatting is the part worth pinning: everything else about the field is a text box and a
 * row of text.
 */
class HabitDurationTest {

    @Test
    fun `minutes under an hour read as minutes`() {
        assertEquals("20m", formatHabitDuration(Duration.ofMinutes(20)))
        assertEquals("5m", formatHabitDuration(Duration.ofMinutes(5)))
        assertEquals("59m", formatHabitDuration(Duration.ofMinutes(59)))
    }

    @Test
    fun `a whole hour drops the minutes`() {
        assertEquals("1h", formatHabitDuration(Duration.ofHours(1)))
        assertEquals("2h", formatHabitDuration(Duration.ofHours(2)))
    }

    @Test
    fun `an hour with minutes shows both`() {
        assertEquals("1h 30m", formatHabitDuration(Duration.ofMinutes(90)))
        assertEquals("2h 5m", formatHabitDuration(Duration.ofMinutes(125)))
    }

    @Test
    fun `seconds below a minute round down rather than vanishing into an empty string`() {
        // Nothing in the UI can produce this — the box takes whole minutes — but the snapshot
        // mappers accept any Long of seconds from another device's build, so it has to render.
        assertEquals("0m", formatHabitDuration(Duration.ofSeconds(30)))
    }

    @Test
    fun `zero formats rather than throwing`() {
        assertEquals("0m", formatHabitDuration(Duration.ZERO))
    }

    @Test
    fun `a long duration stays in hours rather than rolling into days`() {
        // Days would be a fourth unit for a field that means "how long this sitting takes".
        assertEquals("25h", formatHabitDuration(Duration.ofHours(25)))
    }
}
