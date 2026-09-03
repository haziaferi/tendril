package com.tendril.app

import android.app.Application
import com.tendril.app.notifications.NotificationChannels

class TendrilApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
    }
}
