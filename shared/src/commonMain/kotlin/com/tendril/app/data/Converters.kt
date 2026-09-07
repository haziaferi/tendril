package com.tendril.app.data

import androidx.room.TypeConverter
import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.pagedatabase.SortDirection
import com.tendril.app.data.pagedatabase.ViewFilter
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.data.reminder.ReminderOffset
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

private val json = Json { ignoreUnknownKeys = true }

/**
 * §9.4 — the tolerant enum decode, used at every boundary where a persisted `.name` String is
 * turned back into an enum member: the Room converters below, `SnapshotMappers`, and
 * `PagesSyncEngine`'s merge.
 *
 * Every enum in this schema persists as its `.name`, which is exactly why adding a member costs
 * no schema change — and exactly why a **newer build writes values an older build must read**.
 * Two devices on one Syncthing folder are routinely not on the same build, and `MainActivity`
 * calls `syncInBackground()` on every launch, so an unrecognised value is an ordinary event, not
 * a corruption. `Enum.valueOf` answers it by throwing, which at a merge boundary aborts the pass
 * and at a Room cursor kills the whole query; this answers it with `null` and lets each caller
 * decide, which is the same posture `Json { ignoreUnknownKeys = true }` already takes folder-wide
 * for unknown *fields*.
 *
 * Deliberately a lookup rather than `runCatching { enumValueOf<T>(it) }` — and *not* because the
 * lookup is the cheaper of the two, which it is not. `enumValues<T>()` hands back a fresh copy of
 * the constant array on every call, so on the overwhelmingly common recognised path this
 * allocates where a bare `valueOf` would not, once per enum column per row Room maps. The reason
 * is that a `null` composes and a `catch` block does not: each of the converters below, and each
 * of the merge boundaries above, has a *different* right answer to a value it cannot read (a
 * nullable column keeps null, a non-null column substitutes a documented fallback, the merge
 * quarantines the record and suppresses it from this pass's export), and `?:` states that answer
 * in one line at each site while leaving the unreadable case visible in the signature. The
 * allocation is affordable rather than absent: the widest enum in this schema is `BlockType`'s
 * fourteen members, so the array being copied is a handful of references.
 */
inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? {
    if (name == null) return null
    return enumValues<T>().firstOrNull { it.name == name }
}

/**
 * Room `TypeConverter`s for every non-primitive column across the schema. Simple, explicit
 * string/long encodings for small fixed-shape types (Phase 3's Entry/Habit/Reminder); the
 * Phase 5 Page/Block/Property types that are genuinely open-ended lists (formatting spans,
 * a view's visible-property-id list, its single filter) use kotlinx.serialization instead,
 * since it's already a dependency for §9.4's snapshot sync and hand-rolling a second string
 * encoding for these would just be worse JSON.
 */
class Converters {
    // --- java.time primitives ---
    @TypeConverter fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun localTimeToSecondOfDay(value: LocalTime?): Long? = value?.toSecondOfDay()?.toLong()
    @TypeConverter fun secondOfDayToLocalTime(value: Long?): LocalTime? = value?.let(LocalTime::ofSecondOfDay)

    @TypeConverter fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun durationToSeconds(value: Duration?): Long? = value?.seconds
    @TypeConverter fun secondsToDuration(value: Long?): Duration? = value?.let(Duration::ofSeconds)

    @TypeConverter fun periodToIso(value: Period?): String? = value?.toString()
    @TypeConverter fun isoToPeriod(value: String?): Period? = value?.let(Period::parse)

    // --- enums ---
    //
    // Every read below goes through [enumOrNull]. A bare `valueOf` here is not a merge failure
    // that can be quarantined and retried: it throws out of Room's cursor mapping, which kills
    // the entire query and with it whatever screen ran it. Tolerating the value is the only
    // outcome that leaves the person with a working app.
    //
    // Two shapes, and the split is deliberate:
    //
    //  - a *nullable* column decodes to null, which is a value the column already holds and
    //    every reader already handles;
    //  - a *non-null* column decodes to a documented conservative fallback, because Room turns a
    //    null into `IllegalStateException("Expected NON-NULL...")` in its generated mapping code,
    //    so returning null there would only trade one crash for another.
    //
    // A fallback is a last resort, not a merge rule: `PagesSyncEngine` and `SnapshotMappers`
    // quarantine an unrecognised value long before it can reach a column, so the only way to
    // reach one of these is a local database written by a *newer* build of the app than the one
    // now reading it. The cost is that such a row re-exports under the fallback value rather than
    // the one it was written with; the alternative is that the screen showing it does not open.
    @TypeConverter fun entryKindToString(value: EntryKind?): String? = value?.name

    /** Non-null column: an Entry of an unrecognised kind reads as a TASK — it keeps its title,
     * dates and status, and stays visible somewhere the person can find it. */
    @TypeConverter fun stringToEntryKind(value: String?): EntryKind? =
        value?.let { enumOrNull<EntryKind>(it) ?: EntryKind.TASK }

    @TypeConverter fun entryStatusToString(value: EntryStatus?): String? = value?.name

    /** Nullable column — an unrecognised status reads as "no status", which is exactly what an
     * EVENT already stores. */
    @TypeConverter fun stringToEntryStatus(value: String?): EntryStatus? = enumOrNull<EntryStatus>(value)

    @TypeConverter fun entrySourceToString(value: EntrySource?): String? = value?.name

    /** The in-repo precedent this whole sweep follows (`SnapshotMappers` has done the same for
     * `source` since it was written): an unrecognised origin is treated as MANUAL, the value that
     * grants no special handling anywhere. */
    @TypeConverter fun stringToEntrySource(value: String?): EntrySource? =
        enumOrNull<EntrySource>(value) ?: EntrySource.MANUAL

    @TypeConverter fun intervalUnitToString(value: IntervalUnit?): String? = value?.name
    @TypeConverter fun stringToIntervalUnit(value: String?): IntervalUnit? = enumOrNull<IntervalUnit>(value)

    // --- RecurrenceRule: "FIXED:<rrule>" / "ELASTIC:<iso period>" / null = None ---
    @TypeConverter
    fun recurrenceRuleToString(value: RecurrenceRule?): String? = when (value) {
        null -> null
        is RecurrenceRule.Fixed -> "FIXED:${value.rrule}"
        is RecurrenceRule.Elastic -> "ELASTIC:${value.period}"
    }

    @TypeConverter
    fun stringToRecurrenceRule(value: String?): RecurrenceRule? {
        if (value == null) return null
        val (tag, rest) = value.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (tag) {
            "FIXED" -> RecurrenceRule.Fixed(rest)
            // Nullable column, so an unparseable period reads as "not recurring" rather than
            // throwing out of the cursor — the same answer an unrecognised tag has always given.
            "ELASTIC" -> runCatching { RecurrenceRule.Elastic(Period.parse(rest)) }.getOrNull()
            else -> null
        }
    }

    // --- ReminderOffset: "PRESET:<name>" / "CUSTOM:<count>:<unit>" ---
    @TypeConverter
    fun reminderOffsetToString(value: ReminderOffset): String = when (value) {
        is ReminderOffset.FromPreset -> "PRESET:${value.preset.name}"
        is ReminderOffset.Custom -> "CUSTOM:${value.count}:${value.unit.name}"
    }

    /**
     * Non-null column, and the only converter here whose *shape* can change rather than just its
     * enum members — so it reads defensively on both counts. `parts[1]`/`parts[2]` indexing threw
     * `IndexOutOfBounds` on any encoding a later build might introduce, which is the same crash
     * as an unknown preset by another name.
     *
     * A reminder whose offset cannot be read falls back to ONE_DAY: an alarm at the wrong hour is
     * a nuisance the person can see and fix, and a query that throws is a screen that does not
     * open at all.
     */
    @TypeConverter
    fun stringToReminderOffset(value: String): ReminderOffset {
        val parts = value.split(":")
        if (parts.firstOrNull() == "PRESET") {
            enumOrNull<ReminderOffset.Preset>(parts.getOrNull(1))?.let { return ReminderOffset.FromPreset(it) }
            return ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY)
        }
        val count = parts.getOrNull(1)?.toIntOrNull()
        val unit = enumOrNull<IntervalUnit>(parts.getOrNull(2))
        if (count != null && unit != null) return ReminderOffset.Custom(count, unit)
        return ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY)
    }

    // --- HabitFrequency: "<count>:<unit>" ---
    @TypeConverter
    fun habitFrequencyToString(value: HabitFrequency): String = "${value.count}:${value.unit.name}"

    /** Non-null column. Like [stringToReminderOffset] this is an un-versioned two-part string, so
     * a missing half is as likely a failure as an unrecognised unit; both fall back rather than
     * taking the Habits screen down with them. */
    @TypeConverter
    fun stringToHabitFrequency(value: String): HabitFrequency {
        val parts = value.split(":")
        return HabitFrequency(
            parts.getOrNull(0)?.toIntOrNull() ?: 1,
            enumOrNull<IntervalUnit>(parts.getOrNull(1)) ?: IntervalUnit.DAY,
        )
    }

    // --- Phase 5 enums ---
    @TypeConverter fun pageKindToString(value: PageKind): String = value.name

    /** Non-null column: an unrecognised kind opens as an ordinary PAGE, which shows the page's
     * title and blocks rather than nothing. */
    @TypeConverter fun stringToPageKind(value: String): PageKind = enumOrNull<PageKind>(value) ?: PageKind.PAGE

    @TypeConverter fun blockTypeToString(value: BlockType): String = value.name

    /** Non-null column: PARAGRAPH renders the block's own `content` verbatim, so a block written
     * by a newer build still shows its text instead of disappearing with the query. */
    @TypeConverter fun stringToBlockType(value: String): BlockType = enumOrNull<BlockType>(value) ?: BlockType.PARAGRAPH

    @TypeConverter fun propertyTypeToString(value: PropertyType): String = value.name

    /** Non-null column: TEXT is the type that accepts any stored cell value unchanged, so the
     * column's data stays readable even where its editor doesn't exist here yet. */
    @TypeConverter fun stringToPropertyType(value: String): PropertyType = enumOrNull<PropertyType>(value) ?: PropertyType.TEXT

    @TypeConverter fun viewTypeToString(value: ViewType): String = value.name

    /** Non-null column: TABLE shows every row and every property, so nothing in the database is
     * hidden by a view this build cannot draw. */
    @TypeConverter fun stringToViewType(value: String): ViewType = enumOrNull<ViewType>(value) ?: ViewType.TABLE

    @TypeConverter fun sortDirectionToString(value: SortDirection?): String? = value?.name

    /** Nullable column — an unrecognised direction reads as "unsorted". */
    @TypeConverter fun stringToSortDirection(value: String?): SortDirection? = enumOrNull<SortDirection>(value)

    @TypeConverter fun purgedKindToString(value: PurgedKind): String = value.name

    /**
     * The worst of the fourteen, because of *where* it is read: [com.tendril.app.domain.PurgeRegistry]
     * loads every tombstone row at the top of every merge pass, before a single record file has
     * been looked at. One unrecognised kind therefore did not cost one record — it aborted every
     * sync this device would ever run, against every peer, until the build was updated.
     *
     * The fallback is PROPERTY rather than PAGE/ENTRY/HABIT deliberately. A tombstone is a
     * *delete instruction*, and this is the one converter where guessing wrong could destroy
     * something: acting on it removes the record of that kind carrying this uid. PROPERTY is the
     * narrowest of the four — it can only ever match a database column's uid, never a top-level
     * Page, Entry or Habit — and every uid in this schema is a random UUID, so a tombstone from a
     * newer build cannot collide with a local property in any real sense.
     *
     * **What this costs, recorded 2026-09-07 as an accepted loss.** Guessing narrow protects the
     * local database, not the folder. The mislabelled row re-exports to every peer as PROPERTY, so
     * a PAGE or ENTRY delete instruction written by a newer build stops propagating, and the record
     * it named can resurrect on a peer that had not yet applied it. The trade is still the right one
     * — a permanently unsyncable device is worse than one mislabelled tombstone — but it is a trade
     * and not a free win, and it is written here because an unrecorded cost is one a later reader
     * assumes was never paid. The reach is narrow, and was verified rather than assumed: the
     * snapshot path can never put an unknown kind into Room, because the orchestrator's purged-record
     * merge decodes to an entity first and holds the raw element verbatim when that fails. The only
     * route to such a row is running a newer build on this device and then downgrading it. The real
     * fix is the one the snapshot layer already uses — keep the unreadable kind string rather than
     * folding it to a member — which needs a schema change on `purged_records`, so it belongs with
     * §9.10's migration work rather than on its own.
     *
     * See this class's enum note for why null is not an option here (Room's generated non-null
     * check would throw in its place, leaving the abort exactly as it is).
     */
    @TypeConverter fun stringToPurgedKind(value: String): PurgedKind = enumOrNull<PurgedKind>(value) ?: PurgedKind.PROPERTY

    // --- Canvas enums ---
    @TypeConverter fun canvasNodeTypeToString(value: CanvasNodeType): String = value.name

    /** Non-null column: TEXT keeps the node's own `text` on the board. */
    @TypeConverter fun stringToCanvasNodeType(value: String): CanvasNodeType = enumOrNull<CanvasNodeType>(value) ?: CanvasNodeType.TEXT

    @TypeConverter fun canvasArrowDirectionToString(value: CanvasArrowDirection): String = value.name

    /** Non-null column: the edge itself survives with no arrowhead, which is the least the
     * unrecognised value could have meant. */
    @TypeConverter fun stringToCanvasArrowDirection(value: String): CanvasArrowDirection =
        enumOrNull<CanvasArrowDirection>(value) ?: CanvasArrowDirection.NONE

    // --- Phase 5 JSON-encoded columns ---
    @TypeConverter
    fun formattingSpansToJson(value: List<FormattingSpan>): String = json.encodeToString(value)

    /**
     * Span by span, deliberately. This used to decode the whole list inside one `runCatching` and
     * fall back to `emptyList()`, which meant a single span carrying a [SpanStyle] subtype a newer
     * build had added silently discarded **every other span in the block** — the bold, the links,
     * the page mentions. Losing the part that cannot be read is the accepted cost of a forward
     * schema; losing the parts that read perfectly well alongside it is the data loss this whole
     * pass exists to stop.
     */
    @TypeConverter
    fun jsonToFormattingSpans(value: String): List<FormattingSpan> =
        runCatching { json.decodeFromString<JsonArray>(value) }.getOrNull()
            ?.mapNotNull { element -> runCatching { json.decodeFromJsonElement<FormattingSpan>(element) }.getOrNull() }
            ?: emptyList()

    @TypeConverter
    fun longListToJson(value: List<Long>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToLongList(value: String): List<Long> =
        runCatching { json.decodeFromString<List<Long>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun viewFilterToJson(value: ViewFilter?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToViewFilter(value: String?): ViewFilter? =
        value?.let { runCatching { json.decodeFromString<ViewFilter>(it) }.getOrNull() }
}
