package com.tendril.app.ui.components

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocalization
import androidx.compose.ui.platform.PlatformLocalization

/** The app's words for the four platform items — the app is English throughout, whatever the OS locale. */
private val EnglishTextMenu = object : PlatformLocalization {
    override val copy = "Copy"
    override val cut = "Cut"
    override val paste = "Paste"
    override val selectAll = "Select all"
}

@Composable
actual fun PlatformTextMenus(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // The register's surface, its text and its hover (`surfaceVariant` = surface2) — the same three the app's menus wear.
    val representation = remember(scheme) {
        DefaultContextMenuRepresentation(backgroundColor = scheme.surface, textColor = scheme.onSurface, itemHoverColor = scheme.surfaceVariant)
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides representation, LocalLocalization provides EnglishTextMenu, content = content)
}
