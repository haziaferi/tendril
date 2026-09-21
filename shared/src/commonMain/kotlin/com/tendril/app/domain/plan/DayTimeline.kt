package com.tendril.app.domain.plan

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.recurrence.EntryOccurrence
import java.time.Duration
import java.time.LocalTime

/**
 * §0.6.5 / §0.8 step 7b — one placed thing on the day's timeline. Minutes from midnight, so a
 * screen can scale them to pixels however tall its hour is; [lane]/[lanes] pack overlapping
 * blocks side by side, the way every day view does.
 */
data class TimelineBlock(
    val key: String,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    /** True when the length came from an estimate or the default rather than a real end. */
    val estimated: Boolean,
    val lane: Int,
    val lanes: Int,
    val occurrence: EntryOccurrence? = null,
    val kind: BlockKind,
) {
    val minutes: Int get() = endMinute - startMinute
}

/** A block's layer. A habit is no block since 2026-09-21 — it is a stroke under them (`HabitStrokes.kt`). */
enum class BlockKind { EVENT, TASK, OTHER }

/** Something that is on the day but not an Entry (a database row's date, say — habits are strokes, not blocks). */
data class TimelineExtra(val key: String, val title: String, val time: LocalTime, val duration: Duration?, val kind: BlockKind)

/**
 * The timeline, from the occurrences the Calendar already computes. A timed event is its span;
 * a timed task is its estimate, or [defaultTaskMinutes] when it has none — drawn as estimated,
 * so the person sees it is a guess. Untimed items are not here: they are the all-day strip, which
 * the screen lists on its own. Overlaps are packed into lanes greedily in start order, which is
 * enough for a personal day and the same thing Google Calendar does.
 */
fun timelineBlocks(
    occurrences: List<EntryOccurrence>,
    extras: List<TimelineExtra> = emptyList(),
    defaultTaskMinutes: Int = 30,
    minBlockMinutes: Int = 15,
): List<TimelineBlock> {
    val raw = mutableListOf<TimelineBlock>()
    for (o in occurrences) {
        val start = o.startTime ?: continue
        if (!o.isFirstDay) continue
        val e = o.entry
        val startMin = start.toSecondOfDay() / 60
        val (endMin, estimated) = when {
            e.kind == EntryKind.EVENT && o.endTime != null && o.endDate == o.startDate && o.endTime > start -> (o.endTime.toSecondOfDay() / 60) to false
            e.kind == EntryKind.EVENT && o.endDate > o.startDate -> (24 * 60) to false
            e.estimate != null -> (startMin + e.estimate.toMinutes().toInt()) to true
            else -> (startMin + defaultTaskMinutes) to true
        }
        raw += TimelineBlock(
            key = "${e.id}:${o.startDate}", title = e.title, startMinute = startMin,
            endMinute = maxOf(endMin, startMin + minBlockMinutes).coerceAtMost(24 * 60), estimated = estimated,
            lane = 0, lanes = 1, occurrence = o, kind = if (e.kind == EntryKind.EVENT) BlockKind.EVENT else BlockKind.TASK,
        )
    }
    for (x in extras) {
        val startMin = x.time.toSecondOfDay() / 60
        val len = x.duration?.toMinutes()?.toInt() ?: defaultTaskMinutes
        raw += TimelineBlock(
            key = x.key, title = x.title, startMinute = startMin,
            endMinute = maxOf(startMin + len, startMin + minBlockMinutes).coerceAtMost(24 * 60),
            estimated = x.duration == null, lane = 0, lanes = 1, kind = x.kind,
        )
    }
    return packLanes(raw.sortedWith(compareBy({ it.startMinute }, { -it.minutes }, { it.title })))
}

/** Greedy lane packing: a block takes the first lane free at its start; a run of overlapping
 * blocks (a cluster) shares one lane count so widths line up. */
private fun packLanes(sorted: List<TimelineBlock>): List<TimelineBlock> {
    val out = mutableListOf<TimelineBlock>()
    var cluster = mutableListOf<TimelineBlock>()
    var clusterEnd = -1
    val laneEnds = mutableListOf<Int>()
    fun flush() {
        val lanes = laneEnds.size.coerceAtLeast(1)
        out += cluster.map { it.copy(lanes = lanes) }
        cluster = mutableListOf(); laneEnds.clear(); clusterEnd = -1
    }
    for (b in sorted) {
        if (b.startMinute >= clusterEnd && cluster.isNotEmpty()) flush()
        var lane = laneEnds.indexOfFirst { it <= b.startMinute }
        if (lane < 0) { laneEnds += b.endMinute; lane = laneEnds.lastIndex } else laneEnds[lane] = b.endMinute
        cluster += b.copy(lane = lane)
        clusterEnd = maxOf(clusterEnd, b.endMinute)
    }
    if (cluster.isNotEmpty()) flush()
    return out
}

/** A dropped position on the grid, snapped to the nearest [step] minutes and kept inside the day. */
fun snapMinute(minuteOfDay: Int, step: Int = 15): Int =
    ((minuteOfDay + step / 2) / step * step).coerceIn(0, 24 * 60 - step)

fun timeOfMinute(minuteOfDay: Int): LocalTime = LocalTime.ofSecondOfDay(minuteOfDay.coerceIn(0, 24 * 60 - 1) * 60L)

/** The tasks a Plan-mode rail offers: today's untimed ones and the Someday ones, never steps. */
fun unplannedTasks(tasks: List<Entry>, day: java.time.LocalDate): List<Entry> =
    tasks.filter { it.kind == EntryKind.TASK && it.parentEntryId == null && it.originalEntryId == null && it.startTime == null && (it.startDate == null || it.startDate == day) }
        .sortedWith(compareBy({ it.startDate == null }, { it.title }))
