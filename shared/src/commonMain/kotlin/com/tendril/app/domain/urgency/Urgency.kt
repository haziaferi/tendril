package com.tendril.app.domain.urgency

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * B§13.8.1 (14g·3) — *urgency is the colour*. A task's level is the greater of what the person
 * set ([Entry.importance]) and the pressure its Deadline puts on it today; the When never
 * counts (§0.5.2 — a plan that moves without guilt). A finished task has none: done is dim and
 * struck, and spends no colour.
 */
enum class Urgency(val level: Int, val label: String) {
    NONE(0, "None"),
    LOW(1, "Low"),
    MID(2, "Mid"),
    HIGH(3, "High"),
    URGENT(4, "Urgent");

    companion object {
        fun fromLevel(level: Int): Urgency = entries.first { it.level == level.coerceIn(0, 4) }
    }
}

/** Within 7 days → low, 3 → mid, today or tomorrow → high, past → urgent (decided 2026-09-16). */
fun timePressure(dueDate: LocalDate?, today: LocalDate): Urgency {
    if (dueDate == null) return Urgency.NONE
    val days = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        days < 0 -> Urgency.URGENT
        days <= 1 -> Urgency.HIGH
        days <= 3 -> Urgency.MID
        days <= 7 -> Urgency.LOW
        else -> Urgency.NONE
    }
}

fun urgencyOf(entry: Entry, today: LocalDate): Urgency {
    if (entry.status == EntryStatus.DONE || entry.status == EntryStatus.SKIPPED) return Urgency.NONE
    val set = Urgency.fromLevel(entry.importance)
    val pressure = timePressure(entry.dueDate, today)
    return if (pressure.level > set.level) pressure else set
}
