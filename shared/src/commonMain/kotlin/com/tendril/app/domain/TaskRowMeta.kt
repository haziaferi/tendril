package com.tendril.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The phone's fix PR (P6, 2026-09-18) — a task row's second line, one formatter for both
 * profiles: the When as the tray's *sab 12* (the month named only outside this month), its
 * time, the deadline as *due …*, the steps, the tracked segment; parts joined by ` · `, none
 * of them an ISO date. `phone-catch-up.md` #6: *2026-09-12 · due 2026-09-11 · 0/1 steps*
 * wrapped the phone's rows to 86 dp.
 */
fun taskRowMeta(
    startDate: LocalDate?,
    startTime: LocalTime?,
    dueDate: LocalDate?,
    stepsDone: Int,
    stepsTotal: Int,
    logged: String?,
    today: LocalDate,
): String = listOfNotNull(
    startDate?.let { dayLabel(it, today) },
    startTime?.format(CLOCK),
    dueDate?.let { "due " + dayLabel(it, today) },
    if (stepsTotal > 0) "$stepsDone/$stepsTotal steps" else null,
    logged,
).joinToString(" · ")

/** *sab 12* in this month, *sab 12 ott* outside it. */
fun dayLabel(day: LocalDate, today: LocalDate): String =
    if (day.year == today.year && day.month == today.month) day.format(DAY) else day.format(DAY_MONTH)
