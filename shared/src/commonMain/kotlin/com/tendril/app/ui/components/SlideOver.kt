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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tendril.app.ui.nav.TOP_BAR_HEIGHT
import com.tendril.app.ui.nav.LocalTitleBar
import com.tendril.app.ui.theme.pageTitle

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
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(properties = PopupProperties(focusable = true, dismissOnBackPress = false), onDismissRequest = onDismiss) {
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
                    // PR B's walk (2026-09-18): since L5 the window's caption buttons are painted over the
                    // top-right corner of *everything*, and a full-height panel at the right edge put its
                    // own x (or a titleless sheet's first row) exactly under Windows' x. The panel keeps
                    // its height; its content starts under the title bar's row where a handle exists.
                    val underCaption = if (LocalTitleBar.current != null) TOP_BAR_HEIGHT else 0.dp
                    Column(modifier = Modifier.fillMaxSize().padding(top = underCaption)) {
                        // A sheet that passes no title draws its own first row, close control
                        // included (History, the Trash sheets, Reminders) — no second header over it.
                        if (title != null) Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text( // type: BAR_TITLE — a slide-over's header is the panel's title bar
                                title,
                                // T5 (PR B, 2026-09-18 — `desktop-type-full.md` #5): a slide-over is a page-sized
                                // surface, so its header is the page title's size; a card keeps `heading`.
                                style = MaterialTheme.typography.pageTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Bounded height; the body scrolls unless the sheet is itself a `LazyColumn`
                        // (History, the Trash sheets), which measures against a real height instead of
                        // infinity — the pairing `TendrilSheet` documents and rule 20 checks (P2).
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier)
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
