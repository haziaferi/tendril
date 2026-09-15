package com.tendril.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 14e (B§13.6 #8) — the type-ahead jump is a pure function of the titles and the buffer. */
class TypeAheadTest {
    private val titles = listOf("Books v12", "Call the library", "Escape test", "Journal", "Camping")

    @Test
    fun `a prefix finds the first title from the cursor on, case-insensitively`() {
        assertEquals(1, typeAheadTarget(titles, "ca", from = null))
        assertEquals(1, typeAheadTarget(titles, "CA", from = 0))
    }

    @Test
    fun `from a later cursor the search continues forward, then wraps`() {
        assertEquals(4, typeAheadTarget(titles, "ca", from = 2))
        assertEquals("a match at the cursor itself stays put", 4, typeAheadTarget(titles, "ca", from = 4))
        assertEquals("wraps to the top", 0, typeAheadTarget(titles, "b", from = 3))
    }

    @Test
    fun `no match keeps the cursor where it is`() {
        assertNull(typeAheadTarget(titles, "zz", from = 1))
    }

    @Test
    fun `an empty buffer or an empty list jumps nowhere`() {
        assertNull(typeAheadTarget(titles, "", from = 1))
        assertNull(typeAheadTarget(emptyList(), "a", from = null))
    }
}
