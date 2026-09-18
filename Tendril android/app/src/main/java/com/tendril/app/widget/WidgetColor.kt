package com.tendril.app.widget

import androidx.compose.ui.graphics.Color
import com.tendril.app.ui.theme.Register
import com.tendril.app.ui.theme.paletteFor
import com.tendril.app.ui.theme.toHsl
import com.tendril.app.ui.theme.toSrgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

fun Color.toRgb(): Rgb = Rgb(
    Math.round(red * 255f).coerceIn(0, 255),
    Math.round(green * 255f).coerceIn(0, 255),
    Math.round(blue * 255f).coerceIn(0, 255),
)

/**
 * §8.3 — accent2's base HSL: the analogous (−30°) rotation off the register's *solved* accent
 * (14g·1 — derived from `paletteFor`, one rule for every register, where the four old themes
 * had a transcribed table). The eight old values were this rotation of the old accents to
 * within a few degrees; a widget placed before 14g re-resolves on its next refresh, and the
 * configure screen's audit (§8.4) still runs on whatever the rule yields.
 */
data class Accent2Base(val hue: Float, val sat: Float, val lightness: Float)

private val accent2Cache = HashMap<Pair<String, Boolean>, Accent2Base>()

fun accent2Base(register: Register, dark: Boolean): Accent2Base = synchronized(accent2Cache) {
    accent2Cache.getOrPut(register.key to dark) {
        val (h, s, l) = paletteFor(register, dark).accent.toSrgb().toHsl()
        Accent2Base((((h - 30.0) % 360.0 + 360.0) % 360.0).toFloat(), s.toFloat(), l.toFloat())
    }
}

/** §8.3 — "the mode's own verified-safe extreme" Shade=100% interpolates toward. */
private fun safeLightness(dark: Boolean): Float = if (dark) 0.88f else 0.12f

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun toHex(): String = "#%02X%02X%02X".format(r, g, b)
}

/** Direct port of the prototype's `hslToHex` — h in degrees (wraps), s/l in 0..1. */
fun hslToRgb(hueDeg: Float, saturation: Float, lightness: Float): Rgb {
    val h = (((hueDeg % 360f) + 360f) % 360f) / 360f
    val s = saturation
    val l = lightness

    fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    val (r, g, b) = if (s == 0f) {
        Triple(l, l, l)
    } else {
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        Triple(hue2rgb(p, q, h + 1f / 3f), hue2rgb(p, q, h), hue2rgb(p, q, h - 1f / 3f))
    }
    fun to255(v: Float) = (v * 255f).let { Math.round(it) }.coerceIn(0, 255)
    return Rgb(to255(r), to255(g), to255(b))
}

/**
 * §8.3 — `currentAccent2()`: Shade (0–100) interpolates lightness from the theme's own vivid
 * accent2 tone toward the mode's safe extreme; Hue (±90°) rotates around accent2's own base
 * hue. Default state (shade=0, hue=0) reproduces the original fixed accent2 color exactly.
 */
fun currentAccent2(register: Register, dark: Boolean, shade: Int, hueOffsetDeg: Int): Rgb {
    val base = accent2Base(register, dark)
    val safeL = safeLightness(dark)
    val l = base.lightness + (safeL - base.lightness) * (shade / 100f)
    val h = base.hue + hueOffsetDeg
    return hslToRgb(h, base.sat, l)
}

/** §8.2/§8.4 — WCAG relative luminance, direct port of the prototype's `relLum`. */
fun relativeLuminance(rgb: Rgb): Double {
    fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b)
}

fun contrastRatio(a: Rgb, b: Rgb): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/** Alpha-composites [fg] over [bg] at [alphaPct] (0–100) — port of the prototype's `compositeRgb`. */
fun compositeRgb(fg: Rgb, bg: Rgb, alphaPct: Int): Rgb {
    val a = alphaPct / 100f
    fun mix(f: Int, b: Int) = Math.round(a * f + (1f - a) * b).coerceIn(0, 255)
    return Rgb(mix(fg.r, bg.r), mix(fg.g, bg.g), mix(fg.b, bg.b))
}

private val WALLPAPER_WHITE = Rgb(255, 255, 255)
private val WALLPAPER_BLACK = Rgb(0, 0, 0)

/**
 * §8.2 — the adversarial pure-black/pure-white wallpaper bracket: computes [textRgb]'s worst
 * contrast against the widget background composited over each extreme, at [opacityPct].
 */
fun worstCaseContrast(textRgb: Rgb, widgetBg: Rgb, opacityPct: Int): Double {
    val overWhite = contrastRatio(textRgb, compositeRgb(widgetBg, WALLPAPER_WHITE, opacityPct))
    val overBlack = contrastRatio(textRgb, compositeRgb(widgetBg, WALLPAPER_BLACK, opacityPct))
    return min(overWhite, overBlack)
}

/** Role thresholds — port of the prototype's `ROLE_THRESHOLD`: accent2/text/textDim
 * are legitimate small-text uses (4.5:1); textFaint and accent are only ever used at large-text
 * sizes in these widgets (the date number, single-letter weekday headers) — 3:1 applies. */
enum class ContrastRole(val threshold: Double) {
    TEXT(4.5), TEXT_DIM(4.5), TEXT_FAINT(3.0), ACCENT(3.0), ACCENT2(4.5),
}

data class ContrastResult(val role: ContrastRole, val ratio: Double, val pass: Boolean)

/** §8.4 — the live contrast-audit readout, computed against whichever opacity/shade/hue
 * combination is currently selected in [AppWidgetConfigureActivity], not cached. [roleRgb]
 * supplies every role's foreground color except ACCENT2, which is always derived from
 * shade/hue here (never independently overridable — matches §8.3's own model). */
fun auditContrast(
    register: Register,
    dark: Boolean,
    widgetBg: Rgb,
    opacityPct: Int,
    shade: Int,
    hueOffsetDeg: Int,
    roleRgb: (ContrastRole) -> Rgb,
): List<ContrastResult> = ContrastRole.entries.map { role ->
    val fg = if (role == ContrastRole.ACCENT2) currentAccent2(register, dark, shade, hueOffsetDeg) else roleRgb(role)
    val ratio = worstCaseContrast(fg, widgetBg, opacityPct)
    ContrastResult(role, ratio, ratio >= role.threshold)
}
