package com.tendril.app.notionimport

import com.tendril.app.data.page.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §7 / §3.1.1 — imported nesting survives now, one level deep.
 *
 * The importer flattened it deliberately, and the reason was never the format: the in-app editor
 * filtered children out of its own block list, so assigning a parent would have made imported
 * content silently invisible — worse than §7.2's documented "the collapse becomes permanently
 * open". `outlineOf` draws children now, so that reason has expired.
 *
 * What has not expired is the promise underneath it: **content survives even when hierarchy
 * cannot**. Anything indented more deeply than §3.1.1's single level arrives at depth 1 rather
 * than being dropped, and a page that opens on an indented line still imports every block.
 */
class NotionImportNestingTest {

    private fun parse(md: String) = NotionMarkdownParser.parse(md.trimIndent())

    // ------------------------------------------------------------ the ordinary case

    @Test
    fun `an indented sub-list item is nested one level`() {
        val blocks = NotionMarkdownParser.parse("- Parent\n    - Child\n- Sibling")

        assertEquals(listOf(0, 1, 0), blocks.map { it.depth })
        assertEquals(listOf("Parent", "Child", "Sibling"), blocks.map { it.content })
    }

    @Test
    fun `two-space indentation counts too`() {
        // Notion exports four; two is the common Markdown alternative and costs nothing to accept.
        val blocks = NotionMarkdownParser.parse("- Parent\n  - Child")

        assertEquals(listOf(0, 1), blocks.map { it.depth })
    }

    @Test
    fun `a tab counts as an indent`() {
        val blocks = NotionMarkdownParser.parse("- Parent\n\t- Child")

        assertEquals(listOf(0, 1), blocks.map { it.depth })
    }

    @Test
    fun `a single leading space is not an indent`() {
        // Below the threshold: stray alignment in the source should not invent hierarchy.
        val blocks = NotionMarkdownParser.parse("- Parent\n - Not nested")

        assertEquals(listOf(0, 0), blocks.map { it.depth })
    }

    @Test
    fun `numbered items and to-dos nest as well`() {
        val numbered = NotionMarkdownParser.parse("1. Parent\n    2. Child")
        val todos = NotionMarkdownParser.parse("- [ ] Parent\n    - [x] Child")

        assertEquals(listOf(0, 1), numbered.map { it.depth })
        assertEquals(listOf(0, 1), todos.map { it.depth })
        assertEquals(BlockType.NUMBERED_LIST_ITEM, numbered[1].type)
        assertEquals(BlockType.TODO, todos[1].type)
    }

    @Test
    fun `an indented paragraph nests, which is how toggle children arrive`() {
        // §7.2 — a Notion toggle degrades to indented plain content with no marker of its own.
        val blocks = NotionMarkdownParser.parse("- Toggle\n    Some child text")

        assertEquals(listOf(0, 1), blocks.map { it.depth })
        assertEquals(BlockType.PARAGRAPH, blocks[1].type)
    }

    // ------------------------------------------------- content survives what hierarchy cannot

    @Test
    fun `deeper indentation is kept level for level`() {
        // §0.6.1 — this used to clamp to one level; every four columns is a level now.
        val blocks = NotionMarkdownParser.parse("- One\n    - Two\n        - Three\n            - Four")

        assertEquals("every level must still produce a block", 4, blocks.size)
        assertEquals(listOf("One", "Two", "Three", "Four"), blocks.map { it.content })
        assertEquals(listOf(0, 1, 2, 3), blocks.map { it.depth })
    }

    @Test
    fun `a page opening on an indented line still imports every block`() {
        // There is no preceding top-level block to hang from; the importer leaves it top-level
        // rather than discarding it.
        val blocks = NotionMarkdownParser.parse("    - Orphan\n- Later")

        assertEquals(2, blocks.size)
        assertEquals(listOf("Orphan", "Later"), blocks.map { it.content })
    }

    // ------------------------------------------------------------ what stays top-level

    @Test
    fun `structural blocks ignore stray indentation`() {
        // A stray indent on a heading or divider is far likelier to be source noise than
        // intended hierarchy, and §3.1.1's nesting is list items and toggle children.
        val blocks = NotionMarkdownParser.parse("- Parent\n    # Heading\n    ---")

        assertEquals(BlockType.HEADING_1, blocks[1].type)
        assertEquals(0, blocks[1].depth)
        assertEquals(BlockType.DIVIDER, blocks[2].type)
        assertEquals(0, blocks[2].depth)
    }

    @Test
    fun `an unindented document nests nothing`() {
        val blocks = parse(
            """
            # Title
            Some text
            - One
            - Two
            """
        )

        assertTrue("a flat document must stay flat", blocks.all { it.depth == 0 })
    }
}
