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
    /**
     * §8.1.1's undo stash. Omitted from this record originally, so a remote-wins habit merge
     * reset both to their defaults and an undo tapped after a sync round-trip set `streak = 0`
     * instead of restoring it — exactly the reconstruction failure §8.1.1 says the stash
     * exists to prevent ("a weekly+ habit's grace period means 'just subtract a day' can't
     * reconstruct what `lastCompletedDate` actually was"). Defaulted, so older snapshot files
     * still decode.
     */
    val previousStreak: Int = 0,
    val previousCompletedDate: String? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
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
