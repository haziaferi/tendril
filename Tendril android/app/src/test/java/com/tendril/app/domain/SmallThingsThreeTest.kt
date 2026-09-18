package com.tendril.app.domain

import com.tendril.app.domain.journal.displayTitle
import com.tendril.app.domain.reminders.reminderFiresAt
import com.tendril.app.domain.timeline.timelineBars
import com.tendril.app.domain.timeline.timelineInitialColumn
import com.tendril.app.domain.timeline.timelineRange
import com.tendril.app.ui.components.submenuSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Small things III (2026-09-18) — the four pure rules behind L11, L12, L13 and L14/F12. */
class SmallThingsThreeTest {

    private val d = LocalDate.of(2026, 9, 18)
    private data class R(val name: String, val start: LocalDate?, val end: LocalDate? = null)

    @Test
    fun `L11 a submenu flips left only when it would cross the window's right edge`() {
        assertFalse(submenuSide(itemRightPx = 300f, submenuWidthPx = 220f, windowWidthPx = 967f))
        assertTrue(submenuSide(itemRightPx = 800f, submenuWidthPx = 220f, windowWidthPx = 967f))
        assertFalse("an unmeasured window never flips", submenuSide(800f, 220f, 0f))
    }

    @Test
    fun `L12 a Journal day's storage title reads as its date, any other title as itself`() {
        assertEquals("13 Sep 2026", displayTitle("journal/2026-09-13"))
        assertEquals("1 Oct 2026", displayTitle(" journal/2026-10-01 "))
        assertEquals("journal/2026-13-40", displayTitle("journal/2026-13-40"))
        assertEquals("Call the library", displayTitle("Call the library"))
        assertEquals("journal notes", displayTitle("journal notes"))
    }

    @Test
    fun `L13 the Timeline opens with today three columns in and never past the first bar`() {
        val bars = timelineBars(listOf(R("a", d.minusDays(10), d.minusDays(8))), { it.start }, { it.end })
        val range = timelineRange(bars, d)
        // The range starts TIMELINE_PAD_BEFORE days before the first bar; that bar's column caps the scroll.
        val firstBar = java.time.temporal.ChronoUnit.DAYS.between(range.start, d.minusDays(10))
        assertEquals("a bar ten days back wins over today − 3, with a day of margin", firstBar - 1, timelineInitialColumn(range, bars, d))
        val late = timelineBars(listOf(R("b", d.plusDays(10))), { it.start }, { it.end })
        val lateRange = timelineRange(late, d)
        val todayColumn = java.time.temporal.ChronoUnit.DAYS.between(lateRange.start, d)
        assertEquals(todayColumn - 3, timelineInitialColumn(lateRange, late, d))
        assertEquals("never negative", 0L, timelineInitialColumn(d..d.plusDays(5), emptyList<com.tendril.app.domain.timeline.TimelineBar<R>>(), d))
    }

    @Test
    fun `L14 a preset names the moment it fires — the start's time, the anchor, else midnight`() {
        val hour = Duration.ofHours(1)
        assertEquals(LocalDateTime.of(2026, 9, 18, 13, 30), reminderFiresAt(d, LocalTime.of(14, 30), null, hour))
        assertEquals(LocalDateTime.of(2026, 9, 18, 8, 0), reminderFiresAt(d, null, LocalTime.of(9, 0), hour))
        assertEquals(LocalDateTime.of(2026, 9, 17, 23, 0), reminderFiresAt(d, null, null, hour))
        assertNull(reminderFiresAt(null, LocalTime.of(9, 0), null, hour))
    }
}
