package com.tendril.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 14h·2 — six sizes a step apart; the audit's allow-list is the same six (plus 24) by hand. */
class TypeScaleTest {
    @Test
    fun `adjacent sizes are at least a 1-11 step apart and the list is ascending`() {
        val sizes = TypeScale.SIZES
        assertEquals(sizes, sizes.sorted())
        sizes.zipWithNext().forEach { (a, b) -> assertTrue("$a → $b", b / a >= 1.09f) }
        assertEquals(listOf(11f, 12.5f, 14f, 16f, 18f, 22f, 24f), sizes)
    }

    @Test
    fun `every size has a line height above it`() {
        TypeScale.SIZES.forEach { assertTrue(TypeScale.lineHeightFor(it) > it) }
    }
}
