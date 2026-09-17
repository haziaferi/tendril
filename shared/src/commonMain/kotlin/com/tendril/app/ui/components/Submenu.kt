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
    val density = LocalDensity.current
    Box {
        TendrilMenuItem(
            text = text,
            leadingIcon = leadingIcon,
            trailingIcon = { Icon(Icons.Filled.ArrowRight, contentDescription = null) },
            onClick = { open = true },
            modifier = Modifier.onSizeChanged { size = it },
        )
        TendrilMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = with(density) { DpOffset(size.width.toDp(), -size.height.toDp()) },
        ) {
            content { open = false }
        }
    }
}
