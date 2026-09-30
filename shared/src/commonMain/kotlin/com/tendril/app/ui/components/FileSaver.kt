package com.tendril.app.ui.components

import androidx.compose.runtime.Composable

/** What became of one save: written, declined in the platform's dialog, or failed while writing. */
enum class SaveResult { SAVED, CANCELLED, FAILED }

/**
 * §6.3 (plan Phase 6) — save a file the person names and places, through the platform's own dialog.
 *
 * Returns the launcher, as [com.tendril.app.ui.pages.rememberImagePicker] does and for the same
 * reason: Android's is an Activity result registered during composition and fired later, the
 * desktop's a modal dialog. The bytes are made before the dialog opens, so a failure to make them
 * is the caller's, and [onResult] says only what happened to the file.
 */
@Composable
expect fun rememberFileSaver(onResult: (SaveResult) -> Unit): (fileName: String, mimeType: String, bytes: ByteArray) -> Unit
