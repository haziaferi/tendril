package com.tendril.app.widget

import android.app.WallpaperManager
import android.content.Context

/**
 * §8.6 — the system's own reading of the wallpaper (`WallpaperColors`, API 27+; the app's minimum
 * is 30): its primary colour and whether it supports dark text (a light wallpaper). Null when the
 * system has none to report — a live wallpaper, or colours not yet computed — and the readout
 * then keeps to the bracket. No permission is needed for the colours (the image itself would).
 */
fun readWallpaper(context: Context): Wallpaper? = runCatching {
    val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
    val argb = colors.primaryColor.toArgb()
    Wallpaper(
        primary = Rgb((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF),
        light = colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0,
    )
}.getOrNull()
