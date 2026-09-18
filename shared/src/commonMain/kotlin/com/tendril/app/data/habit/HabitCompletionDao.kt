package com.tendril.app.data.habit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface HabitCompletionDao {
    @Insert
    suspend fun insert(completion: HabitCompletion): Long

    /** The live check-ins of one habit, newest day first — what a presence view reads. */
    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND deletedAt IS NULL ORDER BY date DESC")
    fun observeForHabit(habitId: Long): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date AND deletedAt IS NULL")
    suspend fun getLiveForDay(habitId: Long, date: LocalDate): List<HabitCompletion>

    /** §0.10 item 3 — every habit's live check-ins on one day, for the rows' *2 cups today*. */
    @Query("SELECT * FROM habit_completions WHERE date = :date AND deletedAt IS NULL")
    fun observeLiveForDay(date: LocalDate): Flow<List<HabitCompletion>>

    /** Every row, tombstoned ones included — unfiltered for the reason
     * [com.tendril.app.data.reminder.ReminderDao.getAll] gives: §9.4's write pass rewrites the
     * file in full from these rows, and a tombstone that stopped travelling would let the
     * check-in back in from the next device to merge. */
    @Query("SELECT * FROM habit_completions")
    suspend fun getAll(): List<HabitCompletion>

    /** The §9.4 merge key. Unfiltered so a merge can see the local tombstone. */
    @Query("SELECT * FROM habit_completions WHERE uid = :uid")
    suspend fun getByUid(uid: String): HabitCompletion?

    /** Soft, not hard — see [HabitCompletion.deletedAt]. */
    @Query("UPDATE habit_completions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant)

    /** §9.4.1 Restore's wipe-and-replace; explicit rather than by cascade, as
     * [com.tendril.app.data.reminder.ReminderDao.deleteAll] explains. */
    @Query("DELETE FROM habit_completions")
    suspend fun deleteAll()
}
