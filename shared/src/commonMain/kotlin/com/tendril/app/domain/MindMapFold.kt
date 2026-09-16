package com.tendril.app.domain

/**
 * §0.6.2 — a block drawn as a mind map folds its subtree away from the list: these are the ids
 * the page does not draw as rows. [opened] (14h·2, find-function): maps unfolded for the session
 * — a find match inside one is reachable only if the subtree is on screen, so ↵ past the last
 * visible match unfolds the map that holds the next one (state, never a write). Lifted out of
 * `PageDetailScreen` so the fold and its exception are tested.
 */
fun mappedDescendants(outline: List<OutlineBlock>, opened: Set<Long> = emptySet()): Set<Long> {
    val hidden = mutableSetOf<Long>()
    var i = 0
    while (i < outline.size) {
        val entry = outline[i]
        if (entry.block.mindMap && entry.block.id !in hidden && entry.block.id !in opened) {
            var j = i + 1
            while (j < outline.size && outline[j].depth > entry.depth) { hidden += outline[j].block.id; j++ }
        }
        i++
    }
    return hidden
}

/**
 * The nearest folded map above [blockId] — the one to open so the block is drawn — or null when
 * the block is on screen already (no folded ancestor).
 */
fun owningMap(outline: List<OutlineBlock>, blockId: Long, opened: Set<Long> = emptySet()): Long? {
    val index = outline.indexOfFirst { it.block.id == blockId }
    if (index < 0) return null
    var depth = outline[index].depth
    var found: Long? = null
    for (i in index - 1 downTo 0) {
        val e = outline[i]
        if (e.depth < depth) {
            if (e.block.mindMap && e.block.id !in opened) found = e.block.id   // the outermost folded ancestor wins
            depth = e.depth
        }
    }
    return found
}
