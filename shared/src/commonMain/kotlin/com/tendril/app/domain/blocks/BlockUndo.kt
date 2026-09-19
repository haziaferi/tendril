package com.tendril.app.domain.blocks

import com.tendril.app.data.page.Block

/**
 * §0.10 item 19 — a session undo stack for *block operations*. An entry is the affected records
 * before and after the operation; undoing it writes [BlockEdit.before] back over the page through
 * the same DAO calls the operation used (a deleted block returns with its own id, so its
 * children's `parentBlockId` still point at it — Room keeps a given id on insert), and redo is the
 * same entry the other way round. Typing is not on the stack: the field keeps its own undo, and
 * page history (§0.6.13) stays the ten-minute net under both.
 */
data class BlockEdit(val label: String, val before: List<Block>, val after: List<Block>) {
    fun inverse(): BlockEdit = BlockEdit(label, before = after, after = before)
    val affectedIds: Set<Long> get() = (before.map { it.id } + after.map { it.id }).toSet()
}

/** What to write so the affected blocks read as [target]: rows the page lacks are inserted, rows
 * it has are updated, rows the target lacks are deleted. Untouched blocks are never named. */
data class ApplyPlan(val insert: List<Block>, val update: List<Block>, val delete: List<Long>)

fun planApply(current: List<Block>, target: List<Block>, affectedIds: Set<Long>): ApplyPlan {
    val now = current.associateBy { it.id }
    val want = target.associateBy { it.id }
    val insert = mutableListOf<Block>()
    val update = mutableListOf<Block>()
    val delete = mutableListOf<Long>()
    for (id in affectedIds) {
        val has = now[id]
        val wants = want[id]
        when {
            has == null && wants != null -> insert += wants
            has != null && wants == null -> delete += id
            has != null && wants != null && has != wants -> update += wants
        }
    }
    // Parents before children, so a child's foreign key finds its parent on insert; deletes the
    // other way round (children first) for the same reason.
    val depth = target.associate { it.id to depthOf(it, want) }
    return ApplyPlan(insert.sortedBy { depth[it.id] ?: 0 }, update, delete.sortedByDescending { depthOf(now[it]!!, now) })
}

private fun depthOf(block: Block, byId: Map<Long, Block>): Int {
    var d = 0
    var p = block.parentBlockId
    val seen = mutableSetOf(block.id)
    while (p != null && seen.add(p)) { d++; p = byId[p]?.parentBlockId }
    return d
}

/** Fifty entries, newest last; a new edit forgets the redo branch (every editor's rule). */
class BlockUndoStack(private val limit: Int = 50) {
    private val done = ArrayDeque<BlockEdit>()
    private val undone = ArrayDeque<BlockEdit>()

    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()
    val size: Int get() = done.size

    fun push(edit: BlockEdit) {
        done.addLast(edit)
        while (done.size > limit) done.removeFirst()
        undone.clear()
    }

    /** The entry to reverse, moved onto the redo branch; null when there is nothing. */
    fun undo(): BlockEdit? = done.removeLastOrNull()?.also { undone.addLast(it) }

    /** The entry to replay, moved back onto the done branch; null when there is nothing. */
    fun redo(): BlockEdit? = undone.removeLastOrNull()?.also { done.addLast(it) }

    fun clear() { done.clear(); undone.clear() }
}
