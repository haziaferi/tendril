package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType

/** One block as the editor draws it: the block, how far it is indented, and — for a
 * `NUMBERED_LIST_ITEM` only, §B7 — its 1-based position within its own consecutive run of
 * numbered items at that depth. `0` for every other block type, where it means nothing. */
data class OutlineBlock(val block: Block, val depth: Int, val listPosition: Int = 0)

/** §3.1.1 — "nestable one level via indent — matches typical personal-notes depth, not
 * arbitrary nesting". One level means depth 0 and depth 1, and nothing deeper. */
const val MAX_BLOCK_DEPTH = 1

/**
 * Turns a page's flat block rows into the order the editor draws them: each top-level block,
 * followed by its children.
 *
 * `Block.parentBlockId` has existed (indexed, even) since the schema was written, and
 * `PageDetailScreen` has always rendered an indent for it — but the list filtered to
 * `parentBlockId == null`, so a child was simply never drawn. That is why the Notion import
 * path used to collapse imported nesting into a top-level sequence, "rather than risking
 * silently-invisible content" (spec Revision Log, 2026-08-30).
 *
 * **This function is what ended that**, and the KDoc here described the old world for a while
 * after it stopped being true. `PageDetailScreen` builds its list from `outlineOf` now, so a
 * child *is* drawn; with the risk gone, the 2026-09-05 revision reversed the import decision,
 * and `NotionImporter` hangs an indented source block off the last top-level block it emitted —
 * the same rule as [indentTargetFor], which is what the in-app Indent action uses. Nothing
 * upstream normalises the shape before it reaches here any more, so the invariant below is
 * load-bearing rather than defensive.
 *
 * **Every block comes out**, whatever shape the data is in:
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
