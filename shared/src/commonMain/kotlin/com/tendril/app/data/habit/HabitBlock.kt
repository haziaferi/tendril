package com.tendril.app.data.habit

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import java.util.UUID

/**
 * §6.3 (v27) — a time block of the day that calendar habits sit in: Morning, Midday, Afternoon,
 * Evening, Night by default, each editable. Minutes from midnight; [endMinute] is not part of the
 * block (09:00 belongs to the block starting at 09:00). Named `startMinute`/`endMinute` rather than
 * the planner's `start`/`end`, because `END` is an SQL keyword every hand-written query would have
 * to quote.
 *
 * Merged like a Habit (§9.4): tombstoned, and otherwise the later [updatedAt] wins. [overrides]
 * (H2) are the block's own times on some weekdays, carried in the row so they merge with it.
 */
@Entity(tableName = "habit_blocks", indices = [Index("uid", unique = true)])
data class HabitBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    /** H1 — null for a default block the person has not renamed: shown in the app's language. */
    val name: String? = null,
    val startMinute: Int,
    val endMinute: Int,
    /** The order the blocks are listed in, and the order a several-a-day habit is spread over. */
    val position: Int,
    /** Q2 — one of the planner's six (`sunrise`, `sun`, `leaf`, `sunset`, `moon`, `star`); null for none. */
    val icon: String? = null,
    /** Q2 — a hue in degrees on Tendril's wheel, as a database's and a canvas node's (S13); null for none. */
    val hue: Int? = null,
    /** H2 — `PlanCodec.encodeOverrides`: `SAT,SUN@480-630;…`. Kept as text, like a calendar rule. */
    val overrides: String? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

/**
 * §6.3 (H1) — the five blocks every database starts with, under fixed uids so that two devices
 * seeding on their own write the same five rows, which merge to five. `updatedAt` 0: any real edit
 * or deletion is newer and wins. The planner's defaults (06:30–09:00, 09:00–13:00, 13:00–18:00,
 * 18:00–21:30, 21:30–23:30), its icons, and the hues the approved mockup drew — the planner's
 * peach, gold, sage, lavender and sky, placed on Tendril's wheel.
 */
val DEFAULT_HABIT_BLOCKS: List<HabitBlock> = listOf(
    HabitBlock(uid = "block-morning", startMinute = 390, endMinute = 540, position = 0, icon = "sunrise", hue = 25, updatedAt = Instant.EPOCH),
    HabitBlock(uid = "block-midday", startMinute = 540, endMinute = 780, position = 1, icon = "sun", hue = 45, updatedAt = Instant.EPOCH),
    HabitBlock(uid = "block-afternoon", startMinute = 780, endMinute = 1080, position = 2, icon = "leaf", hue = 110, updatedAt = Instant.EPOCH),
    HabitBlock(uid = "block-evening", startMinute = 1080, endMinute = 1290, position = 3, icon = "sunset", hue = 265, updatedAt = Instant.EPOCH),
    HabitBlock(uid = "block-night", startMinute = 1290, endMinute = 1410, position = 4, icon = "moon", hue = 205, updatedAt = Instant.EPOCH),
)

@Dao
interface HabitBlockDao {
    @Insert
    suspend fun insert(block: HabitBlock): Long

    @Update
    suspend fun update(block: HabitBlock)

    /** H1 — the seeding path: a uid already present, live or tombstoned, is left as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(blocks: List<HabitBlock>)

    @Query("SELECT * FROM habit_blocks")
    suspend fun getAll(): List<HabitBlock>

    @Query("SELECT * FROM habit_blocks WHERE uid = :uid")
    suspend fun getByUid(uid: String): HabitBlock?

    @Query("SELECT * FROM habit_blocks WHERE deletedAt IS NULL ORDER BY position, uid")
    fun observeLive(): kotlinx.coroutines.flow.Flow<List<HabitBlock>>

    /** §9.4.1's Restore only, which then writes the archive's blocks and the defaults an empty install has. */
    @Query("DELETE FROM habit_blocks")
    suspend fun deleteAll()
}
