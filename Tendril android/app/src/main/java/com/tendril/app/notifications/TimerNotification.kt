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
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.track.target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * §0.6.5 / §0.8 step 7c — the phone's "now" while a timer runs, answering B§6 #9's open
 * question ("whether a timer survives process death via a notification") with less than it
 * asked for. **The timer survives process death without any notification**: the open
 * [TimeLog] row is the timer. What a person needs from the shade is to *see* it running and
 * to *stop* it, and a plain ongoing notification does both — `setUsesChronometer` has SystemUI
 * count from `startedAt` with no process of ours alive, and the Stop action is a broadcast
 * that writes the missing `endedAt`. No foreground service, no `FOREGROUND_SERVICE_*`
 * permission, no `specialUse` subtype to justify; the manifest's "no new sensitive permission"
 * line stays true.
 *
 * Posted and cleared by [TimerNotifier], which follows the tracker's `running` flow for the
 * life of the process; re-posting after a restart is the same id, so idempotent.
 */
object TimerNotification {
    private const val NOTIFICATION_ID = 20_000

    fun post(context: Context, title: String, log: TimeLog) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.TIMER)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_timer_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setWhen(log.startedAt.toEpochMilli())
            .setUsesChronometer(true)
            .setShowWhen(true)
            .addAction(0, context.getString(R.string.action_stop_timer), TimerStopReceiver.pendingIntent(context))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/** The Stop action: the same [com.tendril.app.domain.track.TimeTracker.stop] a row button calls. */
class TimerStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppContainer.from(context).timeTracker.stop()
                TimerNotification.cancel(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0, Intent(context, TimerStopReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

/** Keeps the shade in step with the tracker: one notification while a log runs, none after. */
class TimerNotifier(private val context: Context, private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            container.timeTracker.running.distinctUntilChangedBy { it?.uid }.collect { log ->
                if (log == null) {
                    TimerNotification.cancel(context)
                } else {
                    val title = when (val target = log.target()) {
                        is TrackTarget.Entry -> container.database.entryDao().getById(target.id)?.title
                        is TrackTarget.Habit -> container.database.habitDao().getById(target.id)?.title
                    } ?: return@collect
                    TimerNotification.post(context, title, log)
                }
            }
        }
    }
}
