package com.tendril.app.data.habit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    /** Cross-device merge key (§9.4) — never the local autoincrement `id`, see [Habit.uid]. */
    @Query("SELECT * FROM habits WHERE uid = :uid")
    suspend fun getByUid(uid: String): Habit?

    /** Every record regardless of state — §9.4 snapshot writing needs the full set. */
    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<Habit>

    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY title")
    fun observeActive(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Habit>>

    @Query("UPDATE habits SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant)

    @Query("UPDATE habits SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restore(id: Long, restoredAt: Instant)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteForever(id: Long)

    /** Restore-from-backup only (§9.4.1) — the deliberately harder-to-reach full wipe. */
    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}
