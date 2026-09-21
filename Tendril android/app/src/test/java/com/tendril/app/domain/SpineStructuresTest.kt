package com.tendril.app.domain

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import com.tendril.app.domain.canvas.BONE_INSET
import com.tendril.app.domain.canvas.CANVAS_LEAF_H
import com.tendril.app.domain.canvas.CanvasStructure
import com.tendril.app.domain.canvas.CanvasTree
import com.tendril.app.domain.canvas.RIB_MIN
import com.tendril.app.domain.canvas.SPINE_MIN_STEP
import com.tendril.app.domain.canvas.SPINE_STEM
import com.tendril.app.domain.canvas.TreeLevel
import com.tendril.app.domain.canvas.TreeSide
import com.tendril.app.domain.canvas.newChildPosition
import com.tendril.app.domain.canvas.ribLength
import com.tendril.app.domain.canvas.ribOf
import com.tendril.app.domain.canvas.spineY
import com.tendril.app.domain.canvas.stemAnchors
import com.tendril.app.domain.canvas.tidy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/** S9 (2026-09-21): the mind map's spine structures — Timeline and Fishbone — `domain/canvas/Tree.kt`. */
class SpineStructuresTest {
    private var nextId = 1L
    private var tick = 0L
    private fun node(x: Float = 0f, y: Float = 0f, parent: Long? = null, text: String = "n", structure: String? = null): CanvasNode {
        val t = Instant.ofEpochMilli(1_000 + tick++)
        return CanvasNode(id = nextId++, canvasId = 1, type = CanvasNodeType.TEXT, x = x, y = y, width = 0f, height = 0f, text = text, createdAt = t, updatedAt = t, parentId = parent, structure = structure)
    }

    /** root → Seed order (February, March), Beds (Tomatoes, Beans, Squash), Compost bins, Water (Rain barrel), Tools — alternating sides by their y. */
    private fun sample(structure: CanvasStructure): Pair<CanvasTree, List<CanvasNode>> {
        val root = node(40f, 208f, text = "Garden plan")                       // centre y = 240
        val t1 = node(300f, 100f, parent = root.id, text = "Seed order")       // above
        val t2 = node(400f, 300f, parent = root.id, text = "Beds")             // below
        val t3 = node(500f, 100f, parent = root.id, text = "Compost bins")     // above
        val t4 = node(600f, 300f, parent = root.id, text = "Water")            // below
        val t5 = node(700f, 100f, parent = root.id, text = "Tools")            // above
        val k1 = node(300f, 20f, parent = t1.id, text = "February"); val k2 = node(360f, 20f, parent = t1.id, text = "March")
        val k3 = node(380f, 400f, parent = t2.id, text = "Tomatoes"); val k4 = node(450f, 400f, parent = t2.id, text = "Beans"); val k5 = node(520f, 400f, parent = t2.id, text = "Squash")
        val k6 = node(600f, 400f, parent = t4.id, text = "Rain barrel")
        val all = listOf(root, t1, t2, t3, t4, t5, k1, k2, k3, k4, k5, k6)
        return CanvasTree(all, structure) to all
    }

    @Test
    fun `a spine root's child is on the side its centre lies on, and the levels follow the structure`() {
        val (tree, n) = sample(CanvasStructure.TIMELINE)
        val root = n[0]
        assertTrue(tree.isSpineRoot(root)); assertFalse(tree.isSpineRoot(n[1]))
        assertEquals(TreeSide.UP, tree.sideOf(n[1])); assertEquals(TreeSide.DOWN, tree.sideOf(n[2]))
        assertEquals("a timeline topic's children keep its side", TreeSide.UP, tree.sideOf(n[6]))
        assertEquals("the strip at every level under a timeline", TreeLevel.BRANCH, tree.levelOf(n[6]))
        val (fish, m) = sample(CanvasStructure.FISHBONE)
        assertTrue(fish.isRibTopic(m[1]))
        assertEquals("a bone grows right", TreeSide.RIGHT, fish.sideOf(m[6]))
        assertEquals("a bone is text on the line", TreeLevel.LEAF, fish.levelOf(m[6]))
        assertEquals(TreeLevel.BRANCH, fish.levelOf(m[1]))
    }

    @Test
    fun `tidy lays a timeline along the spine - in order, sides kept, one side never overlapping, stems of one length`() {
        val (tree, n) = sample(CanvasStructure.TIMELINE)
        val pos = tidy(tree, n[0].id)
        val sy = spineY(tree.box(n[0]))
        val topics = n.subList(1, 6)
        val anchors = topics.map { pos.getValue(it.id).first + tree.box(it).w / 2f }
        assertTrue("anchors advance along the spine", anchors.zipWithNext().all { (a, b) -> b - a >= SPINE_MIN_STEP - 0.01f })
        for (t in topics) {
            val (_, y) = pos.getValue(t.id)
            val b = tree.box(t)
            if (tree.sideOf(t) == TreeSide.UP) assertEquals(sy - SPINE_STEM, y + b.h, 0.01f) else assertEquals(sy + SPINE_STEM, y, 0.01f)
        }
        // the same side's slots never overlap: Seed order's band (two strips) ends before Compost bins begins
        val seedRight = maxOf(pos.getValue(n[6].id).first + tree.box(n[6]).w, pos.getValue(n[7].id).first + tree.box(n[7]).w)
        assertTrue(pos.getValue(n[3].id).first > seedRight)
        // the org chart grows away from the spine: Seed order's children above it, Beds' below
        assertTrue(pos.getValue(n[6].id).second + tree.box(n[6]).h < pos.getValue(n[1].id).second)
        assertTrue(pos.getValue(n[8].id).second > pos.getValue(n[2].id).second + tree.box(n[2]).h)
        val st = stemAnchors(tree.box(n[1]).copy(x = pos.getValue(n[1].id).first, y = pos.getValue(n[1].id).second), sy, TreeSide.UP)
        assertEquals(SPINE_STEM, st.y1 - st.y2, 0.01f)
    }

    @Test
    fun `tidy lays a fishbone - ribs at 60 degrees leaning forward, bones evenly along the rib, a foot after the same side is clear`() {
        val (tree, n) = sample(CanvasStructure.FISHBONE)
        val pos = tidy(tree, n[0].id)
        val sy = spineY(tree.box(n[0]))
        fun boxAt(node: CanvasNode) = tree.box(node).let { it.copy(x = pos.getValue(node.id).first, y = pos.getValue(node.id).second) }
        val beds = boxAt(n[2])
        val rib = ribOf(beds, sy, TreeSide.DOWN)
        assertTrue("the rib leans forward: the topic is right of its foot", rib.topX > rib.footX)
        assertEquals("the rib's length is what its three bones need", ribLength(3), hypot((rib.topX - rib.footX).toDouble(), (rib.topY - rib.spineY).toDouble()).toFloat(), 0.5f)
        assertEquals(60.0, Math.toDegrees(atan2((rib.topY - rib.spineY).toDouble(), (rib.topX - rib.footX).toDouble())), 0.5)
        // the bones: on the rib at even fractions, the word inset from it, its baseline on the bone
        val bones = listOf(n[8], n[9], n[10]).map { boxAt(it) }
        bones.forEachIndexed { j, b ->
            val f = (j + 1) / 4f
            assertEquals(sy + (rib.topY - sy) * f, b.bottom, 0.01f)
            assertEquals(rib.xAt(b.bottom) + BONE_INSET, b.x, 0.01f)
        }
        // the feet advance in order; Compost bins' rib (above) starts after Seed order's strip and bones are clear
        val feet = n.subList(1, 6).map { ribOf(boxAt(it), sy, tree.sideOf(it)).footX }
        assertTrue(feet.zipWithNext().all { (a, b) -> b - a >= SPINE_MIN_STEP - 0.01f })
        val seed = boxAt(n[1]); val compost = boxAt(n[3])
        assertTrue(compost.x > seed.right)
        val compostRib = ribOf(compost, sy, TreeSide.UP)
        assertTrue(compostRib.footX > listOf(n[6], n[7]).maxOf { boxAt(it).right } - (compostRib.topX - compostRib.footX))
    }

    @Test
    fun `a new child on a spine takes the other side, a new bone the rib's next place`() {
        val (tree, n) = sample(CanvasStructure.TIMELINE)
        val root = n[0]
        val sy = spineY(tree.box(root))
        val (x, y) = newChildPosition(tree, root)                    // after Tools (above) → below
        assertTrue(x > tree.box(n[5]).right)
        assertEquals(sy + SPINE_STEM, y, 0.01f)
        val (fish, m) = sample(CanvasStructure.FISHBONE)
        val (bx, by) = newChildPosition(fish, m[1])                  // a third bone on Seed order's rib (two already)
        val rib = ribOf(fish.box(m[1]), spineY(fish.box(m[0])), TreeSide.UP)
        assertEquals(rib.xAt(by + CANVAS_LEAF_H) + BONE_INSET, bx, 0.01f)
        assertTrue(abs(by + CANVAS_LEAF_H - (rib.spineY + (rib.topY - rib.spineY) * 3f / 4)) < 0.01f)
        val (sx, sy2) = newChildPosition(fish, m[0])                 // a sixth topic, after Tools (above) → below, at the least rib's height
        assertTrue(sx > fish.box(m[5]).right)
        assertEquals(spineY(fish.box(m[0])) + RIB_MIN * sin(Math.toRadians(60.0)).toFloat(), sy2, 0.01f)
    }
}
