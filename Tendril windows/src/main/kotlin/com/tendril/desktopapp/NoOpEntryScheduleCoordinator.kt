package com.tendril.desktopapp

import com.tendril.app.data.entry.Entry
import com.tendril.app.domain.EntryScheduleCoordinator

/** Desktop has no alarm scheduler or Calendar Provider to notify (tendril-windows-spec.md §1) —
 * Row deadline/recurrence edits still persist normally, they just don't also reschedule
 * anything platform-level. */
object NoOpEntryScheduleCoordinator : EntryScheduleCoordinator {
    override suspend fun onEntryChanged(entry: Entry) {}
    override suspend fun onEntryRemoved(entry: Entry) {}
}
