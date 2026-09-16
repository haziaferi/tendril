package com.tendril.app.domain

import com.tendril.app.data.page.Block
import com.tendril.app.data.page.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** 14h·2 — the mind-map fold as a pure function, and the session's exceptions to it. */
class MindMapFoldTest {
    private val t0 = Instant.parse("2026-09-16T00:00:00Z")
    private fun entry(id: Long, depth: Int, mindMap: Boolean = false) =
        OutlineBlock(Block(id = id, pageId = 1, type = BlockType.PARAGRAPH, order = id.toInt(), content = "b$id", createdAt = t0, updatedAt = t0, mindMap = mindMap), depth)

    // 1 (map) > 2 > 3 ; 4 ; 5 (map) > 6
    private val outline = listOf(entry(1, 0, mindMap = true), entry(2, 1), entry(3, 2), entry(4, 0), entry(5, 0, mindMap = true), entry(6, 1))

    @Test
    fun `a map folds its descendants and an opened map does not`() {
        assertEquals(setOf(2L, 3L, 6L), mappedDescendants(outline))
        assertEquals(setOf(6L), mappedDescendants(outline, opened = setOf(1L)))
        assertEquals(emptySet<Long>(), mappedDescendants(outline, opened = setOf(1L, 5L)))
    }

    @Test
    fun `the owning map is the folded ancestor, none when the block is on screen`() {
        assertEquals(1L, owningMap(outline, 3))
        assertEquals(5L, owningMap(outline, 6))
        assertNull(owningMap(outline, 4))
        assertNull(owningMap(outline, 3, opened = setOf(1L)))
        assertNull(owningMap(outline, 99))
    }
}
