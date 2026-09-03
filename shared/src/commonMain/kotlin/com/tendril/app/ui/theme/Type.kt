package com.tendril.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.dm_sans_italic_variable
import com.tendril.app.generated.resources.dm_sans_variable
import com.tendril.app.generated.resources.source_serif4_italic_variable
import com.tendril.app.generated.resources.source_serif4_variable
import org.jetbrains.compose.resources.Font as ResFont

/**
 * Milestone 3 — Compose Multiplatform resource-based font loading (`Res.font.*`), the only
 * portable equivalent to Android's `R.font` + `FontVariation` weight-axis loading this file used
 * before the move: the multiplatform resources `Font()` API has no `variationSettings`
 * parameter, so each bundled variable font is loaded once (its default instance) plus its
 * dedicated italic file — Compose's own synthetic bold/italic fills in weights the font file
 * itself doesn't expose a named instance for. Visually close, not pixel-identical to the
 * Android build's true variable-weight rendering; revisit if that gap turns out to matter.
 */
@Composable
private fun dmSansFamily(): FontFamily = FontFamily(
    ResFont(Res.font.dm_sans_variable, weight = FontWeight.Normal),
    ResFont(Res.font.dm_sans_italic_variable, weight = FontWeight.Normal, style = FontStyle.Italic),
)

@Composable
private fun sourceSerif4Family(): FontFamily = FontFamily(
    ResFont(Res.font.source_serif4_variable, weight = FontWeight.Normal),
    ResFont(Res.font.source_serif4_italic_variable, weight = FontWeight.Normal, style = FontStyle.Italic),
)

@Composable
fun fontFamilyFor(typeface: TendrilTypeface): FontFamily = when (typeface) {
    TendrilTypeface.SANS -> dmSansFamily()
    TendrilTypeface.SERIF -> sourceSerif4Family()
}

/**
 * One family for both headings and body (§2.3 — deliberately not a display+body split).
 * Sizes/line-heights follow Material3's default type scale; only the family swaps per theme.
 */
@Composable
fun typographyFor(typeface: TendrilTypeface): Typography {
    val family = fontFamilyFor(typeface)
    val base = Typography()
    fun TextStyle.withFamily() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily(),
    )
}
