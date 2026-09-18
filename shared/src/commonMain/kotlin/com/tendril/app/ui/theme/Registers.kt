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

/**
 * B§13.8.1 (14g·3) — the urgency ladder, *R-wide+*: one coral family on every ground. Each step
 * is a hue and a saturation with its lightness solved to a contrast *target* on the actual
 * ground (`docs/critiques/urgency-ladder-mock.md` #3: the rule reproduces the mock's hexes at
 * dE ≤ 0.4 and re-solves on the OLED ground). Light climbs 3.0 → 13.0 (a mark's floor to a
 * text's ceiling); dark runs pale-and-muted → saturated, 11.5 → 4.6, so urgent is the deepest
 * step. Adjacent steps ≥ 10 dE for everyone, ≥ 8.5 under colour-vision deficiency (measured).
 */
object Ladder {
    data class Step(val hue: Double, val saturation: Double, val target: Double)
    val LIGHT = listOf(Step(10.0, 0.30, 3.0), Step(8.0, 0.50, 5.0), Step(6.0, 0.72, 7.9), Step(4.0, 0.91, 13.0))
    val DARK = listOf(Step(12.0, 0.34, 11.5), Step(10.0, 0.55, 8.1), Step(8.0, 0.74, 6.1), Step(6.0, 0.94, 4.6))
    fun solve(bg: Srgb, dark: Boolean): List<Srgb> = (if (dark) DARK else LIGHT).map { solveToTarget(it.hue, it.saturation, bg, it.target) }
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
    /** A data hue read as text — event, habit, the error red, a label's hue on its chip (14g·2). */
    const val DATA = 4.6
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
    // The design layer (2026-09-18, `desktop-design-layer.md` #4): one share, 6 %, in both modes —
    // at 4 % the light hover measured 1.07:1 and CIEDE2000 1.6 against the ground, under the 2.0
    // just-noticeable difference, so a hovered tree row read as nothing. Tested at ΔE ≥ 2.
    val surface2 = mix(text, bg, 0.06)
    val accent = solveHue(hue, bg, if (dark) Floors.ACCENT_DARK else Floors.ACCENT_LIGHT, lighten = dark)
    val onAccent = listOf(if (dark) bg else Srgb.WHITE, if (dark) Srgb.WHITE else bg, text)
        .firstOrNull { contrast(it, accent) >= Floors.ON_ACCENT } ?: text
    val soft = mix(accent, bg, if (dark) 0.22 else 0.12)
    // The audit's fixes (2026-09-17, `desktop-design-layer.md` #2): dim and faint are solved on the
    // selection's tint as well — a selected row's meta measured 3.45–4.17:1 on `accentSoft`.
    val grounds = listOf(bg, surface2, soft)
    val dim = solveTo(text, bg, Floors.DIM, grounds)
    val faint = solveTo(text, bg, Floors.FAINT, grounds)
    val softStart = mix(accent, text, if (dark) 0.70 else 0.85)
    val softText = if (contrast(softStart, soft) >= Floors.SOFT_TEXT) softStart else solveTo(text, softStart, Floors.SOFT_TEXT, listOf(soft))
    val border = mix(text, bg, if (dark) 0.14 else 0.11)

    // 14g·2 — the fan (B§13.8.3, `coloured-elements-mock.md` #3): event at −120°, habit at +60°,
    // the third at 180° — unless the register has a second channel, which takes the third slot.
    val tintShare = if (dark) 0.24 else 0.14
    val event = fanHue(accent, -120.0, bg, Floors.DATA, lighten = dark)
    val habit = fanHue(accent, 60.0, bg, Floors.DATA, lighten = dark)
    // One third token (the design layer, #6): solved to DATA (4.6), not MARK — the fan cleared 4.6
    // in 28 of 30 palettes anyway, so `third` and the old `thirdStrong` were the same colour, and
    // `onThird` was solved against the one nothing drew. A bar or a find mark reads at 4.6 too.
    val third = second?.let { solveHue(it, bg, Floors.DATA, lighten = dark) } ?: fanHue(accent, 180.0, bg, Floors.DATA, lighten = dark)
    val onThird = onColour(third, bg, text, dark)
    val findSoft = tintFor(third, bg, text, if (dark) 0.36 else 0.30, Floors.DATA)
    // The error family: a red at 5° solved against the ground *and* its own soft (#2 of the critique).
    val error = solveHue(hslToSrgb(5.0, 0.55, if (dark) 0.62 else 0.45), bg, Floors.DATA, lighten = dark, tintShare = tintShare)
    val onError = onColour(error, bg, text, dark)

    return TendrilPalette(
        dark = dark, bg = bg.toColor(), surface2 = surface2.toColor(), text = text.toColor(),
        textDim = dim.toColor(), textFaint = faint.toColor(),
        accent = accent.toColor(), onAccent = onAccent.toColor(),
        accentSoft = soft.toColor(), accentSoftText = softText.toColor(), border = border.toColor(),
        third = third.toColor(), onThird = onThird.toColor(),
        thirdSoft = mix(third, bg, tintShare).toColor(), findSoft = findSoft.toColor(),
        event = event.toColor(), eventSoft = mix(event, bg, tintShare).toColor(),
        habit = habit.toColor(), habitSoft = mix(habit, bg, tintShare).toColor(),
        error = error.toColor(), errorSoft = mix(error, bg, tintShare).toColor(), onError = onError.toColor(),
        ladder = Ladder.solve(bg, dark).map { it.toColor() },
    )
}
