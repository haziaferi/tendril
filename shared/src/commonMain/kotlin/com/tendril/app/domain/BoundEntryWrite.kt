package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import java.time.Instant

/**
 * §5.2.1 — one field of a Row's bound Entry, written from a table cell or the Row's own page.
 *
 * Laid over the entry **as stored now**, never the copy the screen drew (5a.8's class, found in
 * both bound-cell paths on 2026-09-26): written whole, that copy undid a tick, a trash or a Google
 * id that landed since it was drawn. Unchanged writes nothing, since under §9.4 the stamp alone
 * claims an edit (5a.6). [rearm] for a field that moves what rings — the When, the repeat — and not
 * for the deadline, which fires nothing by itself (§0.6.4).
 */
suspend fun writeBoundEntryField(
    entryDao: EntryDao,
    coordinator: EntryScheduleCoordinator,
    entryId: Long,
    rearm: Boolean,
    now: Instant = Instant.now(),
    change: (Entry) -> Entry,
) {
    val stored = entryDao.getById(entryId) ?: return
    val changed = change(stored)
    if (changed == stored) return
    val updated = changed.copy(updatedAt = now)
    entryDao.update(updated)
    if (rearm) coordinator.onEntryChanged(updated)
}
