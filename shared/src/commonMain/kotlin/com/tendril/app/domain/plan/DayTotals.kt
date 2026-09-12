package com.tendril.app.domain.plan

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.track.formatMinutes
import com.tendril.app.domain.track.loggedMinutes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * §0.6.5 / §0.8 step 7d — *compare*, the last of "estimate → plan → track → compare". Two
 * numbers next to each other, never a score, a colour or a percentage (§0.5.2): what the day
 * was meant to hold, and what it actually took so far. Pure, so the Day header, the rows and
 * Plan mode agree by construction.
 */

/**
 * What the day holds: every timeline block's minutes (an event's span, a task's estimate or the
 * default, a habit's duration) **plus** the estimates of the day's untimed tasks that have one —
 * a task on today with "~45m" is planned for today whether or not it has an hour yet. A timed
 * task is already a block, so [untimedTasks] must be the untimed ones only; the caller filters,
 * and [plannedMinutes] ignores anything with a time as a belt.
 */
fun plannedMinutes(blocks: List<TimelineBlock>, untimedTasks: List<Entry>): Int =
    blocks.sumOf { it.minutes } +
        untimedTasks.filter { it.kind == EntryKind.TASK && it.startTime == null }
            .sumOf { it.estimate?.toMinutes()?.toInt() ?: 0 }

/** Minutes logged per entry, an open log counted to [now]. Only entries with something logged
 * appear, so a row can ask `map[id]` and say nothing on null. */
fun loggedByEntry(logs: List<TimeLog>, now: Instant): Map<Long, Int> =
    logs.filter { it.entryId != null }.groupBy { it.entryId!! }
        .mapValues { (_, l) -> loggedMinutes(l, Instant.MIN, Instant.MAX, now) }
        .filterValues { it > 0 }

/** [loggedByEntry], for habits. */
fun loggedByHabit(logs: List<TimeLog>, now: Instant): Map<Long, Int> =
    logs.filter { it.habitId != null }.groupBy { it.habitId!! }
        .mapValues { (_, l) -> loggedMinutes(l, Instant.MIN, Instant.MAX, now) }
        .filterValues { it > 0 }

/** One logged stretch as minutes of the day, for Plan mode's wash behind the planned blocks. */
data class LoggedSpan(val key: String, val startMinute: Int, val endMinute: Int)

/**
 * The day's logs as spans, clipped to the day — a log that crosses midnight is the day it
 * began, cut at 24:00 (the same rule [com.tendril.app.data.track.TimeLogDao.observeBetween]
 * selects by). An open log ends at [now], so the wash grows with the now line.
 */
fun loggedSpans(logs: List<TimeLog>, day: LocalDate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<LoggedSpan> {
    val dayStart = day.atStartOfDay(zone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
    return logs.filter { it.deletedAt == null }.mapNotNull { log ->
        val start = maxOf(log.startedAt, dayStart)
        val end = minOf(log.endedAt ?: now, dayEnd)
        if (end <= start) return@mapNotNull null
        LoggedSpan(
            key = log.uid,
            startMinute = (Duration.between(dayStart, start).toMinutes()).toInt(),
            endMinute = (Duration.between(dayStart, end).toMinutes()).toInt().coerceAtLeast(Duration.between(dayStart, start).toMinutes().toInt() + 1),
        )
    }
}

/** The mean length of a closed session, or null until there are two to average — "about N min
 * each" on the habit detail. Open logs and tombstones are left out: neither is a session yet. */
fun minutesPerSession(logs: List<TimeLog>): Int? {
    val closed = logs.filter { it.deletedAt == null && it.endedAt != null }
    if (closed.size < 2) return null
    val total = closed.sumOf { Duration.between(it.startedAt, it.endedAt).toMinutes() }
    return (total / closed.size).toInt().takeIf { it > 0 }
}

/** The row's last segment: "20m of ~45m" beside an estimate, "20m logged" without one. */
fun loggedSegment(loggedMinutes: Int, estimate: Duration?): String? {
    if (loggedMinutes <= 0) return null
    val est = estimate?.toMinutes()?.toInt()
    return if (est != null && est > 0) "${formatMinutes(loggedMinutes)} of ~${formatMinutes(est)}" else "${formatMinutes(loggedMinutes)} logged"
}
