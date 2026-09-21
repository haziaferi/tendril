package com.tendril.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * S14's phone walk (2026-09-21) — a canvas and a mind map are *drawings*: their text is laid out in
 * content units from the font's own advances at 14 sp (`domain/canvas/TextWidth.kt`) and the person
 * zooms the board, so the Touch profile's one-step-up chrome type (`touchTypography`, T·P1) has no
 * place inside them — on the phone every card and leaf came out cut, its box sized for 14 sp and
 * its text drawn at 16. `WorkbenchEnvironment` provides the desktop's typography here; [DrawingType]
 * puts it around a board's or a map's content, so a drawing is the same drawing on both platforms.
 */
val LocalDrawingTypography = compositionLocalOf<Typography?> { null }

@Composable
fun DrawingType(content: @Composable () -> Unit) {
    val drawing = LocalDrawingTypography.current
    if (drawing == null) content()
    else MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = MaterialTheme.shapes, typography = drawing, content = content)
}
