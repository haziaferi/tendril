package com.tendril.app.ui.components

import androidx.compose.runtime.Composable

/**
 * S3 (small things IV, 2026-09-20) — the platform's text context menu (a right-click in a text
 * field: cut · copy · paste · select all, plus whatever [TextContextMenuExtras] appends). Compose
 * Desktop draws it through `LocalContextMenuRepresentation`, whose default is a light menu with the
 * system locale's labels — white on a dark register, Italian on this machine. The desktop actual
 * provides a representation in the register's colours and the app's English words; Android's text
 * toolbar is the platform's own and the actual is the content alone.
 */
@Composable
expect fun PlatformTextMenus(content: @Composable () -> Unit)
