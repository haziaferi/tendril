package com.tendril.app

import android.app.Application
import com.tendril.app.notifications.NotificationChannels
import com.tendril.app.notifications.TimerNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TendrilApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        // §0.6.5 — the shade follows the running timer for the life of the process.
        TimerNotifier(this, container).start()
        // §3.1.1 — index any page without an FTS row (all of them, once, after v16 emptied the table).
        CoroutineScope(Dispatchers.IO).launch { container.pageContentRepository.healIndex() }
    }
}
