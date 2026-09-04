package com.tendril.app.domain

import com.tendril.app.calendarprovider.CalendarProviderSync
import com.tendril.app.data.entry.Entry
import com.tendril.app.notifications.AlarmScheduler

/**
 * Android's [EntryScheduleCoordinator] (§12.5 — the interface moved to `:shared` so
 * [ResolveEntryUseCase] doesn't depend on Android-only APIs): mirrors a changed/removed Entry
 * into both [AlarmScheduler] and [CalendarProviderSync], the same "one coordinator, one place
 * both concerns fire from" fix §9.8 R1 already established for Entry resolution itself.
 */
class AndroidEntryScheduleCoordinator(
    private val alarmScheduler: AlarmScheduler,
    private val calendarProviderSync: CalendarProviderSync,
) : EntryScheduleCoordinator {
    override suspend fun onEntryChanged(entry: Entry) {
        alarmScheduler.rescheduleFor(entry)
        calendarProviderSync.upsertEntry(entry)
    }

    override suspend fun onEntryRemoved(entry: Entry) {
        alarmScheduler.cancelAllFor(entry.id)
        calendarProviderSync.removeEntry(entry)
    }

    /** No Calendar Provider half: a Habit is not mirrored there (§3.3 — structurally separate
     * from Task), so the alarm is the whole of what it leaves behind. */
    override suspend fun onHabitRemoved(habitId: Long) {
        alarmScheduler.cancelHabit(habitId)
    }
}
