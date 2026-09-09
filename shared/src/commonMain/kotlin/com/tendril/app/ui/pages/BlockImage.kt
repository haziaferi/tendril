package com.tendril.app.ui.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * §3.1.1 / P2 — decode an encoded image (PNG, JPEG, …) into something Compose can draw.
 *
 * `expect`/`actual` because there is no common decoder: Android has `BitmapFactory`, desktop has
 * Skia. Only the *decode* is platform-specific — reading the file is plain JVM I/O and stays in
 * [rememberBlockImage], so the caching, threading and failure handling are written once.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/**
 * The bitmap at [path], or null while it loads and if it cannot be read.
 *
 * Off the composition thread deliberately. A block image is arbitrarily large — it arrived from a
 * camera or a Notion export — and decoding one inside composition would drop frames on the page
 * it is being scrolled through. `produceState` keyed on [path] means each distinct file decodes
 * once and a recomposition reuses the result.
 *
 * Null covers three cases the caller cannot usefully tell apart, which is why they are not
 * separated: still loading, the file is gone, and the bytes are not an image this platform
 * decodes. What the caller shows for null is a UI decision, made in [BlockImage].
 */
@Composable
fun rememberBlockImage(path: String?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path) {
    value = path?.let {
        withContext(Dispatchers.IO) {
            runCatching { File(it).takeIf(File::isFile)?.readBytes()?.let(::decodeImageBitmap) }.getOrNull()
        }
    }
}

/**
 * §3.1.1's IMAGE block, drawn.
 *
 * Before P2 this did not exist: the block type was offered in the slash menu, the Notion importer
 * wrote `imagePath`, and nothing anywhere read it — so an imported page showed a caption with a
 * blank where its picture should be.
 *
 * **A missing file is stated rather than left blank**, and S4 is what makes that worth doing: a
 * block whose image has not arrived from the sync folder yet is now an ordinary, temporary state
 * rather than a permanent hole, so the difference between "no picture here" and "the picture has
 * not caught up" is one a person can act on.
 */
@Composable
fun BlockImage(
    path: String?,
    modifier: Modifier = Modifier,
    /** [ContentScale.Fit] for a block, where the picture is the content. A gallery cover
     * passes [ContentScale.Crop]: it is a thumbnail standing in for a page, the whole image
     * is one tap away, and a uniform grid reads better than letterboxed cells. */
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap by rememberBlockImage(path)
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            // Fit by default, not Crop: a note's picture is content, and silently trimming its
            // edges to fill a box is the kind of "tidy" that loses the part someone photographed
            // it for.
            contentScale = contentScale,
            modifier = modifier.fillMaxWidth().heightIn(max = 320.dp),
        )
    } else {
        Box(
            modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                if (path == null) "No image" else "Image not available on this device yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
