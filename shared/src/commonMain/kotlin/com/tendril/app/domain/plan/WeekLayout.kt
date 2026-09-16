package com.tendril.app.domain.plan

/**
 * 14f·2 — the week grid's pure arithmetic. [laneAt] maps a pointer's x, in pixels from the
 * grid's left, to a day column; [openScrollMinute] says where the grid opens: an hour before the
 * week's earliest block, else 07:00 (the mock's grid ran off its frame with nothing scrolling —
 * `docs/critiques/tasks-calendar-mock.md` #6). [visibleAllDay] caps the all-day row at three,
 * the rest behind "+n".
 */
fun laneAt(xPx: Float, gutterPx: Float, laneWidthPx: Float, lanes: Int = 7): Int =
    ((xPx - gutterPx) / laneWidthPx).toInt().coerceIn(0, lanes - 1)

fun openScrollMinute(blocks: List<TimelineBlock>, morning: Int = 7 * 60): Int =
    (blocks.minOfOrNull { it.startMinute }?.let { it - 60 } ?: morning).coerceAtLeast(0)

fun <T> visibleAllDay(items: List<T>, cap: Int = 3): Pair<List<T>, Int> =
    if (items.size <= cap) items to 0 else items.take(cap - 1) to (items.size - (cap - 1))
