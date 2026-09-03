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
import com.tendril.app.data.pagedatabase.SortDirection
import com.tendril.app.data.pagedatabase.ViewFilter
import com.tendril.app.data.pagedatabase.ViewType
import com.tendril.app.data.reminder.ReminderOffset
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

private val json = Json { ignoreUnknownKeys = true }

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
    @TypeConverter fun entryKindToString(value: EntryKind?): String? = value?.name
    @TypeConverter fun stringToEntryKind(value: String?): EntryKind? = value?.let(EntryKind::valueOf)

    @TypeConverter fun entryStatusToString(value: EntryStatus?): String? = value?.name
    @TypeConverter fun stringToEntryStatus(value: String?): EntryStatus? = value?.let(EntryStatus::valueOf)

    @TypeConverter fun entrySourceToString(value: EntrySource?): String? = value?.name
    @TypeConverter fun stringToEntrySource(value: String?): EntrySource? = value?.let(EntrySource::valueOf) ?: EntrySource.MANUAL

    @TypeConverter fun intervalUnitToString(value: IntervalUnit?): String? = value?.name
    @TypeConverter fun stringToIntervalUnit(value: String?): IntervalUnit? = value?.let(IntervalUnit::valueOf)

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
            "ELASTIC" -> RecurrenceRule.Elastic(Period.parse(rest))
            else -> null
        }
    }

    // --- ReminderOffset: "PRESET:<name>" / "CUSTOM:<count>:<unit>" ---
    @TypeConverter
    fun reminderOffsetToString(value: ReminderOffset): String = when (value) {
        is ReminderOffset.FromPreset -> "PRESET:${value.preset.name}"
        is ReminderOffset.Custom -> "CUSTOM:${value.count}:${value.unit.name}"
    }

    @TypeConverter
    fun stringToReminderOffset(value: String): ReminderOffset {
        val parts = value.split(":")
        return when (parts[0]) {
            "PRESET" -> ReminderOffset.FromPreset(ReminderOffset.Preset.valueOf(parts[1]))
            else -> ReminderOffset.Custom(parts[1].toInt(), IntervalUnit.valueOf(parts[2]))
        }
    }

    // --- HabitFrequency: "<count>:<unit>" ---
    @TypeConverter
    fun habitFrequencyToString(value: HabitFrequency): String = "${value.count}:${value.unit.name}"

    @TypeConverter
    fun stringToHabitFrequency(value: String): HabitFrequency {
        val (count, unit) = value.split(":")
        return HabitFrequency(count.toInt(), IntervalUnit.valueOf(unit))
    }

    // --- Phase 5 enums ---
    @TypeConverter fun pageKindToString(value: PageKind): String = value.name
    @TypeConverter fun stringToPageKind(value: String): PageKind = PageKind.valueOf(value)

    @TypeConverter fun blockTypeToString(value: BlockType): String = value.name
    @TypeConverter fun stringToBlockType(value: String): BlockType = BlockType.valueOf(value)

    @TypeConverter fun propertyTypeToString(value: PropertyType): String = value.name
    @TypeConverter fun stringToPropertyType(value: String): PropertyType = PropertyType.valueOf(value)

    @TypeConverter fun viewTypeToString(value: ViewType): String = value.name
    @TypeConverter fun stringToViewType(value: String): ViewType = ViewType.valueOf(value)

    @TypeConverter fun sortDirectionToString(value: SortDirection?): String? = value?.name
    @TypeConverter fun stringToSortDirection(value: String?): SortDirection? = value?.let(SortDirection::valueOf)

    // --- Canvas enums ---
    @TypeConverter fun canvasNodeTypeToString(value: CanvasNodeType): String = value.name
    @TypeConverter fun stringToCanvasNodeType(value: String): CanvasNodeType = CanvasNodeType.valueOf(value)

    @TypeConverter fun canvasArrowDirectionToString(value: CanvasArrowDirection): String = value.name
    @TypeConverter fun stringToCanvasArrowDirection(value: String): CanvasArrowDirection = CanvasArrowDirection.valueOf(value)

    // --- Phase 5 JSON-encoded columns ---
    @TypeConverter
    fun formattingSpansToJson(value: List<FormattingSpan>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToFormattingSpans(value: String): List<FormattingSpan> =
        runCatching { json.decodeFromString<List<FormattingSpan>>(value) }.getOrDefault(emptyList())

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
