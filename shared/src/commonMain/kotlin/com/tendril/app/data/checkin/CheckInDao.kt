package com.tendril.app.data.checkin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface CheckInDao {
    @Insert
    suspend fun insert(checkIn: CheckIn): Long

    /** The day's live check-ins, in the order they were tapped — the row's logged line. */
    @Query("SELECT * FROM check_ins WHERE date = :date AND deletedAt IS NULL ORDER BY at")
    fun observeForDay(date: LocalDate): Flow<List<CheckIn>>

    /** A month's live check-ins (inclusive bounds), oldest first — the sheet's discs and list. */
    @Query("SELECT * FROM check_ins WHERE date BETWEEN :from AND :to AND deletedAt IS NULL ORDER BY at")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<CheckIn>>

    /** Every row, tombstones included — §9.4's write pass rewrites the file in full, as
     * [com.tendril.app.data.habit.HabitCompletionDao.getAll] explains. */
    @Query("SELECT * FROM check_ins")
    suspend fun getAll(): List<CheckIn>

    /** The §9.4 merge key; unfiltered so a merge sees the local tombstone. */
    @Query("SELECT * FROM check_ins WHERE uid = :uid")
    suspend fun getByUid(uid: String): CheckIn?

    @Query("UPDATE check_ins SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant)

    /** §9.4.1 Restore's wipe-and-replace. */
    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}
