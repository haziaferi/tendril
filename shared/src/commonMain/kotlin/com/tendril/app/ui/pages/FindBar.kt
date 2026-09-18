package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.tendril.app.ui.nav.LocalDensityProfile
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.tabular

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
    /** 14h·2 — matches inside a folded mind map: counted apart, reached by ↵ past the last visible one. */
    hidden: Int = 0,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusTick) { runCatching { focus.requestFocus() } }
    val pointer = LocalDensityProfile.current.pointer // P9 — the hints name keys under a pointer only
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(findBarHeight())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        TendrilField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Find in page",
            height = findButton(),
            focusRequester = focus,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@TendrilField false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { if (event.isShiftPressed) onPrevious() else onNext(); true }
                    else -> false
                }
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                query.isEmpty() -> ""
                total == 0 && hidden == 0 -> "No matches"
                total == 0 -> "$hidden inside a mind map"
                hidden > 0 -> "${(current ?: 0) + 1} of $total · $hidden inside a mind map"
                else -> "${(current ?: 0) + 1} of $total"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.description.tabular(),
        )
        IconButton(onClick = onPrevious, enabled = total > 0, modifier = Modifier.size(findButton())) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = if (pointer) "Previous (Shift+Enter)" else "Previous", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onNext, enabled = total > 0 || hidden > 0, modifier = Modifier.size(findButton())) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (pointer) "Next (Enter)" else "Next", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onClose, modifier = Modifier.size(findButton())) {
            Icon(Icons.Filled.Close, contentDescription = if (pointer) "Close (Esc)" else "Close", modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

private val FIND_BAR_HEIGHT = 44.dp

/** L·P3 (the phone's second fix PR, 2026-09-18): 44 / 28 under a pointer; 56 / 48 under Touch — the bar's three buttons measured 36 dp wide on the phone. */
@Composable
private fun findBarHeight() = if (LocalDensityProfile.current.pointer) FIND_BAR_HEIGHT else 56.dp
@Composable
private fun findButton() = if (LocalDensityProfile.current.pointer) FIND_BUTTON else 48.dp
/** The tree's `···` target (14d): 23.8 px at the scale's floor. */
private val FIND_BUTTON = 28.dp
