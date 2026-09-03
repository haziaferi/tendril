@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.AppContainer
import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.page.Page
import com.tendril.app.ui.components.EmptyState
import kotlin.math.roundToInt

private const val NODE_W = 180f
private const val NODE_H = 90f
private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 2.5f

/**
 * The in-page Canvas (Obsidian Canvas reference) — a freeform, pannable/zoomable board of
 * text cards and page-embed cards connected by user-drawn, directional, optionally-labeled
 * arrows. All positions/content are in content-space float units; [scale]/[pan] transform that
 * space onto the screen, applied once via `graphicsLayer` to a single content layer so nodes
 * and edges always stay aligned to each other regardless of zoom level.
 */
@Composable
fun CanvasScreen(container: AppContainer, pageId: Long, onBack: () -> Unit, onOpenPage: (Long) -> Unit) {
    val viewModel: CanvasViewModel = viewModel(
        key = "canvas_$pageId",
        factory = viewModelFactory {
            initializer {
                CanvasViewModel(pageId, container.database.pageDao(), container.database.pageCanvasDao(), container.database.canvasNodeDao(), container.database.canvasEdgeDao())
            }
        }
    )
    val page by viewModel.page.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val edges by viewModel.edges.collectAsState()
    val embeddedPages by viewModel.embeddedPages.collectAsState()

    var titleField by remember(page?.id) { mutableStateOf(page?.title ?: "") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showPagePicker by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<CanvasNode?>(null) }
    var editingEdge by remember { mutableStateOf<CanvasEdge?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density

    // screen_px = (content_dp * density) * scale + pan, so a screen point inverts back to
    // content-dp via ((screen_px - pan) / scale) / density — used to drop a new card near
    // wherever the view currently looks centered, regardless of current pan/zoom.
    fun screenToContent(screen: Offset): Offset = ((screen - pan) / scale) / density

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = titleField,
                        onValueChange = { titleField = it; viewModel.updateTitle(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showAddMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "Add card") }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("Text card") }, onClick = {
                        showAddMenu = false
                        val center = screenToContent(Offset(200f, 200f))
                        viewModel.addTextNode(center.x, center.y)
                    })
                    DropdownMenuItem(text = { Text("Page card") }, onClick = { showAddMenu = false; showPagePicker = true })
                }
            }
        },
    ) { innerPadding ->
        if (nodes.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Description,
                message = "Nothing on this canvas yet — add a text or page card",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            CanvasBoard(
                nodes = nodes,
                edges = edges,
                embeddedPages = embeddedPages,
                scale = scale,
                pan = pan,
                onScaleChange = { scale = it },
                onPanChange = { pan = it },
                onMoveNode = { node, x, y -> viewModel.moveNode(node, x, y) },
                onTapNode = { node ->
                    if (node.type == CanvasNodeType.TEXT) editingNode = node
                    else node.embeddedPageId?.let(onOpenPage)
                },
                onDeleteNode = { viewModel.deleteNode(it) },
                onConnect = { from, to -> viewModel.addEdge(from.id, to.id) },
                onTapEdge = { editingEdge = it },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    editingNode?.let { node ->
        TextNodeEditor(node = node, onDismiss = { editingNode = null }, onSave = { text -> viewModel.setNodeText(node, text); editingNode = null })
    }

    editingEdge?.let { edge ->
        EdgeEditor(
            edge = edge,
            onDismiss = { editingEdge = null },
            onCycleDirection = { viewModel.cycleEdgeDirection(edge) },
            onSetLabel = { viewModel.setEdgeLabel(edge, it) },
            onDelete = { viewModel.deleteEdge(edge); editingEdge = null },
        )
    }

    if (showPagePicker) {
        CanvasPagePickerSheet(
            viewModel = viewModel,
            onDismiss = { showPagePicker = false },
            onPick = { target ->
                val center = screenToContent(Offset(200f, 200f))
                viewModel.addPageEmbedNode(center.x, center.y, target)
                showPagePicker = false
            },
        )
    }
}

@Composable
private fun CanvasBoard(
    nodes: List<CanvasNode>,
    edges: List<CanvasEdge>,
    embeddedPages: Map<Long, Page>,
    scale: Float,
    pan: Offset,
    onScaleChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    onMoveNode: (CanvasNode, Float, Float) -> Unit,
    onTapNode: (CanvasNode) -> Unit,
    onDeleteNode: (CanvasNode) -> Unit,
    onConnect: (CanvasNode, CanvasNode) -> Unit,
    onTapEdge: (CanvasEdge) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var linkDrag by remember { mutableStateOf<Pair<CanvasNode, Offset>?>(null) }
    var nodeMenuFor by remember { mutableStateOf<CanvasNode?>(null) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTransformGestures { _, panDelta, zoom, _ ->
                    onScaleChange((scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE))
                    onPanChange(pan + panDelta)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)),
        ) {
            Canvas(modifier = Modifier.size(4000.dp).pointerInput(edges, nodes, scale) {
                detectDragGestures { change, _ ->
                    // A tap-to-select-edge, expressed as a drag detector so it composes with
                    // the pinch/pan detector above rather than adding a third competing
                    // gesture stream on the same layer.
                    val hit = nearestEdge(change.position, nodes, edges, density.density)
                    if (hit != null) onTapEdge(hit)
                }
            }) {
                edges.forEach { edge ->
                    val from = nodes.find { it.id == edge.fromNodeId } ?: return@forEach
                    val to = nodes.find { it.id == edge.toNodeId } ?: return@forEach
                    drawCanvasEdge(from, to, edge, density.density)
                }
                linkDrag?.let { (fromNode, pointer) ->
                    val start = with(density) { Offset((fromNode.x + NODE_W / 2) * density.density, (fromNode.y + NODE_H / 2) * density.density) }
                    drawLine(color = Color.Gray, start = start, end = pointer, strokeWidth = 3f)
                }
            }

            nodes.forEach { node ->
                key(node.id) {
                    CanvasNodeCard(
                        node = node,
                        embeddedPage = node.embeddedPageId?.let { embeddedPages[it] },
                        density = density.density,
                        onMove = { x, y -> onMoveNode(node, x, y) },
                        onTap = { onTapNode(node) },
                        onDeleteRequest = { nodeMenuFor = node },
                        onLinkDragStart = { linkDrag = node to Offset((node.x + NODE_W / 2) * density.density, (node.y + NODE_H / 2) * density.density) },
                        onLinkDrag = { pointerInParent -> linkDrag = linkDrag?.let { (n, _) -> n to pointerInParent } },
                        onLinkDragEnd = { pointerInParent ->
                            val target = nodes.firstOrNull { candidate ->
                                candidate.id != node.id &&
                                    pointerInParent.x / density.density >= candidate.x && pointerInParent.x / density.density <= candidate.x + NODE_W &&
                                    pointerInParent.y / density.density >= candidate.y && pointerInParent.y / density.density <= candidate.y + NODE_H
                            }
                            if (target != null) onConnect(node, target)
                            linkDrag = null
                        },
                    )
                }
            }
        }
    }

    nodeMenuFor?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeMenuFor = null },
            title = { Text("Delete this card?") },
            text = { Text("Any arrows connected to it go too.") },
            confirmButton = { TextButton(onClick = { onDeleteNode(node); nodeMenuFor = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { nodeMenuFor = null }) { Text("Cancel") } },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasEdge(from: CanvasNode, to: CanvasNode, edge: CanvasEdge, density: Float) {
    val start = Offset((from.x + NODE_W / 2) * density, (from.y + NODE_H / 2) * density)
    val end = Offset((to.x + NODE_W / 2) * density, (to.y + NODE_H / 2) * density)
    drawLine(color = Color.Gray, start = start, end = end, strokeWidth = 3f)
    if (edge.direction == CanvasArrowDirection.ONE_WAY || edge.direction == CanvasArrowDirection.TWO_WAY) {
        drawCanvasArrowhead(start, end, NODE_H * density / 2f)
    }
    if (edge.direction == CanvasArrowDirection.TWO_WAY) {
        drawCanvasArrowhead(end, start, NODE_H * density / 2f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasArrowhead(from: Offset, to: Offset, pullBack: Float) {
    val delta = to - from
    val len = delta.getDistance()
    if (len < 1f) return
    val unit = delta / len
    val perp = Offset(-unit.y, unit.x)
    val tip = to - unit * pullBack
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo((tip - unit * 14f + perp * 7f).x, (tip - unit * 14f + perp * 7f).y)
        lineTo((tip - unit * 14f - perp * 7f).x, (tip - unit * 14f - perp * 7f).y)
        close()
    }
    drawPath(path, color = Color.Gray)
}

/** Point-to-segment distance across every edge, nearest under a small screen-space threshold
 * wins — the simplest reliable way to hit-test a tap against a drawn line. [tap] arrives in
 * this Canvas's own pixel space (matching [drawCanvasEdge]'s drawing coordinates), so node
 * positions (stored as dp-equivalent floats) need the same `* density` conversion before
 * comparing — comparing raw dp values against a pixel-space tap would silently never hit on
 * any non-1.0-density device. */
private fun nearestEdge(tap: Offset, nodes: List<CanvasNode>, edges: List<CanvasEdge>, density: Float): CanvasEdge? {
    var best: CanvasEdge? = null
    var bestDist = 24f
    edges.forEach { edge ->
        val from = nodes.find { it.id == edge.fromNodeId } ?: return@forEach
        val to = nodes.find { it.id == edge.toNodeId } ?: return@forEach
        val a = Offset((from.x + NODE_W / 2) * density, (from.y + NODE_H / 2) * density)
        val b = Offset((to.x + NODE_W / 2) * density, (to.y + NODE_H / 2) * density)
        val dist = distanceToSegment(tap, a, b)
        if (dist < bestDist) { bestDist = dist; best = edge }
    }
    return best
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lenSq = ab.x * ab.x + ab.y * ab.y
    if (lenSq < 0.0001f) return (p - a).getDistance()
    val t = (((p - a).x * ab.x + (p - a).y * ab.y) / lenSq).coerceIn(0f, 1f)
    val projection = a + ab * t
    return (p - projection).getDistance()
}

/** Body drag (reposition) and the tap-vs-drag disambiguation both use the same proven
 * near-zero-movement-on-release pattern as [com.tendril.app.ui.roadmap.RoadMapNode] — one
 * gesture detector per hit target (body, link handle, delete badge), never two competing
 * detectors on the same target. No long-press timer: a long-press-triggered menu is easy to
 * fire by accident mid-drag, so delete gets its own always-visible small badge instead. */
@Composable
private fun CanvasNodeCard(
    node: CanvasNode,
    embeddedPage: Page?,
    density: Float,
    onMove: (Float, Float) -> Unit,
    onTap: () -> Unit,
    onDeleteRequest: () -> Unit,
    onLinkDragStart: () -> Unit,
    onLinkDrag: (Offset) -> Unit,
    onLinkDragEnd: (Offset) -> Unit,
) {
    Surface(
        color = if (node.type == CanvasNodeType.PAGE_EMBED) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .offset { IntOffset((node.x * density).roundToInt(), (node.y * density).roundToInt()) }
            .size((NODE_W).dp, (NODE_H).dp)
            .pointerInput(node.id, density) {
                // Built on the manual awaitEachGesture loop, not detectDragGestures — the
                // latter only fires onDragStart/onDragEnd once the pointer crosses touch
                // slop, so a genuinely near-stationary tap would trigger neither and silently
                // do nothing (same fix as com.tendril.app.ui.roadmap.RoadMapNode).
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Consumed unconditionally, not just when the pointer actually moves — an
                    // unconsumed zero-movement tap would otherwise still reach the pan/zoom
                    // detector on the outer Box (see RoadMapNode's own fix for the same issue).
                    down.consume()
                    var totalDrag = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) {
                                totalDrag += delta
                                onMove(node.x + delta.x / density, node.y + delta.y / density)
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (totalDrag.getDistance() < 12f) onTap()
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Badges live in their own dedicated row above the content, not overlaid on top
            // of it — an absolutely-positioned corner badge overlapped whatever text happened
            // to be underneath it (confirmed live: the delete badge covered the first letters
            // of "Empty card," the link handle sat mid-word before "edit"). A reserved row
            // makes overlap structurally impossible regardless of text length or wrapping.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        .clickable(onClick = onDeleteRequest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete card", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(3.dp))
                }

                // The connect handle — its own dedicated pointerInput scoped just to its own
                // bounds, so dragging it never competes with the card body's own
                // reposition-drag gesture.
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(node.id, density) {
                            var parentPointer = Offset.Zero
                            detectDragGestures(
                                onDragStart = {
                                    parentPointer = Offset((node.x + NODE_W) * density, node.y * density)
                                    onLinkDragStart()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    parentPointer += dragAmount
                                    onLinkDrag(parentPointer)
                                },
                                onDragEnd = { onLinkDragEnd(parentPointer) },
                                onDragCancel = { onLinkDragEnd(parentPointer) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Link, contentDescription = "Draw arrow to another card", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
                when (node.type) {
                    CanvasNodeType.TEXT -> Text(
                        node.text.orEmpty().ifBlank { "Empty card — tap to edit" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CanvasNodeType.PAGE_EMBED -> Column {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            embeddedPage?.title ?: "…",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextNodeEditor(node: CanvasNode, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(node.id) { mutableStateOf(node.text.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Edit card", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onSave(text) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun EdgeEditor(edge: CanvasEdge, onDismiss: () -> Unit, onCycleDirection: () -> Unit, onSetLabel: (String) -> Unit, onDelete: () -> Unit) {
    var label by remember(edge.id) { mutableStateOf(edge.label.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Arrow", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it; onSetLabel(it) },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { onDismiss() }),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Direction: ${edge.direction.name.lowercase().replace('_', ' ')}", modifier = Modifier.weight(1f))
                TextButton(onClick = onCycleDirection) { Text("Change") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete arrow")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun CanvasPagePickerSheet(viewModel: CanvasViewModel, onDismiss: () -> Unit, onPick: (Page) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.pageSearchResults.collectAsState()

    ModalBottomSheet(onDismissRequest = { viewModel.clearPageSearch(); onDismiss() }) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add a page card", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearPageSearch(); onDismiss() }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchPages(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search pages…") },
            )
            LazyColumn {
                items(results, key = { it.id }) { candidate ->
                    Text(
                        candidate.title,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(candidate) }.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
