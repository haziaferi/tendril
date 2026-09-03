package com.tendril.app.ui.pages

import androidx.compose.runtime.compositionLocalOf

/**
 * §3.1.2 — View-Only lock, threaded implicitly through every Pages composable via
 * CompositionLocal rather than as an explicit parameter on each one: dozens of independent
 * leaf composables (block rows, row property editors, database cells, view config sheets) all
 * need to react to the same single global flag, and threading it through every function
 * signature between each screen root and each leaf would touch effectively every composable
 * in this package for a flag that's read, never written, below the root. Provided once in
 * [com.tendril.app.ui.nav.WorkbenchScaffold].
 */
val LocalViewOnly = compositionLocalOf { false }

/**
 * §3.1.2 — checkbox-only mode folded together with View-Only into one "is everything but a
 * to-do block's own checkbox locked on this page" flag, provided by
 * [com.tendril.app.ui.pages.PageDetailScreen] alone (the only screen checkbox-only ever
 * applies to — see [com.tendril.app.ui.pages.PageDetailViewModel.hasCheckboxes]'s note on why a
 * Database's table view doesn't need this). [BlockPrefix]'s to-do Checkbox is the one control
 * in that subtree that deliberately reads [LocalViewOnly] instead, since it's the one thing
 * checkbox-only mode leaves tappable.
 */
val LocalContentLocked = compositionLocalOf { false }
