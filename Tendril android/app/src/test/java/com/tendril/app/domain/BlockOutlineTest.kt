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
        assertEquals(listOf(0, 1, 0), depths(out))
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
    fun `a grandchild is drawn at depth 2, under its own parent`() {
        // §0.6.1 — depth is unlimited; this is the case §3.1.1 used to flatten to depth 1.
        val out = outlineOf(listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 2)))

        assertEquals(listOf(1L, 2L, 3L), ids(out))
        assertEquals(listOf(0, 1, 2), depths(out))
    }

    @Test
    fun `a subtree is drawn in full before the next sibling, however deep`() {
        // 1 > 2 > 3 > 4, then 5 as 1's second child, then 6 at the top: reading order is the tree.
        val out = outlineOf(
            listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 2), block(4, 3, parent = 3), block(5, 4, parent = 1), block(6, 5)),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), ids(out))
        assertEquals(listOf(0, 1, 2, 3, 1, 0), depths(out))
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
        // The grandchild is at 2 now; the orphan and both halves of the cycle are roots.
        assertEquals(2, out.first { it.block.id == 3L }.depth)
        assertEquals(0, out.first { it.block.id == 4L }.depth)
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
    fun `indent targets the nearest preceding sibling`() {
        val blocks = listOf(block(1, 0), block(2, 1), block(3, 2))

        assertEquals(2L, indentTargetFor(blocks[2], blocks)?.id)
    }

    @Test
    fun `indent skips over another parent's children when picking the target`() {
        // 2 is a child of 1, so the nearest *sibling* predecessor of 3 (a root) is 1.
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2))

        assertEquals(1L, indentTargetFor(blocks[2], blocks)?.id)
    }

    @Test
    fun `the first block has nothing to indent under`() {
        val blocks = listOf(block(1, 0), block(2, 1))

        assertNull(indentTargetFor(blocks[0], blocks))
    }

    @Test
    fun `an indented block indents again, under its previous sibling`() {
        // §0.6.1 — where §3.1.1 used to stop. 3 goes under 2, becoming a grandchild of 1.
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 1))

        assertEquals(2L, indentTargetFor(blocks[2], blocks)?.id)
    }

    @Test
    fun `the first child under a parent has nothing to indent under`() {
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1))

        assertNull(indentTargetFor(blocks[1], blocks))
    }

    // ------------------------------------------------------------------------- outdent

    @Test
    fun `outdent moves a block under its grandparent and adopts the siblings after it`() {
        // 1 > [2, 3, 4]; outdenting 3 gives 1 > [2], then 3 > [4] at the top: reading order kept.
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 1), block(4, 3, parent = 1))

        val plan = outdentPlanFor(blocks[2], blocks)!!
        assertNull(plan.newParentId)
        assertEquals(listOf(4L), plan.adoptedIds)
    }

    @Test
    fun `outdent from depth 2 lands at depth 1`() {
        val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 2))

        assertEquals(1L, outdentPlanFor(blocks[2], blocks)!!.newParentId)
    }

    @Test
    fun `a top-level block has no outdent`() {
        val blocks = listOf(block(1, 0))

        assertNull(outdentPlanFor(blocks[0], blocks))
    }

    // ------------------------------------------------------------------------------- §B7

    private fun positions(outline: List<OutlineBlock>) = outline.map { it.listPosition }

    @Test
    fun `a run of numbered items counts from 1`() {
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.NUMBERED_LIST_ITEM),
                block(2, 1, type = BlockType.NUMBERED_LIST_ITEM),
                block(3, 2, type = BlockType.NUMBERED_LIST_ITEM),
            ),
        )

        assertEquals(listOf(1, 2, 3), positions(out))
    }

    @Test
    fun `a list under a heading starts at 1, not the page position`() {
        // The actual regression: `block.order + 1` numbered from wherever `order` happened to
        // land, so a list starting partway down a page read "4.", "5.", "6." instead of "1.", "2.", "3.".
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.HEADING_1),
                block(2, 1, type = BlockType.PARAGRAPH),
                block(3, 2, type = BlockType.PARAGRAPH),
                block(4, 3, type = BlockType.NUMBERED_LIST_ITEM),
                block(5, 4, type = BlockType.NUMBERED_LIST_ITEM),
                block(6, 5, type = BlockType.NUMBERED_LIST_ITEM),
            ),
        )

        assertEquals(listOf(0, 0, 0, 1, 2, 3), positions(out))
    }

    @Test
    fun `deleting the middle item renumbers what follows`() {
        // "Deleting item 2 renumbers 3→2" — simulated as the deleted block simply not being
        // among the input, exactly how a real deletion reaches outlineOf.
        val withAll = outlineOf(
            listOf(
                block(1, 0, type = BlockType.NUMBERED_LIST_ITEM),
                block(2, 1, type = BlockType.NUMBERED_LIST_ITEM),
                block(3, 2, type = BlockType.NUMBERED_LIST_ITEM),
            ),
        )
        assertEquals(listOf(1, 2, 3), positions(withAll))

        val afterDeletingItem2 = outlineOf(
            listOf(
                block(1, 0, type = BlockType.NUMBERED_LIST_ITEM),
                block(3, 2, type = BlockType.NUMBERED_LIST_ITEM),
            ),
        )

        assertEquals(listOf(1L, 3L), ids(afterDeletingItem2))
        assertEquals("the old #3 becomes #2", listOf(1, 2), positions(afterDeletingItem2))
    }

    @Test
    fun `a non-numbered block breaks the run, restarting the next list at 1`() {
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.NUMBERED_LIST_ITEM),
                block(2, 1, type = BlockType.NUMBERED_LIST_ITEM),
                block(3, 2, type = BlockType.PARAGRAPH),
                block(4, 3, type = BlockType.NUMBERED_LIST_ITEM),
            ),
        )

        assertEquals(listOf(1, 2, 0, 1), positions(out))
    }

    @Test
    fun `a numbered list indented under a toggle numbers independently of one before it`() {
        val out = outlineOf(
            listOf(
                block(1, 0, type = BlockType.NUMBERED_LIST_ITEM),
                block(2, 1, type = BlockType.NUMBERED_LIST_ITEM),
                block(3, 2, type = BlockType.TOGGLE),
                block(4, 3, type = BlockType.NUMBERED_LIST_ITEM, parent = 3),
                block(5, 4, type = BlockType.NUMBERED_LIST_ITEM, parent = 3),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(out))
        assertEquals("the toggle's own row (position 0, not numbered) breaks the run", listOf(1, 2, 0, 1, 2), positions(out))
    }
}
