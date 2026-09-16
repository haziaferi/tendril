package com.tendril.app.ui.nav

import com.tendril.app.data.prefs.KeyValueStore

/**
 * B§13.6 #6 — pop-out windows, as shared UI sees them: which pages are out (the tree marks
 * them) and how to send one out. The desktop implements it over its `Window`s
 * (`PopOutWindows.kt`); the phone, which has no windows, passes null to the scaffold and no
 * opener is composed — the `paneChrome` pattern.
 */
interface PopOutHost {
    val openPageIds: Set<Long>
    fun open(pageId: Long)
}

/**
 * The remembered half of the pop-outs, pure so it is tested here rather than in the desktop
 * module: the open page ids under [PAGES_KEY] (`"3,11"`, in opening order) and a frame per page
 * under [frameKeyFor]. Decided 2026-09-16: remembered across a relaunch, like the shelf. The
 * desktop drops an id whose page no longer exists when it restores.
 */
class PopOutRegistry(private val store: KeyValueStore) {
    var pageIds: List<Long> = decode(store.get(PAGES_KEY))
        private set

    fun add(pageId: Long) {
        if (pageId in pageIds) return
        pageIds = pageIds + pageId
        store.put(PAGES_KEY, encode(pageIds))
    }

    fun remove(pageId: Long) {
        if (pageId !in pageIds) return
        pageIds = pageIds - pageId
        store.put(PAGES_KEY, if (pageIds.isEmpty()) null else encode(pageIds))
    }

    /** Keep only [live] ids — a restore after a page was trashed elsewhere. */
    fun retain(live: Set<Long>) {
        val kept = pageIds.filter { it in live }
        if (kept.size != pageIds.size) { pageIds = kept; store.put(PAGES_KEY, if (kept.isEmpty()) null else encode(kept)) }
    }

    fun frameFor(pageId: Long): WindowFrame? = WindowFrame.decode(store.get(frameKeyFor(pageId)), min = POPOUT_MIN)
    fun putFrame(pageId: Long, frame: WindowFrame) = store.put(frameKeyFor(pageId), frame.encode())

    /**
     * Where a new pop-out opens: its own remembered frame if it has one, else the default size
     * cascaded 32 dp from [last] (the OS convention), or platform-placed when there is no last.
     */
    fun nextFrame(pageId: Long, last: WindowFrame?): WindowFrame =
        frameFor(pageId) ?: when {
            last != null && last.positioned -> WindowFrame(POPOUT_DEFAULT.w, POPOUT_DEFAULT.h, last.x + CASCADE_DP, last.y + CASCADE_DP)
            else -> POPOUT_DEFAULT
        }

    companion object {
        const val PAGES_KEY = "popout_pages"
        const val CASCADE_DP = 32
        fun frameKeyFor(pageId: Long) = "popout_frame_$pageId"
        /** 720 × 600 (the critique's #1: two title bars on a 520 px window); the minimum is the editor's floor. */
        val POPOUT_DEFAULT = WindowFrame(720, 600, -1, -1)
        val POPOUT_MIN = WindowFrame(480, 400, -1, -1)
        fun encode(ids: List<Long>) = ids.joinToString(",")
        fun decode(value: String?): List<Long> =
            value?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.distinct() ?: emptyList()
    }
}
