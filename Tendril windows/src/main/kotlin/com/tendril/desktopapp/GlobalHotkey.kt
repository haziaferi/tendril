package com.tendril.desktopapp

import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.tendril.app.ui.nav.QuickAddChord
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/**
 * B§13.6 #7 — the global quick-add chord. `RegisterHotKey(null, …)` posts `WM_HOTKEY` to the
 * queue of the thread that registered it, so the chord lives on its own daemon thread with a
 * `GetMessage` loop; a rebind posts `WM_QUIT` to that thread, which unregisters and ends. The
 * Win32 halves of the chord come from the shared [QuickAddChord]; [error] is the registration
 * failure Settings shows beside the picker (another app holds the chord). Windows only — the
 * desktop build has no other target, but the guard keeps a stray JVM from loading `user32`.
 */
internal class GlobalHotkey(private val onPress: () -> Unit) {
    val error = MutableStateFlow<String?>(null)
    private var thread: Thread? = null
    @Volatile private var threadId = 0

    fun bind(chord: QuickAddChord) {
        unbind()
        if (!Platform.isWindows()) { error.value = null; return }
        val ready = CountDownLatch(1)
        var ok = false
        thread = Thread({
            val user32 = User32.INSTANCE
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            ok = user32.RegisterHotKey(null, HOTKEY_ID, chord.modifiersMask(), chord.vk)
            ready.countDown()
            if (!ok) return@Thread
            val msg = WinUser.MSG()
            while (user32.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WinUser.WM_HOTKEY) SwingUtilities.invokeLater(onPress)
            }
            user32.UnregisterHotKey(Pointer.NULL, HOTKEY_ID)
        }, "tendril-hotkey").apply { isDaemon = true; start() }
        ready.await(2, TimeUnit.SECONDS)
        error.value = if (ok) null else "${chord.label} is taken by another app — choose another chord."
    }

    fun unbind() {
        val t = thread ?: return
        if (t.isAlive && threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
            t.join(500)
        }
        thread = null
    }

    private companion object {
        const val HOTKEY_ID = 0x7E4D
    }
}
