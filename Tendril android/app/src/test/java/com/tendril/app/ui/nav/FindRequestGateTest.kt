package com.tendril.app.ui.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The audit's fixes (2026-09-17, F1): the count a screen is born with is not an event; a bump is, once. */
class FindRequestGateTest {
    @Test
    fun `the initial count is not an event`() {
        val gate = FindRequestGate(3)
        assertFalse(gate.accept(3))
    }

    @Test
    fun `a bump is an event once`() {
        val gate = FindRequestGate(3)
        assertTrue(gate.accept(4))
        assertFalse(gate.accept(4))
        assertTrue(gate.accept(5))
    }

    @Test
    fun `a screen born after a bump sees nothing until the next one`() {
        val gate = FindRequestGate(7)
        assertFalse(gate.accept(7))
        assertTrue(gate.accept(8))
    }
}
