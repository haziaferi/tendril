package com.tendril.app.sync

import com.tendril.app.data.Converters
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * §9.4 — what a merge does with a value it has never heard of.
 *
 * Every enum in this schema persists as its `.name` String, through a `@TypeConverter` on the
 * Room side and a plain `String` field on the snapshot side. That is deliberate and it is why
 * adding an enum member costs no schema change at all — but it has a consequence nothing in the
 * codebase currently handles: **a newer build writes values an older build must read.** Two
 * devices on one Syncthing folder are routinely not on the same build, and `MainActivity` calls
 * `syncInBackground()` on every launch and every backgrounding, so this is the ordinary case
 * rather than a manual-sync curiosity.
 *
 * `SnapshotSyncOrchestrator.kt:17` already sets `ignoreUnknownKeys = true`, so an unknown
 * *field* is tolerated folder-wide. These tests pin the same posture for an unknown *value*,
 * under the policy this work adopted:
 *
 *  - the pass does not throw — one unrecognised value must not abort the merge for everything
 *    else in the batch;
 *  - nothing local is deleted or cleared before the incoming record has fully decoded;
 *  - the record is skipped for this pass and this device's local copy is left exactly as it is;
 *  - **and the page is suppressed from this pass's export.**
 *
 * That last clause is the one that makes quarantine different from a plain skip, and it is the
 * whole reason a plain skip is wrong. `SnapshotSyncOrchestrator.kt:232` exports *every* local
 * page over `pages/<uid>.json` unconditionally on every write pass, and `preserveLostPages`
 * (:356) only ever sees records `mergePages` returned. A merely-skipped record therefore means
 * this device rewrites its own stale copy over the peer's newer file on the very next pass,
 * with no `.tendril-lost-` copy kept — manufacturing exactly the data loss this work exists to
 * prevent. `a quarantined page is not overwritten by this device's stale copy on the export
 * pass` is that assertion, and it is the one to read first.
 *
 * Quarantine also has to be *visible*: silently dropping a peer's page every pass, forever,
 * with nothing said, is how someone discovers on the day it matters that two devices stopped
 * agreeing months ago. It is reported through the channel sync problems already travel on —
 * [SnapshotMergeResult], which `SyncCoordinator.kt:159` turns into `lastError` for Settings.
 */
class UnknownEnumQuarantineTest {

    @get:Rule val temp = TemporaryFolder()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // Valid UUIDs: `mergePages` refuses any uid that is not one, since a page uid becomes a
    // filename on the next write pass.
    private companion object {
        const val UID_A = "11111111-1111-4111-8111-111111111111"
        const val UID_B = "22222222-2222-4222-8222-222222222222"
        const val UID_DB = "33333333-3333-4333-8333-333333333333"
        const val UID_PROP = "44444444-4444-4444-8444-444444444444"
        const val UID_VIEW = "55555555-5555-4555-8555-555555555555"
        const val UID_NODE = "66666666-6666-4666-8666-666666666666"
        const val UID_NODE2 = "77777777-7777-4777-8777-777777777777"
        const val UID_BLOCK_A = "88888888-8888-4888-8888-888888888888"
        const val UID_BLOCK_B = "99999999-9999-4999-8999-999999999999"
        const val UID_ENTRY_LOCAL = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val UID_ENTRY_BAD = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val UID_ENTRY_GOOD = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        const val UID_HABIT_BAD = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
        const val UID_HABIT_GOOD = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"

        // Section 5g's hierarchy and database row. P is the unreadable one throughout; CHILD
        // and GRANDCHILD hang off it, ROW is a row of the unreadable DATABASE.
        const val UID_CHILD = "12121212-1212-4121-8121-121212121212"
        const val UID_GRANDCHILD = "13131313-1313-4131-8131-131313131313"
        const val UID_ROW = "14141414-1414-4141-8141-141414141414"
        const val UID_PROP_NEW = "15151515-1515-4151-8151-151515151515"
    }

    /**
     * One device: the twelve page DAOs over a single [FakePageStore] (so the `ON DELETE CASCADE`
     * chain behaves), the merge engine, and the orchestrator that reads and writes the folder.
     *
     * The same [FakeEntryDao]/[FakeHabitDao] instances reach both the [PurgeRegistry] and the
     * orchestrator — the assertions here are about rows surviving a pass, so both halves have to
     * be looking at the same rows.
     */
    private class Device(entries: List<Entry> = emptyList(), habits: List<Habit> = emptyList()) {
        val db = FakePageStore()
        val folder = InMemorySyncFileStore()

        val pageDao = FakePageDao(db)
        val blockDao = FakeBlockDao(db)
        val labelDao = FakeLabelDao(db)
        val pageDatabaseDao = FakePageDatabaseDao(db)
        val propertyDao = FakePropertyDao(db)
        val propertyValueDao = FakePropertyValueDao(db)
        val viewDao = FakePageDatabaseViewDao(db)
        val canvasDao = FakePageCanvasDao(db)
        val nodeDao = FakeCanvasNodeDao(db)
        val edgeDao = FakeCanvasEdgeDao(db)
        val relationDao = FakePageRelationDao(db)
        val ftsDao = FakePageFtsDao(db)

        val entryDao = FakeEntryDao(entries)
        val habitDao = FakeHabitDao(habits)
        val purgedDao = FakePurgedRecordDao()

        val registry = PurgeRegistry(
            purgedDao, pageDao, entryDao, habitDao, propertyDao, RecordingEntryScheduleCoordinator(),
        )

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
            purgeRegistry = registry,
            pageContentRepository = PageContentRepository(pageDao, blockDao, ftsDao, FakeBlockFtsDao(db)),
            pageHistory = PageHistory(pageDao, blockDao, FakePageRevisionDao()),
        )

        val orchestrator = SnapshotSyncOrchestrator(
            entryDao = entryDao,
            habitDao = habitDao,
            pageDao = pageDao,
            pagesSyncEngine = engine,
            purgeRegistry = registry,
            reminderDao = mockk(relaxed = true),
            entryCompletionDao = mockk(relaxed = true),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            localImages = InMemoryLocalImageStore(),
        )

        suspend fun blocksOf(uid: String): List<String> =
            pageDao.getByUid(uid)?.let { page -> blockDao.getForPage(page.id).map { it.content } } ?: emptyList()
    }

    // ------------------------------------------------------------------- record builders

    private fun pageRecord(
        uid: String,
        title: String,
        updatedAt: Long,
        kind: String = PageKind.PAGE.name,
        blocks: List<BlockSnapshotRecord> = emptyList(),
        database: PageDatabaseSnapshotRecord? = null,
        canvas: CanvasSnapshotRecord? = null,
        parentUid: String? = null,
        databaseUid: String? = null,
        propertyValues: List<PropertyValueSnapshotRecord> = emptyList(),
    ) = PageSnapshotRecord(
        uid = uid, title = title, kind = kind, parentUid = parentUid, databaseUid = databaseUid,
        createdAt = updatedAt, updatedAt = updatedAt, blocks = blocks,
        propertyValues = propertyValues, database = database, canvas = canvas,
    )

    private fun blockRecord(uid: String, content: String, type: String = "PARAGRAPH", at: Long = 1_000L) =
        BlockSnapshotRecord(uid = uid, type = type, order = 0, content = content, createdAt = at, updatedAt = at)

    /** An ordinary page that shares the batch with whatever is undecodable, carrying a block so
     * the assertion reaches Pass 5 — a bare row alone proves nothing, since Pass 1 commits those
     * before any of the later passes has a chance to throw. */
    private fun goodSibling(updatedAt: Long = 1_000L) = pageRecord(
        UID_B, "An ordinary page", updatedAt = updatedAt,
        blocks = listOf(blockRecord(UID_BLOCK_B, "still merged")),
    )

    private fun textNode(uid: String, text: String, type: String = "TEXT") = CanvasNodeSnapshotRecord(
        uid = uid, type = type, x = 0f, y = 0f, width = 180f, height = 90f,
        text = text, createdAt = 1_000L, updatedAt = 1_000L,
    )

    private fun entryRecord(uid: String, title: String, kind: String = "TASK", status: String? = "PENDING", source: String = "MANUAL", updatedAt: Long = 5_000L) =
        EntrySnapshotRecord(uid = uid, title = title, kind = kind, status = status, source = source, createdAt = updatedAt, updatedAt = updatedAt)

    private fun habitRecord(uid: String, title: String, frequency: String = "1:DAY", updatedAt: Long = 5_000L) =
        HabitSnapshotRecord(uid = uid, title = title, frequency = frequency, streak = 0, createdAt = updatedAt, updatedAt = updatedAt)

    private fun localEntry(uid: String, title: String, updatedAt: Long = 1_000L) = Entry(
        id = 1, uid = uid, title = title, kind = EntryKind.TASK,
        startDate = null, startTime = null, endDate = null, endTime = null,
        recurrenceRule = null, status = EntryStatus.PENDING,
        createdAt = Instant.ofEpochMilli(1_000), updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    private fun localHabit(uid: String, title: String, updatedAt: Long = 1_000L) = Habit(
        id = 1, uid = uid, title = title, frequency = HabitFrequency(1, IntervalUnit.DAY),
        createdAt = Instant.ofEpochMilli(1_000), updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    // =========================================================== 1. the destructive one

    /**
     * The one that destroys data rather than merely refusing it.
     *
     * `PagesSyncEngine.kt` Pass 5 calls `blockDao.deleteForPage(pageId)` (:426) and only *then*
     * `BlockType.valueOf(b.type)` (:431). By the time the unknown type throws, this device's own
     * blocks are already gone — and the throw escapes `mergePages`, so the rest of the pass
     * never runs either. Two losses from one unrecognised string.
     */
    @Test
    fun `a peer's unknown BlockType must not destroy this device's blocks`() = runBlocking {
        val d = Device()
        d.engine.mergePages(
            listOf(pageRecord(UID_A, "Mine", updatedAt = 1_000L, blocks = listOf(blockRecord(UID_BLOCK_A, "kept locally"))))
        )

        val thrown = runCatching {
            d.engine.mergePages(
                listOf(
                    // A newer build's block type, on a record that legitimately wins on time.
                    pageRecord(
                        UID_A, "Theirs, newer", updatedAt = 5_000L,
                        blocks = listOf(blockRecord(UID_BLOCK_A, "written by a newer build", type = "SUPER_CALLOUT")),
                    ),
                    goodSibling(updatedAt = 5_000L),
                )
            )
        }.exceptionOrNull()

        assertEquals(
            "Pass 5 deletes this page's blocks before it decodes the incoming ones — an unrecognised " +
                "block type therefore destroys local content that was never in question",
            listOf("kept locally"),
            d.blocksOf(UID_A),
        )
        assertEquals(
            "a quarantined record is skipped whole: the local copy stays exactly as it was",
            "Mine",
            d.pageDao.getByUid(UID_A)?.title,
        )
        assertNull("one unreadable value must not abort the merge: $thrown", thrown)
        assertEquals(
            "the rest of the pass must still complete for every other page in the batch",
            listOf("still merged"),
            d.blocksOf(UID_B),
        )
    }

    // ===================================================== 2. half-committed is not skipped

    /**
     * Pass 1 has already inserted the bare Page row (and, for a DATABASE, its `PageDatabase`
     * shell) by the time Pass 3's `PropertyType.valueOf` (:341/:344) throws. What is left behind
     * is a page with no schema, no views and no blocks — and it stays that way, because on every
     * later pass the same record is no longer *newer* than the row Pass 1 wrote, so it is not a
     * winner and Passes 3-5 skip it. Permanently empty, from one string.
     */
    @Test
    fun `an unknown PropertyType does not leave the page permanently empty`() = runBlocking {
        val d = Device()
        val batch = listOf(
            pageRecord(
                UID_DB, "Tasks", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                database = PageDatabaseSnapshotRecord(
                    properties = listOf(PropertySnapshotRecord(UID_PROP, "Owner", "LOOKUP", order = 0)),
                ),
            ),
            goodSibling(),
        )

        val thrown = runCatching {
            d.engine.mergePages(batch)
            // And again: every later pass sees the same record, which is what makes the empty
            // row permanent rather than merely temporary.
            d.engine.mergePages(batch)
        }.exceptionOrNull()

        assertNull("an unrecognised property type must not abort the merge: $thrown", thrown)
        assertNull(
            "a quarantined record commits nothing — a bare row that no later pass can ever fill " +
                "in is worse than no row at all",
            d.pageDao.getByUid(UID_DB),
        )
        assertEquals(
            "the rest of the pass must still complete",
            listOf("still merged"),
            d.blocksOf(UID_B),
        )
    }

    // ============================================ 3. every other enum on the page merge path

    /** Pass 1, `toBareEntity` (:500) — this one throws before any pass has begun. */
    @Test
    fun `an unknown PageKind is quarantined rather than aborting the pass`() = runBlocking {
        val d = Device()

        val thrown = runCatching {
            d.engine.mergePages(listOf(pageRecord(UID_A, "From a newer build", updatedAt = 1_000L, kind = "TIMELINE"), goodSibling()))
        }.exceptionOrNull()

        assertNull("PageKind.valueOf must not abort the merge: $thrown", thrown)
        assertEquals("the rest of the pass must still complete", listOf("still merged"), d.blocksOf(UID_B))
        assertNull("a quarantined record commits nothing", d.pageDao.getByUid(UID_A))
    }

    /** Pass 3, `ViewType.valueOf` (:361). */
    @Test
    fun `an unknown ViewType is quarantined rather than aborting the pass`() = runBlocking {
        val d = Device()

        val thrown = runCatching {
            d.engine.mergePages(
                listOf(
                    pageRecord(
                        UID_DB, "Tasks", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                        database = PageDatabaseSnapshotRecord(
                            properties = listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0)),
                            views = listOf(ViewSnapshotRecord(uid = UID_VIEW, name = "By quarter", viewType = "GANTT", order = 0)),
                        ),
                    ),
                    goodSibling(),
                )
            )
        }.exceptionOrNull()

        assertNull("ViewType.valueOf must not abort the merge: $thrown", thrown)
        assertEquals("the rest of the pass must still complete", listOf("still merged"), d.blocksOf(UID_B))
        assertNull("a quarantined record commits nothing", d.pageDao.getByUid(UID_DB))
    }

    /** Pass 3, `ViewFilter.Comparator.valueOf` (:365) — reached only once the filter's property
     * resolves, so the record carries the property the filter names. */
    @Test
    fun `an unknown ViewFilter Comparator is quarantined rather than aborting the pass`() = runBlocking {
        val d = Device()

        val thrown = runCatching {
            d.engine.mergePages(
                listOf(
                    pageRecord(
                        UID_DB, "Tasks", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                        database = PageDatabaseSnapshotRecord(
                            properties = listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0)),
                            views = listOf(
                                ViewSnapshotRecord(
                                    uid = UID_VIEW, name = "Matching", viewType = "TABLE",
                                    filter = ViewFilterSnapshot(UID_PROP, "MATCHES_REGEX", "^done"),
                                    order = 0,
                                )
                            ),
                        ),
                    ),
                    goodSibling(),
                )
            )
        }.exceptionOrNull()

        assertNull("ViewFilter.Comparator.valueOf must not abort the merge: $thrown", thrown)
        assertEquals("the rest of the pass must still complete", listOf("still merged"), d.blocksOf(UID_B))
        assertNull("a quarantined record commits nothing", d.pageDao.getByUid(UID_DB))
    }

    /** Pass 3, `SortDirection.valueOf` (:367). */
    @Test
    fun `an unknown SortDirection is quarantined rather than aborting the pass`() = runBlocking {
        val d = Device()

        val thrown = runCatching {
            d.engine.mergePages(
                listOf(
                    pageRecord(
                        UID_DB, "Tasks", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                        database = PageDatabaseSnapshotRecord(
                            properties = listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0)),
                            views = listOf(
                                ViewSnapshotRecord(
                                    uid = UID_VIEW, name = "Shuffled", viewType = "TABLE",
                                    sortPropertyUid = UID_PROP, sortDirection = "RANDOM", order = 0,
                                )
                            ),
                        ),
                    ),
                    goodSibling(),
                )
            )
        }.exceptionOrNull()

        assertNull("SortDirection.valueOf must not abort the merge: $thrown", thrown)
        assertEquals("the rest of the pass must still complete", listOf("still merged"), d.blocksOf(UID_B))
        assertNull("a quarantined record commits nothing", d.pageDao.getByUid(UID_DB))
    }

    /** Pass 4, `CanvasNodeType.valueOf` (:399). Pass 4 also deletes the canvas's existing nodes
     * and edges (:393-394) before it decodes the incoming ones, the same shape as Pass 5. */
    @Test
    fun `an unknown CanvasNodeType is quarantined rather than aborting the pass`() = runBlocking {
        val d = Device()

        val thrown = runCatching {
            d.engine.mergePages(
                listOf(
                    pageRecord(
                        UID_A, "Board", updatedAt = 1_000L, kind = PageKind.CANVAS.name,
                        canvas = CanvasSnapshotRecord(nodes = listOf(textNode(UID_NODE, "a sticky", type = "STICKY_NOTE"))),
                    ),
                    goodSibling(),
                )
            )
        }.exceptionOrNull()

        assertNull("CanvasNodeType.valueOf must not abort the merge: $thrown", thrown)
        assertEquals("the rest of the pass must still complete", listOf("still merged"), d.blocksOf(UID_B))
        assertNull("a quarantined record commits nothing", d.pageDao.getByUid(UID_A))
    }

    /** Pass 4, `CanvasArrowDirection.valueOf` (:409). */
    @Test
    fun `an unknown CanvasArrowDirection is quarantined rather than aborting the pass`() = runBlocking {
        val d = Device()

        val thrown = runCatching {
            d.engine.mergePages(
                listOf(
                    pageRecord(
                        UID_A, "Board", updatedAt = 1_000L, kind = PageKind.CANVAS.name,
                        canvas = CanvasSnapshotRecord(
                            nodes = listOf(textNode(UID_NODE, "from"), textNode(UID_NODE2, "to")),
                            edges = listOf(CanvasEdgeSnapshotRecord(UID_NODE, UID_NODE2, "SQUIGGLY", label = "leads to")),
                        ),
                    ),
                    goodSibling(),
                )
            )
        }.exceptionOrNull()

        assertNull("CanvasArrowDirection.valueOf must not abort the merge: $thrown", thrown)
        assertEquals("the rest of the pass must still complete", listOf("still merged"), d.blocksOf(UID_B))
        assertNull("a quarantined record commits nothing", d.pageDao.getByUid(UID_A))
    }

    // ========================================== 4. the Entry/Habit half, in SnapshotMappers

    /**
     * `SnapshotMappers.kt:58` — `EntryKind.valueOf(kind)` inside `toEntity`, called from
     * `SnapshotSyncOrchestrator.mergeEntryContent`. Entries travel as one array per file, so a
     * single unreadable record throws out of the loop and takes every *later* record in the same
     * file with it — and, being uncaught, the whole merge pass after it.
     */
    @Test
    fun `an unknown EntryKind quarantines one record, not the whole entries file`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Precious local task")))
        d.folder.putRoot(
            "entries_active.json",
            json.encodeToString(
                listOf(
                    entryRecord(UID_ENTRY_BAD, "From a newer build", kind = "MILESTONE"),
                    entryRecord(UID_ENTRY_GOOD, "An ordinary task"),
                )
            ),
        )

        val thrown = runCatching { d.orchestrator.readAndMerge(d.folder) }.exceptionOrNull()

        assertNull("EntryKind.valueOf must not abort the pass: $thrown", thrown)
        assertNotNull(
            "a record behind the unreadable one in the same file must still merge",
            d.entryDao.getByUid(UID_ENTRY_GOOD),
        )
        assertNull("the unreadable record itself is skipped", d.entryDao.getByUid(UID_ENTRY_BAD))
        assertEquals(
            "and nothing local is touched by a record that could not be read",
            listOf("Precious local task"),
            d.entryDao.getAll().filter { it.uid == UID_ENTRY_LOCAL }.map { it.title },
        )
    }

    /** `SnapshotMappers.kt:74` — `status?.let(EntryStatus::valueOf)`. */
    @Test
    fun `an unknown EntryStatus quarantines one record, not the whole entries file`() = runBlocking {
        val d = Device()
        d.folder.putRoot(
            "entries_active.json",
            json.encodeToString(
                listOf(
                    entryRecord(UID_ENTRY_BAD, "From a newer build", status = "DEFERRED"),
                    entryRecord(UID_ENTRY_GOOD, "An ordinary task"),
                )
            ),
        )

        val thrown = runCatching { d.orchestrator.readAndMerge(d.folder) }.exceptionOrNull()

        assertNull("EntryStatus.valueOf must not abort the pass: $thrown", thrown)
        assertNotNull("the readable record still merges", d.entryDao.getByUid(UID_ENTRY_GOOD))
        assertNull("the unreadable record itself is skipped", d.entryDao.getByUid(UID_ENTRY_BAD))
    }

    /** `SnapshotMappers.kt:105` — `IntervalUnit.valueOf(unit)`, out of a Habit's
     * `"<count>:<unit>"` frequency string. */
    @Test
    fun `an unknown IntervalUnit quarantines one habit, not the whole habits file`() = runBlocking {
        val d = Device()
        d.folder.putRoot(
            "habits.json",
            json.encodeToString(
                listOf(
                    habitRecord(UID_HABIT_BAD, "From a newer build", frequency = "1:FORTNIGHT"),
                    habitRecord(UID_HABIT_GOOD, "Make the bed"),
                )
            ),
        )

        val thrown = runCatching { d.orchestrator.readAndMerge(d.folder) }.exceptionOrNull()

        assertNull("IntervalUnit.valueOf must not abort the pass: $thrown", thrown)
        assertNotNull("the readable habit still merges", d.habitDao.getByUid(UID_HABIT_GOOD))
        assertNull("the unreadable habit itself is skipped", d.habitDao.getByUid(UID_HABIT_BAD))
    }

    /**
     * The in-repo precedent, and the shape everything above is being brought into line with:
     * `SnapshotMappers.kt:77` already wraps `EntrySource.valueOf` in `runCatching` and falls
     * back to `MANUAL`. Pinned here so the sweep that adds the other guards cannot quietly
     * remove the one that was already right.
     */
    @Test
    fun `an unknown EntrySource already falls back to MANUAL, and must keep doing so`() {
        val entry = entryRecord(UID_ENTRY_GOOD, "Synced from somewhere new", source = "OUTLOOK_CALENDAR")
            .toEntity(emptyMap(), emptyMap())

        assertEquals(EntrySource.MANUAL, entry.source)
        assertEquals("nothing else about the record is disturbed by the fallback", "Synced from somewhere new", entry.title)
    }

    // ================================================================= 5. export suppression

    /** A peer's page file this build cannot decode: newer than what is held locally, and
     * carrying a block type only a later build knows about. */
    private fun undecodablePeerPage(updatedAt: Long) = pageRecord(
        UID_A, "Theirs, newer", updatedAt = updatedAt,
        blocks = listOf(blockRecord(UID_BLOCK_A, "written by a newer build", type = "SUPER_CALLOUT")),
    )

    /**
     * The clause that makes quarantine different from skipping, and the reason a plain skip
     * would be worse than the bug it fixes.
     *
     * This device holds an *old* copy of the page. The folder holds a newer one it cannot
     * decode. Skip it and the write half of the very same pass exports every local page
     * unconditionally (`SnapshotSyncOrchestrator.kt:232`), so this device's stale copy lands on
     * top of the peer's newer file — and because the record never came back out of `mergePages`,
     * `preserveLostPages` (:356) keeps no `.tendril-lost-` copy of what it replaced. The peer's
     * work is simply gone, from every device, silently, on the next launch.
     *
     * The peer file must therefore come back byte-for-byte unchanged.
     */
    @Test
    fun `a quarantined page is not overwritten by this device's stale copy on the export pass`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID_A, "Mine, stale", updatedAt = 1_000L)))
        val peerFile = json.encodeToString(undecodablePeerPage(9_000L))
        d.folder.putPage("$UID_A.json", peerFile)

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        assertEquals(
            "this device must not publish its stale copy over a peer file it could not read — " +
                "that destroys the peer's newer page on every device, with no lost-copy kept",
            peerFile,
            d.folder.readPage("$UID_A.json")?.toString(Charsets.UTF_8),
        )
    }

    /** Suppression is per page, not a blanket refusal to publish: one undecodable file must not
     * stop this device sharing everything else it holds, or a single unknown value from one peer
     * would freeze the whole folder. */
    @Test
    fun `export suppression covers only the quarantined page`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID_A, "Mine, stale", updatedAt = 1_000L), pageRecord(UID_B, "Mine, fine", updatedAt = 1_000L)))
        d.folder.putPage("$UID_A.json", json.encodeToString(undecodablePeerPage(9_000L)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        assertTrue(
            "every other page still publishes — the pages written were ${d.folder.pageNames()}",
            "$UID_B.json" in d.folder.pageNames(),
        )
    }

    // ============================================ 5b. the folder-wide files: republish verbatim
    //
    // Suppression by uid is enough for pages, because a page is one file: skip the write and the
    // peer's copy is left exactly where it lies. Entries, habits and tombstones have no such
    // granularity — each family is ONE folder-wide file that every device overwrites in full from
    // its own rows, so a record the merge never adopted is not merely unpublished, it is deleted
    // from the folder for everyone.
    //
    // That makes this the one regression a quarantine can introduce rather than fix: at HEAD the
    // same input threw out of the merge, the pass aborted before the write half ever ran, and the
    // peer's record survived untouched. A loud abort became silent loss.
    //
    // Skipping the whole file instead would be the mirror-image mistake — one unknown value from
    // one peer would stop this device publishing every other entry it holds. So the device
    // republishes the raw record verbatim: it decodes as JSON perfectly well (every field is a
    // plain String or number) and only the enum lookup inside `toEntity` failed, so putting back
    // what it could not interpret costs nothing and interprets nothing.
    //
    // These assert on the file's contents, not on the in-memory tally. The tally is what the page
    // tests above already pin, and the tally was never what broke.

    @Test
    fun `a quarantined peer entry is republished verbatim, not erased from the folder file`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Precious local task")))
        val peerRecord = entryRecord(UID_ENTRY_BAD, "From a newer build", kind = "MILESTONE")
        d.folder.putRoot(
            "entries_active.json",
            json.encodeToString(listOf(peerRecord, entryRecord(UID_ENTRY_GOOD, "An ordinary task"))),
        )

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        val published = json.decodeFromString<List<EntrySnapshotRecord>>(
            d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
        )
        assertEquals(
            "entries_active.json is rewritten in full from this device's own rows, and the record " +
                "it could not read was never among them — so it has to be put back exactly as it " +
                "arrived, or quarantining it destroys it on every device in the folder",
            peerRecord,
            published.singleOrNull { it.uid == UID_ENTRY_BAD },
        )
        assertEquals(
            "and every readable entry still publishes beside it — holding the whole file back " +
                "would freeze the folder over one unknown value",
            listOf("An ordinary task", "Precious local task"),
            published.filter { it.uid != UID_ENTRY_BAD }.map { it.title }.sorted(),
        )
    }

    @Test
    fun `a quarantined peer habit is republished verbatim, not erased from the folder file`() = runBlocking {
        val d = Device(habits = listOf(localHabit(UID_HABIT_GOOD, "Make the bed")))
        val peerRecord = habitRecord(UID_HABIT_BAD, "From a newer build", frequency = "1:FORTNIGHT")
        d.folder.putRoot("habits.json", json.encodeToString(listOf(peerRecord)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a habit it cannot decode: $thrown", thrown)
        val published = json.decodeFromString<List<HabitSnapshotRecord>>(
            d.folder.readRoot("habits.json")!!.toString(Charsets.UTF_8)
        )
        assertEquals(
            "habits.json is the same unconditional whole-file rewrite as entries_active.json",
            peerRecord,
            published.singleOrNull { it.uid == UID_HABIT_BAD },
        )
        assertEquals(
            "this device's own habits publish normally alongside it",
            listOf("Make the bed"),
            published.filter { it.uid != UID_HABIT_BAD }.map { it.title },
        )
    }

    /**
     * §5.5.1.1 — the same shape applied to delete instructions, which is where losing a record
     * costs the most. A dropped Entry is a delayed arrival: some later pass merges it. A dropped
     * tombstone is *undone work* — every device that had already carried the purge out takes the
     * record straight back from the next peer whose file never mentioned the deletion.
     */
    @Test
    fun `a purge tombstone of an unreadable kind is republished verbatim, not erased`() = runBlocking {
        val d = Device()
        val unreadable = PurgedRecordSnapshot(kind = "WORKSPACE", uid = UID_A, purgedAt = 7_000L)
        val readable = PurgedRecordSnapshot(kind = "HABIT", uid = UID_HABIT_BAD, purgedAt = 6_000L)
        d.folder.putRoot("purged_records.json", json.encodeToString(listOf(unreadable, readable)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an unrecognised PurgedKind must not abort the pass: $thrown", thrown)
        val published = json.decodeFromString<List<PurgedRecordSnapshot>>(
            d.folder.readRoot("purged_records.json")!!.toString(Charsets.UTF_8)
        )
        assertEquals(
            "the tombstone this build has no PurgedKind for is skipped locally — it always was — " +
                "but purged_records.json is then rewritten from the registry it never entered, " +
                "which erases the deletion from the folder and resurrects what it was deleting",
            unreadable,
            published.singleOrNull { it.uid == UID_A },
        )
        assertEquals(
            "the tombstones this build did adopt still travel",
            listOf(readable),
            published.filter { it.uid != UID_A },
        )
    }

    // ================================================ 5c. page_relations.json, the third omission
    //
    // Suppression has now been implemented three times, and each round an adversarial reader found
    // one more folder-wide file that had been forgotten: pages first, then entries/habits/purged,
    // then this one. What made `page_relations.json` the hardest to see is that **no value in it is
    // unreadable**. The file decodes perfectly; the loss is second-order. A peer on a newer build
    // creates page P with a `PageKind` this build has no member for and links Q -> P. P is
    // quarantined, so it is never inserted locally; `PagesSyncEngine.mergeRelations` drops any edge
    // whose endpoint is missing locally, so Q -> P never reaches Room and is absent from
    // `exportRelations()`; and the write pass publishes that local view over the file. The link is
    // gone from the folder, for everyone.
    //
    // At HEAD the same input threw out of `mergePages` before the write half ran, so the link
    // survived. That makes it a regression introduced by quarantining, which is the one thing this
    // work must not do — and the reason the fix for it is a structure (`FolderArrayFile`, and the
    // two exhaustive `when`s over it) rather than a sixth special case.

    @Test
    fun `a peer's link to a quarantined page is not erased from page_relations`() = runBlocking {
        val d = Device()
        // Q is this device's. P is the peer's, and only a later build can read it.
        d.engine.mergePages(listOf(pageRecord(UID_B, "Q, mine", updatedAt = 1_000L)))
        d.folder.putPage(
            "$UID_A.json",
            json.encodeToString(pageRecord(UID_A, "P, from a newer build", updatedAt = 9_000L, kind = "TIMELINE")),
        )
        val link = PageRelationSnapshotRecord(fromPageUid = UID_B, toPageUid = UID_A, createdAt = 1_000L)
        d.folder.putRoot("page_relations.json", json.encodeToString(listOf(link)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a page it cannot decode: $thrown", thrown)
        assertEquals(
            "the edge points at a page this build quarantined, so it can never resolve to two " +
                "local rows and can never appear in exportRelations() — publishing that view " +
                "deletes the peer's link from the folder on every device",
            listOf(link),
            json.decodeFromString<List<PageRelationSnapshotRecord>>(
                d.folder.readRoot("page_relations.json")!!.toString(Charsets.UTF_8)
            ),
        )
    }

    /**
     * The same rule seen from the conflict sweep. `mergeRelationsContent` used to return true for
     * any parseable JSON — including a file every one of whose edges it had just dropped — and the
     * sweep deletes a sibling it believes it absorbed. A `.sync-conflict-` file is the copy that
     * *lost* the race, so that is the only copy of those edges being destroyed unread.
     */
    @Test
    fun `a relations conflict sibling whose edges were all dropped is not deleted`() = runBlocking {
        val d = Device()
        val sibling = "page_relations.sync-conflict-20260101-123456-DEVICEID.json"
        d.folder.putRoot(
            sibling,
            json.encodeToString(listOf(PageRelationSnapshotRecord(UID_A, UID_B, 1_000L))),
        )

        d.orchestrator.readAndMerge(d.folder)

        assertTrue(
            "neither endpoint exists on this device, so nothing in the file was absorbed — a file " +
                "you did not take in is not a file you may delete. Root files left: " +
                "${d.folder.rootNames()}",
            sibling in d.folder.rootNames(),
        )
    }

    // ============================================== 5d. "verbatim" has to mean verbatim
    //
    // `json` is configured `ignoreUnknownKeys = true` and every snapshot record is a plain data
    // class with no catch-all, so decoding a peer's element into one *strips* any field a newer
    // build added. Holding the decoded record and re-encoding it therefore republished a quietly
    // lossy copy while the KDoc over it promised "byte-equivalent" — and a third device adopting
    // that copy loses the field permanently, with nothing reporting it. The held records are raw
    // `JsonElement`s for exactly this reason: nothing round-trips through a data class at all.

    @Test
    fun `a quarantined record is republished with the fields this build has no class for intact`() = runBlocking {
        val d = Device()
        // A newer build's entry: an EntryKind this one cannot read *and* a field it has never
        // heard of. Written as text rather than through EntrySnapshotRecord, because the whole
        // point is that this shape cannot survive a trip through that class.
        val peerText = """
            [{"uid":"$UID_ENTRY_BAD","title":"From a newer build","kind":"MILESTONE",
              "priority":"CRITICAL","createdAt":5000,"updatedAt":5000}]
        """.trimIndent()
        d.folder.putRoot("entries_active.json", peerText)

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        val published = json.parseToJsonElement(
            d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
        ).jsonArray.single().jsonObject
        assertEquals(
            "the record has to come back exactly as it arrived. Decoding it into this build's " +
                "EntrySnapshotRecord drops `priority` on the floor, and republishing that is a " +
                "silent downgrade every other device then adopts as the truth",
            json.parseToJsonElement(peerText).jsonArray.single().jsonObject,
            published,
        )
    }

    // ====================================== 5e. entries_archived, and the pooling across both files

    /**
     * `entries_archived.json` shipped with the rest of the suppression and with no test at all —
     * the same class of gap as the file that was left out entirely, one step further along.
     *
     * A held record goes back into the file it *arrived* in, never the one this device would have
     * filed it under: active-vs-archived is a property of the record, and this build could not read
     * the record. Choosing a file for it would be interpreting it after all.
     */
    @Test
    fun `a quarantined peer entry in entries_archived is republished into that same file`() = runBlocking {
        val d = Device()
        val peerRecord = entryRecord(UID_ENTRY_BAD, "From a newer build", kind = "MILESTONE", status = "DONE")
        d.folder.putRoot(
            "entries_archived.json",
            json.encodeToString(listOf(peerRecord, entryRecord(UID_ENTRY_GOOD, "A finished task", status = "DONE"))),
        )

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        val archived = json.decodeFromString<List<EntrySnapshotRecord>>(
            d.folder.readRoot("entries_archived.json")!!.toString(Charsets.UTF_8)
        )
        assertEquals(
            "entries_archived.json is the same unconditional whole-file rewrite entries_active.json " +
                "is, and had no test proving the held records ever reached it",
            peerRecord,
            archived.singleOrNull { it.uid == UID_ENTRY_BAD },
        )
        assertEquals(
            "the readable archived record still publishes beside it",
            listOf("A finished task"),
            archived.filter { it.uid != UID_ENTRY_BAD }.map { it.title },
        )
        val active = json.decodeFromString<List<EntrySnapshotRecord>>(
            d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
        )
        assertTrue(
            "and it is not also filed under active — this build never read it, so it has no " +
                "opinion about which heading it belongs under: ${active.map { it.uid }}",
            active.none { it.uid == UID_ENTRY_BAD },
        )
    }

    /**
     * The pooling that stops one uid appearing in both entry files at once — flagged by the last
     * reviewer as the least-verified line in the previous round, since it was an unprompted design
     * decision with nothing exercising it.
     *
     * The peer's record says "archived" where this device still holds the row active, and it won
     * the timestamp race, which is why the merge tried to decode it at all. Suppressing per-file
     * would silence only the archived side: this device would publish its stale *active* row
     * alongside the held archived one, and the folder would carry the same uid twice with two
     * different states — a divergence no later pass resolves, because both copies keep being
     * rewritten from the two sides that disagree.
     */
    @Test
    fun `a uid held out of entries_archived also silences this device's active row for it`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Mine, stale and active")))
        val peerRecord = entryRecord(
            UID_ENTRY_LOCAL, "Theirs, archived and newer",
            kind = "MILESTONE", status = "DONE", updatedAt = 9_000L,
        )
        d.folder.putRoot("entries_archived.json", json.encodeToString(listOf(peerRecord)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        val active = json.decodeFromString<List<EntrySnapshotRecord>>(
            d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
        )
        val archived = json.decodeFromString<List<EntrySnapshotRecord>>(
            d.folder.readRoot("entries_archived.json")!!.toString(Charsets.UTF_8)
        )
        assertEquals(
            "the held record is republished under the heading it arrived under",
            peerRecord,
            archived.singleOrNull { it.uid == UID_ENTRY_LOCAL },
        )
        assertFalse(
            "and this device's own row for that uid stands aside — suppression pools across both " +
                "entry files, or one uid ends up in two of them at once: active held " +
                "${active.map { it.uid to it.title }}",
            active.any { it.uid == UID_ENTRY_LOCAL },
        )
    }

    // ================================ 5f. a tombstone must not settle a comparison it never made

    /**
     * §5.5.1.1 — a purge and an edit are settled by comparing their two timestamps, and
     * `PurgeRegistry` supersedes the tombstone when the record turns out to be newer. That
     * comparison happens inside `mergePages`, which a page file this build cannot *parse* never
     * reaches: the uid is quarantined by filename and nothing else about the file is ever read.
     *
     * The write pass then deleted the file anyway, on the strength of an older tombstone that was
     * never given the chance to be superseded — an outright erasure, of the one file whose content
     * nobody on this device has seen. It needs both conditions to bite, which is presumably why it
     * survived three passes of review. The tombstone still travels, so a device that *can* read the
     * page settles the comparison properly and the file goes then.
     */
    @Test
    fun `a tombstone does not delete a page file this build could not parse`() = runBlocking {
        val d = Device()
        // Truncated mid-object: well-formed enough to be in the folder, undecodable here.
        d.folder.putPage("$UID_A.json", """{"uid":"$UID_A","title":"From a newer build",""")
        val tombstone = PurgedRecordSnapshot(kind = "PAGE", uid = UID_A, purgedAt = 7_000L)
        d.folder.putRoot("purged_records.json", json.encodeToString(listOf(tombstone)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an unparseable page file must not abort the pass: $thrown", thrown)
        assertTrue(
            "the tombstone may be older than the page's own updatedAt — nothing here can tell, " +
                "because the page never decoded far enough to carry one. Deleting on half a " +
                "comparison destroys the file on every device: pages left were ${d.folder.pageNames()}",
            "$UID_A.json" in d.folder.pageNames(),
        )
        assertEquals(
            "and the tombstone still travels, so a device that can read the page settles it",
            listOf(tombstone),
            json.decodeFromString<List<PurgedRecordSnapshot>>(
                d.folder.readRoot("purged_records.json")!!.toString(Charsets.UTF_8)
            ),
        )
    }

    // ================================================ 5g. quarantine has to travel along references
    //
    // Suppressing the record this build could not read does nothing about the damage that arrives
    // through a *different* record — one that decodes perfectly and merely points at the
    // quarantined one. Adopting that record means resolving the pointer to null and then
    // republishing the nulled result over the folder, which is the same folder-wide erasure
    // quarantine exists to prevent, arriving one hop away from where anyone was looking.
    //
    // Every assertion below reads the file back after a full pass. The in-memory tally was right
    // and the folder wrong twice in this work already.

    /** Peer's page P: perfectly well-formed, and only a later build has this `PageKind`. */
    private fun unreadablePeerPage(uid: String = UID_A, title: String = "P, from a newer build") =
        pageRecord(uid, title, updatedAt = 9_000L, kind = "WHITEBOARD")

    private suspend fun PageSnapshotRecord.publishedIn(store: InMemorySyncFileStore): PageSnapshotRecord? =
        store.readPage("$uid.json")?.toString(Charsets.UTF_8)?.let { json.decodeFromString<PageSnapshotRecord>(it) }

    /**
     * CRITICAL 1 — the hierarchy.
     *
     * P carries a `PageKind` this build has no member for, so it is quarantined and never
     * inserted; `uidToId["P"]` is therefore empty. Q is readable, newer than anything local, and
     * wins outright — so Pass 2 computes `parentId = null` and writes it, and the export pass
     * republishes Q at the root. One string nobody could read moves a whole subtree, on every
     * device in the folder, and HEAD did not do this: there the same input threw out of the merge
     * before the write half ran.
     */
    @Test
    fun `a page whose parent this build cannot read is not re-parented to the root`() = runBlocking {
        val d = Device()
        val parent = unreadablePeerPage()
        val child = pageRecord(UID_CHILD, "Q", updatedAt = 9_000L, parentUid = UID_A)
        d.folder.putPage("$UID_A.json", json.encodeToString(parent))
        d.folder.putPage("$UID_CHILD.json", json.encodeToString(child))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a page it cannot decode: $thrown", thrown)
        assertEquals(
            "Q's parent is a page this pass quarantined, so it cannot resolve and cannot heal — " +
                "adopting Q means publishing it at the root, which destroys the hierarchy for " +
                "every device. Q has to be quarantined with its parent",
            UID_A,
            child.publishedIn(d.folder)?.parentUid,
        )
        assertNull(
            "and nothing local is written for it either — a row committed with a null parent is " +
                "what the export then reads back",
            d.pageDao.getByUid(UID_CHILD),
        )
    }

    /**
     * …and to a fixpoint, not one hop. The grandchild's parent is the *child*, which is readable
     * in itself and quarantined only because of what it points at. Propagating a single level
     * leaves the grandchild adopted with a null parent and republished that way — the same loss,
     * one generation down, which is exactly how a hierarchy fails.
     */
    @Test
    fun `quarantine propagates down a chain of references, not just one hop`() = runBlocking {
        val d = Device()
        val child = pageRecord(UID_CHILD, "Q", updatedAt = 9_000L, parentUid = UID_A)
        val grandchild = pageRecord(UID_GRANDCHILD, "G", updatedAt = 9_000L, parentUid = UID_CHILD)
        d.folder.putPage("$UID_A.json", json.encodeToString(unreadablePeerPage()))
        d.folder.putPage("$UID_CHILD.json", json.encodeToString(child))
        d.folder.putPage("$UID_GRANDCHILD.json", json.encodeToString(grandchild))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a page it cannot decode: $thrown", thrown)
        assertEquals(
            "G points at Q, and Q was withheld — so G's parent is just as unresolvable as Q's " +
                "was. One level of propagation moves the loss down a generation instead of " +
                "stopping it",
            UID_CHILD,
            grandchild.publishedIn(d.folder)?.parentUid,
        )
    }

    /**
     * The other half of the rule, and the one that keeps it proportionate: a quarantined page this
     * device **already holds a row for** resolves perfectly well. Quarantining on the reference
     * alone would freeze whole subtrees over a page every device has had for months, every pass,
     * until someone updated the app.
     */
    @Test
    fun `a reference to a quarantined page this device already holds still merges`() = runBlocking {
        val d = Device()
        // P has been on this device since before the peer rewrote it in a shape only a newer
        // build can read.
        d.engine.mergePages(listOf(pageRecord(UID_A, "P, mine", updatedAt = 1_000L)))
        d.folder.putPage("$UID_A.json", json.encodeToString(unreadablePeerPage()))
        val child = pageRecord(
            UID_CHILD, "Q", updatedAt = 9_000L, parentUid = UID_A,
            blocks = listOf(blockRecord(UID_BLOCK_B, "merged normally")),
        )
        d.folder.putPage("$UID_CHILD.json", json.encodeToString(child))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a page it cannot decode: $thrown", thrown)
        assertEquals(
            "the id Q's parentUid needs is right here — quarantine is about references that would " +
                "dangle, not about references that merely name an unreadable record",
            listOf("merged normally"),
            d.blocksOf(UID_CHILD),
        )
        assertEquals(
            "and Q republishes with its parent intact, because the merge resolved it",
            UID_A,
            child.publishedIn(d.folder)?.parentUid,
        )
    }

    /**
     * CRITICAL 2 — the largest, because it destroys content somebody typed.
     *
     * This device already has database D with one column. The peer adds a second column whose
     * `PropertyType` only a newer build knows, and fills it in on row R. D is quarantined, so the
     * new column is never inserted and `propertyUidToId` cannot resolve it — but R itself decodes
     * perfectly, wins on time, and merges. Pass 5 clears the row's cells and reinserts only the
     * ones it can place; the export then republishes R without the other. The person's cell is
     * gone from the folder, on every device, and nothing says so.
     */
    @Test
    fun `a row is not merged without the column its cell belongs to`() = runBlocking {
        val d = Device()
        val known = PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0)
        d.engine.mergePages(
            listOf(
                pageRecord(
                    UID_DB, "People", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                    database = PageDatabaseSnapshotRecord(properties = listOf(known)),
                )
            )
        )
        // The peer's newer schema: the same column plus one this build has no PropertyType for.
        d.folder.putPage(
            "$UID_DB.json",
            json.encodeToString(
                pageRecord(
                    UID_DB, "People", updatedAt = 9_000L, kind = PageKind.DATABASE.name,
                    database = PageDatabaseSnapshotRecord(
                        properties = listOf(known, PropertySnapshotRecord(UID_PROP_NEW, "Owner", "LOOKUP", order = 1)),
                    ),
                )
            ),
        )
        val row = pageRecord(
            UID_ROW, "Ada", updatedAt = 9_000L, databaseUid = UID_DB,
            propertyValues = listOf(
                PropertyValueSnapshotRecord(UID_PROP, "Doing"),
                PropertyValueSnapshotRecord(UID_PROP_NEW, "Ada Lovelace"),
            ),
        )
        d.folder.putPage("$UID_ROW.json", json.encodeToString(row))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a database it cannot decode: $thrown", thrown)
        assertEquals(
            "the row's cells outlive the column definition this build could not read. Merging the " +
                "row anyway drops the value it cannot place and republishes the row without it — " +
                "and a cell somebody typed is not something to lose to a schema race",
            listOf("Doing", "Ada Lovelace"),
            row.publishedIn(d.folder)?.propertyValues?.map { it.value },
        )
    }

    /** The same loss by the shorter route: the whole database page is unreadable, so the row's
     * `databaseUid` is what dangles. Without this the row merges as a page belonging to nothing,
     * and republishes with `databaseUid` null and every cell gone. */
    @Test
    fun `a row whose whole database page is unreadable is not merged`() = runBlocking {
        val d = Device()
        d.folder.putPage(
            "$UID_DB.json",
            json.encodeToString(
                pageRecord(
                    UID_DB, "People", updatedAt = 9_000L, kind = PageKind.DATABASE.name,
                    database = PageDatabaseSnapshotRecord(
                        properties = listOf(PropertySnapshotRecord(UID_PROP_NEW, "Owner", "LOOKUP", order = 0)),
                    ),
                )
            ),
        )
        val row = pageRecord(
            UID_ROW, "Ada", updatedAt = 9_000L, databaseUid = UID_DB,
            propertyValues = listOf(PropertyValueSnapshotRecord(UID_PROP_NEW, "Ada Lovelace")),
        )
        d.folder.putPage("$UID_ROW.json", json.encodeToString(row))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a database it cannot decode: $thrown", thrown)
        assertEquals(
            "a row detached from its database is not a row — it publishes back with databaseUid " +
                "null and no cells at all",
            row,
            row.publishedIn(d.folder),
        )
    }

    /**
     * CRITICAL 2, by the route the local-referent refinement was blind to.
     *
     * "A reference is dangling only if the referent was withheld **and** this device holds no row
     * for it" is right and has to stay — the test above it depends on it. But it asks its question
     * about the *page*, and a database page is not only a page. This device has held `D` for
     * months, so the page-level answer is "resolves fine"; the file the peer just wrote is one
     * this build cannot parse into a record at all, and it is where the column `C` is defined. So
     * the cell under `C` on row `R` resolves against nothing, Pass 5 drops it, and the export
     * republishes `R` with the value gone from every device.
     *
     * The variant where `D` merely carries an unreadable `PropertyType` is already covered, and
     * differs in one decisive way: that record *decoded*, so its column uids were legible and went
     * into the withheld set. Here there is no record to read them off, which is why the question
     * has to be asked from the row's side instead — a cell whose column neither this device nor
     * anything in this batch defines, on a row whose own database was withheld.
     */
    @Test
    fun `a cell for a column only the unparseable database file defines is not dropped`() = runBlocking {
        val d = Device()
        // D has been on this device since before the peer rewrote it, with one column.
        d.engine.mergePages(
            listOf(
                pageRecord(
                    UID_DB, "People", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                    database = PageDatabaseSnapshotRecord(
                        properties = listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0)),
                    ),
                )
            )
        )
        // The peer's newer D: truncated mid-object here, and whatever a later build made of it in
        // the folder. Either way this build never sees the column it added.
        d.folder.putPage("$UID_DB.json", """{"uid":"$UID_DB","title":"People","kind":"DATABASE",""")
        val row = pageRecord(
            UID_ROW, "Ada", updatedAt = 9_000L, databaseUid = UID_DB,
            propertyValues = listOf(
                PropertyValueSnapshotRecord(UID_PROP, "Doing"),
                PropertyValueSnapshotRecord(UID_PROP_NEW, "Ada Lovelace"),
            ),
        )
        d.folder.putPage("$UID_ROW.json", json.encodeToString(row))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an unparseable database file must not abort the pass: $thrown", thrown)
        assertEquals(
            "the column this cell belongs to exists only inside the file this build could not " +
                "parse, so merging the row drops the value and republishes the row without it. " +
                "That the database page itself is local answers a different question from the " +
                "one the cell is asking",
            listOf("Doing", "Ada Lovelace"),
            row.publishedIn(d.folder)?.propertyValues?.map { it.value },
        )
        assertNull(
            "and nothing local is written for the row either — a row committed with the cell " +
                "already dropped is what the export then reads back",
            d.pageDao.getByUid(UID_ROW),
        )
    }

    /**
     * …and the refinement stays refined. The same withheld local database, and a row whose cells
     * are all columns this device already holds: every one of them resolves, nothing is dropped,
     * and quarantining it would freeze the rows of a database every device has had for months
     * over a file only one peer can read. The column-granular test above must not be bought by
     * turning "its database was withheld" into a blanket refusal.
     */
    @Test
    fun `a row of a withheld database still merges when every cell resolves`() = runBlocking {
        val d = Device()
        d.engine.mergePages(
            listOf(
                pageRecord(
                    UID_DB, "People", updatedAt = 1_000L, kind = PageKind.DATABASE.name,
                    database = PageDatabaseSnapshotRecord(
                        properties = listOf(PropertySnapshotRecord(UID_PROP, "Status", "TEXT", order = 0)),
                    ),
                )
            )
        )
        d.folder.putPage("$UID_DB.json", """{"uid":"$UID_DB","title":"People","kind":"DATABASE",""")
        val row = pageRecord(
            UID_ROW, "Ada", updatedAt = 9_000L, databaseUid = UID_DB,
            propertyValues = listOf(PropertyValueSnapshotRecord(UID_PROP, "Doing")),
            blocks = listOf(blockRecord(UID_BLOCK_B, "merged normally")),
        )
        d.folder.putPage("$UID_ROW.json", json.encodeToString(row))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an unparseable database file must not abort the pass: $thrown", thrown)
        assertEquals(
            "every column this row names is right here — quarantine is about cells that would be " +
                "dropped, not about rows that merely belong to an unreadable database",
            listOf("merged normally"),
            d.blocksOf(UID_ROW),
        )
        assertEquals(
            "and it republishes with its cell intact, because the merge placed it",
            listOf("Doing"),
            row.publishedIn(d.folder)?.propertyValues?.map { it.value },
        )
    }

    /**
     * The propagation has to see the files that never became records at all.
     *
     * A `pages/<uid>.json` whose JSON will not decode into a [PageSnapshotRecord] is quarantined
     * by filename in `mergePagesDir` and never handed to `mergePages` — so the sweep inside the
     * engine cannot know it exists, and would re-parent its children exactly as before. These are
     * the files *furthest* ahead of this build, which makes them the worst ones to be blind to.
     */
    @Test
    fun `a page whose parent file would not parse at all is not re-parented either`() = runBlocking {
        val d = Device()
        // Truncated mid-object: in the folder, and undecodable here.
        d.folder.putPage("$UID_A.json", """{"uid":"$UID_A","title":"From a newer build",""")
        val child = pageRecord(UID_CHILD, "Q", updatedAt = 9_000L, parentUid = UID_A)
        d.folder.putPage("$UID_CHILD.json", json.encodeToString(child))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an unparseable page file must not abort the pass: $thrown", thrown)
        assertEquals(
            "the parent's file never reached the merge engine as a record, so the engine has to " +
                "be told about it — otherwise the one file this build understands least is the " +
                "one whose children it silently flattens",
            UID_A,
            child.publishedIn(d.folder)?.parentUid,
        )
    }

    /** A block mentioning a quarantined page is the same defect wearing different clothes: adopt
     * the page and `mentionedPageId` resolves to null, and the export — which derives the uid back
     * out of that id — republishes the block with the mention simply absent. */
    @Test
    fun `a page mentioning a quarantined page is not republished with the mention dropped`() = runBlocking {
        val d = Device()
        d.folder.putPage("$UID_A.json", json.encodeToString(unreadablePeerPage()))
        val mentioning = pageRecord(
            UID_B, "Notes", updatedAt = 9_000L,
            blocks = listOf(blockRecord(UID_BLOCK_B, "see also").copy(mentionedPageUid = UID_A)),
        )
        d.folder.putPage("$UID_B.json", json.encodeToString(mentioning))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a page it cannot decode: $thrown", thrown)
        assertEquals(
            "the mention points at a page this pass withheld, so merging the block that carries " +
                "it publishes the block back with the link gone",
            UID_A,
            mentioning.publishedIn(d.folder)?.blocks?.single()?.mentionedPageUid,
        )
    }

    // =========================== 5h. a folder-wide file whose outer shape this build cannot read
    //
    // `decodeArray` marks the whole file unreadable when non-blank content will not parse as a
    // JSON array — the shape a newer build changing the file's *structure* takes, and equally the
    // shape a corrupt file takes. Refusing to write it is right for one pass and catastrophic as a
    // steady state: the condition never clears on its own, so this device stops publishing every
    // entry it holds, to peers on the same build who could have read them perfectly, for as long
    // as the file sits there. And it said so nowhere — the path never touched the quarantine
    // channel, so Settings showed a clean sync while the device's own data went nowhere.

    /** The object shape a newer build would plausibly move to: a version marker and a nested
     * array, where this build expects the array itself. */
    private val reshapedEntriesFile = """{"version":2,"entries":[{"uid":"$UID_ENTRY_BAD"}]}"""

    @Test
    fun `an unreadable folder file does not permanently silence this device's own rows`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Precious local task")))
        d.folder.putRoot("entries_active.json", reshapedEntriesFile)

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an unreadable file must not abort the pass: $thrown", thrown)
        assertEquals(
            "skipping the write leaves this device publishing nothing, forever, because nothing " +
                "about the file changes between passes — the one dataset no peer has a copy of, " +
                "withheld over a file that was never this device's to begin with",
            listOf("Precious local task"),
            json.decodeFromString<List<EntrySnapshotRecord>>(
                d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
            ).map { it.title },
        )
    }

    @Test
    fun `an unreadable folder file is copied aside before this device writes over it`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Precious local task")))
        d.folder.putRoot("entries_active.json", reshapedEntriesFile)

        d.orchestrator.syncNow(d.folder)

        assertEquals(
            "publishing over a file this device never read destroys it for everyone unless a copy " +
                "is kept — byte for byte, since nothing in it was ever interpreted. Root files " +
                "left: ${d.folder.rootNames()}",
            reshapedEntriesFile,
            d.folder.readRoot("entries_active.tendril-lost-unreadable.json")?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `an unreadable folder file is reported through the same channel as every other quarantine`() = runBlocking {
        val d = Device()
        d.folder.putRoot("habits.json", """{"version":2,"habits":[]}""")

        val merge = d.orchestrator.readAndMerge(d.folder)

        val reported = merge.quarantinedRecords.singleOrNull()
        assertNotNull(
            "a device that has stopped reading — and is about to write over — a whole family of " +
                "records must say so. This path incremented no counter at all, so the person was " +
                "told nothing while it happened: reported ${merge.quarantinedRecords}",
            reported,
        )
        assertEquals("named as a file, since there is no record in it to name", QuarantinedRecord.FILE, reported!!.kind)
        assertEquals("and by the file name, which is the only handle there is", "habits.json", reported.uid)
        assertNotNull("with a message a person can act on", merge.quarantineMessage())
    }

    // ============================================= 5i. holding is not free: hold only what won
    //
    // A held record is republished verbatim AND this device's own row for the same uid is
    // suppressed from the write. That is correct when the peer's copy is the newer one and a
    // permanent, silent gag on this device's better data when it is not — and the hold for a
    // record that would not decode at all was being taken several lines *above* the timestamp
    // comparison that governs every other record in the same loop.

    /** An entry only a newer build can decode: `kind` promoted from a String to an object, so the
     * element will not decode into this build's class at all — while `uid` and `updatedAt` stay
     * perfectly legible, which is what lets the race still be judged. */
    private fun undecodableEntryText(uid: String, updatedAt: Long) =
        """[{"uid":"$uid","title":"Theirs","kind":{"id":"MILESTONE"},"createdAt":1000,"updatedAt":$updatedAt}]"""

    private fun undecodableHabitText(uid: String, updatedAt: Long) =
        """[{"uid":"$uid","title":"Theirs","frequency":{"count":1,"unit":"FORTNIGHT"},"streak":0,"createdAt":1000,"updatedAt":$updatedAt}]"""

    @Test
    fun `an unreadable peer entry that lost on time does not suppress this device's newer row`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Mine, newer", updatedAt = 9_000L)))
        d.folder.putRoot("entries_active.json", undecodableEntryText(UID_ENTRY_LOCAL, updatedAt = 1_000L))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        assertEquals(
            "the peer's copy is the older one, so the ordinary last-write-wins outcome is that " +
                "this device publishes over it. Holding it instead republishes the loser AND " +
                "suppresses the winner — this device's own newer row — from every future write",
            listOf("Mine, newer"),
            json.decodeFromString<List<EntrySnapshotRecord>>(
                d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
            ).map { it.title },
        )
    }

    /** The other order, which must not change: an unreadable peer record that genuinely *won* is
     * still held and republished, and this device's stale row still stands aside for it. */
    @Test
    fun `an unreadable peer entry that won on time is still held and still suppresses the local row`() = runBlocking {
        val d = Device(entries = listOf(localEntry(UID_ENTRY_LOCAL, "Mine, stale", updatedAt = 1_000L)))
        val peerText = undecodableEntryText(UID_ENTRY_LOCAL, updatedAt = 9_000L)
        d.folder.putRoot("entries_active.json", peerText)

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a record it cannot decode: $thrown", thrown)
        assertEquals(
            "the newer record still comes back exactly as it arrived, and this device's stale row " +
                "for that uid still stands aside — one uid must never appear twice in the file",
            json.parseToJsonElement(peerText).jsonArray,
            json.parseToJsonElement(d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)).jsonArray,
        )
    }

    @Test
    fun `an unreadable peer habit that lost on time does not suppress this device's newer row`() = runBlocking {
        val d = Device(habits = listOf(localHabit(UID_HABIT_GOOD, "Mine, newer", updatedAt = 9_000L)))
        d.folder.putRoot("habits.json", undecodableHabitText(UID_HABIT_GOOD, updatedAt = 1_000L))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a habit it cannot decode: $thrown", thrown)
        assertEquals(
            "same rule on the habits file: an unreadable record that lost the race has no claim " +
                "to silence the row that beat it",
            listOf("Mine, newer"),
            json.decodeFromString<List<HabitSnapshotRecord>>(
                d.folder.readRoot("habits.json")!!.toString(Charsets.UTF_8)
            ).map { it.title },
        )
    }

    // ============================== 5j. …and across the family boundary, not only within pages
    //
    // 5g proves the propagation is a real fixpoint — among pages. But `mergePages` is one family
    // of four, and the schema has exactly one field outside it that names a page:
    // `EntrySnapshotRecord.sourceRowUid`, the Row a task was made from (§9.4). (§3.4's relations
    // are the other cross-file reference and 5c pins them; Habits and tombstones name nothing;
    // Reminders never travel in a snapshot at all, so that is the whole list, read off
    // `SnapshotRecords.kt` rather than remembered.)
    //
    // The page pass withholds the Row correctly and suppresses its file — and then hands the
    // entry pass a `rowUidToId` built from `pageDao.getAll()`, which has no Row in it *because*
    // the quarantine never inserted one. The link resolves to null, the Entry is adopted, and
    // `entries_active.json` is rewritten from local rows with the link gone. Everything about the
    // page half was right and the damage arrived one family over.

    /** A Row page the peer wrote in a shape only a newer build can read. Quarantined by
     * `mergePages`, so it is never inserted and nothing local can resolve it. */
    private fun unreadablePeerRow() = pageRecord(UID_ROW, "Ada", updatedAt = 9_000L, kind = "WHITEBOARD")

    private suspend fun publishedEntries(store: InMemorySyncFileStore): List<EntrySnapshotRecord> =
        json.decodeFromString(store.readRoot("entries_active.json")!!.toString(Charsets.UTF_8))

    /**
     * CRITICAL 3 — the same loss as 5g's, arriving through a family that had never been told
     * quarantine exists.
     *
     * HEAD did not have it: `PageKind.valueOf` threw out of Pass 1 there, the pass aborted before
     * the write half, and `entries_active.json` was left exactly as the peer wrote it. A loud
     * abort became a silent unlinking — of the one field that ties a task back to the database row
     * somebody is looking at it in.
     */
    @Test
    fun `an entry whose source row this build cannot read keeps its link to it`() = runBlocking {
        val d = Device()
        d.folder.putPage("$UID_ROW.json", json.encodeToString(unreadablePeerRow()))
        val peerEntry = entryRecord(UID_ENTRY_GOOD, "Call Ada").copy(sourceRowUid = UID_ROW)
        d.folder.putRoot("entries_active.json", json.encodeToString(listOf(peerEntry)))

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a row it cannot decode: $thrown", thrown)
        assertEquals(
            "the Row this task came from was withheld by the page pass, so `sourceRowUid` cannot " +
                "resolve and cannot heal — adopting the Entry publishes it back with the link " +
                "gone, on every device. It has to be held with the Row it names",
            peerEntry,
            publishedEntries(d.folder).singleOrNull { it.uid == UID_ENTRY_GOOD },
        )
        assertNull(
            "and nothing local is written for it either — a row committed with a null " +
                "sourceRowId is what the write pass then reads back",
            d.entryDao.getByUid(UID_ENTRY_GOOD),
        )
    }

    /**
     * …and proportionate, by the same rule the page sweep follows: a quarantined Row this device
     * **already holds** resolves perfectly well, so the Entry merges normally. Freezing every task
     * belonging to a database row one peer rewrote in a newer shape would be the mirror-image
     * mistake, and it is the one this refinement exists to prevent.
     */
    @Test
    fun `an entry whose source row this device already holds still merges`() = runBlocking {
        val d = Device()
        // The Row has been here since before the peer rewrote it unreadably.
        d.engine.mergePages(listOf(pageRecord(UID_ROW, "Ada, mine", updatedAt = 1_000L)))
        d.folder.putPage("$UID_ROW.json", json.encodeToString(unreadablePeerRow()))
        d.folder.putRoot(
            "entries_active.json",
            json.encodeToString(listOf(entryRecord(UID_ENTRY_GOOD, "Call Ada").copy(sourceRowUid = UID_ROW))),
        )

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("the pass must survive a row it cannot decode: $thrown", thrown)
        assertEquals(
            "the id `sourceRowUid` needs is right here — quarantine is about links that would " +
                "dangle, not about links that merely name an unreadable record",
            "Call Ada",
            d.entryDao.getByUid(UID_ENTRY_GOOD)?.title,
        )
        assertEquals(
            "and the Entry republishes with its Row intact, because the merge resolved it",
            UID_ROW,
            publishedEntries(d.folder).singleOrNull { it.uid == UID_ENTRY_GOOD }?.sourceRowUid,
        )
    }

    /**
     * The other end of the same rule: a Row that is merely *late* is not a Row that was withheld.
     * Nothing in the folder says this build could not read it — it simply has not arrived — and
     * `SnapshotMappers.toEntity`'s drop-and-self-heal is the documented answer to that, because
     * the alternative is that a task from a database row does not appear on this device until
     * Syncthing happens to deliver two files in the right order. Quarantining on "the link did not
     * resolve" alone would swallow that case whole.
     */
    @Test
    fun `an entry whose source row has simply not arrived yet still merges`() = runBlocking {
        val d = Device()
        // No pages/<row>.json in the folder at all, and nothing local: the Row is in flight.
        d.folder.putRoot(
            "entries_active.json",
            json.encodeToString(listOf(entryRecord(UID_ENTRY_GOOD, "Call Ada").copy(sourceRowUid = UID_ROW))),
        )

        val thrown = runCatching { d.orchestrator.syncNow(d.folder) }.exceptionOrNull()

        assertNull("an absent Row must not abort the pass: $thrown", thrown)
        assertEquals(
            "an unresolved link is only a quarantine when this pass is the reason it cannot " +
                "resolve; otherwise the Entry merges and the link heals on the pass that brings " +
                "the Row in",
            "Call Ada",
            d.entryDao.getByUid(UID_ENTRY_GOOD)?.title,
        )
    }

    // ================================================================= 6. quarantine is visible

    /**
     * Silence is the failure mode that outlives the bug. A device that quietly drops one of its
     * peer's pages on every pass, forever, looks exactly like a device that is fully in sync —
     * and the person finds out on the day they go looking for the page.
     *
     * Reported through the channel sync problems already travel on: [SnapshotMergeResult], which
     * `SyncCoordinator.kt:159` turns into the `lastError` Settings shows (the same route
     * `passphraseMismatch` takes).
     */
    @Test
    fun `a quarantined record is reported to the caller, not swallowed silently`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID_A, "Mine, stale", updatedAt = 1_000L)))
        d.folder.putPage("$UID_A.json", json.encodeToString(undecodablePeerPage(9_000L)))

        val outcome = runCatching { d.orchestrator.readAndMerge(d.folder) }
        val merge = outcome.getOrNull()

        assertNotNull("the pass must survive the record: ${outcome.exceptionOrNull()}", merge)
        assertEquals(
            "the one unreadable page is surfaced the way an unreadable file already is — no more, " +
                "and above all never none, or sync silently diverges forever",
            1,
            merge!!.quarantinedRecords.size,
        )
        val reported = merge.quarantinedRecords.single()
        assertEquals("named as a page, so the message can say what kind of thing went missing", QuarantinedRecord.PAGE, reported.kind)
        assertEquals("and by uid, which is the only handle the person has on the file", UID_A, reported.uid)
        assertTrue(
            "quoting the value nobody could read, which is what makes \"update Tendril\" advice " +
                "rather than a guess — the detail was \"${reported.detail}\"",
            reported.detail.contains("\"SUPER_CALLOUT\""),
        )
    }

    // ==================================================== 6b. merge-then-write, in both directions
    //
    // The orchestrator is a process-lifetime singleton and `writeSnapshots` publishes whatever the
    // last merge decided. `syncNow` and `rekey` can no longer get that wrong — the merge hands its
    // answer straight to the write half as a required argument, so there is no earlier pass's
    // answer in scope to reach for — but `SyncCoordinator.runPass` and the desktop sync button call
    // the two halves separately (they need `passphraseMismatch` before they are allowed to write at
    // all), and nothing in a signature can make *that* sequence mandatory. These two pin the
    // contract those callers depend on, from both sides.

    @Test
    fun `the write that follows a merge still suppresses what that merge quarantined`() = runBlocking {
        val d = Device()
        d.engine.mergePages(listOf(pageRecord(UID_A, "Mine, stale", updatedAt = 1_000L)))
        val peerFile = json.encodeToString(undecodablePeerPage(9_000L))
        d.folder.putPage("$UID_A.json", peerFile)

        // Exactly `SyncCoordinator.runPass`'s sequence: the two halves, called separately.
        d.orchestrator.readAndMerge(d.folder)
        d.orchestrator.writeSnapshots(d.folder)

        assertEquals(
            "the suppression a merge computes has to survive as far as the write that follows it — " +
                "consuming it, or clearing it once a write has used it, would leave the second " +
                "half of every production pass publishing over the peer file it just refused",
            peerFile,
            d.folder.readPage("$UID_A.json")?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `a later merge that finds nothing unreadable clears the previous pass's suppression`() = runBlocking {
        val d = Device()
        val peerRecord = entryRecord(UID_ENTRY_BAD, "From a newer build", kind = "MILESTONE")
        d.folder.putRoot("entries_active.json", json.encodeToString(listOf(peerRecord)))
        d.orchestrator.syncNow(d.folder)

        // The peer has since removed the record it could not be read for. Nothing on this device
        // ever adopted it, so there is no local row for it either — it exists only as last pass's
        // held copy, and that copy must not outlive the pass that made it.
        d.folder.putRoot("entries_active.json", "[]")
        d.orchestrator.syncNow(d.folder)

        assertEquals(
            "quarantine is this pass's answer, replaced wholesale by the next one. Accumulating it " +
                "would republish a record forever after the folder stopped carrying it — and would " +
                "keep suppressing a page whose file has since become readable",
            emptyList<EntrySnapshotRecord>(),
            json.decodeFromString<List<EntrySnapshotRecord>>(
                d.folder.readRoot("entries_active.json")!!.toString(Charsets.UTF_8)
            ),
        )
    }

    // ============================================================ 7. restore-from-backup

    private fun archive(entryDao: FakeEntryDao, habitDao: FakeHabitDao, backing: FakeContentResolverBacking) =
        PortableArchive(
            context = fakeContext(backing, temp.newFolder(), temp.newFolder()),
            entryDao = entryDao,
            habitDao = habitDao,
            pageDao = mockk<PageDao>(relaxed = true),
            reminderDao = FakeReminderDao(),
            entryCompletionDao = FakeEntryCompletionDao(),
            habitCompletionDao = FakeHabitCompletionDao(),
            checkInDao = FakeCheckInDao(),
            timeLogDao = FakeTimeLogDao(),
            purgeRegistry = mockk(relaxed = true),
            pagesSyncEngine = mockk(relaxed = true),
            localImages = InMemoryLocalImageStore(),
            passphrase = { null },
            rearmAlarms = { _ -> },
        )

    private val manifestJson =
        """{"appVersion":"0.1.0","exportedAtEpochMillis":1,"kind":"full","includedFiles":[]}"""

    /**
     * `PortableArchive.restoreFromBackup` already proves the archive *decodes as JSON* before it
     * deletes anything (`require` at :163) — that is ARCH-01, and [PortableArchiveTest] pins it.
     * An unknown enum value slips straight through that guard: the JSON is perfectly well-formed,
     * so the records are "readable", so `entryDao.deleteAll()` (:168), `habitDao.deleteAll()`
     * (:169) and `purgeRegistry.clearAll()` (:173) all run — and *then* `applyEntries` throws on
     * `EntryKind.valueOf`. There is no transaction around any of it. The database is left empty
     * and the archive unapplied, which is the exact outcome ARCH-01 existed to prevent.
     */
    @Test
    fun `restore refuses an archive carrying an unknown EntryKind and leaves the database untouched`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(localEntry(UID_ENTRY_LOCAL, "Precious local task")))
        val habitDao = FakeHabitDao(listOf(localHabit(UID_HABIT_GOOD, "Make the bed")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(
            zipOfText(
                "manifest.json" to manifestJson,
                "entries_active.json" to json.encodeToString(listOf(entryRecord(UID_ENTRY_BAD, "From a newer build", kind = "MILESTONE"))),
            )
        )

        val thrown = runCatching { archive(entryDao, habitDao, backing).restoreFromBackup(uri) }.exceptionOrNull()

        assertEquals(
            "restore wipes before it applies, so an archive it cannot fully decode has to be " +
                "refused before the delete, not discovered afterwards",
            listOf("Precious local task"),
            entryDao.getAll().map { it.title },
        )
        assertEquals(
            "the habits went down in the same unguarded sequence",
            listOf("Make the bed"),
            habitDao.getAll().map { it.title },
        )
        assertNotNull("and the refusal has to be reported, not swallowed", thrown)
    }

    /** The same hole through `habits.json` — `IntervalUnit.valueOf` in `HabitSnapshotRecord.toEntity`,
     * reached only after both `deleteAll()` calls have already run. */
    @Test
    fun `restore refuses an archive carrying an unknown IntervalUnit and leaves the database untouched`() = runBlocking {
        val entryDao = FakeEntryDao(listOf(localEntry(UID_ENTRY_LOCAL, "Precious local task")))
        val habitDao = FakeHabitDao(listOf(localHabit(UID_HABIT_GOOD, "Make the bed")))
        val backing = FakeContentResolverBacking()
        val uri = backing.givenFile(
            zipOfText(
                "manifest.json" to manifestJson,
                "habits.json" to json.encodeToString(listOf(habitRecord(UID_HABIT_BAD, "From a newer build", frequency = "1:FORTNIGHT"))),
            )
        )

        val thrown = runCatching { archive(entryDao, habitDao, backing).restoreFromBackup(uri) }.exceptionOrNull()

        assertEquals(listOf("Precious local task"), entryDao.getAll().map { it.title })
        assertEquals(listOf("Make the bed"), habitDao.getAll().map { it.title })
        assertNotNull("and the refusal has to be reported, not swallowed", thrown)
    }

    // ============================================================ 8. the Room read path

    /**
     * `Converters.kt:59-139` — fourteen bare `valueOf` calls across thirteen converters, each of
     * them on the *read* side of a Room cursor. A row written by a newer build (or merged in
     * from one) makes the query that reads it throw, which is not a merge failure that can be
     * quarantined: it is a crash in whatever screen ran the query.
     *
     * Each converter is named individually in the failure output rather than asserted one test
     * apiece, since the fix is the same shape for all of them and reading thirteen near-identical
     * failures is worse than reading one list.
     */
    private fun unknownValueDecodes(): List<Pair<String, () -> Any?>> {
        val c = Converters()
        return listOf(
            Pair("stringToEntryKind", { c.stringToEntryKind("MILESTONE") }),
            Pair("stringToEntryStatus", { c.stringToEntryStatus("DEFERRED") }),
            Pair("stringToEntrySource", { c.stringToEntrySource("OUTLOOK_CALENDAR") }),
            Pair("stringToIntervalUnit", { c.stringToIntervalUnit("FORTNIGHT") }),
            Pair("stringToReminderOffset (preset)", { c.stringToReminderOffset("PRESET:TWO_FORTNIGHTS_BEFORE") }),
            Pair("stringToReminderOffset (custom unit)", { c.stringToReminderOffset("CUSTOM:2:FORTNIGHT") }),
            Pair("stringToHabitFrequency", { c.stringToHabitFrequency("1:FORTNIGHT") }),
            Pair("stringToPageKind", { c.stringToPageKind("TIMELINE") }),
            Pair("stringToBlockType", { c.stringToBlockType("SUPER_CALLOUT") }),
            Pair("stringToPropertyType", { c.stringToPropertyType("LOOKUP") }),
            Pair("stringToViewType", { c.stringToViewType("GANTT") }),
            Pair("stringToSortDirection", { c.stringToSortDirection("RANDOM") }),
            Pair("stringToPurgedKind", { c.stringToPurgedKind("WORKSPACE") }),
            Pair("stringToCanvasNodeType", { c.stringToCanvasNodeType("STICKY_NOTE") }),
            Pair("stringToCanvasArrowDirection", { c.stringToCanvasArrowDirection("SQUIGGLY") }),
        )
    }

    @Test
    fun `an unknown enum value decodes tolerantly instead of throwing out of Room's cursor mapping`() {
        val threw = unknownValueDecodes().mapNotNull { (name, decode) ->
            runCatching { decode() }.exceptionOrNull()?.let { "$name -> ${it::class.simpleName}: ${it.message}" }
        }

        assertEquals(
            "an enum member added by a newer build must not make an older build's query throw",
            emptyList<String>(),
            threw,
        )
    }

    /**
     * `stringToPurgedKind` (:132) is the worst of the fourteen and gets its own test because of
     * *where* it is read. `PurgeRegistry` loads every tombstone row at the top of every merge
     * pass (`tombstones` / `applyToLocalRecords`), before any record file has been looked at — so
     * a single unrecognised kind, written by a device that purges something this build has no
     * concept of, does not quarantine one record. It aborts every sync this device will ever run,
     * against every peer, until the build is updated.
     */
    @Test
    fun `an unknown PurgedKind does not abort every sync before a single record is read`() {
        val thrown = runCatching { Converters().stringToPurgedKind("WORKSPACE") }.exceptionOrNull()

        assertNull(
            "every merge pass reads the whole tombstone table first, so this throws before any " +
                "record is even considered: $thrown",
            thrown,
        )
    }
}
