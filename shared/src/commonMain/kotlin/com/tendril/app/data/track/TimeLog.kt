package com.tendril.app.data.track

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.habit.Habit
import java.time.Instant
import java.util.UUID

/**
 * §0.6.5 / §0.8 step 7c — one stretch of tracked time on a task or a habit: the *track* half
 * of "estimate → plan → track → compare". Written by [com.tendril.app.domain.track.TimeTracker],
 * the one funnel every start and stop goes through, so the row buttons, the running strip and
 * the phone's notification action feed it alike.
 *
 * **The row is the timer.** A log is inserted *open* ([endedAt] null) when the timer starts and
 * closed when it stops; nothing else remembers that a timer runs. That is what makes process
 * death harmless on the phone (B§6 #9's open question): the notification that shows the
 * elapsed time is a chronometer SystemUI ticks on its own, and its Stop action writes the
 * [endedAt] this row is missing — no foreground service holds anything in memory.
 *
 * **Both edited and tombstoned — the one table that is.** A reminder is inserted once and
 * deleted once; a completion is never touched again; a log is inserted open, closed later, and
 * can be deleted. So §9.4's merge combines the two rules the other families use alone: "deleted
 * on any device wins", else last-write-wins on [updatedAt]. See
 * `SnapshotSyncOrchestrator.mergeTimeLogContent`.
 *
 * Exactly one of [entryId] and [habitId] is set. Room cannot express that as a CHECK, so
 * [com.tendril.app.domain.track.TrackTarget] is the only way a log is made.
 */
@Entity(
    tableName = "time_logs",
    foreignKeys = [
        ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("entryId"), Index("habitId")],
)
data class TimeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4, on the same terms as [Entry.uid]. */
    val uid: String = UUID.randomUUID().toString(),
    val entryId: Long? = null,
    val habitId: Long? = null,
    val startedAt: Instant,
    /** Null while the timer runs. */
    val endedAt: Instant? = null,
    /** Soft-delete, never cleared — the tombstone §9.4's merge lets win. */
    val deletedAt: Instant? = null,
    /** Bumped on every write, so two devices' edits of the same log compare. */
    val updatedAt: Instant,
) {
    val isRunning: Boolean get() = endedAt == null && deletedAt == null
}
