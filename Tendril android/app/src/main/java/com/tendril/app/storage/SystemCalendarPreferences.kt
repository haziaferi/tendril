package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "tendril_system_calendars"
private const val KEY_TICKED = "ticked"
private const val KEY_LAST_CHOSEN = "last_chosen"

/**
 * §9.12 — per device, never synced: which of this phone's calendars Tendril reads, and the one a
 * new event starts in. The keys are `Entry.calendarKey`s; an empty last choice is "this phone only".
 */
class SystemCalendarPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _ticked = MutableStateFlow(prefs.getStringSet(KEY_TICKED, emptySet()).orEmpty().toSet())
    val ticked: StateFlow<Set<String>> = _ticked.asStateFlow()

    private val _lastChosen = MutableStateFlow(prefs.getString(KEY_LAST_CHOSEN, "").orEmpty().ifEmpty { null })
    /** The calendar a new event starts in; null for this phone only. */
    val lastChosen: StateFlow<String?> = _lastChosen.asStateFlow()

    fun setTicked(key: String, on: Boolean) {
        val next = if (on) _ticked.value + key else _ticked.value - key
        _ticked.value = next
        prefs.edit { putStringSet(KEY_TICKED, next) }
        // A calendar no longer read cannot be where new events start.
        if (!on && _lastChosen.value == key) setLastChosen(null)
    }

    fun setLastChosen(key: String?) {
        _lastChosen.value = key
        prefs.edit { putString(KEY_LAST_CHOSEN, key.orEmpty()) }
    }
}
