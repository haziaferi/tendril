package com.tendril.app.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * §0.6.15 — the desktop's key: one line in a file of its own, never the `.properties` beside it.
 * Owner-only permissions where the filesystem speaks POSIX; on NTFS `File.setReadable` /
 * `setWritable` is the best the JDK offers without a native call. **§0.10 item 20 (2026-09-18):**
 * the line is wrapped by the platform's [SecretWrap] — Windows' DPAPI through the desktop
 * module's `DpapiWrap` (`jna-platform` was in the offline cache after all) — so the file holds a
 * blob only this Windows account can open; the permissions stay as a second fence. A bare line
 * from before is read and rewritten wrapped on first load. Clearing the key deletes the file, so
 * no empty secret file lingers.
 */
class FileAiKeyStore(private val file: File, private val wrap: SecretWrap = SecretWrap.None) : AiKeyStore {
    private val _key = MutableStateFlow(load())
    override val key: StateFlow<String?> = _key.asStateFlow()

    override fun set(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        _key.value = normalized
        if (normalized == null) {
            file.delete()
            return
        }
        write(normalized)
    }

    private fun write(key: String) {
        file.parentFile?.mkdirs()
        file.writeText(SecretFileFormat.encode(key, wrap))
        restrict()
    }

    private fun restrict() {
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
            .onFailure {
                file.setReadable(false, false); file.setReadable(true, true)
                file.setWritable(false, false); file.setWritable(true, true)
            }
    }

    private fun load(): String? {
        if (!file.exists()) return null
        val line = file.readText()
        val key = SecretFileFormat.decode(line, wrap)
        if (key != null && SecretFileFormat.wantsRewrap(line, wrap)) runCatching { write(key) }
        return key
    }
}
