package com.tendril.app.domain

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.domain.canvas.CANVAS_NODE_H
import com.tendril.app.domain.canvas.CanvasStructure
import com.tendril.app.domain.canvas.CanvasTree
import com.tendril.app.domain.canvas.FRAME_COLLAPSED_EXTRA
import com.tendril.app.domain.canvas.cardWidth
import com.tendril.app.domain.canvas.tidy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** S12 (2026-09-21): a frame collapses to a strip and hides what it holds — `CanvasTree.frameContents`, `hidden`, `box`. */
class CollapsibleFramesTest {
    private var nextId = 1L
    private var tick = 0L
    private fun node(x: Float = 0f, y: Float = 0f, parent: Long? = null, text: String = "n", type: CanvasNodeType = CanvasNodeType.TEXT, folded: Boolean = false, w: Float = 0f, h: Float = 0f): CanvasNode {
        val t = Instant.ofEpochMilli(1_000 + tick++)
        return CanvasNode(id = nextId++, canvasId = 1, type = type, x = x, y = y, width = w, height = h, text = text, createdAt = t, updatedAt = t, parentId = parent, folded = folded)
    }

    @Test
    fun `a hand-sized frame holds the cards inside its rectangle and a nested frame, never a card outside`() {
        val beds = node(60f, 120f, text = "Beds", type = CanvasNodeType.FRAME, w = 300f, h = 300f)
        val a = node(90f, 160f, text = "Tomatoes"); val b = node(90f, 240f, text = "Beans")
        val nested = node(80f, 300f, text = "Late", type = CanvasNodeType.FRAME, w = 200f, h = 100f)
        val outside = node(400f, 16f, text = "A loose card")
        val tree = CanvasTree(listOf(beds, a, b, nested, outside), CanvasStructure.FREE)
        assertEquals(setOf(a.id, b.id, nested.id), tree.frameContents(beds).map { it.id }.toSet())
        assertEquals("the count is cards, not frames", 2, tree.hiddenCount(beds.id))
        assertTrue(tree.isVisible(a))
    }

    @Test
    fun `a collapsed hand-sized frame hides what it holds, keeps their positions and draws as a strip at its top-left`() {
        val beds = node(60f, 120f, text = "Beds", type = CanvasNodeType.FRAME, w = 300f, h = 300f, folded = true)
        val a = node(90f, 160f, text = "Tomatoes"); val b = node(90f, 240f, text = "Beans")
        val outside = node(400f, 16f, text = "A loose card")
        val tree = CanvasTree(listOf(beds, a, b, outside), CanvasStructure.FREE)
        assertFalse(tree.isVisible(a)); assertFalse(tree.isVisible(b)); assertTrue(tree.isVisible(outside)); assertTrue(tree.isVisible(beds))
        val box = tree.box(beds)
        assertEquals(60f, box.x, 0.01f); assertEquals(120f, box.y, 0.01f)
        assertEquals(CANVAS_NODE_H, box.h, 0.01f)
        assertEquals(cardWidth("Beds") + FRAME_COLLAPSED_EXTRA, box.w, 0.01f)
        assertEquals("positions are the nodes' own — a fold keeps them", 160f, a.y, 0.01f)
    }

    @Test
    fun `a collapsed following frame hides its anchor and the subtree, stands where the anchor stood, and tidy leaves the hidden alone`() {
        val root = node(520f, 120f, text = "Compost bins")
        val garden = node(420f, 250f, parent = root.id, text = "Garden waste")
        val empty = node(420f, 380f, parent = garden.id, text = "Empty card")
        val leaves = node(700f, 250f, parent = root.id, text = "LeavesKitchen scraps")
        val frame = node(0f, 0f, parent = garden.id, text = "Garden", type = CanvasNodeType.FRAME, folded = true)
        val tree = CanvasTree(listOf(root, garden, empty, leaves, frame), CanvasStructure.DOWN)
        assertEquals(setOf(garden.id, empty.id), tree.frameContents(frame).map { it.id }.toSet())
        assertFalse(tree.isVisible(garden)); assertFalse(tree.isVisible(empty)); assertTrue(tree.isVisible(frame)); assertTrue(tree.isVisible(leaves))
        val box = tree.box(frame)
        assertEquals(420f, box.x, 0.01f); assertEquals(250f, box.y, 0.01f); assertEquals(CANVAS_NODE_H, box.h, 0.01f)
        val pos = tidy(tree, root.id)
        assertFalse("a hidden node is not moved", pos.containsKey(garden.id))
        assertTrue(pos.containsKey(leaves.id))
    }

    @Test
    fun `a frame following a hidden anchor is hidden with it`() {
        val root = node(520f, 120f, text = "Compost bins", folded = true)
        val garden = node(420f, 250f, parent = root.id, text = "Garden waste")
        val frame = node(0f, 0f, parent = garden.id, text = "Garden", type = CanvasNodeType.FRAME)
        val tree = CanvasTree(listOf(root, garden, frame), CanvasStructure.DOWN)
        assertFalse(tree.isVisible(garden)); assertFalse(tree.isVisible(frame))
    }
}
