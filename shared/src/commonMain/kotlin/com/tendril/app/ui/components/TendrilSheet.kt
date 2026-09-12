@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True where a sheet should open fully expanded whatever its height — the desktop, whose window
 * is short enough that Material's half-expanded state hides a sheet's buttons below the frame
 * (§0.10 item 11). The phone keeps the half-expanded default: there a short sheet over a tall
 * screen is the right shape. Set once in `Tendril windows`' `Main.kt`.
 */
val LocalSheetsOpenExpanded = compositionLocalOf { false }

/**
 * The one frame every bottom sheet in the app uses (2026-09-12). Before this, each of the 29
 * sheets built its own: an outer `Column` with 16 or 20 dp of padding, a title styled by hand,
 * and a bottom that was a 24 dp spacer on some, a button row on others, nothing on the rest — so
 * the last element sat flush against the screen edge and no two sheets looked alike. The frame
 * is the fix, not a decorative line at the bottom: the sheet already has its affordance at the
 * top (the drag handle), and what reads as "stark" is content touching the edge.
 *
 * What it settles, once: 20 dp at the sides; [title] in `titleMedium` with 12 dp under it; a
 * bottom room of [sheetBottomRoom] — proportional to the window, 24 dp on a phone — on top of
 * the system navigation-bar inset Material already applies through `contentWindowInsets`; and
 * whether the sheet opens fully expanded ([fullHeight], or everywhere on the desktop through
 * [LocalSheetsOpenExpanded]). A sheet that needs a header with a control in it (the Trash
 * sheets, the reminder list) passes no [title] and draws its own first row.
 *
 * `ModalBottomSheet` is deliberately called nowhere else — `grep` is the check.
 */
@Composable
fun TendrilSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    fullHeight: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expanded = fullHeight || LocalSheetsOpenExpanded.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = sheetBottomRoom()),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            }
            content()
        }
    }
}

/** 3 % of the window's height, kept between 24 dp (a phone) and 48 dp (a tall desktop window). */
@Composable
private fun sheetBottomRoom(): Dp {
    val heightPx = LocalWindowInfo.current.containerSize.height
    val heightDp = with(LocalDensity.current) { heightPx.toDp() }
    return (heightDp * 0.03f).coerceIn(24.dp, 48.dp)
}
