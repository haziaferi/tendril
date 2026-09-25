package com.tendril.app.markdown

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.data.canvas.CanvasArrowDirection
import com.tendril.app.data.canvas.CanvasEdge
import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.data.canvas.PageCanvas
import com.tendril.app.sync.FakeBlockDao
import com.tendril.app.sync.FakeCanvasEdgeDao
import com.tendril.app.sync.FakeCanvasNodeDao
import com.tendril.app.sync.FakePageCanvasDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageStore
import com.tendril.app.sync.InMemoryLocalImageStore
import com.tendril.app.sync.entriesOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * §7 in reverse, the I/O half.
 *
 * `MarkdownWriterTest` covers what a block turns into; this covers everything around it — where a
 * file lands, what it is called when two pages disagree about their name, and whether a link
 * written on one page actually resolves to the other from where it sits.
 */
class MarkdownExporterTest {

    private val store = FakePageStore()
    private val pageDao = FakePageDao(store)
    private val blockDao = FakeBlockDao(store)
    private val localImages = InMemoryLocalImageStore()
    private val canvasDao = FakePageCanvasDao(store)
    private val nodeDao = FakeCanvasNodeDao(store)
    private val edgeDao = FakeCanvasEdgeDao(store)
    private val exporter = MarkdownExporter(pageDao, blockDao, localImages, canvasDao, nodeDao, edgeDao)

    private fun at(m: Long) = Instant.ofEpochMilli(m)

    private fun page(
        title: String,
        parentId: Long? = null,
        deleted: Boolean = false,
        isTemplate: Boolean = false,
        icon: String? = null,
        kind: PageKind = PageKind.PAGE,
    ): Long = store.seedPage(
        Page(
            uid = "uid-${title.lowercase().replace(' ', '-')}-${store.pages.size}",
            title = title,
            icon = icon,
            kind = kind,
            parentId = parentId,
            isTemplate = isTemplate,
            deletedAt = if (deleted) at(9) else null,
            createdAt = at(1),
            updatedAt = at(1),
        )
    )

    private fun addBlock(
        pageId: Long,
        type: BlockType,
        content: String = "",
        spans: List<FormattingSpan> = emptyList(),
        imagePath: String? = null,
        mentionedPageId: Long? = null,
    ): Block {
        val id = store.nextId()
        val block = Block(
            id = id,
            uid = "block-$id",
            pageId = pageId,
            type = type,
            order = id.toInt(),
            content = content,
            formattingSpans = spans,
            imagePath = imagePath,
            mentionedPageId = mentionedPageId,
            createdAt = at(1),
            updatedAt = at(1),
        )
        store.blocks[id] = block
        return block
    }

    private fun exported(): Map<String, ByteArray> {
        val out = ByteArrayOutputStream()
        runBlocking { exporter.export(out) }
        return entriesOf(out.toByteArray())
    }

    /** The page files alone — the root's `CLAUDE.md` (2026-09-22) is the zip's, not a page's. */
    private val Map<String, ByteArray>.pageNames: Set<String> get() = keys.filterNot { it == "CLAUDE.md" }.toSet()

    private fun text(entries: Map<String, ByteArray>, name: String) =
        entries[name]?.toString(Charsets.UTF_8) ?: error("no entry '$name' in ${entries.keys}")

    // ------------------------------------------------------------------ files and names

    @Test
    fun `a page becomes a md file titled with its own name`() {
        val id = page("Trip", icon = "🧭")
        addBlock(id, BlockType.PARAGRAPH, "we left early")

        val out = exported()

        // The H1 as well as the file name: the file name is lost the moment the text is pasted
        // anywhere else, and every reader looks at the first heading for a document's name.
        assertEquals("# 🧭 Trip\n\nwe left early\n", text(out, "Trip.md"))
    }

    /** `docs/agent-over-export.md` — the zip explains itself at its root (2026-09-22). */
    @Test
    fun `the zip carries a CLAUDE md at its root with the counts and the three rules`() {
        val id = page("Trip")
        addBlock(id, BlockType.PARAGRAPH, "we left early")

        val out = exported()

        val readme = text(out, "CLAUDE.md")
        assertTrue(readme.startsWith("# These notes"))
        assertTrue(readme.contains("1 page, 0 canvases and 0 images"))
        assertTrue(readme.contains("Read-only towards the app") && readme.contains("A snapshot") && readme.contains("Where you run is where the notes go"))
    }

    @Test
    fun `the page tree becomes directories`() {
        val trip = page("Trip")
        val day = page("Day one", parentId = trip)
        addBlock(day, BlockType.PARAGRAPH, "walked")

        assertTrue("Trip/Day one.md" in exported().keys)
    }

    @Test
    fun `two pages that would take the same file name do not overwrite each other`() {
        page("Notes")
        page("notes")

        val names = exported().pageNames
        // Compared case-insensitively on purpose: a zip extracted onto Windows or macOS lands on a
        // filesystem where these are the same file, and one silently replacing the other is the
        // worst outcome available here.
        assertEquals(setOf("Notes.md", "notes (2).md"), names)
    }

    @Test
    fun `the same name under different parents is not a collision at all`() {
        val a = page("A")
        val b = page("B")
        page("Notes", parentId = a)
        page("Notes", parentId = b)

        assertEquals(setOf("A.md", "B.md", "A/Notes.md", "B/Notes.md"), exported().pageNames)
    }

    @Test
    fun `a title no filesystem would accept is made safe, and an empty one falls back to the uid`() {
        page("a/b:c*d?e")
        val blank = page("   ")

        val names = exported().pageNames
        assertTrue("illegal characters must not survive", names.any { it == "a b c d e.md" })
        // An oddly named file is recoverable; a file that could not be created is not.
        assertTrue(names.any { it == store.pages.getValue(blank).uid + ".md" })
    }

    @Test
    fun `a Windows device name is not used as a file name`() {
        page("CON")
        // `CON.md` is still CON to Windows, so the guard is on the stem, not the extension.
        assertEquals(setOf("CON-page.md"), exported().pageNames)
    }

    /** `docs/critiques/syntax-highlighting-function.md` recorded this and left it: the root's
     * `CLAUDE.md` is the zip's own, and a root page by that name wrote a second entry of it. */
    @Test
    fun `a root page called CLAUDE does not take the zip's own CLAUDE md`() {
        val id = page("CLAUDE")
        addBlock(id, BlockType.PARAGRAPH, "my notes about the model")

        val out = exported()

        assertTrue(text(out, "CLAUDE.md").startsWith("# These notes"))
        assertEquals("# CLAUDE\n\nmy notes about the model\n", text(out, "CLAUDE (2).md"))
    }

    @Test
    fun `nor does one whose name differs only in case, which extraction would merge`() {
        page("Claude")
        // Two entries in the zip, one file on Windows or macOS — the silent overwrite the
        // per-directory collision rule exists to prevent, with the readme on the losing side.
        assertEquals(setOf("Claude (2).md"), exported().pageNames)
    }

    @Test
    fun `below the root a page may be called CLAUDE`() {
        val trip = page("Trip")
        page("CLAUDE", parentId = trip)
        assertTrue("Trip/CLAUDE.md" in exported().keys)
    }

    @Test
    fun `the trash and templates are not exported`() {
        page("Live")
        page("Deleted", deleted = true)
        page("Template", isTemplate = true)

        assertEquals(setOf("Live.md"), exported().pageNames)
    }

    // ------------------------------------------------------------------ links that must resolve

    @Test
    fun `an image is stored once and linked relative to the page that uses it`() {
        val trip = page("Trip")
        val day = page("Day one", parentId = trip)
        val block = addBlock(day, BlockType.IMAGE, imagePath = "local:holiday.png")
        localImages.written["holiday.png"] = byteArrayOf(1, 2, 3)

        val out = exported()

        assertArrayEquals(byteArrayOf(1, 2, 3), out["assets/${block.uid}.png"])
        // `../assets/...`, not `assets/...`: Markdown resolves a link against the file holding it,
        // so a page one directory down needs to climb out first.
        assertTrue("../assets/${block.uid}.png" in text(out, "Trip/Day one.md"))
    }

    @Test
    fun `an image whose bytes this device does not hold is not linked into nothing`() {
        val id = page("Trip")
        addBlock(id, BlockType.IMAGE, imagePath = "local:missing.png")

        val out = exported()

        // §9.4 — the picture may simply not have synced here yet. No asset, and a comment in place
        // of a link every reader would draw as broken.
        assertNull(out.keys.firstOrNull { it.startsWith("assets/") })
        assertTrue("image not available" in text(out, "Trip.md"))
    }

    @Test
    fun `a page mention resolves to the other page's file from where the link sits`() {
        val trip = page("Trip")
        val day = page("Day one", parentId = trip)
        addBlock(day, BlockType.PARAGRAPH, "see Trip", spans = listOf(FormattingSpan(4, 8, SpanStyle.PageMention(trip))))

        assertTrue("[Trip](../Trip.md)" in text(exported(), "Trip/Day one.md"))
    }

    @Test
    fun `a mention of a page that is not in the export stays as readable text`() {
        val id = page("Trip")
        addBlock(id, BlockType.PARAGRAPH, "see Gone", spans = listOf(FormattingSpan(4, 8, SpanStyle.PageMention(999))))

        // A file full of dead links is worse than a file that lost a link.
        assertTrue("see Gone" in text(exported(), "Trip.md"))
    }

    @Test
    fun `the export reports what it wrote`() {
        val id = page("Trip")
        val block = addBlock(id, BlockType.IMAGE, imagePath = "local:a.png")
        localImages.written["a.png"] = byteArrayOf(9)
        page("Other")

        val out = ByteArrayOutputStream()
        val result = runBlocking { exporter.export(out) }

        assertEquals(2, result.pages)
        assertEquals(1, result.images)
        assertTrue(entriesOf(out.toByteArray()).containsKey("assets/${block.uid}.png"))
    }

    // ----------------------------------------------------------- §0.10 item 6 — JSON Canvas

    @Test
    fun `a canvas page is a canvas file in the zip, not an md, and a mention links it`() = runBlocking {
        val board = page("Garden plan", kind = PageKind.CANVAS)
        val target = page("Compost bins")
        val canvasId = canvasDao.insert(PageCanvas(pageId = board, createdAt = at(1), updatedAt = at(1)))
        val text = nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.TEXT, x = 10.4f, y = 20.6f, text = "Beds by the wall", createdAt = at(1), updatedAt = at(1)))
        val embed = nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.PAGE_EMBED, x = 300f, y = 20f, embeddedPageId = target, createdAt = at(1), updatedAt = at(1)))
        nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.FRAME, x = 0f, y = 0f, width = 400f, height = 300f, text = "Beds", createdAt = at(1), updatedAt = at(1)))
        edgeDao.insert(CanvasEdge(canvasId = canvasId, fromNodeId = text, toNodeId = embed, direction = CanvasArrowDirection.TWO_WAY, label = "then"))
        val note = page("Notes")
        addBlock(note, BlockType.PAGE_MENTION, content = "Garden plan", mentionedPageId = board)

        val entries = exported()

        assertTrue("the canvas is a .canvas file", entries.containsKey("Garden plan.canvas"))
        assertTrue("no .md twin", !entries.containsKey("Garden plan.md"))
        val doc = text(entries, "Garden plan.canvas")
        assertTrue(doc.contains("\"type\": \"text\"") && doc.contains("\"text\": \"Beds by the wall\""))
        assertTrue("integers, rounded", doc.contains("\"x\": 10,") && doc.contains("\"y\": 21,"))
        assertTrue("a page card is a file node at the page's path", doc.contains("\"type\": \"file\"") && doc.contains("\"file\": \"Compost bins.md\""))
        assertTrue("a frame is a group with its label", doc.contains("\"type\": \"group\"") && doc.contains("\"label\": \"Beds\"") && doc.contains("\"width\": 400"))
        assertTrue("a two-way arrow", doc.contains("\"fromEnd\": \"arrow\"") && doc.contains("\"label\": \"then\""))
        assertTrue("the mention links the canvas file", text(entries, "Notes.md").contains("(Garden%20plan.canvas)"))
    }

    @Test
    fun `a page card whose page the export does not carry becomes a text node with its title`() = runBlocking {
        val board = page("Board", kind = PageKind.CANVAS)
        val gone = page("Old idea", deleted = true)
        val canvasId = canvasDao.insert(PageCanvas(pageId = board, createdAt = at(1), updatedAt = at(1)))
        nodeDao.insert(CanvasNode(canvasId = canvasId, type = CanvasNodeType.PAGE_EMBED, x = 0f, y = 0f, embeddedPageId = gone, createdAt = at(1), updatedAt = at(1)))

        val doc = text(exported(), "Board.canvas")

        assertTrue(doc.contains("\"type\": \"text\"") && doc.contains("\"text\": \"Old idea\""))
        assertTrue(!doc.contains("\"file\""))
    }
}
