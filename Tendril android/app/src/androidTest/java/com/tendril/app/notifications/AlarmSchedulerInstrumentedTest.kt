package com.tendril.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.reminder.ReminderOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull

import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * On-device tests for ALARM-01 and ALARM-02.
 *
 * The ALARM-01 crash only reproduces on API 31-32, where `USE_EXACT_ALARM` doesn't exist yet and
 * `SCHEDULE_EXACT_ALARM` is required — so on any other API level these tests can only confirm
 * the guarded path still schedules correctly rather than reproduce the original
 * `SecurityException`. That's still worth having: `canScheduleExactAlarms()` is a real call on
 * API 31+, and nothing had ever exercised it.
 *
 * Whether an alarm is really scheduled is read from `dumpsys alarm` — the system's own view.
 * `PendingIntent.FLAG_NO_CREATE` looks like it should answer that but doesn't: `AlarmManager
 * .cancel()` leaves the PendingIntent registered, and `cancelAllFor` constructs one itself in
 * order to cancel, so it reports non-null even for alarms that were never scheduled.
 */
class AlarmSchedulerInstrumentedTest {

    private lateinit var context: Context

    private class FakeReminderDao(private val byEntry: Map<Long, List<Reminder>>) : ReminderDao {
        override suspend fun insert(reminder: Reminder): Long = 1L
        override suspend fun getForEntry(entryId: Long): List<Reminder> = byEntry[entryId].orEmpty()
        override suspend fun getAllForEntry(entryId: Long): List<Reminder> = byEntry[entryId].orEmpty()
        override fun observeForEntry(entryId: Long): Flow<List<Reminder>> = flowOf(byEntry[entryId].orEmpty())
        override suspend fun softDelete(id: Long, deletedAt: java.time.Instant) = Unit
        override suspend fun getAll(): List<Reminder> = byEntry.values.flatten()
        override suspend fun getByUid(uid: String): Reminder? =
            byEntry.values.flatten().firstOrNull { it.uid == uid }

        /** Read-only fixture — nothing here is ever wiped. */
        override suspend fun deleteAll() = Unit
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    private fun task(id: Long, daysAhead: Long) = Entry(
        id = id,
        title = "Instrumented task $id",
        kind = EntryKind.TASK,
        startDate = LocalDate.now().plusDays(daysAhead),
        startTime = LocalTime.of(9, 0),
        endDate = null, endTime = null,
        recurrenceRule = null,
        status = EntryStatus.PENDING,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    /**
     * Whether a matching PendingIntent record exists.
     *
     * Note this is *not* the same as "an alarm is scheduled": `AlarmManager.cancel()` drops the
     * alarm but leaves the PendingIntent registered, and `AlarmScheduler.cancelAllFor` itself
     * constructs one in order to cancel. So this can only be used to assert presence after
     * scheduling, never absence after cancelling — the alarm count below is what answers that.
     */
    private fun existing(requestCode: Int, receiver: Class<*>): PendingIntent? =
        PendingIntent.getBroadcast(
            context, requestCode, Intent(context, receiver),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Alarms the system currently holds for this package, straight from `dumpsys alarm`. */
    private fun scheduledAlarmCount(): Int {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys alarm")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)
            .bufferedReader().use { it.readText() }
        return dump.lineSequence().count { it.contains(context.packageName) && it.contains("Alarm{") }
    }

    @Test
    fun schedulingAFutureTaskRegistersAnOverdueAlarmWithoutThrowing() = runBlocking {
        // The guarded setExactAndAllowWhileIdle call — this is the line that threw
        // SecurityException on API 31-32 before SCHEDULE_EXACT_ALARM was declared.
        val entryId = 90_001L
        val scheduler = AlarmScheduler(context, FakeReminderDao(emptyMap()))
        scheduler.cancelAllFor(entryId)

        scheduler.rescheduleFor(task(entryId, daysAhead = 3))

        assertNotNull(
            "no overdue alarm was registered",
            existing(AlarmScheduler.overdueRequestCode(entryId), OverdueAlarmReceiver::class.java),
        )
        scheduler.cancelAllFor(entryId)
    }

    @Test
    fun remindersForDifferentEntriesCoexistRatherThanReplacingEachOther() = runBlocking {
        // ALARM-02 on real PendingIntents: under the old request-code scheme entry 1/reminder 1
        // and entry 129/reminder 0 collapsed onto the same code, so scheduling the second
        // silently replaced the first and cancelling either cancelled both.
        val entryA = 1L
        val entryB = 129L
        val dao = FakeReminderDao(
            mapOf(
                entryA to listOf(Reminder(id = 1, entryId = entryA, offset = ReminderOffset.FromPreset(ReminderOffset.Preset.entries.first()))),
                entryB to listOf(Reminder(id = 0, entryId = entryB, offset = ReminderOffset.FromPreset(ReminderOffset.Preset.entries.first()))),
            )
        )
        val scheduler = AlarmScheduler(context, dao)
        scheduler.cancelAllFor(entryA)
        scheduler.cancelAllFor(entryB)

        scheduler.rescheduleFor(task(entryA, daysAhead = 5))
        scheduler.rescheduleFor(task(entryB, daysAhead = 5))

        val codeA = AlarmScheduler.reminderRequestCode(entryA, 1L)
        val codeB = AlarmScheduler.reminderRequestCode(entryB, 0L)
        assertNotNull("entry A's reminder is missing", existing(codeA, ReminderAlarmReceiver::class.java))
        assertNotNull("entry B's reminder is missing", existing(codeB, ReminderAlarmReceiver::class.java))

        // Cancelling one must leave the other registered — the property the collision broke.
        scheduler.cancelAllFor(entryB)
        assertNotNull(
            "cancelling entry B also cancelled entry A's reminder",
            existing(codeA, ReminderAlarmReceiver::class.java),
        )

        scheduler.cancelAllFor(entryA)
    }

    @Test
    fun schedulingAddsAnAlarmAndCancellingRemovesIt() = runBlocking {
        // Counted from dumpsys, so this is the system's own view of what's scheduled rather
        // than an inference from PendingIntent bookkeeping.
        val entryId = 90_002L
        val scheduler = AlarmScheduler(context, FakeReminderDao(emptyMap()))
        scheduler.cancelAllFor(entryId)
        val baseline = scheduledAlarmCount()

        scheduler.rescheduleFor(task(entryId, daysAhead = 2))
        val afterSchedule = scheduledAlarmCount()

        scheduler.cancelAllFor(entryId)
        val afterCancel = scheduledAlarmCount()

        org.junit.Assert.assertEquals("scheduling should add one alarm", baseline + 1, afterSchedule)
        org.junit.Assert.assertEquals("cancelling should remove it again", baseline, afterCancel)
    }

    @Test
    fun aPastDueTaskSchedulesNothing() = runBlocking {
        // §9.7's flood guard: a trigger at or before now is skipped entirely, so a bulk import
        // of overdue items can't produce a notification storm.
        val entryId = 90_003L
        val scheduler = AlarmScheduler(context, FakeReminderDao(emptyMap()))
        scheduler.cancelAllFor(entryId)
        val baseline = scheduledAlarmCount()

        scheduler.rescheduleFor(task(entryId, daysAhead = -7))

        org.junit.Assert.assertEquals(
            "a past-due task should not register an alarm",
            baseline,
            scheduledAlarmCount(),
        )
    }
}
