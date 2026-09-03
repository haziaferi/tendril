package com.tendril.app.data.reminder

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.Entry
import java.time.LocalTime

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
    indices = [Index("entryId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val offset: ReminderOffset,
    val anchorTime: LocalTime? = null,
)
