package com.tendril.app.notifications

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.AppContainer
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.reminder.ReminderOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Audit 2026-09-24 5.1–5.3 — alarms that outlived what they were for, and alarms never set.
 * Counted from `dumpsys alarm`, the system's own view, for the reason
 * [AlarmSchedulerInstrumentedTest] gives. Entry ids sit far above any this device's database hands
 * out, so nothing here can cancel a real reminder.
 */
class ReminderRearmInstrumentedTest {

    private lateinit var context: Context

    /** The real DAO's contract, which is the point: [getForEntry] skips tombstoned rows. */
    private class SoftDeletingReminderDao(initial: List<Reminder>) : ReminderDao {
        val rows = initial.associateBy { it.id }.toMutableMap()
        override suspend fun insert(reminder: Reminder): Long = reminder.id.also { rows[it] = reminder }
        override fun observeForEntry(entryId: Long): Flow<List<Reminder>> = flowOf(live(entryId))
        override suspend fun getForEntry(entryId: Long): List<Reminder> = live(entryId)
        override suspend fun getAllForEntry(entryId: Long): List<Reminder> = rows.values.filter { it.entryId == entryId }
        override suspend fun getAll(): List<Reminder> = rows.values.toList()
        override suspend fun getByUid(uid: String): Reminder? = rows.values.firstOrNull { it.uid == uid }
        override suspend fun deleteAll() = Unit
        override suspend fun softDelete(id: Long, deletedAt: Instant) {
            rows[id]?.let { rows[id] = it.copy(deletedAt = deletedAt) }
        }
        private fun live(entryId: Long) = rows.values.filter { it.entryId == entryId && it.deletedAt == null }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun task(id: Long, daysAhead: Long) = Entry(
        id = id, title = "Rearm test $id", kind = EntryKind.TASK,
        startDate = LocalDate.now().plusDays(daysAhead), startTime = LocalTime.of(9, 0),
        endDate = null, endTime = null, recurrenceRule = null, status = EntryStatus.PENDING,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    private fun scheduledAlarmCount(): Int {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        return dump.lineSequence().count { it.contains(context.packageName) && it.contains("Alarm{") }
    }

    /** 5.1 — a reminder deleted on the other device arrives here as a tombstone. */
    @Test
    fun aReminderDeletedElsewhereIsDisarmedByTheNextReschedule() = runBlocking {
        val entryId = 90_101L
        val dao = SoftDeletingReminderDao(
            listOf(Reminder(id = 7, entryId = entryId, offset = ReminderOffset.FromPreset(ReminderOffset.Preset.entries.first()))),
        )
        val scheduler = AlarmScheduler(context, dao)
        scheduler.cancelAllFor(entryId)
        val baseline = scheduledAlarmCount()
        val entry = task(entryId, daysAhead = 4)
        scheduler.rescheduleFor(entry)
        assertEquals("fixture: the overdue alarm and the reminder's", baseline + 2, scheduledAlarmCount())

        dao.softDelete(7, Instant.now())
        scheduler.rescheduleFor(entry)

        try {
            assertEquals("the deleted reminder's alarm is still armed", baseline + 1, scheduledAlarmCount())
        } finally {
            scheduler.cancelReminder(entryId, 7)
            scheduler.cancelAllFor(entryId)
        }
    }

    /** 5.2's mechanism, as a control: rescheduling a done task cancels its alarm, so the import
     * only has to name the entries it changed. */
    @Test
    fun reschedulingATaskMadeDoneDisarmsIt() = runBlocking {
        val entryId = 90_102L
        val scheduler = AlarmScheduler(context, SoftDeletingReminderDao(emptyList()))
        scheduler.cancelAllFor(entryId)
        val baseline = scheduledAlarmCount()
        scheduler.rescheduleFor(task(entryId, daysAhead = 4))
        assertEquals(baseline + 1, scheduledAlarmCount())

        scheduler.rescheduleFor(task(entryId, daysAhead = 4).copy(status = EntryStatus.DONE))

        assertEquals(baseline, scheduledAlarmCount())
    }

    /**
     * 5.3 — `reconcileAlarms` is the sweep that runs at every launch, after every boot and after
     * every archive import, and it swept entries only. A reboot drops every alarm, so after one
     * no habit reminder rang until the habit was checked in or edited.
     */
    @Test
    fun theLaunchAndBootSweepArmsHabits() = runBlocking {
        val container = AppContainer.from(context)
        val habitDao = container.database.habitDao()
        val id = habitDao.insert(
            Habit(title = "Rearm test habit", frequency = HabitFrequency(1, IntervalUnit.DAY), time = LocalTime.of(23, 59),
                createdAt = Instant.now(), updatedAt = Instant.now()),
        )
        try {
            reconcileAlarms(context)
            container.alarmScheduler.cancelHabit(id)
            val withoutIt = scheduledAlarmCount()

            reconcileAlarms(context)

            assertEquals("the sweep did not arm the habit", withoutIt + 1, scheduledAlarmCount())
        } finally {
            container.alarmScheduler.cancelHabit(id)
            habitDao.deleteForever(id)
        }
    }
}
