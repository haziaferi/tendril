package com.tendril.app.data.habit

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * §0.6.6 — one row per habit check-in, the memory a [Habit] never had. Before v10 a habit knew
 * only its `streak` and `lastCompletedDate`, which can answer "did I do it today?" and nothing
 * older; §0.5.2's presence view — "four times this month", "usually mornings", "last: Tuesday" —
 * needs the Tuesday. Written by [com.tendril.app.domain.CheckInHabitUseCase], the one funnel
 * every check-in already went through, so the widget and the Habits tab feed it alike.
 *
 * **Tombstoned, not append-only — the opposite of [com.tendril.app.data.completion.EntryCompletion].**
 * A task resolution is a fact that happened; a habit check-in is a tap that can be undone (the
 * widget's own undo, §8.1.1). Undo sets [deletedAt]; a later check-in on the same day inserts a
 * *fresh* row rather than reviving the old one, so that §9.4's merge can keep the one rule that
 * converges regardless of arrival order — "deleted on any device wins" — exactly as
 * [com.tendril.app.data.reminder.Reminder] does. Two devices checking the same habit in on the
 * same day while offline therefore produce two live rows for one date, which is harmless: every
 * read here asks "is there a live row for this date", never "how many".
 *
 * There is deliberately **no `value` column** yet. Whether habits become measurable ("2 L",
 * "5 km") is §0.10 item 3, open; a nullable column later is one `ALTER`, and adding it now would
 * decide the question by accident.
 */
@Entity(
    tableName = "habit_completions",
    foreignKeys = [
        ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("habitId"), Index("habitId", "date")],
)
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4, on the same terms as [Habit.uid]. */
    val uid: String = UUID.randomUUID().toString(),
    val habitId: Long,
    /** The day the check-in counts for — the habit's own "today", not the instant of the tap. */
    val date: LocalDate,
    /** When the tap happened. Presence, not performance: this is what "usually mornings" reads. */
    val checkedAt: Instant,
    /** §0.10 item 3 (v21) — the amount this check-in logged for a counting habit; null on a plain one. */
    val value: Double? = null,
    /** Soft-delete: set by undo, never cleared — see the class comment for why a redo inserts
     * instead. Null means the check-in stands. */
    val deletedAt: Instant? = null,
)
