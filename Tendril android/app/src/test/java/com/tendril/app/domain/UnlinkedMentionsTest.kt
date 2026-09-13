package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import com.tendril.app.data.page.FormattingSpan
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.page.SpanStyle
import com.tendril.app.domain.references.linkMention
import com.tendril.app.domain.references.unlinkedMentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** §3.1.5 / §0.6.12 — plain-text mentions of a page's title, and turning one into a link. */
class UnlinkedMentionsTest {

    private val at = Instant.EPOCH
    private var nextId = 10L
    private fun page(id: Long, title: String, deleted: Boolean = false, template: Boolean = false) =
        Page(id = id, title = title, kind = PageKind.PAGE, deletedAt = if (deleted) at else null, isTemplate = template, createdAt = at, updatedAt = at)

    private fun block(pageId: Long, content: String, spans: List<FormattingSpan> = emptyList(), type: BlockType = BlockType.PARAGRAPH) =
        Block(id = nextId++, pageId = pageId, type = type, order = 0, content = content, formattingSpans = spans, createdAt = at, updatedAt = at)

    private val trip = page(1, "Trip")
    private val pages = mapOf(
        1L to trip, 2L to page(2, "Packing"), 3L to page(3, "Old", deleted = true),
        4L to page(4, "Template", template = true), 5L to page(5, "Diary"), 6L to page(6, "Linked already"),
    )

    @Test
    fun `finds a plain occurrence on another live page, case-insensitively, once per block`() {
        val hits = unlinkedMentions("Trip", 1, listOf(block(2, "the TRIP needs socks; trip again")), pages, emptySet())
        assertEquals(1, hits.size)
        assertEquals(4..7, hits.single().range)
        assertEquals("Packing", hits.single().page.title)
    }

    @Test
    fun `skips the page itself, trashed and template pages, already-linked pages, and short or journal titles`() {
        val blocks = listOf(
            block(1, "Trip on its own page"),
            block(3, "Trip in the trash"),
            block(4, "Trip in a template"),
            block(6, "Trip on a page that links already"),
            block(2, "Trip, the real one"),
        )
        assertEquals(listOf(2L), unlinkedMentions("Trip", 1, blocks, pages, linkedPageIds = setOf(6)).map { it.page.id })
        assertTrue(unlinkedMentions("Tr", 1, blocks, pages, emptySet()).isEmpty())
        assertTrue(unlinkedMentions("journal/2026-09-13", 1, listOf(block(2, "see journal/2026-09-13")), pages, emptySet()).isEmpty())
    }

    @Test
    fun `an occurrence already under a mention span is a link, not an unlinked mention`() {
        val linked = block(2, "Trip notes", spans = listOf(FormattingSpan(0, 4, SpanStyle.PageMention(1))))
        assertTrue(unlinkedMentions("Trip", 1, listOf(linked), pages, emptySet()).isEmpty())
        val second = block(5, "Trip and Trip", spans = listOf(FormattingSpan(0, 4, SpanStyle.PageMention(1))))
        assertEquals("the later plain one is found", 9..12, unlinkedMentions("Trip", 1, listOf(second), pages, emptySet()).single().range)
    }

    @Test
    fun `linking adds exactly one mention span over the range and leaves the text alone`() {
        val b = block(2, "the trip needs socks")
        val hit = unlinkedMentions("Trip", 1, listOf(b), pages, emptySet()).single()
        val linked = linkMention(hit.block, hit.range, 1)
        assertEquals("the trip needs socks", linked.content)
        assertEquals(listOf(FormattingSpan(4, 8, SpanStyle.PageMention(1))), linked.formattingSpans)
        assertTrue("and it is no longer unlinked", unlinkedMentions("Trip", 1, listOf(linked), pages, emptySet()).isEmpty())
    }
}
