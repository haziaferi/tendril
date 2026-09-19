package com.tendril.app.sync

import kotlinx.serialization.Serializable

/**
 * §9.4 — the per-domain JSON snapshot shapes. Plain, flat, human-readable (ISO-8601 dates
 * /times, epoch-millis timestamps) rather than a byte-for-byte Room dump — this is
 * deliberately schema-independent so it also serves as the Room-recovery format (§9.10)
 * and the portable `.tendril` export payload (§9.4.1), not a third representation.
 */
@Serializable
data class EntrySnapshotRecord(
    /** Cross-device identity (§9.4) — the merge key. Never the local Room autoincrement
     * `id`, which is only unique per-device (see [com.tendril.app.data.entry.Entry.uid]). */
    val uid: String,
    val title: String,
    val kind: String,
    val startDate: String? = null,
    val startTime: String? = null,
    val endDate: String? = null,
    val endTime: String? = null,
    /** Same "FIXED:<rrule>" / "ELASTIC:<period>" encoding as the Room TypeConverter. */
    val recurrenceRule: String? = null,
    /** References another Entry's [uid], not a local `id` (§4.1 exception rows). */
    val originalEntryUid: String? = null,
    val originalOccurrenceDate: String? = null,
    val isExceptionSkip: Boolean? = null,
    val status: String? = null,
    /** §0.6.4 (v10). All four default so a v9 peer's record, which lacks them, reads as a task
     * with no deadline, no parent, no estimate and no flag — which is what it is. */
    val dueDate: String? = null,
    val parentEntryUid: String? = null,
    val estimateSeconds: Long? = null,
    /** v19's flag, still written (as `importance >= 3`) so a peer on the older build keeps reading
     * it; read only when [importance] is absent — a v19 record's `true` lands on 3 (high). */
    val important: Boolean = false,
    /** 14g·3 (v20) — the ladder's set level, 0–4. */
    val importance: Int? = null,
    /** References a Row's (Page's) [com.tendril.app.data.page.Page.uid] — resolved against
     * [PagesSyncEngine]'s page-uid map the same drop-and-self-heal way `originalEntryUid` is,
     * if the Row hasn't merged in on this device yet. */
    val sourceRowUid: String? = null,
    val deletedAt: Long? = null,
    val source: String = "MANUAL",
    /** §9.5 — travels with the record so a second device doesn't push a duplicate Google
     * event for an Entry the first device already linked. */
    val googleEventId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class HabitSnapshotRecord(
    /** Cross-device identity (§9.4) — see [EntrySnapshotRecord.uid]. */
    val uid: String,
    val title: String,
    val time: String? = null,
    val durationSeconds: Long? = null,
    /** "<count>:<unit>", same encoding as the Room TypeConverter. */
    val frequency: String,
    val streak: Int,
    val lastCompletedDate: String? = null,
    /** §8.1.1's undo-check-in snapshot. Travels with the record because it is per-habit state,
     * not per-device: omitting it meant a merged habit arrived with the defaults (0 / null), so
     * the next undo tap on that device reset the streak to zero instead of stepping it back one.
     * Defaulted, so a snapshot written before this field existed still decodes. */
    val previousStreak: Int = 0,
    val previousCompletedDate: String? = null,
    /** §0.10 item 3 (v21) — a counting habit's unit, amount per check-in and the number set for a day;
     * nullable and defaulted, so an older peer's record decodes and an older peer ignores these. */
    val unit: String? = null,
    val amountPerCheckIn: Double? = null,
    val dailyAmount: Double? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * §4 / S2 — a reminder, as it travels.
 *
 * **No `createdAt`/`updatedAt`, unlike every other record here, and that is a property of the
 * entity rather than an omission.** `ReminderDao` has no update path at all: a reminder is
 * inserted once and tombstoned once, never edited. So there is no second write for a timestamp
 * to adjudicate, and the merge is monotonic — "deleted on any device wins" — which needs no
 * ordering at all. Adding a timestamp would imply a last-write-wins race that cannot occur, and
 * invite a later reader to write one.
 *
 * [deletedAt] is why the field exists on the entity. Every device rewrites `reminders.json` in
 * full from its own rows, so a reminder deleted here but still present in a peer's copy would be
 * re-adopted — and a resurrected reminder is not a stale row on a screen, it re-registers an
 * alarm and fires. A tombstone travels; an absence cannot say anything. Tombstones need no
 * collector of their own: the owning Entry's purge hard-deletes it and `CASCADE` takes them.
 */
@Serializable
data class ReminderSnapshotRecord(
    /** Cross-device identity (§9.4) — see [EntrySnapshotRecord.uid]. */
    val uid: String,
    /** References the owning [com.tendril.app.data.entry.Entry.uid], never the local `entryId`.
     * A reminder cannot exist without its Entry — the column is a `CASCADE` foreign key — so
     * unlike `originalEntryUid` this is never dropped-and-self-healed: a record whose Entry has
     * not merged here yet is *held* and republished verbatim, because a write that simply
     * omitted it would delete the peer's reminder from the folder for everyone. */
    val entryUid: String,
    /** "PRESET:<name>" / "CUSTOM:<count>:<unit>", the same encoding as the Room TypeConverter. */
    val offset: String,
    /** ISO-8601 local time. Set only for an occurrence whose own day has no real time (§4). */
    val anchorTime: String? = null,
    val deletedAt: Long? = null,
)

/**
 * §4.1 / S2 — one completion, as it travels.
 *
 * The simplest record here, because the table is append-only: `EntryCompletionDao` has neither an
 * update nor a delete, so the merge is a plain union by [uid]. No tombstone, because nothing
 * deletes one; no `updatedAt`, because nothing edits one. A grow-only set converges whatever
 * order the files arrive in, which is the strongest guarantee any record in this file has.
 *
 * [resolvedAt] is when the task was resolved, not when the row was written — content rather than
 * merge metadata, and it orders nothing.
 */
@Serializable
data class EntryCompletionSnapshotRecord(
    /** Cross-device identity (§9.4) — see [EntrySnapshotRecord.uid]. */
    val uid: String,
    /** References the owning [com.tendril.app.data.entry.Entry.uid] — held, not dropped, for the
     * reason [ReminderSnapshotRecord.entryUid] gives. */
    val entryUid: String,
    val occurrenceDate: String,
    val resolvedAt: Long,
    val status: String,
)

/** §0.6.6 / v10 — one habit check-in, travelling as [com.tendril.app.data.habit.HabitCompletion]
 * does locally: by `habitUid`, with a tombstone and no `updatedAt`, because the only transition
 * is live → deleted and "deleted on any device wins" needs nothing to compare. */
@Serializable
data class HabitCompletionSnapshotRecord(
    val uid: String,
    val habitUid: String,
    val date: String,
    val checkedAt: Long,
    /** §0.10 item 3 — the amount this check-in logged; null on a plain habit's. */
    val value: Double? = null,
    val deletedAt: Long? = null,
)

/** §0.10 item 4 / v22 — one check-in, travelling as [com.tendril.app.data.checkin.CheckIn] does
 * locally: no owner, a tombstone and no `updatedAt` — a row is inserted once and tombstoned once. */
@Serializable
data class CheckInSnapshotRecord(
    val uid: String,
    val date: String,
    val at: Long,
    val mood: Int? = null,
    val energy: Int? = null,
    val deletedAt: Long? = null,
)

/**
 * §0.6.5 / v14 — one stretch of tracked time, travelling as [com.tendril.app.data.track.TimeLog]
 * does locally: the owner by uid (one of the two, never both), a tombstone *and* an `updatedAt`,
 * because a log is both edited (closed) and deletable — "deleted on any device wins", else the
 * later write. An owner that has not merged here yet holds the record, as a reminder's does.
 */
@Serializable
data class TimeLogSnapshotRecord(
    val uid: String,
    val entryUid: String? = null,
    val habitUid: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val deletedAt: Long? = null,
    val updatedAt: Long,
)

/**
 * §5.5.1.1 — one "deleted forever" fact, as it travels. Carrying [purgedAt] rather than just
 * the uid is what makes a purge comparable with an edit: the later of the two wins, so a stale
 * delete can't quietly destroy work a device did after it (see
 * [com.tendril.app.domain.PurgeRegistry]).
 */
@Serializable
data class PurgedRecordSnapshot(
    /** "PAGE", "ENTRY" or "HABIT", matching `PurgedKind`. A reader that does not recognise the
     * value skips the record rather than failing the file, so this list can grow. */
    val kind: String,
    val uid: String,
    val purgedAt: Long,
)

/**
 * §9.4.2 — the folder's own identity, written in the clear beside the snapshots.
 *
 * Two jobs, and they are the same fact seen from either side. It carries the PBKDF2 [salt], so
 * the salt no longer has to be a compile-time constant shared by every install; and its mere
 * presence marks the folder as "this is an encrypted Tendril folder", which is what lets a
 * reader tell an unencrypted file that *belongs* here from one that was dropped in.
 *
 * Deliberately never encrypted: a device that does not yet have the key still has to read the
 * salt in order to derive it. The salt is not a secret — it defeats precomputation, and does
 * that in the open.
 *
 * [createdAt] exists to settle the bootstrap race the design otherwise has: two devices that
 * enable encryption before either has synced both mint a salt, and Syncthing hands the loser a
 * conflict sibling rather than a merge. The earlier one wins, ties broken on the salt bytes, so
 * every device picks the same winner without needing to talk to any other.
 */
@Serializable
data class SyncMetaRecord(
    val version: Int = 1,
    /** Base64, [SnapshotEncryption.SALT_LENGTH_BYTES] of it. */
    val salt: String,
    val createdAt: Long,
)

@Serializable
data class TendrilManifest(
    val appVersion: String,
    val exportedAtEpochMillis: Long,
    /** "full" or "partial" (§9.4.1) — this build only ever produces "full" (no per-Page
     * selective export yet). */
    val kind: String,
    val includedFiles: List<String>,
    /**
     * §9.4.2 — whether every payload entry beside this manifest is AES-256-GCM ciphertext
     * under the sync passphrase. Defaulted false so archives written before this existed still
     * decode; a reader can also tell from [SnapshotEncryption]'s magic prefix on any payload,
     * but having it stated up front is what lets an importer say "this is encrypted" instead of
     * "this is unreadable" before it has touched anything.
     *
     * The manifest itself is deliberately **not** encrypted. It carries no page content — an
     * app version, a timestamp, full-vs-partial, and a list of `pages/<uid>.json` names, which
     * are uids rather than titles. Leaving it readable costs a page *count* and an export date
     * to anyone holding the file, and buys a legible failure and a working §9.4.1 file picker
     * for someone who has the passphrase but mistyped it. Stated here rather than left to be
     * inferred from the code.
     */
    val encrypted: Boolean = false,
)
