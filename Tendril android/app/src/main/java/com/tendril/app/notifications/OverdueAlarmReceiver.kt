package com.tendril.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tendril.app.AppContainer
import com.tendril.app.MainActivity
import com.tendril.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * §9.7 (added 2026-07-14, MedTimer-pattern) — fires at `start_date`+`start_time` (or
 * midnight) for a `kind = TASK` Entry, with inline Done/Skip actions. Separate from the
 * pre-due [ReminderAlarmReceiver] notifications, and what actually drives sequential
 * recurrence advancement when the person isn't in the app to tap it manually.
 */
class OverdueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(AlarmScheduler.EXTRA_ENTRY_ID, -1L)
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
                    .setContentText(context.getString(R.string.notification_overdue_text))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .addAction(0, context.getString(R.string.action_done), EntryActionReceiver.pendingIntent(context, entryId, EntryActionReceiver.ACTION_DONE))
                    .addAction(0, context.getString(R.string.action_skip), EntryActionReceiver.pendingIntent(context, entryId, EntryActionReceiver.ACTION_SKIP))
                    .build()

                NotificationManagerCompat.from(context).notify(notificationId(entryId), notification)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID_BASE = 10_000

        /** The one place this id is derived. [EntryActionReceiver] has to cancel the very
         * notification whose action was tapped, and used to re-spell `10_000 + entryId` by
         * hand — so changing the base here would have silently stopped Done/Skip from
         * dismissing anything. */
        fun notificationId(entryId: Long): Int = NOTIFICATION_ID_BASE + entryId.toInt()
    }
}
