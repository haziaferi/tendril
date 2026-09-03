package com.tendril.app.data.reminder

import com.tendril.app.data.entry.IntervalUnit
import java.time.Duration

/** §3.2/§5.4 — presets plus a custom (number, unit) fallback, no cap on how many stack. */
sealed class ReminderOffset {
    enum class Preset(val duration: Duration) {
        ONE_HOUR(Duration.ofHours(1)),
        TWO_HOURS(Duration.ofHours(2)),
        FOUR_HOURS(Duration.ofHours(4)),
        EIGHT_HOURS(Duration.ofHours(8)),
        ONE_DAY(Duration.ofDays(1)),
        TWO_DAYS(Duration.ofDays(2)),
        ONE_WEEK(Duration.ofDays(7)),
    }

    data class FromPreset(val preset: Preset) : ReminderOffset()
    data class Custom(val count: Int, val unit: IntervalUnit) : ReminderOffset()
}

fun ReminderOffset.toDuration(): Duration = when (this) {
    is ReminderOffset.FromPreset -> preset.duration
    is ReminderOffset.Custom -> when (unit) {
        IntervalUnit.DAY -> Duration.ofDays(count.toLong())
        IntervalUnit.WEEK -> Duration.ofDays(count.toLong() * 7)
        IntervalUnit.MONTH -> Duration.ofDays(count.toLong() * 30) // approximate — reminders are offsets, not calendar-month-exact
    }
}

/** Presets offered when an all-day reminder needs a concrete time-of-day to fire at (§4). */
enum class AllDayAnchorPreset(val hour: Int, val minute: Int = 0) {
    EIGHT_AM(8), TEN_AM(10), NOON(12), FOUR_PM(16),
}
