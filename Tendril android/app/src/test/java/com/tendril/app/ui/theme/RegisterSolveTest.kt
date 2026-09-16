package com.tendril.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 14g·1 — the AA claim of §2.3 as a property of the code: every register in both modes, and
 * every dark one again on the OLED ground, clears each floor `paletteFor` promises. The
 * numbers are B§13.7.1/13.7.3 with `docs/critiques/registers-mock.md`'s two amendments
 * (text solved into its band; dim and faint solved against `surface2` too).
 */
class RegisterSolveTest {

    private data class Case(val register: Register, val dark: Boolean, val oled: Boolean) {
        val name get() = "${register.key} ${if (dark) "dark" else "light"}${if (oled) " oled" else ""}"
    }

    private val cases = Register.ALL.flatMap { r ->
        listOf(Case(r, dark = false, oled = false), Case(r, dark = true, oled = false), Case(r, dark = true, oled = true))
    }

    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color) = contrast(a.toSrgb(), b.toSrgb())

    @Test
    fun `text sits in the eye pass band on every register`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            val onBg = ratio(p.text, p.bg)
            assertTrue("${c.name}: text ${"%.2f".format(onBg)} on bg", onBg in TEXT_FLOOR..13.0)
            assertTrue("${c.name}: text on surface2", ratio(p.text, p.surface2) >= TEXT_FLOOR)
        }
    }

    @Test
    fun `dim and faint clear their floors on the ground and on surface2`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            listOf(p.bg, p.surface2).forEach { ground ->
                assertTrue("${c.name}: dim", ratio(p.textDim, ground) >= Floors.DIM)
                assertTrue("${c.name}: faint", ratio(p.textFaint, ground) >= Floors.FAINT)
            }
        }
    }

    @Test
    fun `the accent and what sits on it clear their floors`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            val floor = if (c.dark) Floors.ACCENT_DARK else Floors.ACCENT_LIGHT
            assertTrue("${c.name}: accent ${"%.2f".format(ratio(p.accent, p.bg))}", ratio(p.accent, p.bg) >= floor - 0.01)
            assertTrue("${c.name}: on-accent", ratio(p.onAccent, p.accent) >= Floors.ON_ACCENT)
            assertTrue("${c.name}: soft text on soft", ratio(p.accentSoftText, p.accentSoft) >= Floors.SOFT_TEXT)
            assertTrue("${c.name}: strong", ratio(p.accentStrong, p.bg) >= Floors.DIM)
        }
    }

    @Test
    fun `a second channel is a mark at three to one, and the third hue always is`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            assertTrue("${c.name}: third ${"%.2f".format(ratio(p.third, p.bg))}", ratio(p.third, p.bg) >= Floors.MARK)
        }
        // Swiss light's signal yellow measured 2.6:1 on the mock (registers-mock.md #3); solved, it is a mark.
        val swiss = paletteFor(Register.SWISS, dark = false)
        assertTrue(ratio(swiss.third, swiss.bg) >= Floors.MARK)
    }

    private fun hueDistance(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val d = abs(a.toSrgb().toHsl().first - b.toSrgb().toHsl().first) % 360
        return minOf(d, 360 - d)
    }

    /** 14g·2 — the fan (B§13.8.3, `coloured-elements-mock.md` #3): three data hues by one rule. */
    @Test
    fun `event, habit and the third hue sit at least sixty degrees from the accent and from each other`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            // 58, not 60: an 8-bit sRGB round trip moves a hue by a degree or two.
            assertTrue("${c.name}: event ${hueDistance(p.event, p.accent)}", hueDistance(p.event, p.accent) >= 58)
            assertTrue("${c.name}: habit", hueDistance(p.habit, p.accent) >= 58)
            assertTrue("${c.name}: event vs habit", hueDistance(p.event, p.habit) >= 58)
            if (c.register.second == null) {
                assertTrue("${c.name}: third", hueDistance(p.third, p.accent) >= 58)
                assertTrue("${c.name}: third vs event", hueDistance(p.third, p.event) >= 58)
                assertTrue("${c.name}: third vs habit", hueDistance(p.third, p.habit) >= 58)
            }
        }
        // A second channel takes the third slot with its own hue, not the fan's.
        val swiss = paletteFor(Register.SWISS, dark = false)
        assertTrue(hueDistance(swiss.third, Register.SWISS.second!!.first.toColor()) <= 3)
    }

    @Test
    fun `the data hues, the error family and the marks' backgrounds clear their floors`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            assertTrue("${c.name}: event", ratio(p.event, p.bg) >= Floors.DATA)
            assertTrue("${c.name}: habit", ratio(p.habit, p.bg) >= Floors.DATA)
            assertTrue("${c.name}: third strong", ratio(p.thirdStrong, p.bg) >= Floors.DATA)
            assertTrue("${c.name}: on third", ratio(p.onThird, p.thirdStrong) >= Floors.ON_ACCENT)
            assertTrue("${c.name}: error on bg", ratio(p.error, p.bg) >= Floors.DATA)
            assertTrue("${c.name}: error on its soft", ratio(p.error, p.errorSoft) >= Floors.DATA)
            assertTrue("${c.name}: on error", ratio(p.onError, p.error) >= Floors.ON_ACCENT)
            listOf(p.findSoft, p.eventSoft, p.habitSoft, p.thirdSoft, p.errorSoft).forEach { soft ->
                assertTrue("${c.name}: text on a soft", ratio(p.text, soft) >= Floors.DATA)
            }
            assertNotEquals("${c.name}: the find mark is not the selection", p.findSoft, p.accentSoft)
        }
    }

    /** 14g·2 — a stored hue rendered by the register: the eight label hexes and the seven callout pastels. */
    @Test
    fun `every label reads on its chip and its ground, hue kept, and every callout is a mark with a readable tint`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            com.tendril.app.data.page.LabelColors.palette.forEach { hex ->
                val l = labelColours(hex, p)
                assertTrue("${c.name} $hex: hue on tint ${"%.2f".format(ratio(l.hue, l.tint))}", ratio(l.hue, l.tint) >= Floors.DATA)
                assertTrue("${c.name} $hex: hue on bg", ratio(l.hue, p.bg) >= Floors.DATA)
                assertTrue("${c.name} $hex: on hue", ratio(l.onHue, l.hue) >= Floors.ON_ACCENT)
                assertTrue("${c.name} $hex: hue kept", hueDistance(l.hue, Srgb.hex(hex).toColor()) <= 8)
            }
            CALLOUT_COLORS.forEach { hex ->
                val k = calloutColours(hex, p)
                assertTrue("${c.name} $hex: bar", ratio(k.bar, p.bg) >= Floors.MARK)
                assertTrue("${c.name} $hex: text on tint", ratio(p.text, k.tint) >= Floors.DATA)
            }
            // The grey pastel stays grey: saturation is never raised for a stored hue.
            assertTrue("${c.name}: grey callout", calloutColours("#E5E7EB", p).bar.toSrgb().toHsl().second <= 0.15)
        }
        // Memoised: the same object comes back for the same hex on the same ground.
        val ink = paletteFor(Register.INK, dark = false)
        assertSame(labelColours("#A9708D", ink), labelColours("#A9708D", ink))
    }

    @Test
    fun `solving a hue keeps its hue`() {
        // Clay's light accent is 3.9:1 raw; solved by lightness, it is still clay — within a few degrees.
        val raw = Register.CLAY.lightHue.toHsl().first
        val solved = paletteFor(Register.CLAY, dark = false).accent.toSrgb().toHsl().first
        assertTrue("clay hue $raw → $solved", abs(raw - solved) < 4.0)
        // Ink's dark accent measured 5.4 where the model asks 7.6 — re-solved, not the exception.
        val ink = paletteFor(Register.INK, dark = true)
        assertTrue(ratio(ink.accent, ink.bg) >= Floors.ACCENT_DARK - 0.01)
    }

    @Test
    fun `the ground's raw seed is never the text`() {
        // 17:1 black-on-white is the fatigue case (B§13.7.3 rule 2): every seed is over the band and gets pulled.
        Register.ALL.forEach { r ->
            assertNotEquals(r.key, r.ground.lightTextSeed, paletteFor(r, dark = false).text.toSrgb())
            assertNotEquals(r.key, r.ground.darkTextSeed, paletteFor(r, dark = true).text.toSrgb())
        }
    }

    @Test
    fun `OLED drops the dark ground to four percent and light ignores it`() {
        Register.ALL.forEach { r ->
            val oled = paletteFor(r, dark = true, oled = true)
            assertTrue(r.key, oled.bg.toSrgb().toHsl().third <= 0.05)
            assertEquals(r.key, paletteFor(r, dark = false), paletteFor(r, dark = false, oled = true))
            assertNotEquals(r.key, paletteFor(r, dark = true).bg, oled.bg)
        }
    }

    @Test
    fun `the register keys are the stored names, old enum names included`() {
        assertEquals(10, Register.ALL.map { it.key }.toSet().size)
        assertSame(Register.CLAY, Register.fromKey("clay"))
        assertSame(Register.CLAY, Register.fromKey("CLAY"))
        assertSame(Register.INK, Register.fromKey(null))
        assertSame(Register.INK, Register.fromKey("cocoa"))
        assertEquals(TendrilMode.DARK, TendrilMode.fromKey("DARK"))
        assertEquals(TendrilMode.SYSTEM, TendrilMode.fromKey(null))
        assertEquals(TendrilTypeface.SERIF, TendrilTypeface.fromKey("Serif"))
    }
}
