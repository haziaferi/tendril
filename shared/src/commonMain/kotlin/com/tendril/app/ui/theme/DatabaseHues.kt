package com.tendril.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.tendril.app.domain.colour.defaultDatabaseHue

/**
 * S13 (B§13.8.2) — every database's stored hue by its page id (null where none was chosen), provided by
 * `WorkbenchEnvironment` from `PageDatabaseDao.observeAll` so the tree, the phone's cards, the Road Map
 * and the calendar read one map without a ViewModel each. Absent (an empty map) the default hashed from
 * the title is what shows.
 */
val LocalDatabaseHues = compositionLocalOf<Map<Long, Int?>> { emptyMap() }

/** A database's hue on this ground: the stored one, else the title's hash kept clear of the accent. */
@Composable
fun databaseHueColours(pageId: Long, title: String): HueColours {
    val palette = LocalTendrilPalette.current
    val stored = LocalDatabaseHues.current[pageId]
    return hueColours(stored ?: defaultDatabaseHue(title, palette.accent.toSrgb().toHsl().first.toInt()), palette)
}
