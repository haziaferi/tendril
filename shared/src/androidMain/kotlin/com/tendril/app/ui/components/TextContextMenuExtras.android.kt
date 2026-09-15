package com.tendril.app.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun TextContextMenuExtras(items: List<ContextMenuExtra>, content: @Composable () -> Unit) {
    content()
}
