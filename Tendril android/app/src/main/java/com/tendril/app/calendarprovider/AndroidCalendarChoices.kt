package com.tendril.app.calendarprovider

import com.tendril.app.storage.SystemCalendarPreferences
import com.tendril.app.ui.calendar.CalendarChoices
import com.tendril.app.ui.calendar.CalendarOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * §9.12's *Calendar* field on the phone: the ticked calendars Tendril may write to, re-read whenever
 * the ticks change, and the last choice as where a new event starts.
 */
class AndroidCalendarChoices(
    private val calendars: SystemCalendars,
    private val preferences: SystemCalendarPreferences,
) : CalendarChoices {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _options = MutableStateFlow<List<CalendarOption>>(emptyList())
    override val options: StateFlow<List<CalendarOption>> = _options.asStateFlow()
    override val lastChosen: StateFlow<String?> = preferences.lastChosen

    init {
        scope.launch {
            preferences.ticked.collect { ticked ->
                _options.value = calendars.calendars()
                    .filter { it.key in ticked && it.writable }
                    .map { CalendarOption(it.key, it.displayName) }
            }
        }
    }

    override fun remember(key: String?) = preferences.setLastChosen(key)
}
