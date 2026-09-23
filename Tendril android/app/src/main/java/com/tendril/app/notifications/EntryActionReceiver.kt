package com.tendril.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.tendril.app.AppContainer
import com.tendril.app.MainActivity
import com.tendril.app.data.entry.EntryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the overdue notification's inline Done/Skip actions (§9.7) — routes through
 * [com.tendril.app.domain.ResolveEntryUseCase] like every other resolution surface (§5.2/R1),
 * not a bespoke path.
 */
class EntryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(AlarmScheduler.EXTRA_ENTRY_ID, -1L)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (entryId < 0 || action == null) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.from(context)
                val resolved = resolveFromNotification(
                    appLockEnabled = container.appLockPreferences.enabled.value,
                    entryId = entryId,
                    action = action,
                ) { id, status -> container.resolveEntryUseCase.resolve(id, status) }
                // Only a write earns the dismissal. Cancelling a refused notification would
                // make "nothing happened" look exactly like "done".
                if (resolved) {
                    NotificationManagerCompat.from(context).cancel(OverdueAlarmReceiver.notificationId(entryId))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.tendril.app.action.DONE"
        const val ACTION_SKIP = "com.tendril.app.action.SKIP"
        private const val EXTRA_ACTION = "action"

        fun pendingIntent(context: Context, entryId: Long, action: String): PendingIntent {
            // Distinct request code per (entry, action) — Done and Skip need separate
            // PendingIntents for the same Entry, not just distinct from other entries'.
            val requestCode = (entryId.toInt() shl 1) or (if (action == ACTION_DONE) 0 else 1)

            // §3.6 — the UI-gate half, the same shape the Habits widget uses: with App Lock on,
            // the action opens the app *through the lock* rather than resolving the entry from
            // the keyguard. Decided here rather than at each call site so a future notification
            // that adds a Done button cannot forget it. Targeting the Activity also avoids a
            // background-activity-start restriction, which a receiver launching one would hit.
            if (AppContainer.from(context).appLockPreferences.enabled.value) {
                return PendingIntent.getActivity(
                    context, requestCode, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val intent = Intent(context, EntryActionReceiver::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_ACTION, action)
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/**
 * §3.6 — whether an overdue notification's Done/Skip may write, and what it writes.
 *
 * Separated from [EntryActionReceiver] so the rule is reachable without a broadcast dispatch:
 * `goAsync()` only works inside one, and the alternative — an instrumented test — would run
 * against the real database on the device, which is the user's own. The App Lock state arrives
 * as a `Boolean` and the write as a lambda for the same reason
 * [com.tendril.app.domain.CheckboxOnlyState] takes `appLockEnabled` injected: `AppLockPreferences`
 * is Android-only, and the decision is not.
 *
 * `false` means refused. [pendingIntent] already opens the app through the lock rather than
 * broadcasting while App Lock is on, so reaching here locked means the notification was posted
 * before the lock was turned on, or a PendingIntent outlived the setting — the guard half of the
 * UI-gate-plus-guard pair §3.6 prescribes and the Habits widget already uses. Without it, one tap
 * from the keyguard logs a completion, advances a recurring TASK's `start_date`, re-arms its
 * alarms and rewrites the Calendar Provider mirror: exactly the class of write App Lock exists to
 * stop, recorded as reachable here on 2026-09-06 and closed on 2026-09-22.
 */
internal suspend fun resolveFromNotification(
    appLockEnabled: Boolean,
    entryId: Long,
    action: String,
    resolve: suspend (Long, EntryStatus) -> Unit,
): Boolean {
    if (appLockEnabled) return false
    resolve(entryId, if (action == EntryActionReceiver.ACTION_DONE) EntryStatus.DONE else EntryStatus.SKIPPED)
    return true
}
