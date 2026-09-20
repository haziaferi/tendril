package com.tendril.app.ui.nav

import com.tendril.app.ui.components.PlatformTextMenus
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
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
import com.tendril.app.ui.theme.pointerTypography
import com.tendril.app.ui.theme.touchTypography

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
    /**
     * True where the platform already applied [shellScaleFor] to its own density (Android's
     * `MainActivity`, T·P3 of item 23's Lows, 2026-09-18): a dialog, a menu or a sheet there is
     * its own window and starts from the platform's density, not this composition's — so the
     * phone drew every sheet 4.6 % smaller than the page behind it (one style, two heights).
     * The desktop's windows inherit the composition's density and keep scaling here.
     */
    platformScaled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val viewOnly by core.viewLockState.viewOnly.collectAsState()
    // The shorter side is read in the platform's own density, before the override.
    val baseDensity = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val storedProfile by core.keyValueStore.observe(DENSITY_PROFILE_KEY).collectAsState(initial = core.keyValueStore.get(DENSITY_PROFILE_KEY))
    val profile = fixedDensityProfile ?: DensityProfile.fromKey(storedProfile)
    val side = shorterSideDp ?: (minOf(containerSize.width, containerSize.height) / baseDensity.density)
    // The desktop's reference is the system's text size (`LocalSystemTextScale`, 2026-09-19); the
    // window's side is the phone's rule and the fallback where no platform provides the local.
    val systemTextScale = LocalSystemTextScale.current
    val scale = when {
        platformScaled -> 1f
        systemTextScale != null -> systemTextScale * profile.factor
        else -> shellScaleFor(side, profile)
    }
    val scaledDensity = remember(baseDensity, scale) { Density(baseDensity.density * scale, baseDensity.fontScale) }
    // L5 — the window's title bar is the bar (`TitleBar.kt`): installed here, where every window's
    // content passes (the main scaffold and each pop-out), once the scaled density is known — the
    // bar's 52 dp in device px — and re-installed when the ground's darkness changes so the OS's
    // caption glyphs follow the register. Null where the platform has none.
    val titleBarInstaller = LocalTitleBarInstaller.current
    val barPx = with(scaledDensity) { TOP_BAR_HEIGHT.toPx() }
    val darkGround = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleBar = remember(titleBarInstaller, barPx, darkGround) { titleBarInstaller?.install(barPx, darkGround) }
    // T·P1 (the phone's second fix PR): under Touch every chrome role steps one size up the scale —
    // the phone read two to three points under every app beside it (`touchTypography`).
    // The desktop's small styles step up one size too (2026-09-20, measured beside Notion and the
    // Claude app: chrome text is one size there, hierarchy is weight and colour) — `pointerTypography`.
    val typography = if (profile == DensityProfile.TOUCH) touchTypography(MaterialTheme.typography) else pointerTypography(MaterialTheme.typography)
    CompositionLocalProvider(LocalViewOnly provides viewOnly, LocalDensity provides scaledDensity, LocalDensityProfile provides profile, LocalTitleBar provides titleBar) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = MaterialTheme.shapes, typography = typography) {
            // S3 (small things IV) — the platform's text context menu (cut · copy · paste) drawn in the
            // register's colours with the app's words; nothing on Android.
            PlatformTextMenus {
                if (wide == null) content()
                else CompositionLocalProvider(LocalShellLayout provides if (wide) ShellLayout.RAIL else ShellLayout.BAR, content = content)
            }
        }
    }
}
