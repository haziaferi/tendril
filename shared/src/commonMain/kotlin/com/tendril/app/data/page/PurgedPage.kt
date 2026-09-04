package com.tendril.app.data.page

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant

/**
 * §5.5.1 — a tombstone for a Page the person deleted *forever* out of the Trash.
 *
 * Without one, "Delete forever" doesn't stick across a sync. §9.4's snapshot folder holds one
 * file per page (`pages/<uid>.json`), and the merge inserts any record whose uid it doesn't
 * already have locally — so the purged page's own file, still sitting in the folder, put it
 * straight back on the next pass. This happens on a single device with no other peer involved,
 * and pruning the file afterwards can't fix it: the merge runs first and has already restored
 * the row by the time anything could be pruned. The purge has to be *remembered*, not inferred.
 *
 * Deliberately local-only — not written into the sync folder as a snapshot of its own. §9.4's
 * merge is documented as additive and non-destructive ("Never deletes a local record just
 * because it's absent from the remote file"), and a tombstone that travelled would make one
 * device's purge delete another's data, which is a different product decision than this one.
 * The effect is that a purge is permanent *on the device that made it*: a peer that still holds
 * the page keeps its own copy, and this device declines it every time rather than resurrecting.
 *
 * Cleared wholesale by a Restore-from-backup (§9.4.1), which is explicitly a "replace what's
 * here" operation — otherwise a restore could never bring a purged page back.
 */
@Entity(tableName = "purged_pages")
data class PurgedPage(
    @PrimaryKey val uid: String,
    val purgedAt: Instant,
)

@Dao
interface PurgedPageDao {
    /** IGNORE, not REPLACE: purging a uid twice is a no-op, and the first purge's timestamp is
     * the true one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(purged: PurgedPage)

    /** Read once per merge pass and held as a set — cheaper than a query per incoming record,
     * and the count here is bounded by how many pages someone has ever purged. */
    @Query("SELECT uid FROM purged_pages")
    suspend fun getAllUids(): List<String>

    /** Lifts the tombstone for pages an explicit Import actually carries (§9.4.1) — asking for
     * a page back by name outranks having purged it earlier. */
    @Query("DELETE FROM purged_pages WHERE uid IN (:uids)")
    suspend fun clear(uids: List<String>)

    /** Restore-from-backup only (§9.4.1) — see the entity's class doc. */
    @Query("DELETE FROM purged_pages")
    suspend fun deleteAll()
}
