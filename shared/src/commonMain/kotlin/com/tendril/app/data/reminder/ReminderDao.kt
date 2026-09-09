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

    /** Soft, not hard — see [Reminder.deletedAt]. Both reads above filter on
     * `deletedAt IS NULL`, which is what makes this safe to swap in underneath every existing
     * caller: a tombstoned reminder cannot reach [com.tendril.app.notifications.AlarmScheduler]
     * through any query it already uses, so no call site has to remember to exclude it. */
    @Query("UPDATE reminders SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: java.time.Instant)
}
