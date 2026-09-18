package com.tendril.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    // The design layer (2026-09-18, `desktop-design-layer.md` #4): the lifted ground must be told
    // apart from the ground — at 4 % the light hover measured CIEDE2000 1.6, under the 2.0 JND.
    @Test
    fun `surface2 is a noticeable step from the ground in every palette`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            val de = deltaE(p.surface2.toSrgb(), p.bg.toSrgb())
            assertTrue("${c.name}: surface2 ΔE ${"%.2f".format(de)}", de >= 2.0)
        }
    }

    @Test
    fun `dim and faint clear their floors on the ground, on surface2 and on the selection's tint`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            listOf(p.bg, p.surface2, p.accentSoft).forEach { ground ->
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
        }
    }

    @Test
    fun `a second channel and the third hue read as data at four point six`() {
        // One token since the design layer (#6): `third` is solved to DATA, where the old
        // `thirdStrong` was — the fan cleared 4.6 in 28 of 30 palettes anyway.
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            assertTrue("${c.name}: third ${"%.2f".format(ratio(p.third, p.bg))}", ratio(p.third, p.bg) >= Floors.DATA)
        }
        // Swiss light's signal yellow measured 2.6:1 on the mock (registers-mock.md #3); solved, it reads.
        val swiss = paletteFor(Register.SWISS, dark = false)
        assertTrue(ratio(swiss.third, swiss.bg) >= Floors.DATA)
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
            assertTrue("${c.name}: on third", ratio(p.onThird, p.third) >= Floors.ON_ACCENT)
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

    /** 14g·3 (B§13.8.1) — the ladder's four steps at their targets on every ground, adjacent steps apart. */
    @Test
    fun `the urgency ladder sits at its targets, deepens step by step and keeps its steps apart`() {
        cases.forEach { c ->
            val p = paletteFor(c.register, c.dark, c.oled)
            val targets = (if (c.dark) Ladder.DARK else Ladder.LIGHT).map { it.target }
            assertEquals(4, p.ladder.size)
            p.ladder.forEachIndexed { i, step ->
                val r = ratio(step, p.bg)
                assertTrue("${c.name}: step $i ${"%.2f".format(r)} vs ${targets[i]}", abs(r - targets[i]) <= 0.3)
            }
            // Lightness falls every step — light darkens toward urgent, dark runs pale → saturated.
            val lightness = p.ladder.map { it.toSrgb().toHsl().third }
            lightness.zipWithNext().forEach { (a, b) -> assertTrue("${c.name}: monotone", b < a) }
            p.ladder.zipWithNext().forEach { (a, b) -> assertTrue("${c.name}: adjacent ${deltaE(a.toSrgb(), b.toSrgb())}", deltaE(a.toSrgb(), b.toSrgb()) >= 10.0) }
            assertNull(p.urgencyColour(0))
            assertEquals(p.ladder[3], p.urgencyColour(4))
        }
    }

    /** CIEDE2000 on sRGB ints — the test's own yardstick for "told apart", as the mocks measured. */
    private fun deltaE(c1: Srgb, c2: Srgb): Double {
        fun lab(c: Srgb): Triple<Double, Double, Double> {
            fun lin(v: Int): Double { val x = v / 255.0; return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
            val r = lin(c.r); val g = lin(c.g); val b = lin(c.b)
            val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
            val y = (r * 0.2126 + g * 0.7152 + b * 0.0722)
            val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883
            fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
            return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
        }
        val (l1, a1, b1) = lab(c1); val (l2, a2, b2) = lab(c2)
        val cm = (Math.hypot(a1, b1) + Math.hypot(a2, b2)) / 2
        val gg = 0.5 * (1 - Math.sqrt(Math.pow(cm, 7.0) / (Math.pow(cm, 7.0) + Math.pow(25.0, 7.0))))
        val a1p = (1 + gg) * a1; val a2p = (1 + gg) * a2
        val c1p = Math.hypot(a1p, b1); val c2p = Math.hypot(a2p, b2)
        fun hp(a: Double, b: Double): Double { if (a == 0.0 && b == 0.0) return 0.0; val h = Math.toDegrees(Math.atan2(b, a)); return if (h < 0) h + 360 else h }
        val h1p = hp(a1p, b1); val h2p = hp(a2p, b2)
        val dlp = l2 - l1; val dcp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180 -> h2p - h1p
            h2p - h1p > 180 -> h2p - h1p - 360
            else -> h2p - h1p + 360
        }
        val dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dhp / 2))
        val lm = (l1 + l2) / 2; val cmp = (c1p + c2p) / 2
        val hm = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * Math.cos(Math.toRadians(hm - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hm)) + 0.32 * Math.cos(Math.toRadians(3 * hm + 6)) - 0.20 * Math.cos(Math.toRadians(4 * hm - 63))
        val dth = 30 * Math.exp(-Math.pow((hm - 275) / 25, 2.0))
        val rc = 2 * Math.sqrt(Math.pow(cmp, 7.0) / (Math.pow(cmp, 7.0) + Math.pow(25.0, 7.0)))
        val sl = 1 + 0.015 * Math.pow(lm - 50, 2.0) / Math.sqrt(20 + Math.pow(lm - 50, 2.0))
        val sc = 1 + 0.045 * cmp; val sh = 1 + 0.015 * cmp * t
        val rt = -Math.sin(Math.toRadians(2 * dth)) * rc
        return Math.sqrt(Math.pow(dlp / sl, 2.0) + Math.pow(dcp / sc, 2.0) + Math.pow(dHp / sh, 2.0) + rt * (dcp / sc) * (dHp / sh))
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
