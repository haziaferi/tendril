package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** §0.6.2 — the map is a drawing of the outline: positions follow the tree, nothing is stored. */
class MindMapLayoutTest {

    private fun block(id: Long, order: Int, parent: Long? = null, text: String = "n$id") = Block(
        id = id, pageId = 1, type = BlockType.BULLETED_LIST_ITEM, order = order, content = text,
        parentBlockId = parent, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    /** Every node 100 wide and 20 tall, so the arithmetic is readable. */
    private val fixed: (Block) -> Pair<Float, Float> = { 100f to 20f }

    private fun subtree(vararg blocks: Block) = outlineOf(blocks.toList(), expandAll = true)

    @Test
    fun `a lone root sits at the origin`() {
        val l = layoutMindMap(subtree(block(1, 0)), fixed)
        val n = l.nodes.single()
        assertEquals(0f, n.x); assertEquals(0f, n.y)
        assertEquals(100f, l.width); assertEquals(20f, l.height)
    }

    @Test
    fun `children go in the next column, stacked with the gap, and the parent is centred on them`() {
        val l = layoutMindMap(subtree(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 1)), fixed, hGap = 40f, vGap = 10f)
        val root = l.nodeFor(1)!!; val a = l.nodeFor(2)!!; val b = l.nodeFor(3)!!
        assertEquals(140f, a.x); assertEquals(140f, b.x)
        assertEquals(0f, a.y); assertEquals(30f, b.y)              // 20 tall, gap 10
        assertEquals(15f, root.y)                                  // band 50 → root centred at 25
        assertEquals(root.centerY - 15f, a.centerY); assertEquals(root.centerY + 15f, b.centerY)
        assertEquals(1L, a.parentId); assertEquals(1L, b.parentId)
    }

    @Test
    fun `a grandchild opens a third column and widens its parent's band`() {
        val l = layoutMindMap(
            subtree(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 2), block(4, 3, parent = 2), block(5, 4, parent = 1)),
            fixed, hGap = 40f, vGap = 10f,
        )
        assertEquals(280f, l.nodeFor(3)!!.x)
        // 2's band is its two children (50); 5 follows it after the gap.
        assertEquals(60f, l.nodeFor(5)!!.y)
        assertEquals(80f, l.height)
        assertTrue("children stay in outline order", l.nodeFor(3)!!.y < l.nodeFor(4)!!.y)
    }

    @Test
    fun `columns are as wide as their widest node`() {
        val measure: (Block) -> Pair<Float, Float> = { if (it.id == 2L) 200f to 20f else 100f to 20f }
        val l = layoutMindMap(subtree(block(1, 0), block(2, 1, parent = 1), block(3, 2, parent = 1), block(4, 3, parent = 3)), measure, hGap = 40f)
        assertEquals("the third column starts after the 200-wide node", 100f + 40f + 200f + 40f, l.nodeFor(4)!!.x)
    }

    @Test
    fun `the estimate wraps long text and never returns an empty box`() {
        val (w1, h1) = estimateNodeSize("", charWidth = 10f, lineHeight = 10f, maxChars = 10, padding = 0f)
        assertTrue(w1 > 0f && h1 > 0f)
        val (w2, h2) = estimateNodeSize("x".repeat(25), charWidth = 10f, lineHeight = 10f, maxChars = 10, padding = 0f)
        assertEquals(100f, w2); assertEquals(30f, h2)
    }
}
