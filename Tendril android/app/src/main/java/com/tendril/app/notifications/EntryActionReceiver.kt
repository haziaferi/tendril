package com.tendril.app.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.tendril.app.AppContainer
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
                val status = if (action == ACTION_DONE) EntryStatus.DONE else EntryStatus.SKIPPED
                container.resolveEntryUseCase.resolve(entryId, status)
                NotificationManagerCompat.from(context).cancel(10_000 + entryId.toInt())
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
            val intent = Intent(context, EntryActionReceiver::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_ACTION, action)
            }
            // Distinct request code per (entry, action) — Done and Skip need separate
            // PendingIntents for the same Entry, not just distinct from other entries'.
            val requestCode = (entryId.toInt() shl 1) or (if (action == ACTION_DONE) 0 else 1)
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
