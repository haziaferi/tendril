package com.tendril.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tendril.app.AppContainer
import com.tendril.app.MainActivity
import com.tendril.app.R
import com.tendril.app.domain.isHabitDueOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * §9.7 / audit §3 — the notification `NotificationChannels.HABITS` was created for and nothing
 * ever posted to. Until now the channel appeared in Android's notification settings as a
 * permanently silent entry: a control for a feature that did not exist.
 *
 * `CheckInHabitUseCase`'s own doc cites Loop/uhabits as the prior art, and Loop's core
 * interaction is a reminder you can check off without opening the app — so the notification
 * carries the check-off action rather than only a tap-to-open. That action goes through
 * [CheckInHabitUseCase] like every other check-in path (the widget included), so the streak
 * math has exactly one implementation.
 *
 * The habit is re-read and re-tested for due-ness at fire time, not trusted from when the alarm
 * was set: the person may have checked it off on another device and synced since, and a
 * reminder for something already done is worse than no reminder at all.
 */
class HabitReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(AlarmScheduler.EXTRA_HABIT_ID, -1L)
        if (habitId < 0) return
        val checkingOff = intent.action == ACTION_CHECK_OFF
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.from(context)
                // §3.6 — the third surface, and the one that section does not mention. It
                // reconciled the Habits widget on 2026-09-04 and recorded the entry Done/Skip
                // path as still open on 2026-09-06; a habit checked off from its *notification*
                // is the same write by a third route, and nothing had looked at it. Refused
                // here, and [postReminder] posts an action that opens the app instead while the
                // lock is on. Posting the reminder itself stays allowed: reading a habit title
                // on the lock screen is the exposure §3.6 accepts for widgets, and suppressing
                // the reminder would break the feature to protect nothing new.
                if (checkingOff) {
                    val checked = checkInFromNotification(
                        appLockEnabled = container.appLockPreferences.enabled.value,
                        habitId = habitId,
                    ) { container.checkInHabitUseCase.checkIn(it) }
                    // Only a write earns the dismissal, as on the entry side: clearing a refused
                    // notification would make "nothing happened" look like "checked off".
                    if (checked) NotificationManagerCompat.from(context).cancel(notificationId(habitId))
                } else {
                    postReminder(context, container, habitId)
                }
                // Either way the next occurrence has moved, so re-arm from current state.
                container.database.habitDao().getById(habitId)?.let(container.alarmScheduler::rescheduleHabit)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun postReminder(context: Context, container: AppContainer, habitId: Long) {
        val habit = container.database.habitDao().getById(habitId) ?: return
        if (habit.deletedAt != null) return
        // Checked off since the alarm was set — on this device or another one that synced.
        if (!isHabitDueOn(habit, LocalDate.now())) return

        val openIntent = PendingIntent.getActivity(
            context,
            AlarmScheduler.habitRequestCode(habitId),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // A different request code from the alarm's own, or arming the next alarm would
        // replace the check-off action's PendingIntent and vice versa.
        val checkOffCode = AlarmScheduler.habitRequestCode(habitId) + 1
        // §3.6 — with App Lock on the action opens the app through the lock instead of checking
        // the habit off from the keyguard, matching the Habits widget and the overdue
        // notification's Done/Skip. The receiver refuses as well, for a notification posted
        // before the lock was turned on.
        val checkOffIntent = if (container.appLockPreferences.enabled.value) {
            PendingIntent.getActivity(
                context, checkOffCode, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                checkOffCode,
                Intent(context, HabitReminderAlarmReceiver::class.java).apply {
                    action = ACTION_CHECK_OFF
                    putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.HABITS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(habit.title)
            .setContentText(context.getString(R.string.notification_habit_reminder_text))
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.notification_habit_check_off), checkOffIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(habitId), notification)
    }

    companion object {
        const val ACTION_CHECK_OFF = "com.tendril.app.HABIT_CHECK_OFF"

        /** A range of its own, clear of [ReminderAlarmReceiver]'s 20,000,000 base — a habit id
         * and an entry id are independent Room sequences, so without separate ranges one
         * would routinely replace the other's notification. */
        private const val NOTIFICATION_ID_BASE = 30_000_000

        fun notificationId(habitId: Long): Int =
            NOTIFICATION_ID_BASE + (habitId and 0x7FFF).toInt()
    }
}

/**
 * §3.6 — whether the habit reminder's check-off action may write.
 *
 * The third surface of the App Lock hole, and the one §3.6 never recorded: it reconciled the
 * Habits widget on 2026-09-04, recorded the overdue notification on 2026-09-06, and missed the
 * habit *notification* sitting between them, which reaches the same
 * [com.tendril.app.domain.CheckInHabitUseCase] write by a third route.
 *
 * Lifted out of [HabitReminderAlarmReceiver.onReceive] for the same reason as
 * [resolveFromNotification]: a receiver body only runs inside a broadcast dispatch, so the JVM
 * suite cannot reach it, and an instrumented test would write to the device's real database. The
 * first version of this fix left the rule inline and `tools/mutate.py` reported the guard
 * SURVIVED — deleting it broke nothing — which is how a guard added *for* a security hole ends
 * up with nothing defending it.
 *
 * `false` means refused. Posting the reminder itself stays ungated: reading a habit title on the
 * lock screen is the exposure §3.6 already accepts for widgets.
 */
internal suspend fun checkInFromNotification(
    appLockEnabled: Boolean,
    habitId: Long,
    checkIn: suspend (Long) -> Unit,
): Boolean {
    if (appLockEnabled) return false
    checkIn(habitId)
    return true
}
