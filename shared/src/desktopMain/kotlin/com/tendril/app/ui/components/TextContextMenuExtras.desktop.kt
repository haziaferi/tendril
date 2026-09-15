package com.tendril.app.ui.components

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable

@Composable
actual fun TextContextMenuExtras(items: List<ContextMenuExtra>, content: @Composable () -> Unit) {
    ContextMenuDataProvider(items = { items.map { ContextMenuItem(it.label, it.onClick) } }, content = content)
}
