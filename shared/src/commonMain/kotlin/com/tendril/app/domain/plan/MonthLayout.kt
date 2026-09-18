package com.tendril.app.domain.plan

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/**
 * The Month grid's pure arithmetic (the desktop audit's L4, 2026-09-17). Every desktop calendar
 * measured or cited draws equal rows that fill the pane; what a cell can show is then a function
 * of its height, not a number picked once — the standing rule that nothing is fixed where it
 * could be proportional. [monthGridDays] is the grid: the weeks the month spans, from the
 * [firstDay] on or before the 1st to the day before the next [firstDay] after the last — four,
 * five or six rows, never a padding row (Google's rule; Notion's database calendar always draws
 * six). [cellCapacity] is how many chips a cell holds; [visibleInCell] takes the last slot for
 * *+n* when the day overflows, so the count never pushes past the cell — the Week's all-day row
 * is the same rule at a fixed three ([visibleAllDay]). [dayLabel] names the 1st with its month
 * (Notion's *Sep 1*). [monthDots] is the phone's cell: one dot per layer present, at most three,
 * by precedence.
 */
fun monthGridDays(month: YearMonth, firstDay: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val start = first.minusDays((((first.dayOfWeek.value - firstDay.value) % 7) + 7).toLong() % 7)
    val lastOfWeek = firstDay.minus(1)
    val end = last.plusDays((((lastOfWeek.value - last.dayOfWeek.value) % 7) + 7).toLong() % 7)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}

/** The first and last day the grid shows — the range the occurrences are expanded over, so adjacent-month days carry their items. */
fun monthRange(month: YearMonth, firstDay: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, LocalDate> =
    monthGridDays(month, firstDay).let { it.first() to it.last() }

/** Chips a cell of [cellHeightPx] holds under its number row and inset, at least one. */
fun cellCapacity(cellHeightPx: Float, numberRowPx: Float, chipPitchPx: Float, insetPx: Float = 0f): Int =
    floor((cellHeightPx - numberRowPx - insetPx) / chipPitchPx).toInt().coerceAtLeast(1)

/** What a cell shows: everything when it fits, else `capacity − 1` items and the count of the rest for the *+n* slot. */
fun <T> visibleInCell(items: List<T>, capacity: Int): Pair<List<T>, Int> {
    val cap = capacity.coerceAtLeast(1)
    return if (items.size <= cap) items to 0 else items.take(cap - 1) to (items.size - (cap - 1))
}

/** The cell's number: *17*, or *Sep 1* on the first of any month in the grid. */
fun dayLabel(day: LocalDate, locale: Locale = Locale.getDefault()): String =
    if (day.dayOfMonth == 1) "${day.month.getDisplayName(TextStyle.SHORT, locale)} 1" else day.dayOfMonth.toString()

/** The phone's dot kinds, in the precedence a cell keeps when more than [cap] layers fall on one day. */
enum class DotKind { TASK, EVENT, HABIT, DATABASE }

fun monthDots(kinds: Collection<DotKind>, cap: Int = 3): List<DotKind> = kinds.distinct().sortedBy { it.ordinal }.take(cap)

/**
 * A weekday's one-letter initial in the phone's language (T·P4 / F·P3, item 23's Lows, 2026-09-18):
 * the Timeline spelled *S M T W T F S* from the enum's English name on an Italian phone whose Month
 * header read *L M M G V S D*. One spelling — `TextStyle.NARROW`, upper-cased — for both.
 */
fun weekdayInitial(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
    day.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
