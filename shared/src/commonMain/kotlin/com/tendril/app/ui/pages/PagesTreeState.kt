package com.tendril.app.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tendril.app.data.prefs.KeyValueStore

/**
 * B§13.4 14c — the tree pane's own state: how wide, whether hidden, which rows are expanded.
 * Width and the collapsed flag are device preferences and live in [KeyValueStore] (as the
 * calendar's layers do); the expanded set is a session's, since a tree remembers what you opened
 * this sitting and nothing older. Compose state, so the pane redraws on every change; the
 * desktop's Ctrl+\ calls [toggle] from `Main.kt`.
 */
class PagesTreeState(private val store: KeyValueStore) {
    var widthDp: Int by mutableStateOf(store.getInt(WIDTH_KEY, DEFAULT_WIDTH_DP).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP))
        private set
    var collapsed: Boolean by mutableStateOf(store.getBoolean(COLLAPSED_KEY, false))
        private set
    var expanded: Set<Long> by mutableStateOf(emptySet())
        private set

    fun resizeTo(dp: Int) {
        widthDp = dp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
        store.putInt(WIDTH_KEY, widthDp)
    }

    fun toggle() {
        collapsed = !collapsed
        store.putBoolean(COLLAPSED_KEY, collapsed)
    }

    fun toggleExpanded(pageId: Long) {
        expanded = if (pageId in expanded) expanded - pageId else expanded + pageId
    }

    companion object {
        const val WIDTH_KEY = "pages_tree_width"
        const val COLLAPSED_KEY = "pages_tree_collapsed"
        /** The mock's 280 px; clamped so the tree neither vanishes nor eats the page. */
        const val DEFAULT_WIDTH_DP = 280
        const val MIN_WIDTH_DP = 200
        const val MAX_WIDTH_DP = 480
    }
}
