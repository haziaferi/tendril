package com.tendril.app.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.entry.intervalToPeriod
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockDao
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageTag
import com.tendril.app.data.page.Tag
import com.tendril.app.data.page.TagDao
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.data.pagedatabase.PropertyDao
import com.tendril.app.data.pagedatabase.PropertyValue
import com.tendril.app.data.pagedatabase.PropertyValueDao
import com.tendril.app.data.pagedatabase.setValue
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.domain.indentTargetFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val tagDao: TagDao,
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

    val tags: StateFlow<List<Tag>> =
        tagDao.observeForPage(pageId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tagCandidates = MutableStateFlow<List<Tag>>(emptyList())
    val tagCandidates: StateFlow<List<Tag>> = _tagCandidates.asStateFlow()

    private val _mentionCandidates = MutableStateFlow<List<Page>>(emptyList())
    val mentionCandidates: StateFlow<List<Page>> = _mentionCandidates.asStateFlow()

    /** §5.1 Row-as-page — populated only when this Page is a Database row (`databaseId` set);
     * empty/null for an ordinary Page. */
    val rowDatabase: StateFlow<PageDatabase?> = page.filterNotNull()
        .flatMapLatest { p -> p.databaseId?.let(pageDatabaseDao::observeById) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rowProperties: StateFlow<List<Property>> = rowDatabase.filterNotNull()
        .flatMapLatest { propertyDao.observeForDatabase(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun setToggleExpanded(block: Block, expanded: Boolean) = launchAndReindex {
        blockDao.update(block.copy(toggleExpanded = expanded, updatedAt = Instant.now()))
    }

    fun changeType(block: Block, newType: BlockType) = launchAndReindex {
        blockDao.update(block.copy(type = newType, updatedAt = Instant.now()))
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
     * §3.1.1 — tuck [block] under the nearest preceding top-level sibling. A no-op when there
     * is none (nothing to tuck under) or when it is already indented, since one level is the
     * whole of the nesting this spec has.
     *
     * `order` is left alone: [outlineOf] draws a child immediately after its parent whatever
     * its own order says, so re-numbering here would be churn with nothing depending on it.
     */
    fun indentBlock(block: Block) = launchAndReindex {
        val blocks = blockDao.getForPage(pageId)
        val target = indentTargetFor(block, blocks) ?: return@launchAndReindex
        blockDao.update(block.copy(parentBlockId = target.id, updatedAt = Instant.now()))
    }

    /** The inverse, and the escape hatch for a child whose parent went away on another device:
     * anything indented can always be flattened again. */
    fun outdentBlock(block: Block) = launchAndReindex {
        if (block.parentBlockId == null) return@launchAndReindex
        blockDao.update(block.copy(parentBlockId = null, updatedAt = Instant.now()))
    }

    fun searchTagCandidates(query: String) {
        viewModelScope.launch {
            val existingIds = tags.value.map { it.id }.toSet()
            _tagCandidates.value = if (query.isBlank()) emptyList() else
                tagDao.search(query).filter { it.id !in existingIds }
        }
    }

    /** Type-to-search-or-create, matching [searchForMention]'s picker pattern: reuse a tag
     * whose name matches exactly (case-insensitive), otherwise a new one is created on pick. */
    fun addTag(name: String) {
        if (contentLocked()) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val tag = tagDao.findByName(trimmed) ?: run {
                tagDao.insert(Tag(name = trimmed))
                // Re-read rather than building a Tag from the returned id: `color` is derived
                // from the name at construction, so the stored row is the authoritative one.
                tagDao.findByName(trimmed)!!
            }
            // The one page-content mutation here that does not go through a launcher, because
            // its bump is conditional: picking a tag the page already carries changes nothing,
            // and bumping anyway would claim authorship of an edit that did not happen — enough
            // under LWW to beat a real edit sitting unsynced on another device.
            if (tags.value.none { it.id == tag.id }) {
                tagDao.addToPage(PageTag(pageId = pageId, tagId = tag.id))
                touch()
            }
        }
    }

    fun removeTag(tag: Tag) = launchTouching { tagDao.removeFromPage(pageId, tag.id) }

    /** Unbound cell edit for this Row — a bound role (Done/Deadline/Recurrence) never reaches
     * this path; those edit through [toggleRowDone]/[setRowDeadline]/[setRowRecurrence]. */
    fun setRowPropertyValue(property: Property, value: String?) =
        launchTouching { propertyValueDao.setValue(property.id, pageId, value) }

    fun toggleRowDone(checked: Boolean) {
        if (contentLocked()) return
        val entry = rowLinkedEntry.value ?: return
        viewModelScope.launch { resolveEntryUseCase.setDone(entry.id, checked) }
    }

    fun setRowDeadline(date: java.time.LocalDate?) {
        if (contentLocked()) return
        val entry = rowLinkedEntry.value ?: return
        viewModelScope.launch {
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
     * §9.4 — see [PageDao.touch]. Blocks, tags and cell values all travel *inside* this page's
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
     * otherwise locked page), and [addTag], whose bump is conditional.
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
     * no FTS index to rebuild — tags, and this Row's own cell values. Separate launcher rather
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
