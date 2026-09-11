package com.tendril.app.domain

import com.tendril.app.data.habit.HabitCompletion
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * §0.6.6 / §0.5.2 — what a habit's log is allowed to say. Presence, never absence: there is no
 * count of misses here, no gap, no ratio, and no field a screen could draw a chain from. A
 * screen that wants to show more than this has to add a field here first, which is the point
 * of routing every habit summary through one place.
 */
data class HabitPresence(
    /** Distinct days checked in this month. "Four times this month." */
    val timesThisMonth: Int,
    /** The most recent day checked in, or null when the habit has no memory yet. "Last: Tuesday." */
    val lastDate: LocalDate?,
    /** When the person usually does it, or null until there is enough to say. "Usually mornings." */
    val usualTime: TimeOfDay?,
    /** The days of [month] with a live check-in — a set, so a month view can colour a day or
     * leave it *empty*, never mark it missed. */
    val daysThisMonth: Set<LocalDate>,
    val month: YearMonth,
)

enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

/** Local-time buckets, boundaries chosen where ordinary speech puts them. */
fun timeOfDayOf(hour: Int): TimeOfDay = when (hour) {
    in 5..11 -> TimeOfDay.MORNING
    in 12..16 -> TimeOfDay.AFTERNOON
    in 17..21 -> TimeOfDay.EVENING
    else -> TimeOfDay.NIGHT
}

/**
 * Summarises live check-ins. Tombstoned rows are ignored here rather than at the DAO so that a
 * caller holding `getAll()` for sync reasons gets the same answer as one holding the filtered
 * flow. "Usually" needs at least three check-ins and a bucket holding more than half of them;
 * below that the honest answer is nothing, and nothing is what is shown.
 */
fun habitPresenceOf(
    completions: List<HabitCompletion>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): HabitPresence {
    val live = completions.filter { it.deletedAt == null }
    val month = YearMonth.from(today)
    val daysThisMonth = live.map { it.date }.filter { YearMonth.from(it) == month }.toSet()
    val buckets = live.groupingBy { timeOfDayOf(it.checkedAt.atZone(zone).hour) }.eachCount()
    val usual = buckets.maxByOrNull { it.value }
        ?.takeIf { live.size >= 3 && it.value * 2 > live.size }
        ?.key
    return HabitPresence(
        timesThisMonth = daysThisMonth.size,
        lastDate = live.maxOfOrNull { it.date },
        usualTime = usual,
        daysThisMonth = daysThisMonth,
        month = month,
    )
}
