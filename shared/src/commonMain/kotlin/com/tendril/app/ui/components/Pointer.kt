package com.tendril.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * B§13.4 14d — right-click is the pointer's long-press, everywhere a long-press exists.
 *
 * A press with the secondary button, reported with its position so a menu can open under the
 * pointer. Shared, not desktop-only: a mouse on an Android tablet gets the same behaviour, which
 * `ContextMenuArea` (a desktop artefact) would not give. Watched on the Main pass and skipped
 * when a child already consumed the press, so a text field's own right-click (its copy/paste
 * menu on the desktop) wins inside the field and the row's handler takes the margin around it.
 * A site pairs this with `combinedClickable(onLongClick = …)` and hands both the same callback.
 */
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier = pointerInput(onClick) {
    awaitEachGesture {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) return@awaitEachGesture
        val change = event.changes.firstOrNull() ?: return@awaitEachGesture
        if (change.isConsumed) return@awaitEachGesture
        change.consume()
        onClick(change.position)
    }
}

/**
 * A `DropdownMenu` opened at a point inside its container rather than under the container: the
 * pointer's position after a right-click, or [fallback] (a long-press has no useful point — the
 * row's bottom-left, so the menu hangs off the row as a `DropdownMenu` normally does). Place it
 * inside the `Box` that also draws the row; the invisible anchor is laid at [at] and the menu
 * hangs from it. In-window on both platforms.
 */
@Composable
fun PointerMenu(
    expanded: Boolean,
    at: Offset?,
    fallback: IntOffset,
    onDismiss: () -> Unit,
    items: @Composable ColumnScope.() -> Unit,
) {
    val anchor = at?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } ?: fallback
    Box(modifier = Modifier.offset { anchor }.size(1.dp)) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = items)
    }
}

/**
 * 14e (B§13.6 #2) — the mouse's side buttons as Back and Forward, on the scaffold's content so
 * every route has them. Initial pass: a side button is nobody else's, so nothing below needs
 * a look first. A mouse on Android reaches this too.
 */
fun Modifier.onPointerNavigation(onBack: () -> Unit, onForward: () -> Unit): Modifier = pointerInput(onBack, onForward) {
    awaitEachGesture {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.type != PointerEventType.Press) return@awaitEachGesture
        val handler = when {
            event.buttons.isBackPressed -> onBack
            event.buttons.isForwardPressed -> onForward
            else -> return@awaitEachGesture
        }
        event.changes.forEach { it.consume() }
        handler()
    }
}
