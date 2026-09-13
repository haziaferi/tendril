package com.tendril.app.domain

import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.roadmap.EdgeSource
import com.tendril.app.domain.roadmap.RoadMapEdge
import com.tendril.app.domain.roadmap.RoadMapFilter
import com.tendril.app.domain.roadmap.RoadMapGraph
import com.tendril.app.domain.roadmap.filtered
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** §3.4 / §0.8 step 8c — the Road Map's filters and its local-graph walk, off the ViewModel. */
class RoadMapFilterTest {

    private val at = Instant.EPOCH
    private fun page(id: Long, title: String, kind: PageKind = PageKind.PAGE, parentId: Long? = null) =
        Page(id = id, title = title, kind = kind, parentId = parentId, createdAt = at, updatedAt = at)

    // Journal(1) ← day(2) → Trip(3) → Books(4, database) — Canvas(5) related to Trip; Island(6) alone.
    private val journal = page(1, "Journal")
    private val day = page(2, "journal/2026-09-13", parentId = 1)
    private val trip = page(3, "Trip")
    private val books = page(4, "Books", PageKind.DATABASE)
    private val board = page(5, "Board", PageKind.CANVAS)
    private val island = page(6, "Island")
    private val graph = RoadMapGraph(
        nodes = listOf(journal, day, trip, books, board, island),
        edges = listOf(
            RoadMapEdge(2, 1, EdgeSource.MENTION), RoadMapEdge(2, 3, EdgeSource.MENTION),
            RoadMapEdge(3, 4, EdgeSource.MENTION), RoadMapEdge(5, 3, EdgeSource.MANUAL),
        ),
    )
    private val journalIds = setOf(1L, 2L)

    @Test
    fun `the default hides the Journal root and its days, and every edge touching them`() {
        val shown = graph.filtered(RoadMapFilter(), journalIds, null)
        assertEquals(listOf(3L, 4L, 5L, 6L), shown.nodes.map { it.id })
        assertTrue(shown.edges.none { it.fromPageId in journalIds || it.toPageId in journalIds })
        assertEquals(2, shown.edges.size)
        assertEquals("shown again when asked", 6, graph.filtered(RoadMapFilter(hideJournal = false), journalIds, null).nodes.size)
    }

    @Test
    fun `a kind switched off takes its pages and edges with it`() {
        val shown = graph.filtered(RoadMapFilter(hideJournal = false, kinds = setOf(PageKind.PAGE, PageKind.CANVAS)), journalIds, null)
        assertFalse(shown.nodes.any { it.id == 4L })
        assertFalse(shown.edges.any { it.toPageId == 4L })
        assertEquals(3, shown.edges.size)
    }

    @Test
    fun `a label narrows the map to the pages carrying it`() {
        val shown = graph.filtered(RoadMapFilter(hideJournal = false, labelId = 9), journalIds, labelledPageIds = setOf(3L, 5L))
        assertEquals(listOf(3L, 5L), shown.nodes.map { it.id })
        assertEquals(listOf(EdgeSource.MANUAL), shown.edges.map { it.source })
    }

    @Test
    fun `the local graph walks out by hops, undirected, and the filter runs before it`() {
        assertEquals(setOf(2L, 3L, 4L, 5L), graph.around(3, 1).nodes.map { it.id }.toSet())
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), graph.around(3, 2).nodes.map { it.id }.toSet())
        val hidden = graph.filtered(RoadMapFilter(), journalIds, null).around(3, 2)
        assertEquals("depth 2 never routes through the hidden day page", setOf(3L, 4L, 5L), hidden.nodes.map { it.id }.toSet())
    }

    @Test
    fun `the persisted half round-trips, and an absent or empty value is the default`() {
        val f = RoadMapFilter(hideJournal = false, kinds = setOf(PageKind.PAGE, PageKind.CANVAS), labelId = 4)
        val back = RoadMapFilter.decode(f.encode())
        assertEquals(f.copy(labelId = null), back)
        assertEquals(RoadMapFilter(), RoadMapFilter.decode(null))
        assertEquals("j", RoadMapFilter(kinds = emptySet()).encode().first().toString())
        assertEquals("no kind letters means every kind", PageKind.entries.toSet(), RoadMapFilter.decode("j").kinds)
    }
}
