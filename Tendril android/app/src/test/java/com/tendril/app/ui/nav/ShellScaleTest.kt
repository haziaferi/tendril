package com.tendril.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** B§13.5 #4 — the one number every measurement is multiplied by. */
class ShellScaleTest {
    private val eps = 1e-4f

    @Test
    fun `a phone is the floor times Touch`() {
        assertEquals(0.85f * 1.23f, shellScaleFor(360f, DensityProfile.TOUCH), eps)   // 360 ÷ 800 = 0.45 → clamped to 0.85
        assertEquals(0.85f * 1.23f, shellScaleFor(411f, DensityProfile.TOUCH), eps)
    }

    @Test
    fun `an 800 dp side scores one before the profile`() {
        assertEquals(0.85f, shellScaleFor(800f, DensityProfile.COMPACT), eps)
        assertEquals(0.95f, shellScaleFor(800f, DensityProfile.COMFORTABLE), eps)
        assertEquals(1.23f, shellScaleFor(800f, DensityProfile.TOUCH), eps)
    }

    @Test
    fun `the screen factor is capped at 1_25 however tall the window`() {
        assertEquals(1.25f * 0.85f, shellScaleFor(1000f, DensityProfile.COMPACT), eps)
        assertEquals(1.25f * 0.85f, shellScaleFor(4000f, DensityProfile.COMPACT), eps)
    }

    @Test
    fun `a side that is not a size is neutral, never a crash`() {
        assertEquals(0.85f, shellScaleFor(Float.NaN, DensityProfile.COMPACT), eps)
        assertEquals(0.85f, shellScaleFor(0f, DensityProfile.COMPACT), eps)
        assertEquals(1.23f, shellScaleFor(Float.POSITIVE_INFINITY, DensityProfile.TOUCH), eps)
    }

    @Test
    fun `the stored key round-trips and garbage is Compact`() {
        for (p in DensityProfile.entries) assertEquals(p, DensityProfile.fromKey(p.key))
        assertEquals(DensityProfile.COMPACT, DensityProfile.fromKey(null))
        assertEquals(DensityProfile.COMPACT, DensityProfile.fromKey("huge"))
    }
}
