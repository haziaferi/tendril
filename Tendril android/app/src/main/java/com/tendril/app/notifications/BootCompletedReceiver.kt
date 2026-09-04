package com.tendril.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tendril.app.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * §9.7/§9.8 R4 — alarms do not survive a reboot; without this, every reminder silently
 * stops working after every restart until the app happens to be opened again. Reschedules
 * every live, schedulable TASK from Room — idempotent, so this doubles as the
 * app-open reconciliation sweep too (called again from [com.tendril.app.MainActivity]).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reconcileAlarms(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

suspend fun reconcileAlarms(context: Context) {
    val container = AppContainer.from(context)
    container.database.entryDao().getAllSchedulableTasks().forEach { entry ->
        container.alarmScheduler.rescheduleFor(entry)
    }
    // §3.2/§9.9 item 3 — Provider registration's own self-healing sweep, riding alongside the
    // alarm one above. No-ops if permission was never granted; can't request it from a
    // boot-time BroadcastReceiver (no Activity), so a first grant only ever happens from
    // MainActivity's own LaunchedEffect.
    //
    // Guarded for the same reason AlarmScheduler checks canScheduleExactAlarms() rather than
    // trusting the manifest: this runs from MainActivity's launch-time sweep, so a Calendar
    // Provider that refuses the write — absent or restricted on some devices and work profiles,
    // where the insert comes back null and `createCalendar` throws — would take the app down on
    // open. A missing calendar mirror is a degraded integration; a crash on launch is not.
    runCatching { container.calendarProviderSync.ensureCalendarAndBackfill() }
}
