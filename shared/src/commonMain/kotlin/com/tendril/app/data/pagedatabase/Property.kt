package com.tendril.app.data.pagedatabase

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.IntervalUnit
import java.util.UUID

/** §4/§7 — the set a Notion CSV export can actually carry, plus the Tendril-native `Interval`
 * type (§5.2.2), which is never offered in the general "New property" picker outside the
 * recurrence-binding context — enforced by the UI, not the schema. `RELATION` (§5.4, reopened
 * 2026-09-06) is the first computed-properties step: a stored, two-way link to a row in another
 * database, encoded entirely in the existing `config`/`value` string columns — see
 * [encodeRelationConfig] and [encodeRelationValue]. Zero schema change, and deliberately gated:
 * §5.4's non-negotiable precondition is that every device runs the tolerant `PropertyType` decode
 * (Milestone 0) before this member exists anywhere it might sync to. */
enum class PropertyType { TEXT, NUMBER, CHECKBOX, SELECT, MULTI_SELECT, DATE, URL, EMAIL, PHONE, INTERVAL, RELATION }

@Entity(
    tableName = "properties",
    foreignKeys = [ForeignKey(entity = PageDatabase::class, parentColumns = ["id"], childColumns = ["databaseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("uid", unique = true), Index("databaseId")],
)
data class Property(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val databaseId: Long,
    val name: String,
    val type: PropertyType,
    /** Type-specific config — currently only SELECT/MULTI_SELECT's option list, as a
     * `"opt1,opt2,opt3"` string; simple enough not to need its own table or a JSON dependency
     * beyond what's already pulled in for snapshot sync (§9.4). */
    val config: String? = null,
    val order: Int,
)

@Entity(
    tableName = "property_values",
    foreignKeys = [
        ForeignKey(entity = Property::class, parentColumns = ["id"], childColumns = ["propertyId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = com.tendril.app.data.page.Page::class, parentColumns = ["id"], childColumns = ["rowPageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("propertyId"), Index("rowPageId"), Index(value = ["propertyId", "rowPageId"], unique = true)],
)
data class PropertyValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val propertyId: Long,
    val rowPageId: Long,
    /** Raw stored value, encoded per [PropertyType]: number/checkbox/date as their string
     * form, select as the chosen option text, multi-select as `"opt1,opt2"`, relation as
     * [encodeRelationValue]. */
    val value: String? = null,
)

/**
 * A `RELATION`-type [Property.config]: which database this property points at, and which
 * property on that database's own schema is its reverse. Both travel as page/property **uid**s,
 * never local ids — the same convention as [com.tendril.app.data.page.SpanStyle.PageMention]'s
 * `pageUid` and [com.tendril.app.data.canvas.CanvasNode]'s `embeddedPageUid`, since a local id is
 * only ever valid on the device that assigned it. Pairing by the reverse property's own uid
 * (rather than only the target database's) disambiguates two relation properties that both point
 * at the same pair of databases — "Blocked by" and "Related to" between the same two tables would
 * otherwise be indistinguishable from one database's own schema list.
 *
 * The colon separator is safe against every value this ever holds: both halves are
 * `UUID.randomUUID()` strings, which contain only hex digits and hyphens.
 */
data class RelationConfig(val targetDatabasePageUid: String, val reversePropertyUid: String)

fun encodeRelationConfig(targetDatabasePageUid: String, reversePropertyUid: String): String =
    "$targetDatabasePageUid:$reversePropertyUid"

fun parseRelationConfig(config: String?): RelationConfig? {
    if (config == null) return null
    val i = config.indexOf(':')
    if (i < 0) return null
    return RelationConfig(config.substring(0, i), config.substring(i + 1))
}

/** `PropertyValue.value` encoding for a `RELATION`-type property — the related rows' Page
 * **uid**s, comma-joined, the same list shape [PropertyType.MULTI_SELECT] already uses for its
 * option set. A uid with no local match (the row was deleted, or is still quarantined on this
 * device, §9.4) is dropped silently wherever this is resolved, the same tolerant-reference
 * pattern the rest of the sync layer already uses rather than a special case here. */
fun encodeRelationValue(relatedRowUids: Set<String>): String = relatedRowUids.joinToString(",")

fun parseRelationValue(value: String?): Set<String> =
    value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

/** `PropertyValue.value` encoding for an `INTERVAL`-type property — "<count>:<unit>", the
 * same `"n:UNIT"` shape [Converters] already uses for `HabitFrequency`/`ReminderOffset`. */
fun formatIntervalValue(count: Int, unit: IntervalUnit): String = "$count:${unit.name}"

/**
 * A [java.time.Period] as the same `"n:UNIT"` string [formatIntervalValue] produces.
 *
 * Period does not carry back which single IntervalUnit it was entered as, so this re-derives the
 * closest whole-unit pair the way it must have been entered — Elastic recurrence only ever comes
 * from one. It lives here, next to the format it produces, because it existed twice before and
 * the two copies had drifted: `DatabaseSyncManager.crystallize` wrote this form while
 * `PageDatabaseViewModel.valueForCell` returned `Period.toString()`'s "P7D", so a filter on a
 * bound Recurrence column matched the frozen value or the live proxy but never both.
 */
fun formatPeriodAsInterval(period: java.time.Period): String = when {
    period.months != 0 -> formatIntervalValue(period.months, IntervalUnit.MONTH)
    period.days % 7 == 0 && period.days != 0 -> formatIntervalValue(period.days / 7, IntervalUnit.WEEK)
    else -> formatIntervalValue(period.days, IntervalUnit.DAY)
}

fun parseIntervalValue(value: String): Pair<Int, IntervalUnit>? =
    runCatching {
        val (count, unit) = value.split(":")
        count.toInt() to IntervalUnit.valueOf(unit)
    }.getOrNull()
