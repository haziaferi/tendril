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
import java.time.Period
import java.time.ZoneId

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
 *
 * ## View-Only does not reach here, by decision (§3.1.2)
 * This class must never learn about [ViewLockState], and no call site of it should be gated —
 * which is worth stating plainly, because a resolution *is* a write that syncs, so the omission
 * otherwise reads as the one the lock sweep missed. §3.1.2 scopes View-Only to "every page under
 * Pages", and every surface that resolves through here except one lives outside that hub:
 * `EntryActionReceiver`'s notification inline action, the Habits widget, `CalendarScreen` and
 * `TasksHabitsScreen` are quick-capture surfaces where the eye toggle is not on screen and often
 * not even in the app. Gating them would swallow a tap the person had no way to know was locked,
 * silently, at the moment they were trying to record that something really happened — and an
 * unlogged completion is itself lost data, which is the thing the lock exists to prevent.
 *
 * The one deliberate exception is `PageDatabaseViewModel.toggleDone`: a Row's bound Done checkbox
 * is inside Pages, on a page whose lock the person can see and toggle in the same breath, so that
 * call site keeps its `locked()` gate. The split is intentional and lives at the call site rather
 * than here, because "am I inside the locked surface?" is a question only the caller can answer.
 * The ungated half is pinned by `ViewOnlySurfacesGuardTest`, so a later sweep that gates every
 * write it can find breaks a test rather than a person's quick capture.
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
        // Already resolved this way: nothing happened, so nothing is logged (audit 5.2). A task an
        // import or the other device finished could still post an overdue notification armed
        // before, and its Done wrote a second completion. A recurring task is never left resolved
        // (it advances to PENDING), so this cannot swallow a real occurrence's Done.
        if (entry.status == status) return

        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val occurrenceDate = entry.startDate ?: today
        completionDao.insert(
            EntryCompletion(entryId = entryId, occurrenceDate = occurrenceDate, resolvedAt = now, status = status)
        )

        val recurrence = entry.recurrenceRule
        val updated = if (recurrence is RecurrenceRule.Elastic) {
            entry.copy(
                startDate = nextOccurrenceAfter(occurrenceDate, recurrence.period, today),
                status = EntryStatus.PENDING,
                updatedAt = now,
            )
        } else {
            entry.copy(status = status, updatedAt = now)
        }
        entryDao.update(updated)
        entryScheduleCoordinator.onEntryChanged(updated)
    }

    /**
     * The next occurrence of a recurring TASK, after [resolved] was just resolved on [today].
     *
     * §6.2 fixes the *phase*: occurrences step from the original schedule, never from the
     * moment of resolution, so handling one late does not drag the whole series later. Adding
     * a single period is what implements that — but on its own it only lands in the future
     * when the task was resolved roughly on time. Resolve a P7D task nineteen days late and
     * `resolved + P7D` is still eleven days in the past, which is not "the next occurrence"
     * under any reading of §6.1 or §6.2, and it broke three things at once:
     *
     *  - the task reappeared overdue immediately, needing one Done tap per missed period to
     *    clear — while §5.2 keeps exactly one live Entry row per recurring task, so those
     *    intermediate occurrences have no representation to resolve in the first place;
     *  - each of those taps wrote an [EntryCompletion] claiming an occurrence was resolved
     *    that nobody ever did, corrupting the append-only history §4.1 relies on for
     *    "what actually happened";
     *  - §9.7 refuses to schedule an alarm whose trigger time is already past, so every one
     *    of those in-between states was silently unscheduled — a long-neglected recurring
     *    task stopped notifying entirely.
     *
     * So: keep stepping by whole periods (preserving §6.2's phase and, for MONTH intervals,
     * `Period`'s own calendar-correct end-of-month clamping — which repeated addition and a
     * single multiplied jump do not agree on) until the date is strictly in the future. The
     * occurrence just resolved stays logged as resolved; the ones missed in between are
     * passed over rather than invented.
     *
     * A zero or negative period would never terminate, so it advances by nothing at all —
     * the create/edit paths are what keep such a rule from being stored (§4.1).
     */
    private fun nextOccurrenceAfter(resolved: LocalDate, period: Period, today: LocalDate): LocalDate {
        if (period.isZero || period.isNegative) return resolved
        var next = resolved.plus(period)
        while (!next.isAfter(today)) next = next.plus(period)
        return next
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

    /**
     * What a Done checkbox actually means, in one place. Every such checkbox — Tasks, Calendar,
     * a Database row, a Row's own page — has to pick between [resolve] and [unresolve], and each
     * surface was making that choice for itself in its own ViewModel or composable; one of them
     * picked `resolve(SKIPPED)` for the unchecked case, which is a resolution rather than an
     * undo, so unchecking logged a completion, left the Entry SKIPPED with its alarms cancelled,
     * and advanced an Elastic recurrence another period. Deciding it here is the same
     * "centralize the invariant once rather than trust every call site" rule this class's own
     * doc states (§9.8 R1).
     */
    suspend fun setDone(entryId: Long, done: Boolean, now: Instant = Instant.now()) {
        if (done) resolve(entryId, EntryStatus.DONE, now) else unresolve(entryId, now)
    }

    /** Also centralized here (§5.5.1) — Trash for a standalone Entry cancels its alarms and
     * removes its Provider mirror (§3.2) too. */
    suspend fun trash(entryId: Long, now: Instant = Instant.now()) {
        if (entryDao.getById(entryId) == null) return
        // A series takes its exception rows with it, stamped with the same instant so [restore]
        // can bring back exactly these (audit 5.12). Left live, a moved occurrence stayed in the
        // Calendar and the phone's calendar app after "The whole series goes".
        for (exception in entryDao.getExceptionsOf(entryId)) {
            entryDao.softDelete(exception.id, now)
            entryDao.getById(exception.id)?.let { entryScheduleCoordinator.onEntryRemoved(it) }
        }
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
        val trashedAt = entryDao.getById(entryId)?.deletedAt
        // The exception rows [trash] took with this series — same instant — and only those: one
        // trashed on its own before stays in Trash. Restored first, so the series' own re-arm and
        // re-mirror below already see them (its EXDATEs, §9.11).
        if (trashedAt != null) {
            for (exception in entryDao.getAll().filter { it.originalEntryId == entryId && it.deletedAt == trashedAt }) {
                entryDao.restore(exception.id, now)
                entryDao.getById(exception.id)?.let { entryScheduleCoordinator.onEntryChanged(it) }
            }
        }
        entryDao.restore(entryId, now)
        entryDao.getById(entryId)?.let { entryScheduleCoordinator.onEntryChanged(it) }
    }
}
