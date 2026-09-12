package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.searchPrefix
import com.tendril.app.sync.FakeBlockDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageFtsDao
import com.tendril.app.sync.FakePageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** §3.1.7 / §0.8 step 8a — the switcher's parsing and ranking, and the FTS title fix behind it. */
class QuickSwitcherTest {

    private val at = Instant.EPOCH
    private var nextId = 1L
    private fun page(title: String, icon: String? = null) = Page(id = nextId++, title = title, icon = icon, kind = PageKind.PAGE, createdAt = at, updatedAt = at)

    @Test
    fun `a leading angle bracket means commands, anything else means pages`() {
        assertEquals(SwitcherMode.Commands("rev"), parseSwitcherInput(">rev"))
        assertEquals(SwitcherMode.Commands(""), parseSwitcherInput(">"))
        assertEquals(SwitcherMode.Pages("boo"), parseSwitcherInput(" boo "))
    }

    @Test
    fun `commands filter on title and keywords, case-insensitively, all when blank`() {
        val cs = listOf(SwitcherCommand("Review", "weekly walk") {}, SwitcherCommand("New page", "create") {}, SwitcherCommand("Go to Tasks") {})
        assertEquals(3, filterCommands(cs, "").size)
        assertEquals(listOf("Review"), filterCommands(cs, "WEEK").map { it.title })
        assertEquals(listOf("New page"), filterCommands(cs, "creat").map { it.title })
    }

    @Test
    fun `title-prefix hits come first, then the index, each page once, nothing for a blank query`() {
        val books = page("Books"); val bookmarks = page("Bookmarks"); val notes = page("Notes")
        val fts = listOf(PageSearchHit(notes.id, "Notes", null, "…about books…"), PageSearchHit(books.id, "Books", null, "…"))
        val ranked = rankPageHits("boo", listOf(notes, bookmarks, books), fts)
        assertEquals(listOf("Bookmarks", "Books", "Notes"), ranked.map { it.title })
        assertTrue(rankPageHits("", listOf(books), fts).isEmpty())
    }

    @Test
    fun `a page is found by its title, and by its new title after a rename`() = runBlocking {
        val store = FakePageStore()
        val pageDao = FakePageDao(store); val blockDao = FakeBlockDao(store); val ftsDao = FakePageFtsDao(store)
        val repo = PageContentRepository(pageDao, blockDao, ftsDao)
        val id = store.seedPage(Page(title = "Trip", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        blockDao.insert(Block(pageId = id, type = BlockType.PARAGRAPH, order = 0, content = "pack socks", createdAt = at, updatedAt = at))
        repo.rebuildFtsForPage(id)
        assertEquals(listOf(id), ftsDao.searchPrefix("Trip").map { it.pageId })
        assertEquals(listOf(id), ftsDao.searchPrefix("socks").map { it.pageId })

        pageDao.update(pageDao.getById(id)!!.copy(title = "Holiday"))
        repo.rebuildFtsForPage(id)
        assertTrue(ftsDao.searchPrefix("Trip").isEmpty())
        assertEquals(listOf(id), ftsDao.searchPrefix("Holiday").map { it.pageId })
    }

    @Test
    fun `the heal indexes only the pages without a row, and reports how many`() = runBlocking {
        val store = FakePageStore()
        val pageDao = FakePageDao(store); val blockDao = FakeBlockDao(store); val ftsDao = FakePageFtsDao(store)
        val repo = PageContentRepository(pageDao, blockDao, ftsDao)
        val a = store.seedPage(Page(title = "Alpha", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        val b = store.seedPage(Page(title = "Beta", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        store.seedPage(Page(title = "Gone", kind = PageKind.PAGE, deletedAt = at, createdAt = at, updatedAt = at))
        repo.rebuildFtsForPage(a)
        assertEquals(1, repo.healIndex())
        assertEquals(listOf(b), ftsDao.searchPrefix("Beta").map { it.pageId })
        assertEquals("a second run finds nothing to do", 0, repo.healIndex())
        assertTrue("a trashed page is not indexed", ftsDao.searchPrefix("Gone").isEmpty())
    }
}
