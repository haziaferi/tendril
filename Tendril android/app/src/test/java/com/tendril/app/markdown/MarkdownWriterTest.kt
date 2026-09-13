package com.tendril.app.markdown

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.notionimport.NotionMarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * §7 in reverse — blocks out as Markdown.
 *
 * The load-bearing test here is [a callout survives a round trip through this app's own importer]:
 * the two directions were written eighteen months apart against the same syntax, and the only way
 * to know they agree is to run one into the other. Everything above it checks a single case; that
 * one checks the contract.
 */
class MarkdownWriterTest {

    private var nextId = 1L

    private fun block(
        type: BlockType,
        content: String = "",
        spans: List<FormattingSpan> = emptyList(),
        checked: Boolean? = null,
        codeLanguage: String? = null,
        calloutIcon: String? = null,
        parentBlockId: Long? = null,
        mentionedPageId: Long? = null,
        imagePath: String? = null,
    ) = Block(
        id = nextId++,
        pageId = 1,
        type = type,
        order = 0,
        parentBlockId = parentBlockId,
        content = content,
        formattingSpans = spans,
        checked = checked,
        codeLanguage = codeLanguage,
        calloutIcon = calloutIcon,
        mentionedPageId = mentionedPageId,
        imagePath = imagePath,
        createdAt = Instant.ofEpochMilli(1),
        updatedAt = Instant.ofEpochMilli(1),
    )

    private fun render(vararg blocks: Block) = MarkdownWriter.render(blocks.toList()).trimEnd('\n')

    // ------------------------------------------------------------------ block types

    @Test
    fun `headings paragraphs and dividers take their usual syntax`() {
        val out = render(
            block(BlockType.HEADING_1, "Trip"),
            block(BlockType.PARAGRAPH, "We left early."),
            block(BlockType.HEADING_3, "Day one"),
            block(BlockType.DIVIDER),
        )
        assertEquals("# Trip\n\nWe left early.\n\n### Day one\n\n---", out)
    }

    @Test
    fun `numbered lists are written with real ordinals and restart after a break`() {
        val out = render(
            block(BlockType.NUMBERED_LIST_ITEM, "one"),
            block(BlockType.NUMBERED_LIST_ITEM, "two"),
            block(BlockType.PARAGRAPH, "aside"),
            block(BlockType.NUMBERED_LIST_ITEM, "one again"),
        )
        // `1.` repeated renders identically, but the file is meant to be read in a plain editor,
        // which is the whole reason for choosing this format over the JSON already available.
        assertTrue("1. one" in out)
        assertTrue("2. two" in out)
        assertTrue("1. one again" in out)
    }

    @Test
    fun `a to-do carries its checkbox state`() {
        assertEquals("- [x] packed", render(block(BlockType.TODO, "packed", checked = true)))
        assertEquals("- [ ] unpacked", render(block(BlockType.TODO, "unpacked", checked = false)))
    }

    @Test
    fun `a code block fences with its language, and without one when it has none`() {
        assertEquals("```kotlin\nval x = 1\n```", render(block(BlockType.CODE, "val x = 1", codeLanguage = "kotlin")))
        // A bare fence is exactly what the importer reads back as "plain text".
        assertEquals("```\nplain\n```", render(block(BlockType.CODE, "plain")))
    }

    @Test
    fun `a block reference is a quote of its cached words, with the source when it is exported`() {
        val linked = MarkdownWriter.render(listOf(block(BlockType.BLOCK_REFERENCE, "pack socks\nand a hat", mentionedPageId = 7)), { null }, { if (it == 7L) "Trip.md" else null })
        assertEquals("> pack socks\n> and a hat\n> — [source](Trip.md)", linked.trimEnd('\n'))
        assertEquals("> gone", render(block(BlockType.BLOCK_REFERENCE, "gone", mentionedPageId = 99)))
    }

    @Test
    fun `a multi-line quote marks every line`() {
        assertEquals("> first\n> second", render(block(BlockType.QUOTE, "first\nsecond")))
    }

    @Test
    fun `a child block is indented under its parent`() {
        val parent = block(BlockType.BULLETED_LIST_ITEM, "outer")
        val out = render(parent, block(BlockType.BULLETED_LIST_ITEM, "inner", parentBlockId = parent.id))
        assertEquals("- outer\n\n    - inner", out)
    }

    @Test
    fun `a grandchild is indented twice, and a collapsed toggle's subtree is still written`() {
        // §0.6.1 — depth is unlimited, and export takes the outline's depth rather than a flag.
        val toggle = block(BlockType.TOGGLE, "closed").copy(toggleExpanded = false)
        val child = block(BlockType.BULLETED_LIST_ITEM, "one", parentBlockId = toggle.id)
        val grandchild = block(BlockType.BULLETED_LIST_ITEM, "two", parentBlockId = child.id)
        // Handed over out of tree order on purpose: the writer must follow the outline, not the list.
        val out = render(grandchild, toggle, child)
        assertEquals("closed\n\n    - one\n\n        - two", out)
    }

    @Test
    fun `a canvas block is a labelled link to the canvas page, or its label alone`() {
        // §0.6.3 — the board itself is not Markdown; the link is the most the format can carry.
        val linked = MarkdownWriter.render(listOf(block(BlockType.CANVAS, "Trip board", mentionedPageId = 7)), { null }, { if (it == 7L) "Trip board.md" else null })
        assertEquals("[Canvas: Trip board](Trip%20board.md)\n", linked)
        val unlinked = MarkdownWriter.render(listOf(block(BlockType.CANVAS, "Gone", mentionedPageId = 99)), { null }, { null })
        assertEquals("Canvas: Gone\n", unlinked)
    }

    // ------------------------------------------------------------------ inline spans

    @Test
    fun `spans become markup around the right characters`() {
        val out = render(
            block(
                BlockType.PARAGRAPH, "bold italic struck code",
                spans = listOf(
                    FormattingSpan(0, 4, SpanStyle.Bold),
                    FormattingSpan(5, 11, SpanStyle.Italic),
                    FormattingSpan(12, 18, SpanStyle.Strikethrough),
                    FormattingSpan(19, 23, SpanStyle.InlineCode),
                ),
            )
        )
        assertEquals("**bold** *italic* ~~struck~~ `code`", out)
    }

    @Test
    fun `nested spans close innermost first`() {
        val out = render(
            block(
                BlockType.PARAGRAPH, "abcd",
                spans = listOf(FormattingSpan(0, 4, SpanStyle.Bold), FormattingSpan(1, 3, SpanStyle.Italic)),
            )
        )
        // Not `**a*bc**d*` — closing in the order the openings were emitted would interleave the
        // markers and every reader would render the result differently.
        assertEquals("**a*bc*d**", out)
    }

    @Test
    fun `a link carries its target and a page mention resolves through the caller`() {
        val link = render(
            block(BlockType.PARAGRAPH, "see docs", spans = listOf(FormattingSpan(4, 8, SpanStyle.Link("https://x.test/a b"))))
        )
        assertEquals("see [docs](https://x.test/a%20b)", link)

        val mention = MarkdownWriter.render(
            listOf(block(BlockType.PARAGRAPH, "see Trip", spans = listOf(FormattingSpan(4, 8, SpanStyle.PageMention(7))))),
            pageLinkFor = { if (it == 7L) "Trip.md" else null },
        ).trimEnd('\n')
        assertEquals("see [Trip](Trip.md)", mention)
    }

    @Test
    fun `a mention of a page outside the export stays as plain text`() {
        val out = render(
            block(BlockType.PARAGRAPH, "see Trip", spans = listOf(FormattingSpan(4, 8, SpanStyle.PageMention(7))))
        )
        // Linking into nothing would produce a file full of dead links; the label is what survives.
        assertEquals("see Trip", out)
    }

    @Test
    fun `a span whose bounds do not fit the content is dropped, not thrown on`() {
        val out = render(
            block(BlockType.PARAGRAPH, "short", spans = listOf(FormattingSpan(0, 99, SpanStyle.Bold)))
        )
        // An export is the last operation that should fail on one bad row — getting the person's
        // data out is the point of it.
        assertEquals("short", out)
    }

    // ------------------------------------------------------------------ images and escaping

    @Test
    fun `an image links its asset, and says so plainly when this device has no copy`() {
        val img = block(BlockType.IMAGE, "a view", imagePath = "/data/x.png")
        val linked = MarkdownWriter.render(listOf(img), assetPathFor = { "assets/the view.png" }).trimEnd('\n')
        assertEquals("![a view](assets/the%20view.png)", linked)

        // §9.4 — a block whose picture has not synced to this device yet. A comment holds its
        // place; `![]()` would render as a broken image in every reader.
        val missing = render(img)
        assertTrue(missing.startsWith("<!-- image not available on this device:"))
    }

    @Test
    fun `a paragraph that starts like markup is escaped so it reads back as a paragraph`() {
        assertEquals("\\# not a heading", render(block(BlockType.PARAGRAPH, "# not a heading")))
        assertEquals("\\- not a list", render(block(BlockType.PARAGRAPH, "- not a list")))
        // Only at the start of a line, and only structural tokens: escaping every `*` in running
        // text would make the file unpleasant to read, which defeats the format.
        assertEquals("a * b _ c", render(block(BlockType.PARAGRAPH, "a * b _ c")))
    }

    // ------------------------------------------------------------------ the contract

    @Test
    fun `a callout survives a round trip through this app's own importer`() {
        val written = render(block(BlockType.CALLOUT, "mind the gap", calloutIcon = "💡"))
        assertEquals("<aside>\n💡 mind the gap\n</aside>", written)

        val reparsed = NotionMarkdownParser.parse(written)
        assertEquals(1, reparsed.size)
        assertEquals(BlockType.CALLOUT, reparsed[0].type)
        assertEquals("mind the gap", reparsed[0].content)
        assertEquals("💡", reparsed[0].calloutIcon)
    }

    @Test
    fun `a page of mixed blocks survives a round trip through this app's own importer`() {
        val written = render(
            block(BlockType.HEADING_2, "Day one"),
            block(BlockType.PARAGRAPH, "we walked", spans = listOf(FormattingSpan(3, 9, SpanStyle.Bold))),
            block(BlockType.BULLETED_LIST_ITEM, "boots"),
            block(BlockType.TODO, "map", checked = true),
            block(BlockType.QUOTE, "it rained"),
            block(BlockType.CODE, "val x = 1", codeLanguage = "kotlin"),
            block(BlockType.DIVIDER),
        )
        val reparsed = NotionMarkdownParser.parse(written)

        assertEquals(
            listOf(
                BlockType.HEADING_2, BlockType.PARAGRAPH, BlockType.BULLETED_LIST_ITEM,
                BlockType.TODO, BlockType.QUOTE, BlockType.CODE, BlockType.DIVIDER,
            ),
            reparsed.map { it.type },
        )
        assertEquals("we walked", reparsed[1].content)
        assertEquals(1, reparsed[1].spans.size)
        assertEquals(true, reparsed[3].checked)
        assertEquals("kotlin", reparsed[5].codeLanguage)
    }
}
