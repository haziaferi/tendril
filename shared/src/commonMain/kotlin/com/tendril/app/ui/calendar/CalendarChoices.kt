package com.tendril.app.ui.calendar

import kotlinx.coroutines.flow.StateFlow

/** One calendar an event can live in (§9.12): its `Entry.calendarKey`, and what the sheet calls it. */
data class CalendarOption(val key: String, val label: String)

/**
 * §9.12 — the calendars an event can live in, on a device that has any: the phone's ticked,
 * writable calendars. The desktop has no system calendar and passes none, so its sheet shows no
 * *Calendar* field and its new events stay phone-only until edited on the phone.
 */
interface CalendarChoices {
    /** Ticked calendars Tendril may write to; "this phone only" is always offered beside them. */
    val options: StateFlow<List<CalendarOption>>

    /** Where a new event starts: the last calendar chosen, null for this phone only. */
    val lastChosen: StateFlow<String?>

    /** Remember [key] (null: this phone only) as where the next new event starts. */
    fun remember(key: String?)
}
