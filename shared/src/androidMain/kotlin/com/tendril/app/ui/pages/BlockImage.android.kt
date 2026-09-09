package com.tendril.app.ui.pages

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Android's decoder. Returns null rather than throwing on bytes that are not a decodable image —
 * see [rememberBlockImage] for why every failure collapses to the same null. */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
