package com.tendril.app.domain

import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.domain.timeline.TIMELINE_PAD_AFTER
import com.tendril.app.domain.timeline.TIMELINE_PAD_BEFORE
import com.tendril.app.domain.timeline.blockedBy
import com.tendril.app.domain.timeline.shifted
import com.tendril.app.domain.timeline.timelineBars
import com.tendril.app.domain.timeline.timelineRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** §0.6.14 / §0.8 step 8f — bars, the grid's range, a drag, and "blocked by". */
class TimelineTest {

    private val d = LocalDate.of(2026, 9, 13)
    private data class R(val name: String, val start: LocalDate?, val end: LocalDate? = null)

    @Test
    fun `dated rows become bars, undated ones do not, an end before the start is one day`() {
        val bars = timelineBars(
            listOf(R("a", d, d.plusDays(2)), R("b", null), R("c", d.plusDays(1)), R("d", d, d.minusDays(3))),
            { it.start }, { it.end },
        )
        assertEquals(listOf("a", "c", "d"), bars.map { it.row.name })
        assertEquals(3, bars[0].days)
        assertEquals(1, bars[1].days)
        assertEquals("an end before its start collapses to the start", d, bars[2].end)
    }

    @Test
    fun `the range pads the bars and always holds today`() {
        val bars = timelineBars(listOf(R("a", d.plusDays(30), d.plusDays(31))), { it.start }, { it.end })
        val range = timelineRange(bars, d)
        assertEquals(d.minusDays(TIMELINE_PAD_BEFORE), range.start)
        assertEquals(d.plusDays(31 + TIMELINE_PAD_AFTER), range.endInclusive)
        val empty = timelineRange(emptyList<com.tendril.app.domain.timeline.TimelineBar<R>>(), d)
        assertTrue(d in empty)
        assertEquals(TIMELINE_PAD_BEFORE + TIMELINE_PAD_AFTER + 1, java.time.temporal.ChronoUnit.DAYS.between(empty.start, empty.endInclusive) + 1)
    }

    @Test
    fun `a drag moves start and end together by whole days`() {
        val bar = timelineBars(listOf(R("a", d, d.plusDays(2))), { it.start }, { it.end }).single()
        val moved = bar.shifted(-3)
        assertEquals(d.minusDays(3), moved.start)
        assertEquals(d.minusDays(1), moved.end)
        assertEquals(bar.days, moved.days)
    }

    @Test
    fun `blocked by resolves rows, ignores unknown uids, and is open only while a blocker is not done`() {
        val at = Instant.EPOCH
        val a = Page(id = 1, uid = "a", title = "A", kind = PageKind.PAGE, createdAt = at, updatedAt = at)
        val b = Page(id = 2, uid = "b", title = "B", kind = PageKind.PAGE, createdAt = at, updatedAt = at)
        val byUid = mapOf("a" to a, "b" to b)
        val state = blockedBy("a,b,ghost", { byUid[it] }) { it.id == 1L }
        assertEquals(listOf("A", "B"), state.blockers.map { it.title })
        assertEquals(listOf("B"), state.open.map { it.title })
        assertTrue(state.isBlocked)
        assertFalse("every blocker done: not blocked", blockedBy("a", { byUid[it] }) { true }.isBlocked)
        assertTrue("no Done binding: every blocker counts", blockedBy("a", { byUid[it] }) { false }.isBlocked)
        assertFalse("nothing resolvable: not blocked", blockedBy("ghost", { byUid[it] }) { false }.isBlocked)
    }
}
