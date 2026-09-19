package com.tendril.desktopapp

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.tendril.app.ui.nav.systemTextScale

/**
 * The desktop's measurement reference (decided 2026-09-19): **Windows' own text size** —
 * Settings › Accessibility › Text size, 100…225 % — read from the registry value the slider
 * writes (`HKCU\Software\Microsoft\Accessibility\TextScaleFactor`, absent until first moved),
 * not the window's size. Display scaling (125 %, 150 %) is already in `LocalDensity` through
 * the runtime; this is the second, user-chosen number, and it is the one every other Windows app
 * that honours it (the shell, Mail, Settings) grows by. Re-read when the main window regains
 * focus, so a change made in Settings applies on the way back.
 */
object WindowsTextScale {
    private const val KEY = "Software\\Microsoft\\Accessibility"
    private const val VALUE = "TextScaleFactor"

    /** 1.0 at 100 %, 2.25 at the slider's end; 1.0 where the value is absent or this is not Windows. */
    fun read(): Float {
        if (!Platform.isWindows()) return 1f
        return try {
            if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, VALUE)) 1f
            else systemTextScale(Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, KEY, VALUE))
        } catch (e: Exception) {
            1f
        }
    }
}
