@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.components.openVerb
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.tendril.app.domain.canvas.nodeBox
import com.tendril.app.ui.canvas.CanvasLayer
import com.tendril.app.domain.canvas.fitToBoxes
import com.tendril.app.domain.canvas.embedHeightForBoxes
import com.tendril.app.domain.canvas.CANVAS_CONTENT_MIN_SCALE
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.WorkbenchCore
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

/**
 * §0.6.3 / B§9.6 — a Canvas page *in* a page, as a block. Inert, it is a card drawing the board's
 * nodes and edges at thumbnail scale from the same rows the board reads; tapped, the caller arms
 * it and the real `CanvasScreen` fills the viewport (see `PageDetailScreen`). §3.7's "a page kind,
 * not a block type" stands: the block points at a Canvas *page* by id — the same field a page
 * mention uses — and there is no second canvas model. Nothing here scrolls, zooms or drags, which
 * is what lets the block list scroll over it.
 */

@Composable
internal fun CanvasBlockCard(core: WorkbenchCore, canvasPageId: Long?, fallbackTitle: String, onArm: () -> Unit) {
    val page by produceState<Page?>(null, canvasPageId) {
        value = canvasPageId?.let { core.database.pageDao().getById(it) }
    }
    val canvas by remember(canvasPageId) {
        if (canvasPageId == null) flowOf(null) else core.database.pageCanvasDao().observeByPageId(canvasPageId)
    }.collectAsState(initial = null)
    @Suppress("OPT_IN_USAGE")
    val nodes by remember(canvas?.id) {
        val id = canvas?.id
        if (id == null) flowOf(emptyList()) else core.database.canvasNodeDao().observeForCanvas(id)
    }.collectAsState(initial = emptyList<CanvasNode>())
    @Suppress("OPT_IN_USAGE")
    val edges by remember(canvas?.id) {
        val id = canvas?.id
        if (id == null) flowOf(emptyList()) else core.database.canvasEdgeDao().observeForCanvas(id)
    }.collectAsState(initial = emptyList<CanvasEdge>())

    val title = page?.title?.ifBlank { null } ?: fallbackTitle.ifBlank { "Canvas" }
    val missing = canvasPageId != null && page == null
    // §0.10 item 7 — the card is as tall as the board's shape asks at this column's width (Obsidian's embed, measured), clamped.
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val boxes = remember(nodes) { nodes.map { nodeBox(it) } }   // item 15 — a frame's box is its own
    val columnDp = with(density) { box.width.toDp().value }
    val heightDp = embedHeightForBoxes(boxes, columnDp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .onSizeChanged { box = it }
            .height(heightDp.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !missing, onClick = onArm),
    ) {
        if (missing) {
            Text(
                "This canvas is not on this device",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (nodes.isEmpty()) {
            Text(
                "Empty canvas · " + openVerb(),
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (box != IntSize.Zero) {
            // The board's own layer at the fit — one canvas at two sizes (§3.7); inert, so the
            // list scrolls over it. Text shows where the fit keeps it readable.
            val fit = fitToBoxes(boxes, box.width.toFloat(), box.height.toFloat(), density.density, marginDp = 16f)
            val embedded by produceState(emptyMap<Long, Page>(), nodes) {
                val ids = nodes.mapNotNull { it.embeddedPageId }.distinct()
                value = ids.mapNotNull { id -> core.database.pageDao().getById(id)?.let { id to it } }.toMap()
            }
            CanvasLayer(nodes, edges, embedded, scale = fit.scale, pan = Offset(fit.panX, fit.panY), interactive = null, showContent = fit.scale >= CANVAS_CONTENT_MIN_SCALE)
        }
        Text(
            title + " · " + openVerb(),
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

/**
 * Which canvas the new block should show: one that exists, or a new one. The new one is made a
 * child of the page holding the block, so the tree records where it came from and the Pages hub
 * lists it under that page rather than at the root.
 */
@Composable
internal fun CanvasPickerSheet(
    core: WorkbenchCore,
    onPickExisting: (Long) -> Unit,
    onCreate: (title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val canvases by produceState(emptyList<Page>()) {
        value = core.database.pageDao().getAll().filter { it.kind == PageKind.CANVAS && it.deletedAt == null && !it.isTemplate }
    }
    var newTitle by remember { mutableStateOf("") }
    TendrilSheet(title = "Canvas in this page", onDismiss = onDismiss) {
        Column {
            Text(
                "Shows a canvas here, inert until tapped. It stays a page of its own, so it can be opened from the Pages hub and shown in more than one place.",
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TendrilField(value = newTitle, onValueChange = { newTitle = it }, placeholder = "New canvas", modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { onCreate(newTitle) }) { Text("Create and insert") }
            if (canvases.isNotEmpty()) {
                Text("Or an existing one", style = MaterialTheme.typography.label, modifier = Modifier.padding(top = 8.dp))
                canvases.forEach { c ->
                    TextButton(onClick = { onPickExisting(c.id) }, modifier = Modifier.fillMaxWidth()) { Text(c.title.ifBlank { "Untitled" }) }
                }
            }
        }
    }
}
