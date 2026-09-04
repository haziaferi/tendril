@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.LocalDate

@Composable
fun PageDetailScreen(
    core: WorkbenchCore,
    pageId: Long,
    onBack: () -> Unit,
    onOpenPage: (Long) -> Unit,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
) {
    val viewModel: PageDetailViewModel = viewModel(
        key = "page_$pageId",
        factory = viewModelFactory {
            initializer {
                PageDetailViewModel(
                    pageId,
                    core.database.pageDao(),
                    core.database.blockDao(),
                    core.database.tagDao(),
                    core.database.propertyDao(),
                    core.database.propertyValueDao(),
                    core.database.pageDatabaseDao(),
                    core.database.entryDao(),
                    core.resolveEntryUseCase,
                    core.entryScheduleCoordinator,
                    core.pageContentRepository,
                    core.templateManager,
                    core.viewLockState,
                    core.checkboxOnlyState,
                )
            }
        }
    )
    val viewOnly = LocalViewOnly.current
    val hasCheckboxes by viewModel.hasCheckboxes.collectAsState()
    val checkboxOnlyActive by viewModel.isCheckboxOnlyActive.collectAsState()
    // §3.1.2 — "View-Only overrides checkbox-only. If View-Only is on, no page is interactive
    // regardless of its own checkbox-only setting" — folded into one flag every non-checkbox
    // control in this screen's subtree reads via [LocalContentLocked].
    val contentLocked = viewOnly || checkboxOnlyActive
    val page by viewModel.page.collectAsState()
    val blocks by viewModel.blocks.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val rowDatabase by viewModel.rowDatabase.collectAsState()
    val rowProperties by viewModel.rowProperties.collectAsState()
    val rowValues by viewModel.rowValues.collectAsState()
    val rowLinkedEntry by viewModel.rowLinkedEntry.collectAsState()
    val backlinks by viewModel.backlinks.collectAsState()
    var titleField by remember(page?.id) { mutableStateOf(page?.title ?: "") }
    var blockActionSheetFor by remember { mutableStateOf<Block?>(null) }
    // The block plus the definitively-current base content to insert the mention onto — never
    // `block.content` at insert time, which can be one async Room round-trip stale (typing '@'
    // strips it via a launched coroutine, not synchronously) and would duplicate/corrupt text.
    var mentionTarget by remember { mutableStateOf<Pair<Block, String>?>(null) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCheckboxOnlyConfirm by remember { mutableStateOf(false) }

    // §3.1.2 — "turning it back off requires a full device unlock" on Android, via
    // [onCheckboxOnlyUnlockRequest] (real `BiometricPrompt`, supplied by the Android call site —
    // see WorkbenchScaffold's doc). Desktop has no App Lock to gate against (no callback
    // supplied), so there's nothing to unlock — the toggle itself is enough friction there too.
    fun requestTurnOffCheckboxOnly() {
        val request = onCheckboxOnlyUnlockRequest
        if (request != null) {
            request { unlocked -> if (unlocked) viewModel.deactivateCheckboxOnly() }
        } else {
            viewModel.deactivateCheckboxOnly()
        }
    }

    CompositionLocalProvider(LocalContentLocked provides contentLocked) {
    Scaffold(
        topBar = {
            // The editable title lives in the app bar's own title row rather than a
            // second, separately-padded block below it — one compact header, not two
            // stacked ones (an empty app bar followed by a large title field read as
            // "doubled" height for no visual payoff).
            TopAppBar(
                title = {
                    BasicTextField(
                        value = titleField,
                        onValueChange = {
                            titleField = it
                            viewModel.updateTitle(it)
                        },
                        readOnly = contentLocked,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    // The menu trigger itself only ever respects View-Only, never
                    // checkbox-only's own contentLocked — checkbox-only mode's "turn it back
                    // off" item lives inside this same menu and must stay reachable while it's
                    // active, the same way the Pages hub's own eye toggle stays reachable while
                    // View-Only is on.
                    IconButton(onClick = { showMoreMenu = true }, enabled = !viewOnly) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        if (hasCheckboxes) {
                            if (checkboxOnlyActive) {
                                DropdownMenuItem(text = { Text("Turn off checkbox-only") }, onClick = { showMoreMenu = false; requestTurnOffCheckboxOnly() })
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Checkbox-only mode…") },
                                    enabled = !contentLocked,
                                    onClick = { showMoreMenu = false; showCheckboxOnlyConfirm = true },
                                )
                            }
                        }
                        DropdownMenuItem(text = { Text("Save as template") }, enabled = !contentLocked, onClick = { showMoreMenu = false; viewModel.saveAsTemplate() })
                        DropdownMenuItem(text = { Text("Move to Trash") }, enabled = !contentLocked, onClick = { showMoreMenu = false; showDeleteConfirm = true })
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeTag(tag) },
                        enabled = !contentLocked,
                        label = { Text(tag.name) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag", modifier = Modifier.size(16.dp)) },
                    )
                }
                if (!contentLocked) {
                    AssistChip(
                        onClick = { showAddTagDialog = true },
                        label = { Text("Add tag") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // §5.1 Row-as-page — a Database row shows its property values as a compact
                // strip above the same free-form Block body every other Page has.
                if (rowDatabase != null) {
                    items(rowProperties, key = { "prop_${it.id}" }) { property ->
                        RowPropertyEditor(
                            property = property,
                            database = rowDatabase,
                            storedValue = rowValues[property.id]?.value,
                            linkedEntry = rowLinkedEntry,
                            viewModel = viewModel,
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }
                items(blocks, key = { it.id }) { block ->
                    if (block.parentBlockId == null) {
                        BlockRow(
                            block = block,
                            viewModel = viewModel,
                            onLongPress = { blockActionSheetFor = block },
                            onRequestMention = { baseContent -> mentionTarget = block to baseContent },
                            onOpenPage = onOpenPage,
                        )
                    }
                }
                if (!contentLocked) {
                    item {
                        TextButton(onClick = {
                            val lastOrder = blocks.maxOfOrNull { it.order } ?: -1
                            viewModel.addBlock(BlockType.PARAGRAPH, lastOrder)
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add block")
                        }
                    }
                }
                item { BacklinksPanel(backlinks, onOpenPage) }
            }
        }
    }

    blockActionSheetFor?.let { block ->
        BlockActionSheet(
            block = block,
            onDismiss = { blockActionSheetFor = null },
            onMoveUp = { viewModel.moveBlock(block, -1); blockActionSheetFor = null },
            onMoveDown = { viewModel.moveBlock(block, 1); blockActionSheetFor = null },
            onTurnInto = { type -> viewModel.changeType(block, type); blockActionSheetFor = null },
            onDelete = { viewModel.deleteBlock(block); blockActionSheetFor = null },
        )
    }

    if (showAddTagDialog) {
        AddTagDialog(
            viewModel = viewModel,
            onDismiss = { showAddTagDialog = false },
            onPick = { name -> viewModel.addTag(name); showAddTagDialog = false },
        )
    }

    mentionTarget?.let { (block, baseContent) ->
        MentionPickerDialog(
            viewModel = viewModel,
            onDismiss = { mentionTarget = null },
            onPick = { targetPage ->
                val insertion = "@${targetPage.title}"
                val newContent = baseContent + insertion
                val span = FormattingSpan(baseContent.length, newContent.length, SpanStyle.PageMention(targetPage.id))
                viewModel.updateBlockContent(block, newContent, block.formattingSpans + span)
                mentionTarget = null
            },
        )
    }

    if (showDeleteConfirm) {
        // §5.5 — Row deletion requires an explicit confirm dialog; a Row is a Page (§5.1), so
        // this same "···" action covers both a plain Page and a database Row opened here.
        val isRow = rowDatabase != null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (isRow) "Delete \"${page?.title}\"?" else "Move \"${page?.title}\" to Trash?") },
            text = {
                Text(
                    if (isRow && rowLinkedEntry != null) {
                        "This row and its linked Task both move to Trash — restorable from there, not deleted outright."
                    } else {
                        "It moves to Trash — restorable from there, not deleted outright."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.trashPage(onBack) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showCheckboxOnlyConfirm) {
        // §3.1.2 — "The confirm dialog's copy states the concrete consequence in plain words
        // each time," the same instant-write-plus-confirm pattern §5.5 uses elsewhere, not a
        // separate accidental-activation gate.
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCheckboxOnlyConfirm = false },
            title = { Text("Turn on checkbox-only mode?") },
            text = {
                Text(
                    "Only this page's checkboxes stay tappable — nothing else on it can be edited. " +
                        "This page will show over your lock screen while this is on. It turns off " +
                        "automatically when you leave this page, or you can turn it off here, which " +
                        "needs a full unlock.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showCheckboxOnlyConfirm = false; viewModel.activateCheckboxOnly() }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { showCheckboxOnlyConfirm = false }) { Text("Cancel") } },
        )
    }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    viewModel: PageDetailViewModel,
    onLongPress: () -> Unit,
    onRequestMention: (baseContent: String) -> Unit,
    onOpenPage: (Long) -> Unit,
) {
    // Keyed only on block.id, not block.content: every edit round-trips through Room and
    // re-emits this same block via the Flow, and re-keying on content would reset this
    // TextFieldValue (cursor back to 0) on every keystroke — local state leads while typing,
    // the database write is fire-and-forget persistence, not the source of truth mid-edit.
    var fieldValue by remember(block.id) { mutableStateOf(TextFieldValue(block.content)) }
    // The content this field itself last wrote, tracked separately from `fieldValue.text` —
    // an external write (a mention inserted via the picker, which mutates `block.content`
    // from outside this composable's own onValueChange) would otherwise leave the visible
    // field stuck on stale text until the page is closed and reopened, even though Room
    // already has the correct content. Comparing `block.content` against what THIS field
    // last wrote (not against `fieldValue.text`, which can briefly lag Room mid-keystroke)
    // distinguishes "a change I already know about" from "a change that happened elsewhere."
    var lastWrittenContent by remember(block.id) { mutableStateOf(block.content) }
    LaunchedEffect(block.content) {
        if (block.content != lastWrittenContent) {
            fieldValue = TextFieldValue(block.content, selection = TextRange(block.content.length))
            lastWrittenContent = block.content
        }
    }
    var showSlashMenu by remember { mutableStateOf(false) }
    val indent = if (block.parentBlockId != null) 24.dp else 0.dp
    val locked = LocalContentLocked.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp + indent, end = 16.dp, top = 2.dp, bottom = 2.dp)
                .combinedClickable(onClick = {}, onLongClick = if (locked) null else onLongPress),
            verticalAlignment = Alignment.Top,
        ) {
            BlockPrefix(block, viewModel)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (block.type == BlockType.DIVIDER) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else if (block.type != BlockType.PAGE_MENTION) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { newValue ->
                            fieldValue = newValue
                            val text = newValue.text
                            // §3.1.1 — "a slash-command menu (/) to insert any block type at
                            // the cursor." Typing '/' at the end of the block's content opens
                            // the picker; the '/' itself never becomes stored content.
                            if (text.endsWith("/")) {
                                showSlashMenu = true
                                val stripped = text.dropLast(1)
                                fieldValue = fieldValue.copy(text = stripped, selection = TextRange(stripped.length))
                                lastWrittenContent = stripped
                                viewModel.updateBlockContent(block, stripped, remapSpans(block.formattingSpans, block.content, stripped))
                            } else if (text.endsWith("@")) {
                                // Previously the only entry point to a mention was selecting
                                // text first and tapping the toolbar's @ icon — not discoverable,
                                // and the reason Road Map read as empty in practice. Typing '@'
                                // now opens the picker directly, the same way '/' opens the
                                // slash menu; the '@' itself never becomes stored content either.
                                val stripped = text.dropLast(1)
                                fieldValue = fieldValue.copy(text = stripped, selection = TextRange(stripped.length))
                                lastWrittenContent = stripped
                                viewModel.updateBlockContent(block, stripped, remapSpans(block.formattingSpans, block.content, stripped))
                                onRequestMention(stripped)
                            } else {
                                lastWrittenContent = text
                                viewModel.updateBlockContent(block, text, remapSpans(block.formattingSpans, block.content, text))
                            }
                        },
                        textStyle = blockTextStyle(block.type).copy(color = MaterialTheme.colorScheme.onSurface),
                        visualTransformation = spansVisualTransformation(block.formattingSpans),
                        readOnly = locked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Surface(
                        onClick = { block.mentionedPageId?.let(onOpenPage) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(block.content, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (!locked && fieldValue.selection.length > 0) {
                    FormattingToolbar(
                        onApply = { style ->
                            val range = fieldValue.selection
                            val span = FormattingSpan(range.min, range.max, style)
                            viewModel.updateBlockContent(block, fieldValue.text, block.formattingSpans + span)
                        },
                        onMention = { onRequestMention(fieldValue.text) },
                    )
                }

                if (block.type == BlockType.TOGGLE && block.toggleExpanded) {
                    // Child blocks (one level of nesting, §3.1.1) render via the parent
                    // LazyColumn's flat list filtered by parentBlockId in a real nested
                    // pass — kept out of this MVP render pass since no UI path creates
                    // toggle children yet (§3.1.1's nesting is list items primarily).
                }
            }
        }
    }

    if (showSlashMenu) {
        SlashCommandSheet(
            onDismiss = { showSlashMenu = false },
            onPick = { type ->
                showSlashMenu = false
                viewModel.addBlock(type, block.order)
            },
        )
    }
}

@Composable
private fun SlashCommandSheet(onDismiss: () -> Unit, onPick: (BlockType) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text("Insert block", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            listOf(
                BlockType.PARAGRAPH to "Paragraph", BlockType.HEADING_1 to "Heading 1", BlockType.HEADING_2 to "Heading 2",
                BlockType.HEADING_3 to "Heading 3", BlockType.BULLETED_LIST_ITEM to "Bulleted list",
                BlockType.NUMBERED_LIST_ITEM to "Numbered list", BlockType.TODO to "To-do", BlockType.QUOTE to "Quote",
                BlockType.CODE to "Code", BlockType.TOGGLE to "Toggle", BlockType.CALLOUT to "Callout", BlockType.DIVIDER to "Divider",
                BlockType.IMAGE to "Image",
            ).forEach { (type, label) ->
                TextButton(onClick = { onPick(type) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun BlockPrefix(block: Block, viewModel: PageDetailViewModel) {
    // §3.1.2 — the to-do checkbox is the one control on this page that checkbox-only mode
    // deliberately leaves tappable, so it reads the raw View-Only flag, not [LocalContentLocked]
    // (which also trips while checkbox-only is active for this page).
    val viewOnly = LocalViewOnly.current
    val contentLocked = LocalContentLocked.current
    when (block.type) {
        BlockType.TODO -> Checkbox(
            checked = block.checked == true,
            onCheckedChange = { viewModel.setChecked(block, it) },
            enabled = !viewOnly,
            modifier = Modifier.size(24.dp),
        )
        BlockType.BULLETED_LIST_ITEM -> Text("•", modifier = Modifier.width(20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        BlockType.NUMBERED_LIST_ITEM -> Text("${block.order + 1}.", modifier = Modifier.width(20.dp))
        BlockType.QUOTE -> Box(modifier = Modifier.width(3.dp).height(20.dp).background(MaterialTheme.colorScheme.outline))
        BlockType.TOGGLE -> IconButton(
            onClick = { viewModel.setToggleExpanded(block, !block.toggleExpanded) },
            enabled = !contentLocked,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(if (block.toggleExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, contentDescription = "Toggle")
        }
        BlockType.CALLOUT -> Text(block.calloutIcon ?: "💡", modifier = Modifier.width(24.dp))
        BlockType.CODE -> Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(20.dp))
        else -> Spacer(Modifier.width(0.dp))
    }
}

/** Derives every block's text style from the app's own type scale (§2.3's Sans/Serif
 * choice, theme-driven line-height/letter-spacing) rather than hardcoded `sp` literals —
 * those wouldn't track the chosen typeface's metrics or the system font-scale setting
 * (§2.4) the rest of the app already respects by going through `MaterialTheme.typography`. */
@Composable
private fun blockTextStyle(type: BlockType): androidx.compose.ui.text.TextStyle {
    val typography = MaterialTheme.typography
    return when (type) {
        BlockType.HEADING_1 -> typography.headlineMedium
        BlockType.HEADING_2 -> typography.headlineSmall
        BlockType.HEADING_3 -> typography.titleLarge
        BlockType.QUOTE -> typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        BlockType.CODE -> typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        else -> typography.bodyLarge
    }
}

/**
 * Spans are (start, end) indices into the block's *old* plain text, so any edit that isn't a
 * pure append has to move them. `BasicTextField` hands over only the new string, not where the
 * change happened, so the edited region is recovered by trimming the common prefix and common
 * suffix — that bracket always contains the real edit, which is all the remap needs.
 *
 * The previous version's first branch (`span.end <= oldText.length && oldText.length <=
 * newText.length`) was true for essentially every insertion, so *no* span ever moved: inserting
 * at the start of a block left every span pointing at the wrong characters, silently
 * re-formatting the wrong text rather than the "dropped rather than corrupted" behaviour the
 * comment promised. Deletions shifted `end` while leaving `start` put, stretching spans.
 */
private fun remapSpans(spans: List<FormattingSpan>, oldText: String, newText: String): List<FormattingSpan> {
    if (spans.isEmpty() || oldText == newText) return spans

    val maxShared = minOf(oldText.length, newText.length)
    var prefix = 0
    while (prefix < maxShared && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (suffix < maxShared - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++

    val editEnd = oldText.length - suffix
    val delta = newText.length - oldText.length

    return spans.mapNotNull { span ->
        when {
            // Entirely before the edit — untouched. Also the append-while-typing case, where
            // the edit starts at the end of the text and every existing span ends before it.
            span.end <= prefix -> span
            // Entirely after the edit — slides by the length change.
            span.start >= editEnd -> span.copy(start = span.start + delta, end = span.end + delta)
            // The edit happened strictly inside the span: typing inside a bold run extends it.
            span.start <= prefix && span.end >= editEnd -> span.copy(end = span.end + delta)
            // The edit straddles one of the span's boundaries — where it should now start or
            // stop is genuinely ambiguous, so drop it rather than guess.
            else -> null
        }
    }.filter { it.start >= 0 && it.end <= newText.length && it.start < it.end }
}

@Composable
private fun FormattingToolbar(onApply: (SpanStyle) -> Unit, onMention: () -> Unit) {
    Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onApply(SpanStyle.Bold) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onApply(SpanStyle.Italic) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onApply(SpanStyle.Strikethrough) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Strikethrough", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { onApply(SpanStyle.InlineCode) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Code, contentDescription = "Inline code", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMention, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.AlternateEmail, contentDescription = "Mention a page", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BlockActionSheet(
    block: Block,
    onDismiss: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTurnInto: (BlockType) -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            SheetActionRow(Icons.Filled.ArrowUpward, "Move up", onMoveUp)
            SheetActionRow(Icons.Filled.ArrowDownward, "Move down", onMoveDown)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Turn into", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
            listOf(
                BlockType.PARAGRAPH to "Paragraph", BlockType.HEADING_1 to "Heading 1", BlockType.HEADING_2 to "Heading 2",
                BlockType.HEADING_3 to "Heading 3", BlockType.BULLETED_LIST_ITEM to "Bulleted list",
                BlockType.NUMBERED_LIST_ITEM to "Numbered list", BlockType.TODO to "To-do", BlockType.QUOTE to "Quote",
                BlockType.CODE to "Code", BlockType.TOGGLE to "Toggle", BlockType.CALLOUT to "Callout", BlockType.DIVIDER to "Divider",
            ).forEach { (type, label) ->
                TextButton(onClick = { onTurnInto(type) }) { Text(label) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SheetActionRow(Icons.Filled.Delete, "Delete", onDelete)
        }
    }
}

@Composable
private fun SheetActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun AddTagDialog(viewModel: PageDetailViewModel, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val candidates by viewModel.tagCandidates.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add tag", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchTagCandidates(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            )
            if (query.isNotBlank() && candidates.none { it.name.equals(query.trim(), ignoreCase = true) }) {
                TextButton(onClick = { onPick(query) }) { Text("Create \"$query\"") }
            }
            LazyColumn {
                items(candidates, key = { it.id }) { candidate ->
                    Text(
                        candidate.name,
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onPick(candidate.name) }).padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionPickerDialog(viewModel: PageDetailViewModel, onDismiss: () -> Unit, onPick: (com.tendril.app.data.page.Page) -> Unit) {
    var query by remember { mutableStateOf("") }
    val candidates by viewModel.mentionCandidates.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // A fraction of the current screen's height, not a flat dp figure — stays
        // proportionate from small phones to tablets rather than over/under-filling.
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mention a page", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchForMention(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            )
            LazyColumn {
                items(candidates, key = { it.id }) { candidate ->
                    Text(
                        candidate.title,
                        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onPick(candidate) }).padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** §3.1.5 — "a collapsed-by-default 'Linked mentions' section at the bottom of every page."
 * Nothing rendered at all when there are no backlinks, rather than an empty collapsed header
 * — no reason to advertise a section with nothing behind it. */
@Composable
private fun BacklinksPanel(backlinks: List<Backlink>, onOpenPage: (Long) -> Unit) {
    if (backlinks.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { expanded = !expanded }).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Linked mentions (${backlinks.size})", style = MaterialTheme.typography.labelLarge)
        }
        if (expanded) {
            backlinks.forEach { backlink ->
                Column(
                    modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenPage(backlink.fromPage.id) }).padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(backlink.fromPage.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        backlink.block.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** §5.1 Row-as-page property strip — one row per property, name on the left, an editor
 * matching the table view's own cell behavior on the right (a bound role edits through the
 * linked Entry, everything else edits the stored [PropertyValue] directly). */
@Composable
private fun RowPropertyEditor(
    property: Property,
    database: PageDatabase?,
    storedValue: String?,
    linkedEntry: Entry?,
    viewModel: PageDetailViewModel,
) {
    val locked = LocalContentLocked.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(property.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (property.id) {
                database?.donePropertyId -> Checkbox(
                    checked = linkedEntry?.status == EntryStatus.DONE,
                    onCheckedChange = { viewModel.toggleRowDone(it) },
                    enabled = !locked,
                )
                database?.deadlinePropertyId -> RowDeadlineEditor(linkedEntry, viewModel)
                database?.recurrencePropertyId -> RowRecurrenceEditor(linkedEntry, viewModel)
                else -> RowUnboundEditor(property, storedValue, viewModel)
            }
        }
    }
}

@Composable
private fun RowDeadlineEditor(entry: Entry?, viewModel: PageDetailViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    val locked = LocalContentLocked.current
    Text(entry?.startDate?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.combinedClickable(onClick = { if (entry != null && !locked) showPicker = true }))
    if (showPicker && entry != null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (entry.startDate ?: LocalDate.now()).toDatePickerMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.setRowDeadline(datePickerMillisToLocalDate(it)) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun RowRecurrenceEditor(entry: Entry?, viewModel: PageDetailViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    val locked = LocalContentLocked.current
    val rule = entry?.recurrenceRule as? RecurrenceRule.Elastic
    Text(rule?.period?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.combinedClickable(onClick = { if (entry != null && !locked) showPicker = true }))
    if (showPicker && entry != null) {
        var countText by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf(IntervalUnit.WEEK) }
        var showUnitMenu by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text("Repeat every", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.width(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box {
                        TextButton(onClick = { showUnitMenu = true }) { Text(unit.name.lowercase() + "(s)") }
                        DropdownMenu(expanded = showUnitMenu, onDismissRequest = { showUnitMenu = false }) {
                            IntervalUnit.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.name.lowercase()) }, onClick = { unit = option; showUnitMenu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { countText.toIntOrNull()?.let { viewModel.setRowRecurrence(it, unit) }; showPicker = false }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun RowUnboundEditor(property: Property, storedValue: String?, viewModel: PageDetailViewModel) {
    val locked = LocalContentLocked.current
    when (property.type) {
        PropertyType.CHECKBOX -> Checkbox(checked = storedValue == "true", onCheckedChange = { viewModel.setRowPropertyValue(property, it.toString()) }, enabled = !locked)
        PropertyType.DATE -> {
            var showPicker by remember { mutableStateOf(false) }
            Text(storedValue ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.combinedClickable(onClick = { if (!locked) showPicker = true }))
            if (showPicker) {
                val initial = storedValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                val state = rememberDatePickerState(initialSelectedDateMillis = initial.toDatePickerMillis())
                DatePickerDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { viewModel.setRowPropertyValue(property, datePickerMillisToLocalDate(it).toString()) }
                            showPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
                ) { DatePicker(state = state) }
            }
        }
        PropertyType.SELECT -> {
            var showMenu by remember { mutableStateOf(false) }
            val options = property.config?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            Box {
                Text(storedValue ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.combinedClickable(onClick = { if (!locked) showMenu = true }))
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { viewModel.setRowPropertyValue(property, option); showMenu = false })
                    }
                }
            }
        }
        PropertyType.MULTI_SELECT -> {
            var showMenu by remember { mutableStateOf(false) }
            val options = property.config?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val selected = storedValue?.split(",")?.filter { it.isNotBlank() }.orEmpty().toSet()
            Box {
                Text(if (selected.isEmpty()) "—" else selected.joinToString(", "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.combinedClickable(onClick = { if (!locked) showMenu = true }))
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text((if (option in selected) "✓ " else "") + option) },
                            onClick = {
                                val newSelected = if (option in selected) selected - option else selected + option
                                viewModel.setRowPropertyValue(property, newSelected.joinToString(","))
                            },
                        )
                    }
                }
            }
        }
        else -> {
            var text by remember(storedValue) { mutableStateOf(storedValue ?: "") }
            BasicTextField(
                value = text,
                onValueChange = { text = it; viewModel.setRowPropertyValue(property, it) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                readOnly = locked,
                singleLine = true,
            )
        }
    }
}
