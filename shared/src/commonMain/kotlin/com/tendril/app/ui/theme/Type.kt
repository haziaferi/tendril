package com.tendril.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.dm_sans_italic_variable
import com.tendril.app.generated.resources.dm_sans_variable
import com.tendril.app.generated.resources.inter_italic_variable
import com.tendril.app.generated.resources.inter_variable
import com.tendril.app.generated.resources.source_serif4_italic_variable
import com.tendril.app.generated.resources.source_serif4_variable
import org.jetbrains.compose.resources.Font as ResFont
import org.jetbrains.compose.resources.FontResource

/**
 * The type PR (2026-09-16) — every bundled variable font is loaded at **three true instances**,
 * 400 / 500 / 600, through the resources `Font()` overload that takes `variationSettings` (both
 * platforms, Compose Multiplatform 1.12). Until then each family was loaded at its default
 * instance only and Compose synthesised the rest — which it does for bold, not for Medium, so on
 * the desktop every `FontWeight.Medium` in the app rendered as Regular and the hierarchy had only
 * size to lean on. That is the finding behind "headers bold, not large" (`docs/critiques/type-vocabulary-mock.md` #2).
 */
private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

@Composable
private fun variableFamily(upright: FontResource, italic: FontResource): FontFamily = FontFamily(
    WEIGHTS.flatMap { w ->
        val axis = FontVariation.Settings(FontVariation.weight(w.weight))
        listOf(
            ResFont(upright, weight = w, style = FontStyle.Normal, variationSettings = axis),
            ResFont(italic, weight = w, style = FontStyle.Italic, variationSettings = axis),
        )
    },
)

@Composable
fun fontFamilyFor(typeface: TendrilTypeface): FontFamily = when (typeface) {
    TendrilTypeface.INTER -> variableFamily(Res.font.inter_variable, Res.font.inter_italic_variable)
    TendrilTypeface.SANS -> variableFamily(Res.font.dm_sans_variable, Res.font.dm_sans_italic_variable)
    TendrilTypeface.SERIF -> variableFamily(Res.font.source_serif4_variable, Res.font.source_serif4_italic_variable)
}

/**
 * B§13.7.3 rule 2, amended by the type PR — three weights and no others: 400, 500, **600**. No
 * thin (anything lighter reads as 400), no bold (anything at or over 550 reads as SemiBold — the
 * eye-pass never shouts). The editor's `Bold` span is content, not chrome, and does not pass
 * through here.
 */
fun eyePassWeight(weight: FontWeight?): FontWeight {
    val w = (weight ?: FontWeight.Normal).weight
    return when {
        w >= 550 -> FontWeight.SemiBold
        w >= 450 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
}

/**
 * The scale — B§13.4 14h·2 put the roles on six sizes a 1.125 step apart; the type PR (2026-09-16,
 * `docs/critiques/type-vocabulary-mock.md`) steps the titles **down** and lets weight carry the
 * hierarchy: 11 · 12.5 · 14 · 16 · 18, plus 20 and 24 for the editor's H2 and H1 only. Nothing in
 * the chrome is above 18. Every text in shared UI takes one of `TendrilType.kt`'s styles — the audit
 * (`tools/audit.py` rule 12, *literal type*) refuses a literal `fontSize` or `fontWeight`
 * outside this package. The density profile's scale multiplies as before (density is the size
 * lever, not a base size).
 */
object TypeScale {
    val CAPTION = 11f
    val LABEL = 12.5f
    val BODY = 14f
    val EDITOR_BODY = 16f
    val PAGE_TITLE = 18f
    val EDITOR_H2 = 20f
    val EDITOR_H1 = 24f
    val SIZES: List<Float> = listOf(CAPTION, LABEL, BODY, EDITOR_BODY, PAGE_TITLE, EDITOR_H2, EDITOR_H1)
    /** The line height a size takes — read by [typographyFor] and `TendrilType.kt`. */
    fun lineHeightFor(size: Float): Float = when (size) {
        CAPTION -> 16f
        LABEL -> 17f
        BODY -> 20f
        EDITOR_BODY -> 24f
        PAGE_TITLE -> 24f
        EDITOR_H2 -> 26f
        else -> 32f
    }
}

/**
 * One family for both headings and body (§2.3 — deliberately not a display+body split). The
 * Material roles are set so that `TendrilType.kt`'s seven styles read straight off them — every
 * chip, dialog and Material component then inherits the same vocabulary without being told:
 *
 *  - `titleLarge` = pageTitle 18/600 · `titleMedium` = heading 14/600 · `titleSmall` = label
 *  - `bodyLarge` = the editor's body 16 · `bodyMedium` = body 14 · `bodySmall` = description 12.5
 *  - `labelLarge` = label 12.5/500 (chips and buttons) · `labelMedium` = caption 11 · `labelSmall` = eyebrow 11/500
 *  - `headlineSmall` = the editor's H1 24/600 · `headlineMedium` = its H2 20/600
 *
 * The display roles keep Material's sizes (nothing draws them).
 */
/**
 * One chrome style: the family, the scale's size and its line height — and **T·P3 (item 23's Lows,
 * 2026-09-18): the line height applies to a single line too.** A `TextStyle` without a
 * `LineHeightStyle` trims the first and last lines to the font's own ascent and descent, so a
 * one-line `Text` measured ~1.21 em (22 dp for `pageTitle` at 18, 24 at 20) while a text field
 * measured 25 / 27 — the same style at two heights, on both platforms. Material's roles carry
 * `LineHeightStyle(Center, None)`; these do now, so a line is its style's line height everywhere.
 */
fun chromeStyle(family: FontFamily, size: Float, weight: FontWeight = FontWeight.Normal, letterSpacing: Float = 0f, platformStyle: PlatformTextStyle? = null): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = TypeScale.lineHeightFor(size).sp,
    letterSpacing = letterSpacing.em,
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
    // Material's own platform style (no font padding on Android) — without it the bar's title
    // still measured 27 dp against the sheet's 26; the constructor that says so is Android-only.
    platformStyle = platformStyle,
)

@Composable
fun typographyFor(typeface: TendrilTypeface): Typography {
    val family = fontFamilyFor(typeface)
    val base = Typography()
    fun style(size: Float, weight: FontWeight = FontWeight.Normal, letterSpacing: Float = 0f) = chromeStyle(family, size, weight, letterSpacing, base.bodyLarge.platformStyle)
    fun TextStyle.withFamily() = copy(fontFamily = family, fontWeight = eyePassWeight(fontWeight))
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = style(TypeScale.EDITOR_H2, FontWeight.SemiBold),
        headlineSmall = style(TypeScale.EDITOR_H1, FontWeight.SemiBold),
        titleLarge = style(TypeScale.PAGE_TITLE, FontWeight.SemiBold),
        titleMedium = style(TypeScale.BODY, FontWeight.SemiBold),
        titleSmall = style(TypeScale.LABEL, FontWeight.Medium),
        bodyLarge = style(TypeScale.EDITOR_BODY),
        bodyMedium = style(TypeScale.BODY),
        bodySmall = style(TypeScale.LABEL),
        labelLarge = style(TypeScale.LABEL, FontWeight.Medium),
        labelMedium = style(TypeScale.CAPTION),
        labelSmall = style(TypeScale.CAPTION, FontWeight.Medium, letterSpacing = 0.06f),
    )
}

/**
 * The phone's second fix PR (T·P1, 2026-09-18) — **the Touch type scale.** The vocabulary was cut
 * against Notion's desktop (14 / 12.5 measured there) and the phone inherited it; on the phone every
 * ground sets its primary text at 16–17 sp and its second line at 14 (Todoist, TickTick, Notion,
 * `docs/critiques/phone-type-full.md`). Under Touch each chrome role steps **one size up the same
 * scale** — body 16 · description and label 14 · caption and eyebrow 12.5 · heading 16 · pageTitle
 * 20 — and the editor's three (`bodyLarge`, `headlineMedium`, `headlineSmall`) stay: the page's
 * text was already 16. Provided by `WorkbenchEnvironment` where the profile is known, so a sheet
 * or a popup inherits it like any other `MaterialTheme` value; the seven styles keep reading off
 * the roles and the type-class audit sees no new site. A remap of the theme's own typography,
 * so the family and weights travel with it.
 */
/**
 * The desktop's second type pass (2026-09-20, the user's brief after the rail: "look at the other
 * text elements and resize accordingly, on real examples"). Measured beside Notion and the Claude
 * app at 125 % (`docs/critiques/desktop-type-grounds.md`): both set **every piece of chrome text
 * at one size** — Notion's property labels, values, chips, view tabs, buttons and sidebar items
 * all 14 px CSS (10 px of cap height), Claude's items, meta lines and body 10–12 — and reserve a
 * smaller size (12 px, 9 px of cap) for section headers alone; hierarchy is colour and weight,
 * not size. Tendril's `label`, `description`, `caption` and `eyebrow` stood a step under that
 * (8 px of cap at Compact). Under a pointer profile the four step **one size up the same scale** —
 * label and description 14 (Medium and Regular: the weight and the dim colour keep them apart
 * from `body`, Notion's way), caption and eyebrow 12.5 — pageTitle comes down to 16 (the editor's
 * size, SemiBold; the desktop's chrome is then 12.5 · 14 · 16 with 20 · 24 for H2 / H1 alone) — and
 * body, heading and the editor's sizes stay: those already measured at the grounds' heights. The mirror of
 * [touchTypography], provided by `WorkbenchEnvironment` the same way; the phone is untouched.
 */
fun pointerTypography(base: Typography): Typography {
    fun TextStyle.at(size: Float) = copy(fontSize = size.sp, lineHeight = TypeScale.lineHeightFor(size).sp)
    return base.copy(
        // The bar's title at the editor's size (decided 2026-09-20 with the size count: 18 was the one
        // chrome size off the scale; the Claude app's title bar measured 11 px of cap, 16 / 600's).
        titleLarge = base.titleLarge.at(TypeScale.EDITOR_BODY),
        titleSmall = base.titleSmall.at(TypeScale.BODY),
        bodySmall = base.bodySmall.at(TypeScale.BODY),
        labelLarge = base.labelLarge.at(TypeScale.BODY),
        labelMedium = base.labelMedium.at(TypeScale.LABEL),
        labelSmall = base.labelSmall.at(TypeScale.LABEL),
    )
}

fun touchTypography(base: Typography): Typography {
    fun TextStyle.at(size: Float) = copy(fontSize = size.sp, lineHeight = TypeScale.lineHeightFor(size).sp)
    return base.copy(
        titleLarge = base.titleLarge.at(TypeScale.EDITOR_H2),
        titleMedium = base.titleMedium.at(TypeScale.EDITOR_BODY),
        titleSmall = base.titleSmall.at(TypeScale.BODY),
        bodyMedium = base.bodyMedium.at(TypeScale.EDITOR_BODY),
        bodySmall = base.bodySmall.at(TypeScale.BODY),
        labelLarge = base.labelLarge.at(TypeScale.BODY),
        labelMedium = base.labelMedium.at(TypeScale.LABEL),
        labelSmall = base.labelSmall.at(TypeScale.LABEL),
    )
}
