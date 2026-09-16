package com.tendril.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 14g·2 (B§13.8.3) — a *stored* hue rendered by the register. A label's `color` and a callout's
 * `calloutColor` are `#RRGGBB` strings that sync and never change (they are the identity);
 * what changes is how each ground draws them: the hue kept, the lightness solved to the floor
 * the use needs. Saturation is never raised for a stored hue — a grey callout stays grey.
 * Memoised per (hex, ground): the Pages filter row would otherwise solve eight chips a frame.
 */
data class LabelColours(
    /** The hue as text, ≥ 4.6:1 on the ground *and* on [tint]. */
    val hue: Color,
    /** The chip: [hue] at 14 % over the ground. */
    val tint: Color,
    /** What reads on a solid [hue] — the selected chip. */
    val onHue: Color,
)

data class CalloutColours(
    /** The 3 px bar — a mark, ≥ 3.0:1. */
    val bar: Color,
    /** The card: [bar] at 20 % / 28 % over the ground; the register's text reads on it. */
    val tint: Color,
)

private val labelCache = HashMap<Pair<String, Color>, LabelColours>()
private val calloutCache = HashMap<Pair<String, Color>, CalloutColours>()

fun labelColours(storedHex: String, palette: TendrilPalette): LabelColours {
    val dark = palette.dark
    val key = storedHex to palette.bg
    synchronized(labelCache) { labelCache[key]?.let { return it } }
    val bg = palette.bg.toSrgb()
    val hue = solveHue(Srgb.hex(storedHex), bg, Floors.DATA, lighten = dark, tintShare = LABEL_TINT)
    val out = LabelColours(
        hue = hue.toColor(),
        tint = mix(hue, bg, LABEL_TINT).toColor(),
        onHue = onColour(hue, bg, palette.text.toSrgb(), dark).toColor(),
    )
    synchronized(labelCache) { labelCache[key] = out }
    return out
}

fun calloutColours(storedHex: String, palette: TendrilPalette): CalloutColours {
    val dark = palette.dark
    val key = storedHex to palette.bg
    synchronized(calloutCache) { calloutCache[key]?.let { return it } }
    val bg = palette.bg.toSrgb()
    val bar = solveHue(Srgb.hex(storedHex), bg, Floors.MARK, lighten = dark)
    val out = CalloutColours(bar = bar.toColor(), tint = mix(bar, bg, if (dark) 0.28 else 0.20).toColor())
    synchronized(calloutCache) { calloutCache[key] = out }
    return out
}

const val LABEL_TINT = 0.14

/** §P3 — the callout's seven stored hues, the same shape as [com.tendril.app.data.page.LabelColors]'s
 * small fixed palette rather than a full colour picker. Since 14g·2 they are hues, not fills: each
 * is rendered by the register through [calloutColours] (the pastels only ever worked in light). */
val CALLOUT_COLORS = listOf(
    "#FDE68A", "#BFDBFE", "#BBF7D0", "#FBCFE8", "#DDD6FE", "#FED7AA", "#E5E7EB",
)
