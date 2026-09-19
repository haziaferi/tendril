package com.tendril.app.data.checkin

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * §0.10 item 4 (v22) — one tap on the Journal's day page: *how are you?* A note about the
 * person, not a habit (B§10.3): no cadence, nothing due, nothing missed. A row carries **one**
 * scale — [mood] or [energy], the other null — because either is a valid check-in on its own
 * and the two are tapped separately; several rows a day are the day as it went, each with its
 * moment ([at]). There is no note column on purpose: the Journal page under the row is the words.
 *
 * Tombstoned like [com.tendril.app.data.habit.HabitCompletion], for the same reason: a tap can
 * be undone, and "deleted on any device wins" is the one merge rule that converges with nothing
 * to compare (§9.4). A changed mind is a new row, never an edit.
 */
@Entity(
    tableName = "check_ins",
    indices = [Index("uid", unique = true), Index("date")],
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4. */
    val uid: String = UUID.randomUUID().toString(),
    /** The Journal day the check-in belongs to — the page it was tapped on, which may be a past day. */
    val date: LocalDate,
    /** The moment of the tap. */
    val at: Instant,
    /** 1 awful … 5 great, or null when this row is an energy check-in. */
    val mood: Int? = null,
    /** 1 drained … 5 full, or null when this row is a mood check-in. */
    val energy: Int? = null,
    /** Set by undo, never cleared. */
    val deletedAt: Instant? = null,
)
