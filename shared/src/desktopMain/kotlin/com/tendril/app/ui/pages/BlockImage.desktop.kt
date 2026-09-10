package com.tendril.app.ui.pages

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/** Desktop's decoder, via Skia — the same engine Compose draws with, so anything it accepts
 * renders. Desktop had no image handling of any kind before P2. */
actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

// [maxDimension] is accepted and not applied here, which is worth stating rather than leaving to be
// discovered. Skia's `makeFromEncoded` has no sampling equivalent to `BitmapFactory.inSampleSize`:
// reducing the result would mean decoding in full and scaling afterwards, which leaves the peak
// allocation -- the thing that crashes a phone -- exactly where it was. The parameter is honoured
// where the constraint is real, on Android, whose per-app heap is a small fraction of a JVM's.
// If desktop ever grows a gallery of large images, the fix is a Skia `Surface` downscale here, and
// this comment is the note that it was considered rather than missed.
