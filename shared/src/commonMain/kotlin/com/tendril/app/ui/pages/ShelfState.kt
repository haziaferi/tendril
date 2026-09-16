package com.tendril.app.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.ui.components.PaneWidthState

/**
 * B§13.4 14h·1 — what the shelf holds: a second pane beside the open page on a wide window.
 * Three kinds (decided 2026-09-16 on `docs/mockups/shelf.html`): another page, this page's Road
 * Map neighbourhood, or today's Journal. [Journal] is stored as the *kind*, not the day's page id,
 * so the shelf opened on Monday still shows today on Tuesday; the id is resolved by the workspace
 * (`PagesViewModel.openJournal`, create-or-find). Encoded as `page:<id>` / `graph` / `journal`.
 */
sealed class Shelf {
    data class Page(val pageId: Long) : Shelf()
    data object Graph : Shelf()
    data object Journal : Shelf()

    fun encode(): String = when (this) {
        is Page -> "page:$pageId"
        Graph -> "graph"
        Journal -> "journal"
    }

    companion object {
        /** Null for absent or garbage — a shelf that cannot be read is a shelf that is closed. */
        fun decode(value: String?): Shelf? = when {
            value == null -> null
            value == "graph" -> Graph
            value == "journal" -> Journal
            value.startsWith("page:") -> value.removePrefix("page:").toLongOrNull()?.let { Page(it) }
            else -> null
        }
    }
}

/**
 * The shelf's state: what it holds (remembered across relaunch under [CONTENT_KEY]), what it last
 * held (so Ctrl+Shift+\ reopens it), and its width (a [PaneWidthState], 380 dp, 280…560). The
 * width is also clamped at draw time to 45 % of the workspace ([effectiveWidthDp]) so the main
 * pane keeps ≥ 360 dp at the 1200 px minimum — the critique's #3: tree 260 + shelf 380 left the
 * page 488 px. Held by the scaffold beside `PagesTreeState`.
 */
class ShelfState(private val store: KeyValueStore) {
    var content: Shelf? by mutableStateOf(Shelf.decode(store.get(CONTENT_KEY)))
        private set
    private var last: Shelf? = Shelf.decode(store.get(LAST_KEY)) ?: content
    val width = PaneWidthState(store, WIDTH_KEY, DEFAULT_WIDTH_DP, MIN_WIDTH_DP, MAX_WIDTH_DP)

    fun open(shelf: Shelf) {
        content = shelf
        last = shelf
        store.put(CONTENT_KEY, shelf.encode())
        store.put(LAST_KEY, shelf.encode())
    }

    fun close() {
        content = null
        store.put(CONTENT_KEY, null)
    }

    /** The chord: close an open shelf; reopen the last one when closed (nothing if there never was one). */
    fun toggle() {
        if (content != null) close() else last?.let { open(it) }
    }

    /**
     * What the shelf actually shows beside the page [openPageId], given today's Journal page
     * [journalPageId] (null while unresolved). Nothing at the tab root — a shelf beside an empty
     * state is a page in the wrong pane. A page cannot sit beside itself: the shelf shows its
     * graph instead, and this holds for the Journal when the Journal is the open page.
     */
    fun shown(openPageId: Long?, journalPageId: Long?): Shelf? {
        val c = content ?: return null
        if (openPageId == null) return null
        return when (c) {
            is Shelf.Page -> if (c.pageId == openPageId) Shelf.Graph else c
            Shelf.Journal -> if (journalPageId != null && journalPageId == openPageId) Shelf.Graph else c
            Shelf.Graph -> c
        }
    }

    /** The drawn width: the remembered width, never more than 45 % of the workspace. */
    fun effectiveWidthDp(workspaceWidthDp: Float): Int =
        minOf(width.widthDp, (workspaceWidthDp * MAX_SHARE).toInt())

    /**
     * `⇄` — the shelf's page to the main pane, the main page to the shelf. Returns the id the
     * caller shows in the main pane; the shelf takes [mainPageId] as a plain [Shelf.Page] (a
     * Journal swapped out stops being "today's" — it is now that day's page, which is what was
     * on screen).
     */
    fun swap(mainPageId: Long, shelfPageId: Long): Long {
        open(Shelf.Page(mainPageId))
        return shelfPageId
    }

    companion object {
        const val CONTENT_KEY = "pages_shelf"
        const val LAST_KEY = "pages_shelf_last"
        const val WIDTH_KEY = "pages_shelf_width"
        const val DEFAULT_WIDTH_DP = 380
        const val MIN_WIDTH_DP = 280
        const val MAX_WIDTH_DP = 560
        const val MAX_SHARE = 0.45f
    }
}
