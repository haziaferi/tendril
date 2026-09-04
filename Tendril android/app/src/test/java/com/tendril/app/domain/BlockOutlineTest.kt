package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * §3.1.1 — one level of nesting, and the rule that outranks it: nothing silently disappears.
 *
 * `Block.parentBlockId` has been in the schema (indexed) from the start and `PageDetailScreen`
 * has always rendered an indent for it, but the block list filtered to `parentBlockId == null`,
 * so a child was never drawn at all. Spec line 46 records the Notion importer flattening
 * imported nesting for exactly that reason — "rather than risking silently-invisible content".
 *
 * So the assertions below care less about the tidy cases than about the malformed ones. A child
 * pointing at a parent on another page, a grandchild, a cycle two devices' merges could produce:
 * none of them may cost the person a block. A collapsed toggle is the one legitimate way for
 * content not to be drawn, because a person asked for it and one tap undoes it.
 */
class BlockOutlineTest {

    private fun block(
        id: Long,
        order: Int,
        parent: Long? = null,
        type: BlockType = BlockType.PARAGRAPH,
        expanded: Boolean = true,
    ) = Block(
        id = id,
        pageId = 1L,
        type = type,
        order = order,
        parentBlockId = parent,
        toggleExpanded = expanded,
        createdAt = Instant.ofEpochMilli(0),
        updatedAt = Instant.ofEpochMilli(0),
    )

    private fun ids(outline: List<OutlineBlock>) = outline.map { it.block.id }
    private fun depths(outline: List<OutlineBlock>) = outline.map { it.depth }

    // ------------------------------------------------------------ the ordinary shape

    @Test
    fun `a flat page comes out in order`() {
        val out = outlineOf(listOf(block(3, 2), block(1, 0), block(2, 1)))

        assertEquals(listOf(1L, 2L, 3L), ids(out))
        assertTrue("nothing is indented", depths(out).all { it == 0 })
    }

    @Test
    fun `a child is drawn straight after its parent, indented`() {
        val out = outlineOf(listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2)))

        assertEquals(listOf(1L, 2L, 3L), ids(out))
        assertEquals(listOf(0, MAX_BLOCK_DEPTH, 0), depths(out))
    }

    @Test
    fun `children keep their own order under the parent`() {
        // Deliberately supplied back-to-front: `order` decides, not list position.
        val out = outlineOf(listOf(block(1, 0), block(3, 2, parent = 1), block(2, 1, parent = 1)))

        assertEquals(listOf(1L, 2L, 3L), ids(out))
        assertEquals(listOf(0, 1, 1), depths(out))
    }

    @Test
    fun `a child sorts with its parent, not by its own order among roots`() {
        // Parent at order 5, its child at order 1: the child follows the parent regardless.
        val out = outlineOf(listOf(block(1, 0), block(2, 5), block(3, 1, parent = 2)))

        assertEquals(listOf(1L, 2L, 3L), ids(out))
        assertEquals(listOf(0, 0, 1), depths(out))
    }

    // ------------------------------------------------- the malformed shapes, all still drawn

    @Test
    fun `a child whose parent is not on this page is drawn at the top level`() {
        // Reachable from a merge: the parent was purged on another device, the child was not.
        val out = outlineOf(listOf(block(1, 0), block(2, 1, parent = 999)))

        assertEquals("the orphan must still appear", listOf(1L, 2L), ids(out))
        assertEquals(listOf(0, 0), depths(out))
    }

    @Test
    fun `a grandchild is re-attached to its top-level ancestor`() {
        // §3.1.1 has no depth 2, so 3 is drawn under 1 rather than hidden or indented twice.
        val out = outlineOf(listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 2)))

        assertEquals(listOf(1L, 2L, 3L), ids(out))
        assertEquals("nothing may exceed one level", listOf(0, 1, 1), depths(out))
        assertTrue(depths(out).all { it <= MAX_BLOCK_DEPTH })
    }

    @Test
    fun `a parent cycle still renders both blocks`() {
        // No UI creates this; a merge of two devices' reparenting edits could.
        val out = outlineOf(listOf(block(1, 0, parent = 2), block(2, 1, parent = 1)))

        assertEquals("a cycle must not swallow either block", setOf(1L, 2L), ids(out).toSet())
        assertEquals(2, out.size)
    }

    @Test
    fun `a block parented to itself still renders`() {
        val out = outlineOf(listOf(block(1, 0, parent = 1), block(2, 1)))

        assertEquals(setOf(1L, 2L), ids(out).toSet())
    }

    @Test
    fun `every block is accounted for exactly once`() {
        // The invariant the whole function exists to protect, over a deliberately messy page.
        val blocks = listOf(
            block(1, 0),
            block(2, 1, parent = 1),
            block(3, 2, parent = 2),      // grandchild
            block(4, 3, parent = 404),    // orphan
            block(5, 4, parent = 6),      // cycle half
            block(6, 5, parent = 5),      // cycle half
            block(7, 6),
        )

        val out = outlineOf(blocks)

        assertEquals("no block may be dropped or duplicated", blocks.size, out.size)
        assertEquals(blocks.map { it.id }.toSet(), ids(out).toSet())
        assertTrue(depths(out).all { it in 0..MAX_BLOCK_DEPTH })
    }

    // ------------------------------------------------------------ the one legitimate hide

    @Test
    fun `a collapsed toggle hides its children`() {
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.TOGGLE, expanded = false),
                block(2, 1, parent = 1),
                block(3, 2),
            )
        )

        assertEquals(listOf(1L, 3L), ids(out))
    }

    @Test
    fun `an expanded toggle shows them`() {
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.TOGGLE, expanded = true),
                block(2, 1, parent = 1),
                block(3, 2),
            )
        )

        assertEquals(listOf(1L, 2L, 3L), ids(out))
    }

    @Test
    fun `a collapsed non-toggle hides nothing`() {
        // toggleExpanded defaults false-able on any row; only TOGGLE gets to act on it.
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.PARAGRAPH, expanded = false),
                block(2, 1, parent = 1),
            )
        )

        assertEquals(listOf(1L, 2L), ids(out))
    }

    @Test
    fun `an empty page produces an empty outline`() {
        assertTrue(outlineOf(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------ the indent target

    @Test
    fun `indent targets the nearest preceding top-level sibling`() {
        val blocks = listOf(block(1, 0), block(2, 1), block(3, 2))

        assertEquals(2L, indentTargetFor(blocks[2], blocks)?.id)
    }

    @Test
    fun `indent skips over children when picking the target`() {
        // 2 is already a child of 1, so the nearest *top-level* predecessor of 3 is 1.
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2))

        assertEquals(1L, indentTargetFor(blocks[2], blocks)?.id)
    }

    @Test
    fun `the first block has nothing to indent under`() {
        val blocks = listOf(block(1, 0), block(2, 1))

        assertNull(indentTargetFor(blocks[0], blocks))
    }

    @Test
    fun `an already-indented block cannot indent further`() {
        // §3.1.1 stops at one level, so this is where indenting ends.
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 1))

        assertNull(indentTargetFor(blocks[2], blocks))
    }
}
