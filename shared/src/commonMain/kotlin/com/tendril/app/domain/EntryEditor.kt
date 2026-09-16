package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Which occurrences a change to a recurring Entry applies to. */
enum class MoveScope { THIS_ONE, ALL }

/**
 * §0.8 step 6b — the edit path §3.2 said was never built, as one use case rather than a sheet's
 * private writes. Every edit or move of a stored Entry ends here, and here alone, in the two
 * calls §4.1 asks for: `EntryDao.update` and then [EntryScheduleCoordinator.onEntryChanged] (alarms
 * and the Calendar Provider mirror). [ResolveEntryUseCase] owns done/undone/trash the same way.
 *
 * The kind invariants Room cannot check (§4) are enforced on the way through, so a sheet that
 * flips Task ↔ Event cannot store a Task with a span or an Event with a deadline.
 */
class EntryEditor(
    private val entryDao: EntryDao,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) {
    /** Writes [edited] as the new state of its own row, normalised for its kind. */
    suspend fun save(edited: Entry, now: Instant = Instant.now()): Entry {
        val normalised = edited.normalisedForKind().copy(updatedAt = now)
        entryDao.update(normalised)
        entryDao.getById(normalised.id)?.let { entryScheduleCoordinator.onEntryChanged(it) }
        return normalised
    }

    /**
     * Moves an Entry — or one occurrence of a recurring one — to [toDate] at [toTime] (null
     * keeps the entry's own time, so a day drag leaves an all-day entry all-day; a time places
     * one that had none, which is Plan mode's drop). A span keeps its length in
     * days and minutes. [occurrenceDate] names which occurrence was dragged; with
     * [MoveScope.THIS_ONE] on a recurring base that becomes an override row (§4.1's exception
     * machinery), with [MoveScope.ALL] the series' anchor moves. A non-recurring entry, or an
     * override row itself, simply moves.
     */
    suspend fun move(
        entry: Entry,
        occurrenceDate: LocalDate?,
        toDate: LocalDate,
        toTime: LocalTime? = null,
        scope: MoveScope = MoveScope.ALL,
        now: Instant = Instant.now(),
    ): Entry {
        val spanDays = if (entry.startDate != null && entry.endDate != null) ChronoUnit.DAYS.between(entry.startDate, entry.endDate) else 0L
        val newStartTime = toTime ?: entry.startTime
        val spanMinutes = if (entry.startTime != null && entry.endTime != null) Duration.between(entry.startTime, entry.endTime) else null
        val newEndTime = if (newStartTime != null && spanMinutes != null) newStartTime.plus(spanMinutes) else if (entry.kind == EntryKind.EVENT) entry.endTime else null
        val newEndDate = if (entry.endDate != null) toDate.plusDays(spanDays) else null

        val isSeries = entry.recurrenceRule != null && entry.originalEntryId == null
        if (isSeries && scope == MoveScope.THIS_ONE && occurrenceDate != null) {
            // One occurrence steps out of the series: a row of its own that the expander shows
            // in the base's place on that date, and never a second copy of the base.
            val override = entry.copy(
                id = 0,
                uid = java.util.UUID.randomUUID().toString(),
                startDate = toDate, startTime = newStartTime, endDate = newEndDate, endTime = newEndTime,
                recurrenceRule = null,
                originalEntryId = entry.id, originalOccurrenceDate = occurrenceDate, isExceptionSkip = false,
                sourceRowId = null,
                createdAt = now, updatedAt = now,
            )
            val id = entryDao.insert(override)
            val stored = entryDao.getById(id)!!
            entryScheduleCoordinator.onEntryChanged(stored)
            return stored
        }
        // The series' anchor, or a plain entry, moves.
        return save(entry.copy(startDate = toDate, startTime = newStartTime, endDate = newEndDate, endTime = newEndTime), now)
    }

    /**
     * B§13.6 #5 — a block dropped back on the Calendar's tray: the When goes (date, time, the
     * span's end), the Deadline stays — two dates, two things (§0.6.4). A series keeps its days:
     * null, and the caller says so; an override row of a series is plain and clears like any
     * entry. Reminders on the old day are re-armed off through the coordinator, as every edit is.
     */
    suspend fun clearWhen(entry: Entry, now: Instant = Instant.now()): Entry? {
        if (entry.recurrenceRule != null && entry.originalEntryId == null) return null
        return save(entry.copy(startDate = null, startTime = null, endDate = null, endTime = null), now)
    }

    /** The §4 invariants, applied rather than assumed. */
    private fun Entry.normalisedForKind(): Entry = when (kind) {
        EntryKind.TASK -> copy(
            endDate = null, endTime = null,
            status = status ?: EntryStatus.PENDING,
            // A task repeats by period, never by RRULE.
            recurrenceRule = recurrenceRule?.takeIf { it is RecurrenceRule.Elastic },
            startTime = if (startDate == null) null else startTime,
        )
        EntryKind.EVENT -> copy(
            status = null, dueDate = null, estimate = null, importance = 0, parentEntryId = null,
            recurrenceRule = recurrenceRule?.takeIf { it is RecurrenceRule.Fixed },
            endDate = if (endTime != null || endDate != null) (endDate ?: startDate) else null,
            startTime = if (startDate == null) null else startTime,
            endTime = if (startDate == null) null else endTime,
        )
    }
}
