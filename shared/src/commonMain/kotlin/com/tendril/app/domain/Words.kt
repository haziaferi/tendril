package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.ViewType

/**
 * The audit's fixes (2026-09-17, T2 of `docs/critiques/desktop-type-full.md`): the words a
 * person reads for a count, a cadence, a unit or a kind — one home, so *1 block(s)* and
 * *Every 1 day(s)* and *Type: table* cannot come back. Pure; `WordsTest` pins them.
 */
fun plural(n: Int, one: String, many: String = one + "s"): String = if (n == 1) "1 $one" else "$n $many"

/** *day · week · month* and their plurals — the unit's word, never its enum name. */
fun IntervalUnit.word(n: Int = 1): String = when (this) {
    IntervalUnit.DAY -> if (n == 1) "day" else "days"
    IntervalUnit.WEEK -> if (n == 1) "week" else "weeks"
    IntervalUnit.MONTH -> if (n == 1) "month" else "months"
}

/** *Every day · Every 3 days · Every week* — the cadence as a habit row, the Journal strip and the Trash read it. */
fun HabitFrequency.label(): String = if (count == 1) "Every ${unit.word(1)}" else "Every $count ${unit.word(count)}"

/** The view kind as a person names it (*Table*, not `TABLE`). */
val ViewType.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)

/** One line on what a view needs, under the chosen kind in the New view sheet. */
val ViewType.blurb: String get() = when (this) {
    ViewType.TABLE -> "Rows and columns; every property a cell."
    ViewType.BOARD -> "Cards in columns, grouped by a Select property — a Status property is added when the database has none."
    ViewType.GALLERY -> "Cards with a cover, one per row."
    ViewType.CALENDAR -> "Rows on the day of a Date property — one is added when the database has none."
    ViewType.TIMELINE -> "Bars from a start date to an end date — a Date property is added when the database has none."
}

/** The property kind as a person names it. */
val PropertyType.label: String get() = when (this) {
    PropertyType.TEXT -> "Text"; PropertyType.NUMBER -> "Number"; PropertyType.CHECKBOX -> "Checkbox"
    PropertyType.SELECT -> "Select"; PropertyType.MULTI_SELECT -> "Multi-select"; PropertyType.DATE -> "Date"
    PropertyType.URL -> "URL"; PropertyType.EMAIL -> "Email"; PropertyType.PHONE -> "Phone"
    PropertyType.INTERVAL -> "Interval"; PropertyType.RELATION -> "Relation"; PropertyType.COMPUTED -> "Formula"
}
