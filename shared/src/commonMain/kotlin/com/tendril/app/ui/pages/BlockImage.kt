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
import com.tendril.app.ui.theme.description

/**
 * §3.1.1 / P2 — decode an encoded image (PNG, JPEG, …) into something Compose can draw.
 *
 * `expect`/`actual` because there is no common decoder: Android has `BitmapFactory`, desktop has
 * Skia. Only the *decode* is platform-specific — reading the file is plain JVM I/O and stays in
 * [rememberBlockImage], so the caching, threading and failure handling are written once.
 */
expect fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap?

/**
 * How large a decoded block image is allowed to be, in pixels on its longest side.
 *
 * A block draws at `fillMaxWidth().heightIn(max = 320.dp)`, so on the widest phone this codebase
 * targets it is never asked to fill more than about 1440 x 1120. Decoding a camera photograph at
 * full resolution to draw it there is the difference between roughly 6 MB and roughly 48 MB of
 * bitmap: a 12-megapixel image is 4000 x 3000 x 4 bytes, and two or three of them on one page is
 * an OutOfMemoryError rather than a slow page.
 */
const val BLOCK_IMAGE_MAX_DIMENSION = 1600

/** The same budget for a Gallery cover, which is a 100dp-tall thumbnail standing in for a page
 * (§5.6) rather than the content itself, and needs a fraction of the pixels. */
const val COVER_IMAGE_MAX_DIMENSION = 512

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
fun rememberBlockImage(
    path: String?,
    maxDimension: Int = BLOCK_IMAGE_MAX_DIMENSION,
): State<ImageBitmap?> = produceState<ImageBitmap?>(null, path, maxDimension) {
    value = path?.let {
        withContext(Dispatchers.IO) {
            runCatching {
                File(it).takeIf(File::isFile)?.readBytes()?.let { bytes ->
                    decodeImageBitmap(bytes, maxDimension)
                }
            }.getOrNull()
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
    /** The decode budget — see [BLOCK_IMAGE_MAX_DIMENSION]. A caller drawing a thumbnail should
     * pass [COVER_IMAGE_MAX_DIMENSION] rather than pay for pixels it then crops away. */
    maxDimension: Int = BLOCK_IMAGE_MAX_DIMENSION,
) {
    val bitmap by rememberBlockImage(path, maxDimension)
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
                style = MaterialTheme.typography.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * The largest power-of-two reduction that still leaves the picture at least [maxDimension] across.
 *
 * Erring *above* the budget rather than below it: `inSampleSize` only halves, so the choice for a
 * 4000px photo against a 1600px budget is 2000px or 1000px, and 1000 would be visibly soft on a
 * screen asked to draw it 1440 wide. 2000px costs about 12 MB against the 48 MB a full decode
 * would have taken, which is the reduction that matters.
 *
 * Powers of two because that is what `BitmapFactory` honours: any other value is rounded down to
 * one, so computing a precise ratio here would only disguise what actually happens.
 *
 * In `commonMain` though only Android applies it, because it is the one piece of this that is
 * arithmetic rather than platform API, and arithmetic is what a test can hold still.
 */
fun imageSampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    // A header this build could not parse leaves the dimensions at -1. Decoding at full size is
    // the safe reading of "unknown": it is what happened before this function existed.
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxDimension) sample *= 2
    return sample
}
