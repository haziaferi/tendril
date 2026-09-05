package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.data.purge.PurgedRecordDao
import java.time.Instant

/**
 * §5.5.1.1 — the one place "Delete forever" is expressed, on the same principle as
 * [ResolveEntryUseCase] (§9.8 R1): recording the tombstone and deleting the row are one
 * operation, never two things a call site has to remember to do in order.
 *
 * **Purges propagate.** The tombstone travels in the snapshot folder (`purged_records.json`,
 * written by `SnapshotSyncOrchestrator`), so deleting something forever on one device deletes
 * it everywhere rather than only where the tap happened. This is a deliberate narrowing of
 * §9.4's "merge is additive and non-destructive" rule, which was written before there was any
 * way to tell "this was deliberately destroyed" apart from "this hasn't arrived yet" — the
 * tombstone is exactly that signal, and without it a purge could never be more than local.
 *
 * **A purge is a timestamped fact, not a veto.** For any one `uid` the folder can carry both a
 * record (`updatedAt`) and a tombstone (`purgedAt`); the later of the two wins, the same
 * last-write-wins rule §9.4 already applies to every other field. So a purge removes the record
 * everywhere — unless some device edited it *after* the purge, having never seen it, in which
 * case that edit resurrects it and [supersede] drops the tombstone so it stops fighting.
 * Anything else would let a stale delete quietly destroy work someone was still doing.
 */
class PurgeRegistry(
    private val purgedRecordDao: PurgedRecordDao,
    private val pageDao: PageDao,
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) {
    /** Records the tombstone *before* dropping the row, so a crash between the two leaves a
     * tombstone with no row (harmless, self-correcting on the next merge) rather than a row
     * with no tombstone, which resurrects. */
    suspend fun purgePage(pageId: Long, now: Instant = Instant.now()) {
        val page = pageDao.getById(pageId) ?: return
        purgedRecordDao.insert(PurgedRecord(PurgedKind.PAGE, page.uid, now))
        pageDao.deleteForever(pageId)
    }

    suspend fun purgeEntry(entryId: Long, now: Instant = Instant.now()) {
        val entry = entryDao.getById(entryId) ?: return
        purgedRecordDao.insert(PurgedRecord(PurgedKind.ENTRY, entry.uid, now))
        removeEntry(entry)
    }

    /** Habits need no [removeEntry]-style teardown: they carry no alarms and no Calendar
     * Provider mirror, so the row is the whole of it. */
    suspend fun purgeHabit(habitId: Long, now: Instant = Instant.now()) {
        val habit = habitDao.getById(habitId) ?: return
        purgedRecordDao.insert(PurgedRecord(PurgedKind.HABIT, habit.uid, now))
        // The reminder comes down before the row does, for the same reason [removeEntry] tears
        // an Entry's alarms down first: an alarm outliving its row is a wakeup for nothing.
        entryScheduleCoordinator.onHabitRemoved(habitId)
        habitDao.deleteForever(habitId)
    }

    /** uid → `purgedAt` for one kind, read once per merge pass rather than queried per record. */
    suspend fun tombstones(kind: PurgedKind): Map<String, Instant> =
        purgedRecordDao.getForKind(kind).associate { it.uid to it.purgedAt }

    /**
     * Whether an incoming record should be refused, given the [tombstones] this pass loaded.
     * True when a tombstone covers it and the record is not newer; when the record *is* newer
     * the tombstone is superseded and dropped here, so this is the only check a merge needs.
     */
    suspend fun isPurged(kind: PurgedKind, uid: String, recordUpdatedAt: Instant, tombstones: Map<String, Instant>): Boolean {
        val purgedAt = tombstones[uid] ?: return false
        if (recordUpdatedAt.isAfter(purgedAt)) {
            purgedRecordDao.clear(kind, uid)
            return false
        }
        return true
    }

    /**
     * Applies every tombstone this device now holds to its own rows — the half that makes a
     * purge arriving *from* another device actually delete anything here. Run right after the
     * tombstone file merges and before the record files do, so a record and the tombstone that
     * kills it can't cross in the same pass.
     *
     * A local row edited after the purge supersedes it, by the same rule as [isPurged].
     */
    suspend fun applyToLocalRecords() {
        for (tombstone in purgedRecordDao.getAll()) {
            when (tombstone.kind) {
                PurgedKind.PAGE -> pageDao.getByUid(tombstone.uid)?.let { page ->
                    if (page.updatedAt.isAfter(tombstone.purgedAt)) supersede(tombstone)
                    else pageDao.deleteForever(page.id)
                }
                PurgedKind.ENTRY -> entryDao.getByUid(tombstone.uid)?.let { entry ->
                    if (entry.updatedAt.isAfter(tombstone.purgedAt)) supersede(tombstone)
                    else removeEntry(entry)
                }
                PurgedKind.HABIT -> habitDao.getByUid(tombstone.uid)?.let { habit ->
                    if (habit.updatedAt.isAfter(tombstone.purgedAt)) supersede(tombstone)
                    else {
                        entryScheduleCoordinator.onHabitRemoved(habit.id)
                        habitDao.deleteForever(habit.id)
                    }
                }
            }
        }
    }

    /**
     * Alarms and the Calendar Provider mirror come down before the row does. Trashing an Entry
     * normally does this already, but an Entry can reach Trash from `GoogleCalendarSyncEngine`'s
     * pull without going through the coordinator, so its alarms may still be live — and an alarm
     * outliving its row is a wakeup for nothing. It matters twice as much here as at the Trash
     * button, because [applyToLocalRecords] deletes Entries purged on *another* device, where
     * this side never saw the deletion coming.
     */
    private suspend fun removeEntry(entry: Entry) {
        entryScheduleCoordinator.onEntryRemoved(entry)
        entryDao.deleteForever(entry.id)
    }

    private suspend fun supersede(tombstone: PurgedRecord) =
        purgedRecordDao.clear(tombstone.kind, tombstone.uid)

    suspend fun all(): List<PurgedRecord> = purgedRecordDao.getAll()

    /**
     * Takes on tombstones arriving from another device or an archive, keeping the **earliest**
     * `purgedAt` for any (kind, uid) — which is what [PurgedRecordDao.insert]'s IGNORE was
     * reaching for but cannot express on its own.
     *
     * IGNORE keeps whichever tombstone this device happened to see *first*, which is not the
     * same thing and is not order-independent: two devices that both purged the same record
     * each keep their own timestamp, and an edit from a third device landing between the two
     * then supersedes the purge on one and loses to it on the other — leaving them permanently
     * disagreeing about whether the record exists. A minimum converges from any order, and it
     * is the conservative choice of the two: the older the tombstone, the easier it is for a
     * later edit to supersede it, and destroying live work is the failure this whole comparison
     * exists to avoid.
     */
    suspend fun adopt(records: List<PurgedRecord>) {
        val existing = purgedRecordDao.getAll().associateBy { it.kind to it.uid }
        val earliestIncoming = records.groupBy { it.kind to it.uid }.mapValues { (_, group) -> group.minBy { it.purgedAt } }
        for ((key, record) in earliestIncoming) {
            val current = existing[key]
            if (current != null && !record.purgedAt.isBefore(current.purgedAt)) continue
            if (current != null) purgedRecordDao.clear(record.kind, record.uid)
            purgedRecordDao.insert(record)
        }
    }

    /** Restore-from-backup only (§9.4.1) — see [PurgedRecordDao.deleteAll]. */
    suspend fun clearAll() = purgedRecordDao.deleteAll()
}
