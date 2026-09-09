package com.tendril.app.ui.pages

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/** Desktop's decoder, via Skia — the same engine Compose draws with, so anything it accepts
 * renders. Desktop had no image handling of any kind before P2. */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
