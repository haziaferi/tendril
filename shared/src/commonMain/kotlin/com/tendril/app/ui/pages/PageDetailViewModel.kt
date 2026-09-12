package com.tendril.app.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.Label
import com.tendril.app.data.page.LabelDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.pagedatabase.setValue
import com.tendril.app.domain.BindingRole
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.LabelMembership
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.domain.indentTargetFor
import com.tendril.app.domain.outdentPlanFor
import com.tendril.app.domain.outlineOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** §3.1.5 backlinks panel entry — the page doing the mentioning, and the specific block the
 * mention lives in (its content is the snippet shown in the panel). */
data class Backlink(val fromPage: Page, val block: Block)

@OptIn(ExperimentalCoroutinesApi::class)
class PageDetailViewModel(
    private val pageId: Long,
    private val pageDao: PageDao,
    private val blockDao: BlockDao,
    private val labelDao: LabelDao,
    private val propertyDao: PropertyDao,
    private val propertyValueDao: PropertyValueDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val entryDao: EntryDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
    private val contentRepository: PageContentRepository,
    private val templateManager: TemplateManager,
    private val viewLockState: ViewLockState,
    private val checkboxOnlyState: CheckboxOnlyState,
    private val localImages: com.tendril.app.sync.LocalImageStore,
    private val labelMembership: LabelMembership,
) : ViewModel() {
    /** §3.1.2 — "every page under Pages becomes read-only as a group... no per-page exception."
     * Every mutating function below early-returns through this guard rather than relying on the
     * UI alone to withhold the affordance — the single enforcement point, matching this
     * codebase's existing preference for centralizing an invariant once (§9.8 R1) instead of
     * trusting every call site to remember it. Checkbox-only mode being active for *this* page
     * folds into the same guard: "only that page's unlinked checkboxes stay tappable, nothing
     * else can be edited" is exactly [contentLocked] plus [setChecked]'s own narrower
     * [viewOnlyLocked] exemption below. */
    private fun viewOnlyLocked() = viewLockState.viewOnly.value
    private fun contentLocked() = viewOnlyLocked() || checkboxOnlyState.activePageId.value == pageId

    val page: StateFlow<Page?> =
        pageDao.observeById(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val blocks: StateFlow<List<Block>> =
        blockDao.observeForPage(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §3.1.2 — "Available on any page containing block-level checkboxes not linked to a Task
     * (§3.1.1's to-do block)." A to-do block is never Task-linked by construction — only a
     * Database Row's own bound Done property is, and that's a different screen entirely
     * ([PageDatabaseScreen][com.tendril.app.ui.pages.PageDatabaseScreen]'s table view) — so
     * eligibility is simply "this page has at least one to-do block." */
    val hasCheckboxes: StateFlow<Boolean> =
        blocks.map { list -> list.any { it.type == BlockType.TODO } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isCheckboxOnlyActive: StateFlow<Boolean> = checkboxOnlyState.activePageId
        .map { it == pageId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** No unlock needed to turn on — §3.1.2: "toggling checkbox-only on plus confirming is
     * judged sufficient friction on its own." View-Only still wins if somehow both are toggled
     * at once, since a locked page has nothing to newly protect by going into checkbox-only. */
    fun activateCheckboxOnly(): Boolean {
        if (viewOnlyLocked()) return false
        return checkboxOnlyState.activate(pageId)
    }

    /** §3.1.2 — "turning it back off requires a full device unlock." The unlock prompt itself
     * is a platform-level concern (Android's `BiometricPrompt`, threaded down as
     * `onCheckboxOnlyUnlockRequest` — see [PageDetailScreen]); this is only ever called after
     * that prompt already succeeded (or on desktop, where there's no App Lock to gate against),
     * never directly from a tap. */
    fun deactivateCheckboxOnly() {
        checkboxOnlyState.deactivate()
    }

    val labels: StateFlow<List<Label>> =
        labelDao.observeForPage(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _labelCandidates = MutableStateFlow<List<Label>>(emptyList())
    val labelCandidates: StateFlow<List<Label>> = _labelCandidates.asStateFlow()

    private val _mentionCandidates = MutableStateFlow<List<Page>>(emptyList())
    val mentionCandidates: StateFlow<List<Page>> = _mentionCandidates.asStateFlow()

    /** §5.1 Row-as-page — the database this Page is a *native* row of (`databaseId` set), or null
     * for an ordinary Page. Since §0.6.8 it is one of possibly several [memberships]; it is still
     * the one that decides whether deleting this page is deleting a row. */
    val rowDatabase: StateFlow<PageDatabase?> = page.filterNotNull()
        .flatMapLatest { p -> p.databaseId?.let(pageDatabaseDao::observeById) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** §0.6.8 — one database this page is a member of, with the label that opened the door
     * (null for the native home) and the fields it brings to the header. */
    data class Membership(val database: PageDatabase, val viaLabel: Label?, val properties: List<Property>)

    private val labelledDatabases: kotlinx.coroutines.flow.Flow<List<Pair<PageDatabase, Label>>> = labels.flatMapLatest { carried ->
        if (carried.isEmpty()) flowOf(emptyList())
        else pageDatabaseDao.observeDatabasesForLabels(carried.map { it.id }).map { databases ->
            databases.mapNotNull { db -> carried.firstOrNull { it.id == db.labelId }?.let { db to it } }
        }
    }

    /** The home first, then the labelled memberships in label order; a native row that also
     * carries its own database's label is listed once, as home. */
    val memberships: StateFlow<List<Membership>> = combine(rowDatabase, labelledDatabases) { home, labelled ->
        listOfNotNull(home?.let { it to null as Label? }) + labelled.filter { (db, _) -> db.id != home?.id }
    }.flatMapLatest { pairs ->
        if (pairs.isEmpty()) flowOf(emptyList())
        else combine(pairs.map { (db, via) -> propertyDao.observeForDatabase(db.id).map { Membership(db, via, it) } }) { it.toList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §0.6.8 — a database's title for its membership strip. */
    suspend fun pageTitle(id: Long): String = pageDao.getById(id)?.title.orEmpty()

    /** §0.6.8 / B§12.0 — which of this page's labels are doorways, for the chip's small mark. */
    val boundLabelIds: StateFlow<Set<Long>> = pageDatabaseDao.observeBoundLabelIds().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val rowValues: StateFlow<Map<Long, PropertyValue>> = propertyValueDao.observeForRow(pageId)
        .map { values -> values.associateBy { it.propertyId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val rowLinkedEntry: StateFlow<Entry?> = entryDao.observeBySourceRowIds(listOf(pageId))
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** §3.1.5 — "the same edge data Road Map already computes, consumed here as a second,
     * lighter-weight view." Loaded once per page-open rather than kept live: the panel is
     * collapsed by default, and a backlink only changes when some *other* page's content is
     * edited, which this screen has no reason to be watching for. */
    private val _backlinks = MutableStateFlow<List<Backlink>>(emptyList())
    val backlinks: StateFlow<List<Backlink>> = _backlinks.asStateFlow()

    init {
        viewModelScope.launch {
            val mentioningBlocks = contentRepository.mentionsOf(pageId)
            _backlinks.value = mentioningBlocks.mapNotNull { block ->
                // §5.5.1 — a trashed page is "invisible everywhere except Trash", the
                // backlinks panel named alongside FTS and Road Map. `mentionsOf` reads blocks
                // without joining `pages`, and `getById` has no `deletedAt` filter, so a
                // trashed page's mentions kept showing up here and stayed tappable. Road Map
                // already filters the same edge data (RoadMapViewModel.refresh); this is the
                // second consumer §3.1.5 says should agree with it.
                pageDao.getById(block.pageId)
                    ?.takeIf { it.deletedAt == null }
                    ?.let { Backlink(it, block) }
            }
        }
    }

    /** §3.1.3 — "Save as template" from this page's "···" menu. Clones this page's own block
     * structure into a brand-new template page; the original page (and this screen's own
     * content) is untouched. */
    fun saveAsTemplate() {
        if (contentLocked()) return
        val current = page.value ?: return
        viewModelScope.launch { templateManager.saveAsTemplate(current) }
    }

    /** §5.5.1 — "Outright deletion — of a Page, a Row... goes to a Trash instead of being
     * destroyed immediately." A Row is a Page (§5.1) that happens to have `databaseId` set —
     * trashing it here also trashes its linked Task (§5.5's row-deletion confirm dialog states
     * this), the same pairing [DatabaseSyncManager.disableSync] and
     * [PageDatabaseViewModel.confirmDeleteRow] apply elsewhere, so a Task never outlives the
     * row it was generated from. */
    fun trashPage(onDeleted: () -> Unit) {
        if (contentLocked()) return
        viewModelScope.launch {
            val now = Instant.now()
            rowLinkedEntry.value?.let { resolveEntryUseCase.trash(it.id, now) }
            pageDao.softDelete(pageId, now)
            onDeleted()
        }
    }

    fun updateTitle(title: String) {
        if (contentLocked()) return
        viewModelScope.launch {
            val current = page.value ?: pageDao.getById(pageId) ?: return@launch
            pageDao.update(current.copy(title = title, updatedAt = Instant.now()))
            // The title is in the index (§3.1.1), so a rename re-indexes like a block edit does.
            contentRepository.rebuildFtsForPage(pageId)
        }
    }

    /** Inserts after the block currently at [afterOrder], renumbering every block from that
     * point on so ordering stays a dense, gap-free sequence — the same full-list rewrite
     * [moveBlock] uses, cheap at personal-notes block counts. */
    fun addBlock(type: BlockType, afterOrder: Int, content: String = ""): Unit = launchAndReindex {
        val existing = blockDao.getForPage(pageId).sortedBy { it.order }
        val insertAt = (afterOrder + 1).coerceIn(0, existing.size)
        val now = Instant.now()

        existing.drop(insertAt).forEach { b -> blockDao.update(b.copy(order = b.order + 1)) }
        blockDao.insert(
            Block(
                pageId = pageId,
                type = type,
                order = insertAt,
                content = content,
                checked = if (type == BlockType.TODO) false else null,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    fun updateBlockContent(block: Block, content: String, spans: List<FormattingSpan> = block.formattingSpans) = launchAndReindex {
        blockDao.update(block.copy(content = content, formattingSpans = spans, updatedAt = Instant.now()))
    }

    /** Deliberately gated by [viewOnlyLocked] alone, not [launchAndReindex]'s [contentLocked] —
     * §3.1.2's whole point for checkbox-only mode is that a to-do block's own checkbox stays
     * tappable even while everything else on the page is locked. */
    fun setChecked(block: Block, checked: Boolean) {
        if (viewOnlyLocked()) return
        viewModelScope.launch {
            blockDao.update(block.copy(checked = checked, updatedAt = Instant.now()))
            contentRepository.rebuildFtsForPage(pageId)
            touch()
        }
    }

    /** §0.6.3 — a block showing [canvasPageId], inserted after [afterOrder] like any other. */
    fun insertCanvasBlock(afterOrder: Int, canvasPageId: Long) = launchAndReindex {
        val existing = blockDao.getForPage(pageId).sortedBy { it.order }
        val insertAt = (afterOrder + 1).coerceIn(0, existing.size)
        val now = Instant.now()
        val title = pageDao.getById(canvasPageId)?.title.orEmpty()
        existing.drop(insertAt).forEach { b -> blockDao.update(b.copy(order = b.order + 1)) }
        blockDao.insert(
            Block(
                pageId = pageId, type = BlockType.CANVAS, order = insertAt,
                // The title as content, so the export and a device without the canvas still
                // have a word for what stood here — the same reason a mention block keeps its text.
                content = title, mentionedPageId = canvasPageId,
                createdAt = now, updatedAt = now,
            )
        )
    }

    /** §0.6.3 — a new Canvas page under this one, then the block that shows it. */
    fun createCanvasAndInsert(afterOrder: Int, title: String) = launchAndReindex {
        val now = Instant.now()
        val canvasPageId = pageDao.insert(
            Page(title = title.ifBlank { "Untitled canvas" }, kind = PageKind.CANVAS, parentId = pageId, createdAt = now, updatedAt = now),
        )
        insertCanvasBlock(afterOrder, canvasPageId)
    }

    /** §0.6.2 — show or hide this block's subtree as a mind map. */
    fun setMindMap(block: Block, mindMap: Boolean) = launchAndReindex {
        blockDao.update(block.copy(mindMap = mindMap, updatedAt = Instant.now()))
    }

    /**
     * §0.6.2 — a new child at the end of [parent]'s subtree: the map's "add child" is an
     * ordinary block insert. Placed after the last block of the parent's subtree in outline
     * order (so it reads last among its siblings in the list too), then parented, with the same
     * dense renumbering [addBlock] does.
     */
    fun addBlockUnder(parent: Block, content: String) = launchAndReindex {
        val existing = blockDao.getForPage(pageId)
        val outline = outlineOf(existing, expandAll = true)
        val parentIndex = outline.indexOfFirst { it.block.id == parent.id }
        if (parentIndex < 0) return@launchAndReindex
        val parentDepth = outline[parentIndex].depth
        // The subtree ends where the next entry is no deeper than the parent.
        var end = parentIndex
        while (end + 1 < outline.size && outline[end + 1].depth > parentDepth) end++
        val afterOrder = outline[end].block.order
        val sorted = existing.sortedBy { it.order }
        val insertAt = (afterOrder + 1).coerceIn(0, sorted.size)
        val now = Instant.now()
        sorted.drop(insertAt).forEach { b -> blockDao.update(b.copy(order = b.order + 1)) }
        blockDao.insert(
            Block(
                pageId = pageId,
                type = BlockType.BULLETED_LIST_ITEM,
                order = insertAt,
                content = content,
                parentBlockId = parent.id,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    fun setToggleExpanded(block: Block, expanded: Boolean) = launchAndReindex {
        blockDao.update(block.copy(toggleExpanded = expanded, updatedAt = Instant.now()))
    }

    fun changeType(block: Block, newType: BlockType) = launchAndReindex {
        blockDao.update(block.copy(type = newType, updatedAt = Instant.now()))
    }

    /** CODE only (P1). `null` means "plain text" — the same value the Notion importer
     * stores for a fence with no language tag (`NotionMarkdownParser`'s `.ifBlank { null }`). */
    fun setCodeLanguage(block: Block, language: String?) = launchAndReindex {
        blockDao.update(block.copy(codeLanguage = language, updatedAt = Instant.now()))
    }

    /** CALLOUT only (P3). */
    /**
     * §3.1.1 / P2 — store a chosen image and point [block] at this device's copy.
     *
     * Named after the block, matching what §9.4's sync would call it, so a picture inserted here
     * and one fetched from the folder are the same thing on disk. `launchAndReindex` like every
     * other block mutation: the image is not indexed, but `pages.updatedAt` still has to move or
     * the change never reaches the other device (the §9.4 defect fixed in `0d2a932`).
     */
    fun setBlockImage(block: Block, fileName: String, bytes: ByteArray) = launchAndReindex {
        val extension = fileName.substringAfterLast('.', "")
        val name = if (extension.isBlank()) block.uid else "${block.uid}.$extension"
        val path = localImages.write(name, bytes)
        blockDao.update(block.copy(imagePath = path, updatedAt = java.time.Instant.now()))
    }

    fun setCalloutColor(block: Block, color: String) = launchAndReindex {
        blockDao.update(block.copy(calloutColor = color, updatedAt = Instant.now()))
    }

    fun deleteBlock(block: Block) = launchAndReindex {
        blockDao.delete(block.id)
    }

    /** Reordering via Move Up/Down rather than a literal drag gesture (§3.1.1 calls for
     * "a drag handle on hover/long-press to reorder") — same end capability, any block can
     * move to any position, with far less gesture-tracking risk on a touch target this small. */
    fun moveBlock(block: Block, direction: Int) = launchAndReindex {
        val ordered = blockDao.getForPage(pageId).sortedBy { it.order }
        val index = ordered.indexOfFirst { it.id == block.id }
        val targetIndex = index + direction
        if (index < 0 || targetIndex < 0 || targetIndex >= ordered.size) return@launchAndReindex
        val reordered = ordered.toMutableList().apply { add(targetIndex, removeAt(index)) }
        reordered.forEachIndexed { i, b -> if (b.order != i) blockDao.update(b.copy(order = i)) }
    }

    /**
     * §3.1.1 / §0.6.1 — tuck [block] under its nearest preceding sibling. A no-op when there is
     * none. Any depth: the cap that used to live here and in [indentTargetFor] is gone.
     *
     * `order` is left alone: [outlineOf] draws a child immediately after its parent whatever
     * its own order says, so re-numbering here would be churn with nothing depending on it.
     */
    fun indentBlock(block: Block) = launchAndReindex {
        val blocks = blockDao.getForPage(pageId)
        val target = indentTargetFor(block, blocks) ?: return@launchAndReindex
        blockDao.update(block.copy(parentBlockId = target.id, updatedAt = Instant.now()))
    }

    /** The inverse — one level up, taking the siblings that followed along as children so the
     * page keeps its reading order (see [outdentPlanFor]). Still the escape hatch for a child
     * whose parent went away on another device: the outline already draws such a block as a
     * root, and this makes that permanent. */
    fun outdentBlock(block: Block) = launchAndReindex {
        val blocks = blockDao.getForPage(pageId)
        val plan = outdentPlanFor(block, blocks)
        val now = Instant.now()
        if (plan == null) {
            // Parent missing on this page: flatten, which is what the outline was showing anyway.
            if (block.parentBlockId != null) blockDao.update(block.copy(parentBlockId = null, updatedAt = now))
            return@launchAndReindex
        }
        blockDao.update(block.copy(parentBlockId = plan.newParentId, updatedAt = now))
        for (id in plan.adoptedIds) {
            val sibling = blocks.first { it.id == id }
            blockDao.update(sibling.copy(parentBlockId = block.id, updatedAt = now))
        }
    }

    fun searchLabelCandidates(query: String) {
        viewModelScope.launch {
            val existingIds = labels.value.map { it.id }.toSet()
            _labelCandidates.value = if (query.isBlank()) emptyList() else
                labelDao.search(query).filter { it.id !in existingIds }
        }
    }

    /** §0.6.8 — the once-only question: this label opens a database that syncs to Tasks, and
     * nobody has said yes to that for it yet. The label is applied on [confirmPendingLabel]. */
    data class PendingLabel(val label: Label, val database: PageDatabase)

    private val _pendingLabel = MutableStateFlow<PendingLabel?>(null)
    val pendingLabel: StateFlow<PendingLabel?> = _pendingLabel.asStateFlow()
    fun dismissPendingLabel() { _pendingLabel.value = null }
    fun confirmPendingLabel() {
        if (contentLocked()) return
        val pending = _pendingLabel.value ?: return
        _pendingLabel.value = null
        viewModelScope.launch {
            labelMembership.confirm(pending.database)
            if (labelMembership.applyLabel(pageId, pending.label)) touch()
        }
    }

    /** Type-to-search-or-create, matching [searchForMention]'s picker pattern: reuse a label
     * whose name matches exactly (case-insensitive), otherwise a new one is created on pick. */
    fun addLabel(name: String) {
        if (contentLocked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val label = labelDao.findByName(trimmed) ?: run {
                labelDao.insert(Label(name = trimmed))
                // Re-read rather than building a Label from the returned id: `color` is derived
                // from the name at construction, so the stored row is the authoritative one.
                labelDao.findByName(trimmed)!!
            }
            // §0.6.8 — a label that makes this page a task is applied only after the person has
            // been told so, once per database.
            val asks = labelMembership.needsConfirmation(label)
            if (asks != null && labels.value.none { it.id == label.id }) {
                _pendingLabel.value = PendingLabel(label, asks)
                return@launch
            }
            // The one page-content mutation here that does not go through a launcher, because
            // its bump is conditional: picking a label the page already carries changes nothing,
            // and bumping anyway would claim authorship of an edit that did not happen — enough
            // under LWW to beat a real edit sitting unsynced on another device.
            if (labelMembership.applyLabel(pageId, label)) touch()
        }
    }

    /** §0.6.8 — through [LabelMembership], so a label that made this page a task takes the task
     * with it; the page's values for that database stay, hidden until the label returns. */
    fun removeLabel(label: Label) = launchTouching { labelMembership.removeLabel(pageId, label) }

    /** Unbound cell edit for this Row — a bound role (Done/Deadline/Recurrence) never reaches
     * this path; those edit through [toggleRowDone]/[setRowBoundDate]/[setRowRecurrence]. */
    fun setRowPropertyValue(property: Property, value: String?) =
        launchTouching { propertyValueDao.setValue(property.id, pageId, value) }

    fun toggleRowDone(checked: Boolean) {
        if (contentLocked()) return
        val entry = rowLinkedEntry.value ?: return
        viewModelScope.launch { resolveEntryUseCase.setDone(entry.id, checked) }
    }

    /** The row's bound date — the When or, for [BindingRole.DUE_DATE], the deadline (§0.6.4). */
    fun setRowBoundDate(role: BindingRole, date: java.time.LocalDate?) {
        if (contentLocked()) return
        val entry = rowLinkedEntry.value ?: return
        viewModelScope.launch {
            if (role == BindingRole.DUE_DATE) {
                entryDao.update(entry.copy(dueDate = date, updatedAt = Instant.now()))
                return@launch
            }
            val updated = entry.copy(startDate = date, updatedAt = Instant.now())
            entryDao.update(updated)
            entryScheduleCoordinator.onEntryChanged(updated)
        }
    }

    fun setRowRecurrence(count: Int, unit: IntervalUnit) {
        if (contentLocked()) return
        val entry = rowLinkedEntry.value ?: return
        viewModelScope.launch {
            val updated = entry.copy(recurrenceRule = RecurrenceRule.Elastic(intervalToPeriod(count, unit)), updatedAt = Instant.now())
            entryDao.update(updated)
            entryScheduleCoordinator.onEntryChanged(updated)
        }
    }

    fun searchForMention(query: String) {
        viewModelScope.launch {
            _mentionCandidates.value = if (query.isBlank()) emptyList() else pageDao.getAll().filter {
                it.deletedAt == null && it.id != pageId && it.title.contains(query, ignoreCase = true)
            }.take(20)
        }
    }

    /**
     * §9.4 — see [PageDao.touch]. Blocks, labels and cell values all travel *inside* this page's
     * snapshot, and the merge decides whether to apply any of them by looking at the page row
     * alone. Writing only the child row leaves the exported snapshot claiming, truthfully as far
     * as the page row knows, that nothing changed — so the peer drops the edit.
     *
     * Deliberately alongside [PageContentRepository.rebuildFtsForPage] rather than anywhere
     * else: the FTS index and the sync timestamp are two derived things with exactly the same
     * trigger, and keeping them in one place is what makes the next block mutation get both for
     * free. Everything that mutates this page's content goes through [launchAndReindex] or
     * [launchTouching], so the bump is part of launching rather than a second call to remember
     * after it — which is exactly the shape of the bug this fixes, and the shape it would come
     * back in. Two mutations call this directly and say why at their own site: [setChecked],
     * whose lock gate is the narrower [viewOnlyLocked] (§3.1.2 keeps a to-do tappable on an
     * otherwise locked page), and [addLabel], whose bump is conditional.
     */
    private suspend fun touch() = pageDao.touch(pageId, Instant.now())

    private fun launchAndReindex(block: suspend () -> Unit) {
        if (contentLocked()) return
        viewModelScope.launch {
            block()
            contentRepository.rebuildFtsForPage(pageId)
            touch()
        }
    }

    /** The same guarantee for the page-content mutations that are not block edits and so have
     * no FTS index to rebuild — labels, and this Row's own cell values. Separate launcher rather
     * than a flag on [launchAndReindex], because "rebuild the index" and "say the page changed"
     * are two different claims and a boolean at the call site would read as neither. */
    private fun launchTouching(block: suspend () -> Unit) {
        if (contentLocked()) return
        viewModelScope.launch {
            block()
            touch()
        }
    }
}
