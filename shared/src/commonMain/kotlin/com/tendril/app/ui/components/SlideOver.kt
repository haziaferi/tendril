@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * B§13.4 14b — a sheet's frame on a wide window, as `docs/mockups/desktop-shell.html` draws it
 * (`.scrim.right .dlg`): a scrim of the text colour at 32 % over the whole window, a 440 dp panel
 * flush right and full height with a soft shadow to its left, a header with the title and a
 * close × (none when the sheet draws its own first row), a body of bounded height. The page stays readable beside it — Notion's side peek — which
 * is why a slide-over and not a centred dialog (B§13.5 #2). Scrim click, Escape (the desktop's
 * back input) and a back gesture all close it.
 *
 * Called from [TendrilSheet] and nowhere else, as `ModalBottomSheet` is: the sheet decides its
 * own form from the window, the 33 sites that open sheets never do.
 */
@Composable
internal fun SlideOver(
    onDismiss: () -> Unit,
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
        BackHandler(enabled = true, onBack = onDismiss)
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = shown, enter = fadeIn()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
            }
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .width(slideOverWidth())
                        .fillMaxHeight()
                        // The panel takes its own clicks, so none fall through to the scrim.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // A sheet that passes no title draws its own first row, close control
                        // included (History, the Trash sheets, Reminders) — no second header over it.
                        if (title != null) Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Bounded height and no scroll of its own — as `ModalBottomSheet`'s content column
                        // has none — so a sheet that is a `LazyColumn` (History, the Trash sheets) measures
                        // against a real height instead of infinity, which is a crash.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(start = 20.dp, end = 20.dp, top = if (title != null) 4.dp else 16.dp, bottom = 20.dp),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/** The mock's 440 px, never more than three fifths of the window so an 840 dp window keeps its page. */
@Composable
private fun slideOverWidth(): Dp {
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return minOf(440.dp, widthDp * 0.6f)
}
