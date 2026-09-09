package com.tendril.app.ui.pages

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android's picker: `PickVisualMedia`, which needs no storage permission at all — the system
 * picker hands back a grant for the one item chosen. Asking for `READ_MEDIA_IMAGES` to insert a
 * single picture would be a far larger request than the feature warrants.
 */
@Composable
actual fun rememberImagePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    // The callback is captured by the launcher for the life of the composition; without this a
    // recomposition that changed `onPicked` would keep firing the first one.
    val current by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Read now, while the grant is live. It is a one-shot permission tied to this result.
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        // The extension comes from the MIME type, not the path. A photo-picker Uri's last
        // segment is a bare media id with no dot in it, so deriving one from the "file name"
        // yields nothing and the stored copy ends up extension-less -- harmless for decoding,
        // which reads the bytes, but it quietly defeats the reason the extension travels at
        // all: telling a PNG from a JPEG in the sync folder without opening it. Found by
        // running the picker on a device; no test here could have.
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(context.contentResolver.getType(uri))
        val stem = uri.lastPathSegment.orEmpty()
        current(if (extension.isNullOrBlank()) stem else "$stem.$extension", bytes)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
