package com.tendril.app.data.reminder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Query("SELECT * FROM reminders WHERE entryId = :entryId")
    fun observeForEntry(entryId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE entryId = :entryId")
    suspend fun getForEntry(entryId: Long): List<Reminder>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

}
