package com.tendril.app.data.completion

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.EntryStatus
import java.time.Instant
import java.time.LocalDate

/**
 * §4.1 — append-only, written by [com.tendril.app.domain.ResolveEntryUseCase] every time a
 * TASK resolves, recurring or not, so a future history screen has one consistent source.
 * Never updated or deleted after insert (a seeded-on-bind row per §5.2.1 is the one
 * exception, whose [resolvedAt] is documented there as approximate, not the real historical
 * completion time).
 */
@Entity(tableName = "entry_completions", indices = [Index("entryId")])
data class EntryCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val occurrenceDate: LocalDate,
    val resolvedAt: Instant,
    val status: EntryStatus,
)
