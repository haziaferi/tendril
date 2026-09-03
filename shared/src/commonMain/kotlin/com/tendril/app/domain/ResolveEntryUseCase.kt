package com.tendril.app.domain

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import java.time.Instant
import java.time.LocalDate

/**
 * §5.2 / §9.8 R1 — the one place every surface that can resolve a TASK Entry (Row checkbox,
 * Task list, Calendar, Merged, widget quick-actions, the overdue notification's inline
 * actions) must call through, instead of each reimplementing the
 * multi-step sequence. No call site touches [Entry]/[com.tendril.app.data.reminder.Reminder]/
 * [EntryCompletion] directly for a resolution.
 *
 * Sequence: log completion → advance-or-finalize → reschedule alarms + Provider mirror (via
 * [EntryScheduleCoordinator], §3.2/§9.9 item 3's own centralization). A bound Row's Done
 * property (§5.2.1) never needs a separate write-back step here — once bound it's a live
 * proxy computed from this Entry at display time (see [com.tendril.app.domain.DatabaseSyncManager]),
 * not a second stored value that could drift out of lockstep.
 */
class ResolveEntryUseCase(
    private val entryDao: EntryDao,
    private val completionDao: EntryCompletionDao,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) {
    suspend fun resolve(entryId: Long, status: EntryStatus, now: Instant = Instant.now()) {
        require(status != EntryStatus.PENDING) { "resolve() only takes a terminal status (DONE/SKIPPED)" }
        val entry = entryDao.getById(entryId) ?: return
        require(entry.kind == EntryKind.TASK) { "Only TASK Entries resolve; EVENT has no done/not-done state (§4)" }

        val occurrenceDate = entry.startDate ?: LocalDate.now()
        completionDao.insert(
            EntryCompletion(entryId = entryId, occurrenceDate = occurrenceDate, resolvedAt = now, status = status)
        )

        val recurrence = entry.recurrenceRule
        val updated = if (recurrence is RecurrenceRule.Elastic) {
            // §6.2 — anchored to the *original fixed schedule*, not to when it was actually
            // resolved: advance by exactly one period from the occurrence just resolved.
            entry.copy(
                startDate = occurrenceDate.plus(recurrence.period),
                status = EntryStatus.PENDING,
                updatedAt = now,
            )
        } else {
            entry.copy(status = status, updatedAt = now)
        }
        entryDao.update(updated)
        entryScheduleCoordinator.onEntryChanged(updated)
    }

    /** The undo counterpart to [resolve] — unchecking a Row's bound Done checkbox (§5.2) or
     * Tasks' own checkbox after a same-session mis-tap. Not a real "resolution" (no
     * [EntryCompletion] entry, since nothing was actually completed), so it stays out of
     * [resolve]'s terminal-status contract; alarms still need rearming since a DONE task's
     * alarms were cancelled when it resolved. */
    suspend fun unresolve(entryId: Long, now: Instant = Instant.now()) {
        val entry = entryDao.getById(entryId) ?: return
        require(entry.kind == EntryKind.TASK) { "Only TASK Entries have a done/not-done state to unresolve (§4)" }
        val updated = entry.copy(status = EntryStatus.PENDING, updatedAt = now)
        entryDao.update(updated)
        entryScheduleCoordinator.onEntryChanged(updated)
    }

    /** Also centralized here (§5.5.1) — Trash for a standalone Entry cancels its alarms and
     * removes its Provider mirror (§3.2) too. */
    suspend fun trash(entryId: Long, now: Instant = Instant.now()) {
        if (entryDao.getById(entryId) == null) return
        entryDao.softDelete(entryId, now)
        // Re-read rather than reusing the pre-delete snapshot. The coordinator tears down the
        // Calendar mirror, which writes back to this same row to clear its mirror id — handed
        // a stale copy, that write restored `deletedAt = null` and the Entry came back from
        // Trash with its alarms already cancelled. The implementations are column-scoped now,
        // but this is the half that makes the invariant hold for *any* coordinator.
        val trashed = entryDao.getById(entryId) ?: return
        entryScheduleCoordinator.onEntryRemoved(trashed)
    }

    suspend fun restore(entryId: Long, now: Instant = Instant.now()) {
        entryDao.restore(entryId, now)
        entryDao.getById(entryId)?.let { entryScheduleCoordinator.onEntryChanged(it) }
    }
}
