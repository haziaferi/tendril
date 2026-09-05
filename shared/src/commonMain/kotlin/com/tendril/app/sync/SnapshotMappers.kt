package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/**
 * [idToUid] resolves the local-only `originalEntryId` FK to the cross-device `uid` it
 * should travel as; build it once from the full Entry set before mapping many records,
 * rather than making every single mapping call suspend/DB-aware. [rowIdToUid] does the same
 * for `sourceRowId` — a Page id, from `pageDao.getAll()`, not the Entry set.
 */
fun Entry.toSnapshot(idToUid: Map<Long, String>, rowIdToUid: Map<Long, String>): EntrySnapshotRecord = EntrySnapshotRecord(
    uid = uid,
    title = title,
    kind = kind.name,
    startDate = startDate?.toString(),
    startTime = startTime?.toString(),
    endDate = endDate?.toString(),
    endTime = endTime?.toString(),
    recurrenceRule = when (val r = recurrenceRule) {
        null -> null
        is RecurrenceRule.Fixed -> "FIXED:${r.rrule}"
        is RecurrenceRule.Elastic -> "ELASTIC:${r.period}"
    },
    originalEntryUid = originalEntryId?.let { idToUid[it] },
    originalOccurrenceDate = originalOccurrenceDate?.toString(),
    isExceptionSkip = isExceptionSkip,
    status = status?.name,
    sourceRowUid = sourceRowId?.let { rowIdToUid[it] },
    deletedAt = deletedAt?.toEpochMilli(),
    source = source.name,
    googleEventId = googleEventId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

/** [uidToId] resolves `originalEntryUid` back to this device's local `id` for that uid,
 * if that referenced record has already been merged locally; if not (its own record
 * hasn't merged yet), the link is dropped rather than left dangling — a rare edge case
 * (recurring-exception rows aren't producible from any UI yet) not worth a two-pass fixup.
 * [rowUidToId] resolves `sourceRowUid` the same drop-and-self-heal way — a Row not having
 * merged in yet before its linked Entry is a lot more plausible than the exception-row case
 * above, but still self-heals on the next sync pass once it has. */
fun EntrySnapshotRecord.toEntity(uidToId: Map<String, Long>, rowUidToId: Map<String, Long>): Entry = Entry(
    uid = uid,
    title = title,
    kind = EntryKind.valueOf(kind),
    startDate = startDate?.let(LocalDate::parse),
    startTime = startTime?.let(LocalTime::parse),
    endDate = endDate?.let(LocalDate::parse),
    endTime = endTime?.let(LocalTime::parse),
    recurrenceRule = recurrenceRule?.let { raw ->
        val (tag, rest) = raw.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        when (tag) {
            "FIXED" -> RecurrenceRule.Fixed(rest)
            "ELASTIC" -> RecurrenceRule.Elastic(Period.parse(rest))
            else -> null
        }
    },
    originalEntryId = originalEntryUid?.let { uidToId[it] },
    originalOccurrenceDate = originalOccurrenceDate?.let(LocalDate::parse),
    isExceptionSkip = isExceptionSkip,
    status = status?.let(EntryStatus::valueOf),
    sourceRowId = sourceRowUid?.let { rowUidToId[it] },
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    source = runCatching { EntrySource.valueOf(source) }.getOrDefault(EntrySource.MANUAL),
    googleEventId = googleEventId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Habit.toSnapshot(): HabitSnapshotRecord = HabitSnapshotRecord(
    uid = uid,
    title = title,
    time = time?.toString(),
    durationSeconds = duration?.seconds,
    frequency = "${frequency.count}:${frequency.unit.name}",
    streak = streak,
    lastCompletedDate = lastCompletedDate?.toString(),
    previousStreak = previousStreak,
    previousCompletedDate = previousCompletedDate?.toString(),
    deletedAt = deletedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun HabitSnapshotRecord.toEntity(): Habit {
    val (count, unit) = frequency.split(":")
    return Habit(
        uid = uid,
        title = title,
        time = time?.let(LocalTime::parse),
        duration = durationSeconds?.let(Duration::ofSeconds),
        frequency = HabitFrequency(count.toInt(), IntervalUnit.valueOf(unit)),
        streak = streak,
        lastCompletedDate = lastCompletedDate?.let(LocalDate::parse),
        previousStreak = previousStreak,
        previousCompletedDate = previousCompletedDate?.let(LocalDate::parse),
        deletedAt = deletedAt?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}

/** §4 / §9.4 — the Active/Archived split resolved 2026-08-08: Active is the small,
 * frequently-edited set (`PENDING` TASK, or any live EVENT); Archived is everything else
 * (terminal TASKs, anything soft-deleted). */
fun Entry.isActive(): Boolean = deletedAt == null && (kind == EntryKind.EVENT || status == EntryStatus.PENDING)
