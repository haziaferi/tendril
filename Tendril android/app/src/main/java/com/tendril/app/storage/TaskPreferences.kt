package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "tendril_task_prefs"
private const val KEY_SHOW_IMPORTANCE = "show_importance"
private const val KEY_SHOW_HABIT_STREAKS = "show_habit_streaks"

/**
 * §0.5.1 progressive disclosure, §0.5.2 the human layer — the two switches that decide whether
 * a pressure-shaped number or flag appears at all. Both default **off**: a person who never opens
 * Settings sees no importance flag on a task and no streak on a habit. Per-device on purpose,
 * like every other preference here: how much of this one wants to see is not data about the
 * tasks, and a device one uses for quiet planning need not match a device one uses for work.
 */
class TaskPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _showImportance = MutableStateFlow(prefs.getBoolean(KEY_SHOW_IMPORTANCE, false))
    /** §0.6.4 — the single *important* flag on a task is shown, and settable, only while this is on. */
    val showImportance: StateFlow<Boolean> = _showImportance.asStateFlow()

    private val _showHabitStreaks = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HABIT_STREAKS, false))
    /** §0.6.6 — the streak left the habit row; this puts the number back for whoever wants it. */
    val showHabitStreaks: StateFlow<Boolean> = _showHabitStreaks.asStateFlow()

    fun setShowImportance(value: Boolean) {
        _showImportance.value = value
        prefs.edit { putBoolean(KEY_SHOW_IMPORTANCE, value) }
    }

    fun setShowHabitStreaks(value: Boolean) {
        _showHabitStreaks.value = value
        prefs.edit { putBoolean(KEY_SHOW_HABIT_STREAKS, value) }
    }
}
