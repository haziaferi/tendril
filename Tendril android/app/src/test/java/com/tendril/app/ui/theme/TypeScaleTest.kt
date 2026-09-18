package com.tendril.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** 14h·2 — sizes a step apart; the type PR: 11 · 12.5 · 14 · 16 · 18 for the chrome, 20 and 24 for the editor's H2 and H1. */
class TypeScaleTest {
    @Test
    fun `adjacent sizes are at least a 1-11 step apart and the list is ascending`() {
        val sizes = TypeScale.SIZES
        assertEquals(sizes, sizes.sorted())
        sizes.zipWithNext().forEach { (a, b) -> assertTrue("$a → $b", b / a >= 1.09f) }
        assertEquals(listOf(11f, 12.5f, 14f, 16f, 18f, 20f, 24f), sizes)
    }

    @Test
    fun `every size has a line height above it`() {
        TypeScale.SIZES.forEach { assertTrue(TypeScale.lineHeightFor(it) > it) }
    }

    /** T·P1 (the phone's second fix PR, 2026-09-18): under Touch each chrome role steps one size up the same scale; the editor's stay. */
    @Test
    fun `the Touch scale steps every chrome role up one size and leaves the editor's`() {
        fun at(size: Float) = TextStyle(fontSize = size.sp, lineHeight = TypeScale.lineHeightFor(size).sp)
        val base = Typography(
            titleLarge = at(18f), titleMedium = at(14f), titleSmall = at(12.5f), bodyLarge = at(16f), bodyMedium = at(14f), bodySmall = at(12.5f),
            labelLarge = at(12.5f), labelMedium = at(11f), labelSmall = at(11f), headlineMedium = at(20f), headlineSmall = at(24f),
        )
        val t = touchTypography(base)
        assertEquals(20f, t.titleLarge.fontSize.value)      // pageTitle
        assertEquals(16f, t.titleMedium.fontSize.value)     // heading
        assertEquals(16f, t.bodyMedium.fontSize.value)      // body
        assertEquals(14f, t.bodySmall.fontSize.value)       // description
        assertEquals(14f, t.labelLarge.fontSize.value)      // label
        assertEquals(12.5f, t.labelMedium.fontSize.value)   // caption
        assertEquals(12.5f, t.labelSmall.fontSize.value)    // eyebrow
        assertEquals(16f, t.bodyLarge.fontSize.value)       // the editor's body, unchanged
        assertEquals(20f, t.headlineMedium.fontSize.value); assertEquals(24f, t.headlineSmall.fontSize.value)
        listOf(t.titleLarge, t.titleMedium, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium).forEach {
            assertTrue("${it.fontSize} on the scale", it.fontSize.value in TypeScale.SIZES)
            assertTrue("line height above the size", it.lineHeight.value > it.fontSize.value)
        }
    }
}
