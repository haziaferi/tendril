package com.tendril.app.data.entry

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class EntryKind { TASK, EVENT }

/** TASK-only (§4) — an EVENT has no meaningful done/not-done state. */
enum class EntryStatus { PENDING, DONE, SKIPPED }

/**
 * §4 / §4.1 — Task and Event are the same underlying table (one query surface, avoiding
 * dual-write/id-mapping/drift risk) but not the same *kind* of row; `kind` is the
 * discriminator. See the class-level notes on each field for the invariants Room itself
 * can't enforce.
 */
@Entity(tableName = "entries", indices = [Index("uid", unique = true)])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable cross-device identity for §9.4 snapshot merge — a local Room `id` is only
     * unique on the device that assigned it; two offline devices can independently create
     * records that land on the same autoincrement `id`, which would silently corrupt a
     * merge keyed on `id`. Generated once at creation, never reassigned. */
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: EntryKind,

    val startDate: LocalDate?,
    val startTime: LocalTime?,
    /** EVENT-only — permanently null for `kind = TASK` (§4.1: "a Task is never a span"). */
    val endDate: LocalDate?,
    /** EVENT-only — permanently null for `kind = TASK`. */
    val endTime: LocalTime?,

    /** `Fixed` only legal for EVENT, `Elastic` only legal for TASK (§4.1 R2) — upheld by the
     * shape of the create/edit call sites, but not actually checked anywhere; see
     * [RecurrenceRule]'s own KDoc for which paths bypass it. */
    val recurrenceRule: RecurrenceRule?,

    /** Points at the recurring base Entry when this row is a single-occurrence exception
     * (§4.1) — a skip tombstone (`isExceptionSkip = true`) or a full per-occurrence override. */
    val originalEntryId: Long? = null,
    val originalOccurrenceDate: LocalDate? = null,
    val isExceptionSkip: Boolean? = null,

    /** TASK-only tri-state; always null for EVENT (§4). */
    val status: EntryStatus? = null,

    /**
     * §0.6.4 — the *Deadline*, optional and TASK-only. [startDate] is the *When*: the day the
     * person plans to do it, where Calendar draws it and what §5.2 binds. Those were one field
     * until v10, which meant every dated task was implicitly due that day; this is the second
     * date, rare and real, that Things and OmniFocus keep apart from the plan. Nothing sets it
     * by migration — an existing task gains no deadline it never had.
     */
    val dueDate: LocalDate? = null,

    /** §0.6.4 — a checklist-style sub-task's parent. Children carry no dates of their own until
     * someone asks for that. Travels as `parentEntryUid`, resolved the way `originalEntryId` is,
     * with the same known gap (see [com.tendril.app.sync.EntrySnapshotRecord]). */
    val parentEntryId: Long? = null,

    /** §0.6.4 / §0.6.5 — how long this is expected to take. Stored now so the field is not
     * designed without its consumers; shown nowhere until Plan mode or tracking reads it. */
    val estimate: Duration? = null,

    /** §0.6.4 — the single opt-in *important* flag: a flag and not a scale, by §0.5.2. Hidden
     * in the UI until enabled in Settings. */
    val important: Boolean = false,

    /** FK to a Database Row (§5.2), populated by [com.tendril.app.domain.DatabaseSyncManager]'s
     * Sync-to-Tasks. Travels across devices via `sourceRowUid` — see
     * [com.tendril.app.sync.EntrySnapshotRecord]. */
    val sourceRowId: Long? = null,

    /** Soft-delete (§5.5.1) — null means live; set means Trash. */
    val deletedAt: Instant? = null,

    /**
     * Where this Entry came from. [EntrySource.DATABASE_SYNC] is produced by
     * [com.tendril.app.domain.DatabaseSyncManager]; [EntrySource.GOOGLE_CALENDAR] by a pull in
     * `GoogleCalendarSyncEngine` (§9.5.1).
     *
     * `GOOGLE_CALENDAR` is load-bearing, not decorative: `CalendarProviderSync` excludes those
     * rows from the system Calendar Provider mirror (§9.11), on the reasoning that they already
     * reach the OS through the device's own Google account. Anything that sets this value on a
     * locally-created Entry therefore removes it from every system calendar surface.
     *
     * There is deliberately no member for the Notion importer; [EntrySource]'s own KDoc records
     * why, and why the one that existed was removed rather than wired up.
     */
    val source: EntrySource = EntrySource.MANUAL,

    /** Google Calendar sync (§3.2, §9.5), EVENT-only in practice — null until this Entry has
     * been pushed at least once, or when it originated from a pulled Google event. Cleared
     * (not repopulated) when [com.tendril.app.googlecalendar.GoogleCalendarSyncEngine] deletes
     * the remote copy on a local Trash transition, so restoring from Trash re-creates a fresh
     * remote event rather than writing to an id that no longer exists. */
    val googleEventId: String? = null,

    /** System Calendar Provider registration (§3.2, §9.9 item 3) — `CalendarContract.Events`'
     * local `_ID`, one-directional (Room → Provider only, never read back). **Per-device
     * only**, unlike [googleEventId]: each device's Calendar Provider is its own independent
     * database, so this is deliberately excluded from the cross-device snapshot record
     * (§9.4) — a remote-wins merge must preserve whatever this device's own value already
     * was, never adopt another device's Provider row id. */
    val providerEventId: Long? = null,

    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Where an Entry came from. Provenance, and in one case behaviour: [GOOGLE_CALENDAR] is what
 * `CalendarProviderSync` tests against to keep a row out of the system Calendar Provider mirror
 * (§9.11), so this is not a decorative label.
 *
 * **`NOTION_IMPORT` removed 2026-09-07.** It was declared "reserved for integrations not yet
 * built" in the initial commit and was never once constructed in the 49 commits since —
 * `git log -S NOTION_IMPORT --all -- '*.kt'` names only that commit, i.e. the declaration
 * itself. Nor was there anywhere to construct it: `NotionImporter` creates Pages, Blocks and
 * Database Rows and no Entry at all, and an imported database becomes an Entry only later,
 * through `DatabaseSyncManager`'s Sync-to-Tasks, which marks it [DATABASE_SYNC]. Setting it
 * would have meant inventing Entry creation in the importer, not labelling something that
 * already happens.
 *
 * Deleting an enum member is normally the risky half of that choice, because a value an older
 * peer still holds arrives in the next snapshot. Not here, on two counts. Nothing has ever
 * written it, so no peer holds it. And if some build outside this repository's history somehow
 * had, both read paths already fold an unrecognised source to [MANUAL] instead of throwing —
 * `SnapshotMappers.toEntity` and `Converters.stringToEntrySource`, both pinned by
 * `UnknownEnumQuarantineTest` — and [MANUAL] is the *behaviourally identical* reading, since
 * [GOOGLE_CALENDAR] is the only member any call site branches on. The entire worst case is one
 * row's provenance label reading MANUAL.
 */
enum class EntrySource { MANUAL, DATABASE_SYNC, GOOGLE_CALENDAR }
