@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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
private const val CARD_HEIGHT_DP = 200

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(CARD_HEIGHT_DP.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
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
        } else {
            CanvasThumbnail(nodes, edges)
        }
        Text(
            title + " · " + openVerb(),
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

/** The board scaled to fit: node boxes and straight edges, nothing else. A thumbnail is for
 * recognising a board, not reading it. */
@Composable
private fun CanvasThumbnail(nodes: List<CanvasNode>, edges: List<CanvasEdge>) {
    val nodeFill = MaterialTheme.colorScheme.surfaceVariant
    val nodeStroke = MaterialTheme.colorScheme.outline
    val edgeColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val minX = nodes.minOf { it.x }; val minY = nodes.minOf { it.y }
        val maxX = nodes.maxOf { it.x + it.width }; val maxY = nodes.maxOf { it.y + it.height }
        val contentW = (maxX - minX).coerceAtLeast(1f); val contentH = (maxY - minY).coerceAtLeast(1f)
        val fit = minOf(size.width / contentW, size.height / contentH).coerceAtMost(1f)
        val offset = Offset((size.width - contentW * fit) / 2f, (size.height - contentH * fit) / 2f)
        fun at(x: Float, y: Float) = Offset(offset.x + (x - minX) * fit, offset.y + (y - minY) * fit)
        for (edge in edges) {
            val from = nodes.firstOrNull { it.id == edge.fromNodeId } ?: continue
            val to = nodes.firstOrNull { it.id == edge.toNodeId } ?: continue
            drawLine(edgeColor, at(from.x + from.width / 2, from.y + from.height / 2), at(to.x + to.width / 2, to.y + to.height / 2), strokeWidth = 2f)
        }
        for (node in nodes) {
            val topLeft = at(node.x, node.y)
            val s = Size(node.width * fit, node.height * fit)
            drawRoundRect(nodeFill, topLeft, s, androidx.compose.ui.geometry.CornerRadius(6f * fit.coerceAtLeast(0.3f)))
            drawRoundRect(nodeStroke, topLeft, s, androidx.compose.ui.geometry.CornerRadius(6f * fit.coerceAtLeast(0.3f)), style = Stroke(width = 1.5f))
        }
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
