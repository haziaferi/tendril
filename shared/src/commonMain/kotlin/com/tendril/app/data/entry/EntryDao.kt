package com.tendril.app.data.entry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    /** Every record regardless of state — §9.4 snapshot writing needs the full set to
     * split into Active/Archived, not any of the filtered UI queries above. */
    @Query("SELECT * FROM entries")
    suspend fun getAll(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): Entry?

    /** Cross-device merge key (§9.4) — never the local autoincrement `id`, see [Entry.uid]. */
    @Query("SELECT * FROM entries WHERE uid = :uid")
    suspend fun getByUid(uid: String): Entry?

    /** §9.5 — the Google Calendar sync engine's linkage lookup, deciding insert vs. update. */
    @Query("SELECT * FROM entries WHERE googleEventId = :googleEventId")
    suspend fun getByGoogleEventId(googleEventId: String): Entry?

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<Entry?>

    /** §5.2's Row↔Entry link — the actual FK read/written by `bindProperty`/`unbindProperty`
     * (§5.2.1) and by the Row checkbox's resolve/unresolve calls. */
    @Query("SELECT * FROM entries WHERE sourceRowId = :rowPageId AND deletedAt IS NULL LIMIT 1")
    suspend fun getBySourceRowId(rowPageId: Long): Entry?

    /** §0.6.8 — the Task a page had before its label was removed, in Trash. Relabelling brings
     * it back rather than seeding a fresh one, so unlabel → relabel loses nothing. */
    @Query("SELECT * FROM entries WHERE sourceRowId = :rowPageId AND deletedAt IS NOT NULL ORDER BY deletedAt DESC LIMIT 1")
    suspend fun getTrashedBySourceRowId(rowPageId: Long): Entry?

    @Query("SELECT * FROM entries WHERE sourceRowId IN (:rowPageIds) AND deletedAt IS NULL")
    fun observeBySourceRowIds(rowPageIds: List<Long>): Flow<List<Entry>>

    /** Tasks view (§3.3) — `WHERE kind = TASK`, live and undeleted. */
    @Query("SELECT * FROM entries WHERE kind = 'TASK' AND deletedAt IS NULL ORDER BY startDate IS NULL, startDate, startTime")
    fun observeTasks(): Flow<List<Entry>>

    /** Calendar queries `WHERE start_date IS NOT NULL` regardless of kind (§4). */
    @Query("SELECT * FROM entries WHERE startDate IS NOT NULL AND deletedAt IS NULL ORDER BY startDate, startTime")
    fun observeDated(): Flow<List<Entry>>

    /**
     * §8.1 — the one-shot twin of [observeDated], for the two widgets, which render from a
     * snapshot on each update rather than collecting a Flow.
     *
     * Returns *every* live dated row, not a date window, because that is what recurrence
     * expansion needs (§4.1.1): a series' stored row sits at its first occurrence, which for a
     * long-running weekly meeting is nowhere near the fortnight a widget is drawing. This
     * replaced a `startDate BETWEEN :from AND :to` query that returned nothing for exactly
     * those series — deleted rather than left in place, since the next caller to reach for it
     * would reintroduce the same bug. Callers pass the result to
     * [com.tendril.app.domain.recurrence.EntryOccurrences] with the window they actually want.
     */
    @Query("SELECT * FROM entries WHERE startDate IS NOT NULL AND deletedAt IS NULL ORDER BY startDate, startTime")
    suspend fun getAllDated(): List<Entry>

    /** §4.1's single-occurrence exceptions for one recurring base — a skip tombstone
     * (`isExceptionSkip`) or a full per-occurrence override. Trashed rows are excluded here:
     * a trashed *override* means the occurrence reverts to the series' own definition, which
     * is what the expander does when it finds none. */
    @Query("SELECT * FROM entries WHERE originalEntryId = :baseEntryId AND deletedAt IS NULL")
    suspend fun getExceptionsOf(baseEntryId: Long): List<Entry>

    /** Every exception row at once, for the boot/app-open reconciliation sweep — one query
     * grouped in memory rather than one per Entry (§9.7). */
    @Query("SELECT * FROM entries WHERE originalEntryId IS NOT NULL AND deletedAt IS NULL")
    suspend fun getAllExceptions(): List<Entry>

    @Query("SELECT * FROM entries WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Entry>>

    /**
     * One-shot, for boot-time/app-open alarm reconciliation (§9.7) — every live Entry that
     * could have a scheduled alarm. Rescheduling is idempotent (deterministic request
     * codes), so calling this reconciliation pass redundantly is always safe.
     *
     * EVENTs are in scope, not just TASKs. §4 makes reminders apply to either kind ("a
     * birthday reminder is just as valid as a deadline reminder") and
     * [com.tendril.app.notifications.AlarmScheduler.rescheduleFor] schedules them for both.
     * A TASK-only query here meant an EVENT's reminders were armed once, at creation, and
     * then never re-armed — so every one of them was lost at the first reboot, which is the
     * exact failure §9.7 says this sweep exists to prevent. Only the TASK branch takes the
     * `status = PENDING` gate, since an EVENT has no status (§4).
     */
    @Query(
        "SELECT * FROM entries WHERE startDate IS NOT NULL AND deletedAt IS NULL " +
            "AND (kind = 'EVENT' OR status = 'PENDING')"
    )
    suspend fun getAllSchedulable(): List<Entry>

    /**
     * Calendar Provider bookkeeping only (§3.2). Deliberately a column-scoped UPDATE rather
     * than an `@Update` of the whole row: the sync runs *after* the write it accompanies and
     * only ever holds the caller's pre-write snapshot, so a whole-row write here silently
     * reverts whatever that call path just changed. That is exactly how `trash()` used to
     * resurrect the Entry it had just soft-deleted — `removeEntry` wrote back a snapshot
     * captured before `softDelete`, restoring `deletedAt = null` while the alarms stayed
     * cancelled. Keep this narrow; it must never touch a field it does not name.
     */
    @Query("UPDATE entries SET providerEventId = :providerEventId WHERE id = :id")
    suspend fun setProviderEventId(id: Long, providerEventId: Long?)

    /**
     * Google Calendar bookkeeping only — same reasoning as [setProviderEventId], and a wider
     * window to get it wrong: `push` snapshots every candidate Entry up front, then makes a
     * network round-trip per Entry before recording the id it got back. Anything edited
     * locally during that sync would be reverted by a whole-row write of the pre-sync
     * snapshot. Column-scoped, so a slow sync can only ever write the one field it owns.
     */
    @Query("UPDATE entries SET googleEventId = :googleEventId WHERE id = :id")
    suspend fun setGoogleEventId(id: Long, googleEventId: String?)

    @Query("UPDATE entries SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: java.time.Instant)

    @Query("UPDATE entries SET deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restore(id: Long, restoredAt: java.time.Instant)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteForever(id: Long)

    /** Restore-from-backup only (§9.4.1) — the deliberately harder-to-reach full wipe. */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}
