package com.tendril.app.domain.timeline

import com.tendril.app.data.page.Page
import com.tendril.app.data.pagedatabase.parseRelationValue
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * §0.6.14 / B§6 #17 — the Timeline view's shape, pure. The ViewModel supplies what a row's
 * start and end are (through the same bound-or-stored read every view uses) and where a
 * "blocked by" cell points; this decides what becomes a bar, how wide the grid is, what a drag
 * means, and whether a row is blocked.
 */
data class TimelineBar<R>(val row: R, val start: LocalDate, val end: LocalDate) {
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1
}

/** Undated rows are not bars (the view lists them under the grid); an end before its start is a
 * one-day bar rather than a negative one. */
fun <R> timelineBars(rows: List<R>, startOf: (R) -> LocalDate?, endOf: (R) -> LocalDate?): List<TimelineBar<R>> =
    rows.mapNotNull { row ->
        val start = startOf(row) ?: return@mapNotNull null
        val end = endOf(row)?.takeIf { !it.isBefore(start) } ?: start
        TimelineBar(row, start, end)
    }

const val TIMELINE_PAD_BEFORE = 7L
const val TIMELINE_PAD_AFTER = 14L

/** A week before the first bar to two weeks after the last, always holding [today]; with no
 * bars, three weeks around today. */
fun <R> timelineRange(bars: List<TimelineBar<R>>, today: LocalDate): ClosedRange<LocalDate> {
    if (bars.isEmpty()) return today.minusDays(TIMELINE_PAD_BEFORE)..today.plusDays(TIMELINE_PAD_AFTER)
    val first = minOf(bars.minOf { it.start }, today)
    val last = maxOf(bars.maxOf { it.end }, today)
    return first.minusDays(TIMELINE_PAD_BEFORE)..last.plusDays(TIMELINE_PAD_AFTER)
}

/**
 * L13 (small things III, 2026-09-18): the column the Timeline opens scrolled to — today three
 * columns in, so the days just gone are visible, and never past the day before the earliest
 * bar (the report's *Call the library* opened clipped at the pane's left edge). In days from the range's start.
 */
fun <R> timelineInitialColumn(range: ClosedRange<LocalDate>, bars: List<TimelineBar<R>>, today: LocalDate): Long {
    val todayColumn = ChronoUnit.DAYS.between(range.start, today)
    val firstBarColumn = bars.minOfOrNull { ChronoUnit.DAYS.between(range.start, it.start) } ?: todayColumn
    // A day of margin before the first bar: its title is drawn from the bar's start leftwards.
    return minOf(todayColumn - 3, firstBarColumn - 1).coerceAtLeast(0)
}

/** The drag: start and end move together, by whole days. */
fun <R> TimelineBar<R>.shifted(days: Long): TimelineBar<R> = copy(start = start.plusDays(days), end = end.plusDays(days))

/** [blockers] — every row the "blocked by" cell resolves to; [open] — those not done, the ones
 * that still block. */
data class BlockedState(val blockers: List<Page>, val open: List<Page>) {
    val isBlocked: Boolean get() = open.isNotEmpty()
}

/**
 * [relationValue] is the row's "blocked by" cell (row page uids, comma-joined); [rowByUid]
 * resolves each to a page this device has (a uid it lacks is dropped, the relation cell's own
 * tolerance); [isDone] is the Done binding's answer — pass `{ false }` when the database has no
 * Done binding: a database that cannot say "done" cannot say "unblocked", so every blocker
 * counts.
 */
fun blockedBy(relationValue: String?, rowByUid: (String) -> Page?, isDone: (Page) -> Boolean): BlockedState {
    val blockers = parseRelationValue(relationValue).mapNotNull(rowByUid)
    return BlockedState(blockers, blockers.filterNot(isDone))
}
