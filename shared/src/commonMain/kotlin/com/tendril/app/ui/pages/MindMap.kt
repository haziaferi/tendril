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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.nav.ShellTopBar
import androidx.compose.runtime.Composable
import com.tendril.app.ui.components.openVerb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.tendril.app.domain.canvas.CANVAS_CONTENT_MIN_SCALE
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.Block
import com.tendril.app.domain.MindMapLayout
import androidx.compose.ui.graphics.StrokeCap
import com.tendril.app.ui.theme.pageTitle
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.canvas.drawBranch
import com.tendril.app.domain.canvas.leafWidth
import com.tendril.app.ui.theme.DrawingType
import com.tendril.app.domain.canvas.leafUnderline
import com.tendril.app.domain.canvas.cardHeight
import com.tendril.app.domain.canvas.TreeSide
import com.tendril.app.domain.canvas.TREE_SIBLING_GAP
import com.tendril.app.domain.canvas.TREE_LEVEL_GAP
import com.tendril.app.domain.canvas.NodeBox
import com.tendril.app.domain.canvas.CANVAS_ROOT_W
import com.tendril.app.domain.canvas.CANVAS_ROOT_H
import com.tendril.app.domain.canvas.cardWidth
import com.tendril.app.domain.canvas.CANVAS_LEAF_H
import com.tendril.app.domain.MapNode
import com.tendril.app.domain.OutlineBlock
import com.tendril.app.domain.layoutMindMap
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.label

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

/**
 * The mind-map pass (2026-09-20) — the outline's map takes **the canvas's grammar** (`ui/canvas/TreeGrammar.kt`,
 * `domain/canvas/Tree.kt`): the root at `pageTitle` in a bordered box (220 × 64), its children the
 * 200 × 48 strip (taller by the line for longer text), deeper nodes as text on the line — the branch
 * is the word's underline; branches headless curves, one colour per main branch. The layout stays
 * `layoutMindMap` (the RIGHT structure — the map's only one, since the outline gives the tree). Sizes
 * by depth, in content units; the screen turns them into dp one-to-one.
 */
private fun measureAt(depth: Int, block: Block): Pair<Float, Float> = when {
    depth == 0 -> CANVAS_ROOT_W to CANVAS_ROOT_H
    depth == 1 -> cardWidth(block.content).let { w -> w to cardHeight(block.content, width = w) }
    else -> leafWidth(block.content) to CANVAS_LEAF_H
}

private fun layoutByGrammar(subtree: List<OutlineBlock>): MindMapLayout {
    val rootDepth = subtree.firstOrNull()?.depth ?: 0
    val depthOf = subtree.associate { it.block.id to it.depth - rootDepth }
    return layoutMindMap(subtree, { block -> measureAt(depthOf[block.id] ?: 0, block) }, hGap = TREE_LEVEL_GAP, vGap = TREE_SIBLING_GAP)
}

/** The main branch a map node hangs from — its depth-1 ancestor's index among the root's children; −1 for the root. */
private fun mainBranchIndex(layout: MindMapLayout, node: MapNode): Int {
    var cur = node
    while (true) {
        val parent = cur.parentId?.let(layout::nodeFor) ?: return -1
        if (parent.parentId == null) return layout.nodes.filter { it.parentId == parent.block.id }.indexOfFirst { it.block.id == cur.block.id }
        cur = parent
    }
}

@Composable
internal fun MindMapCard(subtree: List<OutlineBlock>, onArm: () -> Unit) = DrawingType { MindMapCardBody(subtree, onArm) }

@Composable
private fun MindMapCardBody(subtree: List<OutlineBlock>, onArm: () -> Unit) {
    val layout = remember(subtree) { layoutByGrammar(subtree) }
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
            // The size count: where the fit would shrink the words under the smallest chrome size, the
            // layer draws the boxes alone and `ReadableLabels` writes the words over them at `caption`.
            val readable = fit >= CANVAS_CONTENT_MIN_SCALE
            MapLayer(layout, scale = fit, pan = Offset(pad, pad), selectedId = null, onTapNode = null, interactive = false, showContent = readable)
            if (!readable) ReadableLabels(
                labels = layout.nodes.map { n -> ScaledLabel(n.x, n.y, n.width, n.height, n.block.content.ifBlank { "…" }, emphasis = n.parentId == null, underline = n.depth >= 2) },
                scale = fit, pan = Offset(pad, pad), density = density, color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "Mind map · " + openVerb(),
            style = MaterialTheme.typography.description,
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
) = DrawingType { MindMapFullScreenBody(title, subtree, locked, onClose, onEditText, onAddChild, onDelete) }

@Composable
private fun MindMapFullScreenBody(
    title: String,
    subtree: List<OutlineBlock>,
    locked: Boolean,
    onClose: () -> Unit,
    onEditText: (Block, String) -> Unit,
    onAddChild: (Block, String) -> Unit,
    onDelete: (Block) -> Unit,
) {
    val layout = remember(subtree) { layoutByGrammar(subtree) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(24f, 24f)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Block?>(null) }
    var addingUnder by remember { mutableStateOf<Block?>(null) }
    val selected = selectedId?.let { id -> subtree.firstOrNull { it.block.id == id }?.block }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ShellTopBar(
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
                    style = MaterialTheme.typography.label,
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
 * apart at any zoom. The nodes by level and the branches through `TreeGrammar` — the canvas's own.
 */
@Composable
private fun MapLayer(layout: MindMapLayout, scale: Float, pan: Offset, selectedId: Long?, onTapNode: ((Long) -> Unit)?, interactive: Boolean, showContent: Boolean = true) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val palette = LocalTendrilPalette.current
    val branchInks = remember(palette) { listOf(palette.event, palette.habit, palette.third) }
    val nodeFill = MaterialTheme.colorScheme.surfaceVariant
    val ground = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y, transformOrigin = TransformOrigin(0f, 0f)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (node in layout.nodes) {
                val parent = node.parentId?.let(layout::nodeFor) ?: continue
                val ink = branchInks[mainBranchIndex(layout, node).coerceAtLeast(0) % branchInks.size]
                val width = (if (node.depth == 1) 2f else 1.5f) * density
                val pb = NodeBox(parent.x, parent.y, parent.width, parent.height)
                val nb = NodeBox(node.x, node.y, node.width, node.height)
                drawBranch(pb, nb, TreeSide.RIGHT, curved = true, density = density, ink = ink, width = width)
                if (node.depth >= 2) {
                    val u = leafUnderline(nb)
                    drawLine(ink, Offset(u.x1 * density, u.y1 * density), Offset(u.x2 * density, u.y2 * density), strokeWidth = width, cap = StrokeCap.Round)
                }
            }
        }
        for (node in layout.nodes) {
            val isRoot = node.parentId == null
            val leaf = node.depth >= 2
            val isSelected = node.block.id == selectedId
            val shape = RoundedCornerShape(if (isRoot) 10.dp else 8.dp)
            Box(
                modifier = Modifier
                    .padding(start = node.x.dp, top = node.y.dp)
                    .width(node.width.dp)
                    .height(node.height.dp)
                    .then(if (leaf) Modifier else Modifier.background(if (isRoot) ground else nodeFill, shape))
                    .then(
                        when {
                            isSelected -> Modifier.border(2.dp, accent, shape)
                            isRoot -> Modifier.border(2.dp, accent.copy(alpha = 0.8f), shape)
                            else -> Modifier
                        },
                    )
                    .then(if (interactive && onTapNode != null) Modifier.clickable { onTapNode(node.block.id) } else Modifier),
                contentAlignment = if (leaf) Alignment.CenterStart else if (isRoot) Alignment.Center else Alignment.CenterStart,
            ) {
                if (showContent) Text(  // type: TITLE — a node shows its block's text
                    node.block.content.ifBlank { "…" },
                    style = if (isRoot) MaterialTheme.typography.pageTitle else if (leaf) MaterialTheme.typography.body else MaterialTheme.typography.description,
                    color = textColor,
                    maxLines = if (isRoot) 2 else if (leaf) 1 else 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isRoot) androidx.compose.ui.text.style.TextAlign.Center else null,
                    modifier = Modifier.padding(horizontal = if (leaf) 6.dp else if (isRoot) 12.dp else 8.dp, vertical = 2.dp),
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
        text = { TendrilField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
