package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.preview.LineKind
import com.tendril.app.domain.preview.canvasPreview
import com.tendril.app.domain.preview.databasePreview
import com.tendril.app.domain.preview.pagePreview
import com.tendril.app.domain.preview.referencePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** B§13.6 #3 — the hover card's lines as a pure function of the page's rows. */
class PagePreviewTest {
    private val t0 = Instant.parse("2026-09-16T00:00:00Z")
    private val page = Page(id = 1, title = "Escape test", createdAt = t0, updatedAt = t0)
    private fun block(id: Long, type: BlockType = BlockType.PARAGRAPH, content: String = "b$id", parent: Long? = null, checked: Boolean? = null, mindMap: Boolean = false) =
        Block(id = id, uid = "u$id", pageId = 1, type = type, order = id.toInt(), parentBlockId = parent, content = content, checked = checked, mindMap = mindMap, createdAt = t0, updatedAt = t0)

    @Test
    fun `the kinds map and a folded map is one counted line`() {
        val blocks = listOf(
            block(1, BlockType.HEADING_1, "Root"),
            block(2, BlockType.TODO, "Buy the tickets", checked = false),
            block(3, BlockType.TODO, "Done thing", checked = true),
            block(4, BlockType.DIVIDER, ""),
            block(5, content = "Map root", mindMap = true),
            block(6, parent = 5, content = "child a"),
            block(7, parent = 5, content = "child b"),
            block(8, content = "After the map"),
        )
        val p = pagePreview(page, blocks)
        assertEquals(listOf(LineKind.HEADING, LineKind.TODO_OPEN, LineKind.TODO_DONE, LineKind.TEXT, LineKind.COUNT, LineKind.TEXT), p.lines.map { it.kind })
        assertEquals("Map root", p.lines[3].text)
        assertEquals("Mind map · 2 nodes", p.lines[4].text)
        assertEquals("After the map", p.lines[5].text)
        assertEquals(0, p.more)
        // A map with no nodes yet is a block with words and nothing more.
        assertEquals(listOf("solo"), pagePreview(page, listOf(block(1, content = "solo", mindMap = true))).lines.map { it.text })
    }

    @Test
    fun `lines are cut to ninety characters`() {
        val long = "x".repeat(200)
        val p = pagePreview(page, listOf(block(1, content = long)))
        assertEquals(90, p.lines[0].text.length)
        assertTrue(p.lines[0].text.endsWith("…"))
    }

    @Test
    fun `more counts blocks left out and a short page has none`() {
        val eight = (1L..8L).map { block(it) }
        assertEquals(2, pagePreview(page, eight).more)
        assertEquals(6, pagePreview(page, eight).lines.size)
        assertEquals(0, pagePreview(page, eight.take(2)).more)
    }

    @Test
    fun `a reference shows its line in context`() {
        val blocks = (1L..4L).map { block(it) }
        val first = referencePreview(page, blocks, "u1")
        assertEquals(listOf("b1", "b2"), first.lines.map { it.text })
        assertEquals(listOf(true, false), first.lines.map { it.marked })
        val middle = referencePreview(page, blocks, "u3")
        assertEquals(listOf("b2", "b3", "b4"), middle.lines.map { it.text })
        assertTrue(middle.lines[1].marked)
        assertFalse(middle.lines[0].marked)
        val unknown = referencePreview(page, blocks, "nope")
        assertEquals(4, unknown.lines.size)
        assertTrue(unknown.lines.none { it.marked })
    }

    @Test
    fun `a reference inside a folded map is still found`() {
        val blocks = listOf(block(1, content = "map", mindMap = true), block(2, parent = 1, content = "inside"))
        val p = referencePreview(page, blocks, "u2")
        assertEquals(listOf("map", "inside"), p.lines.map { it.text })
        assertTrue(p.lines[1].marked)
    }

    @Test
    fun `a database says its view, its rows and its columns`() {
        val db = page.copy(kind = PageKind.DATABASE, title = "Books v12")
        val rows = (1L..5L).map { Page(id = it, title = "Row $it", createdAt = t0, updatedAt = t0) }
        val p = databasePreview(db, "Table", listOf("Read on", "Author", "Done"), rows)
        assertEquals("Table · 5 rows · Read on, Author, Done", p.lines[0].text)
        assertEquals(LineKind.COUNT, p.lines[0].kind)
        assertEquals(listOf("Row 1", "Row 2", "Row 3"), p.lines.drop(1).map { it.text })
        assertEquals(2, p.more)
    }

    @Test
    fun `a canvas counts its cards and links`() {
        val c = page.copy(kind = PageKind.CANVAS, title = "Garden plan")
        val p = canvasPreview(c, listOf("Beds", "Compost", "", "Shed"), links = 2)
        assertEquals("Canvas · 4 cards · 2 links", p.lines[0].text)
        assertEquals(listOf("Beds", "Compost", "(empty card)"), p.lines.drop(1).map { it.text })
        assertEquals(1, p.more)
        assertEquals("Canvas · 1 card", canvasPreview(c, listOf("Beds"), links = 0).lines[0].text)
    }
}
