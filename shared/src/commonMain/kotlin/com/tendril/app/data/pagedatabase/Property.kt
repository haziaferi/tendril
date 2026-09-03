package com.tendril.app.data.pagedatabase

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tendril.app.data.entry.IntervalUnit
import java.util.UUID

/** §4/§7 — the set a Notion CSV export can actually carry, plus the Noema-native `Interval`
 * type (§5.2.2), which is never offered in the general "New property" picker outside the
 * recurrence-binding context — enforced by the UI, not the schema. */
enum class PropertyType { TEXT, NUMBER, CHECKBOX, SELECT, MULTI_SELECT, DATE, URL, EMAIL, PHONE, INTERVAL }

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
     * form, select as the chosen option text, multi-select as `"opt1,opt2"`. */
    val value: String? = null,
)

/** `PropertyValue.value` encoding for an `INTERVAL`-type property — "<count>:<unit>", the
 * same `"n:UNIT"` shape [Converters] already uses for `HabitFrequency`/`ReminderOffset`. */
fun formatIntervalValue(count: Int, unit: IntervalUnit): String = "$count:${unit.name}"

fun parseIntervalValue(value: String): Pair<Int, IntervalUnit>? =
    runCatching {
        val (count, unit) = value.split(":")
        count.toInt() to IntervalUnit.valueOf(unit)
    }.getOrNull()
