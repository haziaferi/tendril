package com.tendril.app.data.completion

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.EntryStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * §4.1 — append-only, written by [com.tendril.app.domain.ResolveEntryUseCase] every time a
 * TASK resolves, recurring or not, so a future history screen has one consistent source.
 * Never updated or deleted after insert (a seeded-on-bind row per §5.2.1 is the one
 * exception, whose [resolvedAt] is documented there as approximate, not the real historical
 * completion time).
 */
@Entity(
    tableName = "entry_completions",
    indices = [Index("uid", unique = true), Index("entryId")],
)
data class EntryCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4 snapshot merge, on the same terms as
     * [com.tendril.app.data.entry.Entry.uid]. Append-only makes this table the one place a
     * duplicated identity would be permanent — nothing ever updates a completion row, so a
     * merge that keyed on the local `id` could not later correct itself. Added in v9 (S2);
     * rows that predate v9 are assigned one by [com.tendril.app.data.MIGRATION_8_9]. */
    val uid: String = UUID.randomUUID().toString(),
    val entryId: Long,
    val occurrenceDate: LocalDate,
    val resolvedAt: Instant,
    val status: EntryStatus,
)
