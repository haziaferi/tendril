package com.tendril.app.sync

import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.domain.PurgeRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * audit 2.1 — a page whose parent arrives in a later batch used to be orphaned permanently.
 *
 * `mergePages` Pass 2 resolved `parentUid` for winners only. A child arriving before its parent
 * finds nothing to point at and drops to root; by the time the parent shows up, the child's
 * `updatedAt` is no longer newer than the local row Pass 1 just wrote, so it is not a winner, so
 * Pass 2 skips it — and nothing ever revisits it. The class doc's claim of "a real two-pass
 * fixup instead of drop-and-heal" was true only within a single batch.
 *
 * The repair is deliberately asymmetric, and the second half of these tests is about that half:
 * a non-winner may *fill* an unresolved position but never overwrite a resolved one. Letting it
 * overwrite would hand a stale record the power to move a page, which is exactly what losing on
 * `updatedAt` is supposed to deny it.
 */
class PageParentHealingTest {

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val blockDao = FakeBlockDao(store)
    private val ftsDao = FakePageFtsDao(store)

    private val engine = PagesSyncEngine(
        pageDao = pageDao,
        blockDao = blockDao,
        labelDao = FakeLabelDao(store),
        pageDatabaseDao = FakePageDatabaseDao(store),
        propertyDao = FakePropertyDao(store),
        propertyValueDao = FakePropertyValueDao(store),
        pageDatabaseViewDao = FakePageDatabaseViewDao(store),
        pageCanvasDao = FakePageCanvasDao(store),
        canvasNodeDao = FakeCanvasNodeDao(store),
        canvasEdgeDao = FakeCanvasEdgeDao(store),
        pageRelationDao = FakePageRelationDao(store),
        purgeRegistry = PurgeRegistry(
            FakePurgedRecordDao(), pageDao, FakeEntryDao(), FakeHabitDao(),
            FakePropertyDao(store), RecordingEntryScheduleCoordinator(),
        ),
        pageContentRepository = PageContentRepository(pageDao, blockDao, ftsDao),
            pageHistory = PageHistory(pageDao, blockDao, FakePageRevisionDao()),
    )

    private companion object {
        // isSafeUid admits UUIDs only -- the path-traversal guard from the security pass -- so a
        // record with a friendly uid is dropped before it ever reaches the merge.
        const val PARENT = "11111111-1111-4111-8111-111111111111"
        const val CHILD = "22222222-2222-4222-8222-222222222222"
        const val GRANDCHILD = "33333333-3333-4333-8333-333333333333"
        const val A = "44444444-4444-4444-8444-444444444444"
        const val B = "55555555-5555-4555-8555-555555555555"
        const val GONE = "66666666-6666-4666-8666-666666666666"
    }

    private fun pageRecord(uid: String, title: String, updatedAt: Long, parentUid: String? = null) =
        PageSnapshotRecord(
            uid = uid,
            title = title,
            kind = PageKind.PAGE.name,
            parentUid = parentUid,
            databaseUid = null,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )

    private suspend fun parentUidOf(uid: String): String? {
        val page = pageDao.getByUid(uid) ?: return null
        return page.parentId?.let { pageDao.getById(it)?.uid }
    }

    // ------------------------------------------------------------ the regression

    @Test
    fun `a parent arriving in a later batch still adopts its child`() = runBlocking {
        // Batch 1: the child alone. Nothing to point at, so it lands at root.
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 1_000, parentUid = PARENT)))
        assertNull("no parent to resolve yet", pageDao.getByUid(CHILD)!!.parentId)

        // Batch 2: the parent arrives, and the child is re-sent unchanged — which is what a
        // remote file does, since it lists every page every time. The child is NOT a winner here.
        engine.mergePages(
            listOf(
                pageRecord(PARENT, "Parent", 2_000),
                pageRecord(CHILD, "Child", 1_000, parentUid = PARENT),
            )
        )

        assertEquals("the child must find its parent on a later pass", PARENT, parentUidOf(CHILD))
    }

    @Test
    fun `a child re-sent alone after its parent exists is still healed`() = runBlocking {
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 1_000, parentUid = PARENT)))
        engine.mergePages(listOf(pageRecord(PARENT, "Parent", 2_000)))

        // The parent is local now but was never in a batch alongside the child.
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 1_000, parentUid = PARENT)))

        assertEquals(PARENT, parentUidOf(CHILD))
    }

    @Test
    fun `the whole chain heals, not just one level`() = runBlocking {
        engine.mergePages(listOf(pageRecord(GRANDCHILD, "Grandchild", 1_000, parentUid = CHILD)))
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 1_000, parentUid = PARENT)))
        engine.mergePages(listOf(pageRecord(PARENT, "Parent", 1_000)))

        // Everything is local now; one more pass carrying them all should settle both edges.
        engine.mergePages(
            listOf(
                pageRecord(PARENT, "Parent", 1_000),
                pageRecord(CHILD, "Child", 1_000, parentUid = PARENT),
                pageRecord(GRANDCHILD, "Grandchild", 1_000, parentUid = CHILD),
            )
        )

        assertEquals(PARENT, parentUidOf(CHILD))
        assertEquals(CHILD, parentUidOf(GRANDCHILD))
    }

    // ------------------------------------- the asymmetry: fill nulls, never overwrite

    @Test
    fun `a stale record cannot move a page that already has a parent`() = runBlocking {
        engine.mergePages(
            listOf(
                pageRecord(A, "A", 1_000),
                pageRecord(B, "B", 1_000),
                pageRecord(CHILD, "Child", 5_000, parentUid = A),
            )
        )
        assertEquals(A, parentUidOf(CHILD))

        // An older record claiming a different parent. It loses on updatedAt, so its opinion
        // about the tree must not count — the healing path fills nulls, it does not re-parent.
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 2_000, parentUid = B)))

        assertEquals("a stale record must not move the page", A, parentUidOf(CHILD))
    }

    @Test
    fun `a newer record may still move a page`() = runBlocking {
        engine.mergePages(
            listOf(
                pageRecord(A, "A", 1_000),
                pageRecord(B, "B", 1_000),
                pageRecord(CHILD, "Child", 2_000, parentUid = A),
            )
        )

        // Winning on updatedAt is exactly what earns the right to re-parent.
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 9_000, parentUid = B)))

        assertEquals(B, parentUidOf(CHILD))
    }

    @Test
    fun `a newer record may deliberately move a page to the root`() = runBlocking {
        engine.mergePages(
            listOf(pageRecord(A, "A", 1_000), pageRecord(CHILD, "Child", 2_000, parentUid = A))
        )

        engine.mergePages(listOf(pageRecord(CHILD, "Child", 9_000, parentUid = null)))

        assertNull("a winner clearing its parent is a real edit, not an unresolved link", parentUidOf(CHILD))
    }

    @Test
    fun `an unresolvable parent leaves the page at the root without failing`() = runBlocking {
        // The parent was purged on another device and is never coming.
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 1_000, parentUid = GONE)))
        engine.mergePages(listOf(pageRecord(CHILD, "Child", 1_000, parentUid = GONE)))

        assertNotNull("the page itself must survive", pageDao.getByUid(CHILD))
        assertNull(parentUidOf(CHILD))
    }
}
