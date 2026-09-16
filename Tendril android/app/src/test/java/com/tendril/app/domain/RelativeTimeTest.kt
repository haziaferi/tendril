package com.tendril.app.domain

import com.tendril.app.domain.time.relativeTime
import com.tendril.app.domain.time.rowsLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** 14h·2 — the phone card's *edited …* line and the History sheet share one clock. */
class RelativeTimeTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-09-16T10:00:00Z")
    private fun at(iso: String) = relativeTime(Instant.parse(iso), now, zone)

    @Test
    fun `minutes and hours today`() {
        assertEquals("just now", at("2026-09-16T09:59:30Z"))
        assertEquals("5 min ago", at("2026-09-16T09:55:00Z"))
        assertEquals("3 h ago", at("2026-09-16T07:00:00Z"))
    }

    @Test
    fun `yesterday is the calendar's, not 24 hours`() {
        assertEquals("yesterday", at("2026-09-15T23:30:00Z"))     // 10.5 h ago, but yesterday
        assertEquals("yesterday", at("2026-09-15T01:00:00Z"))
        assertEquals("3 days ago", at("2026-09-13T12:00:00Z"))
        assertEquals("2026-09-08", at("2026-09-08T12:00:00Z"))
    }

    @Test
    fun `rows are counted in words`() {
        assertEquals("1 row", rowsLabel(1))
        assertEquals("2 rows", rowsLabel(2))
        assertEquals("0 rows", rowsLabel(0))
    }
}
