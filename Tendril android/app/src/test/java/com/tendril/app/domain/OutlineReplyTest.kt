package com.tendril.app.domain

import com.tendril.app.domain.ai.AiVerb
import com.tendril.app.domain.ai.OutlineRow
import com.tendril.app.domain.ai.isText
import com.tendril.app.domain.ai.parseOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** §0.6.15's fourth verb (2026-09-22) — a *Mind map* reply is a nested list; the parse is the model. */
class OutlineReplyTest {

    @Test
    fun `a two-space list parses to depths, the root first`() {
        val rows = parseOutline("- Compost\n  - What goes in\n    - Kitchen scraps\n    - No meat\n  - Care\n")
        assertEquals(listOf(0, 1, 2, 2, 1), rows.map { it.depth })
        assertEquals(listOf("Compost", "What goes in", "Kitchen scraps", "No meat", "Care"), rows.map { it.text })
    }

    @Test
    fun `markers, tabs and four-space steps are all read`() {
        assertEquals(listOf(0, 1, 2), parseOutline("* Root\n\t* Child\n\t\t* Grandchild").map { it.depth })
        assertEquals(listOf(0, 1, 1), parseOutline("1. Root\n    2) One\n    3) Two").map { it.depth })
        assertEquals(listOf(0, 1), parseOutline("• Root\n  • Child").map { it.depth })
    }

    @Test
    fun `a preamble before the list is dropped, a trailing colon trimmed, blank lines skipped`() {
        val rows = parseOutline("Here is the map:\n\n- Compost:\n\n  - Care\n")
        assertEquals(listOf(OutlineRow(0, "Compost"), OutlineRow(1, "Care")), rows)
    }

    @Test
    fun `a jump of two levels lands one under its parent, and an indented first line is the root`() {
        assertEquals(listOf(0, 1, 1), parseOutline("- Root\n      - Deep\n      - Deep two").map { it.depth })
        assertEquals(listOf(0, 1), parseOutline("  - Root\n    - Child").map { it.depth })
    }

    @Test
    fun `no list at all is no rows, and plain lines are rows`() {
        assertTrue(parseOutline("").isEmpty())
        assertTrue(parseOutline("\n  \n").isEmpty())
        assertEquals(listOf(OutlineRow(0, "Root"), OutlineRow(1, "Child")), parseOutline("Root\n  Child"))
    }

    @Test
    fun `the fourth verb asks for a list and is the one non-text verb`() {
        assertTrue(AiVerb.MIND_MAP.instruction.contains("nested Markdown list"))
        assertFalse(AiVerb.MIND_MAP.isText)
        assertTrue(AiVerb.entries.filter { it != AiVerb.MIND_MAP }.all { it.isText })
    }
}
