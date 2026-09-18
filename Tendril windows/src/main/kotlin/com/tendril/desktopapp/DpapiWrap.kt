package com.tendril.desktopapp

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.tendril.app.data.prefs.SecretWrap

/**
 * §0.10 item 20 (2026-09-18) — the AI key file wrapped with Windows' DPAPI
 * (`CryptProtectData` under the signed-in user's credentials), the way Electron's `safeStorage`
 * keeps Notion's and VS Code's tokens on Windows. `jna-platform` is the tray's dependency already.
 * A blob opens only for the account that wrote it on the machine that wrote it; another account
 * reads no key, not a wrong one. Off Windows the store keeps the bare line under owner-only
 * permissions ([SecretWrap.None]).
 */
object DpapiWrap : SecretWrap {
    override fun protect(plain: ByteArray): ByteArray = Crypt32Util.cryptProtectData(plain)
    override fun unprotect(stored: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(stored)

    /** DPAPI where it exists, else nothing — the container picks once. */
    fun forThisPlatform(): SecretWrap = if (Platform.isWindows()) DpapiWrap else SecretWrap.None
}
