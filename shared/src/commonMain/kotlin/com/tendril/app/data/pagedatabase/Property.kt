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
 * (Milestone 0) before this member exists anywhere it might sync to.
 *
 * `COMPUTED` is the second step, and deliberately arrives under its *final* name rather than as
 * a separate `ROLLUP` — §5.4 states the eventual shape as one type with two authoring paths onto
 * one evaluator (pickers that write an expression, and a `ƒ` that reveals it as editable text).
 * What exists today is only the picker half: [RollupConfig] is a small structured descriptor, not
 * an expression, and there is no evaluator yet — see [computeRollupValue][
 * com.tendril.app.ui.pages.PageDatabaseViewModel.computeRollupValue]. Naming it `COMPUTED` now
 * means the formula-language step that completes §5.4 extends this member's meaning rather than
 * renaming it out from under every property and value already stored under `RELATION`/`COMPUTED`. */
enum class PropertyType { TEXT, NUMBER, CHECKBOX, SELECT, MULTI_SELECT, DATE, URL, EMAIL, PHONE, INTERVAL, RELATION, COMPUTED }

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

/** The aggregation a `COMPUTED` rollup applies over its relation's related rows. `COUNT` needs
 * no target property; every other member reads [RollupConfig.targetPropertyUid] on each related
 * row and either aggregates it (`SUM`/`MIN`/`MAX` over a parseable number, `EARLIEST`/`LATEST`
 * over a parseable [java.time.LocalDate]) or lists it as-is (`SHOW_ORIGINAL`). The picker that
 * creates a rollup does not restrict which target property pairs with which aggregation — a
 * mismatch (e.g. `SUM` over a non-numeric property) simply has nothing to parse and the cell
 * reads empty, the same tolerant-degrade posture [parseRelationValue] already takes on a uid
 * that cannot be resolved, rather than a picker that has to know every property type's shape. */
enum class RollupAggregation { COUNT, SUM, MIN, MAX, EARLIEST, LATEST, SHOW_ORIGINAL }

/**
 * A `COMPUTED`-type [Property.config], today always this shape — see [PropertyType.COMPUTED]'s
 * own note on why the type is not called `ROLLUP`. [relationPropertyUid] names a `RELATION`
 * property on *this* property's own database; [targetPropertyUid] names the property being
 * aggregated on the relation's target database, `null` only for [RollupAggregation.COUNT].
 *
 * Three colon-delimited fields rather than [RelationConfig]'s two-field `indexOf`-based split,
 * because a third field genuinely needs its own boundary: an aggregation name is plain
 * `[A-Z_]+`, and every field here — the two uids and the name — is guaranteed free of `:`,
 * a uid because it is always a [UUID], the name because [RollupAggregation.name] is.
 */
data class RollupConfig(val relationPropertyUid: String, val targetPropertyUid: String?, val aggregation: RollupAggregation)

/** The one placeholder value below is never itself mistaken for a uid: it is one character,
 * while [UUID.randomUUID] never produces a string shorter than 36. */
private const val NO_ROLLUP_TARGET = "-"

fun encodeRollupConfig(relationPropertyUid: String, targetPropertyUid: String?, aggregation: RollupAggregation): String =
    "$relationPropertyUid:${targetPropertyUid ?: NO_ROLLUP_TARGET}:${aggregation.name}"

fun parseRollupConfig(config: String?): RollupConfig? {
    if (config == null) return null
    val parts = config.split(":")
    if (parts.size != 3) return null
    val aggregation = RollupAggregation.entries.find { it.name == parts[2] } ?: return null
    return RollupConfig(parts[0], parts[1].takeIf { it != NO_ROLLUP_TARGET }, aggregation)
}

/** Display form for a `SUM`/`MIN`/`MAX` rollup result — `3` rather than Kotlin's default `3.0`
 * for a whole-number [Double], while a genuine fraction keeps its decimal part. */
fun formatRollupNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

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
