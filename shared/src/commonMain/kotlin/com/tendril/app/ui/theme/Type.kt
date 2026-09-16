package com.tendril.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
 * B§13.7.3 rule 2 (14g·1) — no thin weights: the scale uses DM Sans 400 and 500 only. Anything
 * lighter reads as 400, anything at or over 450 as 500; a site that asks for bold gets its own
 * emphasis, but the *scale* never does. (No base-size control either — density is the size lever.)
 */
fun eyePassWeight(weight: FontWeight?): FontWeight =
    if ((weight ?: FontWeight.Normal).weight >= 450) FontWeight.Medium else FontWeight.Normal

/**
 * The scale — B§13.4 14h·2, from `docs/critiques/small-things-measured.md` #1: Material's
 * defaults put nine roles on five sizes, seven of them inside 11…16 sp, with `titleMedium` and
 * `bodyLarge` both at 16. The roles now sit on **six sizes a 1.125 step apart** (titleLarge
 * skipping one), and every literal `fontSize` in shared UI takes one of them (`tools/audit.py`
 * rule 12): 11 · 12.5 · 14 · 16 · 18 · 22, plus 24 for `headlineSmall`. The density profile's
 * scale multiplies as before (B§13.7.3 rule 2 — density is the size lever, not a base size).
 */
object TypeScale {
    val LABEL_SMALL = 11f
    val BODY_SMALL = 12.5f
    val BODY_MEDIUM = 14f
    val BODY_LARGE = 16f
    val TITLE_MEDIUM = 18f
    val TITLE_LARGE = 22f
    val HEADLINE_SMALL = 24f
    /** Every size a literal `fontSize` in shared UI may take. */
    val SIZES: List<Float> = listOf(LABEL_SMALL, BODY_SMALL, BODY_MEDIUM, BODY_LARGE, TITLE_MEDIUM, TITLE_LARGE, HEADLINE_SMALL)
    /** The line height a size takes — read by [typographyFor], and by the audit's doc. */
    fun lineHeightFor(size: Float): Float = when (size) {
        LABEL_SMALL -> 16f
        BODY_SMALL -> 17f
        BODY_MEDIUM -> 20f
        BODY_LARGE -> 24f
        TITLE_MEDIUM -> 24f
        TITLE_LARGE -> 28f
        else -> 32f
    }
}

/**
 * One family for both headings and body (§2.3 — deliberately not a display+body split); the
 * sizes are [TypeScale]'s, the display and headline roles above `headlineSmall` keep Material's;
 * every style's weight is clamped by [eyePassWeight].
 */
@Composable
fun typographyFor(typeface: TendrilTypeface): Typography {
    val family = fontFamilyFor(typeface)
    val base = Typography()
    fun TextStyle.withFamily() = copy(fontFamily = family, fontWeight = eyePassWeight(fontWeight))
    fun TextStyle.at(size: Float) = withFamily().copy(fontSize = size.sp, lineHeight = TypeScale.lineHeightFor(size).sp)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.at(TypeScale.HEADLINE_SMALL),
        titleLarge = base.titleLarge.at(TypeScale.TITLE_LARGE),
        titleMedium = base.titleMedium.at(TypeScale.TITLE_MEDIUM),
        titleSmall = base.titleSmall.at(TypeScale.BODY_MEDIUM),
        bodyLarge = base.bodyLarge.at(TypeScale.BODY_LARGE),
        bodyMedium = base.bodyMedium.at(TypeScale.BODY_MEDIUM),
        bodySmall = base.bodySmall.at(TypeScale.BODY_SMALL),
        labelLarge = base.labelLarge.at(TypeScale.BODY_MEDIUM),
        labelMedium = base.labelMedium.at(TypeScale.BODY_SMALL),
        labelSmall = base.labelSmall.at(TypeScale.LABEL_SMALL),
    )
}
