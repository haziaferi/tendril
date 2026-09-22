package com.tendril.app.sync

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.ui.pages.PagesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * §3.1.2 — View-Only, on the one Pages surface that has never honoured it.
 *
 * The lock is a single global toggle in [ViewLockState], and every other Pages ViewModel gates
 * on it with the same three-line idiom (`private fun locked() = viewLockState.viewOnly.value`,
 * then `if (locked()) return` at the top of each mutation — see
 * [com.tendril.app.ui.pages.PageDetailViewModel] and
 * [com.tendril.app.ui.pages.PageDatabaseViewModel]). [PagesViewModel] takes that same
 * [ViewLockState] in its constructor, exposes it as `viewOnly`, hands the eye toggle its
 * `setViewOnly` — and then writes straight past it. Every mutation on the Pages hub is
 * unguarded.
 *
 * That is worse here than anywhere else in the app, and it is a *sync* defect rather than a
 * UI-polish one, which is why these tests live in this package:
 *
 *  - "Delete forever" (`PagesViewModel.kt:95`) does not soft-delete. It runs
 *    [PurgeRegistry.purgePage], which records a `PurgedKind.PAGE` tombstone and drops the row
 *    as one operation. The tombstone is *designed* to propagate: it travels in
 *    `purged_records.json` and `applyToLocalRecords` deletes the matching row on every peer
 *    that adopts it. So one mis-tap behind a lock the person believes is on destroys the page
 *    on every device they own, with nothing left anywhere to say it existed. It is the only
 *    write in Tendril that is unrecoverable by design.
 *  - Restore is worse still in shape, if not in blast radius: `PagesScreen.kt:465` and `:495`
 *    call `core.database.pageDao().restore(...)` *directly from a composable `onClick`*,
 *    bypassing [PagesViewModel] entirely. There is no gate to forget because there is no
 *    ViewModel method at all. This file therefore asserts against a `restore(pageIds)` that
 *    does not exist yet: the missing signature is the finding.
 *  - The four creates (`:53`, `:102`, `:116`, `:127`) each insert a Page — a new record the
 *    next export carries to every peer.
 *  - `openJournal` (`:146`) is the interesting one, and gets two tests rather than one. It is
 *    read-*and*-write: it creates a "Journal" root and a `journal/<date>` child on first open
 *    of a day, then reuses them forever after. Gating it wholesale would break navigation to a
 *    journal page that already exists, and reading is not writing. So the lock has to stop the
 *    lazy creation and nothing else.
 *
 * Shape of every locked test, matching `WritePathSyncTest`'s
 * "opening a page does not move its timestamp": set the lock, perform the action, assert the
 * store is exactly as it was. The unlocked half of each pair is not optional — a guard that
 * refuses everything is as broken as one that refuses nothing, and cheaper to ship by accident.
 *
 * Every test installs `Dispatchers.Main`: [PagesViewModel] mutates on `viewModelScope`, which
 * a JVM unit test does not have until it says so.
 */
class ViewOnlyPagesGuardTest {

    /** Older than any `Instant.now()` a write path can produce, so an unchanged `t0` is
     * unambiguously "nothing was written" rather than a rounding accident. */
    private val t0: Instant = Instant.ofEpochMilli(1_000L)

    /** The day these tests journal about. Fixed rather than `LocalDate.now()` so the page title
     * the assertions name is the same string on every run. */
    private val day: LocalDate = LocalDate.of(2026, 3, 14)

    /** Unconfined rather than Standard, for the reason `WritePathSyncTest` gives: every
     * mutation under test is `viewModelScope.launch { }` over in-memory fakes that never really
     * suspend, so running them eagerly on the calling thread lets each test read as the sequence
     * of user actions it describes — and, here, lets "nothing happened" be asserted immediately
     * after the call rather than after an advance that might itself be what swallowed the write. */
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun installMainDispatcher() = Dispatchers.setMain(mainDispatcher)

    @After
    fun restoreMainDispatcher() = Dispatchers.resetMain()

    /**
     * One device: its local store, the real [PagesViewModel] wired to the same DAOs the app
     * wires it to, and the engine that exports from and merges into it.
     *
     * Deliberately a second harness rather than an import: `WritePathSyncTest.Device` is private
     * to that file. It shares the public [FakePageStore] doubles with [PageMergeTest] and
     * [WritePathSyncTest] though, because the `ON DELETE CASCADE` behaviour that store models is
     * exactly what makes a purge unrecoverable — a fake where deleting a Page left its blocks
     * behind would understate the defect.
     */
    private class Device {
        val store = FakePageStore()
        val pageDao = FakePageDao(store)
        val blockDao = FakeBlockDao(store)
        val labelDao = FakeLabelDao(store)
        val pageDatabaseDao = FakePageDatabaseDao(store)
        val propertyDao = FakePropertyDao(store)
        val propertyValueDao = FakePropertyValueDao(store)
        val viewDao = FakePageDatabaseViewDao(store)
        val canvasDao = FakePageCanvasDao(store)
        val nodeDao = FakeCanvasNodeDao(store)
        val edgeDao = FakeCanvasEdgeDao(store)
        val relationDao = FakePageRelationDao(store)
        val ftsDao = FakePageFtsDao(store)

        val entryDao = FakeEntryDao()
        val completionDao = SilentEntryCompletionDao()
        val coordinator = RecordingEntryScheduleCoordinator()

        val contentRepository = PageContentRepository(pageDao, blockDao, ftsDao, FakeBlockFtsDao(store))
        val resolveEntryUseCase = ResolveEntryUseCase(entryDao, completionDao, coordinator)
        val templateManager = TemplateManager(pageDao, blockDao, pageDatabaseDao, propertyDao, canvasDao, nodeDao, edgeDao)
        val databaseSyncManager =
            DatabaseSyncManager(pageDao, pageDatabaseDao, propertyValueDao, entryDao, completionDao, resolveEntryUseCase)

        val viewLockState = ViewLockState()

        val purgedDao = FakePurgedRecordDao()
        val purgeRegistry = PurgeRegistry(purgedDao, pageDao, entryDao, FakeHabitDao(), propertyDao, coordinator)

        val engine = PagesSyncEngine(
            pageDao = pageDao,
            blockDao = blockDao,
            labelDao = labelDao,
            pageDatabaseDao = pageDatabaseDao,
            propertyDao = propertyDao,
            propertyValueDao = propertyValueDao,
            pageDatabaseViewDao = viewDao,
            pageCanvasDao = canvasDao,
            canvasNodeDao = nodeDao,
            canvasEdgeDao = edgeDao,
            pageRelationDao = relationDao,
            purgeRegistry = purgeRegistry,
            pageContentRepository = contentRepository,
            pageHistory = PageHistory(pageDao, blockDao, FakePageRevisionDao()),
        )

        /**
         * The Pages hub itself (§3.1) — the only surface that creates pages, canvases and whole
         * databases, and the only one that empties the Trash. Unscoped, unlike the per-page
         * ViewModels: a create is a write even though there is no page open to gate it against.
         */
        fun pages() = PagesViewModel(
            pageDao, pageDatabaseDao, propertyDao, ftsDao, labelDao, purgeRegistry, databaseSyncManager,
            templateManager, viewLockState, contentRepository, entryDao, resolveEntryUseCase,
        )

        fun lock() = viewLockState.setViewOnly(true)
    }

    private val a = Device()
    private val b = Device()

    /** One sync pass in one direction, exactly as the folder sync runs it: A writes its snapshot
     * out, B reads it in. Tombstones first, then records — the order `SnapshotSyncOrchestrator`
     * uses, so a record and the tombstone that kills it cannot cross in the same pass. */
    private suspend fun syncAtoB() {
        b.purgeRegistry.adopt(a.purgedDao.getAll())
        b.purgeRegistry.applyToLocalRecords()
        b.engine.mergePages(a.engine.exportPages())
    }

    private suspend fun seedPageOnA(title: String, kind: PageKind = PageKind.PAGE): Page {
        val id = a.store.seedPage(Page(title = title, kind = kind, createdAt = t0, updatedAt = t0))
        return a.pageDao.getById(id)!!
    }

    /** A page already in the Trash — the only state from which "Restore" and "Delete forever"
     * are reachable at all (§5.5.1). */
    private suspend fun seedTrashedPageOnA(title: String): Page {
        val page = seedPageOnA(title)
        a.pageDao.softDelete(page.id, t0)
        return a.pageDao.getById(page.id)!!
    }

    /** A saved template (§3.1.3) with one block of structure to clone. */
    private suspend fun seedTemplateOnA(): Page {
        val id = a.store.seedPage(Page(title = "Meeting notes", isTemplate = true, createdAt = t0, updatedAt = t0))
        a.blockDao.insert(Block(pageId = id, type = BlockType.PARAGRAPH, order = 0, content = "Agenda", createdAt = t0, updatedAt = t0))
        return a.pageDao.getById(id)!!
    }

    // ----------------------------------------------------------------- delete forever (§5.5.1.1)

    /**
     * The headline defect. `PagesViewModel.kt:95` goes straight to [PurgeRegistry.purgePage]
     * with no lock check, so View-Only does not stand between a mis-tap and the one write in the
     * app that cannot be undone.
     */
    @Test
    fun `delete forever does not purge a page while View-Only is on`() = runTest(mainDispatcher) {
        val page = seedTrashedPageOnA("Doomed")
        a.lock()

        a.pages().deleteForever(listOf(page.id))

        assertNotNull(
            "View-Only is on, so Delete forever must not run: PagesViewModel.deleteForever purges through PurgeRegistry with no locked() gate",
            a.pageDao.getById(page.id),
        )
        assertTrue(
            "and no PurgedKind.PAGE tombstone should exist either -- the tombstone is the propagating half of a purge",
            a.purgedDao.getAll().isEmpty(),
        )
    }

    /**
     * The same tap, seen from the other device. A tombstone is not a local fact: it travels in
     * `purged_records.json`, and `applyToLocalRecords` deletes the row it names wherever it
     * lands. An unguarded "Delete forever" therefore destroys the page on every device the
     * person owns, which is why the guard belongs in the ViewModel and not only in the confirm
     * dialog.
     */
    @Test
    fun `delete forever while View-Only is on does not destroy the page on the other device`() = runTest(mainDispatcher) {
        val page = seedTrashedPageOnA("Doomed")
        syncAtoB()
        assertNotNull("precondition: B holds the page before anything is deleted", b.pageDao.getByUid(page.uid))

        a.lock()
        a.pages().deleteForever(listOf(page.id))
        syncAtoB()

        assertNotNull(
            "a purge View-Only should have refused must never reach a peer -- the tombstone propagates and deletes B's row too",
            b.pageDao.getByUid(page.uid),
        )
    }

    @Test
    fun `delete forever purges a page when View-Only is off`() = runTest(mainDispatcher) {
        val page = seedTrashedPageOnA("Doomed")

        a.pages().deleteForever(listOf(page.id))

        assertNull("unlocked, Delete forever must still empty the Trash", a.pageDao.getById(page.id))
        assertEquals(
            "and must still record the tombstone that propagates the purge",
            listOf(page.uid),
            a.purgedDao.getAll().map { it.uid },
        )
    }

    // -------------------------------------------------------------------------- restore (§5.5.1)

    /**
     * Restore has no gate because it has no ViewModel method: `PagesScreen.kt:465` (the
     * multi-select "Restore (n)") and `:495` (the per-row "Restore") both reach
     * `core.database.pageDao().restore(...)` from inside a composable `onClick`, with the
     * [PagesViewModel] sitting right there unused. No enforcement idiom can reach that call
     * site, so the fix has to move the write behind `PagesViewModel.restore(pageIds)` first —
     * which is why this test names a signature that does not exist yet.
     */
    @Test
    fun `restore does not resurrect a trashed page while View-Only is on`() = runTest(mainDispatcher) {
        val page = seedTrashedPageOnA("Trashed")
        a.lock()

        a.pages().restore(listOf(page.id))

        assertNotNull(
            "View-Only is on, so Restore must leave the page in the Trash: PagesScreen calls pageDao().restore() straight from onClick, bypassing the ViewModel and every gate in it",
            a.pageDao.getById(page.id)?.deletedAt,
        )
        assertEquals(
            "and the page row must not move, since a restore rewrites updatedAt and would win the next merge",
            t0,
            a.pageDao.getById(page.id)?.updatedAt,
        )
    }

    @Test
    fun `restore resurrects a trashed page when View-Only is off`() = runTest(mainDispatcher) {
        val page = seedTrashedPageOnA("Trashed")

        a.pages().restore(listOf(page.id))

        assertNull("unlocked, Restore must still bring the page back out of the Trash", a.pageDao.getById(page.id)?.deletedAt)
    }

    // ------------------------------------------------------------- move to trash (B§13.4 14d)

    /** A row's menu (hover `···`, right-click, the phone's long-press) trashes through
     * [PagesViewModel.moveToTrash], a write the lock must stand in front of like the page's own. */
    @Test
    fun `move to trash from a row menu is refused while View-Only is on`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Kept")
        a.lock()

        a.pages().moveToTrash(page.id)

        assertNull("View-Only is on, so the row menu's Move to Trash must not soft-delete", a.pageDao.getById(page.id)?.deletedAt)
    }

    @Test
    fun `move to trash from a row menu soft-deletes when View-Only is off`() = runTest(mainDispatcher) {
        val page = seedPageOnA("Gone")

        a.pages().moveToTrash(page.id)

        assertNotNull("unlocked, the row menu's Move to Trash must land the page in the Trash", a.pageDao.getById(page.id)?.deletedAt)
    }

    // ------------------------------------------------------------------------- creates (§3.1.3)

    @Test
    fun `creating a blank page writes nothing while View-Only is on`() = runTest(mainDispatcher) {
        a.lock()
        var created: Long? = null

        a.pages().createBlankPage("New page") { created = it }

        assertEquals(
            "View-Only is on, so PagesViewModel.createBlankPage must not insert a Page",
            emptyList<String>(),
            a.pageDao.getAll().map { it.title },
        )
        assertNull("and must not navigate to a page it did not create", created)
    }

    @Test
    fun `creating a blank page still works when View-Only is off`() = runTest(mainDispatcher) {
        var created: Long? = null

        a.pages().createBlankPage("New page") { created = it }

        assertEquals(listOf("New page"), a.pageDao.getAll().map { it.title })
        assertEquals(a.pageDao.getAll().single().id, created)
    }

    @Test
    fun `creating a canvas writes nothing while View-Only is on`() = runTest(mainDispatcher) {
        a.lock()
        var created: Long? = null

        a.pages().createCanvas("Board") { created = it }

        assertEquals(
            "View-Only is on, so PagesViewModel.createCanvas must not insert a PageKind.CANVAS page",
            emptyList<String>(),
            a.pageDao.getAll().map { it.title },
        )
        assertNull("and must not navigate to a canvas it did not create", created)
    }

    @Test
    fun `creating a canvas still works when View-Only is off`() = runTest(mainDispatcher) {
        a.pages().createCanvas("Board") { }

        assertEquals(listOf(PageKind.CANVAS), a.pageDao.getAll().map { it.kind })
    }

    /**
     * A database is the largest single create on this screen: a Page, a
     * [com.tendril.app.data.pagedatabase.PageDatabase] row, and — as a To-do database — a
     * CHECKBOX property plus a Sync-to-Tasks binding through [DatabaseSyncManager]. Asserted on
     * all three tables, because a guard that stopped only the Page insert would leave orphan
     * schema behind.
     */
    @Test
    fun `creating a database writes nothing while View-Only is on`() = runTest(mainDispatcher) {
        a.lock()
        var created: Long? = null

        a.pages().createDatabase("Tasks", asToDoDatabase = true) { created = it }

        assertEquals(
            "View-Only is on, so PagesViewModel.createDatabase must not insert the database's page",
            emptyList<String>(),
            a.pageDao.getAll().map { it.title },
        )
        assertEquals("nor its PageDatabase row", 0, a.store.databases.size)
        assertEquals("nor the To-do shortcut's Done property", emptyList<String>(), a.propertyDao.getAll().map { it.name })
        assertNull("and must not navigate to a database it did not create", created)
    }

    @Test
    fun `creating a database still works when View-Only is off`() = runTest(mainDispatcher) {
        a.pages().createDatabase("Tasks", asToDoDatabase = true) { }

        assertEquals(listOf("Tasks"), a.pageDao.getAll().map { it.title })
        assertEquals(1, a.store.databases.size)
        assertEquals(listOf("Done"), a.propertyDao.getAll().map { it.name })
    }

    /**
     * "New from template" deep-clones through [TemplateManager], so it writes a Page *and* every
     * block of the template's structure. The template itself is untouched either way — it is the
     * thing being read.
     */
    @Test
    fun `creating a page from a template writes nothing while View-Only is on`() = runTest(mainDispatcher) {
        val template = seedTemplateOnA()
        a.lock()
        var created: Long? = null

        a.pages().createFromTemplate(template, "Monday standup") { created = it }

        assertEquals(
            "View-Only is on, so PagesViewModel.createFromTemplate must not clone the template into a new page",
            listOf("Meeting notes"),
            a.pageDao.getAll().map { it.title },
        )
        assertEquals("and none of its blocks should have been cloned either", 1, a.store.blocks.size)
        assertNull("and it must not navigate to a page it did not create", created)
    }

    @Test
    fun `creating a page from a template still works when View-Only is off`() = runTest(mainDispatcher) {
        val template = seedTemplateOnA()

        a.pages().createFromTemplate(template, "Monday standup") { }

        val clone = a.pageDao.getAll().single { it.title == "Monday standup" }
        assertFalse("the clone is a live page, not a second template", clone.isTemplate)
        assertEquals(listOf("Agenda"), a.blockDao.getForPage(clone.id).map { it.content })
    }

    // ---------------------------------------------------------------------- journal (§3.1.4)

    /**
     * Half (a) of the split. `openJournal` creates the permanent "Journal" root and the day's
     * page lazily on first open, and both are ordinary Page inserts the next export carries to
     * every peer. Under the lock, opening a day that has never been journalled must create
     * neither.
     */
    @Test
    fun `opening an unwritten journal day creates nothing while View-Only is on`() = runTest(mainDispatcher) {
        a.lock()
        var opened: Long? = null

        a.pages().openJournal(day) { opened = it }

        assertEquals(
            "View-Only is on, so PagesViewModel.openJournal must not lazily create the Journal root or the day's page",
            emptyList<String>(),
            a.pageDao.getAll().map { it.title },
        )
        assertNull("and there is nothing to open, so nothing should be navigated to", opened)
    }

    /**
     * Half (b), and the reason `openJournal` is not simply gated at the top like every other
     * mutation here: reading is not writing. A journal page that already exists must still open
     * under View-Only, exactly as any other existing page does — the lock stops edits, it does
     * not hide the notes.
     */
    @Test
    fun `opening an existing journal day still navigates while View-Only is on`() = runTest(mainDispatcher) {
        val root = seedPageOnA("Journal")
        val existingId = a.store.seedPage(Page(title = "journal/$day", parentId = root.id, createdAt = t0, updatedAt = t0))
        a.lock()
        var opened: Long? = null

        a.pages().openJournal(day) { opened = it }

        assertEquals(
            "View-Only must not block navigation to a journal page that already exists -- reading is not writing",
            existingId,
            opened,
        )
        assertEquals("and opening it must not create a second one", 2, a.pageDao.getAll().size)
        assertEquals(
            "nor move its timestamp, which would beat a real edit made on another device",
            t0,
            a.pageDao.getById(existingId)?.updatedAt,
        )
    }

    @Test
    fun `opening an unwritten journal day still creates it when View-Only is off`() = runTest(mainDispatcher) {
        var opened: Long? = null

        a.pages().openJournal(day) { opened = it }

        assertEquals(listOf("Journal", "journal/$day"), a.pageDao.getAll().map { it.title })
        assertEquals(a.pageDao.getAll().last().id, opened)
    }

    // ------------------------------------------------------------------ RED-phase scaffolding

    /**
     * Reached only because [ResolveEntryUseCase] and [DatabaseSyncManager] are constructor
     * arguments of the ViewModel under test; nothing here asserts on completions. Nested rather
     * than top-level so it cannot collide with the identically-shaped double in
     * `WritePathSyncTest.kt`, which is file-private there.
     */
    private class SilentEntryCompletionDao : EntryCompletionDao {
        private val rows = mutableListOf<EntryCompletion>()

        override suspend fun insert(completion: EntryCompletion): Long {
            rows += completion
            return rows.size.toLong()
        }

        override fun observeForEntry(entryId: Long): Flow<List<EntryCompletion>> =
            flowOf(rows.filter { it.entryId == entryId })
        /** §9.4 / S2 — the snapshot write pass republishes the whole table. */
        override suspend fun getAll(): List<EntryCompletion> = rows.toList()

        override suspend fun getByUid(uid: String): EntryCompletion? =
            rows.firstOrNull { it.uid == uid }


        override suspend fun deleteAll() { rows.clear() }
    }
}
