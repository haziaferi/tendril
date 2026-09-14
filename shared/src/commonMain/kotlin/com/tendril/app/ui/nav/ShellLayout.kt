package com.tendril.app.ui.nav

/**
 * B§13.4 14a / §2.2 — the shell's two forms, chosen by the *window*, not the platform.
 *
 * Below [RAIL_MIN_WIDTH_DP] the five destinations sit in a bottom bar ([ShellBottomBar]); from it
 * upward they stand in a left rail ([ShellRail]). The breakpoint
 * is Material's expanded-width class (840 dp), and it is a property of the width the scaffold is
 * given, so a narrow desktop window keeps the bar and a phone turned landscape past 840 dp gets
 * the rail — decided 2026-09-14 rather than keying on platform, because the layout question is
 * "is there room for a rail", and only the width answers it.
 */
enum class ShellLayout { BAR, RAIL }

const val RAIL_MIN_WIDTH_DP = 840f

fun shellLayoutFor(widthDp: Float): ShellLayout =
    if (widthDp >= RAIL_MIN_WIDTH_DP) ShellLayout.RAIL else ShellLayout.BAR

/**
 * The desktop window's remembered frame, kept in `KeyValueStore` under [WINDOW_FRAME_KEY] as
 * `"w,h,x,y"` (dp; a negative position means "let the OS place it"). Pure so the encoding is
 * testable without a window: every other value the store holds is encoded by its consumer the
 * same way (`CalendarLayers`, `RoadMapFilter`).
 */
data class WindowFrame(val w: Int, val h: Int, val x: Int, val y: Int) {
    val positioned: Boolean get() = x >= 0 && y >= 0

    fun encode(): String = "$w,$h,$x,$y"

    companion object {
        /** Null on absent or unreadable input; a size below [MIN_WINDOW] is raised to it. */
        fun decode(value: String?): WindowFrame? {
            val parts = value?.split(',')?.map { it.trim().toIntOrNull() } ?: return null
            if (parts.size != 4 || parts.any { it == null }) return null
            val (w, h, x, y) = parts.map { it!! }
            if (w <= 0 || h <= 0) return null
            return WindowFrame(maxOf(w, MIN_WINDOW.w), maxOf(h, MIN_WINDOW.h), x, y)
        }
    }
}

const val WINDOW_FRAME_KEY = "window_frame"

/** First launch: a comfortable two-pane size, placed by the OS. */
val DEFAULT_WINDOW = WindowFrame(1200, 800, -1, -1)

/** The smallest frame the window accepts — the bar layout still fits with room for a sheet. */
val MIN_WINDOW = WindowFrame(800, 600, -1, -1)
