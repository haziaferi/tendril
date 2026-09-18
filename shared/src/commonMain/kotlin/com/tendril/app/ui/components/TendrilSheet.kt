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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.nav.SheetForm
import com.tendril.app.ui.nav.sheetFormFor
import com.tendril.app.ui.theme.pageTitle

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
 * always opening **fully expanded**. Material's half-expanded state was §0.10 item 11 on the
 * desktop and, it turned out, the same thing on the phone: a sheet taller than half the screen
 * opened with its buttons under the navigation bar until dragged up. Every sheet here is
 * content-sized, so the partial state buys nothing and is skipped everywhere. A sheet that needs a header with a control in it (the Trash
 * sheets, the reminder list) passes no [title] and draws its own first row.
 *
 * **On a wide window (B§13.4 14b, 2026-09-14) the same frame is a right [SlideOver]** — the
 * decision is the window's width at the shell's own breakpoint ([sheetFormFor], 840 dp), so
 * the rail and the slide-over arrive together; below it the bottom sheet is exactly what it was.
 * The 33 sites that open a sheet see one signature and never choose.
 *
 * `ModalBottomSheet` and [SlideOver] are deliberately called nowhere else — `grep` is the check.
 */
@Composable
fun TendrilSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (sheetFormFor(windowWidthDp().value) == SheetForm.SLIDE_OVER) {
        SlideOver(onDismiss = onDismiss, title = title, content = content)
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = sheetBottomRoom()),
        ) {
            if (title != null) {
                // T5 — the sheet's title at the page title's size on both forms (the slide-over's header is the same title).
                Text(title, style = MaterialTheme.typography.pageTitle, modifier = Modifier.padding(bottom = 12.dp)) // type: BAR_TITLE
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

@Composable
private fun windowWidthDp(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
