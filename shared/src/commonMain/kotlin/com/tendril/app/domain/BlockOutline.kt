package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType

/** One block as the editor draws it: the block, how far it is indented, and — for a
 * `NUMBERED_LIST_ITEM` only, §B7 — its 1-based position within its own consecutive run of
 * numbered items at that depth. `0` for every other block type, where it means nothing. */
data class OutlineBlock(val block: Block, val depth: Int, val listPosition: Int = 0)

/**
 * Turns a page's flat block rows into the order the editor draws them: each block followed by
 * its subtree, depth-first, siblings in `order`.
 *
 * **Depth is unlimited since 2026-09-11 (§0.6.1).** `Block.parentBlockId` was never bounded;
 * §3.1.1's "nestable one level" lived in this function, which re-attached a grandchild to its
 * top-level ancestor, and in [indentTargetFor], which refused an already-indented block. Both
 * are gone: a grandchild is drawn at depth 2, and the outline mind map (§0.6.2) is a drawing of
 * exactly this tree, which is why there is no second tree for it to keep.
 *
 * **Every block comes out**, whatever shape the data is in:
 *
 *  - a child whose parent is not on this page is drawn as a root, not dropped;
 *  - a parent cycle (which no UI can create, but a merge of two devices' edits could) is
 *    broken at the block where the walk would repeat, which becomes a root;
 *  - a collapsed toggle keeps its whole subtree — hidden, not detached — unless [expandAll]
 *    asks for everything, which an export does: a person choosing to hide a branch on screen is
 *    not choosing to lose it from the file.
 */
fun outlineOf(blocks: List<Block>, expandAll: Boolean = false): List<OutlineBlock> {
    val byId = blocks.associateBy { it.id }
    val sorted = blocks.sortedBy { it.order }

    // A block is a root when it has no parent here, or when following parents from it comes
    // back to it. The cycle check is per block so that exactly the blocks on the cycle become
    // roots and everything hanging off them is still drawn beneath them.
    fun isRoot(block: Block): Boolean {
        var current = block.parentBlockId?.let(byId::get) ?: return true
        val seen = mutableSetOf(block.id)
        while (true) {
            if (!seen.add(current.id)) return true
            current = current.parentBlockId?.let(byId::get) ?: return false
        }
    }

    val children = LinkedHashMap<Long, MutableList<Block>>()
    val roots = mutableListOf<Block>()
    for (block in sorted) {
        if (isRoot(block)) roots += block
        else children.getOrPut(block.parentBlockId!!) { mutableListOf() } += block
    }

    val outline = mutableListOf<OutlineBlock>()
    val emitted = mutableSetOf<Long>()
    fun walk(block: Block, depth: Int) {
        if (!emitted.add(block.id)) return
        outline += OutlineBlock(block, depth)
        if (block.type == BlockType.TOGGLE && !block.toggleExpanded && !expandAll) return
        children[block.id]?.forEach { walk(it, depth + 1) }
    }
    roots.forEach { walk(it, 0) }
    return withListPositions(outline)
}

/** §B7 — a `NUMBERED_LIST_ITEM`'s prefix was `block.order + 1`: its position among *every*
 * block on the page, not its position in the list it visually belongs to, so a list starting
 * partway down a page (after a heading, say) numbered from wherever `order` happened to be
 * rather than from 1. A run breaks on any non-`NUMBERED_LIST_ITEM` neighbor, including a depth
 * change — [outlineOf]'s own ordering guarantees siblings under a different parent are never
 * adjacent without an intervening block, so a plain "same type, same depth as the previous
 * entry" check is enough; nothing here needs to know about parents directly. */
private fun withListPositions(outline: List<OutlineBlock>): List<OutlineBlock> {
    val numbered = ArrayList<OutlineBlock>(outline.size)
    var runCounter = 0
    for (i in outline.indices) {
        val entry = outline[i]
        if (entry.block.type != BlockType.NUMBERED_LIST_ITEM) {
            runCounter = 0
            numbered += entry
            continue
        }
        val previous = outline.getOrNull(i - 1)
        runCounter = if (previous?.block?.type == BlockType.NUMBERED_LIST_ITEM && previous.depth == entry.depth) runCounter + 1 else 1
        numbered += entry.copy(listPosition = runCounter)
    }
    return numbered
}

/**
 * The block [block] would become a child of when indented: its nearest preceding *sibling* —
 * the previous block under the same parent. Null when there is none: the first block on a page,
 * or the first child under a parent, has nothing to tuck under. Any depth (§0.6.1); the outliner
 * rule Logseq and Workflowy share.
 */
fun indentTargetFor(block: Block, blocks: List<Block>): Block? =
    blocks.asSequence()
        .filter { it.parentBlockId == block.parentBlockId && it.order < block.order && it.id != block.id }
        .maxByOrNull { it.order }

/** What an outdent does to the tree — see [outdentPlanFor]. */
data class OutdentPlan(
    /** The block's new parent: its former grandparent, or null for the top level. */
    val newParentId: Long?,
    /** The former later siblings, which become the block's children so that the page reads in
     * the same order after the outdent as before it. */
    val adoptedIds: List<Long>,
)

/**
 * Outdenting moves [block] one level up, under its former grandparent — and takes the siblings
 * that followed it *with* it, as its children. Without that, the outline would draw those
 * siblings before the outdented block (they still sit under the old parent, which sorts first)
 * and a block would visibly jump below the lines that used to follow it. Logseq's, Workflowy's
 * and every outliner's answer; it is what makes indent and outdent inverses.
 *
 * Null when [block] is already at the top level, or when its parent is not on this page.
 */
fun outdentPlanFor(block: Block, blocks: List<Block>): OutdentPlan? {
    val parentId = block.parentBlockId ?: return null
    val parent = blocks.firstOrNull { it.id == parentId } ?: return null
    val adopted = blocks
        .filter { it.parentBlockId == parentId && it.order > block.order && it.id != block.id }
        .sortedBy { it.order }
        .map { it.id }
    return OutdentPlan(newParentId = parent.parentBlockId, adoptedIds = adopted)
}
