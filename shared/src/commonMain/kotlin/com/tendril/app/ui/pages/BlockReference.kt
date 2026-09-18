package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.Page
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.HoverPreviewState
import com.tendril.app.ui.components.PreviewTarget
import com.tendril.app.ui.components.TendrilSheet
import com.tendril.app.ui.components.hoverPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading

/**
 * §0.6.12 — a BLOCK_REFERENCE block: the source block's live text behind an accent bar (the
 * mark that says "someone else's words"), its page's title under it, a tap opening that page.
 * Inert — edit the words at their source. Without [core] (a preview) or without the source on
 * this device, the cached [Block.content] stands in, and the caption says so.
 */
@Composable
internal fun BlockReferenceCard(core: WorkbenchCore?, block: Block, onOpenPage: (Long) -> Unit, hoverPreview: HoverPreviewState? = null) {
    val uid = block.referencedBlockUid
    val source by (if (core != null && uid != null) core.database.blockDao().observeByUid(uid) else flowOf(null)).collectAsState(initial = null)
    val sourcePageId = block.mentionedPageId
    val sourceTitle by (if (core != null && sourcePageId != null) core.database.pageDao().observeById(sourcePageId) else flowOf(null)).collectAsState(initial = null)
    val text = source?.content ?: block.content
    val caption = when {
        sourceTitle != null && source != null -> sourceTitle!!.title
        sourceTitle != null -> sourceTitle!!.title + " · block not on this device"
        else -> "source not on this device"
    }
    Surface(
        onClick = { sourcePageId?.let(onOpenPage) },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        // B§13.6 #3 — hover: the source line in its context, marked.
        modifier = Modifier.fillMaxWidth().then(if (hoverPreview != null && uid != null) Modifier.hoverPreview(hoverPreview, PreviewTarget.Reference(uid, sourcePageId)) else Modifier),
    ) {
        // IntrinsicSize.Min so the accent bar is as tall as the text beside it.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(text.ifBlank { "(empty block)" }, style = MaterialTheme.typography.body)
                Text(caption, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** §0.6.12 — the picker behind `((` and the slash sheet's "Block reference": search the words,
 * pick the block. Rows say which page they are from; the debounce is the switcher's. */
@Composable
internal fun BlockReferencePickerDialog(viewModel: PageDetailViewModel, onDismiss: () -> Unit, onPick: (Block) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Page, Block>>>(emptyList()) }
    LaunchedEffect(query) {
        delay(150)
        viewModel.searchBlocks(query) { results = it }
    }
    TendrilSheet(scrolls = false, onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.5f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reference a block", style = MaterialTheme.typography.heading, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.body.copy(color = MaterialTheme.colorScheme.onSurface),
            )
            if (query.isBlank()) {
                Text("Type a few words of the block", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(results, key = { it.second.id }) { (page, candidate) ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onPick(candidate) }.padding(vertical = 8.dp)) {
                        Text(candidate.content, style = MaterialTheme.typography.body, maxLines = 2)
                        Text(page.title, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
