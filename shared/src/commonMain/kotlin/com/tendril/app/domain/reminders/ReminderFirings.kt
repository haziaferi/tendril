package com.tendril.app.domain.reminders

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.toDuration
import com.tendril.app.domain.nextHabitReminderAt
import com.tendril.app.domain.recurrence.EntryOccurrences
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What a firing is for: the task's start passing, a reminder set before it, a habit's hour. */
enum class FiringKind { OVERDUE, REMINDER, HABIT }

/**
 * One moment a notification is owed. [title] is the entry's or habit's; [detail] is the line
 * under it (*14:30*, *in 10 min*, *every 3 days*), spelled here so both platforms say the same.
 */
data class Firing(
    val kind: FiringKind,
    val at: Instant,
    val entryId: Long? = null,
    val habitId: Long? = null,
    val reminderId: Long? = null,
    val title: String,
    val detail: String,
    /** The desktop opens the Calendar for an EVENT's firing and Tasks for the rest. */
    val isEvent: Boolean = false,
)

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * B§13.6 #7 — the alarm arithmetic that lived only in Android's `AlarmScheduler.rescheduleFor`,
 * lifted out unchanged so the desktop's scheduler and the phone's alarms agree to the minute
 * (§9.7). The rules, in order: a trashed entry fires nothing; a resolved TASK fires nothing (an
 * EVENT has no status to resolve, so pre-due reminders on one still count); the occurrence the
 * firings anchor to is `startDate`, except for a recurring EVENT, which anchors to its next
 * occurrence still in the future (§4.1 — otherwise a weekly meeting reminded exactly once); a TASK
 * owes an OVERDUE firing at its start date + time, or midnight without one; every live reminder
 * owes a firing at `base − offset`, the base being the entry's own time on that day, else the
 * reminder's `anchorTime`, else midnight; and nothing at or before [now] is ever returned —
 * a past firing would arrive at once, and a bulk write would arrive as a flood.
 */
fun entryFirings(
    entry: Entry,
    reminders: List<Reminder>,
    exceptions: List<Entry> = emptyList(),
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Firing> {
    if (entry.deletedAt != null) return emptyList()
    if (entry.kind == EntryKind.TASK && entry.status != EntryStatus.PENDING) return emptyList()
    val startDate = nextOccurrenceDate(entry, exceptions, now, zone) ?: return emptyList()
    val out = mutableListOf<Firing>()
    if (entry.kind == EntryKind.TASK) {
        val at = LocalDateTime.of(startDate, entry.startTime ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
        if (at.isAfter(now)) out += Firing(FiringKind.OVERDUE, at, entryId = entry.id, title = entry.title, detail = entry.startTime?.format(CLOCK) ?: "today")
    }
    reminders.filter { it.deletedAt == null }.forEach { reminder ->
        val base = LocalDateTime.of(startDate, entry.startTime ?: reminder.anchorTime ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
        val at = base.minus(reminder.offset.toDuration())
        if (at.isAfter(now)) {
            out += Firing(FiringKind.REMINDER, at, entryId = entry.id, reminderId = reminder.id, title = entry.title, detail = beforeLabel(reminder.offset.toDuration(), base, zone), isEvent = entry.kind == EntryKind.EVENT)
        }
    }
    return out.sortedBy { it.at }
}

/** A habit's next firing — [nextHabitReminderAt]'s answer as a [Firing]; null when it has no hour. */
fun habitFiring(habit: Habit, now: ZonedDateTime = ZonedDateTime.now()): Firing? {
    val at = nextHabitReminderAt(habit, now) ?: return null
    return Firing(FiringKind.HABIT, at, habitId = habit.id, title = habit.title, detail = habit.time?.format(CLOCK) ?: "")
}

/** *in 10 min · 14:30* — the gap the reminder was set at, then the moment it counts back from. */
private fun beforeLabel(offset: Duration, base: Instant, zone: ZoneId): String {
    val minutes = offset.toMinutes()
    val gap = when {
        minutes < 60 -> "in $minutes min"
        minutes % (24 * 60) == 0L -> "in ${minutes / (24 * 60)} d"
        minutes % 60 == 0L -> "in ${minutes / 60} h"
        else -> "in ${minutes / 60} h ${minutes % 60} min"
    }
    return "$gap · ${base.atZone(zone).toLocalTime().format(CLOCK)}"
}

/**
 * Which occurrence the firings anchor to: `startDate`, or for a recurring EVENT the first
 * occurrence whose start is still ahead of [now] (today's, once it has started, is behind).
 * Only that one — the phone's request codes are per (entry, reminder), so a run of occurrences
 * would collide; the next is armed when this one passes (§9.7's sweep, the desktop's replan).
 */
private fun nextOccurrenceDate(entry: Entry, exceptions: List<Entry>, now: Instant, zone: ZoneId): LocalDate? {
    val anchor = entry.startDate ?: return null
    if (entry.kind != EntryKind.EVENT || entry.recurrenceRule !is RecurrenceRule.Fixed) return anchor
    val today = now.atZone(zone).toLocalDate()
    val candidates = EntryOccurrences
        .expand(listOf(entry) + exceptions, today, today.plusDays(EntryOccurrences.DEFAULT_ALARM_HORIZON_DAYS))
        .filter { it.isFirstDay && (it.entry.id == entry.id || it.entry.originalEntryId == entry.id) }
    return candidates.firstOrNull { o ->
        LocalDateTime.of(o.startDate, o.startTime ?: LocalTime.MIDNIGHT).atZone(zone).toInstant().isAfter(now)
    }?.startDate ?: candidates.firstOrNull()?.startDate
}
