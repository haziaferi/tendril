package com.tendril.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import com.tendril.app.ui.WorkbenchCore
import com.tendril.app.ui.pages.LocalViewOnly

/**
 * What every Workbench surface reads from its surroundings, provided once: View-Only, the scaled
 * density (B§13.5 #4 — one scale for every measurement, from the window's shorter side and the
 * profile, `ShellScale.kt`), the profile itself, and — when the caller knows it — the shell
 * layout. Extracted from [WorkbenchScaffold] for the pop-out windows (B§13.6 #6): a page in its
 * own window needs the same four locals but **the main window's scale** (decided 2026-09-16 —
 * a 600 px pop-out would otherwise draw its page smaller than the main pane draws it), so
 * [shorterSideDp] can be handed in instead of read from this window; [wide] likewise.
 */
@Composable
fun WorkbenchEnvironment(
    core: WorkbenchCore,
    fixedDensityProfile: DensityProfile? = null,
    shorterSideDp: Float? = null,
    wide: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val viewOnly by core.viewLockState.viewOnly.collectAsState()
    // The shorter side is read in the platform's own density, before the override.
    val baseDensity = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val storedProfile by core.keyValueStore.observe(DENSITY_PROFILE_KEY).collectAsState(initial = core.keyValueStore.get(DENSITY_PROFILE_KEY))
    val profile = fixedDensityProfile ?: DensityProfile.fromKey(storedProfile)
    val side = shorterSideDp ?: (minOf(containerSize.width, containerSize.height) / baseDensity.density)
    val scale = shellScaleFor(side, profile)
    val scaledDensity = remember(baseDensity, scale) { Density(baseDensity.density * scale, baseDensity.fontScale) }
    CompositionLocalProvider(LocalViewOnly provides viewOnly, LocalDensity provides scaledDensity, LocalDensityProfile provides profile) {
        if (wide == null) content()
        else CompositionLocalProvider(LocalShellLayout provides if (wide) ShellLayout.RAIL else ShellLayout.BAR, content = content)
    }
}
