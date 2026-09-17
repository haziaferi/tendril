package com.tendril.app.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * L5 (2026-09-17) — the Calendar's bar carries the range and its navigation on a wide window
 * (`‹ › September 2026 · Today`, Google's row; Notion Calendar spends a second row on it), so
 * the grids no longer draw their own ‹ › rows there. Pure: the title for a view at its anchor
 * day, and the anchor one step over. The Agenda runs from today and does not step.
 */
fun rangeTitle(view: CalendarView, anchor: LocalDate, locale: Locale = Locale.getDefault(), agendaDays: Int = 30): String = when (view) {
    CalendarView.DAY -> anchor.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
    CalendarView.WEEK -> {
        val start = weekStartOf(anchor)
        weekLabel(start, start.plusDays(6), locale)
    }
    CalendarView.MONTH -> YearMonth.from(anchor).let { "${it.month.getDisplayName(TextStyle.FULL, locale)} ${it.year}" }
    CalendarView.AGENDA -> "Next $agendaDays days"
}

/** The anchor `steps` units on: a day, a week, a month; the Agenda stays. */
fun shiftedAnchor(view: CalendarView, anchor: LocalDate, steps: Int): LocalDate = when (view) {
    CalendarView.DAY -> anchor.plusDays(steps.toLong())
    CalendarView.WEEK -> anchor.plusWeeks(steps.toLong())
    CalendarView.MONTH -> anchor.plusMonths(steps.toLong())
    CalendarView.AGENDA -> anchor
}

/** Whether the view has anything to step through — the Agenda's ‹ › would do nothing. */
fun CalendarView.steps(): Boolean = this != CalendarView.AGENDA

fun weekStartOf(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

/** `14 – 20 September 2026`, or `28 Sep – 4 Oct 2026` across a month boundary (the Week grid's label since 14f·2). */
fun weekLabel(first: LocalDate, last: LocalDate, locale: Locale = Locale.getDefault()): String =
    if (first.month == last.month) "${first.dayOfMonth} – ${last.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}"
    else "${first.format(DateTimeFormatter.ofPattern("d MMM", locale))} – ${last.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))}"
