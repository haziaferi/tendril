package com.tendril.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.domain.reminders.FiringKind
import com.tendril.app.domain.reminders.entryFirings
import com.tendril.app.domain.reminders.habitFiring
import java.time.Instant
import java.time.ZonedDateTime

/**
 * §4.1 (round 2) / §9.7 / §9.8 R4 — elastic TASK recurrence means only the currently-live
 * occurrence's alarms ever exist; every write path that can move an Entry's `startDate`
 * (create, edit, resolve-and-advance) must cancel old alarms and schedule fresh ones as one
 * atomic step through this one component. Idempotent request codes (derived from entryId,
 * never auto-incrementing) mean calling reschedule twice never duplicates an alarm.
 *
 * §9.7 (added 2026-07-16) — never schedules a trigger time at or before "now"; a bulk
 * operation creating already-overdue Entries relies on this to avoid a notification flood
 * (the batched-summary notification for that case is deferred — nothing in Phase 3 creates
 * Entries in bulk yet; Notion import and retroactive Sync-to-Tasks do, in later phases).
 */
class AlarmScheduler(
    private val context: Context,
    private val reminderDao: ReminderDao,
) {
    private val alarmManager: AlarmManager? = context.getSystemService()

    /**
     * @param exceptions [entry]'s single-occurrence exception rows (§4.1), so a skipped or
     * moved occurrence isn't the one alarms get anchored to. Defaults to none — correct for
     * every Entry that isn't a recurring EVENT, which is all of them until a series is pulled
     * from Google Calendar (§9.5.1).
     */
    suspend fun rescheduleFor(entry: Entry, exceptions: List<Entry> = emptyList()) {
        cancelAllFor(entry.id)
        // B§13.6 #7 — the arithmetic (the gates, the next occurrence of a recurring EVENT, the
        // overdue trigger at start+time or midnight, each reminder at base − offset with the
        // anchor rule, nothing in the past) is `domain/reminders/ReminderFirings.kt`, shared with
        // the desktop's scheduler and tested there; this class only owns the request codes and
        // the receivers.
        entryFirings(entry, reminderDao.getForEntry(entry.id), exceptions).forEach { firing ->
            when (firing.kind) {
                FiringKind.OVERDUE -> scheduleIfFuture(
                    requestCode = overdueRequestCode(entry.id),
                    triggerAt = firing.at,
                    receiver = OverdueAlarmReceiver::class.java,
                    entryId = entry.id,
                )
                FiringKind.REMINDER -> scheduleIfFuture(
                    requestCode = reminderRequestCode(entry.id, firing.reminderId!!),
                    triggerAt = firing.at,
                    receiver = ReminderAlarmReceiver::class.java,
                    entryId = entry.id,
                    reminderId = firing.reminderId,
                )
                FiringKind.HABIT -> Unit
            }
        }
    }

    /**
     * §5.4's delete-a-reminder path — cancels one reminder's alarm without disturbing the
     * others. Lives here rather than at the call site so request-code derivation stays in
     * the one place that owns it. Cancelling needs only the two ids, not the row, but the
     * caller must still call this *before* deleting: [rescheduleFor] cancels by reading the
     * reminder rows back out of the DAO, so a deleted row's alarm is invisible to it.
     */
    /**
     * §9.7 / §3 — schedules (or clears) this habit's next reminder.
     *
     * Idempotent for the same reason [rescheduleFor] is: the request code comes from the habit
     * id, so calling it twice replaces one alarm rather than creating two. Every path that can
     * change when a habit is next due — creating it, checking it in, undoing that, trashing it —
     * calls this, and [nextHabitReminderAt] decides whether there is anything to schedule.
     */
    fun rescheduleHabit(habit: com.tendril.app.data.habit.Habit) {
        val requestCode = habitRequestCode(habit.id)
        cancelHabit(habit.id)
        val triggerAt = habitFiring(habit, ZonedDateTime.now())?.at ?: return
        scheduleIfFuture(
            requestCode = requestCode,
            triggerAt = triggerAt,
            receiver = HabitReminderAlarmReceiver::class.java,
            entryId = habit.id,
            idKey = EXTRA_HABIT_ID,
        )
    }

    fun cancelHabit(habitId: Long) {
        cancel(habitRequestCode(habitId), HabitReminderAlarmReceiver::class.java, habitId, idKey = EXTRA_HABIT_ID)
    }

    fun cancelReminder(entryId: Long, reminderId: Long) {
        cancel(reminderRequestCode(entryId, reminderId), ReminderAlarmReceiver::class.java, entryId, reminderId)
    }

    /** Tombstoned reminders included (audit 5.1): a reminder deleted on another device arrives
     * as a tombstone, and its alarm, armed while it was live, is still this device's to cancel. */
    suspend fun cancelAllFor(entryId: Long) {
        cancel(overdueRequestCode(entryId), OverdueAlarmReceiver::class.java, entryId)
        reminderDao.getAllForEntry(entryId).forEach { reminder ->
            cancel(reminderRequestCode(entryId, reminder.id), ReminderAlarmReceiver::class.java, entryId, reminder.id)
        }
    }

    private fun scheduleIfFuture(
        requestCode: Int,
        triggerAt: Instant,
        receiver: Class<*>,
        entryId: Long,
        reminderId: Long? = null,
        // Habits ride the same two helpers but are not Entries; the key travels with the caller
        // rather than being hardcoded, so a habit alarm never arrives labelled as an entry.
        idKey: String = EXTRA_ENTRY_ID,
    ) {
        // Never schedule a trigger at/before now — AlarmManager fires a past-due alarm
        // almost immediately, which would flood notifications on bulk/retroactive writes.
        if (!triggerAt.isAfter(Instant.now())) return
        val manager = alarmManager ?: return
        val intent = Intent(context, receiver).apply {
            putExtra(idKey, entryId)
            reminderId?.let { putExtra(EXTRA_REMINDER_ID, it) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Exact alarms are permission-gated from API 31 up. Ask AlarmManager rather than
        // assuming the manifest declaration is enough: USE_EXACT_ALARM doesn't exist below
        // API 33, and SCHEDULE_EXACT_ALARM (the 31-32 stand-in) is revocable by the user in
        // Settings. An unguarded call throws SecurityException, and this runs inside
        // MainActivity's launch-time reconcileAlarms sweep — an uncaught one there is a
        // crash on launch, not a missed notification.
        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
        } else {
            // Still Doze-tolerant, just not to-the-minute: the reminder drifts rather than
            // never arriving. Degrading beats both crashing and silently dropping the alarm.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
        }
    }

    /**
     * True below API 31 (exact alarms were unrestricted there), otherwise whatever
     * AlarmManager currently reports for this app.
     *
     * Audit 2.12 — `internal` so `ExactAlarmInstrumentedTest` can assert the decision this app
     * actually makes, rather than re-deriving the same expression and asserting its own copy. The
     * distinction matters because what this returns is the *only* half of alarm exactness Tendril
     * controls: on a OnePlus LE2123 (API 34, `USE_EXACT_ALARM` granted) this returns true, the
     * exact call below is made, and the system hands back a windowed alarm regardless — measured
     * at 3600000 ms for a task two days out. That is a property of the phone, recorded in the
     * audit, and no assertion here can move it.
     */
    @VisibleForTesting
    internal fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun cancel(
        requestCode: Int,
        receiver: Class<*>,
        entryId: Long,
        reminderId: Long? = null,
        idKey: String = EXTRA_ENTRY_ID,
    ) {
        val manager = alarmManager ?: return
        val intent = Intent(context, receiver).apply {
            putExtra(idKey, entryId)
            reminderId?.let { putExtra(EXTRA_REMINDER_ID, it) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pendingIntent)
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_REMINDER_ID = "reminder_id"

        // Deterministic request codes derived from entry_id/reminder_id (§9.7/§9.8 R4) —
        // never auto-incrementing, so reschedule is always idempotent.
        //
        // The two ids occupy separate bit ranges rather than being XOR-folded together. The
        // previous `(entryId * 2 + 1) xor (reminderId shl 8)` shifted reminderId straight
        // into entryId's own bits, so pairs collided: entry 1/reminder 1 and entry 129/
        // reminder 0 both produced 259. PendingIntent identity ignores extras, so the second
        // alarm silently replaced the first and cancelling either cancelled both — reminders
        // just disappeared. Bit 0 stays the overdue/reminder discriminator, so the two kinds
        // can never collide with each other either.
        //
        // Capacity is 32,767 entries × 32,767 reminders before ids wrap; beyond that the
        // modular arithmetic can alias, which is a real (if distant) bound rather than the
        // absolute guarantee this comment used to claim.
        private const val ID_MASK = 0x7FFF

        /** Bits 1-16, bit 0 clear. Unchanged from the previous scheme for entry ids below
         * 65,536, so alarms scheduled before this fix are still cancellable. */
        fun overdueRequestCode(entryId: Long): Int = ((entryId and 0xFFFF) shl 1).toInt()

        /** entryId in bits 16-30, reminderId in bits 1-15, bit 0 set. */
        fun reminderRequestCode(entryId: Long, reminderId: Long): Int =
            (((entryId and ID_MASK.toLong()) shl 16) or
                ((reminderId and ID_MASK.toLong()) shl 1) or 1L).toInt()

        /**
         * Habits get a region of their own, well clear of both Entry schemes (§9.7).
         *
         * Those two partition the low space between them by bit 0 — overdue clear, reminder set —
         * so a habit cannot simply pick a spelling and hope. Bit 0 stays clear to stay out of the
         * reminder range, and the base puts every habit code above the largest overdue code
         * (`0xFFFF shl 1`, so bits 1-16) by a wide margin. A habit id and an entry id are
         * independent Room sequences and will collide constantly; the ranges are what keeps one
         * from cancelling the other's alarm.
         */
        private const val HABIT_REQUEST_BASE = 0x2000_0000

        fun habitRequestCode(habitId: Long): Int =
            HABIT_REQUEST_BASE + (((habitId and ID_MASK.toLong()) shl 1).toInt())
    }
}
