package com.tendril.app.data.track

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TimeLogDao {
    @Insert
    suspend fun insert(log: TimeLog): Long

    @Update
    suspend fun update(log: TimeLog)

    /** The open logs, newest first. Normally one; after a merge, one per device that had a
     * timer running — see [com.tendril.app.domain.track.TimeTracker]. */
    @Query("SELECT * FROM time_logs WHERE endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeRunning(): Flow<List<TimeLog>>

    @Query("SELECT * FROM time_logs WHERE endedAt IS NULL AND deletedAt IS NULL ORDER BY startedAt DESC")
    suspend fun getRunning(): List<TimeLog>

    @Query("SELECT * FROM time_logs WHERE entryId = :entryId AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeForEntry(entryId: Long): Flow<List<TimeLog>>

    @Query("SELECT * FROM time_logs WHERE habitId = :habitId AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeForHabit(habitId: Long): Flow<List<TimeLog>>

    /** Every row, tombstoned ones included — unfiltered for the reason
     * [com.tendril.app.data.habit.HabitCompletionDao.getAll] gives. */
    @Query("SELECT * FROM time_logs")
    suspend fun getAll(): List<TimeLog>

    /** The §9.4 merge key. Unfiltered so a merge can see the local tombstone. */
    @Query("SELECT * FROM time_logs WHERE uid = :uid")
    suspend fun getByUid(uid: String): TimeLog?

    @Query("UPDATE time_logs SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant)

    /** §9.4.1 Restore's wipe-and-replace; explicit rather than by cascade, as
     * [com.tendril.app.data.reminder.ReminderDao.deleteAll] explains. */
    @Query("DELETE FROM time_logs")
    suspend fun deleteAll()
}
