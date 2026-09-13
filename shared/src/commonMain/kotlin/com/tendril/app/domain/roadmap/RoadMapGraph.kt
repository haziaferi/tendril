package com.tendril.app.domain.roadmap

import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind

/** §3.4's two edge sources, kept distinct through rendering — a mention has a real
 * mentioner→mentioned direction (drawn as an arrow); a manual "Relate to" is genuinely
 * undirected (drawn as a plain line). */
enum class EdgeSource { MENTION, MANUAL }

data class RoadMapEdge(val fromPageId: Long, val toPageId: Long, val source: EdgeSource)

data class RoadMapGraph(val nodes: List<Page>, val edges: List<RoadMapEdge>) {
    /** Every id a given node touches, either direction — the neighbor-highlight set and the
     * BFS focus walk both read this rather than re-deriving it per call. */
    fun neighborsOf(pageId: Long): Set<Long> = edges.mapNotNull { edge ->
        when (pageId) {
            edge.fromPageId -> edge.toPageId
            edge.toPageId -> edge.fromPageId
            else -> null
        }
    }.toSet()

    fun degreeOf(pageId: Long): Int = edges.count { it.fromPageId == pageId || it.toPageId == pageId }

    /** Breadth-first walk out to [depth] hops from [rootId], undirected — a mention's arrow
     * direction matters for rendering, not for "is this page part of the neighborhood."
     * The Obsidian "local graph". */
    fun around(rootId: Long, depth: Int): RoadMapGraph {
        var frontier = setOf(rootId)
        val visited = mutableSetOf(rootId)
        repeat(depth) {
            val next = frontier.flatMap { neighborsOf(it) }.filterNot { it in visited }.toSet()
            visited += next
            frontier = next
        }
        return keep(visited)
    }

    /** The subgraph on [ids]: those nodes, and only the edges with both ends among them. */
    fun keep(ids: Set<Long>): RoadMapGraph =
        RoadMapGraph(nodes.filter { it.id in ids }, edges.filter { it.fromPageId in ids && it.toPageId in ids })

    companion object {
        val EMPTY = RoadMapGraph(emptyList(), emptyList())
    }
}

/**
 * §3.4 (amended, §0.8 step 8c / B§6 #14) — what the map leaves out. Journal pages hidden by
 * default (Logseq's answer: a day page mentions everything and says nothing about structure);
 * a page kind can be switched off; one label at a time narrows the map to the pages carrying
 * it. Applied *before* the focus walk, so "from this page, depth 2" never routes through a
 * hidden page.
 */
data class RoadMapFilter(
    val hideJournal: Boolean = true,
    val kinds: Set<PageKind> = PageKind.entries.toSet(),
    /** Per-device id, so it is never persisted — see [encode]. */
    val labelId: Long? = null,
) {
    /** The persisted half: `j` when the Journal is hidden, then one letter per shown kind. */
    fun encode(): String = buildString {
        if (hideJournal) append('j')
        PageKind.entries.filter { it in kinds }.forEach { append(it.name.first().lowercaseChar()) }
    }

    companion object {
        fun decode(value: String?): RoadMapFilter {
            if (value == null) return RoadMapFilter()
            val kinds = PageKind.entries.filter { it.name.first().lowercaseChar() in value }.toSet()
            return RoadMapFilter(hideJournal = 'j' in value, kinds = kinds.ifEmpty { PageKind.entries.toSet() })
        }
    }
}

/**
 * [filter] applied to the full graph. [journalPageIds] is the Journal root and its day pages;
 * [labelledPageIds] is null when no label is chosen, else the pages carrying the chosen one.
 */
fun RoadMapGraph.filtered(filter: RoadMapFilter, journalPageIds: Set<Long>, labelledPageIds: Set<Long>?): RoadMapGraph {
    val kept = nodes.filter { page ->
        page.kind in filter.kinds &&
            !(filter.hideJournal && page.id in journalPageIds) &&
            (labelledPageIds == null || page.id in labelledPageIds)
    }.mapTo(mutableSetOf()) { it.id }
    return keep(kept)
}
