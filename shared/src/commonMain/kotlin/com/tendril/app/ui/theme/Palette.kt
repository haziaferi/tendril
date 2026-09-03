package com.tendril.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One theme's full token set, per spec §2.3. Every text/background pairing here is
 * WCAG AA verified in the source design (4.5:1 body/small text, 3:1 large text/icons) —
 * these hex values are copied verbatim from the spec table, not re-derived.
 */
data class TendrilPalette(
    val bg: Color,
    val surface2: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentSoftText: Color,
    val border: Color,
)

enum class TendrilColorTheme(val label: String) {
    INK("Ink"),
    CLAY("Clay"),
    MOSS("Moss"),
    MAUVE("Mauve"),
}

enum class TendrilMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
}

enum class TendrilTypeface(val label: String) {
    SANS("Sans"),
    SERIF("Serif"),
}

/** Milestone 3 — replaces `android.graphics.Color.parseColor`, which has no multiplatform
 * equivalent; every value here is a plain "#RRGGBB" literal so a fixed-width hex parse is all
 * that's needed (no "#RGB"/named-color support required). */
private fun c(hex: String): Color {
    val value = hex.removePrefix("#").toLong(16)
    return Color((0xFF000000L or value).toInt())
}

// §2.3 palette table, transcribed exactly — do not "improve" these without updating the spec.
val TendrilPalettes: Map<Pair<TendrilColorTheme, TendrilMode>, TendrilPalette> = mapOf(
    (TendrilColorTheme.INK to TendrilMode.LIGHT) to TendrilPalette(
        bg = c("#FFFFFF"), surface2 = c("#F1F0EC"), text = c("#1B1B18"),
        textDim = c("#6B6A63"), textFaint = c("#959389"), accent = c("#4A5568"),
        accentStrong = c("#333D4D"), onAccent = c("#FFFFFF"), accentSoft = c("#E7EAEE"),
        accentSoftText = c("#3D4759"), border = c("#E4E2DA"),
    ),
    (TendrilColorTheme.INK to TendrilMode.DARK) to TendrilPalette(
        bg = c("#1B1D21"), surface2 = c("#24262B"), text = c("#EDEDEF"),
        textDim = c("#9B9DA3"), textFaint = c("#6A6C72"), accent = c("#8792A6"),
        accentStrong = c("#A9B3C4"), onAccent = c("#12131A"), accentSoft = c("#262A33"),
        accentSoftText = c("#9BAAC2"), border = c("#2A2D33"),
    ),
    (TendrilColorTheme.CLAY to TendrilMode.LIGHT) to TendrilPalette(
        bg = c("#FFFFFF"), surface2 = c("#F1E9DC"), text = c("#2A2320"),
        textDim = c("#7A6B5C"), textFaint = c("#A28F79"), accent = c("#A87249"),
        accentStrong = c("#8A5D3B"), onAccent = c("#FFFFFF"), accentSoft = c("#F3E4D3"),
        accentSoftText = c("#7A4E2E"), border = c("#EBDFCC"),
    ),
    (TendrilColorTheme.CLAY to TendrilMode.DARK) to TendrilPalette(
        bg = c("#221C18"), surface2 = c("#2B241F"), text = c("#F2EAE1"),
        textDim = c("#B0A190"), textFaint = c("#776A5A"), accent = c("#D4A57C"),
        accentStrong = c("#E8C39D"), onAccent = c("#1A1310"), accentSoft = c("#33281F"),
        accentSoftText = c("#E0B98E"), border = c("#362E27"),
    ),
    (TendrilColorTheme.MOSS to TendrilMode.LIGHT) to TendrilPalette(
        bg = c("#FFFFFF"), surface2 = c("#EBF0E9"), text = c("#1E241F"),
        textDim = c("#5E6B5F"), textFaint = c("#889783"), accent = c("#6E8C70"),
        accentStrong = c("#48624A"), onAccent = c("#FFFFFF"), accentSoft = c("#E4ECE3"),
        accentSoftText = c("#3C5240"), border = c("#E2E9E0"),
    ),
    (TendrilColorTheme.MOSS to TendrilMode.DARK) to TendrilPalette(
        bg = c("#1C211B"), surface2 = c("#232A22"), text = c("#E9EFE7"),
        textDim = c("#9DAA9A"), textFaint = c("#6B766A"), accent = c("#93AC93"),
        accentStrong = c("#B3C7B1"), onAccent = c("#131A13"), accentSoft = c("#253026"),
        accentSoftText = c("#A9C2A8"), border = c("#2A322A"),
    ),
    (TendrilColorTheme.MAUVE to TendrilMode.LIGHT) to TendrilPalette(
        bg = c("#FFFFFF"), surface2 = c("#F1E7EC"), text = c("#241E22"),
        textDim = c("#7A6B72"), textFaint = c("#A38C99"), accent = c("#A9708D"),
        accentStrong = c("#7D4D64"), onAccent = c("#FFFFFF"), accentSoft = c("#F0DEE6"),
        accentSoftText = c("#7A4A5E"), border = c("#EBD9E1"),
    ),
    (TendrilColorTheme.MAUVE to TendrilMode.DARK) to TendrilPalette(
        bg = c("#211A23"), surface2 = c("#29212C"), text = c("#F0E6EB"),
        textDim = c("#AC9AA5"), textFaint = c("#77636D"), accent = c("#CC93AC"),
        accentStrong = c("#E0AFC4"), onAccent = c("#18121A"), accentSoft = c("#322730"),
        accentSoftText = c("#DBA9BE"), border = c("#362B38"),
    ),
)

fun paletteFor(theme: TendrilColorTheme, mode: TendrilMode): TendrilPalette =
    TendrilPalettes.getValue(theme to mode)
