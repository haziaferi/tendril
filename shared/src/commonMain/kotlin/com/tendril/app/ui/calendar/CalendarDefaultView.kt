package com.tendril.app.ui.calendar

/**
 * B§13.4 14f·2 — the Calendar's opening view is a Settings choice (decided 2026-09-13), kept in
 * `KeyValueStore` under [CALENDAR_DEFAULT_VIEW_KEY] as one of [CalendarViewKey]; nothing stored
 * means **Week on a wide window** (Fantastical, Sunsama and Akiflow open on the week) and **Day
 * otherwise** — the phone's default since §2.2, not Month, which B§13.4's row had assumed.
 */
enum class CalendarViewKey(val key: String, val label: String) {
    DAY("day", "Day"), WEEK("week", "Week"), MONTH("month", "Month"), AGENDA("agenda", "Agenda");

    companion object {
        fun fromKey(key: String?): CalendarViewKey? = entries.firstOrNull { it.key == key }
    }
}

const val CALENDAR_DEFAULT_VIEW_KEY = "calendar_default_view"

/** The view the tab opens on: the stored choice, else the width's default. Pure, tested. */
fun defaultCalendarView(stored: String?, wide: Boolean): CalendarViewKey =
    CalendarViewKey.fromKey(stored) ?: if (wide) CalendarViewKey.WEEK else CalendarViewKey.DAY
