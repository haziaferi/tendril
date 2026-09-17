package com.tendril.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.nav.DensityProfile
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description

/**
 * The one-line row under a pointer — the tray PR's brief (2026-09-16, *the vertical space between
 * rows is still too large*, measured beside Notion's 30 px), shared since the audit's fixes so the
 * Tasks list, the Calendar's Day and Agenda lists and the Table draw the same row: the profile's
 * height ([DensityProfile.rowHeightDp]), no vertical padding, the title on one line with its meta
 * at the right, buttons at 28 dp with 18 dp glyphs, the list's 28 dp interactive minimum. Under
 * Touch every helper hands back Material's defaults (two lines, 40 dp buttons, 48 dp targets).
 */

/** A row's button under a pointer: the find bar's 28 dp; Material's 40 dp under Touch. */
fun rowButtonModifier(pointer: Boolean): Modifier = if (pointer) Modifier.size(28.dp) else Modifier

/** Its glyph: 18 dp under a pointer, Material's 24 under Touch. */
fun rowGlyphModifier(pointer: Boolean): Modifier = if (pointer) Modifier.size(18.dp) else Modifier

/** The row's frame: the profile's height and no vertical padding under a pointer; 8 dp of it under Touch. */
fun Modifier.listRow(profile: DensityProfile, start: Int = 16, end: Int = 16): Modifier =
    if (profile.pointer) heightIn(min = profile.rowHeightDp.dp).padding(start = start.dp, end = end.dp)
    else padding(start = start.dp, end = end.dp, top = 8.dp, bottom = 8.dp)

/** A list's controls at the profile's interactive minimum (28 under a pointer, 48 under Touch). */
@Composable
fun ListInteractiveMinimum(content: @Composable () -> Unit) {
    val profile = LocalDensityProfile.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides profile.listInteractiveMinDp.dp, content = content)
}

/**
 * A row's title and its meta: one line under a pointer (the meta as a `description` at the
 * title's right — Things' date tag, Notion's list view), two lines under Touch.
 */
@Composable
fun TitleAndMeta(
    title: String,
    meta: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
) {
    val pointer = LocalDensityProfile.current.pointer
    val hasMeta = !meta.isNullOrEmpty()
    if (pointer) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (hasMeta) Text(meta!!, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        }
    } else {
        Column(modifier = modifier) {
            Text(title, style = MaterialTheme.typography.body, color = titleColor)
            if (hasMeta) Text(meta!!, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
