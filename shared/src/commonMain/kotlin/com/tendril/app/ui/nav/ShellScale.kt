package com.tendril.app.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * B§13.5 #4 (decided 2026-09-13) — every measurement in the app is proportional to the screen,
 * on both platforms, through **one** number: the shell multiplies `LocalDensity` by
 * [shellScaleFor], so each `.dp` and `.sp` anywhere below it — the rail's 84, a row's 48, the
 * calendar's `DAY_WIDTH`, a slide-over's 440 — converts to pixels through the same factor. No
 * site is edited; none can forget.
 *
 * The factor is the window's shorter side over 800 dp, clamped to 0.85…1.25, times a profile:
 * *Compact* 0.9 (the desktop's default, chosen in its Settings), *Comfortable* 1.0, *Touch* 1.3
 * (the phone, fixed — a finger does not get a setting). The shorter side is measured in the
 * platform's own dp, before the override, or the scale would feed itself; the shell's 840 dp
 * breakpoint is then read in the *scaled* dp, since "room for a rail" is a question about
 * scaled things.
 */
enum class DensityProfile(val factor: Float, val key: String, val label: String,
    /** 14h·2 — a list row's minimum height (the Table's, measured 58–60 px against the tree's 27:
     * the `Checkbox`'s 48 dp interactive minimum was the floor, not the padding). */
    val rowHeightDp: Int,
    /** The minimum interactive size a control keeps inside a dense list: 28 dp under a pointer, Material's 48 under a finger. */
    val listInteractiveMinDp: Int,
) {
    COMPACT(0.9f, "compact", "Compact", rowHeightDp = 36, listInteractiveMinDp = 28),
    COMFORTABLE(1.0f, "comfortable", "Comfortable", rowHeightDp = 44, listInteractiveMinDp = 28),
    TOUCH(1.3f, "touch", "Touch", rowHeightDp = 56, listInteractiveMinDp = 48);

    /** "Pointer or finger?" for copy and affordances: *· open* under a pointer, *· tap to open* on Touch (14h·2 #3). */
    val pointer: Boolean get() = this != TOUCH

    companion object {
        /** The stored key, else the desktop's default. */
        fun fromKey(key: String?): DensityProfile = entries.firstOrNull { it.key == key } ?: COMPACT
    }
}

const val DENSITY_PROFILE_KEY = "density_profile"

/**
 * B§13.4 14d — the profile in force, for a surface that must ask "pointer or finger?" without
 * a platform check: the block toolbar floats above the selection under a pointer profile and
 * sits inline under Touch. Provided by the scaffold beside the scaled density; Touch when
 * nothing provides it, so a surface drawn outside the shell errs toward the finger.
 */
val LocalDensityProfile = staticCompositionLocalOf { DensityProfile.TOUCH }

/** The side length that scores 1.0 before the profile — a small laptop window, a large tablet. */
const val SCALE_REFERENCE_DP = 800f
const val SCALE_MIN = 0.85f
const val SCALE_MAX = 1.25f

fun shellScaleFor(shorterSideDp: Float, profile: DensityProfile): Float {
    val screen = if (shorterSideDp.isFinite() && shorterSideDp > 0f) (shorterSideDp / SCALE_REFERENCE_DP).coerceIn(SCALE_MIN, SCALE_MAX) else 1f
    return screen * profile.factor
}
