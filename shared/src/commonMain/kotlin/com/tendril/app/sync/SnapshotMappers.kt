package com.tendril.app.sync

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.enumOrNull
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.checkin.CheckIn
import com.tendril.app.data.track.TimeLog
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderOffset
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
    dueDate = dueDate?.toString(),
    parentEntryUid = parentEntryId?.let { idToUid[it] },
    estimateSeconds = estimate?.seconds,
    important = importance >= 3,
    importance = importance,
    sourceRowUid = sourceRowId?.let { rowIdToUid[it] },
    deletedAt = deletedAt?.toEpochMilli(),
    source = source.name,
    googleEventId = googleEventId,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

/**
 * §9.4 — a record this build cannot fully read, named precisely enough to tell someone *why*
 * their page or task stopped updating.
 *
 * Thrown by the `toEntity` mappers and caught at each merge boundary, which turns it into a
 * quarantine: the record is skipped for this pass, the local copy is left exactly as it is, and
 * [detail] travels out to the caller through `SnapshotMergeResult`. Quarantine only works if it
 * is legible — "sync is fine, the page just never changes" is the failure that outlives the bug.
 */
class SnapshotDecodeException(val detail: String) : IllegalArgumentException(detail)

private fun undecodable(label: String, raw: String?): Nothing =
    throw SnapshotDecodeException(unrecognisedValueDetail(label, raw))

/** [uidToId] resolves `originalEntryUid` back to this device's local `id` for that uid,
 * if that referenced record has already been merged locally; if not (its own record
 * hasn't merged yet), the link is dropped rather than left dangling — a rare edge case
 * (recurring-exception rows aren't producible from any UI yet) not worth a two-pass fixup.
 * [rowUidToId] resolves `sourceRowUid` the same drop-and-self-heal way — a Row not having
 * merged in yet before its linked Entry is a lot more plausible than the exception-row case
 * above, but still self-heals on the next sync pass once it has.
 *
 * **Drop-and-self-heal is only honest for a Row that is merely late.** It heals because the next
 * pass sees the same peer record again and resolves the link properly — which stops being true
 * the moment this device *republishes* the Entry with the link already nulled, because then the
 * folder no longer holds the record that would have healed it. That is exactly what happens when
 * the Row was quarantined rather than delayed: it will not be in [rowUidToId] on this pass or any
 * later one until the build is updated. So the caller answers that case before it gets here —
 * `SnapshotSyncOrchestrator.mergeEntryContent` withholds an Entry whose `sourceRowUid` names a Row
 * this pass quarantined and holds no local page for, and republishes the peer's bytes untouched.
 * This mapper keeps the plain rule for the plain case; it has no way to tell the two apart, which
 * is precisely why the decision is not its to make.
 *
 * `originalEntryUid` has no such guard and is a known gap rather than a decision: the same nulling
 * is possible when the referenced Entry is itself quarantined, but the reference is intra-family
 * and Entries span two folder files that merge one after the other, so seeing it whole needs both
 * files read before either is applied. Exception rows are not producible from any UI in this
 * build, which is what makes that affordable to leave.
 *
 * Throws [SnapshotDecodeException] when a field carries a value this build has no member for —
 * an Entry written by a *newer* build, which is the ordinary case for two devices on one folder
 * rather than corruption. Callers that merge choose [toEntityOrNull] or catch this; either way
 * the rule is the same, and it is the whole point: **decode fully before touching anything
 * local**. `EntryKind.valueOf` used to throw from the middle of `mergeEntryContent`'s loop, which
 * took every later record in the same file with it and then aborted the pass.
 */
fun EntrySnapshotRecord.toEntity(uidToId: Map<String, Long>, rowUidToId: Map<String, Long>): Entry = Entry(
    uid = uid,
    title = title,
    kind = enumOrNull<EntryKind>(kind) ?: undecodable("entry kind", kind),
    startDate = startDate?.let(LocalDate::parse),
    startTime = startTime?.let(LocalTime::parse),
    endDate = endDate?.let(LocalDate::parse),
    endTime = endTime?.let(LocalTime::parse),
    recurrenceRule = recurrenceRule?.let { raw ->
        val (tag, rest) = raw.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        when (tag) {
            "FIXED" -> RecurrenceRule.Fixed(rest)
            "ELASTIC" -> runCatching { RecurrenceRule.Elastic(Period.parse(rest)) }
                .getOrElse { undecodable("recurrence period", raw) }
            // An unrecognised tag has always read as "not recurring" here, and stays that way:
            // the field is nullable, so this is a value the record could legitimately have
            // carried, not a guess.
            else -> null
        }
    },
    originalEntryId = originalEntryUid?.let { uidToId[it] },
    originalOccurrenceDate = originalOccurrenceDate?.let(LocalDate::parse),
    isExceptionSkip = isExceptionSkip,
    // Absent status is legal (every EVENT has one); a *present* status this build cannot read is
    // not, and quarantines the record rather than quietly flattening a task to "no status".
    status = status?.let { enumOrNull<EntryStatus>(it) ?: undecodable("entry status", it) },
    dueDate = dueDate?.let(LocalDate::parse),
    // Resolved like `originalEntryUid`, and with its known gap: a parent that has not merged
    // here yet reads as no parent, and the link is not guaranteed to heal on a later pass.
    parentEntryId = parentEntryUid?.let { uidToId[it] },
    estimate = estimateSeconds?.let(Duration::ofSeconds),
    importance = (importance ?: if (important) 3 else 0).coerceIn(0, 4),
    sourceRowId = sourceRowUid?.let { rowUidToId[it] },
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    // The exception, and the precedent the rest of this sweep was measured against: `source`
    // grants special handling (GOOGLE_CALENDAR is excluded from the system-calendar mirror), so
    // an origin this build doesn't know is safest read as the one that grants none.
    source = enumOrNull<EntrySource>(source) ?: EntrySource.MANUAL,
    googleEventId = googleEventId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

/** [toEntity]'s quarantining form: null instead of a throw, for a caller that only needs to skip
 * the record and has nothing to say about why. */
fun EntrySnapshotRecord.toEntityOrNull(uidToId: Map<String, Long>, rowUidToId: Map<String, Long>): Entry? =
    runCatching { toEntity(uidToId, rowUidToId) }.getOrNull()

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
    unit = unit,
    amountPerCheckIn = amountPerCheckIn,
    dailyAmount = dailyAmount,
    deletedAt = deletedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

/**
 * Throws [SnapshotDecodeException] on a frequency this build cannot read — see
 * [EntrySnapshotRecord.toEntity] for why that is a quarantine rather than a failure.
 *
 * `"<count>:<unit>"` is un-versioned, so both halves are checked: the destructuring
 * `val (count, unit) = frequency.split(":")` threw `IndexOutOfBounds` on any later encoding, the
 * same way `IntervalUnit.valueOf` threw on a unit added later. Neither is corruption, and neither
 * should cost the other habits in the same file.
 */
fun HabitSnapshotRecord.toEntity(): Habit {
    val parts = frequency.split(":")
    val count = parts.getOrNull(0)?.toIntOrNull() ?: undecodable("habit frequency", frequency)
    val unit = enumOrNull<IntervalUnit>(parts.getOrNull(1)) ?: undecodable("habit frequency unit", frequency)
    return Habit(
        uid = uid,
        title = title,
        time = time?.let(LocalTime::parse),
        duration = durationSeconds?.let(Duration::ofSeconds),
        frequency = HabitFrequency(count, unit),
        streak = streak,
        lastCompletedDate = lastCompletedDate?.let(LocalDate::parse),
        previousStreak = previousStreak,
        previousCompletedDate = previousCompletedDate?.let(LocalDate::parse),
        unit = this.unit,   // the record's unit — the local `unit` above is the frequency's
        amountPerCheckIn = amountPerCheckIn,
        dailyAmount = dailyAmount,
        deletedAt = deletedAt?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}

/** [HabitSnapshotRecord.toEntity]'s quarantining form — see [EntrySnapshotRecord.toEntityOrNull]. */
fun HabitSnapshotRecord.toEntityOrNull(): Habit? = runCatching { toEntity() }.getOrNull()

/**
 * [entryUid] is resolved by the caller from the owning Entry rather than looked up here, which
 * keeps this total: every local reminder has an Entry row behind its `CASCADE` foreign key, and a
 * caller holding one that does not resolve is looking at a state this mapper has no useful answer
 * for.
 */
fun Reminder.toSnapshot(entryUid: String): ReminderSnapshotRecord = ReminderSnapshotRecord(
    uid = uid,
    entryUid = entryUid,
    offset = when (val o = offset) {
        is ReminderOffset.FromPreset -> "PRESET:${o.preset.name}"
        is ReminderOffset.Custom -> "CUSTOM:${o.count}:${o.unit.name}"
    },
    anchorTime = anchorTime?.toString(),
    deletedAt = deletedAt?.toEpochMilli(),
)

/**
 * Throws [SnapshotDecodeException] on an offset this build cannot read.
 *
 * **Deliberately stricter than [com.tendril.app.data.Converters.stringToReminderOffset]**, which
 * falls back to `ONE_DAY`. That fallback is right for Room: the column is non-null, a throw would
 * take the reminder list down with it, and the value being read is one this device itself wrote.
 * It is wrong here, where the value came from another device possibly running a newer build.
 * Flattening an offset this build has no member for would silently change *when someone's alarm
 * fires*, and do it invisibly. Quarantining costs that one reminder and says so — the same trade
 * [HabitSnapshotRecord.toEntity] makes for an unreadable frequency.
 *
 * [entryId] is the caller's to resolve, for the reason [ReminderSnapshotRecord.entryUid] gives.
 */
fun ReminderSnapshotRecord.toEntity(entryId: Long): Reminder {
    val parts = offset.split(":")
    val decoded = when (parts.firstOrNull()) {
        "PRESET" -> ReminderOffset.FromPreset(
            enumOrNull<ReminderOffset.Preset>(parts.getOrNull(1))
                ?: undecodable("reminder offset preset", offset)
        )
        // Both halves checked, not just the unit. "CUSTOM:<count>:<unit>" is un-versioned, so a
        // later build could change its arity as easily as add a unit, and positional indexing
        // threw IndexOutOfBounds on that rather than quarantining it — the same shape of bug
        // [HabitSnapshotRecord.toEntity] documents for frequency.
        "CUSTOM" -> ReminderOffset.Custom(
            parts.getOrNull(1)?.toIntOrNull() ?: undecodable("reminder offset count", offset),
            enumOrNull<IntervalUnit>(parts.getOrNull(2)) ?: undecodable("reminder offset unit", offset),
        )
        else -> undecodable("reminder offset", offset)
    }
    return Reminder(
        uid = uid,
        entryId = entryId,
        offset = decoded,
        anchorTime = anchorTime?.let(LocalTime::parse),
        deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    )
}

/** [ReminderSnapshotRecord.toEntity]'s quarantining form — see [EntrySnapshotRecord.toEntityOrNull]. */
fun ReminderSnapshotRecord.toEntityOrNull(entryId: Long): Reminder? =
    runCatching { toEntity(entryId) }.getOrNull()

/** See [Reminder.toSnapshot] — [entryUid] is the caller's to resolve, for the same reason. */
fun EntryCompletion.toSnapshot(entryUid: String): EntryCompletionSnapshotRecord =
    EntryCompletionSnapshotRecord(
        uid = uid,
        entryUid = entryUid,
        occurrenceDate = occurrenceDate.toString(),
        resolvedAt = resolvedAt.toEpochMilli(),
        status = status.name,
    )

/**
 * Throws [SnapshotDecodeException] on a status this build cannot read. Unlike
 * [EntrySnapshotRecord.toEntity], where an *absent* status is legal because every EVENT has one,
 * a completion always resolved to something — the column is non-null — so there is no "no status"
 * reading for this to fall back to.
 */
fun EntryCompletionSnapshotRecord.toEntity(entryId: Long): EntryCompletion = EntryCompletion(
    uid = uid,
    entryId = entryId,
    occurrenceDate = LocalDate.parse(occurrenceDate),
    resolvedAt = Instant.ofEpochMilli(resolvedAt),
    status = enumOrNull<EntryStatus>(status) ?: undecodable("completion status", status),
)

/** [EntryCompletionSnapshotRecord.toEntity]'s quarantining form — see
 * [EntrySnapshotRecord.toEntityOrNull]. */
fun EntryCompletionSnapshotRecord.toEntityOrNull(entryId: Long): EntryCompletion? =
    runCatching { toEntity(entryId) }.getOrNull()

/** [habitUid] is the owner's cross-device identity, resolved by the caller from the full Habit
 * set for the same reason [Entry.toSnapshot] takes a map. */
fun HabitCompletion.toSnapshot(habitUid: String): HabitCompletionSnapshotRecord =
    HabitCompletionSnapshotRecord(
        uid = uid,
        habitUid = habitUid,
        date = date.toString(),
        checkedAt = checkedAt.toEpochMilli(),
        value = value,
        deletedAt = deletedAt?.toEpochMilli(),
    )

/** Throws [SnapshotDecodeException] on a date this build cannot parse. */
fun HabitCompletionSnapshotRecord.toEntity(habitId: Long): HabitCompletion = HabitCompletion(
    uid = uid,
    habitId = habitId,
    date = runCatching { LocalDate.parse(date) }.getOrElse { undecodable("habit completion date", date) },
    checkedAt = Instant.ofEpochMilli(checkedAt),
    value = value,
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun CheckIn.toSnapshot(): CheckInSnapshotRecord = CheckInSnapshotRecord(
    uid = uid,
    date = date.toString(),
    at = at.toEpochMilli(),
    mood = mood,
    energy = energy,
    deletedAt = deletedAt?.toEpochMilli(),
)

/** Throws [SnapshotDecodeException] on a date this build cannot parse. */
fun CheckInSnapshotRecord.toEntity(): CheckIn = CheckIn(
    uid = uid,
    date = runCatching { LocalDate.parse(date) }.getOrElse { undecodable("check-in date", date) },
    at = Instant.ofEpochMilli(at),
    mood = mood,
    energy = energy,
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

/** The owner's cross-device identity, one of the two, resolved by the caller from the full
 * Entry or Habit set for the same reason [Entry.toSnapshot] takes a map. */
fun TimeLog.toSnapshot(entryUid: String?, habitUid: String?): TimeLogSnapshotRecord =
    TimeLogSnapshotRecord(
        uid = uid,
        entryUid = entryUid,
        habitUid = habitUid,
        startedAt = startedAt.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        deletedAt = deletedAt?.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

fun TimeLogSnapshotRecord.toEntity(entryId: Long?, habitId: Long?): TimeLog = TimeLog(
    uid = uid,
    entryId = entryId,
    habitId = habitId,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let(Instant::ofEpochMilli),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

/** §4 / §9.4 — the Active/Archived split resolved 2026-08-08: Active is the small,
 * frequently-edited set (`PENDING` TASK, or any live EVENT); Archived is everything else
 * (terminal TASKs, anything soft-deleted). */
fun Entry.isActive(): Boolean = deletedAt == null && (kind == EntryKind.EVENT || status == EntryStatus.PENDING)
