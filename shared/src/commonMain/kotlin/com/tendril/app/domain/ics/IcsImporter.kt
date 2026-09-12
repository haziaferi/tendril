package com.tendril.app.domain.ics

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.EntryScheduleCoordinator
import java.time.Instant
import java.time.Period
import java.time.ZoneId

data class IcsImportResult(val created: Int, val updated: Int, val skipped: Int)

/**
 * §0.8 step 6e — an `.ics` file into Entries. Matched by `UID` against `Entry.uid`, so importing
 * the same file twice, or Tendril's own export, updates rather than duplicates; a `VEVENT` is an
 * event, a `VTODO` a task. A component with `RECURRENCE-ID` is one occurrence of a series moved
 * (§4.1's override row): its base is the component sharing its `UID` (Google, Apple) or the one
 * named by `RELATED-TO` (Tendril's own export, which gives overrides their own uid). `EXDATE`s
 * become skip rows. Every written row goes through [EntryScheduleCoordinator.onEntryChanged],
 * as every write path must.
 */
class IcsImporter(
    private val entryDao: EntryDao,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) {
    suspend fun import(text: String, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): IcsImportResult {
        val components = IcsReader.read(text, zone)
        var created = 0; var updated = 0; var skipped = 0
        val baseByUid = mutableMapOf<String, Entry>()

        // Pass 1 — bases and plain entries.
        for (c in components.filter { it.recurrenceId == null }) {
            if (c.startDate == null && c.kind == EntryKind.EVENT) { skipped++; continue }
            val existing = entryDao.getByUid(c.uid)
            val row = c.toEntry(existing, now)
            val id = if (existing == null) { created++; entryDao.insert(row) } else { updated++; entryDao.update(row); row.id }
            val stored = entryDao.getById(id) ?: continue
            entryScheduleCoordinator.onEntryChanged(stored)
            baseByUid[c.uid] = stored
            // Skips — one tombstone per EXDATE that has none yet.
            val existingSkips = entryDao.getExceptionsOf(stored.id).filter { it.isExceptionSkip == true }.mapNotNull { it.originalOccurrenceDate }.toSet()
            for (date in c.exdates) {
                if (date in existingSkips) continue
                entryDao.insert(
                    stored.copy(
                        id = 0, uid = java.util.UUID.randomUUID().toString(), recurrenceRule = null,
                        originalEntryId = stored.id, originalOccurrenceDate = date, isExceptionSkip = true,
                        startDate = date, createdAt = now, updatedAt = now,
                    )
                )
            }
        }

        // Pass 2 — moved occurrences.
        for (c in components.filter { it.recurrenceId != null }) {
            val base = baseByUid[c.uid] ?: c.relatedTo?.let { baseByUid[it] ?: entryDao.getByUid(it) }
            if (base == null || c.startDate == null) { skipped++; continue }
            val existing = entryDao.getExceptionsOf(base.id).firstOrNull { it.isExceptionSkip != true && it.originalOccurrenceDate == c.recurrenceId }
            val row = c.toEntry(existing, now).copy(
                uid = existing?.uid ?: (if (c.uid == base.uid) java.util.UUID.randomUUID().toString() else c.uid),
                recurrenceRule = null, originalEntryId = base.id, originalOccurrenceDate = c.recurrenceId, isExceptionSkip = false,
            )
            val id = if (existing == null) { created++; entryDao.insert(row) } else { updated++; entryDao.update(row); row.id }
            entryDao.getById(id)?.let { entryScheduleCoordinator.onEntryChanged(it) }
        }
        return IcsImportResult(created, updated, skipped)
    }

    /** The component's fields over [existing] (kept: id, uid, source links, created time), or a new row. */
    private fun IcsComponent.toEntry(existing: Entry?, now: Instant): Entry {
        val isTask = kind == EntryKind.TASK
        val rule = rrule?.let { r ->
            if (isTask) periodOf(r)?.let { RecurrenceRule.Elastic(it) } else RecurrenceRule.Fixed(r)
        }
        val base = existing ?: Entry(
            uid = uid, title = summary, kind = kind, startDate = null, startTime = null, endDate = null, endTime = null,
            recurrenceRule = null, createdAt = now, updatedAt = now,
        )
        return base.copy(
            title = summary.ifBlank { base.title.ifBlank { "Untitled" } },
            kind = kind,
            startDate = startDate,
            startTime = if (startDate != null) startTime else null,
            endDate = if (!isTask) endDate else null,
            endTime = if (!isTask && startTime != null) endTime else null,
            recurrenceRule = if (startDate != null) rule else null,
            status = if (isTask) (if (completed) EntryStatus.DONE else EntryStatus.PENDING) else null,
            dueDate = if (isTask) due else null,
            updatedAt = now,
        )
    }

    /** A task's `RRULE` back to the period it was written from; anything the period cannot hold is dropped. */
    private fun periodOf(rrule: String): Period? {
        val parts = rrule.split(";").associate { it.substringBefore("=").uppercase() to it.substringAfter("=", "") }
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        return when (parts["FREQ"]) {
            "DAILY" -> Period.ofDays(interval)
            "WEEKLY" -> Period.ofWeeks(interval)
            "MONTHLY" -> Period.ofMonths(interval)
            else -> null
        }
    }
}
