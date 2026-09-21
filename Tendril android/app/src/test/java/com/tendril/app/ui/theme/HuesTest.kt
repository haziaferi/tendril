package com.tendril.app.ui.theme

import com.tendril.app.domain.colour.HUE_PRESETS
import com.tendril.app.domain.colour.defaultDatabaseHue
import com.tendril.app.domain.colour.hueDistance
import com.tendril.app.domain.colour.hueHex
import com.tendril.app.domain.colour.huePreset
import com.tendril.app.domain.colour.jsonCanvasColour
import com.tendril.app.domain.colour.normalHue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S13 (B§13.8.2): a hue is what is stored; the register makes the colour — `domain/colour/Hues.kt`, `ui/theme/DataColours.kt`. */
class HuesTest {
    private val palettes = Register.ALL.flatMap { r -> listOf(paletteFor(r, dark = false, oled = false), paletteFor(r, dark = true, oled = false), paletteFor(r, dark = true, oled = true)) }
    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color) = contrast(a.toSrgb(), b.toSrgb())

    @Test
    fun `the wheel's arithmetic - normal, distance, the six presets within ten degrees, a hex, the JSON Canvas colour`() {
        assertEquals(350, normalHue(-10)); assertEquals(5, normalHue(365))
        assertEquals(20, hueDistance(350, 10))
        assertEquals(1, huePreset(0)); assertEquals(1, huePreset(355)); assertEquals(6, huePreset(280)); assertNull(huePreset(100))
        assertEquals("#C63939", hueHex(0))   // the wheel's red at s .55 / l .5
        assertEquals("1", jsonCanvasColour(5)); assertEquals(hueHex(100), jsonCanvasColour(100)); assertTrue(jsonCanvasColour(100).startsWith("#"))
    }

    @Test
    fun `a database's default hue is the title's and stays thirty degrees from the accent`() {
        val a = defaultDatabaseHue("Books v12", 210)
        assertEquals("the same title, the same hue", a, defaultDatabaseHue("Books v12", 210))
        for (title in listOf("Books v12", "Errands", "Beds board", "Reading log", "a", "b", "c", "Trip", "Garden", "x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9"))
            for (accent in listOf(0, 45, 120, 210, 300)) {
                val h = defaultDatabaseHue(title, accent)
                assertTrue("$title on $accent → $h", h in 0..359 && hueDistance(h, accent) >= 30)
            }
    }

    @Test
    fun `every preset solved on every register reads on the ground and on its own tint, and something reads on the solid hue`() {
        for (p in palettes) for (h in HUE_PRESETS + listOf(100, 160, 230, 320)) {
            val c = hueColours(h, p)
            assertTrue("hue $h on ${p.bg}", ratio(c.hue, p.bg) >= 4.6)
            assertTrue("hue $h on its tint", ratio(c.hue, c.tint) >= 4.6)
            assertTrue("text on the tint of $h", ratio(p.text, c.tint) >= 4.6)
            assertTrue("text on the frame tint of $h", ratio(p.text, c.frameTint) >= 4.6)
            assertTrue("on-hue for $h", ratio(c.onHue, c.hue) >= 4.6)
        }
    }
}
