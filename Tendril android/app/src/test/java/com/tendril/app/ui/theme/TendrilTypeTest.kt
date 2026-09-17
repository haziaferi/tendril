package com.tendril.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The audit's fixes (2026-09-17): the seven styles are aliases of Material's roles, so renaming
 * the 196 raw-role sites onto them moved no pixel — this pins the aliases so the sweep stays a
 * rename. A style's size and weight are `TypeScaleTest`'s and `typographyFor`'s; here only the identities.
 */
class TendrilTypeTest {
    // `typographyFor` is composable (it loads fonts); the aliases are extension getters over any
    // Typography, so Material's default instance proves the identities.
    private val t = androidx.compose.material3.Typography()

    @Test
    fun `each chrome style is exactly its Material role`() {
        assertEquals(t.titleLarge, t.pageTitle)
        assertEquals(t.titleMedium, t.heading)
        assertEquals(t.bodyMedium, t.body)
        assertEquals(t.labelLarge, t.label)
        assertEquals(t.bodySmall, t.description)
        assertEquals(t.labelMedium, t.caption)
        assertEquals(t.labelSmall, t.eyebrow)
    }

    @Test
    fun `the tabular derivation changes only the figures`() {
        assertEquals(t.body.fontSize, t.clock.fontSize)
        assertEquals(t.caption.fontSize, t.clockSmall.fontSize)
        assertEquals("tnum", t.clock.fontFeatureSettings)
        assertNotEquals(t.body, t.clock)
    }
}
