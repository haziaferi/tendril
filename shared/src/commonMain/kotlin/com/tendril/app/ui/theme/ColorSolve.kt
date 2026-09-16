package com.tendril.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * B§13.7.3 rule 1 (14g·1, 2026-09-16) — the arithmetic every theme token is *solved* with, on
 * plain sRGB ints so a unit test can walk every register without the graphics stack. WCAG's
 * relative luminance and contrast ratio, verbatim; a linear sRGB mix (the mock's `mixHex` — an
 * OKLab mix would be a refinement, not a floor, and the floors do the work); and the three
 * solvers: [solveTo] for the greys, [solveText] for the text band, [solveHue] for a hue.
 */
data class Srgb(val r: Int, val g: Int, val b: Int) {
    fun toColor(): Color = Color(r, g, b)
    fun toHex(): String = "#" + listOf(r, g, b).joinToString("") { it.toString(16).padStart(2, '0').uppercase() }

    companion object {
        /** A `#RRGGBB` literal — the only form the registers are written in. */
        fun hex(value: String): Srgb {
            val v = value.removePrefix("#").toInt(16)
            return Srgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        }
        val WHITE = Srgb(255, 255, 255)
    }
}

fun Color.toSrgb(): Srgb = Srgb((red * 255f).roundToInt(), (green * 255f).roundToInt(), (blue * 255f).roundToInt())

fun relativeLuminance(c: Srgb): Double {
    fun channel(v: Int): Double {
        val x = v / 255.0
        return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(c.r) + 0.7152 * channel(c.g) + 0.0722 * channel(c.b)
}

/** WCAG 2.x contrast, symmetric, 1.0…21.0. */
fun contrast(a: Srgb, b: Srgb): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** [share] of [a] over [b], 0.0…1.0, per channel in sRGB. */
fun mix(a: Srgb, b: Srgb, share: Double): Srgb {
    fun ch(x: Int, y: Int) = (x * share + y * (1 - share)).roundToInt().coerceIn(0, 255)
    return Srgb(ch(a.r, b.r), ch(a.g, b.g), ch(a.b, b.b))
}

/**
 * The smallest share of [fg] mixed over [bg] whose contrast clears [floor] against *every*
 * colour in [against] — the ground and `surface2` both, so a dim label reads on a hovered row
 * as well as on the page (`docs/critiques/registers-mock.md` #2: solved on the ground alone,
 * dim landed at 4.28–4.51 on `surface2`). Falls back to [fg] itself when no share clears.
 */
fun solveTo(fg: Srgb, bg: Srgb, floor: Double, against: List<Srgb> = listOf(bg)): Srgb {
    for (p in 0..100) {
        val m = mix(fg, bg, p / 100.0)
        if (against.all { contrast(m, it) >= floor }) return m
    }
    return fg
}

/**
 * B§13.7.3 rule 2 — body text in the 9.5–13:1 band, not maximal: the [seed] (a ground's own
 * near-black or near-white) is pulled toward [bg] until it reads at or under [target], the
 * band's upper third. A seed already inside the band is returned as it is; the [floor] is the
 * band's bottom and cannot be crossed by construction (the walk stops at the first step under
 * [target], never more than one step past it).
 */
fun solveText(seed: Srgb, bg: Srgb, target: Double = TEXT_TARGET, floor: Double = TEXT_FLOOR): Srgb {
    if (contrast(seed, bg) <= target) return seed
    for (p in 100 downTo 0) {
        val m = mix(seed, bg, p / 100.0)
        if (contrast(m, bg) <= target) return if (contrast(m, bg) >= floor) m else seed
    }
    return seed
}

/**
 * A hue solved to [floor] on [bg] by *lightness only* — darkened on a light ground, lightened
 * on a dark one — so Clay stays clay rather than the taupe a mix toward the text produces.
 * A hue already over the floor is left where its register put it.
 */
fun solveHue(hue: Srgb, bg: Srgb, floor: Double, lighten: Boolean): Srgb {
    if (contrast(hue, bg) >= floor) return hue
    val (h, s, l) = hue.toHsl()
    var last = hue
    for (i in 1..100) {
        val lightness = if (lighten) l + (1 - l) * i / 100.0 else l * (1 - i / 100.0)
        last = hslToSrgb(h, s, lightness)
        if (contrast(last, bg) >= floor) return last
    }
    return last
}

/** B§13.7.3 rule 3 — the OLED ground: the register's dark ground at 4 % lightness, hue kept. */
fun oledGround(bg: Srgb): Srgb {
    val (h, s, _) = bg.toHsl()
    return hslToSrgb(h, s, OLED_LIGHTNESS)
}

const val TEXT_TARGET = 12.0
const val TEXT_FLOOR = 9.5
const val OLED_LIGHTNESS = 0.04

/** Hue in degrees, saturation and lightness 0…1 — the CSS definition. */
fun Srgb.toHsl(): Triple<Double, Double, Double> {
    val r = this.r / 255.0
    val g = this.g / 255.0
    val b = this.b / 255.0
    val hi = maxOf(r, g, b)
    val lo = minOf(r, g, b)
    val l = (hi + lo) / 2
    if (hi == lo) return Triple(0.0, 0.0, l)
    val d = hi - lo
    val s = if (l > 0.5) d / (2 - hi - lo) else d / (hi + lo)
    var h = when (hi) {
        r -> (g - b) / d + (if (g < b) 6 else 0)
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    h *= 60
    return Triple(h, s, l)
}

fun hslToSrgb(h: Double, s: Double, l: Double): Srgb {
    fun f(n: Int): Double {
        val k = (n + h / 30) % 12
        val a = s * min(l, 1 - l)
        return l - a * max(-1.0, min(k - 3, min(9 - k, 1.0)))
    }
    fun ch(x: Double) = (x * 255).roundToInt().coerceIn(0, 255)
    return Srgb(ch(f(0)), ch(f(8)), ch(f(4)))
}
