package com.tendril.app.domain.checkin

import com.tendril.app.data.checkin.CheckIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * §0.10 item 4 — the two scales a check-in answers with, as words (the user, 2026-09-19: no
 * emoji). Five steps each, Daylio's count; the words are the app's, plain and unranked — a
 * check-in is *how it was*, not a score, so nothing here averages, counts days or draws a curve
 * (B§10.3). [Mood.hex] is the stored hue the month's discs take, solved by the register as a
 * label's is (`labelColours`): slate · blue · mauve · teal · green-gold — no red, by the rule.
 */
enum class Mood(val level: Int, val label: String, val hex: String) {
    AWFUL(1, "Awful", "#5A6E96"),
    BAD(2, "Bad", "#3F7EA6"),
    OKAY(3, "Okay", "#7C6BA3"),
    GOOD(4, "Good", "#2E8A76"),
    GREAT(5, "Great", "#6E8A2C");

    companion object {
        fun fromLevel(level: Int): Mood? = entries.firstOrNull { it.level == level }
    }
}

enum class Energy(val level: Int, val label: String) {
    DRAINED(1, "Drained"),
    LOW(2, "Low"),
    STEADY(3, "Steady"),
    HIGH(4, "High"),
    FULL(5, "Full");

    companion object {
        fun fromLevel(level: Int): Energy? = entries.firstOrNull { it.level == level }
    }
}

private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** What one row says: *Good* · *Energy high*. Null for a row with neither (a decode this build
 * cannot read keeps the row but has no words for it). */
fun checkInLabel(row: CheckIn): String? =
    row.mood?.let { m -> Mood.fromLevel(m)?.label }
        ?: row.energy?.let { e -> Energy.fromLevel(e)?.let { "Energy " + it.label.lowercase() } }

/** The latest live row of each scale on a day, as the row's chosen chips: (mood level, energy level). */
fun latestOfDay(rows: List<CheckIn>): Pair<Int?, Int?> {
    val live = rows.filter { it.deletedAt == null }.sortedBy { it.at }
    return live.lastOrNull { it.mood != null }?.mood to live.lastOrNull { it.energy != null }?.energy
}

/**
 * The line under the chips — *Good at 14:32 · energy high at 14:35* — from the latest of each
 * scale; empty when nothing was logged. The mood first when both, whatever the order of the taps.
 */
fun dayLine(rows: List<CheckIn>, zone: ZoneId = ZoneId.systemDefault()): String {
    val live = rows.filter { it.deletedAt == null }.sortedBy { it.at }
    val mood = live.lastOrNull { it.mood != null }
    val energy = live.lastOrNull { it.energy != null }
    val parts = listOfNotNull(
        mood?.let { r -> Mood.fromLevel(r.mood!!)?.let { "${it.label} at ${r.at.atZone(zone).format(hourMinute)}" } },
        energy?.let { r -> Energy.fromLevel(r.energy!!)?.let { "energy ${it.label.lowercase()} at ${r.at.atZone(zone).format(hourMinute)}" } },
    )
    return parts.joinToString(" · ")
}

/** The month's discs: a day → its latest mood's level; a day with only energy logged → 0 (a ring). */
fun monthOfMoods(rows: List<CheckIn>): Map<LocalDate, Int> {
    val out = HashMap<LocalDate, Int>()
    rows.filter { it.deletedAt == null }.sortedBy { it.at }.forEach { r ->
        when {
            r.mood != null -> out[r.date] = r.mood
            r.energy != null -> out.putIfAbsent(r.date, 0)
        }
    }
    return out
}

/** The row is offered on a Journal day that is today or past — never a day that has not happened. */
fun checkInOffered(day: LocalDate, today: LocalDate): Boolean = !day.isAfter(today)
