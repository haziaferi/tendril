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

    /** §9.4's write pass republishes the whole table. No filter is needed or possible here:
     * this table is append-only, so there is no deleted state to exclude. */
    @Query("SELECT * FROM entry_completions")
    suspend fun getAll(): List<EntryCompletion>

    /** The §9.4 merge key. A completion already held locally is left alone rather than
     * re-inserted — the merge is a union, and nothing ever edits a completion. */
    @Query("SELECT * FROM entry_completions WHERE uid = :uid")
    suspend fun getByUid(uid: String): EntryCompletion?
}
