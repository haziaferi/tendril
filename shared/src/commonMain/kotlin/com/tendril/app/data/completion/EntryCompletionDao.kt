package com.tendril.app.data.completion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryCompletionDao {
    @Insert
    suspend fun insert(completion: EntryCompletion): Long

    @Query("SELECT * FROM entry_completions WHERE entryId = :entryId ORDER BY resolvedAt DESC")
    fun observeForEntry(entryId: Long): Flow<List<EntryCompletion>>
}
