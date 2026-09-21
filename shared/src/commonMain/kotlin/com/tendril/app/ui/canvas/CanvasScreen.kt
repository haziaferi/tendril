@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.canvas

import androidx.compose.foundation.gestures.drag
import com.tendril.app.domain.canvas.CANVAS_EMPTY_CARD_TEXT
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.theme.DrawingType
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.domain.canvas.boxEdgeDistance
import com.tendril.app.domain.canvas.nodeBox
import com.tendril.app.domain.canvas.fitToBoxes
import com.tendril.app.domain.canvas.FRAME_DEFAULT_W
import com.tendril.app.domain.canvas.FRAME_DEFAULT_LABEL
import com.tendril.app.domain.canvas.FRAME_DEFAULT_H
import com.tendril.app.domain.canvas.cardWidth
import com.tendril.app.domain.canvas.CANVAS_NODE_H
import androidx.compose.foundation.layout.ColumnScope
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.domain.plural
import com.tendril.app.ui.components.SubmenuItem
import com.tendril.app.ui.components.PointerMenu
import com.tendril.app.ui.components.KeyChip
import com.tendril.app.ui.components.MenuCheck
import com.tendril.app.ui.components.BarPillButton
import com.tendril.app.ui.components.BarMenuButton
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.components.onSecondaryClick
import com.tendril.app.domain.canvas.tidy
import com.tendril.app.domain.canvas.relationRoute
import com.tendril.app.domain.canvas.newChildPosition
import com.tendril.app.domain.canvas.leafUnderline
import com.tendril.app.domain.canvas.spineY
import com.tendril.app.domain.canvas.spineEnd
import com.tendril.app.domain.canvas.stemAnchors
import com.tendril.app.domain.canvas.ribOf
import com.tendril.app.domain.canvas.SPINE_DOT
import com.tendril.app.domain.canvas.foldBadgeAt
import com.tendril.app.domain.canvas.branchAnchors
import com.tendril.app.domain.canvas.TreeSide
import com.tendril.app.domain.canvas.TreeLevel
import com.tendril.app.domain.canvas.RelationRoute
import com.tendril.app.domain.canvas.NodeBox
import com.tendril.app.domain.canvas.FOLD_BADGE
import com.tendril.app.domain.canvas.CanvasTree
import com.tendril.app.domain.canvas.CanvasStructure
import com.tendril.app.domain.canvas.CANVAS_CARD_MAX_LINES
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.nav.ShellLayout
import com.tendril.app.ui.nav.LocalShellLayout
import com.tendril.app.ui.nav.LocalDensityProfile
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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
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
import com.tendril.app.ui.components.HueSheet
import com.tendril.app.ui.theme.hueColours

internal const val NODE_H = CANVAS_NODE_H
/** A card's own height — the strip its text asks for (`nodeBox`); `NODE_H` is the one-line minimum. */
internal fun CanvasNode.boxH(): Float = nodeBox(this).h   // a free card's; the tree's boxes come from `CanvasTree.box`
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
                CanvasViewModel(pageId, core.database.pageDao(), core.database.pageCanvasDao(), core.database.canvasNodeDao(), core.database.canvasEdgeDao(), core.viewLockState, core.pageContentRepository, core.templateManager)
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
    // The mind-map pass — the board's structure and the tree it makes of the nodes.
    val structure by viewModel.structure.collectAsState()
    val tree = remember(nodes, structure, embeddedPages) { CanvasTree(nodes, structure) { embeddedPages[it]?.title } }
    var showStructureMenu by remember { mutableStateOf(false) }
    val hasTree = nodes.any { it.parentId != null && it.type != CanvasNodeType.FRAME }

    var titleField by remember(page?.id) { mutableStateOf(page?.title ?: "") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showPagePicker by remember { mutableStateOf(false) }
    var showOwnMenu by remember { mutableStateOf(false) }
    var showTrashCanvas by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<CanvasNode?>(null) }
    var hueNode by remember { mutableStateOf<CanvasNode?>(null) }
    var editingEdge by remember { mutableStateOf<CanvasEdge?>(null) }
    var nodeToDelete by remember { mutableStateOf<CanvasNode?>(null) }

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
    fun newCardOrigin(): Pair<Float, Float> = contentAtPaneCentre(paneSize.width.toFloat(), paneSize.height.toFloat(), scale, pan.x, pan.y, density, cardWidth(null), NODE_H)
    fun fit() {
        val f = fitToBoxes(nodes.filter { tree.isVisible(it) }.map { tree.box(it) }, paneSize.width.toFloat(), paneSize.height.toFloat(), density)
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
            HorizontalDivider()
            // §0.10 item 15 — a frame at the visible centre, its label editor open at once (Obsidian's *Crea gruppo*).
            TendrilMenuItem(text = { Text("Frame") }, onClick = {
                showAddMenu = false
                val c = contentAtPaneCentre(paneSize.width.toFloat(), paneSize.height.toFloat(), scale, pan.x, pan.y, density, FRAME_DEFAULT_W, FRAME_DEFAULT_H)
                viewModel.addFrame(c.first, c.second) { pendingNewNodeId = it }
            })
        }
    }

    // The mind-map pass — the structure's check-menu: the board's, then *Tidy now*.
    val structureItems: @Composable ColumnScope.(close: () -> Unit) -> Unit = { close ->
        CanvasStructure.entries.forEach { s ->
            TendrilMenuItem(text = { Text(s.label) }, trailingIcon = { MenuCheck(structure == s) }, onClick = { close(); if (s != structure) viewModel.setBoardStructure(s) })
        }
        HorizontalDivider()
        TendrilMenuItem(text = { Text("Tidy now") }, enabled = hasTree, onClick = { close(); viewModel.tidy() })
    }
    // A node's tree verbs — the desktop's right-click menu and the phone's sheet draw the same list.
    val nodeVerbs: @Composable ColumnScope.(node: CanvasNode, close: () -> Unit) -> Unit = { node, close ->
        val kids = tree.children(node.id)
        val frame = node.type == CanvasNodeType.FRAME
        if (!frame) {
            // Under a pointer the menu is the way in, so it offers the editor; the phone's sheet *is* the editor.
            if (pointer || node.type == CanvasNodeType.PAGE_EMBED) TendrilMenuItem(text = { Text(if (node.type == CanvasNodeType.PAGE_EMBED) "Open page" else "Edit") }, onClick = { close(); if (node.type == CanvasNodeType.TEXT) editingNode = node else node.embeddedPageId?.let(onOpenPage) })
            if (!viewOnly) {
                TendrilMenuItem(text = { Text("Add child") }, trailingIcon = if (pointer) ({ KeyChip("Insert") }) else null, onClick = { close(); viewModel.addChild(node) { pendingNewNodeId = it } })
                TendrilMenuItem(text = { Text("Add sibling") }, trailingIcon = if (pointer) ({ KeyChip("Enter") }) else null, onClick = { close(); viewModel.addSibling(node) { pendingNewNodeId = it } })
                if (kids.isNotEmpty() || node.folded) {
                    val hidden = tree.hiddenCount(node.id)
                    TendrilMenuItem(text = { Text(if (node.folded) "Unfold ($hidden hidden)" else "Fold") }, onClick = { close(); viewModel.toggleFold(node) })
                }
                SubmenuItem(text = { Text("Structure") }) { closeSub ->
                    TendrilMenuItem(text = { Text("Inherit") }, trailingIcon = { MenuCheck(node.structure == null) }, onClick = { closeSub(); close(); viewModel.setNodeStructure(node, null) })
                    CanvasStructure.entries.forEach { s ->
                        TendrilMenuItem(text = { Text(s.label) }, trailingIcon = { MenuCheck(node.structure == s.key) }, onClick = { closeSub(); close(); viewModel.setNodeStructure(node, s) })
                    }
                }
                if (kids.isNotEmpty()) TendrilMenuItem(text = { Text("Frame this subtree") }, onClick = { close(); viewModel.frameSubtree(node) { pendingNewNodeId = it } })
                if (node.parentId != null) TendrilMenuItem(text = { Text("Detach from the tree") }, onClick = { close(); viewModel.setParent(node, null) })
                HorizontalDivider()
            }
        } else {
            TendrilMenuItem(text = { Text("Rename") }, onClick = { close(); editingNode = node })
            // S12 — a frame collapses to its strip and expands again; the count is what it holds.
            if (!viewOnly) {
                val held = tree.hiddenCount(node.id)
                TendrilMenuItem(text = { Text(if (node.folded) "Expand ($held hidden)" else "Collapse ($held inside)") }, onClick = { close(); viewModel.toggleFold(node) })
            }
        }
        // S13 — a hue for a text card or a frame (Obsidian's canvas colour); a page card keeps its own tint.
        if (!viewOnly && node.type != CanvasNodeType.PAGE_EMBED) TendrilMenuItem(text = { Text("Colour…") }, onClick = { close(); hueNode = node })
        if (!viewOnly) TendrilMenuItem(text = { Text("Delete") }, onClick = { close(); nodeToDelete = node })
    }

    // screen_px = (content_dp * density) * scale + pan, so a screen point inverts back to
    // content-dp via ((screen_px - pan) / scale) / density — used to drop a new card near
    // wherever the view currently looks centered, regardless of current pan/zoom.
    fun screenToContent(screen: Offset): Offset = ((screen - pan) / scale) / density
    fun addAtDoubleTap(tap: Offset) {
        val c = screenToContent(tap)
        viewModel.addTextNode(c.x - cardWidth(null) / 2f, c.y - NODE_H / 2f) { pendingNewNodeId = it }
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
                        // The mind-map pass — the structure's name in the bar (the Calendar's `Week ▾` pattern) and *Tidy*.
                        Box {
                            BarMenuButton(label = structure.label, open = showStructureMenu, onClick = { showStructureMenu = true })
                            TendrilMenu(expanded = showStructureMenu, onDismissRequest = { showStructureMenu = false }) { structureItems { showStructureMenu = false } }
                        }
                        if (hasTree && !viewOnly) BarPillButton(label = "Tidy", onClick = { viewModel.tidy() }, modifier = Modifier.padding(start = 4.dp))
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
                            // The phone's bar holds only `···` (L·P2): Structure, Tidy and Fit live in it — the desktop's bar
                            // buttons. Fit was missing here (canvas-frames' walk; S-list 2026-09-20): a frame made at the centre
                            // of a 360 dp screen is wider than the screen, and nothing brought it back.
                            if (!wide) {
                                SubmenuItem(text = { Text("Structure") }) { closeSub -> structureItems { closeSub(); showOwnMenu = false } }
                                if (hasTree && !viewOnly) TendrilMenuItem(text = { Text("Tidy") }, onClick = { showOwnMenu = false; viewModel.tidy() })
                                TendrilMenuItem(text = { Text("Fit every card") }, enabled = nodes.isNotEmpty(), onClick = { showOwnMenu = false; fit() })
                                HorizontalDivider()
                            }
                            if (onShowOnRoadMap != null) TendrilMenuItem(text = { Text("Show on Road Map") }, onClick = { showOwnMenu = false; onShowOnRoadMap(pageId) })
                            if (!viewOnly) TendrilMenuItem(text = { Text("Save as template") }, onClick = { showOwnMenu = false; viewModel.saveAsTemplate() })
                            if (!viewOnly) TendrilMenuItem(text = { Text("Move to Trash") }, onClick = { showOwnMenu = false; showTrashCanvas = true })
                        }
                    }
                    if (paneChrome != null) {
                        var showPaneMenu by remember { mutableStateOf(false) }
                        paneChrome.actions(this)
                        Box {
                            IconButton(onClick = { showPaneMenu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More") }
                            TendrilMenu(expanded = showPaneMenu, onDismissRequest = { showPaneMenu = false }) {
                                if (!wide) { SubmenuItem(text = { Text("Structure") }) { closeSub -> structureItems { closeSub(); showPaneMenu = false } }; HorizontalDivider() }
                                // §0.10 item 15 — the canvas's own verb before the workspace's (the page's `···` has the same).
                                if (!viewOnly) { TendrilMenuItem(text = { Text("Save as template") }, onClick = { showPaneMenu = false; viewModel.saveAsTemplate() }); HorizontalDivider() }
                                paneChrome.menuItems(this) { showPaneMenu = false }
                            }
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
                structure = structure,
                scale = scale,
                pan = pan,
                onScaleChange = { scale = it },
                onPanChange = { pan = it },
                onMoveNode = { node, x, y -> if (node.type == CanvasNodeType.FRAME) viewModel.moveFrame(node, x, y) else viewModel.moveNode(node, x, y) },
                onResizeFrame = { frame, w, h -> viewModel.resizeFrame(frame, w, h) },
                onResizeCard = { card, dx -> viewModel.resizeCard(card, dx) },
                onDroppedInTree = { node -> viewModel.tidyTreeOf(node) },
                onTapNode = { node ->
                    // S12 — a tap on a collapsed frame's strip expands it (the phone's; the desktop's strip handles its own click).
                    if (node.type == CanvasNodeType.FRAME && node.folded) viewModel.toggleFold(node)
                    // Under a pointer a first click selects, a second opens (Obsidian's); the phone opens at once.
                    else if (pointer && selectedNodeId != node.id) selectedNodeId = node.id
                    else if (node.type == CanvasNodeType.TEXT || node.type == CanvasNodeType.FRAME) editingNode = node
                    else node.embeddedPageId?.let(onOpenPage)
                },
                onDeleteNode = { viewModel.deleteNode(it); if (selectedNodeId == it.id) selectedNodeId = null },
                onConnect = { from, to -> viewModel.addEdge(from.id, to.id) },
                onSetParent = { node, parent -> viewModel.setParent(node, parent) },
                onFoldToggle = { viewModel.toggleFold(it) },
                onAddChild = { viewModel.addChild(it) { id -> pendingNewNodeId = id } },
                onAddSibling = { viewModel.addSibling(it) { id -> pendingNewNodeId = id } },
                nodeVerbs = nodeVerbs,
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
    hueNode?.let { node ->
        HueSheet(
            title = (node.text?.ifBlank { null } ?: if (node.type == CanvasNodeType.FRAME) FRAME_DEFAULT_LABEL else "Card") + " — colour",
            current = node.hue,
            unsetLabel = "None",
            onDone = { viewModel.setNodeHue(node, it); hueNode = null },
            onDismiss = { hueNode = null },
        )
    }
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
        TextNodeEditor(node = node, viewOnly = viewOnly, onDismiss = { closeEditor(null) }, onSave = { closeEditor(it) }, verbs = if (pointer || viewOnly) null else { close -> nodeVerbs(node) { editingNode = null; close() } })
    }

    nodeToDelete?.takeIf { !viewOnly }?.let { node ->
        val frame = node.type == CanvasNodeType.FRAME
        val kids = tree.children(node.id).size
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text(if (frame) "Delete this frame?" else "Delete this card?", style = MaterialTheme.typography.heading) },
            text = { Text(if (frame) "Its cards stay." else if (kids > 0) "Its ${plural(kids, "child", "children")} move up a level; any relationships go too." else "Any relationships go too.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteNode(node); if (selectedNodeId == node.id) selectedNodeId = null; nodeToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { nodeToDelete = null }) { Text("Cancel") } },
        )
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
    structure: CanvasStructure,
    scale: Float,
    pan: Offset,
    onScaleChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    onMoveNode: (CanvasNode, Float, Float) -> Unit,
    /** §0.10 item 15 — a frame's corner handle. */
    onResizeFrame: (CanvasNode, Float, Float) -> Unit,
    onResizeCard: (CanvasNode, Float) -> Unit,
    /** A tree node dropped among its siblings (S8): the tree tidies from its root. */
    onDroppedInTree: (CanvasNode) -> Unit,
    onTapNode: (CanvasNode) -> Unit,
    onDeleteNode: (CanvasNode) -> Unit,
    onConnect: (CanvasNode, CanvasNode) -> Unit,
    /** The mind-map pass — a node dropped on a card becomes its child; Tab and Enter add a child and a sibling of the selected node. */
    onSetParent: (CanvasNode, CanvasNode) -> Unit,
    onFoldToggle: (CanvasNode) -> Unit,
    onAddChild: (CanvasNode) -> Unit,
    onAddSibling: (CanvasNode) -> Unit,
    nodeVerbs: @Composable ColumnScope.(node: CanvasNode, close: () -> Unit) -> Unit,
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
    val currentPan by rememberUpdatedState(pan)
    val currentScale by rememberUpdatedState(scale)
    var linkDrag by remember { mutableStateOf<Pair<CanvasNode, Offset>?>(null) }
    var nodeMenuFor by remember { mutableStateOf<CanvasNode?>(null) }
    val tree = remember(nodes, structure, embeddedPages) { CanvasTree(nodes, structure) { embeddedPages[it]?.title } }
    var dropTargetId by remember { mutableStateOf<Long?>(null) }
    // The node's menu at the pointer (board px) — a right-click on a card.
    var contextMenu by remember { mutableStateOf<Pair<CanvasNode, Offset>?>(null) }
    /** The card under a layer point that a dragged node may become a child of — not itself, not its own descendant. */
    fun dropCandidate(dragged: CanvasNode, layerPx: Offset): CanvasNode? {
        val hit = tree.hit(layerPx.x / density.density, layerPx.y / density.density, except = dragged.id) ?: return null
        if (hit.id == dragged.id || tree.descendants(dragged.id).any { it.id == hit.id }) return null
        return hit
    }
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
                if (event.type != KeyEventType.KeyDown || selected == null || viewOnly) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Delete, Key.Backspace -> { nodeMenuFor = selected; true }
                    // The mind-map pass — Xmind's Tab and Freeplane's Insert for a child (AWT keeps Tab for focus
                    // traversal on the desktop, so Insert is the key that always arrives), Enter for a sibling.
                    Key.Tab, Key.Insert -> { if (selected.type != CanvasNodeType.FRAME) onAddChild(selected); true }
                    Key.Enter -> { if (selected.type != CanvasNodeType.FRAME) onAddSibling(selected); true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                // Item 15's walk: `pan` and `scale` read through `rememberUpdatedState` — the lambda
                // of a `pointerInput(Unit)` never restarts, so it had kept the pan captured at first
                // composition and every move set the board to that pan plus one delta: a 400 px
                // drag moved the board 26 px, on both platforms, since the board was built.
                detectTransformGestures { _, panDelta, zoom, _ ->
                    onScaleChange((currentScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE))
                    onPanChange(currentPan + panDelta)
                }
            }
            // A second detector on the ground for taps only: a tap clears the selection, a double
            // tap makes a card (L10). The cards consume their own downs, so neither reaches them.
            .pointerInput(onGroundDoubleTap) {
                detectTapGestures(onTap = { onGroundTap() }, onDoubleTap = onGroundDoubleTap)
            },
    ) {
        CanvasLayer(
            nodes = nodes,
            edges = edges,
            embeddedPages = embeddedPages,
            scale = scale,
            pan = pan,
            structure = structure,
            interactive = CanvasInteraction(
                viewOnly = viewOnly,
                selectedNodeId = selectedNodeId,
                linkDrag = linkDrag,
                dropTargetId = dropTargetId,
                onDragOver = { node, p -> dropTargetId = dropCandidate(node, p)?.id },
                // S8 (2026-09-20) — a tree node dropped among its siblings (not onto a card) takes its new slot at once:
                // the order is the position's (y on a side, x under Down; under Map the side is the node's own), and the
                // tree tidies from its root so the slot is drawn — Xmind's and Mindomo's drop. A board on *Free* keeps
                // the node where it was dropped, and a dragged node on any board is where its hand left it only until then.
                onDropAt = { node, p ->
                    val target = dropCandidate(node, p)
                    if (target != null) onSetParent(node, target)
                    else if (node.parentId != null && tree.structureOf(node) != CanvasStructure.FREE) onDroppedInTree(node)
                    dropTargetId = null
                },
                onFoldToggle = onFoldToggle,
                onContextMenu = { node, p -> contextMenu = node to (p * scale + pan) },
                onMoveNode = onMoveNode,
                onResizeFrame = onResizeFrame,
                onResizeCard = onResizeCard,
                onTapNode = onTapNode,
                onDeleteRequest = { nodeMenuFor = it },
                onTapEdge = onTapEdge,
                onLinkDragStart = { node -> val b = tree.box(node); linkDrag = node to Offset((b.x + b.w / 2) * density.density, (b.y + b.h / 2) * density.density) },
                onLinkDrag = { pointerInParent -> linkDrag = linkDrag?.let { (n, _) -> n to pointerInParent } },
                onLinkDragEnd = { node, pointerInParent ->
                    val target = tree.hit(pointerInParent.x / density.density, pointerInParent.y / density.density, except = node.id)
                    if (target != null) onConnect(node, target)
                    linkDrag = null
                },
            ),
        )
        contextMenu?.let { (node, at) ->
            PointerMenu(expanded = true, at = at, fallback = IntOffset.Zero, onDismiss = { contextMenu = null }) { nodeVerbs(node) { contextMenu = null } }
        }
    }

    // The `takeIf` is not redundant with the hidden badge: the badge is the only way to *open*
    // this dialog, but View-Only can be turned on from the Pages topbar while it is already
    // open, and a confirm tap after that would be exactly the destructive write the lock exists
    // to stop.
    nodeMenuFor?.takeIf { !viewOnly }?.let { node ->
        val frame = node.type == CanvasNodeType.FRAME
        AlertDialog(
            onDismissRequest = { nodeMenuFor = null },
            title = { Text(if (frame) "Delete this frame?" else "Delete this card?") },
            // Item 15 — a frame goes alone; its cards stay where they are (Obsidian's, on both platforms).
            text = { Text(if (frame) "Its cards stay." else "Any arrows connected to it go too.") },
            confirmButton = { TextButton(onClick = { onDeleteNode(node); nodeMenuFor = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { nodeMenuFor = null }) { Text("Cancel") } },
        )
    }
}

/** The board's live half: what a card and an edge can do, given only where the layer is interactive. */
internal class CanvasInteraction(
    val viewOnly: Boolean,
    val selectedNodeId: Long?,
    val linkDrag: Pair<CanvasNode, Offset>?,
    /** The mind-map pass — the card a dragged node is over (its ring lights): a drop makes the dragged node its child. */
    val dropTargetId: Long? = null,
    val onMoveNode: (CanvasNode, Float, Float) -> Unit,
    val onResizeFrame: (CanvasNode, Float, Float) -> Unit,
    /** The card's width by hand — the handle at its left edge on a selected card under a pointer; the value is the edge's move in dp (left grows). */
    val onResizeCard: (CanvasNode, Float) -> Unit = { _, _ -> },
    val onTapNode: (CanvasNode) -> Unit,
    val onDeleteRequest: (CanvasNode) -> Unit,
    val onTapEdge: (CanvasEdge) -> Unit,
    val onLinkDragStart: (CanvasNode) -> Unit,
    val onLinkDrag: (Offset) -> Unit,
    val onLinkDragEnd: (CanvasNode, Offset) -> Unit,
    /** A node being dragged, with the pointer in the layer's px — the board names the card under it. */
    val onDragOver: (CanvasNode, Offset) -> Unit = { _, _ -> },
    /** The drag ended at this point in the layer's px — over a card, the dragged node becomes its child. */
    val onDropAt: (CanvasNode, Offset) -> Unit = { _, _ -> },
    /** The fold badge at a branch's end. */
    val onFoldToggle: (CanvasNode) -> Unit = {},
    /** A right-click (or the phone's long press) on a node: the node's menu at the pointer (layer px). */
    val onContextMenu: (CanvasNode, Offset) -> Unit = { _, _ -> },
)

/**
 * §0.10 item 7 (2026-09-18) — **one canvas at two sizes**, the mind map's rule (§0.6.2): the
 * edges and the cards on one `graphicsLayer`, drawn by the board (interactive, filling the
 * viewport) and by the inert card in a page (`CanvasBlockCard`, at `fitToCards`'s scale, no
 * gesture on it — the block list scrolls over it). With [interactive] null nothing here reads a
 * pointer; with [showContent] false the cards are their boxes alone (a fit under
 * `CANVAS_CONTENT_MIN_SCALE`, where text would be unreadable — Obsidian hides it the same way).
 *
 * The mind-map pass (2026-09-20, `docs/critiques/mind-map-grounds.md`) — **one grammar**: the
 * layer draws from a [CanvasTree]. A node with a parent hangs on a *branch* — a headless curve
 * from the parent's side (an elbow under DOWN), one colour per main branch, 2 dp at the first
 * level and 1.5 deeper; a leaf's branch runs on as its underline. A `canvas_edges` row is a
 * *relationship*: dashed, 1.5 dp, `dim`, a head on the target's edge, its label on the line —
 * routed from the outer sides so it never crosses the branch it relates (`relationRoute`). A
 * folded node ends its branch in a badge with the hidden count. A frame with a parent follows
 * that subtree's box. The order: frames, branches and relationships, cards, badges, the frames'
 * labels (an arrow over a frame's border — #121).
 */
@Composable
internal fun CanvasLayer(
    nodes: List<CanvasNode>,
    edges: List<CanvasEdge>,
    embeddedPages: Map<Long, Page>,
    scale: Float,
    pan: Offset,
    interactive: CanvasInteraction?,
    showContent: Boolean = true,
    structure: CanvasStructure = CanvasStructure.FREE,
) = DrawingType { CanvasLayerBody(nodes, edges, embeddedPages, scale, pan, interactive, showContent, structure) }

@Composable
private fun CanvasLayerBody(
    nodes: List<CanvasNode>,
    edges: List<CanvasEdge>,
    embeddedPages: Map<Long, Page>,
    scale: Float,
    pan: Offset,
    interactive: CanvasInteraction?,
    showContent: Boolean,
    structure: CanvasStructure,
) {
    val density = LocalDensity.current
    val edgeInk = MaterialTheme.colorScheme.onSurfaceVariant
    val draftInk = MaterialTheme.colorScheme.primary
    val palette = LocalTendrilPalette.current
    val branchInks = remember(palette) { listOf(palette.event, palette.habit, palette.third) }
    val tree = remember(nodes, structure, embeddedPages) { CanvasTree(nodes, structure) { embeddedPages[it]?.title } }
    val visible = remember(tree) { nodes.filter { tree.isVisible(it) } }
    val labelStyle = MaterialTheme.typography.caption
    val labelGround = MaterialTheme.colorScheme.surface
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    Box(
        modifier = Modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)),
    ) {
        // Item 15 — frames first, so every card sits over its region; the edges over the frames
        // (#121 — Obsidian draws its edges above groups), then the cards, then the frames' labels.
        val frames = visible.filter { it.type == CanvasNodeType.FRAME }
        frames.forEach { node ->
            key(node.id) { CanvasFrameBox(node = node, box = tree.box(node), following = tree.followedBy(node) != null, hiddenCount = tree.hiddenCount(node.id), density = density.density, interactive = interactive, showContent = showContent, part = FramePart.BODY) }
        }
        Canvas(modifier = Modifier.size(4000.dp).then(
            if (interactive == null) Modifier else Modifier.pointerInput(edges, tree, scale) {
                // A tap on an arrow opens its editor. Item 15's walk found the previous form — a
                // `detectDragGestures` "so it composes with the pan" — consumed every drag past
                // touch slop and so cancelled the board's pan on both platforms (a 380 px swipe
                // moved the board 40). This waits for the up without consuming the down or the
                // moves; the pan consuming them cancels the wait, so a drag pans and a tap picks.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val hit = nearestEdge(up.position, tree, edges, density.density)
                    if (hit != null) { up.consume(); interactive.onTapEdge(hit) }
                }
            },
        )) {
            val d = density.density
            // S9 — a spine per spine root: `dim`, 2 dp, from the root's right edge past its last topic and bone.
            for (node in visible) {
                if (node.type == CanvasNodeType.FRAME || !tree.isSpineRoot(node) || node.folded) continue
                val extents = tree.descendants(node.id).filter { it.type != CanvasNodeType.FRAME && tree.isVisible(it) }.map { tree.box(it) }
                if (extents.isEmpty()) continue
                val rb = tree.box(node); val sy = spineY(rb)
                drawLine(edgeInk, Offset(rb.right * d, sy * d), Offset(spineEnd(rb, extents) * d, sy * d), strokeWidth = 2f * d, cap = StrokeCap.Round)
            }
            // S12 — a collapsed following frame stands where its anchor stood: the branch from the anchor's parent ends on the strip.
            for (frame in visible) {
                if (frame.type != CanvasNodeType.FRAME || !frame.folded) continue
                val anchor = tree.followedBy(frame) ?: continue
                val parent = tree.parentOf(anchor) ?: continue
                if (!tree.isVisible(parent)) continue
                val ink = branchInks[tree.mainBranchIndex(anchor).coerceAtLeast(0) % branchInks.size]
                val width = (if (tree.depthOf(anchor.id) == 1) 2f else 1.5f) * d
                drawBranch(tree.box(parent), tree.box(frame), tree.sideOf(anchor), tree.structureOf(parent).curved, d, ink, width)
            }
            // The branches: every visible node with a visible parent.
            for (node in visible) {
                if (node.type == CanvasNodeType.FRAME) continue
                val parent = tree.parentOf(node) ?: continue
                if (!tree.isVisible(parent)) continue
                val ink = branchInks[tree.mainBranchIndex(node).coerceAtLeast(0) % branchInks.size]
                val width = (if (tree.depthOf(node.id) == 1) 2f else 1.5f) * d
                val structure = tree.structureOf(node)
                if (structure.spine && tree.isSpineRoot(parent)) {
                    // S9 — a topic on the spine: a timeline's dot and stem; a fishbone's rib, leaning forward.
                    val sy = spineY(tree.box(parent)); val nb = tree.box(node); val side = tree.sideOf(node)
                    if (structure == CanvasStructure.TIMELINE) {
                        val st = stemAnchors(nb, sy, side)
                        drawLine(ink, Offset(st.x1 * d, st.y1 * d), Offset(st.x2 * d, st.y2 * d), strokeWidth = width, cap = StrokeCap.Round)
                        drawCircle(ink, radius = SPINE_DOT / 2f * d, center = Offset(st.x1 * d, st.y1 * d))
                    } else {
                        val rib = ribOf(nb, sy, side)
                        drawLine(ink, Offset(rib.footX * d, rib.spineY * d), Offset(rib.topX * d, rib.topY * d), strokeWidth = width, cap = StrokeCap.Round)
                    }
                } else if (structure == CanvasStructure.FISHBONE && tree.isRibTopic(parent)) {
                    // S9 — a bone: from the rib, at the word's baseline, to the word; the underline carries on from there.
                    val root = tree.spineRootOf(parent)
                    val nb = tree.box(node)
                    if (root != null) {
                        val rib = ribOf(tree.box(parent), spineY(tree.box(root)), tree.sideOf(parent))
                        drawLine(ink, Offset(rib.xAt(nb.bottom) * d, nb.bottom * d), Offset(nb.x * d, nb.bottom * d), strokeWidth = width, cap = StrokeCap.Round)
                    }
                } else drawBranch(tree.box(parent), tree.box(node), tree.sideOf(node), tree.structureOf(parent).curved, d, ink, width)
                if (tree.levelOf(node) == TreeLevel.LEAF) {
                    val u = leafUnderline(tree.box(node))
                    drawLine(ink, Offset(u.x1 * d, u.y1 * d), Offset(u.x2 * d, u.y2 * d), strokeWidth = width, cap = StrokeCap.Round)
                }
            }
            // The relationships.
            edges.forEach { edge ->
                val from = tree.byId[edge.fromNodeId] ?: return@forEach
                val to = tree.byId[edge.toNodeId] ?: return@forEach
                if (!tree.isVisible(from) || !tree.isVisible(to)) return@forEach
                val route = relationRoute(tree.box(from), tree.parentOf(from)?.let { tree.box(it) }, tree.box(to), tree.parentOf(to)?.let { tree.box(it) })
                drawRelationship(route, edge, d, edgeInk)
                val label = edge.label?.takeIf { it.isNotBlank() }
                if (label != null && showContent) {
                    val measured = textMeasurer.measure(label, labelStyle)
                    val w = measured.size.width + 8 * d; val h = measured.size.height + 2 * d
                    val x = route.midX * d - w / 2f; val y = route.midY * d - h / 2f
                    drawRoundRect(labelGround, Offset(x, y), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(4 * d))
                    drawText(measured, edgeInk, Offset(x + 4 * d, y + d))
                }
            }
            interactive?.linkDrag?.let { (fromNode, pointer) ->
                val fb = tree.box(fromNode)
                val start = Offset((fb.x + fb.w / 2) * d, (fb.y + fb.h / 2) * d)
                drawLine(color = draftInk, start = start, end = pointer, strokeWidth = 3f)
            }
        }

        visible.filter { it.type != CanvasNodeType.FRAME }.forEach { node ->
            key(node.id) {
                CanvasNodeCard(
                    node = node,
                    box = tree.box(node),
                    level = tree.levelOf(node),
                    embeddedPage = node.embeddedPageId?.let { embeddedPages[it] },
                    density = density.density,
                    interactive = interactive,
                    showContent = showContent,
                )
            }
        }
        // The fold badges: at a folded node's branch end, with the hidden count.
        visible.filter { it.folded && it.type != CanvasNodeType.FRAME }.forEach { node ->
            val count = tree.hiddenCount(node.id)
            if (count == 0) return@forEach
            val (cx, cy) = foldBadgeAt(tree.box(node), tree.sideOf(node))
            key("fold", node.id) { FoldBadge(count = count, x = cx, y = cy, density = density.density, onClick = interactive?.let { i -> { i.onFoldToggle(node) } }) }
        }
        // The labels over the cards: a card that overlaps a frame's top edge must not hide its handle.
        frames.forEach { node ->
            key("label", node.id) { CanvasFrameBox(node = node, box = tree.box(node), following = tree.followedBy(node) != null, hiddenCount = tree.hiddenCount(node.id), density = density.density, interactive = interactive, showContent = showContent, part = FramePart.LABEL) }
        }
    }
}

/** The mind-map pass — a folded branch's end: a disc with the hidden count; a click unfolds. */
@Composable
private fun FoldBadge(count: Int, x: Float, y: Float, density: Float, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .offset { IntOffset(((x - FOLD_BADGE / 2f) * density).roundToInt(), ((y - FOLD_BADGE / 2f) * density).roundToInt()) }
            .size(FOLD_BADGE.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurface) // type: BADGE — the fold's hidden count
    }
}

/**
 * §0.10 item 15 (2026-09-18) — the phone's canvas gesture, decided on Obsidian mobile measured:
 * **a long press lifts a card or a frame; a swipe pans.** The tray's chips follow the same rule
 * under Touch (B§13.6 #5). A tap still opens (a card) or edits (a frame's label). The detectors
 * consume nothing before the long press, so a swipe that starts on a card reaches the board's
 * pan. The desktop keeps its press-and-move (a pointer never pans by dragging a card).
 */
private fun Modifier.touchNodeGestures(density: Float, viewOnly: Boolean, node: () -> CanvasNode, interactive: CanvasInteraction): Modifier = this
    .pointerInput(density, viewOnly) {
        detectDragGesturesAfterLongPress(onDrag = { change, delta ->
            change.consume()
            if (!viewOnly) { val n = node(); interactive.onMoveNode(n, n.x + delta.x / density, n.y + delta.y / density) }
        })
    }
    .pointerInput(Unit) {
        // Not `detectTapGestures`: it consumes the down, and a consumed down cancels the board's
        // pan — a swipe that began on a card moved the board 40 px of 380 on the walk. This waits
        // for the up without touching the down; the pan consuming the moves cancels the wait.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation()
            if (up != null) { up.consume(); interactive.onTapNode(node()) }
        }
    }

/**
 * §0.10 item 15 — a frame: its region under the cards (a 3 % tint of the text, a hairline on
 * `faint` — Obsidian's group border measures 4.6 : 1, the critique's #1), its **label the handle**
 * in a pill above the top-left corner (drag moves the frame and what lies wholly inside it; a
 * click selects under a pointer, a tap edits on the phone), its corner handle the resize. The
 * body reads no pointer, so a press on it pans the board and a double-click makes a card there.
 * The delete disc lives on the label (a frame's verbs are its label's); the ring on selection.
 */
private enum class FramePart { BODY, LABEL }

@Composable
private fun CanvasFrameBox(node: CanvasNode, box: NodeBox, following: Boolean, hiddenCount: Int, density: Float, interactive: CanvasInteraction?, showContent: Boolean, part: FramePart) {
    val current by rememberUpdatedState(node)
    val palette = LocalTendrilPalette.current
    val pointer = LocalDensityProfile.current.pointer
    val viewOnly = interactive?.viewOnly ?: true
    val selected = interactive?.selectedNodeId == node.id
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val live = interactive != null && (!pointer || hovered || selected)
    val shape = RoundedCornerShape(12.dp)
    // S13 — a coloured frame: an 8 % tint of its hue with the border in the hue (its strip at 14 %).
    val frameHue = node.hue?.let { hueColours(it, palette) }
    Box(
        modifier = Modifier
            .offset { IntOffset((box.x * density).roundToInt(), (box.y * density).roundToInt()) }
            .size(box.w.dp, box.h.dp),
    ) {
        // The mind-map pass — a frame following a subtree is a drawn structure: its stroke at `dim`
        // (`faint` measured 3.56 : 1 on the ground, the mock critique's #2); the hand-sized frame keeps `faint`.
        // S12 — a collapsed frame is its strip: the label, `· n hidden`, a chevron; a click expands it; the
        // strip is the handle (a hand-sized frame carries its hidden cards along, as its label does). Drawn
        // in the label pass, over the cards — the body pass sits under the board's ground, which takes the down.
        if (node.folded) {
            if (part == FramePart.LABEL) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .background(frameHue?.tint ?: palette.text.copy(alpha = 0.03f), shape)
                    .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else frameHue?.hue ?: if (following) palette.textDim else palette.textFaint, shape)
                    .then(
                        when {
                            interactive == null -> Modifier
                            !pointer -> Modifier.touchNodeGestures(density, viewOnly, { current }, interactive)
                            else -> Modifier.hoverable(interaction).onSecondaryClick { at -> interactive.onContextMenu(current, Offset(box.x * density, box.y * density) + at) }.pointerInput(node.id, density, viewOnly) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(); down.consume()
                                    var totalDrag = Offset.Zero
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.pressed) {
                                            val delta = change.position - change.previousPosition
                                            if (delta != Offset.Zero) {
                                                totalDrag += delta
                                                if (!viewOnly) interactive.onMoveNode(current, current.x + delta.x / density, current.y + delta.y / density)
                                            }
                                            change.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (totalDrag.getDistance() < 12f) interactive.onFoldToggle(current)
                                }
                            }
                        },
                    )
                    .padding(start = 10.dp, end = 8.dp),
            ) {
                if (showContent) {
                    Text( // type: SECTION_HEADING — the collapsed frame's name, the strip's own heading
                        node.text.orEmpty().ifBlank { FRAME_DEFAULT_LABEL },
                        style = MaterialTheme.typography.heading,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text( // type: META — the hidden count beside the name
                        "· $hiddenCount hidden",
                        style = MaterialTheme.typography.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            return
        }
        if (part == FramePart.BODY) Box(
            modifier = Modifier
                .fillMaxSize()
                .background(frameHue?.frameTint ?: palette.text.copy(alpha = 0.03f), shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else frameHue?.hue ?: if (following) palette.textDim else palette.textFaint, shape),
        )
        if (showContent && part == FramePart.LABEL) {
            val pillHeight = if (pointer) 24.dp else 28.dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset(y = -(pillHeight + 4.dp))
                    .height(pillHeight)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .then(if (live) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)) else Modifier)
                    .then(
                        when {
                            interactive == null -> Modifier
                            !pointer -> Modifier.touchNodeGestures(density, viewOnly, { current }, interactive)
                            // S12 — a right-click on the pill opens the frame's menu (Rename · Collapse · Delete), as a card's does.
                            else -> Modifier.hoverable(interaction).onSecondaryClick { at -> interactive.onContextMenu(current, Offset(box.x * density, box.y * density) + at) }.pointerInput(node.id, density, viewOnly) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(); down.consume()
                                    var totalDrag = Offset.Zero
                                    do {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.pressed) {
                                            val delta = change.position - change.previousPosition
                                            if (delta != Offset.Zero) {
                                                totalDrag += delta
                                                if (!viewOnly) interactive.onMoveNode(current, current.x + delta.x / density, current.y + delta.y / density)
                                            }
                                            change.consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                    if (totalDrag.getDistance() < 12f) interactive.onTapNode(current)
                                }
                            }
                        },
                    )
                    .padding(horizontal = 8.dp),
            ) {
                // The user (2026-09-20, on the build): the name "blends in / disappears" at `label` in the
                // dim colour — a frame's name is the board's section heading: `heading`, `onSurface` at rest.
                Text( // type: SECTION_HEADING — a frame's name, the one text on a board that names a group
                    node.text.orEmpty().ifBlank { FRAME_DEFAULT_LABEL },
                    style = MaterialTheme.typography.heading,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // S12 — the collapse's handle on the pill: a chevron under a pointer (the phone's sheet has the verb).
                if (interactive != null && !viewOnly && live && pointer) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.ExpandMore, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).clickable { interactive.onFoldToggle(current) },
                    )
                }
                if (interactive != null && !viewOnly && live) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(if (pointer) 16.dp else 20.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            .clickable { interactive.onDeleteRequest(current) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete frame", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(3.dp))
                    }
                }
            }
        }
        // The resize handle at the corner: on the selected frame under a pointer, always on the phone;
        // never on a following frame — its subtree sizes it.
        if (part == FramePart.BODY && !following && interactive != null && !viewOnly && (selected || !pointer)) {
            val handle = if (pointer) 14.dp else 20.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = handle / 2, y = handle / 2)
                    .size(handle)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .pointerInput(node.id, density) {
                        detectDragGestures(onDrag = { change, delta ->
                            change.consume()
                            val n = current
                            interactive.onResizeFrame(n, n.width + delta.x / density, n.height + delta.y / density)
                        })
                    },
            )
        }
    }
}

/** The cubic's points at ten steps, for the tap test. */
private fun RelationRoute.samples(density: Float): List<Offset> = (0..10).map { i ->
    val t = i / 10f; val u = 1 - t
    Offset(
        (u * u * u * x1 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x2) * density,
        (u * u * u * y1 + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * y2) * density,
    )
}

/** Point-to-segment distance along every relationship's route, nearest under a small screen-space
 * threshold wins — the simplest reliable way to hit-test a tap against a drawn line. [tap] arrives
 * in this Canvas's own pixel space (matching the drawing's coordinates), so the route's dp go
 * through the same `* density`. */
private fun nearestEdge(tap: Offset, tree: CanvasTree, edges: List<CanvasEdge>, density: Float): CanvasEdge? {
    var best: CanvasEdge? = null
    var bestDist = 24f
    edges.forEach { edge ->
        val from = tree.byId[edge.fromNodeId] ?: return@forEach
        val to = tree.byId[edge.toNodeId] ?: return@forEach
        if (!tree.isVisible(from) || !tree.isVisible(to)) return@forEach
        val pts = relationRoute(tree.box(from), tree.parentOf(from)?.let { tree.box(it) }, tree.box(to), tree.parentOf(to)?.let { tree.box(it) }).samples(density)
        for (i in 0 until pts.size - 1) {
            val dist = distanceToSegment(tap, pts[i], pts[i + 1])
            if (dist < bestDist) { bestDist = dist; best = edge }
        }
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
 * fire by accident mid-drag, so delete gets its own always-visible small badge instead.
 *
 * The mind-map pass — the card by its [level]: a ROOT is `pageTitle` in a box with the accent's
 * border; a BRANCH the strip; a LEAF bare text whose branch is its underline (no badges — a
 * leaf's verbs are its menu's). A drag reports the pointer to the board so the card under it can
 * light as a drop target; the drop makes the dragged node its child. */
@Composable
private fun CanvasNodeCard(
    node: CanvasNode,
    box: NodeBox,
    level: TreeLevel,
    embeddedPage: Page?,
    density: Float,
    /** Null on the inert card in a page (item 7): no gesture, no badge, no hover. */
    interactive: CanvasInteraction?,
    showContent: Boolean = true,
) {
    val viewOnly = interactive?.viewOnly ?: true
    val selected = interactive?.selectedNodeId == node.id
    val dropTarget = interactive?.dropTargetId == node.id
    // The gesture reads the card as it is now, not as it was when the detector started.
    val current by rememberUpdatedState(node)
    // L10 — under a pointer the badges show on hover or on the selected card (14d's rule for row
    // controls); their row stays laid out so the text never moves. The phone shows them always.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pointer = LocalDensityProfile.current.pointer
    val badgesAlpha = if (!pointer || hovered || selected) 1f else 0f
    val leaf = level == TreeLevel.LEAF
    val root = level == TreeLevel.ROOT
    val ring = when {
        dropTarget -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        root -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        leaf -> null
        node.hue != null && node.type != CanvasNodeType.PAGE_EMBED -> BorderStroke(1.dp, hueColours(node.hue, LocalTendrilPalette.current).hue)
        // The user (2026-09-20): a 1 px border around the cards — the register's hairline, so a card holds its shape on every tint.
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    // A `Box`, not a `Surface`: a Surface clips its content to its shape, and the badges and the width
    // handle straddle the card's right edge (the frame's handle straddles its corner the same way).
    // S13 — a coloured card: a 14 % tint of its hue with the border in the hue (Obsidian's canvas colour, measured);
    // a leaf keeps its line, a page card its own tint, the selection ring still wins.
    val hueColours = node.hue?.takeIf { !leaf && node.type != CanvasNodeType.PAGE_EMBED }?.let { hueColours(it, LocalTendrilPalette.current) }
    val fill = when {
        leaf -> Color.Transparent
        hueColours != null -> hueColours.tint
        root -> MaterialTheme.colorScheme.background
        node.type == CanvasNodeType.PAGE_EMBED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val shape = RoundedCornerShape(if (root) 10.dp else 8.dp)
    Box(
        modifier = Modifier
            .offset { IntOffset((box.x * density).roundToInt(), (box.y * density).roundToInt()) }
            .size(box.w.dp, box.h.dp)
            .background(fill, shape)
            .then(if (ring != null) Modifier.border(ring, shape) else Modifier)
            .then(if (interactive == null) Modifier else if (!pointer) Modifier.touchNodeGestures(density, viewOnly, { current }, interactive) else Modifier.hoverable(interaction)
            .onSecondaryClick { at -> interactive.onContextMenu(current, Offset(current.x * density, current.y * density) + at) }
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
                    var last = down.position
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val delta = change.position - change.previousPosition
                            last = change.position
                            if (delta != Offset.Zero) {
                                // Accumulated even under View-Only, so the tap-vs-drag test below
                                // stays honest: a smeared finger that would have been a drag must
                                // not fall through and open the card's editor instead.
                                totalDrag += delta
                                if (!viewOnly) {
                                    interactive.onMoveNode(current, current.x + delta.x / density, current.y + delta.y / density)
                                    interactive.onDragOver(current, Offset(current.x * density, current.y * density) + change.position)
                                }
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (totalDrag.getDistance() < 12f) interactive.onTapNode(current)
                    else if (!viewOnly) interactive.onDropAt(current, Offset(current.x * density, current.y * density) + last)
                }
            }),
    ) {
        if (!showContent) return@Box
        if (leaf) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                Text( // type: TITLE — a leaf is its text on its branch, one line
                    node.text.orEmpty().ifBlank { "…" },
                    style = MaterialTheme.typography.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            return@Box
        }
        if (root) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text( // type: CARD_TITLE — the root's name, the map's title
                    node.text.orEmpty().ifBlank { embeddedPage?.title ?: "Untitled" },
                    style = MaterialTheme.typography.pageTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (interactive != null && !viewOnly) Box(
                    modifier = Modifier.align(Alignment.CenterEnd).alpha(badgesAlpha)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(node.id, density) {
                            var parentPointer = Offset.Zero
                            detectDragGestures(
                                onDragStart = { parentPointer = Offset((node.x + box.w) * density, node.y * density); interactive.onLinkDragStart(node) },
                                onDrag = { change, dragAmount -> change.consume(); parentPointer += dragAmount; interactive.onLinkDrag(parentPointer) },
                                onDragEnd = { interactive.onLinkDragEnd(node, parentPointer) },
                                onDragCancel = { interactive.onLinkDragEnd(node, parentPointer) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Link, contentDescription = "Draw a relationship to another card", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp)) }
            }
            return@Box
        }
        // The size count (2026-09-20): a card is a 48 dp strip, so the badges' reserved space is a
        // column at the right edge (× above the link handle), and the text keeps a 28 dp end margin —
        // the width the strip has to spare, never its height. The rule stands: the text never moves.
        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            // Badges live in their own dedicated column beside the content, not overlaid on top
            // of it — an absolutely-positioned corner badge overlapped whatever text happened
            // to be underneath it (confirmed live: the delete badge covered the first letters
            // of "Empty card," the link handle sat mid-word before "edit"). A reserved row
            // makes overlap structurally impossible regardless of text length or wrapping.
            // §3.1.2 — both badges are pure write affordances (destroy a card, draw an arrow),
            // so both come off the card entirely while View-Only is on rather than sitting there
            // greyed out. The row itself goes with them: with nothing left to reserve space for,
            // an empty strip would only push the card's own text down for no reason.
            // A folded node draws no link handle: its badge sits where the handle's column is (the critique's #3).
            // The width handle (the user, 2026-09-20 — a hand may set a card's width, Obsidian's rule): on a selected card
            // under a pointer, straddling the **left** edge's middle — the right edge is the badges' (on a one-line card
            // the two discs fill its height); the right edge stays put, the text keeps deciding the height.
            if (interactive != null && !viewOnly && pointer && selected && !leaf && !root) Box(
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-7).dp).size(14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .pointerInput(node.id, density) {
                        // The down is consumed at once: the card's body consumes every unconsumed change to move the
                        // card, and `detectDragGestures` would leave the first ones to it while waiting for slop.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            drag(down.id) { change ->
                                val dx = change.position.x - change.previousPosition.x
                                change.consume()
                                if (dx != 0f) interactive.onResizeCard(current, dx / density)
                            }
                        }
                    },
            )
            // The badges straddle the card's right edge (the frame's handle straddles its corner the same way), so the
            // text keeps the whole card: with the width fit to the text, a reserved 28 dp column was half of a short card.
            if (interactive != null && !viewOnly) Column(modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd).offset(x = if (pointer) 10.dp else 16.dp).alpha(badgesAlpha), verticalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        .clickable { interactive.onDeleteRequest(current) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete card", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(3.dp))
                }
                // The link handle's own detector, separate from the body's, so the initial
                // touch position (not a delta) can seed the parent-space pointer for the
                // reposition-drag gesture.
                if (!node.folded) Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .pointerInput(node.id, density) {
                            var parentPointer = Offset.Zero
                            detectDragGestures(
                                onDragStart = {
                                    parentPointer = Offset((node.x + box.w) * density, node.y * density)
                                    interactive.onLinkDragStart(node)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    parentPointer += dragAmount
                                    interactive.onLinkDrag(parentPointer)
                                },
                                onDragEnd = { interactive.onLinkDragEnd(node, parentPointer) },
                                onDragCancel = { interactive.onLinkDragEnd(node, parentPointer) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Link, contentDescription = "Draw a relationship to another card", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.CenterStart) {
                when (node.type) {
                    // The caption's verb by the profile (14h·2's `openVerb`); `onSurface` on the card's tint
                    // (`onSurfaceVariant` measured 3.45 : 1 there — `canvas-cards-mock.md` #1).
                    CanvasNodeType.TEXT -> Text( // type: PREVIEW_LINE — a card shows at most three lines of its text; the editor holds the rest
                        node.text.orEmpty().ifBlank { CANVAS_EMPTY_CARD_TEXT },   // the caption the box is sized for; the card itself is the verb
                        style = MaterialTheme.typography.description,
                        maxLines = CANVAS_CARD_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CanvasNodeType.FRAME -> Unit // drawn by CanvasFrameBox, never as a card
                    // A page card is one line: the glyph beside the title, on the strip.
                    CanvasNodeType.PAGE_EMBED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            embeddedPage?.title ?: "…",
                            style = MaterialTheme.typography.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextNodeEditor(node: CanvasNode, viewOnly: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit, verbs: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null) {
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
            val frame = node.type == CanvasNodeType.FRAME
            Text( // type: SHEET_HEADER — the editor sheet's own title, as it was before the frame's branch
                if (viewOnly) (if (frame) "Frame" else "Card") else (if (frame) "Edit frame" else "Edit card"),
                style = MaterialTheme.typography.heading, modifier = Modifier.padding(bottom = 12.dp),
            )
            TendrilField(value = text, onValueChange = { text = it }, readOnly = viewOnly, modifier = Modifier.fillMaxWidth(), focusRequester = focus, minLines = if (frame) 1 else 3)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(if (viewOnly) "Done" else "Cancel") }
                if (!viewOnly) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(text) }) { Text("Save") }
                }
            }
            // The mind-map pass — the phone's way to the tree's verbs (the desktop's right-click menu draws the same list).
            if (verbs != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Tree", style = MaterialTheme.typography.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) // type: EYEBROW
                verbs(onDismiss)
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
