package com.tendril.app.data.habit

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant
import java.util.UUID

/**
 * §6.3 (v27, D10) — one "Apply this change to…": a change to a calendar habit or a time block,
 * over one entry, a day, a week, or from a date on. What the engine's
 * [com.tendril.app.domain.plan.ScheduleEdit] is, as three strings `PlanCodec` reads — [target],
 * [scope] and [changes] — kept verbatim, so a key a newer build writes survives this one.
 *
 * **Inserted once and tombstoned once**, like a [HabitCompletion]: an edit is never rewritten (a
 * further change is a further edit, applied after it), and an undo sets [deletedAt]. §9.4's
 * "deleted on any device wins" is therefore the whole merge, with nothing to compare. Two devices
 * writing edits offline keep both, applied in [createdAt] order and then by [uid], the same on
 * every device (plan §7, risk 2).
 */
@Entity(tableName = "habit_schedule_edits", indices = [Index("uid", unique = true), Index("refUid")])
data class HabitScheduleEdit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    /** `HABIT` or `BLOCK`. */
    val target: String,
    /** The habit's or the block's uid. */
    val refUid: String,
    val scope: String,
    val changes: String,
    val createdAt: Instant,
    val deletedAt: Instant? = null,
)

@Dao
interface HabitScheduleEditDao {
    @Insert
    suspend fun insert(edit: HabitScheduleEdit): Long

    @Query("SELECT * FROM habit_schedule_edits")
    suspend fun getAll(): List<HabitScheduleEdit>

    @Query("SELECT * FROM habit_schedule_edits WHERE uid = :uid")
    suspend fun getByUid(uid: String): HabitScheduleEdit?

    @Query("SELECT * FROM habit_schedule_edits WHERE deletedAt IS NULL")
    fun observeLive(): kotlinx.coroutines.flow.Flow<List<HabitScheduleEdit>>

    @Query("UPDATE habit_schedule_edits SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Instant)

    /** §9.4.1's Restore only. */
    @Query("DELETE FROM habit_schedule_edits")
    suspend fun deleteAll()
}
