@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.nav.ShellLayout
import com.tendril.app.ui.nav.LocalShellLayout
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.components.openVerb
import com.tendril.app.domain.canvas.fitToCards
import com.tendril.app.domain.canvas.contentAtPaneCentre
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.tendril.app.ui.nav.PaneChrome
import com.tendril.app.ui.nav.ShellTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.page.Page
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.pages.LocalViewOnly
import kotlin.math.roundToInt
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.pageTitle
import com.tendril.app.ui.components.TendrilMenu
import com.tendril.app.ui.components.TendrilMenuItem

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
fun CanvasScreen(
    core: WorkbenchCore,
    pageId: Long,
    onBack: (() -> Unit)?,
    onOpenPage: (Long) -> Unit,
    paneChrome: PaneChrome? = null,
    /** P5 — the phone's canvas menu: *Show on Road Map*; null hides the item. */
    onShowOnRoadMap: ((Long) -> Unit)? = null,
) {
    val viewModel: CanvasViewModel = viewModel(
        key = "canvas_$pageId",
        factory = viewModelFactory {
            initializer {
                CanvasViewModel(pageId, core.database.pageDao(), core.database.pageCanvasDao(), core.database.canvasNodeDao(), core.database.canvasEdgeDao(), core.viewLockState, core.pageContentRepository)
            }
        }
    )
    // §3.1.2 — the same flag [CanvasViewModel.locked] refuses on, read here so the board stops
    // *offering* the edits it would then silently swallow. Provided once by
    // [com.tendril.app.ui.nav.WorkbenchScaffold]; a Canvas page is a Pages destination and
    // renders inside that provider, so this is the same global toggle, not a second one.
    val viewOnly = LocalViewOnly.current
    val page by viewModel.page.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val edges by viewModel.edges.collectAsState()
    val embeddedPages by viewModel.embeddedPages.collectAsState()

    var titleField by remember(page?.id) { mutableStateOf(page?.title ?: "") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showPagePicker by remember { mutableStateOf(false) }
    var showOwnMenu by remember { mutableStateOf(false) }
    var showTrashCanvas by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<CanvasNode?>(null) }
    var editingEdge by remember { mutableStateOf<CanvasEdge?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    // L9 + L10 (2026-09-17) — under a pointer on a wide window the verbs live in the bar (*Fit*, *+*),
    // a double-click on the ground makes a text card where it landed, a click selects a card (its
    // badges then stay; the ring), a new card from the menu lands at the visible centre. The phone
    // keeps its FAB, its always-on badges and its tap-to-open.
    val wide = LocalShellLayout.current == ShellLayout.RAIL
    val pointer = LocalDensityProfile.current.pointer
    var paneSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedNodeId by remember { mutableStateOf<Long?>(null) }
    // A card made by a double-click: its editor opens as soon as it exists, and it is discarded if
    // that editor closes with nothing typed (a stray double-click leaves no card — the scoring's rule).
    var pendingNewNodeId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(nodes, pendingNewNodeId) {
        val id = pendingNewNodeId ?: return@LaunchedEffect
        nodes.firstOrNull { it.id == id }?.let { if (editingNode?.id != id) editingNode = it }
    }
    fun newCardOrigin(): Pair<Float, Float> = contentAtPaneCentre(paneSize.width.toFloat(), paneSize.height.toFloat(), scale, pan.x, pan.y, density, NODE_W, NODE_H)
    fun fit() {
        val f = fitToCards(nodes.map { it.x to it.y }, NODE_W, NODE_H, paneSize.width.toFloat(), paneSize.height.toFloat(), density)
        scale = f.scale.coerceIn(MIN_SCALE, MAX_SCALE); pan = Offset(f.panX, f.panY)
    }
    val addMenu: @Composable () -> Unit = {
        TendrilMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
            TendrilMenuItem(text = { Text("Text card") }, onClick = {
                showAddMenu = false
                val (x, y) = newCardOrigin()
                viewModel.addTextNode(x, y)
            })
            TendrilMenuItem(text = { Text("Page card") }, onClick = { showAddMenu = false; showPagePicker = true })
        }
    }

    // screen_px = (content_dp * density) * scale + pan, so a screen point inverts back to
    // content-dp via ((screen_px - pan) / scale) / density — used to drop a new card near
    // wherever the view currently looks centered, regardless of current pan/zoom.
    fun screenToContent(screen: Offset): Offset = ((screen - pan) / scale) / density
    fun addAtDoubleTap(tap: Offset) {
        val c = screenToContent(tap)
        viewModel.addTextNode(c.x - NODE_W / 2f, c.y - NODE_H / 2f) { pendingNewNodeId = it }
    }

    Scaffold(
        topBar = {
            ShellTopBar(
                title = {
                    BasicTextField(
                        value = titleField,
                        onValueChange = { titleField = it; viewModel.updateTitle(it) },
                        readOnly = viewOnly,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = (if (paneChrome?.compact == true) MaterialTheme.typography.heading else MaterialTheme.typography.pageTitle).copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                    )
                },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } else paneChrome?.leading?.invoke() },
                actions = {
                    if (wide) {
                        IconButton(onClick = { fit() }, enabled = nodes.isNotEmpty()) { Icon(Icons.Outlined.FitScreen, contentDescription = "Fit every card") }
                        if (!viewOnly) Box {
                            IconButton(onClick = { showAddMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "New card") }
                            addMenu()
                        }
                    }
                    // As a pane the canvas gains the workspace's menu (View-Only, Trash…); on its own — the
                    // phone — it carries the page's two verbs (the phone's fix PR, P5: it had no `···` at all).
                    if (paneChrome == null) Box {
                        IconButton(onClick = { showOwnMenu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More") }
                        TendrilMenu(expanded = showOwnMenu, onDismissRequest = { showOwnMenu = false }) {
                            if (onShowOnRoadMap != null) TendrilMenuItem(text = { Text("Show on Road Map") }, onClick = { showOwnMenu = false; onShowOnRoadMap(pageId) })
                            if (!viewOnly) TendrilMenuItem(text = { Text("Move to Trash") }, onClick = { showOwnMenu = false; showTrashCanvas = true })
                        }
                    }
                    if (paneChrome != null) {
                        var showPaneMenu by remember { mutableStateOf(false) }
                        paneChrome.actions(this)
                        Box {
                            IconButton(onClick = { showPaneMenu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More") }
                            TendrilMenu(expanded = showPaneMenu, onDismissRequest = { showPaneMenu = false }) { paneChrome.menuItems(this) { showPaneMenu = false } }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Hidden rather than disabled while View-Only is on, matching the Pages hub's own
            // create FAB (§3.1.2) — there is nothing else on this control, so a greyed-out one
            // would only advertise an action the lock has already refused. The phone's: on a
            // wide window the verb is the bar's + (L9).
            if (!viewOnly && !wide) {
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) { Icon(Icons.Filled.Add, contentDescription = "Add card") }
                    addMenu()
                }
            }
        },
    ) { innerPadding ->
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).onSizeChanged { paneSize = it }
                    .pointerInput(wide, viewOnly) { if (wide && !viewOnly) detectTapGestures(onDoubleTap = { addAtDoubleTap(it) }) },
            ) {
            EmptyState(
                icon = Icons.Outlined.Description,
                // The prompt half drops under View-Only, the same way the Pages hub drops its
                // empty-state CTA: the add control is gone, so inviting the tap would be an
                // instruction to use something that is no longer there.
                message = when {
                    viewOnly -> "Nothing on this canvas yet"
                    wide && pointer -> "Nothing on this canvas yet — double-click the ground for a text card, or + for a page card"
                    else -> "Nothing on this canvas yet — add a text or page card"
                },
                modifier = Modifier.fillMaxSize(),
            )
            }
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
                    // Under a pointer a first click selects, a second opens (Obsidian's); the phone opens at once.
                    if (pointer && selectedNodeId != node.id) selectedNodeId = node.id
                    else if (node.type == CanvasNodeType.TEXT) editingNode = node
                    else node.embeddedPageId?.let(onOpenPage)
                },
                onDeleteNode = { viewModel.deleteNode(it); if (selectedNodeId == it.id) selectedNodeId = null },
                onConnect = { from, to -> viewModel.addEdge(from.id, to.id) },
                onTapEdge = { editingEdge = it },
                viewOnly = viewOnly,
                selectedNodeId = selectedNodeId,
                onGroundTap = { selectedNodeId = null },
                onGroundDoubleTap = if (wide && !viewOnly) ::addAtDoubleTap else null,
                onPaneSize = { paneSize = it },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }

    // Both sheets stay *openable* under View-Only and turn into readers instead of closing:
    // a card shows at most three lines on the board and an arrow's label is not drawn at all,
    // so refusing to open them would hide content rather than protect it. What they lose is
    // every control that writes — see each one's own note.
    editingNode?.let { node ->
        // The discard rule: a double-click's card whose editor closes with nothing typed is deleted.
        fun closeEditor(text: String?) {
            if (node.id == pendingNewNodeId) {
                pendingNewNodeId = null
                if (text.isNullOrBlank() && node.text.isNullOrBlank()) { viewModel.deleteNode(node); editingNode = null; return }
            }
            if (text != null) viewModel.setNodeText(node, text)
            editingNode = null
        }
        TextNodeEditor(node = node, viewOnly = viewOnly, onDismiss = { closeEditor(null) }, onSave = { closeEditor(it) })
    }

    editingEdge?.let { edge ->
        EdgeEditor(
            edge = edge,
            viewOnly = viewOnly,
            onDismiss = { editingEdge = null },
            onCycleDirection = { viewModel.cycleEdgeDirection(edge) },
            onSetLabel = { viewModel.setEdgeLabel(edge, it) },
            onDelete = { viewModel.deleteEdge(edge); editingEdge = null },
        )
    }

    // Not `takeIf`-guarded like the delete dialog but `&&`-guarded for the same reason: the FAB
    // that opens it is gone under the lock, and a pick made after a mid-flow toggle would add a
    // card.
    // P5 — the same question the page's and the database's `···` ask.
    if (showTrashCanvas) {
        AlertDialog(
            onDismissRequest = { showTrashCanvas = false },
            title = { Text("Move this canvas to Trash?", style = MaterialTheme.typography.heading) },
            text = { Text("Its cards go with it; everything can be restored from Trash.") },
            confirmButton = { TextButton(onClick = { showTrashCanvas = false; viewModel.trashPage { onBack?.invoke() } }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showTrashCanvas = false }) { Text("Cancel") } },
        )
    }

    if (showPagePicker && !viewOnly) {
        CanvasPagePickerSheet(
            viewModel = viewModel,
            onDismiss = { showPagePicker = false },
            onPick = { target ->
                val (x, y) = newCardOrigin()
                viewModel.addPageEmbedNode(x, y, target)
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
    /** §3.1.2 — pan, zoom, tapping a card open and tapping an arrow to read its label all stay
     * live; only the four things that write (move, delete, connect, edit) come off the board. */
    viewOnly: Boolean,
    selectedNodeId: Long? = null,
    onGroundTap: () -> Unit = {},
    /** L10 — a double-click on the ground (screen px in the board) makes a text card there; null on the phone and under the lock. */
    onGroundDoubleTap: ((Offset) -> Unit)? = null,
    onPaneSize: (IntSize) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val edgeInk = MaterialTheme.colorScheme.onSurfaceVariant
    val draftInk = MaterialTheme.colorScheme.primary
    var linkDrag by remember { mutableStateOf<Pair<CanvasNode, Offset>?>(null) }
    var nodeMenuFor by remember { mutableStateOf<CanvasNode?>(null) }
    // L10 — the board takes focus when a card is selected, so Delete / Backspace reach it and
    // open the same dialog the badge opens (the desktop's key for a selection; the lock still
    // gates the dialog below). A finger never selects, so the phone never focuses the board.
    val boardFocus = remember { FocusRequester() }
    LaunchedEffect(selectedNodeId) { if (selectedNodeId != null) boardFocus.requestFocus() }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged(onPaneSize)
            .focusRequester(boardFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val selected = selectedNodeId?.let { id -> nodes.firstOrNull { it.id == id } }
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Delete || event.key == Key.Backspace) && selected != null && !viewOnly) {
                    nodeMenuFor = selected; true
                } else false
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, panDelta, zoom, _ ->
                    onScaleChange((scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE))
                    onPanChange(pan + panDelta)
                }
            }
            // A second detector on the ground for taps only: a tap clears the selection, a double
            // tap makes a card (L10). The cards consume their own downs, so neither reaches them.
            .pointerInput(onGroundDoubleTap) {
                detectTapGestures(onTap = { onGroundTap() }, onDoubleTap = onGroundDoubleTap)
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
                    drawCanvasEdge(from, to, edge, density.density, edgeInk)
                }
                linkDrag?.let { (fromNode, pointer) ->
                    val start = with(density) { Offset((fromNode.x + NODE_W / 2) * density.density, (fromNode.y + NODE_H / 2) * density.density) }
                    drawLine(color = draftInk, start = start, end = pointer, strokeWidth = 3f)
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
                        viewOnly = viewOnly,
                        selected = node.id == selectedNodeId,
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

    // The `takeIf` is not redundant with the hidden badge: the badge is the only way to *open*
    // this dialog, but View-Only can be turned on from the Pages topbar while it is already
    // open, and a confirm tap after that would be exactly the destructive write the lock exists
    // to stop.
    nodeMenuFor?.takeIf { !viewOnly }?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeMenuFor = null },
            title = { Text("Delete this card?") },
            text = { Text("Any arrows connected to it go too.") },
            confirmButton = { TextButton(onClick = { onDeleteNode(node); nodeMenuFor = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { nodeMenuFor = null }) { Text("Cancel") } },
        )
    }
}

/** 14g·2 — a canvas edge is drawn in the register's dim (B§13.8.3), as the Road Map's mention edges are. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasEdge(from: CanvasNode, to: CanvasNode, edge: CanvasEdge, density: Float, ink: Color) {
    val start = Offset((from.x + NODE_W / 2) * density, (from.y + NODE_H / 2) * density)
    val end = Offset((to.x + NODE_W / 2) * density, (to.y + NODE_H / 2) * density)
    drawLine(color = ink, start = start, end = end, strokeWidth = 3f)
    if (edge.direction == CanvasArrowDirection.ONE_WAY || edge.direction == CanvasArrowDirection.TWO_WAY) {
        drawCanvasArrowhead(start, end, NODE_H * density / 2f, ink)
    }
    if (edge.direction == CanvasArrowDirection.TWO_WAY) {
        drawCanvasArrowhead(end, start, NODE_H * density / 2f, ink)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasArrowhead(from: Offset, to: Offset, pullBack: Float, ink: Color) {
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
    drawPath(path, color = ink)
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
    viewOnly: Boolean,
    selected: Boolean = false,
) {
    // L10 — under a pointer the badges show on hover or on the selected card (14d's rule for row
    // controls); their row stays laid out so the text never moves. The phone shows them always.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pointer = LocalDensityProfile.current.pointer
    val badgesAlpha = if (!pointer || hovered || selected) 1f else 0f
    Surface(
        color = if (node.type == CanvasNodeType.PAGE_EMBED) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .offset { IntOffset((node.x * density).roundToInt(), (node.y * density).roundToInt()) }
            .size((NODE_W).dp, (NODE_H).dp)
            .hoverable(interaction)
            // `viewOnly` is a key, not just a captured value: `pointerInput` keeps running the
            // same lambda until a key changes, so a board already on screen when the eye toggle
            // is flipped would otherwise go on dragging against the value captured at first
            // composition.
            .pointerInput(node.id, density, viewOnly) {
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
                                // Accumulated even under View-Only, so the tap-vs-drag test below
                                // stays honest: a smeared finger that would have been a drag must
                                // not fall through and open the card's editor instead.
                                totalDrag += delta
                                if (!viewOnly) onMove(node.x + delta.x / density, node.y + delta.y / density)
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
            // §3.1.2 — both badges are pure write affordances (destroy a card, draw an arrow),
            // so both come off the card entirely while View-Only is on rather than sitting there
            // greyed out. The row itself goes with them: with nothing left to reserve space for,
            // an empty strip would only push the card's own text down for no reason.
            if (!viewOnly) Row(modifier = Modifier.fillMaxWidth().alpha(badgesAlpha), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        .clickable(enabled = badgesAlpha > 0f, onClick = onDeleteRequest),
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
                    // The caption's verb by the profile (14h·2's `openVerb`); `onSurface` on the card's tint
                    // (`onSurfaceVariant` measured 3.45 : 1 there — `canvas-cards-mock.md` #1).
                    CanvasNodeType.TEXT -> Text( // type: PREVIEW_LINE — a card shows at most three lines of its text; the editor holds the rest
                        node.text.orEmpty().ifBlank { "Empty card — ${openVerb()}" },
                        style = MaterialTheme.typography.description,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CanvasNodeType.PAGE_EMBED -> Column {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            embeddedPage?.title ?: "…",
                            style = MaterialTheme.typography.body,
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
private fun TextNodeEditor(node: CanvasNode, viewOnly: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(node.id) { mutableStateOf(node.text.orEmpty()) }
    // L10 — the field takes focus as the editor opens: a double-click's card is typed into at once
    // (Obsidian's), and a card left blank is discarded by the caller's rule.
    val focus = remember { FocusRequester() }
    LaunchedEffect(node.id) { runCatching { focus.requestFocus() } }
    TendrilSheet(onDismiss = onDismiss) {
        Column {
            // §3.1.2 — a reader, not an editor, while the lock is on: the field goes read-only
            // (so the full text of a card the board truncates at three lines is still legible)
            // and Save goes away, leaving one button that closes the sheet.
            Text(if (viewOnly) "Card" else "Edit card", style = MaterialTheme.typography.heading, modifier = Modifier.padding(bottom = 12.dp))
            TendrilField(value = text, onValueChange = { text = it }, readOnly = viewOnly, modifier = Modifier.fillMaxWidth(), focusRequester = focus, minLines = 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(if (viewOnly) "Done" else "Cancel") }
                if (!viewOnly) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(text) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun EdgeEditor(edge: CanvasEdge, viewOnly: Boolean, onDismiss: () -> Unit, onCycleDirection: () -> Unit, onSetLabel: (String) -> Unit, onDelete: () -> Unit) {
    var label by remember(edge.id) { mutableStateOf(edge.label.orEmpty()) }
    TendrilSheet(title = "Arrow", onDismiss = onDismiss) {
        Column {
            // §3.1.2 — the sheet is how an arrow's label is read at all (the board draws the
            // line, never the text), so the field stays and turns read-only. "Change" and
            // "Delete arrow" both write, so both go.
            TendrilField(
                value = label,
                onValueChange = { label = it; onSetLabel(it) },
                readOnly = viewOnly,
                placeholder = "Label (optional)",
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { onDismiss() }),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Direction: ${edge.direction.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.body, modifier = Modifier.weight(1f))
                if (!viewOnly) TextButton(onClick = onCycleDirection) { Text("Change") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (!viewOnly) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete arrow")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun CanvasPagePickerSheet(viewModel: CanvasViewModel, onDismiss: () -> Unit, onPick: (Page) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.pageSearchResults.collectAsState()

    TendrilSheet(scrolls = false, onDismiss = { viewModel.clearPageSearch(); onDismiss() }, modifier = Modifier.fillMaxHeight(0.6f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add a page card", style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearPageSearch(); onDismiss() }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            TendrilField(
                value = query,
                onValueChange = { query = it; viewModel.searchPages(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = "Search pages…",
            )
            LazyColumn {
                items(results, key = { it.id }) { candidate ->
                    Text(
                        candidate.title,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(candidate) }.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.body,
                    )
                }
            }
        }
    }
}
