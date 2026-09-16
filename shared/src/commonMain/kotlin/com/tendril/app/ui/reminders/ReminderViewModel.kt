package com.tendril.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.reminder.ReminderOffset
import com.tendril.app.domain.EntryScheduleCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime

/**
 * §5.4 — the reminder list for one Entry. Reminders stack with no cap, so this is a plain
 * list-plus-add surface rather than a single-value picker.
 *
 * Every mutation reschedules through [EntryScheduleCoordinator], the one path §9.7 requires of
 * every write that can change which alarms should exist. Removing a reminder tells the
 * coordinator *before* the row is soft-deleted (`onReminderRemoved`, added by B§13.6 #7 so this
 * class could move to `shared/`): the platform's reschedule derives what to cancel by re-reading
 * the reminder rows, and a row already gone would leave its alarm armed with nothing left to
 * describe it. No call site touches a platform scheduler (§9.11) — without an asterisk now.
 */
class ReminderViewModel(
    private val entryId: Long,
    private val entryDao: EntryDao,
    private val reminderDao: ReminderDao,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) : ViewModel() {
    val reminders: StateFlow<List<Reminder>> =
        reminderDao.observeForEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(offset: ReminderOffset, anchorTime: LocalTime?) {
        viewModelScope.launch {
            reminderDao.insert(Reminder(entryId = entryId, offset = offset, anchorTime = anchorTime))
            reschedule()
        }
    }

    fun remove(reminderId: Long) {
        viewModelScope.launch {
            // Before the row goes — see the class doc.
            entryScheduleCoordinator.onReminderRemoved(entryId, reminderId)
            reminderDao.softDelete(reminderId, Instant.now())
            reschedule()
        }
    }

    private suspend fun reschedule() {
        entryDao.getById(entryId)?.let { entryScheduleCoordinator.onEntryChanged(it) }
    }
}
