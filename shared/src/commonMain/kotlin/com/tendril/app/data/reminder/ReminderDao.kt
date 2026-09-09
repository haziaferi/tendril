package com.tendril.app.data.reminder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Query("SELECT * FROM reminders WHERE entryId = :entryId AND deletedAt IS NULL")
    fun observeForEntry(entryId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE entryId = :entryId AND deletedAt IS NULL")
    suspend fun getForEntry(entryId: Long): List<Reminder>

    /** Every reminder, tombstoned ones included — deliberately unfiltered, unlike the two reads
     * above. §9.4's write pass rewrites `reminders.json` in full from these rows, so a tombstone
     * that stopped travelling would let the reminder back in on the next device to merge, which
     * is the resurrection [Reminder.deletedAt] exists to stop. */
    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    /** The §9.4 merge key. Also unfiltered: a merge has to see the local tombstone, or it
     * re-inserts the peer's live copy over it. */
    @Query("SELECT * FROM reminders WHERE uid = :uid")
    suspend fun getByUid(uid: String): Reminder?

    /** §9.4.1 Restore's wipe-and-replace. Called explicitly rather than left to the
     * `entries` foreign key's `CASCADE`: the cascade only fires while SQLite's
     * `foreign_keys` pragma is on, and a restore that silently kept every old reminder —
     * each of which registers an alarm — is not a failure worth making conditional on a
     * pragma. */
    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    /** Soft, not hard — see [Reminder.deletedAt]. Both reads above filter on
     * `deletedAt IS NULL`, which is what makes this safe to swap in underneath every existing
     * caller: a tombstoned reminder cannot reach [com.tendril.app.notifications.AlarmScheduler]
     * through any query it already uses, so no call site has to remember to exclude it. */
    @Query("UPDATE reminders SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: java.time.Instant)
}
