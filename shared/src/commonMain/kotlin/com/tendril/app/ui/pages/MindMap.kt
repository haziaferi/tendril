@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.Block
import com.tendril.app.domain.MindMapLayout
import com.tendril.app.domain.OutlineBlock
import com.tendril.app.domain.estimateNodeSize
import com.tendril.app.domain.layoutMindMap

/**
 * §0.6.2 / B§9.6 — one map at two sizes.
 *
 * **Inert** ([MindMapCard]): drawn in the page's block list where the subtree would have been,
 * scaled to fit a fixed-height card, consuming nothing but a tap. The list scrolls straight
 * over it. **Armed** ([MindMapFullScreen]): the same drawing filling the viewport, with pan and
 * zoom, a node tap selecting it, and the three edits a map needs — change the text, add a child,
 * delete — each of which is an ordinary block edit on the ordinary block. There is no second
 * data model: the layout is recomputed from the outline every time the blocks change.
 *
 * Sizes are estimated ([estimateNodeSize]) rather than measured with fonts. The card is scaled
 * to fit, so proportion is all it needs; the full screen draws text inside boxes the estimate
 * sized, clipped with an ellipsis where the estimate ran short, which is a smaller sin than a
 * layout pass that measures every node before it can draw one.
 */
private const val MIN_SCALE = 0.25f
private const val MAX_SCALE = 3f
private const val CARD_HEIGHT_DP = 220

/** Content-unit sizes for [layoutMindMap]; the screen turns them into dp one-to-one. */
private fun measureNode(block: Block): Pair<Float, Float> = estimateNodeSize(block.content, charWidth = 7.5f, lineHeight = 18f, maxChars = 22, padding = 20f)

@Composable
internal fun MindMapCard(subtree: List<OutlineBlock>, onArm: () -> Unit) {
    val layout = remember(subtree) { layoutMindMap(subtree, ::measureNode) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(CARD_HEIGHT_DP.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onArm)
            .onSizeChanged { box = it },
    ) {
        if (box != IntSize.Zero) {
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            val pad = 12 * density
            val fit = minOf((box.width - 2 * pad) / (layout.width * density), (box.height - 2 * pad) / (layout.height * density)).coerceAtMost(1f)
            MapLayer(layout, scale = fit, pan = Offset(pad, pad), selectedId = null, onTapNode = null, interactive = false)
        }
        Text(
            "Mind map · tap to open",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

@Composable
internal fun MindMapFullScreen(
    title: String,
    subtree: List<OutlineBlock>,
    locked: Boolean,
    onClose: () -> Unit,
    onEditText: (Block, String) -> Unit,
    onAddChild: (Block, String) -> Unit,
    onDelete: (Block) -> Unit,
) {
    val layout = remember(subtree) { layoutMindMap(subtree, ::measureNode) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(24f, 24f)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Block?>(null) }
    var addingUnder by remember { mutableStateOf<Block?>(null) }
    val selected = selectedId?.let { id -> subtree.firstOrNull { it.block.id == id }?.block }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close map") } },
            actions = {
                if (selected != null && !locked) {
                    IconButton(onClick = { editing = selected }) { Icon(Icons.Filled.Edit, contentDescription = "Edit node") }
                    IconButton(onClick = { addingUnder = selected }) { Icon(Icons.Filled.Add, contentDescription = "Add child") }
                    // The root is the block that carries the map; deleting it from inside the map
                    // would delete the map with it, which is the list's job to offer, not this.
                    if (selected.id != subtree.first().block.id) {
                        IconButton(onClick = { onDelete(selected); selectedId = null }) { Icon(Icons.Filled.Delete, contentDescription = "Delete node") }
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panDelta, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        pan += panDelta
                    }
                }
                .pointerInput(layout) {
                    // `density` is the PointerInputScope's own: content units are dp, so a tap in
                    // pixels comes back through pan, scale and density to reach the layout.
                    detectTapGestures { tap -> selectedId = hitTest(layout, tap, scale, pan, density) }
                },
        ) {
            MapLayer(layout, scale, pan, selectedId, onTapNode = { selectedId = it }, interactive = true)
            if (selected == null) {
                Text(
                    if (locked) "Read-only while locked" else "Tap a node to edit it, add a child, or delete it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
        }
    }

    editing?.let { block ->
        NodeTextDialog(title = "Edit node", initial = block.content, onDismiss = { editing = null }) { text ->
            onEditText(block, text); editing = null
        }
    }
    addingUnder?.let { parent ->
        NodeTextDialog(title = "New child", initial = "", onDismiss = { addingUnder = null }) { text ->
            onAddChild(parent, text); addingUnder = null
        }
    }
}

/** Which node a tap at [tap] (screen pixels) lands on, given the current transform. */
private fun hitTest(layout: MindMapLayout, tap: Offset, scale: Float, pan: Offset, density: Float): Long? {
    val cx = (tap.x - pan.x) / scale / density
    val cy = (tap.y - pan.y) / scale / density
    return layout.nodes.firstOrNull { cx >= it.x && cx <= it.right && cy >= it.y && cy <= it.y + it.height }?.block?.id
}

/**
 * The drawing, in content units scaled by density then by [scale] and moved by [pan] — one
 * `graphicsLayer` on one layer, the Canvas board's rule (§3.7), so edges and boxes cannot drift
 * apart at any zoom.
 */
@Composable
private fun MapLayer(layout: MindMapLayout, scale: Float, pan: Offset, selectedId: Long?, onTapNode: ((Long) -> Unit)?, interactive: Boolean) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val edgeColor = MaterialTheme.colorScheme.outline
    val nodeFill = MaterialTheme.colorScheme.surfaceVariant
    val rootFill = MaterialTheme.colorScheme.primaryContainer
    val selectedStroke = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y, transformOrigin = TransformOrigin(0f, 0f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (node in layout.nodes) {
                val parent = node.parentId?.let(layout::nodeFor) ?: continue
                val start = Offset(parent.right * density, parent.centerY * density)
                val end = Offset(node.x * density, node.centerY * density)
                val mid = (start.x + end.x) / 2f
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(mid, start.y, mid, end.y, end.x, end.y)
                }
                drawPath(path, edgeColor, style = Stroke(width = 2f * density))
            }
        }
        for (node in layout.nodes) {
            val isRoot = node.parentId == null
            val isSelected = node.block.id == selectedId
            Box(
                modifier = Modifier
                    .padding(start = node.x.dp, top = node.y.dp)
                    .width(node.width.dp)
                    .height(node.height.dp)
                    .background(if (isRoot) rootFill else nodeFill, RoundedCornerShape(10.dp))
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) selectedStroke else edgeColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .then(if (interactive && onTapNode != null) Modifier.clickable { onTapNode(node.block.id) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    node.block.content.ifBlank { "…" },
                    style = if (isRoot) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = textColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NodeTextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
