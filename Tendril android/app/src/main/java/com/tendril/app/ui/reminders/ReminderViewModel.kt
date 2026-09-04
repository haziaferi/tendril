package com.tendril.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.reminder.ReminderOffset
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.notifications.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * §5.4 — the reminder list for one Entry. Reminders stack with no cap, so this is a plain
 * list-plus-add surface rather than a single-value picker.
 *
 * Every mutation reschedules through [EntryScheduleCoordinator], the one path §9.7 requires of
 * every write that can change which alarms should exist — **with one exception this class is
 * honest about rather than hiding**: removing a reminder cancels its alarm via [AlarmScheduler]
 * directly first, because `rescheduleFor` derives what to cancel by re-reading the reminder rows,
 * and a row already deleted leaves its alarm armed with nothing left to describe it. Earlier text
 * here claimed "the sheet never touches AlarmManager itself," which the constructor below plainly
 * contradicts. The real fix is an `onReminderRemoved(entryId, reminderId)` on the coordinator so
 * this class can drop the [AlarmScheduler] dependency entirely and §9.11's "no call site touches
 * AlarmScheduler" holds without an asterisk — not done here, since it changes an interface two
 * platforms implement.
 */
class ReminderViewModel(
    private val entryId: Long,
    private val entryDao: EntryDao,
    private val reminderDao: ReminderDao,
    private val alarmScheduler: AlarmScheduler,
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
            // Cancel *before* the row is deleted. `rescheduleFor` cancels by reading the
            // reminder rows back out of the DAO, so a reminder deleted first is invisible to
            // it — the row goes but its alarm stays registered and still fires.
            alarmScheduler.cancelReminder(entryId, reminderId)
            reminderDao.delete(reminderId)
            reschedule()
        }
    }

    private suspend fun reschedule() {
        entryDao.getById(entryId)?.let { entryScheduleCoordinator.onEntryChanged(it) }
    }
}
