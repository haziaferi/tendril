package com.tendril.app.domain

import com.tendril.app.data.habit.HabitDao
import java.time.Instant

/**
 * §5.5.1 — habits back out of Trash. Out of `HabitTrashSheet` so it can be tested: the sheet is a
 * composable, and the one thing worth pinning here is what happens after the row comes back.
 */
suspend fun restoreHabitsFromTrash(
    habitDao: HabitDao,
    entryScheduleCoordinator: EntryScheduleCoordinator,
    ids: Collection<Long>,
    now: Instant = Instant.now(),
) {
    for (id in ids) {
        habitDao.restore(id, now)
        // A habit back from Trash is due again, and until 2026-09-26 (audit 5.3) nothing armed
        // it: its reminder stayed unset until the next check-in or edit.
        habitDao.getById(id)?.let { entryScheduleCoordinator.onHabitChanged(it) }
    }
}
