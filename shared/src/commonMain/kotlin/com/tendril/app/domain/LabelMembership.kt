package com.tendril.app.domain

import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageLabel
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import java.time.Instant

/**
 * §0.6.8 / B§12 — schema on a label, shape A. A database may *bind* a label; a page carrying
 * that label is a member of the database — a full row in its views, its fields in the page's
 * header — while keeping its own place in the tree. This class is the one place a membership
 * change turns into the Task consequences §5.2 attaches to being a row, the way
 * [DatabaseSyncManager] is the one place a binding change does. Everything else about
 * membership is a query ([PageDao.getMembersOf]) and needs no code here at all.
 *
 * The rules, each the smallest that keeps the acceptance in §0.6.8 true:
 *
 * - **Applying** a label whose database syncs to Tasks makes the page a task, because that is
 *   what being a row of that database means. If the page had a task before — it carried the
 *   label once and lost it — that task comes back from Trash rather than a fresh one being
 *   seeded, so unlabel → relabel loses nothing, Done state included.
 * - **Removing** it sends the task to Trash (restorable), unless something else still makes the
 *   page a task: it is a native row of a syncing database, or another of its labels opens a
 *   syncing database. Its stored values are untouched; nothing lists them once membership
 *   ends, and the label coming back shows them again. They purge with the database (§5.5.1.1).
 * - A page is **one task** however many syncing databases show it — one `sourceRowId`, so both
 *   databases' bound columns proxy the same Entry.
 * - **Native rows are not auto-labelled.** Membership is the union, so the label adds nothing
 *   to a native row, and applying it to every existing row would touch every row's `updatedAt`
 *   — an authorship claim under last-write-wins (§9.4) for a change that changes nothing.
 * - The first time a label is applied while its database syncs, the app **asks once**
 *   ([needsConfirmation]); the answer is [PageDatabase.labelConfirmed], on the database, so it
 *   travels.
 */
class LabelMembership(
    private val pageDao: PageDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val labelDao: LabelDao,
    private val entryDao: EntryDao,
    private val databaseSyncManager: DatabaseSyncManager,
    private val resolveEntryUseCase: ResolveEntryUseCase,
) {
    /** The syncing database bound to [label] that has not yet been acknowledged, or null when
     * applying the label needs no dialog: no such database, or it was confirmed before. */
    suspend fun needsConfirmation(label: Label): PageDatabase? =
        pageDatabaseDao.getByLabelId(label.id).firstOrNull { it.syncToTasks && !it.labelConfirmed }

    /** Records the acknowledgement so the question is never asked again for this database. */
    suspend fun confirm(database: PageDatabase, now: Instant = Instant.now()): PageDatabase {
        val updated = database.copy(labelConfirmed = true, updatedAt = now)
        pageDatabaseDao.update(updated)
        pageDao.touch(database.pageId, now)
        return updated
    }

    /**
     * Puts [label] on [pageId] and applies the consequences. Returns false when the page already
     * carried the label, in which case nothing was written — the caller's bump is conditional
     * for the same reason `PageDetailViewModel.addLabel`'s always was.
     */
    suspend fun applyLabel(pageId: Long, label: Label, now: Instant = Instant.now()): Boolean {
        if (labelDao.getForPage(pageId).any { it.id == label.id }) return false
        labelDao.addToPage(PageLabel(pageId = pageId, tagId = label.id))
        for (database in pageDatabaseDao.getByLabelId(label.id)) {
            if (database.syncToTasks) ensureTask(pageId, database, now)
        }
        return true
    }

    /** Takes [label] off [pageId]; the task goes to Trash unless another membership keeps it. */
    suspend fun removeLabel(pageId: Long, label: Label, now: Instant = Instant.now()) {
        labelDao.removeFromPage(pageId, label.id)
        retireTaskIfOrphaned(pageId, now)
    }

    /** §0.6.8 — makes [labelId] the doorway of [database]. Members it gains are read live by
     * the views; if the database syncs, they become tasks now, exactly as if each had just been
     * labelled — the confirmation is not asked here, the bind sheet's copy already says so. */
    suspend fun bindLabel(database: PageDatabase, labelId: Long, now: Instant = Instant.now()): PageDatabase {
        val previous = database.labelId
        // The label it already has changes nothing in the database's record, so nothing is written
        // or touched (audit 5a.6, §9.4); the members' tasks below are still ensured, idempotently.
        val updated = if (previous == labelId) database else database.copy(labelId = labelId, updatedAt = now)
        if (updated !== database) {
            pageDatabaseDao.update(updated)
            pageDao.touch(database.pageId, now)
        }
        if (previous != null && previous != labelId) retireOrphansOf(updated, previous, now)
        if (updated.syncToTasks) {
            for (member in pageDao.getMembersOf(updated.id, labelId)) {
                if (member.databaseId != updated.id) ensureTask(member.id, updated, now)
            }
        }
        return updated
    }

    /** The reverse: labelled members leave the views, and their tasks go to Trash unless
     * something else keeps them. Native rows are untouched. */
    suspend fun unbindLabel(database: PageDatabase, now: Instant = Instant.now()): PageDatabase {
        val previous = database.labelId ?: return database
        val updated = database.copy(labelId = null, labelConfirmed = false, updatedAt = now)
        pageDatabaseDao.update(updated)
        pageDao.touch(database.pageId, now)
        retireOrphansOf(updated, previous, now)
        return updated
    }

    /** The page has a live task from now on: the trashed one restored if there is one, or a
     * fresh one seeded through the same per-row step `addRow` uses. Idempotent. */
    private suspend fun ensureTask(pageId: Long, database: PageDatabase, now: Instant) {
        if (entryDao.getBySourceRowId(pageId) != null) return
        val trashed = entryDao.getTrashedBySourceRowId(pageId)
        if (trashed != null) {
            resolveEntryUseCase.restore(trashed.id, now)
            return
        }
        databaseSyncManager.addRowToSync(database, pageId, now)
    }

    /** Every page that was a member of [database] through [labelId] and is not native to it. */
    private suspend fun retireOrphansOf(database: PageDatabase, labelId: Long, now: Instant) {
        for (member in pageDao.getMembersOf(database.id, labelId)) {
            if (member.databaseId != database.id) retireTaskIfOrphaned(member.id, now)
        }
    }

    /** Trashes the page's task when no membership of a syncing database remains. */
    private suspend fun retireTaskIfOrphaned(pageId: Long, now: Instant) {
        val entry = entryDao.getBySourceRowId(pageId) ?: return
        if (isTaskStillDue(pageId)) return
        resolveEntryUseCase.trash(entry.id, now)
    }

    private suspend fun isTaskStillDue(pageId: Long): Boolean {
        val page = pageDao.getById(pageId) ?: return false
        val home = page.databaseId?.let { pageDatabaseDao.getById(it) }
        if (home?.syncToTasks == true) return true
        return labelDao.getForPage(pageId).any { label ->
            pageDatabaseDao.getByLabelId(label.id).any { it.syncToTasks }
        }
    }
}
