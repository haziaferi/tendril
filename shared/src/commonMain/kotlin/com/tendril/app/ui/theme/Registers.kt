package com.tendril.app.ui.theme

/**
 * B§13.7.1 — a theme is **ground × hue × mode**, and a register is a named preset of the first
 * two. Two grounds only, never warm (an hour of writing on cream strains the eye — B§13.7.1);
 * one accent hue per mode; at most one second channel where a use needs two meanings. The
 * ten registers below are the seven B§13.7.2 chose plus Clay, Moss and Mauve returning as
 * registers (decided 2026-09-16): their old `Palette.kt` hues on the cold ground, solved like
 * the rest. Adding one costs a hue and nothing else — [paletteFor] does the solving.
 */
enum class Ground(val label: String, lightBg: String, lightText: String, darkBg: String, darkText: String) {
    /** Ink's: white, `#1B1D21`. */
    NEUTRAL("neutral", "#FFFFFF", "#1B1B18", "#1B1D21", "#EDEDEF"),
    /** Console's: `#FBFBFA`, `#101216`. */
    COLD("cold", "#FBFBFA", "#16181B", "#101216", "#E3E6EA"),
    /** Swiss's own — a cooler white and a bluer black, the lexicon pair's. */
    COLD_SWISS("cold", "#F4F6F8", "#0F1018", "#0E1018", "#E8F0F0");

    val lightBg: Srgb = Srgb.hex(lightBg)
    val lightTextSeed: Srgb = Srgb.hex(lightText)
    val darkBg: Srgb = Srgb.hex(darkBg)
    val darkTextSeed: Srgb = Srgb.hex(darkText)
}

data class Register(
    /** The stored name (`theme_register`), lower-case; the old enum names resolve through [fromKey]. */
    val key: String,
    val label: String,
    /** What it was imagined for — the swatch row's caption. */
    val use: String,
    val ground: Ground,
    val lightHue: Srgb,
    val darkHue: Srgb,
    /** Light / dark, fills and marks only (B§13.7.2). */
    val second: Pair<Srgb, Srgb>? = null,
) {
    companion object {
        val INK = Register("ink", "Ink", "the default", Ground.NEUTRAL, Srgb.hex("#4A5568"), Srgb.hex("#8792A6"))
        val CONSOLE = Register("console", "Console", "coding projects, a process log", Ground.COLD, Srgb.hex("#4B3AA6"), Srgb.hex("#A99AF0"))
        val SWISS = Register(
            "swiss", "Swiss", "company project management", Ground.COLD_SWISS, Srgb.hex("#1D3557"), Srgb.hex("#7FA6D9"),
            second = Srgb.hex("#B8960A") to Srgb.hex("#F4D03F"),
        )
        val PLAYGROUND = Register(
            "playground", "Playground", "groceries, kids' appointments", Ground.COLD, Srgb.hex("#1D5663"), Srgb.hex("#53B2C5"),
            second = Srgb.hex("#8A4523") to Srgb.hex("#D18E61"),
        )
        val BLUSH = Register("blush", "Blush", "a personal diary", Ground.COLD, Srgb.hex("#86324F"), Srgb.hex("#D793AB"))
        val CHALK = Register("chalk", "Chalk", "a quieter diary", Ground.COLD, Srgb.hex("#574C38"), Srgb.hex("#B7A380"))
        val KODACHROME = Register("kodachrome", "Kodachrome", "a travel journal", Ground.COLD, Srgb.hex("#7E3925"), Srgb.hex("#D29879"))
        val CLAY = Register("clay", "Clay", "the old warm one, on a cold ground", Ground.COLD, Srgb.hex("#A87249"), Srgb.hex("#D4A57C"))
        val MOSS = Register("moss", "Moss", "the old green one", Ground.COLD, Srgb.hex("#6E8C70"), Srgb.hex("#93AC93"))
        val MAUVE = Register("mauve", "Mauve", "the old mauve one", Ground.COLD, Srgb.hex("#A9708D"), Srgb.hex("#CC93AC"))

        val ALL: List<Register> = listOf(INK, CONSOLE, SWISS, PLAYGROUND, BLUSH, CHALK, KODACHROME, CLAY, MOSS, MAUVE)

        /** `"clay"` and the old `ThemePreferences` value `"CLAY"` both resolve; unknown → Ink. */
        fun fromKey(key: String?): Register = ALL.firstOrNull { it.key == key?.lowercase() } ?: INK
    }
}

/** The floors, in one place (B§13.7.1/13.7.3 with `docs/critiques/registers-mock.md`'s amendments). */
object Floors {
    const val DIM = 4.6
    const val FAINT = 3.05
    const val ACCENT_LIGHT = 8.0
    const val ACCENT_DARK = 7.6
    const val ON_ACCENT = 4.6
    const val SOFT_TEXT = 4.6
    const val MARK = 3.0
}

/**
 * The whole derivation. [dark] picks the mode's ground and hue; [oled] (a phone-only device
 * setting, B§13.7.3 rule 3) drops the dark ground to 4 % lightness before anything is solved,
 * so every derived token re-solves against the deeper black.
 */
fun paletteFor(register: Register, dark: Boolean, oled: Boolean = false): TendrilPalette {
    val g = register.ground
    val bg0 = if (dark) g.darkBg else g.lightBg
    val bg = if (dark && oled) oledGround(bg0) else bg0
    val seed = if (dark) g.darkTextSeed else g.lightTextSeed
    val hue = if (dark) register.darkHue else register.lightHue
    val second = register.second?.let { if (dark) it.second else it.first }

    val text = solveText(seed, bg)
    val surface2 = mix(text, bg, if (dark) 0.06 else 0.04)
    val grounds = listOf(bg, surface2)
    val dim = solveTo(text, bg, Floors.DIM, grounds)
    val faint = solveTo(text, bg, Floors.FAINT, grounds)

    val accent = solveHue(hue, bg, if (dark) Floors.ACCENT_DARK else Floors.ACCENT_LIGHT, lighten = dark)
    val strong = mix(accent, text, 0.80)
    val onAccent = listOf(if (dark) bg else Srgb.WHITE, if (dark) Srgb.WHITE else bg, text)
        .firstOrNull { contrast(it, accent) >= Floors.ON_ACCENT } ?: text
    val soft = mix(accent, bg, if (dark) 0.22 else 0.12)
    val softStart = mix(accent, text, if (dark) 0.70 else 0.85)
    val softText = if (contrast(softStart, soft) >= Floors.SOFT_TEXT) softStart else solveTo(text, softStart, Floors.SOFT_TEXT, listOf(soft))
    val border = mix(text, bg, if (dark) 0.14 else 0.11)

    val third = second?.let { solveHue(it, bg, Floors.MARK, lighten = dark) } ?: mix(accent, text, 0.55)
    val thirdSoft = mix(third, bg, if (dark) 0.24 else 0.14)

    return TendrilPalette(
        bg = bg.toColor(), surface2 = surface2.toColor(), text = text.toColor(),
        textDim = dim.toColor(), textFaint = faint.toColor(),
        accent = accent.toColor(), accentStrong = strong.toColor(), onAccent = onAccent.toColor(),
        accentSoft = soft.toColor(), accentSoftText = softText.toColor(), border = border.toColor(),
        third = third.toColor(), thirdSoft = thirdSoft.toColor(),
    )
}
