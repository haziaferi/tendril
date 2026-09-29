package com.tendril.app.calendarprovider

import com.tendril.app.data.calendar.CalendarLink
import com.tendril.app.data.calendar.CalendarLinkDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.calendar.CalendarItemFields
import com.tendril.app.domain.calendar.CalendarItemKey
import com.tendril.app.domain.calendar.ReconcileStep
import com.tendril.app.domain.calendar.planCalendarReconcile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The reads and writes [CalendarReadBack] makes of one synced calendar; [SystemCalendars] on the phone. */
interface CalendarStore {
    fun rows(calendarId: Long): List<SystemCalendarRow>
    fun insert(calendarId: Long, values: Map<String, Any?>): Long?
    fun update(rowId: Long, values: Map<String, Any?>): Boolean
    fun insertException(seriesRowId: Long, values: Map<String, Any?>): Long?
    fun delete(rowId: Long): Boolean
}

/** What one pass did, for Calendar settings to report. */
data class ReadBackResult(
    val created: Int = 0,
    val updated: Int = 0,
    val written: Int = 0,
    val trashed: Int = 0,
    val deleted: Int = 0,
    /** Titles of Tendril versions a calendar edit overrode (§9.12: the calendar wins a conflict). */
    val conflicts: List<String> = emptyList(),
    /** Titles skipped because the same event is already in Tendril from another calendar — the
     * same server calendar reached through two sync apps, both ticked. */
    val duplicates: List<String> = emptyList(),
)

/**
 * §9.12 — one read-back pass over one ticked calendar: Tendril's events in it, the calendar's rows,
 * and the links, through [planCalendarReconcile]; then each step carried out. Entries are written
 * through the coordinator like any other edit (§9.7), with `updatedAt` the moment of the pass, so an
 * edit made elsewhere reaches the desktop through the folder sync as an ordinary one. Trash goes
 * through [ResolveEntryUseCase] so a series takes its exceptions with it (§5.5.1).
 */
class CalendarReadBack(
    private val entryDao: EntryDao,
    private val linkDao: CalendarLinkDao,
    private val store: CalendarStore,
    private val coordinator: EntryScheduleCoordinator,
    private val resolve: ResolveEntryUseCase,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun sync(calendarKey: String, calendarId: Long, now: Instant = Instant.now()): ReadBackResult {
        val all = entryDao.getAll()
        val series = all.filter { it.kind == EntryKind.EVENT && it.originalEntryId == null && it.calendarKey == calendarKey && it.deletedAt == null }
        val seriesById = series.associateBy { it.id }
        val tendril = LinkedHashMap<CalendarItemKey, CalendarItemFields>()
        for (entry in series) tendril[CalendarItemKey(entry.uid, null)] = entry.itemFields()
        for (exception in all) {
            val base = exception.originalEntryId?.let(seriesById::get) ?: continue
            if (exception.deletedAt == null && exception.originalOccurrenceDate != null) tendril[CalendarItemKey(base.uid, exception.originalOccurrenceDate)] = exception.itemFields()
        }

        val links = linkDao.getForCalendar(calendarKey).associate { CalendarItemKey(it.entryUid, it.occurrence.ifEmpty { null }?.let(LocalDate::parse)) to it.fingerprint }
        val providerItems = rowsToItems(store.rows(calendarId), zone)

        // An event whose UID already belongs to an entry outside this calendar, never linked here:
        // the same server event through a second sync app. Creating it would duplicate it (and
        // break the uid's uniqueness), so it is reported instead.
        val elsewhere = all.filter { it.originalEntryId == null && it.calendarKey != calendarKey }.map { it.uid }.toSet()
        val duplicateUids = providerItems.keys.filter { it.uid in elsewhere && it !in links }.map { it.uid }.toSet()
        val duplicates = providerItems.filterKeys { it.uid in duplicateUids && it.occurrence == null }.values.map { it.fields.title }
        val provider = providerItems.filterKeys { it.uid !in duplicateUids }.mapValues { it.value.fields }

        var result = ReadBackResult(duplicates = duplicates)
        val insertedSeries = HashMap<String, Long>()
        // A series before its exceptions: an exception needs its series in place on both sides.
        for (step in planCalendarReconcile(tendril, provider, links).sortedBy { it.key.occurrence != null }) {
            val k = step.key
            when (step) {
                is ReconcileStep.TakeFromProvider -> {
                    if (step.conflict) tendril[k]?.let { result = result.copy(conflicts = result.conflicts + it.title) }
                    val created = take(k, step.fields, calendarKey, now) ?: continue
                    result = if (created) result.copy(created = result.created + 1) else result.copy(updated = result.updated + 1)
                    link(calendarKey, k, step.fields)
                }
                is ReconcileStep.WriteToProvider -> {
                    if (!write(k, step.fields, calendarId, providerItems, tendril, insertedSeries)) continue
                    result = result.copy(written = result.written + 1)
                    link(calendarKey, k, step.fields)
                }
                is ReconcileStep.TrashInTendril -> {
                    entryFor(k, includeTrashed = false)?.let { resolve.trash(it.id, now) }
                    result = result.copy(trashed = result.trashed + 1)
                    linkDao.delete(calendarKey, k.uid, k.occurrence.text())
                }
                is ReconcileStep.DeleteFromProvider -> {
                    providerItems[k]?.let { store.delete(it.rowId) }
                    result = result.copy(deleted = result.deleted + 1)
                    linkDao.delete(calendarKey, k.uid, k.occurrence.text())
                }
                is ReconcileStep.Relink -> linkDao.upsert(CalendarLink(calendarKey, k.uid, k.occurrence.text(), step.fingerprint))
                is ReconcileStep.Forget -> linkDao.delete(calendarKey, k.uid, k.occurrence.text())
            }
        }
        return result
    }

    /** Stores [fields] as the entry for [k]; true when it was created, null when it cannot be
     * placed (an exception whose series is not in Tendril). A trashed entry comes back. */
    private suspend fun take(k: CalendarItemKey, fields: CalendarItemFields, calendarKey: String, now: Instant): Boolean? {
        val existing = entryFor(k, includeTrashed = true)
        if (existing != null) {
            val updated = existing.withFields(fields).copy(calendarKey = calendarKey, deletedAt = null, updatedAt = now)
            entryDao.update(updated)
            coordinator.onEntryChanged(updated)
            return false
        }
        val base = if (k.occurrence == null) null else entryDao.getByUid(k.uid) ?: return null
        val fresh = Entry(
            uid = if (k.occurrence == null) k.uid else java.util.UUID.randomUUID().toString(),
            title = fields.title, kind = EntryKind.EVENT, startDate = null, startTime = null, endDate = null, endTime = null,
            recurrenceRule = null, originalEntryId = base?.id, originalOccurrenceDate = k.occurrence,
            isExceptionSkip = if (k.occurrence == null) null else fields.isSkip,
            source = EntrySource.CALENDAR, calendarKey = calendarKey, createdAt = now, updatedAt = now,
        ).withFields(fields)
        val id = entryDao.insert(fresh)
        entryDao.getById(id)?.let { coordinator.onEntryChanged(it) }
        return true
    }

    private fun write(
        k: CalendarItemKey,
        fields: CalendarItemFields,
        calendarId: Long,
        providerItems: Map<CalendarItemKey, ProviderItem>,
        tendril: Map<CalendarItemKey, CalendarItemFields>,
        insertedSeries: MutableMap<String, Long>,
    ): Boolean {
        val existingRow = providerItems[k]?.rowId
        val occurrence = k.occurrence
        if (occurrence == null) {
            val values = itemValues(fields, k.uid, zone)
            if (existingRow != null) return store.update(existingRow, values)
            val id = store.insert(calendarId, values) ?: return false
            insertedSeries[k.uid] = id
            return true
        }
        val seriesKey = CalendarItemKey(k.uid, null)
        val seriesRow = providerItems[seriesKey]?.rowId ?: insertedSeries[k.uid] ?: return false
        val values = exceptionValues(fields, occurrence, tendril[seriesKey]?.startTime, zone)
        return if (existingRow != null) store.update(existingRow, values - "originalInstanceTime")
        else store.insertException(seriesRow, values) != null
    }

    private suspend fun entryFor(k: CalendarItemKey, includeTrashed: Boolean): Entry? {
        val all = entryDao.getAll()
        val base = all.firstOrNull { it.uid == k.uid && it.originalEntryId == null } ?: return null
        val entry = if (k.occurrence == null) base else all.firstOrNull { it.originalEntryId == base.id && it.originalOccurrenceDate == k.occurrence }
        return entry?.takeIf { includeTrashed || it.deletedAt == null }
    }

    private suspend fun link(calendarKey: String, k: CalendarItemKey, fields: CalendarItemFields) =
        linkDao.upsert(CalendarLink(calendarKey, k.uid, k.occurrence.text(), fields.fingerprint()))
}

private fun LocalDate?.text() = this?.toString().orEmpty()

private fun Entry.itemFields() = CalendarItemFields(
    title, startDate, startTime, endDate, endTime, (recurrenceRule as? RecurrenceRule.Fixed)?.rrule, isExceptionSkip == true,
)

private fun Entry.withFields(f: CalendarItemFields) = copy(
    title = f.title, startDate = f.startDate, startTime = f.startTime, endDate = f.endDate, endTime = f.endTime,
    recurrenceRule = if (originalEntryId != null) null else f.rrule?.let { RecurrenceRule.Fixed(it) },
    isExceptionSkip = if (originalEntryId != null) f.isSkip else isExceptionSkip,
)
