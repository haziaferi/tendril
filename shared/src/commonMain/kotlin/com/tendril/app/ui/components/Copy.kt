package com.tendril.app.ui.components

import androidx.compose.runtime.Composable
import com.tendril.app.ui.nav.LocalDensityProfile

/**
 * 14h·2 — the verb a caption uses for "this opens": *open* under a pointer profile, *tap to
 * open* under Touch (`pages-desktop.md` #8: a mouse does not tap). Read from the profile the
 * scaffold provides, so a surface never asks the platform.
 */
@Composable
fun openVerb(): String = if (LocalDensityProfile.current.pointer) "open" else "tap to open"
