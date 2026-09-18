package com.tendril.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize

/**
 * 14h·2 — a menu item that opens a second menu **beside** it: the item's own width to the right
 * and its own height up, so the submenu's first row sits level with the item at its right edge
 * (Windows', macOS' and the browsers' placement). A `DropdownMenu` nested with no offset lands
 * under the parent's rows instead (`urgency-ladder-function.md` #3, `shelf-function.md` #3).
 * Compose still flips it inside the window when the right edge is near. [content] gets `close`,
 * which shuts the submenu only — the caller shuts the parent, as it would for any item.
 */
@Composable
fun SubmenuItem(
    text: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var rightInWindow by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    // L11 (small things III, 2026-09-18): the submenu opens to the left when there is no room
    // at the right — at the user's window *Show beside ▸* had opened on top of its own parent.
    val submenuWidthPx = with(density) { SUBMENU_WIDTH.toPx() }
    val flip = submenuSide(rightInWindow, submenuWidthPx, windowWidth.toFloat())
    Box {
        TendrilMenuItem(
            text = text,
            leadingIcon = leadingIcon,
            trailingIcon = { Icon(Icons.Filled.ArrowRight, contentDescription = null) },
            onClick = { open = true },
            modifier = Modifier.onSizeChanged { size = it }.onGloballyPositioned { rightInWindow = it.positionInWindow().x + it.size.width },
        )
        TendrilMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = with(density) { DpOffset(if (flip) -SUBMENU_WIDTH else size.width.toDp(), -size.height.toDp()) },
        ) {
            content { open = false }
        }
    }
}

/** The width a submenu is assumed to take when the flip is decided (Material's menus size to content; 220 dp holds every submenu the app has). */
private val SUBMENU_WIDTH = 220.dp

/** True when a submenu opened at the parent item's right edge would cross the window's right edge — pure, tested. */
fun submenuSide(itemRightPx: Float, submenuWidthPx: Float, windowWidthPx: Float): Boolean =
    windowWidthPx > 0f && itemRightPx + submenuWidthPx > windowWidthPx
