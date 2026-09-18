@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.desktopapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.tendril.app.ui.nav.LocalTitleBarInstaller
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.nav.PageRoute
import com.tendril.app.ui.nav.PaneChrome
import com.tendril.app.ui.nav.PopOutHost
import com.tendril.app.ui.nav.PopOutRegistry
import com.tendril.app.ui.nav.ShortcutAction
import com.tendril.app.ui.nav.WindowFrame
import com.tendril.app.ui.nav.WorkbenchEnvironment
import com.tendril.app.ui.nav.WorkbenchNavState
import com.tendril.app.ui.nav.WorkbenchRoute
import com.tendril.app.ui.nav.shortcutFor
import com.tendril.app.ui.pages.ShelfGlyph
import com.tendril.app.ui.theme.TendrilTheme
import com.tendril.app.ui.theme.resolveDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import java.awt.Dimension


/**
 * B§13.6 #6 — one page in its own OS window (decided 2026-09-16 on `docs/mockups/pop-out.html`,
 * `docs/critiques/pop-out-mock.md`): the same `PageRoute` as the main pane, its own back stack
 * (a link pushes, Escape pops and **stops at the seed**), its own
 * ViewModel store (cleared on close), the theme, and **the main window's scale**. The page stays
 * open in the main window. Keys: Escape · Ctrl+F · Ctrl+W · Alt+← / Alt+→; everything else is
 * the main window's.
 */
internal class PopOut(val pageId: Long) {
    val nav = WorkbenchNavState().apply { openPage(pageId) }
    val storeOwner = DesktopViewModelStoreOwner()
    /** Set by the window once it has one, so `open` on an already-open page can front it. */
    var front: () -> Unit = {}
}

internal class PopOuts(
    private val core: WorkbenchCore,
    private val registry: PopOutRegistry,
) : PopOutHost {
    val windows: SnapshotStateList<PopOut> = mutableStateListOf()
    override val openPageIds: Set<Long> get() = windows.map { it.pageId }.toSet()
    /** The frame the last pop-out took, for the cascade; the registry's stored frame wins. */
    var lastFrame: WindowFrame? = null

    override fun open(pageId: Long) {
        windows.firstOrNull { it.pageId == pageId }?.let { it.front(); return }
        windows.add(PopOut(pageId))
        registry.add(pageId)
    }

    fun close(pageId: Long) {
        val w = windows.firstOrNull { it.pageId == pageId } ?: return
        windows.remove(w)
        w.storeOwner.viewModelStore.clear()
        registry.remove(pageId)
    }

    /** At start: the remembered pop-outs whose pages still exist — a page trashed elsewhere is dropped from the key. */
    fun restore() {
        val live = runBlocking { registry.pageIds.filter { core.database.pageDao().getById(it)?.deletedAt == null } }.toSet()
        registry.retain(live)
        registry.pageIds.forEach { windows.add(PopOut(it)) }
    }

}

/** The main window as a pop-out reaches it: its nav state, a way to front it, and its shorter side (the scale's input). */
internal class MainWindowActions(val nav: WorkbenchNavState) {
    var front: () -> Unit = {}
    var shorterSideDp: Float = 0f
}

/**
 * The pop-out windows, composed inside `application { }` after the main window. [mainShorterSideDp]
 * is read from the main window's state — the pop-out's page draws at the main window's scale.
 */
@Composable
internal fun PopOutWindows(core: WorkbenchCore, popOuts: PopOuts, registry: PopOutRegistry, main: MainWindowActions) {
    // A copy: closing a window mutates the list mid-iteration otherwise.
    popOuts.windows.toList().forEach { p ->
        key(p.pageId) {
            val page by core.database.pageDao().observeById(p.pageId).collectAsState(initial = null)
            // The OS title follows the page on top of the pop-out's own stack (a pushed link), not only the seed.
            val topId = (p.nav.current as? WorkbenchRoute.PageDetail)?.pageId ?: p.pageId
            val topPage by core.database.pageDao().observeById(topId).collectAsState(initial = null)
            // The page trashed or purged anywhere (the main window, a sync): the window goes with it.
            var seen by remember { mutableStateOf(false) }
            LaunchedEffect(page) {
                val current = page
                if (current != null && current.deletedAt == null) seen = true
                else if (seen || (current != null && current.deletedAt != null)) popOuts.close(p.pageId)
            }
            val frame = remember { registry.nextFrame(p.pageId, popOuts.lastFrame).also { popOuts.lastFrame = it } }
            val windowState = rememberWindowState(
                size = DpSize(frame.w.dp, frame.h.dp),
                position = if (frame.positioned) WindowPosition(frame.x.dp, frame.y.dp) else WindowPosition.PlatformDefault,
            )
            val title = topPage?.title?.ifBlank { "Untitled" } ?: "Untitled"
            Window(
                onCloseRequest = { popOuts.close(p.pageId) },
                state = windowState,
                // The OS title mirrors the page's title as it is typed (the critique's #1).
                title = "$title — Tendril",
                icon = painterResource("tendril_icon.png"),
                onPreviewKeyEvent = { event ->
                    // Escape is the runtime's own back dispatch (see Main.kt) — nothing to do here.
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.isCtrlPressed && !event.isShiftPressed && !event.isAltPressed && event.key == Key.W) { popOuts.close(p.pageId); return@Window true }
                        when (shortcutFor(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)) {
                            ShortcutAction.FIND_IN_PAGE -> { p.nav.requestFind(); return@Window true }
                            ShortcutAction.BACK -> { if (p.nav.depth > 2) p.nav.back(); return@Window true }
                            ShortcutAction.FORWARD -> { p.nav.forward(); return@Window true }
                            else -> {}
                        }
                    }
                    false
                },
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(PopOutRegistry.POPOUT_MIN.w, PopOutRegistry.POPOUT_MIN.h)
                    p.front = { window.toFront(); window.requestFocus() }
                    snapshotFlow { windowState.size to windowState.position }.collectLatest { (size, position) ->
                        delay(400)
                        if (windowState.isMinimized || windowState.placement != WindowPlacement.Floating) return@collectLatest
                        val stored = if (position.isSpecified) WindowFrame(size.width.value.toInt(), size.height.value.toInt(), position.x.value.toInt(), position.y.value.toInt())
                        else WindowFrame(size.width.value.toInt(), size.height.value.toInt(), -1, -1)
                        registry.putFrame(p.pageId, stored)
                        popOuts.lastFrame = stored
                    }
                }
                val theme = core.themeSettings.observe()
                // L5 — a pop-out's page bar is its title bar, the same way.
                val titleBarInstaller = remember(window) { DesktopTitleBarInstaller(window) }
                CompositionLocalProvider(LocalTitleBarInstaller provides titleBarInstaller) {
                TendrilTheme(register = theme.register, dark = theme.mode.resolveDark(), typeface = theme.typeface) {
                    PopOutContent(core, p, popOuts, main, main.shorterSideDp, kind = page?.kind)
                }
                }
            }
        }
    }
}

@Composable
private fun PopOutContent(core: WorkbenchCore, p: PopOut, popOuts: PopOuts, main: MainWindowActions, mainShorterSideDp: Float, kind: PageKind?) {
    CompositionLocalProvider(LocalViewModelStoreOwner provides p.storeOwner) {
        WorkbenchEnvironment(core, shorterSideDp = mainShorterSideDp, wide = true) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // The seed is the root: `openPage` pushed it over the tab root, so Back is armed only past it.
                    val deeper = p.nav.depth > 2
                    androidx.compose.ui.backhandler.BackHandler(enabled = deeper) { p.nav.back() }
                    val pageId = (p.nav.current as? WorkbenchRoute.PageDetail)?.pageId ?: p.pageId
                    val glyph = when (kind) {
                        PageKind.DATABASE -> Icons.Filled.TableChart
                        PageKind.CANVAS -> Icons.Filled.Dashboard
                        else -> Icons.Outlined.Description
                    }
                    val handOff: (Long) -> Unit = { id -> main.nav.switchTab(com.tendril.app.ui.nav.WorkbenchDestination.PAGES); main.nav.showPage(id); main.front(); popOuts.close(p.pageId) }
                    val chrome = remember(glyph, pageId) {
                        PaneChrome(
                            leading = { ShelfGlyph(glyph) },
                            actions = {},
                            menuItems = { close ->
                                DropdownMenuItem(text = { Text("Open in the main window") }, onClick = { close(); handOff(pageId) })
                                DropdownMenuItem(text = { Text("Close window") }, trailingIcon = { Text("Ctrl+W", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { close(); popOuts.close(p.pageId) })
                            },
                            // The page trashed anywhere: the window goes with it.
                            onClosed = { popOuts.close(p.pageId) },
                            compact = true,
                        )
                    }
                    PageRoute(
                        core = core,
                        pageId = pageId,
                        onBack = if (deeper) ({ p.nav.back() }) else null,
                        navState = p.nav,
                        onCheckboxOnlyUnlockRequest = null,
                        paneChrome = chrome,
                        onOpenPage = p.nav::openPage,
                        findRequest = p.nav.findRequested,
                        // Cross-window verbs front the main window (the critique's #4).
                        onShowOnRoadMap = { id -> main.nav.showOnRoadMap(id); main.front(); popOuts.close(p.pageId) },
                    )
                }
            }
        }
    }
}
