package com.tendril.app.domain.colour

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * S13 (B§13.8.2) — a **hue** is what Tendril stores (an integer on the wheel, 0–359, on a database
 * or a canvas node); a *colour* is what the register makes of it on a ground (`ui/theme/DataColours.kt`).
 * This file is the pure half: the wheel's arithmetic, Obsidian's six presets, a database's default.
 */

fun normalHue(hue: Int): Int = ((hue % 360) + 360) % 360

/** The shorter way round the wheel between two hues. */
fun hueDistance(a: Int, b: Int): Int = abs(normalHue(a) - normalHue(b)).let { min(it, 360 - it) }

/** Obsidian's six canvas presets on the wheel (JSON Canvas `"1"`–`"6"`): red, orange, yellow, green, cyan, purple. */
val HUE_PRESETS: List<Int> = listOf(0, 30, 55, 130, 185, 275)

/** The preset a hue stands for, within ±10°, else null — the JSON Canvas export's choice between `"n"` and a hex. */
fun huePreset(hue: Int): Int? {
    HUE_PRESETS.forEachIndexed { i, p -> if (hueDistance(hue, p) <= 10) return i + 1 }
    return null
}

/** The wheel's own saturation and lightness for a hue written to a file (no ground to solve on). */
const val HUE_FILE_SATURATION = 0.55
const val HUE_FILE_LIGHTNESS = 0.5

/** A hue as `#RRGGBB` at the wheel's own lightness — JSON Canvas's custom colour. */
fun hueHex(hue: Int): String {
    val h = normalHue(hue) / 360.0; val s = HUE_FILE_SATURATION; val l = HUE_FILE_LIGHTNESS
    fun channel(t0: Double): Int {
        var t = t0; if (t < 0) t += 1.0; if (t > 1) t -= 1.0
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val v = when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < 0.5 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
        return (v * 255).roundToInt().coerceIn(0, 255)
    }
    return "#%02X%02X%02X".format(channel(h + 1.0 / 3), channel(h), channel(h - 1.0 / 3))
}

/** JSON Canvas's `color`: a preset's number where the hue is one of the six, else its hex. */
fun jsonCanvasColour(hue: Int): String = huePreset(hue)?.toString() ?: hueHex(hue)

/**
 * B§13.8.2 — a database's default hue: hashed from its title over the whole circle, kept ≥ 30° from
 * the accent's hue (the reserved one) by moving into the band 30–60° past it, still by the hash.
 * The same title gives the same hue on every device and ground; a renamed database changes colour
 * unless one was chosen.
 */
fun defaultDatabaseHue(title: String, accentHue: Int): Int {
    var h = 0
    for (c in title.trim().lowercase()) h = (h * 31 + c.code) and 0x7fffffff
    val hue = h % 360
    val a = normalHue(accentHue)
    return if (hueDistance(hue, a) < 30) normalHue(a + 30 + hue % 30) else hue
}
