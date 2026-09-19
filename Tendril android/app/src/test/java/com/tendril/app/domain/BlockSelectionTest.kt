package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.domain.blocks.BlockEdit
import com.tendril.app.domain.blocks.BlockUndoStack
import com.tendril.app.domain.blocks.Box
import com.tendril.app.domain.blocks.blockBefore
import com.tendril.app.domain.blocks.marqueeHits
import com.tendril.app.domain.blocks.planApply
import com.tendril.app.domain.blocks.runBetween
import com.tendril.app.domain.blocks.stepSelection
import com.tendril.app.domain.blocks.withSubtrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** §0.10 item 19 — the block selection's run rule, the subtree rule, the marquee, the steps; the
 * undo stack's inverse, plan and limit. */
class BlockSelectionTest {

    private val now = Instant.ofEpochMilli(1_000L)
    private fun block(id: Long, order: Int, parent: Long? = null, type: BlockType = BlockType.PARAGRAPH, text: String = "b$id") =
        Block(id = id, uid = "u$id", pageId = 1, type = type, order = order, parentBlockId = parent, content = text, createdAt = now, updatedAt = now)

    // 1 · 2 (child of 1) · 3 (child of 2) · 4 · 5
    private val blocks = listOf(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 2), block(4, 3), block(5, 4))
    private val outline = outlineOf(blocks)
    private val ids = outline.map { it.block.id }

    @Test
    fun `a run is contiguous in reading order, either way round, and tolerates a stray id`() {
        assertEquals(listOf(2L, 3L, 4L), runBetween(ids, 2, 4))
        assertEquals(listOf(2L, 3L, 4L), runBetween(ids, 4, 2))
        assertEquals(listOf(4L), runBetween(ids, 99, 4))
        assertTrue(runBetween(ids, 98, 99).isEmpty())
    }

    @Test
    fun `a selected parent takes the subtree the outline draws under it`() {
        assertEquals(setOf(1L, 2L, 3L), withSubtrees(setOf(1L), outline))
        assertEquals(setOf(2L, 3L, 5L), withSubtrees(setOf(2L, 5L), outline))
        assertEquals(setOf(4L), withSubtrees(setOf(4L), outline))
    }

    @Test
    fun `the marquee hits the rows it crosses, from either corner`() {
        val bounds = mapOf(1L to Box(0f, 0f, 300f, 30f), 2L to Box(0f, 32f, 300f, 62f), 3L to Box(0f, 64f, 300f, 94f))
        assertEquals(setOf(1L, 2L), marqueeHits(bounds, Box.of(10f, 10f, 40f, 40f)))
        assertEquals(setOf(1L, 2L), marqueeHits(bounds, Box.of(40f, 40f, 10f, 10f)))
        assertTrue(marqueeHits(bounds, Box.of(0f, 100f, 300f, 120f)).isEmpty())
    }

    @Test
    fun `an arrow steps a single, shift extends from the anchor, the ends hold`() {
        assertEquals(listOf(3L) to 3L, stepSelection(ids, 2, 2, +1, extend = false))
        assertEquals(listOf(2L, 3L) to 3L, stepSelection(ids, 2, 2, +1, extend = true))
        assertEquals(listOf(2L, 3L, 4L) to 4L, stepSelection(ids, 2, 3, +1, extend = true))
        assertEquals(listOf(5L) to 5L, stepSelection(ids, 5, 5, +1, extend = false))
        assertEquals(listOf(1L) to 1L, stepSelection(ids, 1, 1, -1, extend = true))
    }

    @Test
    fun `the block a run lands after is the one before it, never one of its own`() {
        assertEquals(1L, blockBefore(ids, setOf(2L, 3L)))
        assertNull(blockBefore(ids, setOf(1L, 2L)))
        assertEquals(3L, blockBefore(ids, setOf(4L)))
    }

    @Test
    fun `an edit's inverse swaps before and after, and the plan writes only what differs`() {
        val edit = BlockEdit("delete", before = listOf(blocks[1], blocks[2]), after = emptyList())
        val plan = planApply(current = listOf(blocks[0], blocks[3], blocks[4]), target = edit.before, affectedIds = edit.affectedIds)
        assertEquals(listOf(2L, 3L), plan.insert.map { it.id }) // the parent before its child
        assertTrue(plan.update.isEmpty() && plan.delete.isEmpty())

        val undo = planApply(current = blocks, target = edit.inverse().before, affectedIds = edit.affectedIds)
        assertEquals(listOf(3L, 2L), undo.delete) // the child before its parent
        val retype = BlockEdit("turn into", before = listOf(blocks[3]), after = listOf(blocks[3].copy(type = BlockType.HEADING_2)))
        assertEquals(listOf(BlockType.HEADING_2), planApply(blocks, retype.after, retype.affectedIds).update.map { it.type })
    }

    @Test
    fun `the stack undoes newest first, a new edit clears redo, and fifty is the limit`() {
        val stack = BlockUndoStack(limit = 3)
        val e = (1..4).map { BlockEdit("e$it", emptyList(), emptyList()) }
        e.forEach(stack::push)
        assertEquals(3, stack.size)
        assertEquals("e4", stack.undo()!!.label)
        assertTrue(stack.canRedo)
        assertEquals("e4", stack.redo()!!.label)
        stack.undo(); stack.push(BlockEdit("e5", emptyList(), emptyList()))
        assertFalse(stack.canRedo)
        assertEquals("e5", stack.undo()!!.label)
        assertEquals("e3", stack.undo()!!.label)
        assertEquals("e2", stack.undo()!!.label)
        assertNull(stack.undo())
    }
}
