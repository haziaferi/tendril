package com.tendril.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.reminder.toDuration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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

    suspend fun rescheduleFor(entry: Entry) {
        cancelAllFor(entry.id)
        if (entry.deletedAt != null) return
        // A resolved TASK stops reminding. An EVENT has no status to resolve (§4), so this
        // gate is deliberately TASK-only rather than folded into one `kind != TASK` bail —
        // pre-due reminders below apply to either kind.
        if (entry.kind == EntryKind.TASK && entry.status != EntryStatus.PENDING) return
        val startDate = entry.startDate ?: return
        val zone = ZoneId.systemDefault()

        // Overdue trigger: start_date+start_time, or midnight if no time (§4.1 round 1).
        // TASK-only (§9.7) — an EVENT has no done/not-done state for it to drive.
        if (entry.kind == EntryKind.TASK) {
            val overdueTrigger = LocalDateTime.of(startDate, entry.startTime ?: LocalTime.MIDNIGHT)
                .atZone(zone).toInstant()
            scheduleIfFuture(
                requestCode = overdueRequestCode(entry.id),
                triggerAt = overdueTrigger,
                receiver = OverdueAlarmReceiver::class.java,
                entryId = entry.id,
            )
        }

        // Pre-due reminders (§3.2/§5.4) — anchored to the real time if present, else the
        // reminder's own configured anchor time, else midnight (§4).
        val anchorTime = entry.startTime ?: LocalTime.MIDNIGHT
        val baseInstant = LocalDateTime.of(startDate, anchorTime).atZone(zone).toInstant()
        reminderDao.getForEntry(entry.id).forEach { reminder ->
            val effectiveBase = if (entry.startTime != null) {
                baseInstant
            } else {
                LocalDateTime.of(startDate, reminder.anchorTime ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
            }
            val trigger = effectiveBase.minus(reminder.offset.toDuration())
            scheduleIfFuture(
                requestCode = reminderRequestCode(entry.id, reminder.id),
                triggerAt = trigger,
                receiver = ReminderAlarmReceiver::class.java,
                entryId = entry.id,
                reminderId = reminder.id,
            )
        }
    }

    /**
     * §5.4's delete-a-reminder path — cancels one reminder's alarm without disturbing the
     * others. Lives here rather than at the call site so request-code derivation stays in
     * the one place that owns it. Cancelling needs only the two ids, not the row, but the
     * caller must still call this *before* deleting: [rescheduleFor] cancels by reading the
     * reminder rows back out of the DAO, so a deleted row's alarm is invisible to it.
     */
    fun cancelReminder(entryId: Long, reminderId: Long) {
        cancel(reminderRequestCode(entryId, reminderId), ReminderAlarmReceiver::class.java, entryId, reminderId)
    }

    suspend fun cancelAllFor(entryId: Long) {
        cancel(overdueRequestCode(entryId), OverdueAlarmReceiver::class.java, entryId)
        reminderDao.getForEntry(entryId).forEach { reminder ->
            cancel(reminderRequestCode(entryId, reminder.id), ReminderAlarmReceiver::class.java, entryId, reminder.id)
        }
    }

    private fun scheduleIfFuture(
        requestCode: Int,
        triggerAt: Instant,
        receiver: Class<*>,
        entryId: Long,
        reminderId: Long? = null,
    ) {
        // Never schedule a trigger at/before now — AlarmManager fires a past-due alarm
        // almost immediately, which would flood notifications on bulk/retroactive writes.
        if (!triggerAt.isAfter(Instant.now())) return
        val manager = alarmManager ?: return
        val intent = Intent(context, receiver).apply {
            putExtra(EXTRA_ENTRY_ID, entryId)
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

    /** True below API 31 (exact alarms were unrestricted there), otherwise whatever
     * AlarmManager currently reports for this app. */
    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun cancel(requestCode: Int, receiver: Class<*>, entryId: Long, reminderId: Long? = null) {
        val manager = alarmManager ?: return
        val intent = Intent(context, receiver).apply {
            putExtra(EXTRA_ENTRY_ID, entryId)
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
    }
}
