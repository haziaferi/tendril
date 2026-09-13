@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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
import com.tendril.app.ui.components.TendrilSheet
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
import com.tendril.app.data.pagedatabase.formatPeriodAsHumanInterval
import com.tendril.app.domain.indentTargetFor
import com.tendril.app.domain.OutlineBlock
import com.tendril.app.domain.outlineOf
import com.tendril.app.domain.references.UnlinkedMention
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.components.datePickerMillisToLocalDate
import com.tendril.app.ui.components.toDatePickerMillis
import java.time.LocalDate
import com.tendril.app.domain.BindingRole

@Composable
fun PageDetailScreen(
    core: WorkbenchCore,
    pageId: Long,
    onBack: () -> Unit,
    onOpenPage: (Long) -> Unit,
    onCheckboxOnlyUnlockRequest: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
    /** §3.4 (step 8c) — "Show on Road Map" in `···`: the map, focused on this page. */
    onShowOnRoadMap: (Long) -> Unit = {},
) {
    val viewModel: PageDetailViewModel = viewModel(
        key = "page_$pageId",
        factory = viewModelFactory {
            initializer {
                PageDetailViewModel(
                    pageId,
                    core.database.pageDao(),
                    core.database.blockDao(),
                    core.database.labelDao(),
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
                    core.localImages,
                    core.labelMembership,
                    core.database.habitDao(),
                    core.checkInHabitUseCase,
                    core.pageHistory,
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
    // §3.1.1 — the drawn order, with children under their parents. Recomputed only when the
    // block list itself changes, not on every recomposition.
    val outline = remember(blocks) { outlineOf(blocks) }
    val labels by viewModel.labels.collectAsState()
    val rowDatabase by viewModel.rowDatabase.collectAsState()
    val memberships by viewModel.memberships.collectAsState()
    val boundLabelIds by viewModel.boundLabelIds.collectAsState()
    val pendingLabel by viewModel.pendingLabel.collectAsState()
    val rowValues by viewModel.rowValues.collectAsState()
    val rowLinkedEntry by viewModel.rowLinkedEntry.collectAsState()
    LaunchedEffect(pageId) { viewModel.onOpened() }
    val backlinks by viewModel.backlinks.collectAsState()
    val unlinkedMentions by viewModel.unlinkedMentions.collectAsState()
    val journalToday by viewModel.journalToday.collectAsState()
    var titleField by remember(page?.id) { mutableStateOf(page?.title ?: "") }
    var blockActionSheetFor by remember { mutableStateOf<Block?>(null) }
    // §0.6.2 / B§9.6 — the armed map: the block whose subtree fills the viewport, or null.
    var armedMapRoot by remember { mutableStateOf<Long?>(null) }
    // §0.6.3 — the armed canvas: the Canvas page filling the viewport, or null.
    var armedCanvasPage by remember { mutableStateOf<Long?>(null) }
    // The slash menu's "Canvas" needs a second choice — which canvas — before it can insert.
    var canvasPickerAfterOrder by remember { mutableStateOf<Int?>(null) }
    // The block plus the definitively-current base content to insert the mention onto — never
    // `block.content` at insert time, which can be one async Room round-trip stale (typing '@'
    // strips it via a launched coroutine, not synchronously) and would duplicate/corrupt text.
    var mentionTarget by remember { mutableStateOf<Pair<Block, String>?>(null) }
    /** §0.6.12 — the block a reference is being inserted after (`((` or the slash sheet). */
    var blockReferenceAfter by remember { mutableStateOf<Block?>(null) }
    /** §0.6.13 — the History sheet. */
    var showHistory by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCheckboxOnlyConfirm by remember { mutableStateOf(false) }
    var checkboxOnlyRefused by remember { mutableStateOf(false) }

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
                        DropdownMenuItem(text = { Text("Show on Road Map") }, onClick = { showMoreMenu = false; onShowOnRoadMap(pageId) })
                        DropdownMenuItem(text = { Text("History") }, onClick = { showMoreMenu = false; showHistory = true })
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
                labels.forEach { label ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeLabel(label) },
                        enabled = !contentLocked,
                        label = { Text(label.name) },
                        // §0.6.8 / B§12.0 — "a bound label looks like any other, with a small mark
                        // that it brings fields."
                        leadingIcon = if (label.id in boundLabelIds) {
                            { Icon(Icons.Filled.TableChart, contentDescription = "Brings a database's fields", modifier = Modifier.size(14.dp)) }
                        } else null,
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove label", modifier = Modifier.size(16.dp)) },
                    )
                }
                if (!contentLocked) {
                    AssistChip(
                        onClick = { showAddLabelDialog = true },
                        label = { Text("Add label") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            // §0.6.8 — a membership strip is inserted *above* the first block, and a LazyColumn
            // keeps its first visible item where it was, so the fields a person just gained by
            // labelling the page would appear scrolled out of sight. Show them.
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            var membershipCount by remember { mutableStateOf(-1) }
            LaunchedEffect(memberships.size) {
                if (membershipCount in 0 until memberships.size) listState.scrollToItem(0)
                membershipCount = memberships.size
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // §3.1.4 (amended, step 8b) — today's Journal page opens with the day: its tasks
                // and due habits, checkable, above everything else. Null on every other page.
                journalToday?.let { (date, today) -> journalTodayItems(today, date, viewModel) }
                // §5.1 Row-as-page — a Database row shows its property values as a compact
                // strip above the same free-form Block body every other Page has. §0.6.8 — one
                // strip per membership: the home database first, then each database a label
                // opened. A lone native strip is untitled, as it always was; a labelled one says
                // which database and which label, since the page's place in the tree no longer
                // tells the reader.
                memberships.forEach { membership ->
                    val db = membership.database
                    if (membership.viaLabel != null || memberships.size > 1) {
                        item(key = "member_${db.id}") {
                            Text(
                                buildString {
                                    append(membershipTitle(membership.database.pageId, viewModel))
                                    membership.viaLabel?.let { append("  ·  #").append(it.name) }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(membership.properties, key = { "prop_${db.id}_${it.id}" }) { property ->
                        RowPropertyEditor(
                            property = property,
                            database = db,
                            storedValue = rowValues[property.id]?.value,
                            linkedEntry = rowLinkedEntry,
                            viewModel = viewModel,
                        )
                    }
                    item(key = "member_end_${db.id}") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }
                // Children used to be filtered out here (`if (block.parentBlockId == null)`),
                // which is why nesting existed in the schema but never on screen.
                // §0.6.2 — a block shown as a mind map keeps its own row and replaces its subtree
                // with the inert card; the descendants are still in `outline`, just not drawn as
                // rows. `mappedAway` is that set, computed once per outline.
                val mappedAway = mappedDescendants(outline)
                items(outline.filter { it.block.id !in mappedAway }, key = { it.block.id }) { entry ->
                    BlockRow(
                        block = entry.block,
                        depth = entry.depth,
                        listPosition = entry.listPosition,
                        viewModel = viewModel,
                        onLongPress = { blockActionSheetFor = entry.block },
                        onRequestMention = { baseContent -> mentionTarget = entry.block to baseContent },
                        onRequestBlockReference = { blockReferenceAfter = entry.block },
                        onOpenPage = onOpenPage,
                        core = core,
                        onArmCanvas = { armedCanvasPage = it },
                        onInsertCanvas = { afterOrder -> canvasPickerAfterOrder = afterOrder },
                    )
                    if (entry.block.mindMap) {
                        MindMapCard(subtree = subtreeOf(outline, entry.block.id), onArm = { armedMapRoot = entry.block.id })
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
                item { BacklinksPanel(backlinks, unlinkedMentions, onOpenPage, onLink = { viewModel.link(it) }) }
            }
        }
    }

    blockActionSheetFor?.let { block ->
        BlockActionSheet(
            block = block,
            onDismiss = { blockActionSheetFor = null },
            onMoveUp = { viewModel.moveBlock(block, -1); blockActionSheetFor = null },
            onMoveDown = { viewModel.moveBlock(block, 1); blockActionSheetFor = null },
            canIndent = indentTargetFor(block, blocks) != null,
            canOutdent = block.parentBlockId != null,
            onIndent = { viewModel.indentBlock(block); blockActionSheetFor = null },
            onOutdent = { viewModel.outdentBlock(block); blockActionSheetFor = null },
            // Dismisses on pick like every other row here — `block` is a snapshot captured
            // when the sheet opened, not a live reference, so leaving the sheet open would
            // show a selection ring that never moves to the newly picked swatch/language.
            onSetLanguage = { language -> viewModel.setCodeLanguage(block, language); blockActionSheetFor = null },
            onSetCalloutColor = { color -> viewModel.setCalloutColor(block, color); blockActionSheetFor = null },
            onTurnInto = { type -> viewModel.changeType(block, type); blockActionSheetFor = null },
            onDelete = { viewModel.deleteBlock(block); blockActionSheetFor = null },
            mindMap = block.mindMap,
            onToggleMindMap = { viewModel.setMindMap(block, !block.mindMap); blockActionSheetFor = null },
        )
    }

    canvasPickerAfterOrder?.let { afterOrder ->
        CanvasPickerSheet(
            core = core,
            onPickExisting = { id -> viewModel.insertCanvasBlock(afterOrder, id); canvasPickerAfterOrder = null },
            onCreate = { title -> viewModel.createCanvasAndInsert(afterOrder, title); canvasPickerAfterOrder = null },
            onDismiss = { canvasPickerAfterOrder = null },
        )
    }

    // §0.6.3 / B§9.6 — armed: the real board, the whole screen's `CanvasScreen`, over the page.
    // Its own back arrow disarms; so does the system back gesture.
    armedCanvasPage?.let { canvasPageId ->
        MapBackHandler { armedCanvasPage = null }
        com.tendril.app.ui.canvas.CanvasScreen(
            core = core,
            pageId = canvasPageId,
            onBack = { armedCanvasPage = null },
            onOpenPage = onOpenPage,
        )
    }

    // §0.6.2 / B§9.6 — armed: the same map grown to fill the viewport, over the page. Drawn last
    // so it sits above everything, including the sheets; closing it is the only way out, and the
    // block list beneath is untouched by anything that happened inside it except through the
    // ordinary block edits the map performs.
    armedMapRoot?.let { rootId ->
        val subtree = subtreeOf(outline, rootId)
        if (subtree.isEmpty()) {
            armedMapRoot = null
        } else {
            MapBackHandler { armedMapRoot = null }
            MindMapFullScreen(
                title = subtree.first().block.content.ifBlank { "Mind map" },
                subtree = subtree,
                locked = contentLocked,
                onClose = { armedMapRoot = null },
                onEditText = { b, text -> viewModel.updateBlockContent(b, text) },
                onAddChild = { parent, text -> viewModel.addBlockUnder(parent, text) },
                onDelete = { b -> viewModel.deleteBlock(b) },
            )
        }
    }

    if (showAddLabelDialog) {
        AddLabelDialog(
            viewModel = viewModel,
            onDismiss = { showAddLabelDialog = false },
            onPick = { name -> viewModel.addLabel(name); showAddLabelDialog = false },
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

    if (showHistory) {
        HistorySheet(viewModel = viewModel, contentLocked = contentLocked, onDismiss = { showHistory = false })
    }

    blockReferenceAfter?.let { after ->
        BlockReferencePickerDialog(
            viewModel = viewModel,
            onDismiss = { blockReferenceAfter = null },
            onPick = { source -> viewModel.insertBlockReference(after.order, source); blockReferenceAfter = null },
        )
    }

    // §0.6.8 — the once-only question, asked where the label is applied.
    pendingLabel?.let { pending ->
        val databaseTitle = membershipTitle(pending.database.pageId, viewModel)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissPendingLabel() },
            title = { Text("Pages labelled #${pending.label.name} become tasks") },
            text = {
                Text(
                    "$databaseTitle syncs to Tasks, and this label makes a page one of its rows — so this page becomes a task, and so will any other page given #${pending.label.name}. Removing the label sends the task to Trash. This is asked once.",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmPendingLabel() }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissPendingLabel() }) { Text("Cancel") } },
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
                TextButton(onClick = {
                    showCheckboxOnlyConfirm = false
                    // Refused when App Lock is on (audit 4.3). Saying so beats a button that
                    // looks like it worked and did nothing.
                    if (!viewModel.activateCheckboxOnly()) checkboxOnlyRefused = true
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { showCheckboxOnlyConfirm = false }) { Text("Cancel") } },
        )
    }

    if (checkboxOnlyRefused) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { checkboxOnlyRefused = false },
            title = { Text("App Lock is on") },
            text = {
                Text(
                    "Checkbox-only mode shows this page over your lock screen, which would step " +
                        "around the App Lock you have turned on. Turn App Lock off in Settings first " +
                        "if you want this page reachable without unlocking.",
                )
            },
            confirmButton = { TextButton(onClick = { checkboxOnlyRefused = false }) { Text("OK") } },
        )
    }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    depth: Int,
    listPosition: Int,
    viewModel: PageDetailViewModel,
    onLongPress: () -> Unit,
    core: WorkbenchCore? = null,
    onArmCanvas: (Long) -> Unit = {},
    onInsertCanvas: (Int) -> Unit = {},
    onRequestMention: (baseContent: String) -> Unit,
    onRequestBlockReference: () -> Unit = {},
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
    // Driven by the outline's computed depth rather than by `parentBlockId != null`, so a
    // grandchild re-attached to its top-level ancestor indents once, not twice.
    // §0.10 item 8, resolved with §0.6.1: a full step for the first six levels and a small one
    // after, so a deep branch stays readable on a phone instead of walking off the right edge.
    // Six is where a 24dp step has spent a third of a narrow screen; past it the eye has the
    // shape already and needs only to see that the line is deeper still.
    val indent = (24 * minOf(depth, 6) + 8 * maxOf(depth - 6, 0)).dp
    val locked = LocalContentLocked.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp + indent, end = 16.dp, top = 2.dp, bottom = 2.dp)
                .then(
                    // §P3 — a callout always has a tinted background, even before a swatch is
                    // chosen, matching the "icon + colored background" design §3.1.1 called for.
                    if (block.type == BlockType.CALLOUT) {
                        Modifier
                            .background(calloutBackgroundColor(block.calloutColor), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(onClick = {}, onLongClick = if (locked) null else onLongPress),
            verticalAlignment = Alignment.Top,
        ) {
            BlockPrefix(block, listPosition, viewModel)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (block.type == BlockType.DIVIDER) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else if (block.type == BlockType.CANVAS) {
                    if (core != null) {
                        CanvasBlockCard(core, block.mentionedPageId, fallbackTitle = block.content, onArm = { block.mentionedPageId?.let(onArmCanvas) })
                    }
                } else if (block.type == BlockType.BLOCK_REFERENCE) {
                    BlockReferenceCard(core, block, onOpenPage)
                } else if (block.type != BlockType.PAGE_MENTION) {
                    // §P1 — free-form, matching `Block.codeLanguage`'s own shape (the Notion
                    // importer stores a fence tag verbatim); "Plain text" is `null`, not "".
                    // §3.1.1 / P2 — the picture, above its caption. IMAGE keeps the text field
                    // below: the Notion importer stores an image block's alt text as its content,
                    // and a block that drew the image *instead* of the field would make that
                    // uneditable and invisible at once.
                    if (block.type == BlockType.IMAGE) {
                        BlockImage(block.imagePath)
                        // §3.1.1's "Image" block was in the slash menu with no way to put a
                        // picture in it. Gated by the View-Only lock like every other write on
                        // this screen; "Replace" rather than a second Choose, because the file is
                        // named after the block and a new pick overwrites in place.
                        if (!LocalViewOnly.current) {
                            val pickImage = rememberImagePicker { fileName, bytes ->
                                viewModel.setBlockImage(block, fileName, bytes)
                            }
                            TextButton(onClick = pickImage) {
                                Text(if (block.imagePath == null) "Choose image" else "Replace image")
                            }
                        }
                    }
                    if (block.type == BlockType.CODE) {
                        Text(
                            block.codeLanguage?.takeIf { it.isNotBlank() } ?: "Plain text",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                            } else if (text.endsWith("((")) {
                                // §0.6.12 — Logseq's `((` opens the block-reference picker, the
                                // way '@' opens the page picker; the two parentheses are never
                                // stored either.
                                val stripped = text.dropLast(2)
                                fieldValue = fieldValue.copy(text = stripped, selection = TextRange(stripped.length))
                                lastWrittenContent = stripped
                                viewModel.updateBlockContent(block, stripped, remapSpans(block.formattingSpans, block.content, stripped))
                                onRequestBlockReference()
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

                // Children, at any depth (§0.6.1), are emitted by `outlineOf` into the same
                // LazyColumn, immediately after this block — a collapsed toggle simply has none
                // emitted. Nothing to render here.

            }
        }
    }

    if (showSlashMenu) {
        SlashCommandSheet(
            onDismiss = { showSlashMenu = false },
            onPick = { type ->
                showSlashMenu = false
                when (type) {
                    BlockType.CANVAS -> onInsertCanvas(block.order)
                    BlockType.BLOCK_REFERENCE -> onRequestBlockReference()
                    else -> viewModel.addBlock(type, block.order)
                }
            },
        )
    }
}

@Composable
private fun SlashCommandSheet(onDismiss: () -> Unit, onPick: (BlockType) -> Unit) {
    // Scrollable, because the list is taller than the sheet. Without this the `Column`
    // simply clipped whatever did not fit, and what did not fit was the last entry --
    // "Image". The type was in this list all along and could not be picked, which is how
    // §3.1.1's Image block came to be "offered" and yet impossible to insert.
    TendrilSheet(title = "Insert block", onDismiss = onDismiss, modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column {
            listOf(
                BlockType.PARAGRAPH to "Paragraph", BlockType.HEADING_1 to "Heading 1", BlockType.HEADING_2 to "Heading 2",
                BlockType.HEADING_3 to "Heading 3", BlockType.BULLETED_LIST_ITEM to "Bulleted list",
                BlockType.NUMBERED_LIST_ITEM to "Numbered list", BlockType.TODO to "To-do", BlockType.QUOTE to "Quote",
                BlockType.CODE to "Code", BlockType.TOGGLE to "Toggle", BlockType.CALLOUT to "Callout", BlockType.DIVIDER to "Divider",
                BlockType.IMAGE to "Image", BlockType.CANVAS to "Canvas", BlockType.BLOCK_REFERENCE to "Block reference",
            ).forEach { (type, label) ->
                TextButton(onClick = { onPick(type) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun BlockPrefix(block: Block, listPosition: Int, viewModel: PageDetailViewModel) {
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
        // §B7 — was `block.order + 1`: this block's position among every block on the page, not
        // its position within the numbered run it visually belongs to. `listPosition` comes from
        // `outlineOf`, which resets it at the start of each run (`BlockOutline.kt`'s own note).
        BlockType.NUMBERED_LIST_ITEM -> Text("$listPosition.", modifier = Modifier.width(20.dp))
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
 * Do not regress this to a plain `end + delta` shift: that silently re-formats the wrong
 * characters on any edit that isn't an append.
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
        // A deletion that removes exactly a span's interior leaves start == end; the other
        // branches can't produce an out-of-range or empty span.
    }.filter { it.start < it.end }
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

/** §P1 — a fixed preset list rather than free text entry: no syntax highlighting depends on
 * this (§3.1.1 — "no syntax highlighting required for v1"), so it only needs to round-trip
 * with the Notion importer's fence-tag strings (`NotionMarkdownParser`), and a short tap
 * list is faster than typing on every device. Lowercase to match the importer's own tags. */
private val CODE_LANGUAGES = listOf(
    "kotlin", "java", "swift", "python", "javascript", "typescript",
    "bash", "sql", "json", "yaml", "html", "css", "c", "cpp", "csharp", "go", "rust", "ruby", "php", "markdown",
)

/** §P3 — a small fixed palette, the same shape as [com.tendril.app.data.page.LabelColors]'s,
 * rather than a full color picker; picked for card-style backgrounds (readable text over
 * them at full opacity), unlike the label palette's mid-tone hues meant to be their own swatch. */
private val CALLOUT_COLORS = listOf(
    "#FDE68A", "#BFDBFE", "#BBF7D0", "#FBCFE8", "#DDD6FE", "#FED7AA", "#E5E7EB",
)

private fun calloutBackgroundColor(stored: String?): Color = parseHexColor(stored ?: CALLOUT_COLORS.first())

/** Parses a "#RRGGBB" hex string into a Compose [Color]. `android.graphics.Color.parseColor`
 * is Android-only and this file is `commonMain` (Android + desktop JVM both render it), so
 * this is hand-rolled rather than reused from a platform API. */
private fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val argb = if (clean.length == 6) "FF$clean" else clean
    return Color(argb.toLong(16).toInt())
}

@Composable
private fun BlockActionSheet(
    block: Block,
    onDismiss: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canIndent: Boolean,
    canOutdent: Boolean,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onSetLanguage: (String?) -> Unit,
    onSetCalloutColor: (String) -> Unit,
    onTurnInto: (BlockType) -> Unit,
    onDelete: () -> Unit,
    mindMap: Boolean = false,
    onToggleMindMap: () -> Unit = {},
) {
    TendrilSheet(onDismiss = onDismiss) {
        Column {
            SheetActionRow(Icons.Filled.ArrowUpward, "Move up", onMoveUp)
            SheetActionRow(Icons.Filled.ArrowDownward, "Move down", onMoveDown)
            // §3.1.1 — one level, so each is offered only where it would actually do something:
            // nothing to tuck under, or already tucked under, and the row is simply absent.
            if (canIndent) SheetActionRow(Icons.Filled.FormatIndentIncrease, "Indent", onIndent)
            if (canOutdent) SheetActionRow(Icons.Filled.FormatIndentDecrease, "Outdent", onOutdent)
            // §0.6.2 — the subtree as a map, or back to rows. Offered on every block: a block
            // with no children yet becomes a one-node map whose first act is "add child".
            SheetActionRow(Icons.Filled.AccountTree, if (mindMap) "Show as list" else "Show as mind map", onToggleMindMap)
            // §P1 — free-form language, same shape the Notion importer already stores.
            if (block.type == BlockType.CODE) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Language", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                val currentLanguage = block.codeLanguage?.takeIf { it.isNotBlank() }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = currentLanguage == null, onClick = { onSetLanguage(null) }, label = { Text("Plain text") })
                    CODE_LANGUAGES.forEach { language ->
                        FilterChip(selected = currentLanguage == language, onClick = { onSetLanguage(language) }, label = { Text(language) })
                    }
                }
            }

            // §P3 — a fixed swatch row rather than a full color picker; matches Label's own
            // small-fixed-palette choice (`LabelColors`) rather than introducing a second,
            // heavier color-picking pattern for one field.
            if (block.type == BlockType.CALLOUT) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                val currentColor = block.calloutColor ?: CALLOUT_COLORS.first()
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CALLOUT_COLORS.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(parseHexColor(hex), CircleShape)
                                .then(
                                    if (hex == currentColor) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onSetCalloutColor(hex) },
                        )
                    }
                }
            }

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
private fun AddLabelDialog(viewModel: PageDetailViewModel, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val candidates by viewModel.labelCandidates.collectAsState()

    TendrilSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.5f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add label", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchLabelCandidates(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                // A bare field was invisible until typed into — on desktop, where no keyboard
                // rises to say "type here", there was nothing to aim a click at.
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Label name", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
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

    TendrilSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(0.5f)) {
        // A fraction of the current screen's height, not a flat dp figure — stays
        // proportionate from small phones to tablets rather than over/under-filling.
        Column {
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
 * — no reason to advertise a section with nothing behind it. §0.6.12 adds a second section,
 * **Unlinked mentions**, same shape, each row with Obsidian's *Link*. */
@Composable
private fun BacklinksPanel(
    backlinks: List<Backlink>,
    unlinked: List<UnlinkedMention>,
    onOpenPage: (Long) -> Unit,
    onLink: (UnlinkedMention) -> Unit,
) {
    if (backlinks.isEmpty() && unlinked.isEmpty()) return
    val contentLocked = LocalContentLocked.current
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        if (backlinks.isNotEmpty()) {
            MentionSection(title = "Linked mentions (${backlinks.size})") {
                backlinks.forEach { backlink ->
                    MentionRow(backlink.fromPage.title, backlink.block.content, onOpen = { onOpenPage(backlink.fromPage.id) }, action = null)
                }
            }
        }
        if (unlinked.isNotEmpty()) {
            MentionSection(title = "Unlinked mentions (${unlinked.size})") {
                unlinked.forEach { mention ->
                    MentionRow(
                        mention.page.title, mention.block.content, onOpen = { onOpenPage(mention.page.id) },
                        action = if (contentLocked) null else ({ TextButton(onClick = { onLink(mention) }) { Text("Link") } }),
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionSection(title: String, rows: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { expanded = !expanded }).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.labelLarge)
    }
    if (expanded) rows()
}

@Composable
private fun MentionRow(pageTitle: String, blockText: String, onOpen: () -> Unit, action: (@Composable () -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pageTitle, style = MaterialTheme.typography.bodyMedium)
            Text(blockText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        action?.invoke()
    }
}

/** §5.1 Row-as-page property strip — one row per property, name on the left, an editor
 * matching the table view's own cell behavior on the right (a bound role edits through the
 * linked Entry, everything else edits the stored [PropertyValue] directly). */
/** §0.6.8 — a database's title for a membership strip, read once; titles change rarely and the
 * strip is rebuilt whenever the membership list is. */
@Composable
private fun membershipTitle(databasePageId: Long, viewModel: PageDetailViewModel): String {
    val title by androidx.compose.runtime.produceState("", databasePageId) { value = viewModel.pageTitle(databasePageId) }
    return title.ifBlank { "Database" }
}

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
                database?.deadlinePropertyId -> RowBoundDateEditor(linkedEntry, viewModel, BindingRole.DEADLINE)
                database?.dueDatePropertyId -> RowBoundDateEditor(linkedEntry, viewModel, BindingRole.DUE_DATE)
                database?.recurrencePropertyId -> RowRecurrenceEditor(linkedEntry, viewModel)
                else -> RowUnboundEditor(property, storedValue, viewModel)
            }
        }
    }
}

@Composable
private fun RowBoundDateEditor(entry: Entry?, viewModel: PageDetailViewModel, role: BindingRole) {
    var showPicker by remember { mutableStateOf(false) }
    val locked = LocalContentLocked.current
    val current = if (role == BindingRole.DUE_DATE) entry?.dueDate else entry?.startDate
    Text(current?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.combinedClickable(onClick = { if (entry != null && !locked) showPicker = true }))
    if (showPicker && entry != null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (current ?: LocalDate.now()).toDatePickerMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.setRowBoundDate(role, datePickerMillisToLocalDate(it)) }
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
    // §B6 — same fix as `PageDatabaseScreen.kt`'s `RecurrenceCell`: this was `Period.toString()`'s
    // raw ISO form ("P7D") rather than anything a person reads as a recurrence.
    Text(
        rule?.period?.let(::formatPeriodAsHumanInterval) ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.combinedClickable(onClick = { if (entry != null && !locked) showPicker = true }),
    )
    if (showPicker && entry != null) {
        var countText by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf(IntervalUnit.WEEK) }
        var showUnitMenu by remember { mutableStateOf(false) }
        TendrilSheet(title = "Repeat every", onDismiss = { showPicker = false }) {
            Column {
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
            // §B3 — same `lastWrittenValue` guard as the sibling fix in
            // `PageDatabaseScreen.kt`'s `UnboundCell`: keying `remember` on `storedValue`
            // reset `text` on every Room emission, including a merge write landing
            // mid-keystroke, which clobbered the character just typed.
            val currentValue = storedValue ?: ""
            var text by remember(property.id) { mutableStateOf(currentValue) }
            var lastWrittenValue by remember(property.id) { mutableStateOf(currentValue) }
            LaunchedEffect(currentValue) {
                if (currentValue != lastWrittenValue) {
                    text = currentValue
                    lastWrittenValue = currentValue
                }
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it; lastWrittenValue = it; viewModel.setRowPropertyValue(property, it) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                readOnly = locked,
                singleLine = true,
            )
        }
    }
}

/** §0.6.2 — the ids of every block hidden behind a mind-map card: the descendants of any block
 * whose `mindMap` is on. A mapped block inside a mapped subtree is simply part of the outer map. */
private fun mappedDescendants(outline: List<OutlineBlock>): Set<Long> {
    val hidden = mutableSetOf<Long>()
    var i = 0
    while (i < outline.size) {
        val entry = outline[i]
        if (entry.block.mindMap && entry.block.id !in hidden) {
            var j = i + 1
            while (j < outline.size && outline[j].depth > entry.depth) { hidden += outline[j].block.id; j++ }
        }
        i++
    }
    return hidden
}

/** The root and its descendants, in outline order — what the map draws. */
private fun subtreeOf(outline: List<OutlineBlock>, rootId: Long): List<OutlineBlock> {
    val start = outline.indexOfFirst { it.block.id == rootId }
    if (start < 0) return emptyList()
    val depth = outline[start].depth
    var end = start
    while (end + 1 < outline.size && outline[end + 1].depth > depth) end++
    return outline.subList(start, end + 1)
}

/** Back closes the armed map before it does anything else. Compose Multiplatform's own
 * `BackHandler`; the Android scaffold's system-back handler is further out and never sees it. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MapBackHandler(onBack: () -> Unit) {
    androidx.compose.ui.backhandler.BackHandler(enabled = true, onBack = onBack)
}
