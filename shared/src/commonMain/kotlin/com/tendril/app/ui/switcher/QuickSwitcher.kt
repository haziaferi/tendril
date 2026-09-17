@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tendril.app.ui.switcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.SEARCH_HL_CLOSE
import com.tendril.app.data.page.SEARCH_HL_OPEN
import com.tendril.app.data.page.searchPrefix
import com.tendril.app.domain.SwitcherCommand
import com.tendril.app.domain.SwitcherItem
import com.tendril.app.domain.SwitcherMode
import com.tendril.app.domain.SwitcherSection
import com.tendril.app.domain.flat
import com.tendril.app.domain.parseSwitcherInput
import com.tendril.app.domain.prefixRange
import com.tendril.app.domain.rankPageHits
import com.tendril.app.domain.switcherSections
import com.tendril.app.domain.time.relativeTime
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.CentredCard
import com.tendril.app.ui.components.EmptyState
import com.tendril.app.ui.components.KeyChip
import com.tendril.app.ui.components.TendrilField
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.nav.LocalShellLayout
import com.tendril.app.ui.nav.ShellLayout
import com.tendril.app.ui.theme.body
import com.tendril.app.ui.theme.caption
import com.tendril.app.ui.theme.description
import com.tendril.app.ui.theme.eyebrow
import com.tendril.app.ui.theme.heading
import com.tendril.app.ui.theme.label
import kotlinx.coroutines.delay

/** Whether the switcher is open — owned by the scaffold, toggled by the Pages search icon and
 * by the desktop's Ctrl+K, which lives outside the composition and needs a handle. */
class SwitcherState {
    var open by mutableStateOf(false)
}

private const val RECENTS = 8

/**
 * §3.1.7 / B§6 #11 — one field over everything. Text finds pages (title first, then the FTS
 * index, which since §0.8 step 8a holds titles too); a leading `>` finds commands: new page,
 * new database, Journal today, Review, the five tabs, a timer on a task. ↑↓ move, Enter runs,
 * Esc / Back closes. Replaces the Pages search overlay, whose rows and highlighting it keeps.
 *
 * L6 (2026-09-17, the desktop audit's #6): on a wide window the switcher is **a centred card**
 * — [CentredCard]'s frame at 20 % of the window, 560 dp, a 36 dp [TendrilField], rows at the
 * profile's height (Notion Calendar's command menu measured 544 CSS px · 37 · 29; Obsidian's
 * 560 · 38 · 26) — with **Recents** when nothing is typed, section eyebrows, the typed prefix
 * bold, the chord beside a command, and a footer of key hints. A plain query lists the pages
 * and then up to three matching commands ([switcherSections]'s A1); `>` lists commands alone.
 * The phone keeps its full-screen overlay, gaining the Recents.
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
    var recents by remember { mutableStateOf<List<Pair<PageSearchHit, String>>>(emptyList()) }
    var selected by remember { mutableStateOf(0) }
    val mode = parseSwitcherInput(text)
    val sections = switcherSections(mode, hits, recents, commands)
    val items = sections.flat()
    val focus = remember { FocusRequester() }

    BackHandler(enabled = true, onBack = onDismiss)
    LaunchedEffect(Unit) {
        focus.requestFocus()
        recents = core.database.pageDao().getRecentlyEdited(RECENTS).map { page ->
            PageSearchHit(pageId = page.id, title = page.title, icon = page.icon, snippet = "") to relativeTime(page.updatedAt)
        }
    }
    LaunchedEffect(text) {
        selected = 0
        val m = parseSwitcherInput(text)
        if (m is SwitcherMode.Pages && m.query.isNotBlank()) {
            delay(150)
            val live = core.database.pageDao().getAll()
            hits = rankPageHits(m.query, live, core.database.pageFtsDao().searchPrefix(m.query))
        } else {
            hits = emptyList()
        }
    }
    fun run(item: SwitcherItem) {
        onDismiss()
        when (item) {
            is SwitcherItem.Cmd -> item.command.run()
            is SwitcherItem.Hit -> onOpenPage(item.hit.pageId)
        }
    }
    val keys: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) false
        else when (event.key) {
            Key.DirectionDown -> { if (items.isNotEmpty()) selected = (selected + 1) % items.size; true }
            Key.DirectionUp -> { if (items.isNotEmpty()) selected = (selected - 1 + items.size) % items.size; true }
            Key.Enter, Key.NumPadEnter -> { items.getOrNull(selected)?.let(::run); true }
            else -> false
        }
    }
    val query = (mode as? SwitcherMode.Pages)?.query ?: (mode as SwitcherMode.Commands).query

    if (LocalShellLayout.current == ShellLayout.RAIL) {
        SwitcherCard(text, { text = it }, sections, items, selected, query, focus, keys, ::run, onDismiss)
    } else {
        SwitcherOverlay(text, { text = it }, sections, items, selected, query, mode, focus, keys, ::run, onDismiss)
    }
}

// ---- the wide window's card

@Composable
private fun SwitcherCard(
    text: String,
    onText: (String) -> Unit,
    sections: List<SwitcherSection>,
    items: List<SwitcherItem>,
    selected: Int,
    query: String,
    focus: FocusRequester,
    keys: (KeyEvent) -> Boolean,
    onRun: (SwitcherItem) -> Unit,
    onDismiss: () -> Unit,
) {
    CentredCard(onDismiss = onDismiss, top = 0.2f) { _ ->
        Column {
            TendrilField(
                value = text,
                onValueChange = onText,
                placeholder = "Search pages, or > for a command",
                leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) },
                focusRequester = focus,
                onPreviewKeyEvent = keys,
                modifier = Modifier.fillMaxWidth(),
            )
            if (items.isEmpty() && query.isNotBlank()) {
                Text(
                    "No pages match ‘$query’",
                    style = MaterialTheme.typography.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else if (items.isNotEmpty()) {
                val listState = rememberLazyListState()
                val rowHeight = LocalDensityProfile.current.rowHeightDp.dp
                // The selected row stays in view; the list's items are the sections' labels and rows in order.
                val listIndexOf = remember(sections) { sectionListIndices(sections) }
                LaunchedEffect(selected) { listIndexOf.getOrNull(selected)?.let { listState.animateScrollToItem(it) } }
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 480.dp).padding(bottom = 4.dp)) {
                    var index = 0
                    for (section in sections) {
                        item(key = "label:" + section.label) {
                            Text(
                                section.label.uppercase(),
                                style = MaterialTheme.typography.eyebrow,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }
                        for (entry in section.items) {
                            val i = index++
                            item(key = itemKey(entry)) { CardRow(entry, i == selected, query, rowHeight) { onRun(entry) } }
                        }
                        if (section.label == "Recent") {
                            item(key = "hint") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = rowHeight).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text( // type: EXPLAINER — the Recents' last row explains the field's two rules
                                        "Type to find a page ·", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    KeyChip(">")
                                    Text( // type: EXPLAINER
                                        "for a command", style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FootHint("↑↓", "Navigate"); FootHint("↵", "Open"); FootHint("Esc", "Close")
            }
        }
    }
}

/** The `LazyColumn` index of each item — labels and the hint row take slots too. */
private fun sectionListIndices(sections: List<SwitcherSection>): List<Int> = buildList {
    var slot = 0
    for (section in sections) {
        slot++ // the label
        repeat(section.items.size) { add(slot++) }
        if (section.label == "Recent") slot++ // the hint
    }
}

private fun itemKey(item: SwitcherItem): String = when (item) {
    is SwitcherItem.Hit -> "page:" + item.hit.pageId
    is SwitcherItem.Cmd -> "cmd:" + item.command.title
}

@Composable
private fun FootHint(key: String, verb: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(key, style = MaterialTheme.typography.label, color = MaterialTheme.colorScheme.onSurface)
        Text( // type: ICON_LABEL — the footer's verb beside its key, Notion Calendar's 11 px hint line
            verb, style = MaterialTheme.typography.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardRow(item: SwitcherItem, selected: Boolean, query: String, rowHeight: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    // A selected row's secondary text in `onSurface`: `dim` on the tint measured 3.45:1 (the mock's #1).
    val meta = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .heightIn(min = rowHeight)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (item) {
            is SwitcherItem.Hit -> {
                val hitIcon = item.hit.icon
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    if (hitIcon != null) Text(hitIcon, style = MaterialTheme.typography.body)
                    else Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(highlightPrefix(item.hit.title, query), style = MaterialTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.hit.snippet.isNotBlank()) {
                        Text( // type: META — the FTS snippet after the title, one line
                            highlightMatches(item.hit.snippet, MaterialTheme.typography.heading.fontWeight), style = MaterialTheme.typography.description, color = meta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
                if (item.meta != null) Text( // type: META — the page's age at the row's right
                    item.meta, style = MaterialTheme.typography.description, color = meta, maxLines = 1)
            }
            is SwitcherItem.Cmd -> {
                Icon(Icons.Outlined.KeyboardCommandKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(highlightPrefix(item.command.title, query), style = MaterialTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val chord = item.command.chord
                if (chord != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { chord.split('+').forEach { KeyChip(it) } }
                }
            }
        }
    }
}

// ---- the phone's overlay (unchanged in form; the sections are the model, so it gains the Recents)

@Composable
private fun SwitcherOverlay(
    text: String,
    onText: (String) -> Unit,
    sections: List<SwitcherSection>,
    items: List<SwitcherItem>,
    selected: Int,
    query: String,
    mode: SwitcherMode,
    focus: FocusRequester,
    keys: (KeyEvent) -> Boolean,
    onRun: (SwitcherItem) -> Unit,
    onDismiss: () -> Unit,
) {
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
                    onValueChange = onText,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent(keys),
                    singleLine = true,
                    placeholder = { Text("Search pages, or > for a command") },
                )
            }
            if (items.isEmpty() && query.isNotBlank()) {
                if (mode is SwitcherMode.Commands) EmptyState(icon = Icons.Outlined.KeyboardCommandKey, message = "No command matches", modifier = Modifier.fillMaxSize())
                else EmptyState(icon = Icons.Filled.Search, message = "No pages match '$query'", modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn {
                    var index = 0
                    for (section in sections) {
                        item(key = "label:" + section.label) {
                            Text(
                                section.label.uppercase(),
                                style = MaterialTheme.typography.eyebrow,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        for (entry in section.items) {
                            val i = index++
                            item(key = itemKey(entry)) {
                                ResultRow(selected = i == selected, onClick = { onRun(entry) }) {
                                    when (entry) {
                                        is SwitcherItem.Cmd -> {
                                            Icon(Icons.Outlined.KeyboardCommandKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(12.dp))
                                            Text(entry.command.title, style = MaterialTheme.typography.body)
                                        }
                                        is SwitcherItem.Hit -> {
                                            val hitIcon = entry.hit.icon
                                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                                if (hitIcon != null) Text(hitIcon, style = MaterialTheme.typography.heading)
                                                else Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(entry.hit.title, style = MaterialTheme.typography.body)
                                                if (entry.hit.snippet.isNotBlank()) {
                                                    Text(highlightMatches(entry.hit.snippet, MaterialTheme.typography.heading.fontWeight), style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (entry.meta != null) Text(entry.meta, style = MaterialTheme.typography.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ResultRow(selected: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The typed letters at the heading's weight where the title starts with them (Obsidian's rule); the title plain otherwise. */
internal fun highlightPrefix(title: String, query: String, weight: FontWeight? = FontWeight.SemiBold): AnnotatedString {
    val range = prefixRange(title, query) ?: return AnnotatedString(title)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = weight)) { append(title.substring(range)) }
        append(title.substring(range.last + 1))
    }
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
