package com.tendril.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tendril.app.AppContainer
import com.tendril.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** §3.2/§5.4 — a pre-due reminder, distinct from [OverdueAlarmReceiver]'s at-due alert. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(AlarmScheduler.EXTRA_ENTRY_ID, -1L)
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (entryId < 0) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.from(context)
                val entry = container.database.entryDao().getById(entryId) ?: return@launch
                if (entry.deletedAt != null) return@launch

                val openIntent = Intent(context, MainActivity::class.java)
                val contentIntent = android.app.PendingIntent.getActivity(
                    context, entryId.toInt(), openIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, NotificationChannels.TASKS_EVENTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(entry.title)
                    .setContentText(context.getString(com.tendril.app.R.string.notification_reminder_text))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(notificationId(entryId, reminderId), notification)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 20_000_000

        /** Same bit-packing rationale as [AlarmScheduler.reminderRequestCode]: the previous
         * `entryId * 1000 + reminderId` aliased (entry 1 / reminder 1000 collided with entry 2 /
         * reminder 0) and overflowed Int well before Room's ids would. Separate ranges instead,
         * so a reminder can only ever replace or dismiss its own notification. */
        fun notificationId(entryId: Long, reminderId: Long): Int =
            NOTIFICATION_ID_BASE + (((entryId and 0x7FFF) shl 15) or (reminderId and 0x7FFF)).toInt()
    }
}
