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
import com.tendril.app.data.page.PageRevision
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
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.domain.CheckInHabitUseCase
import com.tendril.app.domain.CheckboxOnlyState
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.LabelMembership
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.data.prefs.AiKeyStore
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.domain.ai.AI_MODEL_KEY
import com.tendril.app.domain.ai.AiModels
import com.tendril.app.domain.ai.AiVerb
import com.tendril.app.domain.ai.OutlineRow
import com.tendril.app.domain.ai.ClaudeClient
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.indentTargetFor
import com.tendril.app.domain.outlineOf
import com.tendril.app.domain.blocks.BlockEdit
import com.tendril.app.domain.blocks.BlockUndoStack
import com.tendril.app.domain.blocks.planApply
import com.tendril.app.domain.blocks.withSubtrees
import com.tendril.app.markdown.MarkdownWriter
import com.tendril.app.domain.references.MIN_UNLINKED_TITLE_LENGTH
import com.tendril.app.domain.references.UnlinkedMention
import com.tendril.app.domain.references.linkMention
import com.tendril.app.domain.references.unlinkedMentions
import com.tendril.app.domain.journal.JournalToday
import com.tendril.app.domain.journal.journalDayOf
import com.tendril.app.data.checkin.CheckIn
import com.tendril.app.data.checkin.CheckInDao
import com.tendril.app.domain.checkin.checkInOffered
import java.time.YearMonth
import com.tendril.app.domain.journal.todayHabits
import com.tendril.app.domain.journal.todayTasks
import com.tendril.app.domain.track.minuteTicker
import com.tendril.app.domain.outdentPlanFor
import com.tendril.app.domain.outlineOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

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
    private val habitDao: HabitDao,
    private val checkInHabitUseCase: CheckInHabitUseCase,
    private val pageHistory: PageHistory,
    private val aiKeyStore: AiKeyStore,
    private val keyValueStore: KeyValueStore,
    private val checkInDao: CheckInDao,
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

    /** §3.1.4 (amended, step 8b) — the strip above today's Journal page, null on every other
     * page. The day is re-read each minute (`loggedToday`'s pattern in `TasksHabitsViewModel`)
     * so a page left open past midnight stops claiming to be today; the root lookup runs once
     * per page/day, the string parse is all a non-Journal page pays. */
    val journalToday: StateFlow<Pair<LocalDate, JournalToday>?> =
        combine(page.filterNotNull(), minuteTicker().map { LocalDate.now() }.distinctUntilChanged()) { p, today -> p to today }
            .flatMapLatest { (p, today) ->
                if (journalDayOf(p, pageDao.findRootByTitle("Journal")) != today) flowOf(null)
                else combine(entryDao.observeTasks(), habitDao.observeActive()) { tasks, habits ->
                    today to JournalToday(todayTasks(tasks, today), todayHabits(habits, today))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** §0.10 item 4 — the Journal day this page is, null on every other page; the check-in row's
     * home (any day that has happened, not only today — the strip's rule is its own). */
    val journalDay: StateFlow<LocalDate?> =
        page.filterNotNull().map { journalDayOf(it, pageDao.findRootByTitle("Journal")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The day's live check-ins, in tap order. Empty on a non-Journal page and on a future day. */
    val checkIns: StateFlow<List<CheckIn>> =
        journalDay.flatMapLatest { day ->
            if (day == null || !checkInOffered(day, LocalDate.now())) flowOf(emptyList()) else checkInDao.observeForDay(day)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The sheet's month — the live rows between its first and last day. */
    fun checkInsIn(month: YearMonth): Flow<List<CheckIn>> = checkInDao.observeBetween(month.atDay(1), month.atEndOfMonth())

    /** One tap: a row with one scale set, stamped now, dated the page's day. Not behind
     * [contentLocked] for the strip's reason — a note about the person, not the page's text. */
    fun checkIn(mood: Int? = null, energy: Int? = null) {
        val day = journalDay.value ?: return
        if (!checkInOffered(day, LocalDate.now())) return
        viewModelScope.launch { checkInDao.insert(CheckIn(date = day, at = Instant.now(), mood = mood, energy = energy)) }
    }

    /** A tap on the chosen chip again: the row is tombstoned, never edited (see [CheckIn]). */
    fun undoCheckIn(id: Long) {
        viewModelScope.launch { checkInDao.softDelete(id, Instant.now()) }
    }

    /** The strip's boxes. Not behind [contentLocked]: these are task and habit writes, which
     * View-Only (§3.1.2, "pages become read-only") never covered — the Tasks tab ticks under
     * the lock, and the strip ticks the same rows through the same use cases (§9.8 R1). */
    fun setTaskDone(entryId: Long, done: Boolean) {
        viewModelScope.launch { resolveEntryUseCase.setDone(entryId, done) }
    }

    fun checkInHabit(habitId: Long) {
        viewModelScope.launch { checkInHabitUseCase.checkIn(habitId); rearmHabit(habitId) }
    }

    fun undoCheckInHabit(habitId: Long) {
        viewModelScope.launch { checkInHabitUseCase.undoCheckIn(habitId); rearmHabit(habitId) }
    }

    /** §9.7 — every write that moves when a habit is next due re-arms from the row as it stands. */
    private suspend fun rearmHabit(habitId: Long) {
        habitDao.getById(habitId)?.let { entryScheduleCoordinator.onHabitChanged(it) }
    }

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

    /** 14d — a RELATION property's rows by title for the row-as-page strip; a uid this device
     * cannot resolve (deleted, quarantined §9.4) is absent, as `PageDatabaseViewModel.resolveRelatedTitles`. */
    suspend fun resolveRelatedTitles(uids: Set<String>): List<String> = uids.mapNotNull { pageDao.getByUid(it)?.title }

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

    /** §3.1.5 (amended, §0.6.12) — pages naming this one in plain text without a link; loaded
     * with [backlinks], for the same reason, and reloaded after [link]. */
    private val _unlinkedMentions = MutableStateFlow<List<UnlinkedMention>>(emptyList())
    val unlinkedMentions: StateFlow<List<UnlinkedMention>> = _unlinkedMentions.asStateFlow()

    /** Called by the screen each time the page is entered — not from `init`, because the
     * ViewModel is keyed per page in the window's store and outlives the route: "once per
     * page-open" was, until step 8d, once per session. Both loads are cheap. */
    fun onOpened() {
        viewModelScope.launch {
            refreshBlockReferenceCaches()
            loadMentions()
        }
    }

    private suspend fun loadMentions() {
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
        val title = pageDao.getById(pageId)?.title.orEmpty().trim()
        _unlinkedMentions.value = if (title.length < MIN_UNLINKED_TITLE_LENGTH) emptyList() else {
            // The picker's substring scan doubles as the candidate set: live, non-template
            // pages' text blocks containing the title. The pure function does the rest.
            val candidates = blockDao.searchContent(title)
            val pages = candidates.map { it.pageId }.distinct().mapNotNull { pageDao.getById(it) }.associateBy { it.id }
            unlinkedMentions(title, pageId, candidates, pages, _backlinks.value.mapTo(mutableSetOf()) { it.fromPage.id })
        }
    }

    /** §0.6.12 — Obsidian's "Link": the plain words become a real mention, on the *other* page.
     * A write to that page, so it is re-indexed and its timestamp touched (the mentioning page is
     * what changed; this one only gained a backlink). Gated like every write here. */
    fun link(mention: UnlinkedMention) {
        if (contentLocked()) return
        viewModelScope.launch {
            pageHistory.captureBeforeEdit(mention.block.pageId)
            blockDao.update(linkMention(mention.block, mention.range, pageId).copy(updatedAt = Instant.now()))
            contentRepository.rebuildFtsForPage(mention.block.pageId)
            pageDao.touch(mention.block.pageId, Instant.now())
            loadMentions()
        }
    }

    /** §0.6.12 — a reference block's [Block.content] is a cache of its source's text, refreshed
     * here when the source has moved on. Derived, not authored, so neither the block's nor the
     * page's timestamp moves: every device refreshes its own copy on open, and the export
     * carries whatever is current. */
    private suspend fun refreshBlockReferenceCaches() {
        for (block in blockDao.getForPage(pageId)) {
            val uid = block.referencedBlockUid ?: continue
            val source = blockDao.getByUid(uid) ?: continue
            if (source.content != block.content) blockDao.update(block.copy(content = source.content))
        }
    }

    /** §0.6.15 — the verb row exists only while a key is set; nothing else changes without one. */
    val aiAvailable: StateFlow<Boolean> = aiKeyStore.key.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), aiKeyStore.key.value != null)

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    /** §0.6.15 — one press, one client, one request carrying only [text] and the verb's
     * instruction; the client is built here and dropped with the call. The result goes back
     * to the sheet — nothing is written until the person chooses. */
    fun runVerb(verb: AiVerb, text: String, onResult: (Result<String>) -> Unit) {
        val key = aiKeyStore.key.value ?: return
        val model = keyValueStore.get(AI_MODEL_KEY)?.takeIf { it in AiModels } ?: AiModels.first()
        viewModelScope.launch {
            _aiBusy.value = true
            val result = ClaudeClient(key, model).complete(verb, text)
            _aiBusy.value = false
            onResult(result)
        }
    }

    /** §0.6.13 — History: the page's kept bodies, newest first. */
    val revisions: Flow<List<PageRevision>> = pageHistory.revisions(pageId)

    /** §0.6.13 — the title and blocks become the revision's; the current body is kept first.
     * An edit like any other: locked, re-indexed, touched — and the block-reference caches on
     * the restored body refresh at the next open like everyone else's. */
    fun restore(revisionId: Long) = launchAndReindex { pageHistory.restore(revisionId) }

    /** §0.6.12 — the block-reference picker's search; the page title rides along for the row. */
    fun searchBlocks(query: String, onResult: (List<Pair<Page, Block>>) -> Unit) {
        viewModelScope.launch {
            if (query.isBlank()) { onResult(emptyList()); return@launch }
            val blocks = blockDao.searchContent(query)
            val pages = blocks.map { it.pageId }.distinct().mapNotNull { pageDao.getById(it) }.associateBy { it.id }
            onResult(blocks.mapNotNull { b -> pages[b.pageId]?.let { it to b } })
        }
    }

    /** §0.6.12 — a block showing [source]'s live text, inserted after [afterOrder] like any other.
     * The words are cached as content (see [refreshBlockReferenceCaches]); the source's page is
     * the tap target. A reference to a reference is refused — it would show a cache of a cache. */
    fun insertBlockReference(afterOrder: Int, source: Block) = launchAndReindex {
        if (source.type == BlockType.BLOCK_REFERENCE) return@launchAndReindex
        val existing = blockDao.getForPage(pageId).sortedBy { it.order }
        val insertAt = (afterOrder + 1).coerceIn(0, existing.size)
        val now = Instant.now()
        existing.drop(insertAt).forEach { b -> blockDao.update(b.copy(order = b.order + 1)) }
        blockDao.insert(
            Block(
                pageId = pageId, type = BlockType.BLOCK_REFERENCE, order = insertAt,
                content = source.content, mentionedPageId = source.pageId, referencedBlockUid = source.uid,
                createdAt = now, updatedAt = now,
            )
        )
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
            pageHistory.captureBeforeEdit(pageId)
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

    fun changeType(block: Block, newType: BlockType) = changeTypes(setOf(block.id), newType)

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

    fun deleteBlock(block: Block) = deleteBlocks(setOf(block.id))

    /** Reordering via Move Up/Down rather than a literal drag gesture (§3.1.1 calls for
     * "a drag handle on hover/long-press to reorder") — same end capability, any block can
     * move to any position, with far less gesture-tracking risk on a touch target this small. */
    fun moveBlock(block: Block, direction: Int) = launchRecorded("move") {
        val ordered = blockDao.getForPage(pageId).sortedBy { it.order }
        val index = ordered.indexOfFirst { it.id == block.id }
        val targetIndex = index + direction
        if (index < 0 || targetIndex < 0 || targetIndex >= ordered.size) return@launchRecorded
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
    fun indentBlock(block: Block) = launchRecorded("indent") { blocks ->
        val target = indentTargetFor(block, blocks) ?: return@launchRecorded
        blockDao.update(block.copy(parentBlockId = target.id, updatedAt = Instant.now()))
    }

    /** The inverse — one level up, taking the siblings that followed along as children so the
     * page keeps its reading order (see [outdentPlanFor]). Still the escape hatch for a child
     * whose parent went away on another device: the outline already draws such a block as a
     * root, and this makes that permanent. */
    fun outdentBlock(block: Block) = launchRecorded("outdent") { blocks ->
        val plan = outdentPlanFor(block, blocks)
        val now = Instant.now()
        if (plan == null) {
            // Parent missing on this page: flatten, which is what the outline was showing anyway.
            if (block.parentBlockId != null) blockDao.update(block.copy(parentBlockId = null, updatedAt = now))
            return@launchRecorded
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
            // F9 (small things III): every label the page does not have yet, filtered as typed —
            // Notion's and Obsidian's pickers list the existing ones first; a blank query lists all.
            _labelCandidates.value = labelDao.search(query.trim()).filter { it.id !in existingIds }
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


    // ── §0.10 item 19 — a block selection's verbs and the block undo stack ──────────────────
    // Every verb below runs under [launchRecorded]: the page's blocks are read before and after,
    // and the rows that differ become one [BlockEdit] on the stack. Typing does not come here —
    // [updateBlockContent] stays on [launchAndReindex], the field keeps its own undo.

    private val undoStack = BlockUndoStack()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    private fun undoChanged() { _canUndo.value = undoStack.canUndo; _canRedo.value = undoStack.canRedo }

    /** The last copied run in reading order — types, spans and nesting kept. The rows are the
     * source's; a paste mints new ones, so a run can be pasted twice. */
    private var copied: List<Block> = emptyList()
    val hasCopiedBlocks: Boolean get() = copied.isNotEmpty()

    /** The run [ids] closed under the outline's subtrees, in reading order. */
    private fun runOf(blocks: List<Block>, ids: Set<Long>): List<Block> {
        val outline = outlineOf(blocks, expandAll = true)
        val closed = withSubtrees(ids, outline)
        return outline.map { it.block }.filter { it.id in closed }
    }

    private fun launchRecorded(label: String, onDone: (BlockEdit?) -> Unit = {}, op: suspend (List<Block>) -> Unit) = launchAndReindex {
        val before = blockDao.getForPage(pageId)
        op(before)
        val after = blockDao.getForPage(pageId)
        val b = before.associateBy { it.id }
        val a = after.associateBy { it.id }
        val changed = (b.keys + a.keys).filter { b[it] != a[it] }.toSet()
        val edit = if (changed.isEmpty()) null else BlockEdit(label, before.filter { it.id in changed }, after.filter { it.id in changed })
        if (edit != null) { undoStack.push(edit); undoChanged() }
        onDone(edit)
    }

    /** Delete a run and its subtrees — one entry. */
    fun deleteBlocks(ids: Set<Long>, onDone: (Int) -> Unit = {}) = launchRecorded("delete", onDone = { onDone(it?.before?.size ?: 0) }) { blocks ->
        runOf(blocks, ids).forEach { blockDao.delete(it.id) }
    }

    /** Move a run one step in the page's flat order (the sheet's Move up / Move down); parents
     * stay as they are, as [moveBlock] has always left them. */
    fun moveBlocks(ids: Set<Long>, direction: Int) = launchRecorded("move") { blocks ->
        val ordered = blocks.sortedBy { it.order }
        val run = runOf(blocks, ids).map { it.id }.toSet()
        if (run.isEmpty()) return@launchRecorded
        val rest = ordered.filter { it.id !in run }.toMutableList()
        val first = ordered.indexOfFirst { it.id in run }
        val at = (first + direction).coerceIn(0, rest.size)
        val moving = ordered.filter { it.id in run }
        rest.addAll(at, moving)
        rest.forEachIndexed { i, b -> if (b.order != i) blockDao.update(b.copy(order = i)) }
    }

    /** Drop a run after [afterId] (null: the top of the page) — the group drag. The run's roots
     * become siblings of the block they land after; their subtrees follow. */
    fun moveBlocksAfter(ids: Set<Long>, afterId: Long?) = launchRecorded("move") { blocks ->
        val run = runOf(blocks, ids)
        val runIds = run.map { it.id }.toSet()
        if (run.isEmpty() || afterId in runIds) return@launchRecorded
        val ordered = outlineOf(blocks, expandAll = true).map { it.block }.filter { it.id !in runIds }.toMutableList()
        val target = afterId?.let { id -> blocks.firstOrNull { it.id == id } }
        // After the target's whole subtree, so the run lands beside it, not inside it.
        val at = if (target == null) 0 else {
            val outline = outlineOf(blocks.filter { it.id !in runIds }, expandAll = true)
            val i = outline.indexOfFirst { it.block.id == target.id }
            var j = i + 1
            while (j < outline.size && outline[j].depth > outline[i].depth) j++
            j
        }
        val newParent = target?.parentBlockId
        val now = Instant.now()
        val moved = run.map { b -> if (b.parentBlockId == null || b.parentBlockId !in runIds) b.copy(parentBlockId = newParent, updatedAt = now) else b }
        ordered.addAll(at.coerceIn(0, ordered.size), moved)
        ordered.forEachIndexed { i, b -> val want = b.copy(order = i); if (want != blocks.first { it.id == b.id }) blockDao.update(want) }
    }

    fun indentBlocks(ids: Set<Long>) = launchRecorded("indent") { blocks ->
        var current = blocks
        for (root in runOf(blocks, ids).filter { it.parentBlockId !in ids }) {
            val target = indentTargetFor(root, current) ?: continue
            if (target.id in ids) continue
            val updated = root.copy(parentBlockId = target.id, updatedAt = Instant.now())
            blockDao.update(updated)
            current = current.map { if (it.id == updated.id) updated else it }
        }
    }

    fun outdentBlocks(ids: Set<Long>) = launchRecorded("outdent") { blocks ->
        for (root in runOf(blocks, ids).filter { it.parentBlockId !in ids }.asReversed()) {
            val current = blockDao.getForPage(pageId)
            val block = current.first { it.id == root.id }
            val plan = outdentPlanFor(block, current)
            val now = Instant.now()
            if (plan == null) {
                if (block.parentBlockId != null) blockDao.update(block.copy(parentBlockId = null, updatedAt = now))
                continue
            }
            blockDao.update(block.copy(parentBlockId = plan.newParentId, updatedAt = now))
            for (id in plan.adoptedIds) {
                val sibling = current.first { it.id == id }
                blockDao.update(sibling.copy(parentBlockId = block.id, updatedAt = now))
            }
        }
    }

    fun changeTypes(ids: Set<Long>, newType: BlockType) = launchRecorded("turn into") { blocks ->
        val now = Instant.now()
        blocks.filter { it.id in ids && it.type != newType }.forEach { b ->
            blockDao.update(b.copy(type = newType, checked = if (newType == BlockType.TODO) (b.checked ?: false) else null, updatedAt = now))
        }
    }

    /** Copy a run; returns its Markdown for the system clipboard (`MarkdownWriter`). */
    fun copyBlocks(ids: Set<Long>): String {
        copied = runOf(blocks.value, ids)
        return MarkdownWriter.render(copied)
    }

    fun cutBlocks(ids: Set<Long>): String {
        val text = copyBlocks(ids)
        deleteBlocks(ids)
        return text
    }

    /** Paste the copied run after [afterId] (null: the top) as new rows — fresh ids and uids,
     * nesting rebuilt relative to the run's own roots, which land beside the block they follow.
     * [onDone] gets the new rows' ids so the screen can select them. */
    fun pasteBlocks(afterId: Long?, onDone: (Set<Long>) -> Unit = {}) {
        val run = copied
        if (run.isEmpty()) return
        launchRecorded("paste", onDone = { edit -> onDone(edit?.after?.map { it.id }?.toSet() ?: emptySet()) }) { blocks ->
            val ordered = blocks.sortedBy { it.order }
            val target = afterId?.let { id -> blocks.firstOrNull { it.id == id } }
            val at = if (target == null) 0 else ordered.indexOfFirst { it.id == target.id } + 1
            ordered.drop(at).forEach { b -> blockDao.update(b.copy(order = b.order + run.size)) }
            val runIds = run.map { it.id }.toSet()
            val newIds = mutableMapOf<Long, Long>()
            val now = Instant.now()
            run.forEachIndexed { i, b ->
                val parent = if (b.parentBlockId in runIds) newIds[b.parentBlockId] else target?.parentBlockId
                val id = blockDao.insert(b.copy(id = 0, uid = java.util.UUID.randomUUID().toString(), pageId = pageId, order = at + i, parentBlockId = parent, imagePath = null, createdAt = now, updatedAt = now))
                newIds[b.id] = id
            }
        }
    }

    /** §0.6.15's fourth verb — the reply's rows become bulleted blocks after [after] (at its
     * depth, as a paste lands), each row's parent the nearest shallower row before it; the root
     * carries §0.6.2's flag when [asMindMap], so the page draws the map at once. One recorded
     * edit, so one undo removes the whole subtree. Nothing is written until this is called. */
    fun insertOutline(after: Block, rows: List<OutlineRow>, asMindMap: Boolean) = launchRecorded("mind map") { blocks ->
        if (rows.isEmpty()) return@launchRecorded
        val ordered = blocks.sortedBy { it.order }
        val subtreeEnd = outlineOf(blocks, expandAll = true).let { outline ->
            val i = outline.indexOfFirst { it.block.id == after.id }
            if (i < 0) return@let after.order
            var end = i
            while (end + 1 < outline.size && outline[end + 1].depth > outline[i].depth) end++
            outline[end].block.order
        }
        val at = ordered.indexOfFirst { it.order == subtreeEnd }.let { if (it < 0) ordered.size else it + 1 }
        ordered.drop(at).forEach { b -> blockDao.update(b.copy(order = b.order + rows.size)) }
        val now = Instant.now()
        val parents = ArrayDeque<Long>()   // parents[d] = the id of the last row at depth d
        rows.forEachIndexed { i, row ->
            while (parents.size > row.depth) parents.removeLast()
            val parent = if (row.depth == 0) after.parentBlockId else parents.lastOrNull()
            val id = blockDao.insert(
                Block(pageId = pageId, type = BlockType.BULLETED_LIST_ITEM, order = at + i, parentBlockId = parent,
                    content = row.text, mindMap = asMindMap && i == 0, createdAt = now, updatedAt = now)
            )
            parents.addLast(id)
        }
    }

    fun duplicateBlocks(ids: Set<Long>, onDone: (Set<Long>) -> Unit = {}) {
        copyBlocks(ids)
        pasteBlocks(runOf(blocks.value, ids).lastOrNull()?.id, onDone)
    }

    fun undo() = applyEdit(undoStack.undo()?.inverse())
    fun redo() = applyEdit(undoStack.redo())

    /** Write [edit]'s `after` over the page — unrecorded; the stack already moved the entry. */
    private fun applyEdit(edit: BlockEdit?) {
        if (edit == null) return
        undoChanged()
        launchAndReindex {
            val plan = planApply(blockDao.getForPage(pageId), edit.after, edit.affectedIds)
            plan.delete.forEach { blockDao.delete(it) }
            plan.insert.forEach { blockDao.insert(it) }
            plan.update.forEach { blockDao.update(it) }
        }
    }

    private fun launchAndReindex(block: suspend () -> Unit) {
        if (contentLocked()) return
        viewModelScope.launch {
            // §0.6.13 — the body as it stands before this edit, at most once per window.
            pageHistory.captureBeforeEdit(pageId)
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
