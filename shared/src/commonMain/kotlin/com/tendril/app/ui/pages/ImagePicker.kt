package com.tendril.app.ui.pages

import androidx.compose.runtime.Composable

/**
 * §3.1.1 / P2 — choose an image file, on whichever platform this is.
 *
 * Returns the launcher rather than taking a "show" flag, because the two platforms disagree about
 * what showing one means: Android's picker is an Activity result that must be registered during
 * composition and fired later, while desktop's is a modal dialog that blocks where it is called.
 * A `() -> Unit` is the only shape both can honestly satisfy.
 *
 * [onPicked] receives the file's own name — used only for its extension, so the stored copy keeps
 * a sensible suffix — and its bytes. Bytes rather than a path or a Uri on purpose: a content Uri
 * is not readable later and a path may be outside anything this app can reach on the next launch,
 * so the copy into app-private storage has to happen while the grant is still live. That is the
 * same reason `NotionImporter` copies assets in rather than referencing them where they sit.
 */
@Composable
expect fun rememberImagePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit
