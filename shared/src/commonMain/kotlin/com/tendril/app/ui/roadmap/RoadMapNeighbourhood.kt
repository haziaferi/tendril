package com.tendril.app.ui.roadmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.EmptyState

/**
 * 14h·1 — the Road Map's local graph around one page, drawn in the shelf: [RoadMapCanvas] with a
 * narrower node, the neighbourhood taken from the **full** graph (`RoadMapGraph.around`), not the
 * map's filtered one — the map may hide the Journal, and a Journal page's own neighbourhood must
 * not come back empty for that. Its own ViewModel instance (`key = "roadmap_shelf"`), so the
 * map's focus and selection are untouched; refreshed when the page changes. The map's gesture,
 * plus one: a click focuses a node, a click on the focused node opens it here, a double-click
 * opens it in the main pane ([RoadMapCanvas]'s `onDoubleTap`).
 */
@Composable
fun RoadMapNeighbourhood(
    core: WorkbenchCore,
    pageId: Long,
    depth: Int,
    onOpenHere: (Long) -> Unit,
    onOpenInMain: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RoadMapViewModel = viewModel(
        key = "roadmap_shelf",
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
    LaunchedEffect(pageId) { viewModel.refresh() }
    val full by viewModel.graph.collectAsState()
    val graph = remember(full, pageId, depth) { full.around(pageId, depth) }
    Box(modifier = modifier.fillMaxSize()) {
        if (graph.nodes.size <= 1) {
            EmptyState(
                icon = Icons.Outlined.AccountTree,
                message = "No connections within $depth hop(s)",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            RoadMapCanvas(graph = graph, onOpenPage = onOpenHere, nodeWidth = SHELF_NODE_WIDTH, onDoubleTap = onOpenInMain)
        }
        // The edge key, and nothing else (the critique's #5: the click legend belongs on the node).
        Text(
            "→ mention   — related",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp),
        )
    }
}

/**
 * The depth control for the shelf header: the Road Map's three steps as 28 dp targets (the
 * critique's floor), not its `FilterChip`s — at 380 dp the chips took the title's room.
 */
@Composable
fun DepthChips(depth: Int, onDepthChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..3).forEach { d ->
            val selected = depth == d
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onDepthChange(d) })
                    .semantics { contentDescription = "Depth $d" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$d",
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 120 dp against the map's 132: the shelf is 280–560 dp wide, and two nodes must fit abreast. */
private val SHELF_NODE_WIDTH = 120.dp

