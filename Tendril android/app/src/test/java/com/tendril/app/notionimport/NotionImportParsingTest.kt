package com.tendril.app.notionimport

import com.tendril.app.data.page.BlockType
import com.tendril.app.data.pagedatabase.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §7.4 — the Notion importer's pure parsing logic (no Room/Android dependency needed for any
 * of these three) is the one part of this project with the most to gain from a JVM unit test:
 * it hand-parses a foreign, loosely-specified format under a "must not silently break or drop
 * content" bar, exactly the kind of edge-case-heavy logic that's cheap to check here and
 * expensive to get wrong in the field. First test file in this project — worth starting here
 * rather than not at all, given how directly this caught a real bug during development
 * (`markdown_parsesHeadingListsQuoteCalloutCodeDividerAndInline` initially failed: internal
 * links were resolving to the *parent folder's* Notion id instead of the linked page's own,
 * since a nested export path carries both).
 */
class NotionImportParsingTest {

    // ---- CSV ----

    @Test
    fun csv_parsesQuotedFieldsAndBlankCells() {
        val text = "Name,Done,Due Date,Priority\n" +
            "Buy milk,Yes,2026-09-01,Low\n" +
            "Finish report,No,2026-09-05,High\n" +
            "Call plumber,No,,Medium\n"
        val rows = NotionCsvParser.parse(text)
        assertEquals(4, rows.size)
        assertEquals(listOf("Name", "Done", "Due Date", "Priority"), rows[0])
        assertEquals(listOf("Call plumber", "No", "", "Medium"), rows[3])
    }

    @Test
    fun csv_handlesEmbeddedCommaNewlineAndEscapedQuote() {
        val text = "Name,Notes\n\"Smith, John\",\"Line1\nLine2 says \"\"hi\"\"\"\n"
        val rows = NotionCsvParser.parse(text)
        assertEquals(2, rows.size)
        assertEquals("Smith, John", rows[1][0])
        assertEquals("Line1\nLine2 says \"hi\"", rows[1][1])
    }

    // ---- Property type inference ----

    @Test
    fun inference_detectsCheckboxDateNumberAndSelect() {
        assertEquals(PropertyType.CHECKBOX, NotionPropertyTypeInference.infer(listOf("Yes", "No", "No")).type)
        assertEquals(PropertyType.DATE, NotionPropertyTypeInference.infer(listOf("2026-09-01", "2026-09-05")).type)
        assertEquals(PropertyType.NUMBER, NotionPropertyTypeInference.infer(listOf("1", "42", "-3.5")).type)
        val select = NotionPropertyTypeInference.infer(listOf("Low", "High", "Medium", "Low"))
        assertEquals(PropertyType.SELECT, select.type)
        assertTrue(select.config!!.contains("Low"))
    }

    @Test
    fun inference_fallsBackToTextForFreeform() {
        val values = listOf("This is a long free-text note about something specific.", "Another distinct sentence here.")
        assertEquals(PropertyType.TEXT, NotionPropertyTypeInference.infer(values).type)
    }

    @Test
    fun inference_parsesLongFormNotionDate() {
        val date = NotionPropertyTypeInference.parseNotionDate("August 30, 2026")
        assertEquals("2026-08-30", date.toString())
    }

    // ---- Markdown ----

    @Test
    fun markdown_parsesHeadingListsQuoteCalloutCodeDividerAndInline() {
        val md = """
            # Project Plan

            This links to a **child page**: [Design Notes](Project%20Plan%2011111111111111111111111111111111/Design%20Notes%2022222222222222222222222222222222.md).

            - [ ] Draft the outline
            - [x] Pick a name

            > A quote from the kickoff meeting.

            <aside>
            💡 Remember to check the budget before committing.
            </aside>

            ```kotlin
            fun hello() = println("hi")
            ```

            ---

            Some *italic*, some `inline code`, and a [normal link](https://example.com).
        """.trimIndent()

        val blocks = NotionMarkdownParser.parse(md)
        val types = blocks.map { it.type }
        assertEquals(
            listOf(
                BlockType.HEADING_1, BlockType.PARAGRAPH, BlockType.TODO, BlockType.TODO,
                BlockType.QUOTE, BlockType.CALLOUT, BlockType.CODE, BlockType.DIVIDER, BlockType.PARAGRAPH,
            ),
            types,
        )

        assertEquals("Project Plan", blocks[0].content)

        val linkParagraph = blocks[1]
        assertEquals("This links to a child page: Design Notes.", linkParagraph.content)
        val boldSpan = linkParagraph.spans.first { it.kind is ParsedSpanKind.Bold }
        assertEquals("child page", linkParagraph.content.substring(boldSpan.start, boldSpan.end))
        val pageRefSpan = linkParagraph.spans.first { it.kind is ParsedSpanKind.PageRef }
        assertEquals("Design Notes", linkParagraph.content.substring(pageRefSpan.start, pageRefSpan.end))
        // The link target must be the CHILD page's own id (last id in the path), not the
        // parent folder's id that also appears earlier in the same relative path.
        assertEquals("22222222222222222222222222222222", (pageRefSpan.kind as ParsedSpanKind.PageRef).notionId)

        assertEquals(false, blocks[2].checked)
        assertEquals("Draft the outline", blocks[2].content)
        assertEquals(true, blocks[3].checked)
        assertEquals("Pick a name", blocks[3].content)

        assertEquals("A quote from the kickoff meeting.", blocks[4].content)

        assertEquals("💡", blocks[5].calloutIcon)
        assertEquals("Remember to check the budget before committing.", blocks[5].content)

        assertEquals("kotlin", blocks[6].codeLanguage)
        assertTrue(blocks[6].content.contains("fun hello"))

        val inlineParagraph = blocks[8]
        val italicSpan = inlineParagraph.spans.first { it.kind is ParsedSpanKind.Italic }
        assertEquals("italic", inlineParagraph.content.substring(italicSpan.start, italicSpan.end))
        val codeSpan = inlineParagraph.spans.first { it.kind is ParsedSpanKind.InlineCode }
        assertEquals("inline code", inlineParagraph.content.substring(codeSpan.start, codeSpan.end))
        val extLink = inlineParagraph.spans.first { it.kind is ParsedSpanKind.ExternalLink } .kind as ParsedSpanKind.ExternalLink
        assertEquals("https://example.com", extLink.url)
    }

    @Test
    fun markdown_flattensNestedListsAndOrdersNumberedItems() {
        val md = "1. First numbered item\n2. Second numbered item\n"
        val blocks = NotionMarkdownParser.parse(md)
        assertEquals(2, blocks.size)
        assertEquals(BlockType.NUMBERED_LIST_ITEM, blocks[0].type)
        assertEquals("First numbered item", blocks[0].content)
        assertEquals("Second numbered item", blocks[1].content)
    }

    @Test
    fun markdown_degradesTablesToCodeRatherThanDropping() {
        val md = "| A | B |\n| --- | --- |\n| 1 | 2 |\n"
        val blocks = NotionMarkdownParser.parse(md)
        assertEquals(1, blocks.size)
        assertEquals(BlockType.CODE, blocks[0].type)
        assertTrue(blocks[0].content.contains("| A | B |"))
    }

    @Test
    fun markdown_standaloneImageLineBecomesImageBlock() {
        val blocks = NotionMarkdownParser.parse("![a photo](assets/photo.png)")
        assertEquals(1, blocks.size)
        assertEquals(BlockType.IMAGE, blocks[0].type)
        assertEquals("assets/photo.png", blocks[0].imageAssetPath)
    }

    @Test
    fun markdown_unresolvableExternalLinkIsNotMistakenForAPageRef() {
        val blocks = NotionMarkdownParser.parse("A [plain link](https://example.com/not-a-page) here.")
        val span = blocks[0].spans.single()
        assertTrue(span.kind is ParsedSpanKind.ExternalLink)
        assertNull((span.kind as? ParsedSpanKind.PageRef)?.notionId)
    }
}
