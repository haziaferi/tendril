@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.roadmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import com.tendril.app.ui.components.LabelDot
import com.tendril.app.ui.theme.LocalTendrilPalette
import com.tendril.app.ui.theme.labelColours
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.tendril.app.ui.nav.ShellTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import com.tendril.app.ui.components.HoverPreviewCard
import com.tendril.app.ui.components.HoverPreviewState
import com.tendril.app.ui.components.PreviewTarget
import com.tendril.app.ui.components.hoverPreview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.data.page.Label
import com.tendril.app.domain.roadmap.EdgeSource
import com.tendril.app.domain.roadmap.RoadMapGraph
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.empty_pages_message
import com.tendril.app.generated.resources.empty_road_map_cta
import com.tendril.app.generated.resources.empty_road_map_message
import com.tendril.app.generated.resources.nav_road_map
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.pages.LocalViewOnly
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label
import com.tendril.app.ui.components.TendrilMenu
import androidx.compose.material3.HorizontalDivider
import com.tendril.app.ui.nav.ShellLayout
import com.tendril.app.ui.nav.LocalShellLayout
import com.tendril.app.ui.components.MenuCheck
import com.tendril.app.ui.components.BarMenuButton
import com.tendril.app.ui.components.TendrilMenuItem
import com.tendril.app.domain.plural

internal val NODE_WIDTH = 132.dp
private val NODE_HEIGHT = 44.dp
private const val REPULSION = 900_000f
private const val SPRING_STRENGTH = 0.02f
private const val CENTERING_STRENGTH = 0.015f
private const val DAMPING = 0.82f
private const val SETTLE_THRESHOLD_DP_PER_S = 4f
private const val DIMMED_ALPHA = 0.28f
private const val SETTLE_WARMUP_FRAMES = 30

/**
 * §3.4 — full-screen interactive canvas of relationships between existing Pages, reworked
 * against Obsidian's Graph View: tap a node once to highlight its neighborhood (dimming
 * everything else), tap the already-selected node again to open it; nodes carry a
 * degree-scaled border so hubs read visually heavier; mention edges draw an arrowhead
 * (mentioner → mentioned), manual "Relate to" edges stay a plain undirected line; the "All
 * Pages" sheet doubles as Obsidian's local-graph picker — choosing "Focus" there recenters
 * the canvas on that page plus its neighbors out to a depth slider.
 */
@Composable
fun RoadMapScreen(
    core: WorkbenchCore,
    onOpenPage: (Long) -> Unit,
    /** A page asking to be the focus — "Show on Road Map" from its `···` (step 8c); consumed once. */
    focusRequest: Long? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: RoadMapViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RoadMapViewModel(
                    core.database.pageDao(),
                    core.database.pageRelationDao(),
                    core.pageContentRepository,
                    core.viewLockState,
                    core.database.labelDao(),
                    core.keyValueStore,
                )
            }
        }
    )
    LaunchedEffect(focusRequest) {
        if (focusRequest != null) { viewModel.setFocus(focusRequest); onFocusConsumed() }
    }
    // §3.1.2 — the same global toggle [RoadMapViewModel.locked] refuses on, read here so the
    // screen stops offering the one write it has. Provided by
    // [com.tendril.app.ui.nav.WorkbenchScaffold], which hosts this tab.
    val viewOnly = LocalViewOnly.current
    val graph by viewModel.displayedGraph.collectAsState()
    val allPages by viewModel.allPages.collectAsState()
    val focusedPageId by viewModel.focusedPageId.collectAsState()
    val focusDepth by viewModel.focusDepth.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val labels by viewModel.labels.collectAsState()
    var showAllPages by remember { mutableStateOf(false) }
    var showRelateFlow by remember { mutableStateOf(false) }
    val focusedPage = allPages.find { it.id == focusedPageId }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                val wide = LocalShellLayout.current == ShellLayout.RAIL
                ShellTopBar(
                    title = { Text(stringResource(Res.string.nav_road_map)) },
                    actions = {
                        // L5 (C1) — on a wide window the filter row folds into a `Filter ▾` check-menu.
                        if (wide) FilterMenuButton(
                            filter = filter,
                            labels = labels,
                            onHideJournal = { viewModel.setHideJournal(it) },
                            onToggleKind = { viewModel.toggleKind(it) },
                            onLabel = { viewModel.setLabel(it) },
                        )
                        // The only control on this screen that writes anything. Hidden rather
                        // than disabled while View-Only is on, matching the Pages hub's own
                        // create FAB — refresh, focus and "All Pages" stay, so the app bar does
                        // not go empty and the map is still fully explorable.
                        if (!viewOnly) {
                            IconButton(onClick = { showRelateFlow = true }) {
                                Icon(Icons.Filled.Link, contentDescription = "Relate two pages")
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Road Map")
                        }
                        IconButton(onClick = { showAllPages = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "All Pages")
                        }
                    },
                )
                if (!wide) FilterRow(
                    filter = filter,
                    labels = labels,
                    onHideJournal = { viewModel.setHideJournal(it) },
                    onToggleKind = { viewModel.toggleKind(it) },
                    onLabel = { viewModel.setLabel(it) },
                )
                if (focusedPage != null) {
                    FocusBar(
                        page = focusedPage,
                        depth = focusDepth,
                        onDepthChange = { viewModel.setFocusDepth(it) },
                        onClear = { viewModel.setFocus(null) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (graph.nodes.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.AccountTree,
                    message = if (focusedPage != null) {
                        "\"${focusedPage.title}\" has no connections within " + plural(focusDepth, "hop")
                    } else {
                        stringResource(Res.string.empty_road_map_message) + " — " + stringResource(Res.string.empty_road_map_cta)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RoadMapCanvas(graph = graph, onOpenPage = onOpenPage, nodeWidth = NODE_WIDTH, core = core)
            }
            // L5 — the edge legend at the canvas's foot on a wide window (the filter row carried it).
            if (LocalShellLayout.current == ShellLayout.RAIL) {
                Text(
                    "→ mention   — related",
                    style = MaterialTheme.typography.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showAllPages) {
        AllPagesSheet(
            pages = allPages,
            onDismiss = { showAllPages = false },
            onOpenPage = { showAllPages = false; onOpenPage(it) },
            onFocus = { showAllPages = false; viewModel.setFocus(it) },
        )
    }

    // `&& !viewOnly` for the same reason the Canvas guards its own open sheets: the toggle lives
    // on another screen and can be turned on mid-flow, and this flow's second pick is the write.
    if (showRelateFlow && !viewOnly) {
        RelateToFlow(
            viewModel = viewModel,
            onDismiss = { showRelateFlow = false },
            onDone = { showRelateFlow = false },
        )
    }
}

/**
 * §3.4 (amended, step 8c / B§6 #14) — what the map leaves out, always visible under the app bar
 * like the FocusBar: the Journal (hidden by default), each page kind, one label. A legend for the
 * two edge kinds sits at the end, since the tint is the only thing that tells them apart at a
 * glance.
 */
@Composable
private fun FilterRow(
    filter: com.tendril.app.domain.roadmap.RoadMapFilter,
    labels: List<Label>,
    onHideJournal: (Boolean) -> Unit,
    onToggleKind: (PageKind) -> Unit,
    onLabel: (Long?) -> Unit,
) {
    var labelMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !filter.hideJournal,
            onClick = { onHideJournal(!filter.hideJournal) },
            label = { Text("Journal") },
            modifier = Modifier.padding(end = 6.dp),
        )
        for ((kind, name) in listOf(PageKind.PAGE to "Pages", PageKind.DATABASE to "Databases", PageKind.CANVAS to "Canvases")) {
            FilterChip(
                selected = kind in filter.kinds,
                onClick = { onToggleKind(kind) },
                label = { Text(name) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Box {
            val chosen = labels.find { it.id == filter.labelId }
            AssistChip(
                onClick = { labelMenu = true },
                label = { Text(if (chosen != null) "#" + chosen.name else "Any label") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            TendrilMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                TendrilMenuItem(text = { Text("Any label") }, onClick = { labelMenu = false; onLabel(null) })
                val palette = LocalTendrilPalette.current
                labels.forEach { label ->
                    TendrilMenuItem(
                        text = { Text("#" + label.name) },
                        leadingIcon = { LabelDot(labelColours(label.color, palette).hue) },
                        onClick = { labelMenu = false; onLabel(label.id) },
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "→ mention   — related",
            style = MaterialTheme.typography.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * L5 (C1) — the filter row as a check-menu in the bar: the Journal, the three kinds (a kind
 * refuses to be the last one unchecked, as `toggleKind` does), then the labels one at a time
 * (8c's rule). The menu stays open while toggling.
 */
@Composable
private fun FilterMenuButton(
    filter: com.tendril.app.domain.roadmap.RoadMapFilter,
    labels: List<Label>,
    onHideJournal: (Boolean) -> Unit,
    onToggleKind: (PageKind) -> Unit,
    onLabel: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val chosen = labels.find { it.id == filter.labelId }
    Box {
        BarMenuButton(if (chosen != null) "Filter · #" + chosen.name else "Filter", open = open, onClick = { open = true })
        TendrilMenu(expanded = open, onDismissRequest = { open = false }) {
            TendrilMenuItem(text = { Text("Journal") }, trailingIcon = { MenuCheck(!filter.hideJournal) }, onClick = { onHideJournal(!filter.hideJournal) })
            for ((kind, name) in listOf(PageKind.PAGE to "Pages", PageKind.DATABASE to "Databases", PageKind.CANVAS to "Canvases")) {
                TendrilMenuItem(text = { Text(name) }, trailingIcon = { MenuCheck(kind in filter.kinds) }, onClick = { onToggleKind(kind) })
            }
            HorizontalDivider()
            TendrilMenuItem(text = { Text("Any label") }, trailingIcon = { MenuCheck(filter.labelId == null) }, onClick = { onLabel(null) })
            val palette = LocalTendrilPalette.current
            labels.forEach { label ->
                TendrilMenuItem(
                    text = { Text("#" + label.name) },
                    leadingIcon = { LabelDot(labelColours(label.color, palette).hue) },
                    trailingIcon = { MenuCheck(filter.labelId == label.id) },
                    onClick = { onLabel(label.id) },
                )
            }
        }
    }
}

/** The active local-graph filter, shown as a dismissible bar under the app bar — Obsidian's
 * own local graph exposes the same two controls (which note, how many hops) permanently
 * visible while active, not buried in a settings sheet. */
@Composable
private fun FocusBar(page: Page, depth: Int, onDepthChange: (Int) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Focused on \"${page.title}\"",
            style = MaterialTheme.typography.label,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        (1..3).forEach { d ->
            FilterChip(
                selected = depth == d,
                onClick = { onDepthChange(d) },
                label = { Text("$d") },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Show entire Road Map") }
    }
}

/**
 * The force-directed canvas, shared with the shelf's neighbourhood (14h·1, `RoadMapNeighbourhood`):
 * the map draws it at [NODE_WIDTH], the shelf narrower. [onDoubleTap] non-null makes a tap wait the
 * double-tap timeout before it fires (the shelf: click here, double-click the main pane); the map
 * passes null and keeps its immediate select-then-open.
 */
@Composable
internal fun RoadMapCanvas(graph: RoadMapGraph, onOpenPage: (Long) -> Unit, nodeWidth: Dp, onDoubleTap: ((Long) -> Unit)? = null, core: WorkbenchCore? = null) {
    // B§13.6 #3 — the nodes preview their pages; the canvas holds the state and draws the one card.
    val hoverPreview = remember { HoverPreviewState() }
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Long, Offset>() }
    val velocities = remember { mutableMapOf<Long, Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var simulationTick by remember { mutableStateOf(0) }

    val nodeWidthPx = with(density) { nodeWidth.toPx() }
    val nodeHeightPx = with(density) { NODE_HEIGHT.toPx() }
    val springLengthPx = nodeWidthPx * 1.8f
    val minDistancePx = nodeWidthPx * 1.1f
    val settleThresholdPx = with(density) { SETTLE_THRESHOLD_DP_PER_S.dp.toPx() }
    // REPULSION is a dp-space constant; a 1/d² force in px space scales by density³ (two for
    // the distance, one for the px-per-dp of the resulting acceleration). Without it the phone's
    // ~2.75× density made repulsion ~7× weaker than the desktop's — below the settle threshold
    // for nodes seeded on top of each other, which is why they stayed stacked (step 8c).
    val repulsionPx = REPULSION * density.density * density.density * density.density

    // A selection surviving into a graph that no longer contains it (focus/depth changed,
    // or a refresh dropped the node) would otherwise dim everything forever with nothing to
    // compare against.
    LaunchedEffect(graph.nodes.map { it.id }.toSet()) {
        if (selectedId != null && graph.nodes.none { it.id == selectedId }) selectedId = null
    }

    // Seed any newly-appeared node (deterministic pseudo-random angle from its id, so the same
    // graph always starts from the same initial spread across app opens) and drop stale ids —
    // a page trashed/un-graphed since the last [RoadMapViewModel.refresh] shouldn't leave a
    // ghost entry the physics loop keeps simulating forever.
    LaunchedEffect(graph.nodes.map { it.id }, canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        val liveIds = graph.nodes.map { it.id }.toSet()
        positions.keys.retainAll(liveIds)
        velocities.keys.retainAll(liveIds)
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        graph.nodes.forEachIndexed { index, page ->
            if (page.id !in positions) {
                val angle = ((page.id * 137 + index * 47) % 360).toFloat() * (Math.PI / 180.0).toFloat()
                val radius = minOf(canvasSize.width, canvasSize.height) * 0.3f
                positions[page.id] = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)
                velocities[page.id] = Offset.Zero
            }
        }
    }

    // The force simulation itself — repulsion (all pairs) + spring (edges only) + a weak
    // centering pull, integrated with velocity damping until movement drops below a settle
    // threshold, then the loop stops ticking (battery/CPU idle) until [simulationTick] bumps
    // it awake again (a manual drag ending, or the graph changing).
    LaunchedEffect(graph.nodes.map { it.id }.toSet(), graph.edges, canvasSize, simulationTick) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        val ids = graph.nodes.map { it.id }
        var lastFrameTime = 0L
        var frames = 0
        while (isActive) {
            var settled = false
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeNanos
                    return@withFrameNanos
                }
                val dt = ((frameTimeNanos - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastFrameTime = frameTimeNanos

                val forces = HashMap<Long, Offset>(ids.size)
                ids.forEach { forces[it] = Offset.Zero }

                for (i in ids.indices) {
                    for (j in i + 1 until ids.size) {
                        val a = ids[i]
                        val b = ids[j]
                        val posA = positions[a] ?: continue
                        val posB = positions[b] ?: continue
                        val delta = posA - posB
                        val dist = delta.getDistance()
                        val dir = if (dist < 1f) Offset(1f, 0f) else delta / dist
                        // Collision avoidance folded into repulsion: below minDistancePx the
                        // clamp keeps force magnitude finite while still pushing hard, rather
                        // than a discontinuous separate "collision" pass.
                        val clampedDist = dist.coerceAtLeast(minDistancePx * 0.5f)
                        val forceMag = repulsionPx / (clampedDist * clampedDist)
                        forces[a] = forces[a]!! + dir * forceMag
                        forces[b] = forces[b]!! - dir * forceMag
                    }
                }

                graph.edges.forEach { edge ->
                    val posA = positions[edge.fromPageId] ?: return@forEach
                    val posB = positions[edge.toPageId] ?: return@forEach
                    val delta = posB - posA
                    val dist = delta.getDistance().coerceAtLeast(1f)
                    val dir = delta / dist
                    val forceMag = (dist - springLengthPx) * SPRING_STRENGTH
                    forces[edge.fromPageId] = (forces[edge.fromPageId] ?: Offset.Zero) + dir * forceMag
                    forces[edge.toPageId] = (forces[edge.toPageId] ?: Offset.Zero) - dir * forceMag
                }

                val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                ids.forEach { id ->
                    val pos = positions[id] ?: return@forEach
                    forces[id] = forces[id]!! + (center - pos) * CENTERING_STRENGTH
                }

                var totalSpeed = 0f
                ids.forEach { id ->
                    if (id == draggingId) return@forEach
                    val vel = ((velocities[id] ?: Offset.Zero) + (forces[id] ?: Offset.Zero) * dt) * DAMPING
                    velocities[id] = vel
                    // Kept on the canvas: a node the layout pushes past the edge is unreachable
                    // on a phone, and nothing the person can do brings it back but a lucky drag.
                    val next = (positions[id] ?: center) + vel * dt
                    positions[id] = Offset(
                        next.x.coerceIn(nodeWidthPx / 2f, (canvasSize.width - nodeWidthPx / 2f).coerceAtLeast(nodeWidthPx / 2f)),
                        next.y.coerceIn(nodeHeightPx / 2f, (canvasSize.height - nodeHeightPx / 2f).coerceAtLeast(nodeHeightPx / 2f)),
                    )
                    totalSpeed += vel.getDistance()
                }

                // Never on the first frames: velocities start at zero, and one frame of
                // acceleration is below the threshold even for nodes seeded on top of each
                // other — the loop used to declare the layout settled before it had moved
                // (found on the desktop, step 8c; the phone hid it behind the first drag).
                frames++
                if (frames > SETTLE_WARMUP_FRAMES && ids.isNotEmpty() && totalSpeed / ids.size < settleThresholdPx) settled = true
            }
            if (settled) break
        }
    }

    val highlightSet: Set<Long>? = selectedId?.let { setOf(it) + graph.neighborsOf(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { selectedId = null })
            },
    ) {
        // Step 8c — the two edge kinds tinted apart, not only shaped apart: a mention keeps its
        // arrowhead in the neutral ink, a manual "Relate to" line takes the tertiary hue.
        val mentionInk = MaterialTheme.colorScheme.onSurfaceVariant
        val relatedInk = MaterialTheme.colorScheme.tertiary
        Canvas(modifier = Modifier.fillMaxSize()) {
            graph.edges.forEach { edge ->
                val from = positions[edge.fromPageId]
                val to = positions[edge.toPageId]
                if (from != null && to != null) {
                    val touchesSelection = selectedId != null && (edge.fromPageId == selectedId || edge.toPageId == selectedId)
                    val alpha = if (highlightSet == null || touchesSelection) 0.6f else DIMMED_ALPHA * 0.6f
                    val lineColor = (if (edge.source == EdgeSource.MENTION) mentionInk else relatedInk).copy(alpha = alpha)
                    drawLine(color = lineColor, start = from, end = to, strokeWidth = 2f)
                    if (edge.source == EdgeSource.MENTION) {
                        drawArrowhead(from, to, nodeWidthPx / 2f, lineColor.copy(alpha = alpha * 1.6f))
                    }
                }
            }
        }

        graph.nodes.forEach { page ->
            key(page.id) {
                val dimmed = highlightSet != null && page.id !in highlightSet
                RoadMapNode(
                    page = page,
                    degree = graph.degreeOf(page.id),
                    dimmed = dimmed,
                    positions = positions,
                    nodeWidth = nodeWidth,
                    nodeWidthPx = nodeWidthPx,
                    nodeHeightPx = nodeHeightPx,
                    onDragStart = { draggingId = page.id; hoverPreview.hide() },
                    onDragEnd = { draggingId = null; simulationTick++ },
                    onTap = {
                        if (selectedId == page.id) onOpenPage(page.id) else selectedId = page.id
                    },
                    onDoubleTap = onDoubleTap?.let { f -> { f(page.id) } },
                    hoverPreview = hoverPreview,
                )
            }
        }
        if (core != null) HoverPreviewCard(core, hoverPreview, onOpenPage)
    }
}

/** A small filled triangle near the *to* end, pulled back by [nodeHalfWidth] so the tip sits
 * at the node's edge rather than being swallowed under it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowhead(from: Offset, to: Offset, nodeHalfWidth: Float, color: Color) {
    val delta = to - from
    val len = delta.getDistance()
    if (len < 1f) return
    val unit = delta / len
    val perp = Offset(-unit.y, unit.x)
    val tip = to - unit * nodeHalfWidth
    val back = 10f
    val spread = 5f
    val p1 = tip - unit * back + perp * spread
    val p2 = tip - unit * back - perp * spread
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color = color)
}

/** A single custom gesture detector rather than layering `clickable` + a separate
 * `pointerInput(detectDragGestures)` on the same node — two independent gesture detectors
 * racing for the same pointer stream is a real Compose footgun. Built on the manual
 * `awaitEachGesture`/`awaitPointerEvent` loop rather than `detectDragGestures` — the latter
 * only calls `onDragStart`/`onDragEnd` once the pointer crosses touch slop, so a genuinely
 * near-stationary tap (common with a steady finger, and how `adb shell input tap` behaves)
 * would fire neither callback at all and silently do nothing. Total drag distance below a
 * small threshold on release reads as a tap; anything past it is a reposition. A tap on an
 * already-selected node opens it (second tap = confirm); a tap on any other node just selects
 * it, mirroring Obsidian Graph View's hover-to-highlight on a device with no hover. */
@Composable
internal fun RoadMapNode(
    page: Page,
    degree: Int,
    dimmed: Boolean,
    positions: SnapshotStateMap<Long, Offset>,
    nodeWidth: Dp,
    nodeWidthPx: Float,
    nodeHeightPx: Float,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    hoverPreview: HoverPreviewState? = null,
) {
    val borderWidth = (1f + min(degree, 6) * 0.5f).dp
    // 14g·2 (B§13.8.3) — a page is the lifted ground, a database a fill (the third hue's tint until
    // the slider PR gives it its own), a canvas an outline in the third hue: a board is drawn, a
    // database is filled. The degree still thickens the accent border on pages and databases.
    val borderColor = when (page.kind) {
        PageKind.CANVAS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = (0.25f + min(degree, 6) * 0.1f).coerceAtMost(0.9f))
    }

    Surface(
        color = when (page.kind) {
            PageKind.DATABASE -> MaterialTheme.colorScheme.tertiaryContainer
            PageKind.CANVAS -> MaterialTheme.colorScheme.surfaceVariant
            PageKind.PAGE -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (page.kind == PageKind.CANVAS) maxOf(borderWidth, 1.5.dp) else borderWidth, borderColor),
        modifier = Modifier
            .offset {
                val pos = positions[page.id] ?: Offset.Zero
                IntOffset((pos.x - nodeWidthPx / 2f).roundToInt(), (pos.y - nodeHeightPx / 2f).roundToInt())
            }
            .size(nodeWidth, NODE_HEIGHT)
            .alpha(if (dimmed) DIMMED_ALPHA else 1f)
            .then(if (hoverPreview != null) Modifier.hoverPreview(hoverPreview, PreviewTarget.Page(page.id)) else Modifier)
            .pointerInput(page.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Consumed unconditionally, not just when the pointer actually moves — an
                    // unconsumed zero-movement tap would otherwise still reach the canvas
                    // background's own tap-to-deselect detector underneath, which immediately
                    // cleared the selection this same tap had just set (the two-tap
                    // select-then-open flow could never reach "already selected" as a result).
                    down.consume()
                    var totalDrag = Offset.Zero
                    var started = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) {
                                if (!started) { started = true; onDragStart() }
                                totalDrag += delta
                                positions[page.id] = (positions[page.id] ?: Offset.Zero) + delta
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    if (started) onDragEnd()
                    if (totalDrag.getDistance() < 12f) {
                        if (onDoubleTap == null) {
                            onTap()
                        } else {
                            // 14h·1 — the shelf's nodes: a second press inside the timeout is a
                            // double-tap (the main pane); otherwise the tap fires late, as
                            // `detectTapGestures` fires it. Same pattern, inside the one detector.
                            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                            if (second == null) {
                                onTap()
                            } else {
                                second.consume()
                                waitForUpOrCancellation()
                                onDoubleTap()
                            }
                        }
                    }
                }
            }
            .semantics { contentDescription = if (onDoubleTap == null) "${page.title} — tap to focus, tap again to open" else "${page.title} — click to focus, click again to open here, double-click to open in the main pane" },
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            Text(page.title, style = MaterialTheme.typography.label, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** §2.2/§3.4 — "'All Pages' is a collapsible bottom sheet/drawer over the canvas, not a fixed
 * side panel." Doubles as Obsidian's local-graph picker: "Focus" recenters Road Map on that
 * page's neighborhood instead of just navigating away from the map entirely. */
@Composable
private fun AllPagesSheet(pages: List<Page>, onDismiss: () -> Unit, onOpenPage: (Long) -> Unit, onFocus: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(pages, query) {
        if (query.isBlank()) pages else pages.filter { it.title.contains(query, ignoreCase = true) }
    }

    TendrilSheet(title = "All Pages", onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.7f)) {
        Column {
            OutlinedTextField(
                textStyle = MaterialTheme.typography.body,
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                placeholder = { Text("Filter…") },
            )
            if (filtered.isEmpty()) {
                EmptyState(icon = Icons.Outlined.Description, message = stringResource(Res.string.empty_pages_message), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { page ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenPage(page.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (page.kind == PageKind.DATABASE) Icons.Outlined.TableChart else Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(page.title, style = MaterialTheme.typography.body, modifier = Modifier.weight(1f), maxLines = 1)
                            AssistChip(
                                onClick = { onFocus(page.id) },
                                label = { Text("Focus") },
                                leadingIcon = { Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** §3.4 — the manual "Relate to…" action: two sequential search-and-pick steps (from, then to)
 * rather than a canvas drag-to-connect gesture, which would conflict with node repositioning's
 * own drag handler on the same nodes. */
@Composable
private fun RelateToFlow(viewModel: RoadMapViewModel, onDismiss: () -> Unit, onDone: () -> Unit) {
    var fromPage by remember { mutableStateOf<Page?>(null) }
    val current = fromPage

    if (current == null) {
        PagePickerSheet(
            title = "Relate — pick the first page",
            viewModel = viewModel,
            excludePageId = null,
            onDismiss = onDismiss,
            onPick = { fromPage = it },
        )
    } else {
        PagePickerSheet(
            title = "Relate \"${current.title}\" to…",
            viewModel = viewModel,
            excludePageId = current.id,
            onDismiss = onDismiss,
            onPick = { target -> viewModel.relate(current.id, target.id); onDone() },
        )
    }
}

@Composable
private fun PagePickerSheet(title: String, viewModel: RoadMapViewModel, excludePageId: Long?, onDismiss: () -> Unit, onPick: (Page) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.pageSearchResults.collectAsState()

    TendrilSheet(onDismiss = { viewModel.clearPageSearch(); onDismiss() }, modifier = Modifier.fillMaxHeight(0.6f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.clearPageSearch(); onDismiss() }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            OutlinedTextField(
                textStyle = MaterialTheme.typography.body,
                value = query,
                onValueChange = { query = it; viewModel.searchPages(it, excludePageId) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search pages…") },
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchPages(query, excludePageId) }),
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
