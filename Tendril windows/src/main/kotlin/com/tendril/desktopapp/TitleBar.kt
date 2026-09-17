package com.tendril.desktopapp

import androidx.compose.ui.awt.ComposeWindow
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.tendril.app.ui.nav.TitleBarHandle
import com.tendril.app.ui.nav.TitleBarInstaller

/**
 * L5 (2026-09-17) — the borderless window: the JetBrains Runtime's custom title bar removes the
 * OS's own and paints the caption buttons over the client area, keeping every native behaviour
 * (drag, double-click, snap layouts, edge resize) for the region the app declares as title bar —
 * the 52 dp bar and the rail's top (`ui/nav/TitleBar.kt`). Notion Calendar's 46 px toolbar,
 * measured beside it, is the same thing done by Electron.
 *
 * Needs the runtime: `jbr-api` is compiled against, but the service exists only on a JetBrains
 * Runtime (the toolchain since this PR — Android Studio's JBR 21, which jpackage bundles). On any
 * other JDK [JBR.isWindowDecorationsSupported] is false and the window keeps the OS title bar:
 * the installer returns null and the shared side draws nothing differently.
 *
 * Units: the runtime speaks user-space pixels (the platform's logical px before the display's
 * scale); the shared side speaks device px, so both directions convert through the window's
 * scale. The insets are re-read on every install — the caption block's width is the OS's.
 */
class DesktopTitleBarInstaller(private val window: ComposeWindow) : TitleBarInstaller {
    private var bar: WindowDecorations.CustomTitleBar? = null

    override fun install(heightPx: Float, dark: Boolean): TitleBarHandle? {
        if (!JBR.isWindowDecorationsSupported()) return null
        val scale = window.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat()?.takeIf { it > 0f } ?: 1f
        val decorations = JBR.getWindowDecorations()
        val titleBar = bar ?: decorations.createCustomTitleBar().also { bar = it }
        titleBar.height = heightPx / scale
        titleBar.putProperty("controls.visible", true)
        titleBar.putProperty("controls.dark", dark)
        decorations.setCustomTitleBar(window, titleBar)
        return TitleBarHandle(
            rightInsetPx = titleBar.rightInset * scale,
            leftInsetPx = titleBar.leftInset * scale,
            markGround = { titleBar.forceHitTest(false) },
        )
    }
}
