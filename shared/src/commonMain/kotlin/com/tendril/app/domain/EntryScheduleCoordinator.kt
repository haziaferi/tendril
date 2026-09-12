package com.tendril.app.domain

import com.tendril.app.data.entry.Entry

/**
 * §9.8 R1's centralization principle, applied to a second fan-out. Every write path that
 * changes a schedulable Entry needs its alarms and (Android's) Calendar Provider mirror kept
 * in sync — [ResolveEntryUseCase] calls through here rather than touching either directly.
 *
 * §12.5 — lives in `:shared` as an interface, not a concrete class: alarms and Calendar
 * Provider are Android-only platform APIs with no desktop equivalent (§12.1), so each platform
 * supplies its own implementation. Android's is `AndroidEntryScheduleCoordinator` (`:app`).
 */
interface EntryScheduleCoordinator {
    suspend fun onEntryChanged(entry: Entry)
    suspend fun onEntryRemoved(entry: Entry)

    /**
     * A Habit is going away and its reminder must go with it (§9.7, audit §3).
     *
     * Same hazard [onEntryRemoved] exists for: an alarm that outlives its row is a wakeup for
     * nothing, and it matters most for a purge arriving from *another* device, where this side
     * never saw the deletion coming. Takes an id rather than a Habit because the caller reaches
     * it after the row is already identified for deletion, and there is nothing else to mirror.
     *
     * Defaulted to a no-op: desktop has no alarms at all (§12.1), and a platform that cannot
     * schedule has nothing to cancel.
     */
    suspend fun onHabitRemoved(habitId: Long) {}

    /** A Habit was added or its time changed, so its reminder must follow (§9.7). Defaulted to
     * a no-op for the same reason as [onHabitRemoved]; §0.8 step 7a moved the Tasks & Habits
     * screen to `shared/`, and this is the seam that lets it stop knowing `AlarmScheduler`. */
    suspend fun onHabitChanged(habit: com.tendril.app.data.habit.Habit) {}
}
