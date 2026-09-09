package com.tendril.app.data.reminder

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.Entry
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * §4 — a repeatable list per Entry, no cap on how many stack. [anchorTime] is only used
 * when the occurrence's own day has no real start/end time (an all-day EVENT, or a middle
 * day of a multi-day span) — unused when the Entry already has a real time that day.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = Entry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("uid", unique = true), Index("entryId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4 snapshot merge, on the same terms as
     * [com.tendril.app.data.entry.Entry.uid]: a local Room `id` is only unique on the device
     * that assigned it. Added in v9 (S2); rows that predate v9 are assigned one by
     * [com.tendril.app.data.MIGRATION_8_9], not by this Kotlin default — a default only runs
     * for rows this process constructs. */
    val uid: String = UUID.randomUUID().toString(),
    val entryId: Long,
    val offset: ReminderOffset,
    val anchorTime: LocalTime? = null,
    /** Soft-delete (§5.5.1) — null means live. A tombstone rather than a hard `DELETE`
     * because §9.4's merge unions reminder records by [uid]: a reminder removed on one device
     * but still present in another's snapshot would come back, and a resurrected reminder is
     * not a stale row on a screen — it re-registers an alarm and fires. A tombstone travels;
     * an absence cannot say anything.
     *
     * No `updatedAt` is needed to order the merge, unlike [com.tendril.app.data.entry.Entry].
     * [ReminderDao] has no update path at all, so a uid is created once and deleted once, and
     * "deleted on any device wins" is monotonic — there is no second edit for a timestamp to
     * adjudicate. The value is the deletion time, kept because it is free and legible, not
     * because the merge consults it. */
    val deletedAt: Instant? = null,
)
