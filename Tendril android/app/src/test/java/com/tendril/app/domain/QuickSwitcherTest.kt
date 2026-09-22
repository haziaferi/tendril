package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.BlockSearchHit
import com.tendril.app.data.page.PageSearchHit
import com.tendril.app.data.page.searchPrefix
import com.tendril.app.sync.FakeBlockDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakeBlockFtsDao
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
    fun `title-prefix hits come first, then the blocks - three a page at most, none for a page already titled - nothing for a blank query`() {
        val books = page("Books"); val bookmarks = page("Bookmarks"); val notes = page("Notes")
        val hits = listOf(
            BlockSearchHit(notes.id, 11, "Notes", null, "\u2026about books\u2026"), BlockSearchHit(books.id, 21, "Books", null, "\u2026"),
            BlockSearchHit(notes.id, 12, "Notes", null, "\u2026more books\u2026"), BlockSearchHit(notes.id, 13, "Notes", null, "\u2026books three\u2026"),
            BlockSearchHit(notes.id, 14, "Notes", null, "\u2026books four\u2026"),
            BlockSearchHit(bookmarks.id, 31, "Bookmarks", null, "a block on a titled page"),
        )
        val ranked = rankPageHits("boo", listOf(notes, bookmarks, books), hits)
        assertEquals(listOf("Bookmarks", "Books", "Notes", "Notes", "Notes"), ranked.map { it.title })
        assertEquals(listOf(null, null, 11L, 12L, 13L), ranked.map { it.blockId })   // the title rows carry no block; the fourth block is one Ctrl+F away; Bookmarks is titled
        assertTrue(rankPageHits("", listOf(books), hits).isEmpty())
        // a multi-line block's snippet lands on the row's one line, the match in view
        val multi = rankPageHits("boo", listOf(notes), listOf(BlockSearchHit(bookmarks.id, 31, "Bookmarks", null, "line one\n  \u0002books\u0003 on line two")))
        assertEquals("line one \u0002books\u0003 on line two", multi.single().snippet)
    }

    // ---- L6: the card's sections

    private val cmds = listOf(
        SwitcherCommand("New page", "create blank", chord = "Ctrl+N") {}, SwitcherCommand("New canvas", "create board") {},
        SwitcherCommand("Go to Calendar", "tab", chord = "Ctrl+2") {}, SwitcherCommand("Go to Tasks", "tab", chord = "Ctrl+3") {},
        SwitcherCommand("Review", "weekly walk") {},
    )
    private fun hit(title: String) = PageSearchHit(nextId++, title, null, "")

    @Test
    fun `a blank query lists Recent with each page's age, and nothing else`() {
        val recents = listOf(hit("Escape test") to "just now", hit("Trip") to "6 Sep")
        val sections = switcherSections(SwitcherMode.Pages(""), hits = listOf(hit("ignored")), recents = recents, commands = cmds)
        assertEquals(listOf("Recent"), sections.map { it.label })
        assertEquals(listOf("just now", "6 Sep"), sections.single().items.map { (it as SwitcherItem.Hit).meta })
    }

    @Test
    fun `a query lists the pages, then at most three matching commands under their own label`() {
        val sections = switcherSections(SwitcherMode.Pages("e"), hits = listOf(hit("Escape test")), recents = emptyList(), commands = cmds)
        assertEquals(listOf("Pages", "Commands"), sections.map { it.label })
        assertEquals(3, sections[1].items.size) // five commands carry an "e"; the tail is capped
        assertEquals(4, sections.flat().size)
        assertTrue(switcherSections(SwitcherMode.Pages("zq"), emptyList(), emptyList(), cmds).isEmpty())
    }

    @Test
    fun `an angle bracket lists every matching command alone, with its chord`() {
        val sections = switcherSections(SwitcherMode.Commands("go"), hits = listOf(hit("Go west")), recents = emptyList(), commands = cmds)
        assertEquals(listOf("Commands"), sections.map { it.label })
        val listed = sections.single().items.map { (it as SwitcherItem.Cmd).command }
        assertEquals(listOf("Go to Calendar", "Go to Tasks"), listed.map { it.title })
        assertEquals(listOf("Ctrl+2", "Ctrl+3"), listed.map { it.chord })
        assertEquals(null, cmds.first { it.title == "New canvas" }.chord)
    }

    @Test
    fun `the typed prefix is found at a title's start only`() {
        assertEquals(0..1, prefixRange("Call the library", "ca"))
        assertEquals(0..1, prefixRange("Call the library", " CA "))
        assertEquals(null, prefixRange("Call the library", "lib"))
        assertEquals(null, prefixRange("Call the library", ""))
    }

    @Test
    fun `a page is found by its title, and by its new title after a rename`() = runBlocking {
        val store = FakePageStore()
        val pageDao = FakePageDao(store); val blockDao = FakeBlockDao(store); val ftsDao = FakePageFtsDao(store); val blockFts = FakeBlockFtsDao(store)
        val repo = PageContentRepository(pageDao, blockDao, ftsDao, blockFts)
        val id = store.seedPage(Page(title = "Trip", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        blockDao.insert(Block(pageId = id, type = BlockType.PARAGRAPH, order = 0, content = "pack socks", createdAt = at, updatedAt = at))
        repo.rebuildFtsForPage(id)
        assertEquals(listOf(id), ftsDao.searchPrefix("Trip").map { it.pageId })
        assertEquals(listOf(id), ftsDao.searchPrefix("socks").map { it.pageId })
        assertEquals(listOf(id), blockFts.searchPrefix("socks").map { it.pageId })                 // v25 — the block's own row
        assertTrue("the title is the page row's, not a block's", blockFts.searchPrefix("Trip").isEmpty())

        pageDao.update(pageDao.getById(id)!!.copy(title = "Holiday"))
        repo.rebuildFtsForPage(id)
        assertTrue(ftsDao.searchPrefix("Trip").isEmpty())
        assertEquals(listOf(id), ftsDao.searchPrefix("Holiday").map { it.pageId })
    }

    @Test
    fun `the heal indexes only the pages without a row, and reports how many`() = runBlocking {
        val store = FakePageStore()
        val pageDao = FakePageDao(store); val blockDao = FakeBlockDao(store); val ftsDao = FakePageFtsDao(store); val blockFts = FakeBlockFtsDao(store)
        val repo = PageContentRepository(pageDao, blockDao, ftsDao, blockFts)
        val a = store.seedPage(Page(title = "Alpha", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        val b = store.seedPage(Page(title = "Beta", kind = PageKind.PAGE, createdAt = at, updatedAt = at))
        store.seedPage(Page(title = "Gone", kind = PageKind.PAGE, deletedAt = at, createdAt = at, updatedAt = at))
        blockDao.insert(Block(pageId = a, type = BlockType.PARAGRAPH, order = 0, content = "alpha text", createdAt = at, updatedAt = at))
        repo.rebuildFtsForPage(a)
        assertEquals(1, repo.healIndex())
        // v25 — a page whose block rows are missing (the first launch after MIGRATION_24_25) is healed too
        blockFts.rows.clear()
        assertEquals(1, repo.healIndex())
        assertEquals(listOf(b), ftsDao.searchPrefix("Beta").map { it.pageId })
        assertEquals("a second run finds nothing to do", 0, repo.healIndex())
        assertTrue("a trashed page is not indexed", ftsDao.searchPrefix("Gone").isEmpty())
    }
}
