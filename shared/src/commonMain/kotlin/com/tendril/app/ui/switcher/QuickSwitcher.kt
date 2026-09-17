@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.switcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.SEARCH_HL_CLOSE
import com.tendril.app.data.page.SEARCH_HL_OPEN
import com.tendril.app.data.page.searchPrefix
import com.tendril.app.domain.SwitcherCommand
import com.tendril.app.domain.SwitcherMode
import com.tendril.app.domain.filterCommands
import com.tendril.app.domain.parseSwitcherInput
import com.tendril.app.domain.rankPageHits
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.EmptyState
import kotlinx.coroutines.delay
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.body

/** Whether the switcher is open — owned by the scaffold, toggled by the Pages search icon and
 * by the desktop's Ctrl+K, which lives outside the composition and needs a handle. */
class SwitcherState {
    var open by mutableStateOf(false)
}

/**
 * §3.1.7 / B§6 #11 — one field over everything. Text finds pages (title first, then the FTS
 * index, which since §0.8 step 8a holds titles too); a leading `>` finds commands: new page,
 * new database, Journal today, Review, the five tabs, a timer on a task. ↑↓ move, Enter runs,
 * Esc / Back closes. Replaces the Pages search overlay, whose rows and highlighting it keeps.
 */
@Composable
fun QuickSwitcher(
    core: WorkbenchCore,
    commands: List<SwitcherCommand>,
    onOpenPage: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PageSearchHit>>(emptyList()) }
    var selected by remember { mutableStateOf(0) }
    val mode = parseSwitcherInput(text)
    val shownCommands = (mode as? SwitcherMode.Commands)?.let { filterCommands(commands, it.query) }.orEmpty()
    val focus = remember { FocusRequester() }

    BackHandler(enabled = true, onBack = onDismiss)
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(text) {
        selected = 0
        val m = parseSwitcherInput(text)
        if (m is SwitcherMode.Pages) {
            delay(150)
            val live = core.database.pageDao().getAll()
            hits = rankPageHits(m.query, live, core.database.pageFtsDao().searchPrefix(m.query))
        } else {
            hits = emptyList()
        }
    }
    val count = if (mode is SwitcherMode.Commands) shownCommands.size else hits.size
    fun runSelected() {
        when (mode) {
            is SwitcherMode.Commands -> shownCommands.getOrNull(selected)?.let { onDismiss(); it.run() }
            is SwitcherMode.Pages -> hits.getOrNull(selected)?.let { onDismiss(); onOpenPage(it.pageId) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // The scaffold leaves the status-bar inset to each screen's own TopAppBar (see
        // `WorkbenchScaffold`); this overlay has none, so it takes the inset itself or the field
        // sits under the clock. Zero on the desktop.
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                OutlinedTextField(
                    textStyle = MaterialTheme.typography.body,
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { if (count > 0) selected = (selected + 1) % count; true }
                                Key.DirectionUp -> { if (count > 0) selected = (selected - 1 + count) % count; true }
                                Key.Enter, Key.NumPadEnter -> { runSelected(); true }
                                else -> false
                            }
                        },
                    singleLine = true,
                    placeholder = { Text("Search pages, or > for a command") },
                )
            }
            when (mode) {
                is SwitcherMode.Commands -> {
                    if (shownCommands.isEmpty()) {
                        EmptyState(icon = Icons.Outlined.KeyboardCommandKey, message = "No command matches", modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn {
                            itemsIndexed(shownCommands, key = { _, c -> c.title }) { index, command ->
                                ResultRow(selected = index == selected, onClick = { onDismiss(); command.run() }) {
                                    Icon(Icons.Outlined.KeyboardCommandKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(command.title, style = MaterialTheme.typography.body)
                                }
                            }
                        }
                    }
                }
                is SwitcherMode.Pages -> {
                    if (mode.query.isNotBlank() && hits.isEmpty()) {
                        EmptyState(icon = Icons.Filled.Search, message = "No pages match '${mode.query}'", modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn {
                            itemsIndexed(hits, key = { _, h -> h.pageId }) { index, hit ->
                                ResultRow(selected = index == selected, onClick = { onDismiss(); onOpenPage(hit.pageId) }) {
                                    val hitIcon = hit.icon
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        if (hitIcon != null) Text(hitIcon, style = MaterialTheme.typography.heading)
                                        else Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(hit.title, style = MaterialTheme.typography.body)
                                        if (hit.snippet.isNotBlank()) {
                                            Text(highlightMatches(hit.snippet, MaterialTheme.typography.heading.fontWeight), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The delimiters SQLite's `snippet()` wraps each match in → a span at the heading's weight (see `PageFtsDao.search`). */
internal fun highlightMatches(snippet: String, weight: FontWeight? = FontWeight.SemiBold): AnnotatedString = buildAnnotatedString {
    var inMatch = false
    for (char in snippet) {
        when (char) {
            SEARCH_HL_OPEN -> inMatch = true
            SEARCH_HL_CLOSE -> inMatch = false
            else -> if (inMatch) withStyle(SpanStyle(fontWeight = weight)) { append(char) } else append(char)
        }
    }
}
