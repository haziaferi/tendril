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
 * every live, schedulable Entry from Room (TASK *and* EVENT — §4 gives reminders to
 * both) — idempotent, so this doubles as the
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
    container.database.entryDao().getAllSchedulable().forEach { entry ->
        container.alarmScheduler.rescheduleFor(entry)
    }
    // §3.2/§9.9 item 3 — Provider registration's own self-healing sweep, riding alongside the
    // alarm one above. No-ops if permission was never granted; can't request it from a
    // boot-time BroadcastReceiver (no Activity), so a first grant only ever happens from
    // MainActivity's own LaunchedEffect.
    container.calendarProviderSync.ensureCalendarAndBackfill()
}
