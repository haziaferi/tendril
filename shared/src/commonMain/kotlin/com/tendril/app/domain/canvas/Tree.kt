package com.tendril.app.domain.canvas

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType

/**
 * The mind-map pass (2026-09-20, `docs/critiques/mind-map-grounds.md`) — **the tree on the
 * canvas**. A node with a `parentId` is a branch of that node; the board and any node carry a
 * [CanvasStructure] that *Tidy* applies to a subtree (Xmind's structure-per-topic, measured in
 * its Style panel); the same nodes whichever structure is chosen. This file is the pure half:
 * who is whose child, how deep, on which side, what box a node gets at its level, which nodes a
 * fold hides, where Tidy puts a subtree, and how a relationship is routed round it. Content dp.
 *
 * The grammar per level (Xmind's and Mindomo's three levels, measured): the root at `pageTitle`
 * in a bordered box; a branch the 200 × 48 strip; deeper nodes as **text on the line** — the
 * branch is the word's underline. Under [CanvasStructure.DOWN] (an org chart) every level is
 * the strip, as Mindomo draws it. A page card is always a strip (its tint and glyph are its
 * own). A parentless node with no children is a free card, the strip too.
 */
enum class CanvasStructure(val key: String, val label: String) {
    /** No Tidy; nodes where you put them; a parent link still draws its branch. Mindomo's *concept map*. */
    FREE("free", "Free"),
    /** Both sides of the root: the first half of its children to the right, the rest to the left (Xmind's clockwise). */
    MAP("map", "Map"),
    /** Every branch to the right — markmap's, Ideascape's shape. */
    RIGHT("right", "Right"),
    /** An org chart: children under their parent, elbowed branches, the strip at every level. */
    DOWN("down", "Down");

    /** Branches are curves in the map structures and elbows under DOWN. */
    val curved: Boolean get() = this != DOWN

    companion object {
        fun fromKey(key: String?): CanvasStructure = entries.firstOrNull { it.key == key } ?: FREE
    }
}

/** What a node draws as, by its depth and its structure. */
enum class TreeLevel { ROOT, BRANCH, LEAF }

/** Where a node's subtree grows from it. */
enum class TreeSide { RIGHT, LEFT, DOWN }

const val CANVAS_ROOT_W = 220f
const val CANVAS_ROOT_H = 64f
/** A leaf's line: `body` at 14 sp sits in 24 dp; its width from its characters. */
const val CANVAS_LEAF_H = 24f
const val CANVAS_LEAF_CHAR = 7.5f
const val CANVAS_LEAF_PAD = 12f
const val CANVAS_LEAF_MAX_CHARS = 40
/** Between a parent's side and its children (horizontal structures) — Xmind's ≈ 48 px at 125 %. */
const val TREE_LEVEL_GAP = 48f
/** Between sibling bands. */
const val TREE_SIBLING_GAP = 12f
/** DOWN: between a parent's bottom and its children's top, and between sibling columns. */
const val DOWN_LEVEL_GAP = 64f
const val DOWN_SIBLING_GAP = 24f
/** A following frame's air around its subtree (Xmind's boundary ≈ 10 px at 125 %). */
const val FRAME_FOLLOW_PAD = 12f
/** The count badge at a folded branch's end. */
const val FOLD_BADGE = 20f

/** A leaf's width from its text, as `estimateNodeSize` guesses a map node's. */
fun leafWidth(text: String?): Float {
    val n = text.orEmpty().ifBlank { "…" }.length.coerceIn(1, CANVAS_LEAF_MAX_CHARS)
    return n * CANVAS_LEAF_CHAR + CANVAS_LEAF_PAD
}

class CanvasTree(nodes: List<CanvasNode>, val boardStructure: CanvasStructure) {
    val nodes: List<CanvasNode> = nodes
    val byId: Map<Long, CanvasNode> = nodes.associateBy { it.id }
    private val childrenOf: Map<Long, List<CanvasNode>> = nodes
        .filter { it.parentId != null && it.parentId in byId && it.type != CanvasNodeType.FRAME }
        .groupBy { it.parentId!! }
    private val depthMemo = HashMap<Long, Int>()
    private val structureMemo = HashMap<Long, CanvasStructure>()
    private val sideMemo = HashMap<Long, TreeSide>()
    private val boxMemo = HashMap<Long, NodeBox>()

    /** Siblings in the order they were made — the stable order the MAP split and the colours read; dragging never changes it. */
    fun creationOrder(id: Long): List<CanvasNode> = childrenOf[id].orEmpty().sortedWith(compareBy({ it.createdAt }, { it.id }))

    /** A node's tree children (frames are never children in the drawing sense) in drawing order:
     * top to bottom beside a parent (a MAP root's right side first, then its left), left to right under one. */
    fun children(id: Long): List<CanvasNode> {
        val kids = childrenOf[id].orEmpty()
        if (kids.isEmpty()) return kids
        val parent = byId[id] ?: return kids
        val structure = structureOf(parent)
        return when {
            structure == CanvasStructure.DOWN -> kids.sortedWith(compareBy({ it.x }, { it.y }, { it.createdAt }, { it.id }))
            structure == CanvasStructure.MAP && parent.parentId == null ->
                kids.sortedWith(compareBy({ if (sideOf(it) == TreeSide.LEFT) 1 else 0 }, { it.y }, { it.x }, { it.createdAt }, { it.id }))
            else -> kids.sortedWith(compareBy({ it.y }, { it.x }, { it.createdAt }, { it.id }))
        }
    }

    fun parentOf(node: CanvasNode): CanvasNode? = node.parentId?.let { byId[it] }

    /** 0 for a node with no parent in this tree. A cycle (never written, but a merge could carry one) stops at the first repeat. */
    fun depthOf(id: Long): Int = depthMemo.getOrPut(id) {
        var d = 0
        var cur = byId[id]
        val seen = HashSet<Long>()
        while (cur != null && seen.add(cur.id)) {
            val p = cur.parentId?.let { byId[it] } ?: break
            d++; cur = p
        }
        d
    }

    fun rootOf(id: Long): CanvasNode? {
        var cur = byId[id] ?: return null
        val seen = HashSet<Long>()
        while (seen.add(cur.id)) { cur = cur.parentId?.let { byId[it] } ?: return cur }
        return cur
    }

    /** The structure that lays this node's subtree out: its own, else the nearest ancestor's, else the board's. */
    fun structureOf(node: CanvasNode): CanvasStructure = structureMemo.getOrPut(node.id) {
        if (node.structure != null) CanvasStructure.fromKey(node.structure)
        else parentOf(node)?.let { structureOf(it) } ?: boardStructure
    }

    /** The side a node's subtree grows on: DOWN under a DOWN structure; under MAP a root's first
     * half of children (in creation order) go right and the rest left, and every deeper node keeps
     * its branch's side; RIGHT and FREE grow right. A node with its own structure starts afresh. */
    fun sideOf(node: CanvasNode): TreeSide = sideMemo.getOrPut(node.id) { computeSide(node) }

    private fun computeSide(node: CanvasNode): TreeSide {
        val structure = structureOf(node)
        if (structure == CanvasStructure.DOWN) return TreeSide.DOWN
        val parent = parentOf(node) ?: return TreeSide.RIGHT
        val parentStructure = structureOf(parent)
        if (node.structure != null && structure != parentStructure) return TreeSide.RIGHT
        if (parentStructure == CanvasStructure.MAP && parent.parentId == null) {
            val order = creationOrder(parent.id)
            val half = (order.size + 1) / 2
            return if (order.indexOfFirst { it.id == node.id } >= half) TreeSide.LEFT else TreeSide.RIGHT
        }
        val parentSide = sideOf(parent)
        return if (parentSide == TreeSide.DOWN) TreeSide.RIGHT else parentSide
    }

    /** The grammar by depth. */
    fun levelOf(node: CanvasNode): TreeLevel {
        if (node.type == CanvasNodeType.FRAME) return TreeLevel.BRANCH
        val depth = depthOf(node.id)
        if (depth == 0) return if (childrenOf[node.id].isNullOrEmpty()) TreeLevel.BRANCH else TreeLevel.ROOT
        if (node.type == CanvasNodeType.PAGE_EMBED) return TreeLevel.BRANCH
        if (structureOf(node) == CanvasStructure.DOWN) return TreeLevel.BRANCH
        return if (depth == 1) TreeLevel.BRANCH else TreeLevel.LEAF
    }

    /** The main branch a node hangs from — the index of its depth-1 ancestor among the root's children; −1 for a root or a free node. Colours come from it. */
    fun mainBranchIndex(node: CanvasNode): Int {
        var cur = node
        val seen = HashSet<Long>()
        while (seen.add(cur.id)) {
            val p = parentOf(cur) ?: return -1
            if (p.parentId == null) return creationOrder(p.id).indexOfFirst { it.id == cur.id }
            cur = p
        }
        return -1
    }

    /** Every descendant, depth first, in child order; frames following a node in the subtree come with it. */
    fun descendants(id: Long): List<CanvasNode> {
        val out = mutableListOf<CanvasNode>()
        val seen = HashSet<Long>()
        fun walk(cur: Long) {
            for (kid in children(cur)) if (seen.add(kid.id)) { out += kid; walk(kid.id) }
        }
        walk(id)
        val inside = seen + id
        out += nodes.filter { it.type == CanvasNodeType.FRAME && it.parentId in inside }
        return out
    }

    /** The nodes a fold hides: every descendant of a folded node (and a frame following one). */
    val hidden: Set<Long> by lazy {
        val out = HashSet<Long>()
        for (n in nodes) if (n.folded && n.id !in out) descendants(n.id).forEach { out += it.id }
        out
    }

    fun isVisible(node: CanvasNode): Boolean = node.id !in hidden

    /** What a fold hides under this node, for the badge. */
    fun hiddenCount(id: Long): Int = descendants(id).count { it.type != CanvasNodeType.FRAME }

    /** A frame's anchor: the node whose subtree it follows, if it has one. */
    fun followedBy(frame: CanvasNode): CanvasNode? = if (frame.type == CanvasNodeType.FRAME) parentOf(frame) else null

    /** The box a node draws in, by its level; a following frame's from its subtree's visible boxes plus [FRAME_FOLLOW_PAD]. */
    fun box(node: CanvasNode): NodeBox = boxMemo.getOrPut(node.id) {
        if (node.type == CanvasNodeType.FRAME) {
            val anchor = followedBy(node) ?: return@getOrPut nodeBox(node)
            val boxes = (listOf(anchor) + descendants(anchor.id).filter { it.type != CanvasNodeType.FRAME && isVisible(it) }).map { box(it) }
            val x = boxes.minOf { it.x } - FRAME_FOLLOW_PAD
            val y = boxes.minOf { it.y } - FRAME_FOLLOW_PAD
            val r = boxes.maxOf { it.right } + FRAME_FOLLOW_PAD
            val b = boxes.maxOf { it.bottom } + FRAME_FOLLOW_PAD
            return@getOrPut NodeBox(x, y, r - x, b - y)
        }
        when (levelOf(node)) {
            TreeLevel.ROOT -> NodeBox(node.x, node.y, CANVAS_ROOT_W, CANVAS_ROOT_H)
            TreeLevel.BRANCH -> nodeBox(node)
            TreeLevel.LEAF -> NodeBox(node.x, node.y, leafWidth(node.text), CANVAS_LEAF_H)
        }
    }

    fun boxOf(id: Long): NodeBox? = byId[id]?.let { box(it) }

    /** Which node's box holds a content point — the topmost drawn wins (a card over a frame). */
    fun hit(x: Float, y: Float, except: Long? = null): CanvasNode? {
        val cards = nodes.filter { it.type != CanvasNodeType.FRAME && it.id != except && isVisible(it) }
        return cards.lastOrNull { val b = box(it); x >= b.x && x <= b.right && y >= b.y && y <= b.bottom }
    }
}

/** Where a branch leaves its parent and enters its child, by the child's side. */
data class BranchAnchors(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

fun branchAnchors(parent: NodeBox, child: NodeBox, side: TreeSide): BranchAnchors = when (side) {
    TreeSide.RIGHT -> BranchAnchors(parent.right, parent.y + parent.h / 2f, child.x, child.y + child.h / 2f)
    TreeSide.LEFT -> BranchAnchors(parent.x, parent.y + parent.h / 2f, child.right, child.y + child.h / 2f)
    TreeSide.DOWN -> BranchAnchors(parent.x + parent.w / 2f, parent.bottom, child.x + child.w / 2f, child.y)
}

/** A leaf's branch runs on as its underline: the line's ends at the word's baseline. */
fun leafUnderline(leaf: NodeBox): BranchAnchors = BranchAnchors(leaf.x, leaf.bottom, leaf.right, leaf.bottom)

/** The fold badge's centre at the branch's end, on the node's outer side. */
fun foldBadgeAt(box: NodeBox, side: TreeSide): Pair<Float, Float> = when (side) {
    TreeSide.RIGHT -> (box.right + FOLD_BADGE / 2f + 4f) to (box.y + box.h / 2f)
    TreeSide.LEFT -> (box.x - FOLD_BADGE / 2f - 4f) to (box.y + box.h / 2f)
    TreeSide.DOWN -> (box.x + box.w / 2f) to (box.bottom + FOLD_BADGE / 2f + 4f)
}

/**
 * **Tidy** — new positions for every visible node under [rootId] (the root itself stays where it
 * is; a dragged root is the tree's anchor). Bands, as `layoutMindMap` does them: a node is centred
 * on the band its subtree occupies, a band is the sum of its children's bands plus the gaps or the
 * node's own extent, whichever is larger. Horizontal structures stack bands vertically beside the
 * parent; DOWN stacks them horizontally under it. Under MAP the root's children split by side,
 * each side its own stack centred on the root. A subtree with its own structure lays out by that
 * structure from its node. Folded subtrees are left alone (hidden nodes keep their positions).
 */
fun tidy(tree: CanvasTree, rootId: Long): Map<Long, Pair<Float, Float>> {
    val root = tree.byId[rootId] ?: return emptyMap()
    val out = HashMap<Long, Pair<Float, Float>>()
    val moved = HashMap<Long, NodeBox>()
    fun boxNow(n: CanvasNode): NodeBox = moved[n.id] ?: tree.box(n)
    fun visibleKids(n: CanvasNode): List<CanvasNode> = if (n.folded) emptyList() else tree.children(n.id)

    val bandH = HashMap<Long, Float>()
    fun bandHeight(n: CanvasNode): Float = bandH.getOrPut(n.id) {
        val own = tree.box(n).h
        val kids = visibleKids(n)
        if (kids.isEmpty() || tree.structureOf(n) == CanvasStructure.DOWN) own
        else maxOf(own, kids.sumOf { bandHeight(it).toDouble() }.toFloat() + TREE_SIBLING_GAP * (kids.size - 1))
    }
    val bandW = HashMap<Long, Float>()
    fun bandWidth(n: CanvasNode): Float = bandW.getOrPut(n.id) {
        val own = tree.box(n).w
        val kids = visibleKids(n)
        if (kids.isEmpty() || tree.structureOf(n) != CanvasStructure.DOWN) own
        else maxOf(own, kids.sumOf { bandWidth(it).toDouble() }.toFloat() + DOWN_SIBLING_GAP * (kids.size - 1))
    }

    fun place(n: CanvasNode, x: Float, y: Float) {
        val b = tree.box(n)
        out[n.id] = x to y
        moved[n.id] = NodeBox(x, y, b.w, b.h)
    }

    fun layoutChildren(n: CanvasNode) {
        val kids = visibleKids(n)
        if (kids.isEmpty()) return
        val pb = boxNow(n)
        val structure = tree.structureOf(n)
        when {
            structure == CanvasStructure.DOWN -> {
                val total = kids.sumOf { bandWidth(it).toDouble() }.toFloat() + DOWN_SIBLING_GAP * (kids.size - 1)
                var left = pb.x + pb.w / 2f - total / 2f
                val top = pb.bottom + DOWN_LEVEL_GAP
                for (k in kids) {
                    val bw = bandWidth(k)
                    val kb = tree.box(k)
                    place(k, left + (bw - kb.w) / 2f, top)
                    layoutChildren(k)
                    left += bw + DOWN_SIBLING_GAP
                }
            }
            else -> {
                val groups: List<Pair<TreeSide, List<CanvasNode>>> =
                    if (structure == CanvasStructure.MAP && n.parentId == null)
                        listOf(TreeSide.RIGHT to kids.filter { tree.sideOf(it) == TreeSide.RIGHT }, TreeSide.LEFT to kids.filter { tree.sideOf(it) == TreeSide.LEFT }) else listOf((if (tree.sideOf(n) == TreeSide.LEFT) TreeSide.LEFT else TreeSide.RIGHT) to kids)
                for ((side, group) in groups) {
                    if (group.isEmpty()) continue
                    val total = group.sumOf { bandHeight(it).toDouble() }.toFloat() + TREE_SIBLING_GAP * (group.size - 1)
                    var top = pb.y + pb.h / 2f - total / 2f
                    for (k in group) {
                        val bh = bandHeight(k)
                        val kb = tree.box(k)
                        val x = if (side == TreeSide.RIGHT) pb.right + TREE_LEVEL_GAP else pb.x - TREE_LEVEL_GAP - kb.w
                        place(k, x, top + (bh - kb.h) / 2f)
                        layoutChildren(k)
                        top += bh + TREE_SIBLING_GAP
                    }
                }
            }
        }
    }
    layoutChildren(root)
    return out
}

/** Where a new child goes without moving its siblings: after the last one, beside or under the parent. */
fun newChildPosition(tree: CanvasTree, parent: CanvasNode, childW: Float = CANVAS_NODE_W, childH: Float = CANVAS_NODE_H): Pair<Float, Float> {
    val pb = tree.box(parent)
    val kids = tree.children(parent.id)
    val structure = tree.structureOf(parent)
    if (structure == CanvasStructure.DOWN) {
        val last = kids.lastOrNull()?.let { tree.box(it) }
        return if (last == null) (pb.x + pb.w / 2f - childW / 2f) to (pb.bottom + DOWN_LEVEL_GAP)
        else (last.right + DOWN_SIBLING_GAP) to last.y
    }
    // A MAP root's new child takes the side the split will give it once it exists (index n of n + 1).
    val side = if (structure == CanvasStructure.MAP && parent.parentId == null) { if (kids.size >= (kids.size + 2) / 2) TreeSide.LEFT else TreeSide.RIGHT }
    else if (tree.sideOf(parent) == TreeSide.LEFT) TreeSide.LEFT else TreeSide.RIGHT
    val x = if (side == TreeSide.RIGHT) pb.right + TREE_LEVEL_GAP else pb.x - TREE_LEVEL_GAP - childW
    val sameSide = kids.filter { tree.sideOf(it) == side }
    val last = sameSide.lastOrNull()?.let { tree.box(it) }
    val y = if (last == null) pb.y + pb.h / 2f - childH / 2f else last.bottom + TREE_SIBLING_GAP
    return x to y
}

/** A relationship's route — from the source's outer side to the target's outer side, bowing outward, its label at the curve's middle. */
data class RelationRoute(val x1: Float, val y1: Float, val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x2: Float, val y2: Float) {
    /** The cubic's point at t = 0.5, where the label sits. */
    val midX: Float get() = 0.125f * x1 + 0.375f * c1x + 0.375f * c2x + 0.125f * x2
    val midY: Float get() = 0.125f * y1 + 0.375f * c1y + 0.375f * c2y + 0.125f * y2
}

/** The side of [box] that faces away from [away] (a parent's box), or toward [other] when there is no parent. */
fun outerSide(box: NodeBox, away: NodeBox?, other: NodeBox): TreeSide {
    val ref = away ?: other
    val dx = (box.x + box.w / 2f) - (ref.x + ref.w / 2f)
    val dy = (box.y + box.h / 2f) - (ref.y + ref.h / 2f)
    return if (away != null) {
        if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) (if (dx >= 0) TreeSide.RIGHT else TreeSide.LEFT) else TreeSide.DOWN
    } else {
        // Toward the other box: the side that faces it.
        if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) (if (dx >= 0) TreeSide.LEFT else TreeSide.RIGHT) else TreeSide.DOWN
    }
}

fun relationRoute(from: NodeBox, fromParent: NodeBox?, to: NodeBox, toParent: NodeBox?): RelationRoute {
    fun anchor(b: NodeBox, side: TreeSide): Pair<Float, Float> = when (side) {
        TreeSide.RIGHT -> b.right to (b.y + b.h / 2f)
        TreeSide.LEFT -> b.x to (b.y + b.h / 2f)
        TreeSide.DOWN -> (b.x + b.w / 2f) to b.bottom
    }
    val fs = outerSide(from, fromParent, to)
    val ts = outerSide(to, toParent, from)
    val (x1, y1) = anchor(from, fs)
    val (x2, y2) = anchor(to, ts)
    val dist = kotlin.math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    val bow = maxOf(40f, dist / 3f)
    fun normal(side: TreeSide): Pair<Float, Float> = when (side) { TreeSide.RIGHT -> 1f to 0f; TreeSide.LEFT -> -1f to 0f; TreeSide.DOWN -> 0f to 1f }
    val (nx1, ny1) = normal(fs)
    val (nx2, ny2) = normal(ts)
    return RelationRoute(x1, y1, x1 + nx1 * bow, y1 + ny1 * bow, x2 + nx2 * bow, y2 + ny2 * bow, x2, y2)
}
