package com.tendril.app.domain.canvas

import com.tendril.app.data.canvas.CanvasNode
import com.tendril.app.data.canvas.CanvasNodeType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

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
    DOWN("down", "Down"),
    /** S9 (2026-09-21) — a spine from the root's right; main topics on it, alternating above and below (Xmind's off-axis
     * timeline, measured), each subtree an org chart growing away from the spine; the strip at every level. */
    TIMELINE("timeline", "Timeline"),
    /** S9 — a fishbone: the head at the left, ribs leaning forward at 60° (Xmind's, measured), a main topic at each rib's end,
     * level 2 as horizontal bones off the rib — text on the line — and deeper levels stacked right of their bone. */
    FISHBONE("fishbone", "Fishbone");

    /** Branches are curves in the map structures and elbows under DOWN and the spine structures. */
    val curved: Boolean get() = this == FREE || this == MAP || this == RIGHT
    /** A spine structure lays its root's children along a line (S9). */
    val spine: Boolean get() = this == TIMELINE || this == FISHBONE

    companion object {
        fun fromKey(key: String?): CanvasStructure = entries.firstOrNull { it.key == key } ?: FREE
    }
}

/** What a node draws as, by its depth and its structure. */
enum class TreeLevel { ROOT, BRANCH, LEAF }

/** Where a node's subtree grows from it. UP is a spine's upper side (S9). */
enum class TreeSide { RIGHT, LEFT, DOWN, UP }

const val CANVAS_ROOT_W = 220f
const val CANVAS_ROOT_H = 64f
/** A leaf's line: `body` at 14 sp sits in 24 dp; its width from its text (`textWidth`, S14). */
const val CANVAS_LEAF_H = 24f
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
/** S12 — a collapsed frame's strip: its label's card width plus the room for *· n hidden* (≈ 70 dp at `description`), the chevron and the paddings. */
const val FRAME_COLLAPSED_EXTRA = 120f
/** S9 — a spine: the stem from the spine to a timeline topic (Xmind's 24 px), the dot on the spine, the least distance between
 * two topics' anchors along it (the order stays readable), the spine's tail past its last topic. */
const val SPINE_STEM = 24f
const val SPINE_DOT = 8f
const val SPINE_MIN_STEP = 48f
const val SPINE_TAIL = 24f
/** S9 — a fishbone: the rib's lean from the spine (Xmind's ≈ 59°, measured), its least length, the bones' spacing along it,
 * a bone's inset from the rib to its word, and the least rib length past the bones. */
const val RIB_ANGLE_DEG = 60f
const val RIB_MIN = 96f
const val BONE_GAP = 28f
const val BONE_INSET = 18f
const val RIB_END_PAD = 40f

/** A leaf's width from its text — the font's own advances (S14; `TextWidth.kt`) plus the padding, the text held to [CANVAS_LEAF_MAX_CHARS]. */
fun leafWidth(text: String?): Float = textWidth(text.orEmpty().ifBlank { "…" }.take(CANVAS_LEAF_MAX_CHARS)) + CANVAS_LEAF_PAD

class CanvasTree(nodes: List<CanvasNode>, val boardStructure: CanvasStructure, /** A page card's title by page id — its width (`cardWidth`). */ private val titleOf: (Long) -> String? = { null }) {
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
            // S9 — along a spine by x; a timeline topic's org chart by x; a fishbone topic's bones by their distance from the spine.
            structure == CanvasStructure.TIMELINE || isSpineRoot(parent) -> kids.sortedWith(compareBy({ it.x }, { it.y }, { it.createdAt }, { it.id }))
            structure == CanvasStructure.FISHBONE && parentOf(parent)?.let { isSpineRoot(it) } == true -> {
                val sy = spineRootOf(parent)?.let { spineY(box(it)) } ?: 0f
                kids.sortedWith(compareBy({ abs(it.y + box(it).h / 2f - sy) }, { it.x }, { it.createdAt }, { it.id }))
            }
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

    /** S9 — the node a spine hangs from: a node under a spine structure whose parent is not under the same one (the board's
     * root, or a node given the structure itself). */
    fun isSpineRoot(node: CanvasNode): Boolean {
        val s = structureOf(node)
        if (!s.spine) return false
        val p = parentOf(node) ?: return true
        return structureOf(p) != s
    }

    /** The spine root above [node] (itself when it is one), or null outside a spine structure. */
    fun spineRootOf(node: CanvasNode): CanvasNode? {
        var cur: CanvasNode? = node
        val seen = HashSet<Long>()
        while (cur != null && seen.add(cur.id)) {
            if (!structureOf(cur).spine) return null
            if (isSpineRoot(cur)) return cur
            cur = parentOf(cur)
        }
        return null
    }

    /** S9 — a fishbone topic: a spine root's child under FISHBONE. Its children are the bones. */
    fun isRibTopic(node: CanvasNode): Boolean = structureOf(node) == CanvasStructure.FISHBONE && parentOf(node)?.let { isSpineRoot(it) } == true

    /** The structure that lays this node's subtree out: its own, else the nearest ancestor's, else the board's. */
    fun structureOf(node: CanvasNode): CanvasStructure = structureMemo.getOrPut(node.id) {
        if (node.structure != null) CanvasStructure.fromKey(node.structure)
        else parentOf(node)?.let { structureOf(it) } ?: boardStructure
    }

    /** The side a node's subtree grows on: DOWN under a DOWN structure; under MAP a root's child is on the
     * side its own centre lies on (S8, 2026-09-20 — a drag across the root moves it over; Xmind's), and every deeper node keeps
     * its branch's side; RIGHT and FREE grow right. A node with its own structure starts afresh. */
    fun sideOf(node: CanvasNode): TreeSide = sideMemo.getOrPut(node.id) { computeSide(node) }

    private fun computeSide(node: CanvasNode): TreeSide {
        val structure = structureOf(node)
        if (structure == CanvasStructure.DOWN) return TreeSide.DOWN
        val parent = parentOf(node) ?: return TreeSide.RIGHT
        val parentStructure = structureOf(parent)
        if (node.structure != null && structure != parentStructure) return TreeSide.RIGHT
        if (structure.spine) {
            // S9 — a spine root's child is on the side its own centre lies on (S8's rule, turned on its side); under a timeline
            // every deeper node keeps its topic's side (the org chart grows away from the spine); a fishbone's bones and
            // what hangs from them grow right.
            if (isSpineRoot(parent)) return if (node.y + box(node).h / 2f < spineY(box(parent))) TreeSide.UP else TreeSide.DOWN
            return if (structure == CanvasStructure.TIMELINE) sideOf(parent) else TreeSide.RIGHT
        }
        if (parentStructure == CanvasStructure.MAP && parent.parentId == null) {
            // The side is the node's own: its centre against the root's (the boxes' widths differ, so centres, not edges).
            val pb = box(parent)
            return if (node.x + box(node).w / 2f < pb.x + pb.w / 2f) TreeSide.LEFT else TreeSide.RIGHT
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
        val structure = structureOf(node)
        if (structure == CanvasStructure.DOWN || structure == CanvasStructure.TIMELINE) return TreeLevel.BRANCH
        // S9 — a fishbone: the rib's topic is a strip, its bones and everything past them text on the line.
        if (structure == CanvasStructure.FISHBONE) return if (isRibTopic(node) || spineRootOf(node) == node) TreeLevel.BRANCH else TreeLevel.LEAF
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

    /** S12 — what a frame holds: a following frame its anchor and the anchor's subtree (frames following
     * nodes in it included); a hand-sized frame every node whose box lies inside its rectangle (nested
     * frames too, by their own rectangles). Never the frame itself. */
    fun frameContents(frame: CanvasNode): List<CanvasNode> {
        if (frame.type != CanvasNodeType.FRAME) return emptyList()
        val anchor = followedBy(frame)
        if (anchor != null) return (listOf(anchor) + descendants(anchor.id)).filter { it.id != frame.id }
        val f = nodeBox(frame)
        return nodes.filter { other ->
            if (other.id == frame.id) return@filter false
            val b = when {
                other.type != CanvasNodeType.FRAME -> nodeBox(other, titleOf = titleOf)
                followedBy(other) == null -> nodeBox(other)   // a nested hand-sized frame, by its own rectangle
                else -> null                                  // a following frame goes with its anchor (`hidden`)
            }
            b != null && b.x >= f.x && b.y >= f.y && b.right <= f.right && b.bottom <= f.bottom
        }
    }

    /** The nodes a fold hides: every descendant of a folded node (and a frame following one); S12 — a
     * collapsed frame hides what it holds. Positions are kept, as a fold keeps them. */
    val hidden: Set<Long> by lazy {
        val out = HashSet<Long>()
        for (n in nodes) if (n.folded && n.id !in out) {
            if (n.type == CanvasNodeType.FRAME) frameContents(n).forEach { out += it.id }
            else descendants(n.id).forEach { out += it.id }
        }
        // A frame following a hidden anchor is hidden with it — unless it is the collapsed frame that hides the anchor.
        nodes.filter { it.type == CanvasNodeType.FRAME && !it.folded && followedBy(it)?.id in out }.forEach { out += it.id }
        out
    }

    fun isVisible(node: CanvasNode): Boolean = node.id !in hidden

    /** What a fold hides under this node, for the badge; a frame's count is its contents' cards (S12). */
    fun hiddenCount(id: Long): Int {
        val n = byId[id] ?: return 0
        return (if (n.type == CanvasNodeType.FRAME) frameContents(n) else descendants(id)).count { it.type != CanvasNodeType.FRAME }
    }

    /** A frame's anchor: the node whose subtree it follows, if it has one. */
    fun followedBy(frame: CanvasNode): CanvasNode? = if (frame.type == CanvasNodeType.FRAME) parentOf(frame) else null

    /** The box a node draws in, by its level; a following frame's from its subtree's visible boxes plus [FRAME_FOLLOW_PAD]. */
    fun box(node: CanvasNode): NodeBox = boxMemo.getOrPut(node.id) {
        if (node.type == CanvasNodeType.FRAME) {
            val anchor = followedBy(node)
            // S12 — a collapsed frame is a strip at its own top-left (a following one where its anchor stood).
            if (node.folded) {
                val w = cardWidth(node.text.orEmpty().ifBlank { "Frame" }) + FRAME_COLLAPSED_EXTRA
                return@getOrPut if (anchor != null) NodeBox(anchor.x, anchor.y, w, CANVAS_NODE_H) else NodeBox(node.x, node.y, w, CANVAS_NODE_H)
            }
            if (anchor == null) return@getOrPut nodeBox(node, titleOf = titleOf)
            val boxes = (listOf(anchor) + descendants(anchor.id).filter { it.type != CanvasNodeType.FRAME && isVisible(it) }).map { box(it) }
            val x = boxes.minOf { it.x } - FRAME_FOLLOW_PAD
            val y = boxes.minOf { it.y } - FRAME_FOLLOW_PAD
            val r = boxes.maxOf { it.right } + FRAME_FOLLOW_PAD
            val b = boxes.maxOf { it.bottom } + FRAME_FOLLOW_PAD
            return@getOrPut NodeBox(x, y, r - x, b - y)
        }
        when (levelOf(node)) {
            TreeLevel.ROOT -> NodeBox(node.x, node.y, CANVAS_ROOT_W, CANVAS_ROOT_H)
            TreeLevel.BRANCH -> nodeBox(node, titleOf = titleOf)
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
    TreeSide.UP -> BranchAnchors(parent.x + parent.w / 2f, parent.y, child.x + child.w / 2f, child.bottom)
}

/** S9 — a spine's line: the root's centre height. */
fun spineY(root: NodeBox): Float = root.y + root.h / 2f

/** S9 — where a spine ends: past its last topic (and any bone), by the tail. */
fun spineEnd(root: NodeBox, extents: List<NodeBox>): Float = maxOf(root.right, extents.maxOfOrNull { it.right } ?: root.right) + SPINE_TAIL

/** S9 — a timeline topic's anchor on the spine and the edge its stem reaches. */
fun stemAnchors(topic: NodeBox, spineY: Float, side: TreeSide): BranchAnchors {
    val cx = topic.x + topic.w / 2f
    return BranchAnchors(cx, spineY, cx, if (side == TreeSide.UP) topic.bottom else topic.y)
}

/** S9 — a rib from its foot on the spine to the topic's near edge, leaning forward at [RIB_ANGLE_DEG]; derived from the
 * topic's box alone, so a dragged topic keeps a straight rib. */
data class Rib(val footX: Float, val spineY: Float, val topX: Float, val topY: Float) {
    /** The rib's x at a height between its ends (a bone's attachment). */
    fun xAt(y: Float): Float = if (topY == spineY) footX else footX + (topX - footX) * (y - spineY) / (topY - spineY)
}

fun ribOf(topic: NodeBox, spineY: Float, side: TreeSide): Rib {
    val topY = if (side == TreeSide.UP) topic.bottom else topic.y
    val topX = topic.x + topic.w / 2f
    val dy = abs(spineY - topY)
    return Rib(topX - dy / tan(Math.toRadians(RIB_ANGLE_DEG.toDouble())).toFloat(), spineY, topX, topY)
}

/** S9 — a rib long enough for [bones] bones at [BONE_GAP], never under [RIB_MIN]. */
fun ribLength(bones: Int): Float = maxOf(RIB_MIN, RIB_END_PAD + BONE_GAP * (bones + 1))

/** A leaf's branch runs on as its underline: the line's ends at the word's baseline. */
fun leafUnderline(leaf: NodeBox): BranchAnchors = BranchAnchors(leaf.x, leaf.bottom, leaf.right, leaf.bottom)

/** The fold badge's centre at the branch's end, on the node's outer side. */
fun foldBadgeAt(box: NodeBox, side: TreeSide): Pair<Float, Float> = when (side) {
    TreeSide.RIGHT -> (box.right + FOLD_BADGE / 2f + 4f) to (box.y + box.h / 2f)
    TreeSide.LEFT -> (box.x - FOLD_BADGE / 2f - 4f) to (box.y + box.h / 2f)
    TreeSide.DOWN -> (box.x + box.w / 2f) to (box.bottom + FOLD_BADGE / 2f + 4f)
    TreeSide.UP -> (box.x + box.w / 2f) to (box.y - FOLD_BADGE / 2f - 4f)
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
    fun visibleKids(n: CanvasNode): List<CanvasNode> = if (n.folded) emptyList() else tree.children(n.id).filter { tree.isVisible(it) }

    // S9 — how a node's children are arranged: beside it (a stack), under or over it (a row), along a spine, along a rib.
    fun columns(n: CanvasNode): Boolean { val s = tree.structureOf(n); return s == CanvasStructure.DOWN || (s == CanvasStructure.TIMELINE && !tree.isSpineRoot(n)) }
    fun stacks(n: CanvasNode): Boolean = !columns(n) && !tree.isSpineRoot(n) && !tree.isRibTopic(n)
    val bandH = HashMap<Long, Float>()
    fun bandHeight(n: CanvasNode): Float = bandH.getOrPut(n.id) {
        val own = tree.box(n).h
        val kids = visibleKids(n)
        if (kids.isEmpty() || !stacks(n)) own
        else maxOf(own, kids.sumOf { bandHeight(it).toDouble() }.toFloat() + TREE_SIBLING_GAP * (kids.size - 1))
    }
    val bandW = HashMap<Long, Float>()
    fun bandWidth(n: CanvasNode): Float = bandW.getOrPut(n.id) {
        val own = tree.box(n).w
        val kids = visibleKids(n)
        if (kids.isEmpty() || !columns(n)) own
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
            // S9 — a timeline's spine: topics along it in order, each on its own side, a slot as wide as its org chart's band;
            // slots on one side never overlap, the two sides may interleave, anchors at least SPINE_MIN_STEP apart.
            structure == CanvasStructure.TIMELINE && tree.isSpineRoot(n) -> {
                val sy = spineY(pb)
                val x0 = pb.right + DOWN_SIBLING_GAP
                val sideRight = HashMap<TreeSide, Float>().apply { put(TreeSide.UP, x0 - DOWN_SIBLING_GAP); put(TreeSide.DOWN, x0 - DOWN_SIBLING_GAP) }
                var lastAnchor = x0 - SPINE_MIN_STEP
                for (k in kids) {
                    val side = if (tree.sideOf(k) == TreeSide.UP) TreeSide.UP else TreeSide.DOWN
                    val band = bandWidth(k)
                    val kb = tree.box(k)
                    val left = maxOf(sideRight.getValue(side) + DOWN_SIBLING_GAP, lastAnchor + SPINE_MIN_STEP - band / 2f)
                    val anchor = left + band / 2f
                    place(k, anchor - kb.w / 2f, if (side == TreeSide.UP) sy - SPINE_STEM - kb.h else sy + SPINE_STEM)
                    layoutChildren(k)
                    sideRight[side] = left + band
                    lastAnchor = anchor
                }
            }
            // S9 — a fishbone's spine: a rib per topic, leaning forward, as long as its bones need; a foot after the same
            // side's previous rib is clear (its strip, and its bones brought back to the spine along the slope).
            structure == CanvasStructure.FISHBONE && tree.isSpineRoot(n) -> {
                val sy = spineY(pb)
                val x0 = pb.right + DOWN_SIBLING_GAP
                val rad = Math.toRadians(RIB_ANGLE_DEG.toDouble())
                val cosA = cos(rad).toFloat(); val sinA = sin(rad).toFloat()
                val sideBones = HashMap<TreeSide, Float>().apply { put(TreeSide.UP, x0 - DOWN_SIBLING_GAP); put(TreeSide.DOWN, x0 - DOWN_SIBLING_GAP) }
                val sideStrip = HashMap<TreeSide, Float>().apply { put(TreeSide.UP, x0 - DOWN_SIBLING_GAP); put(TreeSide.DOWN, x0 - DOWN_SIBLING_GAP) }
                var lastFoot = x0 - SPINE_MIN_STEP
                for (k in kids) {
                    val side = if (tree.sideOf(k) == TreeSide.UP) TreeSide.UP else TreeSide.DOWN
                    val kb = tree.box(k)
                    val bones = visibleKids(k)
                    val rib = ribLength(bones.size)
                    val dx = rib * cosA; val dy = rib * sinA
                    val foot = maxOf(sideBones.getValue(side) + DOWN_SIBLING_GAP, sideStrip.getValue(side) + DOWN_SIBLING_GAP + kb.w / 2f - dx, lastFoot + SPINE_MIN_STEP)
                    val topX = foot + dx
                    place(k, topX - kb.w / 2f, if (side == TreeSide.UP) sy - dy - kb.h else sy + dy)
                    var reach = foot
                    bones.forEachIndexed { j, b ->
                        val f = (j + 1).toFloat() / (bones.size + 1)
                        val by = if (side == TreeSide.UP) sy - dy * f else sy + dy * f
                        val bx = foot + dx * f
                        val bb = tree.box(b)
                        place(b, bx + BONE_INSET, by - bb.h)
                        layoutChildren(b)
                        // the bone's subtree, brought back to the spine along the slope
                        val right = (listOf(b) + tree.descendants(b.id).filter { tree.isVisible(it) && it.type != CanvasNodeType.FRAME }).maxOf { boxNow(it).right }
                        reach = maxOf(reach, right - dx * f)
                    }
                    sideBones[side] = reach
                    sideStrip[side] = topX + kb.w / 2f
                    lastFoot = foot
                }
            }
            columns(n) -> {
                val total = kids.sumOf { bandWidth(it).toDouble() }.toFloat() + DOWN_SIBLING_GAP * (kids.size - 1)
                var left = pb.x + pb.w / 2f - total / 2f
                val up = tree.sideOf(n) == TreeSide.UP
                for (k in kids) {
                    val bw = bandWidth(k)
                    val kb = tree.box(k)
                    place(k, left + (bw - kb.w) / 2f, if (up) pb.y - DOWN_LEVEL_GAP - kb.h else pb.bottom + DOWN_LEVEL_GAP)
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
fun newChildPosition(tree: CanvasTree, parent: CanvasNode, childW: Float = cardWidth(null), childH: Float = CANVAS_NODE_H): Pair<Float, Float> {
    val pb = tree.box(parent)
    val kids = tree.children(parent.id)
    val structure = tree.structureOf(parent)
    // S9 — along a spine: after the last topic, on the other side (the sides alternate as the map grows); a fishbone's new
    // bone at the rib's next place; a timeline topic's new child in its row, over or under it.
    if (structure.spine && tree.isSpineRoot(parent)) {
        val sy = spineY(pb)
        val last = kids.lastOrNull()
        val side = if (last == null || tree.sideOf(last) == TreeSide.DOWN) TreeSide.UP else TreeSide.DOWN
        val x = (last?.let { tree.box(it).right } ?: pb.right) + DOWN_SIBLING_GAP
        val lift = if (structure == CanvasStructure.TIMELINE) SPINE_STEM else RIB_MIN * sin(Math.toRadians(RIB_ANGLE_DEG.toDouble())).toFloat()
        return x to (if (side == TreeSide.UP) sy - lift - childH else sy + lift)
    }
    if (tree.isRibTopic(parent)) {
        val root = tree.spineRootOf(parent)!!
        val rib = ribOf(pb, spineY(tree.box(root)), tree.sideOf(parent))
        val f = (kids.size + 1).toFloat() / (kids.size + 2)
        val y = rib.spineY + (rib.topY - rib.spineY) * f
        return (rib.xAt(y) + BONE_INSET) to (y - CANVAS_LEAF_H)
    }
    if (structure == CanvasStructure.DOWN || (structure == CanvasStructure.TIMELINE)) {
        val last = kids.lastOrNull()?.let { tree.box(it) }
        val up = tree.sideOf(parent) == TreeSide.UP
        return if (last == null) (pb.x + pb.w / 2f - childW / 2f) to (if (up) pb.y - DOWN_LEVEL_GAP - childH else pb.bottom + DOWN_LEVEL_GAP)
        else (last.right + DOWN_SIBLING_GAP) to last.y
    }
    // A MAP root's new child goes to the side with fewer children, the right on a tie (the map stays balanced as it grows).
    val side = if (structure == CanvasStructure.MAP && parent.parentId == null) {
        val right = kids.count { tree.sideOf(it) == TreeSide.RIGHT }
        if (kids.size - right < right) TreeSide.LEFT else TreeSide.RIGHT
    } else if (tree.sideOf(parent) == TreeSide.LEFT) TreeSide.LEFT else TreeSide.RIGHT
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
        if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) (if (dx >= 0) TreeSide.RIGHT else TreeSide.LEFT) else (if (dy >= 0) TreeSide.DOWN else TreeSide.UP)
    } else {
        // Toward the other box: the side that faces it.
        if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) (if (dx >= 0) TreeSide.LEFT else TreeSide.RIGHT) else (if (dy >= 0) TreeSide.UP else TreeSide.DOWN)
    }
}

fun relationRoute(from: NodeBox, fromParent: NodeBox?, to: NodeBox, toParent: NodeBox?): RelationRoute {
    fun anchor(b: NodeBox, side: TreeSide): Pair<Float, Float> = when (side) {
        TreeSide.RIGHT -> b.right to (b.y + b.h / 2f)
        TreeSide.LEFT -> b.x to (b.y + b.h / 2f)
        TreeSide.DOWN -> (b.x + b.w / 2f) to b.bottom
        TreeSide.UP -> (b.x + b.w / 2f) to b.y
    }
    val fs = outerSide(from, fromParent, to)
    val ts = outerSide(to, toParent, from)
    val (x1, y1) = anchor(from, fs)
    val (x2, y2) = anchor(to, ts)
    val dist = kotlin.math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    val bow = maxOf(40f, dist / 3f)
    fun normal(side: TreeSide): Pair<Float, Float> = when (side) { TreeSide.RIGHT -> 1f to 0f; TreeSide.LEFT -> -1f to 0f; TreeSide.DOWN -> 0f to 1f; TreeSide.UP -> 0f to -1f }
    val (nx1, ny1) = normal(fs)
    val (nx2, ny2) = normal(ts)
    return RelationRoute(x1, y1, x1 + nx1 * bow, y1 + ny1 * bow, x2 + nx2 * bow, y2 + ny2 * bow, x2, y2)
}
