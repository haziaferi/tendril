package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * §0.10 item 19 — the find bar, option A of `docs/mockups/find-in-page.html` (decided
 * 2026-09-16): 44 dp under the page's bar on both platforms, the browsers' and Bear's home,
 * rather than a floating card that covers the first line (`docs/critiques/find-in-page-mock.md`
 * #2). A full-width field, the count, ↑ ↓ ×. ↵ is next, Shift+↵ previous; Esc reaches the
 * screen's `BackHandler` through the window's back dispatch and closes it. The *No matches*
 * state keeps the count's colour — never `outlineVariant`, which the critique measured under
 * 3:1 on this ground (#4).
 */
@Composable
fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    current: Int?,
    total: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    /** Bumped by the opener so a second Ctrl+F while open re-focuses the field. */
    focusTick: Int,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusTick) { runCatching { focus.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FIND_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            if (query.isEmpty()) Text("Find in page", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> { if (event.isShiftPressed) onPrevious() else onNext(); true }
                            else -> false
                        }
                    },
            )
        }
        Text(
            when {
                query.isEmpty() -> ""
                total == 0 -> "No matches"
                else -> "${(current ?: 0) + 1} of $total"
            },
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        IconButton(onClick = onPrevious, enabled = total > 0, modifier = Modifier.size(FIND_BUTTON)) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous (Shift+Enter)", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onNext, enabled = total > 0, modifier = Modifier.size(FIND_BUTTON)) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next (Enter)", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onClose, modifier = Modifier.size(FIND_BUTTON)) {
            Icon(Icons.Filled.Close, contentDescription = "Close (Esc)", modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

private val FIND_BAR_HEIGHT = 44.dp
/** The tree's `···` target (14d): 23.8 px at the scale's floor. */
private val FIND_BUTTON = 28.dp
