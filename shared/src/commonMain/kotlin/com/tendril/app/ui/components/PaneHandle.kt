package com.tendril.app.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tendril.app.data.prefs.KeyValueStore

/**
 * 14f·1 — a resizable pane's width, remembered in [KeyValueStore] under [key] as the Pages
 * tree's is (`PagesTreeState`, 14c): the tree came first with its own state; this is the same
 * idea for any list-beside-detail split (the Tasks list). Clamped so the pane neither vanishes
 * nor eats the other.
 */
class PaneWidthState(
    private val store: KeyValueStore,
    private val key: String,
    defaultDp: Int,
    private val minDp: Int,
    private val maxDp: Int,
) {
    var widthDp: Int by mutableStateOf(store.getInt(key, defaultDp).coerceIn(minDp, maxDp))
        private set

    fun resizeTo(dp: Int) {
        widthDp = dp.coerceIn(minDp, maxDp)
        store.putInt(key, widthDp)
    }
}

/**
 * The divider and its drag handle, laid over the right edge of the pane it resizes: place it
 * inside a `Box` that also holds the pane, aligned `CenterEnd`. 12 dp astride the 1 dp hairline
 * (the tree's lesson, 14c: 8 dp was a 5 px strip under the scale), above its neighbour
 * (`zIndex`) so the outer half is not shadowed, the resize cursor on hover.
 */
@Composable
fun PaneHandle(state: PaneWidthState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var dragStartWidth by remember { mutableStateOf(state.widthDp) }
    var dragDeltaPx by remember { mutableStateOf(0f) }
    VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = modifier)
    Box(
        modifier = modifier
            .offset(x = HANDLE_WIDTH / 2)
            .width(HANDLE_WIDTH)
            .fillMaxHeight()
            .zIndex(1f)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(state) {
                detectHorizontalDragGestures(
                    onDragStart = { dragStartWidth = state.widthDp; dragDeltaPx = 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        dragDeltaPx += delta
                        state.resizeTo(dragStartWidth + with(density) { dragDeltaPx.toDp() }.value.toInt())
                    },
                )
            },
    )
}

private val HANDLE_WIDTH = 12.dp
