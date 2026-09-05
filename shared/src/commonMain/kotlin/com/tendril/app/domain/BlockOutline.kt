package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType

/** One block as the editor draws it: the block, and how far it is indented. */
data class OutlineBlock(val block: Block, val depth: Int)

/** §3.1.1 — "nestable one level via indent — matches typical personal-notes depth, not
 * arbitrary nesting". One level means depth 0 and depth 1, and nothing deeper. */
const val MAX_BLOCK_DEPTH = 1

/**
 * Turns a page's flat block rows into the order the editor draws them: each top-level block,
 * followed by its children.
 *
 * `Block.parentBlockId` has existed (indexed, even) since the schema was written, and
 * `PageDetailScreen` has always rendered an indent for it — but the list filtered to
 * `parentBlockId == null`, so a child was simply never drawn. That is why the Notion importer
 * assigns top-level parents only: spec line 46 records it deciding to flatten imported nesting
 * "rather than risking silently-invisible content".
 *
 * That constraint is the one this function is built around. **Every block comes out**, whatever
 * shape the data is in:
 *
 *  - a child whose parent is not on this page is drawn at the top level, not dropped;
 *  - a grandchild is re-attached to its top-level ancestor rather than hidden or drawn at an
 *    indent §3.1.1 does not have;
 *  - a parent cycle (which no UI can create, but a merge of two devices' edits could) resolves
 *    to each block being its own root instead of looping forever or vanishing.
 *
 * The single case where content is legitimately not drawn is a collapsed toggle, because that
 * is a person choosing to hide it and one tap reverses it.
 */
fun outlineOf(blocks: List<Block>): List<OutlineBlock> {
    val byId = blocks.associateBy { it.id }

    /** The top-level block [start] belongs under, or [start] itself. Walks up rather than
     * trusting one hop, so a grandchild lands at depth 1 under the right ancestor; the visited
     * set is what stops a cycle, returning [start] so the block stays visible as its own root. */
    fun rootOf(start: Block): Block {
        var current = start
        val seen = mutableSetOf(current.id)
        while (true) {
            val parent = current.parentBlockId?.let(byId::get) ?: return current
            if (!seen.add(parent.id)) return start
            current = parent
        }
    }

    val roots = mutableListOf<Block>()
    val children = LinkedHashMap<Long, MutableList<Block>>()
    // Sorted once, so both the roots and each child list come out in `order` without sorting
    // again per group.
    for (block in blocks.sortedBy { it.order }) {
        val root = rootOf(block)
        if (root.id == block.id) roots += block
        else children.getOrPut(root.id) { mutableListOf() } += block
    }

    val outline = mutableListOf<OutlineBlock>()
    for (root in roots) {
        outline += OutlineBlock(root, 0)
        // A collapsed toggle keeps its children — they are hidden, not detached, and expanding
        // brings them straight back.
        if (root.type == BlockType.TOGGLE && !root.toggleExpanded) continue
        children[root.id]?.forEach { outline += OutlineBlock(it, MAX_BLOCK_DEPTH) }
    }
    return outline
}

/**
 * The block [block] would become a child of when indented: the nearest preceding top-level
 * sibling. Null when there is none — the first block on a page has nothing to tuck under — or
 * when [block] is already indented, since §3.1.1 stops at one level.
 */
fun indentTargetFor(block: Block, blocks: List<Block>): Block? {
    if (block.parentBlockId != null) return null
    return blocks.asSequence()
        .filter { it.parentBlockId == null && it.order < block.order && it.id != block.id }
        .maxByOrNull { it.order }
}
