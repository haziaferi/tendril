package com.tendril.app.ui.pages

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Android's decoder. Returns null rather than throwing on bytes that are not a decodable image —
 * see [rememberBlockImage] for why every failure collapses to the same null. */
actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? = runCatching {
    // Two passes, which is the only way to know how big a picture is before committing memory to
    // it. The first reads the header alone -- `inJustDecodeBounds` allocates no pixels and returns
    // null by design -- and the second decodes for real at a scale chosen from those dimensions.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = imageSampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()
