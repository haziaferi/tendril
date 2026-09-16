package com.tendril.app.domain.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 14h·2 — one relative time for the two places that say when something happened: the History
 * sheet's rows (which had this privately) and the phone's page card, whose *Page / Database /
 * Canvas* line said what the icon already said (`docs/critiques/pages-phone.md` #1). Minutes and
 * hours today, *yesterday*, days up to a week, then the date — the calendar's own boundaries,
 * not 24-hour arithmetic, so "yesterday" is yesterday.
 */
fun relativeTime(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val d = Duration.between(at, now)
    if (d.toMinutes() < 1) return "just now"
    if (d.toMinutes() < 60) return d.toMinutes().toString() + " min ago"
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val days = today.toEpochDay() - day.toEpochDay()
    return when {
        days == 0L -> d.toHours().toString() + " h ago"
        days == 1L -> "yesterday"
        days in 2..6 -> "$days days ago"
        else -> day.toString()
    }
}

/** *1 row* / *2 rows* — the Table's footer and its five conversion warnings (`pages-desktop.md` #10). */
fun rowsLabel(n: Int): String = if (n == 1) "1 row" else "$n rows"

