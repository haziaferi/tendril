package com.tendril.app.data.habit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.IntervalUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * §4 lists Habit's `frequency` field with no further shape specified anywhere in the spec.
 * Resolved here (this pass) as an (count, unit) interval — reusing the exact representation
 * §5.2.2 already committed to for the database-binding `Interval` property type, rather than
 * inventing a third shape alongside Task's RRULE-based `Fixed` and TASK's `Elastic`. Chosen
 * over a days-of-week model (real habit-tracker precedent, e.g. Streaks/Loop) because: (a)
 * it costs nothing to change later — Room is in pre-v1 destructive-migration mode (§9.10),
 * so this is not a locked-in schema commitment; (b) it matches the spec's own consistent
 * bias toward the smallest representation that covers the stated reference case (daily
 * bed-making) rather than speculative generality; (c) it's a pure UI/data-shape choice, not
 * a product-taste one — nothing else in the spec depends on which cadence model Habits use.
 */
data class HabitFrequency(val count: Int, val unit: IntervalUnit)

/**
 * §3.3 — a habit's optional duration, as it reads next to its time ("9:00 · 20m").
 *
 * Kept as a function rather than inlined at the call site because [Habit.duration] had no
 * consumer at all until now: it round-tripped through the Room converter and the snapshot
 * mappers and was rendered nowhere, which is how a field stays plumbed and dead. One formatter
 * means every surface that grows a habit row later shows the same thing.
 */
fun formatHabitDuration(duration: java.time.Duration): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/** §6.1 — streak-based, missing an instance does not create backlog (the defining test that
 * separates a Habit from a recurring Task). Structurally separate from [com.tendril.app.data.entry.Entry] (§3.3). */
@Entity(tableName = "habits", indices = [Index("uid", unique = true)])
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4 snapshot merge — see [com.tendril.app.data.entry.Entry.uid]. */
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val time: LocalTime? = null,
    val duration: java.time.Duration? = null,
    val frequency: HabitFrequency,
    val streak: Int = 0,
    /** Date this habit's current streak run last extended — drives whether today still
     * needs a check-in vs. the streak already covers it, and whether a missed period
     * silently resets [streak] to 0 without ever showing "backlog" (§6.1). */
    val lastCompletedDate: LocalDate? = null,
    /** [streak]/[lastCompletedDate] as they stood immediately before the most recent
     * check-in — the only state undo-check-in (§8.1.1) needs to revert exactly, since a
     * weekly+ habit's grace period means "just subtract a day" can't reconstruct what
     * lastCompletedDate actually was. Cleared back to defaults once consumed by an undo. */
    val previousStreak: Int = 0,
    val previousCompletedDate: LocalDate? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
