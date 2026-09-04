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
    /** §8.1.1's undo-check-in snapshot. Travels with the record because it is per-habit state,
     * not per-device: omitting it meant a merged habit arrived with the defaults (0 / null), so
     * the next undo tap on that device reset the streak to zero instead of stepping it back one.
     * Defaulted, so a snapshot written before this field existed still decodes. */
    val previousStreak: Int = 0,
    val previousCompletedDate: String? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
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
    /** "PAGE" or "ENTRY", matching `PurgedKind`. */
    val kind: String,
    val uid: String,
    val purgedAt: Long,
)

@Serializable
data class TendrilManifest(
    val appVersion: String,
    val exportedAtEpochMillis: Long,
    /** "full" or "partial" (§9.4.1) — this build only ever produces "full" (no per-Page
     * selective export yet). */
    val kind: String,
    val includedFiles: List<String>,
)
