package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.pagedatabase.PropertyType
import com.tendril.app.data.pagedatabase.ViewType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The audit's fixes (2026-09-17, T2): the words a person reads for a count, a cadence, a unit and a kind. */
class WordsTest {
    @Test
    fun `a count reads as one or many`() {
        assertEquals("1 block", plural(1, "block"))
        assertEquals("3 blocks", plural(3, "block"))
        assertEquals("0 hops", plural(0, "hop"))
    }

    @Test
    fun `a cadence names its unit and never a bracket`() {
        assertEquals("Every day", HabitFrequency(1, IntervalUnit.DAY).label())
        assertEquals("Every 3 days", HabitFrequency(3, IntervalUnit.DAY).label())
        assertEquals("Every week", HabitFrequency(1, IntervalUnit.WEEK).label())
        assertEquals("Every 2 months", HabitFrequency(2, IntervalUnit.MONTH).label())
        assertEquals("days", IntervalUnit.DAY.word(2))
    }

    @Test
    fun `a kind is named, not spelled from its enum`() {
        assertEquals(listOf("Table", "Board", "Gallery", "Calendar", "Timeline"), ViewType.entries.map { it.label })
        assertEquals("Multi-select", PropertyType.MULTI_SELECT.label)
        assertEquals("Formula", PropertyType.COMPUTED.label)
        ViewType.entries.forEach { check(it.blurb.endsWith(".")) { it.name } }
    }
}
