package com.tendril.app.ui.nav

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

/**
 * B§13.4 14c — what a page's own top bar carries when the page is the right pane of the Pages
 * workspace rather than a screen of its own (the Obsidian/Bear split chosen 2026-09-15: what
 * acts on *pages* sits on the tree, what acts on *this page* sits on the page).
 *
 * The three detail screens (page, database, canvas) draw their own bar; on a wide window the
 * workspace hands them this so the bar gains what the tab used to carry — the View-Only eye
 * before their `···`, a *Trash…* item after their own menu items — and, with the tree collapsed,
 * the expand chevron and the tree's search/journal in the leading slot where the back arrow
 * would be. Null on the phone: the screens are exactly what they were.
 */
class PaneChrome(
    /** The leading slot, in place of the back arrow (which a pane does not have). */
    val leading: @Composable () -> Unit,
    /** Actions placed before the screen's own `···`. */
    val actions: @Composable RowScope.() -> Unit,
    /** Items appended to the screen's `···` menu, after its own; `close` shuts that menu (a
     * submenu's pick, 14h·1's *Show beside ▸*, would otherwise leave the parent open). */
    val menuItems: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    /** What "this page is gone" does in a pane — a trashed page closes to the tab root. */
    val onClosed: () -> Unit,
    /** 14h·1 — the shelf: the bar is 280–560 dp wide, so the title takes `titleMedium`, not `titleLarge`. */
    val compact: Boolean = false,
    /** B§13.6 #3 — where a hover card's Ctrl+click and Shift+click go (the tree's chords); null where
     * there is no shelf or window to open into (a shelf, a pop-out, the phone). */
    val openBeside: ((Long) -> Unit)? = null,
    val openInWindow: ((Long) -> Unit)? = null,
)
