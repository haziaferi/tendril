package com.tendril.app.domain

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.domain.canvas.CANVAS_LEAF_H
import com.tendril.app.domain.canvas.CANVAS_NODE_H
import com.tendril.app.domain.canvas.cardWidth
import com.tendril.app.domain.canvas.CANVAS_ROOT_H
import com.tendril.app.domain.canvas.CANVAS_ROOT_W
import com.tendril.app.domain.canvas.CanvasStructure
import com.tendril.app.domain.canvas.CanvasTree
import com.tendril.app.domain.canvas.DOWN_LEVEL_GAP
import com.tendril.app.domain.canvas.FRAME_FOLLOW_PAD
import com.tendril.app.domain.canvas.NodeBox
import com.tendril.app.domain.canvas.TREE_LEVEL_GAP
import com.tendril.app.domain.canvas.TREE_SIBLING_GAP
import com.tendril.app.domain.canvas.TreeLevel
import com.tendril.app.domain.canvas.TreeSide
import com.tendril.app.domain.canvas.newChildPosition
import com.tendril.app.domain.canvas.outerSide
import com.tendril.app.domain.canvas.relationRoute
import com.tendril.app.domain.canvas.tidy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The mind-map pass (2026-09-20): the tree on the canvas — `domain/canvas/Tree.kt`. */
class CanvasTreeTest {
    private var nextId = 1L
    private var tick = 0L
    private fun node(x: Float = 0f, y: Float = 0f, parent: Long? = null, text: String = "n", type: CanvasNodeType = CanvasNodeType.TEXT, folded: Boolean = false, structure: String? = null, w: Float = 0f, h: Float = 0f): CanvasNode {
        val t = Instant.ofEpochMilli(1_000 + tick++)
        return CanvasNode(id = nextId++, canvasId = 1, type = type, x = x, y = y, width = w, height = h, text = text, createdAt = t, updatedAt = t, parentId = parent, folded = folded, structure = structure)
    }

    /** root → a, b, c (a → a1, a2); a frame follows a. */
    private fun sample(structure: CanvasStructure): Pair<CanvasTree, List<CanvasNode>> {
        val root = node(400f, 200f, text = "Garden plan")
        val a = node(700f, 60f, parent = root.id, text = "Beds")
        val b = node(700f, 200f, parent = root.id, text = "Compost bins")
        val c = node(700f, 340f, parent = root.id, text = "Water")
        val a1 = node(950f, 40f, parent = a.id, text = "Tomatoes")
        val a2 = node(950f, 90f, parent = a.id, text = "Beans")
        val frame = node(0f, 0f, parent = a.id, type = CanvasNodeType.FRAME, text = "This season", w = 388f, h = 212f)
        val free = node(50f, 400f, text = "A loose card")
        val all = listOf(root, a, b, c, a1, a2, frame, free)
        return CanvasTree(all, structure) to all
    }

    @Test
    fun `depth, root and the grammar by level`() {
        val (tree, n) = sample(CanvasStructure.MAP)
        val root = n[0]; val a = n[1]; val a1 = n[4]; val frame = n[6]; val free = n[7]
        assertEquals(0, tree.depthOf(root.id)); assertEquals(1, tree.depthOf(a.id)); assertEquals(2, tree.depthOf(a1.id))
        assertEquals(root.id, tree.rootOf(a1.id)!!.id)
        assertEquals(TreeLevel.ROOT, tree.levelOf(root)); assertEquals(TreeLevel.BRANCH, tree.levelOf(a)); assertEquals(TreeLevel.LEAF, tree.levelOf(a1))
        assertEquals(TreeLevel.BRANCH, tree.levelOf(free))   // a parentless card with no children is a strip
        assertEquals(NodeBox(400f, 200f, CANVAS_ROOT_W, CANVAS_ROOT_H), tree.box(root))
        assertEquals(cardWidth("Beds"), tree.box(a).w); assertEquals(CANVAS_NODE_H, tree.box(a).h)   // the strip is as wide as its text asks
        assertEquals(CANVAS_LEAF_H, tree.box(a1).h)
        // Under DOWN every level is the strip.
        val down = CanvasTree(n, CanvasStructure.DOWN)
        assertEquals(TreeLevel.BRANCH, down.levelOf(a1)); assertEquals(TreeLevel.ROOT, down.levelOf(root))
        assertEquals(TreeLevel.BRANCH, tree.levelOf(frame))
    }

    @Test
    fun `a MAP root splits its children by creation order, the first half right`() {
        val (tree, n) = sample(CanvasStructure.MAP)
        val (_, a, b, c, a1) = n
        assertEquals(TreeSide.RIGHT, tree.sideOf(a)); assertEquals(TreeSide.RIGHT, tree.sideOf(b)); assertEquals(TreeSide.LEFT, tree.sideOf(c))
        assertEquals(TreeSide.RIGHT, tree.sideOf(a1))          // a grandchild keeps its branch's side
        assertEquals(0, tree.mainBranchIndex(a1)); assertEquals(2, tree.mainBranchIndex(c)); assertEquals(-1, tree.mainBranchIndex(n[0]))
        // A node's own structure overrides the board's for its subtree.
        val (t2, m) = sample(CanvasStructure.MAP).let { (_, all) -> CanvasTree(all.map { if (it.text == "Beds") it.copy(structure = "down") else it }, CanvasStructure.MAP) to all }
        val beds = t2.nodes.first { it.text == "Beds" }; val tomatoes = t2.nodes.first { it.text == "Tomatoes" }
        assertEquals(CanvasStructure.DOWN, t2.structureOf(beds)); assertEquals(CanvasStructure.DOWN, t2.structureOf(tomatoes))
        assertEquals(TreeSide.DOWN, t2.sideOf(tomatoes)); assertEquals(TreeLevel.BRANCH, t2.levelOf(tomatoes))
        assertEquals(CanvasStructure.MAP, t2.structureOf(t2.nodes.first { it.text == "Water" }))
        assertTrue(m.isNotEmpty())
    }

    @Test
    fun `a fold hides the subtree and counts it, and a following frame follows what is visible`() {
        val (_, n) = sample(CanvasStructure.MAP)
        val a = n[1]
        val tree = CanvasTree(n.map { if (it.id == a.id) it.copy(folded = true) else it }, CanvasStructure.MAP)
        val a1 = n[4]; val a2 = n[5]; val frame = n[6]
        assertFalse(tree.isVisible(a1)); assertFalse(tree.isVisible(a2)); assertFalse(tree.isVisible(frame))
        assertTrue(tree.isVisible(tree.byId.getValue(a.id)))
        assertEquals(2, tree.hiddenCount(a.id))
        // Unfolded: the frame's box is the subtree's bounds plus the pad.
        val open = CanvasTree(n, CanvasStructure.MAP)
        val fb = open.box(frame)
        val boxes = listOf(open.box(a), open.box(a1), open.box(a2))
        assertEquals(boxes.minOf { it.x } - FRAME_FOLLOW_PAD, fb.x, 0.01f)
        assertEquals(boxes.maxOf { it.right } + FRAME_FOLLOW_PAD, fb.right, 0.01f)
        assertEquals(boxes.maxOf { it.bottom } + FRAME_FOLLOW_PAD, fb.bottom, 0.01f)
        // A frame with no parent keeps its own box.
        val loose = node(10f, 10f, type = CanvasNodeType.FRAME, w = 300f, h = 200f)
        assertEquals(NodeBox(10f, 10f, 300f, 200f), CanvasTree(listOf(loose), CanvasStructure.FREE).box(loose))
    }

    @Test
    fun `Tidy under MAP keeps the root, stacks each side on the root's centre and never overlaps siblings`() {
        val (tree, n) = sample(CanvasStructure.MAP)
        val root = n[0]
        val pos = tidy(tree, root.id)
        assertNull(pos[root.id])
        val rb = tree.box(root)
        val right = listOf(n[1], n[2]).map { pos.getValue(it.id) }
        val left = pos.getValue(n[3].id)
        assertTrue(right.all { it.first == rb.right + TREE_LEVEL_GAP })
        assertEquals(rb.x - TREE_LEVEL_GAP - tree.box(n[3]).w, left.first, 0.01f)
        // The lone left child sits on the root's centre line.
        assertEquals(rb.y + rb.h / 2f, left.second + CANVAS_NODE_H / 2f, 0.01f)
        // The right stack of *bands* is centred on the root: Beds' band is its two leaves (24 + 12 + 24 = 60),
        // so its strip sits 6 under the band's top; Compost bins' band is its own 48.
        val bedsBand = 2 * CANVAS_LEAF_H + TREE_SIBLING_GAP
        val topOfStack = right[0].second - (bedsBand - CANVAS_NODE_H) / 2f
        val bottomOfStack = right[1].second + CANVAS_NODE_H
        assertEquals(rb.y + rb.h / 2f, (topOfStack + bottomOfStack) / 2f, 0.01f)
        // Beds' band holds its two leaves stacked with the gap, and Compost bins sits under the band, not on it.
        val a1y = pos.getValue(n[4].id).second; val a2y = pos.getValue(n[5].id).second
        assertEquals(CANVAS_LEAF_H + TREE_SIBLING_GAP, a2y - a1y, 0.01f)
        assertTrue(pos.getValue(n[2].id).second >= a2y + CANVAS_LEAF_H + TREE_SIBLING_GAP - 0.01f)
        // Leaves sit right of Beds' strip.
        assertEquals(right[0].first + tree.box(n[1]).w + TREE_LEVEL_GAP, pos.getValue(n[4].id).first, 0.01f)
        assertNull(pos[n[7].id])   // a free card is not the tree's
    }

    @Test
    fun `Tidy under DOWN puts each parent over its children's centre with the level gap`() {
        val (tree, n) = sample(CanvasStructure.DOWN)
        val root = n[0]
        val pos = tidy(tree, root.id)
        val rb = tree.box(root)
        val kids = listOf(n[1], n[2], n[3]).map { pos.getValue(it.id) }
        assertTrue(kids.all { it.second == rb.bottom + DOWN_LEVEL_GAP })
        // The bands are centred under the root: Beds' band is its two strips (each its text's width, 24 between), the others their own.
        val bedsBand = tree.box(n[4]).w + 24f + tree.box(n[5]).w
        val bandLeft = kids[0].first - (bedsBand - tree.box(n[1]).w) / 2f
        val bandRight = kids[2].first + tree.box(n[3]).w
        assertEquals(rb.x + rb.w / 2f, (bandLeft + bandRight) / 2f, 0.01f)
        val a = pos.getValue(n[1].id); val a1 = pos.getValue(n[4].id); val a2 = pos.getValue(n[5].id)
        assertEquals(a.first + tree.box(n[1]).w / 2f, (a1.first + a2.first + tree.box(n[5]).w) / 2f, 0.01f)
        assertTrue(a2.first >= a1.first + tree.box(n[4]).w)
    }

    @Test
    fun `a new child lands beside or under its parent after the last sibling`() {
        val (tree, n) = sample(CanvasStructure.RIGHT)
        val root = n[0]; val a = n[1]
        val (x, y) = newChildPosition(tree, root)
        assertEquals(tree.box(root).right + TREE_LEVEL_GAP, x, 0.01f)
        assertEquals(tree.box(n[3]).bottom + TREE_SIBLING_GAP, y, 0.01f)
        val (lx, _) = newChildPosition(tree, a)
        assertEquals(tree.box(a).right + TREE_LEVEL_GAP, lx, 0.01f)
        val down = CanvasTree(n, CanvasStructure.DOWN)
        val (dx, dy) = newChildPosition(down, a)
        assertEquals(down.box(n[5]).right + 24f, dx, 0.01f); assertEquals(down.box(n[5]).y, dy, 0.01f)
        val leaf = n[4]
        val (ex, ey) = newChildPosition(down, leaf)
        assertEquals(down.box(leaf).x + down.box(leaf).w / 2f - cardWidth(null) / 2f, ex, 0.01f); assertEquals(down.box(leaf).bottom + DOWN_LEVEL_GAP, ey, 0.01f)
    }

    @Test
    fun `a relationship leaves and enters by the outer sides and bows outward`() {
        val parent = NodeBox(0f, 0f, 220f, 64f)
        val from = NodeBox(400f, 100f, 200f, 48f)   // right of and a little below its parent → its outer side is RIGHT (dx wins)
        val to = NodeBox(300f, 0f, 200f, 48f)
        assertEquals(TreeSide.RIGHT, outerSide(from, parent, to))
        assertEquals(TreeSide.RIGHT, outerSide(to, parent, from))
        val r = relationRoute(from, parent, to, parent)
        assertEquals(from.right, r.x1, 0f); assertEquals(to.right, r.x2, 0f)
        assertTrue(r.c1x > r.x1 && r.c2x > r.x2)
        assertTrue(r.midX > (r.x1 + r.x2) / 2f)   // the curve bows outward of its chord
        // Free nodes: the side facing each other.
        val l = NodeBox(0f, 0f, 200f, 48f); val rr = NodeBox(400f, 0f, 200f, 48f)
        assertEquals(TreeSide.RIGHT, outerSide(l, null, rr)); assertEquals(TreeSide.LEFT, outerSide(rr, null, l))
    }
}
