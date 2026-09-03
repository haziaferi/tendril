package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "tendril_app_lock_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_LOCK_ON_LAUNCH = "lock_on_launch"
private const val KEY_LOCK_ON_BACKGROUND = "lock_on_background"

/**
 * App Lock settings (§3.6) — off by default, matching the app's "opt-in, nothing surprising"
 * pattern. Plain SharedPreferences: these are just toggles, not secrets, unlike [SecretStore].
 */
class AppLockPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lockOnLaunch = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ON_LAUNCH, true))
    val lockOnLaunch: StateFlow<Boolean> = _lockOnLaunch.asStateFlow()

    // Stricter than lock-on-launch, so it defaults off even when App Lock itself is on (§3.6) —
    // re-prompting on every brief backgrounding would be disruptive for all-day personal use.
    private val _lockOnBackground = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ON_BACKGROUND, false))
    val lockOnBackground: StateFlow<Boolean> = _lockOnBackground.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit { putBoolean(KEY_ENABLED, value) }
    }

    fun setLockOnLaunch(value: Boolean) {
        _lockOnLaunch.value = value
        prefs.edit { putBoolean(KEY_LOCK_ON_LAUNCH, value) }
    }

    fun setLockOnBackground(value: Boolean) {
        _lockOnBackground.value = value
        prefs.edit { putBoolean(KEY_LOCK_ON_BACKGROUND, value) }
    }
}
