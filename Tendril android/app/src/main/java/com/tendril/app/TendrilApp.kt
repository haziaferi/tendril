package com.tendril.app

import android.app.Application
import com.tendril.app.notifications.NotificationChannels
import com.tendril.app.notifications.TimerNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tendril.app.ui.settings.applyAppLanguage

class TendrilApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Plan Phase 4 — the app's language before anything formats a date or reads a string:
        // the widgets and notifications run in this process without any window.
        applyAppLanguage(container.languageSettings.current())
        NotificationChannels.ensureCreated(this)
        // §0.6.5 — the shade follows the running timer for the life of the process.
        TimerNotifier(this, container).start()
        // §3.1.1 — index any page without an FTS row (all of them, once, after v16 emptied the table).
        CoroutineScope(Dispatchers.IO).launch { container.pageContentRepository.healIndex() }
    }

    /** A configuration change resets the process's default locale to the device's; the app's language goes back on top. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAppLanguage(container.languageSettings.current())
    }
}
