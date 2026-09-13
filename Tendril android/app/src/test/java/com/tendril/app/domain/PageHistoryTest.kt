package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.RevisionReason
import com.tendril.app.domain.history.PageHistory
import com.tendril.app.sync.FakeBlockDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageRevisionDao
import com.tendril.app.sync.FakePageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** §0.6.13 / B§6 #12 — when a body is kept, and that a kept body comes back whole. */
class PageHistoryTest {

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val blockDao = FakeBlockDao(store)
    private val revisions = FakePageRevisionDao()
    private var now: Instant = Instant.ofEpochSecond(1_000_000)
    private val history = PageHistory(pageDao, blockDao, revisions) { now }
    private val at = Instant.EPOCH

    private fun page(title: String) = store.seedPage(Page(title = title, kind = PageKind.PAGE, createdAt = at, updatedAt = at))
    private suspend fun block(pageId: Long, content: String, order: Int = 0, parentId: Long? = null): Long =
        blockDao.insert(Block(pageId = pageId, type = BlockType.PARAGRAPH, order = order, parentBlockId = parentId, content = content, createdAt = at, updatedAt = at))

    @Test
    fun `an edit keeps the body once per ten-minute window`() = runBlocking {
        val id = page("Trip")
        block(id, "pack socks")
        history.captureBeforeEdit(id)
        now += Duration.ofMinutes(3)
        history.captureBeforeEdit(id)
        assertEquals("the second edit is inside the window", 1, revisions.rows.size)
        now += Duration.ofMinutes(8)
        history.captureBeforeEdit(id)
        assertEquals(2, revisions.rows.size)
        assertEquals(RevisionReason.EDIT, revisions.rows.values.first().reason)
    }

    @Test
    fun `a merge keeps the body only when it differs from the last kept one`() = runBlocking {
        val id = page("Trip")
        block(id, "pack socks")
        history.captureBeforeMerge(id)
        history.captureBeforeMerge(id)
        assertEquals("unchanged since the last capture: nothing", 1, revisions.rows.size)
        blockDao.getForPage(id).single().let { blockDao.update(it.copy(content = "pack socks and a hat")) }
        history.captureBeforeMerge(id)
        assertEquals(2, revisions.rows.size)
        assertEquals(RevisionReason.MERGE, revisions.latestForPage(id)!!.reason)
    }

    @Test
    fun `a restore brings the title and blocks back, children relinked, and keeps what it replaced`() = runBlocking {
        val id = page("Trip")
        val parent = block(id, "Packing", order = 0)
        block(id, "socks", order = 1, parentId = parent)
        history.captureBeforeEdit(id)
        val kept = revisions.latestForPage(id)!!
        assertEquals(2, kept.blockCount)

        // The page moves on: retitled, the child gone, a new block.
        pageDao.update(pageDao.getById(id)!!.copy(title = "Holiday"))
        blockDao.getForPage(id).forEach { blockDao.delete(it.id) }
        block(id, "nothing left")

        history.restore(kept.id)

        assertEquals("Trip", pageDao.getById(id)!!.title)
        val restored = blockDao.getForPage(id)
        assertEquals(listOf("Packing", "socks"), restored.map { it.content })
        val child = restored.single { it.content == "socks" }
        assertNotNull(child.parentBlockId)
        assertEquals(restored.single { it.content == "Packing" }.id, child.parentBlockId)
        val latest = revisions.latestForPage(id)!!
        assertEquals("the replaced body is kept first", RevisionReason.RESTORE, latest.reason)
        assertEquals("Holiday", latest.title)
    }

    @Test
    fun `at most fifty are kept per page, the oldest dropped`() = runBlocking {
        val id = page("Trip")
        block(id, "x")
        repeat(55) {
            now += Duration.ofMinutes(11)
            history.captureBeforeEdit(id)
        }
        assertEquals(PageHistory.KEEP, revisions.rows.size)
        assertEquals("the survivors are the newest", now, revisions.latestForPage(id)!!.takenAt)
    }
}
