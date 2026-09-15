package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.domain.find.FindMatch
import com.tendril.app.domain.find.findMatches
import com.tendril.app.domain.find.nextIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** §0.10 item 19 — find in page: matches are a pure function of the blocks and the query. */
class FindInPageTest {
    private val t0 = Instant.parse("2026-09-16T00:00:00Z")
    private fun block(id: Long, content: String, type: BlockType = BlockType.PARAGRAPH) =
        Block(id = id, pageId = 1, type = type, order = id.toInt(), content = content, createdAt = t0, updatedAt = t0)

    @Test
    fun `finds every occurrence, case-insensitively, with offsets, in page order`() {
        val blocks = listOf(block(1, "Child one!"), block(2, "The children's section"), block(3, "nothing here"))
        assertEquals(
            listOf(FindMatch(1, 0, 5), FindMatch(2, 4, 9)),
            findMatches(blocks, "CHILD"),
        )
    }

    @Test
    fun `two hits in one block are two matches`() {
        assertEquals(listOf(FindMatch(1, 0, 1), FindMatch(1, 2, 3)), findMatches(listOf(block(1, "a a")), "a"))
    }

    @Test
    fun `kinds without searchable text are skipped, a block reference's cached text is not`() {
        val blocks = listOf(
            block(1, "fine", BlockType.DIVIDER), block(2, "fine", BlockType.IMAGE),
            block(3, "fine", BlockType.PAGE_MENTION), block(4, "fine", BlockType.CANVAS),
            block(5, "fine", BlockType.BLOCK_REFERENCE), block(6, "fine", BlockType.CODE),
        )
        assertEquals(listOf(5L, 6L), findMatches(blocks, "fine").map { it.blockId })
    }

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(findMatches(listOf(block(1, "anything")), "  ").isEmpty())
    }

    @Test
    fun `the cursor wraps both ways and starts at an end when unset`() {
        assertEquals(0, nextIndex(current = 2, count = 3, forward = true))
        assertEquals(2, nextIndex(current = 0, count = 3, forward = false))
        assertEquals(0, nextIndex(current = null, count = 3, forward = true))
        assertEquals(2, nextIndex(current = null, count = 3, forward = false))
        assertNull(nextIndex(current = null, count = 0, forward = true))
    }
}
