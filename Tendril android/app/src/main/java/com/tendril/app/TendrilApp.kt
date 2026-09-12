package com.tendril.app

import android.app.Application
import com.tendril.app.notifications.NotificationChannels
import com.tendril.app.notifications.TimerNotifier

class TendrilApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        // §0.6.5 — the shade follows the running timer for the life of the process.
        TimerNotifier(this, container).start()
    }
}
