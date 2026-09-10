package com.tendril.app.markdown

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.sync.FakeBlockDao
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
    private val exporter = MarkdownExporter(pageDao, blockDao, localImages)

    private fun at(m: Long) = Instant.ofEpochMilli(m)

    private fun page(
        title: String,
        parentId: Long? = null,
        deleted: Boolean = false,
        isTemplate: Boolean = false,
        icon: String? = null,
    ): Long = store.seedPage(
        Page(
            uid = "uid-${title.lowercase().replace(' ', '-')}-${store.pages.size}",
            title = title,
            icon = icon,
            kind = PageKind.PAGE,
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

        val names = exported().keys
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

        assertEquals(setOf("A.md", "B.md", "A/Notes.md", "B/Notes.md"), exported().keys)
    }

    @Test
    fun `a title no filesystem would accept is made safe, and an empty one falls back to the uid`() {
        page("a/b:c*d?e")
        val blank = page("   ")

        val names = exported().keys
        assertTrue("illegal characters must not survive", names.any { it == "a b c d e.md" })
        // An oddly named file is recoverable; a file that could not be created is not.
        assertTrue(names.any { it == store.pages.getValue(blank).uid + ".md" })
    }

    @Test
    fun `a Windows device name is not used as a file name`() {
        page("CON")
        // `CON.md` is still CON to Windows, so the guard is on the stem, not the extension.
        assertEquals(setOf("CON-page.md"), exported().keys)
    }

    @Test
    fun `the trash and templates are not exported`() {
        page("Live")
        page("Deleted", deleted = true)
        page("Template", isTemplate = true)

        assertEquals(setOf("Live.md"), exported().keys)
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
}
