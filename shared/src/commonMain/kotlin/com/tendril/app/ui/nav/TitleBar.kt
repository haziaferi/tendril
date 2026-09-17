package com.tendril.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * L5 (2026-09-17) — the window's title bar is the 52 dp bar itself, as Notion Calendar's 46 px
 * toolbar is (measured): the OS keeps painting minimize / maximize / close over the bar's right
 * end and keeps its drag, double-click, snap and resize behaviour; the app draws everything
 * else. The platform half (JetBrains Runtime's custom title bar) installs a handle here; the
 * shared half asks two questions of every bar, at runtime, from its own position in the window:
 *
 * - **Is this bar at the window's top edge?** Then its *ground* — where no control sits — is
 *   title-bar area: the pointer events over it are reported to the platform as non-client
 *   ([TitleBarHandle.markGround]), so a press there drags the window. Controls are not marked:
 *   a canvas with mouse listeners is client area by default, so a button stays a button. The
 *   ground is a box *behind* the bar's content, and Compose's hit test reaches it only where no
 *   pointer-input node lies on top — a title's plain text passes through, an `IconButton` does
 *   not. A bar lower down (a page's bar under the Pages tab's, the shelf's under the tab root)
 *   asks the same question and gets *no*.
 * - **Does its right edge reach the window's?** Then its actions end with the caption buttons'
 *   width ([TitleBarHandle.rightInsetPx]), so `···` never sits under ✕.
 *
 * With no handle (Android, a runtime without the API), both answers are no and nothing changes.
 */
class TitleBarHandle(
    /** The caption buttons' width at the window's right end, in device px (the platform's number). */
    val rightInsetPx: Float,
    /** The left inset, for a platform that puts its buttons there; zero on Windows. */
    val leftInsetPx: Float,
    /** Called on every pointer event over a bar's ground; the platform reads it as "non-client here". */
    val markGround: () -> Unit,
)

val LocalTitleBar = compositionLocalOf<TitleBarHandle?> { null }

/**
 * The platform's installer: given the bar's height in device px and whether the ground is dark
 * (the caption glyphs follow), attach a custom title bar to the current window and return the
 * handle — or null where the runtime has none. Provided by the desktop's window scope; absent on
 * Android. The scaffold calls it once it knows the scaled density (`WorkbenchEnvironment`).
 */
fun interface TitleBarInstaller {
    fun install(heightPx: Float, dark: Boolean): TitleBarHandle?
}

val LocalTitleBarInstaller = compositionLocalOf<TitleBarInstaller?> { null }

/** Whether this box sits at the window's top edge (its ground is title bar) and reaches its right edge (its actions inset). */
class TitleBarPlacement {
    var atTop by mutableStateOf(false)
    var atRight by mutableStateOf(false)
}

/** Observes where the bar lies in the window; the two flags drive [TitleBarGround] and [TitleBarEndInset]. */
@Composable
fun Modifier.titleBarPlacement(placement: TitleBarPlacement): Modifier {
    val windowWidth = LocalWindowInfo.current.containerSize.width
    return onGloballyPositioned { coords ->
        val pos = coords.positionInWindow()
        placement.atTop = pos.y < 1f
        placement.atRight = pos.x + coords.size.width >= windowWidth - 1f
    }
}

/**
 * The ground behind a bar's content: a box the size of its parent that reports every pointer
 * event over it as title-bar area. Place it as the *first* child of a `Box`, so the content lies
 * on top and the hit test reaches the ground only where no control sits.
 */
@Composable
fun BoxScope.TitleBarGround(placement: TitleBarPlacement) {
    val handle = LocalTitleBar.current
    if (handle == null || !placement.atTop) return
    Box(
        modifier = Modifier.matchParentSize().pointerInput(handle) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // The platform wants the call on every event but Exit and Scroll (its
                    // contract); it reverts to client on any event that goes unmarked.
                    if (event.type != PointerEventType.Exit && event.type != PointerEventType.Scroll) handle.markGround()
                }
            }
        },
    )
}

/** The room the caption buttons take at the end of a bar that reaches the window's right edge. */
@Composable
fun TitleBarEndInset(placement: TitleBarPlacement) {
    val handle = LocalTitleBar.current ?: return
    if (!placement.atRight || handle.rightInsetPx <= 0f) return
    val dp = with(LocalDensity.current) { handle.rightInsetPx.toDp() }
    Spacer(modifier = Modifier.width(dp))
}

@Composable
fun rememberTitleBarPlacement(): TitleBarPlacement = remember { TitleBarPlacement() }
