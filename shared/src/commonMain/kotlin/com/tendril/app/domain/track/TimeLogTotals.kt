package com.tendril.app.domain.track

import com.tendril.app.data.track.TimeLog
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * §0.6.5 — minutes logged inside `[from, to)`, over live logs only. Each log is clipped to the
 * window, and an open one counts up to [now], so a running timer's minutes are already in the
 * total the moment a screen asks. Pure, so the habit sheet's "N min this month" and step 7d's
 * "logged today" share it.
 */
fun loggedMinutes(logs: List<TimeLog>, from: Instant, to: Instant, now: Instant): Int {
    var total = Duration.ZERO
    for (log in logs) {
        if (log.deletedAt != null) continue
        val start = maxOf(log.startedAt, from)
        val end = minOf(log.endedAt ?: now, to)
        if (end > start) total += Duration.between(start, end)
    }
    return total.toMinutes().toInt()
}

/** The calendar month's total in the person's zone — what the habit detail reads. */
fun loggedInMonth(logs: List<TimeLog>, month: YearMonth, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int =
    loggedMinutes(
        logs,
        from = month.atDay(1).atStartOfDay(zone).toInstant(),
        to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        now = now,
    )

/** "1h 05m", "45m", "0m" — one spelling for every screen that shows a total. */
fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m" else "${minutes}m"
