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
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.reminder.toDuration
import com.tendril.app.domain.nextHabitReminderAt
import com.tendril.app.domain.recurrence.EntryOccurrences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
        if (entry.deletedAt != null) return
        // A resolved TASK stops reminding. An EVENT has no status to resolve (§4), so this
        // gate is deliberately TASK-only rather than folded into one `kind != TASK` bail —
        // pre-due reminders below apply to either kind.
        if (entry.kind == EntryKind.TASK && entry.status != EntryStatus.PENDING) return
        val zone = ZoneId.systemDefault()
        val startDate = nextOccurrenceDate(entry, exceptions, zone) ?: return

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
     * Which occurrence this Entry's alarms should be anchored to.
     *
     * For everything except a recurring EVENT this is just `entry.startDate`, unchanged. For a
     * recurring EVENT it is the first occurrence whose start instant is still in the future
     * (§4.1). Anchoring to `startDate` meant that once a series' *first* occurrence had passed,
     * [scheduleIfFuture]'s never-in-the-past guard suppressed every alarm after it — so a
     * weekly meeting reminded exactly once, ever, while the same series went on recurring in
     * Tendril's Calendar and in the system calendar it publishes to (§9.11).
     *
     * Only the next occurrence is armed, never a run of them: request codes are derived from
     * `(entryId, reminderId)` alone (§9.7/§9.8 R4), so two occurrences of one series would
     * collide on the same `PendingIntent` and the second would silently replace the first.
     * That is the same "only the currently-live occurrence's alarms exist" model §4.1 already
     * settled on for elastic TASK recurrence, and it leans on the same backstop: §9.7's
     * reconciliation sweep, which re-arms everything on boot and on app open. A series whose
     * next occurrence passes while the app is never opened waits for that sweep — bounded and
     * self-healing, rather than the previous permanent silence.
     */
    private fun nextOccurrenceDate(entry: Entry, exceptions: List<Entry>, zone: ZoneId): LocalDate? {
        val anchor = entry.startDate ?: return null
        if (entry.kind != EntryKind.EVENT || entry.recurrenceRule !is RecurrenceRule.Fixed) return anchor

        val now = Instant.now()
        val today = LocalDate.now(zone)
        val candidates = EntryOccurrences
            .expand(listOf(entry) + exceptions, today, today.plusDays(EntryOccurrences.DEFAULT_ALARM_HORIZON_DAYS))
            .filter { it.isFirstDay && (it.entry.id == entry.id || it.entry.originalEntryId == entry.id) }

        // "On or after today" isn't enough on its own: today's occurrence may already have
        // started, in which case its alarms are all in the past and the series' *next* one is
        // what should be armed.
        return candidates.firstOrNull { occurrence ->
            LocalDateTime.of(occurrence.startDate, occurrence.startTime ?: LocalTime.MIDNIGHT)
                .atZone(zone).toInstant().isAfter(now)
        }?.startDate ?: candidates.firstOrNull()?.startDate
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
        val triggerAt = nextHabitReminderAt(habit, ZonedDateTime.now()) ?: return
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

    /** True below API 31 (exact alarms were unrestricted there), otherwise whatever
     * AlarmManager currently reports for this app. */
    private fun canScheduleExact(manager: AlarmManager): Boolean =
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
