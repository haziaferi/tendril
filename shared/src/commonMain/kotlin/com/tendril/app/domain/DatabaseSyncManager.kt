package com.tendril.app.domain

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.pagedatabase.formatPeriodAsInterval
import com.tendril.app.data.pagedatabase.parseIntervalValue
import java.time.Instant
import java.time.LocalDate

/** [DEADLINE] fills `Entry.startDate` — the *When* — and is named for what §5.2 called it when
 * built. Kept as the storage and snapshot name (`deadlinePropertyId`/`deadlinePropertyUid`) so a
 * v9 peer's record keeps its meaning; the UI says "Date". §0.6.4's second binding, for
 * `Entry.dueDate`, is not this and does not exist yet. */
enum class BindingRole { DONE, DEADLINE, RECURRENCE, /** §0.8 step 2b — `Entry.dueDate`, the deadline proper. */ DUE_DATE }

/**
 * §5.2/§5.2.1 — the database-level Sync-to-Tasks toggle plus the `bindProperty`/
 * `unbindProperty` operations named there. Every linked Entry carries `sourceRowId` (§4),
 * the FK [ResolveEntryUseCase] and the Row checkbox both read/write through, and
 * `source = DATABASE_SYNC` to distinguish it from a manually-created Entry.
 *
 * Once bound, a role's property is a *live proxy*: its display value is always read from
 * the linked Entry (§5.2.1), never re-stored in `property_values` — that table is cleared
 * for a role's property on bind and repopulated only on unbind ("crystallizing" it back
 * into an ordinary stored column, frozen at the Entry's state at that instant).
 */
class DatabaseSyncManager(
    private val pageDao: PageDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val propertyValueDao: PropertyValueDao,
    private val entryDao: EntryDao,
    private val completionDao: EntryCompletionDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
) {
    /**
     * Turns Sync-to-Tasks on: records the three bindings and creates one linked Entry per
     * selected row in [rowIds] (an "All" shortcut is just every row's id, §5.5), seeding each
     * from whatever the bound properties already hold (§5.2.1). Rows that already carry a
     * live Entry are skipped, not duplicated — safe to call again after a partial run.
     */
    suspend fun enableSync(
        database: PageDatabase,
        donePropertyId: Long,
        deadlinePropertyId: Long?,
        recurrencePropertyId: Long?,
        rowIds: List<Long>,
        now: Instant = Instant.now(),
        dueDatePropertyId: Long? = null,
    ): PageDatabase {
        for (rowId in rowIds) {
            if (entryDao.getBySourceRowId(rowId) != null) continue
            val row = pageDao.getById(rowId) ?: continue

            val checked = propertyValueDao.getForPropertyAndRow(donePropertyId, rowId)?.value == "true"
            val startDate = deadlinePropertyId
                ?.let { propertyValueDao.getForPropertyAndRow(it, rowId)?.value }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val dueDate = dueDatePropertyId
                ?.let { propertyValueDao.getForPropertyAndRow(it, rowId)?.value }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val recurrenceRule = recurrencePropertyId
                ?.let { propertyValueDao.getForPropertyAndRow(it, rowId)?.value }
                ?.let(::parseIntervalValue)
                ?.let { (count, unit) -> RecurrenceRule.Elastic(intervalToPeriod(count, unit)) }

            val entryId = entryDao.insert(
                Entry(
                    title = row.title,
                    kind = EntryKind.TASK,
                    startDate = startDate,
                    startTime = null,
                    endDate = null,
                    endTime = null,
                    recurrenceRule = recurrenceRule,
                    dueDate = dueDate,
                    status = if (checked) EntryStatus.DONE else EntryStatus.PENDING,
                    sourceRowId = rowId,
                    source = EntrySource.DATABASE_SYNC,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            // §5.2.1 — a seeded DONE also writes one EntryCompletion row, `resolvedAt`
            // approximate (the moment of binding, not the row's real historical completion
            // time, which isn't recoverable).
            if (checked) {
                completionDao.insert(
                    EntryCompletion(entryId = entryId, occurrenceDate = startDate ?: LocalDate.now(), resolvedAt = now, status = EntryStatus.DONE)
                )
            }
        }
        for (propertyId in listOfNotNull(donePropertyId, deadlinePropertyId, recurrencePropertyId, dueDatePropertyId)) {
            propertyValueDao.deleteAllForProperty(propertyId)
        }

        val updated = database.copy(
            syncToTasks = true,
            donePropertyId = donePropertyId,
            deadlinePropertyId = deadlinePropertyId,
            dueDatePropertyId = dueDatePropertyId,
            recurrencePropertyId = recurrencePropertyId,
            updatedAt = now,
        )
        // Rows too, not just the database page: `deleteAllForProperty` above cleared the bound
        // columns' stored values for *every* row, whatever `rowIds` asked for.
        return commit(updated, now, touchRows = true)
    }

    /** §5.5 — bulk-cleanup: every row's linked Entry goes to Trash (restorable), bindings clear. */
    suspend fun disableSync(database: PageDatabase, now: Instant = Instant.now()): PageDatabase {
        for (row in pageDao.getMembersOf(database.id, database.labelId)) {
            entryDao.getBySourceRowId(row.id)?.let { resolveEntryUseCase.trash(it.id, now) }
        }
        val updated = database.copy(syncToTasks = false, donePropertyId = null, deadlinePropertyId = null, dueDatePropertyId = null, recurrencePropertyId = null, updatedAt = now)
        // The database page alone: this clears bindings and trashes Entries (their own snapshot
        // records, with their own timestamps) without rewriting any row's stored cell values.
        return commit(updated, now, touchRows = false)
    }

    /**
     * §5.2.1 `bindProperty` — binds [propertyId] to [role] on an already-syncing database (a
     * post-hoc bind of a previously-unbound optional role, or the "new" half of a rebind).
     * Crystallizes whatever was previously bound to this role first, then seeds every row's
     * Entry from the new property's existing stored values, then clears them (live proxy from
     * here on).
     */
    suspend fun bindProperty(database: PageDatabase, role: BindingRole, propertyId: Long, now: Instant = Instant.now()): PageDatabase {
        val rows = pageDao.getMembersOf(database.id, database.labelId)
        val oldPropertyId = database.propertyIdFor(role)
        if (oldPropertyId != null) crystallize(rows, oldPropertyId, role, now)

        for (row in rows) {
            val entry = entryDao.getBySourceRowId(row.id) ?: continue
            val newValue = propertyValueDao.getForPropertyAndRow(propertyId, row.id)?.value
            val updatedEntry = when (role) {
                BindingRole.DONE -> entry.copy(status = if (newValue == "true") EntryStatus.DONE else EntryStatus.PENDING, updatedAt = now)
                BindingRole.DEADLINE -> entry.copy(startDate = newValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, updatedAt = now)
                BindingRole.DUE_DATE -> entry.copy(dueDate = newValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, updatedAt = now)
                BindingRole.RECURRENCE -> entry.copy(
                    recurrenceRule = newValue?.let(::parseIntervalValue)?.let { (count, unit) -> RecurrenceRule.Elastic(intervalToPeriod(count, unit)) },
                    updatedAt = now,
                )
            }
            entryDao.update(updatedEntry)
        }
        propertyValueDao.deleteAllForProperty(propertyId)

        return commit(database.withPropertyIdFor(role, propertyId).copy(updatedAt = now), now, touchRows = true)
    }

    /** §5.2.1 `unbindProperty` — the reverse: crystallizes the role's current Entry-derived
     * value back into an ordinary stored column and stops proxying. */
    suspend fun unbindProperty(database: PageDatabase, role: BindingRole, now: Instant = Instant.now()): PageDatabase {
        val propertyId = database.propertyIdFor(role) ?: return database
        crystallize(pageDao.getMembersOf(database.id, database.labelId), propertyId, role, now)
        return commit(database.withPropertyIdFor(role, null).copy(updatedAt = now), now, touchRows = true)
    }

    /** §5.2.1 rebind — `unbindProperty` immediately followed by `bindProperty`, as one atomic
     * action. No Entry is ever deleted or recreated (`sourceRowId` is untouched throughout). */
    suspend fun rebindProperty(database: PageDatabase, role: BindingRole, newPropertyId: Long, now: Instant = Instant.now()): PageDatabase =
        bindProperty(unbindProperty(database, role, now), role, newPropertyId, now)

    /**
     * The single exit for every binding change — writing the row and saying the page changed are
     * one operation here, not a write followed by a bump someone has to remember.
     *
     * §9.4 (see [com.tendril.app.data.page.PageDao.touch]): a binding change rewrites two kinds
     * of synced content at once. The database's own snapshot carries `syncToTasks` and the three
     * role ids, and — through [crystallize], and the clear that follows a bind — the stored cell
     * values of every row under it change too. Rows are separate snapshot records with separate
     * timestamps, so bumping the database page alone would propagate the new bindings and strand
     * every row's crystallized value on this device, the values being the half that cannot be
     * recomputed from anywhere else. [touchRows] is false only for `disableSync`, which clears
     * bindings and trashes Entries (their own records, their own timestamps) without rewriting
     * any row's stored values.
     */
    private suspend fun commit(updated: PageDatabase, now: Instant, touchRows: Boolean): PageDatabase {
        pageDatabaseDao.update(updated)
        pageDao.touch(updated.pageId, now)
        if (touchRows) for (row in pageDao.getMembersOf(updated.id, updated.labelId)) pageDao.touch(row.id, now)
        return updated
    }

    /** A live-proxy property's `property_values` rows are already empty (cleared on bind) —
     * this only ever writes fresh rows, one per Row that has a linked Entry, never needs to
     * clear first. */
    private suspend fun crystallize(rows: List<com.tendril.app.data.page.Page>, propertyId: Long, role: BindingRole, now: Instant) {
        for (row in rows) {
            val entry = entryDao.getBySourceRowId(row.id) ?: continue
            val frozen = when (role) {
                BindingRole.DONE -> (entry.status == EntryStatus.DONE).toString()
                BindingRole.DEADLINE -> entry.startDate?.toString()
                BindingRole.DUE_DATE -> entry.dueDate?.toString()
                BindingRole.RECURRENCE -> (entry.recurrenceRule as? RecurrenceRule.Elastic)
                    ?.period?.let(::formatPeriodAsInterval)
            }
            if (frozen != null) propertyValueDao.insert(PropertyValue(propertyId = propertyId, rowPageId = row.id, value = frozen))
        }
    }

    private fun PageDatabase.propertyIdFor(role: BindingRole): Long? = when (role) {
        BindingRole.DONE -> donePropertyId
        BindingRole.DEADLINE -> deadlinePropertyId
        BindingRole.DUE_DATE -> dueDatePropertyId
        BindingRole.RECURRENCE -> recurrencePropertyId
    }

    private fun PageDatabase.withPropertyIdFor(role: BindingRole, propertyId: Long?): PageDatabase = when (role) {
        BindingRole.DONE -> copy(donePropertyId = propertyId)
        BindingRole.DEADLINE -> copy(deadlinePropertyId = propertyId)
        BindingRole.DUE_DATE -> copy(dueDatePropertyId = propertyId)
        BindingRole.RECURRENCE -> copy(recurrencePropertyId = propertyId)
    }
}
