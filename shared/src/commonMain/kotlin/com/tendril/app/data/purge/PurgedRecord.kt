package com.tendril.app.data.purge

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant

/** What a tombstone is *for*. Every member is reached from a real, deliberate delete that
 * exists today, and only those: PAGE, ENTRY and HABIT from "Delete forever" in their Trash
 * sheets — HABIT is no longer speculative, `HabitTrashSheet` grew that action on 2026-09-05 and
 * calls [com.tendril.app.domain.PurgeRegistry.purgeHabit] from its confirm dialog — and
 * PROPERTY from §5.5's delete-column dialog, which is not a Trash at all (a property has no
 * Trash) but needs a tombstone for exactly the same reason §9.4's merge would otherwise
 * re-upsert the column from a peer that has not seen the deletion. Speculative members are the
 * sort of dead generality this codebase avoids: add one alongside the delete path that needs
 * it, never ahead of it. */
enum class PurgedKind { PAGE, ENTRY, HABIT, PROPERTY }

/**
 * §5.5.1.1 — a tombstone for a record the person deleted *forever* out of the Trash.
 *
 * Without one, "Delete forever" doesn't stick. §9.4's merge inserts any record whose `uid`
 * isn't already local, so the purged item comes back from whatever still holds it: for a Page,
 * its own `pages/<uid>.json` file, which resurrects it on the very next pass **on one device**;
 * for an Entry, the array file another device rewrites. Pruning after the merge cannot fix
 * either — the merge runs first and has already restored the row. The purge has to be
 * *recorded*, not inferred from absence.
 *
 * Keyed by (kind, uid) rather than uid alone: a Page uid and an Entry uid are both UUIDs from
 * separate spaces, and nothing guarantees they never coincide.
 */
@Entity(tableName = "purged_records", primaryKeys = ["kind", "uid"])
data class PurgedRecord(
    val kind: PurgedKind,
    val uid: String,
    val purgedAt: Instant,
)

@Dao
interface PurgedRecordDao {
    /** IGNORE, not REPLACE: purging a record that already carries a tombstone is not a
     * conflict, and re-recording it must not push the timestamp forward. Choosing *between*
     * two devices' timestamps for the same record needs a comparison this annotation cannot
     * express, so [com.tendril.app.domain.PurgeRegistry.adopt] does it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: PurgedRecord)

    @Query("SELECT * FROM purged_records")
    suspend fun getAll(): List<PurgedRecord>

    /** Read once per merge pass and held as a map — cheaper than a query per incoming record,
     * and bounded by how many things have ever been purged. */
    @Query("SELECT * FROM purged_records WHERE kind = :kind")
    suspend fun getForKind(kind: PurgedKind): List<PurgedRecord>

    /** Drops a tombstone that a *newer* edit has superseded — see [PurgeRegistry]. */
    @Query("DELETE FROM purged_records WHERE kind = :kind AND uid = :uid")
    suspend fun clear(kind: PurgedKind, uid: String)

    /** Restore-from-backup only (§9.4.1): a whole-database replace starts from the archive's
     * tombstones, not this device's. */
    @Query("DELETE FROM purged_records")
    suspend fun deleteAll()
}
