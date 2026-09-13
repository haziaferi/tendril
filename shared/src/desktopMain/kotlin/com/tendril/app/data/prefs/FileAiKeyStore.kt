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
 * `setWritable` is the best the JDK offers without a native call (a DPAPI wrap would need JNA,
 * which this offline build cannot fetch — `tendril-windows-spec.md` says so). Clearing the key
 * deletes the file, so no empty secret file lingers.
 */
class FileAiKeyStore(private val file: File) : AiKeyStore {
    private val _key = MutableStateFlow(load())
    override val key: StateFlow<String?> = _key.asStateFlow()

    override fun set(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        _key.value = normalized
        if (normalized == null) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText(normalized)
        restrict()
    }

    private fun restrict() {
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
            .onFailure {
                file.setReadable(false, false); file.setReadable(true, true)
                file.setWritable(false, false); file.setWritable(true, true)
            }
    }

    private fun load(): String? = if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
}
