package com.tendril.app.ui.components

import androidx.compose.runtime.Composable

/** One extra item for a text field's own right-click menu. */
class ContextMenuExtra(val label: String, val onClick: () -> Unit)

/**
 * B§13.4 14d — inside a text field the field's own right-click menu (cut / copy / paste on the
 * desktop) must win, as its long-press wins on the phone; the block's actions are then one item
 * appended to that menu rather than a second menu fighting it. Desktop: Compose's
 * `ContextMenuDataProvider`, whose items every text field below it lists after its own.
 * Android: the content unchanged — the platform's text toolbar is not extensible from here, and
 * the block's long-press on its margin opens the same sheet.
 */
@Composable
expect fun TextContextMenuExtras(items: List<ContextMenuExtra>, content: @Composable () -> Unit)
