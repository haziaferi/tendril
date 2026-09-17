package com.tendril.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle

/**
 * The type vocabulary (2026-09-16, `docs/mockups/type-vocabulary.html`,
 * `docs/critiques/type-vocabulary-mock.md`) — **seven styles, one spelling per kind of chrome
 * text**, read as `MaterialTheme.typography.heading` and friends. The Material roles underneath
 * are set by [typographyFor] so a chip, a button or a dialog inherits the same seven without
 * being told; these names are for the sites the app draws itself. Weight carries the hierarchy
 * (Notion's rule); nothing in the chrome is larger than 18.
 *
 * | style | size / weight | where |
 * |---|---|---|
 * | [pageTitle] | 18 / 600 | a screen's title, the page bar's editable title, a task pane's title |
 * | [heading] | 14 / 600 | the tree's *Pages*, the tray's *Tasks*, a shelf's or a slide-over's header, Settings' and a sheet's sections, a card's title |
 * | [body] | 14 / 400 | rows, dialogs' copy, a chip's host |
 * | [label] | 12.5 / 500 | field labels (*Register*, *Mode*), chips, the FocusBar |
 * | [description] | 12.5 / 400 | explainers under a section, a card's second line, a due date — the caller colours it `onSurfaceVariant` |
 * | [caption] | 11 / 400 | *edited 2 h ago*, a card's footer, a count |
 * | [eyebrow] | 11 / 500, +0.06 em | *UNSCHEDULED · 3*, the shortcuts' groups, *Steps* — the caller passes the text upper-cased |
 *
 * The editor's content is not chrome and keeps its own sizes: [editorBody] 16, [editorH1] 24,
 * [editorH2] 20, [editorH3] 16 SemiBold. [clock] is the timer's tabular figures.
 */
val Typography.pageTitle: TextStyle get() = titleLarge
val Typography.heading: TextStyle get() = titleMedium
val Typography.body: TextStyle get() = bodyMedium
val Typography.label: TextStyle get() = labelLarge
val Typography.description: TextStyle get() = bodySmall
val Typography.caption: TextStyle get() = labelMedium
val Typography.eyebrow: TextStyle get() = labelSmall

val Typography.editorBody: TextStyle get() = bodyLarge
val Typography.editorH1: TextStyle get() = headlineSmall
val Typography.editorH2: TextStyle get() = headlineMedium
val Typography.editorH3: TextStyle get() = bodyLarge.copy(fontWeight = titleLarge.fontWeight)
val Typography.editorQuote: TextStyle get() = bodyLarge.copy(fontStyle = FontStyle.Italic)
val Typography.editorCode: TextStyle get() = bodyMedium.copy(fontFamily = FontFamily.Monospace)

/** Tabular figures, so a count or a clock does not jitter as its digits change — the one derivation
 * a site may make of a style (`desktop-design-layer.md` #3: three hand-made copies were an eighth style). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
/** The timer's figures at [body]'s size; [clockSmall] at [caption]'s (the rail's foot). */
val Typography.clock: TextStyle get() = body.tabular()
val Typography.clockSmall: TextStyle get() = caption.tabular()
